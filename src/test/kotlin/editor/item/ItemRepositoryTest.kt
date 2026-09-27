package editor.item

import cache.TestCache
import cache.encode.ItemTypeEncoder
import cache.types.ItemType
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

class ItemRepositoryTest {

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
    fun `Unedited form rebuilds every item identically`() {
        repository { repository ->
            for (summary in repository.summaries()) {
                val type = repository.load(summary.id)!!
                val form = ItemForm(type, isNew = false, derived = repository.derivedValues(type))
                assertFalse(form.isDirty, "Item ${summary.id} dirty without edits")
                val built = assertNotNull(
                    form.build(repository.copy(type)),
                    "Item ${summary.id} invalid: ${form.errors.entries.joinToString { "${it.key.label}=${it.value}" }}"
                )
                assertContentEquals(ItemTypeEncoder.encode(type), ItemTypeEncoder.encode(built), "Item ${summary.id}")
            }
        }
    }

    @Test
    fun `Edit item and save to cache`() {
        val normalId = repository { repository ->
            repository.summaries().first { summary ->
                val t = repository.load(summary.id)!!
                t.notedTemplateId == -1 && t.lendTemplateId == -1
            }.id
        }
        repository { repository ->
            val form = ItemForm(repository.load(normalId)!!, isNew = false)
            form.texts[field("Name")] = "Edited item"
            form.texts[field("Sprite scale")] = "1500"
            form.texts[field("Cost")] = "500"
            form.texts[field("Params")] = "123 = \"hello\"\n456 = 7"
            assertTrue(form.isDirty)
            repository.save(form.build(repository.copy(form.base))!!)
        }
        repository { repository ->
            val type = repository.load(normalId)!!
            assertEquals("Edited item", type.name)
            assertEquals(1500, type.spriteScale)
            assertEquals(500, type.cost)
            assertEquals(mapOf(123 to "hello", 456 to 7), type.params.toMap())
        }
    }

    @Test
    fun `Noted item shows derived values but keeps wire values on save`() {
        val notedId = repository { repository ->
            repository.summaries().first { repository.load(it.id)!!.notedTemplateId != -1 }.id
        }
        repository { repository ->
            val type = repository.load(notedId)!!
            val source = repository.load(type.noteId)!!
            val template = repository.load(type.notedTemplateId)!!
            val nameField = field("Name")
            val costField = field("Cost")
            val scaleField = field("Sprite scale")
            val membersField = flag("Members")
            val form = ItemForm(type, isNew = false, derived = repository.derivedValues(type))

            assertEquals(source.name + " noted", form.texts[nameField])
            assertEquals(source.cost.toString(), form.texts[costField])
            assertEquals(template.spriteScale.toString(), form.texts[scaleField])
            assertEquals(source.members, form.flags[membersField])
            assertTrue(form.isReadOnly(nameField))
            assertTrue(form.isReadOnly(costField))
            assertTrue(form.isReadOnly(scaleField))
            assertTrue(form.isReadOnly(membersField))
            assertFalse(form.isDirty)

            form.texts[nameField] = "Tweaked"
            form.texts[costField] = "1"
            form.texts[scaleField] = "1"
            form.flags[membersField] = !source.members
            assertFalse(form.isDirty, "editing read-only derived fields must not mark dirty")

            val built = form.build(repository.copy(type))!!
            assertEquals(type.name, built.name, "wire name must be preserved for noted items")
            assertEquals(type.cost, built.cost, "wire cost must be preserved")
            assertEquals(type.members, built.members, "wire members must be preserved")
            assertEquals(type.spriteScale, built.spriteScale, "wire sprite scale must be preserved")
            assertContentEquals(ItemTypeEncoder.encode(type), ItemTypeEncoder.encode(built))
        }
    }

    @Test
    fun `Lent item shows derived values but keeps wire values on save`() {
        val lentId = repository { repository ->
            repository.summaries().first { repository.load(it.id)!!.lendTemplateId != -1 }.id
        }
        repository { repository ->
            val type = repository.load(lentId)!!
            val source = repository.load(type.lendId)!!
            val template = repository.load(type.lendTemplateId)!!
            val nameField = field("Name")
            val costField = field("Cost")
            val scaleField = field("Sprite scale")
            val membersField = flag("Members")
            val form = ItemForm(type, isNew = false, derived = repository.derivedValues(type))

            assertEquals(source.name + " lent", form.texts[nameField])
            assertEquals("0", form.texts[costField])
            assertEquals(template.spriteScale.toString(), form.texts[scaleField])
            assertEquals(source.members, form.flags[membersField])
            assertTrue(form.isReadOnly(nameField))
            assertTrue(form.isReadOnly(costField))
            assertTrue(form.isReadOnly(scaleField))
            assertTrue(form.isReadOnly(membersField))
            assertFalse(form.isDirty)

            val built = form.build(repository.copy(type))!!
            assertEquals(type.name, built.name, "wire name must be preserved for lent items")
            assertEquals(type.cost, built.cost, "wire cost must be preserved")
            assertEquals(type.members, built.members, "wire members must be preserved")
            assertEquals(type.spriteScale, built.spriteScale, "wire sprite scale must be preserved")
            assertContentEquals(ItemTypeEncoder.encode(type), ItemTypeEncoder.encode(built))
        }
    }

    @Test
    fun `Invalid input is rejected`() {
        repository { repository ->
            val form = ItemForm(repository.load(0)!!, isNew = false)
            form.texts[field("Sprite scale")] = "abc"
            form.texts[field("Cost")] = "1e9"
            assertNull(form.build(repository.copy(form.base)))
            assertEquals(setOf("Sprite scale", "Cost"), form.errors.keys.map { it.label }.toSet())
        }
    }

    @Test
    fun `Create new items`() {
        val (first, count) = repository { it.nextFreeId() to it.summaries().size }
        val ids = (first until first + 4).toList()
        repository { repository ->
            for (id in ids) {
                assertFalse(repository.exists(id))
                val form = ItemForm(ItemType(id).apply { name = "Clone $id" }, isNew = true)
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
    fun `Params show and parse names`() {
        val params = ITEM_SECTIONS.flatMap { it.fields }.filterIsInstance<ItemField.Params>().single()
        val paramItemId = repository { repository ->
            repository.summaries().first { repository.load(it.id)!!.params.containsKey(14) }.id
        }
        repository { repository ->
            val form = ItemForm(repository.load(paramItemId)!!, isNew = false)
            assertTrue(form.texts.getValue(params).lines().any { it.startsWith("attack_speed = ") })

            form.texts[params] = "attack_speed = 5\n1139 = \"Scout\"\n999999 = 1"
            repository.save(form.build(repository.copy(form.base))!!)
            assertEquals(mapOf(14 to 5, 1139 to "Scout", 999999 to 1), repository.load(paramItemId)!!.params.toMap())
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
        assertEquals(
            catalog.params.sortedByDescending { it.npcCount }.map { it.npcCount },
            catalog.params.map { it.npcCount }
        )
    }

    private fun <T> repository(block: (ItemRepository) -> T): T {
        val cache = CacheLibrary.create(directory.absolutePath)
        try {
            return block(ItemRepository(cache))
        } finally {
            cache.close()
        }
    }

    private fun field(label: String) = ITEM_SECTIONS.flatMap { it.fields }.filterIsInstance<ItemField.Text>().first { it.label == label }

    private fun flag(label: String) = ITEM_SECTIONS.flatMap { it.fields }.filterIsInstance<ItemField.Flag>().first { it.label == label }
}
