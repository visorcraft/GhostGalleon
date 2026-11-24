package com.visorcraft.ghostgalleon.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
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
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.audio.SoundCue
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
import com.visorcraft.ghostgalleon.ui.deck.OverlayAudioLogic
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
    const val STANDARD_DIALOG_MAX_WIDTH_DP: Int = 460
    const val CUSTOM_VIEW_DIALOG_MAX_WIDTH_DP: Int = 520
    const val DIALOG_WIDTH_FRAC: Float = 0.88f
    const val LIST_ROW_MIN_HEIGHT_DP: Int = 48

    /**
     * Responsive dialog width clamped cleanly across 360dp portrait, 540dp Sugar,
     * and 960dp Odin 2 Portal viewports (`460dp` standard, `520dp` custom-view).
     * Host-tested.
     */
    fun dialogWidthPx(
        screenWidthPx: Int,
        density: Float,
        isCustomView: Boolean = false,
    ): Int = OverlayPrimitives.responsiveCardWidthPx(
        screenWidthPx = screenWidthPx,
        density = density,
        maxDp = if (isCustomView) CUSTOM_VIEW_DIALOG_MAX_WIDTH_DP else STANDARD_DIALOG_MAX_WIDTH_DP,
        widthFrac = DIALOG_WIDTH_FRAC,
        minMarginDp = UiDimens.SPACE_LG,
    )

    /**
     * True when the inline 44dp quick-clear button should be visible.
     * Suppresses the internal clear button whenever a caller adds an external
     * overlay child (e.g. Game Mode search's own clear button) to the returned
     * container (`hasExternalChild == true`). Host-tested.
     */
    fun shouldShowClearButton(
        text: CharSequence?,
        hasExternalChild: Boolean = false,
    ): Boolean = !hasExternalChild && !text.isNullOrEmpty()

    /**
     * Border stroke color for [DeckDialogs.createInput]'s persistent pill wrapper.
     * Blank-submit invalid state flashes [UiDimens.COLOR_DESTRUCTIVE]; focused state
     * lifts [accent] via [OverlayPrimitives.accessibleFocusStroke] (`>= 3.0:1`);
     * idle falls back to [idleHairline]. Host-tested.
     */
    fun inputFocusStrokeColor(
        focused: Boolean,
        invalid: Boolean,
        accent: Int,
        surface: Int,
        idleHairline: Int = ThemePack.HAIRLINE_LIGHT,
    ): Int = when {
        invalid -> UiDimens.COLOR_DESTRUCTIVE
        focused -> OverlayPrimitives.accessibleFocusStroke(accent, surface)
        else -> idleHairline
    }

    /** Border thickness (dp) for [DeckDialogs.createInput]'s pill wrapper. */
    fun inputStrokeDp(focused: Boolean, invalid: Boolean): Int =
        if (focused || invalid) 2 else 1

    /**
     * Valid initial focus/scroll index for [DeckDialogs.chooseItem]. Returns
     * [checkedIndex] when in `0 until count`, else `-1`. Host-tested.
     */
    fun initialCheckedFocusIndex(checkedIndex: Int, count: Int): Int =
        if (count > 0 && checkedIndex in 0 until count) checkedIndex else -1

    /**
     * WCAG AA (`>= 4.5:1`) accent text & checkmark color for the active checked row
     * in [DeckDialogs.chooseItem], legible over both idle card fill and focused wash.
     */
    fun checkedRowTextColor(accent: Int, tokens: ThemeTokens): Int {
        val idleSurface = ThemePack.cardFill(tokens)
        val focusedSurface = listFocusedFill(accent, tokens)
        val onIdle = ThemePack.accentTextColor(accent, idleSurface)
        val onFocused = ThemePack.accentTextColor(accent, focusedSurface)
        return if (ThemePack.relativeLuminance(onFocused) >= ThemePack.relativeLuminance(onIdle)) {
            onFocused
        } else {
            onIdle
        }
    }

    /**
     * Strip redundant trailing `" ✓"` from [text] on rows that already render the
     * dedicated `ic_check_circle` glyph, preserving any `Spanned` formatting spans.
     */
    fun stripTrailingCheckmark(text: CharSequence): CharSequence {
        val suffix = " \u2713"
        return if (text.length > suffix.length && text.endsWith(suffix)) {
            text.subSequence(0, text.length - suffix.length)
        } else {
            text
        }
    }

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
 * responsive OLED dialog widths, WCAG-safe accent button tinting, gamepad
 * focus rings on action buttons, padded sunken [EditText] pills with quick
 * clear, and immersive status-bar preservation.
 */
object DeckDialogs {

    private const val TITLE_DIVIDER_TAG = "gg_dialog_title_divider"

    private class InputPillState(
        val pill: View,
        val setInvalid: (Boolean) -> Unit,
    )

    private class ChooseRowHolder(
        val accentBar: View,
        val label: TextView,
        val checkIcon: ImageView,
    )

    /** Theme-bound [AlertDialog.Builder] using `Theme.GhostGalleon.Dialog`. */
    fun builder(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(context, R.style.Theme_GhostGalleon_Dialog)

    /**
     * Apply live [ThemePack] card surface, responsive OLED width clamp, subtle
     * title hairline, hairline list dividers, button contrast/focus rings, and
     * immersive status-bar hiding to a shown dialog.
     */
    fun style(
        dialog: AlertDialog,
        context: Context,
        destructivePositive: Boolean = false,
        isCustomView: Boolean = false,
    ): AlertDialog {
        val tokens = resolveTokens(context)
        val accent = resolveAccent(context, tokens)
        val palette = DeckDialogTokens.palette(accent, tokens)

        dialog.window?.let { win ->
            val dm = context.resources.displayMetrics
            val widthPx = DeckDialogTokens.dialogWidthPx(
                screenWidthPx = dm.widthPixels,
                density = dm.density,
                isCustomView = isCustomView,
            )
            val vInset = context.dp(UiDimens.SPACE_MD)
            win.setBackgroundDrawable(
                InsetDrawable(OverlayPrimitives.modalCardBackground(context), 0, vInset, 0, vInset),
            )
            win.setLayout(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setDimAmount(0.80f)
            hideStatusBar(win)
        }

        val titleView = dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            OverlayPrimitives.markHeading(this)
        }
        val topPanel = (titleView?.parent as? View)?.parent as? LinearLayout
        if (titleView != null && titleView.visibility == View.VISIBLE &&
            topPanel != null && topPanel.findViewWithTag<View>(TITLE_DIVIDER_TAG) == null
        ) {
            val divider = View(context).apply {
                tag = TITLE_DIVIDER_TAG
                setBackgroundColor(TileBackgrounds.hairlineDivider(context))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            topPanel.addView(
                divider,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.strokePx(1),
                ).apply {
                    marginStart = context.dp(20)
                    marginEnd = context.dp(20)
                    topMargin = context.dp(10)
                },
            )
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
            val focusColor = OverlayPrimitives.accessibleFocusStroke(
                DeckDialogTokens.buttonFocusStrokeColor(which, destructivePositive, accent),
                palette.surfaceFill,
            )
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
        isCustomView: Boolean = false,
    ): AlertDialog = style(
        dialog = builder.show(),
        context = context,
        destructivePositive = destructivePositive,
        isCustomView = isCustomView,
    )

    /**
     * Build a padded, sunken-surface pill container wrapping an [EditText] with
     * reactive WCAG focus stroke and a trailing RTL-safe 44dp Clear button.
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
        val inputFill = ThemePack.surfaceSunken(tokens)
        val idleHairline = ThemePack.hairlineDivider(tokens)
        val pillBg = GradientDrawable().apply {
            setColor(inputFill)
            cornerRadius = context.dpF(12)
            setStroke(context.strokePx(1), idleHairline)
        }
        var invalidState = false
        var focusedState = false
        var hasExternalChild = false

        fun paintPillStroke() {
            val strokeDp = DeckDialogTokens.inputStrokeDp(focusedState, invalidState)
            val strokeColor = DeckDialogTokens.inputFocusStrokeColor(
                focused = focusedState,
                invalid = invalidState,
                accent = accent,
                surface = inputFill,
                idleHairline = idleHairline,
            )
            pillBg.setStroke(context.strokePx(strokeDp), strokeColor)
        }

        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(48)
            background = pillBg
        }
        lateinit var clearBtn: ImageView
        fun syncClearVisibility(text: CharSequence?) {
            clearBtn.visibility = if (DeckDialogTokens.shouldShowClearButton(text, hasExternalChild)) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
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
            background = null
            setPaddingRelative(
                context.dp(14),
                context.dp(12),
                context.dp(6),
                context.dp(12),
            )
            setOnFocusChangeListener { _, hasFocus ->
                focusedState = hasFocus
                paintPillStroke()
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    syncClearVisibility(s)
                    if (invalidState) {
                        invalidState = false
                        paintPillStroke()
                    }
                }
            })
        }
        clearBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(UiDimens.ALPHA_SECONDARY)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = context.getString(R.string.action_clear)
            isFocusable = false
            isClickable = true
            background = OverlayPrimitives.roundedRipple(
                context = context,
                content = null,
                cornerRadiusDp = 22,
            )
            val pad = context.dp(13)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                playCue(context, SoundCue.FILTER_SELECT)
                input.setText("")
                input.requestFocus()
            }
        }
        syncClearVisibility(initialText)
        val touchSize = context.dp(UiDimens.TOUCH_MIN_DP)
        pill.addView(
            input,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        pill.addView(
            clearBtn,
            LinearLayout.LayoutParams(touchSize, touchSize).apply {
                marginEnd = context.dp(2)
            },
        )
        val container = FrameLayout(context).apply {
            val hPad = context.dp(20)
            setPadding(hPad, context.dp(12), hPad, context.dp(4))
            addView(
                pill,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    if (child !== pill) {
                        hasExternalChild = childCount > 1
                        syncClearVisibility(input.text)
                    }
                }

                override fun onChildViewRemoved(parent: View?, child: View?) {
                    hasExternalChild = childCount > 1
                    syncClearVisibility(input.text)
                }
            })
            tag = InputPillState(pill) { invalid ->
                invalidState = invalid
                paintPillStroke()
            }
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
        emitSubmitSound: Boolean = true,
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
        emitSubmitSound = emitSubmitSound,
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
        emitSubmitSound: Boolean = true,
        onSubmit: (String) -> Unit,
    ): AlertDialog {
        val (container, input) = createInput(
            context = context,
            initialText = initialText,
            hintRes = hintRes,
            inputType = inputType,
        )
        val pillState = container.tag as? InputPillState
        val shakeTarget: View = pillState?.pill ?: input
        val b = builder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton(positiveRes, null)
            .setNegativeButton(negativeRes) { _, _ -> playCue(context, SoundCue.BACK) }
            .setOnCancelListener { playCue(context, SoundCue.BACK) }
        val dialog = b.create()
        @Suppress("DEPRECATION")
        dialog.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        dialog.show()
        style(dialog, context)
        input.post { input.requestFocus() }

        fun nudgeInvalidInput() {
            pillState?.setInvalid?.invoke(true)
            playCue(context, SoundCue.ERROR_DENY)
            if (!ValueAnimator.areAnimatorsEnabled() || !shakeTarget.isAttachedToWindow) return
            val dx = context.dpF(6)
            shakeTarget.animate().cancel()
            shakeTarget.animate()
                .translationX(-dx)
                .setDuration(45L)
                .withEndAction {
                    if (shakeTarget.isAttachedToWindow) {
                        shakeTarget.animate()
                            .translationX(dx)
                            .setDuration(60L)
                            .withEndAction {
                                if (shakeTarget.isAttachedToWindow) {
                                    shakeTarget.animate()
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
        dialog.setOnDismissListener { shakeTarget.animate().cancel() }

        fun attemptSubmit() {
            val raw = input.text?.toString().orEmpty()
            if (!DeckDialogTokens.canSubmitText(raw, requireNonBlank)) {
                nudgeInvalidInput()
                return
            }
            if (emitSubmitSound) playCue(context, SoundCue.CONFIRM)
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
        items: Array<out CharSequence>,
        @StringRes neutralRes: Int? = null,
        onNeutral: (() -> Unit)? = null,
        @StringRes negativeRes: Int? = R.string.action_cancel,
        emitPickSound: Boolean = true,
        checkedIndex: Int = -1,
        onPick: (Int) -> Unit,
    ): AlertDialog = chooseItem(
        context = context,
        title = context.getString(titleRes),
        items = items,
        neutralRes = neutralRes,
        onNeutral = onNeutral,
        negativeRes = negativeRes,
        emitPickSound = emitPickSound,
        checkedIndex = checkedIndex,
        onPick = onPick,
    )

    /** Styled list-picker dialog (dynamic title) with optional active-row emphasis & focus sync. */
    fun chooseItem(
        context: Context,
        title: CharSequence,
        items: Array<out CharSequence>,
        @StringRes neutralRes: Int? = null,
        onNeutral: (() -> Unit)? = null,
        @StringRes negativeRes: Int? = R.string.action_cancel,
        emitPickSound: Boolean = true,
        checkedIndex: Int = -1,
        onPick: (Int) -> Unit,
    ): AlertDialog {
        val tokens = resolveTokens(context)
        val accent = resolveAccent(context, tokens)
        val initialFocus = DeckDialogTokens.initialCheckedFocusIndex(checkedIndex, items.size)
        val adapter = chooseItemAdapter(
            context = context,
            items = items,
            checkedIndex = initialFocus,
            accent = accent,
            tokens = tokens,
        )
        val b = builder(context)
            .setTitle(title)
            .setAdapter(adapter) { _, which ->
                if (emitPickSound) playCue(context, SoundCue.CONFIRM)
                onPick(which)
            }
            .setOnCancelListener { playCue(context, SoundCue.BACK) }
        if (neutralRes != null && onNeutral != null) {
            b.setNeutralButton(neutralRes) { _, _ ->
                playCue(context, SoundCue.CONFIRM)
                onNeutral()
            }
        }
        if (negativeRes != null) {
            b.setNegativeButton(negativeRes) { _, _ -> playCue(context, SoundCue.BACK) }
        }
        val dialog = show(b, context)
        if (initialFocus >= 0) {
            dialog.listView?.let { lv ->
                lv.setSelection(initialFocus)
                lv.post { lv.setSelection(initialFocus) }
            }
        }
        return dialog
    }

    private fun chooseItemAdapter(
        context: Context,
        items: Array<out CharSequence>,
        checkedIndex: Int,
        accent: Int,
        tokens: ThemeTokens,
    ): BaseAdapter {
        val hasChecked = checkedIndex >= 0
        val checkedTint = DeckDialogTokens.checkedRowTextColor(accent, tokens)
        val barColor = OverlayPrimitives.accessibleFocusStroke(accent, ThemePack.cardFill(tokens))
        val minRowHeightPx = context.dp(DeckDialogTokens.LIST_ROW_MIN_HEIGHT_DP)
        val hStartPx = context.dp(if (hasChecked) 14 else 20)
        val hEndPx = context.dp(18)
        val vPadPx = context.dp(10)
        val barWidthPx = context.dp(3)
        val barHeightPx = context.dp(22)
        val barGapPx = context.dp(10)
        val iconSizePx = context.dp(18)
        val iconGapPx = context.dp(10)
        val checkTintList = ColorStateList.valueOf(checkedTint)

        return object : BaseAdapter() {
            override fun getCount(): Int = items.size
            override fun getItem(position: Int): CharSequence = items[position]
            override fun getItemId(position: Int): Long = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row: LinearLayout
                val holder: ChooseRowHolder
                if (convertView is LinearLayout && convertView.tag is ChooseRowHolder) {
                    row = convertView
                    holder = convertView.tag as ChooseRowHolder
                } else {
                    val accentBar = View(context).apply {
                        background = TileBackgrounds.activeAccentBar(context, barColor, cornerRadiusDp = 2)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    val label = TextView(context).apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                        useTabularNumerals()
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    }
                    val checkIcon = ImageView(context).apply {
                        setImageResource(R.drawable.ic_check_circle)
                        imageTintList = checkTintList
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                    row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = minRowHeightPx
                        setPaddingRelative(hStartPx, vPadPx, hEndPx, vPadPx)
                        addView(
                            accentBar,
                            LinearLayout.LayoutParams(barWidthPx, barHeightPx).apply {
                                marginEnd = barGapPx
                            },
                        )
                        addView(
                            label,
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
                        )
                        addView(
                            checkIcon,
                            LinearLayout.LayoutParams(iconSizePx, iconSizePx).apply {
                                marginStart = iconGapPx
                            },
                        )
                    }
                    holder = ChooseRowHolder(accentBar, label, checkIcon)
                    row.tag = holder
                }
                val isChecked = position == checkedIndex
                val rawText = items[position]
                holder.accentBar.visibility = when {
                    !hasChecked -> View.GONE
                    isChecked -> View.VISIBLE
                    else -> View.INVISIBLE
                }
                holder.label.text = if (isChecked) {
                    DeckDialogTokens.stripTrailingCheckmark(rawText)
                } else {
                    rawText
                }
                holder.label.setTextColor(if (isChecked) checkedTint else UiDimens.ALPHA_PRIMARY)
                holder.label.setTypeface(null, if (isChecked) Typeface.BOLD else Typeface.NORMAL)
                holder.checkIcon.visibility = if (isChecked) View.VISIBLE else View.GONE
                row.contentDescription = rawText
                return row
            }
        }
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
            .setPositiveButton(positiveRes) { _, _ ->
                playCue(context, OverlayAudioLogic.dialogConfirmCue(destructive))
                onConfirm()
            }
            .setOnCancelListener { playCue(context, SoundCue.BACK) }
        if (negativeRes != null) {
            b.setNegativeButton(negativeRes) { _, _ -> playCue(context, SoundCue.BACK) }
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
            .setOnCancelListener { playCue(context, SoundCue.BACK) }
        if (positiveRes != null && onPositive != null) {
            b.setPositiveButton(positiveRes) { _, _ ->
                playCue(context, OverlayAudioLogic.dialogConfirmCue(destructivePositive))
                onPositive()
            }
        }
        if (negativeRes != null) {
            b.setNegativeButton(negativeRes) { _, _ ->
                playCue(context, SoundCue.BACK)
                onNegative?.invoke()
            }
        }
        return show(
            builder = b,
            context = context,
            destructivePositive = destructivePositive,
            isCustomView = true,
        )
    }

    private fun listSelector(
        context: Context,
        accent: Int,
        tokens: ThemeTokens,
    ): StateListDrawable {
        val hInset = context.dp(UiDimens.SPACE_SM)
        val vInset = context.dp(2)
        val radiusPx = context.dpF(10)
        val focusedFill = DeckDialogTokens.listFocusedFill(accent, tokens)
        val focusedStroke = OverlayPrimitives.accessibleFocusStroke(accent, focusedFill)
        val focused = InsetDrawable(
            GradientDrawable().apply {
                setColor(focusedFill)
                cornerRadius = radiusPx
                setStroke(context.strokePx(2), focusedStroke)
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

    private fun playCue(context: Context, cue: SoundCue) {
        (context.applicationContext as? GhostGalleonApp)?.playSfx(cue)
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
