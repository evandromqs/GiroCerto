# Giro Certo 1.0.9

- Poppins Medium, margens e espaçamentos padronizados, parágrafos justificados e transições suaves entre telas.
- Cartão do copiloto remodelado, com botão de minimizar, conteúdo rolável e cores de resultado.
- Atualizações baixadas na pasta do Giro Certo, verificadas e abertas diretamente no instalador do Android.
- Proteção de registros financeiros danificados, rascunhos persistentes, navegação por data corrigida e validação dos campos.
- Leitura de ofertas mais conservadora para medidas repetidas, valores ambíguos, notas e números com milhar; descarte de OCR desatualizado.
- Fechamento semanal com cobrança única do contrato e backup completo com restauração validada.
- Carregamento assíncrono, histórico otimizado, ações preservadas ao navegar e testes em Android 11 e Android 15.

Fonte das notas publicadas e do histórico interno: `changelog.json`.

## Cobertura da auditoria anterior

- Achados 1–9: proteção de dados inválidos e correções conservadoras do interpretador/OCR.
- Achados 10–14: recuperação/cancelamento de atualização, arquivo ausente, voltar, permissão pendente e mudança de dia.
- Achados 15–18: validação contextual, recorte dos cartões, seleção por linha e mensagens de voz coerentes.
- Achados 19–25: carregamento em segundo plano, lista preguiçosa, rascunhos fora do Bundle, coleta por ciclo de vida, ViewModels, texto sem largura implícita e filtros fora da UI.
- Achados 26–27: contrato semanal debitado uma vez e exportação/restauração dos registros financeiros.
- Achado 28: testes automatizados em API 30 e 35 adicionados ao pipeline. Ofertas reais de cada plataforma ainda precisam de validação em aparelho físico.
- Achado 29: changelog compartilhado entre app e release.
- Achado 30: R8 e redução de recursos ativados. Baseline Profile específico e medição de fluidez em aparelho físico ficam pendentes; não se afirma ganho de desempenho medido.

O fluxo de instalação abre o instalador do Android diretamente. A autorização para instalar por esta origem, quando exigida, e a confirmação do sistema permanecem obrigatórias.
