package editor.item

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import editor.SearchDropdown
import editor.npc.ParamInfo
import kotlinx.coroutines.launch

@Composable
fun ItemTabContent(tab: ItemTab) {
    Row(Modifier.fillMaxSize()) {
        ItemList(tab, Modifier.width(320.dp).fillMaxHeight())
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val form = tab.form
            if (form == null) {
                Text(
                    "Select an item to view it, or create a new one",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                ItemEditor(tab, form)
            }
        }
    }

    tab.pendingSelection?.let { id ->
        AlertDialog(
            onDismissRequest = { tab.pendingSelection = null },
            title = { Text("Discard changes?") },
            text = { Text("Item ${tab.form?.id} has unsaved changes which will be lost.") },
            confirmButton = { TextButton(onClick = { tab.load(id) }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { tab.pendingSelection = null }) { Text("Keep editing") } },
        )
    }

    if (tab.showCreateDialog) {
        CreateItemDialog(tab)
    }
}

@Composable
private fun ItemList(tab: ItemTab, modifier: Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { tab.showCreateDialog = true }, enabled = tab.repository != null, modifier = Modifier.fillMaxWidth()) {
            Text("Create new item")
        }
        OutlinedTextField(
            value = tab.query,
            onValueChange = { tab.query = it },
            label = { Text("Search name or id") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${tab.filtered.size} of ${tab.items.size} items",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.weight(1f)) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                items(tab.filtered, key = { it.id }) { item ->
                    val selected = item.id == tab.form?.id
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { tab.select(item.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(item.id.toString(), Modifier.width(48.dp), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            buildString {
                                append("cost ${item.cost}")
                                if (item.members) {
                                    append(" · members")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun ItemEditor(tab: ItemTab, form: ItemForm) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Item ${form.id}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                val status = when {
                    form.isNew -> "New item, not yet written to the cache"
                    form.isDirty -> "Unsaved changes"
                    else -> "Saved"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (form.isDirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { tab.revert() }, enabled = form.isDirty && !form.isNew) { Text("Revert") }
            Button(onClick = { tab.save() }, enabled = form.isDirty) { Text("Save to cache") }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (section in ITEM_SECTIONS) {
                    Section(tab, form, section)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(tab: ItemTab, form: ItemForm, section: ItemSection) {
    val fields = section.fields.filterNot { it.unverified }
    if (fields.isEmpty()) {
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (field in fields.filterIsInstance<ItemField.Text>()) {
                    if (field is ItemField.Params) {
                        ParamsEditor(tab, form, field)
                    } else {
                        TextFieldEditor(form, field)
                    }
                }
            }
            val flags = fields.filterIsInstance<ItemField.Flag>()
            if (flags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (field in flags) {
                        FlagEditor(form, field)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextFieldEditor(form: ItemForm, field: ItemField.Text) {
    val error = form.errors[field]
    OutlinedTextField(
        value = form.texts.getValue(field),
        onValueChange = {
            form.texts[field] = it
            form.errors.remove(field)
        },
        label = { Text(label(form, field)) },
        placeholder = { Text(field.hint) },
        supportingText = supportingText(error),
        isError = error != null,
        singleLine = !field.multiline,
        minLines = if (field.multiline) 4 else 1,
        modifier = if (field.multiline || field.hint.contains("separated")) Modifier.fillMaxWidth() else Modifier.width(260.dp),
    )
}

@Composable
private fun ParamsEditor(tab: ItemTab, form: ItemForm, field: ItemField.Params) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextFieldEditor(form, field)
        SearchDropdown(
            label = "Add param",
            selected = "",
            search = { query -> tab.params.search(query) },
            describe = ::describeParam,
            onSelect = { param -> tab.addParam(field, param) },
            modifier = Modifier.width(PICKER_WIDTH),
            placeholder = "Search params by name or id",
        )
    }
}

private fun describeParam(param: ParamInfo) = buildString {
    append(param.label)
    append(" (")
    append(param.id)
    append(')')
    if (param.type?.isString == true) {
        append(" - text")
    }
    if (param.npcCount > 0) {
        append(" - used by ")
        append(param.npcCount)
        append(" items")
    }
}

private val PICKER_WIDTH = 420.dp

@Composable
private fun FlagEditor(form: ItemForm, field: ItemField.Flag) {
    val checked = form.flags.getValue(field)
    Row(Modifier.toggleable(checked, role = Role.Checkbox) { form.flags[field] = it }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label(form, field))
    }
}

private fun label(form: ItemForm, field: ItemField) = buildString {
    append(field.label)
    append(" - ")
    append(field.opcode)
    if (form.edited(field)) {
        append(" *")
    }
}

private fun supportingText(error: String?): (@Composable () -> Unit)? = error?.let { { Text(it) } }

@Composable
private fun CreateItemDialog(tab: ItemTab) {
    val scope = rememberCoroutineScope()
    var idText by remember { mutableStateOf("") }
    var copy by remember { mutableStateOf(false) }
    var error: String? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) { idText = tab.nextFreeId().toString() }

    val current = tab.form
    AlertDialog(
        onDismissRequest = { tab.showCreateDialog = false },
        title = { Text("Create new item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = idText,
                    onValueChange = {
                        idText = it
                        error = null
                    },
                    label = { Text("Item id") },
                    supportingText = { Text(error ?: "Defaults to the next free id") },
                    isError = error != null,
                    singleLine = true,
                )
                if (current != null && !current.isNew) {
                    Row(Modifier.toggleable(copy, role = Role.Checkbox) { copy = it }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copy, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text("Copy saved values from item ${current.id}")
                    }
                }
                if (current?.isDirty == true) {
                    Text("Unsaved changes to item ${current.id} will be discarded.", color = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(Modifier.width(360.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    val id = idText.trim().toIntOrNull()
                    error = when {
                        id == null || id < 0 -> "Not a valid id"
                        id > 0xFFFF -> "Must be at most 65535"
                        tab.exists(id) -> "Item $id already exists"
                        else -> null
                    }
                    if (error == null && id != null) {
                        tab.create(id, copy && current != null && !current.isNew)
                    }
                }
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = { tab.showCreateDialog = false }) { Text("Cancel") } },
    )
}
