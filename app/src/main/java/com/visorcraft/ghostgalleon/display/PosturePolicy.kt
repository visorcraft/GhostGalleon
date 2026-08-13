package com.visorcraft.ghostgalleon.display

enum class DevicePosture { UNKNOWN, CLOSED, TABLETOP, BOOK, FLAT }

enum class PostureEffect { NONE, PAUSE_IF_PLAYING, SHOW_YIELD_CHIP, HIDE_YIELD_CHIP }

object PosturePolicy {
    private const val CLOSED_LT = 15f
    private const val TABLETOP_LT = 140f
    private const val BOOK_LT = 170f

    /** v1: [deviceState] unused; unknown OEM ids stay UNKNOWN. */
    @Suppress("UNUSED_PARAMETER")
    fun fromSensors(hingeDeg: Float?, deviceState: Int?): DevicePosture {
        if (hingeDeg == null || hingeDeg.isNaN()) return DevicePosture.UNKNOWN
        return when {
            hingeDeg < CLOSED_LT -> DevicePosture.CLOSED
            hingeDeg < TABLETOP_LT -> DevicePosture.TABLETOP
            hingeDeg < BOOK_LT -> DevicePosture.BOOK
            else -> DevicePosture.FLAT
        }
    }

    fun effect(
        posture: DevicePosture,
        previous: DevicePosture,
        dualMode: Boolean,
        sessionOwnsCompanion: Boolean,
        keepRaPlaying: Boolean,
        suggestYieldEnabled: Boolean,
        postureAware: Boolean,
    ): PostureEffect {
        if (posture == previous) return PostureEffect.NONE
        if (posture == DevicePosture.CLOSED &&
            postureAware &&
            !sessionOwnsCompanion &&
            keepRaPlaying
        ) {
            return PostureEffect.PAUSE_IF_PLAYING
        }
        if (posture == DevicePosture.FLAT &&
            suggestYieldEnabled &&
            !sessionOwnsCompanion &&
            dualMode
        ) {
            return PostureEffect.SHOW_YIELD_CHIP
        }
        if (previous == DevicePosture.FLAT) return PostureEffect.HIDE_YIELD_CHIP
        return PostureEffect.NONE
    }
}
