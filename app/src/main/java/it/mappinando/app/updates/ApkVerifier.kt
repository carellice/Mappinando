package it.mappinando.app.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import it.mappinando.app.BuildConfig
import java.io.File
import java.security.MessageDigest

internal object ApkVerifier {
    fun verify(context: Context, file: File, release: AppRelease, installedCode: Int = BuildConfig.VERSION_CODE) {
        require(file.isFile && file.length() == release.sizeBytes) { "Il pacchetto non è completo. Scaricalo di nuovo." }
        require(sha256(file) == release.sha256) { "Il file scaricato non supera il controllo di integrità. Scaricalo di nuovo." }
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: error("Il file non è un APK valido.")
        require(archive.packageName == BuildConfig.APPLICATION_ID) { "Il pacchetto non appartiene a Mappinando." }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        require(code == release.versionCode.toLong() && code > installedCode && archive.versionName == release.versionName) { "La versione del pacchetto non corrisponde all’aggiornamento." }
        @Suppress("DEPRECATION")
        val installed = manager.getPackageInfo(BuildConfig.APPLICATION_ID, flags)
        val expected = signerDigests(installed)
        require(expected.isNotEmpty() && expected == signerDigests(archive)) { "La firma del pacchetto è diversa da quella dell’app installata." }
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty() else info.signatures.orEmpty()
        return signatures.map { signature -> MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex() }.toSet()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
