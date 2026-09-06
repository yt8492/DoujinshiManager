package com.yt8492.doujinshimanager.ui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.shared.domain.model.Doujinshi
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiId
import com.yt8492.doujinshimanager.shared.infra.repository.AuthorRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.CircleRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.DoujinshiRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.EventRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.TagRepositoryImpl
import com.yt8492.doujinshimanager.ui.bindingmodel.DoujinshiBindingModel
import com.yt8492.doujinshimanager.ui.detail.DetailViewModel
import com.yt8492.doujinshimanager.ui.edit.EditViewModel
import com.yt8492.doujinshimanager.ui.lib.PopBackDestination
import com.yt8492.doujinshimanager.ui.register.RegisterViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MissingDoujinshiTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: Database
    private lateinit var repository: DoujinshiRepositoryImpl
    private val doujinshi = Doujinshi(
        id = DoujinshiId("test-id"),
        title = "Test book",
        circle = null,
        authors = emptyList(),
        tags = emptyList(),
        event = null,
        pubDate = null,
        imagePaths = listOf("cover.png", "back.png"),
        createdAt = Instant.parse("2026-09-06T00:00:00Z"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        database = Database(driver)
        repository = DoujinshiRepositoryImpl(database.doujinshiQueries)
    }

    @After
    fun tearDown() {
        driver.close()
        Dispatchers.resetMain()
    }

    @Test
    fun missingIdReturnsNull() = runTest {
        assertNull(repository.get(doujinshi.id))
    }

    @Test
    fun existingBookLoadsWithImagesAndCanBeUpdated() = runTest {
        repository.save(doujinshi)
        val loaded = requireNotNull(repository.get(doujinshi.id))
        assertEquals(doujinshi.copy(imagePaths = loaded.imagePaths), loaded)
        assertEquals(doujinshi.imagePaths.sorted(), loaded.imagePaths.sorted())
        val updated = doujinshi.copy(title = "Updated", imagePaths = listOf("new.png"))
        repository.update(updated)
        assertEquals(updated, repository.get(doujinshi.id))
    }

    @Test
    fun updatingDeletedBookDoesNotRecreateBookOrImages() = runTest {
        repository.save(doujinshi)
        repository.delete(doujinshi.id)
        repository.update(doujinshi.copy(imagePaths = listOf("new.png")))
        assertNull(repository.get(doujinshi.id))
        assertEquals(emptyList<Any>(), database.doujinshiQueries.findDoujinshiImages(10, 0).executeAsList())
    }

    @Test
    fun reopeningEditAfterDeletionNavigatesBack() = runTest {
        repository.save(doujinshi)
        val firstEdit = editViewModel()
        advanceUntilIdle()
        assertEquals(doujinshi.title, firstEdit.bindingModel.value?.title)
        assertNull(firstEdit.destination.value)
        firstEdit.onClickDelete()
        advanceUntilIdle()
        val reopenedEdit = editViewModel()
        advanceUntilIdle()
        assertNull(reopenedEdit.bindingModel.value)
        assertSame(PopBackDestination, reopenedEdit.destination.value)
    }

    @Test
    fun missingDetailNavigatesBack() = runTest {
        val viewModel = DetailViewModel(doujinshi.id, repository)
        advanceUntilIdle()
        assertNull(viewModel.doujinshi.value)
        assertSame(PopBackDestination, viewModel.destination.value)
    }

    @Test
    fun copyingMissingBookPreservesRegistrationInput() = runTest {
        val viewModel = RegisterViewModel(
            CircleRepositoryImpl(database.circleQueries),
            AuthorRepositoryImpl(database.authorQueries),
            TagRepositoryImpl(database.tagQueries),
            EventRepositoryImpl(database.eventQueries),
            repository,
        )
        viewModel.onInputTitle("Keep this title")
        val before = viewModel.bindingModel.value
        viewModel.onPickResult(DoujinshiBindingModel(doujinshi.id, "Deleted", "", null))
        advanceUntilIdle()
        assertEquals(before, viewModel.bindingModel.value)
    }

    private fun editViewModel() = EditViewModel(
        doujinshi.id,
        CircleRepositoryImpl(database.circleQueries),
        AuthorRepositoryImpl(database.authorQueries),
        TagRepositoryImpl(database.tagQueries),
        EventRepositoryImpl(database.eventQueries),
        repository,
    )
}
