# Giro Certo 1.0.9 — proposta de atualização

**Documento histórico de planejamento.** Para o resultado implementado, consulte `RELEASE_1_0_9.md` e `../changelog.json`. Base: 1.0.8 (`1201a2c`). Não alterar o número instalado nem apresentar estes itens como concluídos antes de implementação e validação. Referência: `COMPOSE-AUDIT-REPORT.md`.

## Correções candidatas prioritárias

- Proteger registros financeiros danificados, mostrando erro e preservando dados para recuperação. Auditoria 01.
- Fazer “Registrar/Editar meu dia” abrir a data de hoje, mantendo rascunhos das outras datas. 02.
- Descartar leituras OCR antigas quando uma oferta for substituída. 03.
- Diferenciar etapas reais de textos duplicados na acessibilidade e resolver totais repetidos. 04–05.
- Reconhecer avaliações abaixo de quatro estrelas e interpretar moeda integralmente. 06–07.
- Rejeitar valores isolados ambíguos e trajetos sem identificação segura das etapas. 08–09.
- Permitir cancelar download pausado e baixar novamente um APK apagado. 10–11.
- Respeitar a tela anterior e as etapas do quiz ao voltar; cancelar também a análise pendente ao recusar permissão. 12–13.
- Atualizar a data de hoje na retomada/virada do dia e explicar erros por campo. 14–15.

## Melhorias candidatas de interface

- Ajustar o acabamento dos cantos dos cartões em ambos os temas. 16.
- Permitir selecionar apps tocando na linha inteira e melhorar sua leitura por acessibilidade. 17.
- Padronizar as decisões faladas com os textos do cartão. 18.
- Tornar explícitas as dimensões dos componentes de texto. 24.

## Melhorias técnicas candidatas

- Carregar históricos fora da thread principal e renderizar apenas os dias necessários. 19–20.
- Limitar o estado salvo dos rascunhos, preservando-os de forma adequada. 21.
- Associar estado de tela ao ciclo de vida e separar gravações/regras da composição. 22–23.
- Evitar refazer transformações do histórico a cada recomposição. 25.
- Adicionar testes em Android recente e centralizar dados do changelog. 28–29.

## Funcionalidades para planejamento separado

- Fechamento semanal com reconciliação do valor fixo do contrato, inclusive semanas com menos ou mais dias trabalhados. 26.
- Backup completo exportável e restaurável, mantendo o CSV como relatório. 27.
- Otimização da release com R8 e avaliação de perfil de inicialização, após validar compatibilidade. 30.

Estes três itens podem ser destinados a uma versão posterior para não atrasar as correções prioritárias.

## Critérios antes de publicar

1. Adicionar regressões dos cenários reproduzidos do interpretador e da navegação por data.
2. Testar preservação de diário corrompido, OCR atrasado, download pausado e APK removido.
3. Validar tema claro/escuro, fontes grandes, seleção de apps com TalkBack e retorno pelo botão/gesto do sistema.
4. Executar testes unitários, instrumentados e lint; incluir API recente além de API 30.
5. Testar ofertas reais no dispositivo, documentando apps/versões e limitações observadas.
6. Atualizar versão, histórico interno e notas somente com os itens efetivamente entregues; conferir assinatura, nome versionado, manifesto e hash do APK.

## Registro desta auditoria

Foi gerado relatório de compilação Compose, sem alteração de código funcional. A auditoria executou sete cenários contra o interpretador compilado; não realizou nova sessão de captura em telefone físico nem publicou release.
