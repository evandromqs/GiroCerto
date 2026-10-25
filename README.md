> Atualização 24/10/2026: [perfil de moto elétrica e diário de ganhos](analysis/MOTO_ELETRICA_DIARIO.md). Quatro abas, quiz de custos/metas e fechamento por aplicativo, separado das ofertas analisadas. Veja também a [investigação REA](analysis/REA_GIGU.md). A documentação abaixo descreve a base inicial.

# Giro Certo — Copiloto Financeiro para Motoristas de Aplicativo

O **Giro Certo** é um aplicativo Android nativo desenvolvido para auxiliar motoristas de aplicativo a avaliarem com rapidez, precisão e transparência a viabilidade financeira de corridas ofertadas.

O aplicativo calcula custos operacionais reais (combustível, manutenção por quilômetro e custos fixos), o ganho por quilômetro (R$/km), o ganho por hora (R$/h) e o lucro líquido estimado, classificando cada oferta em um semáforo de decisão objetivo (**Verde**, **Amarelo**, **Vermelho**).

O aplicativo funciona **100% offline**, sem necessidade de cadastro, sem backend, sem assinaturas e sem dependência de APIs externas pagas.

---

## 🚀 Tecnologias Utilizadas

- **Linguagem**: Kotlin 2.3.20 (JVM Toolchain 17)
- **Interface**: Jetpack Compose com Material 3 (paleta de alto contraste para motoristas)
- **Arquitetura**: Módulo único (`app`), separação de responsabilidades em camadas (`domain`, `data`, `ui`, `overlay`, `monitoring`)
- **Persistência**: `SharedPreferences` para preferências e armazenamento local persistente em JSON no `filesDir`
- **Build System**: Gradle 9.1.0 com Android Gradle Plugin 9.0.1 e Compose Compiler Plugin
- **Testes**: JUnit 4 e Kotlinx Coroutines Test

---

## 📋 Requisitos para Desenvolvimento

- **JDK**: Java 17 (OpenJDK 17 recomendada)
- **Android SDK**: CompileSdk 36, TargetSdk 36, MinSdk 26 (Android 8.0 Oreo ou superior)
- **Android Build Tools**: 36.0.0
- **Android Studio**: Ladybug / Meerkat ou linha de comando via Gradle Wrapper

---

## 📁 Estrutura de Pastas do Projeto

```
GiroCerto/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/girocerto/
│   │   │   │   ├── app/
│   │   │   │   │   ├── MainActivity.kt           # Ponto de entrada da UI Compose
│   │   │   │   │   ├── GiroCertoApp.kt           # Classe Application e Service Locator
│   │   │   │   │   └── theme/                    # Cores, Tipografia e Tema escuro
│   │   │   │   ├── domain/
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── RideOffer.kt          # Modelo de dados da oferta de entrada
│   │   │   │   │   │   ├── VehicleSettings.kt    # Custos e metas configuradas do motorista
│   │   │   │   │   │   ├── RideAnalysis.kt       # Resultado completo com quebra de custos
│   │   │   │   │   │   ├── TrafficLightStatus.kt # Estados do semáforo (Verde, Amarelo, Vermelho)
│   │   │   │   │   │   └── DailySummary.kt       # Resumo consolidado do dia
│   │   │   │   │   └── calculator/
│   │   │   │   │       ├── RideCalculator.kt     # Fórmulas de custos, lucro e margem
│   │   │   │   │       └── TrafficLightEvaluator.kt # Avaliador determinístico por semáforo
│   │   │   │   ├── data/
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── SettingsRepository.kt # Persistência de metas e custos do veículo
│   │   │   │   │       └── RideRepository.kt     # Histórico persistente e métricas diárias
│   │   │   │   ├── navigation/
│   │   │   │   │   ├── AppNavigation.kt          # Scaffold com barra de navegação e telas
│   │   │   │   │   └── Screen.kt                 # Rotas de navegação
│   │   │   │   ├── ui/
│   │   │   │   │   ├── components/               # Badges do semáforo, MetricCards, Dialogs
│   │   │   │   │   ├── home/HomeScreen.kt        # Tela 1: Resumo do dia e últimas análises
│   │   │   │   │   ├── analysis/AnalysisScreen.kt# Tela 2: Entrada manual e cálculo detalhado
│   │   │   │   │   ├── history/HistoryScreen.kt  # Tela 3: Histórico com detalhes e exclusão
│   │   │   │   │   └── settings/SettingsScreen.kt# Tela 4: Ajuste de consumo e metas
│   │   │   │   ├── overlay/
│   │   │   │   │   ├── RideOverlayService.kt     # Serviço de janela flutuante arrastável
│   │   │   │   │   └── OverlayController.kt      # Verificação de permissões e ciclo de vida
│   │   │   │   └── monitoring/
│   │   │   │       ├── OfferReader.kt            # Contrato abstrato e documentação de APIs
│   │   │   │       ├── OfferReaderResult.kt      # Resultado tipado de leitura
│   │   │   │       └── ManualOfferReader.kt      # Leitura e sanitização de entrada manual
│   │   │   └── AndroidManifest.xml
│   │   └── test/java/com/girocerto/
│   │       ├── domain/
│   │       │   ├── RideCalculatorTest.kt         # Testes de limites, zero, divisão e margem
│   │       │   └── TrafficLightEvaluatorTest.kt  # Testes de metas, tolerância e semáforo
│   │       └── monitoring/
│   │           └── ManualOfferReaderTest.kt      # Testes de parsing, vírgula e campos nulos
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── PROJECT_STATUS.md
└── README.md
```

---

## 🛠 Instruções de Execução e Compilação

### Abrir no Android Studio
1. Inicie o Android Studio.
2. Selecione **File > Open...** e aponte para a pasta `c:\Users\evand\OneDrive\Documents\GiroCerto`.
3. Aguarde a sincronização inicial do Gradle.

### Executar Testes Automatizados
No terminal, execute:
```bash
# No Windows PowerShell / CMD:
.\gradlew.bat testDebugUnitTest

# No Linux / macOS:
./gradlew testDebugUnitTest
```

### Gerar APK de Depuração (Build)
```bash
# No Windows PowerShell / CMD:
.\gradlew.bat assembleDebug

# No Linux / macOS:
./gradlew assembleDebug
```

### Localização do APK Gerado
O executável compilado com sucesso está localizado em:
```
c:\Users\evand\OneDrive\Documents\GiroCerto\app\build\outputs\apk\debug\app-debug.apk
```

### Como Instalar no Dispositivo via ADB
Conecte o smartphone Android com Depuração USB ativada e execute:
```bash
adb install -r c:\Users\evand\OneDrive\Documents\GiroCerto\app\build\outputs\apk\debug\app-debug.apk
```

---

## 📦 Dependências Utilizadas e Justificativa

| Dependência | Versão | Justificativa |
|---|---|---|
| `androidx.compose.bom` | 2026.03.01 | Gestão unificada e compatível dos componentes Jetpack Compose |
| `androidx.compose.material3` | BOM | Interface visual moderna com suporte a dark mode de alto contraste |
| `androidx.activity.compose` | 1.13.0 | Integração direta do ciclo de vida da Activity com o Compose |
| `androidx.lifecycle.*` | 2.10.0 | Gerenciamento de ciclo de vida reativo e `StateFlow` |
| `junit` | 4.13.2 | Execução dos testes unitários de lógica financeira e de domínio |
| `kotlinx-coroutines-test` | 1.10.2 | Teste assíncrono determinístico de repositórios e leitores de oferta |

*Nota: Nenhuma biblioteca externa desnecessária foi adicionada. A persistência utiliza recursos nativos seguros do Android (`SharedPreferences` e `org.json`), garantindo inicialização instantânea sem sobrecarga de processamento.*

---

## 🚦 Regras de Negócio e Fórmulas Financeiras

As fórmulas são centralizadas em `RideCalculator`:
1. **Distância Total Considerada**:
   $$\text{Distância Total} = \text{Distância da Corrida} + \text{Distância até o Embarque}$$
2. **Ganho Bruto por Quilômetro**:
   $$\text{R\$/km} = \frac{\text{Valor da Oferta}}{\text{Distância Total}}$$
3. **Ganho Bruto por Hora**:
   $$\text{R\$/h} = \frac{\text{Valor da Oferta}}{\text{Duração em Horas}}$$
4. **Custo Estimado com Combustível**:
   $$\text{Custo Combustível} = \left(\frac{\text{Distância Total}}{\text{Consumo em km/L}}\right) \times \text{Preço do Litro}$$
5. **Custo Operacional Adicional**:
   $$\text{Custo Manutenção} = \text{Distância Total} \times \text{Custo por km}$$
6. **Lucro Estimado**:
   $$\text{Lucro} = \text{Valor da Oferta} - \text{Custo Combustível} - \text{Custo Manutenção} - \text{Custo Fixo}$$
7. **Semáforo de Decisão**:
   - **Verde**: Cumpre metas de R$/km e R$/h com lucro positivo.
   - **Amarelo**: Cumpre ao menos uma das metas ou está a até 15% de tolerância, com lucro positivo.
   - **Vermelho**: Prejuízo operacional (lucro $\le 0$) ou ambos indicadores abaixo de 85% das metas mínimas.

---

## 🔒 Permissões Android Utilizadas

- `android.permission.SYSTEM_ALERT_WINDOW`: Utilizada **exclusivamente** para exibir a Janela Flutuante com o resumo da análise calculada, solicitada com diálogo explicativo e consentimento explícito do usuário nas Configurações do Android.
- **Não são solicitadas permissões invasivas** (sem localização em segundo plano, sem acesso a contatos, sem gravação de tela).

---

## ⚠️ Limitações Conhecidas e Próximos Passos

1. **Leitura Automática de Ofertas**:
   - Permanece em modo experimental conforme isolamento na interface `OfferReader`.
   - APIs de Acessibilidade possuem fortes restrições na Google Play Store. A análise manual é o fluxo primário e 100% estável.
2. **Janela Flutuante**:
   - Em dispositivos com Android 10+ e fabricantes com customizações agressivas (Xiaomi/MIUI, Samsung OneUI), pode ser necessário conceder permissão adicional de "iniciar em segundo plano" nas configurações do sistema.

