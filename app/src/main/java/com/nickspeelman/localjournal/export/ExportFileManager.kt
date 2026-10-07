package com.nickspeelman.localjournal.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object ExportFileManager {
    private const val DIR = "shared_exports"
    fun writeTemp(context: Context, name: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        cleanup(dir)
        val file = File(dir, name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    }
    private fun cleanup(dir: File) {
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        dir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { runCatching { it.delete() } }
    }
    fun share(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}
