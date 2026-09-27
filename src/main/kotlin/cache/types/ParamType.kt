package cache.types

import cache.Type

class ParamType(override val id: Int) : Type(id) {
    var type: Char? = null
    var defaultInt: Int = 0
    var defaultString: String? = null
    var autoDisable: Boolean = true

    val isString: Boolean
        get() = type == 's'
}
