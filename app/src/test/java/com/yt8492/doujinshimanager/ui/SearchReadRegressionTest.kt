package com.yt8492.doujinshimanager.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.shared.domain.model.*
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.domain.repository.EventRepository
import com.yt8492.doujinshimanager.shared.infra.repository.*
import com.yt8492.doujinshimanager.ui.search.SearchViewModel
import com.yt8492.doujinshimanager.ui.top.TopViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class, ObsoleteCoroutinesApi::class)
class SearchReadRegressionTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: Database
    private lateinit var repository: DoujinshiRepositoryImpl
    private val circle = Circle(CircleId("circle"), "Old circle name")

    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        db = Database(driver)
        repository = DoujinshiRepositoryImpl(db.doujinshiQueries)
    }
    @After fun cleanup() { driver.close(); Dispatchers.resetMain() }

    private fun book(i: Int) = Doujinshi(
        DoujinshiId("book-%03d".format(i)), "Book $i", null,
        emptyList(), emptyList(), null, null, emptyList(),
        Instant.parse("2026-10-17T00:00:00Z"),
    )

    private fun search(spec: DoujinshiSearchSpec) = SearchViewModel(
        spec, repository, CircleRepositoryImpl(db.circleQueries), AuthorRepositoryImpl(db.authorQueries),
        TagRepositoryImpl(db.tagQueries), EventRepositoryImpl(db.eventQueries), Dispatchers.Main,
    )

    @Test fun databaseReadsMustNotExecuteOnTheUiThread() = runBlocking {
        val mainThread = newSingleThreadContext("adversarial-ui-thread")
        Dispatchers.setMain(mainThread)
        try {
            repository.save(book(0))
            val queried = CompletableDeferred<String>()
            val observed = object : DoujinshiRepository by repository {
                override suspend fun search(searchSpec: DoujinshiSearchSpec, page: Int, size: Int): DoujinshiSearchResult {
                    val thread = Thread.currentThread().name
                    val result = repository.search(searchSpec, page, size)
                    queried.complete(thread)
                    return result
                }
            }
            val vm = withContext(Dispatchers.Main) { TopViewModel(observed).also { it.load() } }
            val thread = withTimeout(5000) { queried.await() }
            withTimeout(5000) { vm.uiState.first { it.bindingModel.list.size == 1 } }
            println("Actual SQLite query thread: $thread")
            assertFalse("SQLite reads execute on the UI dispatcher: $thread", thread.startsWith("adversarial-ui-thread"))
        } finally {
            Dispatchers.resetMain()
            mainThread.close()
        }
    }

    @Test fun relatedDataDoesNotMultiplyPageRowsAndRefreshKeepsEveryRelation() = runTest {
        val authors = (0 until 3).map { Author(AuthorId("a$it"), "Author $it") }
        val tags = (0 until 10).map { Tag(TagId("t$it"), "Tag $it") }
        authors.forEach { AuthorRepositoryImpl(db.authorQueries).save(it) }
        tags.forEach { TagRepositoryImpl(db.tagQueries).save(it) }
        repeat(101) { i -> repository.save(book(i).copy(
            authors = authors, tags = tags, imagePaths = (0 until 5).map { "cover-$i-$it.png" },
        )) }
        val start = System.nanoTime()
        val rows = db.doujinshiQueries.find(
            keywordQuery = null, circleId = null, eventId = null,
            authorsIsEmpty = true, authorIds = emptyList(), tagsIsEmpty = true,
            tagIds = emptyList(), tagsCount = 0, startDate = null, endDate = null,
            limit = 31, offset = 0,
        ).executeAsList()
        println("31 selected books produced ${rows.size} SQLite rows in ${(System.nanoTime() - start) / 1_000_000} ms")
        assertEquals(31, rows.map { it.id }.distinct().size)
        assertEquals(31, rows.size)
        val refreshStart = System.nanoTime()
        val refreshed = repository.search(DoujinshiSearchSpec(), page = 0, size = 120)
        println("Full loaded range: ${refreshed.list.size} books / ${refreshed.list.sumOf { 1 + it.authors.size + it.tags.size + it.imagePaths.size }} rows in ${(System.nanoTime() - refreshStart) / 1_000_000} ms")
        assertEquals(101, refreshed.list.size)
        assertFalse(refreshed.hasNextPage)
        refreshed.list.forEach {
            assertEquals(authors, it.authors)
            assertEquals(tags, it.tags)
            assertEquals(5, it.imagePaths.size)
            assertEquals("cover-${it.title.substringAfter(' ')}-0.png", it.imagePaths.first())
        }
    }

    @Test fun missingInitialFilterReferencesPreserveTheirIdsWithoutCrashingOrBroadeningSearch() = runTest {
        repository.save(book(0))
        val spec = DoujinshiSearchSpec(circle = CircleId("deleted-circle"),
            authors = listOf(AuthorId("deleted-author")), tags = listOf(TagId("deleted-tag")),
            event = EventId("deleted-event"))
        val vm = search(spec)
        advanceUntilIdle()
        val model = vm.bindingModel.value
        assertTrue(model.searchResult.isEmpty())
        assertNull(model.loadError)
        assertEquals(spec.circle, model.circle?.id)
        assertEquals(spec.authors, model.authors.map { it.id })
        assertEquals(spec.tags, model.tags.map { it.id })
        assertEquals(spec.event, model.event?.id)
        assertTrue(requireNotNull(model.circle).name.isNotBlank())
        assertTrue(model.authors.single().name.isNotBlank())
        assertTrue(model.tags.single().name.isNotBlank())
        assertTrue(requireNotNull(model.event).name.isNotBlank())
    }

    @Test fun metadataReadFailureIsDisplayedAndRetryRestoresNamesAndKeepsLoadedPages() = runTest {
        val events = EventRepositoryImpl(db.eventQueries)
        val event = Event(EventId("existing-event"), "Existing event", null)
        events.save(event)
        repeat(61) { repository.save(book(it).copy(event = event)) }
        var fail = false
        val observed = object : EventRepository by events {
            override suspend fun getOrNull(id: EventId): Event? {
                if (fail) throw IllegalStateException("transient metadata read failure")
                return events.getOrNull(id)
            }
        }
        val vm = SearchViewModel(
            DoujinshiSearchSpec(event = event.id), repository,
            CircleRepositoryImpl(db.circleQueries), AuthorRepositoryImpl(db.authorQueries),
            TagRepositoryImpl(db.tagQueries), observed, Dispatchers.Main,
        )
        advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        fail = true
        vm.refresh(); advanceUntilIdle()
        assertEquals(com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError.REFRESH, vm.bindingModel.value.loadError)
        assertEquals(60, vm.bindingModel.value.searchResult.size)
        assertEquals(1, vm.bindingModel.value.page)
        fail = false
        vm.retry(); advanceUntilIdle()
        assertNull(vm.bindingModel.value.loadError)
        assertEquals(event, vm.bindingModel.value.event)
        assertEquals(60, vm.bindingModel.value.searchResult.size)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(61, vm.bindingModel.value.searchResult.size)
        assertFalse(vm.bindingModel.value.hasNextPage)
    }

    @Test fun initialMetadataFailureAlsoSupportsRetry() = runTest {
        val events = EventRepositoryImpl(db.eventQueries)
        val event = Event(EventId("event"), "Event", null)
        events.save(event)
        repository.save(book(0).copy(event = event))
        var fail = true
        val observed = object : EventRepository by events {
            override suspend fun getOrNull(id: EventId): Event? {
                if (fail) error("temporary failure")
                return events.getOrNull(id)
            }
        }
        val vm = SearchViewModel(DoujinshiSearchSpec(event = event.id), repository,
            CircleRepositoryImpl(db.circleQueries), AuthorRepositoryImpl(db.authorQueries),
            TagRepositoryImpl(db.tagQueries), observed, Dispatchers.Main)
        advanceUntilIdle()
        assertNotNull(vm.bindingModel.value.loadError)
        assertEquals(1, vm.bindingModel.value.searchResult.size)
        fail = false
        vm.retry(); advanceUntilIdle()
        assertNull(vm.bindingModel.value.loadError)
        assertEquals(event, vm.bindingModel.value.event)
    }

    @Test fun supersededMetadataCannotReplaceTheNewFilterOrItsErrorState() = runTest {
        val events = EventRepositoryImpl(db.eventQueries)
        val old = Event(EventId("old"), "Old", null)
        val new = Event(EventId("new"), "New", null)
        events.save(old); events.save(new)
        repository.save(book(0).copy(event = new))
        val gate = CompletableDeferred<Unit>()
        val observed = object : EventRepository by events {
            override suspend fun getOrNull(id: EventId): Event? {
                if (id == old.id) withContext(NonCancellable) { gate.await() }
                return events.getOrNull(id)
            }
        }
        val vm = SearchViewModel(DoujinshiSearchSpec(event = old.id), repository,
            CircleRepositoryImpl(db.circleQueries), AuthorRepositoryImpl(db.authorQueries),
            TagRepositoryImpl(db.tagQueries), observed, Dispatchers.Main)
        runCurrent()
        vm.onSelectEvent(new); runCurrent()
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(new, vm.bindingModel.value.event)
        assertNull(vm.bindingModel.value.loadError)
        assertEquals(book(0).id, vm.bindingModel.value.searchResult.single().id)
    }

    @Test fun fullRangeRefreshHydratesMoreThanSqlitesParameterLimit() = runTest {
        val author = Author(AuthorId("a"), "Author")
        val tag = Tag(TagId("t"), "Tag")
        AuthorRepositoryImpl(db.authorQueries).save(author)
        TagRepositoryImpl(db.tagQueries).save(tag)
        repeat(1001) { repository.save(book(it).copy(authors = listOf(author), tags = listOf(tag),
            imagePaths = listOf("cover-$it.png"))) }
        val result = repository.search(size = 1020)
        assertEquals(1001, result.list.size)
        assertEquals(1001, result.list.map { it.id }.distinct().size)
        assertFalse(result.hasNextPage)
        result.list.forEach {
            assertEquals(listOf(author), it.authors)
            assertEquals(listOf(tag), it.tags)
            assertEquals(listOf("cover-${it.title.substringAfter(' ')}.png"), it.imagePaths)
        }
    }

    @Test fun refreshMustUpdateRenamedSelectedCircle() = runTest {
        val circles = CircleRepositoryImpl(db.circleQueries)
        circles.save(circle)
        repository.save(book(0).copy(circle = circle))
        val vm = search(DoujinshiSearchSpec(circle = circle.id))
        advanceUntilIdle()
        circles.update(circle.copy(name = "Renamed circle"))
        vm.refresh(); advanceUntilIdle()
        assertEquals("Renamed circle", vm.bindingModel.value.searchResult.single().circleName)
        assertEquals("Renamed circle", vm.bindingModel.value.circle?.name)
    }
}
