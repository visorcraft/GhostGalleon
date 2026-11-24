package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.display.SurfaceMode
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.ui.UiDimens
import com.visorcraft.ghostgalleon.ui.dp
import com.visorcraft.ghostgalleon.ui.dpF
import kotlin.math.roundToInt

object HintBar {

    /** SINGLE has no second display: X cannot swap, so the hint omits it. */
    private fun isSingleDisplay(context: Context): Boolean =
        (context.applicationContext as? GhostGalleonApp)
            ?.displayConfig?.mode == SurfaceMode.SINGLE

    fun textFor(context: Context, dockFocused: Boolean): String = when {
        dockFocused -> context.getString(R.string.deck_hint_dock)
        isSingleDisplay(context) ->
            context.getString(R.string.deck_hint_default_single)
        else -> context.getString(R.string.deck_hint_default)
    }

    fun moveText(context: Context): String = context.getString(R.string.deck_hint_move)

    /**
     * Format a localized hint string with tactile inline keycap badges and muted
     * separators using [HintBarFormat.tokenize].
     */
    internal fun formatStyled(context: Context, raw: String): CharSequence {
        val tokens = HintBarFormat.tokenize(raw)
        if (tokens.isEmpty()) return raw
        val span = SpannableString(raw)
        val density = context.resources.displayMetrics.density
        val strokeWidthPx = density.coerceAtLeast(1f)
        val bevelHeightPx = (density * HintBarFormat.KEYCAP_BEVEL_DP).coerceAtLeast(1f)
        val app = context.applicationContext as? GhostGalleonApp
        val themeTokens = app?.let { ThemePack.resolve(it.settings) } ?: ThemePack.GHOST
        val keycapTheme = CompanionThemeAudit.hintKeycapTheme(themeTokens.id)
        val prefixColor = ThemePack.accentTextColor(
            app?.settings?.accentColor ?: themeTokens.accentColor,
            TileBackgrounds.surfaceColor(context),
        )
        for (token in tokens) {
            when (token.kind) {
                HintBarFormat.TokenKind.KEYCAP -> {
                    span.setSpan(
                        KeycapBadgeSpan(
                            fillColor = keycapTheme.fillColor,
                            strokeColor = keycapTheme.strokeColor,
                            textColor = keycapTheme.textColor,
                            strokeWidthPx = strokeWidthPx,
                            bevelHeightPx = bevelHeightPx,
                        ),
                        token.start,
                        token.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                HintBarFormat.TokenKind.PREFIX -> {
                    span.setSpan(
                        ForegroundColorSpan(prefixColor),
                        token.start,
                        token.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    span.setSpan(
                        StyleSpan(Typeface.BOLD),
                        token.start,
                        token.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                HintBarFormat.TokenKind.SEPARATOR -> {
                    span.setSpan(
                        ForegroundColorSpan(keycapTheme.separatorColor),
                        token.start,
                        token.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                HintBarFormat.TokenKind.ACTION -> {
                    // Inherits TextView's ALPHA_SECONDARY base color.
                }
            }
        }
        return span
    }

    fun build(context: Context): View {
        val padH = context.dp(UiDimens.SPACE_LG)
        val padV = context.dp(HintBarFormat.BAR_PAD_VERTICAL_DP)
        return KeycapHintTextView(context).apply {
            text = textFor(context, dockFocused = false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, HintBarFormat.MAX_TEXT_SP.toFloat())
            setTextColor(UiDimens.ALPHA_SECONDARY)
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            minimumHeight = context.dp(24)
            setPadding(padH, padV, padH, padV)
            setShadowLayer(
                context.dpF(HintBarFormat.TEXT_SHADOW_RADIUS_DP),
                0f,
                context.dpF(HintBarFormat.TEXT_SHADOW_DY_DP),
                HintBarFormat.TEXT_SHADOW_COLOR,
            )
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                HintBarFormat.MIN_TEXT_SP,
                HintBarFormat.MAX_TEXT_SP,
                1,
                TypedValue.COMPLEX_UNIT_SP,
            )
        }
    }

    /**
     * [TextView] subclass that transparently styles assigned raw hint strings
     * into keycap-badged spans while deduplicating repeated identical writes on
     * hot D-pad selection ticks and keeping plain-text TalkBack descriptions.
     */
    private class KeycapHintTextView(context: Context) : TextView(context) {
        private var lastRawText: String? = null
        private var applyingStyled = false

        override fun setText(text: CharSequence?, type: BufferType?) {
            if (applyingStyled) {
                super.setText(text, type)
                return
            }
            if (text == null) {
                lastRawText = null
                contentDescription = null
                super.setText(null, type)
                return
            }
            val raw = text.toString()
            if (raw == lastRawText) return
            lastRawText = raw
            contentDescription = CompanionA11y.plainHintDescription(raw)
            val styled = formatStyled(context, raw)
            applyingStyled = true
            try {
                super.setText(styled, BufferType.SPANNABLE)
            } finally {
                applyingStyled = false
            }
        }
    }

    /**
     * Proportional rounded keycap pill span with a physical darker bottom bevel,
     * scaling cleanly with [TextViewCompat] uniform auto-sizing and zero
     * per-frame allocations inside [draw].
     */
    private class KeycapBadgeSpan(
        private val fillColor: Int,
        private val strokeColor: Int,
        private val textColor: Int,
        private val strokeWidthPx: Float,
        private val bevelHeightPx: Float,
    ) : ReplacementSpan() {

        private val rect = RectF()
        private val cachedFm = Paint.FontMetrics()
        private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            val baseSize = paint.textSize
            val padV = HintBarFormat.keycapPadVPx(baseSize)
            if (fm != null) {
                paint.getFontMetricsInt(fm)
                fm.ascent -= padV
                fm.top -= padV
                fm.descent += padV
                fm.bottom += padV
            }
            val savedSize = paint.textSize
            val savedFakeBold = paint.isFakeBoldText
            paint.textSize = baseSize * HintBarFormat.KEYCAP_TEXT_SCALE
            paint.isFakeBoldText = true
            val textWidth = paint.measureText(text, start, end)
            paint.textSize = savedSize
            paint.isFakeBoldText = savedFakeBold

            val pillWidth = HintBarFormat.keycapPillWidthPx(baseSize, textWidth)
            val margin = HintBarFormat.keycapMarginHPx(baseSize)
            return (pillWidth + margin * 2f).roundToInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
        ) {
            val baseSize = paint.textSize
            paint.getFontMetrics(cachedFm)
            val baseAscent = cachedFm.ascent
            val baseDescent = cachedFm.descent

            badgePaint.typeface = paint.typeface
            badgePaint.textSize = baseSize * HintBarFormat.KEYCAP_TEXT_SCALE
            badgePaint.isFakeBoldText = true
            badgePaint.getFontMetrics(cachedFm)
            val badgeAscent = cachedFm.ascent
            val badgeDescent = cachedFm.descent
            val textWidth = badgePaint.measureText(text, start, end)

            val pillWidth = HintBarFormat.keycapPillWidthPx(baseSize, textWidth)
            val margin = HintBarFormat.keycapMarginHPx(baseSize)

            val centerY = y + (baseAscent + baseDescent) * 0.5f
            val halfH = ((baseDescent - baseAscent) * 0.62f).coerceAtLeast(1f)
            val pillTop = maxOf(top.toFloat() + 0.5f, centerY - halfH)
            val pillBottom = minOf(bottom.toFloat() - 0.5f, centerY + halfH)
            val pillHeight = (pillBottom - pillTop).coerceAtLeast(1f)
            val left = x + margin
            val right = left + pillWidth
            val radius = (pillHeight * 0.30f).coerceAtLeast(2f)
            val bevelPx = minOf(bevelHeightPx, pillHeight * 0.25f)
            val faceBottom = (pillBottom - bevelPx).coerceAtLeast(pillTop + 1f)

            // 1. Darker bottom bevel base (adds tactile rim depth & wallpaper separation).
            rect.set(left, pillTop, right, pillBottom)
            badgePaint.style = Paint.Style.FILL
            badgePaint.color = HintBarFormat.KEYCAP_BEVEL_COLOR
            canvas.drawRoundRect(rect, radius, radius, badgePaint)

            // 2. Elevated upper keycap face sitting cleanly above the 1.5dp bottom rim.
            rect.set(left, pillTop, right, faceBottom)
            badgePaint.color = fillColor
            canvas.drawRoundRect(rect, radius, radius, badgePaint)

            // 3. Crisp 1dp outer keycap rim stroke.
            val inset = strokeWidthPx * 0.5f
            rect.set(left + inset, pillTop + inset, right - inset, pillBottom - inset)
            badgePaint.style = Paint.Style.STROKE
            badgePaint.strokeWidth = strokeWidthPx
            badgePaint.color = strokeColor
            canvas.drawRoundRect(rect, radius, radius, badgePaint)

            // 4. Centered keycap glyph on the upper face.
            badgePaint.style = Paint.Style.FILL
            badgePaint.color = textColor
            val textX = left + (pillWidth - textWidth) * 0.5f
            val textY = (pillTop + faceBottom) * 0.5f - (badgeAscent + badgeDescent) * 0.5f
            canvas.drawText(text, start, end, textX, textY, badgePaint)
        }
    }
}
