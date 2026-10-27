package it.mappinando.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.mappinando.app.updates.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdatesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val release = AppRelease("1.0.7", 8, "Mappinando-1.0.7-debug.apk", "a".repeat(64), 60_000_000, 26, "", "", "Ricerca migliorata e aggiornamenti dall’app.")

    @Test fun downloadAndInstallationRequireSeparateExplicitActions() {
        val state = mutableStateOf(UpdateState(UpdatePhase.AVAILABLE, release))
        var downloads = 0; var installs = 0
        compose.setContent { MappinandoTheme {
            AppUpdatesSheet(state.value, {}, {}, { downloads++; state.value = UpdateState(UpdatePhase.READY, release) }, {}, { installs++ }, {})
        } }
        compose.onNodeWithTag("update-install").assertDoesNotExist()
        compose.onNodeWithTag("update-download").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(0, installs) }
        compose.onNodeWithTag("update-install").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, installs) }
    }

    @Test fun progressCanBeCancelledAndCheckFailureCanBeRetried() {
        val state = mutableStateOf(UpdateState(UpdatePhase.DOWNLOADING, release, 30_000_000))
        var cancellations = 0; var checks = 0
        compose.setContent { MappinandoTheme {
            AppUpdatesSheet(state.value, {}, { checks++ }, {}, { cancellations++; state.value = UpdateState(UpdatePhase.ERROR, message = "Connessione non disponibile") }, {}, {})
        } }
        compose.onNodeWithText("Download 50%").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("update-cancel").performScrollTo().performClick()
        compose.onNodeWithTag("update-retry").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, cancellations); assertEquals(1, checks) }
    }

    @Test fun currentVersionCanBeCheckedWithoutStartingADownload() {
        var checks = 0
        compose.setContent { MappinandoTheme {
            AppUpdatesSheet(UpdateState(UpdatePhase.CURRENT), {}, { checks++ }, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Tutto aggiornato.").assertIsDisplayed()
        compose.onNodeWithTag("update-download").assertDoesNotExist()
        compose.onNodeWithTag("update-check").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, checks) }
    }
}
