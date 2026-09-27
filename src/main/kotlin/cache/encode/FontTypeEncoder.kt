package cache.encode

import cache.types.FontType
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import util.p1
import util.p2
import util.p3
import util.p4
import util.pjstr

/**
 * Stub encoder for fonts. Mirrors [NpcTypeEncoder]. It is the exact inverse of
 * [FontJs5Archive]: a field at its default value is not written, which is what makes
 * decode(encode(x)) == x hold.
 *
 * TODO(font): replace these placeholder opcodes with the real font format, keeping the
 * default-skip condition in sync with the model defaults and the decoder.
 */
object FontTypeEncoder {

    fun encode(type: FontType): ByteArray {
        val buf = Unpooled.buffer()
        try {
            buf.encode(type)
            return ByteBufUtil.getBytes(buf)
        } finally {
            buf.release()
        }
    }

    private fun ByteBuf.encode(type: FontType) {
        // TODO(font): replace these placeholder opcodes with the real font format.
        if (type.placeholder != -1) {
            p1(1)
            p2(type.placeholder)
        }

        if (type.name != "null") {
            p1(2)
            pjstr(type.name)
        }

        if (type.placeholderFlag) {
            p1(3)
        }

        if (type.params.isNotEmpty()) {
            p1(249)
            p1(type.params.size)
            type.params.forEach { (key, value) ->
                p1(if (value is String) 1 else 0)
                p3(key)
                when (value) {
                    is String -> pjstr(value)
                    is Int -> p4(value)
                    else -> error("Unsupported param type ${value::class.simpleName} for key $key")
                }
            }
        }

        p1(0)
    }
}
