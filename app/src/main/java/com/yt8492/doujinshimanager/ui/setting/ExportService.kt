package com.yt8492.doujinshimanager.ui.setting

import android.content.Context
import android.net.Uri
import com.yt8492.doujinshimanager.Constants
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportService(
    private val context: Context,
) {
    fun export(outputUri: Uri): Result<Unit> {
        return runCatching {
            val dbFile = context.getDatabasePath(Constants.databaseName)
            val imagesDir = File(context.filesDir, Constants.imagesDirectoryName)
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    dbFile.inputStream().use { dbInputStream ->
                        val zipEntry = ZipEntry(Constants.databaseName)
                        zipOut.putNextEntry(zipEntry)
                        dbInputStream.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                    imagesDir.list()?.forEach { fileName ->
                        val image = File(imagesDir, fileName)
                        if (image.isDirectory) {
                            return@forEach
                        }
                        val zipEntry = ZipEntry("${Constants.imagesDirectoryName}/$fileName")
                        zipOut.putNextEntry(zipEntry)
                        image.inputStream().use { imageInputStream ->
                            imageInputStream.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
        }
    }
}
