# Revisão de ícone e Play Protect — 25/10/2026

O aviso informado pelo usuário é o bloqueio por possível acesso a dados sensíveis,
com risco de roubo de identidade e fraude financeira. A orientação oficial do
Google descreve esse aviso para apps instalados de fontes da internet que
declaram permissões sensíveis, incluindo acessibilidade:
https://developers.google.com/android/play-protect/warning-dev-guidance

O Giro Certo declara serviço de acessibilidade para leitura de ofertas. O código
mantém essa função: mudar ícone, nome, assinatura ou ofuscar permissões não é uma
solução para a classificação. Não há garantia de desbloqueio nesta versão.

Correções: ícone próprio vetorial com G turquesa e raio branco, variantes
adaptativa, convencional e monocromática; remoção da permissão não utilizada
FOREGROUND_SERVICE e permissões de rede incorporadas por bibliotecas (a versão
usa OCR incluído e não tem atualizador/backend); explicação e confirmação antes de abrir a autorização de
acessibilidade. Identificador e chave release preservados.

O ícone antigo era o padrão do Android. Isso não comprova por que o instalador
do aparelho não mostrou um ícone; os novos recursos serão verificados no APK.

## Revisão pelo Google

Formulário: https://support.google.com/googleplay/android-developer/contact/protectappeals
Pacote: com.girocerto.app
O formulário pede contato, nome do desenvolvedor, SHA-256 do APK enviado ao
VirusTotal e justificativa. Não houve envio ao Google ou VirusTotal nesta tarefa.
O desenvolvedor deve revisar os dados e autorizar esses envios separadamente.

Texto-base para revisão:

Giro Certo é um aplicativo de planejamento e acompanhamento de ganhos de
motoristas e entregadores. O serviço de acessibilidade é usado para ler ofertas
nos aplicativos de trabalho selecionados, após autorização do usuário, e calcular
indicadores locais. Não executa gestos nem aceita corridas. OCR e comprovantes
de tela são opcionais e começam desligados. O processamento é local; o histórico
de ofertas é salvo por ação do usuário ou por opção explícita de salvamento
automático. Metas e diário de ganhos funcionam sem acessibilidade. Solicitamos
revisão da classificação do APK identificado pelo hash informado, considerando
a finalidade declarada e as permissões efetivamente utilizadas.
