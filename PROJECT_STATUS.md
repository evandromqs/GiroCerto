# Atualização — reformulação com investigação REA (22/10/2026)

Implementados tema turquesa/preto/branco com relevo discreto, planejador de metas,
preferências do copiloto, filtros do histórico, notificações opcionais, alerta de nota,
mensagem manual e comprovantes opcionais de tela (Android 11+, desligados por padrão).
Gravação escondida e aceite automático estão excluídos.

A matriz de funcionalidades, evidências e limitações está em [analysis/REA_GIGU.md](analysis/REA_GIGU.md).
Não há afirmação de paridade integral com Gigu nem validação em aparelho.

O registro anterior abaixo é histórico e não substitui a matriz atual.

---
# Status do Projeto — Giro Certo

**Data da Última Atualização**: 22 de Outubro de 2026  
**Status Geral**: ✅ **Copiloto em Tempo Real Implementado (Acessibilidade + Overlay Interativo + Síntese de Voz TTS + 100% Testes Aprovados)**

---

## 📊 Resumo Executivo da Entrega

- **Compilação**: Concluída com sucesso (`assembleDebug` gerou APK de depuração com novos serviços nativos).
- **Testes Automatizados**: 100% aprovados (`testDebugUnitTest` com 26 testes unitários sem nenhuma falha).
- **Serviço de Acessibilidade (`GiroCertoAccessibilityService`)**: Implementado nativamente com suporte a detecção de chamadas em primeiro plano (Uber, 99, inDrive) e declaração de captura nativa (`canTakeScreenshot="true"`).
- **Motor de Parsing Resiliente (`AccessibilityOfferReader`)**: Parser especializado em expressões regulares monetárias brasileiras (`R$`), distâncias (viagem vs embarque) e tempos (minutos e horas), nota do passageiro e logradouros.
- **Sintetizador de Voz Nativo (`VoiceAnnouncer`)**: Text-To-Speech em Português do Brasil com anúncio conciso para motoristas em trânsito.
- **Janela Flutuante Aprimorada (`RideOverlayService`)**: Card flutuante com badge visual da plataforma (Uber, 99, inDrive), semáforo em tempo real, atalho para o Google Maps / Street View e botão de salvar no histórico.
- **Simulador de Teste Rápido**: Integrado nas Configurações do app para validação ponta a ponta imediata sem necessidade de esperar uma corrida real tocar.

---

## 📋 Checklist de Funcionalidades do Copiloto Inteligente

### 1. Mecanismo Técnico Central (Acessibilidade & Overlay)
- [x] Serviço nativo `GiroCertoAccessibilityService` com permissão `BIND_ACCESSIBILITY_SERVICE`.
- [x] Configuração XML `accessibility_service_config.xml` (e `xml-v30`) com `canRetrieveWindowContent="true"` e `canTakeScreenshot="true"`.
- [x] Monitoramento de eventos `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_WINDOW_STATE_CHANGED` e `TYPE_WINDOWS_CHANGED`.
- [x] Filtro de pacotes suportados: `com.ubercab.driver`, `com.ubermoto.driver`, `com.ubereats.driver`, `com.taxis99`, `sinet.startup.indriver` e `com.girocerto.app`.
- [x] Debounce inteligente de 3 segundos para prevenir cálculos repetidos durante animações de tela.

### 2. Motor de Extração de Dados (`AccessibilityOfferReader`)
- [x] Extração de valor monetário (`R$ XX,XX` e numéricos isolados).
- [x] Discriminação de distância de embarque (metros ou km próximos) vs distância da viagem principal.
- [x] Extração de duração estimada (minutos e horas somadas).
- [x] Extração de avaliação do passageiro (ex: `★ 4.95`, `⭐ 4.88`).
- [x] Identificação de endereços de destino prováveis para rota.
- [x] Reconhecimento da bandeira do aplicativo emissor.

### 3. Síntese de Voz (Text-to-Speech)
- [x] Componente `VoiceAnnouncer` com inicialização segura de `TextToSpeech` em `pt-BR`.
- [x] Fala adaptada em tom e velocidade para rápida tomada de decisão ("*Uber. R$ 28,00. R$ 3,10 por quilômetro. Sinal verde.*").
- [x] Interrupção fluida (`QUEUE_FLUSH`) para que uma nova chamada substitua imediatamente a anterior.

### 4. Janela Flutuante Interativa (`RideOverlayService`)
- [x] Badge com cor e nome da plataforma (`UBER`, `99`, `INDRIVE`).
- [x] Indicadores visuais grandes de R$/km, R$/hora e Lucro Líquido Real.
- [x] Badge colorido do semáforo determinístico (Verde, Amarelo, Vermelho).
- [x] Botão **📍 Maps**: Dispara Intent direta para o Google Maps com as coordenadas/endereço do destino.
- [x] Botão **💾 Salvar**: Salva a análise instantaneamente no histórico do Giro Certo diretamente pelo card.
- [x] Timer de auto-dispensa após 18 segundos de inatividade para não poluir a tela.
- [x] Arraste livre pela tela (drag-and-drop).

### 5. Interface & Gestão de Permissões
- [x] Card de status do copiloto na Tela Inicial com indicadores verde/amarelo em tempo real.
- [x] Atalhos de um clique para abrir as telas de configurações de Acessibilidade e Sobreposição do Android.
- [x] Botão de simulação rápida em Configurações para validar voz e janela flutuante no aparelho físico.

---

## 🧪 Resultados dos Testes Automatizados

Comando executado: `.\gradlew.bat testDebugUnitTest`

- **AccessibilityOfferReaderTest** (Novos):
  - `testParseUberDriverOffer`: PASS
  - `testParse99MotoristaOffer`: PASS
  - `testParseInDriveOffer`: PASS
  - `testParseLongDistanceWithHours`: PASS
  - `testParseIncompleteScreenWithoutFare`: PASS
  - `testParseEmptyList`: PASS
- **RideCalculatorTest**:
  - `testValidRideCalculation`: PASS
  - `testZeroDistanceRide`: PASS
  - `testZeroDurationRide`: PASS
  - `testZeroFareRide`: PASS
  - `testNegativeValues`: PASS
  - `testInvalidVehicleSettings`: PASS
  - `testRoundingAndMonetarySanity`: PASS
  - `testNonFiniteSafeguard`: PASS
- **TrafficLightEvaluatorTest**:
  - `testGreenWhenBothTargetsMetAndProfitable`: PASS
  - `testYellowWhenOnlyKmMet`: PASS
  - `testYellowWhenOnlyHourMet`: PASS
  - `testYellowWhenNearBothTargetsWithinTolerance`: PASS
  - `testRedWhenLossOrZeroProfit`: PASS
  - `testRedWhenFarBelowBothTargets`: PASS
  - `testInvalidOfferOrSettings`: PASS
- **ManualOfferReaderTest**:
  - `testCompleteManualOfferWithCommas`: PASS
  - `testMissingFields`: PASS
  - `testNegativeOrInvalidText`: PASS

**Total**: 26 testes executados, 0 falhas, 100% de sucesso.

---

## 📦 Artefatos Gerados

- **APK de Depuração**:
  `c:\Users\evand\OneDrive\Documents\GiroCerto\app\build\outputs\apk\debug\app-debug.apk`
  - Assinatura: Debug keystore padrão do Android
  - Compilação: **BUILD SUCCESSFUL**


## 23/10/2026 — foco em ofertas automáticas
Tela de Análise manual removida da navegação e do projeto. Atalhos de início/histórico apontam ao Copiloto. Incluídas correções de leitura real (99 com.app99.driver, nós fragmentados, espaços monetários, leitura de janelas e diagnóstico).


23/10/2026: REA/JADX conseguiu analisar CdgServices; referências OCR/MLKit e configuração por app confirmadas. Adicionado OCR local opcional com gate de oferta e preço único, default desligado. APK instalado no celular. Detecção real via OCR ainda não validada.

