package editor.font

import cache.Js5
import cache.decode.FontJs5Archive
import cache.encode.FontTypeEncoder
import cache.types.FontType
import com.displee.cache.CacheLibrary

data class FontSummary(val id: Int, val name: String)

fun List<FontSummary>.search(query: String): List<FontSummary> {
    val query = query.trim()
    return when {
        query.isEmpty() -> this
        query.all { it.isDigit() } -> filter { it.id.toString().startsWith(query) || it.name.contains(query, ignoreCase = true) }
        else -> filter { it.name.contains(query, ignoreCase = true) }
    }
}

/**
 * Mirrors [NpcRepository]: the glue between the cache library and the UI.
 * Because the font wire format is still a stub, decoding a real font file errors and
 * [summaries] degrades to an empty list rather than crashing.
 */
class FontRepository(private val cache: CacheLibrary) {
    private val archive = FontJs5Archive(cache)

    fun summaries(): List<FontSummary> {
        return (0 until archive.size()).mapNotNull { id ->
            decodeOrNull("font", id) { archive.decode(id) }?.let { FontSummary(it.id, it.name) }
        }
    }

    private fun <T> decodeOrNull(kind: String, id: Int, decode: () -> T?): T? = try {
        decode()
    } catch (e: Exception) {
        System.err.println("Failed to decode $kind $id: ${e.message}")
        null
    }

    fun load(id: Int): FontType? = archive.decode(id)

    fun exists(id: Int): Boolean = cache.data(Js5.JS5_JAGEX_FONTS.id, archive.group(id), archive.file(id)) != null

    fun nextFreeId(): Int = archive.size()

    fun copy(type: FontType, id: Int = type.id): FontType = archive.decode(id, FontTypeEncoder.encode(type))

    fun save(type: FontType): Int {
        val data = FontTypeEncoder.encode(type)
        cache.put(Js5.JS5_JAGEX_FONTS.id, archive.group(type.id), archive.file(type.id), data)
        check(cache.index(Js5.JS5_JAGEX_FONTS.id)?.update() == true) { "Failed to write font index" }
        return data.size
    }
}
