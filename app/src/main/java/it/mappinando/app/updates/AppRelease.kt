package it.mappinando.app.updates

import java.net.URI

internal const val UPDATE_MANIFEST_NAME = "Mappinando-update.json"

data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val apkName: String,
    val sha256: String,
    val sizeBytes: Long,
    val minSdk: Int,
    val downloadUrl: String,
    val pageUrl: String,
    val notes: String,
) {
    init {
        require(versionName.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")))
        require(versionCode in 1..2_100_000_000)
        require(apkName == "Mappinando-$versionName-debug.apk")
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(sizeBytes in 1..268_435_456L)
        require(minSdk in 26..100)
    }

    fun isNewerThan(installedCode: Int) = versionCode > installedCode
}

internal object ReleasePolicy {
    fun requireReleaseUrl(url: String, repository: String, tag: String, asset: String) {
        require(url == "https://github.com/$repository/releases/download/$tag/$asset") { "Indirizzo del pacchetto non valido." }
    }

    fun requireDownloadHost(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)) { "Connessione di aggiornamento non valida." }
        require(uri.host in setOf("api.github.com", "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com", "github-releases.githubusercontent.com")) {
            "Il download non proviene da GitHub."
        }
    }
}

enum class UpdatePhase { IDLE, CHECKING, CURRENT, AVAILABLE, DOWNLOADING, READY, ERROR }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val release: AppRelease? = null,
    val downloadedBytes: Long = 0,
    val message: String? = null,
)
