package cache.types

import cache.Type

/**
 * Stub model for font editing. Mirrors [NpcType] in shape, but the fields below are
 * placeholders standing in for the real font wire format.
 *
 * TODO(font): replace the placeholder fields with the real font fields once the font
 * archive format is known (reverse-engineer from the client / decompilers). Each field's
 * default value must match the decoder's assumption and the encoder's "skip if default".
 */
class FontType(override val id: Int) : Type(id) {
    // Placeholder fields (mirrors the NPC variety: string, unsigned short, boolean, params).
    var name: String = "null"
    var placeholder: Int = -1
    var placeholderFlag: Boolean = false

    val params: MutableMap<Int, Any> = linkedMapOf()
}
