package com.girocerto.test

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.girocerto.app.RuntimeMode

class GiroCertoTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        RuntimeMode.bypassOnboarding = true
        super.onCreate(arguments)
    }
}
