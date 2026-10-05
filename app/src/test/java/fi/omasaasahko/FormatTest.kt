package fi.omasaasahko

import fi.omasaasahko.domain.decimal
import fi.omasaasahko.domain.temperature
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `temperature shows one decimal with a Finnish comma`() {
        assertEquals("12,3°", temperature(12.34))
        assertEquals("-3,3°", temperature(-3.25))
        assertEquals("-27,0°", temperature(-27.0))
        assertEquals("–", temperature(null))
    }

    @Test fun `temperature never shows a negative zero`() {
        assertEquals("0,0°", temperature(-0.04))
        assertEquals("0,0°", temperature(0.0))
        assertEquals("0", decimal(-0.4, 0))
    }
}
