package com.girocerto.monitoring

import java.util.Locale
import java.util.regex.Pattern

/**
 * Interpretador especializado em extrair dados de ofertas de corrida e entrega a partir dos textos
 * presentes na árvore de acessibilidade dos principais aplicativos de transporte e entrega
 * (Uber, 99/DiDi, iFood, inDrive, Keeta).
 */
object AccessibilityOfferReader {

    // Regex para valores monetários em Reais (ex: "R$ 28,50", "R$28.50", "R$ 115,00")
    private val FARE_PATTERN = Pattern.compile(
        """(?:R\$\s*|BRL\s*)((?:[0-9]{1,3}(?:\.[0-9]{3})+,[0-9]{2}|[0-9]{1,4}[.,][0-9]{2}))(?![0-9.,])""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex para valor numérico solto com formato monetário (ex: "28,50", "15.90")
    private val ISOLATED_FARE_PATTERN = Pattern.compile(
        """\b([0-9]{1,3}(?:[.,][0-9]{2}))\b"""
    )

    // Regex para distâncias em quilômetros (ex: "5,4 km", "12.8km", "3 km")
    private val DISTANCE_KM_PATTERN = Pattern.compile(
        """([0-9]+(?:[.,][0-9]+)?)\s*(?:km|quil[oô]metros?)\b""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex para distâncias curtas de embarque em metros (ex: "450 m", "800m", "500 metros")
    private val DISTANCE_M_PATTERN = Pattern.compile(
        """\b([0-9]{2,4})\s*(?:m|metros?)\b""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex para tempo em minutos (ex: "18 min", "25 mins", "12 minutos")
    private val DURATION_MIN_PATTERN = Pattern.compile(
        """\b([0-9]{1,3})\s*(?:min|mins|minutos?)\b""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex para tempo em horas e minutos (ex: "1 h 15 min", "1h20min", "2 horas")
    private val DURATION_HOUR_MIN_PATTERN = Pattern.compile(
        """([0-9]+)\s*(?:h|hr|horas?)\s*(?:e\s*)?(?:([0-9]+)\s*(?:min|mins|minutos?))?""",
        Pattern.CASE_INSENSITIVE
    )

    // Regex para nota de passageiro (ex: "★ 4.95", "⭐ 5.0", "4.92 ★" ou "4.95" isolado)
    private val RATING_STAR_PATTERN = Pattern.compile(
        """[★⭐*]\s*([0-5][.,][0-9]{1,2})\b|\b([0-5][.,][0-9]{1,2})\s*[★⭐*]"""
    )
    private val RATING_ISOLATED_PATTERN = Pattern.compile(
        """\b([0-5][.,][0-9]{1,2})\b"""
    )

    // Palavras-chave indicativas de endereços ou logradouros comuns no Brasil
    private val ADDRESS_KEYWORDS = listOf(
        "rua", "r.", "av.", "avenida", "alameda", "rod.", "rodovia", "estrada",
        "travessa", "pça.", "praça", "bairro", "aeroporto", "terminal", "shopping", "condomínio"
    )

    /**
     * Analisa uma lista de textos coletados da tela do aplicativo e extrai os campos da oferta.
     *
     * @param rawLines Lista de strings extraídas da hierarquia de acessibilidade.
     * @param packageName Nome do pacote da aplicação em primeiro plano (ex: "com.ubercab.driver").
     */
    fun parse(rawLines: List<String>, packageName: String): OfferReaderResult {
        if (rawLines.isEmpty()) {
            return OfferReaderResult(
                appName = resolveAppName(packageName, rawLines),
                error = "Nenhum texto identificado na tela",
                source = OfferSource.ACCESSIBILITY_SERVICE
            )
        }

        val appName = resolveAppName(packageName, rawLines)
        val cleanedLines = joinAdjacentMeasurementNodes(OfferTextNormalization.lines(rawLines))

        // Flattened text cannot distinguish repeated legs from duplicate tree nodes.
        // The collector removes parent aggregates; otherwise fail closed and retry via OCR.
        val measurements = cleanedLines.filter { DISTANCE_KM_PATTERN.matcher(it).find() || DURATION_MIN_PATTERN.matcher(it).find() }
        val repeated = measurements.groupingBy { it }.eachCount().any { (line, count) -> count > 1 && !line.contains("total", true) }
        val aggregates = measurements.any { parent -> measurements.any { child ->
            parent != child && !parent.contains("total", true) &&
                Regex("(?<![0-9A-Za-z.,])" + Regex.escape(child) + "(?![0-9A-Za-z])").containsMatchIn(parent)
        } }
        if (repeated || aggregates) return OfferReaderResult(appName = appName,
            error = "Medidas repetidas ou agrupadas; aguardando leitura inequívoca",
            missingFields = listOf("Trajeto inequívoco"), source = OfferSource.ACCESSIBILITY_SERVICE)

        val eligibleLines = cleanedLines.filterNot {
            Regex("(?i)\\b(saldo|carteira|ganhos de hoje|ganhos da semana)\\b").containsMatchIn(it)
        }
        val priced = eligibleLines.flatMap { line ->
            val matcher = FARE_PATTERN.matcher(line)
            buildList { while (matcher.find()) add(parseCurrency(matcher.group(1))) }
        }
        val maxFare = priced.maxOrNull()
        val included = includedBonusFares(cleanedLines)
        val prices = priced.filterNot { fare -> maxFare != null && fare < maxFare && included.any { kotlin.math.abs(it - fare) < 0.001 } }.distinct()
        if (prices.size > 1) return OfferReaderResult(appName = appName,
            error = "Mais de um valor monetário na janela; aguardando oferta inequívoca",
            missingFields = listOf("Valor da oferta"), source = OfferSource.ACCESSIBILITY_SERVICE)
        val currencyCount = eligibleLines.sumOf { Regex("(?i)(R\\$|BRL)").findAll(it).count() }
        if (priced.isEmpty() && currencyCount > 0) return OfferReaderResult(appName = appName, error = "Formato monetário ambíguo", missingFields = listOf("Valor da oferta"))
        var fare: Double? = prices.singleOrNull() ?: maxFare
        var tripDistanceKm: Double? = null
        var pickupDistanceKm: Double? = null
        var durationMinutes: Int? = null
        var passengerRating: Double? = null
        var destinationAddress: String? = null
        var pickupAddress: String? = null
        val addresses = mutableListOf<String>()
        var addressContext = ""

        val detectedDistances = mutableListOf<Pair<Double, Boolean>>() // (distância, éEmbarque)
        val detectedDurations = mutableListOf<Int>()
        val explicitTotals = mutableListOf<Double>()
        val totalDurations = mutableListOf<Int>()

        for (line in eligibleLines) {
            val lowerLine = line.lowercase(Locale.ROOT)

            // 1. Extração de Tarifa (R$)
            if (fare == null) {
                val fareMatcher = FARE_PATTERN.matcher(line)
                if (fareMatcher.find()) {
                    val rawValue = fareMatcher.group(1)
                    fare = parseCurrency(rawValue)
                }
            }

            // 2. Extração de Distâncias
            val isPickupLine = lowerLine.contains("embarque") ||
                    lowerLine.contains("chegada") ||
                    lowerLine.contains("até") ||
                    lowerLine.contains("ponto de encontro")

            // Procura metros (típico de embarque próximo)
            val mMatcher = DISTANCE_M_PATTERN.matcher(line)
            while (mMatcher.find()) {
                val meters = mMatcher.group(1)?.toDoubleOrNull() ?: 0.0
                if (meters in 50.0..5000.0) {
                    detectedDistances.add(Pair(meters / 1000.0, true))
                }
            }

            // Procura quilômetros
            val kmMatcher = DISTANCE_KM_PATTERN.matcher(line)
            while (kmMatcher.find()) {
                val kmVal = parseDecimal(kmMatcher.group(1))
                if (kmVal > 0.0) {
                    if (lowerLine.contains("total")) explicitTotals.add(kmVal)
                    else detectedDistances.add(Pair(kmVal, isPickupLine))
                }
            }

            // 3. Extração de Duração
            val hourMatcher = DURATION_HOUR_MIN_PATTERN.matcher(line)
            if (hourMatcher.find()) {
                val hours = hourMatcher.group(1)?.toIntOrNull() ?: 0
                val mins = hourMatcher.group(2)?.toIntOrNull() ?: 0
                val totalMins = (hours * 60) + mins
                if (totalMins > 0) {
                    if (lowerLine.contains("total")) totalDurations.add(totalMins) else detectedDurations.add(totalMins)
                }
            } else {
                val minMatcher = DURATION_MIN_PATTERN.matcher(line)
                while (minMatcher.find()) {
                    val mins = minMatcher.group(1)?.toIntOrNull() ?: 0
                    if (mins > 0 && mins < 600) {
                        if (lowerLine.contains("total")) totalDurations.add(mins) else detectedDurations.add(mins)
                    }
                }
            }

            // 4. Extração de Nota do Passageiro (ignora se a linha contiver "R$" ou distância/tempo)
            if (passengerRating == null && !line.contains("R$") && !line.contains("km") && !line.contains("min")) {
                val starMatcher = RATING_STAR_PATTERN.matcher(line)
                if (starMatcher.find()) {
                    val rawRating = starMatcher.group(1) ?: starMatcher.group(2)
                    val ratingVal = parseDecimal(rawRating)
                    if (ratingVal in 0.0..5.0) {
                        passengerRating = ratingVal
                    }
                } else {
                    val isoMatcher = RATING_ISOLATED_PATTERN.matcher(line)
                    if (isoMatcher.find()) {
                        val ratingVal = parseDecimal(isoMatcher.group(1))
                        if (ratingVal in 0.0..5.0) {
                            passengerRating = ratingVal
                        }
                    }
                }
            }

            // Address labels may live in separate accessibility nodes.
            if (listOf("destino", "desembarque", "entrega").any { lowerLine.contains(it) }) addressContext = "destination"
            else if (listOf("embarque", "origem", "coleta", "retirada").any { lowerLine.contains(it) }) addressContext = "pickup"
            if (ADDRESS_KEYWORDS.any { lowerLine.contains(it) } && line.length in 8..120) {
                when (addressContext) {
                    "destination" -> destinationAddress = line
                    "pickup" -> pickupAddress = line
                    else -> addresses.add(line)
                }
                addressContext = ""
            }
        }
        if (destinationAddress == null && addresses.size >= 2) {
            pickupAddress = pickupAddress ?: addresses.first()
            destinationAddress = addresses.last()
        }

        // Se ainda não encontrou tarifa com "R$", tenta valor numérico solto proeminente
        if (fare == null) {
            val candidates = eligibleLines.filterNot { it.trim().matches(Regex("[0-5][.,][0-9]{1,2}")) }
                .mapNotNull { line -> val matcher = ISOLATED_FARE_PATTERN.matcher(line)
                    if (matcher.matches()) parseCurrency(matcher.group(1)).takeIf { it in 4.0..500.0 } else null }.distinct()
            fare = candidates.singleOrNull()
        }

        // Consolidação de Distâncias (Viagem vs Embarque)
        if (explicitTotals.distinct().size == 1) {
            // A stated total includes approach/collection; do not add it twice.
            tripDistanceKm = explicitTotals.distinct().single()
            pickupDistanceKm = 0.0
        } else if (detectedDistances.isNotEmpty() && detectedDistances.size <= 2 && explicitTotals.isEmpty()) {
            val pickupCandidate = detectedDistances.firstOrNull { it.second }
            val tripCandidates = detectedDistances.filter { !it.second }

            if (pickupCandidate != null && tripCandidates.isNotEmpty()) {
                pickupDistanceKm = pickupCandidate.first
                tripDistanceKm = tripCandidates.maxByOrNull { it.first }?.first
            } else if (detectedDistances.size >= 2) {
                // Dois valores: o menor costuma ser o embarque e o maior o trajeto
                val sorted = detectedDistances.map { it.first }.sorted()
                pickupDistanceKm = sorted.first()
                tripDistanceKm = sorted.last()
            } else {
                tripDistanceKm = detectedDistances.first().first
                pickupDistanceKm = 0.0
            }
        }

        // Consolidação de Duração
        if (totalDurations.distinct().size == 1) {
            durationMinutes = totalDurations.first()
        } else if (totalDurations.isEmpty() && detectedDurations.isNotEmpty()) {
            // Em caso de duração de embarque + corrida, consideramos o tempo total ou o maior tempo
            durationMinutes = if (detectedDurations.size > 1 && detectedDurations.size <= 3) {
                detectedDurations.sum()
            } else {
                detectedDurations.maxOrNull()
            }
        }

        // Validação dos campos faltantes
        val missing = mutableListOf<String>()
        if (fare == null || fare <= 0.0) missing.add("Valor da oferta")
        if (tripDistanceKm == null || tripDistanceKm <= 0.0) missing.add("Distância da corrida")
        // Fare and total distance suffice for per-km earnings; time is optional.

        return OfferReaderResult(
            fare = fare,
            distanceKm = tripDistanceKm,
            durationMinutes = durationMinutes,
            pickupDistanceKm = pickupDistanceKm ?: 0.0,
            appName = appName,
            destination = destinationAddress,
            pickupAddress = pickupAddress,
            passengerRating = passengerRating,
            missingFields = missing,
            source = OfferSource.ACCESSIBILITY_SERVICE
        )
    }

    /**
     * Amounts explicitly introduced as included bonus, surge or impulse.
     * The larger fare on the card stays; a bigger unlabeled amount is not treated as bonus.
     */
    internal fun includedBonusFares(lines: List<String>): Set<Double> {
        val marker = Regex("(?i)\\b(inclui|bônus|bonus|impulso)\\b")
        val moneyOnly = Regex("(?i)^(?:R\\$|BRL)\\s*[0-9]{1,4}[.,][0-9]{2}$")
        val found = mutableListOf<Double>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (marker.containsMatchIn(line)) {
                val matcher = FARE_PATTERN.matcher(line)
                while (matcher.find()) found.add(parseCurrency(matcher.group(1)))
            }
            if (line.matches(Regex("(?i)inclui"))) {
                for (next in lines.drop(index + 1).take(2)) {
                    val text = next.trim()
                    if (text.matches(Regex("(?i)(bônus|bonus|impulso)"))) continue
                    if (!moneyOnly.matches(text)) break
                    val matcher = FARE_PATTERN.matcher(text)
                    if (matcher.find()) found.add(parseCurrency(matcher.group(1)))
                    break
                }
            }
        }
        return found.toSet()
    }

    /** Join only adjacent explicit tokens; never invent absent units or values. */
    private fun joinAdjacentMeasurementNodes(lines: List<String>): List<String> {
        val number = Regex("""[0-9]+(?:[.,][0-9]+)?""")
        val money = Regex("""[0-9]{1,4}[.,][0-9]{2}""")
        val unit = Regex("""(?i)(km|quil[oô]metros?|m|metros?|min|mins|minutos?|h|hr|horas?)""")
        val result = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val current = lines[index]
            val next = lines.getOrNull(index + 1)
            when {
                (current.equals("R$", true) || current.equals("BRL", true)) &&
                    next != null && money.matches(next) &&
                    lines.getOrNull(index + 2)?.let { unit.matches(it) } != true -> {
                    result.add("$current $next")
                    index += 2
                }
                number.matches(current) && next != null && unit.matches(next) -> {
                    result.add("$current $next")
                    index += 2
                }
                else -> { result.add(current); index++ }
            }
        }
        return result
    }

    private fun resolveAppName(packageName: String, lines: List<String>): String {
        return when {
            packageName.contains("ubereats") -> "Uber Eats"
            packageName.contains("ubermoto") -> "Uber Moto"
            packageName.contains("ubercab") -> "Uber"
            packageName.contains("didiglobal.food") || packageName.contains("99food") -> "99Food"
            packageName.contains("didiglobal") -> "DiDi"
            packageName == "com.app99.driver" || packageName.contains("taxis99") -> {
                val fullText = lines.joinToString(" ")
                if (fullText.contains("Food", ignoreCase = true)) "99Food" else "99"
            }
            packageName.contains("indriver") -> "inDrive"
            packageName.contains("ifood") -> "iFood"
            packageName.contains("keeta") || packageName.contains("meituan") || packageName == "com.sankuai.sailor.courier" -> "Keeta"
            packageName.contains("girocerto") -> "Giro Certo (Simulação)"
            else -> {
                val fullText = lines.joinToString(" ")
                when {
                    fullText.contains("UberEats", ignoreCase = true) ||
                            fullText.contains("Uber Eats", ignoreCase = true) -> "Uber Eats"
                    fullText.contains("Uber Moto", ignoreCase = true) -> "Uber Moto"
                    fullText.contains("Uber", ignoreCase = true) -> "Uber"
                    fullText.contains("99Food", ignoreCase = true) ||
                            fullText.contains("DiDi Food", ignoreCase = true) -> "99Food"
                    fullText.contains("99Pop", ignoreCase = true) ||
                            fullText.contains("99\b".toRegex()) -> "99"
                    fullText.contains("iFood", ignoreCase = true) -> "iFood"
                    fullText.contains("inDrive", ignoreCase = true) -> "inDrive"
                    fullText.contains("Keeta", ignoreCase = true) -> "Keeta"
                    else -> "Copiloto"
                }
            }
        }
    }

    private fun parseCurrency(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        return try {
            val normalized = raw.trim()
                .replace(" ", "")
                
                 .let { if (it.contains(',')) it.replace(".", "").replace(",", ".") else it }
            normalized.toDoubleOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    private fun parseDecimal(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        return try {
            val normalized = raw.trim().replace(",", ".")
            normalized.toDoubleOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }
}
