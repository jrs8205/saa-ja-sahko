package fi.omasaasahko

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Every character is drawn, nothing is ellipsized or clipped; optionally on a single line. */
internal fun SemanticsNodeInteraction.assertTextFits(singleLine: Boolean = true) {
    assertIsDisplayed()
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
    assertTrue("Text must expose its actual layout", layouts.isNotEmpty())
    layouts.forEach { layout ->
        val text = layout.layoutInput.text.text
        if (singleLine) assertEquals("Keep the text on one line: $text", 1, layout.lineCount)
        assertFalse("Ellipsized text: $text", layout.isLineEllipsized(0))
        assertEquals("All characters must be drawn: $text", text.length,
            layout.getLineEnd(layout.lineCount - 1, visibleEnd = true))
        // Compare the drawn line, not the paragraph's possibly wider cached constraints.
        for (line in 0 until layout.lineCount) {
            assertFalse("Ellipsized line: $text", layout.isLineEllipsized(line))
            assertTrue("Clipped right edge: $text (${layout.getLineRight(line)} > ${layout.size.width}, " +
                "scale=${layout.layoutInput.density.fontScale})", layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("Clipped left edge: $text", layout.getLineLeft(line) >= -1f)
        }
    }
}
