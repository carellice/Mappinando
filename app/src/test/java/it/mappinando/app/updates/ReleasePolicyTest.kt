package it.mappinando.app.updates

import org.junit.Assert.*
import org.junit.Test

class ReleasePolicyTest {
    private fun release(name: String = "1.0.10", code: Int = 20, hash: String = "a".repeat(64), size: Long = 60_000_000, apk: String = "Mappinando-$name-debug.apk") =
        AppRelease(name, code, apk, hash, size, 26, "https://github.com/owner/repo/releases/download/v$name/$apk", "https://github.com/owner/repo/releases/tag/v$name", "Novità")

    @Test fun updatesCompareMonotonicAndroidVersionCodeRatherThanVersionText() {
        val next = release()
        assertTrue(next.isNewerThan(19)); assertFalse(next.isNewerThan(20)); assertFalse(next.isNewerThan(21))
        assertTrue(release("2.0.0", 21).isNewerThan(20))
    }

    @Test fun invalidOrUnboundedMetadataIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { release(hash = "bad") }
        assertThrows(IllegalArgumentException::class.java) { release(size = 0) }
        assertThrows(IllegalArgumentException::class.java) { release(size = 268_435_457) }
        assertThrows(IllegalArgumentException::class.java) { release(code = 0) }
        assertThrows(IllegalArgumentException::class.java) { release(name = "../1.0.0") }
        assertThrows(IllegalArgumentException::class.java) { release(apk = "../other.apk") }
    }

    @Test fun releaseAssetsMustBelongToTheConfiguredRepositoryAndTag() {
        ReleasePolicy.requireReleaseUrl("https://github.com/owner/repo/releases/download/v1.0.7/Mappinando-update.json", "owner/repo", "v1.0.7", "Mappinando-update.json")
        assertThrows(IllegalArgumentException::class.java) { ReleasePolicy.requireReleaseUrl("https://github.com/other/repo/releases/download/v1.0.7/Mappinando-update.json", "owner/repo", "v1.0.7", "Mappinando-update.json") }
        assertThrows(IllegalArgumentException::class.java) { ReleasePolicy.requireReleaseUrl("https://github.com/owner/repo/releases/download/v1.0.6/Mappinando-update.json", "owner/repo", "v1.0.7", "Mappinando-update.json") }
    }

    @Test fun onlyHttpsAndKnownGithubDownloadHostsAreAccepted() {
        ReleasePolicy.requireDownloadHost("https://api.github.com/repos/owner/repo/releases/latest")
        ReleasePolicy.requireDownloadHost("https://release-assets.githubusercontent.com/github-production-release-asset/file?token=public")
        listOf("http://github.com/file", "https://github.com.attacker.test/file", "https://user@github.com/file", "https://github.com:444/file", "file:///tmp/app.apk", "https://attacker.test/app.apk").forEach {
            assertThrows(IllegalArgumentException::class.java) { ReleasePolicy.requireDownloadHost(it) }
        }
    }
}
