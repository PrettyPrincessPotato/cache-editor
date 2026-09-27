package editor.npc

import androidx.compose.runtime.mutableStateMapOf
import cache.types.NpcType

class NpcForm(val base: NpcType, val isNew: Boolean) {
    val id: Int get() = base.id

    private val fields = NPC_SECTIONS.flatMap { it.fields }

    val texts = mutableStateMapOf<NpcField.Text, String>().apply {
        fields.filterIsInstance<NpcField.Text>().forEach { put(it, it.read(base)) }
    }

    val flags = mutableStateMapOf<NpcField.Flag, Boolean>().apply {
        fields.filterIsInstance<NpcField.Flag>().forEach { put(it, it.read(base)) }
    }

    val transforms = mutableStateMapOf<NpcField.Transforms, NpcTransforms>().apply {
        fields.filterIsInstance<NpcField.Transforms>().forEach { put(it, it.read(base)) }
    }

    val errors = mutableStateMapOf<NpcField, String>()

    val isDirty: Boolean
        get() = isNew || texts.any { (field, text) -> text != field.read(base) } || flags.any { (field, value) -> value != field.read(base) } ||
            transforms.any { (field, value) -> value != field.read(base) }

    fun edited(field: NpcField): Boolean = when (field) {
        is NpcField.Text -> texts[field] != field.read(base)
        is NpcField.Flag -> flags[field] != field.read(base)
        is NpcField.Transforms -> transforms[field] != field.read(base)
    }

    fun build(target: NpcType): NpcType? {
        errors.clear()
        for (field in fields) {
            try {
                when (field) {
                    is NpcField.Text -> field.write(target, texts.getValue(field))
                    is NpcField.Flag -> field.write(target, flags.getValue(field))
                    is NpcField.Transforms -> field.write(target, transforms.getValue(field))
                }
            } catch (e: IllegalArgumentException) {
                errors[field] = e.message ?: "Invalid"
            }
        }
        return if (errors.isEmpty()) target else null
    }
}
