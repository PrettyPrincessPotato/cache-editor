package editor

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import cache.TestCache
import com.displee.cache.CacheLibrary
import editor.npc.NpcRepository
import editor.npc.NpcTab
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CacheEditorUiTest {

    private lateinit var directory: File
    private val screenshots = File("build/screenshots").apply { mkdirs() }

    @BeforeTest
    fun setup() {
        directory = TestCache.copy()
    }

    @AfterTest
    fun teardown() {
        directory.deleteRecursively()
    }

    @Test
    fun `View, edit and create npcs`() = runDesktopComposeUiTest(width = 1280, height = 900) {
        lateinit var state: CacheEditorState
        setContent {
            val scope = rememberCoroutineScope()
            state = remember { CacheEditorState(scope, directory.absolutePath) }
            LaunchedEffect(Unit) { state.open() }
            CacheEditorTheme {
                App(state)
            }
        }
        val tab = state.tabs.filterIsInstance<NpcTab>().single()
        waitUntil(timeoutMillis = 30_000) { tab.npcs.isNotEmpty() && !state.loading }
        val total = tab.npcs.size
        onNodeWithText("NPCs").assertIsSelected()
        onNodeWithText("$total of $total npcs").assertExists()
        onNodeWithText("Open").assertIsEnabled()

        onNodeWithText("Hans").performClick()
        waitUntil(timeoutMillis = 10_000) { tab.form?.id == 0 }
        screenshot("1-view")
        onNodeWithText("Model translations", substring = true).performScrollTo()
        screenshot("1-view-models")
        onNodeWithText("Name", substring = true).performScrollTo()

        onNode(hasSetTextAction() and hasText("Hans")).performTextReplacement("Hans the Great")
        onNode(hasSetTextAction() and hasText("0") and hasText("Combat level", substring = true)).performTextReplacement("42")
        onNodeWithText("NPCs *").assertExists()
        screenshot("2-edited")

        runOnUiThread { state.snackbar.currentSnackbarData?.dismiss() }
        val addParam = onNode(hasSetTextAction() and hasText("Add param", substring = true))
        addParam.performScrollTo().performTextReplacement("attack_sp")
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText("attack_speed (14)", substring = true).assertExists() }.isSuccess }
        screenshot("2-param-picker")
        onNodeWithText("attack_speed (14)", substring = true).performClick()
        onNode(hasSetTextAction() and hasText("attack_speed = ", substring = true)).assertExists()
        onNodeWithText("Add param", substring = true).performScrollTo()
        screenshot("2-param-added")

        onNodeWithText("Save to cache").performClick()
        waitUntil(timeoutMillis = 10_000) { tab.form?.isDirty == false }
        onNodeWithText("NPCs").assertExists()
        repository { repository ->
            val saved = repository.load(0)!!
            assertEquals("Hans the Great", saved.name)
            assertEquals(42, saved.combatLevel)
            assertTrue(saved.params.containsKey(14))
        }

        val next = repository { it.nextFreeId() }
        onNodeWithText("Create new NPC").performClick()
        waitUntil(timeoutMillis = 10_000) { runCatching { onNodeWithText(next.toString()).assertExists() }.isSuccess }
        onNodeWithText("Copy saved values from npc 0").performClick()
        screenshot("3-create-dialog")
        onNodeWithText("Create").performClick()
        waitUntil(timeoutMillis = 10_000) { tab.form?.isNew == true }
        onNode(hasSetTextAction() and hasText("Hans the Great")).performTextReplacement("Hans' twin")

        onNode(hasSetTextAction() and hasText("Varbit", substring = true)).performTextReplacement("1234")
        runOnUiThread { state.snackbar.currentSnackbarData?.dismiss() }
        repeat(3) { onNodeWithText("Add index").performScrollTo().performClick() }
        pickNpc("Index 0", "Woman", "4 - Woman (lvl 2)")
        pickNpc("Index 2", "3", "3 - Man (lvl 2)", screenshot = "5-transform-picker")
        pickNpc("Default npc", "Hans the", "0 - Hans the Great (lvl 42)")
        onNodeWithText("Add index").performScrollTo()
        screenshot("6-transforms")

        onNodeWithText("Save to cache").performClick()
        waitUntil(timeoutMillis = 10_000) { tab.form?.isNew == false }
        onNodeWithText("${total + 1} of ${total + 1} npcs").assertExists()
        screenshot("4-created")
        repository { repository ->
            assertTrue(repository.exists(next))
            val created = repository.load(next)!!
            assertEquals("Hans' twin", created.name)
            assertEquals(42, created.combatLevel)
            assertEquals(1234, created.multiNpcVarbit)
            assertContentEquals(intArrayOf(4, -1, 3, 0), created.multiNpcs)
        }
        state.close()
    }

    private fun ComposeUiTest.pickNpc(label: String, query: String, option: String, screenshot: String? = null) {
        onNode(hasSetTextAction() and hasText(label, substring = true)).performTextReplacement(query)
        waitUntil(timeoutMillis = 5_000) { runCatching { onNodeWithText(option).assertExists() }.isSuccess }
        if (screenshot != null) {
            screenshot(screenshot)
        }
        onNodeWithText(option).performClick()
        onNode(hasSetTextAction() and hasText(label, substring = true)).assert(hasText(option))
    }

    private fun <T> repository(block: (NpcRepository) -> T): T {
        val cache = CacheLibrary.create(directory.absolutePath)
        try {
            return block(NpcRepository(cache))
        } finally {
            cache.close()
        }
    }

    private fun ComposeUiTest.screenshot(name: String) {
        waitForIdle()
        ImageIO.write(onAllNodes(isRoot()).onLast().captureToImage().toAwtImage(), "png", File(screenshots, "$name.png"))
    }
}
