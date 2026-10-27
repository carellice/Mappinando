package it.mappinando.app.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** A regular Maps URL opens the selected address without an API key or billing. */
internal fun googleMapsAddressUri(result: SearchResult): Uri = Uri.Builder()
    .scheme("https").authority("www.google.com").path("/maps/search/")
    .appendQueryParameter("api", "1")
    .appendQueryParameter("query", result.address.trim().ifBlank { "${result.latitude},${result.longitude}" })
    .build()

internal fun openAddressInGoogleMaps(context: Context, result: SearchResult): Boolean {
    val uri = googleMapsAddressUri(result)
    fun launch(packageName: String?): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
            if (packageName != null) setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
    return launch("com.google.android.apps.maps") || launch(null)
}
