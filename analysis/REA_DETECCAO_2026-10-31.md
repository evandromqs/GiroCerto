# Diagnóstico e correção da leitura de ofertas — 31/10/2026

## Referência conferida

Reutilizados os resultados locais do REA 6.7.0/JADX 0.7.1, sem executar o Gigu ou consultar seus serviços. Os hashes calculados nesta revisão coincidem com os das evidências:

- APKM fornecido: `574f1df35aa2c0820d74f97ec895ad364f8c7aa92fc2046975dd7f849cb6c0c6`.
- `Referencia/base.apk`: `fccced373c640246ed52d9dd1f93ec910c79f173008758a18453d74581ff6c72`.

## Processo observado no código decompilado

1. `CdgServices.onAccessibilityEvent` mantém janelas/pacotes (`rea/cdg-event.json`).
2. `CdgServices.w` pede uma imagem a `GiguServices`, encaminhando o resultado ao processamento (`rea/cdg-cycle-w.json`). Esse método sozinho não demonstra qual API de captura executa em cada Android.
3. `u` recorta a imagem pelos limites de cada aplicativo alvo; `A` encaminha ao leitor (`rea/cdg-frame-u.json`, `rea/cdg-ocr-A.json`). Há limitações de reconstrução do fluxo pelo decompilador.
4. `el0.b` aguarda o reconhecimento por até cinco segundos, limpa linhas, filtra moeda no cabeçalho de 7,5%, inverte a lista e acrescenta `EOR` (`rea/cdg-reader-el0-b.json`). Referências DEX identificam ML Kit.
5. `CdgServices.b` seleciona `CdgAppData`, consulta `getVarsRegex`/`getLoadingWords`, executa `LuaAppModule.sanitizeLines`, extrai variáveis e usa o cálculo Lua. Os invokes estão em `rea/config-method-b.txt`.
6. `LuaAppModule.calc` chama a função configurada e converte a tabela resultante; os campos incluem `totalValue`, `totalDistance` e `totalTime` (`rea/config-lua-calc.txt`, `rea/config-lua-result.json`). `CdgServices.x` carrega scripts obtidos de `CdgAppData.getScript()` (`rea/config-method-x.json`).

Logo, a referência combina captura, OCR e interpretação configurada por aplicativo. Não foi recuperado o conteúdo integral dos scripts ativos de Uber/99. Não se pode afirmar a fórmula exata da referência nem equivalência completa com ela. A inversão de linhas e `EOR` pertencem ao protocolo das regras dela; não foram transplantados para o interpretador do Giro Certo.

## Causa encontrada no Giro Certo

O caminho anterior fazia `Bitmap.wrapHardwareBuffer`, criava um `Canvas` de software e executava `drawBitmap` com o bitmap de hardware. O Android rejeita essa combinação, antes mesmo de chamar o ML Kit. Isso explica tecnicamente uma falha sistemática na etapa de preparação; sem log do aparelho, não constitui confirmação de que seja a única causa percebida pelo usuário.

Correção: conversão explícita com `copy(Bitmap.Config.ARGB_8888, true)` antes do recorte/máscara. A restrição é desenhar hardware em Canvas de software; criar uma cópia **ARGB_8888 mutável** é permitido.

Fontes Android: [validação de bitmaps em BaseCanvas](https://android.googlesource.com/platform/frameworks/base/+/716f38177ee1401373ced8d1f665431bb7a57d14%5E%21/) e [captura pelo AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService).

## Outras correções implementadas

- Android 14+: captura da janela identificada, excluindo sobreposições da imagem. Android 11–13: captura do display, máscara e recorte pelos limites do aplicativo.
- Removida a leitura indiscriminada do display quando não se identifica a janela alvo; limites inválidos são descartados.
- Revalidação de pacote, permissão/preferências, janela, limites e geração antes de entregar OCR assíncrono; expiração de cinco segundos e invalidação por troca de janela ou leitura acessível concluída.
- Ordenação espacial das linhas; filtro de cabeçalho usa posição no display, evitando apagar tarifa no início de um popup pequeno.
- Normalização de `R $`; reunião dos tokens de bônus também na validação OCR; suporte consistente a separador de milhares; segunda moeda malformada não é ignorada.
- O cálculo próprio permanece `tarifa / km totais` e `tarifa × 60 / minutos totais`. Total explícito não recebe embarque novamente. Sem duração, não se estima um ganho/hora fictício.

## Verificação e limites

Foram acrescentados casos de regressão para preço no topo de popup, moeda espaçada, bônus fragmentado, milhares e moeda ambígua. Um teste Android verifica conversão de bitmap de hardware preservando pixels e permitindo máscaras. Esse teste exige aparelho ou emulador.

Nenhum dispositivo conectado foi encontrado via `adb devices -l`; não há emulador configurado no ambiente inspecionado. Validação de OCR em capturas reais, frequência, sobreposições e layouts atuais de Uber/99 permanece pendente. Os testes de interpretação usam textos controlados, não demonstram precisão sobre imagens reais.

Para validar no celular: habilitar acessibilidade e OCR no Copiloto; manter Uber/99 selecionados; abrir uma oferta sem aceitá-la; conferir preço, distância e duração contra o cartão. Em caso de falha, o diagnóstico distingue captura, preparação da imagem e interpretação. Testar também mudança de aplicativo durante o reconhecimento e popup sobre outro aplicativo.

### Resultado local

- `testDebugUnitTest`: 200 testes, zero falhas/erros após corrigir a normalização literal de `$` identificada pelo novo teste.
- `assembleDebug`: APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 do APK: `2ed4e007b61154ac128cd2b40535634d6d159e143b23e5dc4a09174ce0b05ef8`.
- `compileDebugAndroidTestKotlin`: teste de conversão compilado; não executado sem dispositivo.
- `lintDebug`: concluído, zero erros, 63 avisos e três sugestões. O projeto não está livre de avisos.
