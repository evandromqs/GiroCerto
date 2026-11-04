package com.girocerto.app

/** Process-local switches controlled only by the instrumented test runner. */
object RuntimeMode {
    @Volatile
    var bypassOnboarding: Boolean = false
}
