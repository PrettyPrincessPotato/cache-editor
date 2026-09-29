package cache.decode

import cache.Js5
import cache.Js5Archive
import cache.Type
import cache.types.ItemType
import cache.types.RawOpc
import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import util.g1
import util.g2
import util.g2s
import util.g3
import util.g4
import util.gjstr

class ItemJs5Archive(cache: CacheLibrary) : Js5Archive<ItemType>(cache) {

    override fun archive(): Int = Js5.JS5_CONFIG_ITEMS.id

    override fun group(id: Int): Int = id ushr 8

    override fun file(id: Int): Int = id and 0xff

    override fun create(id: Int) = ItemType(id)

    override fun readOpcode(type: Type, buf: ByteBuf, code: Int) {
        val item = type as? ItemType ?: error("readOpcode expected ItemType, got ${type::class.simpleName}")

        with(item) {
            when (code) {
                2 -> name = buf.gjstr()

                4 -> spriteScale = buf.g2s()

                11 -> stackable = 1

                12 -> cost = buf.g4()

                16 -> members = true

                23 -> primaryMaleModel = buf.g2()

                25 -> primaryFemaleModel = buf.g2()

                in 30..34 -> floorOptions[code - 30] = buf.gjstr()

                in 35..39 -> options[code - 35] = buf.gjstr()

                65 -> exchangeable = true

                96 -> dummyItem = buf.g1()

                97 -> noteId = buf.g2s()

                98 -> notedTemplateId = buf.g2s()

                121 -> lendId = buf.g2s()

                122 -> lendTemplateId = buf.g2s()

                249 -> {
                    repeat(buf.g1()) {
                        val isString = buf.g1() == 1
                        val key = buf.g3()
                        params[key] = if (isString) buf.gjstr() else buf.g4()
                    }
                }

                else -> raw.add(RawOpc(code, buf.readRawPayload(code, item.id)))
            }
        }
    }

    // Payload sizes for the opcodes the server skips, taken verbatim from Void's ItemDecoder.
    private fun ByteBuf.readRawPayload(code: Int, itemId: Int): ByteArray {
        val bytes = mutableListOf<Byte>()
        when (code) {
            40, 41 -> { val c = g1(); bytes.add(c.toByte()); repeat(c * 4) { bytes.add(readByte()) } }
            42 -> { val c = g1(); bytes.add(c.toByte()); repeat(c) { bytes.add(readByte()) } }
            132 -> { val c = g1(); bytes.add(c.toByte()); repeat(c * 2) { bytes.add(readByte()) } }
            in TWO -> repeat(2) { bytes.add(readByte()) }
            in 100..109 -> repeat(4) { bytes.add(readByte()) }
            in ONE -> repeat(1) { bytes.add(readByte()) }
            in 125..130 -> repeat(3) { bytes.add(readByte()) }
            else -> error("Unknown item opcode $code for item $itemId")
        }
        return bytes.toByteArray()
    }

    private companion object {
        private val TWO = setOf(1, 5, 6, 7, 8, 18, 24, 26, 78, 79, 90, 91, 92, 93, 95, 110, 111, 112, 139, 140)
        private val ONE = setOf(113, 114, 115, 134)
    }
}
