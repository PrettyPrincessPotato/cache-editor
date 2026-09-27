package editor.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cache.types.ItemType
import editor.CacheEditorState
import editor.CacheSession
import editor.EditorTab
import editor.npc.ParamInfo
import editor.npc.paramKeys
import editor.npc.paramLine

class ItemTab(private val editor: CacheEditorState) : EditorTab {
    override val title = "Items"

    override val hasUnsavedChanges: Boolean
        get() = form?.isDirty == true

    private var session: CacheSession? = null
    var repository: ItemRepository? by mutableStateOf(null)
        private set
    var items by mutableStateOf(emptyList<ItemSummary>())
        private set
    var params by mutableStateOf(emptyList<ParamInfo>())
        private set
    var query by mutableStateOf("")
    var form: ItemForm? by mutableStateOf(null)
        private set

    var pendingSelection: Int? by mutableStateOf(null)
    var showCreateDialog by mutableStateOf(false)

    val filtered by derivedStateOf { items.search(query) }

    override suspend fun open(session: CacheSession) {
        val repository = ItemRepository(session.library)
        val catalog = session.access { repository.catalog() }
        this.session = session
        this.repository = repository
        items = catalog.items
        params = catalog.params
        form = null
        pendingSelection = null
        showCreateDialog = false
    }

    @Composable
    override fun Content() = ItemTabContent(this)

    fun select(id: Int) {
        if (form?.isDirty == true && form?.id != id) {
            pendingSelection = id
        } else {
            load(id)
        }
    }

    fun load(id: Int) = editor.launch("Failed to load item $id") {
        pendingSelection = null
        val type = access { it.load(id) } ?: throw IllegalStateException("Item $id doesn't exist")
        form = ItemForm(type, isNew = false)
    }

    fun revert() {
        val form = form ?: return
        this.form = ItemForm(form.base, form.isNew)
    }

    fun save() = editor.launch("Failed to save") {
        val form = form ?: return@launch
        val type = form.build(access { it.copy(form.base) })
        if (type == null) {
            editor.message("Fix the ${form.errors.size} highlighted field(s) before saving")
            return@launch
        }
        val (size, saved) = access { it.save(type) to it.load(type.id)!! }
        this@ItemTab.form = ItemForm(saved, isNew = false)
        val summary = ItemSummary(saved.id, saved.name, saved.members, saved.cost)
        items = (items.filter { it.id != saved.id } + summary).sortedBy { it.id }
        editor.message("Saved item ${saved.id} '${saved.name}' ($size bytes)")
    }

    fun addParam(field: ItemField.Params, param: ParamInfo) {
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

    fun create(id: Int, copy: Boolean) = editor.launch("Failed to create item $id") {
        val source = form?.base
        val type = if (copy && source != null) {
            access { it.copy(source, id) }
        } else {
            ItemType(id).apply { name = "New item" }
        }
        showCreateDialog = false
        pendingSelection = null
        form = ItemForm(type, isNew = true)
    }

    private suspend fun <T> access(block: (ItemRepository) -> T): T {
        val session = session ?: throw IllegalStateException("No cache open")
        val repository = repository!!
        return session.access { block(repository) }
    }
}
