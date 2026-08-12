# Dual-paint invariants

Host-tested dual-display paint rules for Ghost Galleon. Violating them can
leave **both physical panels pure black** while accessibility trees stay
alive — the GPU never presents a new buffer.

Authoritative code: `ui/DualPaintPolicy.kt`, `ui/BaseDeckActivity.kt`,
`library/RaProgressGate.kt`, `ui/deck/CompanionHeroMetrics.kt`.
Agent checklist: root `AGENTS.md` (dual-paint section). Diagnostics:
`adb logcat -s GGPaint` (`FULL` / `DEFER`).

How a launched game shares the two panels (yield DS/3DS, keep companion
for single-surface players):
[`split-session-ownership.md`](split-session-ownership.md).

## Policy constants

| Constant | Value | Role |
|----------|-------|------|
| `MIN_FULL_RENDER_GAP_MS` | 32 | Coalesce full `setContentView` rebuilds |
| `MIN_HEAL_GAP_MS` | 2000 | Debounce companion heal launches |
| `DRAWER_DEBOUNCE_MS` | 450 | All-apps open/toggle debounce |

## Checklist (do not violate)

1. **One full paint at a time.** `renderFromState` re-entrancy is blocked;
   nested paint is a debug `check` failure (`BuildConfig.DEBUG`).

2. **Coalesce full paints** (`MIN_FULL_RENDER_GAP_MS` 32). Thrash was
   >10 `setContentView`/s. **Never drop a blocked SETTINGS rebuild
   permanently:** when `allowFullRender` denies, schedule a deferred retry
   via `DualPaintPolicy.deferredFullRenderDelayMs` (`BaseDeckActivity`
   posts `DEFER` in `GGPaint`). Nested → post ASAP after current paint;
   coalesce gap → remaining ms. Stale carousel after chip taps is worse
   than a short delay.

3. **Paint after real `displayId`.** Prefer `onResume` /
   `onAttachedToWindow` once `setLaunchDisplayId` has attached; track
   `paintedForDisplayId` and re-paint on change.

4. **SETTINGS notify is expensive.** Only `updateSettings` /
   `publishRomEntries` / explicit user-data loads. **Browse chips use
   `Change.BROWSE`, not SETTINGS** (`DeckState.setLibraryBrowse`). Hero
   chrome (RA line, Resume dismiss) uses `notifySelectionRefresh()`
   (SELECTION), never `notifyChanged()` (SETTINGS full rebuild).

5. **Network never drives full deck rebuilds.** RetroAchievements: one
   attempt per ROM per process (`RaProgressGate.mayFetch`); store only if
   progress changed; notify SELECTION only.

6. **SECONDARY_HOME absorb is silent.** Existing companion on the target
   wins. Do **not** open All-apps, mass-finish peers, or force full
   repaint on the survivor. Do **not** revert to “newest finishes all
   others”.

7. **Heal is rare and dumb.** 2s debounce; launch only if the secondary
   target is empty. Returning from an app/emulator recreates Companion
   (`MainActivity.restartCompanionPanel`) so pure-black secondary buffers
   can clear without system Force Stop.

8. **No quiet rescan / RA fetch on every resume** (storms `contentEpoch`
   / selection notifies).

9. **All-apps drawer is Main-only.** Companion never opens it. Absorb /
   storms use `allowToggle=false` (open-only / no-op if open).

10. **Companion hero title must fit.** `CompanionHeroMetrics` scales art
    and name on short secondary panels; never fixed 240dp art + 32sp
    title that clips mid-glyph above actions.

## Granular re-render map

`DeckState.Change` drives how each activity reacts:

| Change | Primary (interactive) | Companion |
|--------|------------------------|-----------|
| **SELECTION** | In-place (`Deck.updateSelection`); full rebuild only if that fails | `CompanionPanel.updateSelection` |
| **BROWSE** | GameDeck: `applyBrowseChange()` — recompute entries, filter chrome, new carousel adapter; **no** activity `setContentView`. Else full rebuild | Selection chrome only |
| **MODE / DISPLAY / SETTINGS** | Full rebuild (SETTINGS may defer via coalesce; never silently abandon) | Full rebuild |

### Browse path (critical for chips)

- `setLibraryBrowse(query, force=false)` tags **BROWSE**.
- Pass `force=true` when a chip must re-scroll / rebuild even if the
  query is unchanged (Continue re-tap; All re-tap after coalesce).
- **All** = full `BrowseQuery()` reset with `force=true`, then `select`
  the first unrestricted entry (filter-clear alone can leave a prior
  platform selection centered).
- **Continue** (Game Mode chip) does **not** launch. Resolves
  `continueKey` over the full library with **live** `app.settings`, then
  RECENT + `select(cont, force=true)`. Quick Panel Continue still
  selects + launches.
- `adoptLibraryBrowse` corrects sanitize without notifying (used inside
  `GameDeck.buildEntries`).

### Selection hot paths

- GameDeck: **payload** `notifyItemChanged` for previous+next cards only
  — never `notifyDataSetChanged()` on every NAV.
- GridDeck: in-place `rebindVisibleCells` / `applySelectionVisuals`;
  `getView` **reuses convertView** when the slot key is unchanged.
  Never `adapter.notifyDataSetChanged()` for selection (scroll bug:
  `LAYOUT_SYNC` restores position 0).

## Companion hero chrome

Short secondary panel (Sugar bottom ~540dp):

- One **compact subline**: `HeroDetail.compactSubline` →
  `platform · play meta · player` (not three stacked rows).
- **Resume**: filled accent pill + **white** text
  (`TileBackgrounds.accentPill`).
- **Quick chips** (Favorite / Pin / Art / Open with): dark rounded idle
  chips + white text + fixed height + `isBaselineAligned = false`.

## Performance adjacent to paint

These reduce how often full paints fire or how expensive they are:

- Release: R8 minify + resource shrink (`proguard-rules.pro`).
- Settings: compact JSON; debounced async save (120 ms) with
  `flushSettingsNow()` on activity pause.
- App catalog pre-warm on `APP_IO`.
- Art decode: 2-thread pool; `invalidate` drops all `romId|src:…` keys.
- AppPicker: sort ROMs once + 60 ms search debounce.

## Recovery (pure-black panels)

In-app: **Settings → System** shows the dual-black checklist and a
**Restart companion panel** action (calls `MainActivity.restartCompanionPanel`
without App Info Force Stop).

1. Settings → System → Restart companion panel, or press **X** once or twice
   (swap always recreates Companion in dual mode — see
   `shouldRestartCompanionAfterSwap`).
2. Launch any game and return (`restartCompanionPanel("return-from-app")`).
3. `adb shell am force-stop com.visorcraft.ghostgalleon` then HOME (or system
   Force Stop if no adb).
4. Reboot if buffers stay stuck.

Heal policy: `companionHealAction` restarts peers that claim the secondary
target but are not STARTED-healthy; launches when empty. PERF_HUD live
refresh uses `PERF_HUD_REFRESH_MS` in-place only — never SETTINGS thrash.

Then fix the policy violation that caused thrash — do not paper over with
more full rebuilds. After every `adb install -r`, force-stop: this ROM can
keep the old process alive.

## Device QA before claiming dual-UI done

- Both panels show real pixels after browse chips, HOME swipe, and app
  return.
- `GGPaint` is not logging continuous `FULL` storms.
- Companion absorb does not flash All-apps.
- Hero title does not clip mid-glyph on the short panel.
- Grid long-press menu scrolls; **Remove from grid** is near the top.
