package com.visorcraft.ghostgalleon.input

object SecondSeatPolicy {
    /** SNES-like D-pad + face + start/select in the lower-right 40%. */
    val DEFAULT_ANCHORS: List<SeatAnchor> = listOf(
        SeatAnchor("up", 0.68f, 0.70f),
        SeatAnchor("down", 0.68f, 0.86f),
        SeatAnchor("left", 0.61f, 0.78f),
        SeatAnchor("right", 0.75f, 0.78f),
        SeatAnchor("a", 0.98f, 0.78f),
        SeatAnchor("b", 0.90f, 0.86f),
        SeatAnchor("x", 0.90f, 0.70f),
        SeatAnchor("y", 0.82f, 0.78f),
        SeatAnchor("start", 0.86f, 0.94f),
        SeatAnchor("select", 0.76f, 0.94f),
    )

    fun allowed(
        dualMode: Boolean,
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        assistConnected: Boolean,
        seatEnabled: Boolean,
        playerIsRa: Boolean,
        cockpit: Boolean,
    ): Boolean =
        dualMode &&
            playHostAllowed &&
            !sessionOwnsCompanion &&
            assistConnected &&
            seatEnabled &&
            playerIsRa &&
            !cockpit

    fun anchorsOrDefault(stored: List<SeatAnchor>): List<SeatAnchor> =
        if (stored.isNotEmpty()) stored else DEFAULT_ANCHORS

    fun point(anchor: SeatAnchor, widthPx: Int, heightPx: Int): Pair<Float, Float> =
        Pair(anchor.nx * widthPx.toFloat(), anchor.ny * heightPx.toFloat())
}
