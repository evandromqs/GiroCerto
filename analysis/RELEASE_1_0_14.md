# Release 1.0.14 — Diagnóstico e Solução de OCR e Exibição em Tempo Real

## 1. Contexto e Problema Reportado
Na versão 1.0.13, o copiloto exibia erros na tela ("OCR: janela alvo indisponível ou encoberta", "OCR: captura descartada; janela mudou" ou "0 textos; tela sem texto acessível") e o cálculo em tempo real das ofertas dos apps não aparecia no card flutuante.

## 2. Análise via Engenharia Reversa (REA) no Gigu (v1.2.96)
1. **Captura Global do Display**:
   - Gigu (`defpackage.el0`, `CdgServices.w`, `AccessibilityService.takeScreenshot`) captura sempre o display principal (`Display.DEFAULT_DISPLAY`) em Android 11+ (API 30+).
   - O Gigu nunca utiliza `takeScreenshotOfWindow` em API 34+, pois `takeScreenshotOfWindow` apenas captura a janela de foco ou raiz e omite janelas modais, bottom sheets flutuantes e diálogos dos apps de entrega/corrida (99, Uber).
2. **Entrega Imediata sem Dupla Confirmação**:
   - No Gigu, assim que o ML Kit retorna as linhas de texto da imagem e a regra Lua identifica a tarifa e trajeto, o cálculo é imediatamente computado e postado no `Handler` para anexar ou atualizar o cartão via `WindowManager.addView` / `updateViewLayout`.
   - O Giro Certo possuía um requisito artificial de "duas leituras idênticas consecutivas". A primeira leitura era descartada intencionalmente ("confirmando que a oferta continua na tela"), e o temporizador de contagem regressiva da Uber/99 invalidava a geração do leitor antes da segunda tentativa ocorrer.
3. **Filtro de Cabeçalho**:
   - O Gigu descarta apenas valores monetários situados nos primeiros 7,5% superiores da imagem (`bitmap.getHeight() * 0.075f`), preservando ofertas e bônus no restante da tela.

## 3. Correções Aplicadas na Versão 1.0.14
1. **Alinhamento do OCR com Display.DEFAULT_DISPLAY**:
   - `OfferOcrReader` agora padroniza a captura em `service.takeScreenshot(Display.DEFAULT_DISPLAY, ...)`.
   - Se `window.root` for nulo no momento da animação da janela do app terceiro, o leitor utiliza os limites da tela (`displayBounds`) como fallback seguro, sem abortar a captura.
2. **Eliminação da Latência de Confirmação**:
   - Assim que o ML Kit detecta um resultado completo (`parsed.isComplete`) que cumpre o contexto de oferta, a análise é disparada imediatamente para `acceptParsedOffer`, acionando o cálculo e abrindo o `Mode.OFFER_CARD`.
3. **Ajuste na Tolerância de Valores e Bônus**:
   - Ofertas com bônus inclusos (ex: 99Food `R$ 11,08` com `Inclui R$ 2,00 Bônus`) ou valores proeminentes são aceitas com precisão, selecionando o valor integral da corrida.
   - Remoção de rejeições precipitadas por contagem de tokens de moeda quando há uma tarifa inequívoca.
4. **Foco Estrito nos 3 Apps Principais (99, Uber e iFood)**:
   - Configurações do Copiloto e o seletor de plataformas do diário de trabalho restritos exclusivamente a 99, Uber e iFood.
5. **Intervalo de Re-tentativa**:
   - Reduzido o throttle de 2.000ms para 500ms, garantindo reação rápida antes que o tempo de aceite da oferta expire.
