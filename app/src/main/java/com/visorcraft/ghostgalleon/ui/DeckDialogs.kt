package com.visorcraft.ghostgalleon.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.visorcraft.ghostgalleon.GhostGalleonApp
import com.visorcraft.ghostgalleon.R
import com.visorcraft.ghostgalleon.settings.ThemePack
import com.visorcraft.ghostgalleon.settings.ThemeTokens
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
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setTypeface(typeface, Typeface.BOLD)
        }
        dialog.findViewById<TextView>(android.R.id.message)?.apply {
            setTextColor(UiDimens.ALPHA_SECONDARY)
        }

        dialog.listView?.apply {
            divider = ColorDrawable(TileBackgrounds.hairlineDivider(context))
            dividerHeight = context.strokePx(1)
            setPadding(0, context.dp(UiDimens.SPACE_XS), 0, context.dp(UiDimens.SPACE_XS))
            clipToPadding = false
        }

        fun styleButton(which: Int, textColor: Int) {
            val btn = dialog.getButton(which) ?: return
            btn.setTextColor(textColor)
            btn.setTypeface(btn.typeface, Typeface.BOLD)
            btn.isFocusable = true
            btn.background = TileBackgrounds.focusableRow(
                context = context,
                accent = accent,
                cornerRadiusDp = 12,
                strokeDp = 2,
            )
            btn.setPadding(
                context.dp(14),
                context.dp(UiDimens.SPACE_SM),
                context.dp(14),
                context.dp(UiDimens.SPACE_SM),
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
        val input = EditText(context).apply {
            // Apply singleLine BEFORE inputType so TextView.applySingleLine does
            // not overwrite PasswordTransformationMethod on masked API key prompts.
            if (singleLine) isSingleLine = true
            this.inputType = DeckDialogTokens.resolvedInputType(inputType, singleLine)
            if (initialText.isNotEmpty()) setText(initialText)
            if (hintRes != null) setHint(hintRes)
            setTextColor(UiDimens.ALPHA_PRIMARY)
            setHintTextColor(UiDimens.ALPHA_SUBTLE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setSelectAllOnFocus(selectAllOnFocus)
            background = TileBackgrounds.sunkenField(context, cornerRadiusDp = 12)
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
        onSubmit: (String) -> Unit,
    ): AlertDialog = promptText(
        context = context,
        title = context.getString(titleRes),
        initialText = initialText,
        hintRes = hintRes,
        inputType = inputType,
        positiveRes = positiveRes,
        negativeRes = negativeRes,
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
            .setPositiveButton(positiveRes) { _, _ ->
                onSubmit(input.text?.toString().orEmpty())
            }
            .setNegativeButton(negativeRes, null)
        return show(b, context)
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

    private fun resolveTokens(context: Context): ThemeTokens {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) ThemePack.resolve(app.settings) else ThemePack.GHOST
    }

    private fun resolveAccent(context: Context, tokens: ThemeTokens): Int {
        val app = context.applicationContext
        return if (app is GhostGalleonApp) app.settings.accentColor else tokens.accentColor
    }
}
