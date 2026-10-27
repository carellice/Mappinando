package it.mappinando.app.updates

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.mappinando.app.BuildConfig
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateSecurityInstrumentedTest {
    private val repo = "owner/Mappinando"
    private fun manifest() = JSONObject().put("schemaVersion", 1).put("applicationId", BuildConfig.APPLICATION_ID)
        .put("versionName", "1.0.7").put("versionCode", 8).put("minSdk", 26).put("apk", "Mappinando-1.0.7-debug.apk")
        .put("sha256", "a".repeat(64)).put("sizeBytes", 12345)
    private fun release() = JSONObject().put("draft", false).put("prerelease", false).put("tag_name", "v1.0.7")
        .put("html_url", "https://github.com/$repo/releases/tag/v1.0.7").put("body", "Nuova versione")
        .put("assets", JSONArray().put(JSONObject().put("name", "Mappinando-1.0.7-debug.apk").put("state", "uploaded").put("size", 12345)
            .put("browser_download_url", "https://github.com/$repo/releases/download/v1.0.7/Mappinando-1.0.7-debug.apk")))

    @Test fun stableReleaseMetadataAndAssetAreParsedTogether() {
        val result = GithubReleaseClient.parseRelease(release(), manifest(), repo)
        assertEquals(8, result.versionCode); assertEquals("1.0.7", result.versionName)
        assertEquals(12345L, result.sizeBytes); assertEquals("Nuova versione", result.notes)
    }

    @Test fun wrongApplicationVersionUnfinishedAssetAndUntrustedUrlsAreRejected() {
        fun rejected(release: JSONObject = release(), manifest: JSONObject = manifest()) {
            var failed = false
            try { GithubReleaseClient.parseRelease(release, manifest, repo) } catch (_: Exception) { failed = true }
            assertTrue("Untrusted metadata accepted", failed)
        }
        rejected(manifest = manifest().put("applicationId", "other.app"))
        rejected(release = release().put("draft", true))
        rejected(release = release().put("prerelease", true))
        rejected(release = release().put("tag_name", "v1.0.8"))
        val incomplete = release(); incomplete.getJSONArray("assets").getJSONObject(0).put("state", "new"); rejected(incomplete)
        val size = release(); size.getJSONArray("assets").getJSONObject(0).put("size", 1); rejected(size)
        val url = release(); url.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://attacker.test/app.apk"); rejected(url)
    }

    @Test fun realInstalledApkPassesIntegrityPackageAndSignatureVerification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apk = File(context.applicationInfo.sourceDir)
        val release = AppRelease(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "Mappinando-${BuildConfig.VERSION_NAME}-debug.apk", ApkVerifier.sha256(apk), apk.length(), 26, "", "", "")
        ApkVerifier.verify(context, apk, release, installedCode = BuildConfig.VERSION_CODE - 1)
        assertThrows(IllegalArgumentException::class.java) { ApkVerifier.verify(context, apk, release) }
        assertThrows(IllegalArgumentException::class.java) { ApkVerifier.verify(context, apk, release.copy(sha256 = "0".repeat(64)), installedCode = 0) }
    }

    @Test fun anotherPackageCannotBeInstalledAsAMappinandoUpdate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = File(instrumentation.context.applicationInfo.sourceDir)
        val release = AppRelease("1.0.7", 8, "Mappinando-1.0.7-debug.apk", ApkVerifier.sha256(apk), apk.length(), 26, "", "", "")
        val error = assertThrows(IllegalArgumentException::class.java) { ApkVerifier.verify(instrumentation.targetContext, apk, release, 0) }
        assertTrue(error.message.orEmpty().contains("non appartiene"))
    }
}
