# Play-host depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the KEEP play host a tracker, savestate cinema, achievement theater, second seat, save ferry, posture theater, helper embed, and warm Continue — without ever touching a yielded melonDualDS / Azahar panel.

**Architecture:** Pure host-testable units (`HostSurfacePolicy`, `TrackerCatalog`, `CinemaPolicy`, `RaTheater`, `SecondSeatPolicy`, `SaveFerry`, `PosturePolicy`, `HelperEmbedPolicy`, `WarmResumePolicy`) sit beside shipped `PlayHostPolicy`, `LensCatalog`, `RaCommand`, `InputAssistPolicy`, `SessionHandoff`, `RomIdentity`, and `ActivityEmbed`. Schema **v11** lands once with every new field default-safe. Android code only adds in-place Views, one hinge listener, SAF copies, and the existing `enqueueRaUdp` / assist `dispatchGesture` paths. No fourth session clock.

**Tech Stack:** Kotlin, classic Views, host JUnit (`./gradlew :app:testDebugUnitTest --offline`), no new Gradle dependencies, no Compose.

**Spec:** [`docs/play-host-depth.md`](../../play-host-depth.md)

## Global Constraints

- Dual-surface games **keep both screens**. Never steal a panel from melonDualDS or Azahar.
- `sessionOwnsCompanionDisplay` (YIELD_BOTH or greedy) ⇒ no tracker, cinema, theater, seat, helper, posture relaunch, warm load, focus lock, RAM UDP, cockpit, PixelCopy, or play HUD.
- `playHostAllowed` is the only paint gate for every KEEP surface except ferry (library / details).
- No `SYSTEM_ALERT_WINDOW` / overlay windows. No `WRITE_CORE_RAM`. No `INJECT_EVENTS` / `injectInputEvent`. No `force-stop`. No `MANAGE_EXTERNAL_STORAGE`.
- Do not `ActivityEmbed` the open session package. Do not change `RomEntry.id` / `SlotKey`.
- Do not pre-launch an emulator process. Do not auto-YIELD from a hinge angle.
- Display ids from `DisplayTopology` — never hard-code `0`/`1`.
- Android `Display` / `Sensor` / `AccessibilityService` types stay out of the pure units.
- `OpenSession` stays playtime-only. `SessionSurface` stays the policy record. `HostSurface` is process-only chrome.
- Network / assist / hinge / hash never call `updateSettings` / `notifyChanged` / `publishRomEntries` except theater’s existing `SELECTION_ONLY` when awarded count changes, and user-driven Settings edits.
- One outstanding RA datagram (`enqueueRaUdp`). Cinema, tracker, probe, pause, handoff, and warm load queue; they do not pile up.
- Exclusive surfaces: `SEAT`, `HELPER`, `COCKPIT` hide tracker / cinema / theater. Cockpit disables seat and helper.
- New strings in all five catalogs, then `python3 scripts/i18n_audit.py --write && --check`.
- Commits: Conventional Commits, human author only, no AI attribution.
- Device claims require the Sugar matrix in the spec. Host green is not a Sugar claim.
- SINGLE: tracker, cinema, theater, seat, helper, posture face, and warm load are no-ops. Ferry and identity still work. Warm resolve/probe may run.

## File map

| File | Role | Phase |
|---|---|---|
| `ui/HostSurface.kt` | Exclusive surface enum + policy | all |
| `settings/Settings.kt` + `SettingsStore.kt` | Schema v11 | 1 (all fields) |
| `rom/LensCatalog.kt` | Grow `surface` + `widgets` | 1 |
| `rom/TrackerCatalog.kt` | Widget accept / bit format | 1 |
| `ui/deck/CompanionPanel.kt` | Tracker / cinema / theater / seat / helper views | 1–4, 7 |
| `rom/CinemaPolicy.kt` | Slot band 9–12, capture gate | 2 |
| `library/RaTheater.kt` | Parse + next/unlock diff | 3 |
| `library/RaFetcher.kt` | Optional: pass raw JSON to theater parse | 3 |
| `input/SecondSeatPolicy.kt` | Seat gate + default anchors | 4 |
| `input/InputAssistPolicy.kt` | `mayInjectSeat` | 4 |
| `input/InputAssistService.kt` | Calibrated tap/hold on launch display | 4 |
| `settings/Action.kt` | `TOGGLE_SEAT` | 4 |
| `rom/SaveFerry.kt` | Same-title / same-player / classify | 5 |
| `library/GameDetails.kt` + details UI | Ferry confirm | 5 |
| `display/PosturePolicy.kt` | Hinge buckets + effects | 6 |
| `ui/HelperEmbedPolicy.kt` | Embed gate, fail closed | 7 |
| `ui/deck/ActivityEmbed.kt` | Unchanged API; helper calls `attach` | 7 |
| `rom/WarmResumePolicy.kt` | Probe + Continue-only load | 8 |
| `ui/deck/Deck.kt` | `LaunchReason` into launch | 8 |
| `GhostGalleonApp.kt` | Process-only host state, ferry IO, warm probe | 2, 5, 8 |
| `ui/settings/SettingsActivity.kt` | New rows | 1–8 |
| Tests listed per task | Host-only, Robolectric-free | |

---

## Phase 1 — Tracker surface

Done when: `HostSurfacePolicy` hides tracker on exclusive surfaces; lens JSON with `surface`/`widgets` parses; bad widgets are rejected; KEEP HUD paints an in-place grid only when lenses + trackers + match allow; yield never reads RAM for a tracker.

### Task 1: HostSurface policy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/ui/HostSurface.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/ui/HostSurfaceTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class HostSurface { HUD, TRACKER, CINEMA, THEATER, SEAT, HELPER, COCKPIT }

object HostSurfacePolicy {
    fun exclusive(surface: HostSurface): Boolean
    fun showsTracker(surface: HostSurface): Boolean
    fun showsCinema(surface: HostSurface): Boolean
    fun showsTheater(surface: HostSurface): Boolean
    fun seatAllowed(surface: HostSurface, cockpit: Boolean): Boolean
    fun helperAllowed(surface: HostSurface, cockpit: Boolean): Boolean
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostSurfaceTest {

    @Test
    fun `seat helper and cockpit are exclusive`() {
        assertTrue(HostSurfacePolicy.exclusive(HostSurface.SEAT))
        assertTrue(HostSurfacePolicy.exclusive(HostSurface.HELPER))
        assertTrue(HostSurfacePolicy.exclusive(HostSurface.COCKPIT))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.HUD))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.TRACKER))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.CINEMA))
        assertFalse(HostSurfacePolicy.exclusive(HostSurface.THEATER))
    }

    @Test
    fun `shared HUD bodies hide on exclusive surfaces`() {
        assertTrue(HostSurfacePolicy.showsTracker(HostSurface.HUD))
        assertTrue(HostSurfacePolicy.showsTracker(HostSurface.TRACKER))
        assertTrue(HostSurfacePolicy.showsCinema(HostSurface.CINEMA))
        assertTrue(HostSurfacePolicy.showsTheater(HostSurface.THEATER))
        assertFalse(HostSurfacePolicy.showsTracker(HostSurface.SEAT))
        assertFalse(HostSurfacePolicy.showsCinema(HostSurface.HELPER))
        assertFalse(HostSurfacePolicy.showsTheater(HostSurface.COCKPIT))
    }

    @Test
    fun `cockpit blocks seat and helper`() {
        assertFalse(HostSurfacePolicy.seatAllowed(HostSurface.HUD, cockpit = true))
        assertFalse(HostSurfacePolicy.helperAllowed(HostSurface.HUD, cockpit = true))
        assertTrue(HostSurfacePolicy.seatAllowed(HostSurface.HUD, cockpit = false))
        assertTrue(HostSurfacePolicy.helperAllowed(HostSurface.SEAT, cockpit = false))
        assertFalse(HostSurfacePolicy.seatAllowed(HostSurface.HELPER, cockpit = false))
        assertFalse(HostSurfacePolicy.helperAllowed(HostSurface.SEAT, cockpit = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.HostSurfaceTest'`

Expected: compile fail (`Unresolved reference: HostSurfacePolicy`)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.ui

enum class HostSurface { HUD, TRACKER, CINEMA, THEATER, SEAT, HELPER, COCKPIT }

object HostSurfacePolicy {
    fun exclusive(surface: HostSurface): Boolean =
        surface == HostSurface.SEAT ||
            surface == HostSurface.HELPER ||
            surface == HostSurface.COCKPIT

    fun showsTracker(surface: HostSurface): Boolean = !exclusive(surface)
    fun showsCinema(surface: HostSurface): Boolean = !exclusive(surface)
    fun showsTheater(surface: HostSurface): Boolean = !exclusive(surface)

    fun seatAllowed(surface: HostSurface, cockpit: Boolean): Boolean {
        if (cockpit) return false
        return surface != HostSurface.HELPER
    }

    fun helperAllowed(surface: HostSurface, cockpit: Boolean): Boolean {
        if (cockpit) return false
        return surface != HostSurface.SEAT
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.HostSurfaceTest'`

Expected: BUILD SUCCESSFUL, tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/HostSurface.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/ui/HostSurfaceTest.kt
git commit -m "feat: exclusive play-host surfaces for tracker through cockpit"
```

### Task 2: Settings schema v11

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/settings/Settings.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/settings/SettingsStore.kt` (`CURRENT_SCHEMA = 11`)
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/settings/SettingsStoreTest.kt`
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/input/SeatAnchor.kt` (persist helpers only; seat policy is Task 9)

**Interfaces:**
- Produces `Settings` fields:

```kotlin
val ramTrackersEnabled: Boolean = true
val raCinemaEnabled: Boolean = false
val raCinemaIntervalMs: Int = 60_000
val raTheaterEnabled: Boolean = false
val raTheaterPollMs: Int = 60_000
val raSecondSeat: Boolean = false
val raSeatAnchors: List<SeatAnchor> = emptyList()
val saveFerryEnabled: Boolean = true
val postureAware: Boolean = true
val postureSuggestYield: Boolean = false
val playHostHelperPackage: String? = null
val romHelpers: Map<String, String> = emptyMap()
val warmResumeEnabled: Boolean = true
val warmResumeLoad: Boolean = false
```

```kotlin
// input/SeatAnchor.kt
data class SeatAnchor(val id: String, val nx: Float, val ny: Float) {
    companion object {
        fun fromJson(o: org.json.JSONObject): SeatAnchor? {
            val id = o.optString("id", "").trim()
            if (id.isEmpty()) return null
            val nx = o.optDouble("nx", Double.NaN).toFloat()
            val ny = o.optDouble("ny", Double.NaN).toFloat()
            if (nx.isNaN() || ny.isNaN()) return null
            return SeatAnchor(id, nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
        }
        fun toJson(a: SeatAnchor): org.json.JSONObject =
            org.json.JSONObject().put("id", a.id).put("nx", a.nx.toDouble()).put("ny", a.ny.toDouble())
    }
}
```

Clamp `raCinemaIntervalMs` to `[15000, 300000]`, `raTheaterPollMs` to `[30000, 300000]`. Missing v11 keys load as defaults. `schemaVersion` stamps 11. Existing `assertEquals(10, loaded.schemaVersion)` in v8/v10 tests becomes `11`.

- [ ] **Step 1: Write the failing test**

Add to `SettingsStoreTest`:

```kotlin
    @Test
    fun `v11 play-host fields round-trip and missing keys default`() {
        val f = tmp.root.resolve("cfg-v11/settings.json")
        val anchor = SeatAnchor("a", 0.8f, 0.7f)
        val s = Settings.DEFAULT.copy(
            schemaVersion = 11,
            ramTrackersEnabled = false,
            raCinemaEnabled = true,
            raCinemaIntervalMs = 30_000,
            raTheaterEnabled = true,
            raTheaterPollMs = 45_000,
            raSecondSeat = true,
            raSeatAnchors = listOf(anchor),
            saveFerryEnabled = false,
            postureAware = false,
            postureSuggestYield = true,
            playHostHelperPackage = "org.example.maps",
            romHelpers = mapOf("snes:x.sfc" to "org.example.wiki"),
            warmResumeEnabled = false,
            warmResumeLoad = true,
        )
        SettingsStore(f).save(s)
        val loaded = SettingsStore(f).load()
        assertEquals(false, loaded.ramTrackersEnabled)
        assertEquals(true, loaded.raCinemaEnabled)
        assertEquals(30_000, loaded.raCinemaIntervalMs)
        assertEquals(true, loaded.raTheaterEnabled)
        assertEquals(45_000, loaded.raTheaterPollMs)
        assertEquals(true, loaded.raSecondSeat)
        assertEquals(listOf(anchor), loaded.raSeatAnchors)
        assertEquals(false, loaded.saveFerryEnabled)
        assertEquals(false, loaded.postureAware)
        assertEquals(true, loaded.postureSuggestYield)
        assertEquals("org.example.maps", loaded.playHostHelperPackage)
        assertEquals("org.example.wiki", loaded.romHelpers["snes:x.sfc"])
        assertEquals(false, loaded.warmResumeEnabled)
        assertEquals(true, loaded.warmResumeLoad)
        assertEquals(11, loaded.schemaVersion)

        val raw = org.json.JSONObject(f.readText())
        raw.put("schemaVersion", 10)
        raw.remove("raCinemaEnabled")
        raw.remove("romHelpers")
        raw.remove("warmResumeLoad")
        f.writeText(raw.toString())
        val migrated = SettingsStore(f).load()
        assertEquals(false, migrated.raCinemaEnabled)
        assertTrue(migrated.romHelpers.isEmpty())
        assertEquals(false, migrated.warmResumeLoad)
        assertEquals(true, migrated.warmResumeEnabled)
        assertEquals(11, migrated.schemaVersion)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.SettingsStoreTest'`

Expected: compile fail or assertion fail on schema 10 / missing fields

- [ ] **Step 3: Write minimal implementation**

Add the `Settings` fields. Set `CURRENT_SCHEMA = 11`. Parse:

```kotlin
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
```

`toSeatAnchors`: each JSONObject → `SeatAnchor.fromJson`. Write the inverse in `toJson`. Do **not** implement seat inject or cinema ticks yet.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.SettingsStoreTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/settings/Settings.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/settings/SettingsStore.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/input/SeatAnchor.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/settings/SettingsStoreTest.kt
git commit -m "feat: persist play-host depth settings as schema v11"
```

### Task 3: TrackerCatalog and lens widgets

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/LensCatalog.kt`
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/TrackerCatalog.kt`
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/rom/LensCatalogTest.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/TrackerCatalogTest.kt`

**Interfaces:**
- Consumes: existing `LensSpec` / `LensCatalog.parse`
- Produces: `LensSpec.surface: String` default `"line"`, `LensSpec.widgets: List<TrackerWidget>` default empty

```kotlin
enum class TrackerKind { BITS, GRID, METER, LINE }

data class TrackerWidget(
    val kind: TrackerKind,
    val blockIndex: Int,
    val cols: Int,
    val labels: List<String>,
)

object TrackerCatalog {
    fun parseKind(raw: String?): TrackerKind
    fun acceptable(spec: LensSpec): Boolean
    fun bitOn(bytes: ByteArray, index: Int): Boolean
    fun meterValue(bytes: ByteArray): Int   // first byte 0..255, empty → 0
}
```

`LensCatalog.acceptable` stays byte-budget only. `TrackerCatalog.acceptable` = `LensCatalog.acceptable(spec)` AND (no widgets **or** every widget `blockIndex` in range AND for `BITS`, `labels.size <= block.length * 8`). Missing `surface` = `"line"`. Existing fixture tests must still pass.

- [ ] **Step 1: Write the failing tests**

Add to `LensCatalogTest` (same fixture file, extra object):

```kotlin
    @Test
    fun `parse surface and widgets with line default`() {
        val json = """
          [{"id":"line-only","title":"t","match":{"romId":["snes:a"]},"intervalMs":200,
            "blocks":[{"address":"0x1","length":1,"format":"bytes","labels":[]}]}]
        """.trimIndent()
        val spec = LensCatalog.parse(json).single()
        assertEquals("line", spec.surface)
        assertTrue(spec.widgets.isEmpty())
    }
```

Create `TrackerCatalogTest`:

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerCatalogTest {

    private val block = LensBlock(0x7EF340, 2, "bitfield", emptyList())
    private val spec = LensSpec(
        id = "fix",
        title = "fix",
        platformId = "snes",
        hashes = setOf("abc"),
        romIds = emptySet(),
        intervalMs = 200,
        blocks = listOf(block),
        surface = "tracker",
        widgets = listOf(
            TrackerWidget(TrackerKind.BITS, 0, 8, listOf("bow", "boomerang")),
        ),
    )

    @Test
    fun `acceptable widgets stay inside the lens budget`() {
        assertTrue(LensCatalog.acceptable(spec))
        assertTrue(TrackerCatalog.acceptable(spec))
        val badIndex = spec.copy(
            widgets = listOf(TrackerWidget(TrackerKind.BITS, 3, 8, listOf("x"))),
        )
        assertFalse(TrackerCatalog.acceptable(badIndex))
        val tooManyBits = spec.copy(
            widgets = listOf(TrackerWidget(TrackerKind.BITS, 0, 8, List(17) { "b$it" })),
        )
        assertFalse(TrackerCatalog.acceptable(tooManyBits))
        val line = spec.copy(surface = "line", widgets = emptyList())
        assertTrue(TrackerCatalog.acceptable(line))
    }

    @Test
    fun `bitOn reads little-endian bit index`() {
        val bytes = byteArrayOf(0x02, 0x00) // bit 1
        assertFalse(TrackerCatalog.bitOn(bytes, 0))
        assertTrue(TrackerCatalog.bitOn(bytes, 1))
        assertFalse(TrackerCatalog.bitOn(bytes, 16))
        assertEquals(2, TrackerCatalog.meterValue(bytes))
        assertEquals(0, TrackerCatalog.meterValue(byteArrayOf()))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.LensCatalogTest' --tests 'com.visorcraft.ghostgalleon.rom.TrackerCatalogTest'`

Expected: compile fail (`surface` / `TrackerCatalog`)

- [ ] **Step 3: Write minimal implementation**

Grow `LensSpec` with defaulted `surface` and `widgets`. In `parseLens`, read `surface` (default `"line"`) and `widgets` array (`kind`, `block`, `cols`, `labels`). Invalid widget objects skipped.

```kotlin
object TrackerCatalog {
    fun parseKind(raw: String?): TrackerKind = when (raw?.trim()?.lowercase()) {
        "bits" -> TrackerKind.BITS
        "grid" -> TrackerKind.GRID
        "meter" -> TrackerKind.METER
        else -> TrackerKind.LINE
    }

    fun acceptable(spec: LensSpec): Boolean {
        if (!LensCatalog.acceptable(spec)) return false
        if (spec.widgets.isEmpty()) return true
        return spec.widgets.all { w ->
            w.blockIndex in spec.blocks.indices &&
                (w.kind != TrackerKind.BITS ||
                    w.labels.size <= spec.blocks[w.blockIndex].length * 8)
        }
    }

    fun bitOn(bytes: ByteArray, index: Int): Boolean {
        val byteIndex = index / 8
        if (index < 0 || byteIndex >= bytes.size) return false
        val bit = index % 8
        return ((bytes[byteIndex].toInt() ushr bit) and 1) == 1
    }

    fun meterValue(bytes: ByteArray): Int =
        if (bytes.isEmpty()) 0 else bytes[0].toInt() and 0xFF
}
```

- [ ] **Step 4: Run tests to verify they pass**

Expected: PASS (including existing `LensCatalogTest`)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/LensCatalog.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/rom/TrackerCatalog.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/LensCatalogTest.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/TrackerCatalogTest.kt
git commit -m "feat: parse tracker widgets on read-only RAM lenses"
```

### Task 4: Tracker HUD + Settings

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt` — `var hostSurface: HostSurface = HostSurface.HUD`; reset to `HUD` in `clearSessionSurface` / `markSessionGreedy` / yield `closeQuietly` path
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt` — after a successful lens match, if `spec.surface == "tracker" && settings.ramTrackersEnabled && TrackerCatalog.acceptable(spec) && HostSurfacePolicy.showsTracker(app.hostSurface)`, bind tag `play_hud_tracker` (GridLayout of TextViews) and update cell `alpha` from `TrackerCatalog.bitOn` / `meterValue`. Reuse `tickPlayHudLens` RAM read; do not open a second UDP
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt` — Library row **Trackers** under RAM lenses (`ramTrackersEnabled`)
- Strings (all five catalogs): `settings_ram_trackers`, `play_hud_tracker`

**Interfaces:**
- Consumes: `tickPlayHudLens`, `HostSurfacePolicy.showsTracker`, `TrackerCatalog`

Never start when `sessionOwnsCompanionDisplay` or `!ramLensesEnabled`. In-place only. Zero bundled tracker JSON is fine.

- [ ] **Step 1: i18n + audit**

```xml
    <string name="settings_ram_trackers">RAM trackers</string>
    <string name="play_hud_tracker">Tracker</string>
```

Spanish / German / French / Thai translations. Then:

```bash
python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check
```

Expected: `localization inventory and UI text sinks OK`

- [ ] **Step 2: Implement bind + tick + settings + hostSurface reset**

In `tickPlayHudLens`, after blocks arrive, if tracker surface: find or skip `play_hud_tracker`; set each child alpha `1f`/`0.25f` from bits. If `!showsTracker`, `visibility = GONE`.

- [ ] **Step 3: Run**

Run: `rg -n "WRITE_CORE_RAM" app/src && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.TrackerCatalogTest' --tests 'com.visorcraft.ghostgalleon.ui.HostSurfaceTest'`

Expected: no WRITE; PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: paint opt-in RAM tracker widgets on the play host"
```

---

## Phase 2 — State cinema

Done when: slots cycle 9–12 only; capture is interval-gated and refuses yield / disabled slots; filmstrip tap loads that slot via existing `enqueueRaUdp`; user slots 1–8 untouched.

### Task 5: CinemaPolicy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/CinemaPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/CinemaPolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class CinemaFrame(val slot: Int, val savedAtMs: Long, val thumbKey: String?)

object CinemaPolicy {
    val USER_SLOTS: IntRange = 1..8
    val BAND: IntRange = 9..12
    const val DEFAULT_INTERVAL_MS = 60_000L
    const val MIN_INTERVAL_MS = 15_000L

    fun nextSlot(lastSlot: Int?): Int
    fun inBand(slot: Int): Boolean
    fun shouldCapture(
        enabled: Boolean,
        playHostAllowed: Boolean,
        raPlayer: Boolean,
        slotsLive: Boolean,
        lastCaptureMs: Long,
        nowMs: Long,
        intervalMs: Long,
    ): Boolean
    fun clampInterval(ms: Long): Long
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaPolicyTest {

    @Test
    fun `nextSlot walks the reserved band only`() {
        assertEquals(9, CinemaPolicy.nextSlot(null))
        assertEquals(10, CinemaPolicy.nextSlot(9))
        assertEquals(11, CinemaPolicy.nextSlot(10))
        assertEquals(12, CinemaPolicy.nextSlot(11))
        assertEquals(9, CinemaPolicy.nextSlot(12))
        assertEquals(9, CinemaPolicy.nextSlot(3))
        assertTrue(CinemaPolicy.inBand(9))
        assertFalse(CinemaPolicy.inBand(8))
    }

    @Test
    fun `capture stays off when the host or slots are dead`() {
        assertTrue(
            CinemaPolicy.shouldCapture(true, true, true, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(false, true, true, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, false, true, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, true, false, true, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, true, true, false, 0L, 60_000L, 60_000L),
        )
        assertFalse(
            CinemaPolicy.shouldCapture(true, true, true, true, 10_000L, 20_000L, 60_000L),
        )
        assertEquals(15_000L, CinemaPolicy.clampInterval(100L))
        assertEquals(300_000L, CinemaPolicy.clampInterval(999_999L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.CinemaPolicyTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.rom

data class CinemaFrame(val slot: Int, val savedAtMs: Long, val thumbKey: String?)

object CinemaPolicy {
    val USER_SLOTS: IntRange = 1..8
    val BAND: IntRange = 9..12
    const val DEFAULT_INTERVAL_MS = 60_000L
    const val MIN_INTERVAL_MS = 15_000L
    const val MAX_INTERVAL_MS = 300_000L

    fun nextSlot(lastSlot: Int?): Int {
        if (lastSlot == null || lastSlot !in BAND) return BAND.first
        return if (lastSlot >= BAND.last) BAND.first else lastSlot + 1
    }

    fun inBand(slot: Int): Boolean = slot in BAND

    fun clampInterval(ms: Long): Long = ms.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)

    fun shouldCapture(
        enabled: Boolean,
        playHostAllowed: Boolean,
        raPlayer: Boolean,
        slotsLive: Boolean,
        lastCaptureMs: Long,
        nowMs: Long,
        intervalMs: Long,
    ): Boolean {
        if (!enabled || !playHostAllowed || !raPlayer || !slotsLive) return false
        val wait = clampInterval(intervalMs)
        if (lastCaptureMs <= 0L) return true
        return nowMs - lastCaptureMs >= wait
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/CinemaPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/CinemaPolicyTest.kt
git commit -m "feat: reserve RetroArch slots 9-12 for state cinema"
```

### Task 6: Cinema HUD + Settings

**Files:**
- Modify: `GhostGalleonApp.kt` — `var cinemaFrames: List<CinemaFrame> = emptyList()`, `var cinemaLastSlot: Int? = null`, `var cinemaLastCaptureMs: Long = 0L`, `var cinemaPinnedSlot: Int? = null`; clear on `clearSessionSurface` / yield
- Modify: `CompanionPanel.kt` + `tickPlayHudClock` — if `raCinemaEnabled && playHostAllowed && SessionHandoff.isRaPlayer && HostSurfacePolicy.showsCinema && client.slotStripAllowed()`, and `CinemaPolicy.shouldCapture(...)`, `enqueueRaUdp { saveStateSlot(port, next) }` then append frame (cap 4). Tag `play_hud_cinema`: 4 chips. Tap → `loadStateSlot`. Long-press → `cinemaPinnedSlot = slot`. Do not claim HOST
- Settings → Library under Talk to RetroArch: **State cinema** + interval is the stored `raCinemaIntervalMs` (no extra slider required in v1)
- Strings: `settings_ra_cinema`, `play_hud_cinema`

**Interfaces:**
- Consumes: `CinemaPolicy`, existing `RaCommandClient.saveStateSlot` / `loadStateSlot`

If slot strip is process-disabled, hide cinema. Skip capture when UDP busy.

- [ ] **Step 1: i18n + audit**

```xml
    <string name="settings_ra_cinema">State cinema</string>
    <string name="play_hud_cinema">Cinema</string>
```

All five catalogs, then `python3 scripts/i18n_audit.py --write && --check`.

- [ ] **Step 2: Implement ticker + strip + reset**

Thumbs: reuse KEEP play-surface SAF look (`<stem>.stateN.png`). Miss → numbered chip.

- [ ] **Step 3: Run**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.CinemaPolicyTest' --tests 'com.visorcraft.ghostgalleon.rom.RaCommandTest'`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/CompanionActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: auto-ring reserved savestate slots on the play host"
```

---

## Phase 3 — Achievement theater

Done when: malformed RA JSON is empty; next locked skips unlocked; unlock diff is host-tested; KEEP HUD polls ≤1/min; no SETTINGS rebuild.

### Task 7: RaTheater parse

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/library/RaTheater.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/library/RaTheaterTest.kt`

**Interfaces:**
- Consumes: `RaProgress` / `RetroAchievements.parseProgress`
- Produces:

```kotlin
data class RaCheevo(
    val id: Int,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val badgeName: String?,
)

data class RaTheaterSnap(
    val progress: RaProgress,
    val nextLocked: RaCheevo?,
    val lastUnlock: RaCheevo?,
    val unlockedIds: Set<Int>,
)

object RaTheater {
    fun parse(json: String?): RaTheaterSnap
    fun nextLocked(items: List<RaCheevo>): RaCheevo?
    fun newlyUnlocked(prev: Set<Int>, next: Set<Int>): List<Int>
    fun pollDue(lastMs: Long, nowMs: Long, intervalMs: Long): Boolean
}
```

Parse `Achievements` as object-of-objects or array. `DateEarned` / `DateEarnedHardcore` non-blank ⇒ unlocked. Never throw.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaTheaterTest {

    private val fixture = """
      {
        "ID": 1, "Title": "Game", "NumAwardedToUser": 1, "NumAchievements": 2,
        "Achievements": {
          "10": {"ID":10,"Title":"First","Description":"d","Points":5,"DateEarned":"2020-01-01","BadgeName":"001"},
          "11": {"ID":11,"Title":"Second","Description":"e","Points":10,"DateEarned":"","BadgeName":"002"}
        }
      }
    """.trimIndent()

    @Test
    fun `parse next locked and unlock diff`() {
        val snap = RaTheater.parse(fixture)
        assertEquals(1, snap.progress.numAwarded)
        assertEquals(2, snap.progress.numPossible)
        assertEquals("Second", snap.nextLocked?.title)
        assertEquals(setOf(10), snap.unlockedIds)
        assertEquals(listOf(11), RaTheater.newlyUnlocked(setOf(10), setOf(10, 11)))
        assertTrue(RaTheater.newlyUnlocked(setOf(10), setOf(10)).isEmpty())
        assertTrue(RaTheater.pollDue(0L, 60_000L, 60_000L))
        assertFalse(RaTheater.pollDue(10_000L, 20_000L, 60_000L))
    }

    @Test
    fun `malformed json is empty`() {
        val empty = RaTheater.parse("{ not json")
        assertTrue(empty.progress.isEmpty)
        assertNull(empty.nextLocked)
        assertTrue(empty.unlockedIds.isEmpty())
        assertTrue(RaTheater.parse(null).progress.isEmpty)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.library.RaTheaterTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

Use `RetroAchievements.parseProgress(json)` for the header. Walk `Achievements` keys; skip id≤0. `nextLocked` = first `!unlocked` in iteration order. `lastUnlock` = last unlocked in iteration order. `pollDue`: `lastMs<=0 || nowMs-lastMs >= intervalMs.coerceAtLeast(30_000)`.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/library/RaTheater.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/library/RaTheaterTest.kt
git commit -m "feat: parse RetroAchievements theater snaps without throwing"
```

### Task 8: Theater HUD + Settings

**Files:**
- Modify: `GhostGalleonApp.kt` — process `theaterSnap`, `theaterLastPollMs`, `theaterAttempted` (reuse RA in-flight if present)
- Modify: `RaFetcher.fetchProgress` callers **or** fetch the same URL and pass raw JSON into `RaTheater.parse` on `ROM_IO` / existing RA executor. One attempt per romId per process until `RaTheater.pollDue`
- Modify: `CompanionPanel` — tag `play_hud_theater` when `raTheaterEnabled && playHostAllowed && credentials && HostSurfacePolicy.showsTheater`. In-place `setText` for `awarded/possible`, next locked, 4s unlock ticker
- If awarded count changes: existing `RaProgressGate.notifyAfterStore` → `SELECTION_ONLY` only. Never `notifyChanged`
- Settings → Library near RA credentials: **Achievement theater**
- Strings: `settings_ra_theater`, `play_hud_theater`

Talk to RetroArch is **not** required.

- [ ] **Step 1: i18n + audit** (`settings_ra_theater`, `play_hud_theater`)

- [ ] **Step 2: Implement poll + paint**

Hide on yield. Badge art optional via `ArtCache`; failure → letter.

- [ ] **Step 3: Run**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.library.RaTheaterTest' --tests 'com.visorcraft.ghostgalleon.library.RaProgressGateTest'`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/library/RaFetcher.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: poll live RetroAchievements theater on the play host"
```

---

## Phase 4 — Second seat

Done when: seat is false on yield / !assist / !RA / cockpit; gestures target launch display only; P1 focus lock stays GAME; default-off.

### Task 9: SecondSeatPolicy + mayInjectSeat

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/input/SecondSeatPolicy.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/input/SecondSeatPolicyTest.kt`
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/input/InputAssistPolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
object SecondSeatPolicy {
    val DEFAULT_ANCHORS: List<SeatAnchor>
    fun allowed(
        dualMode: Boolean,
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        assistConnected: Boolean,
        seatEnabled: Boolean,
        playerIsRa: Boolean,
        cockpit: Boolean,
    ): Boolean
    fun anchorsOrDefault(stored: List<SeatAnchor>): List<SeatAnchor>
    fun point(anchor: SeatAnchor, widthPx: Int, heightPx: Int): Pair<Float, Float>
}

// InputAssistPolicy
fun mayInjectSeat(
    assistConnected: Boolean,
    playHostAllowed: Boolean,
    sessionOwnsCompanion: Boolean,
    playerIsRa: Boolean,
    seatEnabled: Boolean,
): Boolean
```

Default anchors: SNES-like cluster in lower-right 40% (`up/down/left/right/a/b/x/y/start/select`).

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.visorcraft.ghostgalleon.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecondSeatPolicyTest {

    @Test
    fun `seat only for dual KEEP RA with assist`() {
        assertTrue(
            SecondSeatPolicy.allowed(true, true, false, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(false, true, false, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, false, false, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, true, true, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, false, true, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, true, false, true, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, true, true, false, false),
        )
        assertFalse(
            SecondSeatPolicy.allowed(true, true, false, true, true, true, true),
        )
    }

    @Test
    fun `empty stored anchors fall back and stay normalized`() {
        val d = SecondSeatPolicy.anchorsOrDefault(emptyList())
        assertTrue(d.any { it.id == "a" })
        val custom = listOf(SeatAnchor("a", 1.5f, -0.2f))
        val one = SecondSeatPolicy.anchorsOrDefault(custom)
        assertEquals(1, one.size)
        val (x, y) = SecondSeatPolicy.point(SeatAnchor("a", 0.5f, 0.25f), 200, 100)
        assertEquals(100f, x)
        assertEquals(25f, y)
    }
}
```

Add to `InputAssistPolicyTest`:

```kotlin
    @Test
    fun `seat inject only for KEEP RA play host`() {
        assertTrue(InputAssistPolicy.mayInjectSeat(true, true, false, true, true))
        assertFalse(InputAssistPolicy.mayInjectSeat(true, true, true, true, true))
        assertFalse(InputAssistPolicy.mayInjectSeat(false, true, false, true, true))
        assertFalse(InputAssistPolicy.mayInjectSeat(true, false, false, true, true))
        assertFalse(InputAssistPolicy.mayInjectSeat(true, true, false, false, true))
        assertFalse(InputAssistPolicy.mayInjectSeat(true, true, false, true, false))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.SecondSeatPolicyTest' --tests 'com.visorcraft.ghostgalleon.input.InputAssistPolicyTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

`allowed` is the AND of every flag. `anchorsOrDefault` returns `stored` if non-empty else `DEFAULT_ANCHORS`. `point` = `nx * width` / `ny * height`. `mayInjectSeat` = assist && playHost && !yield && playerIsRa && seatEnabled.

- [ ] **Step 4: Run tests to verify they pass**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/input/SecondSeatPolicy.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/input/SecondSeatPolicyTest.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/input/InputAssistPolicyTest.kt
git commit -m "feat: gate second-seat inject to KEEP RetroArch with assist"
```

### Task 10: Seat chrome, assist taps, Settings

**Files:**
- Modify: `settings/Action.kt` + `ActionLabelTest` — `TOGGLE_SEAT` (default unmapped)
- Modify: `ControllerLabActivity.kt` / remap lists if explicit
- Modify: `CompanionPanel.kt` — chip **Seat** sets `app.hostSurface = SEAT` (not HOST). Body: 10 buttons. Down/up → `app.injectSeat(id, down)` 
- Modify: `GhostGalleonApp.kt` — `injectSeat` looks up anchor, maps through `SecondSeatPolicy.point` using launch display bounds already used by cockpit, calls existing assist gesture entry (extend `injectPointer` or add `injectSeatTap(x, y, down)`). Gate with `mayInjectSeat`. Fail closed if `setDisplayId` fails (same as cockpit)
- Modify: `InputAssistService.kt` — reuse `dispatchGesture` on launch display
- Settings → Controls: **Second seat** toggle + simple list/reset of anchors (reset writes `emptyList()` → defaults). Full drag editor can be a later polish; v1 reset + toggle is enough if a comment documents default cluster. Prefer a 2-column list of id + nx/ny text fields if cheap
- Strings: `action_toggle_seat`, `settings_second_seat`, `settings_second_seat_layout`, `seat_need_assist`, `play_hud_seat`, `play_hud_back_to_hud`

Do **not** claim HOST on seat touches.

- [ ] **Step 1: Failing ActionLabelTest + i18n**

```kotlin
    @Test
    fun `toggle seat has a user-friendly label`() {
        assertEquals(text(R.string.action_toggle_seat), Action.TOGGLE_SEAT.label())
    }
```

All five catalogs. `python3 scripts/i18n_audit.py --write && --check`.

- [ ] **Step 2: Run ActionLabelTest to verify fail, then implement chrome + inject**

Without assist: body is `seat_need_assist` only. Yield: hide chip. Cockpit: hide chip (`HostSurfacePolicy.seatAllowed`).

- [ ] **Step 3: Run**

Run: `rg -n "INJECT_EVENTS|injectInputEvent" app/src && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.SecondSeatPolicyTest' --tests 'com.visorcraft.ghostgalleon.settings.ActionLabelTest'`

Expected: no injectInputEvent; PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/settings/Action.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/settings/ActionLabelTest.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/ControllerLabActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistService.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: second-seat touch cluster on the KEEP play host"
```

---

## Phase 5 — Save ferry

Done when: same hash (or group) + same RA player produces an offer; different player refuses; copy stays inside granted names; YIELD destination refused; `RomEntry.id` unchanged.

### Task 11: SaveFerry policy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/SaveFerry.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/SaveFerryTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class FerryKind { RA_SRM, RA_STATE }
enum class FerryRefuse { NONE, NOT_READY, DIFFERENT_TITLE, DIFFERENT_PLAYER, YIELD_DEST }

data class SaveDoc(val uri: String, val name: String)

data class FerryOffer(
    val fromRomId: String,
    val toRomId: String,
    val kind: FerryKind,
    val fromUri: String,
    val toName: String,
    val slot: Int?,
)

object SaveFerry {
    fun sameTitle(a: RomIdentity?, b: RomIdentity?): Boolean
    fun samePlayerHint(fromPlayer: String?, toPlayer: String?): Boolean
    fun classifyName(name: String, stem: String): Pair<FerryKind, Int?>?
    fun refuse(
        fromId: RomIdentity?,
        toId: RomIdentity?,
        fromPlayer: String?,
        toPlayer: String?,
        destIsOpenYield: Boolean,
    ): FerryRefuse
    fun offers(
        from: RomEntry,
        to: RomEntry,
        fromDocs: List<SaveDoc>,
        refuse: FerryRefuse,
    ): List<FerryOffer>
}
```

`classifyName`: `<stem>.srm` → `RA_SRM`; `<stem>.state` → state slot 0; `<stem>.stateN` for N in 1..8 → `RA_STATE`. Slots 9–12 → null (never ferry cinema band).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFerryTest {

    private fun ident(id: String, hash: String?, ready: Boolean = true, group: String? = hash) =
        RomIdentity(id, RomIdentities.ALGO_SHA1_PAYLOAD, hash, null, group, null, ready)

    @Test
    fun `same title and RA player produce srm and user-state offers`() {
        val a = ident("snes:a.sfc", "aaa")
        val b = ident("snes:b.sfc", "aaa")
        assertEquals(FerryRefuse.NONE, SaveFerry.refuse(a, b, "ra-snes9x", "ra-snes9x", false))
        assertEquals(FerryKind.RA_SRM to null, SaveFerry.classifyName("a.srm", "a"))
        assertEquals(FerryKind.RA_STATE to 3, SaveFerry.classifyName("a.state3", "a"))
        assertNull(SaveFerry.classifyName("a.state9", "a"))
        val offers = SaveFerry.offers(
            RomEntry("snes:a.sfc", "A", "snes", "content://a", null),
            RomEntry("snes:b.sfc", "B", "snes", "content://b", null),
            listOf(SaveDoc("content://saves/a.srm", "a.srm")),
            FerryRefuse.NONE,
        )
        assertEquals(1, offers.size)
        assertEquals(FerryKind.RA_SRM, offers[0].kind)
    }

    @Test
    fun `refuse not ready different player and yield dest`() {
        assertEquals(
            FerryRefuse.NOT_READY,
            SaveFerry.refuse(ident("a", null, ready = false), ident("b", "x"), "ra-x", "ra-x", false),
        )
        assertEquals(
            FerryRefuse.DIFFERENT_TITLE,
            SaveFerry.refuse(ident("a", "aaa"), ident("b", "bbb"), "ra-x", "ra-x", false),
        )
        assertEquals(
            FerryRefuse.DIFFERENT_PLAYER,
            SaveFerry.refuse(ident("a", "aaa"), ident("b", "aaa"), "ra-x", "drastic", false),
        )
        assertEquals(
            FerryRefuse.YIELD_DEST,
            SaveFerry.refuse(ident("a", "aaa"), ident("b", "aaa"), "ra-x", "ra-x", true),
        )
        assertTrue(SaveFerry.samePlayerHint("ra-snes9x", "ra-mgba"))
        assertTrue(SaveFerry.samePlayerHint("ra-snes9x", SessionHandoff.RA_PACKAGE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SaveFerryTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

`sameTitle`: both ready and (hash equal non-blank **or** groupId equal non-blank). `samePlayerHint`: both `SessionHandoff.isRaPlayer`. `refuse` order: not ready → different title → different player → yield dest → NONE. `offers` empty when refuse != NONE.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/SaveFerry.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/SaveFerryTest.kt
git commit -m "feat: offer same-hash RetroArch save ferry without converting"
```

### Task 12: Ferry details + SAF copy

**Files:**
- Modify: `GameDeck.showDetails` / details dialog — if `saveFerryEnabled` and other entries share title, add **Saves** items. Confirm `confirm_save_ferry`. Destination YIELD open → show `ferry_refuse_player` / yield reason, no copy
- Modify: `GhostGalleonApp.kt` — `fun ferryCopy(offer: FerryOffer)` on `ROM_IO`: open granted `fromUri`; write `*.tmp` beside dest if possible else in place; toast success / `ferry_dest_unwritable`. Locate docs by listing granted RA save/state trees already used for cinema thumbs (no whole-card scan)
- Settings → Library: **Save ferry** toggle
- Strings: `settings_save_ferry`, `confirm_save_ferry`, `ferry_refuse_player`, `ferry_dest_unwritable`

Never `force-stop`. Never rewrite `RomEntry.id`.

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Implement UI + copy**

If `RomEntry` has no public `uri` field name as in the test, use the existing property (`uri` / `path`).

- [ ] **Step 3: Run**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SaveFerryTest' --tests 'com.visorcraft.ghostgalleon.library.GameDetailsTest'`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/GameDeck.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: copy RetroArch saves between same-hash library rows"
```

---

## Phase 6 — Posture theater

Done when: hinge buckets are host-tested; effects never include “set SessionPolicy”; CLOSED may pause KEEP RA once; FLAT + suggest shows the existing YIELD confirm only.

### Task 13: PosturePolicy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/display/PosturePolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/display/PosturePolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class DevicePosture { UNKNOWN, CLOSED, TABLETOP, BOOK, FLAT }
enum class PostureEffect { NONE, PAUSE_IF_PLAYING, SHOW_YIELD_CHIP, HIDE_YIELD_CHIP }

object PosturePolicy {
    fun fromSensors(hingeDeg: Float?, deviceState: Int?): DevicePosture
    fun effect(
        posture: DevicePosture,
        previous: DevicePosture,
        dualMode: Boolean,
        sessionOwnsCompanion: Boolean,
        keepRaPlaying: Boolean,
        suggestYieldEnabled: Boolean,
        postureAware: Boolean,
    ): PostureEffect
}
```

Buckets: `<15 CLOSED`, `15–140 TABLETOP`, `140–170 BOOK`, `≥170 FLAT`, NaN/null → UNKNOWN (unless a future deviceState map is added; v1 ignores unknown OEM ids).

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.display

import org.junit.Assert.assertEquals
import org.junit.Test

class PosturePolicyTest {

    @Test
    fun `hinge buckets`() {
        assertEquals(DevicePosture.UNKNOWN, PosturePolicy.fromSensors(null, null))
        assertEquals(DevicePosture.CLOSED, PosturePolicy.fromSensors(5f, null))
        assertEquals(DevicePosture.TABLETOP, PosturePolicy.fromSensors(90f, null))
        assertEquals(DevicePosture.BOOK, PosturePolicy.fromSensors(155f, null))
        assertEquals(DevicePosture.FLAT, PosturePolicy.fromSensors(180f, null))
    }

    @Test
    fun `effects never set a session policy`() {
        assertEquals(
            PostureEffect.PAUSE_IF_PLAYING,
            PosturePolicy.effect(
                DevicePosture.CLOSED, DevicePosture.BOOK,
                true, false, true, false, true,
            ),
        )
        assertEquals(
            PostureEffect.NONE,
            PosturePolicy.effect(
                DevicePosture.FLAT, DevicePosture.BOOK,
                true, false, true, false, true,
            ),
        )
        assertEquals(
            PostureEffect.SHOW_YIELD_CHIP,
            PosturePolicy.effect(
                DevicePosture.FLAT, DevicePosture.BOOK,
                true, false, true, true, true,
            ),
        )
        assertEquals(
            PostureEffect.NONE,
            PosturePolicy.effect(
                DevicePosture.FLAT, DevicePosture.BOOK,
                true, true, true, true, true,
            ),
        )
        assertEquals(
            PostureEffect.HIDE_YIELD_CHIP,
            PosturePolicy.effect(
                DevicePosture.BOOK, DevicePosture.FLAT,
                true, false, true, true, true,
            ),
        )
        assertEquals(
            PostureEffect.NONE,
            PosturePolicy.effect(
                DevicePosture.CLOSED, DevicePosture.BOOK,
                true, false, true, false, false,
            ),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.display.PosturePolicyTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

Edge-trigger: `posture != previous`. CLOSED + postureAware + !yield + keepRaPlaying → PAUSE. FLAT + suggest + !yield + dual → SHOW. Leave FLAT → HIDE. Else NONE. No policy-write effect exists.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/display/PosturePolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/display/PosturePolicyTest.kt
git commit -m "feat: map hinge angle to pause and yield-chip effects"
```

### Task 14: Posture listener + Settings

**Files:**
- Modify: `GhostGalleonApp.kt` or `BaseDeckActivity` — register `Sensor.TYPE_HINGE_ANGLE` when present (int 36 on API 30+). Missing sensor → stay UNKNOWN. No `DeviceStateManager` OEM ids in v1
- On change, `PosturePolicy.effect` → `PAUSE_IF_PLAYING` enqueues `GET_STATUS` then `PAUSE_TOGGLE` if PLAYING (share `enqueueRaUdp`); `SHOW_YIELD_CHIP` in-place on play host; chip click opens existing owned-surface YIELD confirm (`confirm_yield_on_keep_player`); `HIDE` gone
- Never `beginSession` / never assign `sessionSurface.policy` from the sensor
- Settings → Display & Grid: **Posture** + **Suggest both screens when flat** (hidden on SINGLE)
- Strings: `settings_posture`, `settings_posture_suggest_yield`, `posture_use_both_screens`

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Implement listener + chip**

Log `GGPosture posture=… effect=…`. Yield open → do not pause via this path (session already owns both).

- [ ] **Step 3: Run**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.display.PosturePolicyTest' --tests 'com.visorcraft.ghostgalleon.rom.StagePlotTest'`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: pause KEEP RetroArch on lid close without auto-yield"
```

---

## Phase 7 — Helper embed

Done when: session package / yield / missing embed cannot start a third task; helper is exclusive; Back to HUD releases.

### Task 15: HelperEmbedPolicy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/ui/HelperEmbedPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/ui/HelperEmbedPolicyTest.kt`

**Interfaces:**
- Consumes: `CompanionRoleResolve.pinConflictsWithSession` idea (package == session package)
- Produces:

```kotlin
object HelperEmbedPolicy {
    fun resolvePackage(romId: String?, romHelpers: Map<String, String>, global: String?): String?
    fun mayEmbed(
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        helperPackage: String?,
        sessionPackage: String?,
        embedAvailable: Boolean,
        cockpit: Boolean,
    ): Boolean
    fun mayLaunchOnHostDisplay(): Boolean = false
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HelperEmbedPolicyTest {

    @Test
    fun `rom helper wins then global`() {
        assertEquals(
            "org.wiki",
            HelperEmbedPolicy.resolvePackage("snes:a", mapOf("snes:a" to "org.wiki"), "org.maps"),
        )
        assertEquals("org.maps", HelperEmbedPolicy.resolvePackage("snes:a", emptyMap(), "org.maps"))
        assertNull(HelperEmbedPolicy.resolvePackage("snes:a", emptyMap(), null))
    }

    @Test
    fun `embed refuses session package yield and missing api`() {
        assertTrue(
            HelperEmbedPolicy.mayEmbed(true, false, "org.wiki", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, false, "com.retroarch.aarch64", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, true, "org.wiki", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(false, false, "org.wiki", "com.retroarch.aarch64", true, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, false, "org.wiki", "com.retroarch.aarch64", false, false),
        )
        assertFalse(
            HelperEmbedPolicy.mayEmbed(true, false, "org.wiki", "com.retroarch.aarch64", true, true),
        )
        assertFalse(HelperEmbedPolicy.mayLaunchOnHostDisplay())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.HelperEmbedPolicyTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

`resolvePackage`: rom map then global; blank → null. `mayEmbed`: all flags AND helper != sessionPackage. `mayLaunchOnHostDisplay` always false.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/HelperEmbedPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/ui/HelperEmbedPolicyTest.kt
git commit -m "feat: fail-closed helper embed that never hosts the session package"
```

### Task 16: Helper HUD + Settings

**Files:**
- Modify: `CompanionPanel.kt` — chip **Helper** when `mayEmbed` (or when package set but embed missing → disabled + `helper_embed_unavailable`). Sets `hostSurface = HELPER`, `ActivityEmbed.attach(helperHost, …)`. **Back to HUD** → `ActivityEmbed.release`, `hostSurface = HUD`
- Yield / `clearSessionSurface`: release
- Settings → Apps: **Play-host helper** (existing app picker). Details: per-ROM helper (`romHelpers`)
- Filter picker with `pinConflictsWithSession` + refuse `me.magnum.melondualds` / `org.azahar_emu.azahar`
- Strings: `settings_play_host_helper`, `helper_embed_unavailable`, `play_hud_helper`

No `setLaunchDisplayId` fallback.

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Implement attach/release**

One toast if `attach` returns false.

- [ ] **Step 3: Run**

Run: `rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.HelperEmbedPolicyTest' --tests 'com.visorcraft.ghostgalleon.settings.CompanionRoleTest'`

Expected: overlays still gone; PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/GameDeck.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: embed a non-session helper app on the KEEP play host"
```

---

## Phase 8 — Warm Continue

Done when: probe is idle-only and rate-limited; autoload is Continue + opt-in + slotted; switcher / grid launch never loads; no emulator is started in the background.

### Task 17: WarmResumePolicy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/WarmResumePolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/WarmResumePolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class LaunchReason { CONTINUE, SLOT, SWITCHER, OTHER }

object WarmResumePolicy {
    const val PROBE_GAP_MS = 60_000L
    const val LOAD_BUDGET_MS = 400L

    fun mayProbe(
        warmEnabled: Boolean,
        sessionOpen: Boolean,
        continueKey: String?,
        playerIsRa: Boolean,
        raNetworkCommands: Boolean,
        lastProbeMs: Long,
        nowMs: Long,
    ): Boolean

    fun mayAutoload(
        warmLoadEnabled: Boolean,
        reason: LaunchReason,
        playerIsRa: Boolean,
        slot: Int?,
        sessionOwnsCompanion: Boolean,
    ): Boolean

    fun loadSlot(pinned: Int?, lastCinema: Int?, lastUser: Int?): Int?
}
```

`loadSlot` prefers pinned, then last cinema frame, then last user slot (1–8). Null if none.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmResumePolicyTest {

    @Test
    fun `probe only when idle RA continue is due`() {
        assertTrue(
            WarmResumePolicy.mayProbe(true, false, "rom:a", true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, true, "rom:a", true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(false, false, "rom:a", true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, null, true, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, "rom:a", false, true, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, "rom:a", true, false, 0L, 60_000L),
        )
        assertFalse(
            WarmResumePolicy.mayProbe(true, false, "rom:a", true, true, 10_000L, 20_000L),
        )
    }

    @Test
    fun `autoload only for continue with a slot`() {
        assertTrue(
            WarmResumePolicy.mayAutoload(true, LaunchReason.CONTINUE, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.SWITCHER, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.SLOT, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(false, LaunchReason.CONTINUE, true, 10, false),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.CONTINUE, true, 10, true),
        )
        assertFalse(
            WarmResumePolicy.mayAutoload(true, LaunchReason.CONTINUE, true, null, false),
        )
        assertEquals(10, WarmResumePolicy.loadSlot(10, 11, 1))
        assertEquals(11, WarmResumePolicy.loadSlot(null, 11, 1))
        assertEquals(1, WarmResumePolicy.loadSlot(null, null, 1))
        assertNull(WarmResumePolicy.loadSlot(null, null, null))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.WarmResumePolicyTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

Direct translations of the tables above.

- [ ] **Step 4: Run test to verify it passes**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/WarmResumePolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/WarmResumePolicyTest.kt
git commit -m "feat: warm Continue probe and optional slot load policy"
```

### Task 18: Warm probe + Continue load + Settings

**Files:**
- Modify: `Deck.kt` `launchSlotKey` — add `reason: LaunchReason = LaunchReason.OTHER`. Resume / Continue chip passes `CONTINUE`. Session switcher passes `SWITCHER`. Grid / search stay `SLOT` or `OTHER`
- Modify: `GhostGalleonApp.beginSession` — if `WarmResumePolicy.mayAutoload(...)`, post delayed `LOAD_BUDGET_MS` `enqueueRaUdp { loadStateSlot(port, slot) }` once. Store `lastLaunchReason` process-only from `launchSlotKey` via `app.noteLaunchReason(reason)` immediately before `noteLaunch`
- Idle: when `liveDeckCount` goes 0→1 or after rom index ready, if `mayProbe`, one `VERSION`/`GET_STATUS`. Record `warmLastProbeMs`
- Optional: `ArtCache` prefetch Continue only (existing API). Skip if already warm
- Settings → Library: **Warm Continue** + nested **Load last cinema slot on Continue** (`warmResumeLoad`)
- Strings: `settings_warm_resume`, `settings_warm_resume_load`

Do **not** `startActivity` RA before A. Do **not** autoload switcher destinations (handoff rule stands).

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Implement reason plumbing + probe + delayed load**

If `beginSession` policy is YIELD, skip load even if reason is CONTINUE.

- [ ] **Step 3: Run**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.WarmResumePolicyTest' --tests 'com.visorcraft.ghostgalleon.rom.SessionHandoffTest' --tests 'com.visorcraft.ghostgalleon.ui.PlayHostPolicyTest'`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/Deck.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: probe RetroArch and optionally load Continue cinema slot"
```

### Task 19: Final host verify + spec pointer

**Files:**
- Modify: `docs/play-host-depth.md` — replace “plan not written yet” with this file’s path
- No product code unless a verify command fails

- [ ] **Step 1: Run the spec verify block**

```bash
rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src
rg -n "WRITE_CORE_RAM" app/src
rg -n "INJECT_EVENTS|injectInputEvent" app/src
rg -n "HostSurface|CinemaPolicy|RaTheater|SecondSeat|SaveFerry|PosturePolicy|HelperEmbed|WarmResume" app/src/main/java
python3 scripts/i18n_audit.py --check
./gradlew :app:testDebugUnitTest --offline
```

Expected: first three rg empty (or only comments/docs); symbols present; i18n OK; full host suite green.

- [ ] **Step 2: Point the spec at this plan**

In `docs/play-host-depth.md` last section:

```markdown
Task-by-task implementation plan (all phases):
[`superpowers/plans/2026-08-13-play-host-depth.md`](superpowers/plans/2026-08-13-play-host-depth.md).
```

- [ ] **Step 3: Commit**

```bash
git add docs/play-host-depth.md
git commit -m "docs: point play-host depth spec at the implementation plan"
```

Do **not** claim the Sugar device matrix from host green.

---

## Self-review (coverage)

| Spec section | Task |
|---|---|
| Exclusive `HostSurface` | 1 |
| Schema v11 fields | 2 |
| Tracker widgets + 256-byte budget | 3, 4 |
| Cinema band 9–12, interval, tap load | 5, 6 |
| Theater parse / poll / no SETTINGS | 7, 8 |
| Seat assist + launch display only | 9, 10 |
| Ferry same-hash, no convert, no cinema slots | 11, 12 |
| Posture pause / never auto-YIELD | 13, 14 |
| Helper embed fail closed | 15, 16 |
| Warm probe + Continue-only load | 17, 18 |
| Verify / overlays / WRITE / inject | 19 |
| YIELD / greedy / SINGLE no-ops | gates in 4, 6, 8, 10, 14, 16, 18 |
| One UDP flight | 4, 6, 14, 18 reuse `enqueueRaUdp` |
| No pre-launch emulator | 18 explicit |

Placeholder scan: no TBD. Types (`HostSurface`, `CinemaFrame`, `LaunchReason`, `FerryKind`, `DevicePosture`, `SeatAnchor`) are defined in the task that first produces them.
