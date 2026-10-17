package com.yt8492.doujinshimanager.ui.circledetail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.yt8492.doujinshimanager.database.Database
import com.yt8492.doujinshimanager.shared.domain.model.Circle
import com.yt8492.doujinshimanager.shared.domain.model.CircleId
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchResult
import com.yt8492.doujinshimanager.shared.domain.model.DoujinshiSearchSpec
import com.yt8492.doujinshimanager.shared.domain.repository.CircleRepository
import com.yt8492.doujinshimanager.shared.domain.repository.DoujinshiRepository
import com.yt8492.doujinshimanager.shared.infra.repository.CircleRepositoryImpl
import com.yt8492.doujinshimanager.shared.infra.repository.DoujinshiRepositoryImpl
import com.yt8492.doujinshimanager.ui.theme.DoujinshiManagerTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CircleEditingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var model: CircleDetailViewModel
    private lateinit var circles: CircleRepositoryImpl
    private val circle = Circle(CircleId("circle-edit-test"), "Original name")
    private val writeCount = AtomicInteger()

    @After
    fun tearDown() {
        if (::model.isInitialized) compose.runOnUiThread { model.viewModelScope.cancel() }
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun editButtonWaitsForInitialNameAndSavingWithoutChangesPreservesIt() {
        val readGate = CompletableDeferred<Unit>()
        show(readGate = readGate)
        compose.onNodeWithContentDescription("編集").assertIsNotEnabled()
        readGate.complete(Unit)
        awaitLoaded()
        compose.onNodeWithContentDescription("編集").assertIsEnabled().performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals(circle.name)
        compose.onNodeWithContentDescription("保存").performClick()
        compose.onNodeWithContentDescription("編集").assertIsEnabled()
        assertEquals(circle, runBlocking { circles.get(circle.id) })
        assertEquals(0, writeCount.get())
    }

    @Test
    fun inputAndSaveAreDisabledUntilWriteFinishes() {
        val writeGate = CompletableDeferred<Unit>()
        show(writeGate = writeGate)
        awaitLoaded()
        compose.onNodeWithContentDescription("編集").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("First name")
        compose.onNodeWithContentDescription("保存").performClick()
        compose.onNodeWithText("First name").assertIsNotEnabled()
        compose.onNodeWithContentDescription("保存").assertIsNotEnabled()
        writeGate.complete(Unit)
        awaitSaved("First name")
        compose.onNodeWithContentDescription("編集").assertIsEnabled().performClick()
        compose.onNode(hasSetTextAction()).assertIsEnabled().performTextReplacement("Latest name")
        compose.onNodeWithContentDescription("保存").assertIsEnabled().performClick()
        awaitSaved("Latest name")
        assertEquals("Latest name", runBlocking { circles.get(circle.id).name })
        assertEquals(2, writeCount.get())
    }

    @Test
    fun failedSaveShowsErrorAndKeepsInputForRetry() {
        show(failFirstWrite = true)
        awaitLoaded()
        compose.onNodeWithContentDescription("編集").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Draft")
        compose.onNodeWithContentDescription("保存").performClick()
        compose.waitUntil(5_000) { model.uiState.value.saveError }
        compose.onNodeWithText("サークル名の保存に失敗しました").assertExists()
        compose.onNode(hasSetTextAction()).assertIsEnabled().assertTextContains("Draft")
        compose.onNodeWithContentDescription("保存").assertIsEnabled().performClick()
        awaitSaved("Draft")
        assertEquals("Draft", runBlocking { circles.get(circle.id).name })
        assertEquals(2, writeCount.get())
    }

    private fun awaitLoaded() {
        compose.waitUntil(5_000) { model.uiState.value.hasLoaded }
    }

    private fun awaitSaved(name: String) {
        compose.waitUntil(5_000) {
            val state = model.uiState.value
            !state.saving && !state.refreshing && state.bindingModel.circle.name == name
        }
        compose.onNodeWithText(name).assertExists()
    }

    private fun show(
        readGate: CompletableDeferred<Unit>? = null,
        writeGate: CompletableDeferred<Unit>? = null,
        failFirstWrite: Boolean = false,
    ) {
        // In-memory data keeps the installed app's library untouched.
        driver = AndroidSqliteDriver(Database.Schema, InstrumentationRegistry.getInstrumentation().targetContext, name = null)
        val db = Database(driver)
        circles = CircleRepositoryImpl(db.circleQueries)
        val books = DoujinshiRepositoryImpl(db.doujinshiQueries)
        runBlocking { circles.save(circle) }
        val controlledBooks = object : DoujinshiRepository by books {
            override suspend fun search(searchSpec: DoujinshiSearchSpec, page: Int, size: Int): DoujinshiSearchResult {
                readGate?.await()
                return books.search(searchSpec, page, size)
            }
        }
        val shouldFail = AtomicBoolean(failFirstWrite)
        val controlledCircles = object : CircleRepository by circles {
            override suspend fun update(circle: Circle) {
                writeCount.incrementAndGet()
                writeGate?.await()
                if (shouldFail.compareAndSet(true, false)) error("Test write failure")
                circles.update(circle)
            }
        }
        compose.runOnUiThread {
            model = CircleDetailViewModel(circle.id, controlledCircles, controlledBooks)
        }
        compose.setContent {
            DoujinshiManagerTheme {
                CircleDetailPage(circle.id, rememberNavController(), model)
            }
        }
    }
}
