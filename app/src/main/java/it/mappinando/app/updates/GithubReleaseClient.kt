package it.mappinando.app.updates

import it.mappinando.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal class GithubReleaseClient(private val repository: String = BuildConfig.GITHUB_REPOSITORY) {
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        val releaseText = readJson("https://api.github.com/repos/$repository/releases/latest", allowNotFound = true) ?: return@withContext null
        val release = JSONObject(releaseText)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@withContext null
        val tag = release.getString("tag_name")
        require(tag.matches(Regex("v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) { "La release non ha un formato riconoscibile." }
        val assets = release.getJSONArray("assets")
        val manifestAsset = (0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.optString("name") == UPDATE_MANIFEST_NAME }
            ?: throw IOException("Questa release non contiene i dati per l’aggiornamento. Riprova più tardi.")
        val manifestUrl = manifestAsset.getString("browser_download_url")
        ReleasePolicy.requireReleaseUrl(manifestUrl, repository, tag, UPDATE_MANIFEST_NAME)
        val manifest = JSONObject(checkNotNull(readJson(manifestUrl)))
        parseRelease(release, manifest, repository)
    }

    suspend fun download(release: AppRelease, destination: File, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        val connection = open(release.downloadUrl)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Il download non è disponibile. Riprova tra poco.")
            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastProgress = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        currentCoroutineContext().ensureActive()
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > release.sizeBytes) throw IOException("Il pacchetto scaricato ha dimensioni inattese.")
                        output.write(buffer, 0, count)
                        if (downloaded - lastProgress >= 256 * 1024 || downloaded == release.sizeBytes) { onProgress(downloaded); lastProgress = downloaded }
                    }
                    output.fd.sync()
                    if (downloaded != release.sizeBytes) throw IOException("Download incompleto. Controlla la connessione e riprova.")
                }
            }
        } finally { connection.disconnect() }
    }

    private fun readJson(url: String, allowNotFound: Boolean = false): String? {
        val connection = open(url)
        try {
            val status = connection.responseCode
            if (status == 404 && allowNotFound) return null
            if (status == 403 || status == 429) throw IOException("GitHub ha limitato temporaneamente le richieste. Riprova più tardi.")
            if (status != 200) throw IOException("Non riesco a leggere gli aggiornamenti. Controlla la connessione e riprova.")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val text = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    text.append(buffer, 0, count)
                    if (text.length > 1_048_576) throw IOException("Risposta di aggiornamento troppo grande.")
                }
                text.toString()
            }
        } finally { connection.disconnect() }
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        repeat(6) {
            ReleasePolicy.requireDownloadHost(current)
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000; connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "Mappinando/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", if (current.startsWith("https://api.github.com/")) "application/vnd.github+json" else "application/octet-stream")
            val status = try { connection.responseCode } catch (failure: Exception) { connection.disconnect(); throw failure }
            if (status in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                current = URL(URL(current), location ?: throw IOException("Download non disponibile.")).toString()
            } else return connection
        }
        throw IOException("Troppi reindirizzamenti durante il download.")
    }

    companion object {
        internal fun parseRelease(release: JSONObject, manifest: JSONObject, repository: String): AppRelease {
            require(!release.optBoolean("draft") && !release.optBoolean("prerelease"))
            require(manifest.getInt("schemaVersion") == 1 && manifest.getString("applicationId") == BuildConfig.APPLICATION_ID)
            val name = manifest.getString("versionName")
            val tag = release.getString("tag_name")
            require(tag == "v$name")
            val apk = manifest.getString("apk")
            val assets = release.getJSONArray("assets")
            val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.single { it.optString("name") == apk }
            val download = asset.getString("browser_download_url")
            ReleasePolicy.requireReleaseUrl(download, repository, tag, apk)
            require(asset.getString("state") == "uploaded" && asset.getLong("size") == manifest.getLong("sizeBytes"))
            val page = release.getString("html_url")
            require(page == "https://github.com/$repository/releases/tag/$tag")
            return AppRelease(name, manifest.getInt("versionCode"), apk, manifest.getString("sha256"), manifest.getLong("sizeBytes"),
                manifest.getInt("minSdk"), download, page, release.optString("body").take(8000))
        }
    }
}
