package com.cadence.fit.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Moves backup JSON between the app and the user's storage.
 *
 * Exports are written to the app's own external files directory, which needs no
 * runtime permission and disappears with the app if it is uninstalled.
 */
class BackupService(private val context: Context) {

    fun exportDirectory(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, DIRECTORY_NAME).apply { if (!exists()) mkdirs() }
    }

    fun writeExport(json: String, fileName: String): File {
        val file = File(exportDirectory(), fileName)
        file.writeText(json)
        return file
    }

    fun existingExports(): List<File> =
        exportDirectory().listFiles()?.sortedByDescending { it.lastModified() }.orEmpty()

    fun deleteExport(file: File): Boolean = file.delete()

    fun readText(file: File): String = file.readText()

    /**
     * Writes to a destination the user picked through the system file picker,
     * so exports end up wherever they want them rather than in app storage.
     */
    fun writeToUri(uri: Uri, text: String) {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("The chosen location could not be opened for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    fun readFromUri(uri: Uri): String {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("The chosen file could not be opened")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    companion object {
        const val DIRECTORY_NAME = "exports"
        const val MIME_TYPE = "application/json"
    }
}
