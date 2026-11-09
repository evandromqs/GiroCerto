package com.girocerto.voice

import java.text.Normalizer
import java.util.Locale

sealed interface VoiceCommand {
    data object Confirm : VoiceCommand
    data object Cancel : VoiceCommand
    data object AcceptOffer : VoiceCommand
    data object RejectOffer : VoiceCommand
    data object PunchShift : VoiceCommand
    data object DailySummary : VoiceCommand
    data object Help : VoiceCommand
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
        if (text in setOf("ajuda", "ajudar me", "o que posso falar", "comandos de voz", "comandos")) return VoiceCommand.Help
        if (text in setOf("confirmar", "confirmo", "sim pode", "pode confirmar", "sim", "confirma")) return VoiceCommand.Confirm
        if (text in setOf("cancelar", "cancela", "nao", "nao confirmar", "deixa")) return VoiceCommand.Cancel
        if (text.contains("parar comandos") || text.contains("parar de ouvir") || text.contains("desativar voz")) return VoiceCommand.StopListening
        
        // Comandos de navegação
        if ((text.contains("voltar") || text.contains("abrir") || text.contains("ir para")) && text.contains("waze")) return VoiceCommand.OpenNavigation(Navigator.WAZE)
        if ((text.contains("voltar") || text.contains("abrir") || text.contains("ir para")) && (text.contains("google maps") || text == "maps" || text.contains("mapa"))) return VoiceCommand.OpenNavigation(Navigator.GOOGLE_MAPS)
        
        // Aceitar corrida
        if (text in setOf("aceitar", "aceita", "pegar", "pega", "pegar corrida", "aceitar corrida", "aceita corrida", "aceitar oferta", "pega essa")) return VoiceCommand.AcceptOffer
        
        // Recusar corrida
        if (text in setOf("recusar", "recusa", "rejeitar", "rejeita", "ignorar", "recusar corrida", "recusa corrida", "recusar oferta", "pula", "passa essa")) return VoiceCommand.RejectOffer
        
        // Novos comandos úteis para o motorista
        if (text.contains("bater ponto") || text.contains("registrar ponto") || text.contains("iniciar turno") || text.contains("encerrar turno")) return VoiceCommand.PunchShift
        if (text.contains("quanto fiz") || text.contains("saldo de hoje") || text.contains("resumo do dia") || text.contains("meta de hoje") || text.contains("meus ganhos")) return VoiceCommand.DailySummary
        
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
