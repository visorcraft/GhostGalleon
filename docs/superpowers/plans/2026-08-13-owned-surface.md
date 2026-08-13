# Owned-surface depth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the KEEP-owned panel playable (input), honest (handoff), steerable (choreography), useful (Winlator cockpit + RAM lenses), and make the library rename-proof (identity) — without ever touching a yielded melonDualDS / Azahar panel.

**Architecture:** Pure host-testable units (`InputOwner`, `InputAssistPolicy`, `SessionHandoff`, `StagePlot`, `CockpitPolicy`, `LensCatalog`, `RomIdentity`) sit beside shipped `SessionPolicy` / `PlayHostPolicy` / `SessionSwitch` / `RaCommand`. Schema **v10** lands once with every new field default-safe. Android code only applies window flags, Views, one optional AccessibilityService, SAF reads, and the existing `enqueueRaUdp` client. No third session clock.

**Tech Stack:** Kotlin, classic Views, host JUnit (`./gradlew :app:testDebugUnitTest --offline`), no new Gradle dependencies, no Compose.

**Spec:** [`docs/owned-surface.md`](../../owned-surface.md)

## Global Constraints

- Dual-surface games **keep both screens**. Never steal a panel from melonDualDS or Azahar.
- `sessionOwnsCompanionDisplay` (YIELD_BOTH or greedy) ⇒ no focus lock, no assist filter, no RAM UDP, no cockpit, no handoff UI, no PixelCopy, no play HUD.
- `playHostAllowed` is the only paint gate for HUD / cockpit / lens / switcher chrome.
- No `SYSTEM_ALERT_WINDOW` / overlay windows. No `WRITE_CORE_RAM`. No `force-stop` on switch. No `MANAGE_EXTERNAL_STORAGE`.
- Do not `ActivityEmbed` the open session package. Do not change `RomEntry.id` / `SlotKey`.
- YIELD ignores launch face. KEEP-on-a-YIELD-player requires Confirm.
- Display ids from `DisplayTopology` — never hard-code `0`/`1`.
- Android `Display` / `AccessibilityService` types stay out of the pure units.
- `OpenSession` stays playtime-only. `SessionSurface` stays the policy record.
- Network / assist / hash never call `updateSettings` / `notifyChanged` / `publishRomEntries` except identity’s one post-batch library publish and user-driven Settings edits.
- One outstanding RA datagram (`enqueueRaUdp`).
- New strings in all five catalogs, then `python3 scripts/i18n_audit.py --write && --check`.
- Commits: Conventional Commits, human author only, no AI attribution.
- Device claims require the Sugar matrix in the spec. Host green is not a Sugar claim.
- SINGLE: input lock, cockpit, lens, play-host handoff UI are no-ops. Identity and stage plots still persist.

## File map

| File | Role | Phase |
|---|---|---|
| `input/InputOwner.kt` | `inputOwner` / `effectiveOwner` / `focusLockAllowed` | 1 |
| `input/InputAssistPolicy.kt` | `mayFilterKeys` / `mayInjectPointer` | 1b |
| `settings/Action.kt` | `CLAIM_HOST`, `RELEASE_HOST` | 1 |
| `settings/Settings.kt` + `SettingsStore.kt` | Schema v10 | 1 (all fields) |
| `GhostGalleonApp.kt` | `hostClaimed`, reset, identity sidecar hook | 1, 6 |
| `ui/BaseDeckActivity.kt` | New Actions, switcher handoff, launch face | 1, 2, 3 |
| `ui/CompanionActivity.kt` + `ui/MainActivity.kt` | Focus-lock flags, HOST timeout | 1 |
| `ui/deck/CompanionPanel.kt` | Owner hint, cockpit, lens panel | 1, 4, 5 |
| `input/InputAssistService.kt` | Optional AccessibilityService | 1b, 4 |
| `AndroidManifest.xml` | Service + accessibility XML | 1b |
| `rom/SessionHandoff.kt` | Pure planner | 2 |
| `rom/StagePlot.kt` | Plot resolve, launch face, confirm | 3 |
| `rom/PlatformPack.kt` + `Platform.kt` | Optional `launchFace` on templates | 3 |
| `rom/RomLauncher.kt` + `ui/deck/Deck.kt` | `packageYield`, launch face | 3 |
| `rom/CockpitPolicy.kt` | When cockpit may bind | 4 |
| `rom/RaCommand.kt` | `READ_CORE_RAM` encode/parse | 5 |
| `rom/LensCatalog.kt` | JSON match + length cap | 5 |
| `rom/RomIdentity.kt` + store | Algorithms + sidecar | 6 |
| `ui/settings/SettingsActivity.kt` | New rows | 1b–6 |
| Tests listed per task | Host-only, Robolectric-free | |

---

## Phase 1 — Input ownership

Done when: host tests prove owner is NONE for yield/greedy/SINGLE and GAME for KEEP play host; CLAIM/RELEASE have labels; v10 settings exist; play host applies `FLAG_NOT_FOCUSABLE` only when `focusLockAllowed`; tap/switcher claims HOST; 8s idle and session end reset; yield never locks.

### Task 1: InputOwner policy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/input/InputOwner.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/input/InputOwnerTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class InputOwner { GAME, HOST, NONE }

object InputOwnerPolicy {
    fun inputOwner(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        playHostAllowed: Boolean,
    ): InputOwner

    fun effectiveOwner(base: InputOwner, hostClaimed: Boolean): InputOwner

    fun focusLockAllowed(owner: InputOwner, playHostAllowed: Boolean): Boolean
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.input

import com.visorcraft.ghostgalleon.rom.SessionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputOwnerTest {

    @Test
    fun `keep play host defaults to GAME`() {
        assertEquals(
            InputOwner.GAME,
            InputOwnerPolicy.inputOwner(
                dualMode = true,
                policy = SessionPolicy.KEEP_COMPANION,
                greedy = false,
                playHostAllowed = true,
            ),
        )
    }

    @Test
    fun `yield greedy single and no host are NONE`() {
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, SessionPolicy.YIELD_BOTH, false, true),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, SessionPolicy.KEEP_COMPANION, true, true),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(false, SessionPolicy.KEEP_COMPANION, false, true),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, SessionPolicy.KEEP_COMPANION, false, false),
        )
        assertEquals(
            InputOwner.NONE,
            InputOwnerPolicy.inputOwner(true, null, false, false),
        )
    }

    @Test
    fun `hostClaimed only flips GAME`() {
        assertEquals(InputOwner.HOST, InputOwnerPolicy.effectiveOwner(InputOwner.GAME, true))
        assertEquals(InputOwner.GAME, InputOwnerPolicy.effectiveOwner(InputOwner.GAME, false))
        assertEquals(InputOwner.NONE, InputOwnerPolicy.effectiveOwner(InputOwner.NONE, true))
        assertEquals(InputOwner.HOST, InputOwnerPolicy.effectiveOwner(InputOwner.HOST, false))
    }

    @Test
    fun `focus lock only when GAME and play host`() {
        assertTrue(InputOwnerPolicy.focusLockAllowed(InputOwner.GAME, true))
        assertFalse(InputOwnerPolicy.focusLockAllowed(InputOwner.HOST, true))
        assertFalse(InputOwnerPolicy.focusLockAllowed(InputOwner.NONE, true))
        assertFalse(InputOwnerPolicy.focusLockAllowed(InputOwner.GAME, false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputOwnerTest'`

Expected: compile fail (`Unresolved reference: InputOwnerPolicy`)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.input

import com.visorcraft.ghostgalleon.rom.SessionPolicy

enum class InputOwner { GAME, HOST, NONE }

object InputOwnerPolicy {
    fun inputOwner(
        dualMode: Boolean,
        policy: SessionPolicy?,
        greedy: Boolean,
        playHostAllowed: Boolean,
    ): InputOwner {
        if (!dualMode) return InputOwner.NONE
        if (policy == SessionPolicy.YIELD_BOTH || greedy) return InputOwner.NONE
        if (policy == SessionPolicy.KEEP_COMPANION && playHostAllowed) return InputOwner.GAME
        return InputOwner.NONE
    }

    fun effectiveOwner(base: InputOwner, hostClaimed: Boolean): InputOwner {
        if (base == InputOwner.NONE) return InputOwner.NONE
        if (base == InputOwner.HOST) return InputOwner.HOST
        return if (hostClaimed) InputOwner.HOST else InputOwner.GAME
    }

    fun focusLockAllowed(owner: InputOwner, playHostAllowed: Boolean): Boolean =
        owner == InputOwner.GAME && playHostAllowed
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputOwnerTest'`

Expected: BUILD SUCCESSFUL, tests pass

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/input/InputOwner.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/input/InputOwnerTest.kt
git commit -m "feat: add InputOwner policy for KEEP play host"
```

### Task 2: CLAIM_HOST and RELEASE_HOST actions

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/settings/Action.kt`
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/settings/ActionLabelTest.kt`
- Modify: `app/src/main/res/values/strings.xml` (and `values-es`, `values-de`, `values-fr`, `values-th`)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/ControllerLabActivity.kt` (add the two actions to the lab list next to `TOGGLE_PLAY_HUD`)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt` (remap list includes new actions automatically if it iterates `Action.entries`; if it uses an explicit list, add them)

**Interfaces:**
- Produces: `Action.CLAIM_HOST`, `Action.RELEASE_HOST` with `UiText` labels. Default keymap stays unmapped.

- [ ] **Step 1: Write the failing test**

Add to `ActionLabelTest`:

```kotlin
    @Test
    fun `input owner actions have user-friendly labels`() {
        assertEquals(text(R.string.action_claim_host), Action.CLAIM_HOST.label())
        assertEquals(text(R.string.action_release_host), Action.RELEASE_HOST.label())
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.ActionLabelTest'`

Expected: compile fail (`Unresolved reference: CLAIM_HOST`)

- [ ] **Step 3: Write minimal implementation**

In `Action` enum, before `NONE`:

```kotlin
    CLAIM_HOST, RELEASE_HOST,
```

In `Action.label()`:

```kotlin
    Action.CLAIM_HOST -> R.string.action_claim_host
    Action.RELEASE_HOST -> R.string.action_release_host
```

Add to all five `strings.xml` catalogs (do not interpolate):

```xml
    <string name="action_claim_host">Pad to launcher</string>
    <string name="action_release_host">Pad to game</string>
    <string name="input_owner_game">Pad → game</string>
    <string name="input_owner_host">Pad → launcher</string>
```

Spanish / German / French / Thai: same keys, translated. Then:

```bash
python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check
```

Expected: `localization inventory and UI text sinks OK`

Add `Action.CLAIM_HOST, Action.RELEASE_HOST` beside `TOGGLE_PLAY_HUD` in Controller Lab and Settings remap lists if those lists are explicit.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.ActionLabelTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/settings/Action.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/settings/ActionLabelTest.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/ControllerLabActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  docs/localization-inventory.md
git commit -m "feat: add claim/release host pad actions"
```

### Task 3: Settings schema v10

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/settings/Settings.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/settings/SettingsStore.kt` (`CURRENT_SCHEMA = 10`, parse/save new fields)
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/settings/SettingsStoreTest.kt`
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/StagePlot.kt` (data + JSON helpers used by the store; resolve comes in Task 10)

**Interfaces:**
- Produces: `Settings` fields with these defaults:

```kotlin
val inputHostTimeoutMs: Int = 8000
val inputAssistEnabled: Boolean = false
val raHandoffSave: Boolean = true
val stagePlots: Map<String, StagePlot> = emptyMap()
val packageYield: Map<String, Boolean> = emptyMap()
val winlatorCockpit: Boolean = true
val ramLensesEnabled: Boolean = false
val ramLensPackUri: String? = null
val stackClones: Boolean = false
```

```kotlin
// rom/StagePlot.kt — persist helpers only in this task
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

        fun fromJson(o: org.json.JSONObject): StagePlot = StagePlot(
            policy = if (o.has("policy") && !o.isNull("policy")) {
                SessionPolicy.parse(o.optString("policy"))
            } else {
                null
            },
            launchFace = parse(o.optString("launchFace", "auto")),
        )

        fun toJson(plot: StagePlot): org.json.JSONObject = org.json.JSONObject().apply {
            if (plot.policy != null) put("policy", plot.policy.name)
            put("launchFace", plot.launchFace.name.lowercase())
        }
    }
}
```

Missing v10 keys load as defaults. `schemaVersion` stamps 10 on save.

- [ ] **Step 1: Write the failing test**

Add to `SettingsStoreTest`:

```kotlin
    @Test
    fun `v10 owned-surface fields round-trip and missing keys default`() {
        val f = tmp.root.resolve("cfg-v10/settings.json")
        val plot = StagePlot(SessionPolicy.KEEP_COMPANION, LaunchFace.INTERACTIVE)
        val s = Settings.DEFAULT.copy(
            schemaVersion = 10,
            inputHostTimeoutMs = 5000,
            inputAssistEnabled = true,
            raHandoffSave = false,
            stagePlots = mapOf("snes:x.sfc" to plot),
            packageYield = mapOf("com.example.dual" to true),
            winlatorCockpit = false,
            ramLensesEnabled = true,
            ramLensPackUri = "content://lenses/pack.json",
            stackClones = true,
        )
        SettingsStore(f).save(s)
        val loaded = SettingsStore(f).load()
        assertEquals(5000, loaded.inputHostTimeoutMs)
        assertEquals(true, loaded.inputAssistEnabled)
        assertEquals(false, loaded.raHandoffSave)
        assertEquals(plot, loaded.stagePlots["snes:x.sfc"])
        assertEquals(true, loaded.packageYield["com.example.dual"])
        assertEquals(false, loaded.winlatorCockpit)
        assertEquals(true, loaded.ramLensesEnabled)
        assertEquals("content://lenses/pack.json", loaded.ramLensPackUri)
        assertEquals(true, loaded.stackClones)
        assertEquals(10, loaded.schemaVersion)

        val raw = org.json.JSONObject(f.readText())
        raw.put("schemaVersion", 9)
        raw.remove("inputHostTimeoutMs")
        raw.remove("stagePlots")
        raw.remove("packageYield")
        raw.remove("stackClones")
        f.writeText(raw.toString())
        val migrated = SettingsStore(f).load()
        assertEquals(8000, migrated.inputHostTimeoutMs)
        assertEquals(true, migrated.raHandoffSave)
        assertTrue(migrated.stagePlots.isEmpty())
        assertTrue(migrated.packageYield.isEmpty())
        assertEquals(false, migrated.stackClones)
        assertEquals(10, migrated.schemaVersion)
    }
```

Import `StagePlot`, `LaunchFace`, `SessionPolicy`. Existing `assertEquals(9, loaded.schemaVersion)` in the v8-migrate test must become `10`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.SettingsStoreTest'`

Expected: compile fail or assertion fail on missing fields / schema 9

- [ ] **Step 3: Write minimal implementation**

Add the `Settings` fields. Set `CURRENT_SCHEMA = 10`. In `parse`, read:

```kotlin
inputHostTimeoutMs = o.optInt("inputHostTimeoutMs", 8000).coerceIn(1_000, 60_000),
inputAssistEnabled = o.optBoolean("inputAssistEnabled", false),
raHandoffSave = o.optBoolean("raHandoffSave", true),
stagePlots = o.optJSONObject("stagePlots").toStagePlotMap(),
packageYield = o.optJSONObject("packageYield").toBooleanMap(),
winlatorCockpit = o.optBoolean("winlatorCockpit", true),
ramLensesEnabled = o.optBoolean("ramLensesEnabled", false),
ramLensPackUri = o.optString("ramLensPackUri", "").ifBlank { null },
stackClones = o.optBoolean("stackClones", false),
```

`toStagePlotMap`: each value is a JSONObject → `StagePlot.fromJson`. `toBooleanMap`: each value `optBoolean`. Write the inverse in `toJson`.

Do **not** implement `StagePlot.resolve` yet (Task 10). Persist helpers only.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.settings.SettingsStoreTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/settings/Settings.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/settings/SettingsStore.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/rom/StagePlot.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/settings/SettingsStoreTest.kt
git commit -m "feat: persist owned-surface settings as schema v10"
```

### Task 4: Focus lock on the play host

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt` (process-only `hostClaimed`)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/CompanionActivity.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/MainActivity.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt` (owner hint `TextView` tag `play_hud_owner`)
- Test: extend `InputOwnerTest` if any new pure helper appears; no Robolectric

**Interfaces:**
- Consumes: `InputOwnerPolicy.*`, `PlayHostPolicy.playHostAllowed`
- Produces:

```kotlin
// GhostGalleonApp
var hostClaimed: Boolean = false
    private set
fun claimHost() { hostClaimed = true }
fun releaseHost() { hostClaimed = false }
```

Reset `hostClaimed = false` in `clearSessionSurface`, `markSessionGreedy`, and `beginSession` (new session starts GAME).

Shared helper (put on `BaseDeckActivity`):

```kotlin
protected fun applyPlayHostFocusLock() {
    val surface = app.sessionSurface
    val dual = app.displayConfig.mode == SurfaceMode.DUAL
    val allowed = PlayHostPolicy.playHostAllowed(
        dualMode = dual,
        policy = surface?.policy,
        greedy = surface?.greedy == true,
        hostDisplayId = currentDisplayId(),
        launchDisplayId = surface?.launchDisplayId,
    )
    val base = InputOwnerPolicy.inputOwner(
        dual = dual,
        policy = surface?.policy,
        greedy = surface?.greedy == true,
        playHostAllowed = allowed,
    )
    val owner = InputOwnerPolicy.effectiveOwner(base, app.hostClaimed)
    val lock = InputOwnerPolicy.focusLockAllowed(owner, allowed)
    val w = window ?: return
    val params = w.attributes
    if (lock) params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
    else params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
    w.attributes = params
    CompanionPanel.bindOwnerHint(window.decorView, owner)
    if (owner == InputOwner.HOST) armHostTimeout() else disarmHostTimeout()
    android.util.Log.i("GGInput", "owner=$owner host=${javaClass.simpleName} lock=$lock")
}
```

`armHostTimeout`: `Handler` posts `releaseHost()` + `applyPlayHostFocusLock()` after `settings.inputHostTimeoutMs`. Touch on the play-host content (`setOnTouchListener` that returns **false**) calls `claimHost()` + re-arms. Do not consume the touch.

Call `applyPlayHostFocusLock()` from play-host `onResume`, `onContentRebuilt`, and after claim/release. Yield / `closeQuietly` path must clear the flag (owner NONE).

HUD hint: `CompanionPanel.bindOwnerHint(root, owner)` sets tag `play_hud_owner` to `R.string.input_owner_game` / `input_owner_host` or GONE when `NONE`. In-place `setText` only.

- [ ] **Step 1: Write the failing test**

No new Android test. Extend `InputOwnerTest` only if you extract `shouldClearHostClaim(sessionGone: Boolean, greedy: Boolean, yield: Boolean): Boolean` — if so:

```kotlin
    @Test
    fun `host claim clears when session is gone yield or greedy`() {
        assertTrue(InputOwnerPolicy.shouldClearHostClaim(true, false, false))
        assertTrue(InputOwnerPolicy.shouldClearHostClaim(false, true, false))
        assertTrue(InputOwnerPolicy.shouldClearHostClaim(false, false, true))
        assertFalse(InputOwnerPolicy.shouldClearHostClaim(false, false, false))
    }
```

If you keep resets inline in `GhostGalleonApp`, skip this extra test and rely on Task 1.

- [ ] **Step 2: Run existing InputOwner tests**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputOwnerTest'`

Expected: PASS (baseline)

- [ ] **Step 3: Write the Android wiring**

Implement `hostClaimed` / `claimHost` / `releaseHost` / resets. Implement `applyPlayHostFocusLock` on `BaseDeckActivity`. Wire Companion + Main `onResume` / `onContentRebuilt`. Add hint view in `buildPlayHud` (GONE by default). Never set `FLAG_NOT_TOUCHABLE`. Never apply the flag when `focusLockAllowed` is false.

- [ ] **Step 4: Run host tests**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputOwnerTest' --tests 'com.visorcraft.ghostgalleon.ui.PlayHostPolicyTest' --tests 'com.visorcraft.ghostgalleon.ui.DualPaintPolicyTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/CompanionActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/MainActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/input/InputOwnerTest.kt
git commit -m "feat: lock play-host key focus while KEEP pad is in-game"
```

### Task 5: Claim / release / switcher / SWAP resets

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt` (`handleGlobalAction` for `CLAIM_HOST` / `RELEASE_HOST`; `openSessionSwitcher` claims host; onClose releases if no launch)
- Modify: SWAP path in `BaseDeckActivity` — after swap, `app.releaseHost()` + `applyPlayHostFocusLock()`
- Test: none new if policy is already covered

**Interfaces:**
- Consumes: `Action.CLAIM_HOST`, `Action.RELEASE_HOST`, `app.claimHost()` / `releaseHost()`

- [ ] **Step 1: Write the failing test**

No new pure function required. If you extract switcher-claim:

```kotlin
fun switcherShouldClaimHost(owner: InputOwner): Boolean =
    owner == InputOwner.GAME || owner == InputOwner.HOST
```

Test it true for GAME/HOST, false for NONE.

- [ ] **Step 2: Run to verify fail or skip**

If no new function, go to Step 3.

- [ ] **Step 3: Wire actions**

```kotlin
Action.CLAIM_HOST -> {
    if (repeatCount == 0) {
        app.claimHost()
        applyPlayHostFocusLock()
    }
    true
}
Action.RELEASE_HOST -> {
    if (repeatCount == 0) {
        app.releaseHost()
        applyPlayHostFocusLock()
    }
    true
}
```

In `openSessionSwitcher` after `allowed`: `app.claimHost(); applyPlayHostFocusLock()`.
`onClose`: `SessionSwitcherView.detach`; `app.releaseHost()` only if `sessionSurface` is still KEEP and no launch started (LAUNCH path leaves claim until the new session’s `beginSession` resets it).
SWAP: `app.releaseHost()` then re-apply flags on both activities (`liveDeckActivities().forEach` apply if you expose the method as internal).

- [ ] **Step 4: Run host tests**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.*' --tests 'com.visorcraft.ghostgalleon.ui.PlayHostPolicyTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt
git commit -m "feat: claim and release KEEP pad ownership from actions"
```

---

## Phase 1b — Input assist (optional)

Done when: pure policy refuses filter/inject on yield; Settings deep-links to system Accessibility; service is a no-op until connected; no key codes written to disk.

### Task 6: InputAssistPolicy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/input/InputAssistPolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
object InputAssistPolicy {
    fun mayFilterKeys(
        assistConnected: Boolean,
        owner: InputOwner,
        sessionOwnsCompanion: Boolean,
    ): Boolean

    fun mayInjectPointer(
        assistConnected: Boolean,
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        playerId: String?,
    ): Boolean
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputAssistPolicyTest {

    @Test
    fun `filter only when connected GAME and not yield`() {
        assertTrue(InputAssistPolicy.mayFilterKeys(true, InputOwner.GAME, false))
        assertFalse(InputAssistPolicy.mayFilterKeys(false, InputOwner.GAME, false))
        assertFalse(InputAssistPolicy.mayFilterKeys(true, InputOwner.HOST, false))
        assertFalse(InputAssistPolicy.mayFilterKeys(true, InputOwner.GAME, true))
        assertFalse(InputAssistPolicy.mayFilterKeys(true, InputOwner.NONE, false))
    }

    @Test
    fun `pointer only for KEEP winlator play host`() {
        assertTrue(
            InputAssistPolicy.mayInjectPointer(true, true, false, "winlator"),
        )
        assertTrue(
            InputAssistPolicy.mayInjectPointer(true, true, false, "winlator-main"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(true, true, false, "ra-snes9x"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(true, true, true, "winlator"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(false, true, false, "winlator"),
        )
        assertFalse(
            InputAssistPolicy.mayInjectPointer(true, false, false, "winlator"),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputAssistPolicyTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.input

object InputAssistPolicy {
    fun mayFilterKeys(
        assistConnected: Boolean,
        owner: InputOwner,
        sessionOwnsCompanion: Boolean,
    ): Boolean = assistConnected && owner == InputOwner.GAME && !sessionOwnsCompanion

    fun mayInjectPointer(
        assistConnected: Boolean,
        playHostAllowed: Boolean,
        sessionOwnsCompanion: Boolean,
        playerId: String?,
    ): Boolean {
        if (!assistConnected || !playHostAllowed || sessionOwnsCompanion) return false
        val id = playerId?.trim().orEmpty()
        return id == "winlator" || id == "winlator-main"
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputAssistPolicyTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/input/InputAssistPolicyTest.kt
git commit -m "feat: gate input-assist filter and pointer inject"
```

### Task 7: Accessibility service + Settings deep-link

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistService.kt`
- Create: `app/src/main/res/xml/input_assist_service.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt` (Controls row)
- Modify: five `strings.xml` (`settings_input_assist`, `settings_input_assist_open_system`)
- Test: none (Android service). Policy already tested.

**Interfaces:**
- Consumes: `InputAssistPolicy.mayFilterKeys`
- Produces: optional service; `GhostGalleonApp.inputAssistConnected: Boolean`

Service rules:

- `android:canRequestFilterKeyEvents="true"` and `android:canPerformGestures="true"` only.
- `onKeyEvent`: if `!mayFilterKeys` return false. If the key maps to `CLAIM_HOST` / `RELEASE_HOST`, post those Actions on the main handler and return **true** (consume). Otherwise return false (gameplay passes through).
- Never write key codes to logs or disk. Log at most `GGInput assist filter=on|off`.
- `onServiceConnected` / `onUnbind` flip `app.inputAssistConnected`.
- If `sessionOwnsCompanionDisplay`, return false immediately.

Manifest inside `<application>`:

```xml
        <service
            android:name=".input.InputAssistService"
            android:exported="true"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/input_assist_service" />
        </service>
```

Settings row: toggling `inputAssistEnabled` only stores the preference and starts

```kotlin
Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
```

GG does not enable the service itself.

- [ ] **Step 1: i18n strings + audit**

Add keys to all five catalogs. Run `python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check`.

- [ ] **Step 2: Implement service + row**

- [ ] **Step 3: Confirm no overlay permission**

Run: `rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src`

Expected: no matches

- [ ] **Step 4: Host tests still pass**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.*'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistService.kt \
  app/src/main/res/xml/input_assist_service.xml \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt
git commit -m "feat: optional input-assist Accessibility service"
```

---

## Phase 2 — Instant session handoff

Done when: `SessionHandoff.plan` matches the spec table; switcher confirm runs RA pause+save once then `launchSlotKey`; yield still refuses; Library toggle exists; no `force-stop`.

### Task 8: SessionHandoff.plan

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionHandoff.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/SessionHandoffTest.kt`

**Interfaces:**
- Consumes: `SessionSwitch.decide`
- Produces:

```kotlin
enum class HandoffPrep { NONE, RA_PAUSE_SAVE }

data class HandoffPlan(val result: SwitchToResult, val prep: HandoffPrep)

object SessionHandoff {
    const val RA_PACKAGE = "com.retroarch.aarch64"
    const val PREP_BUDGET_MS = 400L

    fun isRaPlayer(playerId: String?, packageName: String?): Boolean

    fun plan(
        current: SessionSurface?,
        target: SessionRingEntry,
        raNetworkCommands: Boolean,
        raHandoffSave: Boolean,
    ): HandoffPlan
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHandoffTest {

    private fun keepRa() = SessionSurface(
        key = "rom:snes:a.sfc",
        playerId = "ra-snes9x",
        packageName = SessionHandoff.RA_PACKAGE,
        policy = SessionPolicy.KEEP_COMPANION,
        launchDisplayId = 0,
    )

    private fun target(key: String, player: String, pkg: String, policy: SessionPolicy) =
        SessionRingEntry(key, player, pkg, policy, 1L, "t")

    @Test
    fun `isRaPlayer`() {
        assertTrue(SessionHandoff.isRaPlayer("ra-snes9x", "x"))
        assertTrue(SessionHandoff.isRaPlayer(null, SessionHandoff.RA_PACKAGE))
        assertFalse(SessionHandoff.isRaPlayer("drastic", "com.dsemu.drastic"))
    }

    @Test
    fun `same key is no-op without prep`() {
        val t = target(keepRa().key, keepRa().playerId!!, keepRa().packageName, SessionPolicy.KEEP_COMPANION)
        val p = SessionHandoff.plan(keepRa(), t, true, true)
        assertEquals(SwitchToResult.NO_OP, p.result)
        assertEquals(HandoffPrep.NONE, p.prep)
    }

    @Test
    fun `yield current refuses`() {
        val cur = keepRa().copy(policy = SessionPolicy.YIELD_BOTH, playerId = "melondualds")
        val t = target("rom:snes:b.sfc", "ra-snes9x", SessionHandoff.RA_PACKAGE, SessionPolicy.KEEP_COMPANION)
        val p = SessionHandoff.plan(cur, t, true, true)
        assertEquals(SwitchToResult.REFUSE_YIELD, p.result)
        assertEquals(HandoffPrep.NONE, p.prep)
    }

    @Test
    fun `ra keep to other title preps when toggle on`() {
        val t = target("rom:gba:b.gba", "ra-mgba", SessionHandoff.RA_PACKAGE, SessionPolicy.KEEP_COMPANION)
        val on = SessionHandoff.plan(keepRa(), t, true, true)
        assertEquals(SwitchToResult.LAUNCH, on.result)
        assertEquals(HandoffPrep.RA_PAUSE_SAVE, on.prep)
        val off = SessionHandoff.plan(keepRa(), t, true, false)
        assertEquals(HandoffPrep.NONE, off.prep)
        val talkOff = SessionHandoff.plan(keepRa(), t, false, true)
        assertEquals(HandoffPrep.NONE, talkOff.prep)
    }

    @Test
    fun `drastic keep launches without prep`() {
        val cur = keepRa().copy(playerId = "drastic", packageName = "com.dsemu.drastic")
        val t = target("rom:gba:b.gba", "ra-mgba", SessionHandoff.RA_PACKAGE, SessionPolicy.KEEP_COMPANION)
        val p = SessionHandoff.plan(cur, t, true, true)
        assertEquals(SwitchToResult.LAUNCH, p.result)
        assertEquals(HandoffPrep.NONE, p.prep)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SessionHandoffTest'`

Expected: compile fail

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.visorcraft.ghostgalleon.rom

enum class HandoffPrep { NONE, RA_PAUSE_SAVE }

data class HandoffPlan(val result: SwitchToResult, val prep: HandoffPrep)

object SessionHandoff {
    const val RA_PACKAGE = "com.retroarch.aarch64"
    const val PREP_BUDGET_MS = 400L

    fun isRaPlayer(playerId: String?, packageName: String?): Boolean {
        if (playerId?.startsWith("ra-") == true) return true
        return packageName == RA_PACKAGE
    }

    fun plan(
        current: SessionSurface?,
        target: SessionRingEntry,
        raNetworkCommands: Boolean,
        raHandoffSave: Boolean,
    ): HandoffPlan {
        val result = SessionSwitch.decide(
            current?.key,
            current?.playerId,
            current?.policy,
            current?.greedy == true,
            target,
        )
        if (result != SwitchToResult.LAUNCH) return HandoffPlan(result, HandoffPrep.NONE)
        if (current == null || current.policy != SessionPolicy.KEEP_COMPANION || current.greedy) {
            return HandoffPlan(result, HandoffPrep.NONE)
        }
        val prep =
            if (raNetworkCommands && raHandoffSave && isRaPlayer(current.playerId, current.packageName)) {
                HandoffPrep.RA_PAUSE_SAVE
            } else {
                HandoffPrep.NONE
            }
        return HandoffPlan(result, prep)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SessionHandoffTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/SessionHandoff.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/SessionHandoffTest.kt
git commit -m "feat: plan KEEP session handoff before switcher launch"
```

### Task 9: Execute handoff + Library toggle

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt` (`openSessionSwitcher` onPick)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt` (row under Talk to RetroArch)
- Modify: five `strings.xml` (`settings_ra_handoff_save`)

**Interfaces:**
- Consumes: `SessionHandoff.plan`, `app.enqueueRaUdp`, `RaCommandClient.status` / `pauseToggle` / `saveState`

Replace `SessionSwitch.decide` in `onPick` with `SessionHandoff.plan(...)`. On `LAUNCH`:

```kotlin
SwitchToResult.LAUNCH -> {
    SessionSwitcherView.detach(host)
    val finish = {
        launchSlotKey(
            activity, app.deckState, app.romEntries, target.key,
            playerId = target.playerId,
        )
    }
    if (plan.prep != HandoffPrep.RA_PAUSE_SAVE) {
        finish()
    } else {
        val started = android.os.SystemClock.elapsedRealtime()
        app.enqueueRaUdp(
            work = { client ->
                val port = app.settings.raNetworkCmdPort
                val status = client.status(port)
                if (status == RaStatus.PLAYING) client.pauseToggle(port)
                client.saveState(port)
            },
            onMain = {
                val used = android.os.SystemClock.elapsedRealtime() - started
                android.util.Log.i("GGHandoff", "prep ms=$used")
                finish()
            },
        ) || run { finish(); true }
        // If enqueue refuses (datagram in flight), still launch:
        // enqueueRaUdp returns false → call finish() immediately.
    }
}
```

If `enqueueRaUdp` returns false, call `finish()` immediately. Do not wait longer than the worker naturally takes; do not add a second socket. Do not `force-stop`. Do not `LOAD_STATE`.

Settings row: boolean `raHandoffSave`, default true, only meaningful when `raNetworkCommands` is on (row can stay visible but disabled when talk is off).

- [ ] **Step 1: i18n**

Add `settings_ra_handoff_save` to all five catalogs. `python3 scripts/i18n_audit.py --write && python3 scripts/i18n_audit.py --check`

- [ ] **Step 2: Implement onPick + row**

- [ ] **Step 3: Run tests**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.SessionHandoffTest' --tests 'com.visorcraft.ghostgalleon.rom.SessionSwitch*' --tests 'com.visorcraft.ghostgalleon.rom.RaCommandTest'`

Expected: PASS

- [ ] **Step 4: Confirm no force-stop**

Run: `rg -n "force-stop|forceStop" app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt`

Expected: no new kill of the previous package

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/BaseDeckActivity.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: pause and save RetroArch before session switch"
```

---

## Phase 3 — Display choreography

Done when: `StagePlot.resolve` order is rom > pack > packageYield > player; YIELD ignores launch face; confirm flags match the spec; Screens UI + Apps yield exist; `LaunchSession` / `launchOnOtherDisplay` consume the resolved plot.

### Task 10: StagePlot.resolve

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/StagePlot.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/StagePlotTest.kt`

**Interfaces:**
- Produces:

```kotlin
enum class PlotConfirm { NONE, KEEP_ON_YIELD_PLAYER, YIELD_ON_KEEP_PLAYER }

object StagePlots {
    fun resolve(
        romPlot: StagePlot?,
        packPlot: StagePlot?,
        packageYield: Boolean,
        playerId: String?,
    ): StagePlot

    fun launchDisplayId(
        face: LaunchFace,
        policy: SessionPolicy,
        interactiveId: Int?,
        companionId: Int?,
        launchId: Int?,
    ): Int?

    fun confirmFor(builtIn: SessionPolicy, requested: SessionPolicy?): PlotConfirm
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StagePlotTest {

    @Test
    fun `resolve prefers rom then pack then package then player`() {
        val rom = StagePlot(SessionPolicy.KEEP_COMPANION, LaunchFace.INTERACTIVE)
        val pack = StagePlot(SessionPolicy.YIELD_BOTH, LaunchFace.COMPANION)
        assertEquals(rom, StagePlots.resolve(rom, pack, true, "melondualds"))
        assertEquals(pack, StagePlots.resolve(null, pack, true, "melondualds"))
        val y = StagePlots.resolve(null, null, true, "ra-snes9x")
        assertEquals(SessionPolicy.YIELD_BOTH, y.policy)
        assertEquals(LaunchFace.AUTO, y.launchFace)
        val p = StagePlots.resolve(null, null, false, "melondualds")
        assertEquals(SessionPolicy.YIELD_BOTH, p.policy)
        val k = StagePlots.resolve(null, null, false, "ra-snes9x")
        assertEquals(SessionPolicy.KEEP_COMPANION, k.policy)
    }

    @Test
    fun `yield ignores launch face`() {
        assertEquals(
            0,
            StagePlots.launchDisplayId(LaunchFace.INTERACTIVE, SessionPolicy.YIELD_BOTH, 1, 2, 0),
        )
        assertEquals(
            1,
            StagePlots.launchDisplayId(LaunchFace.INTERACTIVE, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertEquals(
            2,
            StagePlots.launchDisplayId(LaunchFace.COMPANION, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertEquals(
            0,
            StagePlots.launchDisplayId(LaunchFace.AUTO, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertEquals(
            0,
            StagePlots.launchDisplayId(LaunchFace.OTHER, SessionPolicy.KEEP_COMPANION, 1, 2, 0),
        )
        assertNull(
            StagePlots.launchDisplayId(LaunchFace.AUTO, SessionPolicy.KEEP_COMPANION, null, null, null),
        )
    }

    @Test
    fun `confirm when overriding built-in policy`() {
        assertEquals(
            PlotConfirm.KEEP_ON_YIELD_PLAYER,
            StagePlots.confirmFor(SessionPolicy.YIELD_BOTH, SessionPolicy.KEEP_COMPANION),
        )
        assertEquals(
            PlotConfirm.YIELD_ON_KEEP_PLAYER,
            StagePlots.confirmFor(SessionPolicy.KEEP_COMPANION, SessionPolicy.YIELD_BOTH),
        )
        assertEquals(
            PlotConfirm.NONE,
            StagePlots.confirmFor(SessionPolicy.YIELD_BOTH, SessionPolicy.YIELD_BOTH),
        )
        assertEquals(
            PlotConfirm.NONE,
            StagePlots.confirmFor(SessionPolicy.YIELD_BOTH, null),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.StagePlotTest'`

Expected: compile fail (`Unresolved reference: StagePlots`)

- [ ] **Step 3: Write minimal implementation**

```kotlin
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
```

Put this in the same `StagePlot.kt` file as the data class.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.StagePlotTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/StagePlot.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/StagePlotTest.kt
git commit -m "feat: resolve per-title stage plots and launch faces"
```

### Task 11: Pack launchFace + fire-time wiring

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/Platform.kt` (`PlayerTemplate.launchFace: LaunchFace = AUTO`)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/PlatformPack.kt` (parse `"launchFace"`)
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/rom/PlatformPackTest.kt` (or Example test) — one JSON with launchFace
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/RomLauncher.kt` (`LaunchSession.forRom` / `forApp`)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/Deck.kt` (`launchOnOtherDisplay` takes explicit id)
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/Deck.kt` `launchSlotKey` to resolve plot

**Interfaces:**
- Consumes: `StagePlots.resolve`, `Settings.stagePlots`, `Settings.packageYield`
- Produces: `LaunchSession.forRom` still returns `SessionSurface` whose `launchDisplayId` is the **resolved** id; `romOverride` is `resolved.policy` when it differs from `forPlayerId`, or the existing pack-YIELD-only rule plus rom plot.

Resolution inside `launchSlotKey` (ROM path):

```kotlin
val builtIn = SessionPolicy.forPlayerId(template.id)
val romPlot = app.settings.stagePlots[entry.id]
val packPlot = StagePlot(template.sessionPolicy.takeIf { it == SessionPolicy.YIELD_BOTH }, template.launchFace)
    .takeIf { it.policy != null || template.launchFace != LaunchFace.AUTO }
val pkgYield = app.settings.packageYield[template.component.substringBefore('/')] == true
val plot = StagePlots.resolve(romPlot, packPlot, pkgYield, template.id)
val launchId = StagePlots.launchDisplayId(
    plot.launchFace,
    plot.policy ?: builtIn,
    interactiveId = app.displayConfig.interactiveDisplayId,
    companionId = app.displayConfig.companionDisplayId, // use real topology field names
    launchId = app.displayConfig.launchDisplayId,
)
val surface = SessionSurface.forLaunch(
    key = key,
    playerId = template.id,
    packageName = template.component.substringBefore('/'),
    launchDisplayId = launchId,
    packageYield = pkgYield,
    romOverride = plot.policy,
)
```

Look up actual `DisplayConfig` / `ResolvedTopology` property names in `display/DisplayTopology.kt` and use those — do not invent `interactiveDisplayId` if the field is `primaryDisplayId` + `secondaryHomeDisplayId`. Map:

- interactive → `deckState.primaryDisplayId` / topology interactive
- companion → the non-interactive dual display (not launch if they differ; on Sugar Auto companion == secondaryHome)
- launch → `displayConfig.launchDisplayId`

App launches: `packageYield[package] == true` only; launch face AUTO.

Change `launchOnOtherDisplay(activity, state, intent)` to `launchOnOtherDisplay(activity, state, intent, launchDisplayId: Int? = null)`. If `launchDisplayId` is non-null and present, use it; else today’s topology launch id.

- [ ] **Step 1: Write the failing pack test**

Extend an existing pack JSON test: `"launchFace": "interactive"` parses to `LaunchFace.INTERACTIVE`. Missing field → AUTO.

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.PlatformPack*'`

Expected: fail until `PlayerTemplate.launchFace` exists

- [ ] **Step 3: Implement parse + launch wiring**

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.StagePlotTest' --tests 'com.visorcraft.ghostgalleon.rom.SessionSurfaceTest' --tests 'com.visorcraft.ghostgalleon.rom.PlatformPack*' --tests 'com.visorcraft.ghostgalleon.rom.RomLauncherTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/Platform.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/rom/PlatformPack.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/rom/RomLauncher.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/Deck.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/
git commit -m "feat: apply stage plots at ROM launch"
```

### Task 12: Screens UI + package yield

**Files:**
- Modify: ROM details / long-press menu (the existing Game Mode / grid entry menu — `EntryActions` / `GameDetails` sheet). Add a **Screens** item that opens a dialog: Default / KEEP / YIELD + Auto / Interactive / Other. SINGLE disables face buttons.
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt` Apps page: per-package “Uses both screens”
- Modify: five `strings.xml` (`settings_stage_plot`, `settings_package_yield`, `confirm_keep_on_yield_player`, `confirm_yield_on_keep_player`)

**Interfaces:**
- Consumes: `StagePlots.confirmFor`, `SessionPolicy.forPlayerId`
- On save: if `confirmFor != NONE`, `AlertDialog` with the matching string; Cancel = no write. Confirm writes `settings.stagePlots`.

Clear / Default removes the romId from `stagePlots` (no confirm).

Do not notify from anywhere except `updateSettings` (user-driven).

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Implement dialogs**

Use `PlotConfirm` to choose the message. Built-in policy = `SessionPolicy.forPlayerId(preferredPlayerId)`.

- [ ] **Step 3: Run i18n check + unit tests**

Run: `python3 scripts/i18n_audit.py --check && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.StagePlotTest'`

Expected: OK + PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/ \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: per-title screens plot and package yield UI"
```

---

## Phase 4 — Winlator cockpit

Done when: cockpit binds only for KEEP Winlator play host; IME via HOST; trackpad injects only when `mayInjectPointer`; yield shows nothing.

### Task 13: CockpitPolicy

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/CockpitPolicy.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/CockpitPolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
object CockpitPolicy {
    fun cockpitAllowed(
        playHostAllowed: Boolean,
        playerId: String?,
        cockpitEnabled: Boolean,
    ): Boolean
}
```

- [ ] **Step 1: Write the failing test**

```kotlin
package com.visorcraft.ghostgalleon.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitPolicyTest {
    @Test
    fun `only keep winlator play host`() {
        assertTrue(CockpitPolicy.cockpitAllowed(true, "winlator", true))
        assertTrue(CockpitPolicy.cockpitAllowed(true, "winlator-main", true))
        assertFalse(CockpitPolicy.cockpitAllowed(true, "winlator", false))
        assertFalse(CockpitPolicy.cockpitAllowed(false, "winlator", true))
        assertFalse(CockpitPolicy.cockpitAllowed(true, "ra-snes9x", true))
        assertFalse(CockpitPolicy.cockpitAllowed(true, "melondualds", true))
    }
}
```

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.CockpitPolicyTest'`

Expected: compile fail

- [ ] **Step 3: Implement**

```kotlin
package com.visorcraft.ghostgalleon.rom

object CockpitPolicy {
    fun cockpitAllowed(
        playHostAllowed: Boolean,
        playerId: String?,
        cockpitEnabled: Boolean,
    ): Boolean {
        if (!playHostAllowed || !cockpitEnabled) return false
        val id = playerId?.trim().orEmpty()
        return id == "winlator" || id == "winlator-main"
    }
}
```

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.CockpitPolicyTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/CockpitPolicy.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/CockpitPolicyTest.kt
git commit -m "feat: gate Winlator cockpit to KEEP play host"
```

### Task 14: Cockpit chrome + IME

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt` (`buildPlayHud`: if `cockpitAllowed`, replace the generic action row with cockpit chips)
- Modify: five `strings.xml` (`cockpit_keyboard`, `cockpit_need_assist`)
- Modify: `SettingsActivity` Library or System: `winlatorCockpit` toggle (default on)

**Interfaces:**
- Consumes: `CockpitPolicy.cockpitAllowed`, `app.claimHost()`, `InputAssistPolicy.mayInjectPointer` (for hint only)

Chrome:

- Keep art, title, clock, switcher chip.
- Chip “Keyboard”: `claimHost()` + `InputMethodManager.showSoftInput` on a hidden `EditText` in the HUD (exists only to host the IME). `releaseHost` on IME hide if you can listen; else HOST timeout handles it.
- Trackpad `View` filling remaining space. `onTouch` records normalized 0..1. **Do not inject in this task** — if assist not connected, show `cockpit_need_assist`.
- Mouse buttons are no-ops until Task 15 (visible, disabled alpha if no assist).

No overlay window. Tags: `play_hud_cockpit`, `play_hud_trackpad`.

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Bind chrome**

- [ ] **Step 3: Run tests + overlay grep**

Run: `rg -n "SYSTEM_ALERT_WINDOW" app/src && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.CockpitPolicyTest'`

Expected: no overlay permission; PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: show Winlator cockpit keyboard on KEEP play host"
```

### Task 15: Assist pointer inject

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistService.kt`
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt` (trackpad / buttons call a method on the service/app)
- Test: `InputAssistPolicyTest` already covers the gate

**Interfaces:**
- Consumes: `InputAssistPolicy.mayInjectPointer`
- Produces: `GhostGalleonApp.injectLaunchPointer(normX: Float, normY: Float, down: Boolean)` which no-ops unless the service is connected and the gate is true

Implementation:

- Map `normX/Y` to the **launch display** bounds (`Display.getRectangle` / `DisplayMetrics` for `sessionSurface.launchDisplayId`). Never the play-host display.
- API 30+: `GestureDescription.Builder` + `dispatchGesture` with `displayId` if the overload exists (reflect if needed; if missing, disable pad + keep hint).
- Buttons: tap at last pad coord or center of launch display.
- If `sessionOwnsCompanionDisplay`, return immediately.

- [ ] **Step 1: No new host test unless you extract coord mapping**

If you add:

```kotlin
fun mapNormToDisplay(normX: Float, normY: Float, left: Int, top: Int, width: Int, height: Int): Pair<Int, Int>
```

Test corners and clamp 0..1.

- [ ] **Step 2: Implement inject**

- [ ] **Step 3: Run**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.input.InputAssistPolicyTest'`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/input/InputAssistService.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/input/InputAssistPolicyTest.kt
git commit -m "feat: inject Winlator trackpad on the launch display"
```

---

## Phase 5 — RAM lenses

Done when: `READ_CORE_RAM` encodes/parses; no WRITE helper exists; match order is hash then romId; length >256 rejected; HUD ticks ≤5 Hz in-place; default off; yield sends nothing.

### Task 16: READ_CORE_RAM codec

**Files:**
- Modify: `app/src/main/java/com/visorcraft/ghostgalleon/rom/RaCommand.kt`
- Modify: `app/src/test/java/com/visorcraft/ghostgalleon/rom/RaCommandTest.kt`

**Interfaces:**
- Produces:

```kotlin
fun encodeReadCoreRam(address: Int, length: Int): ByteArray?
fun parseRamReply(reply: String?, expectedLen: Int): ByteArray?
```

`encodeReadCoreRam` returns null if `length !in 1..256` or `address < 0`. Command: `READ_CORE_RAM <hex> <len>\n` (uppercase hex address without `0x`, matching RetroArch).

`parseRamReply`: strip command echo, parse remaining hex into bytes; null if size ≠ expectedLen.

Do **not** add `encodeWriteCoreRam`.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `encode READ_CORE_RAM rejects bad length and write is absent`() {
        assertArrayEquals(
            "READ_CORE_RAM 7EF340 16\n".toByteArray(Charsets.US_ASCII),
            RaCommand.encodeReadCoreRam(0x7EF340, 16),
        )
        assertEquals(null, RaCommand.encodeReadCoreRam(0, 0))
        assertEquals(null, RaCommand.encodeReadCoreRam(0, 257))
        assertEquals(null, RaCommand.encodeReadCoreRam(-1, 8))
        assertEquals(0, RaCommand::class.java.methods.count { it.name.contains("Write", ignoreCase = true) })
    }

    @Test
    fun `parse RAM reply`() {
        val bytes = RaCommand.parseRamReply("READ_CORE_RAM 00 01 02 03", 4)
        assertArrayEquals(byteArrayOf(0, 1, 2, 3), bytes)
        assertEquals(null, RaCommand.parseRamReply(null, 4))
        assertEquals(null, RaCommand.parseRamReply("00 01", 4))
    }
```

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RaCommandTest'`

Expected: compile fail

- [ ] **Step 3: Implement encode/parse only**

- [ ] **Step 4: Run to verify pass**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RaCommandTest'`

Expected: PASS

- [ ] **Step 5: Confirm no WRITE**

Run: `rg -n "WRITE_CORE_RAM" app/src`

Expected: no matches

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/RaCommand.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/RaCommandTest.kt
git commit -m "feat: encode and parse RetroArch READ_CORE_RAM"
```

### Task 17: LensCatalog

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/LensCatalog.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/LensCatalogTest.kt`
- Create: `app/src/test/resources/lenses/fixture.json` (or embed JSON string in the test)

**Interfaces:**
- Produces:

```kotlin
data class LensBlock(val address: Int, val length: Int, val format: String, val labels: List<String>)
data class LensSpec(
    val id: String,
    val title: String,
    val platformId: String?,
    val hashes: Set<String>,
    val romIds: Set<String>,
    val intervalMs: Long,
    val blocks: List<LensBlock>,
)

object LensCatalog {
    const val MAX_BYTES = 256
    fun parse(json: String): List<LensSpec>
    fun match(lenses: List<LensSpec>, romId: String?, hash: String?, platformId: String?): LensSpec?
    fun totalBytes(spec: LensSpec): Int
    fun acceptable(spec: LensSpec): Boolean
}
```

Match: first spec where (`hash` non-null and in `hashes`) else (`romId` in `romIds`). `platformId` is an extra constraint when present on the spec. No match → null. `acceptable` if `totalBytes in 1..MAX_BYTES` and every block length > 0.

- [ ] **Step 1: Write the failing test**

Use a JSON string with two lenses: one hash-matched, one romId-matched, one oversized block. Assert order (hash wins over later romId), no match, reject length 300.

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.LensCatalogTest'`

Expected: compile fail

- [ ] **Step 3: Implement parser + match**

Invalid JSON → empty list. Do not throw.

- [ ] **Step 4: Run to verify pass**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/LensCatalog.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/LensCatalogTest.kt
git commit -m "feat: match read-only RAM lenses by hash or rom id"
```

### Task 18: Lens HUD + Settings

**Files:**
- Create: `app/src/main/assets/lenses/.gitkeep` (zero bundled games is allowed)
- Modify: `GhostGalleonApp.kt` — load bundled + optional SAF pack on a background thread into `var lenses: List<LensSpec>`
- Modify: `CompanionPanel` — if `playHostAllowed && raNetworkCommands && ramLensesEnabled && SessionHandoff.isRaPlayer && match != null && acceptable`, show tag `play_hud_lens` under the clock
- Tick from existing play-HUD handler **or** a 200 ms handler that no-ops unless `acceptable`. Use `enqueueRaUdp` to READ each block (or one concatenated command per block, still one flight). 3 failures → add lens id to `app.lensDisabledThisProcess`. In-place `setText` only.
- Settings → Library: `ramLensesEnabled` (default off) + import SAF pack (`ramLensPackUri`)
- Strings: `settings_ram_lenses`, `settings_import_lens_pack`

**Interfaces:**
- Consumes: `LensCatalog.match`, `RaCommand.encodeReadCoreRam`, `RomIdentity` hash if Task 20 already landed; otherwise `hash = null` (romId match still works)

Interval: `min(spec.intervalMs, 200L)` and never faster than one completed UDP.

- [ ] **Step 1: i18n + audit**

- [ ] **Step 2: Implement bind + tick + settings**

Never start the lens ticker when `sessionOwnsCompanionDisplay`. Never `notifyChanged`.

- [ ] **Step 3: Run**

Run: `rg -n "WRITE_CORE_RAM" app/src && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.LensCatalogTest' --tests 'com.visorcraft.ghostgalleon.rom.RaCommandTest'`

Expected: no WRITE; PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/lenses \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/CompanionPanel.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md
git commit -m "feat: paint opt-in RetroArch RAM lenses on the play host"
```

---

## Phase 6 — Library identity

Done when: algorithms are host-tested; sidecar is atomic; scan does not block first paint; details show hash; `stackClones` default off leaves the flat carousel; `RomEntry.id` unchanged.

### Task 19: RomIdentity algorithms

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/RomIdentity.kt`
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/RomIdentityTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class RomIdentity(
    val romId: String,
    val algo: String,
    val hash: String?,
    val headerTitle: String?,
    val groupId: String?,
    val discIndex: Int?,
    val ready: Boolean,
)

object RomIdentities {
    const val ALGO_SHA1_PAYLOAD = "sha1-payload"
    const val ALGO_SHA256_SAMPLE = "sha256-sample"
    const val ALGO_SFO_TITLE = "sfo-title"
    const val ALGO_DAT_CRC = "dat-crc"
    const val SMALL_MAX_BYTES = 32L * 1024 * 1024

    fun stripInes(payload: ByteArray): ByteArray
    fun sha1Hex(bytes: ByteArray): String
    fun sampleSha256(size: Long, head: ByteArray, mid: ByteArray, tail: ByteArray): String
    fun chooseAlgo(size: Long, platformId: String): String
}
```

`stripInes`: if bytes start with `NES\u001a` and size > 16, return `copyOfRange(16, size)`; else return bytes.

`chooseAlgo`: `vita` → `sfo-title`; `arcade` → `dat-crc`; size > SMALL_MAX → sample; else sha1-payload.

`sampleSha256`: hash `size` as 8-byte little-endian + head + mid + tail (each already 64 KiB or shorter).

- [ ] **Step 1: Write the failing test**

Include a 16-byte iNES header fixture + 4 payload bytes. Assert strip, sha1 stability, sample stability, chooseAlgo table, and that sample path does not require a 40 MiB array (pass three tiny chunks).

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RomIdentityTest'`

Expected: compile fail

- [ ] **Step 3: Implement using `java.security.MessageDigest` only**

- [ ] **Step 4: Run to verify pass**

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/RomIdentity.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/RomIdentityTest.kt
git commit -m "feat: compute ROM identity hashes without changing ids"
```

### Task 20: Sidecar store + ROM_IO

**Files:**
- Create: `app/src/main/java/com/visorcraft/ghostgalleon/rom/RomIdentityStore.kt` (pure JSON + File, like `RomLibrary`)
- Test: `app/src/test/java/com/visorcraft/ghostgalleon/rom/RomIdentityStoreTest.kt`
- Modify: `GhostGalleonApp.kt` — after `publishRomEntries` / arcade rematch, `ROM_IO.execute { compute missing; save; post one identity map }`
- Do **not** change `holdFirstPaintUntilReady`

**Interfaces:**
- Produces: `filesDir/rom_identity.json` atomic write; `GhostGalleonApp.romIdentities: Map<String, RomIdentity>`

Compute: for each `RomEntry`, if sidecar already `ready` for that `romId`, keep. Else read via existing SAF/path helpers used by the scanner. Failures → `ready=false`, no crash. Vita uses `VitaSfo` titleId. Arcade uses DAT crc when present.

One main-thread post after the **batch**, not per file. That post may `notifySelectionRefresh` (not `notifyChanged`) so details can read hashes. Do not rebuild decks.

- [ ] **Step 1: Write store round-trip test** on a temp dir (missing file → empty; atomic rename)

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RomIdentityStoreTest'`

Expected: compile fail

- [ ] **Step 3: Implement store + hook ROM_IO**

Log `GGIdent ready=n fail=m` once per batch.

- [ ] **Step 4: Run tests**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RomIdentity*' --tests 'com.visorcraft.ghostgalleon.ui.DualPaintPolicyTest'`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/RomIdentityStore.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/RomIdentityStoreTest.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/GhostGalleonApp.kt
git commit -m "feat: persist ROM identity sidecar off the first paint"
```

### Task 21: Details + optional clone stack

**Files:**
- Modify: details sheet builder (Game Mode / grid long-press details) to show algo, short hash (first 8 + last 8), group, disc. Long-press hash copies to clipboard. If `!ready`, `identity_not_ready`.
- Modify: `GameDeck.buildEntries` (or `installEntries`) — if `settings.stackClones`, fold entries that share a non-null `groupId` into one card; primary = max `lastLaunchedMs` in the group, else first name. Default **off** = today’s list.
- Modify: Settings → Library `stackClones` row
- Modify: five `strings.xml` (`settings_stack_clones`, `identity_hash`, `identity_not_ready`)
- Test: extract `fun stackByGroup(entries, identities, lastLaunchedMs): List<String /* romId */>` and host-test it

**Interfaces:**
- Consumes: `app.romIdentities`, `settings.stackClones`
- Does **not** rewrite `RomEntry.id` or migrate playtime

```kotlin
object IdentityStack {
    fun primaryIds(
        ids: List<String>,
        groupId: (String) -> String?,
        lastLaunchedMs: Map<String, Long>,
    ): List<String>
}
```

- [ ] **Step 1: Write IdentityStack test** (three ids, two share a group → two primaries; off-path is just “do not call”)

- [ ] **Step 2: Run to verify fail**

Run: `./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.IdentityStackTest'`

Expected: compile fail

- [ ] **Step 3: Implement stack + UI + settings**

- [ ] **Step 4: i18n check + tests**

Run: `python3 scripts/i18n_audit.py --check && ./gradlew :app:testDebugUnitTest --offline --tests 'com.visorcraft.ghostgalleon.rom.RomIdentity*' --tests 'com.visorcraft.ghostgalleon.rom.IdentityStackTest'`

Expected: OK + PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/visorcraft/ghostgalleon/rom/IdentityStack.kt \
  app/src/test/java/com/visorcraft/ghostgalleon/rom/IdentityStackTest.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/deck/GameDeck.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/library/GameDetails.kt \
  app/src/main/java/com/visorcraft/ghostgalleon/ui/settings/SettingsActivity.kt \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-es/strings.xml \
  app/src/main/res/values-de/strings.xml \
  app/src/main/res/values-fr/strings.xml \
  app/src/main/res/values-th/strings.xml \
  docs/localization-inventory.md \
  docs/owned-surface.md
git commit -m "feat: show ROM identity and optional clone stacking"
```

After this task, mark phases in `docs/owned-surface.md` as **Implemented (host)** the same way KEEP play surface does, and leave the Sugar matrix **Not run**.

---

## Final verification (after Task 21)

```text
rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src   # must stay gone
rg -n "WRITE_CORE_RAM" app/src                                  # must stay gone
rg -n "FLAG_NOT_FOCUSABLE|InputOwner|SessionHandoff|StagePlot|READ_CORE_RAM|RomIdentity" app/src/main/java
python3 scripts/i18n_audit.py --check
./gradlew :app:testDebugUnitTest --offline
```

Device: run the matrix in `docs/owned-surface.md`. Host green is not a Sugar claim.

## Self-review (plan vs spec)

| Spec section | Tasks |
|---|---|
| Input owner + focus lock + timeout + claim/release | 1–5 |
| Input assist service + no keylog + yield refuse | 6–7 |
| Handoff plan + RA pause/save + no force-stop + no LOAD | 8–9 |
| Stage plot resolve, YIELD ignores face, confirms, package yield, pack JSON | 10–12 |
| Winlator cockpit + IME + assist pointer on launch display | 13–15 |
| READ_CORE_RAM, no WRITE, match, 256 B, 5 Hz, default off | 16–18 |
| Identity sidecar, algos, no id rewrite, details, stack off | 19–21 |
| Schema v10 all fields | 3 |
| Dual-paint / no overlay / no third clock | Global + each Android task |

No TBD steps. Types used later match the Interfaces blocks above.
