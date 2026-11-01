# Giro Certo 1.0.19

- Primária #0000FF; secundária #1591EA. Variações claras mantêm contraste no tema escuro.
- Painel no topo até 50% da área disponível, com rolagem interna. Cards uniformes e respiro entre seções.
- Cabeçalho com marca; X visual de 24dp em alvo de toque de 48dp.
- Plataforma pelo ícone, tarifa e pílula de avaliação. Distância/tempo e indicadores em 16sp; mapa e relógio vetoriais.
- Rótulos pedidos: Prejuízo e Lucro Médio. A fórmula de lucro continua a estimativa da oferta individual, sem mudança financeira.
- Barra regressiva por oferta: 15s por padrão, 30s opcional em Ajustes → Cartão e voz.
- Popups sem raiz acessível usam evidência de origem de eventos por até 10s, sempre limitada à janela visível identificada. Nenhuma captura arbitrária do app em foco.
- Atalho de plataforma por IntentSender no Android 13+, iniciado somente por toque, com opção de abertura visível no Android 16; alternativas para os pacotes conhecidos.
- Popups reais e abertura do Uber no aparelho do usuário ainda exigem confirmação em campo; testes locais não reproduzem o app proprietário.

# Giro Certo 1.0.18 — visual e painel

- Cor de marca: #371BE4. Tons claros de roxo mantêm contraste de textos no tema escuro.
- Superfícies com luz superior, sombra suave e resposta pressionada. Cores de avaliação preservadas.
- Ícones originais SVG em icons/; equivalentes VectorDrawable em app/src/main/res/drawable.
- Painel de ofertas no topo, abaixo da área segura, até 30% de altura. Conteúdo maior usa rolagem interna, sem reduzir alvos de toque abaixo de 48dp.
- Até três observações, uma por plataforma. Prazo monotônico de 30 segundos desde a primeira identificação; repetir um frame não renova nem ressuscita o registro.
- X recolhe sem pausar; Abrir usa o pacote de origem e recolhe após abrir. Nenhuma corrida é aceita automaticamente.
- A lista só contém ofertas observadas e não confirma disponibilidade na plataforma.

## Distribuição e compatibilidade

Esta versão permanece na distribuição GitHub existente. Não representa certificação Google ou garantia de compatibilidade bancária.
Uma distribuição Play deve remover o atualizador de APK e REQUEST_INSTALL_PACKAGES, usar atualizações pela loja e concluir declarações de acessibilidade, privacidade, segurança de dados e revisão no Play Console.
O bloqueio relatado do Nubank ainda depende da mensagem exata e de validação no aparelho; não há alteração para contornar mecanismos de segurança.
