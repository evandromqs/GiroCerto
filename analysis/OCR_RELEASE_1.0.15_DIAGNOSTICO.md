# OCR 1.0.15: falha na inicialização do reconhecedor

## Evidência do aparelho

O usuário informou Giro Certo 1.0.15, Android 16, acessibilidade e OCR ativos. A captura fornecida mostra uma oferta Uber Envios Moto de R$ 46,83, 34,9 km e 62 minutos. O modal exibe falha em preparação da imagem.

O diagnóstico contém `NullPointerException` em `o0.e1.a:49`, seguido de `o7.i.getValue:21` e `y4.m.onSuccess:383`, com identificador R8 `3a6b633ab261adf1aa600a0cc0af67360914332e6a81fc673b229fc6b61bf910`.

## Inspeção do APK publicado

Baixado o APK de `v1.0.15`; SHA-256 `42541fda84a6c9d3ccbd9008891a29226580a946e0d5fb3a4fea3dbb36f21f1d`. Inspecionado com o JADX headless 0.7.1 já instalado para o REA. Resultados locais em `analysis/rea/giro-1.0.15` (ignorados pelo Git).

- `y4.m.onSuccess` chega à inicialização lazy do reconhecedor após conversão, máscara e recorte da imagem.
- `o7.i` implementa o lazy Kotlin.
- O caso 22 de `o0.e1.a` resolve a fábrica `k7.g` pelo contexto ML Kit e usa seus campos para criar o reconhecedor.
- O manifesto mantém os nomes dos três registradores ML Kit.
- Os inventários DEX dos três registradores (`TextRegistrar`, `VisionCommonRegistrar`, `CommonComponentRegistrar`) têm somente o método de componentes e **nenhum construtor**. Uma segunda verificação independente lê diretamente os métodos definidos no DEX e confirma a ausência dos construtores públicos sem argumentos.

A descoberta por reflexão não consegue instanciar esses registradores. A fábrica do reconhecedor fica indisponível. A mensagem genérica confundia essa inicialização com preparação de bitmap. Os testes anteriores executavam a variante debug, sem a otimização que causou a perda dos construtores.

## Correção e prevenção

- Regra R8 explícita preserva o construtor público sem argumentos de implementações de `ComponentRegistrar`, mantendo a otimização das demais classes.
- `scripts/verify-ocr-release.cjs` inspeciona o APK/DEX otimizado e falha se algum dos três construtores estiver ausente. A publicação passa a executar essa verificação sobre o APK de produção, antes de publicar.
- Etapas do diagnóstico separam buffer, conversão, superfície, janela, máscara, recorte, inicialização do reconhecedor, entrada e envio ao OCR. Exceções encadeadas mantêm tipos e locais, sem copiar mensagens potencialmente privadas. Diagnóstico inclui fabricante/modelo/API.
- Teste Android cobre a instanciação por reflexão; teste unitário da transcrição verifica R$ 1,34/km e R$ 45,32/h. A imagem original, com endereços, não foi adicionada ao repositório.

## Validação local

- O verificador rejeita o APK publicado 1.0.15 e lista os três construtores ausentes.
- O mesmo verificador aceita o APK debug (controle sem otimização).
- `minifyReleaseWithR8` concluído com sucesso; o verificador aceita o DEX otimizado corrigido e confirma os três construtores públicos sem argumentos.
- `testDebugUnitTest`: 202 testes, zero falhas/erros; `assembleDebug` concluído.
- Correção incluída na versão 1.0.16. Não foi validada em execução no celular do usuário.

## Limites de funcionamento

A transcrição não é um teste de precisão do OCR na imagem. A correção da inicialização não comprova reconhecimento de todas as ofertas nem resolve eventuais períodos em que o Uber não expõe sua janela. Ainda é necessário conferir a oferta no aparelho após instalar o APK corrigido.
