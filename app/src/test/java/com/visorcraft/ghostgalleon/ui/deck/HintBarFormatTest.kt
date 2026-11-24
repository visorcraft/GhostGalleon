package com.visorcraft.ghostgalleon.ui.deck

import com.visorcraft.ghostgalleon.ui.deck.HintBarFormat.TokenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HintBarFormatTest {

    private fun extract(raw: String, kind: TokenKind): List<String> =
        HintBarFormat.tokenize(raw)
            .filter { it.kind == kind }
            .map { raw.substring(it.start, it.end) }

    @Test
    fun `default dual and single hints tokenize keycaps across all five locales`() {
        val samples = listOf(
            "A Launch · B Back · X Swap · Y Mode · Start Settings" to listOf("A", "B", "X", "Y", "Start"),
            "A Launch · B Back · Y Mode · Start Settings" to listOf("A", "B", "Y", "Start"),
            "A Starten · B Zurück · X Tauschen · Y Modus · Start Einstellungen" to listOf("A", "B", "X", "Y", "Start"),
            "A Lanzar · B Volver · X Intercambiar · Y Modo · Start Ajustes" to listOf("A", "B", "X", "Y", "Start"),
            "A Lancer · B Retour · X Échanger · Y Mode · Start Paramètres" to listOf("A", "B", "X", "Y", "Start"),
            "A เปิด · B กลับ · X สลับ · Y โหมด · Start การตั้งค่า" to listOf("A", "B", "X", "Y", "Start"),
        )
        for ((raw, expectedKeycaps) in samples) {
            assertEquals("keycaps for: $raw", expectedKeycaps, extract(raw, TokenKind.KEYCAP))
            assertEquals(
                "actions for: $raw",
                expectedKeycaps.size,
                extract(raw, TokenKind.ACTION).size,
            )
            assertEquals(
                "separators for: $raw",
                expectedKeycaps.size - 1,
                extract(raw, TokenKind.SEPARATOR).size,
            )
        }
    }

    @Test
    fun `dock hints highlight localized hold trigger across all five locales`() {
        val samples = listOf(
            "A Launch / Add · B Back · Hold Move / Remove" to listOf("A", "B", "Hold"),
            "A Starten / Hinzufügen · B Zurück · Halten Verschieben / Entfernen" to listOf("A", "B", "Halten"),
            "A Lanzar / Añadir · B Volver · Mantener Mover / Eliminar" to listOf("A", "B", "Mantener"),
            "A Lancer/Ajouter · B Retour · Maintenir Déplacer/Retirer" to listOf("A", "B", "Maintenir"),
            "A เปิด/เพิ่ม · B กลับ · กดค้าง ย้าย/ลบ" to listOf("A", "B", "กดค้าง"),
        )
        for ((raw, expectedKeycaps) in samples) {
            assertEquals("dock keycaps for: $raw", expectedKeycaps, extract(raw, TokenKind.KEYCAP))
            assertEquals(3, extract(raw, TokenKind.ACTION).size)
        }
    }

    @Test
    fun `move hints split mode prefix from button keycaps in every locale`() {
        val samples = listOf(
            "Moving - A drop · B cancel" to "Moving",
            "Verschieben - A Ablegen · B Abbrechen" to "Verschieben",
            "Moviendo - A soltar · B cancelar" to "Moviendo",
            "Déplacement - A Déposer · B Annuler" to "Déplacement",
            "ย้ายอยู่ - A วาง · B ยกเลิก" to "ย้ายอยู่",
        )
        for ((raw, expectedPrefix) in samples) {
            assertEquals(listOf(expectedPrefix), extract(raw, TokenKind.PREFIX))
            assertEquals(listOf("A", "B"), extract(raw, TokenKind.KEYCAP))
            assertEquals(2, extract(raw, TokenKind.ACTION).size)
            assertEquals(listOf("-", "·"), extract(raw, TokenKind.SEPARATOR))
        }
    }

    @Test
    fun `folder hints with standalone button segments do not misclassify actions as keycaps`() {
        val samples = listOf(
            "A · Launch  ·  Y / long-press · Remove  ·  B · Close" to listOf("A", "Y", "B"),
            "A · Starten · Y / Lang · Entfernen · B · Schließen" to listOf("A", "Y", "B"),
            "A · Lanzar · Y / mantener pulsado · Eliminar · B · Cerrar" to listOf("A", "Y", "B"),
            "A · Lancer  ·  Y/Appui long · Retirer  ·  B · Fermer" to listOf("A", "Y", "B"),
            "A เปิด · Y/กดค้าง ลบ · B ปิด" to listOf("A", "Y", "B"),
            "B · Close" to listOf("B"),
        )
        for ((raw, expectedKeycaps) in samples) {
            assertEquals("folder keycaps for: $raw", expectedKeycaps, extract(raw, TokenKind.KEYCAP))
        }
    }

    @Test
    fun `tokens are ordered, non-overlapping, and strictly within string bounds`() {
        val raw = "Moving - A drop · B cancel"
        val tokens = HintBarFormat.tokenize(raw)
        assertTrue(tokens.isNotEmpty())
        var prevEnd = 0
        for (t in tokens) {
            assertTrue("start >= prevEnd", t.start >= prevEnd)
            assertTrue("end > start", t.end > t.start)
            assertTrue("end <= length", t.end <= raw.length)
            prevEnd = t.end
        }
    }

    @Test
    fun `blank and degenerate strings return empty token lists safely`() {
        assertEquals(emptyList<HintBarFormat.Token>(), HintBarFormat.tokenize(""))
        assertEquals(emptyList<HintBarFormat.Token>(), HintBarFormat.tokenize("   "))
    }
}
