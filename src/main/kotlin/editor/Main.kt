package editor

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.util.prefs.Preferences

private const val LAST_CACHE = "lastCachePath"

fun main(args: Array<String>) = application {
    val preferences = remember { Preferences.userRoot().node("cache-editor") }
    val scope = rememberCoroutineScope()
    val state = remember {
        CacheEditorState(scope, args.firstOrNull() ?: preferences.get(LAST_CACHE, "")) { path ->
            preferences.put(LAST_CACHE, path.absolutePath)
        }
    }
    LaunchedEffect(Unit) {
        if (state.cachePath.isNotBlank()) {
            state.open()
        }
    }
    DisposableEffect(Unit) { onDispose { state.close() } }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Cache Editor",
        state = rememberWindowState(width = 1280.dp, height = 860.dp),
    ) {
        CacheEditorTheme {
            App(state)
        }
    }
}
