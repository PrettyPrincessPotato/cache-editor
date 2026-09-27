package editor.item

import cache.Js5
import cache.decode.ItemJs5Archive
import cache.decode.ParamJs5Archive
import cache.encode.ItemTypeEncoder
import cache.types.ItemType
import com.displee.cache.CacheLibrary
import editor.ParamNames
import editor.npc.ParamInfo

data class ItemSummary(val id: Int, val name: String, val members: Boolean, val cost: Int)

fun List<ItemSummary>.search(query: String): List<ItemSummary> {
    val query = query.trim()
    return when {
        query.isEmpty() -> this
        query.all { it.isDigit() } -> filter { it.id.toString().startsWith(query) || it.name.contains(query, ignoreCase = true) }
        else -> filter { it.name.contains(query, ignoreCase = true) }
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

    fun summaries(): List<ItemSummary> = catalog().items

    fun catalog(): ItemCatalog {
        val usage = mutableMapOf<Int, Int>()
        val items = (0 until archive.size()).mapNotNull { id ->
            val type = decodeOrNull("item", id) { archive.decode(id) } ?: return@mapNotNull null
            for (key in type.params.keys) {
                usage.merge(key, 1, Int::plus)
            }
            ItemSummary(id, type.name, type.members, type.cost)
        }
        val paramArchive = ParamJs5Archive(cache)
        val types = (0 until paramArchive.size()).mapNotNull { id -> decodeOrNull("param", id) { paramArchive.decode(id) } }.associateBy { it.id }
        val params = (types.keys + usage.keys)
            .map { id -> ParamInfo(id, ParamNames.name(id), types[id], usage[id] ?: 0) }
            .sortedWith(compareByDescending<ParamInfo> { it.npcCount }.thenBy { it.id })
        return ItemCatalog(items, params)
    }

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
