package editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import javax.swing.JFileChooser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(state: CacheEditorState) {
    Scaffold(snackbarHost = { SnackbarHost(state.snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CacheBar(state)
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            PrimaryScrollableTabRow(selectedTabIndex = state.selectedTab, edgePadding = 12.dp) {
                state.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == state.selectedTab,
                        onClick = { state.selectedTab = index },
                        text = { Text(if (tab.hasUnsavedChanges) "${tab.title} *" else tab.title) },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.session == null) {
                    Text(
                        "Open a cache to begin",
                        Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.tabs[state.selectedTab].Content()
                }
            }
        }
    }

    if (state.confirmOpen) {
        val tabs = state.tabs.filter { it.hasUnsavedChanges }.joinToString { it.title }
        AlertDialog(
            onDismissRequest = { state.confirmOpen = false },
            title = { Text("Discard changes?") },
            text = { Text("Opening a cache discards unsaved changes in: $tabs") },
            confirmButton = { TextButton(onClick = { state.open() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { state.confirmOpen = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun CacheBar(state: CacheEditorState) {
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.cachePath,
            onValueChange = { state.cachePath = it },
            label = { Text("Cache directory") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = {
            chooseDirectory(state.cachePath)?.let {
                state.cachePath = it
                state.requestOpen()
            }
        }, enabled = !state.loading) { Text("Browse") }
        Button(onClick = { state.requestOpen() }, enabled = !state.loading) { Text("Open") }
    }
}

private fun chooseDirectory(current: String): String? {
    val chooser = JFileChooser(current.ifBlank { null }).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select cache directory"
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}
