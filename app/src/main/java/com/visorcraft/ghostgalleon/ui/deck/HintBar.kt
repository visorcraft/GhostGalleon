package com.visorcraft.ghostgalleon.ui.deck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
        return KeycapHintTextView(context).apply {
            text = textFor(context, dockFocused = false)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
            minimumHeight = context.dp(24)
            setPadding(padH, context.dp(5), padH, context.dp(7))
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                this,
                10,
                13,
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
     * Proportional rounded keycap pill span that scales cleanly with
     * [TextViewCompat] uniform auto-sizing and preserves vertical room for borders.
     */
    private class KeycapBadgeSpan(
        private val fillColor: Int,
        private val strokeColor: Int,
        private val textColor: Int,
        private val strokeWidthPx: Float,
    ) : ReplacementSpan() {

        private val rect = RectF()

        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
        ): Int {
            val baseSize = paint.textSize
            val padV = (baseSize * 0.14f).roundToInt().coerceAtLeast(1)
            if (fm != null) {
                paint.getFontMetricsInt(fm)
                fm.ascent -= padV
                fm.top -= padV
                fm.descent += padV
                fm.bottom += padV
            }
            val savedSize = paint.textSize
            val savedFakeBold = paint.isFakeBoldText
            paint.textSize = baseSize * 0.90f
            paint.isFakeBoldText = true
            val textWidth = paint.measureText(text, start, end)
            paint.textSize = savedSize
            paint.isFakeBoldText = savedFakeBold

            val padH = baseSize * 0.36f
            val minPill = baseSize * 1.42f
            val pillWidth = maxOf(minPill, textWidth + padH * 2f)
            val margin = baseSize * 0.12f
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
            val savedColor = paint.color
            val savedStyle = paint.style
            val savedStroke = paint.strokeWidth
            val savedFakeBold = paint.isFakeBoldText
            val savedAntiAlias = paint.isAntiAlias
            val baseFm = paint.fontMetrics

            paint.isAntiAlias = true
            paint.textSize = baseSize * 0.90f
            paint.isFakeBoldText = true
            val badgeFm = paint.fontMetrics
            val textWidth = paint.measureText(text, start, end)

            val padH = baseSize * 0.36f
            val minPill = baseSize * 1.42f
            val pillWidth = maxOf(minPill, textWidth + padH * 2f)
            val margin = baseSize * 0.12f

            val centerY = y + (baseFm.ascent + baseFm.descent) * 0.5f
            val halfH = ((baseFm.descent - baseFm.ascent) * 0.62f).coerceAtLeast(1f)
            val pillTop = maxOf(top.toFloat() + 0.5f, centerY - halfH)
            val pillBottom = minOf(bottom.toFloat() - 0.5f, centerY + halfH)
            val left = x + margin
            val right = left + pillWidth
            val radius = ((pillBottom - pillTop) * 0.30f).coerceAtLeast(2f)

            rect.set(left, pillTop, right, pillBottom)
            paint.style = Paint.Style.FILL
            paint.color = fillColor
            canvas.drawRoundRect(rect, radius, radius, paint)

            val inset = strokeWidthPx * 0.5f
            rect.inset(inset, inset)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidthPx
            paint.color = strokeColor
            canvas.drawRoundRect(rect, radius, radius, paint)

            paint.style = Paint.Style.FILL
            paint.color = textColor
            val textX = left + (pillWidth - textWidth) * 0.5f
            val textY = (pillTop + pillBottom) * 0.5f - (badgeFm.ascent + badgeFm.descent) * 0.5f
            canvas.drawText(text, start, end, textX, textY, paint)

            paint.textSize = baseSize
            paint.color = savedColor
            paint.style = savedStyle
            paint.strokeWidth = savedStroke
            paint.isFakeBoldText = savedFakeBold
            paint.isAntiAlias = savedAntiAlias
        }
    }
}
