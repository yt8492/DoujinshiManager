package com.yt8492.doujinshimanager.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.shared.domain.model.*
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.infra.repository.*
import com.yt8492.doujinshimanager.ui.circledetail.CircleDetailViewModel
import com.yt8492.doujinshimanager.ui.detail.DetailViewModel
import com.yt8492.doujinshimanager.ui.edit.EditViewModel
import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError
import com.yt8492.doujinshimanager.ui.lib.DoujinshiPageState
import com.yt8492.doujinshimanager.ui.lib.DoujinshiPager
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import com.yt8492.doujinshimanager.ui.search.SearchViewModel
import com.yt8492.doujinshimanager.ui.top.TopViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PaginationAndEditTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: Database
    private lateinit var repository: DoujinshiRepositoryImpl
    private lateinit var circles: CircleRepositoryImpl
    private lateinit var authors: AuthorRepositoryImpl
    private lateinit var tags: TagRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private val circleA = Circle(CircleId("circle-a"), "Circle A")
    private val circleB = Circle(CircleId("circle-b"), "Circle B")
    private val eventA = Event(EventId("event-a"), "Event A", null)
    private val eventB = Event(EventId("event-b"), "Event B", null)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        db = Database(driver)
        repository = DoujinshiRepositoryImpl(db.doujinshiQueries)
        circles = CircleRepositoryImpl(db.circleQueries)
        authors = AuthorRepositoryImpl(db.authorQueries)
        tags = TagRepositoryImpl(db.tagQueries)
        events = EventRepositoryImpl(db.eventQueries)
    }

    @After
    fun tearDown() { driver.close(); Dispatchers.resetMain() }

    private fun book(index: Int) = Doujinshi(
        id = DoujinshiId("book-%03d".format(index)), title = "Book $index", circle = null,
        authors = emptyList(), tags = emptyList(), event = null, pubDate = null,
        imagePaths = emptyList(), createdAt = Instant.parse("2026-10-17T00:00:00Z"),
    )

    private suspend fun seed(count: Int) {
        // Insert out of ID order with equal timestamps: ordering must not depend on row insertion.
        ((0 until count).filter { it % 2 == 0 } + (0 until count).filter { it % 2 == 1 })
            .forEach { repository.save(book(it)) }
    }

    private fun search(spec: DoujinshiSearchSpec = DoujinshiSearchSpec(), repo: DoujinshiRepository = repository) =
        SearchViewModel(spec, repo, circles, authors, tags, events)

    private fun edit(id: DoujinshiId) = EditViewModel(id, circles, authors, tags, events, repository)

    @Test
    fun repositoryPaginates101TiedTimestampsWithoutMissingOrDuplicateBooks() = runTest {
        seed(101)
        val results = (0..3).map { repository.search(page = it) }
        assertEquals(listOf(30, 30, 30, 11), results.map { it.list.size })
        assertEquals(listOf(true, true, true, false), results.map { it.hasNextPage })
        assertEquals((100 downTo 0).map { book(it).id }, results.flatMap { it.list }.map { it.id })
        assertTrue(repository.search(page = 4).list.isEmpty())
    }

    @Test
    fun exactPageBoundariesAndEmptyDatabaseReportTheActualEnd() = runTest {
        for (count in listOf(0, 1, 29, 30, 31, 60, 90)) {
            repository.deleteAll()
            seed(count)
            val lastPage = ((count - 1).coerceAtLeast(0)) / 30
            val result = repository.search(page = lastPage)
            assertFalse("count=$count", result.hasNextPage)
            assertEquals(if (count == 0) 0 else (count - lastPage * 30), result.list.size)
        }
    }

    @Test
    fun searchHydratesAllRelationsAndKeepsTheFirstCoverAtPageBoundaries() = runTest {
        val author1 = Author(AuthorId("author-1"), "A1")
        val author2 = Author(AuthorId("author-2"), "A2")
        val tag1 = Tag(TagId("tag-1"), "T1")
        val tag2 = Tag(TagId("tag-2"), "T2")
        authors.save(author1); authors.save(author2); tags.save(tag1); tags.save(tag2)
        for (i in 0..30) repository.save(book(i).copy(
            authors = listOf(author1, author2), tags = listOf(tag1, tag2),
            imagePaths = listOf("cover-$i.png", "back-$i.png"),
        ))
        val result = repository.search(DoujinshiSearchSpec(authors = listOf(author1.id), tags = listOf(tag1.id, tag2.id)))
        assertEquals(30, result.list.size)
        result.list.forEach {
            assertEquals(setOf(author1, author2), it.authors.toSet())
            assertEquals(setOf(tag1, tag2), it.tags.toSet())
            assertEquals(listOf("cover-${it.title.substringAfter(' ')}.png", "back-${it.title.substringAfter(' ')}.png"), it.imagePaths)
        }
        assertEquals(1, repository.search(page = 1).list.size)
    }

    @Test
    fun editPersistsCircleAndEventChangesIncludingRemovingBoth() = runTest {
        circles.save(circleA); circles.save(circleB); events.save(eventA); events.save(eventB)
        val original = book(0).copy(circle = circleA, event = eventA)
        repository.save(original)
        val vm = edit(original.id); advanceUntilIdle()
        vm.onSelectCircle(circleB); vm.onSelectEvent(eventB); vm.onInputTitle("Updated")
        vm.onSelectImages(listOf("new-cover.png")); vm.onClickSave(); advanceUntilIdle()
        val changed = requireNotNull(repository.get(original.id))
        assertEquals(circleB, changed.circle); assertEquals(eventB, changed.event)
        assertEquals("Updated", changed.title); assertEquals(listOf("new-cover.png"), changed.imagePaths)
        assertEquals(original.createdAt, changed.createdAt)
        assertTrue(repository.search(DoujinshiSearchSpec(circle = circleA.id)).list.isEmpty())
        assertEquals(original.id, repository.search(DoujinshiSearchSpec(circle = circleB.id)).list.single().id)
        val reopened = edit(original.id); advanceUntilIdle()
        reopened.onDeleteEvent(); reopened.onDeleteCircle(); reopened.onClickSave(); advanceUntilIdle()
        assertNull(repository.get(original.id)?.event); assertNull(repository.get(original.id)?.circle)
        assertTrue(repository.search(DoujinshiSearchSpec(event = eventB.id)).list.isEmpty())
        assertFalse(circles.getAll().any { it.name.isBlank() })
    }

    @Test
    fun topReachesTheOldestOf101BooksAndIgnoresDuplicateAndTerminalRequests() = runTest {
        seed(101)
        val controlled = ControlledRepository(repository)
        val vm = TopViewModel(controlled)
        vm.load(); repeat(10) { vm.loadMore() }; advanceUntilIdle()
        assertEquals(30, vm.uiState.value.bindingModel.list.size)
        repeat(3) { repeat(10) { vm.loadMore() }; advanceUntilIdle() }
        assertEquals((100 downTo 0).map { book(it).id }, vm.uiState.value.bindingModel.list.map { it.id })
        repeat(10) { vm.loadMore() }; advanceUntilIdle()
        assertEquals(listOf(0, 1, 2, 3), controlled.calls.map { it.second })
        assertFalse(vm.uiState.value.bindingModel.hasNextPage)
    }

    @Test
    fun topRefreshKeepsThreeLoadedPagesAndUpdatesAnEditedBookBeyondTheFirstPage() = runTest {
        seed(101)
        val vm = TopViewModel(repository); vm.load(); advanceUntilIdle()
        repeat(2) { vm.loadMore(); advanceUntilIdle() }
        val before = vm.uiState.value.bindingModel
        val editedId = before.list[55].id
        repository.update(requireNotNull(repository.get(editedId)).copy(title = "Edited page two"))
        vm.load()
        assertEquals(90, vm.uiState.value.bindingModel.list.size)
        vm.loadMore(); advanceUntilIdle()
        val after = vm.uiState.value.bindingModel
        assertEquals(before.list.map { it.id }, after.list.map { it.id })
        assertEquals(2, after.page); assertEquals("Edited page two", after.list[55].title)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(101, vm.uiState.value.bindingModel.list.size)
    }

    @Test
    fun searchUsesEveryInitialFilterForLoadMoreAndRefresh() = runTest {
        circles.save(circleA); circles.save(circleB); events.save(eventA)
        val author = Author(AuthorId("author"), "Author"); authors.save(author)
        val tag = Tag(TagId("tag"), "Tag"); tags.save(tag)
        val date = LocalDate.parse("2026-10-17")
        val spec = DoujinshiSearchSpec(keyword = "Book", circle = circleA.id, event = eventA.id,
            authors = listOf(author.id), tags = listOf(tag.id), pubTerm = Period(date, date))
        for (i in 0..100) repository.save(book(i).copy(circle = circleA, event = eventA,
            authors = listOf(author), tags = listOf(tag), pubDate = date))
        repository.save(book(200).copy(title = "Unrelated", circle = circleB))
        val controlled = ControlledRepository(repository)
        val vm = search(spec, controlled); advanceUntilIdle()
        assertEquals("Book", vm.bindingModel.value.title); assertEquals(circleA, vm.bindingModel.value.circle)
        assertEquals(listOf(author), vm.bindingModel.value.authors); assertEquals(listOf(tag), vm.bindingModel.value.tags)
        assertEquals(eventA, vm.bindingModel.value.event)
        vm.loadMore(); advanceUntilIdle()
        val removed = vm.bindingModel.value.searchResult[45].id
        repository.update(requireNotNull(repository.get(removed)).copy(circle = circleB))
        vm.refresh(); advanceUntilIdle()
        assertEquals(60, vm.bindingModel.value.searchResult.size)
        assertFalse(vm.bindingModel.value.searchResult.any { it.id == removed })
        assertTrue(controlled.calls.all { it.first == spec })
        assertEquals(listOf(30, 30, 60), controlled.calls.map { it.third })
        assertEquals(1, vm.bindingModel.value.page)
    }

    @Test
    fun circleRefreshRemovesMovedBookAndRefillsLoadedRange() = runTest {
        circles.save(circleA); circles.save(circleB)
        for (i in 0..100) repository.save(book(i).copy(circle = circleA))
        val vm = CircleDetailViewModel(circleA.id, circles, repository); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        val removed = vm.uiState.value.bindingModel.list[45].id
        repository.update(requireNotNull(repository.get(removed)).copy(circle = circleB))
        vm.load(); vm.loadMore(); advanceUntilIdle()
        assertEquals(60, vm.uiState.value.bindingModel.list.size)
        assertFalse(vm.uiState.value.bindingModel.list.any { it.id == removed })
        assertEquals(1, vm.uiState.value.bindingModel.page)
        repeat(2) { vm.loadMore(); advanceUntilIdle() }
        assertEquals(100, vm.uiState.value.bindingModel.list.size)
        assertFalse(vm.uiState.value.bindingModel.hasNextPage)
    }

    @Test
    fun circleRenameRemainsVisibleWhenLoadingAnotherPage() = runTest {
        circles.save(circleA)
        for (i in 0..60) repository.save(book(i).copy(circle = circleA))
        val vm = CircleDetailViewModel(circleA.id, circles, repository); advanceUntilIdle()
        vm.onClickEdit(); vm.onInputName("Renamed circle"); vm.onClickSave(); advanceUntilIdle()
        vm.loadMore(); advanceUntilIdle()
        assertEquals(60, vm.uiState.value.bindingModel.list.size)
        assertTrue(vm.uiState.value.bindingModel.list.all { it.circleName == "Renamed circle" })
        vm.onClickEdit(); vm.onInputName("Unsaved name"); vm.load(); advanceUntilIdle()
        assertEquals("Unsaved name", vm.uiState.value.bindingModel.inputCircleName)
    }

    @Test
    fun detailReloadsSavedChangesAndReturnsAfterDeletion() = runTest {
        repository.save(book(0))
        val vm = DetailViewModel(book(0).id, repository); advanceUntilIdle()
        val changed = book(0).copy(title = "Edited", imagePaths = listOf("new-cover.png"))
        repository.update(changed); vm.load(); advanceUntilIdle()
        assertEquals(changed, vm.doujinshi.value)
        repository.delete(changed.id); vm.load(); advanceUntilIdle()
        assertNull(vm.doujinshi.value); assertSame(PopBackDestination, vm.destination.value)
    }

    @Test
    fun failedAppendCanBeRetriedWithoutAdvancingOrDuplicatingPages() = runTest {
        seed(61)
        val controlled = ControlledRepository(repository)
        val vm = TopViewModel(controlled); vm.load(); advanceUntilIdle()
        controlled.fail = true
        vm.loadMore(); advanceUntilIdle()
        assertEquals(DoujinshiLoadError.LOAD_MORE, vm.uiState.value.loadError)
        assertFalse(vm.uiState.value.loadingMore); assertEquals(0, vm.uiState.value.bindingModel.page)
        vm.loadMore(); advanceUntilIdle()
        assertEquals(2, controlled.calls.size)
        vm.retry(); repeat(5) { vm.loadMore() }; advanceUntilIdle()
        assertEquals(60, vm.uiState.value.bindingModel.list.size)
        assertEquals(60, vm.uiState.value.bindingModel.list.distinctBy { it.id }.size)
        assertEquals(listOf(0, 1, 1), controlled.calls.map { it.second })
    }

    @Test
    fun failedRefreshRetainsLoadedRowsAndRetriesTheSameRange() = runTest {
        seed(101)
        val controlled = ControlledRepository(repository)
        val vm = TopViewModel(controlled); vm.load(); advanceUntilIdle(); vm.loadMore(); advanceUntilIdle()
        val before = vm.uiState.value.bindingModel
        controlled.fail = true; vm.load(); advanceUntilIdle()
        assertEquals(before, vm.uiState.value.bindingModel)
        assertFalse(vm.uiState.value.refreshing)
        assertEquals(DoujinshiLoadError.REFRESH, vm.uiState.value.loadError)
        vm.retry(); advanceUntilIdle()
        assertEquals(before, vm.uiState.value.bindingModel); assertNull(vm.uiState.value.loadError)
        assertEquals(listOf(30, 30, 60, 60), controlled.calls.map { it.third })
    }

    @Test
    fun refreshSupersedesAnInFlightAppendEvenIfItIgnoresCancellation() = runTest {
        seed(101)
        val controlled = ControlledRepository(repository)
        val vm = TopViewModel(controlled); vm.load(); advanceUntilIdle()
        val gate = CompletableDeferred<Unit>(); controlled.gate = gate
        vm.loadMore(); runCurrent()
        assertTrue(vm.uiState.value.loadingMore)
        repository.update(book(100).copy(title = "Fresh"))
        vm.load(); advanceUntilIdle()
        assertEquals("Fresh", vm.uiState.value.bindingModel.list.first().title)
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(30, vm.uiState.value.bindingModel.list.size)
        assertEquals(0, vm.uiState.value.bindingModel.page)
        assertEquals("Fresh", vm.uiState.value.bindingModel.list.first().title)
        assertFalse(vm.uiState.value.loadingMore)
    }

    @Test
    fun rapidFilterChangeDiscardsOldSearchAndUsesNewFilterOnRetry() = runTest {
        seed(61)
        val controlled = ControlledRepository(repository)
        val vm = search(repo = controlled); advanceUntilIdle()
        val gate = CompletableDeferred<Unit>(); controlled.gate = gate
        vm.onInputTitle("Book"); runCurrent()
        vm.onInputTitle("Book 1"); advanceUntilIdle()
        val latest = vm.bindingModel.value.searchResult
        assertTrue(latest.isNotEmpty()); assertTrue(latest.all { "Book 1" in it.title })
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(latest, vm.bindingModel.value.searchResult)
        assertEquals("Book 1", vm.bindingModel.value.title)
        controlled.fail = true; vm.refresh(); advanceUntilIdle()
        vm.retry(); advanceUntilIdle()
        assertEquals("Book 1", controlled.calls.last().first.keyword)
        assertEquals(latest, vm.bindingModel.value.searchResult)
    }

    @Test
    fun pagerPublishesUpdatedCoverAndDistinctRowsWithoutAndroidUriConversion() = runTest {
        repository.save(book(0).copy(imagePaths = listOf("old.png")))
        var state = DoujinshiPageState()
        val pager = DoujinshiPager(this, repository) { state = it }
        pager.refresh(); advanceUntilIdle()
        repository.update(book(0).copy(imagePaths = listOf("new.png")))
        pager.refresh(); advanceUntilIdle()
        assertEquals(listOf("new.png"), state.list.single().imagePaths)
    }

    private class ControlledRepository(private val delegate: DoujinshiRepository) : DoujinshiRepository by delegate {
        val calls = mutableListOf<Triple<DoujinshiSearchSpec, Int, Int>>()
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun search(searchSpec: DoujinshiSearchSpec, page: Int, size: Int): DoujinshiSearchResult {
            calls += Triple(searchSpec, page, size)
            if (fail) { fail = false; throw IllegalStateException("temporary read failure") }
            val captured = delegate.search(searchSpec, page, size)
            gate?.let { gate = null; withContext(NonCancellable) { it.await() } }
            return captured
        }
    }
}
