package com.visorcraft.ghostgalleon.ui.deck

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.art.ArtTile
import com.visorcraft.ghostgalleon.library.AppLibrary
import com.visorcraft.ghostgalleon.rom.PlatformTile
import com.visorcraft.ghostgalleon.rom.RomEntry
import com.visorcraft.ghostgalleon.settings.DockSlots
import com.visorcraft.ghostgalleon.settings.Settings
import com.visorcraft.ghostgalleon.settings.SlotKey
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

// The dock bar pinned at the bottom edge of BOTH launcher decks: a
// wrap-content card bar (just wide enough for its visible slots, centered)
// holding the auto-growing dock row. The bar renders
// DockSlots.visibleCount slots — one "+" placeholder past the filled
// count, at least 4, at most 9. Filled slots show the app icon / ROM
// platform tile (a missing ROM dims to 40% alpha, same as the grid);
// empty slots render as "+" tiles, visually consistent with the grid's
// blank slots. Taps and long-presses are delegated to the owning deck
// (launch / picker / slot menu); the bar itself only renders and repaints
// focus.
//
// The bar also hosts the grid's page dots (grid mode only) and the
// transient "Moving - A drop · B cancel" hint, which replaces the slots
// and dots while a grid or dock tile is lifted.
class DockBar(
    private val activity: AppCompatActivity,
    private val settings: Settings,
    private val library: AppLibrary,
    private val iconLoader: AppIconLoader,
    private val roms: List<RomEntry>,
    // The slots to render; the owning deck substitutes its working copy
    // while a dock move is in flight.
    private val slots: () -> List<String?>,
    private val onTap: (Int) -> Unit,
    private val onLongPress: (Int) -> Unit,
) {
    private val slotFrames = mutableListOf<FrameLayout>()
    private var moveHintBanner: LinearLayout? = null
    private var moveDotView: View? = null
    private var moveDotAnimator: ObjectAnimator? = null
    private var contentView: LinearLayout? = null
    private var pulseAnimator: AnimatorSet? = null
    private var slotSize: Int = 0
    private var slotRadiusDp: Int = 14
    private var innerContentRadiusDp: Int = 10
    private var paintedFocus: Int? = null
    private var paintedMoving: Int? = null
    private var focusPainted = false

    // Dock tiles resolve like grid slots: apps through the FULL cache (a
    // hidden app stays docked and launchable), ROMs through the library
    // snapshot. An app key that no longer resolves (uninstalled) renders
    // as a blank "+" slot — tapping it opens the picker and overwrites.
    private val appByPkg by lazy {
        library.all(settings).associateBy { it.packageName }
    }
    private val romByKey by lazy { roms.associateBy { SlotKey.rom(it.id) } }

    fun keyAt(index: Int): String? = slots().getOrNull(index)

    // Blank = renders as a "+" placeholder: no key, or an app key whose
    // package is gone. Missing ROMs stay filled (they keep launch-toast
    // and menu behavior, same as the grid's "Missing" tiles).
    fun isBlank(index: Int): Boolean {
        val key = keyAt(index) ?: return true
        if (SlotKey.isRom(key)) return false
        return !appByPkg.containsKey(key)
    }

    // [pageDots] is kept optional for compatibility (GridDeck now renders page
    // indicators in a centered strip above the dock so the dock never shifts
    // sideways between Grid and Game modes).
    fun build(
        context: Context,
        pageDots: LinearLayout? = null,
        reserveChromeInset: Boolean = false,
    ): View {
        // Slot frames sized from window metrics (DockBarMetrics + LayoutMetrics)
        // so the floating bar fits cleanly and never overlaps Swap/Settings.
        val metrics = context.resources.displayMetrics
        val layout = com.visorcraft.ghostgalleon.display.LayoutMetricsResolver.fromWindow(
            windowWidthPx = metrics.widthPixels,
            windowHeightPx = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            topologyMode = (context.applicationContext as? com.visorcraft.ghostgalleon.GhostGalleonApp)
                ?.displayConfig?.mode
                ?: com.visorcraft.ghostgalleon.display.SurfaceMode.SINGLE,
            isCompanionRole = false,
        )
        val visibleSlots = DockSlots.visibleCount(slots())
        val spec = DockBarMetrics.compute(
            windowWidthPx = metrics.widthPixels,
            density = metrics.density,
            suggestedSlotDp = layout.suggestedDockSlotDp,
            visibleSlots = visibleSlots,
            hasChromeIcons = reserveChromeInset,
        )
        slotSize = spec.slotSizePx
        val padVDp = 8
        val padV = context.dp(padVDp)
        val density = metrics.density.coerceAtLeast(0.1f)
        val slotSizeDp = (slotSize / density).toInt()
        val iconSizeDp = ((slotSize * 40 / 48) / density).toInt()
        val themeTokens = ThemePack.resolve(settings)
        slotRadiusDp = DeckCornerMetrics.dockSlotRadiusDp(
            tokens = themeTokens,
            barPadVDp = padVDp,
            slotSizeDp = slotSizeDp,
        )
        innerContentRadiusDp = DeckCornerMetrics.dockInnerContentRadiusDp(
            tokens = themeTokens,
            barPadVDp = padVDp,
            slotSizeDp = slotSizeDp,
            iconSizeDp = iconSizeDp,
        )

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = TileBackgrounds.pill(context)
            setPadding(spec.barPadHPx, padV, spec.barPadHPx, padV)
            minimumHeight = slotSize + padV * 2
            clipChildren = false
            clipToPadding = false
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    cancelAllAnimations()
                }
            })
        }
        // Transient move-mode banner (child 0, hidden by default); shown in
        // place of the slots while a grid tile is lifted without collapsing
        // the bar's height.
        val surfaceFill = TileBackgrounds.surfaceColor(context)
        val moveAccent = MoveChoreography.liftedBorderColor(settings.accentColor, surfaceFill)
        val moveDot = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(moveAccent)
            }
        }
        moveDotView = moveDot
        val moveHint = TextView(context).apply {
            setText(R.string.deck_hint_move)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(moveAccent)
            gravity = Gravity.CENTER
        }
        val moveBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(context.dp(12), 0, context.dp(12), 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
            addView(
                moveDot,
                LinearLayout.LayoutParams(context.dp(8), context.dp(8)).apply {
                    marginEnd = context.dp(8)
                },
            )
            addView(
                moveHint,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        moveHintBanner = moveBanner
        bar.addView(moveBanner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            slotSize,
        ))
        slotFrames.clear()
        repeat(visibleSlots) { index ->
            val frame = FrameLayout(context).apply {
                clipChildren = false
                clipToPadding = false
            }
            frame.setOnClickListener { onTap(index) }
            frame.setOnLongClickListener {
                onLongPress(index)
                true
            }
            populate(frame, index)
            slotFrames.add(frame)
            bar.addView(frame, LinearLayout.LayoutParams(slotSize, slotSize).apply {
                marginStart = spec.slotMarginHPx
                marginEnd = spec.slotMarginHPx
            })
        }
        if (pageDots != null) {
            pageDots.setPadding(context.dp(10), 0, 0, 0)
            bar.addView(pageDots, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        contentView = bar
        // Floating wrap-content pill centered horizontally with bottom bezel
        // breathing room and side insets clear of Swap/Settings chrome icons.
        return bar.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                marginStart = spec.sideInsetPx
                marginEnd = spec.sideInsetPx
                bottomMargin = spec.bottomMarginPx
            }
        }
    }

    // One slot's content: app icon / ROM cover art (async via ArtTile over
    // platform placeholder) / dimmed missing tile / recessed "+" placeholder.
    private fun populate(frame: FrameLayout, index: Int) {
        val context = frame.context
        val iconSize = slotSize * 40 / 48

        frame.removeAllViews()
        val key = keyAt(index)
        val romEntry = key?.takeIf(SlotKey::isRom)?.let { romByKey[it] }
        val appEntry = key?.takeUnless(SlotKey::isRom)?.let { appByPkg[it] }
        val content: View = when {
            romEntry != null -> ArtTile.view(
                context = context,
                cache = (activity.application as GhostGalleonApp).artCache,
                rom = romEntry,
                targetPx = iconSize,
                cornerRadiusDp = innerContentRadiusDp,
                artOverrides = settings.artOverrides,
            )
            appEntry != null -> ImageView(context).apply {
                CustomIcon.bind(
                    this, iconLoader,
                    (activity.application as GhostGalleonApp).artCache,
                    settings, key, iconSize)
            }
            SlotKey.isRom(key) -> PlatformTile.view(
                context, SlotKey.platformIdOf(key) ?: "rom",
                cornerRadiusDp = innerContentRadiusDp).apply { alpha = 0.4f }
            else -> TextView(context).apply {
                setText(R.string.glyph_add)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                setTextColor(UiDimens.ALPHA_SUBTLE)
                gravity = Gravity.CENTER
                background = TileBackgrounds.blankSlot(
                    context,
                    cornerRadiusDp = innerContentRadiusDp,
                )
            }
        }
        frame.addView(content, FrameLayout.LayoutParams(
            iconSize, iconSize, Gravity.CENTER))
    }

    // Repopulates every slot's contents (a dock move swapped keys in the
    // working copy) without rebuilding the bar.
    fun rebind() {
        slotFrames.forEachIndexed { index, frame -> populate(frame, index) }
        focusPainted = false
    }

    // Moves the accent ring, subtle scale lift, and elevation to [focused]
    // (null = no dock focus, ring returns to deck content) and applies
    // lifted / drop-target choreography while a dock move is in flight.
    fun updateFocus(focused: Int?, moving: Int? = null) {
        if (focusPainted && paintedFocus == focused && paintedMoving == moving) {
            return
        }
        cancelPulseAnimator()
        paintedFocus = focused
        paintedMoving = moving
        focusPainted = true
        slotFrames.forEachIndexed { index, frame ->
            val ctx = frame.context
            val role = MoveChoreography.resolveRole(
                position = index,
                selectedIndex = focused,
                movingIndex = moving,
                surfaceFocused = focused != null,
            )
            val spec = MoveChoreography.dockVisualSpec(role, isBlank(index))
            val surfaceFill = TileBackgrounds.surfaceColor(ctx)
            frame.animate().cancel()
            frame.background = when (role) {
                MoveChoreography.SlotRole.LIFTED -> GradientDrawable().apply {
                    setColor(MoveChoreography.liftedWashFill(settings.accentColor, surfaceFill))
                    cornerRadius = ctx.dpF(slotRadiusDp)
                    setStroke(
                        ctx.strokePx(3),
                        MoveChoreography.liftedBorderColor(settings.accentColor, surfaceFill),
                    )
                }
                MoveChoreography.SlotRole.DROP_TARGET -> GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = ctx.dpF(slotRadiusDp)
                    setStroke(
                        ctx.strokePx(2),
                        MoveChoreography.dropTargetStrokeColor(settings.accentColor, surfaceFill),
                        ctx.dpF(5),
                        ctx.dpF(3),
                    )
                }
                MoveChoreography.SlotRole.FOCUSED -> TileBackgrounds.focusStroke(
                    context = ctx,
                    accent = settings.accentColor,
                    fill = surfaceFill,
                    cornerRadiusDp = slotRadiusDp,
                    strokeDp = 3,
                )
                MoveChoreography.SlotRole.NORMAL -> null
            }
            frame.translationZ = if (spec.elevationDp > 0f) frame.dpF(spec.elevationDp) else 0f
            if (role == MoveChoreography.SlotRole.LIFTED) {
                frame.scaleX = spec.scale
                frame.scaleY = spec.scale
                frame.translationY = 0f
                frame.alpha = spec.alpha
                startPulse(frame)
            } else if (frame.isLaidOut) {
                frame.animate()
                    .scaleX(spec.scale)
                    .scaleY(spec.scale)
                    .translationY(0f)
                    .alpha(spec.alpha)
                    .setDuration(
                        if (role == MoveChoreography.SlotRole.DROP_TARGET) {
                            MoveChoreography.SETTLE_DURATION_MS
                        } else {
                            FOCUS_ANIM_MS
                        },
                    )
                    .setInterpolator(FOCUS_INTERPOLATOR)
                    .start()
            } else {
                frame.scaleX = spec.scale
                frame.scaleY = spec.scale
                frame.translationY = 0f
                frame.alpha = spec.alpha
            }
        }
    }

    private companion object {
        const val FOCUS_ANIM_MS = 90L
        val FOCUS_INTERPOLATOR = DecelerateInterpolator()
        val PULSE_INTERPOLATOR = AccelerateDecelerateInterpolator()
    }

    private fun cancelPulseAnimator() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    private fun cancelMoveDotAnimator() {
        moveDotAnimator?.cancel()
        moveDotAnimator = null
        moveDotView?.alpha = 1f
    }

    private fun cancelAllAnimations() {
        cancelPulseAnimator()
        cancelMoveDotAnimator()
        slotFrames.forEach { frame ->
            frame.animate().cancel()
            frame.translationY = 0f
            frame.alpha = 1f
        }
    }

    private fun startPulse(frame: FrameLayout) {
        cancelPulseAnimator()
        val floatPx = frame.dpF(MoveChoreography.DOCK_FLOAT_DP)
        val scaleX = ObjectAnimator.ofFloat(
            frame, View.SCALE_X,
            MoveChoreography.DOCK_PULSE_SCALE_MIN, MoveChoreography.DOCK_PULSE_SCALE_MAX,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val scaleY = ObjectAnimator.ofFloat(
            frame, View.SCALE_Y,
            MoveChoreography.DOCK_PULSE_SCALE_MIN, MoveChoreography.DOCK_PULSE_SCALE_MAX,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val hoverY = ObjectAnimator.ofFloat(frame, View.TRANSLATION_Y, 0f, floatPx).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        val alpha = ObjectAnimator.ofFloat(
            frame, View.ALPHA, 1f, MoveChoreography.PULSE_ALPHA_MIN,
        ).apply {
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, hoverY, alpha)
            duration = MoveChoreography.PULSE_DURATION_MS
            interpolator = PULSE_INTERPOLATOR
            start()
        }
    }

    // Swaps the move hint banner in for the slots/dots while a tile is lifted,
    // and back when the move ends.
    fun showMoveHint(show: Boolean) {
        val bar = contentView ?: return
        val banner = moveHintBanner ?: return
        banner.visibility = if (show) View.VISIBLE else View.GONE
        for (i in 0 until bar.childCount) {
            val child = bar.getChildAt(i)
            if (child !== banner) child.visibility = if (show) View.GONE else View.VISIBLE
        }
        if (show) {
            cancelMoveDotAnimator()
            moveDotView?.let { dot ->
                moveDotAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f).apply {
                    duration = MoveChoreography.PULSE_DURATION_MS
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    start()
                }
            }
        } else {
            cancelMoveDotAnimator()
        }
    }
}
