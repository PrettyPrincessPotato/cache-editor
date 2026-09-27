package editor

import com.displee.cache.CacheLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheSession(val path: File) : AutoCloseable {
    val library: CacheLibrary = CacheLibrary.create(path.absolutePath)

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun <T> access(block: () -> T): T = withContext(dispatcher) { block() }

    override fun close() {
        library.close()
    }
}
