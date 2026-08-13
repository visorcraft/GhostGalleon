# Owned-surface depth

How Ghost Galleon turns the panel it still owns — and the library that
feeds it — into something no other launcher can copy.

This spec **consumes** [`split-session-ownership.md`](split-session-ownership.md)
and [`keep-play-surface.md`](keep-play-surface.md). It does not weaken
yield. Dual-paint rules in
[`dual-paint-invariants.md`](dual-paint-invariants.md) stay in force.

**Dual-surface games keep both screens.** melonDualDS and Azahar
(`YIELD_BOTH`, or greedy KEEP) get **no** play HUD, **no** input lock,
**no** RAM lens, **no** Winlator cockpit, **no** session handoff from
inside the game, **no** `PixelCopy`, and **no** RetroArch / Accessibility
injection. Ghost Galleon must not steal a panel, pin over them, or
overlay a HUD on them while they run.

**Single-surface games keep a live companion.** That owned panel is
already a play HUD. This spec makes it *playable* (input), *true*
(handoff), *useful* (cockpit / RAM), and makes launch + library honest
(choreography / identity).

KEEP play-surface v1 explicitly left RAM peek, force-stop-on-switch,
and per-ROM session-policy UI out. This spec **reopens those three**
as new work. It does not reopen overlays, TaskView-of-the-game, or
`MANAGE_EXTERNAL_STORAGE`.

## Why one spec

The six features share one policy spine. A RAM lens that paints on a
DS panel, a cockpit that injects into Azahar, or a stage plot that
launches melonDualDS as KEEP-without-confirm would undo 0.10 / 0.11.
They ship as **one program, six phases**, not six side quests.

```
split-session (shipped)     KEEP play surface (shipped)
        \                       /
         \                     /
          v                   v
              owned-surface
     1 input → 2 handoff → 3 choreography
               ↘         ↙
            4 cockpit   5 RAM lenses
                    ↘
                 6 identity (parallel after 1)
```

Phase 4 needs 1 (touch cockpit while the pad stays with Winlator).
Phase 5 needs the RA UDP client (shipped) and the play host (shipped).
Phase 2 needs the session ring (shipped). Phase 3 writes the per-title
override that `SessionPolicy.resolve` already accepts. Phase 6 does
not touch session policy; it may run beside 4–5.

## Terms

| Term | Meaning |
|---|---|
| **KEEP / yield / greedy / play host / owned display / launch display** | Unchanged from KEEP play surface. |
| **Input owner** | Who should receive the pad: `GAME`, `HOST`, or `NONE`. |
| **Focus lock** | Play-host window is visible and touchable but not key-focusable, so the KEEP game keeps the pad. |
| **Input assist** | Optional `AccessibilityService`. Off by default. Never required. |
| **Handoff** | Switcher path that prepares the current KEEP session before launching the target. |
| **RAM lens** | Read-only companion view driven by RetroArch `READ_CORE_RAM`. |
| **Cockpit** | Play-host chrome for Winlator: keyboard + trackpad. Not an overlay. |
| **RomIdentity** | Content fingerprint beside `RomEntry`. Does **not** replace `RomEntry.id`. |
| **Stage plot** | Per-title launch choreography: policy override + launch face. |
| **Launch face** | Which topology role receives `setLaunchDisplayId` at fire time. |

Display ids are never hard-coded `0`/`1`. Roles come from
`DisplayTopology` / `DeckState.primaryDisplayId`.

## Non-goals (all six)

- Do not add `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`.
- Do not `ActivityEmbed` / TaskView the **open session** package
  (`pinConflictsWithSession` stays).
- Do not inject keys, RAM, or gestures into a YIELD or greedy session.
- Do not replace Android Recents or patch Quickstep.
- Do not add Compose or a new Gradle dependency.
- Do not request `MANAGE_EXTERNAL_STORAGE` or broad storage.
- Do not change `RomEntry.id` / `SlotKey` (playtime, dock, favorites,
  names, hidden ids stay path-stable).
- Do not `WRITE_CORE_RAM`, poke cheats, or auto-load a savestate on
  the destination in v1.
- Do not `force-stop` the previous package on switch.
- Do not guess YIELD from a platform id. Player id still wins.
- SINGLE topology: input lock, cockpit, RAM lens, and play-host
  handoff UI are no-ops (one display; the game covers it). Identity
  and stage-plot storage still work.

## Invariants

1. `sessionOwnsCompanionDisplay` (YIELD or greedy) ⇒ no focus lock, no
   assist filter, no RAM UDP, no cockpit, no handoff UI, no PixelCopy,
   no play HUD. Same gate as KEEP play surface.
2. `playHostAllowed` is still the only paint gate for HUD / cockpit /
   lens / switcher chrome.
3. Network / assist / hash never call `updateSettings`,
   `notifyChanged()`, or `publishRomEntries`. In-place views only.
   Identity publish is a **library-ready** event after a scan, same
   as today’s ROM index — not a 60 Hz path.
4. One outstanding RA datagram. Lenses, probes, pause, and handoff
   save share `enqueueRaUdp`. They queue; they do not pile up.
5. User overrides that would steal a dual-surface player’s second
   panel require an explicit confirm. Defaults never do that.

---

## 1 — Input ownership

### Problem

KEEP is visually solved and input-broken. Android normally has one
focused window. Touch the play HUD and the pad can leave the game.

### Owner

```
enum class InputOwner { GAME, HOST, NONE }

fun inputOwner(
    dualMode: Boolean,
    policy: SessionPolicy?,
    greedy: Boolean,
    playHostAllowed: Boolean,
): InputOwner {
    if (!dualMode) return InputOwner.NONE
    if (policy == SessionPolicy.YIELD_BOTH || greedy) return InputOwner.NONE
    if (policy == SessionPolicy.KEEP_COMPANION && playHostAllowed) {
        return InputOwner.GAME   // default; HOST is a process-only flip
    }
    return InputOwner.NONE
}
```

`NONE` = today’s behavior (idle HOME, SINGLE, yield).

The process-only flip (`GAME` ↔ `HOST`) lives on `GhostGalleonApp`,
next to `playHudExpanded`. It is **not** persisted. Yield, greedy,
`clearSessionSurface`, and `onPause` of the game’s return-to-HOME
reset it to `GAME` (or `NONE` if the session is gone).

### v1: focus lock (no new permission)

When `inputOwner == GAME` and this activity is the **play host**:

- Set `WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE` on **that
  window only**.
- Do **not** set `FLAG_NOT_TOUCHABLE`. Touch on the HUD still works.
- Do **not** apply this to a non-host GG activity (the launch-display
  GG window should not exist; if it does, KEEP heal rules already
  forbid painting there).

Keys go to the KEEP game. HUD chips are tap targets.

Claim `HOST` when any of these happen:

- Touch down on the play host.
- `Action.CLAIM_HOST` (new; default **unmapped** — user binds it in
  Controls if they want a pad chord without touching).
- Opening the session switcher.

Release to `GAME` when:

- `Action.RELEASE_HOST` (new; default unmapped).
- Play-host idle **8000 ms** with no touch and no key (HOST only).
- Switcher closes without launching.
- Session ends, yield starts, or greedy is marked.

On `HOST`, clear `FLAG_NOT_FOCUSABLE` so the pad drives the HUD /
switcher / cockpit keyboard. The game will likely lose the pad. That
is honest and visible (HUD can show a small “pad → launcher” hint).

SWAP during KEEP does not change `SessionPolicy`. Recalculate the
play host; apply flags only there. Owner resets to `GAME`.

### v1b: input assist (optional)

Settings → Controls → **Input assist** (default **off**).

- Ships an `AccessibilityService` with
  `canRequestFilterKeyEvents`. No other Accessibility capabilities
  in v1b except `canPerformGestures` (needed by the cockpit, §4).
- Enabling in GG only **deep-links** to the system Accessibility
  screen. GG does not flip the service on by itself.
- When the service is connected **and** `inputOwner == GAME`:
  filter-key events may consume `CLAIM_HOST` / `RELEASE_HOST` and
  the host chord so those keys never reach the game. Gameplay keys
  pass through because the game remains the focused window (focus
  lock still on).
- The service must refuse to bind any filter when
  `sessionOwnsCompanionDisplay`. If yield starts, disable filtering
  immediately.
- The service must not log key codes to disk. No keylogger.

v1 ships and is useful **without** v1b. Do not block 1 on Google
review of an Accessibility service. If assist is not certified in
time, ship focus lock alone.

### What it does not do

- Does not inject into melonDualDS / Azahar / greedy packages.
- Does not keep the game focused **and** drive the HUD from the pad
  without assist (impossible on single-focus Android without
  injection).
- Does not use `Instrumentation` / `INJECT_EVENTS`.

### Settings / actions

| Field / action | Default | Role |
|---|---|---|
| `Action.CLAIM_HOST` | unmapped | Pad chord → HOST |
| `Action.RELEASE_HOST` | unmapped | Pad chord → GAME |
| `inputHostTimeoutMs` | 8000 | HOST → GAME idle |
| `inputAssistEnabled` | false | Preference to show the system deep-link; not the OS grant |

Log: `adb logcat -s GGInput`  
`owner=GAME\|HOST\|NONE host=<activity> flags=notFocusable`

---

## 2 — Instant session handoff

### Problem

Today the switcher calls `launchSlotKey` on the target. The previous
KEEP title is whatever Android paused. RetroArch can do better:
pause and save first.

### Pure planner

```
enum class HandoffPrep { NONE, RA_PAUSE_SAVE }

data class HandoffPlan(
    val result: SwitchToResult,   // existing NO_OP / REFUSE_YIELD / LAUNCH
    val prep: HandoffPrep,
)

fun SessionHandoff.plan(
    current: SessionSurface?,
    target: SessionRingEntry,
    raNetworkCommands: Boolean,
    raHandoffSave: Boolean,
): HandoffPlan
```

Rules, in order:

1. Reuse `SessionSwitch.decide` for `result`.
2. If `result != LAUNCH`, `prep = NONE`.
3. If current is not KEEP (null / yield / greedy), `prep = NONE`
   (`REFUSE_YIELD` already covers an open yield).
4. If `raNetworkCommands && raHandoffSave` and the current player is
   RA (`playerId` starts with `ra-` or package is
   `com.retroarch.aarch64`), `prep = RA_PAUSE_SAVE`.
5. Else `prep = NONE` (DraStic, PPSSPP, Winlator, apps: launch only).

### Execute

On the play host, after the switcher row is confirmed:

1. Close the switcher (in-place).
2. If `prep == RA_PAUSE_SAVE`: `enqueueRaUdp` once:
   - `GET_STATUS`; if PLAYING, `PAUSE_TOGGLE`.
   - `SAVE_STATE` (current slot; fire-and-forget).
   - Do not wait for a SAVE reply (RA sends none).
   - Bound the whole prep to **400 ms**, then launch anyway.
3. `launchSlotKey(target.key)` — existing path (`noteLaunch` then
   `beginSession`).
4. If the target is `YIELD_BOTH`, `closeQuietly` still runs. That is
   success. Do not respawn the HUD.
5. If the target is KEEP, the play host stays; HUD rebinds.

Do **not** `LOAD_STATE` on the destination in v1. A KEEP → KEEP RA
switch launches the target ROM; RetroArch’s own last-content rules
apply. Auto-load is a later toggle (`raHandoffLoad`, default off,
out of this spec’s v1).

Do **not** `force-stop` the previous package. Android pauses it.

### Failure

- RA link down: skip prep, launch anyway. No toast storm.
- Prep timeout: launch anyway.
- Yield current: still refuse (cannot open the switcher there).

### Settings

| Field | Default | Role |
|---|---|---|
| `raHandoffSave` | true | Used only when Talk to RetroArch is on |

A Library row under Talk to RetroArch: **Save before switching**.
Off = today’s launch-only switcher.

KEEP play-surface “optional kill previous RA” stays **out**.

---

## 3 — Per-title display choreography

### Problem

Policy is per **player** (correct). Users still need a per-**title**
and per-**package** override: this one DS ROM in DraStic (KEEP), that
rare dual-screen Android app (YIELD), this Winlator title on the
larger panel.

`SessionPolicy.resolve` already accepts `romOverride` and
`packageYield`. There is no UI and no launch-face override.

### Stage plot

```
enum class LaunchFace { AUTO, INTERACTIVE, COMPANION, OTHER }

data class StagePlot(
    val policy: SessionPolicy?,   // null = do not override
    val launchFace: LaunchFace,   // AUTO = topology launchDisplayId
)
```

Resolution at fire time (`StagePlot.resolve`), first non-null wins:

1. Per-ROM plot in `Settings.stagePlots[romId]`.
2. Platform-pack plot on the chosen `PlayerTemplate` (optional JSON
   `stagePlot`; missing = unset).
3. Per-package yield in `Settings.packageYield[packageName]`
   (true ⇒ `policy = YIELD_BOTH`, `launchFace = AUTO`).
4. `SessionPolicy.forPlayerId` + `LaunchFace.AUTO`.

`SessionSurface.forLaunch` takes the resolved policy the same way it
does today (`romOverride` / `packageYield`).

**YIELD ignores launch face.** Dual-surface players must keep today’s
`launchOnOtherDisplay` path (`setLaunchDisplayId` on the topology
launch display, then the game claims the other panel itself). A plot
must not pin melonDualDS / Azahar to a single face.

KEEP launch display becomes:

```
fun launchDisplayId(
    face: LaunchFace,
    interactiveId: Int?,
    companionId: Int?,
    launchId: Int?,
): Int? = when (face) {
    LaunchFace.AUTO -> launchId
    LaunchFace.INTERACTIVE -> interactiveId
    LaunchFace.COMPANION -> companionId
    LaunchFace.OTHER -> launchId   // topology “other” == today’s launch
}
```

`OTHER` and `AUTO` are equal on current Sugar Auto (`launch` is
already the non-interactive panel). Both exist so a future topology
that launches on interactive can still say “the other one.”

If a KEEP face puts the game on the display that would have been the
play host, `playHostAllowed` is false (host == launch). That is
allowed: no HUD, no lock, no cockpit. Do not heal onto that display.

Android **apps** use `packageYield` only. Per-app launch face is out
of v1 (one map, not two).

### Confirmations

| User action | Dialog |
|---|---|
| Set KEEP on a player whose built-in policy is YIELD | “This player is built for both screens. Ghost Galleon will keep a panel. melonDualDS / Azahar will not see the second display.” Require Confirm. |
| Set YIELD on a KEEP player | “Both panels will go to the game. The play HUD will hide.” Require Confirm. |
| Set a launch face that equals the only display | Disabled (SINGLE). |

Cancel leaves the previous plot.

Clearing the plot returns to player default. No confirm.

### Settings UI

- ROM long-press / details: **Screens** row → KEEP / YIELD / Default,
  plus launch face (Auto / Interactive / Other).
- Settings → Apps: per-package **Uses both screens** toggle
  (`packageYield`). Default off. This is the row split-session already
  promised.
- Platform pack JSON may include `"sessionPolicy"` (already) and
  `"launchFace": "auto"|"interactive"|"companion"|"other"`.

### What it does not do

- Does not persist greedy. Greedy stays process-only.
- Does not change SWAP. SWAP is runtime; the plot is launch-time.
- Does not letterbox or density-override the game (hidden compat
  APIs stay unused).
- Does not auto-YIELD after one greedy run.

---

## 4 — Winlator cockpit

### When

`playHostAllowed` **and** current `playerId` is `winlator` or
`winlator-main` **and** `Settings.winlatorCockpit` is on (default
**on**).

YIELD / greedy / non-Winlator KEEP: no cockpit.

### Chrome

The play host **replaces** the generic KEEP action row with cockpit
chrome. Art + title + clock stay. Session switcher chip stays.

| Control | v1 |
|---|---|
| Keyboard | Companion `InputMethod` when owner is `HOST`. Hidden when owner is `GAME` (focus lock). A “Keyboard” chip claims HOST and shows the IME. |
| Trackpad | A full-bleed pad under the chips. Motion is **not** sent unless input assist is connected. |
| Mouse buttons | LMB / RMB / MMB chips. Same assist gate as the pad. |
| Hint | If assist is off: one line, “Enable Input assist to move the mouse.” Keyboard still works via HOST + IME. |

No `SYSTEM_ALERT_WINDOW`. The cockpit is a child of the play host,
same as the HUD.

### Pointer inject (assist only)

When the Accessibility service is connected and the session is KEEP
Winlator:

- Map pad coords (0..1) to the **launch display**’s bounds.
- `dispatchGesture` click / drag on that display (API 30+
  `displayId` when present).
- If display-targeted gestures are missing, disable the pad and keep
  the hint. Do not send gestures to the play host display.

Refuse all inject when `sessionOwnsCompanionDisplay`.

### What it does not do

- Does not embed Winlator in the companion (`ActivityEmbed` still
  blocked for the open session package).
- Does not assume a Bluetooth mouse.
- Does not send keys to Winlator via assist in v1 (IME on HOST is
  enough for typing; the game has the pad under focus lock).

---

## 5 — Companion RAM lenses

### When

`playHostAllowed` **and** Talk to RetroArch is on **and**
`ramLensesEnabled` is on (default **off**) **and** the current player
is RA **and** a lens matches the open ROM.

Supersedes KEEP play-surface “Do not use RA … RAM peek … in v1.”

### Protocol

One extra RA command, same UDP client, same 200 ms timeout, same
single-flight rule:

```
READ_CORE_RAM <hex_address> <byte_count>\n
```

Reply: hex bytes (RetroArch’s usual ASCII). Parse in `RaCommand`.
Never send `WRITE_CORE_RAM`.

Limits:

- At most **256** bytes per tick.
- At most **5 Hz** (200 ms). Slower if `enqueueRaUdp` is busy.
- 3 consecutive parse / timeout failures → disable the **current**
  lens for the process (same idea as the slot strip). HUD stays.

### Lens files

Bundled under `assets/lenses/*.json`. Optional user pack: a SAF
document picked in Settings → Library → **Import lens pack**. Invalid
JSON is ignored. No network fetch of lenses.

```
{
  "id": "snes-alttp-items",
  "title": "A Link to the Past — items",
  "match": { "platformId": "snes", "hash": ["<sha1>", "..."] },
  "intervalMs": 200,
  "blocks": [
    { "address": "0x7EF340", "length": 16, "format": "bitfield", "labels": ["bow", "boomerang"] }
  ]
}
```

Match order:

1. `RomIdentity.hash` (phase 6). If identity is not ready, skip hash
   match (do not block launch).
2. Optional `match.romId` exact `RomEntry.id`.
3. No match → no lens. Never apply a lens to the wrong core.

v1 bundled set is **small and proven**: start with 0–3 lenses that
have host-tested parse fixtures (synthetic RAM). Shipping zero
bundled lenses is allowed; the engine and one fixture lens in tests
are the gate. Do not invent a 200-game atlas in this spec.

### Paint

A compact panel **below** the KEEP HUD clock (or replacing the RA
slot strip when a lens is active). In-place `setText` / bit views.
No `notifyChanged`. No SETTINGS rebuild.

Owner `GAME` (focus lock): lens is display-only — correct, because
the pad is in the game.

### What it does not do

- No writes, cheats, or speed hacks.
- No lens on DraStic / melonDualDS / Azahar / PPSSPP.
- No 60 Hz. No second UDP socket.

---

## 6 — Library identity

### Problem

`RomEntry.id` is `<platformId>:<relativePath>`. Renames fork
playtime, art overrides, and collections. Disc sets and clones look
like unrelated games.

### Sidecar, not a new id

```
data class RomIdentity(
    val romId: String,            // existing RomEntry.id
    val algo: String,             // "sha1-payload" | "sha256-sample" | "sfo-title" | "dat-crc"
    val hash: String?,            // hex, or null if not yet computed
    val headerTitle: String?,
    val groupId: String?,         // same hash / same DAT clone set
    val discIndex: Int?,          // 1-based if known
    val ready: Boolean,
)
```

`SlotKey`, dock, favorites, `romNames`, `hiddenRomIds`, and
`romProfiles` stay on `RomEntry.id`. Identity is an index the UI
may *read*.

### When / where

After a scan, on `ROM_IO` (same pool as arcade rematch). Never on
first paint. `holdFirstPaintUntilReady` stays about the **path
index**, not hashes.

Persist `filesDir/rom_identity.json` (atomic write, like the ROM
index). Missing file = all unknown. Schema of this file is
independent of Settings.

### Algorithms

| Kind | Algo | Input |
|---|---|---|
| Size ≤ 32 MiB, known header (iNES, SNES, GB/GBC/GBA) | `sha1-payload` | File bytes after documented header strip |
| Size ≤ 32 MiB, unknown | `sha1-payload` | Whole file |
| Size > 32 MiB | `sha256-sample` | 8-byte LE size + SHA-256(head 64 KiB + mid 64 KiB + tail 64 KiB) |
| Vita | `sfo-title` | Existing `VitaSfo` titleId (already uppercased) |
| Arcade | `dat-crc` | DAT crc/name when the imported / bundled DAT has the rom |
| Disc set (`.m3u` / `.cue`) | group = parent playlist id; members sampled individually | Scanner already prefers m3u/cue |

SAF: read via the existing tree grant. If a read fails, `ready=false`,
no crash, retry on next rescan.

### UI

- Details sheet: algorithm, short hash, group, disc index. Copy hash
  on long-press.
- Game Mode: no grouping by default. Settings → Library → **Stack
  clones** (default **off**) folds `groupId` into one carousel card
  (primary = last launched in the group). Off = today’s flat list.
- RAM lenses (phase 5) use `hash` when `ready`.

### What it does not do

- Does not rewrite `RomEntry.id` or migrate playtime onto hashes
  (a later, explicit migrator can offer “merge rename”; not v1).
- Does not download redump DATs. User may import an arcade DAT
  (already shipped).
- Does not hash on the UI thread. Does not delay first paint.

---

## Data / settings (schema v10)

`Settings.schemaVersion` becomes **10**. `SettingsStoreTest` covers
missing-v10 → defaults. All new fields optional on disk.

| Field | Default | Phase |
|---|---|---|
| `inputHostTimeoutMs: Int` | 8000 | 1 |
| `inputAssistEnabled: Boolean` | false | 1b |
| `raHandoffSave: Boolean` | true | 2 |
| `stagePlots: Map<String, StagePlotJson>` | empty | 3 |
| `packageYield: Map<String, Boolean>` | empty | 3 |
| `winlatorCockpit: Boolean` | true | 4 |
| `ramLensesEnabled: Boolean` | false | 5 |
| `ramLensPackUri: String?` | null | 5 |

Process-only (not Settings):

- `inputOwnerFlip: InputOwner` (GAME/HOST)
- `lensDisabledThisProcess: Set<String>`
- existing `playHudExpanded`, `sessionSurface.greedy`

New strings (all five catalogs, then `i18n_audit.py --write && --check`):

- `action_claim_host`, `action_release_host`
- `input_owner_game`, `input_owner_host`
- `settings_input_assist`, `settings_input_assist_open_system`
- `settings_ra_handoff_save`
- `settings_stage_plot`, `settings_package_yield`
- `confirm_keep_on_yield_player`, `confirm_yield_on_keep_player`
- `cockpit_keyboard`, `cockpit_need_assist`
- `settings_ram_lenses`, `settings_import_lens_pack`
- `settings_stack_clones`, `identity_hash`, `identity_not_ready`

Log tags: `GGInput`, `GGHandoff`, `GGLens`, `GGIdent`, plus existing
`GGSession` / `GGOracle` / `GGPaint`.

## Dual-paint additions

- Focus-lock flag flips are **not** SETTINGS. No `setContentView`.
- HOST ↔ GAME is not SELECTION unless the HUD must rewrite a hint
  line (in-place `setText`).
- Handoff prep is not a deck notify.
- Lens ticks follow the KEEP clock rule: in-place only.
- Identity ready → existing library publish path (one notify after
  the batch, never per-file).
- Stage-plot edits are Settings (user-driven). One chrome/settings
  notify is allowed. Do not notify from scan.

## Phases

Ship in this order. Phase 6 may overlap 4–5 after 1.

| Phase | Ships | Depends on |
|---|---|---|
| **1 — Input** | `InputOwner`, focus lock, CLAIM/RELEASE, timeout, `GGInput` | KEEP play host |
| **1b — Assist** | Accessibility service, filter-key, cockpit gestures | 1; optional |
| **2 — Handoff** | `SessionHandoff`, RA pause+save, Library toggle | Session ring + RA UDP |
| **3 — Choreography** | `StagePlot`, Screens UI, `packageYield`, confirm dialogs | `SessionPolicy.resolve` |
| **4 — Cockpit** | Winlator HUD chrome, IME, assist trackpad | 1 (1b for mouse) |
| **5 — Lenses** | `READ_CORE_RAM`, lens JSON, in-place panel | RA UDP; identity hash optional |
| **6 — Identity** | `RomIdentity`, sidecar file, details, optional stack | ROM_IO scan |

Do not ship 4 before 1: a cockpit that steals the pad on first touch
is worse than no cockpit. Do not ship 5 on by default. Do not ship
3’s KEEP-on-melonDualDS without the confirm dialog.

Host tests are not device proof. Do not claim the Sugar matrix from
this doc.

## Device matrix (Sugar)

**Not run.** Intended checks, not observed proof.

| Step | Must see |
|---|---|
| RA SNES KEEP, do not touch HUD | Pad stays in game. HUD clock still ticks. |
| Tap HUD | Owner HOST. Pad drives HUD. Hint visible. |
| Idle 8s | Owner GAME. Pad back in game. |
| melonDualDS | No focus lock, no HUD, no assist filter, both panels DS. |
| Switch KEEP SNES → KEEP GBA, Talk+handoff on | RA pauses/saves (no crash), GBA launches, HUD retitles. |
| Switch KEEP SNES → melonDualDS | Prep save, then **both** panels DS. No Companion. |
| Screens: KEEP on a DraStic ROM | Next launch KEEP HUD. melonDualDS default still yields. |
| Screens: KEEP on melonDualDS + Confirm | Game may be single-panel — user asked. Cancel = still yield. |
| Package yield on a dual-screen app | Both panels to the app. No HUD. |
| Winlator KEEP, assist off | Keyboard chip works (HOST+IME). Trackpad shows hint, no inject. |
| Winlator KEEP, assist on | Trackpad moves cursor on **launch** display only. |
| RAM lens off | No `READ_CORE_RAM`. |
| RAM lens on, matching fixture | Panel updates ≤5 Hz. `GGPaint` has no FULL storm. |
| RAM lens on, melonDualDS | No UDP. |
| Rename a small ROM, rescan | New `RomEntry.id`; identity hash matches previous; playtime stays on old id (v1). Details show both. |
| Stack clones off | Flat carousel (today). |

## Host tests (pure)

- `inputOwner`: yield/greedy/SINGLE → NONE; KEEP+playHost → GAME.
- Focus-lock allowed only when owner==GAME and playHostAllowed.
- `SessionHandoff.plan`: refuse yield; RA+toggle → RA_PAUSE_SAVE;
  DraStic → NONE; same key → NO_OP.
- `StagePlot.resolve`: rom plot > pack > packageYield > player;
  confirm-required flags for KEEP-on-yield-player and YIELD-on-keep.
- `launchDisplayId` face table (null-safe).
- `RaCommand.encode` / parse for `READ_CORE_RAM`; reject write helper
  (no encode for WRITE in v1).
- Lens match: hash hit, romId hit, no match, length>256 rejected.
- Identity: header strip vectors; sample hash stable; large-file path
  does not read the whole array in the test fixture.
- Dual-paint: existing resume / heal / playHost tables unchanged.

## Verification (agents)

```text
verify: rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src  # must stay gone
verify: rg -n "WRITE_CORE_RAM" app/src  # must stay gone
verify: rg -n "FLAG_NOT_FOCUSABLE|InputOwner|SessionHandoff|StagePlot|READ_CORE_RAM|RomIdentity" app/src/main/java
verify: python3 scripts/i18n_audit.py --check
verify: ./gradlew :app:testDebugUnitTest --offline --tests '*SessionPolicy*' --tests '*PlayHost*' --tests '*SessionSwitch*' --tests '*RaCommand*' --tests '*RomProfiles*' --tests '*DualPaint*'
```

Device: run the matrix. Host green is not a Sugar claim.

Task-by-task implementation plan (all phases):
[`superpowers/plans/2026-08-13-owned-surface.md`](superpowers/plans/2026-08-13-owned-surface.md).

## Code map (intended)

| Unit | Kind | Phase |
|---|---|---|
| `input/InputOwner.kt` | Pure | 1 |
| `rom/SessionHandoff.kt` | Pure | 2 |
| `rom/StagePlot.kt` | Pure | 3 |
| `rom/RaCommand.kt` | Pure + existing client | 2, 5 |
| `rom/RomIdentity.kt` | Pure | 6 |
| `rom/LensCatalog.kt` | Pure | 5 |
| Play-host window flags | Android, play host only | 1 |
| Optional `InputAssistService` | Android, Settings opt-in | 1b, 4 |
| Cockpit views in `CompanionPanel` | Android | 4 |
| Lens views in `CompanionPanel` | Android | 5 |
| `filesDir/rom_identity.json` | ROM_IO | 6 |

No third session type. `OpenSession` stays playtime.
`SessionSurface` stays the policy record. Stage plot feeds
`forLaunch`; it does not become a third clock.
