# Perfil elétrico e diário de ganhos — 24/10/2026

## Fluxo implementado

Quatro abas: Início, Ganhos, Metas e Ajustes. Ícones vetoriais de navegação,
com gráfico para ganhos e alvo para metas. Interface própria com menos caminhos
principais; não é uma reprodução visual comprovada do Gigu.

Metas contém um quiz de quatro etapas: nome/contrato semanal e por km;
recarga/manutenção extras por km; dias, horas e km planejados; meta líquida
semanal. Valores do contrato e da meta começam vazios, sem inventar os custos
do usuário. Dias/horas são sugestões editáveis (5 dias, 8 horas).

Fórmulas: custo semanal = fixo semanal + custo variável por km × km/dia × dias.
Meta bruta diária = (meta líquida semanal + custo semanal) / dias.
Metas por km e hora usam km e horas diários planejados. O quiz atualiza custos
variáveis e metas do copiloto. O fixo entra no fechamento diário, não como
uma cobrança integral em cada oferta.

Diário separado do histórico de ofertas: Uber, Uber Eats, iFood, 99, 99Food e
Keeta. Cada app tem receita e detalhes opcionais de km/horas. Totais reais de
km e horas do dia são informados separadamente para não duplicar atividades
simultâneas nem omitir deslocamentos sem corrida. Valores recebidos devem ser
informados após taxas da plataforma e antes dos custos cadastrados da moto.

Cada data tem um fechamento editável; salvar novamente substitui esse dia.
Custos e meta são preservados com o primeiro salvamento. Exclusão exige
confirmação. Perfil e diário persistem localmente, com confirmação da escrita
antes de mostrar sucesso. Nenhuma conta ou serviço remoto foi adicionado.

## Limites e critérios de validação

O saldo desconta apenas os custos cadastrados. O rateio diário usa os dias
planejados: trabalhar menos dias não reduz a obrigação semanal. Não há ainda
conciliação de semanas com dias de folga nem importação automática de receitas.
Lançamentos reais não são criados a partir de ofertas detectadas.

O cadastro dos seis apps não comprova leitura automática de todas as versões
de suas telas. As limitações do leitor existente continuam aplicáveis.

Testes cobrem contrato elétrico, metas, sobreposição de horas/apps, entradas
inválidas e dias sem movimento. Teste instrumentado cobre salvar, substituir,
reabrir e excluir um fechamento. Sem dispositivo conectado, os testes de
interface e persistência Android precisam ser executados no aparelho; não há
alegação de validação visual.

Roteiro: criar perfil com valores conhecidos; verificar a projeção; registrar
duas plataformas no mesmo dia; salvar e reabrir; editar sem duplicação; mudar
o contrato e confirmar que o dia antigo mantém custos; testar exclusão,
teclado, rotação, fonte ampliada e retorno entre as quatro abas.

## Resultado local

65 testes unitários aprovados, zero falhas/erros. APK debug gerado e testes
instrumentados compilados. SHA-256 do APK:
`f852f937258781ce516359110cd10f23f5f4f5e8aeec4fc810ab2f2b9f6aaf7a`.
Local: `app/build/outputs/apk/debug/app-debug.apk`.
