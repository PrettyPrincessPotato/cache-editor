package editor.npc

import cache.TestCache
import cache.encode.NpcTypeEncoder
import com.displee.cache.CacheLibrary
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NpcRepositoryTest {

    private lateinit var directory: File

    @BeforeTest
    fun setup() {
        directory = TestCache.copy()
    }

    @AfterTest
    fun teardown() {
        directory.deleteRecursively()
    }

    @Test
    fun `Unedited form rebuilds every npc identically`() {
        repository { repository ->
            for (summary in repository.summaries()) {
                val type = repository.load(summary.id)!!
                val form = NpcForm(type, isNew = false)
                assertFalse(form.isDirty, "Npc ${summary.id} dirty without edits")
                val built = assertNotNull(form.build(repository.copy(type)), "Npc ${summary.id} invalid: ${form.errors.entries.joinToString { "${it.key.label}=${it.value}" }}")
                assertContentEquals(NpcTypeEncoder.encode(type), NpcTypeEncoder.encode(built), "Npc ${summary.id}")
            }
        }
    }

    @Test
    fun `Edit npc and save to cache`() {
        val before = repository { it.summaries() }
        repository { repository ->
            val form = NpcForm(repository.load(0)!!, isNew = false)
            form.texts[field("Name")] = "Edited Hans"
            form.texts[field("Combat level")] = "99"
            form.texts[field("Params")] = "123 = \"hello\"\n456 = 7"
            assertTrue(form.isDirty)
            repository.save(form.build(repository.copy(form.base))!!)
        }
        repository { repository ->
            val type = repository.load(0)!!
            assertEquals("Edited Hans", type.name)
            assertEquals(99, type.combatLevel)
            assertEquals(mapOf(123 to "hello", 456 to 7), type.params.toMap())
            assertEquals(before.drop(1), repository.summaries().drop(1))
        }
    }

    @Test
    fun `Invalid input is rejected`() {
        repository { repository ->
            val form = NpcForm(repository.load(0)!!, isNew = false)
            form.texts[field("Size")] = "300"
            form.texts[field("Diffusion")] = "7"
            form.texts[field("Models")] = "1, x"
            assertNull(form.build(repository.copy(form.base)))
            assertEquals(setOf("Size", "Diffusion", "Models"), form.errors.keys.map { it.label }.toSet())
        }
    }

    @Test
    fun `Create new npcs`() {
        val (first, count) = repository { it.nextFreeId() to it.summaries().size }
        val ids = (first..(first or 0x7f) + 2).toList()
        repository { repository ->
            for (id in ids) {
                assertFalse(repository.exists(id))
                val form = NpcForm(repository.copy(repository.load(0)!!, id), isNew = true)
                form.texts[field("Name")] = "Clone $id"
                repository.save(form.build(repository.copy(form.base))!!)
            }
        }
        repository { repository ->
            assertEquals(ids.last() + 1, repository.nextFreeId())
            assertEquals(count + ids.size, repository.summaries().size)
            for (id in ids) {
                assertEquals("Clone $id", repository.load(id)!!.name)
            }
        }
    }

    @Test
    fun `Edit transforms`() {
        val transforms = NPC_SECTIONS.flatMap { it.fields }.filterIsInstance<NpcField.Transforms>().single()
        repository { repository ->
            val form = NpcForm(repository.load(0)!!, isNew = false)
            assertEquals(NpcTransforms(emptyList(), -1), form.transforms[transforms])

            form.transforms[transforms] = NpcTransforms(emptyList(), 2)
            assertNull(form.build(repository.copy(form.base)))
            assertEquals(setOf("Transforms"), form.errors.keys.map { it.label }.toSet())

            form.texts[field("Varbit")] = "1234"
            form.transforms[transforms] = NpcTransforms(listOf(1, -1, 4), 2)
            repository.save(form.build(repository.copy(form.base))!!)
            val saved = repository.load(0)!!
            assertEquals(1234, saved.multiNpcVarbit)
            assertContentEquals(intArrayOf(1, -1, 4, 2), saved.multiNpcs)

            val cleared = NpcForm(saved, isNew = false)
            cleared.transforms[transforms] = NpcTransforms(emptyList(), -1)
            repository.save(cleared.build(repository.copy(cleared.base))!!)
            assertNull(repository.load(0)!!.multiNpcs)
        }
    }

    @Test
    fun `Params show and parse names`() {
        val params = NPC_SECTIONS.flatMap { it.fields }.filterIsInstance<NpcField.Params>().single()
        repository { repository ->
            val id = repository.summaries().first { repository.load(it.id)!!.params.containsKey(14) }.id
            val form = NpcForm(repository.load(id)!!, isNew = false)
            assertTrue(form.texts.getValue(params).lines().any { it.startsWith("attack_speed = ") })

            form.texts[params] = "attack_speed = 5\n1139 = \"Scout\"\n999999 = 1"
            repository.save(form.build(repository.copy(form.base))!!)
            assertEquals(mapOf(14 to 5, 1139 to "Scout", 999999 to 1), repository.load(id)!!.params.toMap())

            form.texts[params] = "not_a_param = 1"
            assertNull(form.build(repository.copy(form.base)))
            assertEquals("Unknown param 'not_a_param'", form.errors[params])
        }
    }

    @Test
    fun `Catalog lists named and typed params`() {
        val catalog = repository { it.catalog() }
        val byId = catalog.params.associateBy { it.id }
        val attackSpeed = byId.getValue(14)
        assertEquals("attack_speed", attackSpeed.name)
        assertTrue(attackSpeed.npcCount > 0)
        assertFalse(attackSpeed.type!!.isString)
        assertTrue(byId.getValue(1139).type!!.isString)
        assertEquals("shadow", byId.getValue(1912).name)
        assertNull(byId.getValue(1912).type)
        assertEquals(catalog.params.sortedByDescending { it.npcCount }.map { it.npcCount }, catalog.params.map { it.npcCount })
    }

    private fun <T> repository(block: (NpcRepository) -> T): T {
        val cache = CacheLibrary.create(directory.absolutePath)
        try {
            return block(NpcRepository(cache))
        } finally {
            cache.close()
        }
    }

    private fun field(label: String) = NPC_SECTIONS.flatMap { it.fields }.filterIsInstance<NpcField.Text>().first { it.label == label }
}
