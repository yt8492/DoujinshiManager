package com.yt8492.doujinshimanager.ui.setting

import android.content.Context
import android.net.Uri
import app.cash.sqldelight.db.use
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.yt8492.doujinshimanager.Constants
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.database.DoujinshiQueries
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class ImportService(
    private val context: Context,
) {
    fun importData(inputUri: Uri): Result<Unit> {
        return runCatching {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    val imagesDir = File(context.filesDir, Constants.imagesDirectoryName)
                    if (imagesDir.exists()) {
                        imagesDir.delete()
                    }
                    imagesDir.mkdirs()
                    val databaseFile = context.getDatabasePath(Constants.databaseName)
                    if (databaseFile.exists()) {
                        databaseFile.delete()
                    }
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            if (entry.name.startsWith("${Constants.imagesDirectoryName}/")) {
                                val fileName = entry.name.substringAfter("${Constants.imagesDirectoryName}/")
                                val imageFile = File(imagesDir, fileName)
                                FileOutputStream(imageFile).use { output ->
                                    zipIn.copyTo(output)
                                }
                            } else if (entry.name == Constants.databaseName) {
                                FileOutputStream(databaseFile).use { output ->
                                    zipIn.copyTo(output)
                                }
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            AndroidSqliteDriver(
                schema = Database.Schema,
                context = context,
                name = Constants.databaseName,
            ).use { driver ->
                val queries = DoujinshiQueries(driver)
                val imagesDir = File(context.filesDir, Constants.imagesDirectoryName)
                var offset = 0L
                var hasNextPage = true
                do {
                    val result = queries.findDoujinshiImages(limit = 30, offset = offset).executeAsList()
                    result.forEach { image ->
                        val fileName = image.image_path.substringAfter("${Constants.imagesDirectoryName}/")
                        queries.updateDoujinshiImage(
                            id = image.id,
                            image_path = "${imagesDir.path}/$fileName",
                        )
                    }
                    offset = offset + result.size
                    hasNextPage = result.isNotEmpty()
                } while (hasNextPage)
            }
        }
    }
}
