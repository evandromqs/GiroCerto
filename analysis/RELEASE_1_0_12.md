# Release 1.0.12 — Sistema de Design Neobrutalista e Reconstrução Visual

**Versão**: 1.0.12 (versionCode 12)  
**Data**: 31 de Outubro de 2026  

---

## 🎨 Principais Mudanças e Entregas

### 1. Sistema de Design Neobrutalista e Tokens de Cor (R1)
- Introdução de paleta neobrutalista tátil com alto contraste, pensada para legibilidade sob a luz solar por motoristas em trânsito.
- Cor primária institucional: Dark Blue / Marinho (`#102746`).
- Superfície e fundo em tema claro: Cinza Claro (`#F0F3F8`).
- Contrastes puros de texto e ícones: Branco (`#FFFFFF`) e Preto (`#000000`).
- Suporte nativo à alternância global entre tema claro e tema escuro.

### 2. Tipografia Padronizada Roboto (R1)
- Padronização integral da família tipográfica Roboto em todas as escalas do Compose (`Theme.kt` e `Type.kt`).
- Hierarquia tipográfica consistente com títulos bem definidos e excelente leiturabilidade.

### 3. Escala Geométrica e Cantos Arredondados (R1, R2)
- Padronização dos cantos arredondados nos componentes da interface:
  - `CornerCard = 12dp` para containers e cards táteis neobrutalistas.
  - `CornerButton = 10dp` para botões de ação interativos.
  - `CornerSmall = 8dp` para badges do semáforo, chips e elementos compactos.

### 4. Reconstrução e Alinhamento de Todas as Telas (R2)
- Reconstrução completa mantendo conformidade com as primitivas táteis (`TactileCard`, `TactileButton`, `MetricCard`, `TrafficLightBadge`):
  - **WorkDashboard** e **HomeScreen**: Painel de trabalho com indicadores de faturamento, corridas e metas.
  - **GoalsScreen** e **ElectricProfileScreen**: Gestão de metas financeiras e custos de moto elétrica/locação.
  - **HistoryScreen**: Listagem otimizada de corridas com filtros e ações táteis.
  - **CopilotScreen**: Status dos serviços, permissões, diagnósticos e simulação em tempo real.
  - **SettingsScreen** e **SimpleSettingsScreen**: Configuração de veículo, custos operacionais e backup.

### 5. Modal Flutuante Compacto com Gravidade nos Cantos (R4)
- Introdução do enum `OverlayCorner` (`TOP_LEFT`, `TOP_RIGHT`, `BOTTOM_LEFT`, `BOTTOM_RIGHT`).
- Contratos de física de encaixe e posicionamento customizável para não obstruir mapas de navegação ou controles dos apps de corrida.
- Botões de ação otimizados com espaçamento profissional e alvos de toque generosos (mínimo 48dp).

### 6. Validação e Qualidade Técnica
- Suíte automatizada com **184 testes unitários e testes E2E aprovados** (Tiers 1 a 4).
- Testes adversariais de componentes e validação geométrica de snap dos cantos.
