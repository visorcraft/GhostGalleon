# KEEP play surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a KEEP session, Ghost Galleon turns the panel it still owns into a live play HUD with a session switcher, a black-panel pixel oracle, and an opt-in RetroArch command link — without ever painting, copying, or commanding a yielded DS/3DS panel.

**Architecture:** Pure host-testable policy (`PlayHostPolicy`, `SessionRing`, `SessionSwitch`, `OracleTally`, `RaCommand`) sits beside shipped `SessionPolicy` / `SessionSurface`. `CompanionPanel` binds HUD views only when `playHostAllowed`. Schema v9 persists the ring and two toggles. `PixelCopy` and UDP stay out of policy types.

**Tech Stack:** Kotlin, classic Views, host JUnit (`./gradlew :app:testDebugUnitTest --offline`), no new dependencies, no Compose.

**Spec:** [`docs/keep-play-surface.md`](../../keep-play-surface.md)

## Global Constraints

- Dual-surface games **keep both screens**. Never steal a panel from melonDualDS or Azahar.
- No play HUD, switcher chrome, `PixelCopy`, or RetroArch UDP while `sessionOwnsCompanionDisplay` (YIELD_BOTH or greedy KEEP).
- HUD / switcher / oracle only on a Ghost Galleon activity whose `displayId` ≠ `sessionSurface.launchDisplayId`.
- No `SYSTEM_ALERT_WINDOW` / overlay windows. HUD is a view inside an existing GG activity.
- Do not inject keys into melonDualDS / Azahar.
- Do not replace Android Recents or patch Quickstep.
- Do not `ActivityEmbed` the KEEP game onto the play host.
- Do not rewrite RetroArch cfg without the Settings opt-in. No `MANAGE_EXTERNAL_STORAGE`.
- Display ids from `DisplayTopology` / `sessionSurface.launchDisplayId` — never hard-code `0`/`1`.
- Android `Display` types stay out of `PlayHostPolicy` / `SessionRing` / `SessionSwitch` / `OracleTally` / `RaCommand`.
- `OpenSession` stays playtime-only. `SessionSurface` stays the policy record. No third clock.
- New user-facing strings go in all five catalogs (`values`, `values-es`, `values-de`, `values-fr`, `values-th`) then `python3 scripts/i18n_audit.py --write && --check`.
- Commits: Conventional Commits, human author only, no AI attribution.
- Device claims for every phase require the Sugar matrix in the spec. Host tests alone are not enough.
- SINGLE topology: HUD / switcher / oracle are no-ops.

## File map

| File | Role |
|---|---|
| `ui/PlayHostPolicy.kt` | `playHostAllowed` + `oracleMaySample` |
| `rom/SessionRing.kt` | Cap-8 ring, dedupe, JSON, `SessionRingEntry` |
| `rom/SessionSwitch.kt` | Pure switch-to decision |
| `rom/OracleTally.kt` | Miss counter / heal trigger |
| `rom/RaCommand.kt` | UDP encode/decode + status parse |
| `rom/RaCfg.kt` | Opt-in `network_cmd_*` cfg mutate (no Android) |
| `settings/Action.kt` | `OPEN_SESSION_SWITCHER`, `TOGGLE_PLAY_HUD` |
| `settings/Settings.kt` | Schema v9 fields |
| `settings/SettingsStore.kt` | Persist v9 |
| `GhostGalleonApp.kt` | Hold ring; push on `beginSession` |
| `ui/deck/CompanionPanel.kt` | Bind play HUD / switcher overlay |
| `ui/deck/SessionSwitcherView.kt` | Switcher list (Views) |
| `ui/BaseDeckActivity.kt` | New Actions |
| `ui/CompanionActivity.kt` | Clock tick, oracle schedule, displayId |
| `ui/MainActivity.kt` | Oracle heal reason `oracle-black`; reclaim |
| `ui/settings/SettingsActivity.kt` | System + Library toggles |
| `docs/keep-play-surface.md` | Mark phases implemented |
| Tests listed per task | Host-only, Robolectric-free |

---

## Phase 1 — Play host

Done when: host tests prove HUD is allowed only for dual + KEEP + not greedy + host id ≠ launch id; Actions have real labels; KEEP companion binds play HUD chrome (no RA chips, no switcher yet); clock ticks without SETTINGS; yield still paints nothing extra.

### Task 1: PlayHostPolicy.playHostAllowed

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/ui/PlayHostPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/ui/PlayHostPolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
object PlayHostPolicy {
    fun playHostAllowed(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        hostDisplayId: Int?,
        launchDisplayId: Int?,
    ): Boolean
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHostPolicyTest {

    @Test
    fun `keep on a different display is allowed`() {
        assertTrue(
            PlayHostPolicy.playHostAllowed(
                dualMode = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = false,
                hostDisplayId = 1,
                launchDisplayId = 0,
            ),
        )
    }

    @Test
    fun `denied when same display yield greedy single or missing ids`() {
        val keep = SessionPolicy.KEEP_COMPANION
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, false, 0, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(
                true, SessionPolicy.YIELD_BOTH, false, 1, 0,
            ),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, true, 1, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(false, keep, false, 1, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, false, null, 0),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, keep, false, 1, null),
        )
        assertFalse(
            PlayHostPolicy.playHostAllowed(true, null, false, 1, 0),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.PlayHostPolicyTest'`

Expected: compile fail (`Unresolved reference: PlayHostPolicy`)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.ui

import com.visorcraft.ghostgalleon.rom.SessionPolicy

object PlayHostPolicy {
    fun playHostAllowed(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        hostDisplayId: Int?,
        launchDisplayId: Int?,
    ): Boolean {
        if (!dualMode) return false
        if (policy != SessionPolicy.KEEP_COMPANION) return false
        if (greedy) return false
        if (hostDisplayId == null || launchDisplayId == null) return false
        return hostDisplayId != launchDisplayId
    }
}
```

- [ ] **Step 4: Re-run tests**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/PlayHostPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/ui/PlayHostPolicyTest.kt
git commit -m "feat: gate KEEP play HUD on owned display"
```

### Task 2: Actions + i18n labels

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/settings/Action.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt` (`remappable` list)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/ControllerLabActivity.kt` (action list near line 130)
- Modify: `app/src/main/res/values/strings.xml` and `values-es`, `values-de`, `values-fr`, `values-th`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/settings/ActionLabelTest.kt`

**Interfaces:**
- Produces: `Action.OPEN_SESSION_SWITCHER`, `Action.TOGGLE_PLAY_HUD`
- Default key map unchanged (both unbound)
- Labels must not be raw enum names (`ActionLabelTest`)

- [ ] **Step 1: Write failing assertions** in `ActionLabelTest`

```kotlin
@Test
fun `play surface actions have user-friendly labels`() {
    assertEquals(text(R.string.action_open_session_switcher), Action.OPEN_SESSION_SWITCHER.label())
    assertEquals(text(R.string.action_toggle_play_hud), Action.TOGGLE_PLAY_HUD.label())
}
```

- [ ] **Step 2: Run — expect compile fail** on missing enum constants / string ids

- [ ] **Step 3: Implement**

Add to `enum class Action` after `SHOW_DETAILS`:

```kotlin
    OPEN_SESSION_SWITCHER, TOGGLE_PLAY_HUD, NONE
```

(`NONE` stays last.)

Add to `Action.label()`:

```kotlin
    Action.OPEN_SESSION_SWITCHER -> R.string.action_open_session_switcher
    Action.TOGGLE_PLAY_HUD -> R.string.action_toggle_play_hud
```

English (`values/strings.xml`):

```xml
<string name="action_open_session_switcher">Session switcher</string>
<string name="action_toggle_play_hud">Play HUD</string>
<string name="play_hud_end">End session</string>
<string name="play_hud_reclaim">Reclaim HOME</string>
<string name="play_hud_switcher">Sessions</string>
```

Spanish / German / French / Thai — add the same keys (not English copies):

| key | es | de | fr | th |
|---|---|---|---|---|
| `action_open_session_switcher` | Selector de sesión | Sitzungswechsler | Sélecteur de session | สลับเซสชัน |
| `action_toggle_play_hud` | HUD de juego | Spiel-HUD | HUD de jeu | HUD เล่นเกม |
| `play_hud_end` | Terminar sesión | Sitzung beenden | Terminer la session | สิ้นสุดเซสชัน |
| `play_hud_reclaim` | Recuperar INICIO | HOME zurückholen | Récupérer l’accueil | คืนหน้า HOME |
| `play_hud_switcher` | Sesiones | Sitzungen | Sessions | เซสชัน |

Append `Action.OPEN_SESSION_SWITCHER` and `Action.TOGGLE_PLAY_HUD` to Settings `remappable` and Controller Lab lists (after `SHOW_DETAILS`).

Then:

```bash
python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check
```

- [ ] **Step 4: Re-run**

```bash
./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.ActionLabelTest'
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add session switcher and play HUD actions"
```

### Task 3: Play HUD chrome (no RA, no switcher overlay)

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/ui/PlayHostPolicyTest.kt` (policy already covered). Add `CompanionPlayHudTest` only if you extract a pure label helper; otherwise rely on `PlayHostPolicyTest` + compile.

**Interfaces:**
- Consumes: `PlayHostPolicy.playHostAllowed`, `app.sessionSurface`, `Activity.currentDisplayId()` (`display/AndroidDisplayProbe.kt`)
- Produces: when allowed, `buildNowPlayingCard` is replaced by `buildPlayHud` (same card language). Tags:
  - `play_hud` on the root column
  - `play_hud_clock` on the clock `TextView`
  - `play_hud_actions` on the actions row
- Switcher chip is **GONE** until Task 10 (tag `play_hud_switcher` still created, `visibility = GONE`)
- RA chips are not created yet
- Favorite / Open with / End / Reclaim / existing Swap stay
- End: `app.clearOpenSession()` (already clears surface via `endOpenSession`)
- Reclaim: `app.noteReturnToLauncher()` if that exists; else `app.clearOpenSession()` then `(activity as? MainActivity)?.restartCompanionPanel("return-from-keep-hud")` only when companion is missing — do not `force-stop` the game
- Do not move Companion onto `launchDisplayId`

- [ ] **Step 1: Gate the HUD**

At the top of the NOW_PLAYING / open-session banner path, compute:

```kotlin
val surface = app.sessionSurface
val hostId = (activity as? android.app.Activity)?.let {
    com.visorcraft.ghostgalleon.display.currentDisplayId(it)
}
val playHud = PlayHostPolicy.playHostAllowed(
    dualMode = app.displayConfig.mode ==
        com.visorcraft.ghostgalleon.display.SurfaceMode.DUAL,
    policy = surface?.policy,
    greedy = surface?.greedy == true,
    hostDisplayId = hostId,
    launchDisplayId = surface?.launchDisplayId,
)
```

If `playHud`, call `buildPlayHud(...)` instead of `buildNowPlayingCard`. If not, keep today’s Now Playing / hero (yield paints nothing extra because companion is already dismissed).

- [ ] **Step 2: `buildPlayHud`**

Copy the Now Playing card structure. Differences:

- Root tag `"play_hud"`
- Subtitle line: player display name from `Platforms.ALL.flatMap { it.players }.firstOrNull { it.id == surface.playerId }?.displayName` (or empty)
- Clock `TextView` tag `"play_hud_clock"` — initial text from `SessionTracker.activeElapsedMs` + existing `format_session` / `format_session_paused`
- Actions row tag `"play_hud_actions"`
- Chips: Swap (existing), End (`play_hud_end` → `clearOpenSession`), Reclaim (`play_hud_reclaim`), Favorite (`EntryActions.toggleFavorite(activity, surface.key)`), Open with (existing picker for `surface.key`)
- Switcher chip: tagged `"play_hud_switcher"`, `GONE`

Use `sessionSurface.key` for the title (library name), not `state.selectedKey`.

Do not call `notifyChanged()` from this builder.

- [ ] **Step 3: Compile**

```bash
./gradlew :app:compileDebugKotlin --offline \
  :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.PlayHostPolicyTest'
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: bind KEEP play HUD on the owned companion"
```

### Task 4: Clock tick without SETTINGS

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/CompanionActivity.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/MainActivity.kt` only if Main can be the play host (same ticker helper)

**Interfaces:**
- A `Handler` every **1000 ms** finds `play_hud_clock` on the activity’s content view and sets the elapsed string
- Tick **does not** call `deckState.notifyChanged()`, `notifySelectionRefresh()`, or `updateSettings`
- Stop the ticker in `onPause` / `onDestroy`
- If the view is gone (yield dismissed companion), the tick is a no-op

- [ ] **Step 1: Extract a tiny helper** on `BaseDeckActivity` or a file-level function:

```kotlin
internal fun tickPlayHudClock(root: android.view.View?, app: GhostGalleonApp, activity: android.content.Context) {
    val clock = root?.findViewWithTag<android.widget.TextView>("play_hud_clock") ?: return
    val session = app.openSession ?: return
    val elapsed = com.visorcraft.ghostgalleon.library.SessionTracker
        .activeElapsedMs(session, System.currentTimeMillis())
    clock.text = activity.getString(
        if (session.isActive) R.string.format_session else R.string.format_session_paused,
        activity.let {
            com.visorcraft.ghostgalleon.i18n.resolveText(
                it,
                com.visorcraft.ghostgalleon.library.SessionMath.formatPlaytime(elapsed),
            )
        },
    )
}
```

Use the same `resolveText` / format helpers `buildNowPlayingCard` already uses. If `resolveText` is an extension on Context, call that — do not invent a new formatter.

- [ ] **Step 2: Schedule** in `CompanionActivity.onResume` (and `MainActivity.onResume` if play host can be Main):

```kotlin
private val playHudTick = object : Runnable {
    override fun run() {
        tickPlayHudClock(window.decorView, app, this@CompanionActivity)
        playHudHandler.postDelayed(this, 1000L)
    }
}
```

`onResume`: `playHudHandler.post(playHudTick)`. `onPause`: `playHudHandler.removeCallbacks(playHudTick)`.

- [ ] **Step 3: Compile + DualPaintPolicyTest** (resume table must not change)

```bash
./gradlew :app:compileDebugKotlin --offline \
  :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.ui.DualPaintPolicyTest'
```

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: tick KEEP play HUD clock in place"
```

### Task 5: TOGGLE_PLAY_HUD compact vs expanded

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt` (`when` on `Action`)
- Modify: `CompanionPanel.buildPlayHud` — actions row can `GONE`/`VISIBLE`

**Interfaces:**
- Process flag on `GhostGalleonApp`: `var playHudExpanded: Boolean = true` (not persisted)
- `TOGGLE_PLAY_HUD`: if `playHostAllowed` for **this** activity’s `currentDisplayId()`, flip the flag and set `play_hud_actions` visibility. Else consume as `false` (fall through / ignore) — do **not** fire during YIELD
- Do not `notifyChanged()`

- [ ] **Step 1: Implement the action branch** next to `OPEN_QUICK_PANEL`:

```kotlin
Action.TOGGLE_PLAY_HUD -> {
    if (repeatCount == 0) {
        val surface = app.sessionSurface
        val allowed = PlayHostPolicy.playHostAllowed(
            dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
            policy = surface?.policy,
            greedy = surface?.greedy == true,
            hostDisplayId = currentDisplayId(),
            launchDisplayId = surface?.launchDisplayId,
        )
        if (allowed) {
            app.playHudExpanded = !app.playHudExpanded
            val vis = if (app.playHudExpanded) View.VISIBLE else View.GONE
            window.decorView.findViewWithTag<View>("play_hud_actions")
                ?.visibility = vis
        }
    }
    true
}
Action.OPEN_SESSION_SWITCHER -> {
    // Task 10 fills this. Swallow for now so remap does not crash.
    true
}
```

`buildPlayHud` initializes actions visibility from `app.playHudExpanded`.

- [ ] **Step 2: Compile**

```bash
./gradlew :app:compileDebugKotlin --offline \
  :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.ActionLabelTest'
```

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: toggle KEEP play HUD action row"
```

Phase 1 device gate (do not claim from host tests):

| Launch | Must see |
|---|---|
| RA SNES KEEP | Game on launch (top). Play HUD on owned companion (bottom). No HUD on the game. |
| Clock 10s | Clock text moves. `GGPaint` has no FULL storm. |
| melonDualDS + `.nds` | No play HUD. Both panels DS. |

---

## Phase 2 — Session switcher

Done when: ring cap 8, persist v9, `beginSession` pushes, switch-to refuses yield/greedy current, same key is no-op, KEEP→KEEP relaunches via `launchSlotKey`, KEEP→YIELD dismisses companion.

### Task 6: SessionRing + SessionSwitch (pure)

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionRing.kt`
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionSwitch.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/SessionRingTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class SessionRingEntry(
    val key: String,
    val playerId: String?,
    val packageName: String,
    val policy: SessionPolicy,
    val launchedAtMs: Long,
    val title: String,
)

object SessionRing {
    const val CAP = 8
    fun push(ring: List<SessionRingEntry>, entry: SessionRingEntry): List<SessionRingEntry>
    fun remove(ring: List<SessionRingEntry>, key: String): List<SessionRingEntry>
}

enum class SwitchToResult { NO_OP, REFUSE_YIELD, LAUNCH }

object SessionSwitch {
    fun decide(
        currentKey: String?,
        currentPlayerId: String?,
        currentPolicy: SessionPolicy?,
        currentGreedy: Boolean,
        target: SessionRingEntry,
    ): SwitchToResult
}
```

- [ ] **Step 1: Failing tests**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRingTest {

    private fun e(key: String, player: String? = "ra-snes9x", t: Long = 1L) =
        SessionRingEntry(key, player, "com.retroarch.aarch64", SessionPolicy.KEEP_COMPANION, t, key)

    @Test
    fun `push dedupes by key and caps at 8`() {
        var ring = emptyList<SessionRingEntry>()
        repeat(9) { i -> ring = SessionRing.push(ring, e("k$i", t = i.toLong())) }
        assertEquals(8, ring.size)
        assertEquals("k8", ring.first().key)
        assertTrue(ring.none { it.key == "k0" })
        ring = SessionRing.push(ring, e("k5", t = 99L))
        assertEquals("k5", ring.first().key)
        assertEquals(8, ring.size)
        assertEquals(1, ring.count { it.key == "k5" })
        assertEquals(99L, ring.first().launchedAtMs)
    }

    @Test
    fun `switch decide`() {
        val tgt = e("rom:snes:a.smc", "ra-snes9x")
        assertEquals(
            SwitchToResult.NO_OP,
            SessionSwitch.decide("rom:snes:a.smc", "ra-snes9x", SessionPolicy.KEEP_COMPANION, false, tgt),
        )
        assertEquals(
            SwitchToResult.REFUSE_YIELD,
            SessionSwitch.decide("rom:nds:b.nds", "melondualds", SessionPolicy.YIELD_BOTH, false, tgt),
        )
        assertEquals(
            SwitchToResult.REFUSE_YIELD,
            SessionSwitch.decide("rom:snes:x.smc", "ra-snes9x", SessionPolicy.KEEP_COMPANION, true, tgt),
        )
        assertEquals(
            SwitchToResult.LAUNCH,
            SessionSwitch.decide("rom:gba:c.gba", "ra-mgba", SessionPolicy.KEEP_COMPANION, false, tgt),
        )
    }
}
```

- [ ] **Step 2: Run — expect Unresolved reference**

- [ ] **Step 3: Implement**

```kotlin
package com.visorcraft.ghostgalleon.rom

data class SessionRingEntry(
    val key: String,
    val playerId: String?,
    val packageName: String,
    val policy: SessionPolicy,
    val launchedAtMs: Long,
    val title: String,
)

object SessionRing {
    const val CAP = 8

    fun push(ring: List<SessionRingEntry>, entry: SessionRingEntry): List<SessionRingEntry> =
        (listOf(entry) + ring.filterNot { it.key == entry.key }).take(CAP)

    fun remove(ring: List<SessionRingEntry>, key: String): List<SessionRingEntry> =
        ring.filterNot { it.key == key }
}
```

```kotlin
package com.visorcraft.ghostgalleon.rom

enum class SwitchToResult { NO_OP, REFUSE_YIELD, LAUNCH }

object SessionSwitch {
    fun decide(
        currentKey: String?,
        currentPlayerId: String?,
        currentPolicy: SessionPolicy?,
        currentGreedy: Boolean,
        target: SessionRingEntry,
    ): SwitchToResult {
        if (currentPolicy == SessionPolicy.YIELD_BOTH || currentGreedy) {
            return SwitchToResult.REFUSE_YIELD
        }
        if (target.key == currentKey && target.playerId == currentPlayerId) {
            return SwitchToResult.NO_OP
        }
        return SwitchToResult.LAUNCH
    }
}
```

- [ ] **Step 4: Re-run — PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add session ring and switch-to decisions"
```

### Task 7: Settings schema v9

**Files:**
- Modify: `settings/Settings.kt` (`schemaVersion` default **9**, new fields)
- Modify: `settings/SettingsStore.kt` (`CURRENT_SCHEMA = 9`, parse + toJson)
- Test: `settings/SettingsStoreTest.kt`

**Interfaces:**
- Produces on `Settings`:

```kotlin
    val sessionRing: List<SessionRingEntry> = emptyList(),
    val detectBlackCompanion: Boolean = true,
    val raNetworkCommands: Boolean = false,
    val raNetworkCmdPort: Int = 55355,
    val schemaVersion: Int = 9,
```

- JSON key `sessionRing`: array of objects `{key, playerId, packageName, policy, launchedAtMs, title}`
- Missing key → empty ring / defaults. No greedy field.
- `SettingsStore.CURRENT_SCHEMA = 9`

- [ ] **Step 1: Failing test**

```kotlin
@Test
fun `v8 json loads v9 defaults and stamps schema 9`() {
    val f = tmp.newFile()
    SettingsStore(f).save(Settings.DEFAULT.copy(schemaVersion = 8))
    val raw = org.json.JSONObject(f.readText())
    raw.put("schemaVersion", 8)
    raw.remove("sessionRing")
    raw.remove("detectBlackCompanion")
    raw.remove("raNetworkCommands")
    f.writeText(raw.toString())
    val loaded = SettingsStore(f).load()
    assertEquals(9, loaded.schemaVersion)
    assertTrue(loaded.sessionRing.isEmpty())
    assertTrue(loaded.detectBlackCompanion)
    assertFalse(loaded.raNetworkCommands)
    assertEquals(55355, loaded.raNetworkCmdPort)
}

@Test
fun `sessionRing round trips without a greedy field`() {
    val entry = SessionRingEntry(
        "rom:snes:a.smc", "ra-snes9x", "com.retroarch.aarch64",
        SessionPolicy.KEEP_COMPANION, 10L, "A",
    )
    val f = tmp.newFile("ring.json")
    SettingsStore(f).save(Settings.DEFAULT.copy(sessionRing = listOf(entry)))
    val loaded = SettingsStore(f).load()
    assertEquals(listOf(entry), loaded.sessionRing)
    assertFalse(org.json.JSONObject(f.readText()).getJSONArray("sessionRing")
        .getJSONObject(0).has("greedy"))
}
```

Reuse this test class’s existing temp-file helper (`tmp` or whatever it is named). If it is `file()`, use that.

- [ ] **Step 2: Run — expect fail** (schema still 8 / missing fields)

- [ ] **Step 3: Implement parse/toJson**

Parse (before `schemaVersion = CURRENT_SCHEMA`):

```kotlin
sessionRing = o.optJSONArray("sessionRing").toSessionRing(),
detectBlackCompanion = o.optBoolean("detectBlackCompanion", true),
raNetworkCommands = o.optBoolean("raNetworkCommands", false),
raNetworkCmdPort = o.optInt("raNetworkCmdPort", 55355).let { if (it in 1..65535) it else 55355 },
```

```kotlin
private fun JSONArray?.toSessionRing(): List<SessionRingEntry> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i ->
        val o = optJSONObject(i) ?: return@mapNotNull null
        val key = o.optString("key").trim()
        if (key.isEmpty()) return@mapNotNull null
        SessionRingEntry(
            key = key,
            playerId = o.optString("playerId").trim().ifEmpty { null },
            packageName = o.optString("packageName"),
            policy = SessionPolicy.parse(o.optString("policy")),
            launchedAtMs = o.optLong("launchedAtMs"),
            title = o.optString("title").ifBlank { key },
        )
    }
}
```

toJson:

```kotlin
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
```

`CURRENT_SCHEMA = 9`. `Settings.schemaVersion` default `9`.

- [ ] **Step 4: Re-run SettingsStoreTest — PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: persist session ring in settings schema v9"
```

### Task 8: beginSession pushes the ring

**Files:**
- Modify: `GhostGalleonApp.kt` `beginSession`
- Test: none new if `SessionRing.push` is unit-tested; compile + `SessionRingTest`

**Interfaces:**
- After assigning `sessionSurface`, build a `SessionRingEntry` from the surface + a title (ROM name from `romEntry` / app label / key) + `System.currentTimeMillis()`
- `updateSettings(settings.copy(sessionRing = SessionRing.push(settings.sessionRing, entry)), notify = false)`
- Do not `notifyChanged()`
- Still `closeQuietly` on YIELD after the push (yield sessions stay in the ring for idle switcher)

- [ ] **Step 1: Implement**

```kotlin
fun beginSession(surface: SessionSurface, nowMs: Long = System.currentTimeMillis()) {
    sessionSurface = surface
    val title = SlotKey.romId(surface.key)?.let { romEntry(it)?.name } ?: surface.key
    val entry = SessionRingEntry(
        key = surface.key,
        playerId = surface.playerId,
        packageName = surface.packageName,
        policy = surface.policy,
        launchedAtMs = nowMs,
        title = title,
    )
    settings = settings.copy(sessionRing = SessionRing.push(settings.sessionRing, entry))
    scheduleSettingsSave(settings)
    if (surface.policy == SessionPolicy.YIELD_BOTH) {
        liveCompanions().forEach { it.closeQuietly() }
    }
}
```

Keep the existing `beginSession(surface: SessionSurface)` signature if call sites cannot take `nowMs` — default the clock inside. Use `settings/SlotKey.romId` (already shipped).

- [ ] **Step 2: Compile + SessionRingTest**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: record KEEP and YIELD launches on the session ring"
```

### Task 9: SessionSwitcherView

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/SessionSwitcherView.kt`
- Modify: `CompanionPanel.kt` (host overlay)
- Strings: `session_switcher_title`, `session_ring_empty` (all five locales)

**Interfaces:**
- Produces: `object SessionSwitcherView { fun attach(host: ViewGroup, ...); fun detach(host: ViewGroup) }`
- Overlay tag `"session_switcher"`
- Rows: title, player id (or display name if cheap), YIELD hint via `settings_player_uses_both_screens` when `policy == YIELD_BOTH`
- Confirm / tap → callback `onPick(SessionRingEntry)`
- End on a row → callback `onRemove(key)` (ring only)
- Back / empty tap on scrim → `onClose()`
- Max 8 rows. Empty: `session_ring_empty` = “No recent sessions.”

Locales:

| key | en | es | de | fr | th |
|---|---|---|---|---|---|
| `session_switcher_title` | Sessions | Sesiones | Sitzungen | Sessions | เซสชัน |
| `session_ring_empty` | No recent sessions | No hay sesiones recientes | Keine letzten Sitzungen | Aucune session récente | ไม่มีเซสชันล่าสุด |

Then `python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check`.

- [ ] **Step 1: Implement attach/detach** as a vertical `LinearLayout` + scrim, matching Quick Panel card chrome (`TileBackgrounds.card`). No Compose. No art decode required in v1 (title + player is enough); if an existing tile helper is one call, use it.

- [ ] **Step 2: `i18n_audit --check` + compile**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: add session switcher overlay view"
```

### Task 10: Wire switcher Action + HUD chip + switch-to

**Files:**
- Modify: `BaseDeckActivity.kt` `OPEN_SESSION_SWITCHER` branch
- Modify: `CompanionPanel.buildPlayHud` — show switcher chip
- Modify: `GhostGalleonApp` if `onRemove` needs `updateSettings`

**Interfaces:**
- Open when idle (no session or KEEP with `playHostAllowed`) on **this** activity
- If `sessionOwnsCompanionDisplay`: toast `session_yields_both_screens` and return
- `onPick`: `SessionSwitch.decide(...)` then:
  - `NO_OP` → detach only
  - `REFUSE_YIELD` → toast `session_yields_both_screens`
  - `LAUNCH` → detach, then existing `launchSlotKey` for `target.key` (same entry point Deck uses)
- `onRemove`: `settings.copy(sessionRing = SessionRing.remove(...))` + save, notify false; rebuild overlay list in place

- [ ] **Step 1: Implement Action + chip click** to call a shared `openSessionSwitcher(activity)` that attaches to `window.decorView` as a full-size child (or the play_hud parent).

Find `launchSlotKey` on Deck / BaseDeckActivity and call it. Do not invent a second launcher.

- [ ] **Step 2: Compile + SessionRingTest + ActionLabelTest + i18n --check**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: switch sessions from the KEEP play host"
```

Phase 2 device gate:

| Step | Must see |
|---|---|
| Open switcher on KEEP RA | Current session is first row. Re-confirm = no-op. |
| Switch KEEP SNES → KEEP GBA | GBA launches; HUD retitles; no companion on launch display. |
| Switch KEEP SNES → melonDualDS | Both panels DS. No Companion task. |
| HOME from melonDualDS | Reclaim. Switcher available idle. |
| X during melonDualDS | No GG on a DS panel. |

---

## Phase 3 — Pixel oracle

Done when: `oracleMaySample` matches the spec table; 3 near-black misses heal with reason `oracle-black`; yield never samples; System toggle defaults on.

### Task 11: oracleMaySample + OracleTally

**Files:**
- Modify: `ui/PlayHostPolicy.kt` — add `oracleMaySample`
- Create: `rom/OracleTally.kt`
- Test: `ui/PlayHostPolicyTest.kt`, `rom/OracleTallyTest.kt`

**Interfaces:**

```kotlin
fun oracleMaySample(
    dualMode: Boolean,
    ownsCompanionDisplay: Boolean,
    windowDisplayId: Int?,
    launchDisplayId: Int?,
    sessionOpen: Boolean,
): Boolean

data class OracleTally(
    val misses: Int = 0,
    val backoffUntilMs: Long = 0L,
)

object OracleTallyLogic {
    const val LUMA_MISS = 8
    const val MISSES_TO_HEAL = 3
    fun onSample(
        tally: OracleTally,
        maxLuma: Int?,
        copyFailed: Boolean,
        nowMs: Long,
    ): Pair<OracleTally, Boolean> // second = requestHeal
}
```

- [ ] **Step 1: Failing tests**

```kotlin
@Test
fun `oracleMaySample table`() {
    assertTrue(
        PlayHostPolicy.oracleMaySample(
            dualMode = true,
            ownsCompanionDisplay = false,
            windowDisplayId = 1,
            launchDisplayId = 0,
            sessionOpen = true,
        ),
    )
    assertFalse(
        PlayHostPolicy.oracleMaySample(true, false, 0, 0, sessionOpen = true),
    )
    assertFalse(
        PlayHostPolicy.oracleMaySample(true, true, 1, 0, sessionOpen = true),
    )
    assertFalse(
        PlayHostPolicy.oracleMaySample(false, false, 1, 0, false),
    )
    assertTrue(
        PlayHostPolicy.oracleMaySample(true, false, 1, 0, sessionOpen = false),
    )
}
```

```kotlin
@Test
fun `three luma misses request heal and a copy failure does not count`() {
    var t = OracleTally()
    var heal = false
    repeat(2) {
        val r = OracleTallyLogic.onSample(t, maxLuma = 0, copyFailed = false, nowMs = 0)
        t = r.first
        heal = r.second
    }
    assertFalse(heal)
    val third = OracleTallyLogic.onSample(t, 0, false, 0)
    assertTrue(third.second)
    val fail = OracleTallyLogic.onSample(OracleTally(), null, true, nowMs = 1000L)
    assertFalse(fail.second)
    assertEquals(0, fail.first.misses)
    assertEquals(11_000L, fail.first.backoffUntilMs) // 1000 + 10_000
    val ignored = OracleTallyLogic.onSample(fail.first, 0, false, nowMs = 5000L)
    assertFalse(ignored.second)
    assertEquals(0, ignored.first.misses)
}
```

- [ ] **Step 2: Run — expect fail**

- [ ] **Step 3: Implement**

```kotlin
fun oracleMaySample(
    dualMode: Boolean,
    ownsCompanionDisplay: Boolean,
    windowDisplayId: Int?,
    launchDisplayId: Int?,
    sessionOpen: Boolean,
): Boolean {
    if (!dualMode) return false
    if (ownsCompanionDisplay) return false
    if (windowDisplayId == null) return false
    if (sessionOpen && windowDisplayId == launchDisplayId) return false
    return true
}
```

```kotlin
package com.visorcraft.ghostgalleon.rom

data class OracleTally(
    val misses: Int = 0,
    val backoffUntilMs: Long = 0L,
)

object OracleTallyLogic {
    const val LUMA_MISS = 8
    const val MISSES_TO_HEAL = 3
    const val FAIL_BACKOFF_MS = 10_000L

    fun onSample(
        tally: OracleTally,
        maxLuma: Int?,
        copyFailed: Boolean,
        nowMs: Long,
    ): Pair<OracleTally, Boolean> {
        if (nowMs < tally.backoffUntilMs) return tally to false
        if (copyFailed) {
            return OracleTally(misses = 0, backoffUntilMs = nowMs + FAIL_BACKOFF_MS) to false
        }
        val miss = maxLuma != null && maxLuma < LUMA_MISS
        if (!miss) return OracleTally(0, tally.backoffUntilMs) to false
        val n = tally.misses + 1
        return if (n >= MISSES_TO_HEAL) {
            OracleTally(0, tally.backoffUntilMs) to true
        } else {
            OracleTally(n, tally.backoffUntilMs) to false
        }
    }
}
```

- [ ] **Step 4: PASS + commit**

```bash
git commit -m "feat: add pixel-oracle sample and miss rules"
```

### Task 12: PixelCopy loop + heal

**Files:**
- Modify: `CompanionActivity.kt` (and Main only if it can host companion pixels when idle)
- Modify: `MainActivity.restartCompanionPanel` callers — new reason `"oracle-black"`
- No new Android unit tests (PixelCopy). Host: `PlayHostPolicyTest` + `OracleTallyTest`.

**Interfaces:**
- Period `DualPaintPolicy.MIN_HEAL_GAP_MS` (2000)
- One copy in flight; skip tick if pending or `!settings.detectBlackCompanion`
- Source: `window` of this activity, not `Display`
- Dest: reuse one 32×32 `RGB_565` bitmap field
- `maxLuma` = max of `(r+r+b+g+g+g)/6` per pixel (integer)
- If `OracleTallyLogic` requests heal:
  - If `sessionOwnsCompanionDisplay` → do nothing
  - If KEEP open && `!playHostAllowed` for this window → do nothing
  - Else `MainActivity.restartCompanionPanel("oracle-black")`
- Log: `Log.i("GGOracle", "miss n=3 display=$id maxLuma=$luma")` then `Log.i("GGOracle", "heal reason=oracle-black")`
- `PixelCopy` failure → `onSample(..., copyFailed = true)` + one `Log.w` per process
- Do not sample while a full render is in flight if you can see that flag; otherwise skip when `!hasWindowFocus` is enough

- [ ] **Step 1: Implement ticker** similar to the clock, 2000 ms, `PixelCopy.request` API 26+ (minSdk 26). Recycle/reuse bitmap. Never `notifyChanged()`.

- [ ] **Step 2: Compile + DualPaintPolicyTest**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: heal a black companion from PixelCopy misses"
```

### Task 13: System toggle Detect black companion

**Files:**
- Modify: `SettingsActivity.kt` System section (near topology / restart companion)
- Strings: `settings_detect_black_companion` (five locales)
- Field already in v9 (`detectBlackCompanion` default true)

| en | es | de | fr | th |
|---|---|---|---|---|
| Detect black companion | Detectar compañero negro | Schwarzes Companion erkennen | Détecter le compagnon noir | ตรวจจับแผงคู่สีดำ |

- [ ] **Step 1: Add a switch row** bound to `settings.detectBlackCompanion` via `updateSettings`. Off skips Task 12 ticks.

- [ ] **Step 2: `i18n_audit --check` + compile**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: toggle pixel-oracle from Settings System"
```

Phase 3 device gate:

| Step | Must see |
|---|---|
| Oracle during melonDualDS | No `PixelCopy`, no heal. |
| Dev: force companion black (idle or KEEP host) | After ~6s, one `GGOracle` heal; companion paints. |
| Toggle off | No `GGOracle` lines. |

---

## Phase 4 — RetroArch link

Done when: Talk to RetroArch off → no UDP; on → probe/pause/save/load; unreachable hides RA chips; PPSSPP HUD has no RA chips; cfg write only on first opt-in and never clobbers other keys.

### Task 14: RaCommand codec

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/RaCommand.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/RaCommandTest.kt`

**Interfaces:**

```kotlin
enum class RaStatus { PLAYING, PAUSED, UNKNOWN }

object RaCommand {
    const val DEFAULT_PORT = 55355
    fun encode(command: String): ByteArray
    fun parseStatus(reply: String?): RaStatus
    fun parseSlotReply(reply: String?): Boolean // true if looks like ACK / non-empty
}

interface RaTransport {
    fun send(port: Int, payload: ByteArray, timeoutMs: Int): ByteArray?
}

class RaCommandClient(
    private val transport: RaTransport,
    private val clockMs: () -> Long,
) {
    fun probe(port: Int, nowMs: Long): Boolean
    fun status(port: Int): RaStatus
    fun pauseToggle(port: Int): Boolean
    fun saveState(port: Int): Boolean
    fun loadState(port: Int): Boolean
}
```

Probe interval **5000 ms** while down; timeout **200 ms**; one outstanding send (transport is sync in tests).

- [ ] **Step 1: Failing tests**

```kotlin
@Test
fun `encode is ascii with newline`() {
    assertArrayEquals("VERSION\n".toByteArray(Charsets.US_ASCII), RaCommand.encode("VERSION"))
}

@Test
fun `parseStatus`() {
    assertEquals(RaStatus.PLAYING, RaCommand.parseStatus("GET_STATUS PLAYING"))
    assertEquals(RaStatus.PAUSED, RaCommand.parseStatus("GET_STATUS PAUSED"))
    assertEquals(RaStatus.UNKNOWN, RaCommand.parseStatus(null))
    assertEquals(RaStatus.UNKNOWN, RaCommand.parseStatus(""))
}

@Test
fun `client probe timeout is not thrown`() {
    val c = RaCommandClient(
        transport = { _, _, _ -> null },
        clockMs = { 0L },
    )
    assertFalse(c.probe(55355, nowMs = 0L))
}
```

Use a lambda or fake `RaTransport` that returns null / `"GET_STATUS PAUSED"`.

- [ ] **Step 2: Implement**

```kotlin
fun encode(command: String): ByteArray =
    (command.trim() + "\n").toByteArray(Charsets.US_ASCII)

fun parseStatus(reply: String?): RaStatus {
    val u = reply?.uppercase() ?: return RaStatus.UNKNOWN
    return when {
        u.contains("PAUSED") -> RaStatus.PAUSED
        u.contains("PLAYING") -> RaStatus.PLAYING
        else -> RaStatus.UNKNOWN
    }
}
```

`probe` sends `VERSION`; any non-null reply → true. Remember `lastProbeMs` and skip if `nowMs - lastProbeMs < 5000` unless last probe succeeded (then caller uses `status` instead).

- [ ] **Step 3: PASS + commit**

```bash
git commit -m "feat: add RetroArch network-command codec"
```

### Task 15: Settings toggle + cfg helper

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/RaCfg.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/RaCfgTest.kt`
- Modify: `SettingsActivity.kt` Library section
- Strings: `settings_ra_network_commands` + dialog body `settings_ra_network_commands_help`

**Interfaces:**

```kotlin
object RaCfg {
    fun enableNetworkCommands(cfgText: String): Pair<String, Boolean>
    // first = new text; second = changed
    fun readPort(cfgText: String, defaultPort: Int = 55355): Int
}
```

- Never drop unrelated keys
- If `network_cmd_enable` already `"true"`, do not duplicate; still ensure port line exists
- Settings toggle binds `raNetworkCommands`. First ON: try `File(extras["CONFIGFILE"]).takeIf { it.canWrite() }`; if written, `Log.i("GGSession", "ra-cmd enabled")`. If not writable: `AlertDialog` with the two lines to paste. Leave toggle **on**.

| key | en |
|---|---|
| `settings_ra_network_commands` | Talk to RetroArch |
| `settings_ra_network_commands_help` | In RetroArch: Settings → Network → Network Commands ON, port 55355. |

es/de/fr/th: translate both. Then i18n audit.

- [ ] **Step 1: RaCfgTest**

```kotlin
@Test
fun `enables without clobbering other keys`() {
    val src = "foo = \"bar\"\n"
    val (out, changed) = RaCfg.enableNetworkCommands(src)
    assertTrue(changed)
    assertTrue(out.contains("foo = \"bar\""))
    assertTrue(out.contains("network_cmd_enable = \"true\""))
    assertTrue(out.contains("network_cmd_port = \"55355\""))
    val again = RaCfg.enableNetworkCommands(out)
    assertFalse(again.second)
}

@Test
fun `readPort`() {
    assertEquals(1234, RaCfg.readPort("network_cmd_port = \"1234\"\n"))
    assertEquals(55355, RaCfg.readPort("x = 1\n"))
}
```

- [ ] **Step 2: Implement RaCfg + Settings row**

- [ ] **Step 3: Tests + i18n --check + commit**

```bash
git commit -m "feat: opt in RetroArch network commands from Settings"
```

### Task 16: HUD RA chips

**Files:**
- Modify: `CompanionPanel.buildPlayHud`
- Create: `rom/RaUdpTransport.kt` (Android `DatagramSocket`, thin) — **not** referenced from `RaCommand.kt`
- Modify: `GhostGalleonApp` to hold a process `RaCommandClient?`

**Interfaces:**
- Show Pause/Save/Load only when:
  - `playHostAllowed`
  - `settings.raNetworkCommands`
  - playerId starts with `ra-` **or** `packageName == "com.retroarch.aarch64"`
  - last probe succeeded
- Hidden (not disabled-spam) when unreachable
- Probe on HUD bind + every 5s from the clock ticker if down
- Pause chip label: `play_hud_pause` / `play_hud_resume` from `GET_STATUS`
- Save → `SAVE_STATE`; Load → `LOAD_STATE` (v1 current slot)
- Updates: set TextView / visibility only. Never `updateSettings` / `notifyChanged` / `publishRomEntries`

Strings (five locales): `play_hud_pause`, `play_hud_resume`, `play_hud_save`, `play_hud_load`

| en | es | de | fr | th |
|---|---|---|---|---|
| Pause | Pausa | Pause | Pause | หยุดชั่วคราว |
| Resume | Reanudar | Fortsetzen | Reprendre | เล่นต่อ |
| Save | Guardar | Speichern | Sauver | บันทึก |
| Load | Cargar | Laden | Charger | โหลด |

- [ ] **Step 1: Strings + i18n audit**

- [ ] **Step 2: Wire chips.** `RaUdpTransport` implements `RaTransport.send` with `DatagramSocket.soTimeout = timeoutMs` to `127.0.0.1`. Catch all IO → null.

- [ ] **Step 3: Compile + RaCommandTest + DualPaintPolicyTest + i18n --check**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: control RetroArch from the KEEP play HUD"
```

### Task 17: Savestate slot strip (best-effort thumbs)

**Files:**
- Modify: `CompanionPanel.kt` load chip → show slots 1–8
- Optional helper: `rom/RaStateSlots.kt` — `fun slotLabels(readablePngNames: List<String>): List<Int>` always `1..8`; thumbs are a side map

**Interfaces:**
- v1: numbered slots 1–8. If `File(external + "/states")` lists `*.png`, attach those as optional bitmaps; if not readable, numbers only
- No new permission. No whole-SD scan
- Picking slot N: try `LOAD_STATE_SLOT N` / `SAVE_STATE_SLOT N`; if probe of that command fails once, fall back to `LOAD_STATE` / `SAVE_STATE` and hide the strip (remember process-only)

- [ ] **Step 1: Host test for slot list**

```kotlin
@Test
fun `slots are 1 through 8`() {
    assertEquals((1..8).toList(), RaStateSlots.SLOTS)
}
```

- [ ] **Step 2: UI strip + commit**

```bash
git commit -m "feat: pick RetroArch savestate slots from the HUD"
```

### Task 18: Spec + dual-paint notes

**Files:**
- Modify: `docs/keep-play-surface.md` — mark phases implemented (host); device matrix **not run** unless you ran it
- Modify: `docs/dual-paint-invariants.md` — one bullet: play HUD ticks and oracle heals are not SETTINGS
- Modify: `docs/split-session-ownership.md` only if a sentence is now wrong

- [ ] **Step 1: Docs match shipped behavior. Do not invent a Sugar matrix. Do not invent a greedy package.**

- [ ] **Step 2: Commit**

```bash
git commit -m "docs: note shipped KEEP play-surface rules"
```

Phase 4 device gate:

| Step | Must see |
|---|---|
| RA Talk off | No UDP. HUD has no pause/save. |
| RA Talk on + `network_cmd_enable` | Pause chip tracks GET_STATUS. Save does not crash RA. |
| PPSSPP KEEP | HUD without RA chips. |
| melonDualDS | Still no HUD, no UDP, no PixelCopy. |

---

## Out of scope (do not implement)

- `SYSTEM_ALERT_WINDOW` / Quickstep / Recents replacement
- PixelCopy of the launch display or a yield session
- Key injection into melonDualDS / Azahar
- RA cheats, RAM peek, screenshots
- Kill-previous-RA-on-switch
- Per-ROM `sessionPolicy` Settings UI
- Compose / new Gradle deps

## Self-review

| Spec section | Tasks |
|---|---|
| `playHostAllowed` | 1, 3 |
| Play HUD chrome / clock / actions | 3, 4, 5 |
| New Actions + i18n | 2, 5, 10 |
| Input only on owned display | 5, 10 |
| Session ring + v9 | 6, 7, 8 |
| Switcher UI + switch-to | 6, 9, 10 |
| Refuse switcher during yield | 6, 10 |
| `oracleMaySample` + tally | 11 |
| PixelCopy + heal | 12, 13 |
| RA codec / opt-in cfg | 14, 15 |
| RA HUD chips + slots | 16, 17 |
| Dual-paint / docs | 4, 12, 18 |
| Device matrix | phase gates 1–4 |
| No overlay / no Display in policy | 1, 6, 11, 14 |

`OpenSession` remains playtime-only. `SessionSurface` remains the session-policy record. `sessionOwnsCompanionDisplay` still blocks heal, embed, pin, HUD, oracle, and RA.
