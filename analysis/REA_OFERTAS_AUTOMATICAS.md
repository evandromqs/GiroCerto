# Investigação das ofertas automáticas — 23/10/2026

## Escopo e estado

Referência fornecida pelo usuário: Gigu 1.2.96, `Referencia/base.apk`, SHA-256 `fccced373c640246ed52d9dd1f93ec910c79f173008758a18453d74581ff6c72`. REA 6.7.0 com JADX headless 0.7.1. Investigação estática, sem chamada ao backend Gigu e sem acesso à conta. As evidências JSON completas estão em `analysis/rea`.

Não foi comprovado funcionamento real desta revisão: o dispositivo ADB desconectou e recusou o endereço anterior. Os testes sintéticos não representam capturas reais de ofertas dos aplicativos.

## Fluxo recuperado do Gigu

| Etapa | Evidência | Observação verificável |
| --- | --- | --- |
| Janelas | `cdg-event.json` | `onAccessibilityEvent` mantém registro de janela/pacote; remove janela encerrada. |
| Pedido de imagem | `cdg-cycle-w.json` | `w` solicita captura ao serviço Gigu e encaminha resultado. |
| Recorte por aplicativo | `cdg-frame-u.json` | `u` usa `CdgAppTarget.packageName`, `appBounds`, interseção com imagem e `om0.g`; entrega imagem por alvo a `A`. JADX sinaliza problemas de reconstrução do controle de fluxo. |
| Reconhecimento | `cdg-ocr-A.json` | `A` obtém `el0` e chama `a` com imagem e callback. |
| Limpeza de cabeçalho | `cdg-reader-el0-b.json` | `el0.b` aguarda OCR até 5 segundos; remove linha com R$, $ ou € quando topo da caixa está nos primeiros 7,5% da imagem; inverte linhas e adiciona EOR. |
| Regras por aplicativo | `config-method-b.json` | Fluxo consulta `varsRegex`, `loadingWords` e chama `LuaAppModule.sanitizeLines`/`calc`. Fonte recuperada em smali quando Java não foi reconstruído. |
| Resultado calculado | `config-lua-calc.json`, `config-lua-result.json` | Script retorna tabela convertida em `LuaScriptResult`, incluindo totalDistance, totalValue, totalTime e métricas por distância/tempo. Isso não demonstra uma fórmula fixa Java nem recupera automaticamente as regras de cada plataforma. |
| Entrega ao card | `config-method-b.txt` | Resultado/configuração/retângulo enviados ao Handler por `ql0`. Ver detalhes adicionais em `rea/overlay-findings.md`. |

IDs adicionais: recorte `ev_1be73554330b79ef14cb2f0cee5f1904cb2e0f7456df483d75aee61754727cf6`; OCR/cabeçalho `ev_4c59a2332643570955afb98f56c913416a13168bbad3379b158edd48266db102`.

## Mudanças próprias no Giro Certo

- Leitura da janela alvo por pacote e limites, inclusive quando outra janela tem foco.
- Android 14+: captura da janela. Android 11–13: recorte do aplicativo e máscara da bolha própria. Capturas continuam temporárias em memória e dependem da opção OCR.
- Releitura enquanto janela observável; tentativas adiadas quando processamento está ocupado ou no intervalo entre capturas.
- Filtro do valor monetário do cabeçalho baseado na evidência acima; demais preços conflitantes continuam rejeitados.
- Valor e distância bastam para cálculo por km e custos. Duração ausente fica indisponível, sem apresentar ganho por hora fictício.
- Distância explicitamente rotulada como total não recebe novamente a distância até a coleta.
- A tela manual de Análise permanece removida. Não há aceite automático nem gravação de áudio/vídeo.

## Limites e validação pendente

As regras Lua efetivas de Uber, iFood e 99Food ainda não foram integralmente recuperadas. A implementação do Giro Certo é própria e não constitui paridade comprovada com o Gigu. Ofertas com múltiplos valores, formatos desconhecidos ou texto inacessível podem continuar sendo rejeitadas.

No aparelho é necessário verificar: serviço de acessibilidade conectado, sobreposição autorizada, OCR ativado em Ajustes → Copiloto e uma oferta real. Registrar a sequência leitura → cálculo → janela anexada, sem aceitar a corrida. Revalidar leitura de popup sobre outro aplicativo e presença da bolha do Giro Certo.

A capacidade de captura já estava declarada em `res/xml-v30/accessibility_service_config.xml`. Sua ausência no XML base não prova falha: Android 11+ seleciona a variante v30. Exigência documentada em [AccessibilityService](https://developer.android.com/reference/kotlin/android/accessibilityservice/AccessibilityService).
