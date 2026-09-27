package cache

import java.io.File
import kotlin.io.path.createTempDirectory

object TestCache {
    val directory: File
        get() {
            val directory = File(System.getProperty("cache.path") ?: "../2011Scape-2/cache")
            check(File(directory, "main_file_cache.dat2").exists()) { "No cache at ${directory.absolutePath}, run tests with -PcachePath=<cache directory>" }
            return directory
        }

    fun copy(): File {
        val copy = createTempDirectory("cache-editor").toFile()
        directory.copyRecursively(copy)
        return copy
    }
}
