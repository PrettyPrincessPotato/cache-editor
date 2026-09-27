package editor.npc

import cache.types.NpcType
import editor.ParamNames

sealed class NpcField(val label: String, val opcode: String, val unverified: Boolean) {
    open class Text(
        label: String,
        opcode: String,
        unverified: Boolean = false,
        val hint: String = "",
        val multiline: Boolean = false,
        val read: (NpcType) -> String,
        val write: (NpcType, String) -> Unit,
    ) : NpcField(label, opcode, unverified)

    class Flag(
        label: String,
        opcode: String,
        unverified: Boolean = false,
        val read: (NpcType) -> Boolean,
        val write: (NpcType, Boolean) -> Unit,
    ) : NpcField(label, opcode, unverified)

    class Params(
        label: String,
        opcode: String,
        hint: String,
        read: (NpcType) -> String,
        write: (NpcType, String) -> Unit,
    ) : Text(label, opcode, hint = hint, multiline = true, read = read, write = write)

    class Transforms(
        label: String,
        opcode: String,
        val read: (NpcType) -> NpcTransforms,
        val write: (NpcType, NpcTransforms) -> Unit,
    ) : NpcField(label, opcode, unverified = false)
}

data class NpcTransforms(val ids: List<Int>, val default: Int)

class NpcSection(val title: String, val fields: List<NpcField>)

private val UBYTE = 0..255
private val SBYTE = -128..127
private val USHORT = 0..65535
private val OPT_UBYTE = -1..255
private val OPT_USHORT = -1..65535

private fun parseInt(text: String, range: IntRange): Int {
    val value = text.trim().toIntOrNull() ?: throw IllegalArgumentException("Not a number")
    require(value in range) { "Must be between ${range.first} and ${range.last}" }
    return value
}

private fun parseInts(text: String, range: IntRange, max: Int = 255): IntArray? {
    if (text.isBlank()) {
        return null
    }
    val values = text.split(',').map { parseInt(it, range) }
    require(values.size <= max) { "At most $max values" }
    return values.toIntArray()
}

private fun int(label: String, opcode: Int, range: IntRange, unverified: Boolean = false, get: (NpcType) -> Int, set: (NpcType, Int) -> Unit) =
    NpcField.Text(label, opcode.toString(), unverified, hint = "${range.first}..${range.last}", read = { get(it).toString() }, write = { type, text -> set(type, parseInt(text, range)) })

private fun ints(label: String, opcode: Int, range: IntRange, unverified: Boolean = false, get: (NpcType) -> IntArray?, set: (NpcType, IntArray?) -> Unit) =
    NpcField.Text(label, opcode.toString(), unverified, hint = "Comma separated", read = { get(it)?.joinToString(", ") ?: "" }, write = { type, text -> set(type, parseInts(text, range)) })

private fun flag(label: String, opcode: Int, unverified: Boolean = false, get: (NpcType) -> Boolean, set: (NpcType, Boolean) -> Unit) =
    NpcField.Flag(label, opcode.toString(), unverified, get, set)

private fun op(label: String, opcode: Int, index: Int, ops: (NpcType) -> Array<String?>) =
    NpcField.Text(label, opcode.toString(), unverified = opcode == 151 || opcode == 154, hint = "Blank for none", read = { ops(it)[index] ?: "" }, write = { type, text -> ops(type)[index] = text.ifBlank { null } })

private fun pairs(label: String, opcode: Int, get: (NpcType) -> Pair<IntArray?, IntArray?>, set: (NpcType, IntArray?, IntArray?) -> Unit) = NpcField.Text(
    label,
    opcode.toString(),
    hint = "find=replace, comma separated",
    read = {
        val (source, dest) = get(it)
        if (source == null || dest == null) "" else source.indices.joinToString(", ") { i -> "${source[i]}=${dest[i]}" }
    },
    write = { type, text ->
        if (text.isBlank()) {
            set(type, null, null)
        } else {
            val entries = text.split(',').map { entry ->
                val parts = entry.split('=')
                require(parts.size == 2) { "Expected find=replace but got '${entry.trim()}'" }
                parseInt(parts[0], USHORT) to parseInt(parts[1], USHORT)
            }
            require(entries.size <= 255) { "At most 255 values" }
            set(type, entries.map { it.first }.toIntArray(), entries.map { it.second }.toIntArray())
        }
    },
)

private val translations = NpcField.Text(
    "Model translations",
    "121",
    hint = "model:x,y,z separated by ;",
    read = { type ->
        type.translations?.withIndex()?.filter { it.value != null }?.joinToString("; ") { (model, t) -> "$model:${t!!.joinToString(",")}" } ?: ""
    },
    write = { type, text ->
        if (text.isBlank()) {
            type.translations = null
        } else {
            val models = type.models?.size ?: 0
            val translations = arrayOfNulls<IntArray>(models)
            for (entry in text.split(';')) {
                val parts = entry.split(':')
                require(parts.size == 2) { "Expected model:x,y,z but got '${entry.trim()}'" }
                val model = parseInt(parts[0], UBYTE)
                require(model < models) { "Model index $model but npc only has $models models" }
                val offset = parts[1].split(',').map { parseInt(it, SBYTE) }
                require(offset.size == 3) { "Expected 3 offsets x,y,z" }
                translations[model] = offset.toIntArray()
            }
            type.translations = translations
        }
    },
)

private val transforms = NpcField.Transforms(
    "Transforms",
    "106/118",
    read = { type ->
        val npcs = type.multiNpcs
        if (npcs == null) NpcTransforms(emptyList(), -1) else NpcTransforms(npcs.copyOf(npcs.size - 1).toList(), npcs.last())
    },
    write = { type, transforms ->
        if (transforms.ids.isEmpty()) {
            require(transforms.default == -1) { "Add an index to use a default npc" }
            type.multiNpcs = null
        } else {
            require(transforms.ids.size <= 256) { "At most 256 indices" }
            type.multiNpcs = (transforms.ids + transforms.default).toIntArray()
        }
    },
)

private val params = NpcField.Params(
    "Params",
    "249",
    hint = "One per line: name = 123 or name = \"text\"",
    read = { type -> type.params.entries.joinToString("\n") { (key, value) -> paramLine(key, value) } },
    write = { type, text ->
        val params = linkedMapOf<Int, Any>()
        for (line in text.lines().filter { it.isNotBlank() }) {
            val index = line.indexOf('=')
            require(index != -1) { "Expected key = value but got '${line.trim()}'" }
            val key = parseParamKey(line.substring(0, index))
            val value = line.substring(index + 1).trim()
            params[key] = if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
                value.substring(1, value.length - 1)
            } else {
                value.toIntOrNull() ?: throw IllegalArgumentException("Value for ${line.substring(0, index).trim()} must be a number or \"quoted text\"")
            }
        }
        require(params.size <= 255) { "At most 255 params" }
        type.params.clear()
        type.params.putAll(params)
    },
)

fun paramLine(key: Int, value: Any): String {
    val name = ParamNames.name(key) ?: key.toString()
    return if (value is String) "$name = \"$value\"" else "$name = $value"
}

fun paramKeys(text: String): Set<Int> = text.lines().mapNotNull { line ->
    val index = line.indexOf('=')
    if (index == -1) null else runCatching { parseParamKey(line.substring(0, index)) }.getOrNull()
}.toSet()

private fun parseParamKey(text: String): Int {
    val key = text.trim()
    val id = key.toIntOrNull() ?: ParamNames.id(key) ?: throw IllegalArgumentException("Unknown param '$key'")
    require(id in 0..0xFFFFFF) { "Param id must be between 0 and ${0xFFFFFF}" }
    return id
}

private val diffusion = NpcField.Text(
    "Diffusion",
    "101",
    hint = "Multiple of 5, -640..635",
    read = { it.diffusion.toString() },
    write = { type, text ->
        val value = parseInt(text, -640..635)
        require(value % 5 == 0) { "Must be a multiple of 5" }
        type.diffusion = value
    },
)

val NPC_SECTIONS = listOf(
    NpcSection(
        "General",
        listOf(
            NpcField.Text("Name", "2", read = { it.name }, write = { type, text -> type.name = text }),
            int("Size", 12, 1..255, get = { it.size }, set = { t, v -> t.size = v }),
            int("Combat level", 95, OPT_USHORT, get = { it.combatLevel }, set = { t, v -> t.combatLevel = v }),
            int("Render anim (BAS)", 127, OPT_USHORT, get = { it.basId }, set = { t, v -> t.basId = v }),
            int("Head icon", 102, OPT_USHORT, get = { it.headIcon }, set = { t, v -> t.headIcon = v }),
            int("Health bar sprite", 122, OPT_USHORT, get = { it.healthBarSprite }, set = { t, v -> t.healthBarSprite = v }),
            int("Timer bar sprite", 139, OPT_USHORT, unverified = true, get = { it.timerbarSprite }, set = { t, v -> t.timerbarSprite = v }),
            int("Height", 123, OPT_USHORT, get = { it.height }, set = { t, v -> t.height = v }),
            int("Spawn direction", 125, SBYTE, get = { it.spawnDirection }, set = { t, v -> t.spawnDirection = v }),
            int("Movement capabilities", 119, SBYTE, get = { it.movementCapabilities }, set = { t, v -> t.movementCapabilities = v }),
            int("Map element", 142, OPT_USHORT, get = { it.mapElement }, set = { t, v -> t.mapElement = v }),
            int("Army icon", 138, OPT_USHORT, get = { it.mobilisingArmiesIcon }, set = { t, v -> t.mobilisingArmiesIcon = v }),
            int("Main option (0/1, -1 none)", 159, -1..1, get = { it.mainOptionIndex }, set = { t, v -> t.mainOptionIndex = v }),
            int("Pick size shift", 165, UBYTE, unverified = true, get = { it.pickSizeShift }, set = { t, v -> t.pickSizeShift = v }),
            int("Slayer type", 163, OPT_UBYTE, get = { it.slayerType }, set = { t, v -> t.slayerType = v }),
            int("Unknown 128", 128, OPT_UBYTE, get = { it.unknown128 }, set = { t, v -> t.unknown128 = v }),
            flag("Show on minimap", 93, get = { it.displayOnMiniMap }, set = { t, v -> t.displayOnMiniMap = v }),
            flag("Interactive", 107, get = { it.interactive }, set = { t, v -> t.interactive = v }),
            flag("Crawl", 109, get = { it.crawl }, set = { t, v -> t.crawl = v }),
            flag("Has shadow", 111, get = { it.hasShadow }, set = { t, v -> t.hasShadow = v }),
            flag("Render high priority", 99, get = { it.renderHighPriority }, set = { t, v -> t.renderHighPriority = v }),
            flag("Follower", 141, get = { it.isFollower }, set = { t, v -> t.isFollower = v }),
            flag("Invisible priority", 143, get = { it.invisiblePriority }, set = { t, v -> t.invisiblePriority = v }),
            flag("Vorbis", 162, unverified = true, get = { it.vorbis }, set = { t, v -> t.vorbis = v }),
        ),
    ),
    NpcSection(
        "Options",
        (0 until 5).map { op("Option ${it + 1}", 30 + it, it) { type -> type.ops } } +
            (0 until 5).map { op("Members option ${it + 1}", 150 + it, it) { type -> type.membersOps } },
    ),
    NpcSection(
        "Models",
        listOf(
            ints("Models", 1, OPT_USHORT, get = { it.models }, set = { t, v -> t.models = v }),
            ints("Chathead models", 60, USHORT, get = { it.headModels }, set = { t, v -> t.headModels = v }),
            pairs("Recolours", 40, get = { it.recolS to it.recolD }, set = { t, s, d -> t.recolS = s; t.recolD = d }),
            pairs("Retextures", 41, get = { it.retexS to it.retexD }, set = { t, s, d -> t.retexS = s; t.retexD = d }),
            ints("Recolour palette", 42, SBYTE, unverified = true, get = { it.recolDPalette }, set = { t, v -> t.recolDPalette = v }),
            translations,
        ),
    ),
    NpcSection(
        "Appearance",
        listOf(
            int("Scale horizontal", 97, USHORT, get = { it.scaleH }, set = { t, v -> t.scaleH = v }),
            int("Scale vertical", 98, USHORT, get = { it.scaleV }, set = { t, v -> t.scaleV = v }),
            int("Rotation speed", 103, USHORT, get = { it.yawSpeed }, set = { t, v -> t.yawSpeed = v }),
            int("Ambient", 100, SBYTE, get = { it.ambient }, set = { t, v -> t.ambient = v }),
            diffusion,
            int("Shadow outer colour", 113, USHORT, get = { it.shadowOuterColour }, set = { t, v -> t.shadowOuterColour = v }),
            int("Shadow inner colour", 113, USHORT, get = { it.shadowInnerColour }, set = { t, v -> t.shadowInnerColour = v }),
            int("Shadow outer alpha", 114, SBYTE, get = { it.shadowOuterAlpha }, set = { t, v -> t.shadowOuterAlpha = v }),
            int("Shadow inner alpha", 114, SBYTE, get = { it.shadowInnerAlpha }, set = { t, v -> t.shadowInnerAlpha = v }),
            int("Tint hue", 155, SBYTE, get = { it.colourHue }, set = { t, v -> t.colourHue = v }),
            int("Tint saturation", 155, SBYTE, get = { it.colourSaturation }, set = { t, v -> t.colourSaturation = v }),
            int("Tint lightness", 155, SBYTE, get = { it.colourLightness }, set = { t, v -> t.colourLightness = v }),
            int("Tint scale", 155, SBYTE, get = { it.colourScale }, set = { t, v -> t.colourScale = v }),
        ),
    ),
    NpcSection(
        "Transforms",
        listOf(
            int("Varbit", 106, OPT_USHORT, get = { it.multiNpcVarbit }, set = { t, v -> t.multiNpcVarbit = v }),
            int("Varp", 106, OPT_USHORT, get = { it.multiNpcVarp }, set = { t, v -> t.multiNpcVarp = v }),
            transforms,
        ),
    ),
    NpcSection(
        "Sounds",
        listOf(
            int("Idle sound", 134, OPT_USHORT, get = { it.readySound }, set = { t, v -> t.readySound = v }),
            int("Crawl sound", 134, OPT_USHORT, get = { it.crawlSound }, set = { t, v -> t.crawlSound = v }),
            int("Walk sound", 134, OPT_USHORT, get = { it.walkSound }, set = { t, v -> t.walkSound = v }),
            int("Run sound", 134, OPT_USHORT, get = { it.runSound }, set = { t, v -> t.runSound = v }),
            int("Sound range max", 134, UBYTE, get = { it.soundRangeMax }, set = { t, v -> t.soundRangeMax = v }),
            int("Sound range min", 168, UBYTE, unverified = true, get = { it.soundRangeMin }, set = { t, v -> t.soundRangeMin = v }),
            int("Sound volume", 140, UBYTE, get = { it.soundVolume }, set = { t, v -> t.soundVolume = v }),
            int("Sound rate min", 164, USHORT, unverified = true, get = { it.soundRateMin }, set = { t, v -> t.soundRateMin = v }),
            int("Sound rate max", 164, USHORT, unverified = true, get = { it.soundRateMax }, set = { t, v -> t.soundRateMax = v }),
        ),
    ),
    NpcSection(
        "Cursors",
        listOf(
            int("First cursor option", 135, OPT_UBYTE, get = { it.firstCursorOp }, set = { t, v -> t.firstCursorOp = v }),
            int("First cursor", 135, OPT_USHORT, get = { it.firstCursor }, set = { t, v -> t.firstCursor = v }),
            int("Second cursor option", 136, OPT_UBYTE, get = { it.secondCursorOp }, set = { t, v -> t.secondCursorOp = v }),
            int("Second cursor", 136, OPT_USHORT, get = { it.secondCursor }, set = { t, v -> t.secondCursor = v }),
            int("Attack cursor", 137, OPT_USHORT, get = { it.attackCursor }, set = { t, v -> t.attackCursor = v }),
        ),
    ),
    NpcSection(
        "Quests & params",
        listOf(
            ints("Quests", 160, USHORT, get = { it.quests }, set = { t, v -> t.quests = v }),
            params,
        ),
    ),
)
