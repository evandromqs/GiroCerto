package com.girocerto.update

import org.junit.Assert.*
import org.junit.Test

class AppReleaseTest {
    private val hash = "a".repeat(64)
    private val base = "https://github.com/evandromqs/GiroCerto/releases/download/"
    @Test fun comparesBuildNumbersNotAlphabeticalVersionNames() {
        val release = AppRelease.validated(10, "1.0.10", hash, base + "v1.0.10/GiroCerto-1.0.10.apk")
        assertTrue(release.isNewerThan(9))
        assertFalse(release.isNewerThan(10))
        assertFalse(release.isNewerThan(11))
        assertEquals("GiroCerto-1.0.10.apk", release.fileName)
    }
    @Test fun supportsExistingManifestsWithoutVersionedAssetName() {
        assertEquals(5L, AppRelease.validated(5, "1.0.5", hash, base + "v1.0.5/GiroCerto.apk").versionCode)
    }
    @Test fun requiresExactOfficialRepositoryAndHttps() {
        for (url in listOf(base.replace("https", "http") + "v1.0.6/GiroCerto-1.0.6.apk",
            base.replace("github.com", "github.com.evil.test") + "v1.0.6/GiroCerto-1.0.6.apk",
            base.replace("evandromqs", "someone") + "v1.0.6/GiroCerto-1.0.6.apk")) reject(url = url)
    }
    @Test fun mismatchedVersionOrArtifactIsRejected() {
        reject(url = base + "v1.0.5/GiroCerto-1.0.6.apk")
        reject(url = base + "v1.0.6/another.apk")
    }
    @Test fun unsafeUrlsAreRejected() {
        for (url in listOf(base + "v1.0.6/GiroCerto-1.0.6.apk?redirect=elsewhere",
            base + "v1.0.6/GiroCerto-1.0.6.apk#fragment",
            base.replace("github.com", "user@github.com") + "v1.0.6/GiroCerto-1.0.6.apk")) reject(url = url)
    }
    @Test fun checksumMustBeCompleteHex() {
        reject(checksum = "a".repeat(63))
        reject(checksum = "z".repeat(64))
    }
    @Test fun invalidBuildNumberAndUnsafeVersionNamesAreRejected() {
        reject(code = 0)
        reject(code = Long.MAX_VALUE)
        reject(name = "../1.0.6")
    }
    private fun reject(code: Long = 6, name: String = "1.0.6", checksum: String = hash,
                       url: String = base + "v1.0.6/GiroCerto-1.0.6.apk") {
        try { AppRelease.validated(code, name, checksum, url); fail("Invalid manifest accepted") }
        catch (_: IllegalArgumentException) { }
    }
}
