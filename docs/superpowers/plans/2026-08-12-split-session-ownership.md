# Split-session ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ghost Galleon yields both panels to dual-surface players (melonDualDS, Azahar) and keeps a live companion for single-surface players, then reclaims HOME cleanly.

**Architecture:** A pure `SessionPolicy` (`KEEP_COMPANION` | `YIELD_BOTH`) is stored on `PlayerTemplate` and resolved by player id, never platform id. `GhostGalleonApp` records a `SessionSurface` at successful launch. `DualPaintPolicy` + `MainActivity` heal/restart/swap branch on that record. Dual-paint invariants stay in force.

**Tech Stack:** Kotlin, classic Views, host JUnit (`./gradlew :app:testDebugUnitTest --offline`), no new dependencies, no Compose.

**Spec:** [`docs/split-session-ownership.md`](../../split-session-ownership.md)

## Global Constraints

- Dual-surface games **keep both screens**. Never steal a panel from melonDualDS or Azahar.
- Policy is attached to the **player that launched** (`PlayerTemplate.id`), not `platformId`.
- Default missing/unknown policy is `KEEP_COMPANION`.
- No `SYSTEM_ALERT_WINDOW`. No overlay HUD on a yielded session.
- Do not change `setLaunchDisplayId` to prevent a YIELD player from using the second panel.
- Display ids come from `DisplayTopology` / `DeckState.primaryDisplayId` — never hard-code `0`/`1`.
- `CompanionActivity.skipExitCascade()` is already `true`; yield finish must keep that.
- Android `Display` types stay out of `SessionPolicy` / `SessionSurface`.
- New user-facing strings go in all five catalogs (`values`, `values-es`, `values-de`, `values-fr`, `values-th`) then `python3 scripts/i18n_audit.py --write && --check`.
- Commits: Conventional Commits, human author only, no AI attribution.
- Device claims for phases 2–4 require the Sugar matrix in the spec. Host tests alone are not enough.

## File map

| File | Role |
|---|---|
| `rom/SessionPolicy.kt` | Enum + `resolve()` + built-in yield ids |
| `rom/SessionSurface.kt` | Process session record (key, player, package, policy, launchDisplayId, greedy) |
| `rom/Platform.kt` | `PlayerTemplate.sessionPolicy` default KEEP; set YIELD on `melondualds` and `azahar` |
| `rom/PlatformPack.kt` | Parse optional `"sessionPolicy"` |
| `library/SessionTracker.kt` | Unchanged playtime math (`OpenSession` stays playtime-only) |
| `GhostGalleonApp.kt` | Holds `sessionSurface`; `beginSession` / `endSessionSurface`; expose to heal |
| `rom/RomLauncher.kt` | Return winning template so launch can record a session |
| `ui/deck/Deck.kt` | `launchSlotKey` calls `beginSession` after success (ROM + app) |
| `settings/CompanionRole.kt` | Pin honesty from session policy; delete `DUAL_CLAIM_PLATFORMS` |
| `ui/DualPaintPolicy.kt` | Resume/heal/swap decisions from policy + returning + greedy |
| `ui/MainActivity.kt` | Branch return/heal/restart on policy |
| `ui/CompanionActivity.kt` | Finish quietly when a YIELD session opens |
| `ui/BaseDeckActivity.kt` | SWAP during YIELD: do not `restartCompanionPanel` |
| `ui/settings/SettingsActivity.kt` | Read-only “Uses both screens” on the player row |
| Tests listed per task | Host-only, Robolectric-free |

---

## Phase 1 — Policy

Done when: host tests prove melonDualDS/Azahar yield, DraStic/RA melonDS/SNES keep, pack omit = KEEP, pin honesty uses **player policy** not `nds`/`3ds`. `DUAL_CLAIM_PLATFORMS` is gone.

### Task 1: SessionPolicy resolve

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/SessionPolicyTest.kt`

**Interfaces:**
- Produces: `enum class SessionPolicy { KEEP_COMPANION, YIELD_BOTH }`
- Produces: `SessionPolicy.parse(raw: String?): SessionPolicy` — `"YIELD_BOTH"` / `"yield_both"` → YIELD; blank/unknown → KEEP
- Produces: `SessionPolicy.forPlayerId(playerId: String?): SessionPolicy` — `melondualds` and `azahar` → YIELD; else KEEP
- Produces: `SessionPolicy.resolve(playerId: String?, romOverride: SessionPolicy? = null, packageYield: Boolean = false): SessionPolicy` — order: `romOverride` if non-null, else `packageYield` → YIELD, else `forPlayerId`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPolicyTest {

    @Test
    fun `parse blanks and junk keep`() {
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.parse(null))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.parse(""))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.parse("nope"))
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.parse("YIELD_BOTH"))
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.parse(" yield_both "))
    }

    @Test
    fun `built-in player ids`() {
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.forPlayerId("melondualds"))
        assertEquals(SessionPolicy.YIELD_BOTH, SessionPolicy.forPlayerId("azahar"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("melonds"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("drastic"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("ra-melonds"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId("ra-snes9x"))
        assertEquals(SessionPolicy.KEEP_COMPANION, SessionPolicy.forPlayerId(null))
    }

    @Test
    fun `resolve prefers rom override then package yield then player id`() {
        assertEquals(
            SessionPolicy.KEEP_COMPANION,
            SessionPolicy.resolve("melondualds", romOverride = SessionPolicy.KEEP_COMPANION),
        )
        assertEquals(
            SessionPolicy.YIELD_BOTH,
            SessionPolicy.resolve("ra-snes9x", packageYield = true),
        )
        assertEquals(
            SessionPolicy.YIELD_BOTH,
            SessionPolicy.resolve("melondualds"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SessionPolicyTest'`

Expected: compile fail (`Unresolved reference: SessionPolicy`)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.rom

enum class SessionPolicy {
    KEEP_COMPANION,
    YIELD_BOTH,
    ;

    companion object {
        private val YIELD_PLAYER_IDS = setOf("melondualds", "azahar")

        fun parse(raw: String?): SessionPolicy {
            val key = raw?.trim()?.uppercase().orEmpty()
            return if (key == YIELD_BOTH.name) YIELD_BOTH else KEEP_COMPANION
        }

        fun forPlayerId(playerId: String?): SessionPolicy =
            if (playerId?.trim() in YIELD_PLAYER_IDS) YIELD_BOTH else KEEP_COMPANION

        fun resolve(
            playerId: String?,
            romOverride: SessionPolicy? = null,
            packageYield: Boolean = false,
        ): SessionPolicy {
            if (romOverride != null) return romOverride
            if (packageYield) return YIELD_BOTH
            return forPlayerId(playerId)
        }
    }
}
```

- [ ] **Step 4: Re-run tests**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/SessionPolicyTest.kt
git commit -m "feat: add SessionPolicy resolve for split-session ownership"
```

### Task 2: PlayerTemplate field + built-in yield players + pack parse

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/Platform.kt` (`PlayerTemplate` + `NDS`/`N3DS` player lists)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/PlatformPack.kt` (`parsePlayer`)
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/PlatformsTest.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/PlatformPackCatalogTest.kt` or add cases to `PlatformPackExampleTest.kt`

**Interfaces:**
- Consumes: `SessionPolicy.parse`
- Produces: `PlayerTemplate.sessionPolicy: SessionPolicy = SessionPolicy.KEEP_COMPANION`
- Built-ins: `Platforms.NDS` player `melondualds` and `Platforms.N3DS` player `azahar` set `sessionPolicy = SessionPolicy.YIELD_BOTH`
- Pack JSON: `"sessionPolicy": "YIELD_BOTH"`; omit → KEEP

- [ ] **Step 1: Write failing assertions**

In `PlatformsTest` add:

```kotlin
@Test
fun `dual-surface players yield and other NDS players keep`() {
    val nds = Platforms.NDS.players.associateBy { it.id }
    assertEquals(SessionPolicy.YIELD_BOTH, nds.getValue("melondualds").sessionPolicy)
    assertEquals(SessionPolicy.KEEP_COMPANION, nds.getValue("melonds").sessionPolicy)
    assertEquals(SessionPolicy.KEEP_COMPANION, nds.getValue("drastic").sessionPolicy)
    assertEquals(SessionPolicy.KEEP_COMPANION, nds.getValue("ra-melonds").sessionPolicy)
    assertEquals(SessionPolicy.YIELD_BOTH, Platforms.N3DS.player.sessionPolicy)
    assertEquals(SessionPolicy.KEEP_COMPANION, Platforms.SNES.player.sessionPolicy)
}
```

In `PlatformPackExampleTest` (or a new `PlatformPackSessionPolicyTest`):

```kotlin
@Test
fun `pack sessionPolicy parses and omitted field keeps`() {
    val json = """
        {"schemaVersion":1,"platforms":[{
          "id":"nds","displayName":"NDS","shortName":"NDS",
          "folderNames":["nds"],"extensions":["nds"],
          "players":[
            {"id":"melondualds","displayName":"m","component":"a.b/.C",
             "uriStyle":"URI","sessionPolicy":"YIELD_BOTH"},
            {"id":"other","displayName":"o","component":"c.d/.E","uriStyle":"URI"}
          ]
        }]}
    """.trimIndent()
    val parsed = PlatformPack.parse(json)!!
    val players = parsed.platforms.first().players.associateBy { it.id }
    assertEquals(SessionPolicy.YIELD_BOTH, players.getValue("melondualds").sessionPolicy)
    assertEquals(SessionPolicy.KEEP_COMPANION, players.getValue("other").sessionPolicy)
}
```

- [ ] **Step 2: Run tests — expect fail** on missing `sessionPolicy`

- [ ] **Step 3: Implement**

Add to `PlayerTemplate` after `flags`:

```kotlin
val sessionPolicy: SessionPolicy = SessionPolicy.KEEP_COMPANION,
```

On the `melondualds` and `azahar` `PlayerTemplate(` calls, add `sessionPolicy = SessionPolicy.YIELD_BOTH,`.

In `PlatformPack.parsePlayer`, before `return PlayerTemplate(`:

```kotlin
val sessionPolicy = SessionPolicy.parse(
    if (o.has("sessionPolicy")) o.optString("sessionPolicy") else null,
)
```

Pass `sessionPolicy = sessionPolicy` into the constructor.

- [ ] **Step 4: Re-run**

```bash
./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.PlatformsTest' --tests 'com.visorcraft.ghostgalleon.rom.PlatformPack*' --tests 'com.visorcraft.ghostgalleon.rom.RomLauncherTest'
```

Expected: PASS (`PlayerTemplate` default keeps existing constructor call sites)

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: attach SessionPolicy to player templates and packs"
```

### Task 3: SessionSurface + begin/end on the app object

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionSurface.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/SessionSurfaceTest.kt`
- Modify: `GhostGalleonApp.kt` (`noteLaunch` stays playtime; add `beginSession` / `clearSessionSurface`)

**Interfaces:**
- Produces:

```kotlin
data class SessionSurface(
    val key: String,
    val playerId: String?,
    val packageName: String,
    val policy: SessionPolicy,
    val launchDisplayId: Int?,
    val greedy: Boolean = false,
)
```

- Produces: `SessionSurface.forLaunch(key, playerId, packageName, launchDisplayId, packageYield = false, romOverride = null)` using `SessionPolicy.resolve`
- `GhostGalleonApp.sessionSurface: SessionSurface?`
- `fun beginSession(surface: SessionSurface)`
- `fun markSessionGreedy()`
- `fun clearSessionSurface()`
- `OpenSession` / `SessionTracker` **unchanged**

- [ ] **Step 1: Failing tests** for `forLaunch` (melondualds → YIELD, ra-snes9x → KEEP, packageYield, greedy copy)

```kotlin
@Test
fun `forLaunch uses player id`() {
    val y = SessionSurface.forLaunch("rom:nds:a.nds", "melondualds", "me.magnum.melondualds", 0)
    assertEquals(SessionPolicy.YIELD_BOTH, y.policy)
    val k = SessionSurface.forLaunch("rom:snes:x.smc", "ra-snes9x", "com.retroarch.aarch64", 0)
    assertEquals(SessionPolicy.KEEP_COMPANION, k.policy)
    assertFalse(k.greedy)
}
```

- [ ] **Step 2: Implement `SessionSurface` companion `forLaunch`**

- [ ] **Step 3: On `GhostGalleonApp`:**

```kotlin
@Volatile
var sessionSurface: SessionSurface? = null
    private set

fun beginSession(surface: SessionSurface) {
    sessionSurface = surface
}

fun markSessionGreedy() {
    sessionSurface = sessionSurface?.copy(greedy = true)
}

fun clearSessionSurface() {
    sessionSurface = null
}
```

Call `clearSessionSurface()` from existing `endOpenSession` so playtime end and surface end stay paired.

- [ ] **Step 4: Tests pass + commit**

```bash
git commit -m "feat: record SessionSurface beside the play session"
```

### Task 4: Launch path records the winning player

**Files:**
- Modify: `rom/RomLauncher.kt` — change `launch` to return `PlayerTemplate?` (null = fail)
- Modify: `ui/deck/Deck.kt` `launchSlotKey`
- Test: `RomLauncherTest` only if there are Android-free tests of `launch`; otherwise test via a new thin helper

**Interfaces:**
- `RomLauncher.launch(...): PlayerTemplate?` — after `launchOnOtherDisplay` succeeds, `return template` instead of `true`; all fail paths `return null`
- `launchSlotKey` after success:

```kotlin
val template = RomLauncher.launch(...)
if (template != null) {
    app.beginSession(
        SessionSurface.forLaunch(
            key = key,
            playerId = template.id,
            packageName = template.component.substringBefore('/'),
            launchDisplayId = app.displayConfig.launchDisplayId,
        ),
    )
    app.noteLaunch(key)
}
```

For Android apps (`getLaunchIntentForPackage`):

```kotlin
app.beginSession(
    SessionSurface.forLaunch(
        key = key,
        playerId = null,
        packageName = key,
        launchDisplayId = app.displayConfig.launchDisplayId,
    ),
)
app.noteLaunch(key)
```

- [ ] **Step 1: Update every `RomLauncher.launch(...)` Boolean caller** (`if (ok)` → `if (template != null)`). Grep `RomLauncher.launch`.

- [ ] **Step 2: Run** `:app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RomLauncherTest'`

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: bind SessionSurface at successful launch"
```

### Task 5: Pin honesty from session policy; delete DUAL_CLAIM_PLATFORMS

**Files:**
- Modify: `settings/CompanionRole.kt`
- Test: `settings/CompanionRoleTest.kt`

**Interfaces:**
- `CompanionRoleResolve.Context` replaces `openSessionPlatformId` with `sessionPolicy: SessionPolicy? = null`
- `pinHonesty`: `DUAL_CLAIM` iff `ctx.sessionPolicy == SessionPolicy.YIELD_BOTH`
- Delete `DUAL_CLAIM_PLATFORMS`
- `effective()` unchanged (PINNED_APP still stays PINNED_APP for the CTA)

- [ ] **Step 1: Rewrite the dual-claim test** to pass `sessionPolicy = SessionPolicy.YIELD_BOTH` instead of `openSessionPlatformId = "nds"`. Add a test that `openSession` on NDS with `KEEP_COMPANION` is **not** `DUAL_CLAIM`.

- [ ] **Step 2: Update `CompanionPanel` / any `Context(` call sites** that pass `openSessionPlatformId` to pass `app.sessionSurface?.policy` instead.

- [ ] **Step 3:**

```bash
rg -n "DUAL_CLAIM_PLATFORMS|openSessionPlatformId" app/src
```

Expected: no matches.

```bash
./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.CompanionRoleTest'
```

- [ ] **Step 4: Commit**

```bash
git commit -m "fix: pin dual-claim honesty follows SessionPolicy not platform"
```

### Task 6: DualPaintPolicy resume/heal decisions (pure)

**Files:**
- Modify: `ui/DualPaintPolicy.kt`
- Test: `ui/DualPaintPolicyTest.kt`

**Interfaces:**

```kotlin
enum class ResumeCompanionAction { NONE, HEAL_IF_MISSING, RESTART }

fun resumeCompanionAction(
    dualMode: Boolean,
    returningFromElsewhere: Boolean,
    policy: SessionPolicy?,
    greedy: Boolean,
    pinReady: Boolean,
): ResumeCompanionAction {
    if (!dualMode) return ResumeCompanionAction.NONE
    if (policy == SessionPolicy.YIELD_BOTH) {
        return if (returningFromElsewhere) ResumeCompanionAction.RESTART
        else ResumeCompanionAction.NONE
    }
    if (policy == SessionPolicy.KEEP_COMPANION) {
        if (!returningFromElsewhere) return ResumeCompanionAction.NONE
        if (greedy) return ResumeCompanionAction.RESTART
        if (pinReady) return ResumeCompanionAction.HEAL_IF_MISSING
        return ResumeCompanionAction.HEAL_IF_MISSING
    }
    // No session: today's return-from-app restart (unless pin ready)
    if (returningFromElsewhere && !pinReady) return ResumeCompanionAction.RESTART
    return ResumeCompanionAction.HEAL_IF_MISSING
}

fun allowCompanionRestartDuringSwap(
    dualMode: Boolean,
    policy: SessionPolicy?,
): Boolean = dualMode && policy != SessionPolicy.YIELD_BOTH
```

- [ ] **Step 1: Tests** — table from spec: YIELD+return→RESTART; YIELD+not returning→NONE; KEEP+return→HEAL; KEEP+greedy+return→RESTART; KEEP+still on HOME→NONE; no session+return+!pin→RESTART; swap blocked when YIELD.

- [ ] **Step 2: Implement the two functions. Do not change `allowHeal` timing.**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat: dual-paint resume actions from SessionPolicy"
```

Phase 1 gate:

```bash
rg -n "DUAL_CLAIM_PLATFORMS" app/src
./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SessionPolicyTest' --tests 'com.visorcraft.ghostgalleon.rom.SessionSurfaceTest' --tests 'com.visorcraft.ghostgalleon.rom.PlatformsTest' --tests 'com.visorcraft.ghostgalleon.settings.CompanionRoleTest' --tests 'com.visorcraft.ghostgalleon.ui.DualPaintPolicyTest'
```

---

## Phase 2 — Yield

Done when: on the Sugar, melonDualDS and Azahar own **both** panels (no companion task), and HOME restores both Ghost Galleon surfaces. Host tests cover the branches; device matrix is required to claim done.

### Task 7: MainActivity return/heal uses resumeCompanionAction

**Files:**
- Modify: `ui/MainActivity.kt` `onResume` / `healCompanionIfMissing` / `restartCompanionPanel`

**Interfaces:**
- Consumes: `DualPaintPolicy.resumeCompanionAction`, `app.sessionSurface`

Replace the pin-only branch in `onResume` with:

```kotlin
val surface = app.sessionSurface
val pinReady = app.settings.companionRole == CompanionRole.PINNED_APP.name &&
    !app.settings.companionPinnedPackage.isNullOrBlank()
val action = DualPaintPolicy.resumeCompanionAction(
    dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
    returningFromElsewhere = returningFromElsewhere,
    policy = surface?.policy,
    greedy = surface?.greedy == true,
    pinReady = pinReady,
)
when (action) {
    DualPaintPolicy.ResumeCompanionAction.NONE -> { }
    DualPaintPolicy.ResumeCompanionAction.HEAL_IF_MISSING -> healCompanionIfMissing()
    DualPaintPolicy.ResumeCompanionAction.RESTART ->
        restartCompanionPanel(
            if (surface?.policy == SessionPolicy.YIELD_BOTH) "return-from-yield"
            else "return-from-app",
        )
}
if (returningFromElsewhere) {
    app.clearSessionSurface()
    // existing endOpenSession / playtime accrue stays where it already runs
}
```

Call `clearSessionSurface` only after deciding the action (so YIELD return still sees the policy). Accrue playtime using the existing `endOpenSession` path — do not invent a second clock.

`healCompanionIfMissing`: if `app.sessionSurface?.policy == YIELD_BOTH` and `!returningFromElsewhere`, return immediately (do not launch onto a live DS).

- [ ] **Step 1: Implement the branch. No new Android unit tests** (Activity). Rely on `DualPaintPolicyTest`.

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: reclaim companion from SessionPolicy on HOME return"
```

### Task 8: Companion finishes when a YIELD session starts

**Files:**
- Modify: `ui/CompanionActivity.kt`
- Modify: `GhostGalleonApp.beginSession` to notify live companions

**Interfaces:**
- `GhostGalleonApp.beginSession`: after assigning `sessionSurface`, if `surface.policy == YIELD_BOTH`, `liveCompanions().forEach { it.closeQuietly() }`
- `CompanionActivity.onResume` / `onCreate` after absorb checks: if `app.sessionSurface?.policy == YIELD_BOTH`, `closeQuietly()` and return (do not paint)
- `closeQuietly` already exists and `skipExitCascade()` is `true` — do not call `finish()` in a way that cascades Main

- [ ] **Step 1: Implement. Grep `restartCompanionPanel` and `launchCompanionIfPresent` for paths that could spawn companion during YIELD.**

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: dismiss companion when a YIELD session opens"
```

### Task 9: Swap and SECONDARY_HOME do not fight a YIELD session

**Files:**
- Modify: `ui/BaseDeckActivity.kt` `Action.SWAP_SCREENS` block (~1077)
- Modify: `ui/CompanionActivity.kt` SECONDARY_HOME / absorb path if it relaunches

**Interfaces:**
- Wrap `main.restartCompanionPanel("swap-recover"…)` in:

```kotlin
if (DualPaintPolicy.allowCompanionRestartDuringSwap(
        dualMode = app.displayConfig.mode == SurfaceMode.DUAL,
        policy = app.sessionSurface?.policy,
    )
) {
    main.restartCompanionPanel(...)
} else if (app.sessionSurface?.policy == SessionPolicy.YIELD_BOTH) {
    Toast.makeText(this, R.string.session_yields_both_screens, Toast.LENGTH_SHORT).show()
}
```

- Add string `session_yields_both_screens` = “This game uses both screens” (+ es/de/fr/th). Then:

```bash
python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check
```

- [ ] **Step 1: Strings + swap guard + i18n check**

- [ ] **Step 2: Commit**

```bash
git commit -m "fix: do not restart companion over a YIELD dual-surface game"
```

### Task 10: Settings read-only yield hint

**Files:**
- Modify: `ui/settings/SettingsActivity.kt` (player default / Open-with rows)
- Strings: `settings_player_uses_both_screens` in all five locales

**Interfaces:**
- When listing players for a platform, if `player.sessionPolicy == YIELD_BOTH`, append or subtitle the string. No new setting.

- [ ] **Step 1: i18n + row + `--check`**

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: label dual-surface players in Settings"
```

Phase 2 device gate (do not skip):

| Launch | Must see |
|---|---|
| melonDualDS + `.nds` | Both panels are the DS. `dumpsys activity activities` has no `CompanionActivity` on either display. |
| Azahar + `.3ds` | Same. |
| HOME (or `am force-stop me.magnum.melondualds` then HOME) | Interactive + companion both paint. |
| X during melonDualDS | No Ghost Galleon surface on a DS panel. |
| SECONDARY_HOME during melonDualDS | Absorbed; DS stays. |

---

## Phase 3 — Keep

Done when: RetroArch SNES/GBA (and PPSSPP/Eden if present) stay on the launch display and the companion stays Ghost Galleon (Now Playing or previous role), not black.

### Task 11: KEEP session does not restart companion on the way out

**Files:**
- Modify: `MainActivity.onResume` — already using `resumeCompanionAction` (KEEP + not returning → NONE). Confirm no other `restartCompanionPanel("return-from-app")` fires on launch.
- Modify: `CompanionPanel` so an open KEEP session with preference HERO/NOW_PLAYING shows the open title (existing Now Playing). Do not force PINNED_APP onto the launch display.

**Interfaces:**
- Pin: if `sessionSurface?.policy == KEEP` and `companionPinnedPackage == sessionSurface.packageName`, treat pin as paused (do not ActivityEmbed the game on the companion). Reuse `PinHonesty` or a small `pinConflictsWithSession(package, surface)` in `CompanionRoleResolve`.

```kotlin
fun pinConflictsWithSession(pinnedPackage: String?, surface: SessionSurface?): Boolean {
    if (surface == null || pinnedPackage.isNullOrBlank()) return false
    return pinnedPackage == surface.packageName
}
```

Test that in `CompanionRoleTest`.

- [ ] **Step 1: Test + implement pin conflict**

- [ ] **Step 2: Commit**

```bash
git commit -m "fix: do not pin the KEEP game over itself on companion"
```

### Task 12: Heal stays off the launch display during KEEP

**Files:**
- Modify: `healCompanionIfMissing` / `launchCompanionIfPresent`

**Interfaces:**
- If `sessionSurface?.policy == KEEP_COMPANION` and `!returningFromElsewhere`, do not launch companion on `sessionSurface.launchDisplayId`. Companion may already be on the other display — leave it.
- `shouldLaunchCompanion` remains “no peer on target.” The extra guard is “target != KEEP launch display.”

```kotlin
fun keepHealBlocked(
    policy: SessionPolicy?,
    targetDisplayId: Int?,
    launchDisplayId: Int?,
): Boolean = policy == SessionPolicy.KEEP_COMPANION &&
    targetDisplayId != null &&
    targetDisplayId == launchDisplayId
```

Host-test `keepHealBlocked`. Wire in `healCompanionIfMissing` before `startActivity`.

- [ ] **Step 1: Test + wire**

- [ ] **Step 2: Commit**

```bash
git commit -m "fix: keep heal off the launch display during KEEP sessions"
```

### Task 13: KEEP companion contents

**Files:**
- Modify: `ui/deck/CompanionPanel.kt` Now Playing / Resume

**Interfaces:**
- Resume chip already launches the selected/continue key. During KEEP, Resume uses `sessionSurface.key` via existing `launchSlotKey`.
- No new intent extras.

- [ ] **Step 1: If Resume already uses continue/session key, only a comment + smoke. If it uses `state.selectedKey` only, prefer `app.sessionSurface?.key ?: state.selectedKey` when a KEEP session is open.**

- [ ] **Step 2: Commit if code changed**

```bash
git commit -m "feat: KEEP companion Resume uses the open session key"
```

Phase 3 device gate:

| Launch | Must see |
|---|---|
| RA SNES / GBA | Game on launch (top). Bottom stays Ghost Galleon (Now Playing or hero). |
| DraStic or RA melonDS + same `.nds` | Same: companion still Ghost Galleon. |
| HOME from RA SNES | Interactive unchanged; companion still ours (not black). |

---

## Phase 4 — Greedy keep

Done when: a KEEP title that still covers both panels does not get a companion spawned on top of it, and HOME reclaims both Ghost Galleon surfaces. Greedy is **process-only** (never written to Settings).

### Task 14: Mark greedy on KEEP return if companion was stolen

**Files:**
- Modify: `MainActivity.onResume` before `clearSessionSurface`
- Modify: `DualPaintPolicy.resumeCompanionAction` (already RESTART when greedy)
- Test: `DualPaintPolicyTest` already covers greedy+return→RESTART

**Interfaces:**
- Detection (no PixelCopy in v1): KEEP session + `returningFromElsewhere` + companion missing or not healthy on the companion display → `app.markSessionGreedy()` then `RESTART`.
- Log: `Log.i("GGSession", "greedy package=${surface.packageName} player=${surface.playerId}")`
- Do not write Settings. Do not flip `PlayerTemplate.sessionPolicy`.

```kotlin
fun shouldMarkGreedy(
    policy: SessionPolicy?,
    returningFromElsewhere: Boolean,
    companionHealthy: Boolean,
): Boolean = policy == SessionPolicy.KEEP_COMPANION &&
    returningFromElsewhere &&
    !companionHealthy
```

Host-test `shouldMarkGreedy`.

- [ ] **Step 1: Test + call `markSessionGreedy()` then take RESTART action**

- [ ] **Step 2: Commit**

```bash
git commit -m "feat: treat stolen-companion KEEP returns as greedy reclaim"
```

### Task 15: Heal/pin/embed stay off during an open greedy session

**Files:**
- Modify: `healCompanionIfMissing`, `CompanionPanel` ActivityEmbed, pin launch

**Interfaces:**
- If `sessionSurface?.greedy == true` **and the session is still open** (not yet returned): same as YIELD — do not launch companion, do not embed, do not pin.
- After return, greedy is cleared with `clearSessionSurface`.

- [ ] **Step 1: Guard `sessionSurface?.greedy == true` next to YIELD checks**

- [ ] **Step 2: Commit**

```bash
git commit -m "fix: do not heal or pin over a greedy KEEP session"
```

### Task 16: Spec + dual-paint cross-links

**Files:**
- Modify: `docs/split-session-ownership.md` — mark phases implemented; add `GGSession` log tag
- Modify: `docs/dual-paint-invariants.md` — heal denied during YIELD / greedy KEEP (one bullet, already sketched)

- [ ] **Step 1: Update docs to match shipped behavior (no new policy)**

- [ ] **Step 2: Commit**

```bash
git commit -m "docs: note shipped split-session heal rules"
```

Phase 4 device gate: pick one greedy KEEP package if any exists on the Sugar; confirm no companion spawn mid-game; HOME restores both panels; `adb logcat -s GGSession` shows one greedy line. If none is greedy, document “none observed” — do not invent a YIELD.

---

## Out of scope (do not implement in this plan)

- Shoulder HUD
- Black-panel pixel oracle / `PixelCopy`
- `Settings.yieldPackages` UI (resolver already accepts `packageYield` for a later schema)
- Per-ROM `romProfiles` policy override UI (`resolve` already accepts `romOverride`)
- Quickstep / Recents replacement
- Changing launch display ids for YIELD players

## Self-review

| Spec section | Tasks |
|---|---|
| Dual-surface keeps both screens | 2, 8, 9, device gate 2 |
| Per-player not per-platform | 1, 2, 5 |
| Default KEEP | 1, 2 |
| Pack field | 2 |
| Session record | 3, 4 |
| KEEP lifecycle | 6, 7, 11, 12, 13 |
| YIELD lifecycle | 6, 7, 8, 9 |
| Return / swap / SECONDARY_HOME | 6, 7, 9 |
| Pin honesty | 5, 11 |
| Dual-paint additions | 6, 9, 12, 16 |
| Greedy | 14, 15 |
| Settings label | 10 |
| Host tests / verify rg | 1–6, 12, 14 |
| Device matrix | Phase 2 and 3 gates |

No `DUAL_CLAIM_PLATFORMS` after Task 5. `OpenSession` remains playtime-only. `SessionSurface` is the session-policy record.
