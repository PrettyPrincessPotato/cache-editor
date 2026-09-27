package editor.font

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cache.types.FontType
import editor.CacheEditorState
import editor.CacheSession
import editor.EditorTab

/**
 * Mirrors [NpcTab]: the UI controller for the font tab. It owns the font list, the
 * editable form, and the create/save actions, exactly as the NPC tab does.
 */
class FontTab(private val editor: CacheEditorState) : EditorTab {
    override val title = "Fonts (stub)"

    override val hasUnsavedChanges: Boolean
        get() = form?.isDirty == true

    private var session: CacheSession? = null
    var repository: FontRepository? by mutableStateOf(null)
        private set
    var fonts by mutableStateOf(emptyList<FontSummary>())
        private set
    var query by mutableStateOf("")
    var form: FontForm? by mutableStateOf(null)
        private set

    var pendingSelection: Int? by mutableStateOf(null)
    var showCreateDialog by mutableStateOf(false)

    val filtered by derivedStateOf { fonts.search(query) }

    override suspend fun open(session: CacheSession) {
        val repository = FontRepository(session.library)
        val list = session.access { repository.summaries() }
        this.session = session
        this.repository = repository
        fonts = list
        form = null
        pendingSelection = null
        showCreateDialog = false
    }

    @Composable
    override fun Content() = FontTabContent(this)

    fun select(id: Int) {
        if (form?.isDirty == true && form?.id != id) {
            pendingSelection = id
        } else {
            load(id)
        }
    }

    fun load(id: Int) = editor.launch("Failed to load font $id") {
        pendingSelection = null
        val type = access { it.load(id) } ?: throw IllegalStateException("Font $id doesn't exist")
        form = FontForm(type, isNew = false)
    }

    fun revert() {
        val form = form ?: return
        this.form = FontForm(form.base, form.isNew)
    }

    fun save() = editor.launch("Failed to save") {
        val form = form ?: return@launch
        val type = form.build(access { it.copy(form.base) })
        if (type == null) {
            editor.message("Fix the ${form.errors.size} highlighted field(s) before saving")
            return@launch
        }
        val (size, saved) = access { it.save(type) to it.load(type.id)!! }
        this@FontTab.form = FontForm(saved, isNew = false)
        val summary = FontSummary(saved.id, saved.name)
        fonts = (fonts.filter { it.id != saved.id } + summary).sortedBy { it.id }
        editor.message("Saved font ${saved.id} '${saved.name}' ($size bytes)")
    }

    suspend fun nextFreeId(): Int = access { it.nextFreeId() }

    suspend fun exists(id: Int): Boolean = access { it.exists(id) }

    fun create(id: Int, copy: Boolean) = editor.launch("Failed to create font $id") {
        val source = form?.base
        val type = if (copy && source != null) {
            access { it.copy(source, id) }
        } else {
            FontType(id).apply { name = "New font" }
        }
        showCreateDialog = false
        pendingSelection = null
        form = FontForm(type, isNew = true)
    }

    private suspend fun <T> access(block: (FontRepository) -> T): T {
        val session = session ?: throw IllegalStateException("No cache open")
        val repository = repository!!
        return session.access { block(repository) }
    }
}
