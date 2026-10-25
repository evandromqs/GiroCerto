# Leitura automática e cartão — 24/10/2026

## Evidência verificada nesta execução

REA 6.7.0 instalado, usando JADX headless 0.7.1 e JDK 17 locais.
APKM fornecido: SHA-256 `574f1df35aa2c0820d74f97ec895ad364f8c7aa92fc2046975dd7f849cb6c0c6`, igual ao inventário anterior.

Nova inspeção de `defpackage.ql0.run` em `Referencia/base.apk`:
`rea/overlay-ql0-verified.json`, Evidence
`ev_7e3e5b71ca09cd9f46ed0e0b6b99dd0aebb5e0b2ad87c822dd5021d5450e812d`.
O resultado estático mostra reaproveitamento de vista, atualização dos dados,
`updateViewLayout` se anexada e `addView` caso contrário, seguido de
`postDelayed` com duração configurada. Isso complementa a lacuna descrita no
relatório antigo de overlay. Java decompilado não prova execução em aparelho.

## Aplicação própria ao Giro Certo

- Cartão anexado recebe atualização de conteúdo e renovação do temporizador sem
  remover/recriar a vista, preservando a posição arrastada. Os botões consultam
  a análise atual, inclusive o destino atualizado.
- Leitura por acessibilidade e OCR passam pela mesma deduplicação, separada por
  pacote. Alternar ofertas entre plataformas não libera novamente a anterior
  dentro do intervalo de 15 segundos. Alteração de nota também atualiza a oferta.
- A opção de monitorar a plataforma é conferida novamente antes de entregar e
  processar resultados assíncronos.
- Falha ao solicitar o cartão não interrompe voz, notificação e salvamento
  habilitados; o diagnóstico mantém a falha em vez de afirmar janela solicitada.

A deduplicação é uma correção própria baseada na revisão do código do Giro
Certo, não uma reprodução comprovada do algoritmo do Gigu.

## Limitações

ADB disponível, mas nenhum dispositivo conectado nesta execução. Posição,
atualização visual, permissões e ofertas reais ainda precisam de teste no celular.
As regras Lua por plataforma continuam não recuperadas integralmente. Esta
revisão não demonstra paridade de reconhecimento com Gigu nem acrescenta novos
formatos de oferta ao interpretador existente.

## Roteiro no celular

Validação local concluída: `testDebugUnitTest assembleDebug --offline`, 60 testes,
zero falhas/erros. APK em `app/build/outputs/apk/debug/app-debug.apk`, SHA-256
`4307552c9a93101e536c745710bae84ac9272d8930f07d99a3cad328260e10a9`.
Os três novos testes verificam alternância de plataformas, identidade compartilhada
entre OCR/acessibilidade, mudança de nota, resultados incompletos e expiração.

Habilitar acessibilidade, sobreposição e as plataformas desejadas. Para ofertas
sem texto acessível, habilitar também OCR em Ajustes → Copiloto (Android 11+).
Com uma oferta real, confirmar cálculo e cartão; arrastá-lo e verificar se a
próxima atualização mantém a posição e altera o destino correto. Alternar duas
plataformas e conferir ausência de repetição de voz dentro de 15 segundos.
Pausar uma plataforma durante leitura e confirmar que novos resultados dela
não são entregues. A permissão de sobreposição deve ser testada concedida e
revogada. Nenhuma dessas verificações exige aceitar uma corrida.
