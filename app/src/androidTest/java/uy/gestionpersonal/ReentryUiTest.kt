package uy.gestionpersonal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import uy.gestionpersonal.ui.GestionApp
import uy.gestionpersonal.ui.GestionTheme

@RunWith(AndroidJUnit4::class)
class ReentryUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editorOffersBothExportsAndNoRemovesTheExistingReentryDate() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("reentry", Empresa.RR, mapOf("nombres" to "Ana", "tieneReingreso" to "Sí", "reingreso" to "2026-10-20")))
        compose.setContent { GestionTheme { GestionApp(repository) } }
        compose.onNodeWithContentDescription("Ingresar a R&R").performScrollTo().performClick()
        compose.onAllNodes(hasText("Personal", substring = false) and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Ver ficha").performScrollTo().performClick()
        compose.onNodeWithText("¿Tiene fecha de reingreso?").performScrollTo().performClick()
        compose.onNodeWithText("No", useUnmergedTree = false).performClick()
        compose.onNodeWithContentDescription("Elegir Fecha de reingreso").assertDoesNotExist()
        compose.onNodeWithText("Guardar trabajador").performClick()
        compose.runOnIdle {
            val saved = repository.data(Empresa.RR).workers.single()
            assertEquals("No", saved.reentryAnswer)
            assertFalse(saved.fields.containsKey("reingreso"))
            assertTrue(repository.data(Empresa.TRANSEO).workers.isEmpty())
        }
        compose.onNodeWithText("Ver ficha").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exportar ficha").performClick()
        compose.onNodeWithText("Exportar ficha PDF").assertIsDisplayed()
        compose.onNodeWithText("Exportar ficha Excel").assertIsDisplayed()
    }
}
