package editor.npc

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
import kotlinx.coroutines.launch

@Composable
fun NpcTabContent(tab: NpcTab) {
    Row(Modifier.fillMaxSize()) {
        NpcList(tab, Modifier.width(320.dp).fillMaxHeight())
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight()) {
            val form = tab.form
            if (form == null) {
                Text(
                    "Select an npc to view it, or create a new one",
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NpcEditor(tab, form)
            }
        }
    }

    tab.pendingSelection?.let { id ->
        AlertDialog(
            onDismissRequest = { tab.pendingSelection = null },
            title = { Text("Discard changes?") },
            text = { Text("Npc ${tab.form?.id} has unsaved changes which will be lost.") },
            confirmButton = { TextButton(onClick = { tab.load(id) }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { tab.pendingSelection = null }) { Text("Keep editing") } },
        )
    }

    if (tab.showCreateDialog) {
        CreateNpcDialog(tab)
    }
}

@Composable
private fun NpcList(tab: NpcTab, modifier: Modifier) {
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { tab.showCreateDialog = true }, enabled = tab.repository != null, modifier = Modifier.fillMaxWidth()) {
            Text("Create new NPC")
        }
        OutlinedTextField(
            value = tab.query,
            onValueChange = { tab.query = it },
            label = { Text("Search name or id") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${tab.filtered.size} of ${tab.npcs.size} npcs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 10.dp)) {
                items(tab.filtered, key = { it.id }) { npc ->
                    val selected = npc.id == tab.form?.id
                    Row(
                        Modifier.fillMaxWidth()
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                            .clickable { tab.select(npc.id) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(npc.id.toString(), Modifier.width(48.dp), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(npc.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (npc.combatLevel != -1) {
                            Text("lvl ${npc.combatLevel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun NpcEditor(tab: NpcTab, form: NpcForm) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Npc ${form.id}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                val status = when {
                    form.isNew -> "New npc, not yet written to the cache"
                    form.isDirty -> "Unsaved changes"
                    else -> "Saved"
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = if (form.isDirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { tab.revert() }, enabled = form.isDirty && !form.isNew) { Text("Revert") }
            Button(onClick = { tab.save() }, enabled = form.isDirty) { Text("Save to cache") }
        }
        HorizontalDivider()
        Box(Modifier.weight(1f)) {
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (section in NPC_SECTIONS) {
                    Section(tab, form, section)
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Section(tab: NpcTab, form: NpcForm, section: NpcSection) {
    val fields = section.fields.filterNot { it.unverified }
    if (fields.isEmpty()) {
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (field in fields.filterIsInstance<NpcField.Text>()) {
                    if (field is NpcField.Params) {
                        ParamsEditor(tab, form, field)
                    } else {
                        TextFieldEditor(form, field)
                    }
                }
            }
            for (field in fields.filterIsInstance<NpcField.Transforms>()) {
                TransformsEditor(tab, form, field)
            }
            val flags = fields.filterIsInstance<NpcField.Flag>()
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
private fun TextFieldEditor(form: NpcForm, field: NpcField.Text) {
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
private fun ParamsEditor(tab: NpcTab, form: NpcForm, field: NpcField.Params) {
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
        append(" npcs")
    }
}

@Composable
private fun TransformsEditor(tab: NpcTab, form: NpcForm, field: NpcField.Transforms) {
    val transforms = form.transforms.getValue(field)
    val error = form.errors[field]

    fun update(change: (NpcTransforms) -> NpcTransforms) {
        form.transforms[field] = change(form.transforms.getValue(field))
        form.errors.remove(field)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label(form, field), style = MaterialTheme.typography.titleSmall)
        Text(
            "The varbit value (or varp value when varbit is -1) picks the index, so value 0 shows index 0 and so on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        transforms.ids.forEachIndexed { index, id ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NpcPicker("Index $index", id, tab.npcs, onSelect = { selected ->
                    update { it.copy(ids = it.ids.toMutableList().apply { set(index, selected) }) }
                }, Modifier.width(PICKER_WIDTH))
                if (index == transforms.ids.lastIndex) {
                    TextButton(onClick = { update { it.copy(ids = it.ids.dropLast(1)) } }) { Text("Remove") }
                }
            }
        }
        OutlinedButton(onClick = { update { it.copy(ids = it.ids + -1) } }, enabled = transforms.ids.size < 256) { Text("Add index") }
        NpcPicker(
            "Default npc",
            transforms.default,
            tab.npcs,
            onSelect = { selected -> update { it.copy(default = selected) } },
            Modifier.width(PICKER_WIDTH),
            enabled = transforms.ids.isNotEmpty(),
        )
        Text(
            "Shown when the value has no index, or its index is Nothing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (error != null) {
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private val PICKER_WIDTH = 420.dp

@Composable
private fun FlagEditor(form: NpcForm, field: NpcField.Flag) {
    val checked = form.flags.getValue(field)
    Row(Modifier.toggleable(checked, role = Role.Checkbox) { form.flags[field] = it }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(label(form, field))
    }
}

private fun label(form: NpcForm, field: NpcField) = buildString {
    append(field.label)
    append(" - ")
    append(field.opcode)
    if (form.edited(field)) {
        append(" *")
    }
}

private fun supportingText(error: String?): (@Composable () -> Unit)? = error?.let { { Text(it) } }

@Composable
private fun CreateNpcDialog(tab: NpcTab) {
    val scope = rememberCoroutineScope()
    var idText by remember { mutableStateOf("") }
    var copy by remember { mutableStateOf(false) }
    var error: String? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) { idText = tab.nextFreeId().toString() }

    val current = tab.form
    AlertDialog(
        onDismissRequest = { tab.showCreateDialog = false },
        title = { Text("Create new NPC") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = idText,
                    onValueChange = {
                        idText = it
                        error = null
                    },
                    label = { Text("Npc id") },
                    supportingText = { Text(error ?: "Defaults to the next free id") },
                    isError = error != null,
                    singleLine = true,
                )
                if (current != null && !current.isNew) {
                    Row(Modifier.toggleable(copy, role = Role.Checkbox) { copy = it }.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = copy, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text("Copy saved values from npc ${current.id}")
                    }
                }
                if (current?.isDirty == true) {
                    Text("Unsaved changes to npc ${current.id} will be discarded.", color = MaterialTheme.colorScheme.tertiary)
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
                        tab.exists(id) -> "Npc $id already exists"
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
