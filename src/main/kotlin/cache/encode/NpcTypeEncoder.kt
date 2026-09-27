package cache.encode

import cache.types.NpcType
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import util.p1
import util.p2
import util.p3
import util.p4
import util.pjstr

object NpcTypeEncoder {

    fun encode(type: NpcType): ByteArray {
        val buf = Unpooled.buffer()
        try {
            buf.encode(type)
            return ByteBufUtil.getBytes(buf)
        } finally {
            buf.release()
        }
    }

    private fun ByteBuf.encode(type: NpcType) {
        type.models?.let { models ->
            p1(1)
            p1(models.size)
            models.forEach { p2(it) }
        }

        if (type.name != "null") {
            p1(2)
            pjstr(type.name)
        }

        if (type.size != 1) {
            p1(12)
            p1(type.size)
        }

        type.ops.forEachIndexed { index, op ->
            if (op != null) {
                p1(30 + index)
                pjstr(op)
            }
        }

        writePairs(40, type.recolS, type.recolD)
        writePairs(41, type.retexS, type.retexD)

        type.recolDPalette?.let { palette ->
            p1(42)
            p1(palette.size)
            palette.forEach { p1(it) }
        }

        type.headModels?.let { models ->
            p1(60)
            p1(models.size)
            models.forEach { p2(it) }
        }

        if (!type.displayOnMiniMap) {
            p1(93)
        }

        if (type.combatLevel != -1) {
            p1(95)
            p2(type.combatLevel)
        }

        if (type.scaleH != 128) {
            p1(97)
            p2(type.scaleH)
        }

        if (type.scaleV != 128) {
            p1(98)
            p2(type.scaleV)
        }

        if (type.renderHighPriority) {
            p1(99)
        }

        if (type.ambient != 0) {
            p1(100)
            p1(type.ambient)
        }

        if (type.diffusion != 0) {
            p1(101)
            p1(type.diffusion / 5)
        }

        if (type.headIcon != -1) {
            p1(102)
            p2(type.headIcon)
        }

        if (type.yawSpeed != 32) {
            p1(103)
            p2(type.yawSpeed)
        }

        val multiNpcs = type.multiNpcs
        if (multiNpcs != null) {
            val defaultId = multiNpcs.last()
            val extended = defaultId != -1
            p1(if (extended) 118 else 106)
            p2(type.multiNpcVarbit)
            p2(type.multiNpcVarp)
            if (extended) {
                p2(defaultId)
            }
            p1(multiNpcs.size - 2)
            for (i in 0 until multiNpcs.size - 1) {
                p2(multiNpcs[i])
            }
        }

        if (!type.interactive) {
            p1(107)
        }

        if (!type.crawl) {
            p1(109)
        }

        if (!type.hasShadow) {
            p1(111)
        }

        if (type.shadowOuterColour != 0 || type.shadowInnerColour != 0) {
            p1(113)
            p2(type.shadowOuterColour)
            p2(type.shadowInnerColour)
        }

        if (type.shadowOuterAlpha != -96 || type.shadowInnerAlpha != -16) {
            p1(114)
            p1(type.shadowOuterAlpha)
            p1(type.shadowInnerAlpha)
        }

        if (type.movementCapabilities != 0) {
            p1(119)
            p1(type.movementCapabilities)
        }

        type.translations?.let { translations ->
            p1(121)
            p1(translations.count { it != null })
            translations.forEachIndexed { model, translation ->
                if (translation != null) {
                    p1(model)
                    p1(translation[0])
                    p1(translation[1])
                    p1(translation[2])
                }
            }
        }

        if (type.healthBarSprite != -1) {
            p1(122)
            p2(type.healthBarSprite)
        }

        if (type.height != -1) {
            p1(123)
            p2(type.height)
        }

        if (type.spawnDirection != 4) {
            p1(125)
            p1(type.spawnDirection)
        }

        if (type.basId != -1) {
            p1(127)
            p2(type.basId)
        }

        if (type.unknown128 != -1) {
            p1(128)
            p1(type.unknown128)
        }

        if (type.readySound != -1 || type.crawlSound != -1 || type.walkSound != -1 || type.runSound != -1 || type.soundRangeMax != 0) {
            p1(134)
            p2(type.readySound)
            p2(type.crawlSound)
            p2(type.walkSound)
            p2(type.runSound)
            p1(type.soundRangeMax)
        }

        if (type.firstCursorOp != -1 || type.firstCursor != -1) {
            p1(135)
            p1(type.firstCursorOp)
            p2(type.firstCursor)
        }

        if (type.secondCursorOp != -1 || type.secondCursor != -1) {
            p1(136)
            p1(type.secondCursorOp)
            p2(type.secondCursor)
        }

        if (type.attackCursor != -1) {
            p1(137)
            p2(type.attackCursor)
        }

        if (type.mobilisingArmiesIcon != -1) {
            p1(138)
            p2(type.mobilisingArmiesIcon)
        }

        if (type.timerbarSprite != -1) {
            p1(139)
            p2(type.timerbarSprite)
        }

        if (type.soundVolume != 255) {
            p1(140)
            p1(type.soundVolume)
        }

        if (type.isFollower) {
            p1(141)
        }

        if (type.mapElement != -1) {
            p1(142)
            p2(type.mapElement)
        }

        if (type.invisiblePriority) {
            p1(143)
        }

        type.membersOps.forEachIndexed { index, op ->
            if (op != null) {
                p1(150 + index)
                pjstr(op)
            }
        }

        if (type.colourHue != 0 || type.colourSaturation != 0 || type.colourLightness != 0 || type.colourScale != 0) {
            p1(155)
            p1(type.colourHue)
            p1(type.colourSaturation)
            p1(type.colourLightness)
            p1(type.colourScale)
        }

        when (type.mainOptionIndex) {
            1 -> p1(158)
            0 -> p1(159)
        }

        type.quests?.let { quests ->
            p1(160)
            p1(quests.size)
            quests.forEach { p2(it) }
        }

        if (type.vorbis) {
            p1(162)
        }

        if (type.slayerType != -1) {
            p1(163)
            p1(type.slayerType)
        }

        if (type.soundRateMin != 256 || type.soundRateMax != 256) {
            p1(164)
            p2(type.soundRateMin)
            p2(type.soundRateMax)
        }

        if (type.pickSizeShift != 0) {
            p1(165)
            p1(type.pickSizeShift)
        }

        if (type.soundRangeMin != 0) {
            p1(168)
            p1(type.soundRangeMin)
        }

        if (type.params.isNotEmpty()) {
            p1(249)
            p1(type.params.size)
            type.params.forEach { (key, value) ->
                p1(if (value is String) 1 else 0)
                p3(key)
                when (value) {
                    is String -> pjstr(value)
                    is Int -> p4(value)
                    else -> error("Unsupported param type ${value::class.simpleName} for key $key")
                }
            }
        }

        p1(0)
    }

    private fun ByteBuf.writePairs(opcode: Int, source: IntArray?, dest: IntArray?) {
        if (source == null || dest == null) {
            return
        }
        p1(opcode)
        p1(source.size)
        for (i in source.indices) {
            p2(source[i])
            p2(dest[i])
        }
    }
}
