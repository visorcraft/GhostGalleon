package com.visorcraft.ghostgalleon.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.library.SetupNeeds
import com.visorcraft.ghostgalleon.rom.Platforms
import com.visorcraft.ghostgalleon.rom.PlayerResolver
import com.visorcraft.ghostgalleon.settings.Action
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.deck.GridWrapNav
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.resolveText
import com.visorcraft.ghostgalleon.ui.strokePx
import com.visorcraft.ghostgalleon.ui.useTabularNumerals

/**
 * Pure, host-tested navigation, progress, checklist hierarchy, and contrast
 * logic for [SetupCard].
 */
internal object SetupCardNav {

    enum class ButtonRole { PRIMARY, SECONDARY, QUIET }

    enum class CheckRowTone { DONE, REQUIRED_PENDING, OPTIONAL_PENDING }

    enum class Outcome { NONE, MOVED, CONFIRM, DISMISS }

    data class StepResult(
        val selection: Int,
        val outcome: Outcome,
    )

    data class SetupProgress(
        val done: Int,
        val total: Int,
        val fraction: Float,
        val percent: Int,
        val allDone: Boolean,
    )

    const val PAGE_STEP = 3
    const val FOCUS_ANIM_MS = 90L
    internal const val BUTTON_RIPPLE_COLOR: Int = 0x2EFFFFFF

    fun progress(items: List<Pair<*, Boolean>>): SetupProgress {
        val total = items.size
        val done = items.count { it.second }
        val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
        val percent = if (total <= 0) 0 else ((done * 100 + total / 2) / total).coerceIn(0, 100)
        return SetupProgress(
            done = done,
            total = total,
            fraction = fraction,
            percent = percent,
            allDone = total > 0 && done == total,
        )
    }

    /** Normalized `(filledWeight, emptyWeight)` pair summing to `1f` for the progress bar. */
    fun progressTrackWeights(fraction: Float): Pair<Float, Float> {
        val filled = fraction.coerceIn(0f, 1f)
        return filled to (1f - filled)
    }

    /**
     * Distinguish completed rows, required pending actions (ROM folder in full
     * setup; both chrome toggles in discover-only mode), and optional items.
     */
    fun checkRowTone(index: Int, done: Boolean, chromeOnly: Boolean): CheckRowTone = when {
        done -> CheckRowTone.DONE
        chromeOnly || index == 0 -> CheckRowTone.REQUIRED_PENDING
        else -> CheckRowTone.OPTIONAL_PENDING
    }

    /** WCAG-AA foreground color for a checklist row on the sunken checklist card. */
    fun checkRowTextColor(
        tone: CheckRowTone,
        accent: Int,
        tokens: ThemeTokens,
    ): Int = when (tone) {
        CheckRowTone.DONE -> ThemePack.accentTextColor(accent, ThemePack.surfaceSunken(tokens))
        CheckRowTone.REQUIRED_PENDING -> UiDimens.ALPHA_PRIMARY
        CheckRowTone.OPTIONAL_PENDING -> UiDimens.ALPHA_SECONDARY
    }

    fun step(selection: Int, count: Int, action: Action): StepResult {
        if (action == Action.BACK) {
            return StepResult(GridWrapNav.clampIndex(selection, count), Outcome.DISMISS)
        }
        if (count <= 0) {
            return StepResult(0, Outcome.NONE)
        }
        val cur = GridWrapNav.clampIndex(selection, count)
        return when (action) {
            Action.NAV_UP, Action.NAV_DOWN -> StepResult(
                selection = GridWrapNav.moveAction(cur, count, columns = 1, action = action),
                outcome = Outcome.MOVED,
            )
            Action.PAGE_PREV -> StepResult(
                selection = GridWrapNav.pageMove(cur, count, pageSize = PAGE_STEP, deltaPages = -1),
                outcome = Outcome.MOVED,
            )
            Action.PAGE_NEXT -> StepResult(
                selection = GridWrapNav.pageMove(cur, count, pageSize = PAGE_STEP, deltaPages = 1),
                outcome = Outcome.MOVED,
            )
            Action.CONFIRM -> StepResult(cur, Outcome.CONFIRM)
            else -> StepResult(cur, Outcome.NONE)
        }
    }

    /**
     * Accessible text color on a filled [accent] primary CTA. Prefers
     * [ThemePack.onFillTextColor] when it meets WCAG AA (`>= 4.5:1`), else
     * selects whichever of black or white provides higher contrast so Ghost,
     * Teal, OLED, Neon, and custom themes all stay >= 4.5:1 legible.
     */
    fun accessibleOnAccent(accent: Int): Int {
        val preferred = ThemePack.onFillTextColor(accent)
        if (ThemePack.contrastRatio(preferred, accent) >= ThemePack.MIN_TEXT_CONTRAST) {
            return preferred
        }
        val white = UiDimens.ALPHA_PRIMARY
        val black = 0xFF000000.toInt()
        return if (ThemePack.contrastRatio(black, accent) >= ThemePack.contrastRatio(white, accent)) {
            black
        } else {
            white
        }
    }

    /** Classify a setup CTA into PRIMARY, QUIET (Skip), or SECONDARY. Pure; host-tested. */
    fun buttonRoleFor(isPrimary: Boolean, isSkip: Boolean): ButtonRole = when {
        isPrimary -> ButtonRole.PRIMARY
        isSkip -> ButtonRole.QUIET
        else -> ButtonRole.SECONDARY
    }

    /**
     * Indices needing visual repaint on selection transition `[prev] -> [next]`:
     * returns all indices on initial paint (`prev < 0`), empty on no-op, and
     * strictly `<= 2` indices `[prev, next]` on every gamepad navigation step.
     * Pure; host-tested.
     */
    fun dirtyButtonIndices(prev: Int, next: Int, count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val safeNext = GridWrapNav.clampIndex(next, count)
        if (prev < 0) return (0 until count).toList()
        val safePrev = GridWrapNav.clampIndex(prev, count)
        return if (safePrev == safeNext) emptyList() else listOf(safePrev, safeNext)
    }

    /**
     * OLED-safe & WCAG 1.4.11 focus stroke color for each [ButtonRole]:
     * - [ButtonRole.PRIMARY]: [SettingsUiLogic.activePillFocusRing] (distinct from both
     *   the filled accent pill and dark card, preventing white-on-white OLED rings).
     * - [ButtonRole.SECONDARY] / [ButtonRole.QUIET]: [OverlayPrimitives.accessibleFocusStroke]
     *   against the lighter of `cardFill` and `chipIdle` (`>= 3.0:1`).
     * Pure; host-tested.
     */
    fun buttonFocusStroke(
        role: ButtonRole,
        accent: Int,
        tokens: ThemeTokens,
    ): Int = when (role) {
        ButtonRole.PRIMARY -> SettingsUiLogic.activePillFocusRing(accent)
        ButtonRole.SECONDARY, ButtonRole.QUIET -> {
            val card = ThemePack.cardFill(tokens)
            val surface = if (ThemePack.relativeLuminance(tokens.chipIdle) >
                ThemePack.relativeLuminance(card)
            ) {
                tokens.chipIdle
            } else {
                card
            }
            OverlayPrimitives.accessibleFocusStroke(accent, surface)
        }
    }

    /** Resolved `(backgroundFill, textColor)` for unit-testing button legibility. */
    fun buttonColors(
        role: ButtonRole,
        selected: Boolean,
        accent: Int,
        tokens: ThemeTokens,
    ): Pair<Int, Int> = when (role) {
        ButtonRole.PRIMARY -> accent to accessibleOnAccent(accent)
        ButtonRole.SECONDARY -> if (selected) {
            ThemePack.cardFill(tokens) to UiDimens.ALPHA_PRIMARY
        } else {
            tokens.chipIdle to UiDimens.ALPHA_PRIMARY
        }
        ButtonRole.QUIET -> if (selected) {
            ThemePack.cardFill(tokens) to UiDimens.ALPHA_PRIMARY
        } else {
            ThemePack.cardFill(tokens) to UiDimens.ALPHA_SECONDARY
        }
    }
}

/**
 * First-run / empty-library guided modal controller, plus one-time companion
 * chrome discoverability (Resume + status pill). Hosted as a full-screen
 * overlay on the primary deck when [SetupNeeds.shouldShow] is true.
 *
 * Supports full gamepad navigation (`NAV_UP`/`NAV_DOWN`/`PAGE_PREV`/`PAGE_NEXT`/
 * `CONFIRM`/`BACK`), auto-scrolling on short (~540dp) panels, and WCAG-legible
 * button contrast across all built-in and custom theme packs.
 */
class SetupCard(
    private val activity: AppCompatActivity,
    private val accent: Int,
    private val snap: SetupNeeds.Snapshot,
    private val onAddRomFolder: () -> Unit,
    private val onSgdbKey: () -> Unit,
    private val onGetEmulator: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onEnableCompanionChrome: () -> Unit,
    private val onDismiss: () -> Unit,
) {
    private data class ActionSpec(
        val label: String,
        val role: SetupCardNav.ButtonRole,
        val isActionCue: Boolean = true,
        val onClick: () -> Unit,
    )

    private data class BoundButton(
        val view: TextView,
        val bgDrawable: GradientDrawable,
        val spec: ActionSpec,
    )

    private var selection = 0
    private var paintedSelection = -1
    private val specs = mutableListOf<ActionSpec>()
    private val boundButtons = mutableListOf<BoundButton>()
    private var scrollHost: OverlayPrimitives.MaxHeightScrollView? = null
    private var themeTokens: ThemeTokens = ThemePack.GHOST

    val view: View by lazy { buildView() }

    private fun playSfx(cue: SoundCue) {
        (activity.applicationContext as? GhostGalleonApp)?.playSfx(cue)
    }

    fun handleAction(action: Action): Boolean {
        // Ensure views/specs exist before routing gamepad input.
        view
        val prev = selection
        val result = SetupCardNav.step(selection, specs.size, action)
        selection = result.selection
        when (result.outcome) {
            SetupCardNav.Outcome.MOVED -> {
                if (selection != prev) playSfx(SoundCue.NAV_TICK)
                paintButtons(scrollToFocused = true, animateFocus = true)
            }
            SetupCardNav.Outcome.CONFIRM -> {
                specs.getOrNull(selection)?.let { spec ->
                    if (spec.isActionCue) playSfx(SoundCue.CONFIRM)
                    spec.onClick()
                }
            }
            SetupCardNav.Outcome.DISMISS -> onDismiss()
            SetupCardNav.Outcome.NONE -> Unit
        }
        return true
    }

    private fun buildView(): View {
        val context: Context = activity
        themeTokens = resolveTokens(context)
        val chromeOnly = SetupNeeds.isChromeDiscoverOnly(snap)
        val checklistItems = SetupNeeds.checklist(snap)
        val progress = SetupCardNav.progress(checklistItems)
        val sunkenSurface = TileBackgrounds.surfaceSunken(context)
        val doneAccentText = ThemePack.accentTextColor(accent, sunkenSurface)
        val dividerColor = TileBackgrounds.hairlineDivider(context)

        val overlay = FrameLayout(context).apply {
            setBackgroundColor(OverlayPrimitives.MODAL_SCRIM_COLOR)
            isClickable = true
            isFocusable = true
        }
        val titleText = context.getString(
            if (chromeOnly) R.string.setup_chrome_title else R.string.setup_welcome,
        )
        OverlayPrimitives.bindModalPaneTitle(overlay, titleText)

        val cardWidth = OverlayPrimitives.responsiveCardWidth(
            context = context,
            maxDp = 440,
            widthFrac = 0.90f,
            minMarginDp = UiDimens.SPACE_LG,
        )
        val card = OverlayPrimitives.modalCardContainer(
            context = context,
            padHorizontalDp = 20,
            padVerticalDp = 14,
        )

        // Compact brand header (radial-glow ship emblem + welcome/chrome title + tnum progress pill).
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emblemHalo = FrameLayout(context).apply {
            background = TileBackgrounds.accentGlow(
                context = context,
                accent = accent,
                cornerRadiusDp = 20,
                radial = true,
                peakAlpha = 0x48,
            )
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_brand_ship)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(context.dp(30), context.dp(30), Gravity.CENTER),
            )
        }
        headerRow.addView(
            emblemHalo,
            LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply {
                marginEnd = context.dp(10)
            },
        )
        headerRow.addView(
            TextView(context).apply {
                text = titleText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19.5f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                OverlayPrimitives.markHeading(this)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        if (progress.total > 0) {
            val progressText = context.getString(
                R.string.format_progress,
                progress.done,
                progress.total,
            )
            headerRow.addView(
                TextView(context).apply {
                    text = progressText
                    contentDescription = context.getString(
                        R.string.format_percent,
                        progress.percent,
                    )
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(
                        if (progress.allDone) {
                            SetupCardNav.accessibleOnAccent(accent)
                        } else {
                            doneAccentText
                        },
                    )
                    setTypeface(typeface, Typeface.BOLD)
                    useTabularNumerals()
                    maxLines = 1
                    background = if (progress.allDone) {
                        TileBackgrounds.accentPill(context, accent, cornerRadiusDp = 10)
                    } else {
                        TileBackgrounds.sunkenField(context, cornerRadiusDp = 10)
                    }
                    setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = context.dp(UiDimens.SPACE_SM) },
            )
        }
        card.addView(
            headerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        card.addView(
            View(context).apply {
                setBackgroundColor(dividerColor)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.strokePx(1),
            ).apply {
                topMargin = context.dp(10)
                bottomMargin = context.dp(10)
            },
        )

        // Scrollable body (intro + vector checklist with progress bar + action buttons)
        // so even short ~540dp Sugar panels never clip off-screen.
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val pad = context.dp(UiDimens.SPACE_XS)
            setPadding(pad, 0, pad, pad)
        }
        body.addView(
            TextView(context).apply {
                setText(if (chromeOnly) R.string.setup_chrome_intro else R.string.setup_intro)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(UiDimens.ALPHA_MUTED)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, context.dp(UiDimens.SPACE_MD))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        // Structured checklist inside a recessed sunken container with live progress bar.
        val checklistBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 14)
            setPadding(
                context.dp(14),
                context.dp(12),
                context.dp(14),
                context.dp(10),
            )
        }
        if (progress.total > 0) {
            val (filledWeight, emptyWeight) = SetupCardNav.progressTrackWeights(progress.fraction)
            val trackRadius = context.dpF(4)
            val track = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    setColor(ThemePack.cardFill(themeTokens))
                    cornerRadius = trackRadius
                    setStroke(context.strokePx(1), dividerColor)
                }
                outlineProvider = ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            if (filledWeight > 0f) {
                track.addView(
                    View(context).apply {
                        background = GradientDrawable().apply {
                            setColor(accent)
                            cornerRadius = trackRadius
                        }
                    },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, filledWeight),
                )
            }
            if (emptyWeight > 0f) {
                track.addView(
                    View(context),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, emptyWeight),
                )
            }
            checklistBox.addView(
                track,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(6),
                ).apply { bottomMargin = context.dp(10) },
            )
        }
        checklistItems.forEachIndexed { index, (label, done) ->
            if (index > 0) {
                checklistBox.addView(
                    View(context).apply {
                        setBackgroundColor(dividerColor)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        context.strokePx(1),
                    ).apply {
                        topMargin = context.dp(2)
                        bottomMargin = context.dp(2)
                    },
                )
            }
            val tone = SetupCardNav.checkRowTone(index, done, chromeOnly)
            val labelText = context.resolveText(label)
            val rowTextColor = SetupCardNav.checkRowTextColor(tone, accent, themeTokens)
            val iconTint = when (tone) {
                SetupCardNav.CheckRowTone.DONE -> doneAccentText
                SetupCardNav.CheckRowTone.REQUIRED_PENDING -> UiDimens.ALPHA_SECONDARY
                SetupCardNav.CheckRowTone.OPTIONAL_PENDING -> UiDimens.ALPHA_SUBTLE
            }
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(5), 0, context.dp(5))
                contentDescription = if (done) {
                    context.getString(R.string.format_selected_check, labelText)
                } else {
                    labelText
                }
            }
            row.addView(
                ImageView(context).apply {
                    setImageResource(
                        if (done) R.drawable.ic_check_circle else R.drawable.ic_radio_unchecked,
                    )
                    imageTintList = ColorStateList.valueOf(iconTint)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
                    marginEnd = context.dp(10)
                },
            )
            row.addView(
                TextView(context).apply {
                    text = labelText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                    setTextColor(rowTextColor)
                    setTypeface(
                        typeface,
                        if (tone == SetupCardNav.CheckRowTone.REQUIRED_PENDING) {
                            Typeface.BOLD
                        } else {
                            Typeface.NORMAL
                        },
                    )
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            checklistBox.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        body.addView(
            checklistBox,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = context.dp(14) },
        )

        // Build ordered action specs (primary CTA first, secondary actions, quiet Skip last).
        specs.clear()
        boundButtons.clear()
        paintedSelection = -1
        if (chromeOnly) {
            specs.add(
                ActionSpec(
                    label = context.getString(R.string.setup_enable_companion_chrome),
                    role = SetupCardNav.buttonRoleFor(isPrimary = true, isSkip = false),
                    isActionCue = false,
                    onClick = onEnableCompanionChrome,
                ),
            )
            specs.add(
                ActionSpec(
                    label = context.getString(R.string.action_skip_for_now),
                    role = SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = true),
                    isActionCue = false,
                    onClick = onDismiss,
                ),
            )
        } else {
            specs.add(
                ActionSpec(
                    label = context.getString(R.string.setup_add_rom_folder),
                    role = SetupCardNav.buttonRoleFor(isPrimary = true, isSkip = false),
                    onClick = onAddRomFolder,
                ),
            )
            if (snap.installedPlayerCount == 0 || snap.missingPrimaryCount > 0) {
                specs.add(
                    ActionSpec(
                        label = context.getString(R.string.setup_get_emulator),
                        role = SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = false),
                        onClick = onGetEmulator,
                    ),
                )
            }
            specs.add(
                ActionSpec(
                    label = context.getString(
                        if (snap.hasSgdbKey) R.string.setup_sgdb_set
                        else R.string.setup_sgdb_optional,
                    ),
                    role = SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = false),
                    onClick = onSgdbKey,
                ),
            )
            specs.add(
                ActionSpec(
                    label = context.getString(R.string.setup_enable_companion_chrome),
                    role = SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = false),
                    isActionCue = false,
                    onClick = onEnableCompanionChrome,
                ),
            )
            specs.add(
                ActionSpec(
                    label = context.getString(R.string.setup_open_settings),
                    role = SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = false),
                    onClick = onOpenSettings,
                ),
            )
            specs.add(
                ActionSpec(
                    label = context.getString(R.string.action_skip_for_now),
                    role = SetupCardNav.buttonRoleFor(isPrimary = false, isSkip = true),
                    isActionCue = false,
                    onClick = onDismiss,
                ),
            )
        }

        val btnRadiusDp = themeTokens.cardRadiusDp
        val btnRadiusPx = context.dpF(btnRadiusDp)
        specs.forEachIndexed { index, spec ->
            val bgDrawable = GradientDrawable().apply {
                cornerRadius = btnRadiusPx
            }
            val btn = TextView(context).apply {
                text = spec.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                minHeight = context.dp(46)
                setPadding(
                    context.dp(UiDimens.SPACE_LG),
                    context.dp(11),
                    context.dp(UiDimens.SPACE_LG),
                    context.dp(11),
                )
                background = OverlayPrimitives.roundedRipple(
                    context = context,
                    content = bgDrawable,
                    cornerRadiusDp = btnRadiusDp,
                    rippleColor = SetupCardNav.BUTTON_RIPPLE_COLOR,
                )
                setOnClickListener {
                    selection = index
                    paintButtons(scrollToFocused = false, animateFocus = false)
                    if (spec.isActionCue) playSfx(SoundCue.CONFIRM)
                    spec.onClick()
                }
            }
            boundButtons.add(BoundButton(btn, bgDrawable, spec))
            body.addView(
                btn,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) topMargin = context.dp(UiDimens.SPACE_SM)
                },
            )
        }

        val scroll = OverlayPrimitives.MaxHeightScrollView(
            context = context,
            maxHeightPx = OverlayPrimitives.maxModalHeight(
                context = context,
                heightFrac = 0.58f,
                minDp = 160,
            ),
        ).apply {
            addView(
                body,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        scrollHost = scroll
        val cueColor = OverlayPrimitives.scrollCueTint(accent, ThemePack.cardFill(themeTokens))
        val topCue = OverlayPrimitives.scrollCueBar(context, cueColor)
        val bottomCue = OverlayPrimitives.scrollCueBar(context, cueColor)
        OverlayPrimitives.bindScrollCues(scroll, topCue, bottomCue)
        val scrollViewport = FrameLayout(context).apply {
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                topCue,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(2),
                    Gravity.TOP,
                ),
            )
            addView(
                bottomCue,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(2),
                    Gravity.BOTTOM,
                ),
            )
        }
        card.addView(
            scrollViewport,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        card.addView(
            OverlayPrimitives.modalHintFooter(context, R.string.deck_hint_folder_close),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        paintButtons(scrollToFocused = false, animateFocus = false)
        overlay.addView(
            card,
            FrameLayout.LayoutParams(
                cardWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        OverlayPrimitives.applyImeSafeInsets(overlay)
        OverlayPrimitives.bindModalEnter(overlay, card)
        return overlay
    }

    private fun paintButtonAt(bound: BoundButton, selected: Boolean, animateFocus: Boolean) {
        val role = bound.spec.role
        val (_, textColor) = SetupCardNav.buttonColors(role, selected, accent, themeTokens)
        val bg = bound.bgDrawable
        val focusColor = SetupCardNav.buttonFocusStroke(role, accent, themeTokens)
        when (role) {
            SetupCardNav.ButtonRole.PRIMARY -> {
                bg.setColor(accent)
                if (selected) {
                    bg.setStroke(activity.strokePx(3), focusColor)
                } else {
                    bg.setStroke(0, Color.TRANSPARENT)
                }
            }
            SetupCardNav.ButtonRole.SECONDARY -> {
                if (selected) {
                    bg.setColor(ThemePack.cardFill(themeTokens))
                    bg.setStroke(activity.strokePx(3), focusColor)
                } else {
                    bg.setColor(themeTokens.chipIdle)
                    bg.setStroke(activity.strokePx(1), ThemePack.hairlineDivider(themeTokens))
                }
            }
            SetupCardNav.ButtonRole.QUIET -> {
                if (selected) {
                    bg.setColor(ThemePack.cardFill(themeTokens))
                    bg.setStroke(activity.strokePx(2), focusColor)
                } else {
                    bg.setColor(Color.TRANSPARENT)
                    bg.setStroke(0, Color.TRANSPARENT)
                }
            }
        }
        val tv = bound.view
        tv.setTextColor(textColor)
        tv.setTypeface(
            null,
            if (selected || role == SetupCardNav.ButtonRole.PRIMARY) {
                Typeface.BOLD
            } else {
                Typeface.NORMAL
            },
        )
        tv.isSelected = selected
        OverlayPrimitives.applyFocusScale(
            view = tv,
            selected = selected,
            focusedScale = 1.02f,
            animate = animateFocus,
            durationMs = SetupCardNav.FOCUS_ANIM_MS,
        )
    }

    private fun paintButtons(scrollToFocused: Boolean, animateFocus: Boolean = true) {
        if (boundButtons.isEmpty()) return
        val sel = GridWrapNav.clampIndex(selection, boundButtons.size)
        selection = sel
        val dirty = SetupCardNav.dirtyButtonIndices(paintedSelection, sel, boundButtons.size)
        for (i in 0 until dirty.size) {
            val index = dirty[i]
            paintButtonAt(boundButtons[index], selected = index == sel, animateFocus = animateFocus)
        }
        paintedSelection = sel
        if (!scrollToFocused) return
        val focused = boundButtons.getOrNull(sel)?.view ?: return
        scrollHost?.smoothScrollToCenter(focused)
    }

    private fun resolveTokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) ThemePack.resolve(app.settings) else ThemePack.GHOST
    }

    companion object {
        fun snapshot(app: GhostGalleonApp, installed: (String) -> Boolean): SetupNeeds.Snapshot {
            val players = Platforms.ALL.flatMap { it.players }
                .map { PlayerResolver.packageName(it) }
                .distinct()
                .count { installed(it) }
            val missing = com.visorcraft.ghostgalleon.rom.PlayerInstall.missingPrimaries(
                installed = installed,
            ).size
            val chrome = app.settings.browseChrome
            return SetupNeeds.Snapshot(
                setupDismissed = app.settings.setupDismissed,
                romTreeCount = app.settings.romTreeUris.size,
                romEntryCount = app.romEntries.size,
                installedPlayerCount = players,
                missingPrimaryCount = missing,
                hasSgdbKey = !app.settings.sgdbApiKey.isNullOrBlank(),
                resumeChip = chrome.resumeChip,
                statusPill = chrome.deckStatusPill,
                chromeDiscoverDismissed = app.settings.chromeDiscoverDismissed,
            )
        }
    }
}
