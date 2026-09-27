package editor.item

import androidx.compose.runtime.mutableStateMapOf
import cache.types.ItemType

class ItemForm(val base: ItemType, val isNew: Boolean) {
    val id: Int get() = base.id

    private val fields = ITEM_SECTIONS.flatMap { it.fields }

    val texts = mutableStateMapOf<ItemField.Text, String>().apply {
        fields.filterIsInstance<ItemField.Text>().forEach { put(it, it.read(base)) }
    }

    val flags = mutableStateMapOf<ItemField.Flag, Boolean>().apply {
        fields.filterIsInstance<ItemField.Flag>().forEach { put(it, it.read(base)) }
    }

    val errors = mutableStateMapOf<ItemField, String>()

    val isDirty: Boolean
        get() = isNew || texts.any { (field, text) -> text != field.read(base) } ||
            flags.any { (field, value) -> value != field.read(base) }

    fun edited(field: ItemField): Boolean = when (field) {
        is ItemField.Text -> texts[field] != field.read(base)
        is ItemField.Flag -> flags[field] != field.read(base)
    }

    fun build(target: ItemType): ItemType? {
        errors.clear()
        for (field in fields) {
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
