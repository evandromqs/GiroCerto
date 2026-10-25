# Auditoria do Giro Certo — base para a próxima atualização

Data: 25/10/2026. Base: versão 1.0.8, commit `1201a2c`.
Escopo: módulo Android `app`, Compose, componentes, navegação, diário, persistência, leitura de ofertas, OCR e atualizador. Não há backend remoto de negócios: os dados ficam no aparelho; GitHub distribui as atualizações.

Método: [Jetpack Compose Audit 4.3.2](https://github.com/hamen/compose_skill/blob/main/skills/jetpack-compose-audit/SKILL.md), complementada pela revisão das regras do produto. A skill foi consultada em `app/build/audit-skill`, sem instalação global. UI/UX visual, regras financeiras e serviços Android são cobertura adicional, fora da pontuação Compose. Não se impôs uma mudança da paleta ou do estilo solicitado pelo usuário.

Esta é uma lista dos achados desta revisão, não garantia de ausência de outros defeitos. **Diagnóstico histórico da versão 1.0.8, anterior às correções.** A implementação da 1.0.9 está registrada em `analysis/RELEASE_1_0_9.md`; as evidências e a pontuação abaixo descrevem a base anterior. P1 = priorizar antes da próxima release; P2 = próxima rodada; P3 = acabamento/manutenção. “Reproduzido” significa execução do interpretador compilado; “código” significa fluxo confirmado por inspeção; “risco” exige medição ou teste adicional no dispositivo.

## Pontuação técnica Compose

**62/100 — necessita melhorias. Confiança média.** Esta nota é uma avaliação técnica da amostra inspecionada, não uma medição de satisfação, segurança ou precisão do OCR. Não havia relatório comparável anterior para calcular evolução.

| Categoria | Nota | Peso | Fundamentação |
|---|---:|---:|---|
| Desempenho | 6/10 | 35% | Lista crescente não virtualizada e transformações no corpo da tela; itens 20 e 25. |
| Gestão de estado | 6/10 | 25% | Data de destino, relógio, Bundle crescente e coleta sem ciclo de vida; itens 02, 14, 21 e 22. |
| Efeitos | 6/10 | 20% | Ação de sobreposição cancelada permanece pendente; gravações vinculadas à composição dificultam controle; itens 13 e 23. |
| API dos componentes | 7/10 | 20% | Componentes aceitam Modifier e callbacks, mas AppText decide largura implicitamente e MoneyField não expõe erros; itens 15 e 24. |

Cálculo: `(6×0,35 + 6×0,25 + 6×0,20 + 7×0,20) × 10 = 62`. As referências oficiais de cada dedução estão nos itens correspondentes. Achados financeiros, OCR, desenho visual e serviços fora da composição não foram descontados novamente na nota Compose.

### Diagnóstico do compilador

- Compilação release concluída com sucesso nesta auditoria, em 4m52s, usando o script de relatórios da skill inspecionado antes da execução. Nenhum arquivo de configuração do produto foi alterado.
- `Compiler diagnostics used: yes`. Kotlin 2.3.20; Strong Skipping confirmado ativo em `app/build/compose_audit/release/app-module.json`.
- Módulo: 213 composables skippable / 323 restartable = **65,94%**. O agregado inclui lambdas; não deve ser interpretado isoladamente como defeito.
- Funções nomeadas restartable, excluindo `isLambda=1`: **52/52 = 100%** em `app/build/compose_audit/app-composables.csv`.
- Quinze classes do módulo classificadas como instáveis; apenas **WorkDay** aparece como tipo de projeto instável em parâmetros nomeados deste relatório, em `DayEditor`; `LocalDate` é o outro tipo instável de parâmetro. `ElectricProfile` e `PlatformWork` são estáveis. Não penalizar a contagem bruta de repositórios/serviços como se fossem parâmetros visuais.
- Tabela de teto aplicada: **SSM-on**. `LocalDate.parse(selectedDate)` recria a instância passada ao editor em `WorkDashboard.kt:92,106`; com comparação por identidade para parâmetros instáveis, esse é um ponto localizado de recriação. Teto conservador **8/10**; nota qualitativa 6, nota aplicada **6**. Não há evidência de churn disseminado nem de `equals()` caro/quebrado em componentes compartilhados.
- Nenhuma falha de animação foi confirmada na amostra. Não há Paging nem destinos KMP neste módulo. Não se inferiu recomposição problemática somente pela presença de lambdas.
- References: https://developer.android.com/develop/ui/compose/performance/stability/strongskipping ; https://developer.android.com/develop/ui/compose/performance/stability/diagnose

## Achados prioritários

### 01 — P1 · Diário danificado pode ser sobrescrito sem aviso · Código

`WorkRepository.readDays/readProfile` converte erros e dados inválidos em ausência. Um dia com JSON danificado some da tela; gravar novamente a mesma data substitui a chave original. A proteção adicionada ao histórico de ofertas não cobre o diário financeiro.

Evidência: [WorkRepository.kt:38](app/src/main/java/com/girocerto/data/repository/WorkRepository.kt#L38), leitura até 55 e gravação em 67–74.
Correção: estado explícito de erro, preservar/quarentenar conteúdo original, bloquear substituição inadvertida e oferecer recuperação. Aceite: corromper uma data, reiniciar, verificar aviso e impedir perda do original ao tentar salvar.
References: https://developer.android.com/topic/architecture/data-layer

### 02 — P1 · “Registrar/Editar meu dia” pode abrir uma data antiga · Código

Selecionar ontem no diário, voltar ao início e tocar “Editar meu dia” mantém ontem selecionado. A navegação preserva o estado corretamente, mas a ação do painel não transmite a intenção de abrir hoje.

Evidência: [WorkDashboard.kt:90](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L90), [AppNavigation.kt:119](app/src/main/java/com/girocerto/navigation/AppNavigation.kt#L119).
Correção: passar data/intenção explícita sem apagar os demais rascunhos. Aceite: testar ontem → início → registrar hoje e conferir a data persistida.
References: https://developer.android.com/develop/ui/compose/state

### 03 — P1 · OCR pode entregar uma oferta já substituída · Risco com caminho confirmado

O reconhecimento é assíncrono. Antes de entregar o resultado, verifica pacote, ID e limites da janela, mas não a versão do conteúdo. A oferta A pode desaparecer ou ser substituída por B na mesma janela, enquanto A ainda é processada, e gerar cartão/voz/histórico atrasados.

Evidência: [OfferOcrReader.kt:64](app/src/main/java/com/girocerto/monitoring/OfferOcrReader.kt#L64), callback em 143–151; [GiroCertoAccessibilityService.kt:251](app/src/main/java/com/girocerto/monitoring/GiroCertoAccessibilityService.kt#L251).
Correção: geração da leitura por pacote, validade temporal e descarte de respostas superadas, com revalidação do contexto. Aceite: simular OCR lento e trocar A por B sem trocar ID da janela; somente B pode ser publicada.
References: https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#getId()

### 04 — P1 · Segmentos iguais e nós pai/filho ainda distorcem medidas · Reproduzido

`distinct()` elimina textos iguais mesmo quando representam duas etapas reais. Duas linhas `2 km` viram 2 km, em vez de 4 km quando são embarque e viagem. A situação inversa, um texto agregado `5 min 20 min` junto com filhos `5 min` e `20 min`, produz 20 minutos em vez de 25. A identificação por texto não distingue duplicação da árvore de repetição legítima do trajeto.

Evidência: [AccessibilityOfferReader.kt:79](app/src/main/java/com/girocerto/monitoring/AccessibilityOfferReader.kt#L79), consolidação em 217–247; coleta de pai e filhos no serviço em 292–299.
Correção: preservar identidade/posição/contexto dos segmentos, priorizar total inequívoco e rejeitar casos ambíguos. Aceite: testes com etapas iguais, pai/filhos e ordem alternativa dos nós.
References: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo

## Demais falhas de lógica e atualização

### 05 — P2 · Totais equivalentes podem impedir a análise · Reproduzido

`Total 10 km` e `Distância total: 10 km` deixam dois totais; o leitor retorna distância ausente. Normalizar valores e distinguir totais equivalentes de conflitantes.
Evidência: [AccessibilityOfferReader.kt:217](app/src/main/java/com/girocerto/monitoring/AccessibilityOfferReader.kt#L217). Aceite: dois totais iguais devem produzir 10 km; dois diferentes devem permanecer inconclusivos.
References: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo

### 06 — P2 · Avaliações abaixo de quatro estrelas são ignoradas · Reproduzido

`★ 3,90` retorna nota ausente: regex e validação aceitam apenas 4 a 5. Assim, o aviso de nota abaixo da preferência não dispara para esse caso. Aceitar toda a faixa válida, distinguindo estrelas de dinheiro.
Evidência: [AccessibilityOfferReader.kt:47](app/src/main/java/com/girocerto/monitoring/AccessibilityOfferReader.kt#L47), validações em 164–176; serviço em 247–249. Aceite: notas 3,90, 4,95 e 5,00 com e sem símbolo, sem confundir tarifa.
References: https://developer.android.com/training/testing/local-tests

### 07 — P2 · Moeda com milhar é truncada · Reproduzido

`R$ 1.234,56` retorna tarifa 1,23. O regex aceita um prefixo do número. Interpretar o valor integral ou rejeitar o formato sem gerar análise. Também há diferença em relação às restrições do caminho OCR.
Evidência: [AccessibilityOfferReader.kt:14](app/src/main/java/com/girocerto/monitoring/AccessibilityOfferReader.kt#L14). Aceite: moedas com milhar, espaços e separadores mistos, incluindo entradas malformadas.
References: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/NumberFormat.html

### 08 — P2 · Valores sem símbolo não passam pela mesma proteção contra ambiguidade · Reproduzido

Com linhas `20,00`, `30,00` e `10 km`, o fallback escolhe 20 sem resolver qual quantia é a tarifa. Aplicar ao fallback a mesma política conservadora de múltiplos valores; associar o valor ao cartão/contexto correto.
Evidência: [AccessibilityOfferReader.kt:198](app/src/main/java/com/girocerto/monitoring/AccessibilityOfferReader.kt#L198). Aceite: duas quantias isoladas devem produzir leitura inconclusiva, salvo associação contextual inequívoca.
References: https://developer.android.com/reference/android/view/accessibility/AccessibilityNodeInfo

### 09 — P2 · Trajetos com três etapas podem perder distância · Reproduzido

Com `2 km`, `5 km`, `8 km`, a heurística usa mínimo e máximo, resultando em 10 km. Se as linhas forem três etapas, o trajeto é de 15 km. Não somar cegamente: primeiro distinguir etapas de totais, ou rejeitar a ambiguidade.
Evidência: [AccessibilityOfferReader.kt:222](app/src/main/java/com/girocerto/monitoring/AccessibilityOfferReader.kt#L222). Aceite: cenários com múltiplas paradas e com total já incluído.
References: https://developer.android.com/training/testing/local-tests

### 10 — P2 · Download pausado bloqueia consulta de outra versão · Código

O monitor aguarda indefinidamente e `check()` retorna enquanto ele estiver ativo. Não há ação de cancelar/substituir o download antigo. Oferecer cancelamento e permitir consulta independente, sem iniciar downloads concorrentes inadvertidamente.
Evidência: [AppUpdateManager.kt:45](app/src/main/java/com/girocerto/update/AppUpdateManager.kt#L45), laço em 108–139; [AppUpdatePrompt.kt:42](app/src/main/java/com/girocerto/update/AppUpdatePrompt.kt#L42). Aceite: pausar download, publicar manifesto mais novo e conseguir consultá-lo/cancelar o anterior.
References: https://developer.android.com/reference/android/app/DownloadManager

### 11 — P2 · APK apagado continua marcado como pronto · Código

Após a verificação, apagar o APK em Downloads mantém `READY` na sessão. `download()` rejeita nova tentativa e consultar a mesma release mantém esse estado. Revalidar existência/acesso antes de abrir e permitir baixar novamente.
Evidência: [AppUpdateManager.kt:56](app/src/main/java/com/girocerto/update/AppUpdateManager.kt#L56), 82 e 134–137. Aceite: baixar/verificar, apagar externamente, retornar ao app e baixar novamente sem reiniciar o processo.
References: https://developer.android.com/reference/android/app/DownloadManager

## UI, UX e estado

### 12 — P2 · Voltar ignora a tela anterior e a etapa do quiz · Código

O botão do sistema sempre retorna ao início. Ajustes → Cartão e voz → Voltar não retorna a Ajustes; no quiz, não retrocede a etapa. Definir pilha de navegação e prioridade do fluxo interno.
Evidência: [AppNavigation.kt:80](app/src/main/java/com/girocerto/navigation/AppNavigation.kt#L80), [ElectricProfileScreen.kt:124](app/src/main/java/com/girocerto/ui/goals/ElectricProfileScreen.kt#L124). Aceite: testar botão do sistema e gesto de voltar em cada rota/etapa.
References: https://developer.android.com/guide/navigation

### 13 — P2 · Cancelar pedido de sobreposição não cancela a análise pendente · Código

“Agora não” fecha a explicação, mas mantém `pendingOverlayAnalysis`. Conceder permissão por outro caminho e retomar o app pode mostrar a análise cancelada. Limpar a intenção em cancelamento/dismiss, preservando-a somente no pedido efetivo de permissão.
Evidência: [AppNavigation.kt:62](app/src/main/java/com/girocerto/navigation/AppNavigation.kt#L62), 69–74, 145 e 172.
Aceite: cancelar, conceder permissão fora do fluxo, retomar e não exibir cartão antigo.
References: https://developer.android.com/develop/ui/compose/side-effects

### 14 — P2 · “Hoje” não acompanha explicitamente a virada de data · Risco

O painel usa `LocalDate.now()` durante composição; não observa mudança de dia. Pode continuar mostrando a data anterior até ocorrer recomposição incidental. Introduzir relógio/data observável na retomada e à meia-noite.
Evidência: [WorkDashboard.kt:40](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L40), 55. Aceite: relógio controlado atravessando meia-noite com app aberto e após retomada.
References: https://developer.android.com/develop/ui/compose/state

### 15 — P2 · Formulário não identifica o campo inválido · Código

O quiz desabilita Continuar sem erro contextual. Dias usa teclado decimal, mas aceita apenas inteiro; `1.234,56` colado é rejeitado pelo parser dos campos. Dar mensagem por campo, teclado apropriado e normalização explícita de números brasileiros.
Evidência: [ElectricProfileScreen.kt:24](app/src/main/java/com/girocerto/ui/goals/ElectricProfileScreen.kt#L24), MoneyField 27–29 e validação 103–122. Aceite: 25 horas, 8 dias, `5,0` dias e valor com milhar devem ter comportamento e mensagem claros.
References: https://developer.android.com/develop/ui/compose/text/user-input

### 16 — P3 · Acabamento quadrado fora de cartões arredondados · Visual confirmado

O gradiente de `TactileCard` é desenhado como retângulo depois do conteúdo, fora do recorte interno. Cantos cinza aparecem na captura do cartão Aparência, tema escuro. Recortar somente a decoração à forma do cartão, preservando sombra.
Evidência: [TactileCard.kt:30](app/src/main/java/com/girocerto/ui/components/TactileCard.kt#L30); captura CI em `app/build/ci-1.0.8-final/outputs/managed_device_android_test_additional_output/debug/pixel2api30/settings-DARK.png`.
Aceite: comparação visual em ambos os temas, incluindo bordas/sombra.
References: https://developer.android.com/develop/ui/compose/graphics/draw/modifiers

### 17 — P3 · Seleção de app exige tocar apenas na caixa · Código / usabilidade

Nome e ícone não alternam a seleção. Tornar a linha toda selecionável, com semântica combinada e ação única, facilita toque e leitor de tela.
Evidência: [WorkDashboard.kt:222](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L222). Aceite: tocar nome/ícone/caixa alterna uma única vez; TalkBack anuncia nome e estado juntos.
References: https://developer.android.com/develop/ui/compose/accessibility/semantics

### 18 — P3 · Voz e cartão usam vocabulários diferentes · Código / consistência

A voz anuncia “sinal verde/amarelo/vermelho”, enquanto o visual adotou a paleta azul-marinho e decisões textuais. Usar a mesma decisão textual na voz e no cartão, preservando a informação quando não houver tempo detectado.
Evidência: [VoiceAnnouncer.kt:64](app/src/main/java/com/girocerto/monitoring/VoiceAnnouncer.kt#L64). Aceite: conferir os quatro estados e leitura sem duração.
References: https://developer.android.com/guide/topics/ui/accessibility/principles

## Arquitetura, desempenho e manutenção

### 19 — P2 · Inicialização lê históricos na thread principal · Código / risco de lentidão

`Application.onCreate` instancia os repositórios, que leem e interpretam os históricos sincronicamente. O custo cresce com os registros; recuperar histórico também usa chamada síncrona. Mover leitura para IO e expor carregamento/erro, sem exibir saldo zero como se carregado.
Evidência: [GiroCertoApp.kt:29](app/src/main/java/com/girocerto/app/GiroCertoApp.kt#L29), [RideRepository.kt:43](app/src/main/java/com/girocerto/data/repository/RideRepository.kt#L43), [WorkRepository.kt:34](app/src/main/java/com/girocerto/data/repository/WorkRepository.kt#L34). Aceite: medir inicialização com histórico grande e StrictMode; nenhum tempo de travamento foi medido nesta revisão.
References: https://developer.android.com/topic/performance/vitals/anr

### 20 — P2 · Diário renderiza todo o histórico de uma vez · Código / risco de escala

Todos os dias são criados em `Column.verticalScroll`. Migrar a lista crescente para `LazyColumn`, com identidade por data; listas fixas de seis plataformas não precisam de paginação.
Evidência: [WorkDashboard.kt:94](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L94), 110–115. Aceite: testar centenas de fechamentos, rolagem e seleção preservada.
References: https://developer.android.com/develop/ui/compose/lists

### 21 — P2 · Rascunhos acumulam estado salvo sem limite · Risco

Cada data visitada mantém vetores e campos no `SaveableStateHolder`; não há descarte/limite, e campos numéricos não limitam comprimento. Isso pode aumentar o Bundle salvo excessivamente. Não houve reprodução de `TransactionTooLargeException`.
Evidência: [WorkDashboard.kt:91](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L91), 105 e 123–127. Correção: persistir rascunhos extensos fora do Bundle e guardar nele apenas identificadores; limitar entradas. Aceite: muitas datas visitadas, rotação e recriação do processo.
References: https://developer.android.com/develop/ui/compose/state-saving

### 22 — P2 · Coleta de estado não acompanha o ciclo de vida Android · Código / risco

Telas usam `collectAsState`, inclusive histórico e atualizador. Enquanto a composição permanecer viva em segundo plano, as coletas continuam. Usar `collectAsStateWithLifecycle` onde for estado de tela; o serviço de leitura deve continuar com ciclo de vida próprio.
Evidência: [WorkDashboard.kt:37](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L37), [HistoryScreen.kt:75](app/src/main/java/com/girocerto/ui/history/HistoryScreen.kt#L75), [AppUpdatePrompt.kt:18](app/src/main/java/com/girocerto/update/AppUpdatePrompt.kt#L18). Aceite: observar coletores ao pausar/retomar sem interromper o copiloto autorizado.
References: https://developer.android.com/develop/ui/compose/state

### 23 — P2 · Telas concentram regras, dependências globais e gravação · Dívida arquitetural

Telas montam modelos financeiros e acessam diretamente `GiroCertoApp.instance`; gravações ficam no escopo da composição. Isso dificulta testes isolados e comportamento previsível ao navegar durante operações. Não é prova de perda de gravação. Extrair estado/eventos para ViewModel/controlador com repositórios injetados; não é necessário fragmentar o app em inúmeros módulos.
Evidência: [WorkDashboard.kt:121](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L121), 133–138 e 181–185; [ElectricProfileScreen.kt:42](app/src/main/java/com/girocerto/ui/goals/ElectricProfileScreen.kt#L42), 112–119.
Aceite: testar gravação com navegação/recriação e regras sem inicializar Application inteira.
References: https://developer.android.com/topic/architecture/recommendations

### 24 — P3 · AppText impõe largura conforme tipografia · Risco de reutilização

O componente aplica `fillMaxWidth` por tamanho do estilo, ignorando o parâmetro explícito `fontSize` nessa decisão. A aparência tipográfica altera implicitamente o layout. Deixar dimensões no chamador; não foi identificado um recorte específico de texto causado por isso nesta rodada.
Evidência: [AppText.kt:23](app/src/main/java/com/girocerto/ui/components/AppText.kt#L23). Aceite: títulos em Row, cards e diálogos com fonte ampliada.
References: https://developer.android.com/develop/ui/compose/modifiers

### 25 — P3 · Filtros do histórico refazem transformações na composição · Risco de escala

Mapeamento, distinct, ordenação de plataformas e filtragem de registros ocorrem no corpo da tela. Com histórico grande, qualquer recomposição pertinente repete trabalho. Produzir estado filtrado no controlador, com entradas explícitas e dispatcher adequado.
Evidência: [HistoryScreen.kt:79](app/src/main/java/com/girocerto/ui/history/HistoryScreen.kt#L79), 88–90. Aceite: medir custo com milhares de ofertas; não deduzir travamento a partir apenas da presença de `filter`.
References: https://developer.android.com/develop/ui/compose/performance/bestpractices

## Melhorias de produto e cobertura — não são bugs já demonstrados

### 26 — P2 · Falta fechamento semanal do contrato

O rateio diário é explicado corretamente, mas somar dias registrados não garante descontar exatamente o aluguel semanal se a pessoa trabalhar quantidade diferente da planejada. Ex.: contrato R$200/5 dias → R$40/dia; três dias abatem R$120 e sete abatem R$280. Criar reconciliação semanal do custo real, sem reescrever dias históricos automaticamente. Definir início da semana e tratamento de alterações do contrato.
Evidência: [WorkLedger.kt:21](app/src/main/java/com/girocerto/domain/model/WorkLedger.kt#L21), [WorkDashboard.kt:179](app/src/main/java/com/girocerto/ui/home/WorkDashboard.kt#L179). Aceite: semanas de três, cinco e sete dias com cobrança fixa única.
References: regra do contrato informada pelo usuário e fórmulas locais; https://developer.android.com/training/testing/local-tests para validar a implementação futura.

### 27 — P2 · Exportação não é um backup restaurável completo

CSV exporta receitas por plataforma e totais, mas omite km/horas por app, perfil, metas e detalhes dos custos históricos. Não há importação. Oferecer backup versionado completo com restauração validada, além do CSV de relatório e do backup automático Android existente.
Evidência: [WorkExport.kt:8](app/src/main/java/com/girocerto/data/repository/WorkExport.kt#L8), `DataExportSection.kt`. Aceite: exportar/restaurar e comparar perfil, plataformas e cada registro; tratar versões e dados inválidos.
References: https://developer.android.com/identity/data/autobackup

### 28 — P2 · Matriz Android não cobre caminhos recentes de OCR e notificações

Há testes instrumentados reais, mas o dispositivo gerenciado é API 30. O caminho de captura por janela API 34+, permissão de notificação API 33+ e comportamento de aparelhos de fabricantes precisam de cobertura adicional. Não afirmar que os testes existentes garantem reconhecimento de ofertas reais.
Evidência: [build.gradle.kts:26](app/build.gradle.kts#L26), [OfferOcrReader.kt:123](app/src/main/java/com/girocerto/monitoring/OfferOcrReader.kt#L123). Aceite: incluir API recente na CI e roteiro físico para Uber/99/iFood/Keeta, com app e Android identificados.
References: https://developer.android.com/training/testing/instrumented-tests

### 29 — P3 · Changelog tem fontes duplicadas

Histórico dentro do app e notas Markdown da release são mantidos separadamente. Podem divergir em próximas publicações. Gerar ambos a partir de dados únicos e validar correspondência com `version.properties`.
Evidência: [VersionHistory.kt:13](app/src/main/java/com/girocerto/ui/settings/VersionHistory.kt#L13), `analysis/RELEASE_1_0_8.md`, `version.properties`. Aceite: falhar validação quando versão publicada não existir no histórico.
References: https://developer.android.com/studio/publish/versioning

### 30 — P3 · Release ainda não usa otimização de código

`isMinifyEnabled=false`; avaliar R8 e perfil de inicialização para tamanho/desempenho. Não habilitar sem validar ML Kit, persistência e assinatura; ausência de R8 não é falha de segurança demonstrada.
Evidência: [build.gradle.kts:42](app/build.gradle.kts#L42). Aceite: comparar APK e startup e executar testes na variante otimizada.
References: https://developer.android.com/topic/performance/app-optimization/enable-app-optimization

## Ordem recomendada de execução

1. **Proteger o diário financeiro** — item 01, `WorkRepository.kt:38`. https://developer.android.com/topic/architecture/data-layer
2. **Abrir hoje explicitamente pelo painel** — item 02, `AppNavigation.kt:119` e `WorkDashboard.kt:90`. https://developer.android.com/develop/ui/compose/state
3. **Descartar OCR obsoleto** — item 03, `OfferOcrReader.kt:143`. https://developer.android.com/reference/android/view/accessibility/AccessibilityWindowInfo#getId()
4. Consolidar o interpretador com testes dos itens 04–09; corrigir atualizador 10–11.
5. Corrigir navegação, validação e renderização; evoluir arquitetura com testes preservando comportamento.
6. Planejar reconciliação semanal e backup completo como funcionalidades distintas, sem prometer tudo em uma única correção pequena.

## Evidências, limites e pontos já saudáveis

- Os sete cenários do interpretador foram executados contra classes debug já compiladas, via harness temporário `app/build/ParserAudit.java`. Isso não é teste de captura de tela real. Saídas: tarifa 1,23 para 1.234,56; distância 2 para duas linhas de 2; distância ausente para dois totais iguais; tempo 20 para pai/filhos 5+20; nota ausente para 3,90; primeiro valor 20 entre 20/30; 10 km para três segmentos 2/5/8.
- A release 1.0.8 já tinha CI aprovada com 97 testes unitários e oito instrumentados. Esses resultados são históricos, não execução nova de toda a suíte nesta auditoria.
- Preservação de rascunhos entre datas, proteção do histórico de ofertas, assinatura/hash, adiamento de atualização e regras de backup já existem. Não foram reapresentados como ausentes.
- Há Mutex e gravação em IO no diário, AtomicFile no histórico de ofertas, listas lazy com identidade no histórico, callbacks de ação e limpeza de observadores. Essas decisões contam positivamente.
- Um único módulo e um service locator não são erros por si só. As sugestões arquiteturais se vinculam a fluxos e custos concretos.
- Não há configuração explícita `windowSplashScreenAnimatedIcon` nos temas inspecionados; nenhum defeito específico de splash foi confirmado.
- Sem sessão nova em telefone físico, medição de bateria, profiler de quadros, teste de TalkBack ou garantia sobre comportamento do Play Protect.
- Revisão visual aprofundada e testes de foco/acessibilidade são próximos passos úteis; uma auditoria Material 3 pode complementar, respeitando o design skeumórfico pedido.
- Proposta de changelog em `analysis/CHANGELOG_PROPOSTO_1_0_9.md`. A versão 1.0.9 é sugestão, não release criada.
