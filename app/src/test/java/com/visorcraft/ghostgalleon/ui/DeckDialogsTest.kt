package com.visorcraft.ghostgalleon.ui

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.visorcraft.ghostgalleon.settings.ThemePack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckDialogsTest {

    @Test
    fun `resolvedInputType preserves password variation and toggles multiline flag`() {
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val singlePassword = DeckDialogTokens.resolvedInputType(
            password or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
            singleLine = true,
        )
        assertEquals(password, singlePassword)

        val multiText = DeckDialogTokens.resolvedInputType(
            InputType.TYPE_CLASS_TEXT,
            singleLine = false,
        )
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, multiText)
    }

    @Test
    fun `dialog action button colors meet WCAG AA contrast across all built-in themes`() {
        for (tokens in ThemePack.BUILTINS) {
            val palette = DeckDialogTokens.palette(tokens.accentColor, tokens)
            val surface = palette.surfaceFill

            val positiveRatio = ThemePack.contrastRatio(palette.positiveText, surface)
            assertTrue(
                "Low positive contrast $positiveRatio on theme=${tokens.id}",
                positiveRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val neutralRatio = ThemePack.contrastRatio(palette.neutralText, surface)
            assertTrue(
                "Low neutral contrast $neutralRatio on theme=${tokens.id}",
                neutralRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val destructiveRatio = ThemePack.contrastRatio(palette.destructiveText, surface)
            assertTrue(
                "Low destructive contrast $destructiveRatio on theme=${tokens.id}",
                destructiveRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val negEffective = blendOverOpaque(palette.negativeText, surface)
            val negativeRatio = ThemePack.contrastRatio(negEffective, surface)
            assertTrue(
                "Low negative contrast $negativeRatio on theme=${tokens.id}",
                negativeRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )

            val inputTextRatio = ThemePack.contrastRatio(UiDimens.ALPHA_PRIMARY, palette.inputFill)
            assertTrue(
                "Low input contrast $inputTextRatio on theme=${tokens.id}",
                inputTextRatio >= ThemePack.MIN_TEXT_CONTRAST,
            )
        }
    }

    private fun blendOverOpaque(fg: Int, bg: Int): Int {
        val a = ((fg ushr 24) and 0xFF) / 255.0
        val fr = (fg shr 16) and 0xFF
        val fgCh = (fg shr 8) and 0xFF
        val fb = fg and 0xFF
        val br = (bg shr 16) and 0xFF
        val bgCh = (bg shr 8) and 0xFF
        val bb = bg and 0xFF
        val r = (fr * a + br * (1.0 - a)).toInt().coerceIn(0, 255)
        val g = (fgCh * a + bgCh * (1.0 - a)).toInt().coerceIn(0, 255)
        val b = (fb * a + bb * (1.0 - a)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun `isImeSubmitAction accepts Done, Go, Send, and hardware Enter key down only`() {
        assertTrue(DeckDialogTokens.isImeSubmitAction(EditorInfo.IME_ACTION_DONE))
        assertTrue(DeckDialogTokens.isImeSubmitAction(EditorInfo.IME_ACTION_GO))
        assertTrue(DeckDialogTokens.isImeSubmitAction(EditorInfo.IME_ACTION_SEND))
        assertTrue(
            DeckDialogTokens.isImeSubmitAction(
                EditorInfo.IME_NULL,
                keyCode = KeyEvent.KEYCODE_ENTER,
                keyAction = KeyEvent.ACTION_DOWN,
            ),
        )
        assertTrue(
            DeckDialogTokens.isImeSubmitAction(
                EditorInfo.IME_NULL,
                keyCode = KeyEvent.KEYCODE_NUMPAD_ENTER,
                keyAction = KeyEvent.ACTION_DOWN,
            ),
        )
        assertFalse(
            DeckDialogTokens.isImeSubmitAction(
                EditorInfo.IME_NULL,
                keyCode = KeyEvent.KEYCODE_ENTER,
                keyAction = KeyEvent.ACTION_UP,
            ),
        )
        assertFalse(DeckDialogTokens.isImeSubmitAction(EditorInfo.IME_ACTION_NEXT))
        assertFalse(DeckDialogTokens.isImeSubmitAction(EditorInfo.IME_NULL, keyCode = KeyEvent.KEYCODE_A, keyAction = KeyEvent.ACTION_DOWN))
    }

    @Test
    fun `canSubmitText and positiveButtonAlpha honor requireNonBlank guard`() {
        assertTrue(DeckDialogTokens.canSubmitText("", requireNonBlank = false))
        assertTrue(DeckDialogTokens.canSubmitText("   ", requireNonBlank = false))
        assertFalse(DeckDialogTokens.canSubmitText("", requireNonBlank = true))
        assertFalse(DeckDialogTokens.canSubmitText("   ", requireNonBlank = true))
        assertTrue(DeckDialogTokens.canSubmitText("RPGs", requireNonBlank = true))
        assertEquals(1f, DeckDialogTokens.positiveButtonAlpha(true), 0.0001f)
        assertEquals(0.40f, DeckDialogTokens.positiveButtonAlpha(false), 0.0001f)
    }
}
