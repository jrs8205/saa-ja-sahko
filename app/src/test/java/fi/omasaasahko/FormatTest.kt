package fi.omasaasahko

import fi.omasaasahko.domain.AppLanguage
import fi.omasaasahko.domain.decimal
import fi.omasaasahko.domain.temperature
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun `temperature shows one decimal with a Finnish comma`() {
        assertEquals("12,3°", temperature(12.34, AppLanguage.FI))
        assertEquals("-3,3°", temperature(-3.25, AppLanguage.FI))
        assertEquals("-27,0°", temperature(-27.0, AppLanguage.FI))
        assertEquals("–", temperature(null, AppLanguage.FI))
    }

    @Test fun `temperature never shows a negative zero`() {
        assertEquals("0,0°", temperature(-0.04, AppLanguage.FI))
        assertEquals("0,0°", temperature(0.0, AppLanguage.FI))
        assertEquals("0", decimal(-0.4, AppLanguage.FI, 0))
    }
}
