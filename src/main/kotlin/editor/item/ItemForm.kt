package editor.item

import androidx.compose.runtime.mutableStateMapOf
import cache.types.ItemType

// Opcodes of the fields whose value is derived for noted/lent items:
// Name (2), Sprite scale (4), Cost (12), Members (16). They are displayed read-only
// with their derived value and never written back, so the wire values are preserved.
private val DERIVED_OPCODES = setOf("2", "4", "12", "16")

class ItemForm(val base: ItemType, val isNew: Boolean, val derived: DerivedValues? = null) {
    val id: Int get() = base.id

    private val fields = ITEM_SECTIONS.flatMap { it.fields }

    private fun isDerivedField(field: ItemField) = derived != null && field.opcode in DERIVED_OPCODES
    private val editableFields = fields.filterNot { isDerivedField(it) }

    val texts = mutableStateMapOf<ItemField.Text, String>().apply {
        fields.filterIsInstance<ItemField.Text>().forEach { put(it, derivedText(it) ?: it.read(base)) }
    }

    val flags = mutableStateMapOf<ItemField.Flag, Boolean>().apply {
        fields.filterIsInstance<ItemField.Flag>().forEach { put(it, derivedFlag(it) ?: it.read(base)) }
    }

    val errors = mutableStateMapOf<ItemField, String>()

    val isDirty: Boolean
        get() = isNew ||
            editableFields.filterIsInstance<ItemField.Text>().any { it -> texts[it] != it.read(base) } ||
            editableFields.filterIsInstance<ItemField.Flag>().any { it -> flags[it] != it.read(base) }

    fun edited(field: ItemField): Boolean {
        if (isDerivedField(field)) return false
        return when (field) {
            is ItemField.Text -> texts[field] != field.read(base)
            is ItemField.Flag -> flags[field] != field.read(base)
        }
    }

    fun isReadOnly(field: ItemField): Boolean = isDerivedField(field)

    private fun derivedText(field: ItemField.Text): String? = derived?.let {
        when (field.opcode) {
            "2" -> it.name
            "4" -> it.spriteScale.toString()
            "12" -> it.cost.toString()
            else -> null
        }
    }

    private fun derivedFlag(field: ItemField.Flag): Boolean? = derived?.let {
        when (field.opcode) {
            "16" -> it.members
            else -> null
        }
    }

    fun build(target: ItemType): ItemType? {
        errors.clear()
        for (field in editableFields) {
            try {
                when (field) {
                    is ItemField.Text -> field.write(target, texts[field]!!)
                    is ItemField.Flag -> field.write(target, flags[field]!!)
                }
            } catch (e: IllegalArgumentException) {
                errors[field] = e.message ?: "Invalid"
            }
        }
        return if (errors.isEmpty()) target else null
    }
}
