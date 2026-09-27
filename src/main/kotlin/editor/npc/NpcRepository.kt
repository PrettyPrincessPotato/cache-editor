package editor.npc

import cache.Js5
import cache.decode.NPCJs5Archive
import cache.decode.ParamJs5Archive
import cache.encode.NpcTypeEncoder
import cache.types.NpcType
import cache.types.ParamType
import com.displee.cache.CacheLibrary
import editor.ParamNames

data class NpcSummary(val id: Int, val name: String, val combatLevel: Int)

fun List<NpcSummary>.search(query: String): List<NpcSummary> {
    val query = query.trim()
    return when {
        query.isEmpty() -> this
        query.all { it.isDigit() } -> filter { it.id.toString().startsWith(query) || it.name.contains(query, ignoreCase = true) }
        else -> filter { it.name.contains(query, ignoreCase = true) }
    }
}

data class ParamInfo(val id: Int, val name: String?, val type: ParamType?, val npcCount: Int) {
    val label: String
        get() = name ?: "param $id"
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

class NpcCatalog(val npcs: List<NpcSummary>, val params: List<ParamInfo>)

class NpcRepository(private val cache: CacheLibrary) {
    private val archive = NPCJs5Archive(cache)

    fun summaries(): List<NpcSummary> = catalog().npcs

    fun catalog(): NpcCatalog {
        val usage = mutableMapOf<Int, Int>()
        val npcs = (0 until archive.size()).mapNotNull { id ->
            val type = decodeOrNull("npc", id) { archive.decode(id) } ?: return@mapNotNull null
            for (key in type.params.keys) {
                usage.merge(key, 1, Int::plus)
            }
            NpcSummary(id, type.name, type.combatLevel)
        }
        val paramArchive = ParamJs5Archive(cache)
        val types = (0 until paramArchive.size()).mapNotNull { id -> decodeOrNull("param", id) { paramArchive.decode(id) } }.associateBy { it.id }
        val params = (types.keys + usage.keys)
            .map { id -> ParamInfo(id, ParamNames.name(id), types[id], usage[id] ?: 0) }
            .sortedWith(compareByDescending<ParamInfo> { it.npcCount }.thenBy { it.id })
        return NpcCatalog(npcs, params)
    }

    private fun <T> decodeOrNull(kind: String, id: Int, decode: () -> T?): T? = try {
        decode()
    } catch (e: Exception) {
        System.err.println("Failed to decode $kind $id: ${e.message}")
        null
    }

    fun load(id: Int): NpcType? = archive.decode(id)

    fun exists(id: Int): Boolean = cache.data(Js5.NPC, archive.group(id), archive.file(id)) != null

    fun nextFreeId(): Int = archive.size()

    fun copy(type: NpcType, id: Int = type.id): NpcType = archive.decode(id, NpcTypeEncoder.encode(type))

    fun save(type: NpcType): Int {
        val data = NpcTypeEncoder.encode(type)
        cache.put(Js5.NPC, archive.group(type.id), archive.file(type.id), data)
        check(cache.index(Js5.NPC)?.update() == true) { "Failed to write npc index" }
        return data.size
    }
}
