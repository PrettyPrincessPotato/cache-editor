package editor.npc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cache.types.NpcType
import editor.CacheEditorState
import editor.CacheSession
import editor.EditorTab

class NpcTab(private val editor: CacheEditorState) : EditorTab {
    override val title = "NPCs"

    override val hasUnsavedChanges: Boolean
        get() = form?.isDirty == true

    private var session: CacheSession? = null
    var repository: NpcRepository? by mutableStateOf(null)
        private set
    var npcs by mutableStateOf(emptyList<NpcSummary>())
        private set
    var params by mutableStateOf(emptyList<ParamInfo>())
        private set
    var query by mutableStateOf("")
    var form: NpcForm? by mutableStateOf(null)
        private set

    var pendingSelection: Int? by mutableStateOf(null)
    var showCreateDialog by mutableStateOf(false)

    val filtered by derivedStateOf { npcs.search(query) }

    override suspend fun open(session: CacheSession) {
        val repository = NpcRepository(session.library)
        val catalog = session.access { repository.catalog() }
        this.session = session
        this.repository = repository
        npcs = catalog.npcs
        params = catalog.params
        form = null
        pendingSelection = null
        showCreateDialog = false
    }

    @Composable
    override fun Content() = NpcTabContent(this)

    fun select(id: Int) {
        if (form?.isDirty == true && form?.id != id) {
            pendingSelection = id
        } else {
            load(id)
        }
    }

    fun load(id: Int) = editor.launch("Failed to load npc $id") {
        pendingSelection = null
        val type = access { it.load(id) } ?: throw IllegalStateException("Npc $id doesn't exist")
        form = NpcForm(type, isNew = false)
    }

    fun revert() {
        val form = form ?: return
        this.form = NpcForm(form.base, form.isNew)
    }

    fun save() = editor.launch("Failed to save") {
        val form = form ?: return@launch
        val type = form.build(access { it.copy(form.base) })
        if (type == null) {
            editor.message("Fix the ${form.errors.size} highlighted field(s) before saving")
            return@launch
        }
        val (size, saved) = access { it.save(type) to it.load(type.id)!! }
        this@NpcTab.form = NpcForm(saved, isNew = false)
        val summary = NpcSummary(saved.id, saved.name, saved.combatLevel)
        npcs = (npcs.filter { it.id != saved.id } + summary).sortedBy { it.id }
        editor.message("Saved npc ${saved.id} '${saved.name}' ($size bytes)")
    }

    fun addParam(field: NpcField.Params, param: ParamInfo) {
        val form = form ?: return
        val text = form.texts.getValue(field)
        if (param.id in paramKeys(text)) {
            editor.message("${param.label} is already set")
            return
        }
        val type = param.type
        val value: Any = if (type != null && type.isString) type.defaultString ?: "" else type?.defaultInt ?: 0
        val lines = text.trimEnd()
        form.texts[field] = (if (lines.isEmpty()) "" else "$lines\n") + paramLine(param.id, value)
        form.errors.remove(field)
    }

    suspend fun nextFreeId(): Int = access { it.nextFreeId() }

    suspend fun exists(id: Int): Boolean = access { it.exists(id) }

    fun create(id: Int, copy: Boolean) = editor.launch("Failed to create npc $id") {
        val source = form?.base
        val type = if (copy && source != null) {
            access { it.copy(source, id) }
        } else {
            NpcType(id).apply { name = "New npc" }
        }
        showCreateDialog = false
        pendingSelection = null
        form = NpcForm(type, isNew = true)
    }

    private suspend fun <T> access(block: (NpcRepository) -> T): T {
        val session = session ?: throw IllegalStateException("No cache open")
        val repository = repository!!
        return session.access { block(repository) }
    }
}
