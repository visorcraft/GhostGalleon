package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.widget.TextViewCompat
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import com.visorcraft.ghostgalleon.ui.strokePx

/**
 * Shared modal/overlay framing primitives used across deck and settings
 * overlays (SlotMenu, QuickPanel, FolderPanel, AppPicker, SessionSwitcherView,
 * SetupCard, AboutPage). Keeps scrim opacity, responsive widths, flicker-free
 * max-height scrolling, and headers/footers consistent across dual short
 * (~540dp) and 16:9 single-screen handheld displays.
 */
object OverlayPrimitives {

    /** Standard 80% black modal scrim backdrop. */
    const val MODAL_SCRIM_COLOR: Int = UiDimens.SCRIM_MODAL

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
     * 1-frame `scroll.post` height-shrink flicker.
     */
    class MaxHeightScrollView(
        context: Context,
        var maxHeightPx: Int,
    ) : ScrollView(context) {

        init {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
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

    /** Full-screen modal backdrop scrim that dismisses via [onDismiss] on tap. */
    fun modalBackdrop(
        context: Context,
        onDismiss: () -> Unit,
    ): FrameLayout = FrameLayout(context).apply {
        setBackgroundColor(MODAL_SCRIM_COLOR)
        isClickable = true
        isFocusable = true
        setOnClickListener { onDismiss() }
    }

    /**
     * Standard themed modal card container. Marks [View.setClickable] true so
     * taps on card padding never fall through to the scrim backdrop.
     */
    fun modalCardContainer(
        context: Context,
        padHorizontalDp: Int = UiDimens.SPACE_LG,
        padVerticalDp: Int = 14,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = TileBackgrounds.card(context)
        setPadding(
            context.dp(padHorizontalDp),
            context.dp(padVerticalDp),
            context.dp(padHorizontalDp),
            context.dp(padVerticalDp),
        )
        elevation = context.dpF(8)
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
