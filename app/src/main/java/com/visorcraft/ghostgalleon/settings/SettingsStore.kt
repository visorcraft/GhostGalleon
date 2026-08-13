package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.input.SeatAnchor
import com.visorcraft.ghostgalleon.rom.SessionPolicy
import com.visorcraft.ghostgalleon.rom.SessionRingEntry
import com.visorcraft.ghostgalleon.rom.StagePlot
import com.visorcraft.ghostgalleon.state.UIMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SettingsStore(private val file: File) {

    fun load(): Settings {
        if (!file.exists()) return Settings.DEFAULT
        return try {
            val o = JSONObject(file.readText())
            val s = parse(o)
            // Persist migrations immediately: any file older than the
            // current schema is re-saved with the new stamp.
            if (o.optInt("schemaVersion", 1) < CURRENT_SCHEMA) save(s)
            s
        } catch (e: Exception) {
            Settings.DEFAULT
        }
    }

    fun save(s: Settings) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        // Compact JSON (no pretty indent): settings save on every launch/
        // favorite/dock edit — indent doubled CPU + IO for zero UX gain.
        tmp.writeText(toJson(s).toString())
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        const val CURRENT_SCHEMA = 11

        // Internal (not private) so SettingsBundle can pack/unpack settings
        // through the exact same codec the on-disk file uses; same-module
        // host tests cover the round-trip.
        internal fun parse(o: JSONObject): Settings {
        val schemaVersion = o.optInt("schemaVersion", 1)
        val keyMapObj = o.optJSONObject("keyMap")
        val keyMap = if (keyMapObj != null) {
            keyMapObj.keys().asSequence().associate { k ->
                k.toInt() to Action.valueOf(keyMapObj.getString(k))
            }
        } else Settings.DEFAULT_KEY_MAP
        return Settings(
            theme = o.optString("theme", "dark"),
            accentColor = o.optLong("accentColor", 0xFF3F51B5).toInt(),
            background = o.optString("background", "solid"),
            gridColumns = o.optInt("gridColumns", 5),
            iconSizeDp = o.optInt("iconSizeDp", 72),
            cardSizeDp = o.optInt("cardSizeDp", 200),
            defaultMode = UIMode.valueOf(o.optString("defaultMode", "GRID")),
            primaryDisplay = if (schemaVersion < 2) 1 else o.optInt("primaryDisplay", 1),
            gyroEnabled = o.optBoolean("gyroEnabled", true),
            angleLock = o.optBoolean("angleLock", false),
            haptics = o.optBoolean("haptics", true),
            showHints = o.optBoolean("showHints", true),
            showLabels = o.optBoolean("showLabels", true),
            // Added within schema v3: files without the field keep the
            // vertical default, no schema bump needed.
            gridDirection = o.optString("gridDirection", "vertical"),
            wallpaperUri = if (!o.isNull("wallpaperUri") && o.has("wallpaperUri")) {
                o.getString("wallpaperUri")
            } else {
                null
            },
            // Added within schema v3: files without the field have no ROM
            // folder grants yet, no schema bump needed.
            romTreeUris = o.optJSONArray("romTreeUris").toStringList(),
            // Added within schema v3: files without the field simply have no
            // SteamGridDB key yet, no schema bump needed.
            sgdbApiKey = if (!o.isNull("sgdbApiKey") && o.has("sgdbApiKey")) {
                o.getString("sgdbApiKey")
            } else {
                null
            },
            // v6 makes the dock auto-growing (capacity 9, visible slots
            // derived at render) and stores only the filled keys in slot
            // order. Older files — v4/v5 fixed 5-slot arrays (nulls
            // included) and v3 dockPackages — collapse to their filled
            // keys in order via DockSlots.compact.
            dockSlots = DockSlots.compact(
                if (o.has("dockSlots")) {
                    o.getJSONArray("dockSlots").toNullableStringList()
                } else {
                    o.optJSONArray("dockPackages").toStringList()
                }
            ),
            // v3 introduces the curated grid. Older files have no gridSlots
            // and migrate to a fully blank grid (the all-apps grid is gone).
            gridSlots = if (schemaVersion >= 3 && o.has("gridSlots")) {
                o.getJSONArray("gridSlots").toNullableStringList()
            } else {
                GridSlots.blank()
            },
            hiddenPackages = o.optJSONArray("hiddenPackages").toStringList().toSet(),
            // Within schema v8: absent = no user-hidden ROMs.
            hiddenRomIds = o.optJSONArray("hiddenRomIds").toStringList().toSet(),
            // Added within schema v3: absent = no per-app overrides.
            customNames = o.optJSONObject("customNames").toStringMap(),
            customIcons = o.optJSONObject("customIcons").toStringMap(),
            romNames = o.optJSONObject("romNames").toStringMap(),
            keyMap = keyMap,
            // Schema v5 library/play/collections (absent = empty defaults).
            lastLaunchedMs = o.optJSONObject("lastLaunchedMs").toLongMap(),
            // Within v8: absent = show Resume chip when candidates exist.
            hideResumeChip = o.optBoolean("hideResumeChip", false),
            playtimeMs = o.optJSONObject("playtimeMs").toLongMap(),
            defaultPlayers = o.optJSONObject("defaultPlayers").toStringMap(),
            artOverrides = o.optJSONObject("artOverrides").toStringMap(),
            favorites = o.optJSONArray("favorites").toStringList().toSet(),
            collections = o.optJSONObject("collections").toStringListMap(),
            // Within schema v6: absent = show setup when library empty.
            setupDismissed = o.optBoolean("setupDismissed", false),
            // Within v8 optional: chrome discover nudge + scrape policy.
            chromeDiscoverDismissed = o.optBoolean("chromeDiscoverDismissed", false),
            scrapeWifiOnly = o.optBoolean("scrapeWifiOnly", true),
            scrapePauseBelowBattery = o.optInt("scrapePauseBelowBattery", 15)
                .coerceIn(0, 100),
            // Schema v7 fields (absent = defaults).
            companionRole = o.optString("companionRole", CompanionRole.HERO.name)
                .ifBlank { CompanionRole.HERO.name },
            companionPinnedPackage = if (!o.isNull("companionPinnedPackage") &&
                o.has("companionPinnedPackage")
            ) {
                o.getString("companionPinnedPackage")
            } else {
                null
            },
            romProfiles = o.optJSONObject("romProfiles").toStringMap(),
            folders = o.optJSONObject("folders").toFolderMap(),
            themePackId = o.optString("themePackId", ThemePack.GHOST.id)
                .ifBlank { ThemePack.GHOST.id },
            themeCustomJson = if (!o.isNull("themeCustomJson") && o.has("themeCustomJson")) {
                o.getString("themeCustomJson")
            } else {
                null
            },
            raApiKey = if (!o.isNull("raApiKey") && o.has("raApiKey")) {
                o.getString("raApiKey")
            } else {
                null
            },
            raUsername = if (!o.isNull("raUsername") && o.has("raUsername")) {
                o.getString("raUsername")
            } else {
                null
            },
            // Schema v8: display topology policy. Migrate primaryDisplay when
            // interactiveDisplayMode is absent.
            deviceProfileId = o.optString("deviceProfileId", "auto").ifBlank { "auto" },
            interactiveDisplayMode = migrateInteractiveDisplayMode(o, schemaVersion),
            orientationMode = migrateOrientationMode(o),
            userPinnedPrimaryId = if (o.has("userPinnedPrimaryId") && !o.isNull("userPinnedPrimaryId")) {
                o.optInt("userPinnedPrimaryId")
            } else {
                null
            },
            // Within schema v8: absent browseChrome → minimal defaults.
            browseChrome = BrowseChrome.fromJson(
                if (o.has("browseChrome") && !o.isNull("browseChrome")) {
                    o.optJSONObject("browseChrome")
                } else {
                    null
                },
            ),
            // Within schema v8: absent searchHistory → empty recent queries.
            searchHistory = o.optJSONArray("searchHistory").toStringList()
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            stickDeadzone = o.optInt("stickDeadzone", 50).coerceIn(20, 80),
            layoutSeeded = o.optBoolean("layoutSeeded", false),
            sessionRing = o.optJSONArray("sessionRing").toSessionRing(),
            detectBlackCompanion = o.optBoolean("detectBlackCompanion", true),
            raNetworkCommands = o.optBoolean("raNetworkCommands", false),
            raNetworkCmdPort = o.optInt("raNetworkCmdPort", 55355).let { if (it in 1..65535) it else 55355 },
            // Schema v10 owned-surface fields (absent = defaults).
            inputHostTimeoutMs = o.optInt("inputHostTimeoutMs", 8000).coerceIn(1_000, 60_000),
            inputAssistEnabled = o.optBoolean("inputAssistEnabled", false),
            raHandoffSave = o.optBoolean("raHandoffSave", true),
            stagePlots = o.optJSONObject("stagePlots").toStagePlotMap(),
            packageYield = o.optJSONObject("packageYield").toBooleanMap(),
            winlatorCockpit = o.optBoolean("winlatorCockpit", true),
            ramLensesEnabled = o.optBoolean("ramLensesEnabled", false),
            ramLensPackUri = o.optString("ramLensPackUri", "").ifBlank { null },
            stackClones = o.optBoolean("stackClones", false),
            // Schema v11 play-host depth fields (absent = defaults).
            ramTrackersEnabled = o.optBoolean("ramTrackersEnabled", true),
            raCinemaEnabled = o.optBoolean("raCinemaEnabled", false),
            raCinemaIntervalMs = o.optInt("raCinemaIntervalMs", 60_000).coerceIn(15_000, 300_000),
            raTheaterEnabled = o.optBoolean("raTheaterEnabled", false),
            raTheaterPollMs = o.optInt("raTheaterPollMs", 60_000).coerceIn(30_000, 300_000),
            raSecondSeat = o.optBoolean("raSecondSeat", false),
            raSeatAnchors = o.optJSONArray("raSeatAnchors").toSeatAnchors(),
            saveFerryEnabled = o.optBoolean("saveFerryEnabled", true),
            postureAware = o.optBoolean("postureAware", true),
            postureSuggestYield = o.optBoolean("postureSuggestYield", false),
            playHostHelperPackage = o.optString("playHostHelperPackage", "").ifBlank { null },
            romHelpers = o.optJSONObject("romHelpers").toStringMap(),
            warmResumeEnabled = o.optBoolean("warmResumeEnabled", true),
            warmResumeLoad = o.optBoolean("warmResumeLoad", false),
            schemaVersion = CURRENT_SCHEMA,
        )
        }

        /**
         * Promote legacy [Settings.angleLock] to orientationMode when the
         * v8 field is still auto/absent.
         */
        private fun migrateOrientationMode(o: JSONObject): String {
            val mode = o.optString("orientationMode", "auto").trim().ifBlank { "auto" }
            if (mode != "auto") return mode
            return if (o.optBoolean("angleLock", false)) "lock_landscape" else "auto"
        }

        private fun migrateInteractiveDisplayMode(o: JSONObject, schemaVersion: Int): String {
            if (o.has("interactiveDisplayMode") && !o.isNull("interactiveDisplayMode")) {
                val m = o.optString("interactiveDisplayMode", "auto").trim()
                if (m.isNotEmpty()) return m
            }
            // Pre-v8: derive from primaryDisplay
            val pd = if (schemaVersion < 2) 1 else o.optInt("primaryDisplay", 1)
            return when (pd) {
                0 -> "default"
                1 -> "secondary"
                else -> "id:$pd"
            }
        }

        internal fun toJson(s: Settings): JSONObject {
        val keyMapObj = JSONObject()
        s.keyMap.forEach { (code, action) -> keyMapObj.put(code.toString(), action.name) }
        return JSONObject()
            .put("theme", s.theme)
            .put("accentColor", s.accentColor.toLong() and 0xFFFFFFFFL)
            .put("background", s.background)
            .put("gridColumns", s.gridColumns)
            .put("iconSizeDp", s.iconSizeDp)
            .put("cardSizeDp", s.cardSizeDp)
            .put("defaultMode", s.defaultMode.name)
            .put("primaryDisplay", s.primaryDisplay)
            .put("gyroEnabled", s.gyroEnabled)
            .put("angleLock", s.angleLock)
            .put("haptics", s.haptics)
            .put("showHints", s.showHints)
            .put("showLabels", s.showLabels)
            .put("gridDirection", s.gridDirection)
            .put("wallpaperUri", s.wallpaperUri ?: JSONObject.NULL)
            .put("romTreeUris", JSONArray(s.romTreeUris))
            .put("sgdbApiKey", s.sgdbApiKey ?: JSONObject.NULL)
            // v6: persist only the filled dock keys in slot order; blanks
            // are derived at render (visibleCount = max(4, min(filled+1, 9))).
            .put("dockSlots", JSONArray(DockSlots.filled(s.dockSlots)))
            .put("gridSlots", JSONArray().apply {
                s.gridSlots.forEach { put(it ?: JSONObject.NULL) }
            })
            .put("hiddenPackages", JSONArray(s.hiddenPackages.toList()))
            .put("hiddenRomIds", JSONArray(s.hiddenRomIds.toList()))
            .put("customNames", JSONObject().apply {
                s.customNames.forEach { (pkg, name) -> put(pkg, name) }
            })
            .put("customIcons", JSONObject().apply {
                s.customIcons.forEach { (pkg, uri) -> put(pkg, uri) }
            })
            .put("romNames", JSONObject().apply {
                s.romNames.forEach { (id, name) -> put(id, name) }
            })
            .put("keyMap", keyMapObj)
            .put("lastLaunchedMs", JSONObject().apply {
                s.lastLaunchedMs.forEach { (k, v) -> put(k, v) }
            })
            .put("hideResumeChip", s.hideResumeChip)
            .put("playtimeMs", JSONObject().apply {
                s.playtimeMs.forEach { (k, v) -> put(k, v) }
            })
            .put("defaultPlayers", JSONObject().apply {
                s.defaultPlayers.forEach { (k, v) -> put(k, v) }
            })
            .put("artOverrides", JSONObject().apply {
                s.artOverrides.forEach { (k, v) -> put(k, v) }
            })
            .put("favorites", JSONArray(s.favorites.toList()))
            .put("collections", JSONObject().apply {
                s.collections.forEach { (name, keys) ->
                    put(name, JSONArray(keys))
                }
            })
            .put("setupDismissed", s.setupDismissed)
            .put("chromeDiscoverDismissed", s.chromeDiscoverDismissed)
            .put("scrapeWifiOnly", s.scrapeWifiOnly)
            .put("scrapePauseBelowBattery", s.scrapePauseBelowBattery)
            .put("companionRole", s.companionRole)
            .put("companionPinnedPackage", s.companionPinnedPackage ?: JSONObject.NULL)
            .put("romProfiles", JSONObject().apply {
                s.romProfiles.forEach { (k, v) -> put(k, v) }
            })
            .put("folders", JSONObject().apply {
                s.folders.forEach { (id, spec) ->
                    put(id, JSONObject()
                        .put("id", spec.id)
                        .put("name", spec.name)
                        .put("members", JSONArray(spec.members)))
                }
            })
            .put("themePackId", s.themePackId)
            .put("themeCustomJson", s.themeCustomJson ?: JSONObject.NULL)
            .put("raApiKey", s.raApiKey ?: JSONObject.NULL)
            .put("raUsername", s.raUsername ?: JSONObject.NULL)
            .put("deviceProfileId", s.deviceProfileId)
            .put("interactiveDisplayMode", s.interactiveDisplayMode)
            .put("orientationMode", s.orientationMode)
            .put(
                "userPinnedPrimaryId",
                s.userPinnedPrimaryId?.let { it } ?: JSONObject.NULL,
            )
            .put("browseChrome", s.browseChrome.toJson())
            .put("searchHistory", JSONArray(s.searchHistory))
            .put("stickDeadzone", s.stickDeadzone)
            .put("layoutSeeded", s.layoutSeeded)
            .put("sessionRing", JSONArray().apply {
                s.sessionRing.forEach { e ->
                    put(JSONObject()
                        .put("key", e.key)
                        .put("playerId", e.playerId ?: JSONObject.NULL)
                        .put("packageName", e.packageName)
                        .put("policy", e.policy.name)
                        .put("launchedAtMs", e.launchedAtMs)
                        .put("title", e.title))
                }
            })
            .put("detectBlackCompanion", s.detectBlackCompanion)
            .put("raNetworkCommands", s.raNetworkCommands)
            .put("raNetworkCmdPort", s.raNetworkCmdPort)
            .put("inputHostTimeoutMs", s.inputHostTimeoutMs)
            .put("inputAssistEnabled", s.inputAssistEnabled)
            .put("raHandoffSave", s.raHandoffSave)
            .put("stagePlots", JSONObject().apply {
                s.stagePlots.forEach { (id, plot) -> put(id, StagePlot.toJson(plot)) }
            })
            .put("packageYield", JSONObject().apply {
                s.packageYield.forEach { (pkg, yield) -> put(pkg, yield) }
            })
            .put("winlatorCockpit", s.winlatorCockpit)
            .put("ramLensesEnabled", s.ramLensesEnabled)
            .put("ramLensPackUri", s.ramLensPackUri ?: JSONObject.NULL)
            .put("stackClones", s.stackClones)
            .put("ramTrackersEnabled", s.ramTrackersEnabled)
            .put("raCinemaEnabled", s.raCinemaEnabled)
            .put("raCinemaIntervalMs", s.raCinemaIntervalMs)
            .put("raTheaterEnabled", s.raTheaterEnabled)
            .put("raTheaterPollMs", s.raTheaterPollMs)
            .put("raSecondSeat", s.raSecondSeat)
            .put("raSeatAnchors", JSONArray().apply {
                s.raSeatAnchors.forEach { put(SeatAnchor.toJson(it)) }
            })
            .put("saveFerryEnabled", s.saveFerryEnabled)
            .put("postureAware", s.postureAware)
            .put("postureSuggestYield", s.postureSuggestYield)
            .put("playHostHelperPackage", s.playHostHelperPackage ?: JSONObject.NULL)
            .put("romHelpers", JSONObject().apply {
                s.romHelpers.forEach { (k, v) -> put(k, v) }
            })
            .put("warmResumeEnabled", s.warmResumeEnabled)
            .put("warmResumeLoad", s.warmResumeLoad)
            .put("schemaVersion", CURRENT_SCHEMA)
        }

        private fun JSONArray?.toSessionRing(): List<SessionRingEntry> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { i ->
                val o = optJSONObject(i) ?: return@mapNotNull null
                val key = o.optString("key").trim()
                if (key.isEmpty()) return@mapNotNull null
                SessionRingEntry(
                    key = key,
                    playerId = if (o.isNull("playerId")) null
                    else o.optString("playerId").trim().ifEmpty { null },
                    packageName = o.optString("packageName"),
                    policy = SessionPolicy.parse(o.optString("policy")),
                    launchedAtMs = o.optLong("launchedAtMs"),
                    title = o.optString("title").ifBlank { key },
                )
            }
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { getString(it) }
        }

        private fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { getString(it) }
        }

        private fun JSONObject?.toLongMap(): Map<String, Long> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { getLong(it) }
        }

        private fun JSONObject?.toStringListMap(): Map<String, List<String>> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { k ->
                val arr = optJSONArray(k)
                if (arr == null) emptyList()
                else (0 until arr.length()).map { arr.getString(it) }
            }
        }

        private fun JSONArray.toNullableStringList(): List<String?> =
            (0 until length()).map { if (isNull(it)) null else getString(it) }

        private fun JSONObject?.toFolderMap(): Map<String, FolderSpec> {
            if (this == null) return emptyMap()
            return keys().asSequence().mapNotNull { id ->
                val child = optJSONObject(id) ?: return@mapNotNull null
                val name = child.optString("name", id).ifBlank { id }
                val membersArr = child.optJSONArray("members")
                val members = if (membersArr == null) emptyList()
                else (0 until membersArr.length()).map { membersArr.getString(it) }
                id to FolderSpec(
                    id = child.optString("id", id).ifBlank { id },
                    name = name,
                    members = members,
                )
            }.toMap()
        }

        private fun JSONObject?.toStagePlotMap(): Map<String, StagePlot> {
            if (this == null) return emptyMap()
            return keys().asSequence().mapNotNull { id ->
                val child = optJSONObject(id) ?: return@mapNotNull null
                id to StagePlot.fromJson(child)
            }.toMap()
        }

        private fun JSONObject?.toBooleanMap(): Map<String, Boolean> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { optBoolean(it) }
        }

        private fun JSONArray?.toSeatAnchors(): List<SeatAnchor> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { i ->
                val o = optJSONObject(i) ?: return@mapNotNull null
                SeatAnchor.fromJson(o)
            }
        }
    }
}
