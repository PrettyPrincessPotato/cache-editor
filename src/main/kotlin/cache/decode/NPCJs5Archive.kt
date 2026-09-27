package cache.decode

import cache.Js5
import cache.Js5Archive
import cache.Type
import cache.types.NpcType
import com.displee.cache.CacheLibrary
import io.netty.buffer.ByteBuf
import util.g1
import util.g1s
import util.g2
import util.g3
import util.g4
import util.gjstr

class NPCJs5Archive(cache: CacheLibrary) : Js5Archive<NpcType>(cache) {

    override fun archive(): Int = Js5.NPC

    override fun group(id: Int): Int = id ushr 7

    override fun file(id: Int): Int = id and 0x7f

    override fun create(id: Int) = NpcType(id)

    override fun readOpcode(type: Type, buf: ByteBuf, code: Int) {
        val npcType = type as? NpcType ?: error("readOpcode expected NpcType, got ${type::class.simpleName}")

        with(npcType) {
            when (code) {
                1 -> models = IntArray(buf.g1()) { buf.g2().nullable() }

                2 -> name = buf.gjstr()

                12 -> size = buf.g1()

                in 30..34 -> ops[code - 30] = buf.gjstr()

                40 -> {
                    val count = buf.g1()
                    recolS = IntArray(count)
                    recolD = IntArray(count)
                    repeat(count) {
                        recolS!![it] = buf.g2()
                        recolD!![it] = buf.g2()
                    }
                }

                41 -> {
                    val count = buf.g1()
                    retexS = IntArray(count)
                    retexD = IntArray(count)
                    repeat(count) {
                        retexS!![it] = buf.g2()
                        retexD!![it] = buf.g2()
                    }
                }

                42 -> recolDPalette = IntArray(buf.g1()) { buf.g1s() }

                60 -> headModels = IntArray(buf.g1()) { buf.g2() }

                93 -> displayOnMiniMap = false

                95 -> combatLevel = buf.g2()

                97 -> scaleH = buf.g2()

                98 -> scaleV = buf.g2()

                99 -> renderHighPriority = true

                100 -> ambient = buf.g1s()

                101 -> diffusion = buf.g1s() * 5

                102 -> headIcon = buf.g2()

                103 -> yawSpeed = buf.g2()

                106, 118 -> {
                    multiNpcVarbit = buf.g2().nullable()
                    multiNpcVarp = buf.g2().nullable()

                    val defaultId = if (code == 118) buf.g2().nullable() else -1
                    val count = buf.g1()
                    val npcs = IntArray(count + 2)
                    for (i in 0..count) {
                        npcs[i] = buf.g2().nullable()
                    }
                    npcs[count + 1] = defaultId
                    multiNpcs = npcs
                }

                107 -> interactive = false

                109 -> crawl = false

                111 -> hasShadow = false

                113 -> {
                    shadowOuterColour = buf.g2()
                    shadowInnerColour = buf.g2()
                }

                114 -> {
                    shadowOuterAlpha = buf.g1s()
                    shadowInnerAlpha = buf.g1s()
                }

                119 -> movementCapabilities = buf.g1s()

                121 -> {
                    val translations = arrayOfNulls<IntArray>(models?.size ?: 0)
                    repeat(buf.g1()) {
                        val model = buf.g1()
                        translations[model] = intArrayOf(buf.g1s(), buf.g1s(), buf.g1s())
                    }
                    this.translations = translations
                }

                122 -> healthBarSprite = buf.g2()

                123 -> height = buf.g2()

                125 -> spawnDirection = buf.g1s()

                127 -> basId = buf.g2()

                128 -> unknown128 = buf.g1()

                134 -> {
                    readySound = buf.g2().nullable()
                    crawlSound = buf.g2().nullable()
                    walkSound = buf.g2().nullable()
                    runSound = buf.g2().nullable()
                    soundRangeMax = buf.g1()
                }

                135 -> {
                    firstCursorOp = buf.g1()
                    firstCursor = buf.g2()
                }

                136 -> {
                    secondCursorOp = buf.g1()
                    secondCursor = buf.g2()
                }

                137 -> attackCursor = buf.g2()

                138 -> mobilisingArmiesIcon = buf.g2()

                139 -> timerbarSprite = buf.g2()

                140 -> soundVolume = buf.g1()

                141 -> isFollower = true

                142 -> mapElement = buf.g2()

                143 -> invisiblePriority = true

                in 150..154 -> membersOps[code - 150] = buf.gjstr()

                155 -> {
                    colourHue = buf.g1s()
                    colourSaturation = buf.g1s()
                    colourLightness = buf.g1s()
                    colourScale = buf.g1s()
                }

                158 -> mainOptionIndex = 1

                159 -> mainOptionIndex = 0

                160 -> quests = IntArray(buf.g1()) { buf.g2() }

                162 -> vorbis = true

                163 -> slayerType = buf.g1()

                164 -> {
                    soundRateMin = buf.g2()
                    soundRateMax = buf.g2()
                }

                165 -> pickSizeShift = buf.g1()

                168 -> soundRangeMin = buf.g1()

                249 -> {
                    repeat(buf.g1()) {
                        val isString = buf.g1() == 1
                        val key = buf.g3()
                        params[key] = if (isString) buf.gjstr() else buf.g4()
                    }
                }

                else -> error("Unknown npc opcode $code for npc ${npcType.id}")
            }
        }
    }

    private fun Int.nullable() = if (this == 0xFFFF) -1 else this
}
