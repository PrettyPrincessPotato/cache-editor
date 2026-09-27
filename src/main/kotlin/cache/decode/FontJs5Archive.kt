package cache.decode

import cache.Js5
import cache.Js5Archive
import cache.Type
import cache.types.FontType
import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import util.g1
import util.g2
import util.g3
import util.g4
import util.gjstr

/**
 * Stub decoder for fonts. Mirrors [NPCJs5Archive].
 *
 * TODO(font): the opcode table below and the group/file mapping are placeholders.
 * Fill in the real font opcodes (and verify the group/file mapping) by reverse-engineering
 * the font archive from the client / decompilers. Unknown opcodes error on purpose so a
 * wrong table is loud rather than silently corrupting data.
 */
class FontJs5Archive(cache: CacheLibrary) : Js5Archive<FontType>(cache) {

    override fun archive(): Int = Js5.JAGEX_FONTS

    // TODO(font): NPC uses (id ushr 7, id and 0x7f). Fonts may differ — verify against the archive.
    override fun group(id: Int): Int = id ushr 7

    override fun file(id: Int): Int = id and 0x7f

    override fun create(id: Int) = FontType(id)

    override fun readOpcode(type: Type, buf: ByteBuf, code: Int) {
        val font = type as? FontType ?: error("readOpcode expected FontType, got ${type::class.simpleName}")

        with(font) {
            when (code) {
                // TODO(font): replace these placeholder opcodes with the real font format.
                1 -> placeholder = buf.g2()

                2 -> name = buf.gjstr()

                3 -> placeholderFlag = true

                249 -> {
                    repeat(buf.g1()) {
                        val isString = buf.g1() == 1
                        val key = buf.g3()
                        params[key] = if (isString) buf.gjstr() else buf.g4()
                    }
                }

                else -> error("Unknown font opcode $code for font ${font.id} (stub table: real opcodes not yet defined)")
            }
        }
    }
}
