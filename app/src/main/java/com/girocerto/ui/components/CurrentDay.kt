package com.girocerto.ui.components

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun rememberCurrentDay(): LocalDate {
    val owner = LocalLifecycleOwner.current
    val day by produceState(LocalDate.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { value = LocalDate.now(); delay(1_000) }
        }
    }
    return day
}
