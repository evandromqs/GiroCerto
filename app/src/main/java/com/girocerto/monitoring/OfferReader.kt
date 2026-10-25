package com.girocerto.monitoring

/**
 * Contrato abstrato para provedores de leitura de ofertas de corrida.
 *
 * ## NOTA ARQUITETURAL E LIMITAÇÕES DA PLATAFORMA ANDROID:
 * 1. Restrições do Google Play:
 *    O uso de Serviços de Acessibilidade (AccessibilityService) é estritamente restrito
 *    pelas políticas do Google Play para usuários com deficiências. Utilizar acessibilidade
 *    para ler dados de aplicativos comerciais de terceiros em segundo plano (como Uber, 99)
 *    pode acarretar rejeição na Google Play Store se não houver conformidade rigorosa ou
 *    consentimento prévio explícito.
 *
 * 2. Dinâmica de Nós da Interface:
 *    Muitos aplicativos de transporte utilizam renderização customizada (ex: Flutter, React Native,
 *    ou SurfaceViews do Android) onde textos e valores podem não estar expostos de maneira consistente
 *    na hierarquia de acessibilidade (AccessibilityNodeInfo).
 *
 * 3. Integridade e Consentimento:
 *    O Giro Certo NUNCA realiza cliques automáticos, recusas ou aceites involuntários.
 *    Qualquer integração futura deve respeitar a privacidade do motorista e funcionar como
 *    copiloto informativo e transparente.
 */
interface OfferReader {

    /**
     * Fonte da qual este leitor obtém as informações.
     */
    val source: OfferSource

    /**
     * Tenta ler e interpretar os dados de uma oferta de corrida disponível.
     */
    suspend fun readOffer(): OfferReaderResult
}
