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
