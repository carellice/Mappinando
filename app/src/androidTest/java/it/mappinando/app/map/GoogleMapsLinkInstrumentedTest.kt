package it.mappinando.app.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleMapsLinkInstrumentedTest {
    @Test fun addressOpensInGoogleMapsAndSpecialCharactersRemainPartOfTheQuery() {
        val context = RecordingContext()
        val address = "Via dell’Arte 8 & 10, Roma #centro"
        assertTrue(openAddressInGoogleMaps(context, SearchResult("Locale", address, 41.9, 12.5)))
        val intent = context.intents.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.google.android.apps.maps", intent.`package`)
        assertEquals("https", intent.data?.scheme)
        assertEquals("www.google.com", intent.data?.host)
        assertEquals("1", intent.data?.getQueryParameter("api"))
        assertEquals(address, intent.data?.getQueryParameter("query"))
        assertEquals(setOf("api", "query"), intent.data?.queryParameterNames)
    }

    @Test fun coordinatesOpenInBrowserWhenGoogleMapsIsUnavailable() {
        val context = RecordingContext(mapsUnavailable = true)
        assertTrue(openAddressInGoogleMaps(context, SearchResult("Punto", "", -33.8688, 151.2093)))
        assertEquals(2, context.intents.size)
        val browser = context.intents.last()
        assertNull(browser.`package`)
        assertEquals("-33.8688,151.2093", browser.data?.getQueryParameter("query"))
        assertEquals(context.intents.first().data, browser.data)
    }

    @Test fun missingMapsAndBrowserReturnsFailureWithoutCrashing() {
        val context = RecordingContext(mapsUnavailable = true, browserUnavailable = true)
        assertFalse(openAddressInGoogleMaps(context, SearchResult("Punto", "Roma", 41.9, 12.5)))
        assertEquals(2, context.intents.size)
    }

    private class RecordingContext(private val mapsUnavailable: Boolean = false, private val browserUnavailable: Boolean = false) :
        ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val intents = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            intents += Intent(intent)
            if ((intent.`package` != null && mapsUnavailable) || (intent.`package` == null && browserUnavailable)) throw ActivityNotFoundException()
        }
    }
}
