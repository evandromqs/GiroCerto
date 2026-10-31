package com.girocerto.monitoring

/** Delivery-card rules only. Uber always passes through unchanged. */
internal object DeliveryOfferRules {
    private val money = Regex("(?i)(?:R\\$|BRL)\\s*[0-9]+[.,][0-9]{2}")
    private val cash = Regex("(?i)\\b(coletar|cobrar|receber do cliente|dinheiro do cliente|troco)\\b")
    private val extra = Regex("(?i)^\\s*\\+\\s*(?:R\\$|BRL)")

    fun prepare(raw: List<String>, pkg: String): List<String> {
        val ninetyNine = pkg == "com.app99.driver" || pkg == "com.taxis99" || pkg.startsWith("com.didiglobal")
        val ifood = pkg.startsWith("br.com.ifood")
        if (!ninetyNine && !ifood) return raw
        val lines = AccessibilityOfferReader.joinAdjacentMeasurementNodes(OfferTextNormalization.lines(raw))
        if (ifood) {
            // A third-party earnings overlay can be above the actual route card.
            val route = lines.indexOfLast { it.contains(Regex("(?i)rota para (moto|bike|bicicleta|carro)")) }
            if (route >= 0) {
                val fare = (0 until route).lastOrNull { money.matches(lines[it]) }
                return if (fare != null && route - fare <= 3) lines.drop(fare) else lines.drop(route)
            }
            return lines
        }
        val skip = mutableSetOf<Int>()
        lines.forEachIndexed { index, line ->
            if (cash.containsMatchIn(line)) {
                skip.add(index)
                // Accessibility/OCR may split the collection label from its amount.
                if (!money.containsMatchIn(line) && lines.getOrNull(index + 1)?.let { money.matches(it) } == true) {
                    skip.add(index + 1)
                }
            }
            // An additional reward is not the guaranteed fare. Never add a deadline bonus.
            if (extra.containsMatchIn(line)) skip.add(index)
            if (line == "+" && lines.getOrNull(index + 1)?.let { money.containsMatchIn(it) } == true) {
                skip.add(index); skip.add(index + 1)
            }
        }
        return lines.filterIndexed { i, _ -> i !in skip }
    }
}
