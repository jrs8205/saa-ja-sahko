package fi.omasaasahko

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fi.omasaasahko.ui.AppTheme
import fi.omasaasahko.ui.BodyFont
import fi.omasaasahko.ui.DisplayFont
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "fi-rFI-w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `expressive theme uses bundled fonts and defines navigation tokens`() {
        lateinit var typography: Typography
        lateinit var light: ColorScheme
        lateinit var dark: ColorScheme
        compose.setContent {
            AppTheme(dark = false, dynamic = false) { typography = MaterialTheme.typography; light = MaterialTheme.colorScheme }
            AppTheme(dark = true, dynamic = false) { dark = MaterialTheme.colorScheme }
        }
        compose.runOnIdle {
            assertEquals(DisplayFont, typography.displayLarge.fontFamily)
            assertEquals(DisplayFont, typography.titleLarge.fontFamily)
            assertEquals(BodyFont, typography.bodyMedium.fontFamily)
            assertEquals(BodyFont, typography.labelSmall.fontFamily)
            assertEquals(92.sp, typography.displayLarge.fontSize)
            assertEquals(80.sp, typography.displayMedium.fontSize)
            assertEquals(FontWeight.ExtraBold, typography.headlineLarge.fontWeight)
            assertEquals(Color(0xFF18221D), light.inverseSurface)
            assertEquals(Color(0xFFE0EBE1), dark.inverseSurface)
            assertEquals(Color(0xFF313D34), dark.surfaceContainerHighest)
        }
    }
}
