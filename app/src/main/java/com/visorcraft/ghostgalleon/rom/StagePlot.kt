package com.visorcraft.ghostgalleon.rom

import org.json.JSONObject

enum class LaunchFace { AUTO, INTERACTIVE, COMPANION, OTHER }

data class StagePlot(
    val policy: SessionPolicy? = null,
    val launchFace: LaunchFace = LaunchFace.AUTO,
) {
    companion object {
        fun parse(raw: String?): LaunchFace = when (raw?.trim()?.lowercase()) {
            "interactive" -> LaunchFace.INTERACTIVE
            "companion" -> LaunchFace.COMPANION
            "other" -> LaunchFace.OTHER
            else -> LaunchFace.AUTO
        }

        fun fromJson(o: JSONObject): StagePlot = StagePlot(
            policy = if (o.has("policy") && !o.isNull("policy")) {
                SessionPolicy.parse(o.optString("policy"))
            } else {
                null
            },
            launchFace = parse(o.optString("launchFace", "auto")),
        )

        fun toJson(plot: StagePlot): JSONObject = JSONObject().apply {
            if (plot.policy != null) put("policy", plot.policy.name)
            put("launchFace", plot.launchFace.name.lowercase())
        }
    }
}

enum class PlotConfirm { NONE, KEEP_ON_YIELD_PLAYER, YIELD_ON_KEEP_PLAYER }

object StagePlots {
    fun resolve(
        romPlot: StagePlot?,
        packPlot: StagePlot?,
        packageYield: Boolean,
        playerId: String?,
    ): StagePlot {
        romPlot?.let { return it }
        packPlot?.let { return it }
        if (packageYield) return StagePlot(SessionPolicy.YIELD_BOTH, LaunchFace.AUTO)
        return StagePlot(SessionPolicy.forPlayerId(playerId), LaunchFace.AUTO)
    }

    fun launchDisplayId(
        face: LaunchFace,
        policy: SessionPolicy,
        interactiveId: Int?,
        companionId: Int?,
        launchId: Int?,
    ): Int? {
        if (policy == SessionPolicy.YIELD_BOTH) return launchId
        return when (face) {
            LaunchFace.AUTO, LaunchFace.OTHER -> launchId
            LaunchFace.INTERACTIVE -> interactiveId
            LaunchFace.COMPANION -> companionId
        }
    }

    fun confirmFor(builtIn: SessionPolicy, requested: SessionPolicy?): PlotConfirm {
        if (requested == null || requested == builtIn) return PlotConfirm.NONE
        if (builtIn == SessionPolicy.YIELD_BOTH && requested == SessionPolicy.KEEP_COMPANION) {
            return PlotConfirm.KEEP_ON_YIELD_PLAYER
        }
        if (builtIn == SessionPolicy.KEEP_COMPANION && requested == SessionPolicy.YIELD_BOTH) {
            return PlotConfirm.YIELD_ON_KEEP_PLAYER
        }
        return PlotConfirm.NONE
    }
}
