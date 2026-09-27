package editor.item

import cache.types.ItemType
import editor.ParamNames
import editor.npc.paramLine

sealed class ItemField(val label: String, val opcode: String, val unverified: Boolean) {
    open class Text(
        label: String,
        opcode: String,
        unverified: Boolean = false,
        val hint: String = "",
        val multiline: Boolean = false,
        val read: (ItemType) -> String,
        val write: (ItemType, String) -> Unit,
    ) : ItemField(label, opcode, unverified)

    class Flag(
        label: String,
        opcode: String,
        unverified: Boolean = false,
        val read: (ItemType) -> Boolean,
        val write: (ItemType, Boolean) -> Unit,
    ) : ItemField(label, opcode, unverified)

    class Params(
        label: String,
        opcode: String,
        hint: String,
        read: (ItemType) -> String,
        write: (ItemType, String) -> Unit,
    ) : Text(label, opcode, hint = hint, multiline = true, read = read, write = write)
}

class ItemSection(val title: String, val fields: List<ItemField>)

private val UBYTE = 0..255
private val SSHORT = -32768..32767
private val OPT_USHORT = -1..65535
private val INT = Int.MIN_VALUE..Int.MAX_VALUE

private fun parseInt(text: String, range: IntRange): Int {
    val value = text.trim().toIntOrNull() ?: throw IllegalArgumentException("Not a number")
    require(value in range) { "Must be between ${range.first} and ${range.last}" }
    return value
}

private fun int(label: String, opcode: Int, range: IntRange, unverified: Boolean = false, get: (ItemType) -> Int, set: (ItemType, Int) -> Unit) =
    ItemField.Text(label, opcode.toString(), unverified, hint = "${range.first}..${range.last}", read = { get(it).toString() }, write = { type, text -> set(type, parseInt(text, range)) })

private fun flag(label: String, opcode: Int, unverified: Boolean = false, get: (ItemType) -> Boolean, set: (ItemType, Boolean) -> Unit) =
    ItemField.Flag(label, opcode.toString(), unverified, get, set)

private fun op(label: String, opcode: Int, index: Int, ops: (ItemType) -> Array<String?>) =
    ItemField.Text(label, opcode.toString(), hint = "Blank for none", read = { ops(it)[index] ?: "" }, write = { type, text -> ops(type)[index] = text.ifBlank { null } })

private val params = ItemField.Params(
    "Params",
    "249",
    hint = "One per line: name = 123 or name = \"text\"",
    read = { type -> type.params.entries.joinToString("\n") { (key, value) -> paramLine(key, value) } },
    write = { type, text ->
        val params = linkedMapOf<Int, Any>()
        for (line in text.lines().filter { it.isNotBlank() }) {
            val index = line.indexOf('=')
            require(index != -1) { "Expected key = value but got '${line.trim()}'" }
            val key = parseParamKey(line.substring(0, index))
            val value = line.substring(index + 1).trim()
            params[key] = if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
                value.substring(1, value.length - 1)
            } else {
                value.toIntOrNull() ?: throw IllegalArgumentException("Value for ${line.substring(0, index).trim()} must be a number or \"quoted text\"")
            }
        }
        require(params.size <= 255) { "At most 255 params" }
        type.params.clear()
        type.params.putAll(params)
    },
)

private fun parseParamKey(text: String): Int {
    val key = text.trim()
    val id = key.toIntOrNull() ?: ParamNames.id(key) ?: throw IllegalArgumentException("Unknown param '$key'")
    require(id in 0..0xFFFFFF) { "Param id must be between 0 and ${0xFFFFFF}" }
    return id
}

val ITEM_SECTIONS = listOf(
    ItemSection(
        "General",
        listOf(
            ItemField.Text("Name", "2", read = { it.name }, write = { type, text -> type.name = text }),
            int("Sprite scale", 4, SSHORT, get = { it.spriteScale }, set = { t, v -> t.spriteScale = v }),
            int("Cost", 12, INT, get = { it.cost }, set = { t, v -> t.cost = v }),
            flag("Stackable", 11, get = { it.stackable == 1 }, set = { t, v -> t.stackable = if (v) 1 else 0 }),
            flag("Members", 16, get = { it.members }, set = { t, v -> t.members = v }),
            flag("Exchangeable", 65, get = { it.exchangeable }, set = { t, v -> t.exchangeable = v }),
            int("Dummy item", 96, UBYTE, get = { it.dummyItem }, set = { t, v -> t.dummyItem = v }),
            int("Note id", 97, SSHORT, get = { it.noteId }, set = { t, v -> t.noteId = v }),
            int("Noted template id", 98, SSHORT, get = { it.notedTemplateId }, set = { t, v -> t.notedTemplateId = v }),
            int("Lend id", 121, SSHORT, get = { it.lendId }, set = { t, v -> t.lendId = v }),
            int("Lend template id", 122, SSHORT, get = { it.lendTemplateId }, set = { t, v -> t.lendTemplateId = v }),
            int("Primary male model", 23, OPT_USHORT, get = { it.primaryMaleModel }, set = { t, v -> t.primaryMaleModel = v }),
            int("Primary female model", 25, OPT_USHORT, get = { it.primaryFemaleModel }, set = { t, v -> t.primaryFemaleModel = v }),
        ),
    ),
    ItemSection(
        "Options",
        (0 until 5).map { op("Option ${it + 1}", 35 + it, it) { type -> type.options } },
    ),
    ItemSection(
        "Floor options",
        (0 until 5).map { op("Floor option ${it + 1}", 30 + it, it) { type -> type.floorOptions } },
    ),
    ItemSection(
        "Params",
        listOf(params),
    ),
)
