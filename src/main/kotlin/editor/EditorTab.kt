package editor

import androidx.compose.runtime.Composable

interface EditorTab {
    val title: String

    val hasUnsavedChanges: Boolean

    suspend fun open(session: CacheSession)

    @Composable
    fun Content()
}
