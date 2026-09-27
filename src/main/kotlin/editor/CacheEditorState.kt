package editor

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import editor.npc.NpcTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class CacheEditorState(
    private val scope: CoroutineScope,
    initialPath: String,
    private val onOpened: (File) -> Unit = {},
) {
    val snackbar = SnackbarHostState()

    var cachePath by mutableStateOf(initialPath)
    var session: CacheSession? by mutableStateOf(null)
        private set
    var loading by mutableStateOf(false)
        private set

    var confirmOpen by mutableStateOf(false)

    val tabs: List<EditorTab> = listOf(
        NpcTab(this),
    )
    var selectedTab by mutableIntStateOf(0)

    fun requestOpen() {
        if (tabs.any { it.hasUnsavedChanges }) {
            confirmOpen = true
        } else {
            open()
        }
    }

    fun open() = launch("Failed to open cache") {
        confirmOpen = false
        val path = File(cachePath)
        require(File(path, "main_file_cache.dat2").exists()) { "No cache found at ${path.absolutePath}" }
        loading = true
        try {
            val session = withContext(Dispatchers.IO) { CacheSession(path) }
            this@CacheEditorState.session?.let { previous -> previous.access { previous.close() } }
            this@CacheEditorState.session = session
            for (tab in tabs) {
                tab.open(session)
            }
            onOpened(path)
            message("Opened ${path.absolutePath}")
        } finally {
            loading = false
        }
    }

    fun close() {
        session?.close()
    }

    fun message(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun launch(error: String, block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        try {
            block()
        } catch (e: Exception) {
            e.printStackTrace()
            message("$error: ${e.message}")
        }
    }
}
