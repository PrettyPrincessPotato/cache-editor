package editor.item

import androidx.compose.runtime.mutableStateMapOf
import cache.types.ItemType

class ItemForm(val base: ItemType, val isNew: Boolean, val derivedName: String? = null) {
    val id: Int get() = base.id

    private val fields = ITEM_SECTIONS.flatMap { it.fields }
    private val nameField = fields.first { it.opcode == "2" }
    // The Name of a noted/lent item is shown as its derived value, but the wire
    // value must be preserved on save, so the field is never treated as editable.
    private val isDerivedName = derivedName != null
    private val editableFields = fields.filterNot { it == nameField && isDerivedName }

    val texts = mutableStateMapOf<ItemField.Text, String>().apply {
        fields.filterIsInstance<ItemField.Text>().forEach {
            put(it, if (it == nameField && isDerivedName) derivedName!! else it.read(base))
        }
    }

    val flags = mutableStateMapOf<ItemField.Flag, Boolean>().apply {
        fields.filterIsInstance<ItemField.Flag>().forEach { put(it, it.read(base)) }
    }

    val errors = mutableStateMapOf<ItemField, String>()

    val isDirty: Boolean
        get() = isNew ||
            editableFields.filterIsInstance<ItemField.Text>().any { it -> texts[it] != it.read(base) } ||
            editableFields.filterIsInstance<ItemField.Flag>().any { it -> flags[it] != it.read(base) }

    fun edited(field: ItemField): Boolean {
        if (field == nameField && isDerivedName) {
            return false
        }
        return when (field) {
            is ItemField.Text -> texts[field] != field.read(base)
            is ItemField.Flag -> flags[field] != field.read(base)
        }
    }

    fun isReadOnly(field: ItemField): Boolean = field == nameField && isDerivedName

    fun build(target: ItemType): ItemType? {
        errors.clear()
        for (field in editableFields) {
            try {
                when (field) {
                    is ItemField.Text -> field.write(target, texts.getValue(field))
                    is ItemField.Flag -> field.write(target, flags.getValue(field))
                }
            } catch (e: IllegalArgumentException) {
                errors[field] = e.message ?: "Invalid"
            }
        }
        return if (errors.isEmpty()) target else null
    }
}
