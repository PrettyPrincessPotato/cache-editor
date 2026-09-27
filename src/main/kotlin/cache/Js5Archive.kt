package cache

import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import util.g1

abstract class Js5Archive<T : Type>(val cache: CacheLibrary) {

    abstract fun archive(): Int

    abstract fun group(id: Int): Int

    abstract fun file(id: Int): Int

    abstract fun readOpcode(type: Type, buf: ByteBuf, code: Int)

    abstract fun create(id: Int): T

    open fun size(): Int {
        val lastGroup = cache.index(archive())?.archives()?.maxByOrNull { it.id } ?: return 0
        val lastFile = lastGroup.files().maxOfOrNull { it.id } ?: -1
        val groupSize = file(-1) + 1
        return lastGroup.id * groupSize + lastFile + 1
    }

    fun decode(id: Int): T? {
        val data = cache.data(archive(), group(id), file(id)) ?: return null
        return decode(id, data)
    }

    fun decode(id: Int, data: ByteArray): T {
        val type = create(id)
        val buf: ByteBuf = Unpooled.wrappedBuffer(data)
        while (true) {
            val code = buf.g1()
            if (code == 0) {
                break
            }
            readOpcode(type, buf, code)
        }
        return type
    }

    fun decode(): List<T> {
        return (0 until size()).mapNotNull { decode(it) }
    }
}
