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

    @Test fun editorAddsSeveralPeriodsAndPreservesTheExistingWorkerAndLegacyDate() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("reentry", Empresa.RR, mapOf("nombres" to "Ana", "tieneReingreso" to "Sí", "reingreso" to "2026-10-20")))
        compose.setContent { GestionTheme { GestionApp(repository) } }
        compose.onNodeWithContentDescription("Ingresar a R&R").performScrollTo().performClick()
        compose.onAllNodes(hasText("Personal", substring = false) and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Ver ficha").performScrollTo().performClick()
        compose.onNodeWithText("Agregar reingreso").performScrollTo().performClick()
        compose.onNodeWithText("Agregar reingreso").performScrollTo().performClick()
        compose.onNodeWithText("Observaciones del reingreso 3").performScrollTo().performTextInput("Tercer período de prueba")
        compose.onNodeWithText("Guardar trabajador").performClick()
        compose.runOnIdle {
            val saved = repository.data(Empresa.RR).workers.single()
            assertEquals("Sí", saved.reentryAnswer)
            assertEquals("reentry", saved.id)
            assertEquals(3, saved.reentryHistory.size)
            assertEquals("2026-10-20", saved.reentryHistory.first().date)
            assertEquals("Tercer período de prueba", saved.reentryHistory.last().notes)
            assertFalse(saved.fields.containsKey("reingreso"))
            assertTrue(repository.data(Empresa.TRANSEO).workers.isEmpty())
        }
        compose.onNodeWithText("Ver ficha").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exportar ficha").performClick()
        compose.onNodeWithText("Exportar ficha PDF").assertIsDisplayed()
        compose.onNodeWithText("Exportar ficha Excel").assertIsDisplayed()
    }

    @Test fun removingOnePeriodRequiresConfirmationAndKeepsTheOtherPeriodAndPerson() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("periods", Empresa.RR, mapOf("nombres" to "Ana"), reentries = listOf(Reentry("one", "2026-01-01"), Reentry("two", "2026-06-01"))))
        compose.setContent { GestionTheme { GestionApp(repository) } }
        compose.onNodeWithContentDescription("Ingresar a R&R").performScrollTo().performClick()
        compose.onAllNodes(hasText("Personal", substring = false) and hasClickAction()).onFirst().performClick()
        compose.onNodeWithText("Ver ficha").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Quitar reingreso 1").performScrollTo().performClick()
        compose.onNodeWithText("¿Quitar este reingreso?").assertIsDisplayed()
        compose.onNodeWithText("Quitar reingreso").performClick()
        compose.onNodeWithText("Guardar trabajador").performClick()
        compose.runOnIdle {
            val saved = repository.data(Empresa.RR).workers.single()
            assertEquals("periods", saved.id)
            assertEquals(listOf("two"), saved.reentryHistory.map { it.id })
        }
    }
}
