package com.visorcraft.ghostgalleon.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.deck.OverlayPrimitives
import com.visorcraft.ghostgalleon.ui.deck.SlotMenuNav
import com.visorcraft.ghostgalleon.ui.deck.TileBackgrounds

/** Pure, host-tested dialog surface and action-button color tokens. */
internal data class DialogPalette(
    val surfaceFill: Int,
    val inputFill: Int,
    val positiveText: Int,
    val destructiveText: Int,
    val neutralText: Int,
    val negativeText: Int,
)

/**
 * Pure palette resolution for [DeckDialogs]. Ensures dialog action buttons
 * meet WCAG AA (`>= 4.5:1`) contrast across Ghost, Teal, OLED, Neon, and
 * custom theme packs.
 */
internal object DeckDialogTokens {

    fun palette(accent: Int, tokens: ThemeTokens): DialogPalette {
        val surface = ThemePack.cardFill(tokens)
        val accentText = ThemePack.accentTextColor(accent, surface)
        return DialogPalette(
            surfaceFill = surface,
            inputFill = ThemePack.surfaceSunken(tokens),
            positiveText = accentText,
            destructiveText = UiDimens.COLOR_DESTRUCTIVE,
            neutralText = accentText,
            negativeText = UiDimens.ALPHA_SECONDARY,
        )
    }

    /**
     * Resolve effective [InputType] bitmask for single-line vs multi-line fields
     * without stripping password variation flags.
     */
    fun resolvedInputType(inputType: Int, singleLine: Boolean): Int =
        if (singleLine) {
            inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE.inv()
        } else {
            inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }

    /**
     * Pure check for whether an IME editor action or hardware Enter key event
     * should submit a single-line [DeckDialogs.promptText] modal. Host-tested.
     */
    fun isImeSubmitAction(
        actionId: Int,
        keyCode: Int? = null,
        keyAction: Int? = null,
    ): Boolean {
        if (actionId == EditorInfo.IME_ACTION_DONE ||
            actionId == EditorInfo.IME_ACTION_GO ||
            actionId == EditorInfo.IME_ACTION_SEND
        ) {
            return true
        }
        val isEnterKey = keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        return actionId == EditorInfo.IME_NULL &&
            isEnterKey &&
            (keyAction == null || keyAction == KeyEvent.ACTION_DOWN)
    }

    /** True when [rawText] satisfies the prompt's [requireNonBlank] policy. */
    fun canSubmitText(rawText: String, requireNonBlank: Boolean): Boolean =
        !requireNonBlank || rawText.trim().isNotEmpty()

    /** Opacity for the positive submit action when enabled vs disabled by validation. */
    fun positiveButtonAlpha(enabled: Boolean): Float = if (enabled) 1f else 0.40f

    internal const val LIST_PRESSED_WASH: Int = 0x22FFFFFF
    internal const val LIST_FOCUSED_WASH_ALPHA: Int = 0x1F
    const val BUTTON_MIN_HEIGHT_DP: Int = 44

    /** Focus border color for dialog action buttons (destructive positive gets red ring). */
    fun buttonFocusStrokeColor(
        which: Int,
        destructivePositive: Boolean,
        accent: Int,
    ): Int = if (which == AlertDialog.BUTTON_POSITIVE && destructivePositive) {
        UiDimens.COLOR_DESTRUCTIVE
    } else {
        accent
    }

    /** Ripple wash color for dialog action buttons (destructive positive gets red ripple). */
    fun buttonRippleColor(which: Int, destructivePositive: Boolean): Int =
        if (which == AlertDialog.BUTTON_POSITIVE && destructivePositive) {
            SlotMenuNav.DESTRUCTIVE_RIPPLE
        } else {
            OverlayPrimitives.DEFAULT_RIPPLE_COLOR
        }

    /** Subtle accent-tinted fill for focused/selected rows inside `chooseItem` lists. */
    fun listFocusedFill(accent: Int, tokens: ThemeTokens): Int =
        SlotMenuNav.blendOverOpaque(
            (LIST_FOCUSED_WASH_ALPHA shl 24) or (accent and 0x00FFFFFF),
            ThemePack.cardFill(tokens),
        )
}

/**
 * Unified OLED-themed [AlertDialog] builders used across decks, settings,
 * about, and controller lab. Applies live [ThemePack] rounded card framing,
 * WCAG-safe accent button tinting, gamepad focus rings on action buttons,
 * padded sunken [EditText] fields, and immersive status-bar preservation.
 */
object DeckDialogs {

    /** Theme-bound [AlertDialog.Builder] using `Theme.GhostGalleon.Dialog`. */
    fun builder(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(context, R.style.Theme_GhostGalleon_Dialog)

    /**
     * Apply live [ThemePack] card surface, hairline list dividers, button
     * contrast/focus rings, and immersive status-bar hiding to a shown dialog.
     */
    fun style(
        dialog: AlertDialog,
        context: Context,
        destructivePositive: Boolean = false,
    ): AlertDialog {
        val tokens = resolveTokens(context)
        val accent = resolveAccent(context, tokens)
        val palette = DeckDialogTokens.palette(accent, tokens)

        dialog.window?.let { win ->
            val inset = context.dp(UiDimens.SPACE_LG)
            win.setBackgroundDrawable(InsetDrawable(TileBackgrounds.card(context), inset))
            win.setDimAmount(0.80f)
            hideStatusBar(win)
        }

        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            setTextColor(UiDimens.ALPHA_SECONDARY)
            setLineSpacing(0f, 1.18f)
        }

        dialog.listView?.apply {
            divider = ColorDrawable(TileBackgrounds.hairlineDivider(context))
            dividerHeight = context.strokePx(1)
            setHeaderDividersEnabled(true)
            setPadding(0, context.dp(UiDimens.SPACE_XS), 0, context.dp(UiDimens.SPACE_XS))
            clipToPadding = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(
                OverlayPrimitives.fadingEdgeLengthPx(context.resources.displayMetrics.density),
            )
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            selector = listSelector(context, accent, tokens)
        }

        fun styleButton(which: Int, textColor: Int) {
            val btn = dialog.getButton(which) ?: return
            val focusColor = DeckDialogTokens.buttonFocusStrokeColor(which, destructivePositive, accent)
            val rippleColor = DeckDialogTokens.buttonRippleColor(which, destructivePositive)
            btn.setTextColor(textColor)
            btn.setTypeface(btn.typeface, Typeface.BOLD)
            btn.isFocusable = true
            btn.minHeight = context.dp(DeckDialogTokens.BUTTON_MIN_HEIGHT_DP)
            val focusableBg = TileBackgrounds.focusableRow(
                context = context,
                accent = focusColor,
                cornerRadiusDp = 12,
                strokeDp = 2,
            )
            btn.background = OverlayPrimitives.roundedRipple(
                context = context,
                content = focusableBg,
                cornerRadiusDp = 12,
                rippleColor = rippleColor,
            )
            btn.setPadding(
                context.dp(14),
                context.dp(10),
                context.dp(14),
                context.dp(10),
            )
        }

        styleButton(
            AlertDialog.BUTTON_POSITIVE,
            if (destructivePositive) palette.destructiveText else palette.positiveText,
        )
        styleButton(AlertDialog.BUTTON_NEUTRAL, palette.neutralText)
        styleButton(AlertDialog.BUTTON_NEGATIVE, palette.negativeText)
        return dialog
    }

    /** Show [builder] and apply [style] immediately. */
    fun show(
        builder: AlertDialog.Builder,
        context: Context,
        destructivePositive: Boolean = false,
    ): AlertDialog = style(builder.show(), context, destructivePositive)

    /**
     * Build a padded, sunken-surface [EditText] ready for dialog insertion.
     * Returns `(container, editText)`.
     */
    fun createInput(
        context: Context,
        initialText: String = "",
        @StringRes hintRes: Int? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        singleLine: Boolean = true,
        selectAllOnFocus: Boolean = true,
    ): Pair<FrameLayout, EditText> {
        val tokens = resolveTokens(context)
        val accent = resolveAccent(context, tokens)
        val input = EditText(context).apply {
            // Apply singleLine BEFORE inputType so TextView.applySingleLine does
            // not overwrite PasswordTransformationMethod on masked API key prompts.
            if (singleLine) {
                isSingleLine = true
                imeOptions = EditorInfo.IME_ACTION_DONE
            }
            this.inputType = DeckDialogTokens.resolvedInputType(inputType, singleLine)
            if (initialText.isNotEmpty()) {
                setText(initialText)
                setSelection(initialText.length)
            }
            if (hintRes != null) setHint(hintRes)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setHintTextColor(UiDimens.ALPHA_SUBTLE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSelectAllOnFocus(selectAllOnFocus)
            background = TileBackgrounds.focusableRow(
                context = context,
                accent = accent,
                idleBackground = TileBackgrounds.sunkenField(context, cornerRadiusDp = 12),
                cornerRadiusDp = 12,
                strokeDp = 2,
            )
            setPadding(
                context.dp(14),
                context.dp(12),
                context.dp(14),
                context.dp(12),
            )
        }
        val container = FrameLayout(context).apply {
            val hPad = context.dp(20)
            setPadding(hPad, context.dp(12), hPad, context.dp(4))
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        return container to input
    }

    /** Styled single-field text prompt dialog (resource title). */
    fun promptText(
        context: Context,
        @StringRes titleRes: Int,
        initialText: String = "",
        @StringRes hintRes: Int? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        @StringRes positiveRes: Int = R.string.action_save,
        @StringRes negativeRes: Int = R.string.action_cancel,
        requireNonBlank: Boolean = false,
        onSubmit: (String) -> Unit,
    ): AlertDialog = promptText(
        context = context,
        title = context.getString(titleRes),
        initialText = initialText,
        hintRes = hintRes,
        inputType = inputType,
        positiveRes = positiveRes,
        negativeRes = negativeRes,
        requireNonBlank = requireNonBlank,
        onSubmit = onSubmit,
    )

    /** Styled single-field text prompt dialog (dynamic title). */
    fun promptText(
        context: Context,
        title: CharSequence,
        initialText: String = "",
        @StringRes hintRes: Int? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        @StringRes positiveRes: Int = R.string.action_save,
        @StringRes negativeRes: Int = R.string.action_cancel,
        requireNonBlank: Boolean = false,
        onSubmit: (String) -> Unit,
    ): AlertDialog {
        val (container, input) = createInput(
            context = context,
            initialText = initialText,
            hintRes = hintRes,
            inputType = inputType,
        )
        val b = builder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positiveRes, null)
            .setNegativeButton(negativeRes, null)
        val dialog = show(b, context)

        fun nudgeInvalidInput() {
            if (!ValueAnimator.areAnimatorsEnabled() || !input.isAttachedToWindow) return
            val dx = context.dpF(6)
            input.animate().cancel()
            input.animate()
                .translationX(-dx)
                .setDuration(45L)
                .withEndAction {
                    if (input.isAttachedToWindow) {
                        input.animate()
                            .translationX(dx)
                            .setDuration(60L)
                            .withEndAction {
                                if (input.isAttachedToWindow) {
                                    input.animate()
                                        .translationX(0f)
                                        .setDuration(45L)
                                        .start()
                                }
                            }
                            .start()
                    }
                }
                .start()
        }
        dialog.setOnDismissListener { input.animate().cancel() }

        fun attemptSubmit() {
            val raw = input.text?.toString().orEmpty()
            if (!DeckDialogTokens.canSubmitText(raw, requireNonBlank)) {
                nudgeInvalidInput()
                return
            }
            dialog.dismiss()
            onSubmit(raw)
        }

        fun syncPositiveState() {
            if (!requireNonBlank) return
            val valid = DeckDialogTokens.canSubmitText(
                input.text?.toString().orEmpty(),
                requireNonBlank = true,
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                isEnabled = valid
                alpha = DeckDialogTokens.positiveButtonAlpha(valid)
            }
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { attemptSubmit() }
        if (requireNonBlank) {
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) = syncPositiveState()
            })
            syncPositiveState()
        }
        input.setOnEditorActionListener { _, actionId, event ->
            if (DeckDialogTokens.isImeSubmitAction(actionId, event?.keyCode, event?.action)) {
                attemptSubmit()
                true
            } else {
                false
            }
        }
        return dialog
    }

    /** Styled list-picker dialog (resource title). */
    fun chooseItem(
        context: Context,
        @StringRes titleRes: Int,
        items: Array<String>,
        @StringRes neutralRes: Int? = null,
        onNeutral: (() -> Unit)? = null,
        @StringRes negativeRes: Int? = R.string.action_cancel,
        onPick: (Int) -> Unit,
    ): AlertDialog = chooseItem(
        context = context,
        title = context.getString(titleRes),
        items = items,
        neutralRes = neutralRes,
        onNeutral = onNeutral,
        negativeRes = negativeRes,
        onPick = onPick,
    )

    /** Styled list-picker dialog (dynamic title). */
    fun chooseItem(
        context: Context,
        title: CharSequence,
        items: Array<String>,
        @StringRes neutralRes: Int? = null,
        onNeutral: (() -> Unit)? = null,
        @StringRes negativeRes: Int? = R.string.action_cancel,
        onPick: (Int) -> Unit,
    ): AlertDialog {
        val b = builder(context)
            .setTitle(title)
            .setItems(items) { _, which -> onPick(which) }
        if (neutralRes != null && onNeutral != null) {
            b.setNeutralButton(neutralRes) { _, _ -> onNeutral() }
        }
        if (negativeRes != null) {
            b.setNegativeButton(negativeRes, null)
        }
        return show(b, context)
    }

    /** Styled confirmation / informational dialog (CharSequence message). */
    fun confirm(
        context: Context,
        @StringRes titleRes: Int,
        message: CharSequence,
        @StringRes positiveRes: Int = R.string.action_ok,
        @StringRes negativeRes: Int? = R.string.action_cancel,
        destructive: Boolean = false,
        onConfirm: () -> Unit = {},
    ): AlertDialog {
        val b = builder(context)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(positiveRes) { _, _ -> onConfirm() }
        if (negativeRes != null) {
            b.setNegativeButton(negativeRes, null)
        }
        return show(b, context, destructivePositive = destructive)
    }

    /** Styled confirmation / informational dialog (resource message). */
    fun confirm(
        context: Context,
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        @StringRes positiveRes: Int = R.string.action_ok,
        @StringRes negativeRes: Int? = R.string.action_cancel,
        destructive: Boolean = false,
        onConfirm: () -> Unit = {},
    ): AlertDialog = confirm(
        context = context,
        titleRes = titleRes,
        message = context.getString(messageRes),
        positiveRes = positiveRes,
        negativeRes = negativeRes,
        destructive = destructive,
        onConfirm = onConfirm,
    )

    /** Styled custom-view dialog (used by scrollable Settings/About modals). */
    fun customView(
        context: Context,
        @StringRes titleRes: Int,
        view: View,
        @StringRes positiveRes: Int? = null,
        onPositive: (() -> Unit)? = null,
        @StringRes negativeRes: Int? = R.string.action_close,
        onNegative: (() -> Unit)? = null,
        destructivePositive: Boolean = false,
    ): AlertDialog {
        val b = builder(context)
            .setTitle(titleRes)
            .setView(view)
        if (positiveRes != null && onPositive != null) {
            b.setPositiveButton(positiveRes) { _, _ -> onPositive() }
        }
        if (negativeRes != null) {
            b.setNegativeButton(negativeRes) { _, _ -> onNegative?.invoke() }
        }
        return show(b, context, destructivePositive = destructivePositive)
    }

    private fun listSelector(
        context: Context,
        accent: Int,
        tokens: ThemeTokens,
    ): StateListDrawable {
        val hInset = context.dp(UiDimens.SPACE_SM)
        val vInset = context.dp(2)
        val radiusPx = context.dpF(10)
        val focused = InsetDrawable(
            GradientDrawable().apply {
                setColor(DeckDialogTokens.listFocusedFill(accent, tokens))
                cornerRadius = radiusPx
                setStroke(context.strokePx(2), accent)
            },
            hInset,
            vInset,
            hInset,
            vInset,
        )
        val pressed = InsetDrawable(
            GradientDrawable().apply {
                setColor(DeckDialogTokens.LIST_PRESSED_WASH)
                cornerRadius = radiusPx
            },
            hInset,
            vInset,
            hInset,
            vInset,
        )
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(android.R.attr.state_selected), focused)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
    }

    private fun resolveTokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) ThemePack.resolve(app.settings) else ThemePack.GHOST
    }

    private fun resolveAccent(context: Context, tokens: ThemeTokens): Int {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) app.settings.accentColor else tokens.accentColor
    }
}
