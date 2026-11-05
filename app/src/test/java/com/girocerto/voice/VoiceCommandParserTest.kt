package com.girocerto.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandParserTest {
    @Test fun `parses rejection with accents`() =
        assertEquals(VoiceCommand.RejectOffer, VoiceCommandParser.parse("Recusar corrida"))

    @Test fun `parses disconnect with app`() =
        assertEquals(VoiceCommand.Disconnect(WorkApp.UBER), VoiceCommandParser.parse("Desconectar do Uber"))

    @Test fun `parses navigation apps`() {
        assertEquals(VoiceCommand.OpenNavigation(Navigator.WAZE), VoiceCommandParser.parse("Voltar para o Waze"))
        assertEquals(VoiceCommand.OpenNavigation(Navigator.GOOGLE_MAPS), VoiceCommandParser.parse("Abrir Google Maps"))
    }

    @Test fun `does not turn unrelated speech into action`() =
        assertEquals(VoiceCommand.Unknown, VoiceCommandParser.parse("A viagem está tranquila"))
}
