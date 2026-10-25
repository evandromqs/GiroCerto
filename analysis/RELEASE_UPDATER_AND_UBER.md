# Leitura do Uber e atualizações

## Mudanças

- OCR: uma janela parcialmente coberta por aviso de bateria, notificação ou bolha não é descartada inteira. As áreas encobertas são mascaradas antes do reconhecimento; a origem das coordenadas é ajustada para capturas de janela no Android 14+.
- O leitor também tenta a origem do evento de acessibilidade do próprio app quando uma janela flutuante ainda não aparece na lista de janelas. Nenhum nó é guardado entre tentativas.
- Diagnóstico visível em Copiloto e no menu da bolha. Copiar diagnóstico inclui versão, Android, conexão, estado do OCR e contagem de textos; não inclui endereços ou nomes.
- APKs publicados como GiroCerto-VERSAO.apk; update.json e SHA256SUMS.txt apontam para o mesmo arquivo.
- Consulta automática à última release pública do GitHub ao abrir o app. Download iniciado somente ao tocar em Baixar atualização, com progresso, retomada do acompanhamento após reiniciar e conferência SHA-256.
- Ao concluir, o usuário abre Downloads e toca no APK. A instalação continua sujeita à confirmação do Android. Não há instalação silenciosa nem permissão nova para instalar pacotes.
- Ajustes > Atualizações permite consultar manualmente. Falha de rede não impede o uso do app. A internet é usada para a consulta de versão e download; ofertas e ganhos não são enviados.

## Validação e limites

A regressão usa apenas os campos não pessoais transcritos da oferta enviada: Envios Moto, R$ 16,73, 29 min, 10.7 km, Aceitar. Resultado esperado: R$ 1,56/km e R$ 34,61/h brutos. A imagem e os endereços do usuário não estão no repositório.

Testes cobrem máscaras parciais/totais, coordenadas de captura, formato da oferta, versão superior/igual/anterior e rejeição de manifestos com origem ou checksum inválidos. A leitura OCR real no telefone, notificações de download e instalação precisam de validação no dispositivo: não havia aparelho ou emulador conectado.

Instale esta atualização pelo link uma vez para habilitar a detecção interna das próximas versões. Se a leitura do Uber ainda não gerar cartão, toque na bolha para ver o motivo ou copie o diagnóstico em Copiloto após uma oferta.
