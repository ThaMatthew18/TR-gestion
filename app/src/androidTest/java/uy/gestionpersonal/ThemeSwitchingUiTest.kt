package uy.gestionpersonal

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import uy.gestionpersonal.ui.*

@RunWith(AndroidJUnit4::class)
class ThemeSwitchingUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingThemesKeepsTheCompanyAndItsPeople() {
        val repository = MemoryRepository()
        compose.setContent {
            var mode by remember { mutableStateOf(ThemeMode.AMOLED) }
            GestionTheme(mode, { mode = it }) { GestionApp(repository) }
        }
        fun selectTheme(label: String) {
            compose.onNodeWithContentDescription("Cambiar tema").performClick()
            compose.onNodeWithText(label).performClick()
            compose.waitForIdle()
        }
        fun assertCanvas(color: Color) {
            val pixel = compose.onRoot().captureToImage().toPixelMap()[1, 1]
            assertEquals(color.toArgb(), pixel.toArgb())
        }
        assertCanvas(Color.Black)
        selectTheme("Claro")
        assertCanvas(Color(0xFFF4F6F8))
        compose.onNodeWithContentDescription("Ingresar a R&R").performScrollTo().performClick()
        compose.onAllNodes(hasText("Personal", substring = false) and hasClickAction()).onFirst().performClick()
        selectTheme("AMOLED")
        assertCanvas(Color.Black)
        compose.onNodeWithText("2 de 2 trabajadores").assertExists()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Juan Pérez"))
        compose.onNodeWithText("Juan Pérez").assertIsDisplayed()
        assertEquals(2, repository.data(Empresa.RR).workers.size)
        assertEquals(1, repository.data(Empresa.TRANSEO).workers.size)
        compose.onNodeWithText("Cambiar empresa").performClick()
        assertCanvas(Color.Black)
        selectTheme("Sistema")
        compose.onNodeWithContentDescription("Cambiar tema").performClick()
        compose.onNodeWithText("Sistema").assertIsSelected()
    }
}
