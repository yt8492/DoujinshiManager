package com.yt8492.doujinshimanager.ui.circledetail

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchResult
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.infra.repository.CircleRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.DoujinshiRepositoryImpl
import com.yt8492.doujinshimanager.ui.lib.DoujinshiLoadError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CircleDetailViewModelTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var circles: CircleRepositoryImpl
    private lateinit var books: DoujinshiRepositoryImpl
    private lateinit var dispatcher: TestDispatcher
    private val original = Circle(CircleId("circle"), "Original name")

    @Before
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        val db = Database(driver)
        circles = CircleRepositoryImpl(db.circleQueries)
        books = DoujinshiRepositoryImpl(db.doujinshiQueries)
    }

    @After
    fun tearDown() {
        driver.close()
        Dispatchers.resetMain()
    }

    @Test
    fun initialReadMustFinishBeforeEditingOrSaving() = runTest {
        circles.save(original)
        val releaseRead = CompletableDeferred<Unit>()
        val controlled = controlledBooks { spec, page, size ->
            releaseRead.await()
            books.search(spec, page, size)
        }
        val model = CircleDetailViewModel(original.id, circles, controlled, dispatcher)
        model.load()
        runCurrent()
        assertFalse(model.uiState.value.canEdit)
        model.onClickEdit()
        model.onInputName("Must not replace the saved name")
        model.onClickSave()
        assertFalse(model.uiState.value.bindingModel.isEditMode)
        assertFalse(model.uiState.value.saving)
        releaseRead.complete(Unit)
        advanceUntilIdle()
        assertTrue(model.uiState.value.canEdit)
        assertEquals(original.name, model.uiState.value.bindingModel.inputCircleName)
        model.onClickEdit()
        model.onClickSave()
        advanceUntilIdle()
        assertEquals(original, circles.get(original.id))
        assertFalse(model.uiState.value.bindingModel.isEditMode)
    }

    @Test
    fun initialFailureKeepsEditingDisabledUntilRetrySucceeds() = runTest {
        circles.save(original)
        var fail = true
        val controlled = controlledBooks { spec, page, size ->
            if (fail) error("Read failed")
            books.search(spec, page, size)
        }
        val model = CircleDetailViewModel(original.id, circles, controlled, dispatcher)
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.hasLoaded)
        model.onClickEdit()
        assertFalse(model.uiState.value.bindingModel.isEditMode)
        fail = false
        model.retry()
        advanceUntilIdle()
        model.onClickEdit()
        assertTrue(model.uiState.value.canSave)
        assertEquals(original.name, model.uiState.value.bindingModel.inputCircleName)
    }

    @Test
    fun pendingSaveRejectsDuplicateWritesAndEditsThenAllowsAnotherSave() = runTest {
        circles.save(original)
        val releaseWrite = CompletableDeferred<Unit>()
        val writes = mutableListOf<String>()
        val controlled = object : CircleRepository by circles {
            override suspend fun update(circle: Circle) {
                writes += circle.name
                if (circle.name == "First name") releaseWrite.await()
                circles.update(circle)
            }
        }
        val model = CircleDetailViewModel(original.id, controlled, books, dispatcher)
        model.load()
        advanceUntilIdle()
        model.onClickEdit()
        model.onInputName("First name")
        model.onClickSave()
        assertTrue(model.uiState.value.saving)
        // Include repeated callbacks before the first save coroutine even starts.
        repeat(10) { model.onClickSave() }
        runCurrent()
        assertFalse(model.uiState.value.canEdit)
        assertFalse(model.uiState.value.canSave)
        model.onInputName("Ignored while saving")
        model.onClickEdit()
        model.onClickSave()
        model.load() // ON_RESUME must not race the pending write.
        model.loadMore()
        runCurrent()
        assertEquals(listOf("First name"), writes)
        assertEquals("First name", model.uiState.value.bindingModel.inputCircleName)
        assertEquals(original, circles.get(original.id))
        releaseWrite.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.saving)
        assertEquals("First name", circles.get(original.id).name)
        model.onClickEdit()
        model.onInputName("Latest name")
        model.onClickSave()
        advanceUntilIdle()
        assertEquals(listOf("First name", "Latest name"), writes)
        assertEquals("Latest name", circles.get(original.id).name)
        assertEquals("Latest name", model.uiState.value.bindingModel.circle.name)
    }

    @Test
    fun failedSaveKeepsDraftAndAllowsRetry() = runTest {
        circles.save(original)
        var fail = true
        val controlled = object : CircleRepository by circles {
            override suspend fun update(circle: Circle) {
                if (fail) error("Write failed")
                circles.update(circle)
            }
        }
        val model = CircleDetailViewModel(original.id, controlled, books, dispatcher)
        model.load()
        advanceUntilIdle()
        model.onClickEdit()
        model.onInputName("Draft")
        model.onClickSave()
        advanceUntilIdle()
        assertFalse(model.uiState.value.saving)
        assertTrue(model.uiState.value.saveError)
        assertTrue(model.uiState.value.canSave)
        assertEquals("Draft", model.uiState.value.bindingModel.inputCircleName)
        assertEquals(original, circles.get(original.id))
        fail = false
        model.onClickSave()
        advanceUntilIdle()
        assertFalse(model.uiState.value.saveError)
        assertFalse(model.uiState.value.bindingModel.isEditMode)
        assertEquals("Draft", circles.get(original.id).name)
    }

    @Test
    fun refreshDuringEditingKeepsDraft() = runTest {
        circles.save(original)
        val model = CircleDetailViewModel(original.id, circles, books, dispatcher)
        model.load()
        advanceUntilIdle()
        model.onClickEdit()
        model.onInputName("Draft")
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.canSave)
        assertEquals("Draft", model.uiState.value.bindingModel.inputCircleName)
        model.onClickSave()
        advanceUntilIdle()
        assertEquals("Draft", circles.get(original.id).name)
    }

    @Test
    fun cancelledRefreshCannotRestoreOldNameAfterSave() = runTest {
        circles.save(original)
        var blockRead = false
        val releaseRead = CompletableDeferred<Unit>()
        val controlled = controlledBooks { spec, page, size ->
            val result = books.search(spec, page, size)
            if (blockRead) withContext(NonCancellable) { releaseRead.await() }
            result
        }
        val model = CircleDetailViewModel(original.id, circles, controlled, dispatcher)
        model.load()
        advanceUntilIdle()
        blockRead = true
        model.load()
        runCurrent()
        model.onClickEdit()
        model.onInputName("New name")
        model.onClickSave()
        blockRead = false
        runCurrent()
        assertEquals("New name", model.uiState.value.bindingModel.circle.name)
        releaseRead.complete(Unit)
        advanceUntilIdle()
        assertEquals("New name", model.uiState.value.bindingModel.circle.name)
        assertEquals("New name", model.uiState.value.bindingModel.inputCircleName)
    }

    @Test
    fun initialNameReadFailureDisablesEditingUntilRetrySucceeds() = runTest {
        circles.save(original)
        var fail = true
        val controlled = object : CircleRepository by circles {
            override suspend fun get(id: CircleId): Circle {
                if (fail) error("Name read failed")
                return circles.get(id)
            }
        }
        val model = CircleDetailViewModel(original.id, controlled, books, dispatcher)
        advanceUntilIdle()
        assertFalse(model.uiState.value.canEdit)
        assertFalse(model.uiState.value.refreshing)
        assertEquals(DoujinshiLoadError.REFRESH, model.uiState.value.loadError)
        fail = false
        model.retry()
        advanceUntilIdle()
        assertTrue(model.uiState.value.canEdit)
        assertEquals(original.name, model.uiState.value.bindingModel.inputCircleName)
        assertNull(model.uiState.value.loadError)
    }

    @Test
    fun cancelledNameReadCannotRestoreOldNameAfterSave() = runTest {
        circles.save(original)
        var blockRead = false
        val releaseRead = CompletableDeferred<Unit>()
        val controlled = object : CircleRepository by circles {
            override suspend fun get(id: CircleId): Circle {
                val circle = circles.get(id)
                if (blockRead) withContext(NonCancellable) { releaseRead.await() }
                return circle
            }
        }
        val model = CircleDetailViewModel(original.id, controlled, books, dispatcher)
        advanceUntilIdle()
        blockRead = true
        model.load()
        runCurrent()
        model.onClickEdit()
        model.onInputName("New name")
        model.onClickSave()
        blockRead = false
        runCurrent()
        assertEquals("New name", model.uiState.value.bindingModel.circle.name)
        releaseRead.complete(Unit)
        advanceUntilIdle()
        assertEquals("New name", model.uiState.value.bindingModel.circle.name)
        assertEquals("New name", model.uiState.value.bindingModel.inputCircleName)
        assertNull(model.uiState.value.loadError)
    }

    @Test
    fun failedSaveDuringAppendKeepsLoadedRangeAndContinuesWithoutSkippingPages() = runTest {
        circles.save(original)
        repeat(61) { index ->
            books.save(Doujinshi(
                id = DoujinshiId("book-$index"), title = "Book $index", circle = original,
                authors = emptyList(), tags = emptyList(), event = null, pubDate = null,
                imagePaths = emptyList(), createdAt = Instant.fromEpochSeconds(index.toLong()),
            ))
        }
        val releaseRead = CompletableDeferred<Unit>()
        var blockAppend = false
        val requests = mutableListOf<Int>()
        val controlledBooks = controlledBooks { spec, page, size ->
            requests += page
            val result = books.search(spec, page, size)
            if (blockAppend && page == 1) withContext(NonCancellable) { releaseRead.await() }
            result
        }
        val controlledCircles = object : CircleRepository by circles {
            override suspend fun update(circle: Circle) = error("Write failed")
        }
        val model = CircleDetailViewModel(original.id, controlledCircles, controlledBooks, dispatcher)
        advanceUntilIdle()
        blockAppend = true
        model.loadMore()
        runCurrent()
        model.onClickEdit()
        model.onInputName("Draft")
        model.onClickSave()
        runCurrent()
        assertTrue(model.uiState.value.saveError)
        assertEquals(30, model.uiState.value.bindingModel.list.size)
        assertFalse(model.uiState.value.loadingMore)
        blockAppend = false
        model.loadMore()
        runCurrent()
        assertEquals(60, model.uiState.value.bindingModel.list.size)
        releaseRead.complete(Unit)
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        val state = model.uiState.value
        assertEquals(listOf(0, 1, 1, 2), requests)
        assertEquals(61, state.bindingModel.list.map { it.id }.distinct().size)
        assertFalse(state.bindingModel.hasNextPage)
        assertEquals("Draft", state.bindingModel.inputCircleName)
    }

    private fun controlledBooks(
        read: suspend (DoujinshiSearchSpec, Int, Int) -> DoujinshiSearchResult,
    ) = object : DoujinshiRepository by books {
        override suspend fun search(searchSpec: DoujinshiSearchSpec, page: Int, size: Int): DoujinshiSearchResult {
            return read(searchSpec, page, size)
        }
    }
}
