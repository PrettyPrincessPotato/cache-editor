package editor.font

import cache.types.FontType

/**
 * Declarative description of each editable font field — the same pattern as NpcFields.kt.
 * Each field pairs a `read` (model -> UI string/bool) and a `write` (UI -> model) with
 * validation, plus the opcode string shown in the UI.
 *
 * TODO(font): replace FONT_SECTIONS with the real font fields once the wire format is known.
 */
sealed class FontField(val label: String, val opcode: String, val unverified: Boolean) {
    class Text(
        label: String,
        opcode: String,
        unverified: Boolean = false,
        val hint: String = "",
        val read: (FontType) -> String,
        val write: (FontType, String) -> Unit,
    ) : FontField(label, opcode, unverified)

    class Flag(
        label: String,
        opcode: String,
        unverified: Boolean = false,
        val read: (FontType) -> Boolean,
        val write: (FontType, Boolean) -> Unit,
    ) : FontField(label, opcode, unverified)
}

class FontSection(val title: String, val fields: List<FontField>)

private fun parseInt(text: String, range: IntRange): Int {
    val value = text.trim().toIntOrNull() ?: throw IllegalArgumentException("Not a number")
    require(value in range) { "Must be between ${range.first} and ${range.last}" }
    return value
}

private fun int(label: String, opcode: Int, range: IntRange, unverified: Boolean = false, get: (FontType) -> Int, set: (FontType, Int) -> Unit) =
    FontField.Text(label, opcode.toString(), unverified, hint = "${range.first}..${range.last}", read = { get(it).toString() }, write = { type, text -> set(type, parseInt(text, range)) })

private fun flag(label: String, opcode: Int, unverified: Boolean = false, get: (FontType) -> Boolean, set: (FontType, Boolean) -> Unit) =
    FontField.Flag(label, opcode.toString(), unverified, get, set)

// Placeholder field set (string / unsigned short / boolean) so the scaffold has an editable UI.
// TODO(font): swap for the real font fields.
val FONT_SECTIONS = listOf(
    FontSection(
        "General (stub)",
        listOf(
            FontField.Text("Name", "2", read = { it.name }, write = { type, text -> type.name = text }),
            int("Placeholder", 1, -1..65535, get = { it.placeholder }, set = { t, v -> t.placeholder = v }),
            flag("Placeholder flag", 3, get = { it.placeholderFlag }, set = { t, v -> t.placeholderFlag = v }),
        ),
    ),
)
