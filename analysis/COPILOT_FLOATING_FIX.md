# Copiloto flutuante e seleção dos apps

- O serviço de acessibilidade mantém uma conexão vinculada ao serviço da janela. A janela não depende de iniciar um serviço de fundo a cada oferta.
- Conectar o leitor, reativar o copiloto ou voltar da autorização de sobreposição restaura a bolha. Pausar o copiloto ou desconectar o leitor remove a janela.
- Fechar o cartão retorna à bolha; a duração continua configurável. Uma oferta continuamente visível não repete o alerta a cada 15 segundos.
- Janelas dos apps suportados são verificadas a cada 1,5 segundo com tela ligada e desbloqueada, além dos eventos de acessibilidade. A leitura periódica não depende de OCR. OCR continua opcional e local.
- Meus ganhos > Meus apps de trabalho permite selecionar Uber, Uber Eats, iFood, 99, 99Food e Keeta. A preferência fica salva no aparelho e não altera os filtros independentes do copiloto.
- Valores anteriores ou em edição de um app desmarcado permanecem visíveis e entram no fechamento. Apps desmarcados sem valores ficam ocultos.

## Conferência no aparelho

1. Atualizar mantendo os dados. Abrir Copiloto e conferir as autorizações de leitura e cartão flutuante. Após uma atualização do serviço, se o Android não o reconectar, desativar e reativar o serviço nas configurações de acessibilidade.
2. Ativar o copiloto, abrir a tela inicial do Android e um app de trabalho: a bolha deve permanecer. Arrastar a bolha e tocar para abrir o menu.
3. Exibir uma oferta real: conferir valor, distância e tempo disponíveis no cartão. Fechar o cartão: a bolha deve retornar, sem repetição para a oferta ainda visível. Conferir nova oferta com valor diferente.
4. Pausar no menu da bolha: a janela deve desaparecer; reativar no Giro Certo: ela deve retornar.
5. Em Meus ganhos, selecionar dois apps, salvar e reiniciar o app. Conferir filtro persistido. Desmarcar um app com ganhos em um dia antigo: esses ganhos devem permanecer disponíveis.

Não havia celular ou emulador conectado durante a implementação. Testes instrumentados foram preparados/compilados, mas a exibição sobre apps reais precisa ser validada no dispositivo.
