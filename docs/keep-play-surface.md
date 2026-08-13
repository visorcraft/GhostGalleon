# KEEP play surface

How Ghost Galleon uses the panel it still owns while a **KEEP** session
runs: a live play HUD, a session switcher, a black-panel pixel oracle,
and a RetroArch command link.

This spec **consumes** [`split-session-ownership.md`](split-session-ownership.md).
It does not change `SessionPolicy`, yield rules, or launch-display
assignment. Dual-paint rules in
[`dual-paint-invariants.md`](dual-paint-invariants.md) stay in force.

**Dual-surface games keep both screens.** melonDualDS and Azahar
(`YIELD_BOTH`, or greedy KEEP) get **no** play HUD, **no** session
switcher chrome, **no** `PixelCopy`, and **no** RetroArch commands
injected at them. Ghost Galleon must not steal a panel, pin over them,
or overlay a HUD on them while they run.

**Single-surface games keep a live companion.** The remaining Ghost
Galleon surface — never the launch display — becomes a play surface
instead of a dead hero.

## Terms

| Term | Meaning |
|---|---|
| **KEEP session** | `sessionSurface.policy == KEEP_COMPANION` and `greedy == false`. |
| **Yielded / greedy** | `sessionOwnsCompanionDisplay` — YIELD_BOTH or greedy KEEP. No play surface. |
| **Launch display** | `sessionSurface.launchDisplayId` (topology id at fire time). The game lives here. |
| **Owned display** | A display that currently hosts `MainActivity` or `CompanionActivity` and is **not** the launch display. |
| **Play host** | The Ghost Galleon activity on an owned display. That is the only place this spec may paint. |
| **Play HUD** | In-place companion chrome for the open KEEP title (art, clock, actions). Not an overlay window. |
| **Session ring** | Last *N* launched sessions (key, player, policy, time). Not Android Recents. |
| **Session switcher** | UI on the play host that picks from the ring. |
| **Pixel oracle** | `PixelCopy` of a Ghost Galleon window we own, to detect presented-black. |
| **RA link** | UDP network-command client to RetroArch on the KEEP launch display. |

Display ids come from `DisplayTopology` / `sessionSurface.launchDisplayId`.
Never hard-code `0`/`1`.

On stock One X Sugar Auto (`primary=1 companion=0 launch=0
secondaryHome=1`): a KEEP game launches on **0** (top).
`CompanionActivity` is placed on **`secondaryHomeDisplayId` (1, bottom)**.
The play host is that companion (or Main, if it is the activity still
visible on 1). The top panel is the game — not a HUD target.

## Non-goals

- Do not use `SYSTEM_ALERT_WINDOW` / overlay windows. Not on yield, not
  on KEEP. The HUD is a view inside an existing GG activity.
- Do not `PixelCopy` a yielded or greedy display, or the KEEP launch
  display (that buffer is the game).
- Do not inject keys into melonDualDS / Azahar “to drive the HUD.”
- Do not replace Android Recents or patch Quickstep.
- Do not `ActivityEmbed` / TaskView the KEEP game onto the play host
  (`pinConflictsWithSession` already forbids embedding the game package).
- Do not rewrite RetroArch’s cfg without an explicit Settings opt-in.
- Do not request `MANAGE_EXTERNAL_STORAGE` for savestate thumbs.
- Do not add Compose or new Gradle dependencies.
- SINGLE topology: play HUD / switcher / oracle are no-ops (one display,
  one activity; the game covers it). RA link may still run if the user
  returns to HOME, but there is no second panel to show it on.

## Relationship to shipped session policy

| Situation | This spec |
|---|---|
| `YIELD_BOTH` open | No HUD, no switcher, no oracle, no RA UDP. `closeQuietly` stays. |
| Greedy KEEP open | Same as yield. |
| KEEP open, owned display exists | Play host shows HUD. Switcher allowed. Oracle may sample the **host** window. RA link allowed if the player is RetroArch. |
| KEEP open, no owned display | Do nothing. Do not heal onto `launchDisplayId` (`keepHealBlocked`). |
| No session (idle HOME) | No play HUD. Switcher may open from Quick Panel / a new Action. Oracle may sample companion if dual and idle. |
| HOME return | Existing `resumeCompanionAction` + `clearSessionSurface`. Then idle rules. |

`OpenSession` stays playtime-only. `SessionSurface` stays the policy
record. The play HUD **reads** both; it does not grow a third clock.

## 1 — Play HUD

### Who paints

Resolve the play host each time chrome is bound:

```
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
```

Call this from `CompanionPanel` / `MainActivity` before attaching HUD
views. If false, keep today’s Now Playing / hero / idle chrome (or
nothing, on yield).

Do not move `CompanionActivity` onto the launch display to “make room”
for a HUD.

### What it shows

One column, compact on the short panel (`CompanionHeroMetrics` rules).
Clock and RA status mutate `TextView`s in place. Never SETTINGS, BROWSE,
or SELECTION for a clock tick or RA status. There is no
`DeckState.Change.PLAY_HUD`.

| Row | Content | Notes |
|---|---|---|
| Art | Existing tile for `sessionSurface.key` | Skip decode if the tile already shows that ROM (`ArtTile` same-rom). |
| Title | Library name for the key | One line compact / two lines tall. |
| Player | `PlayerTemplate.displayName` | Not the platform id. |
| Clock | `SessionTracker.activeElapsedMs` | Tick every 1s in-place. Do not `notifyChanged()`. |
| RA line | Existing `heroLine` if credentials | `RaProgressGate` still one fetch per ROM per process. |
| Actions | See below | Fixed-height chips. |

Default KEEP preference: if `companionRole` is HERO, the play HUD
**replaces** hero for the open title (same as today’s “open session
requires Now Playing”). PERF_HUD remains allowed as a **tab** / role
switch, still in-place. PINNED_APP stays blocked when
`pinConflictsWithSession`.

### Actions (KEEP play HUD)

| Chip | Behavior |
|---|---|
| **Switcher** | Opens the session switcher on the play host (section 2). |
| **Favorite** | Existing toggle. SELECTION. |
| **Open with** | Existing player picker for this key. Relaunch uses `launchSlotKey` (noteLaunch then beginSession). |
| **End session** | Existing `clearOpenSession` + `clearSessionSurface`. Does **not** `force-stop` the game. HUD goes idle. |
| **Reclaim HOME** | Accrue playtime; `clearSessionSurface`; `restartCompanionPanel("return-from-keep-hud")` only if the companion is missing. Does not kill the game process. |
| **RA: Pause** | Section 4. Hidden if RA link is down or player is not RetroArch. |
| **RA: Save** | Section 4. Slot = last selected (default 1). |
| **RA: Load** | Section 4. Opens slot strip, then load. |

No “inject HOME into the emulator.” Reclaim is Ghost Galleon’s own
resume path.

Swap (`action_swap` on today’s Now Playing card): keep it. SWAP during
KEEP already flips the topology pin and must **not** place Companion on
the launch display (`keepHealBlocked`, `allowCompanionRestartDuringSwap`
is true for KEEP). After swap, re-resolve the play host.

### Input

New `Action` values (Controller Lab + Settings remap, five locales):

| Action | Default | When KEEP + play host exists | Otherwise |
|---|---|---|---|
| `OPEN_SESSION_SWITCHER` | unbound (user binds L2 / Select-hold later) | Open switcher on play host | Open switcher on interactive (idle) |
| `TOGGLE_PLAY_HUD` | unbound | Cycle HUD compact ↔ actions-expanded | NONE |

Existing `OPEN_QUICK_PANEL` (Select) stays on the **interactive** deck.
Do not steal Select from a KEEP game if the interactive activity is
stopped — the game owns that display’s keys. Play-host keys apply only
while a Ghost Galleon window on the owned display has focus (touch on
the HUD, or global input routed to an activity whose `displayId` is the
owned display).

Global input must not send `NAV_*` / `CONFIRM` into RetroArch when the
user is touching the HUD. Route by **focused GG activity display id**,
not by “a session is open.”

YIELD: no new actions fire. Game owns input.

### Paint

- Bind HUD views once per role change. Clock and RA status mutate
  `TextView`s.
- `MIN_FULL_RENDER_GAP_MS` still applies if a role change needs a
  rebuild.
- Do not `setContentView` on a 1s timer.
- Companion never opens the all-apps drawer (dual-paint §9).

## 2 — Session switcher

### Why not Recents

Android Recents is Quickstep. This switcher is **ours**: it knows
`SessionPolicy`, the winning `PlayerTemplate.id`, and that a yield
session cannot show chrome. It does not appear in the system Recents
overlay and does not require a default-Home patch.

### Session ring

Process list on `GhostGalleonApp`, capped at **8**, newest first.

```
data class SessionRingEntry(
    val key: String,
    val playerId: String?,
    val packageName: String,
    val policy: SessionPolicy,
    val launchedAtMs: Long,
    val title: String,
)
```

- Push on successful `beginSession` (after `noteLaunch`).
- Dedupe by `key`: move to front, refresh player/policy/title/time.
- Do not store `greedy`. Do not store display ids (they go stale).
- Persist as `Settings.sessionRing` (schema **v9**). Drop unknown
  fields on read. Missing key → empty ring (no bump break).

Idle library recents (`lastLaunchedMs`) stay as they are. The ring is
the switcher’s source of truth so a KEEP → YIELD → KEEP sequence keeps
the player id.

### UI

A compact list on the play host (KEEP) or on interactive Main (idle).
Same visual language as Quick Panel (cards, no new theme system).

Each row: art tile, title, player name, KEEP/YIELD hint
(`settings_player_uses_both_screens` when `policy == YIELD_BOTH`).

| Control | Behavior |
|---|---|
| Confirm / tap row | **Switch to** that entry (below). |
| End | Remove from ring only. Does not kill a process. |
| Back / Select | Close switcher. Session unchanged. |

Do not show more than 8. Empty ring: one line, “No recent sessions.”

### Switch-to

Let `current` be `sessionSurface` (nullable). Let `target` be the
chosen `SessionRingEntry`.

1. Close the switcher (in-place).
2. If `target.key == current?.key` and same `playerId`: no-op
   (already playing).
3. If `current?.policy == YIELD_BOTH` or `current?.greedy == true`:
   **refuse** — the switcher cannot be open in that state. If it
   somehow is, toast `session_yields_both_screens` and return.
4. Launch `target.key` through existing `launchSlotKey` (ROM or app).
   That path already does `noteLaunch` then `beginSession`.
5. If the target player is `YIELD_BOTH`, `beginSession` dismisses the
   play host (`closeQuietly`). That is success: both panels become the
   game. Do not respawn the HUD.
6. If the target is KEEP, the play host stays; HUD rebinds to the new
   `sessionSurface`.

Do not `force-stop` the previous KEEP package on switch. Android will
pause it. Optional later: a Settings “kill previous RA on switch”
(off by default) — not in v1.

Switching **to** yield from a KEEP HUD is allowed and is the honest
handoff: companion goes away, DS/3DS takes both panels.

### Opening the switcher during yield

Impossible by construction: no play host, no overlay. User HOMEs
first (reclaim), then opens the switcher on idle/KEEP chrome.

## 3 — Pixel oracle

### What it detects

The dual-paint failure mode: accessibility tree alive, GPU presenting
near-black on a **Ghost Galleon** window. It does not detect a dark
game frame on the launch display.

### When it may run

```
fun oracleMaySample(
    dualMode: Boolean,
    ownsCompanionDisplay: Boolean,  // YIELD || greedy
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

`sessionOpen && windowDisplayId == launchDisplayId` is the KEEP game
(or a GG activity stuck on the launch display). Never sample it.

Idle HOME: may sample Companion and Main on their actual `displayId`s.

### How

- One `PixelCopy` in flight. If a copy is pending, skip the tick.
- Source: the play-host or companion `Window`. Not `Display` (that can
  include the other panel).
- Destination: a reused 32×32 `RGB_565` bitmap (no new alloc per tick).
- Period: **2000 ms** (`MIN_HEAL_GAP_MS`). Do not run during
  `allowFullRender == false` / in-flight `setContentView`.
- Score: max luma of the 32×32. `maxLuma < 8` is a miss (near-black).
- After **3 consecutive misses**, request
  `restartCompanionPanel("oracle-black")` if `resumeCompanionAction`
  would allow HEAL or RESTART (idle or KEEP-return). During an open
  KEEP session, oracle **does not** restart onto the launch display;
  it only restarts the play host if that host is black **and**
  `playHostAllowed`.
- Any non-miss resets the counter.
- Log: `adb logcat -s GGOracle`  
  `miss n=3 display=<id> maxLuma=<n>` then `heal reason=oracle-black`.

Failure of `PixelCopy` (timeout, EGL) is **not** a miss. Log once per
process and back off 10s.

### What it does not do

- Does not flip `SessionPolicy` or mark greedy. Greedy stays
  return-time (`shouldMarkGreedy`).
- Does not run a second heal inside `MIN_HEAL_GAP_MS`.
- Does not sample at 60 Hz.

Settings: **System → Detect black companion** (default **on**). Off
disables ticks. No schema meaning beyond a boolean in v9.

## 4 — RetroArch companion protocol

### When

Player id starts with `ra-` **or** `packageName` is
`com.retroarch.aarch64` (or a future Settings override), **and**
`playHostAllowed`, **and** the user enabled **Talk to RetroArch**
(Settings → Library, default **off**).

YIELD / greedy / non-RA KEEP: no socket.

### Transport

Libretro **network commands**, UDP, default port **55355**.

v1 commands (ASCII, newline-terminated):

| Command | HUD |
|---|---|
| `VERSION` | Probe. Any reply → link up. |
| `GET_STATUS` | Pause chip label: PLAYING vs PAUSED. |
| `PAUSE_TOGGLE` | Pause chip. |
| `SAVE_STATE` | Save chip (current slot). |
| `LOAD_STATE` | After slot pick. |
| `SAVE_STATE_SLOT n` / `LOAD_STATE_SLOT n` | If the build supports them; else set slot via two-step documented fallback and disable the strip. |

`PAUSE_TOGGLE`, `SAVE_STATE`, and `LOAD_STATE` are fire-and-forget
(RetroArch sends no UDP reply). A timeout must not drop `linkUp`.
`VERSION` and `GET_STATUS` still require a reply. First failed
`*_STATE_SLOT` hides the slot strip for the process and falls back to
`SAVE_STATE` / `LOAD_STATE`.

Probe at most every **5s** while the HUD is visible and the link is
down. Probe immediately when the HUD binds. Timeout **200 ms**. One
outstanding datagram.

Unreachable: hide RA chips, keep art/title/clock. Do not toast a
failure every probe.

Do not use RA cheats, RAM peek, or screenshot commands in v1.

### Opt-in cfg

Settings toggle **Talk to RetroArch**:

1. Off (default): no UDP, no file writes.
2. First turn-on: try to read
   `…/Android/data/com.retroarch.aarch64/files/retroarch.cfg`
   (path already on `PlayerTemplate.extras["CONFIGFILE"]`).
3. If writable and `network_cmd_enable` is not `"true"`, append or
   set `network_cmd_enable = "true"` and
   `network_cmd_port = "55355"`. Log `GGSession ra-cmd enabled`.
4. If not writable (scoped storage): show a dialog with the two lines
   to paste in RA Settings → Network. Do not crash. Leave the toggle
   on (we still probe).
5. Never overwrite unrelated cfg keys. Never write on launch without
   the toggle.

Port is read back if the cfg has `network_cmd_port`. Default 55355.

Warm RA relaunch stays **NEW_TASK only** (no CLEAR_TASK) — existing
device constraint.

### Savestate thumbs

Best-effort. Look under
`extras["EXTERNAL"] + "/states"` and common
`RetroArch/states` SAF trees the user already granted. Match
`<rom-stem>.stateN.png` or RA’s current naming. If nothing is
readable, show slots **1–8** as numbers.

No new storage permission. No scan of the whole SD card.

### Paint / network

RA replies update TextViews / chip enabled state on the main thread.
Never `updateSettings`, never `notifyChanged()`, never
`publishRomEntries`. Same rule as RetroAchievements: network must not
drive a full deck rebuild.

Cheevo unlocks stay on the existing RA HTTP path + `RaProgressGate`.
The UDP link does not fetch cheevos.

### Non-RA KEEP

PPSSPP, DraStic, Eden, Winlator, Android apps: HUD is art + clock +
switcher + favorite + open with + end/reclaim. No fake pause button.

## Data / settings (schema v9)

`Settings.schemaVersion` is **9**. `SettingsStoreTest` covers
missing-v9 → defaults.

| Field | Default | Role |
|---|---|---|
| `sessionRing: List<SessionRingJson>` | empty | Persisted switcher |
| `detectBlackCompanion: Boolean` | true | Oracle enable |
| `raNetworkCommands: Boolean` | false | RA UDP + optional cfg write |
| `raNetworkCmdPort: Int` | 55355 | Override if cfg differs |

`romProfiles` player overrides already exist (v7). Per-ROM
`sessionPolicy` override UI is still out of scope; `resolve` already
accepts `romOverride`.

New strings (all five catalogs, then `i18n_audit.py --write && --check`):

- `action_open_session_switcher`, `action_toggle_play_hud`
- `play_hud_pause`, `play_hud_resume`, `play_hud_save`, `play_hud_load`
- `session_switcher_title`, `session_ring_empty`
- `settings_detect_black_companion`, `settings_ra_network_commands`
- Reuse `session_yields_both_screens` / `settings_player_uses_both_screens`

Log tags: `GGSession` (existing greedy + `ra-cmd enabled`),
`GGOracle` (oracle), `GGPaint` (unchanged).

## Dual-paint additions

- Play HUD ticks (clock + RA chip state) are not SETTINGS, not BROWSE,
  and not SELECTION.
- Oracle heals use the existing `restartCompanionPanel` + `allowHeal`
  debounce (`oracle-black`). They are not SETTINGS. No new full-paint
  storm.
- Switcher open/close is in-place on the play host (add/remove a
  child). If that fails once, one full companion rebuild is allowed.
- Yield / greedy: `sessionOwnsCompanionDisplay` still blocks heal,
  embed, pin, and now also oracle + HUD bind + RA UDP.

## Phases

Ship in this order. Phase 2 and 3 may overlap after 1. Phase 4 needs 1.

| Phase | Ships | Status |
|---|---|---|
| **1 — Play host** | `playHostAllowed`, HUD chrome (art/title/clock/actions without RA), new Actions, in-place clock ticks | **Implemented** (host). Sugar device matrix **not run**. |
| **2 — Switcher** | Ring push/persist v9, switcher overlay, switch-to via `launchSlotKey` | **Implemented** (host). Sugar device matrix **not run**. |
| **3 — Oracle** | `oracleMaySample`, 32×32 PixelCopy, 3-miss heal, System toggle | **Implemented** (host). Sugar device matrix **not run**. |
| **4 — RA link** | Opt-in cfg, UDP probe/status, fire-and-forget pause/save/load, slot strip 1–8 | **Implemented** (host). Sugar device matrix **not run**. |

Phase 1 before 4: a HUD that pauses RA is useless if it can appear on
a DS panel. Phase 2 before claiming “better than Recents”: the ring
must not be launchable during yield.

Code for all four phases is on `feat/keep-play-surface`. Host tests are
not device proof. Do not claim the Sugar matrix from this doc.

## Device matrix (Sugar)

**Not run.** Rows below are the intended checks, not observed proof.

| Step | Must see |
|---|---|
| RA SNES KEEP | Game on **launch** (top). Play HUD on **owned** panel (bottom companion). No HUD on the game. |
| Clock 10s | HUD clock moves. `GGPaint` has no FULL storm. |
| DraStic / RA melonDS + `.nds` | Same KEEP HUD. melonDualDS still **no** HUD (both panels DS). |
| Open switcher on KEEP RA | List includes this session. Confirm same row = no-op. |
| Switch KEEP SNES → KEEP GBA | GBA launches; HUD retitles; no companion on launch display. |
| Switch KEEP SNES → melonDualDS | Both panels become DS. No Companion task. No PixelCopy. |
| HOME from melonDualDS | Reclaim both GG surfaces. Switcher available idle. |
| X during melonDualDS | Still no GG on a DS panel. |
| Oracle: force companion black (dev) | After ~6s, one `GGOracle` heal; companion paints. |
| Oracle during melonDualDS | No `PixelCopy`, no heal. |
| RA Talk off | No UDP. HUD has no pause/save. |
| RA Talk on + `network_cmd_enable` | Pause chip toggles GET_STATUS. Save does not crash RA. |
| PPSSPP KEEP | HUD without RA chips. |

Greedy KEEP: treat as yield (no HUD / oracle). If none observed, leave
**none observed**.

## Host tests

- `playHostAllowed`: KEEP + different ids → true; KEEP + same ids →
  false; YIELD → false; greedy → false; !dual → false.
- `oracleMaySample`: idle dual + companion id → true; yield → false;
  KEEP + window==launch → false; KEEP + window!=launch → true.
- Session ring: push dedupes by key; cap 8; persist v9 round-trip;
  greedy flag absent.
- Switch-to: YIELD current refuses; same key no-op; KEEP→KEEP
  rebinds; (pure, no Activity).
- RA command codec: newline terminate; port default; probe timeout
  does not throw.
- Dual-paint: existing resume table unchanged; new heal reason string
  is just a label.

## Verification (agents)

```text
verify: rg -n "playHostAllowed|oracleMaySample|SessionRing" app/src/main/java
verify: rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src  # must stay gone
verify: rg -n "PixelCopy" app/src/main/java
verify: python3 scripts/i18n_audit.py --check
verify: ./gradlew :app:testDebugUnitTest --offline --tests '*DualPaintPolicy*' --tests '*SessionPolicy*' --tests '*CompanionRole*' --tests '*SessionRing*' --tests '*PlayHost*' --tests '*RaCommand*'
```

Device: run the matrix. Host green is not a Sugar claim.

Task-by-task implementation plan (all four phases):
[`superpowers/plans/2026-08-13-keep-play-surface.md`](superpowers/plans/2026-08-13-keep-play-surface.md).

## Implementation (shipped)

Authoritative types (host-testable, no Android `Display`):

- `ui/PlayHostPolicy.kt` — `playHostAllowed`, `oracleMaySample`
- `rom/SessionRing.kt` — cap 8, dedupe by key
- `rom/SessionSwitch.kt` — switch-to decision
- `rom/OracleTally.kt` — 3-miss heal tally
- `rom/RaCommand.kt` — UDP encode/decode, probe, fire-and-forget pause/save/load
- `rom/RaCfg.kt` — opt-in `network_cmd_*`
- `rom/RaStateSlots.kt` — slots 1–8 + best-effort thumbs

Call sites:

- `CompanionPanel` binds HUD when `playHostAllowed`
- `SessionSwitcherView` overlay + `launchSlotKey`
- `GhostGalleonApp.beginSession` pushes the ring
- `MainActivity` / `CompanionActivity` clock ticks + oracle (`PixelCopy` of the owned window)
- Settings v9 (`sessionRing`, `detectBlackCompanion`, `raNetworkCommands`) + Controller Lab actions
- `RaUdpTransport` holds `DatagramSocket` (not referenced from `RaCommand.kt` policy types)

Do not put `PixelCopy` or `DatagramSocket` in `SessionPolicy` / `PlayHostPolicy`.
