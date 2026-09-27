package editor.npc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import editor.SearchDropdown

@Composable
fun NpcPicker(
    label: String,
    value: Int,
    npcs: List<NpcSummary>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    noneLabel: String = NOTHING,
) {
    val byId = remember(npcs) { npcs.associateBy { it.id } }
    SearchDropdown(
        label = label,
        selected = if (value == -1) noneLabel else byId[value]?.let { describe(it) } ?: "$value (not in cache)",
        search = { query -> npcs.search(query) },
        describe = { npc -> if (npc == null) noneLabel else describe(npc) },
        onSelect = { npc -> onSelect(npc?.id ?: -1) },
        modifier = modifier,
        enabled = enabled,
        pinned = listOf(null),
    )
}

const val NOTHING = "Nothing (-1)"

private fun describe(npc: NpcSummary) = buildString {
    append(npc.id)
    append(" - ")
    append(npc.name)
    if (npc.combatLevel != -1) {
        append(" (lvl ")
        append(npc.combatLevel)
        append(')')
    }
}
