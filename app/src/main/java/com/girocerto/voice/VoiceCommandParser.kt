package com.girocerto.voice

import java.text.Normalizer
import java.util.Locale

sealed interface VoiceCommand {
    data object Confirm : VoiceCommand
    data object Cancel : VoiceCommand
    data object RejectOffer : VoiceCommand
    data class Disconnect(val app: WorkApp?) : VoiceCommand
    data class OpenNavigation(val navigator: Navigator) : VoiceCommand
    data object StopListening : VoiceCommand
    data object Unknown : VoiceCommand
}

enum class Navigator { WAZE, GOOGLE_MAPS }
enum class WorkApp(val label: String, val packages: List<String>) {
    UBER("Uber", listOf("com.ubercab.driver", "com.ubermoto.driver", "com.ubereats.driver")),
    NINETY_NINE("99", listOf("com.app99.driver", "com.taxis99", "com.didiglobal.driver", "com.didiglobal.food.driver")),
    IFOOD("iFood", listOf("br.com.ifood.driver.app", "br.com.ifood.driver")),
    INDRIVE("inDrive", listOf("sinet.startup.indriver")),
    KEETA("Keeta", listOf("com.sankuai.sailor.courier", "com.sankuai.meituan.dispatch.driver", "com.keeta.driver"));
}

object VoiceCommandParser {
    fun parse(raw: String): VoiceCommand {
        val text = Normalizer.normalize(raw.lowercase(Locale.forLanguageTag("pt-BR")), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (text in setOf("confirmar", "confirmo", "sim pode", "pode confirmar")) return VoiceCommand.Confirm
        if (text in setOf("cancelar", "cancela", "nao", "nao confirmar")) return VoiceCommand.Cancel
        if (text.contains("parar comandos") || text.contains("parar de ouvir")) return VoiceCommand.StopListening
        if ((text.contains("voltar") || text.contains("abrir")) && text.contains("waze")) return VoiceCommand.OpenNavigation(Navigator.WAZE)
        if ((text.contains("voltar") || text.contains("abrir")) && (text.contains("google maps") || text == "maps")) return VoiceCommand.OpenNavigation(Navigator.GOOGLE_MAPS)
        if (text.contains("recusar") && (text.contains("corrida") || text.contains("oferta") || text == "recusar")) return VoiceCommand.RejectOffer
        if (text.contains("desconectar") || text.contains("ficar offline") || text.contains("parar de receber")) {
            return VoiceCommand.Disconnect(resolveApp(text))
        }
        return VoiceCommand.Unknown
    }

    private fun resolveApp(text: String): WorkApp? = when {
        text.contains("uber") -> WorkApp.UBER
        text.contains("99") || text.contains("noventa e nove") -> WorkApp.NINETY_NINE
        text.contains("ifood") || text.contains("i food") -> WorkApp.IFOOD
        text.contains("indrive") || text.contains("in drive") -> WorkApp.INDRIVE
        text.contains("keeta") || text.contains("quita") -> WorkApp.KEETA
        else -> null
    }
}
