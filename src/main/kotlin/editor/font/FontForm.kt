package editor.font

import androidx.compose.runtime.mutableStateMapOf
import cache.types.FontType

/**
 * Mirrors [NpcForm]: editable state over a [FontType]. `build(target)` writes every field
 * onto a copy of the model and collects validation errors, so saving never mutates the
 * loaded object until the form is explicitly built.
 */
class FontForm(val base: FontType, val isNew: Boolean) {
    val id: Int get() = base.id

    private val fields = FONT_SECTIONS.flatMap { it.fields }

    val texts = mutableStateMapOf<FontField.Text, String>().apply {
        fields.filterIsInstance<FontField.Text>().forEach { put(it, it.read(base)) }
    }

    val flags = mutableStateMapOf<FontField.Flag, Boolean>().apply {
        fields.filterIsInstance<FontField.Flag>().forEach { put(it, it.read(base)) }
    }

    val errors = mutableStateMapOf<FontField, String>()

    val isDirty: Boolean
        get() = isNew || texts.any { (field, text) -> text != field.read(base) } ||
            flags.any { (field, value) -> value != field.read(base) }

    fun edited(field: FontField): Boolean = when (field) {
        is FontField.Text -> texts[field] != field.read(base)
        is FontField.Flag -> flags[field] != field.read(base)
        else -> false
    }

    fun build(target: FontType): FontType? {
        errors.clear()
        for (field in fields) {
            try {
                when (field) {
                    is FontField.Text -> field.write(target, texts.getValue(field))
                    is FontField.Flag -> field.write(target, flags.getValue(field))
                    else -> error("Unsupported font field type")
                }
            } catch (e: IllegalArgumentException) {
                errors[field] = e.message ?: "Invalid"
            }
        }
        return if (errors.isEmpty()) target else null
    }
}
