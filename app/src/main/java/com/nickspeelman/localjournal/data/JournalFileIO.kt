package com.nickspeelman.localjournal.data

import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.IOException

object JournalFileIO {
    fun writeText(contentResolver: ContentResolver, uri: Uri, content: String) {
        val stream = contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("Could not open the selected file for writing.")
        stream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(content)
        }
    }

    fun readText(
        contentResolver: ContentResolver,
        uri: Uri,
        maxBytes: Int = DEFAULT_MAX_BACKUP_BYTES
    ): String {
        val stream = contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the selected backup file.")
        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) {
                    throw IOException("This backup file is too large to import safely.")
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
        }
    }

    private const val DEFAULT_MAX_BACKUP_BYTES = 16 * 1024 * 1024
}
