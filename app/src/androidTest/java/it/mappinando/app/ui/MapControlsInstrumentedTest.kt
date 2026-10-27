package it.mappinando.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapControlsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun portraitToolsHaveEqualTargetsAndOneSharedAxis() {
        compose.setContent {
            MappinandoTheme {
                Box(Modifier.size(350.dp, 280.dp)) { MapControls(6, false, false, {}, {}, {}) }
            }
        }
        val fullscreen = bounds("map-fullscreen-toggle")
        val recenter = bounds("map-recenter")
        val add = bounds("map-add")
        assertEquals(fullscreen.width, recenter.width, 1f)
        assertEquals(fullscreen.width, add.width, 1f)
        assertEquals(fullscreen.center.x, recenter.center.x, 1f)
        assertEquals(fullscreen.center.x, add.center.x, 1f)
        assertTrue(fullscreen.bottom <= recenter.top)
        assertTrue(recenter.bottom < add.top)
        assertFalse(bounds("map-place-count").overlaps(fullscreen))
    }

    @Test fun shortMapsKeepEveryActionInsideOneRowWithoutCoveringCount() {
        compose.setContent {
            MappinandoTheme {
                Box(Modifier.size(350.dp, 160.dp)) { MapControls(6, true, false, {}, {}, {}) }
            }
        }
        val fullscreen = bounds("map-fullscreen-toggle")
        val recenter = bounds("map-recenter")
        val add = bounds("map-add")
        val overlay = bounds("map-controls")
        assertEquals(fullscreen.center.y, recenter.center.y, 1f)
        assertEquals(fullscreen.center.y, add.center.y, 1f)
        assertTrue(fullscreen.right <= recenter.left)
        assertTrue(recenter.right < add.left)
        assertTrue(add.right <= overlay.right && add.bottom <= overlay.bottom)
        assertFalse(bounds("map-place-count").overlaps(fullscreen))
    }

    @Test fun transitionBlocksActionsAndTheyResumeWithCorrectFullscreenLabel() {
        val transitioning = mutableStateOf(true)
        val fullscreen = mutableStateOf(false)
        var toggles = 0
        var centers = 0
        var additions = 0
        compose.setContent {
            MappinandoTheme {
                Box(Modifier.size(350.dp, 280.dp)) {
                    MapControls(
                        6, fullscreen.value, transitioning.value,
                        onToggleFullscreen = { toggles++; fullscreen.value = !fullscreen.value },
                        onRecenter = { centers++ }, onAdd = { additions++ },
                    )
                }
            }
        }
        listOf("map-fullscreen-toggle", "map-recenter", "map-add").forEach { tag ->
            compose.onNodeWithTag(tag).assertIsNotEnabled().performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(0, toggles + centers + additions)
            transitioning.value = false
        }
        compose.onNodeWithTag("map-fullscreen-toggle").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Riduci mappa").assertExists()
        compose.onNodeWithTag("map-recenter").performClick()
        compose.onNodeWithTag("map-add").performClick()
        compose.runOnIdle { assertEquals(3, toggles + centers + additions) }
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
}
