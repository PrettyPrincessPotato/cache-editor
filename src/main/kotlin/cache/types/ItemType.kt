package cache.types

import cache.Type

class ItemType(override val id: Int) : Type(id) {
    var name: String = "null"
    var spriteScale: Int = 2000
    var stackable: Int = 0
    var cost: Int = 1
    var members: Boolean = false
    val floorOptions: Array<String?> = arrayOf(null, null, "Take", null, null, "Examine")
    val options: Array<String?> = arrayOf(null, null, null, null, "Drop")
    var exchangeable: Boolean = false
    var dummyItem: Int = 0
    var noteId: Int = -1
    var notedTemplateId: Int = -1
    var lendId: Int = -1
    var lendTemplateId: Int = -1
    var primaryMaleModel: Int = -1
    var primaryFemaleModel: Int = -1
    val equipIndex: Int
        get() = when {
            primaryMaleModel != -1 -> 1
            primaryFemaleModel != -1 -> 2
            else -> -1
        }
    val params: MutableMap<Int, Any> = linkedMapOf()

    // Opcodes the server does not model, preserved verbatim so saving never drops cache data.
    val raw: MutableList<RawOpc> = mutableListOf()
}

class RawOpc(val opcode: Int, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean = other is RawOpc && other.opcode == opcode && other.bytes.contentEquals(bytes)
    override fun hashCode(): Int = 31 * opcode + bytes.contentHashCode()
}
