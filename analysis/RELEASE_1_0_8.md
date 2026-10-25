# Giro Certo 1.0.8

- Ícones originais de Uber Driver, 99 Motorista e Entregador, iFood para Entregadores e Keeta Entregador, obtidos nas páginas oficiais do Google Play. Uber Eats e 99Food usam o ícone do app de trabalho correspondente, mantendo seus nomes no diário.
- Incluído o pacote atual do Keeta Entregador no monitoramento.
- Leitura descarta valores monetários ambíguos, ignora saldos identificados e exige contexto de oferta no leitor automático.
- Medidas repetidas deixam de duplicar distância e tempo; tempo total explícito prevalece sobre os trechos.
- Histórico com erro de leitura é preservado e não pode ser sobrescrito por novos registros; opção de tentar recuperar na tela Histórico.
- Rascunhos do diário são mantidos por data durante a navegação e restauração da tela.
- Bolha mostra saldo registrado no diário, sem somar ofertas como ganhos realizados.
- Varredura de acessibilidade limitada por quantidade de nós, profundidade e tempo, com redução de eventos repetidos e leitura periódica menos frequente.
- Alinhamento de textos definido pelo componente; resumo financeiro separa rótulos e valores sem depender do comprimento do texto.
- “Agora não” adia o aviso da mesma atualização por 24 horas; consulta manual continua disponível.
- Backup Android limitado a perfil, fechamentos, custos e aparência; exclui ofertas, downloads e estados específicos do aparelho. Exportação de ganhos em CSV em Ajustes.
- Histórico das versões disponível em Ajustes.
- Versão definida em arquivo próprio, independente do número de execução do GitHub Actions; publicação condicionada a testes Android em emulador, testes unitários e lint.

Os testes automatizados não substituem a conferência de ofertas reais em cada aplicativo e fabricante de celular.

Fontes dos ícones: [Uber Driver](https://play.google.com/store/apps/details?id=com.ubercab.driver), [99 Motorista](https://play.google.com/store/apps/details?id=com.app99.driver), [iFood para Entregadores](https://play.google.com/store/apps/details?id=br.com.ifood.driver.app), [Keeta Entregador](https://play.google.com/store/apps/details?id=com.sankuai.sailor.courier). Marcas e ícones pertencem aos respectivos titulares; uso para identificação dos apps, sem vínculo comercial.
