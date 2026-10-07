package tv.own.owntv.core.storage

import java.io.ByteArrayInputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class SelectedFileImportTest {
    @Test fun importsSelectedBytesWithoutTrustingProviderPath() {
        val root = Files.createTempDirectory("selected-file-test").toFile()
        try {
            val file = SelectedFileImport.copy(root, "../../list.M3U", setOf("m3u"), ByteArrayInputStream("#EXTM3U".toByteArray()), 64)
            assertEquals("#EXTM3U", file.readText())
            assertTrue(file.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator))
            assertEquals("m3u", file.extension.lowercase())
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsWrongFileTypeWithoutSavingAnything() {
        val root = Files.createTempDirectory("selected-file-test").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                SelectedFileImport.copy(root, "malware.apk", setOf("m3u"), ByteArrayInputStream(byteArrayOf(1)), 64)
            }
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun oversizedImportIsRemovedAndEarlierImportIsPreserved() {
        val root = Files.createTempDirectory("selected-file-test").toFile()
        try {
            val old = SelectedFileImport.copy(root, "a.srt", setOf("srt"), ByteArrayInputStream(byteArrayOf(1)), 4)
            assertThrows(java.io.IOException::class.java) {
                SelectedFileImport.copy(root, "a.srt", setOf("srt"), ByteArrayInputStream(ByteArray(5)), 4)
            }
            assertArrayEquals(byteArrayOf(1), old.readBytes())
            assertEquals(1, root.listFiles().orEmpty().size)
        } finally { root.deleteRecursively() }
    }

    @Test fun exactLimitSucceeds() {
        val root = Files.createTempDirectory("selected-file-test").toFile()
        try {
            val file = SelectedFileImport.copy(root, "a.srt", setOf("srt"), ByteArrayInputStream(ByteArray(4)), 4)
            assertEquals(4L, file.length())
        } finally { root.deleteRecursively() }
    }
}
