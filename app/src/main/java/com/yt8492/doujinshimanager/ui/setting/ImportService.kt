package com.yt8492.doujinshimanager.ui.setting

import android.content.Context
import android.net.Uri
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.AuthorId
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.EventId
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.model.TagId
import com.yt8492.doujinshimanager.shared.domain.repository.AuthorRepository
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.domain.repository.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class ImportService(
    private val context: Context,
    private val doujinshiRepository: DoujinshiRepository,
    private val circleRepository: CircleRepository,
    private val authorRepository: AuthorRepository,
    private val tagRepository: TagRepository,
    private val eventRepository: EventRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importData(inputUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            contentResolver.openInputStream(inputUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    // データを格納するマップ
                    val dataMap = mutableMapOf<String, String>()
                    val imagesDir = File(context.filesDir, "images")
                    
                    // imagesディレクトリを作成
                    if (!imagesDir.exists()) {
                        imagesDir.mkdirs()
                    }
                    
                    // ZIPファイルからデータを読み込み
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            if (entry.name.startsWith("images/")) {
                                // 画像ファイルを保存
                                val fileName = entry.name.substringAfter("images/")
                                val imageFile = File(imagesDir, fileName)
                                FileOutputStream(imageFile).use { output ->
                                    zipIn.copyTo(output)
                                }
                            } else if (entry.name.endsWith(".json")) {
                                // JSONデータを読み込み
                                val content = zipIn.readBytes().toString(Charsets.UTF_8)
                                dataMap[entry.name] = content
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                    
                    // データベースをクリア
                    clearDatabase()
                    
                    // データをインポート（外部キー制約を考慮した順序）
                    importCircles(dataMap["circles.json"])
                    importAuthors(dataMap["authors.json"])
                    importTags(dataMap["tags.json"])
                    importEvents(dataMap["events.json"])
                    importDoujinshis(dataMap["doujinshis.json"], dataMap["doujinshi_authors.json"], 
                                   dataMap["doujinshi_tags.json"], dataMap["doujinshi_images.json"])
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun clearDatabase() {
        // 外部キー制約を考慮して削除順序を決定
        // 同人誌とその関連データを最初に削除
        doujinshiRepository.deleteAll()
        
        // その他のテーブルをクリア
        circleRepository.deleteAll()
        authorRepository.deleteAll()
        tagRepository.deleteAll()
        eventRepository.deleteAll()
    }

    private suspend fun importCircles(jsonData: String?) {
        jsonData?.let { data ->
            val circles = json.decodeFromString<List<ExportCircle>>(data)
            circles.forEach { exportCircle ->
                val circle = Circle(
                    id = CircleId(exportCircle.id),
                    name = exportCircle.name
                )
                circleRepository.save(circle)
            }
        }
    }

    private suspend fun importAuthors(jsonData: String?) {
        jsonData?.let { data ->
            val authors = json.decodeFromString<List<ExportAuthor>>(data)
            authors.forEach { exportAuthor ->
                val author = Author(
                    id = AuthorId(exportAuthor.id),
                    name = exportAuthor.name
                )
                authorRepository.save(author)
            }
        }
    }

    private suspend fun importTags(jsonData: String?) {
        jsonData?.let { data ->
            val tags = json.decodeFromString<List<ExportTag>>(data)
            tags.forEach { exportTag ->
                val tag = Tag(
                    id = TagId(exportTag.id),
                    name = exportTag.name
                )
                tagRepository.save(tag)
            }
        }
    }

    private suspend fun importEvents(jsonData: String?) {
        jsonData?.let { data ->
            val events = json.decodeFromString<List<ExportEvent>>(data)
            events.forEach { exportEvent ->
                val event = Event(
                    id = EventId(exportEvent.id),
                    name = exportEvent.name,
                    term = if (exportEvent.startAt != null && exportEvent.endAt != null) {
                        Period(
                            start = LocalDate.parse(exportEvent.startAt),
                            end = LocalDate.parse(exportEvent.endAt)
                        )
                    } else {
                        null
                    }
                )
                eventRepository.save(event)
            }
        }
    }

    private suspend fun importDoujinshis(
        doujinshisJson: String?,
        authorsJson: String?,
        tagsJson: String?,
        imagesJson: String?
    ) {
        doujinshisJson?.let { data ->
            val doujinshis = json.decodeFromString<List<ExportDoujinshi>>(data)
            val doujinshiAuthors = authorsJson?.let { 
                json.decodeFromString<List<ExportDoujinshiAuthor>>(it) 
            } ?: emptyList()
            val doujinshiTags = tagsJson?.let { 
                json.decodeFromString<List<ExportDoujinshiTag>>(it) 
            } ?: emptyList()
            val doujinshiImages = imagesJson?.let { 
                json.decodeFromString<List<ExportDoujinshiImage>>(it) 
            } ?: emptyList()

            doujinshis.forEach { exportDoujinshi ->
                // 関連する作者を取得
                val authors = doujinshiAuthors
                    .filter { it.doujinshiId == exportDoujinshi.id }
                    .map { doujinshiAuthor ->
                        Author(
                            id = AuthorId(doujinshiAuthor.authorId),
                            name = "",
                        )
                    }

                // 関連するタグを取得
                val tags = doujinshiTags
                    .filter { it.doujinshiId == exportDoujinshi.id }
                    .map { doujinshiTag ->
                        Tag(
                            id = TagId(doujinshiTag.tagId),
                            name = "",
                        )
                    }

                // 関連する画像パスを取得（絶対パスに変換）
                val imagePaths = doujinshiImages
                    .filter { it.doujinshiId == exportDoujinshi.id }
                    .map { "${context.filesDir}/images/${it.imagePath}" }

                val doujinshi = Doujinshi(
                    id = DoujinshiId(exportDoujinshi.id),
                    title = exportDoujinshi.title,
                    circle = exportDoujinshi.circleId?.let { circleId ->
                        Circle(
                            id = CircleId(circleId),
                            name = "",
                        )
                    },
                    event = exportDoujinshi.eventId?.let { eventId ->
                        Event(
                            id = EventId(eventId),
                            name = "",
                            term = null,
                        )
                    },
                    authors = authors,
                    tags = tags,
                    pubDate = exportDoujinshi.pubDate?.let { LocalDate.parse(it) },
                    imagePaths = imagePaths,
                    createdAt = Instant.parse(exportDoujinshi.createdAt),
                )

                doujinshiRepository.save(doujinshi)
            }
        }
    }
}
