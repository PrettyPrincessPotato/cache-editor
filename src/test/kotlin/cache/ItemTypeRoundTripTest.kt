package cache

import cache.decode.ItemJs5Archive
import cache.encode.ItemTypeEncoder
import cache.types.ItemType
import com.displee.cache.CacheLibrary
import io.netty.buffer.Unpooled
import util.g1
import java.util.Objects
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ItemTypeRoundTripTest {

    private val cache = CacheLibrary.create(TestCache.directory.absolutePath)
    private val archive = ItemJs5Archive(cache)

    @Test
    fun `Every item in the cache re-encodes to the same values`() {
        val size = archive.size()
        assertTrue(size > 0)

        val failures = mutableListOf<String>()
        var count = 0
        var identical = 0
        for (id in 0 until size) {
            val data = cache.data(Js5.JS5_CONFIG_ITEMS.id, archive.group(id), archive.file(id)) ?: continue
            count++
            try {
                checkFullyRead(id, data)
                val encoded = ItemTypeEncoder.encode(archive.decode(id, data))
                if (encoded.contentEquals(data)) {
                    identical++
                }
                val difference = difference(archive.decode(id, data), archive.decode(id, encoded))
                if (difference != null) {
                    failures += "$id: $difference"
                }
            } catch (e: Exception) {
                failures += "$id: ${e.message}"
            }
        }
        println("Checked $count items out of $size ids, $identical byte identical, ${failures.size} failures")
        failures.take(25).forEach(::println)
        assertTrue(failures.isEmpty(), "${failures.size} items failed to round trip")
    }

    @Test
    fun `Modified item decodes back to the same values`() {
        val id = (0 until archive.size()).first { cache.data(Js5.JS5_CONFIG_ITEMS.id, archive.group(it), archive.file(it)) != null }
        val type = archive.decode(id)!!
        type.name = "Edited"
        type.cost = 1337
        type.members = true
        type.options[2] = "Pet"
        type.floorOptions[1] = "Grab"
        type.params[1234] = "value"

        val decoded = archive.decode(id, ItemTypeEncoder.encode(type))
        assertEquals("Edited", decoded.name)
        assertEquals(1337, decoded.cost)
        assertEquals(true, decoded.members)
        assertEquals("Pet", decoded.options[2])
        assertEquals("Grab", decoded.floorOptions[1])
        assertEquals("value", decoded.params[1234])
        assertContentEquals(type.options, decoded.options)
        assertContentEquals(type.floorOptions, decoded.floorOptions)
        assertContentEquals(type.raw, decoded.raw)
    }

    private fun checkFullyRead(id: Int, data: ByteArray) {
        val buf = Unpooled.wrappedBuffer(data)
        val type = archive.create(id)
        while (true) {
            val code = buf.g1()
            if (code == 0) {
                break
            }
            archive.readOpcode(type, buf, code)
        }
        check(!buf.isReadable) { "${buf.readableBytes()} trailing bytes" }
    }

    private fun difference(expected: ItemType, actual: ItemType): String? {
        for (field in ItemType::class.java.declaredFields) {
            field.isAccessible = true
            val a = field.get(expected)
            val b = field.get(actual)
            if (!Objects.deepEquals(a, b)) {
                return "${field.name} expected ${a.string()} but was ${b.string()}"
            }
        }
        return null
    }

    private fun Any?.string(): String = when (this) {
        is IntArray -> contentToString()
        is Array<*> -> contentDeepToString()
        else -> toString()
    }
}
