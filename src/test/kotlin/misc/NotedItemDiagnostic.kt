package misc

import cache.TestCache
import cache.decode.ItemJs5Archive
import com.displee.cache.CacheLibrary
import kotlin.test.Test

class NotedItemDiagnostic {
    @Test
    fun dump() {
        val cache = CacheLibrary.create(TestCache.directory.absolutePath)
        val archive = ItemJs5Archive(cache)
        val items = (0 until archive.size()).mapNotNull { id -> archive.decode(id) }
        val noted = items.filter { it.notedTemplateId != -1 }
        val lent = items.filter { it.lendTemplateId != -1 }
        println("total items=${items.size} noted=${noted.size} lent=${lent.size}")
        noted.take(8).forEach { item ->
            val noteItem = items.getOrNull(item.noteId)
            val template = items.getOrNull(item.notedTemplateId)
            println("NOTED ${item.id}: wire name='${item.name}' cost=${item.cost} members=${item.members} scale=${item.spriteScale} noteId=${item.noteId} templateId=${item.notedTemplateId}")
            if (noteItem != null) println("   noteId ${noteItem.id}: name='${noteItem.name}' cost=${noteItem.cost} members=${noteItem.members}")
            if (template != null) println("   template ${template.id}: name='${template.name}' scale=${template.spriteScale}")
        }
        lent.take(8).forEach { item ->
            val lendItem = items.getOrNull(item.lendId)
            val template = items.getOrNull(item.lendTemplateId)
            println("LENT ${item.id}: wire name='${item.name}' cost=${item.cost} members=${item.members} scale=${item.spriteScale} lendId=${item.lendId} templateId=${item.lendTemplateId}")
            if (lendItem != null) println("   lendId ${lendItem.id}: name='${lendItem.name}' cost=${lendItem.cost} members=${lendItem.members}")
            if (template != null) println("   template ${template.id}: name='${template.name}' scale=${template.spriteScale}")
        }
    }
}
