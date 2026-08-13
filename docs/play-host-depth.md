# Play-host depth

How the panel Ghost Galleon still owns during a **KEEP** session
becomes a tracker, a savestate cinema, a live achievement theater,
a second seat, a save ferry, a posture theater, a helper embed, and
a warm Continue — without becoming a second operating system.

This spec **consumes** [`owned-surface.md`](owned-surface.md),
[`keep-play-surface.md`](keep-play-surface.md), and
[`split-session-ownership.md`](split-session-ownership.md). It does
not weaken yield. Dual-paint rules in
[`dual-paint-invariants.md`](dual-paint-invariants.md) stay in force.

Owned-surface made the play host *playable* (input), *true* (handoff),
and *honest* (identity / stage plot). This spec makes it *useful while
the game is running*.

**Dual-surface games keep both screens.** melonDualDS and Azahar
(`YIELD_BOTH`, or greedy KEEP) get **no** tracker, **no** cinema,
**no** achievement theater, **no** second seat, **no** helper embed,
**no** posture-driven relaunch, **no** warm `LOAD_STATE`, **no**
play HUD, **no** input lock, **no** RAM UDP, **no** cockpit, **no**
`PixelCopy`, and **no** RetroArch / Accessibility injection. Ghost
Galleon must not steal a panel, pin over them, or overlay chrome on
them while they run.

**Single-surface games keep a live companion.** That owned panel is
already a play HUD with focus lock, RA UDP, optional lenses, and a
Winlator cockpit. This spec grows *surfaces* on that same host. It
does not grow a third session clock and does not reopen overlays,
TaskView-of-the-game, or `MANAGE_EXTERNAL_STORAGE`.

## Why one spec

The eight features share one paint gate, one UDP socket, one input
owner, and one exclusive-surface rule. A tracker that draws on a DS
panel, a second seat that injects into Azahar, a helper that embeds
RetroArch over itself, or a warm resume that launches melonDualDS in
the background would undo 0.10 / 0.11.

They ship as **one program, eight phases**, not eight side quests.

```
owned-surface (shipped: input, handoff, plot, cockpit, lens, identity)
        |
        +-- 1 tracker -- 2 cinema -- 3 theater -- 8 warm
        |                 (slots)                  (uses 2)
        +-- 5 ferry     (identity, library-only, parallel)
        +-- 6 posture   (topology, parallel; never auto-YIELD)
        +-- 4 seat ----- 7 helper
              (assist)     (embed ≠ session package)
```

Phase 1 needs shipped lenses (`READ_CORE_RAM`, `LensCatalog.match`).
Phase 2 needs shipped RA slots (`SAVE_STATE_SLOT` / `LOAD_STATE_SLOT`).
Phase 3 needs shipped RA HTTP (`RaFetcher`, `RaProgressGate`) plus
the play host. Phase 8 may load a slot only after 2 exists.
Phase 5 needs shipped `RomIdentity`. Phase 6 needs shipped topology
and stage plots; it must not invent a new `SessionPolicy`.
Phase 4 needs shipped focus lock + optional assist (cockpit gestures).
Phase 7 needs shipped `ActivityEmbed` + `pinConflictsWithSession`.

Do not ship 4 before 1 is painted: a seat that has nowhere honest to
live will steal the pad. Do not ship 8’s auto-load default-on.
Do not ship 6 as silent YIELD.

## Terms

| Term | Meaning |
|---|---|
| **KEEP / yield / greedy / play host / owned display / launch display** | Unchanged from KEEP play surface. |
| **Input owner / focus lock / input assist** | Unchanged from owned-surface. |
| **RAM lens** | Shipped one-line `READ_CORE_RAM` view. |
| **Tracker surface** | Structured widget view of the same RAM blocks (bits / grid / meter). |
| **State cinema** | Auto-ring of reserved RA savestate slots + filmstrip on the play host. |
| **Achievement theater** | Live RA set / next locked / unlock ticker on the play host. |
| **Second seat** | Touch P2 on the play host; P1 pad stays in the KEEP game. |
| **Save ferry** | Copy battery / state between two library rows that share a hash. |
| **Posture** | Hinge / device-state reading: `UNKNOWN`, `TABLETOP`, `BOOK`, `FLAT`, `CLOSED`. |
| **Helper** | A *different* Android package shown on the play host (embed or fail closed). |
| **Warm resume** | Prepare Continue (resolve + RA probe + optional load). Not a pre-launched process. |
| **Host surface** | Which exclusive chrome occupies the play host body. |
| **RomIdentity** | Shipped sidecar. Still does **not** replace `RomEntry.id`. |

Display ids are never hard-coded `0`/`1`. Roles come from
`DisplayTopology` / `DeckState.primaryDisplayId`.

## Exclusive surfaces

The play host is one panel. These do not stack as independent
full-bleed UIs:

```
enum class HostSurface { HUD, TRACKER, CINEMA, THEATER, SEAT, HELPER, COCKPIT }

fun HostSurfacePolicy.exclusive(surface: HostSurface): Boolean =
    surface == HostSurface.SEAT ||
        surface == HostSurface.HELPER ||
        surface == HostSurface.COCKPIT
```

| Surface | Shares the HUD chrome (art / clock / chips)? | Body |
|---|---|---|
| `HUD` | — | Default KEEP actions. |
| `TRACKER` | Yes | Widget panel under the clock. |
| `CINEMA` | Yes | Horizontal filmstrip. May sit under a tracker. |
| `THEATER` | Yes | Compact next/last cheevo. May sit under cinema. |
| `SEAT` | Art + title + clock only | Full-bleed P2 pad. Hides tracker / cinema / theater. |
| `HELPER` | Title + “Back to HUD” chip | Embed (or fail closed). Hides the rest. |
| `COCKPIT` | Art + title + clock (shipped) | Winlator. Helper and seat **disabled**. |

Chips stay tappable under focus lock (same as today’s HUD). Switching
to an exclusive surface is a chip tap; it does **not** claim `HOST`
unless the surface needs the IME (helper text, cockpit keyboard).

Process-only: `GhostGalleonApp.hostSurface: HostSurface` (default
`HUD`). Yield, greedy, `clearSessionSurface`, and HOME return reset
it to `HUD`. Not persisted.

## Non-goals (all eight)

- Do not add `SYSTEM_ALERT_WINDOW` / `TYPE_APPLICATION_OVERLAY`.
- Do not `ActivityEmbed` / TaskView the **open session** package
  (`pinConflictsWithSession` stays).
- Do not inject keys, RAM, gestures, or embeds into a YIELD or
  greedy session.
- Do not replace Android Recents or patch Quickstep.
- Do not add Compose or a new Gradle dependency.
- Do not request `MANAGE_EXTERNAL_STORAGE` or broad storage.
- Do not change `RomEntry.id` / `SlotKey`.
- Do not `WRITE_CORE_RAM`, poke cheats, or invent a second UDP
  socket.
- Do not `force-stop` a package to make cinema / warm / ferry
  “cleaner.”
- Do not guess YIELD from a platform id or from hinge angle.
- Do not pre-launch an emulator process in the background.
- Do not OCR the game framebuffer or PixelCopy the launch display.
- SINGLE topology: tracker, cinema, theater, seat, helper, posture
  face, and warm load are no-ops (one display; the game covers it).
  Ferry and identity still work. Warm *resolve* (no launch) may run.

## Invariants

1. `sessionOwnsCompanionDisplay` (YIELD or greedy) ⇒ no focus lock,
   no assist filter, no RAM UDP, no cinema UDP, no theater paint, no
   seat, no helper, no posture relaunch, no warm load, no PixelCopy,
   no play HUD. Same gate as owned-surface.
2. `playHostAllowed` is still the only paint gate for every surface
   in this spec except ferry (library / details, idle or KEEP).
3. Network / assist / hinge / hash never call `updateSettings`,
   `notifyChanged()`, or `publishRomEntries`. In-place views only.
   Theater may use existing `RaProgressGate` `SELECTION_ONLY` when
   the awarded count actually changes. Ferry / helper-package edits
   are user Settings (one notify allowed).
4. One outstanding RA datagram. Lenses, tracker, cinema, probes,
   pause, handoff save, and warm load share `enqueueRaUdp`. They
   queue; they do not pile up.
5. User actions that would steal a dual-surface player’s second
   panel still require the owned-surface confirm. Posture never
   bypasses that dialog. Defaults never YIELD.
6. Assist injection (seat, cockpit) targets the **launch display**
   only. If `displayId` gestures are missing, disable inject and
   keep the hint. Never gesture onto the play host as if it were
   the game.

---

## 1 — Tracker surface

### Problem

Shipped lenses are one `TextView`. The play host can show a map, an
item grid, or a party row — still read-only, still 256 bytes, still
one UDP flight.

### When

Same gate as a lens, plus `Settings.ramTrackersEnabled` (default
**on** when a matching spec has `surface: "tracker"`; the master
`ramLensesEnabled` toggle still gates all RAM reads, default
**off**).

`playHostAllowed` **and** Talk to RetroArch **and** `ramLensesEnabled`
**and** the player is RA **and** a spec matches **and**
`hostSurface` is not exclusive (`SEAT` / `HELPER` / `COCKPIT`).

### Spec extension

Existing `assets/lenses/*.json` and the SAF pack grow an optional
`surface` and `widgets` array. Missing `surface` = `"line"` (today’s
formatter). Invalid widget fields are ignored; a spec with zero
valid widgets falls back to the line.

```
{
  "id": "snes-alttp-items",
  "title": "A Link to the Past — items",
  "surface": "tracker",
  "match": { "platformId": "snes", "hash": ["<sha1>"] },
  "intervalMs": 200,
  "blocks": [
    { "address": "0x7EF340", "length": 16, "format": "bitfield",
      "labels": ["bow", "boomerang"] }
  ],
  "widgets": [
    {
      "kind": "bits",
      "block": 0,
      "cols": 8,
      "labels": ["bow", "boomerang", "hookshot", "bombs",
                 "powder", "fire_rod", "ice_rod", "bombos"]
    }
  ]
}
```

```
enum class TrackerKind { BITS, GRID, METER, LINE }

data class TrackerWidget(
    val kind: TrackerKind,
    val blockIndex: Int,
    val cols: Int,
    val labels: List<String>,
)

fun LensCatalog.parse(...)   // already shipped; grow fields
fun TrackerCatalog.acceptable(spec): Boolean
    // LensCatalog.acceptable AND each widget.blockIndex in range
    // AND labels.size <= block.length * 8 for BITS
```

Match order is unchanged (hash, then `romId`). `LensCatalog.MAX_BYTES`
stays **256**. Interval stays `min(intervalMs, 200)`.

v1 bundled trackers: **zero is allowed**. Host tests ship one fixture
spec. Do not invent a 200-game atlas in this spec.

### Paint

A `LinearLayout` / `GridLayout` of small labeled cells under the
KEEP clock (tag `play_hud_tracker`). In-place: `visibility` /
`alpha` / `setText` only. No `notifyChanged`. No image download
(labels are text or bundled 1-bit glyphs in `res/drawable`, not
network badges).

Owner `GAME`: display-only. Correct — the pad is in the game.

3 consecutive RAM failures still disable that **lens id** for the
process (shipped). The tracker hides.

### What it does not do

- No writes, cheats, or speed hacks.
- No tracker on DraStic / melonDualDS / Azahar / PPSSPP / Winlator.
- No 60 Hz. No second socket. No remote pack fetch.
- No Compose. No WebView “tracker app.”

---

## 2 — State cinema

### Problem

The HUD can save/load the current RA slot. It does not keep a
timeline. The play host is the only honest place to show one
while the pad stays in the game.

### When

`playHostAllowed` **and** Talk to RetroArch **and**
`Settings.raCinemaEnabled` (default **off**) **and** the player is
RA **and** slot commands are not process-disabled (shipped slot
strip fail-closed) **and** `hostSurface` is not exclusive.

### Slot band

User slots **1–8** stay the user’s. Cinema uses a reserved band:

```
object CinemaPolicy {
    val USER_SLOTS: IntRange = 1..8
    val BAND: IntRange = 9..12          // 4 frames
    const val DEFAULT_INTERVAL_MS = 60_000L
    const val MIN_INTERVAL_MS = 15_000L

    fun nextSlot(lastSlot: Int?): Int
    fun shouldCapture(
        enabled: Boolean,
        playHostAllowed: Boolean,
        raPlayer: Boolean,
        slotsLive: Boolean,
        lastCaptureMs: Long,
        nowMs: Long,
        intervalMs: Long,
    ): Boolean
}
```

`nextSlot` walks 9 → 10 → 11 → 12 → 9. It never writes 1–8.

Handoff (owned-surface §2) still uses `SAVE_STATE` on the **current**
user slot. Cinema does not replace handoff.

### Capture

While the gate holds, a ticker (same KEEP HUD handler, not a new
thread) asks `CinemaPolicy.shouldCapture`. If true, `enqueueRaUdp`:

1. `SAVE_STATE_SLOT n` for `nextSlot` (fire-and-forget).
2. Record `CinemaFrame(slot=n, savedAtMs=now, thumbKey=…)`.
3. Best-effort thumb: same SAF look as KEEP play-surface
   (`extras["EXTERNAL"] + "/states"`, granted trees only,
   `<stem>.stateN.png`). Miss → numbered chip. No new permission.

If `enqueueRaUdp` is busy (lens/tracker/probe), skip this interval.
Do not queue a backlog of captures.

Process-only ring: `GhostGalleonApp.cinemaFrames: List<CinemaFrame>`
(cap 4). Cleared on session end / yield / ROM change. Not Settings.

### Paint / load

Horizontal strip of 4 chips under the clock (tag `play_hud_cinema`).
Tap a frame → `LOAD_STATE_SLOT n`. That is an **explicit** user
action, not the auto-load owned-surface forbade on session switch.

Tap does **not** claim `HOST`. Load is UDP; the pad stays in the
game.

Long-press a frame: pin that slot as “Continue slot” for phase 8
(`cinemaPinnedSlot`, process-only). Default pin = most recent frame.

### Failure

- Slot commands already process-disabled: hide cinema, do not retry
  every minute.
- Link down: skip capture, keep last frames if any.
- YIELD starts: clear frames, cancel ticker.

### Settings

| Field | Default | Role |
|---|---|---|
| `raCinemaEnabled` | false | Master |
| `raCinemaIntervalMs` | 60000 | Clamped to `[15000, 300000]` |

Library row under Talk to RetroArch: **State cinema**. Off = no
auto-save, no strip.

### What it does not do

- Does not auto-load on switch or on launch (phase 8 is a separate
  opt-in, Continue only).
- Does not overwrite slots 1–8.
- Does not screenshot the launch display (`PixelCopy` stays on the
  play host only, for the oracle).
- Does not persist frames across process death in v1 (thumbs on
  disk may still be visible next run if SAF finds them; the ring
  starts empty).

---

## 3 — Achievement theater

### Problem

Hero RA is a browse-time line (`RaProgress` + `RaProgressGate`).
During KEEP the play host can show the *set*, the next locked
badge, and a ticker when the awarded count moves — without opening
a browser or RA’s menu.

### When

`playHostAllowed` **and** RA credentials present **and**
`Settings.raTheaterEnabled` (default **off**) **and**
`hostSurface` is not exclusive.

Talk to RetroArch is **not** required (HTTP, not UDP). A RAM lens
must not be required either.

### Fetch

Reuse `RaFetcher` / `RaProgressGate`. One HTTP attempt per `romId`
per process unless a later poll is due.

Extend parse to the `Achievements` object already on
`API_GetGameInfoAndUserProgress`:

```
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
    fun parse(json: String?): RaTheaterSnap      // never throws
    fun nextLocked(items: List<RaCheevo>): RaCheevo?
    fun newlyUnlocked(prev: Set<Int>, next: Set<Int>): List<Int>
    fun pollDue(lastMs: Long, nowMs: Long, intervalMs: Long): Boolean
}
```

`nextLocked` = first locked by display order (RA `DisplayOrder` if
present, else id). No “smart” recommendation.

Poll while the HUD is visible at most every
`raTheaterPollMs` (default **60000**). Same `mayFetch` in-flight
set. Compare `unlockedIds`:

- No change → do nothing (no notify, no setText).
- Awarded count or set changed → in-place rewrite + optional
  `SELECTION_ONLY` if the hero line must move (existing gate).
  Never `notifyChanged`.

Game id resolve stays title + console list (shipped). If identity
hash is ready and a future hash table exists, it may win; v1 does
**not** download a hash DB.

Badges: optional. If `badgeName` is present, load
`https://media.retroachievements.org/Badge/<name>.png` through
`ArtCache` (existing disk LRU, existing 2-thread decode). Failure
→ letter tile. Do not block the ticker on art.

### Paint

Compact block (tag `play_hud_theater`):

- `3 / 40` (reuse `RaProgress.label` style)
- One line: next locked title
- One line, 4s, when `newlyUnlocked` is non-empty: “Unlocked · <title>”

Owner `GAME`: display-only.

### Settings

| Field | Default | Role |
|---|---|---|
| `raTheaterEnabled` | false | Master |
| `raTheaterPollMs` | 60000 | Clamped to `[30000, 300000]` |

Library row near existing RA credentials: **Achievement theater**.

### What it does not do

- Does not hardcore-toggle, does not submit cheevos, does not
  read `MemAddr` to predict unlocks in v1 (that is a lens, and
  only if a pack says so).
- Does not toast every poll failure.
- Does not run during yield. Does not fetch at 5 Hz.
- Does not require Talk to RetroArch.

---

## 4 — Second seat

### Problem

Android has one focused window. Focus lock already keeps P1’s pad
in the KEEP game while the host stays tappable. The host can be
player 2 if — and only if — inject is opt-in, launch-display-only,
and refused on yield.

RetroArch network commands cannot send buttons. There is no
`INJECT_EVENTS` permission. Seat v1 therefore uses the **same
assist gesture path as the Winlator cockpit**, aimed at calibrated
points on the **launch display**.

### When

```
fun SecondSeatPolicy.allowed(
    dualMode: Boolean,
    playHostAllowed: Boolean,
    sessionOwnsCompanion: Boolean,
    assistConnected: Boolean,
    seatEnabled: Boolean,
    playerIsRa: Boolean,
    hostSurface: HostSurface,
): Boolean
```

True only when dual + play host + not yield + assist connected +
`raSecondSeat` on + RA player + surface is `SEAT` or may become
`SEAT`.

Without assist: chip visible, body is the hint
“Enable Input assist to use the second seat.” No inject.

Winlator: seat chip hidden (cockpit owns exclusive).
Non-RA KEEP: chip hidden in v1 (no UDP buttons, no calibrated
overlay to tap). DraStic / PPSSPP / Eden are out.

### Chrome

Chip **Seat** sets `hostSurface = SEAT` (does **not** claim `HOST`;
P1 must keep the pad). Body: a simple digital cluster (D-pad +
A/B/X/Y + Start/Select). No analog stick in v1.

Touch on a cluster button → `dispatchGesture` tap or hold on the
launch display at that button’s calibrated normalized point
`(x, y)` in `0f..1f`. Repeat while held (assist gesture hold),
cancel on up.

`InputAssistPolicy.mayInjectSeat(...)` is the gate inside the
service. Yield must disable inject immediately (same as cockpit).

### Calibration

Settings → Controls → **Second seat layout**. A rectangle
representing the launch display; eight draggable anchors (default
SNES-like cluster in the lower-right 40%). Stored as:

```
data class SeatAnchor(val id: String, val nx: Float, val ny: Float)
// id in {up,down,left,right,a,b,x,y,start,select} — v1 uses 8 + start/select
```

`Settings.raSeatAnchors: List<SeatAnchor>` (empty = defaults).
No PixelCopy of the game. The user lines anchors up with wherever
they put RA’s on-screen overlay (or a known tap-friendly core).

If launch-display gestures are missing (API / ROM): disable inject,
keep the hint. Do not send gestures to the play-host display.

### Settings / actions

| Field / action | Default | Role |
|---|---|---|
| `raSecondSeat` | false | Master |
| `raSeatAnchors` | empty → built-in defaults | Layout |
| `Action.TOGGLE_SEAT` | unmapped | Chip equivalent |

### What it does not do

- Does not create a virtual `InputDevice` or require root / UHID.
- Does not write `input_player2_*` into `retroarch.cfg` in v1.
- Does not inject into melonDualDS / Azahar / greedy / non-RA.
- Does not claim `HOST` (that would steal P1).
- Does not promise analog, rumble, or simultaneous three players.

---

## 5 — Save ferry

### Problem

`RomIdentity.hash` already says two rows are the same title.
Players still treat each emulator folder as a separate planet.
The user should be able to copy a battery / RA state **inside
trees they already granted**.

### When

Library / details, not a KEEP tick. Allowed when:

```
fun SaveFerry.sameTitle(a: RomIdentity?, b: RomIdentity?): Boolean =
    a != null && b != null && a.ready && b.ready &&
        !a.hash.isNullOrBlank() && a.hash == b.hash
```

Also accept `groupId` equality when both ready and hashes differ
but group matches (clone stack). Refuse if either identity is not
ready.

Refuse if an open **YIELD** session’s `packageName` owns the
destination file (do not write under a running dual-surface
emulator). KEEP RA: allowed with confirm (file may be open; user
asked).

### Offer

```
enum class FerryKind { RA_SRM, RA_STATE }

data class FerryOffer(
    val fromRomId: String,
    val toRomId: String,
    val kind: FerryKind,
    val fromUri: String,
    val toUri: String?,
    val slot: Int?,                 // RA_STATE only
)

object SaveFerry {
    fun candidates(
        from: RomEntry,
        identities: Map<String, RomIdentity>,
        entries: List<RomEntry>,
        grantedSaveUris: List<String>,
    ): List<FerryOffer>

    fun samePlayerHint(fromPlayer: String?, toPlayer: String?): Boolean
}
```

v1 kinds:

| Kind | Locate (granted trees only) |
|---|---|
| `RA_SRM` | `saves/**/<stem>.srm` under RA `EXTERNAL` / common `RetroArch/saves` |
| `RA_STATE` | `states/<stem>.state` or `.stateN` (user slots 1–8 only, never cinema 9–12) |

`samePlayerHint` is `playerId` prefix `ra-` on both sides (or both
packages `com.retroarch.aarch64`). **Different player → refuse**
with one reason string (“Cannot convert DraStic ↔ RetroArch in
this version”). No format translation in v1.

No whole-card scan. If nothing is readable, the row is hidden.

### Execute

Details → **Saves** → pick a candidate → confirm:

“Copy <kind> from <from name> to <to name>. The destination will
be overwritten.”

On confirm, `ROM_IO`:

1. Open `fromUri` via existing grant. Fail → toast, stop.
2. Create/truncate `toUri` if the parent is writable under a grant.
   Else toast “Destination is not writable” (scoped RA
   `Android/data/…` is the common miss — same honesty as the
   network-cmd cfg dialog).
3. Stream copy. No rewrite of `RomEntry.id`. No Settings notify
   beyond a one-line toast.

Never `force-stop` RA to unlock the file. If the copy is short or
throws, leave the destination untouched (write to `*.tmp` then
rename when the SAF document allows; if rename is impossible,
write in place and accept the risk — document in the confirm).

### Settings

No master off-switch required (it is a details action). Optional
`saveFerryEnabled` default **true** hides the row when false.

### What it does not do

- Does not convert Azahar / Citra / DraStic / PPSSPP formats.
- Does not request `MANAGE_EXTERNAL_STORAGE`.
- Does not migrate playtime onto hashes (still owned-surface §6
  non-goal).
- Does not run on a timer. Does not ferry cinema band 9–12.

---

## 6 — Posture theater

### Problem

The Sugar is a clamshell. Topology and stage plots already choose
a launch face. The hinge can *pause honestly* and *suggest* a
plot. It must not silently YIELD melonDualDS mid-frame.

### Reading

```
enum class DevicePosture { UNKNOWN, CLOSED, TABLETOP, BOOK, FLAT }

object PosturePolicy {
    fun fromSensors(hingeDeg: Float?, deviceState: Int?): DevicePosture

    fun effect(
        posture: DevicePosture,
        previous: DevicePosture,
        dualMode: Boolean,
        sessionOwnsCompanion: Boolean,
        keepRaPlaying: Boolean,
        suggestYieldEnabled: Boolean,
    ): PostureEffect
}

enum class PostureEffect { NONE, PAUSE_IF_PLAYING, SHOW_YIELD_CHIP, HIDE_YIELD_CHIP }
```

Suggested buckets (tunable constants, host-tested):

| Hinge | Posture |
|---|---|
| missing / NaN | `UNKNOWN` |
| `< 15°` | `CLOSED` |
| `15°–140°` | `TABLETOP` |
| `140°–170°` | `BOOK` |
| `≥ 170°` | `FLAT` |

`DeviceStateManager` (API 31+) `DeviceState` ids are OEM-specific.
Use them only as a hint when the hinge sensor is missing. Unknown
OEM ids → `UNKNOWN`.

Missing both sensors → `UNKNOWN` → `NONE`. No fake posture.

### Effects

| Effect | When | Action |
|---|---|---|
| `NONE` | UNKNOWN, or yield/greedy, or !dual, or same posture | Do nothing |
| `PAUSE_IF_PLAYING` | Edge into `CLOSED`, KEEP RA, Talk on, `GET_STATUS` PLAYING | `enqueueRaUdp` `PAUSE_TOGGLE` once. Playtime already pauses on sleep (shipped). |
| `SHOW_YIELD_CHIP` | Edge into `FLAT`, `postureSuggestYield` on, current player’s built-in policy is KEEP, not already YIELD | In-place chip “Use both screens?” → existing owned-surface **confirm** (`confirm_yield_on_keep_player`). Cancel hides the chip. |
| `HIDE_YIELD_CHIP` | Leave `FLAT` | Hide chip. Do not change the plot. |

**Never relaunch. Never change `SessionSurface.policy` from a
sensor. Never auto-confirm. Never apply a launch face mid-session.**
Launch face stays a stage-plot / fire-time concern.

`CLOSED` does not `finish` activities. The OS may sleep the device;
we only pause RA if we still can.

### Settings

| Field | Default | Role |
|---|---|---|
| `postureAware` | true | Master for pause-on-close |
| `postureSuggestYield` | false | FLAT chip |

Display & Grid → **Posture**: two toggles. SINGLE hides the page
section.

### What it does not do

- Does not auto-YIELD. Does not auto-KEEP.
- Does not remap display 0/1. Does not fight SWAP.
- Does not run cinema / tracker faster or slower by angle.
- Does not treat `FLAT` as “this is a 3DS, steal both panels.”

---

## 7 — Helper embed

### Problem

Pinned companion apps already exist, but pin + KEEP fights
`pinConflictsWithSession` when the pin *is* the game, and a pin
is a role, not a play-host tool. Users want a *wiki / notes /
rules* package on the host while the pad stays in the KEEP game.

### When

```
fun HelperEmbedPolicy.mayEmbed(
    playHostAllowed: Boolean,
    sessionOwnsCompanion: Boolean,
    helperPackage: String?,
    sessionPackage: String?,
    embedAvailable: Boolean,
): Boolean {
    if (!playHostAllowed || sessionOwnsCompanion) return false
    if (helperPackage.isNullOrBlank()) return false
    if (helperPackage == sessionPackage) return false
    return embedAvailable
}

fun HelperEmbedPolicy.mayLaunchOnHostDisplay(...): Boolean = false
// v1: embed or nothing. A NEW_TASK on the host display can
// replace CompanionActivity. Fail closed.
```

`ActivityEmbed.available()` is the shipped hidden `ActivityView` /
`TaskView` probe. If false, the Helper chip shows “Cannot embed on
this system” and does not launch.

### Package choice

Resolution, first non-blank wins:

1. Per-ROM `Settings.romHelpers[romId]`
2. Global `Settings.playHostHelperPackage`
3. None → chip hidden

Picker is the existing app picker, filtered with
`pinConflictsWithSession` against the *current* session (and
against a static refuse list: `me.magnum.melondualds`,
`org.azahar_emu.azahar`, and the current session package).

Winlator cockpit: helper chip hidden (exclusive).

### Chrome / input

Chip **Helper** sets `hostSurface = HELPER` and
`ActivityEmbed.attach` into a full-bleed host
(`TAG_PLAY_HUD_HELPER`). **Back to HUD** releases the embed and
returns `HUD`.

Focus lock stays `GAME` so P1’s pad stays in the KEEP game.
Touches go to the embedded task (the embed child is still inside
the not-focusable play-host window when the ROM allows that).

Honest failure: if the embed takes key focus anyway, the existing
host-timeout path applies — owner becomes `HOST` on touch,
returns to `GAME` after `inputHostTimeoutMs`. Do not invent a
second focus protocol.

If `attach` returns false: release, `hostSurface = HUD`, one toast.

Yield / greedy / session end: `ActivityEmbed.release` immediately.

### Settings

| Field | Default | Role |
|---|---|---|
| `playHostHelperPackage` | null | Global |
| `romHelpers: Map<String, String>` | empty | Per-ROM package |

Apps → **Play-host helper**. Details → **Helper app**.

### What it does not do

- Does not embed the session package. Does not embed melonDualDS /
  Azahar “to put DS on one panel and a map on the other” — that is
  yield theft.
- Does not fall back to `setLaunchDisplayId` of a third task in v1.
- Does not use `SYSTEM_ALERT_WINDOW`.
- Does not replace Companion roles (Hero / Now / Perf / Pin stay
  idle-HOME concerns). Helper is KEEP play-host only.

---

## 8 — Predictive resume (warm Continue)

### Problem

Continue already knows the last key. First A still pays RA probe
+ “which slot?” + a cold core. We can warm the *path* without
starting an emulator behind the user’s back.

### What v1 is

```
object WarmResumePolicy {
    fun mayProbe(
        dualMode: Boolean,
        sessionOpen: Boolean,
        continueKey: String?,
        playerIsRa: Boolean,
        warmEnabled: Boolean,
        lastProbeMs: Long,
        nowMs: Long,
    ): Boolean

    fun mayAutoload(
        warmLoadEnabled: Boolean,
        launchReason: LaunchReason,
        playerIsRa: Boolean,
        slot: Int?,
    ): Boolean
}

enum class LaunchReason { CONTINUE, SLOT, SWITCHER, OTHER }
```

| Step | When | Does |
|---|---|---|
| Resolve | Idle HOME, Continue key set | Remember player id + cinema pin / last user slot in process memory. No launch. |
| Probe | `mayProbe` (idle, RA, Talk on, ≥60s since last) | One `VERSION` / `GET_STATUS` on `enqueueRaUdp` so the next KEEP HUD is not a 5s discover. |
| Art | Idle | Existing ArtCache prefetch of Continue only (no new pool). |
| Autoload | `mayAutoload` after a **Continue** KEEP RA launch | `LOAD_STATE_SLOT n` once, 400ms after `beginSession`, same budget style as handoff prep. `n` = cinema pin else last user slot else skip. |

`LaunchReason.CONTINUE` is only the Continue chip / last-played
dock action / `LibraryBrowse` continue key. Grid slot, search,
switcher, and “open with” are `OTHER` or `SWITCHER` and **never**
autoload.

Handoff still does not autoload the *destination* (owned-surface
§2). Warm load is not a back door for switcher launches.

### What v1 is not

- Not `startActivity` of RetroArch before the user presses A.
- Not a second process. Not a bound service inside RA.
- Not warm of melonDualDS / Azahar / Winlator / DraStic.
- Not a prefetch of the whole library.

Background-start limits and dual-display `setLaunchDisplayId`
races are why the process stays cold. If a future Android allows
a safe warm start, that is a new spec.

### Failure

- Probe fail: stay silent (same as HUD probe).
- Autoload fail / timeout: game stays on RA’s own last-content
  rules. No toast storm.
- Yield target: `mayAutoload` false.

### Settings

| Field | Default | Role |
|---|---|---|
| `warmResumeEnabled` | true | Resolve + probe + art |
| `warmResumeLoad` | false | Continue-only `LOAD_STATE_SLOT` |

Library row: **Warm Continue**. Nested: **Load last cinema slot
on Continue** (off).

### What it does not do

- Does not double-launch. Does not `CLEAR_TASK`.
- Does not write RA cfg.
- Does not skip `noteLaunch` / `beginSession`.

---

## Data / settings (schema v11)

`Settings.schemaVersion` becomes **11**. `SettingsStoreTest` covers
missing-v11 → defaults. All new fields optional on disk. v10 keys
stay.

| Field | Default | Phase |
|---|---|---|
| `ramTrackersEnabled` | true | 1 (still behind `ramLensesEnabled`) |
| `raCinemaEnabled` | false | 2 |
| `raCinemaIntervalMs` | 60000 | 2 |
| `raTheaterEnabled` | false | 3 |
| `raTheaterPollMs` | 60000 | 3 |
| `raSecondSeat` | false | 4 |
| `raSeatAnchors` | empty | 4 |
| `saveFerryEnabled` | true | 5 |
| `postureAware` | true | 6 |
| `postureSuggestYield` | false | 6 |
| `playHostHelperPackage` | null | 7 |
| `romHelpers` | empty | 7 |
| `warmResumeEnabled` | true | 8 |
| `warmResumeLoad` | false | 8 |

Process-only (not Settings):

- `hostSurface: HostSurface`
- `cinemaFrames: List<CinemaFrame>`
- `cinemaPinnedSlot: Int?`
- `warmLastProbeMs: Long`
- existing `hostClaimed`, `playHudExpanded`, `sessionSurface.greedy`,
  `lensDisabledThisProcess`

New strings (all five catalogs, then
`python3 scripts/i18n_audit.py --write && --check`):

- `play_hud_tracker`, `play_hud_cinema`, `play_hud_theater`
- `play_hud_seat`, `play_hud_helper`, `play_hud_back_to_hud`
- `settings_ram_trackers`, `settings_ra_cinema`, `settings_ra_theater`
- `settings_second_seat`, `settings_second_seat_layout`
- `settings_save_ferry`, `confirm_save_ferry`
- `settings_posture`, `settings_posture_suggest_yield`, `posture_use_both_screens`
- `settings_play_host_helper`, `helper_embed_unavailable`
- `settings_warm_resume`, `settings_warm_resume_load`
- `seat_need_assist`, `ferry_refuse_player`, `ferry_dest_unwritable`

Log tags: `GGTrack`, `GGCinema`, `GGTheater`, `GGSeat`, `GGFerry`,
`GGPosture`, `GGHelper`, `GGWarm`, plus existing `GGInput` /
`GGLens` / `GGSession` / `GGOracle` / `GGPaint`.

## Dual-paint additions

- Tracker / cinema / theater ticks are not SETTINGS, not BROWSE,
  not SELECTION (theater may use existing `SELECTION_ONLY` when
  awarded count changes).
- `hostSurface` flips are in-place add/remove of a child on the
  play host. One full companion rebuild is allowed if attach fails
  once (same as the session switcher).
- Posture is not a deck notify. The FLAT chip is in-place.
- Ferry / helper package / seat anchors are user Settings.
- Warm probe is not a deck notify.
- Yield / greedy still tears down every surface in this spec.

## Phases

Ship in this order. 5 and 6 may overlap 1–3. 4 starts after 1 is
painted. 7 after 4’s inject lessons (or after 1 if seat slips).
8’s autoload after 2.

| Phase | Ships | Gate |
|---|---|---|
| **1 — Tracker** | `surface`/`widgets` on lens JSON, in-place grid, `GGTrack` | `ramLensesEnabled` still default off |
| **2 — Cinema** | Band 9–12, interval capture, filmstrip load, `GGCinema` | Default off |
| **3 — Theater** | `RaTheater` parse, poll, next/last lines, `GGTheater` | Default off |
| **5 — Ferry** | `SaveFerry` candidates + SAF copy + confirm | Details only |
| **6 — Posture** | `PosturePolicy`, pause-on-close, optional FLAT chip | Never auto-YIELD |
| **4 — Seat** | Cluster, anchors, assist gestures on launch display | Default off, assist required |
| **7 — Helper** | `HelperEmbedPolicy`, exclusive embed, fail closed | No session package |
| **8 — Warm** | Resolve + probe + optional Continue load | Load default off |

Do not ship 4 default-on. Do not ship 8 load default-on. Do not
ship 6’s FLAT path without the existing YIELD confirm. Do not
ship 1 as a second UDP socket.

Host tests are not device proof. Do not claim the Sugar matrix
from this doc.

## Device matrix (Sugar)

**Not run.** Intended checks, not observed proof.

| Step | Must see |
|---|---|
| RA SNES KEEP, lenses off | No `READ_CORE_RAM`, no tracker. |
| Lenses on, fixture tracker, hash ready | Grid updates ≤5 Hz. `GGPaint` has no FULL storm. Pad stays in game. |
| melonDualDS | No tracker, cinema, theater, seat, helper, warm load. Both panels DS. |
| Cinema off | No slots 9–12 writes. |
| Cinema on, 1 min | One `SAVE_STATE_SLOT` in 9–12. Strip shows a new chip. Slots 1–8 untouched. |
| Tap cinema frame | Game loads that slot. Owner stays GAME. |
| Theater off | No extra RA HTTP while KEEP. |
| Theater on, credentials | Next locked line paints. Poll ≤1/min. Unlock increments in-place. |
| Ferry, two RA clones, same hash | Confirm copy. Destination bytes match. |
| Ferry, DraStic → RA | Refused with reason. No write. |
| Close hinge, KEEP RA playing, Talk on | One pause. No YIELD. |
| Flat, suggest off | No chip. |
| Flat, suggest on | Chip → confirm → only then a plot. Cancel = KEEP. |
| Seat, assist off | Hint, no gestures. |
| Seat, assist on, KEEP RA | P1 pad still in game. P2 taps gesture on **launch** display. |
| Seat during melonDualDS | No service inject. |
| Helper = same as session | Chip refuses / hidden. |
| Helper = maps app, embed available | Embed on host. Pad stays in game until touch-focus. Back to HUD releases. |
| Helper, embed missing | No third-task launch. Toast. |
| Warm probe, idle | At most one UDP / 60s. No RA activity on a display. |
| Continue, warm load off | Launch only. |
| Continue, warm load on, cinema pin 10 | After ~400ms, `LOAD_STATE_SLOT 10`. Grid launch of the same ROM does **not**. |
| SWAP during KEEP + tracker | Surfaces follow the new play host. Game display stays clean. |

## Host tests (pure)

- `TrackerCatalog.acceptable`: line-only still ok; widget block out
  of range rejected; BITS labels longer than bits rejected;
  `MAX_BYTES` still 256.
- `CinemaPolicy.nextSlot` cycles 9–12; `shouldCapture` false when
  !enabled, !playHost, !slotsLive, or interval not elapsed.
- `RaTheater.parse` empty/malformed → empty snap; nextLocked skips
  unlocked; `newlyUnlocked` diff.
- `RaProgressGate` still `SELECTION_ONLY` / never SETTINGS.
- `SecondSeatPolicy.allowed` false on yield, !assist, !RA, SINGLE.
- `InputAssistPolicy.mayInjectSeat` false when
  `sessionOwnsCompanion`.
- `SaveFerry.sameTitle` / `samePlayerHint`; refuse different
  player; refuse !ready identity.
- `PosturePolicy.fromSensors` table; `effect` never returns a
  “set policy” action; FLAT + suggest off → NONE.
- `HelperEmbedPolicy.mayEmbed` false on session package, yield,
  blank helper, !playHost; `mayLaunchOnHostDisplay` stays false.
- `WarmResumePolicy.mayProbe` false when session open; `mayAutoload`
  true only for `CONTINUE` + enabled + slot; switcher/slot false.
- `HostSurfacePolicy.exclusive` seat/helper/cockpit.
- Dual-paint: existing playHost / oracle / yield tables unchanged.

## Verification (agents)

```text
verify: rg -n "SYSTEM_ALERT_WINDOW|TYPE_APPLICATION_OVERLAY" app/src  # must stay gone
verify: rg -n "WRITE_CORE_RAM" app/src  # must stay gone
verify: rg -n "INJECT_EVENTS|injectInputEvent" app/src  # must stay gone
verify: rg -n "HostSurface|CinemaPolicy|RaTheater|SecondSeat|SaveFerry|PosturePolicy|HelperEmbed|WarmResume" app/src/main/java
verify: python3 scripts/i18n_audit.py --check
verify: ./gradlew :app:testDebugUnitTest --offline --tests '*PlayHost*' --tests '*LensCatalog*' --tests '*RaCommand*' --tests '*RaProgress*' --tests '*InputAssist*' --tests '*RomIdentity*' --tests '*DualPaint*' --tests '*CompanionRole*'
```

Device: run the matrix. Host green is not a Sugar claim.

## Code map (intended)

| Unit | Kind | Phase |
|---|---|---|
| `rom/LensCatalog.kt` (grow) | Pure | 1 |
| `rom/TrackerCatalog.kt` | Pure | 1 |
| `rom/CinemaPolicy.kt` | Pure | 2 |
| `library/RaTheater.kt` | Pure | 3 |
| `input/SecondSeatPolicy.kt` | Pure | 4 |
| `input/InputAssistPolicy.kt` (grow) | Pure | 4 |
| `rom/SaveFerry.kt` | Pure | 5 |
| `display/PosturePolicy.kt` | Pure | 6 |
| `ui/HelperEmbedPolicy.kt` | Pure | 7 |
| `rom/WarmResumePolicy.kt` | Pure | 8 |
| `ui/HostSurface.kt` | Pure | all |
| Tracker / cinema / theater / seat views | `CompanionPanel`, in-place | 1–4 |
| Ferry UI | Details / Settings | 5 |
| Hinge listener | Application or play host | 6 |
| Helper attach | Existing `ActivityEmbed` | 7 |
| Warm probe | `GhostGalleonApp` idle | 8 |

No fourth session type. `OpenSession` stays playtime.
`SessionSurface` stays the policy record. `HostSurface` is
process-only chrome on the play host. Stage plot still feeds
`forLaunch` only.

Task-by-task implementation plan: not written yet. Ask for it
the same way owned-surface was planned
([`superpowers/plans/2026-08-13-owned-surface.md`](superpowers/plans/2026-08-13-owned-surface.md)).
