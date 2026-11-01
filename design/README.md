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
