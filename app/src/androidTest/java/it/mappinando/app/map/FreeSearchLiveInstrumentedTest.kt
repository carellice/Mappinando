package it.mappinando.app.map

import android.location.Geocoder
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** Optional network diagnostic, never part of the offline regression suite. */
@RunWith(AndroidJUnit4::class)
class FreeSearchLiveInstrumentedTest {
    @Test fun combinedFreeSearchIncludesRomeAddressWithoutInventingABusiness() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveSearch") == "true" && Geocoder.isPresent())
        val results = PlaceSearch(instrumentation.targetContext).search("Arrosticini Divini Roma", SearchCenter(41.9028, 12.4964), SearchKind.RESTAURANTS)
        results.forEach { Log.i("FreeSearchCheck", "Combined result: $it") }
        assertTrue("Expected the address resolved by the real device provider", results.any {
            it.address.contains("Bufalotta", true) && it.requiresAddressCheck && it.suggestedName == "Arrosticini Divini Roma" &&
                kotlin.math.abs(it.latitude - 41.9694) < .003
        })
    }

    @Test fun inspectDeviceGeocoderCoverage() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("liveSearch") == "true")
        val geocoder = Geocoder(InstrumentationRegistry.getInstrumentation().targetContext, Locale.ITALY)
        Log.i("FreeSearchCheck", "Geocoder present: ${Geocoder.isPresent()}")
        listOf("Arrosticini Divini Roma", "Via della Bufalotta 871 Roma").forEach { query ->
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 8).orEmpty()
            Log.i("FreeSearchCheck", "Query: $query; ${addresses.size} results")
            addresses.forEach { Log.i("FreeSearchCheck", "${it.featureName} | ${it.getAddressLine(0)} | ${it.locality} | ${it.latitude}, ${it.longitude}") }
        }
    }
}
