package com.girocerto.ui

import android.content.Context
import android.widget.DatePicker
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.hamcrest.Matchers.instanceOf
import com.girocerto.app.GiroCertoApp
import com.girocerto.app.theme.*
import com.girocerto.domain.model.ElectricProfile
import com.girocerto.ui.home.WorkDiaryScreen
import com.girocerto.ui.settings.SimpleSettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.io.File

class ReleaseUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun themesAndVersionHistoryRemainAccessibleWithLargeText() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides
                androidx.compose.ui.unit.Density(context.resources.displayMetrics.density, 1.5f)) {
                GiroCertoTheme { androidx.compose.material3.Surface { SimpleSettingsScreen({}, {}, {}) } }
            }
        }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            compose.onNodeWithText(mode.label).performScrollTo().performClick()
            compose.onNodeWithText("Histórico de versões").performScrollTo().performClick()
            compose.onNodeWithText("Versão 1.0.8").performScrollTo().assertIsDisplayed()
            capture("settings-${mode.name}")
            compose.onNodeWithText("Ocultar histórico de versões").performScrollTo().performClick()
        }
        compose.runOnIdle { AppearancePreferences.set(context, ThemeMode.SYSTEM) }
    }

    @Test fun switchingDatesPreservesUnsavedEarnings() {
        runBlocking { GiroCertoApp.instance.workRepository.saveProfile(ElectricProfile("Teste", 200.0, .5, .1, 5, 8.0, 100.0, 500.0)) }
        compose.setContent { GiroCertoTheme { androidx.compose.material3.Surface { WorkDiaryScreen {} } } }
        compose.onNodeWithText("Uber • recebido (R$)").performScrollTo().performTextReplacement("123,45")
        val today = LocalDate.now()
        selectDate(today.minusDays(1))
        compose.onNodeWithText("Uber • recebido (R$)").performScrollTo().performTextReplacement("67,89")
        selectDate(today)
        compose.onNodeWithText("123,45").performScrollTo().assertIsDisplayed()
        selectDate(today.minusDays(1))
        compose.onNodeWithText("67,89").performScrollTo().assertIsDisplayed()
        capture("diary-draft")
    }

    private fun selectDate(date: LocalDate) {
        compose.onNodeWithText("Dia:", substring = true).performScrollTo().performClick()
        onView(instanceOf(DatePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = instanceOf(DatePicker::class.java)
            override fun getDescription() = "Select a diary date"
            override fun perform(ui: UiController, view: View) { (view as DatePicker).updateDate(date.year, date.monthValue - 1, date.dayOfMonth); ui.loopMainThreadUntilIdle() }
        })
        onView(withId(android.R.id.button1)).perform(click())
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: return
        File(directory).mkdirs()
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
