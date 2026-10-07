package tv.own.owntv.core.storage

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** Imports only a document explicitly selected by the user, never a provider-supplied path. */
object SelectedFileImport {
    fun copy(root: File, displayName: String, extensions: Set<String>?, input: InputStream, maxBytes: Long = 64L * 1024 * 1024): File {
        val name = displayName.replace('\\', '/').substringAfterLast('/').ifBlank { "selected" }
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extensions == null || extension in extensions) { "Unsupported file type" }
        require(maxBytes > 0)
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create import folder")
        val safeName = name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").takeLast(120)
        val target = File.createTempFile("selected-", "-$safeName", root)
        try {
            target.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    total += size
                    if (total > maxBytes) throw IOException("Selected file exceeds size limit")
                    output.write(buffer, 0, size)
                }
            }
            return target
        } catch (failure: Throwable) {
            target.delete()
            throw failure
        }
    }
}
