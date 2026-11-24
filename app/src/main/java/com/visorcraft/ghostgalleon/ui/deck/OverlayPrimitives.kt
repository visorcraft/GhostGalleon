package com.visorcraft.ghostgalleon.ui.deck

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

/**
 * Shared modal/overlay framing primitives used across deck and settings
 * overlays (SlotMenu, QuickPanel, FolderPanel, AppPicker, SessionSwitcherView,
 * SetupCard, AboutPage). Keeps scrim opacity, responsive widths, flicker-free
 * max-height scrolling, cancelable entrance choreography, and headers/footers
 * consistent across dual short (~540dp) and 16:9 single-screen handheld displays.
 */
object OverlayPrimitives {

    /** Standard 80% black modal scrim backdrop. */
    const val MODAL_SCRIM_COLOR: Int = UiDimens.SCRIM_MODAL

    /** Consistent floating modal elevation in dp. */
    const val MODAL_ELEVATION_DP: Int = 16

    /** Vertical scroll-edge fading length in dp for scrollable modals. */
    const val FADING_EDGE_DP: Int = 20

    /** Subtle scale-in starting scale for modal cards (0.96 -> 1.0). */
    const val ENTER_START_SCALE: Float = 0.96f

    /** Crisp modal entrance duration in milliseconds. */
    const val ENTER_DURATION_MS: Long = 120L

    /** Standard duration for modal row/tile focus lift animations. */
    const val FOCUS_ANIM_MS: Long = 90L

    /** Default subtle focus lift scale for modal rows. */
    const val DEFAULT_FOCUS_SCALE: Float = 1.015f

    /** Standard subtle white touch ripple color for dark modal surfaces. */
    const val DEFAULT_RIPPLE_COLOR: Int = 0x26FFFFFF

    private val ENTER_INTERPOLATOR = DecelerateInterpolator()

    /** Opacity pair `(topCueAlpha, bottomCueAlpha)` signaling vertical scroll overflow. */
    fun scrollCueAlphas(canScrollUp: Boolean, canScrollDown: Boolean): Pair<Float, Float> =
        (if (canScrollUp) 1f else 0f) to (if (canScrollDown) 1f else 0f)

    /** Accent-tinted translucent scroll-overflow bar color with WCAG-lifted RGB hue. */
    fun scrollCueTint(accent: Int, surface: Int): Int {
        val lifted = ThemePack.accentTextColor(accent, surface)
        return (0x66 shl 24) or (lifted and 0x00FFFFFF)
    }

    /** Create a subtle decorative scroll-overflow indicator hairline view. */
    fun scrollCueBar(context: Context, color: Int): View = View(context).apply {
        setBackgroundColor(color)
        alpha = 0f
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Wire [topCue] and [bottomCue] visibility to [scroll]'s vertical overflow state. */
    fun bindScrollCues(scroll: View, topCue: View, bottomCue: View) {
        fun refresh() {
            val (topAlpha, bottomAlpha) = scrollCueAlphas(
                canScrollUp = scroll.canScrollVertically(-1),
                canScrollDown = scroll.canScrollVertically(1),
            )
            topCue.alpha = topAlpha
            bottomCue.alpha = bottomAlpha
        }
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> refresh() }
        scroll.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }
    }

    /** Wrap [content] in a crisp rounded [RippleDrawable] clipped to [cornerRadiusDp]. */
    fun roundedRipple(
        context: Context,
        content: Drawable?,
        cornerRadiusDp: Int,
        rippleColor: Int = DEFAULT_RIPPLE_COLOR,
    ): Drawable {
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = context.dpF(cornerRadiusDp)
        }
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
    }

    /**
     * Apply a cancelable focus scale lift (`1f` vs [focusedScale]) respecting
     * OS animator settings so every modal shares consistent focus choreography.
     */
    fun applyFocusScale(
        view: View,
        selected: Boolean,
        focusedScale: Float = DEFAULT_FOCUS_SCALE,
        animate: Boolean = true,
        durationMs: Long = FOCUS_ANIM_MS,
    ) {
        val target = if (selected) focusedScale else 1f
        view.animate().cancel()
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            view.animate()
                .scaleX(target)
                .scaleY(target)
                .setDuration(durationMs)
                .setInterpolator(ENTER_INTERPOLATOR)
                .start()
        } else {
            view.scaleX = target
            view.scaleY = target
        }
    }

    /** Pure pixel length for vertical scroll fading edges. Host-tested. */
    fun fadingEdgeLengthPx(density: Float, edgeDp: Int = FADING_EDGE_DP): Int {
        val safeDensity = density.coerceAtLeast(0.1f)
        return (edgeDp.coerceAtLeast(0) * safeDensity).toInt()
    }

    /**
     * Pure bottom inset padding keeping centered modals clear of an active IME
     * or gesture/navigation bar without shifting modals in immersive fullscreen
     * (`0` when both insets are `0`). Host-tested.
     */
    fun safeBottomInsetPx(
        imeBottomPx: Int,
        navBarBottomPx: Int,
        extraMarginPx: Int = 0,
    ): Int {
        val base = maxOf(imeBottomPx.coerceAtLeast(0), navBarBottomPx.coerceAtLeast(0))
        return if (base > 0) base + extraMarginPx.coerceAtLeast(0) else 0
    }

    /**
     * Pure IME-safe vertical height for top-anchored tall modal sheets (such
     * as [AppPicker]'s 0.88f drawer): shrinks cleanly above the soft keyboard
     * while respecting [minHeightPx] whenever screen space allows. Host-tested.
     */
    fun imeAwareCardHeightPx(
        screenHeightPx: Int,
        heightFrac: Float,
        bottomInsetPx: Int,
        topMarginPx: Int,
        minHeightPx: Int,
    ): Int {
        val safeH = screenHeightPx.coerceAtLeast(1)
        val desired = (safeH * heightFrac.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
        val available = (safeH - bottomInsetPx.coerceAtLeast(0) - topMarginPx.coerceAtLeast(0) * 2)
            .coerceAtLeast(1)
        val minBound = minOf(minHeightPx.coerceAtLeast(1), available)
        return minOf(desired, available).coerceAtLeast(minBound)
    }

    /**
     * Keep [backdrop]'s children clear of the soft keyboard and bottom
     * navigation insets without triggering redundant layout passes.
     */
    fun applyImeSafeInsets(
        backdrop: View,
        extraBottomDp: Int = UiDimens.SPACE_SM,
    ) {
        val extraPx = backdrop.context.dp(extraBottomDp)
        ViewCompat.setOnApplyWindowInsetsListener(backdrop) { view, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val nextBottom = safeBottomInsetPx(imeBottom, navBottom, extraPx)
            if (view.paddingBottom != nextBottom) {
                view.setPadding(
                    view.paddingLeft,
                    view.paddingTop,
                    view.paddingRight,
                    nextBottom,
                )
            }
            insets
        }
    }

    /**
     * Pure vertical scroll target that centers an item inside a viewport,
     * clamped to `0 .. maxScroll`. Shared across modals so focus scrolling is
     * consistent and never overshoots or produces negative scroll offsets.
     * Host-tested.
     */
    fun scrollCenterTargetY(
        itemTop: Int,
        itemHeight: Int,
        viewportHeight: Int,
        contentHeight: Int = Int.MAX_VALUE,
    ): Int {
        if (viewportHeight <= 0) return 0
        val safeItemH = itemHeight.coerceAtLeast(0)
        val ideal = itemTop - (viewportHeight - safeItemH) / 2
        val maxScroll = if (contentHeight == Int.MAX_VALUE) {
            Int.MAX_VALUE
        } else {
            (contentHeight - viewportHeight).coerceAtLeast(0)
        }
        return ideal.coerceIn(0, maxScroll)
    }

    /**
     * Pure check for whether entrance choreography should animate or snap
     * directly to resting state (e.g. when OS animators are disabled or an
     * overlay is re-attaching in place after an item mutation). Host-tested.
     */
    fun shouldAnimateEntrance(
        animatorsEnabled: Boolean,
        skipAnimation: Boolean = false,
    ): Boolean = animatorsEnabled && !skipAnimation

    /**
     * Pure interpolated card scale for normalized entrance progress `[0, 1]`.
     * Host-tested.
     */
    fun entranceScale(
        progress: Float,
        startScale: Float = ENTER_START_SCALE,
    ): Float {
        val p = progress.coerceIn(0f, 1f)
        val clampedStart = startScale.coerceIn(0.5f, 1f)
        return clampedStart + (1f - clampedStart) * p
    }

    /**
     * Pure responsive modal card width in pixels. Caps width to [maxDp] while
     * respecting [widthFrac] of the screen and keeping at least [minMarginDp]
     * breathing room on each horizontal edge. Host-tested.
     */
    fun responsiveCardWidthPx(
        screenWidthPx: Int,
        density: Float,
        maxDp: Int = 440,
        widthFrac: Float = 0.88f,
        minMarginDp: Int = 16,
    ): Int {
        val safeW = screenWidthPx.coerceAtLeast(1)
        val safeDensity = density.coerceAtLeast(0.1f)
        val maxPx = (maxDp * safeDensity).toInt().coerceAtLeast(1)
        val fracPx = (safeW * widthFrac.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
        val marginedPx = (safeW - (minMarginDp * 2 * safeDensity).toInt()).coerceAtLeast(1)
        return minOf(maxPx, fracPx, marginedPx).coerceAtLeast(1)
    }

    /** Context wrapper around [responsiveCardWidthPx]. */
    fun responsiveCardWidth(
        context: Context,
        maxDp: Int = 440,
        widthFrac: Float = 0.88f,
        minMarginDp: Int = 16,
    ): Int {
        val dm = context.resources.displayMetrics
        return responsiveCardWidthPx(
            screenWidthPx = dm.widthPixels,
            density = dm.density,
            maxDp = maxDp,
            widthFrac = widthFrac,
            minMarginDp = minMarginDp,
        )
    }

    /**
     * Pure responsive modal max-height in pixels. Never exceeds [screenHeightPx]
     * and stays at least [minDp] when the screen allows. Host-tested.
     */
    fun maxModalHeightPx(
        screenHeightPx: Int,
        density: Float,
        heightFrac: Float = 0.72f,
        minDp: Int = 180,
    ): Int {
        val safeH = screenHeightPx.coerceAtLeast(1)
        val safeDensity = density.coerceAtLeast(0.1f)
        val fracPx = (safeH * heightFrac.coerceIn(0.1f, 1f)).toInt()
        val minPx = (minDp * safeDensity).toInt().coerceAtLeast(1)
        return fracPx.coerceAtLeast(minPx).coerceAtMost(safeH)
    }

    /** Context wrapper around [maxModalHeightPx]. */
    fun maxModalHeight(
        context: Context,
        heightFrac: Float = 0.72f,
        minDp: Int = 180,
    ): Int {
        val dm = context.resources.displayMetrics
        return maxModalHeightPx(
            screenHeightPx = dm.heightPixels,
            density = dm.density,
            heightFrac = heightFrac,
            minDp = minDp,
        )
    }

    /**
     * [ScrollView] that caps its own height directly inside [onMeasure] so
     * short content wraps naturally and tall content scrolls without the
     * 1-frame `scroll.post` height-shrink flicker. Enables subtle vertical
     * scroll-edge fading and overlay scrollbars so scrollable modals signal
     * additional content cleanly on short handheld panels.
     */
    class MaxHeightScrollView(
        context: Context,
        var maxHeightPx: Int,
    ) : ScrollView(context) {

        init {
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(
                fadingEdgeLengthPx(context.resources.displayMetrics.density),
            )
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        /** Smoothly scroll [target] (direct or nested descendant) to viewport center. */
        fun smoothScrollToCenter(target: View) {
            post {
                val content = getChildAt(0) ?: return@post
                var offsetTop = target.top
                var parent = target.parent as? View
                while (parent != null && parent !== content && parent !== this) {
                    offsetTop += parent.top
                    parent = parent.parent as? View
                }
                val targetY = scrollCenterTargetY(
                    itemTop = offsetTop,
                    itemHeight = target.height,
                    viewportHeight = height,
                    contentHeight = content.height,
                )
                smoothScrollTo(0, targetY)
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cappedSpec = if (maxHeightPx > 0) {
                val mode = MeasureSpec.getMode(heightMeasureSpec)
                val size = MeasureSpec.getSize(heightMeasureSpec)
                when (mode) {
                    MeasureSpec.EXACTLY ->
                        MeasureSpec.makeMeasureSpec(minOf(size, maxHeightPx), MeasureSpec.EXACTLY)
                    MeasureSpec.AT_MOST ->
                        MeasureSpec.makeMeasureSpec(minOf(size, maxHeightPx), MeasureSpec.AT_MOST)
                    else ->
                        MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
                }
            } else {
                heightMeasureSpec
            }
            super.onMeasure(widthMeasureSpec, cappedSpec)
        }
    }

    /** Marker [LinearLayout] subtype returned by [modalCardContainer] for entrance choreography. */
    private class ModalCardLayout(context: Context) : LinearLayout(context)

    /**
     * Play a crisp, cancelable fade + scale-in (`0.96 -> 1.0`, `120ms`,
     * [DecelerateInterpolator]) when a modal attaches. Avoids hardware layers
     * to stay safe on dual-display GPUs and respects system animator settings.
     */
    fun animateModalEnter(
        backdrop: View,
        card: View?,
        skipAnimation: Boolean = false,
    ) {
        backdrop.animate().cancel()
        card?.animate()?.cancel()
        if (!shouldAnimateEntrance(ValueAnimator.areAnimatorsEnabled(), skipAnimation)) {
            backdrop.alpha = 1f
            card?.apply {
                alpha = 1f
                scaleX = 1f
                scaleY = 1f
            }
            return
        }
        backdrop.alpha = 0f
        backdrop.animate()
            .alpha(1f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(ENTER_INTERPOLATOR)
            .start()
        card?.apply {
            alpha = 0f
            scaleX = ENTER_START_SCALE
            scaleY = ENTER_START_SCALE
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ENTER_DURATION_MS)
                .setInterpolator(ENTER_INTERPOLATOR)
                .start()
        }
    }

    /**
     * Cancel running entrance/focus animations on [view] and its direct modal
     * card child before detachment so rapid open/close never leaves ghosts.
     */
    fun cancelAnimations(view: View?) {
        view ?: return
        view.animate().cancel()
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                view.getChildAt(i)?.animate()?.cancel()
            }
        }
    }

    /**
     * Attach cancelable entrance choreography to [backdrop] and [card] so
     * overlays run [animateModalEnter] upon window attach and cancel cleanly
     * on detach.
     */
    fun bindModalEnter(
        backdrop: View,
        card: View?,
        skipAnimation: Boolean = false,
    ) {
        var played = false
        val runEnter = {
            if (!played) {
                played = true
                animateModalEnter(backdrop, card, skipAnimation = skipAnimation)
            }
        }
        backdrop.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                runEnter()
            }

            override fun onViewDetachedFromWindow(v: View) {
                cancelAnimations(v)
            }
        })
        if (backdrop.isAttachedToWindow) {
            runEnter()
        }
    }

    private fun findModalCard(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is ModalCardLayout) return child
        }
        return null
    }

    /**
     * Full-screen modal backdrop scrim that dismisses via [onDismiss] on tap
     * and plays [animateModalEnter] automatically for any [modalCardContainer]
     * child when attached.
     */
    fun modalBackdrop(
        context: Context,
        onDismiss: () -> Unit,
    ): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(MODAL_SCRIM_COLOR)
        isClickable = true
        isFocusable = true
        setOnClickListener { onDismiss() }
        var entered = false
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!entered) {
                    entered = true
                    animateModalEnter(v, findModalCard(this@apply))
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                cancelAnimations(v)
            }
        })
        applyImeSafeInsets(this)
    }

    /**
     * Standard themed modal card container with 16dp elevation and rounded
     * background outline shadow. Marks [View.setClickable] true so taps on
     * card padding never fall through to the scrim backdrop.
     */
    fun modalCardContainer(
        context: Context,
        padHorizontalDp: Int = UiDimens.SPACE_LG,
        padVerticalDp: Int = 14,
    ): LinearLayout = ModalCardLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(context)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        setPadding(
            context.dp(padHorizontalDp),
            context.dp(padVerticalDp),
            context.dp(padHorizontalDp),
            context.dp(padVerticalDp),
        )
        elevation = context.dpF(MODAL_ELEVATION_DP)
        isClickable = true
    }

    /**
     * Standardized modal header with bold title, optional muted subtitle, and
     * optional 1dp hairline divider underneath.
     */
    fun modalHeader(
        context: Context,
        title: CharSequence,
        subtitle: CharSequence? = null,
        gravity: Int = Gravity.CENTER,
        showDivider: Boolean = true,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(
            TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTextColor(UiDimens.ALPHA_PRIMARY)
                setTypeface(typeface, Typeface.BOLD)
                this.gravity = gravity
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        if (!subtitle.isNullOrBlank()) {
            addView(
                TextView(context).apply {
                    text = subtitle
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(UiDimens.ALPHA_MUTED)
                    this.gravity = gravity
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(2) },
            )
        }
        if (showDivider) {
            addView(
                View(context).apply {
                    setBackgroundColor(TileBackgrounds.hairlineDivider(context))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.strokePx(1),
                ).apply { topMargin = context.dp(10) },
            )
        }
        setPadding(0, 0, 0, context.dp(if (showDivider) 8 else 10))
    }

    /** Resource-backed overload of [modalHeader]. */
    fun modalHeader(
        context: Context,
        @StringRes titleRes: Int,
        subtitle: CharSequence? = null,
        gravity: Int = Gravity.CENTER,
        showDivider: Boolean = true,
    ): LinearLayout = modalHeader(
        context = context,
        title = context.getString(titleRes),
        subtitle = subtitle,
        gravity = gravity,
        showDivider = showDivider,
    )

    /**
     * Standardized single-line modal control-hint footer (auto-scales down to
     * 10sp so verbose localized hints never wrap or clip).
     */
    fun modalHintFooter(
        context: Context,
        hintText: CharSequence,
    ): TextView = TextView(context).apply {
        text = hintText
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(UiDimens.ALPHA_SUBTLE)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setPadding(context.dp(4), context.dp(8), context.dp(4), 0)
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            this,
            10,
            12,
            1,
            TypedValue.COMPLEX_UNIT_SP,
        )
    }

    /** Resource-backed overload of [modalHintFooter]. */
    fun modalHintFooter(
        context: Context,
        @StringRes hintRes: Int,
    ): TextView = modalHintFooter(context, context.getString(hintRes))
}
