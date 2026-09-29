package tools

import cache.Js5
import com.displee.cache.CacheLibrary

/**
 * Read-only inspector: dumps the font archive's index (to reveal the group/file mapping)
 * and hex-dumps a few font files so the real opcodes are visible. Run with:
 *   ./gradlew fontDump
 * (optionally pass a cache dir as the first arg).
 *
 * Scratch tool for reverse-engineering — safe to delete.
 */
fun main(args: Array<String>) {
    val path = args.firstOrNull() ?: "/home/princess/Projects/Void/data/cache"
    val library = CacheLibrary.create(path)
    try {
        dump(library, Js5.JS5_FONTMETRICS.id, "FONTMETRICS")
        dump(library, Js5.JS5_JAGEX_FONTS.id, "JAGEX_FONTS")
    } finally {
        library.close()
    }
}

private fun dump(library: CacheLibrary, archive: Int, name: String) {
    println()
    println("===== $name (archive $archive) =====")
    val index = library.index(archive)
    if (index == null) {
        println("  (no index for this archive)")
        return
    }
    val groups = index.archives()
    println("  ${groups.size} groups total")
    groups.take(4).forEach { g ->
        val files = g.files()
        println("  group ${g.id}: ${files.size} files (first ids ${files.take(6).joinToString(", ") { it.id.toString() }})")
    }

    val flat = groups.flatMap { g -> g.files().map { g.id to it.id } }
    flat.take(6).forEach { (g, f) ->
        val data = library.data(archive, g, f)
        if (data == null) {
            println("  g${g} f${f}: (no data)")
            return
        }
        val slice = data.take(64)
        println("  g${g} f${f}: ${data.size} bytes")
        println("    hex:   " + slice.joinToString(" ") { String.format("%02x", it.toInt()) })
        println("    ascii: " + slice.joinToString("") { if (it.toInt() in 32..126) it.toInt().toChar().toString() else "." })
    }
}
