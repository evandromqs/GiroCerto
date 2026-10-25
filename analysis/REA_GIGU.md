# Gigu 1.2.96 → Giro Certo

Continuação em 24/10/2026: [leitura automática e cartão flutuante](REA_LEITOR_CARTAO_2026-10-24.md), com nova evidência REA do caminho de atualização/exibição e correções próprias.

Investigação e implementação em 22/10/2026.

## Evidência e limites

REA local 6.7.0, executado diretamente pela CLI já instalada. Não houve modificação do Gigu, uso de seus serviços, cópia de código ou reutilização de imagens/fontes.

- Pacote fornecido: `co.gigu.app`, versão 1.2.96 (1296), identidade confirmada também por aapt2.
- APKM SHA-256: `574f1df35aa2c0820d74f97ec895ad364f8c7aa92fc2046975dd7f849cb6c0c6`.
- Inventário REA: `rea/package.json`, Evidence `ev_8268f0167e3b824e8f0355662bea16136867b598b677049f1b89c6a8d96b1011`.
- APK base: `rea/base-inventory.json`, Evidence `ev_fce55e169fd72f03f64a9fcbb73a032d71c3963b307a6e8e2387d0106dd77285`.
- Manifesto complementar: `rea/gigu-manifest.txt` (aapt2 Android SDK).
- Textos complementares do libapp.so: `rea/native-strings.txt`. São pistas de recursos e rótulos, não prova de execução nem de fórmulas internas.
- REA `inspect-android-package` não prosseguiu: componente JADX headless MCP 0.7.1 não configurado. Diagnóstico em `rea/android.json`.
- REA `inspect-dart-aot` não reconheceu os símbolos de snapshot do binário. Diagnóstico em `rea/dart.json`.
- Nenhum dispositivo/emulador disponível via ADB. A interface e a captura real de ofertas ainda precisam de validação em aparelho.

## Matriz funcional

| Indício no pacote | Implementação própria no Giro Certo | Estado |
|---|---|---|
| Cálculo de Ganhos; Ganho por Km/Hora | Custo, margem e semáforo, manual e acessibilidade | Implementado; leitura real depende do layout dos apps |
| Meta de Lucro Semanal/Mensal; Calcular ganho ideal | Perfil → Metas; custos completos, sugestões por km/h, aplicação às configurações | Implementado com fórmula própria explícita |
| Semáforo de Valores | Metas de km/h e classificação financeira | Implementado; não reproduz fórmula interna não recuperada |
| Nota ideal do passageiro | Copiloto → limite de nota com aviso | Implementado como alerta; sem rejeição automática |
| Apps selecionados | Ajustes → Copiloto → seleção de plataformas | Implementado |
| Selecione quais corridas ouvir | Voz por classificação verde/amarela/vermelha | Implementado |
| Histórico; filtros por aplicativo ou data | Histórico com plataforma e Hoje/7 dias/Todo período | Implementado |
| Compartilhar corrida | Resumo via seletor Android acionado pelo usuário | Implementado |
| Embarque e destino no Maps | Atalhos no histórico e notificação; destino no overlay | Implementado para endereços identificados |
| Notificação de texto | Notificação opcional da última oferta e atalhos | Implementado com permissão Android |
| Botão flutuante e duração do cartão | Bolha existente, card redesenhado, duração configurável, liga/desliga em Copiloto | Implementado; validar ciclo de vida em aparelho |
| Captura de Tela da Chamada | Comprovante opcional Android 11+, tela inteira na galeria, com aviso e limite de frequência | Implementado; desligado por padrão, validar em aparelho |
| Paleta de cores / ordem das métricas | Tema fixo turquesa/preto/branco solicitado | Personalização livre/ordem não implementadas |
| Mensagem para passageiro | Modelo local no Perfil e revisão/compartilhamento manual no histórico | Implementado via seletor Android; usuário escolhe destinatário e confirma |
| Câmera Secreta / gravação | Nenhum acesso a câmera/microfone e nenhuma gravação | Excluído conforme pedido |
| Aceite automático | Nenhum clique/gesto em outros apps | Excluído conforme pedido |

Preservado o funcionamento local do Giro Certo, sem conta ou assinatura. Não se afirma paridade integral: recursos remotos, execução real e detalhes internos do Gigu permanecem desconhecidos. Personalização livre da paleta/ordem das métricas e equivalência de integrações internas permanecem pendentes.

## Design

Superfícies pretas com relevo discreto, sombras, bordas e reflexo fino; turquesa nos controles; branco no conteúdo. Ícones de navegação próprios e monocromáticos. Cores de semáforo preservadas para compreensão da classificação.

## Correções adicionais

Normalização monetária, avaliação não confundida com tarifa, preservação de embarque/nota ao salvar, precisão das metas persistidas, histórico serializado/atômico, custo diário no resumo, estado de abas e retomada da autorização de sobreposição.

Histórico representa ofertas analisadas, não receita comprovada de corridas realizadas. O salvamento automático é opcional e começa desligado.


## Validação final — 23/10/2026

- `testDebugUnitTest`: 39 testes, zero falhas/erros.
- `assembleDebug`: APK gerado com sucesso.
- `lintDebug`: concluído sem erros; avisos permanecem no relatório.
- `compileDebugAndroidTestKotlin`: testes instrumentados compilados, não executados (sem aparelho/emulador).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256: `6BF61E8D20515F3A2C984163ACD93AA670E051D04EC0C3CDE423CA5CF9ED30FE`.
- Aparência, permissões, TTS, notificações, screenshot e integração com apps precisam de testes em aparelho. Não há alegação de validação visual ou de paridade integral.

## Investigação do leitor Android — 23/10/2026

O bloqueio JADX foi resolvido localmente: componente 0.7.1 baixado da distribuição indicada pelo REA e SHA-256 verificado. A análise estática recuperou:
- Serviço CdgServices, métodos e campos: rea/cdg-class.json (ev_c3e4f43d1f31758e39ed088a712f904b367fa839be60ca983250adcbbcf324f1).
- onAccessibilityEvent mantém controle por janela: rea/cdg-event.json.
- Fluxo com Bitmap, resultados OCR e getVarsRegex por app: rea/cdg-read.json.
- Processamento de bitmap e dados de sanitização: rea/cdg-ocr.json.
- DEX contém referências explícitas a CdgOcrReader, ML Kit, engine Lua e sanitize_lines: rea/classes.dex-reader-strings.txt.

Isso comprova componentes estáticos mais amplos que regex na árvore acessível. Não recuperamos todas as regras Lua/configurações efetivas e não comprovamos qual caminho executou em uma oferta específica. Não se conclui que todas as regras estejam embutidas no APK.

Giro Certo: implementação própria de OCR local com modelo incluído, Android11+, opção explícita desligada por padrão, frames só em memória, sem gravação/galeria/rede. Integração real ainda requer validação; não equivale a recuperar regras do Gigu.
