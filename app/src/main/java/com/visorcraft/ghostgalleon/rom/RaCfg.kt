package com.visorcraft.ghostgalleon.rom

/** Pure RetroArch cfg mutate for opt-in network commands. No Android APIs. */
object RaCfg {

    const val ENABLE_KEY = "network_cmd_enable"
    const val PORT_KEY = "network_cmd_port"
    const val DEFAULT_PORT = 55355

    private val assignment = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*(.*?)\s*$""")

    fun enableNetworkCommands(cfgText: String): Pair<String, Boolean> {
        val normalized = cfgText.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n').toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) {
            lines.removeAt(lines.lastIndex)
        }

        var enableTrue = false
        var hasPort = false
        var changed = false

        for (i in lines.indices) {
            val parsed = parseAssignment(lines[i]) ?: continue
            when (parsed.first) {
                ENABLE_KEY -> {
                    if (parsed.second.equals("true", ignoreCase = true)) {
                        enableTrue = true
                    } else {
                        lines[i] = """$ENABLE_KEY = "true""""
                        enableTrue = true
                        changed = true
                    }
                }
                PORT_KEY -> hasPort = true
            }
        }

        if (!enableTrue) {
            lines.add("""$ENABLE_KEY = "true"""")
            changed = true
        }
        if (!hasPort) {
            lines.add("""$PORT_KEY = "$DEFAULT_PORT"""")
            changed = true
        }

        if (!changed) return cfgText to false
        return lines.joinToString("\n", postfix = "\n") to true
    }

    fun readPort(cfgText: String, defaultPort: Int = DEFAULT_PORT): Int {
        for (line in cfgText.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val parsed = parseAssignment(line) ?: continue
            if (parsed.first != PORT_KEY) continue
            val n = parsed.second.toIntOrNull() ?: continue
            if (n in 1..65535) return n
        }
        return defaultPort
    }

    private fun parseAssignment(line: String): Pair<String, String>? {
        val m = assignment.matchEntire(line) ?: return null
        val key = m.groupValues[1]
        var value = m.groupValues[2]
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value = value.substring(1, value.length - 1)
        }
        return key to value
    }
}
