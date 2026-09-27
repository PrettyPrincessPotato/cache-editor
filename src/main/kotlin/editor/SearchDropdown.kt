package editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val MAX_RESULTS = 50

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchDropdown(
    label: String,
    selected: String,
    search: (query: String) -> List<T>,
    describe: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pinned: List<T> = emptyList(),
    placeholder: String = "Search name or id",
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    fun close() {
        expanded = false
        query = ""
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (it && enabled) {
                expanded = true
            } else {
                close()
            }
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (expanded) query else selected,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { close() }) {
            val matches = search(query)
            for (option in pinned + matches.take(MAX_RESULTS)) {
                DropdownMenuItem(text = { Text(describe(option)) }, onClick = {
                    onSelect(option)
                    close()
                })
            }
            if (matches.size > MAX_RESULTS) {
                Text(
                    "${matches.size - MAX_RESULTS} more, type to narrow down",
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
