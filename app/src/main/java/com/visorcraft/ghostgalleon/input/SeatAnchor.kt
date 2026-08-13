package com.visorcraft.ghostgalleon.input

import org.json.JSONObject

data class SeatAnchor(val id: String, val nx: Float, val ny: Float) {
    companion object {
        fun fromJson(o: JSONObject): SeatAnchor? {
            val id = o.optString("id", "").trim()
            if (id.isEmpty()) return null
            val nx = o.optDouble("nx", Double.NaN).toFloat()
            val ny = o.optDouble("ny", Double.NaN).toFloat()
            if (nx.isNaN() || ny.isNaN()) return null
            return SeatAnchor(id, nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
        }
        fun toJson(a: SeatAnchor): JSONObject =
            JSONObject().put("id", a.id).put("nx", a.nx.toDouble()).put("ny", a.ny.toDouble())
    }
}
