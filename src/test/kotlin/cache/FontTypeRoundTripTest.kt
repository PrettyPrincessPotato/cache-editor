package cache

import cache.decode.FontJs5Archive
import cache.encode.FontTypeEncoder
import cache.types.FontType
import com.displee.cache.CacheLibrary
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scaffold round-trip test for the stub font fields. Unlike the NPC round-trip test, this
 * does NOT run against the real cache (the font wire format isn't known yet) — it proves
 * the stub encoder and decoder are inverses for the placeholder fields.
 *
 * TODO(font): once the real opcodes are filled in, replace this with a full-cache test that
 * re-encodes every font in the archive and asserts byte-identity (see NpcTypeRoundTripTest).
 */
class FontTypeRoundTripTest {

    private val cache = CacheLibrary.create(TestCache.directory.absolutePath)
    private val archive = FontJs5Archive(cache)

    @Test
    fun `Stub font fields round trip`() {
        val font = FontType(1).apply {
            name = "Edited"
            placeholder = 1337
            placeholderFlag = true
            params[9] = 5
            params[10] = "value"
        }

        val decoded = archive.decode(1, FontTypeEncoder.encode(font))
        assertEquals("Edited", decoded.name)
        assertEquals(1337, decoded.placeholder)
        assertEquals(true, decoded.placeholderFlag)
        assertEquals(5, decoded.params[9])
        assertEquals("value", decoded.params[10])
    }
}
