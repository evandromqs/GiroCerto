# GitHub e distribuição Android

Repositório privado: https://github.com/evandromqs/GiroCerto

O workflow `.github/workflows/android-release.yml` testa e executa lint em
pull requests e pushes na main. Após sucesso na main, compila um APK release
assinado e publica uma Release com `GiroCerto.apk`, `SHA256SUMS.txt` e
`update.json`. A execução manual também está disponível. PRs não recebem
as credenciais de assinatura. O APK Gigu, ferramentas REA e caches locais
não são versionados.

Os quatro secrets são ANDROID_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD,
ANDROID_KEY_PASSWORD e ANDROID_KEY_ALIAS. A cópia local está em `signing/`,
ignorada pelo Git. `credentials.xml` usa a proteção do Windows, vinculada
ao usuário e computador; copiar apenas esse arquivo para outro computador
não garante recuperação. Preserve um backup recuperável da chave e da senha.
Não recrie a chave para uma atualização comum.

VersionCode usa github.run_number e versionName usa 1.0.N. Preserve a sequência
do workflow; ao substituí-lo, não publique códigos menores ou iguais a uma
versão já distribuída. APKs debug anteriores têm outra assinatura e não são
atualizados diretamente por essa primeira release.

Esta etapa prepara a distribuição; o app ainda não consulta update.json nem
recebe notificações push. Releases privadas exigem autenticação. Para distribuir
a usuários sem acesso ao repositório, será necessário um canal de downloads
apropriado; nunca incluir um token privado do GitHub dentro do APK.
