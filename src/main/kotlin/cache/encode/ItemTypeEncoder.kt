package cache.encode

import cache.types.ItemType
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import util.p1
import util.p2
import util.p3
import util.p4
import util.pjstr

object ItemTypeEncoder {

    private val DEFAULT_FLOOR_OPTIONS = arrayOf(null, null, "Take", null, null, "Examine")
    private val DEFAULT_OPTIONS = arrayOf(null, null, null, null, "Drop")

    fun encode(type: ItemType): ByteArray {
        val buf = Unpooled.buffer()
        try {
            buf.encode(type)
            return ByteBufUtil.getBytes(buf)
        } finally {
            buf.release()
        }
    }

    private fun ByteBuf.encode(type: ItemType) {
        with(type) {
            if (name != "null") { p1(2); pjstr(name) }

            if (spriteScale != 2000) { p1(4); p2(spriteScale) }

            if (stackable != 0) p1(11)

            if (cost != 1) { p1(12); p4(cost) }

            if (members) p1(16)

            if (primaryMaleModel != -1) { p1(23); p2(primaryMaleModel) }

            if (primaryFemaleModel != -1) { p1(25); p2(primaryFemaleModel) }

            // floorOptions has 6 slots, but only indices 0..4 map to opcodes 30..34;
            // index 5 ("Examine") is a frozen default with no opcode.
            for (i in 0 until 5) {
                val op = floorOptions[i]
                if (op != DEFAULT_FLOOR_OPTIONS[i]) { p1(30 + i); pjstr(op ?: "") }
            }

            for (i in 0 until 5) {
                val op = options[i]
                if (op != DEFAULT_OPTIONS[i]) { p1(35 + i); pjstr(op ?: "") }
            }

            if (exchangeable) p1(65)

            if (dummyItem != 0) { p1(96); p1(dummyItem) }

            if (noteId != -1) { p1(97); p2(noteId) }

            if (notedTemplateId != -1) { p1(98); p2(notedTemplateId) }

            if (lendId != -1) { p1(121); p2(lendId) }

            if (lendTemplateId != -1) { p1(122); p2(lendTemplateId) }

            if (params.isNotEmpty()) {
                p1(249)
                p1(params.size)
                params.forEach { (key, value) ->
                    p1(if (value is String) 1 else 0)
                    p3(key)
                    when (value) {
                        is String -> pjstr(value)
                        is Int -> p4(value)
                        else -> error("Unsupported param type ${value::class.simpleName} for key $key")
                    }
                }
            }

            raw.forEach { opc ->
                p1(opc.opcode)
                opc.bytes.forEach { p1(it.toInt()) }
            }

            p1(0)
        }
    }
}
