package de.wartezeiten.app.update

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ApkDownloaderTest {
    @Test
    fun computesKnownSha256ForEmptyFile() {
        val file = File.createTempFile("apk-downloader-test-empty", ".tmp")
        try {
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                file.sha256(),
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun computesKnownSha256ForKnownContent() {
        val file = File.createTempFile("apk-downloader-test-content", ".tmp")
        try {
            file.writeBytes("wartezeiten".toByteArray(Charsets.UTF_8))
            assertEquals(
                "9a10f23dde4aa0578a83820c53f2f1a34496ecf13ff9e44322b274e4e4bfa6ed",
                file.sha256(),
            )
        } finally {
            file.delete()
        }
    }
}
