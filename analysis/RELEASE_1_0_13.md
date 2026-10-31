# Release 1.0.13 — Otimização dos Apps Alvo (99, Uber e iFood) e Correção no OCR

**Versão**: 1.0.13 (versionCode 13)  
**Data**: 31 de Outubro de 2026  

---

## 🚀 Principais Mudanças e Entregas

### 1. Foco nos 3 Aplicativos Principais (99, Uber e iFood)
- Interface do Copiloto atualizada para apresentar com exclusividade os 3 aplicativos de trabalho principais:
  - **99 e 99Food** (`com.app99.driver`, `com.taxis99`, `com.didiglobal.driver`, `com.didiglobal.food.driver`)
  - **Uber e Uber Eats** (`com.ubercab.driver`, `com.ubermoto.driver`, `com.ubereats.driver`)
  - **iFood para Entregadores** (`br.com.ifood.driver.app`, `br.com.ifood.driver`)
- Remoção do inDrive e Keeta das preferências ativas de usuário, mantendo compatibilidade de código.
- Política de monitoramento e leitura (`CopilotPolicy`) atualizada com flag dedicada para `ifood`.

### 2. Correção Crítica no Leitor de Imagem (OCR Local)
- **Eliminação do erro *"OCR: falha temporária em preparação da imagem"***:
  - No Android, bitmaps encapsulados a partir de `HardwareBuffer` (`Bitmap.Config.HARDWARE`) não podem ser copiados diretamente com `isMutable = true`.
  - Substituição da chamada restritiva por desenho direto através de `Canvas.drawBitmap(hardware, 0f, 0f, null)` em um novo bitmap `ARGB_8888`.
  - Adição de clamping defensivo (`coerceIn`) nos limites geométricos da janela (`selected.bounds`) antes do recorte.

### 3. Aprimoramento da Extração de Ofertas Reais
- **99Food**: Reconhecimento e suporte a ofertas de comida com bônus inclusos (ex: *"R$ 11,08 com Inclui R$2,00 Bônus"*), sem descartar a linha nem o valor principal da tarifa.
- **Uber Envios Moto**: Suporte a trajetos longos no formato *"X h e Y min (Z km)"* com identificação precisa do tempo e da distância.
- **Nome do App no Status**: Resolução do nome do aplicativo mesmo quando a janela inicial de acessibilidade não expõe textos imediatamente (evitando exibir `: 0 textos`).
- **Orçamento de Varredura**: Aumento do tempo limite de travessia IPC da árvore de acessibilidade para 300ms e 1024 nós, garantindo que aplicativos com layouts complexos (mapas e camadas vetoriais) não sejam descartados prematuramente.

### 4. Validação e Testes
- Novos testes unitários adicionados cobrindo os layouts reais das capturas de tela fornecidas.
- Suíte completa de testes aprovada.
