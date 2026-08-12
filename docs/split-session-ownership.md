# Split-session ownership

How Ghost Galleon and a launched app share the two physical panels.

**Dual-surface games keep both screens.** melonDualDS is a Nintendo DS
emulator; Azahar is a Nintendo 3DS emulator. They draw two guest screens
on two host panels. Ghost Galleon must not steal a panel from them, pin
over them, or overlay a HUD on them while they run.

**Single-surface games keep a live companion.** RetroArch, PPSSPP,
NetherSX2, Winlator, most Android apps, and single-screen NDS players
(DraStic, stock melonDS, RA melonDS) launch on the topology **launch
display**. Ghost Galleon stays HOME on the **interactive** display and
keeps the **companion** painted (Now Playing, Perf HUD, or a pin that
does not cover the game).

The work is an honest **per-player session policy**, not “always keep a
launcher panel” and not “never allow dual-screen apps.”

Authoritative code (today): `settings/CompanionRole.kt`
(`DUAL_CLAIM_PLATFORMS`), `ui/deck/Deck.kt` (`launchOnOtherDisplay`),
`rom/RomLauncher.kt`, `GhostGalleonApp.noteLaunch`,
`ui/MainActivity.kt` (return / heal / `restartCompanionPanel`),
`ui/deck/ActivityEmbed.kt`, `docs/dual-paint-invariants.md`.

## Terms

| Term | Meaning |
|---|---|
| **Interactive display** | Input target. Grid or Game Mode. Default Sugar: bottom. |
| **Launch display** | Where `launchOnOtherDisplay` sends the game (`displayConfig.launchDisplayId`). Default Sugar: top. |
| **Companion** | `CompanionActivity` on the non-interactive panel (hero / Now Playing / Perf / pin). |
| **Open session** | `GhostGalleonApp.openSession` after a successful `noteLaunch`. |
| **Player** | One `PlayerTemplate.id` (e.g. `melondualds`, `azahar`, `ra-snes9x`). Not a platform. |
| **Yield** | Ghost Galleon gives **both** panels to the session. Companion does not fight. |
| **Keep** | Game stays on the launch display. Companion stays Ghost Galleon. |
| **Reclaim** | After the session ends, both HOME roles paint again. |

Display ids are never hard-coded `0`/`1`. Roles come from
`DisplayTopology` / `DeckState.primaryDisplayId`.

## Non-goals

- Do not clip, letterbox, or cover a dual-surface emulator to show hero art.
- Do not use `SYSTEM_ALERT_WINDOW` / overlay windows on a yielded session.
- Do not force `setLaunchDisplayId` in a way that prevents melonDualDS or
  Azahar from using the second panel.
- Do not replace Android Recents or patch Quickstep in this epic.
- Do not implement the shoulder HUD or black-panel pixel oracle here.
  Those consume this policy; they are separate specs.
- SINGLE topology: this policy is a no-op. One display, one activity.

## Policy

Session policy is attached to the **player that actually launched**, not
the ROM’s platform. NDS is the proof: melonDualDS yields; DraStic and
RetroArch (melonDS) keep.

```
enum class SessionPolicy {
    KEEP_COMPANION,  // single-surface: companion stays ours
    YIELD_BOTH,      // dual-surface: both panels are the game
}
```

Resolution, in order:

1. Per-ROM override in `romProfiles` (optional, later).
2. `PlayerTemplate.sessionPolicy` on the launched player.
3. Default: `KEEP_COMPANION`.

Unknown players stay `KEEP_COMPANION`. If the game then covers both
panels anyway, treat that run as a **greedy keep** (see below). Do not
guess YIELD from the platform id. Today’s
`CompanionRoleResolve.DUAL_CLAIM_PLATFORMS = {nds, 3ds}` is too coarse
and is replaced by this table.

### Built-in player table (Sugar-verified intent)

| Player id | Package (abbrev.) | Policy | Why |
|---|---|---|---|
| `melondualds` | `me.magnum.melondualds` | **YIELD_BOTH** | DS top/bottom on host top/bottom. |
| `azahar` | `org.azahar_emu.azahar` | **YIELD_BOTH** | 3DS dual screen. |
| `melonds` | `me.magnum.melonds` | KEEP_COMPANION | Single-window melonDS. |
| `drastic` | `com.dsemu.drastic` | KEEP_COMPANION | Single-window DS. |
| `ra-melonds` | RetroArch | KEEP_COMPANION | One RA window. |
| All other built-in ROM players | RetroArch, PPSSPP, Eden, NetherSX2, Dolphin, Flycast, Cemu, Vita3K, Winlator | KEEP_COMPANION | One guest framebuffer. |
| Android apps | launcher intent | KEEP_COMPANION | Unless the user marks the package YIELD. |

Imported platform packs may set `sessionPolicy`. Missing field = KEEP.

A Settings row can later mark an **Android package** as YIELD (rare
dual-screen apps). Until that ships, only the table above yields.

### Greedy keep

A KEEP player that still paints over the companion (fullscreen on both
displays) is **greedy**. Ghost Galleon:

1. Does not start a second companion on top of it (heal stays off while
   the session is open).
2. Does not pin or ActivityView-embed into the stolen panel.
3. On return (interactive `onResume` after `leftHomeSinceResume`),
   **reclaims** as if the session had yielded.

Do not flip the stored policy to YIELD from one greedy run. Log it
(`GGSession`) so the table can be updated after device proof.

## Session record

Extend the open session (or a sibling `SessionSurface` on the app object)
with:

| Field | Source |
|---|---|
| `key` | Slot key already passed to `noteLaunch` |
| `playerId` | Template that built the successful plan |
| `packageName` | Template package |
| `policy` | Resolved `SessionPolicy` |
| `launchDisplayId` | Topology launch id at fire time |
| `startedAtMs` | Existing session tracker |

`noteLaunch` today stamps playtime only. The launch path
(`RomLauncher.launch` / `launchSlotKey`) already knows the winning
template — record `playerId` + `policy` there. Clearing the session
stays as it is (return / accrue / end).

## Lifecycle

### KEEP_COMPANION (happy path)

1. User confirms a KEEP title on the interactive deck.
2. `launchOnOtherDisplay` starts the player on the launch display.
3. `noteLaunch` opens a KEEP session.
4. Interactive deck stays put (already true).
5. Companion **stays the existing `CompanionActivity`**. Do not
   `restartCompanionPanel` on the way out.
6. Companion role: Now Playing if that is the user preference or an
   open session requires it; otherwise the user’s companion role.
   PINNED_APP is allowed only when the pin package is **not** the
   game package and the pin target is not the launch display.
7. Heal is **disabled** while a KEEP session is open (the launch
   display is supposed to be the game). Interactive HOME may still
   paint.
8. User returns (HOME / back / force-stop of the game): interactive
   `onResume` with `leftHomeSinceResume`. Accrue playtime. If the
   companion is missing or black, `restartCompanionPanel` — same
   recover-from-emulator path as today.

### YIELD_BOTH (happy path)

1. User confirms a YIELD title (melonDualDS, Azahar, …).
2. Launch as today (`setLaunchDisplayId` still allowed; the player is
   free to occupy the other panel).
3. `noteLaunch` opens a YIELD session.
4. Ghost Galleon **does not fight for the companion display**:
   - Do not launch or recreate `CompanionActivity` on that display.
   - Do not ActivityView-embed.
   - Do not show PINNED_APP on that display.
   - Absorb `SECONDARY_HOME` silently (already required by dual-paint).
5. Interactive activity: `onStop`/`onPause` is expected if the game
   also takes the interactive panel. That is success, not a heal
   trigger.
6. User leaves the game (HOME, gesture, `am force-stop` of the
   emulator). Interactive HOME resumes. **Reclaim:**
   `restartCompanionPanel("return-from-yield")` after the existing
   heal debounce. Both panels are Ghost Galleon again.

Yield is **not** “skip launch flags so the game cannot see the second
display.” The game is supposed to take both.

### Return and death

| Event | KEEP | YIELD |
|---|---|---|
| HOME / back to interactive | Accrue; heal companion if missing | Accrue; **always** restart companion |
| Swipe-up while still on HOME (`onNewIntent`, never `onStop`) | All-apps drawer (unchanged) | N/A (we were not on HOME) |
| Process death of Ghost Galleon | Cold start; no session | Cold start; no session |
| Process death of the game | Same as HOME return | Same as HOME return |
| X (swap) during KEEP | Swap interactive/companion; game stays on its display if the OS kept it | No-op or toast: session owns both panels |
| X during YIELD | Must not spawn companion on a live DS/3DS panel | |

`MainActivity.onResume` today restarts companion on every
`leftHomeSinceResume` unless a pin is ready. After this spec it
branches on **session policy**, not only pin:

- Open YIELD session (user still in the game, rare resume): do nothing.
- Return from YIELD: restart companion.
- Return from KEEP: heal if missing; do not cover the launch display.
- Return from greedy KEEP: restart companion (same as YIELD return).
- Pin ready + KEEP: do not recreate companion over the pin (today’s pin
  guard), and do not recreate over the **game** either.

## Companion UI while a session is open

| Policy | Companion contents |
|---|---|
| KEEP | Now Playing (title, playtime, Resume, Open with, Favorite). Perf HUD allowed. Pin allowed if it is not the game and not on the launch display. |
| YIELD | No companion surface. If a `CompanionActivity` instance still exists, it `finish()`es without cascade (`skipExitCascade`) so it does not kill Main. |
| KEEP + user preference HERO | Hero of the open title is allowed; it is not a second game window. |

Resume chip on KEEP companion: launch the same slot key again (existing
launcher). Do not invent a new intent.

Dual-claim CTA copy (today: pin honesty `DUAL_CLAIM`) stays for **pin
settings**, not as a substitute for yielding. Wording: this player uses
both screens; the pin pauses until you return.

## Input

- KEEP: interactive deck keeps receiving keys (already global). Companion
  is not the input target unless swap moves interactive there.
- YIELD: the game owns input. Ghost Galleon must not inject keys into
  melonDualDS to “drive the HUD.”
- Shoulder HUD (later spec) may appear only under KEEP, and only on the
  display Ghost Galleon still owns.

## Dual-paint

All rules in [`dual-paint-invariants.md`](dual-paint-invariants.md) stay
in force.

Additions:

- Do not `setContentView` on a companion that is about to yield.
- Do not heal onto a display that the open YIELD (or greedy KEEP)
  session owns.
- `SECONDARY_HOME` during YIELD: absorb, no All-apps, no newest-wins.
- Reclaim after YIELD is one restart, heal-debounced, not a paint storm.

## Data / settings

- `PlayerTemplate.sessionPolicy: SessionPolicy = KEEP_COMPANION`
  (host-testable; packs may set `"sessionPolicy": "YIELD_BOTH"`).
- Optional later: `Settings.yieldPackages: Set<String>` for Android apps.
- Optional later: per-ROM override in `romProfiles`.
- No schema bump required if the field is pack/registry-only. A user
  override set needs a Settings schema bump and `SettingsStoreTest`.

Do not persist “greedy” as a user setting from one run.

Task-by-task implementation plan (all four phases):
[`superpowers/plans/2026-08-12-split-session-ownership.md`](superpowers/plans/2026-08-12-split-session-ownership.md).

## Implementation sketch

Pure first, then Android edges.

1. `rom/SessionPolicy.kt` — enum, `resolve(player, romProfile, yieldPackages)`,
   built-in map keyed by `PlayerTemplate.id`. Host tests: NDS players
   disagree; 3DS Azahar yields; SNES keeps; missing pack field keeps.
2. `PlayerTemplate.sessionPolicy` + pack JSON + `PlatformsTest` /
   `PlatformPack` parse.
3. Launch path records `playerId` + policy on the open session.
4. `CompanionRoleResolve` reads **session policy**, not
   `DUAL_CLAIM_PLATFORMS`. Delete the platform set once tests move.
5. `MainActivity` return/heal/restart branches on policy (table above).
6. `CompanionActivity` self-finishes without cascade when a YIELD
   session opens; does not relaunch until reclaim.
7. Settings → player default / Open with: show “Uses both screens” from
   the template (read-only in v1).
8. `adb logcat -s GGSession` for resolve + greedy + reclaim.

Do not put Android `Display` types in `SessionPolicy`.

## Phases

| Phase | Ships | Done when |
|---|---|---|
| **1 — Policy** | Enum, registry table, pack field, session record, pin honesty from player | Host tests green; NDS + DraStic keep; melonDualDS + Azahar yield |
| **2 — Yield** | Companion does not relaunch during melonDualDS/Azahar; reclaim on HOME | Device: both DS/3DS panels are the game; HOME restores both Ghost Galleon surfaces |
| **3 — Keep** | RetroArch/PPSSPP/Eden session leaves companion up (Now Playing); heal off on launch display | Device: game on top, Ghost Galleon on bottom, no black companion |
| **4 — Greedy** | Heal off + reclaim on return for KEEP titles that stole both | Device: one known greedy package documented in GGSession |

Phase 2 before 3: yielding wrong is more harmful than keeping companion
on a single-surface game. Dual-screen correctness is the product
constraint.

## Device matrix (Sugar)

| Launch | Must see |
|---|---|
| melonDualDS + any `.nds` | Top and bottom are the DS. No hero, no pin, no companion task on either panel. |
| Azahar + any `.3ds` | Same: both panels are 3DS. |
| DraStic or RA melonDS + same `.nds` | Game on launch display. Companion still Ghost Galleon. |
| Snes9x / GBA RA | Game on launch display. Companion still Ghost Galleon. |
| HOME from melonDualDS | Interactive deck + companion both paint (not black). |
| HOME from RA SNES | Interactive deck unchanged; companion Now Playing or previous role. |
| X during melonDualDS | Does not spawn Ghost Galleon on a DS panel. |
| SECONDARY_HOME during melonDualDS | Absorbed; DS stays. |

`am force-stop me.magnum.melondualds` remains the documented escape when
the emulator captures both displays and ignores injected HOME.

## Host tests

- `SessionPolicyTest`: table above; pack omit = KEEP; rom override wins
  when implemented.
- `CompanionRoleTest`: dual-claim honesty follows **player**, not
  `platformId == nds`.
- `RomLauncherTest` / session record: successful melonDualDS plan stores
  `YIELD_BOTH`.
- Dual-paint tests unchanged; add a case that heal is denied while a
  YIELD session is open (pure clock + flags, no Display).

## Verification (agents)

```text
verify: rg -n "SessionPolicy|YIELD_BOTH|KEEP_COMPANION" app/src/main/java/com/visorcraft/ghostgalleon
verify: rg -n "DUAL_CLAIM_PLATFORMS" app/src/main/java  # must be gone after phase 1
verify: ./gradlew :app:testDebugUnitTest --offline --tests '*SessionPolicy*' --tests '*CompanionRole*'
```

Device: run the matrix; do not claim phase 2/3 done from unit tests
alone.
