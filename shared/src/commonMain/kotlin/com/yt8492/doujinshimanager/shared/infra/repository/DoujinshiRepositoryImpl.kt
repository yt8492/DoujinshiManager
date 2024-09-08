package com.yt8492.doujinshimanager.shared.infra.repository

import com.yt8492.doujinshimanager.database.DoujinshiQueries
import com.yt8492.doujinshimanager.shared.domain.model.Author
import com.yt8492.doujinshimanager.shared.domain.model.AuthorId
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchResult
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.model.Event
import com.yt8492.doujinshimanager.shared.domain.model.EventId
import com.yt8492.doujinshimanager.shared.domain.model.Period
import com.yt8492.doujinshimanager.shared.domain.model.Tag
import com.yt8492.doujinshimanager.shared.domain.model.TagId
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

class DoujinshiRepositoryImpl(
    private val doujinshiQueries: DoujinshiQueries,
) : DoujinshiRepository {
    override suspend fun search(searchSpec: DoujinshiSearchSpec, page: Int, size: Int): DoujinshiSearchResult {
        val result = doujinshiQueries.find(
            keywordQuery = searchSpec.keyword?.let { "%$it%" },
            circleId = searchSpec.circle?.value,
            eventId = searchSpec.event?.value,
            authorsIsEmpty = searchSpec.authors.isEmpty(),
            authorIds = searchSpec.authors.map { it.value },
            tagsIsEmpty = searchSpec.tags.isEmpty(),
            tagIds = searchSpec.tags.map { it.value },
            tagsCount = searchSpec.tags.size.toLong(),
            startDate = searchSpec.pubTerm?.start?.toString(),
            endDate = searchSpec.pubTerm?.end?.toString(),
            limit = size.toLong(),
            offset = (page * size).toLong()
        )
            .executeAsList()
            .groupBy {
                it.id
            }
            .map { entry ->
                val doujinshis = entry.value
                val doujinshi = doujinshis.first()
                Doujinshi(
                    id = DoujinshiId(entry.key),
                    title = doujinshi.title,
                    circle = if (doujinshi.id_ != null && doujinshi.name != null) {
                        Circle(
                            id = CircleId(doujinshi.id_),
                            name = doujinshi.name,
                        )
                    } else {
                        null
                    },
                    event = if (doujinshi.id__ != null && doujinshi.name_ != null) {
                        Event(
                            id = EventId(doujinshi.id__),
                            name = doujinshi.name_,
                            term = if (doujinshi.start_at != null && doujinshi.end_at != null) {
                                Period(
                                    start = LocalDate.parse(doujinshi.start_at),
                                    end = LocalDate.parse(doujinshi.end_at)
                                )
                            } else {
                                null
                            },
                        )
                    } else {
                        null
                    },
                    authors = doujinshis.mapNotNull {
                        if (it.id____ != null && it.name__ != null) {
                            Author(
                                id = AuthorId(it.id____),
                                name = it.name__,
                            )
                        } else {
                            null
                        }
                    }.distinct(),
                    tags = doujinshis.mapNotNull {
                        if (it.id______ != null && it.name___ != null) {
                            Tag(
                                id = TagId(it.id______),
                                name = it.name___,
                            )
                        } else {
                            null
                        }
                    }.distinct(),
                    pubDate = doujinshi.pub_date?.let {
                        LocalDate.parse(it)
                    },
                    imagePaths = doujinshis.mapNotNull {
                        it.image_path
                    }.distinct(),
                    createdAt = Instant.parse(doujinshi.created_at),
                )
            }
        return DoujinshiSearchResult(
            list = result,
            hasNextPage = result.size == size,
        )
    }

    override suspend fun get(id: DoujinshiId): Doujinshi {
        val doujinshis = doujinshiQueries.findById(id.value).executeAsList()
        val doujinshi = doujinshis.first()
        return Doujinshi(
            id = DoujinshiId(doujinshi.id),
            title = doujinshi.title,
            circle = if (doujinshi.id_ != null && doujinshi.name != null) {
                Circle(
                    id = CircleId(doujinshi.id_),
                    name = doujinshi.name,
                )
            } else {
                null
            },
            event = if (doujinshi.id__ != null && doujinshi.name_ != null) {
                Event(
                    id = EventId(doujinshi.id__),
                    name = doujinshi.name_,
                    term = if (doujinshi.start_at != null && doujinshi.end_at != null) {
                        Period(
                            start = LocalDate.parse(doujinshi.start_at),
                            end = LocalDate.parse(doujinshi.end_at)
                        )
                    } else {
                        null
                    },
                )
            } else {
                null
            },
            authors = doujinshis.mapNotNull {
                if (it.id____ != null && it.name__ != null) {
                    Author(
                        id = AuthorId(it.id____),
                        name = it.name__,
                    )
                } else {
                    null
                }
            }.distinct(),
            tags = doujinshis.mapNotNull {
                if (it.id______ != null && it.name___ != null) {
                    Tag(
                        id = TagId(it.id______),
                        name = it.name___,
                    )
                } else {
                    null
                }
            }.distinct(),
            pubDate = doujinshi.pub_date?.let {
                LocalDate.parse(it)
            },
            imagePaths = doujinshis.mapNotNull {
                it.image_path
            }.distinct(),
            createdAt = Instant.parse(doujinshi.created_at),
        )
    }

    override suspend fun save(doujinshi: Doujinshi) {
        doujinshiQueries.transaction {
            doujinshiQueries.insertDoujinshi(
                id = doujinshi.id.value,
                title = doujinshi.title,
                circle_id = doujinshi.circle?.id?.value,
                event_id = doujinshi.event?.id?.value,
                pub_date = doujinshi.pubDate?.toString(),
                created_at = doujinshi.createdAt.toString()
            )
            doujinshi.authors.forEach {
                doujinshiQueries.insertDoujinshisAuthors(
                    doujinshi_id = doujinshi.id.value,
                    author_id = it.id.value,
                )
            }
            doujinshi.tags.forEach {
                doujinshiQueries.insertDoujinshisTags(
                    doujinshi_id = doujinshi.id.value,
                    tag_id = it.id.value,
                )
            }
            doujinshi.imagePaths.forEach {
                doujinshiQueries.insertDoujinshiImages(
                    doujinshi_id = doujinshi.id.value,
                    image_path = it,
                )
            }
        }
    }

    override suspend fun update(doujinshi: Doujinshi) {
        val saved = get(doujinshi.id)
        val addedAuthors = doujinshi.authors - saved.authors
        val removedAuthors = saved.authors - doujinshi.authors
        val addedTags = doujinshi.tags - saved.tags
        val removedTags = saved.tags - doujinshi.tags
        val addedImages = doujinshi.imagePaths - saved.imagePaths
        val removedImages = saved.imagePaths - doujinshi.imagePaths
        doujinshiQueries.transaction {
            doujinshiQueries.updateDoujinshi(
                id = doujinshi.id.value,
                title = doujinshi.title,
                pub_date = doujinshi.pubDate?.toString(),
            )
            removedAuthors.forEach {
                doujinshiQueries.removeDoujinshisAuthors(
                    doujinshi_id = doujinshi.id.value,
                    author_id = it.id.value,
                )
            }
            addedAuthors.forEach {
                doujinshiQueries.insertDoujinshisAuthors(
                    doujinshi_id = doujinshi.id.value,
                    author_id = it.id.value,
                )
            }
            removedTags.forEach {
                doujinshiQueries.removeDoujinshisTags(
                    doujinshi_id = doujinshi.id.value,
                    tag_id = it.id.value,
                )
            }
            addedTags.forEach {
                doujinshiQueries.insertDoujinshisTags(
                    doujinshi_id = doujinshi.id.value,
                    tag_id = it.id.value,
                )
            }
            removedImages.forEach {
                doujinshiQueries.removeDoujinshiImages(
                    doujinshi_id = doujinshi.id.value,
                    image_path = it,
                )
            }
            addedImages.forEach {
                doujinshiQueries.insertDoujinshiImages(
                    doujinshi_id = doujinshi.id.value,
                    image_path = it,
                )
            }
        }
    }

    override suspend fun delete(id: DoujinshiId) {
        doujinshiQueries.transaction {
            doujinshiQueries.deleteDoujinshiAuthorsByDoujinshiId(id.value)
            doujinshiQueries.deleteDoujinshiTagsByDoujinshiId(id.value)
            doujinshiQueries.deleteDoujinshiImagesByDoujinshiId(id.value)
            doujinshiQueries.deleteDoujinshiById(id.value)
        }
    }
}
