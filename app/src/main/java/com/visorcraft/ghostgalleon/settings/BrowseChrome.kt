package com.visorcraft.ghostgalleon.settings

import com.visorcraft.ghostgalleon.library.LibraryBrowse
import org.json.JSONObject

/**
 * Optional Game Mode / Quick Panel / deck chrome. Defaults are **minimal**:
 * core rails only (All / Recent / Continue / Fav + platforms + search/select).
 * Power-user rails and extras opt in via Settings so the deck stays uncluttered.
 *
 * Pure; host-tested. Stored as optional JSON under settings (schema v8+).
 */
data class BrowseChrome(
    /** Apps by install time. */
    val installedRail: Boolean = false,
    /** CATEGORY_GAME apps + ROMs. */
    val gamesRail: Boolean = false,
    /** Most-played (Top). */
    val topRail: Boolean = false,
    /** Played in the last 24 hours (Today). */
    val todayRail: Boolean = false,
    /** Played in the last 7 days (Week). */
    val weekRail: Boolean = false,
    /** Played in the last 30 days (Month). */
    val monthRail: Boolean = false,
    /** A–Z alphabetical rail (includes letter-jump strip). */
    val alphaRail: Boolean = false,
    /** Unplayed / New ROMs. */
    val unplayedRail: Boolean = false,
    /** Random pick chip on the browse bar. */
    val randomChip: Boolean = false,
    /** Genre chips from gamelist meta. */
    val genreChips: Boolean = false,
    /** Developer / publisher chips from gamelist meta. */
    val developerChips: Boolean = false,
    /** Release-year decade chips (1990s, 2000s, …) from gamelist meta. */
    val yearChips: Boolean = false,
    /**
     * Hide ROMs whose platform has no installed player package.
     * Apps are unaffected. Default off — full library stays visible.
     */
    val launchableOnly: Boolean = false,
    /** Platform filter chips (SNES, Switch, …). Default on — core for ROMs. */
    val platformChips: Boolean = true,
    /** Named user collection chips (not Favorites). Default on if user created any. */
    val collectionRails: Boolean = true,
    /**
     * Clock / battery (time & charge). When on: compact overlay on Grid/Game
     * decks and the larger pill on the companion hero. Off by default so
     * dual-screen chrome stays minimal (Sugar bottom/top either role).
     */
    val deckStatusPill: Boolean = false,
    /**
     * Companion “Resume {title}” pill (launch last-played when different from
     * selection). Off by default; swipe-dismiss still applies when shown
     * ([Settings.hideResumeChip] until next launch).
     */
    val resumeChip: Boolean = false,
    /**
     * Quick Panel browse shortcuts beyond Continue: Random, Top, Fav, Games,
     * Installed, Week, Month, A–Z, New (each rail still needs its own flag).
     * System tiles (Wi‑Fi / Settings / Theme) always stay.
     */
    val quickPanelBrowse: Boolean = false,
) {
    fun allowsMode(mode: LibraryBrowse.Mode): Boolean = when (mode) {
        LibraryBrowse.Mode.ALL,
        LibraryBrowse.Mode.RECENT,
        LibraryBrowse.Mode.FAVORITES,
        LibraryBrowse.Mode.COLLECTION,
        -> true
        LibraryBrowse.Mode.PLAYED_TODAY -> todayRail
        LibraryBrowse.Mode.PLAYED_THIS_WEEK -> weekRail
        LibraryBrowse.Mode.PLAYED_THIS_MONTH -> monthRail
        LibraryBrowse.Mode.MOST_PLAYED -> topRail
        LibraryBrowse.Mode.RECENTLY_INSTALLED -> installedRail
        LibraryBrowse.Mode.GAMES -> gamesRail
        LibraryBrowse.Mode.ALPHA -> alphaRail
        LibraryBrowse.Mode.UNPLAYED -> unplayedRail
    }

    /**
     * Game Mode rails offered as Quick Panel cells when [quickPanelBrowse] is
     * on. Top/Random stay special (selection jump / immediate launch). Order
     * is stable for layout. Pure; host-tested.
     */
    fun quickPanelRailShortcuts(): List<LibraryBrowse.Mode> {
        if (!quickPanelBrowse) return emptyList()
        return buildList {
            add(LibraryBrowse.Mode.RECENT)
            add(LibraryBrowse.Mode.FAVORITES)
            if (gamesRail) add(LibraryBrowse.Mode.GAMES)
            if (installedRail) add(LibraryBrowse.Mode.RECENTLY_INSTALLED)
            if (todayRail) add(LibraryBrowse.Mode.PLAYED_TODAY)
            if (weekRail) add(LibraryBrowse.Mode.PLAYED_THIS_WEEK)
            if (monthRail) add(LibraryBrowse.Mode.PLAYED_THIS_MONTH)
            if (alphaRail) add(LibraryBrowse.Mode.ALPHA)
            if (unplayedRail) add(LibraryBrowse.Mode.UNPLAYED)
        }
    }

    /** Drop disallowed mode/genre/developer/year into a safe query for the current chrome. */
    fun sanitize(q: LibraryBrowse.BrowseQuery): LibraryBrowse.BrowseQuery {
        var next = q
        if (!allowsMode(next.mode)) {
            next = LibraryBrowse.BrowseQuery()
        }
        if (!genreChips && !next.genre.isNullOrBlank()) {
            next = next.copy(genre = null)
        }
        if (!developerChips && !next.developer.isNullOrBlank()) {
            next = next.copy(developer = null)
        }
        if (!yearChips && !next.yearDecade.isNullOrBlank()) {
            next = next.copy(yearDecade = null)
        }
        if (!platformChips && next.platformId != null) {
            next = next.copy(platformId = null)
        }
        if (!collectionRails && next.mode == LibraryBrowse.Mode.COLLECTION) {
            next = LibraryBrowse.BrowseQuery()
        }
        return next
    }

    fun isMinimal(): Boolean = this == MINIMAL
    fun isFull(): Boolean = this == FULL

    /**
     * Sticky preset id for Settings segmented control.
     * - [PRESET_MINIMAL] / [PRESET_FULL] only when every flag matches.
     * - [PRESET_CUSTOM] whenever any flag differs (toggling a rail while on
     *   Minimal/Full flips the label to Custom without a no-op).
     */
    fun presetId(): String = when {
        isFull() -> PRESET_FULL
        isMinimal() -> PRESET_MINIMAL
        else -> PRESET_CUSTOM
    }

    /**
     * Power-user rails (beyond core All/Recent/Continue/Fav + platforms +
     * search). Used to group Settings toggles under an expander.
     */
    fun hasAnyPowerRail(): Boolean =
        installedRail || gamesRail || topRail || todayRail || weekRail ||
            monthRail || alphaRail || unplayedRail || randomChip ||
            genreChips || developerChips || yearChips || launchableOnly ||
            deckStatusPill || resumeChip || quickPanelBrowse

    /**
     * Whether the Settings power-rails expander should start expanded /
     * stay visible after a Minimal↔Full rebind. Pure; host-tested.
     */
    fun powerRailsPanelVisible(): Boolean = hasAnyPowerRail() || !isMinimal()

    /**
     * True when going from [from] to [this] can use [DeckState.Change.CHROME]
     * in-place rebind (no add/remove of StatusPill or Resume chip views).
     * Toggling [deckStatusPill] or [resumeChip] requires a full SETTINGS paint.
     */
    fun allowsInPlaceChromeUpdate(from: BrowseChrome): Boolean =
        deckStatusPill == from.deckStatusPill && resumeChip == from.resumeChip

    /**
     * Flags that change which browse chips exist (not StatusPill / Resume).
     * Used as the chrome half of [LibraryBrowse.filterChromeStructureKey].
     */
    fun chipBarSignature(): String = buildString {
        if (installedRail) append('i')
        if (gamesRail) append('g')
        if (topRail) append('t')
        if (todayRail) append('d')
        if (weekRail) append('w')
        if (monthRail) append('m')
        if (alphaRail) append('a')
        if (unplayedRail) append('u')
        if (randomChip) append('r')
        if (genreChips) append('G')
        if (developerChips) append('D')
        if (yearChips) append('Y')
        if (launchableOnly) append('L')
        if (platformChips) append('P')
        if (collectionRails) append('C')
    }

    /**
     * Ordered flag snapshot for Settings chrome switches (core first, then
     * power rails). Used to rebind Switch isChecked after Minimal/Full without
     * recreating the activity — must match [chromeFlag] get order in Settings.
     */
    fun switchFlags(): List<Boolean> = listOf(
        platformChips,
        collectionRails,
        installedRail,
        gamesRail,
        topRail,
        todayRail,
        weekRail,
        monthRail,
        alphaRail,
        unplayedRail,
        randomChip,
        genreChips,
        developerChips,
        yearChips,
        launchableOnly,
        deckStatusPill,
        resumeChip,
        quickPanelBrowse,
    )

    fun toJson(): JSONObject = JSONObject()
        .put("installedRail", installedRail)
        .put("gamesRail", gamesRail)
        .put("topRail", topRail)
        .put("todayRail", todayRail)
        .put("weekRail", weekRail)
        .put("monthRail", monthRail)
        .put("alphaRail", alphaRail)
        .put("unplayedRail", unplayedRail)
        .put("randomChip", randomChip)
        .put("genreChips", genreChips)
        .put("developerChips", developerChips)
        .put("yearChips", yearChips)
        .put("launchableOnly", launchableOnly)
        .put("platformChips", platformChips)
        .put("collectionRails", collectionRails)
        .put("deckStatusPill", deckStatusPill)
        .put("resumeChip", resumeChip)
        .put("quickPanelBrowse", quickPanelBrowse)

    companion object {
        const val PRESET_MINIMAL = "minimal"
        const val PRESET_CUSTOM = "custom"
        const val PRESET_FULL = "full"

        /** Default: uncluttered deck for most users. */
        val MINIMAL = BrowseChrome()

        /** Everything on for power users. */
        val FULL = BrowseChrome(
            installedRail = true,
            gamesRail = true,
            topRail = true,
            todayRail = true,
            weekRail = true,
            monthRail = true,
            alphaRail = true,
            unplayedRail = true,
            randomChip = true,
            genreChips = true,
            developerChips = true,
            yearChips = true,
            launchableOnly = true,
            platformChips = true,
            collectionRails = true,
            deckStatusPill = true,
            resumeChip = true,
            quickPanelBrowse = true,
        )

        fun fromJson(o: JSONObject?): BrowseChrome {
            if (o == null) return MINIMAL
            return BrowseChrome(
                installedRail = o.optBoolean("installedRail", false),
                gamesRail = o.optBoolean("gamesRail", false),
                topRail = o.optBoolean("topRail", false),
                todayRail = o.optBoolean("todayRail", false),
                weekRail = o.optBoolean("weekRail", false),
                monthRail = o.optBoolean("monthRail", false),
                alphaRail = o.optBoolean("alphaRail", false),
                unplayedRail = o.optBoolean("unplayedRail", false),
                randomChip = o.optBoolean("randomChip", false),
                genreChips = o.optBoolean("genreChips", false),
                developerChips = o.optBoolean("developerChips", false),
                yearChips = o.optBoolean("yearChips", false),
                launchableOnly = o.optBoolean("launchableOnly", false),
                platformChips = o.optBoolean("platformChips", true),
                collectionRails = o.optBoolean("collectionRails", true),
                deckStatusPill = o.optBoolean("deckStatusPill", false),
                resumeChip = o.optBoolean("resumeChip", false),
                quickPanelBrowse = o.optBoolean("quickPanelBrowse", false),
            )
        }
    }
}
