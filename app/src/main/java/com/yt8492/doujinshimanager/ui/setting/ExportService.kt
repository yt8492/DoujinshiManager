package com.yt8492.doujinshimanager.ui.setting

import android.content.Context
import android.net.Uri
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class ExportDoujinshi(
    val id: String,
    val title: String,
    val circleId: String?,
    val eventId: String?,
    val pubDate: String?,
    val createdAt: String
)

@Serializable
data class ExportCircle(
    val id: String,
    val name: String
)

@Serializable
data class ExportAuthor(
    val id: String,
    val name: String
)

@Serializable
data class ExportTag(
    val id: String,
    val name: String
)

@Serializable
data class ExportEvent(
    val id: String,
    val name: String,
    val startAt: String?,
    val endAt: String?
)

@Serializable
data class ExportDoujinshiAuthor(
    val id: Long,
    val doujinshiId: String,
    val authorId: String
)

@Serializable
data class ExportDoujinshiTag(
    val id: Long,
    val doujinshiId: String,
    val tagId: String
)

@Serializable
data class ExportDoujinshiImage(
    val id: Long,
    val doujinshiId: String,
    val imagePath: String
)

class ExportService(
    private val context: Context,
    private val doujinshiRepository: DoujinshiRepository,
    private val circleRepository: CircleRepository,
    private val authorRepository: AuthorRepository,
    private val tagRepository: TagRepository,
    private val eventRepository: EventRepository
) {
    private val json = Json { prettyPrint = true }

    suspend fun exportData(outputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    // Repositoryからデータを取得してJSONファイルを作成
                    exportDoujinshis(zipOut)
                    exportCircles(zipOut)
                    exportAuthors(zipOut)
                    exportTags(zipOut)
                    exportEvents(zipOut)
                    
                    // 画像ファイルをエクスポート
                    exportImages(zipOut)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun exportDoujinshis(zipOut: ZipOutputStream) {
        // 全ての同人誌を取得するため、大きなサイズを指定
        var page = 0
        val pageSize = 100
        val allDoujinshis = mutableListOf<ExportDoujinshi>()
        val allDoujinshiAuthors = mutableListOf<ExportDoujinshiAuthor>()
        val allDoujinshiTags = mutableListOf<ExportDoujinshiTag>()
        val allDoujinshiImages = mutableListOf<ExportDoujinshiImage>()
        
        do {
            val result = doujinshiRepository.search(
                searchSpec = DoujinshiSearchSpec(),
                page = page,
                size = pageSize
            )
            
            result.list.forEach { doujinshi ->
                // 同人誌の基本情報
                allDoujinshis.add(
                    ExportDoujinshi(
                        id = doujinshi.id.value,
                        title = doujinshi.title,
                        circleId = doujinshi.circle?.id?.value,
                        eventId = doujinshi.event?.id?.value,
                        pubDate = doujinshi.pubDate?.toString(),
                        createdAt = doujinshi.createdAt.toString()
                    )
                )
                
                // 作者の関連データ
                doujinshi.authors.forEachIndexed { index, author ->
                    allDoujinshiAuthors.add(
                        ExportDoujinshiAuthor(
                            id = (page * pageSize + allDoujinshis.size - 1) * 1000L + index.toLong(),
                            doujinshiId = doujinshi.id.value,
                            authorId = author.id.value
                        )
                    )
                }
                
                // タグの関連データ
                doujinshi.tags.forEachIndexed { index, tag ->
                    allDoujinshiTags.add(
                        ExportDoujinshiTag(
                            id = (page * pageSize + allDoujinshis.size - 1) * 1000L + index.toLong(),
                            doujinshiId = doujinshi.id.value,
                            tagId = tag.id.value
                        )
                    )
                }
                
                // 画像の関連データ
                doujinshi.imagePaths.forEachIndexed { index, imagePath ->
                    allDoujinshiImages.add(
                        ExportDoujinshiImage(
                            id = (page * pageSize + allDoujinshis.size - 1) * 1000L + index.toLong(),
                            doujinshiId = doujinshi.id.value,
                            imagePath = File(imagePath).name // ファイル名のみに変換
                        )
                    )
                }
            }
            
            page++
        } while (result.hasNextPage)
        
        // 同人誌の基本情報をエクスポート
        val doujinshiJsonString = json.encodeToString(allDoujinshis)
        val doujinshiEntry = ZipEntry("doujinshis.json")
        zipOut.putNextEntry(doujinshiEntry)
        zipOut.write(doujinshiJsonString.toByteArray())
        zipOut.closeEntry()
        
        // 同人誌-作者の関連データをエクスポート
        val authorsJsonString = json.encodeToString(allDoujinshiAuthors)
        val authorsEntry = ZipEntry("doujinshi_authors.json")
        zipOut.putNextEntry(authorsEntry)
        zipOut.write(authorsJsonString.toByteArray())
        zipOut.closeEntry()
        
        // 同人誌-タグの関連データをエクスポート
        val tagsJsonString = json.encodeToString(allDoujinshiTags)
        val tagsEntry = ZipEntry("doujinshi_tags.json")
        zipOut.putNextEntry(tagsEntry)
        zipOut.write(tagsJsonString.toByteArray())
        zipOut.closeEntry()
        
        // 同人誌-画像の関連データをエクスポート
        val imagesJsonString = json.encodeToString(allDoujinshiImages)
        val imagesEntry = ZipEntry("doujinshi_images.json")
        zipOut.putNextEntry(imagesEntry)
        zipOut.write(imagesJsonString.toByteArray())
        zipOut.closeEntry()
    }

    private suspend fun exportCircles(zipOut: ZipOutputStream) {
        // getAllメソッドで全てのサークルを取得
        val circles = circleRepository.getAll()
            .map { 
                ExportCircle(
                    id = it.id.value,
                    name = it.name
                )
            }
        
        val jsonString = json.encodeToString(circles)
        val entry = ZipEntry("circles.json")
        zipOut.putNextEntry(entry)
        zipOut.write(jsonString.toByteArray())
        zipOut.closeEntry()
    }

    private suspend fun exportAuthors(zipOut: ZipOutputStream) {
        // getAllメソッドで全ての作者を取得
        val authors = authorRepository.getAll()
            .map { 
                ExportAuthor(
                    id = it.id.value,
                    name = it.name
                )
            }
        
        val jsonString = json.encodeToString(authors)
        val entry = ZipEntry("authors.json")
        zipOut.putNextEntry(entry)
        zipOut.write(jsonString.toByteArray())
        zipOut.closeEntry()
    }

    private suspend fun exportTags(zipOut: ZipOutputStream) {
        // getAllメソッドで全てのタグを取得
        val tags = tagRepository.getAll()
            .map { 
                ExportTag(
                    id = it.id.value,
                    name = it.name
                )
            }
        
        val jsonString = json.encodeToString(tags)
        val entry = ZipEntry("tags.json")
        zipOut.putNextEntry(entry)
        zipOut.write(jsonString.toByteArray())
        zipOut.closeEntry()
    }

    private suspend fun exportEvents(zipOut: ZipOutputStream) {
        // getAllメソッドで全てのイベントを取得
        val events = eventRepository.getAll()
            .map { 
                ExportEvent(
                    id = it.id.value,
                    name = it.name,
                    startAt = it.term?.start?.toString(),
                    endAt = it.term?.end?.toString()
                )
            }
        
        val jsonString = json.encodeToString(events)
        val entry = ZipEntry("events.json")
        zipOut.putNextEntry(entry)
        zipOut.write(jsonString.toByteArray())
        zipOut.closeEntry()
    }

    private suspend fun exportImages(zipOut: ZipOutputStream) {
        // 全ての同人誌から画像パスを取得
        var page = 0
        val pageSize = 100
        val allImagePaths = mutableSetOf<String>()
        
        do {
            val result = doujinshiRepository.search(
                searchSpec = DoujinshiSearchSpec(),
                page = page,
                size = pageSize
            )
            
            result.list.forEach { doujinshi ->
                allImagePaths.addAll(doujinshi.imagePaths)
            }
            
            page++
        } while (result.hasNextPage)

        for (imagePath in allImagePaths) {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val entry = ZipEntry("images/${imageFile.name}")
                zipOut.putNextEntry(entry)
                
                FileInputStream(imageFile).use { inputStream ->
                    inputStream.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }
        }
    }
}
