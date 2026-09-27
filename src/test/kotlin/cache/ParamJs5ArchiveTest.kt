package cache

import cache.decode.ParamJs5Archive
import com.displee.cache.CacheLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParamJs5ArchiveTest {

    @Test
    fun `Every param in the cache decodes`() {
        val cache = CacheLibrary.create(TestCache.directory.absolutePath)
        val archive = ParamJs5Archive(cache)
        val params = archive.decode()
        assertTrue(params.isNotEmpty())
        assertEquals(archive.size(), params.size)
        assertTrue(params.single { it.id == 1139 }.isString)
        cache.close()
    }
}
