package com.yt8492.doujinshimanager.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.shared.domain.model.*
import com.yt8492.doujinshimanager.shared.domain.repository.*
import com.yt8492.doujinshimanager.shared.infra.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.yt8492.doujinshimanager.ui.top.TopViewModel
import com.yt8492.doujinshimanager.ui.search.SearchViewModel
import com.yt8492.doujinshimanager.ui.circledetail.CircleDetailViewModel
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class PaginationEditNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var driver: AndroidSqliteDriver
    private lateinit var repository: DoujinshiRepositoryImpl
    private lateinit var circles: CircleRepositoryImpl
    private val calls = Collections.synchronizedList(mutableListOf<Pair<Int, Int>>())
    private val circleA = Circle(CircleId("circle-a"), "Circle A")
    private val circleB = Circle(CircleId("circle-b"), "Circle B")
    @Volatile private var failNextSearch = false
    private lateinit var overrides: org.koin.core.module.Module

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        driver = AndroidSqliteDriver(Database.Schema, context, null)
        val db = Database(driver)
        repository = DoujinshiRepositoryImpl(db.doujinshiQueries)
        circles = CircleRepositoryImpl(db.circleQueries)
        circles.save(circleA); circles.save(circleB)
        for (i in 0..100) repository.save(Doujinshi(
            id = DoujinshiId("book-%03d".format(i)), title = "Book $i", circle = circleA,
            authors = emptyList(), tags = emptyList(), event = null, pubDate = null,
            imagePaths = emptyList(), createdAt = Instant.parse("2026-10-17T00:00:00Z"),
        ))
        val observed = object : DoujinshiRepository by repository {
            override suspend fun search(searchSpec: DoujinshiSearchSpec, page: Int, size: Int): DoujinshiSearchResult {
                calls += page to size
                if (failNextSearch) { failNextSearch = false; throw IllegalStateException("temporary failure") }
                return repository.search(searchSpec, page, size)
            }
        }
        overrides = module {
            single<DoujinshiRepository> { observed }
            single<CircleRepository> { circles }
            single<AuthorRepository> { AuthorRepositoryImpl(db.authorQueries) }
            single<TagRepository> { TagRepositoryImpl(db.tagQueries) }
            single<EventRepository> { EventRepositoryImpl(db.eventQueries) }
        }
        loadKoinModules(overrides)
    }

    @After
    fun tearDown() { unloadKoinModules(overrides); driver.close() }

    private fun requests() = synchronized(calls) { calls.toList() }

    private fun start() {
        compose.setContent { MainApp() }
        compose.onNodeWithText("Book 100").assertIsDisplayed()
    }

    private fun scrollTo(index: Int) {
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        compose.waitForIdle()
    }

    private fun reachSecondPage() {
        scrollTo(29)
        compose.waitUntil(10_000) { requests().any { it.first == 1 } }
        compose.waitForIdle()
    }

    private fun editTitle(old: String, new: String) {
        compose.onNodeWithText(old).performClick()
        compose.onNodeWithContentDescription("編集").performClick()
        compose.onNode(hasText(old) and hasSetTextAction()).performTextReplacement(new)
        compose.onNodeWithContentDescription("保存").performClick()
        compose.onAllNodesWithText(new).onFirst().assertIsDisplayed()
    }

    @Test
    fun topLoadsAll101BooksFromInitiallyEmptyStateAndStopsAtTheEnd() {
        start()
        reachSecondPage()
        scrollTo(59); compose.waitUntil(10_000) { requests().any { it.first == 2 } }
        scrollTo(89); compose.waitUntil(10_000) { requests().any { it.first == 3 } }
        scrollTo(100)
        compose.onNodeWithText("Book 0").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(1, 2, 3), requests().map { it.first }.filter { it > 0 }) }
    }

    @Test
    fun topEditBeyondFirstPageRefreshesDetailAndReturnsToTheSameScrollAnchor() {
        start(); reachSecondPage(); scrollTo(45)
        val before = compose.onNodeWithText("Book 55").fetchSemanticsNode().boundsInRoot.top
        editTitle("Book 55", "Edited on page two")
        pressBack()
        compose.onNodeWithText("Edited on page two").assertIsDisplayed()
        val after = compose.onNodeWithText("Edited on page two").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 2f)
        assertTrue(requests().any { it.second == 60 })
        scrollTo(59); compose.waitUntil(10_000) { requests().any { it.first == 2 } }
        scrollTo(89); compose.waitUntil(10_000) { requests().any { it.first == 3 } }
        scrollTo(100); compose.onNodeWithText("Book 0").assertIsDisplayed()
    }

    @Test
    fun searchEditKeepsTheFilterAndSecondPageScrollPosition() {
        start()
        compose.onNodeWithContentDescription("同人誌を検索する").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Book")
        compose.waitForIdle(); reachSecondPage(); scrollTo(45)
        val before = compose.onNodeWithText("Book 55").fetchSemanticsNode().boundsInRoot.top
        editTitle("Book 55", "Book updated")
        pressBack()
        compose.onNodeWithText("Book updated").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextEquals("Book")
        val after = compose.onNodeWithText("Book updated").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 2f)
    }

    @Test
    fun changedCoverUriReachesTopSearchAndCircleBindings() = runBlocking(Dispatchers.Main) {
        val id = DoujinshiId("book-055")
        val original = requireNotNull(repository.get(id))
        repository.update(original.copy(imagePaths = listOf("file:///old-cover.png")))
        val db = Database(driver)
        val top = TopViewModel(repository)
        val search = SearchViewModel(DoujinshiSearchSpec(), repository, circles,
            AuthorRepositoryImpl(db.authorQueries), TagRepositoryImpl(db.tagQueries), EventRepositoryImpl(db.eventQueries))
        val circle = CircleDetailViewModel(circleA.id, circles, repository)
        top.load(); top.loadMore(); search.loadMore(); circle.loadMore()
        assertEquals("file:///old-cover.png", top.uiState.value.bindingModel.list.first { it.id == id }.image.toString())
        repository.update(original.copy(imagePaths = listOf("file:///new-cover.png")))
        top.load(); search.refresh(); circle.load()
        assertEquals("file:///new-cover.png", top.uiState.value.bindingModel.list.first { it.id == id }.image.toString())
        assertEquals("file:///new-cover.png", search.bindingModel.value.searchResult.first { it.id == id }.image.toString())
        assertEquals("file:///new-cover.png", circle.uiState.value.bindingModel.list.first { it.id == id }.image.toString())
    }

    @Test
    fun failedAppendShowsRetryAndContinuesFromTheSamePage() {
        start()
        failNextSearch = true
        scrollTo(29)
        compose.waitUntil(10_000) { requests().any { it.first == 1 } }
        scrollTo(30)
        compose.onNodeWithText("再試行").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { requests().count { it.first == 1 } == 2 }
        compose.waitForIdle()
        scrollTo(30)
        compose.onNodeWithText("Book 70").assertIsDisplayed()
        compose.onNodeWithText("再試行").assertDoesNotExist()
    }

    @Test
    fun circleEditRemovesMovedBookAndKeepsSecondPageAnchor() {
        start()
        compose.onNodeWithText("Book 100").performClick()
        compose.onNodeWithText("Circle A").performClick()
        reachSecondPage(); scrollTo(45)
        val before = compose.onNodeWithText("Book 55").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Book 55").performClick()
        // Save a changed membership and cover while the real navigation stack is on detail.
        runBlocking {
            val book = requireNotNull(repository.get(DoujinshiId("book-054")))
            repository.update(book.copy(circle = circleB, imagePaths = listOf("file:///new-cover.png")))
        }
        compose.onNodeWithContentDescription("編集").performClick()
        compose.onNode(hasText("Book 55") and hasSetTextAction()).performTextReplacement("Circle book updated")
        compose.onNodeWithContentDescription("保存").performClick()
        pressBack()
        compose.onNodeWithText("Circle book updated").assertIsDisplayed()
        compose.onNodeWithText("Book 54").assertDoesNotExist()
        val after = compose.onNodeWithText("Circle book updated").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 2f)
        assertTrue(requests().any { it.second == 60 })
    }
}
