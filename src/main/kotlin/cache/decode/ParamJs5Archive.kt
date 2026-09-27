package cache.decode

import cache.Js5
import cache.Js5Archive
import cache.Type
import cache.types.ParamType
import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import util.Cp1252Charset
import util.g1
import util.g4
import util.gjstr

class ParamJs5Archive(cache: CacheLibrary) : Js5Archive<ParamType>(cache) {

    override fun archive(): Int = Js5.CONFIG

    override fun group(id: Int): Int = GROUP

    override fun file(id: Int): Int = id

    override fun size(): Int {
        val lastFile = cache.index(archive())?.archive(GROUP)?.files()?.maxOfOrNull { it.id } ?: return 0
        return lastFile + 1
    }

    override fun create(id: Int) = ParamType(id)

    override fun readOpcode(type: Type, buf: ByteBuf, code: Int) {
        val paramType = type as? ParamType ?: error("readOpcode expected ParamType, got ${type::class.simpleName}")

        with(paramType) {
            when (code) {
                1 -> this.type = String(byteArrayOf(buf.g1().toByte()), Cp1252Charset)[0]

                2 -> defaultInt = buf.g4()

                4 -> autoDisable = false

                5 -> defaultString = buf.gjstr()

                else -> error("Unknown param opcode $code for param ${paramType.id}")
            }
        }
    }

    private companion object {
        const val GROUP = 11
    }
}
