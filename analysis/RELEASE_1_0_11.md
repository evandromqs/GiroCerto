# Leitura Uber e diagnóstico — 1.0.11

Relato: valores não detectados no Uber e NullPointerException no cartão flutuante.
Não havia aparelho conectado nem stack trace do erro relatado. A origem exata
da exceção no aparelho ainda não está confirmada.

Problemas reproduzíveis/revisados:
- O tratamento de exceções da leitura de texto retornava antes de tentar OCR.
- A validação de contexto do OCR rejeitava espaços não separáveis e marcas
  invisíveis que podem acompanhar a moeda. Agora os dois caminhos normalizam
  a entrada sem inventar valores, mantendo a rejeição de preços concorrentes.
- Exceções nos callbacks de interpretação não eram tratadas; inicialização do
  reconhecedor ocorria antes da região protegida. A inicialização é adiada para
  o processamento e as falhas são registradas por etapa.
- A tela guardava apenas o nome da exceção. O diagnóstico agora retém a última
  falha e até oito posições de código, sem a mensagem da exceção ou texto da tela.

Validação local: 110 testes unitários passaram; lintDebug e compilação dos
testes Android passaram. Novo teste instrumentado usa o reconhecedor ML Kit
real em uma imagem sintética do Uber com R$ 16,73, 10.7 km e 29 min, duas vezes.
Isso não substitui validar captura, permissões e janelas reais no celular.

Após instalar, testar uma oferta Uber com OCR habilitado. Se a leitura falhar,
copiar o diagnóstico em Copiloto após a ocorrência para localizar a exceção.
