package editor.item

import cache.Js5
import cache.decode.ItemJs5Archive
import cache.decode.ParamJs5Archive
import cache.encode.ItemTypeEncoder
import cache.types.ItemType
import com.displee.cache.CacheLibrary
import editor.ParamNames
import editor.npc.ParamInfo

data class ItemSummary(val id: Int, val name: String, val members: Boolean, val cost: Int, val displayName: String)

// Noted and lent items carry their real name as a runtime derivation (the client
// derives it from a referenced item). We show that derived name with a " noted"/" lent"
// suffix so the editor can tell them apart from the source item.
fun ItemType.displayName(all: Map<Int, ItemType>): String? = when {
    notedTemplateId != -1 -> (all[noteId]?.name ?: name) + " noted"
    lendTemplateId != -1 -> (all[lendId]?.name ?: name) + " lent"
    else -> null
}

fun List<ItemSummary>.search(query: String): List<ItemSummary> {
    val query = query.trim()
    return when {
        query.isEmpty() -> this
        query.all { it.isDigit() } -> filter { it.id.toString().startsWith(query) || it.displayName.contains(query, ignoreCase = true) }
        else -> filter { it.displayName.contains(query, ignoreCase = true) }
    }
}

@JvmName("searchParams")
fun List<ParamInfo>.search(query: String): List<ParamInfo> {
    val query = query.trim()
    return when {
        query.isEmpty() -> this
        query.all { it.isDigit() } -> filter { it.id.toString().startsWith(query) || it.name?.contains(query) == true }
        else -> filter { it.label.contains(query, ignoreCase = true) }
    }
}

class ItemCatalog(val items: List<ItemSummary>, val params: List<ParamInfo>)

class ItemRepository(private val cache: CacheLibrary) {
    private val archive = ItemJs5Archive(cache)
    private var allItems: Map<Int, ItemType> = emptyMap()

    fun summaries(): List<ItemSummary> = catalog().items

    fun displayName(type: ItemType): String? {
        if (allItems.isEmpty()) {
            allItems = loadAllItems()
        }
        return type.displayName(allItems)
    }

    fun catalog(): ItemCatalog {
        allItems = loadAllItems()
        val items = allItems.values.toList()
        val usage = mutableMapOf<Int, Int>()
        for (type in items) {
            for (key in type.params.keys) {
                usage.merge(key, 1, Int::plus)
            }
        }
        val summaries = items.map { ItemSummary(it.id, it.name, it.members, it.cost, it.displayName(allItems) ?: it.name) }
        val paramArchive = ParamJs5Archive(cache)
        val paramTypes = (0 until paramArchive.size()).mapNotNull { id -> decodeOrNull("param", id) { paramArchive.decode(id) } }.associateBy { it.id }
        val params = (paramTypes.keys + usage.keys)
            .map { id -> ParamInfo(id, ParamNames.name(id), paramTypes[id], usage[id] ?: 0) }
            .sortedWith(compareByDescending<ParamInfo> { it.npcCount }.thenBy { it.id })
        return ItemCatalog(summaries, params)
    }

    private fun loadAllItems(): Map<Int, ItemType> =
        (0 until archive.size()).mapNotNull { id -> decodeOrNull("item", id) { archive.decode(id) } }.associateBy { it.id }

    private fun <T> decodeOrNull(kind: String, id: Int, decode: () -> T?): T? = try {
        decode()
    } catch (e: Exception) {
        System.err.println("Failed to decode $kind $id: ${e.message}")
        null
    }

    fun load(id: Int): ItemType? = archive.decode(id)

    fun exists(id: Int): Boolean = cache.data(Js5.ITEMS, archive.group(id), archive.file(id)) != null

    fun nextFreeId(): Int = archive.size()

    fun copy(type: ItemType, id: Int = type.id): ItemType = archive.decode(id, ItemTypeEncoder.encode(type))

    fun save(type: ItemType): Int {
        val data = ItemTypeEncoder.encode(type)
        cache.put(Js5.ITEMS, archive.group(type.id), archive.file(type.id), data)
        check(cache.index(Js5.ITEMS)?.update() == true) { "Failed to write item index" }
        return data.size
    }
}
