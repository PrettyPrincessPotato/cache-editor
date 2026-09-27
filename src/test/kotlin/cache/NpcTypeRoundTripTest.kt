package cache

import cache.decode.NPCJs5Archive
import cache.encode.NpcTypeEncoder
import cache.types.NpcType
import com.displee.cache.CacheLibrary
import io.netty.buffer.Unpooled
import util.g1
import java.util.Objects
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcTypeRoundTripTest {

    private val cache = CacheLibrary.create(TestCache.directory.absolutePath)
    private val archive = NPCJs5Archive(cache)

    @Test
    fun `Every npc in the cache re-encodes to the same values`() {
        val size = archive.size()
        assertTrue(size > 0)

        val failures = mutableListOf<String>()
        var count = 0
        var identical = 0
        for (id in 0 until size) {
            val data = cache.data(Js5.JS5_CONFIG_NPC.id, archive.group(id), archive.file(id)) ?: continue
            count++
            try {
                checkFullyRead(id, data)
                val encoded = NpcTypeEncoder.encode(archive.decode(id, data))
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
        println("Checked $count npcs out of $size ids, $identical byte identical, ${failures.size} failures")
        failures.take(25).forEach(::println)
        assertTrue(failures.isEmpty(), "${failures.size} npcs failed to round trip")
    }

    @Test
    fun `Modified npc decodes back to the same values`() {
        val type = archive.decode(1)!!
        type.name = "Edited"
        type.combatLevel = 1337
        type.membersOps[2] = "Pet"
        type.params[1234] = "value"

        val decoded = archive.decode(1, NpcTypeEncoder.encode(type))
        assertEquals("Edited", decoded.name)
        assertEquals(1337, decoded.combatLevel)
        assertEquals("Pet", decoded.membersOps[2])
        assertEquals("value", decoded.params[1234])
        assertContentEquals(type.models, decoded.models)
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

    private fun difference(expected: NpcType, actual: NpcType): String? {
        for (field in NpcType::class.java.declaredFields) {
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
