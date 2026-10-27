package it.mappinando.app.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.mappinando.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FirstLaunchInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun introductionExplainsThreeStepsAndRequestsLocationOnlyAtTheEnd() {
        var choice: Boolean? = null
        compose.setContent { MappinandoTheme { OnboardingScreen { choice = it } } }
        compose.onNodeWithText("Segui la tua\ncuriosità.").assertIsDisplayed()
        assertNull(choice)
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText("Un posto.\nMille possibilità.").assertIsDisplayed()
        assertNull(choice)
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.onNodeWithText("Il tuo mondo,\nda dove sei.").assertIsDisplayed()
        assertNull(choice)
        compose.onNodeWithTag("onboarding-next").performClick()
        compose.runOnIdle { assertEquals(true, choice) }
    }

    @Test fun canSkipWithoutRequestingLocation() {
        var choice: Boolean? = null
        compose.setContent { MappinandoTheme { OnboardingScreen { choice = it } } }
        compose.onNodeWithTag("onboarding-skip").performClick()
        compose.runOnIdle { assertEquals(false, choice) }
    }

    @Test fun lastStepAlsoAllowsContinuingWithoutPermission() {
        var choice: Boolean? = null
        compose.setContent { MappinandoTheme { OnboardingScreen { choice = it } } }
        repeat(2) { compose.onNodeWithTag("onboarding-next").performClick() }
        compose.onNodeWithText("Inizia senza posizione").performClick()
        compose.runOnIdle { assertEquals(false, choice) }
    }

    @Test fun completionSurvivesReopeningPreferences() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val filename = "first_launch_test_${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(filename, mode)
        }
        try {
            assertFalse(AppPreferences(context).onboardingComplete)
            AppPreferences(context).completeOnboarding()
            assertTrue(AppPreferences(context).onboardingComplete)
        } finally { base.deleteSharedPreferences(filename) }
    }

    @Test fun compactCardsKeepEqualHeightsWithLongNamesTasksAndLargeText() {
        val sample = Place(id = "first", name = "Un nome molto lungo di un ristorante da scoprire", category = PlaceCategory.RESTAURANTS, latitude = 42.0, longitude = 12.0)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                MappinandoTheme {
                    Column {
                        CompactPlaceCard(sample, {}, {})
                        CompactPlaceCard(sample.copy(id = "second", name = "Bar", category = PlaceCategory.BARS, tasks = List(100) { PlaceTask(title = "Una cosa da fare $it") }), {}, {})
                        CompactPlaceCard(sample.copy(id = "third", name = "Parco", category = PlaceCategory.NATURE, visited = true), {}, {})
                    }
                }
            }
        }
        val heights = listOf("first", "second", "third").map { compose.onNodeWithTag("compact-place-$it").fetchSemanticsNode().boundsInRoot.height }
        assertEquals(heights[0], heights[1], 1f)
        assertEquals(heights[0], heights[2], 1f)
    }
}
