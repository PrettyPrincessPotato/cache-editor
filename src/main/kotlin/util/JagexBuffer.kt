package util

import io.netty.buffer.ByteBuf
import io.netty.util.ByteProcessor
import java.nio.charset.Charset

private const val HALF_UBYTE = 0x80

public fun ByteBuf.g1(): Int {
    return readUnsignedByte().toInt()
}

public fun ByteBuf.g1s(): Int {
    return readByte().toInt()
}

public fun ByteBuf.g2(): Int {
    return readUnsignedShort()
}

public fun ByteBuf.g2s(): Int {
    return readShort().toInt()
}

public fun ByteBuf.g3(): Int {
    return readUnsignedMedium()
}

public fun ByteBuf.g4(): Int {
    return readInt()
}

public fun ByteBuf.gSmart1or2(): Int {
    val peek = getUnsignedByte(readerIndex()).toInt()
    return if (peek < HALF_UBYTE) {
        g1()
    } else {
        g2() - 32768
    }
}


public fun ByteBuf.gjstr(): String {
    return readString()
}

private fun ByteBuf.readString(charset: Charset = Cp1252Charset): String {
    val start = readerIndex()

    val end = forEachByte(ByteProcessor.FIND_NUL)
    require(end != -1) {
        "Unterminated string"
    }

    val s = toString(start, end - start, charset)
    readerIndex(end + 1)
    return s
}

public fun ByteBuf.p1(value: Int): ByteBuf {
    return writeByte(value)
}

public fun ByteBuf.p2(value: Int): ByteBuf {
    return writeShort(value)
}

public fun ByteBuf.p3(value: Int): ByteBuf {
    return writeMedium(value)
}

public fun ByteBuf.p4(value: Int): ByteBuf {
    return writeInt(value)
}

public fun ByteBuf.pjstr(value: String, charset: Charset = Cp1252Charset): ByteBuf {
    writeCharSequence(value, charset)
    return writeByte(0)
}
