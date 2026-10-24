package uy.gestionpersonal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.MemoryRepository
import uy.gestionpersonal.ui.GestionApp
import uy.gestionpersonal.ui.GestionTheme

@RunWith(AndroidJUnit4::class)
class CompanyIsolationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun startApp() {
        val repository = MemoryRepository()
        compose.setContent { GestionTheme { GestionApp(repository) } }
    }

    private fun enterCompany(name: String) {
        compose.onNodeWithContentDescription("Ingresar a $name")
            .performScrollTo()
            .performClick()
        compose.onNodeWithText("Tu equipo, al día").assertIsDisplayed()
    }

    private fun openPersonal() {
        // Matches the responsive module chip/sidebar, excluding the static brand text.
        compose.onAllNodes(hasText("Personal", substring = false) and hasClickAction())
            .onFirst()
            .performClick()
        compose.onNodeWithText("Nuevo trabajador").assertExists()
    }

    private fun search(value: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasSetTextAction())
        compose.onNode(hasSetTextAction()).performTextReplacement(value)
    }

    private fun assertWorkerVisible(query: String, fullName: String) {
        search(query)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(fullName, substring = false))
        compose.onNodeWithText(fullName).assertIsDisplayed()
    }

    private fun assertNoMatchingWorker(query: String, fullName: String) {
        search(query)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("No encontramos coincidencias"))
        compose.onNodeWithText("No encontramos coincidencias").assertIsDisplayed()
        compose.onNodeWithText(fullName).assertDoesNotExist()
    }

    private fun returnToCompanySelector() {
        compose.onNodeWithText("Cambiar empresa").performClick()
        compose.onNodeWithText("Seleccioná una\nempresa").assertIsDisplayed()
        compose.onNodeWithText("Cambiar empresa").assertDoesNotExist()
        compose.onNodeWithText("Juan Pérez").assertDoesNotExist()
        compose.onNodeWithText("Carlos López").assertDoesNotExist()
        compose.onNodeWithText("Martín Rodríguez").assertDoesNotExist()
    }

    @Test fun initialScreenOffersBothCompaniesWithoutShowingCompanyRecords() {
        startApp()
        compose.onNodeWithText("Seleccioná una\nempresa").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ingresar a R&R").assertExists()
        compose.onNodeWithContentDescription("Ingresar a TRANSEO").assertExists()
        compose.onNodeWithText("Los registros de cada empresa se mantienen separados").assertExists()
        compose.onNodeWithText("Juan Pérez").assertDoesNotExist()
        compose.onNodeWithText("Martín Rodríguez").assertDoesNotExist()
    }

    @Test fun personalListsStaySeparateAndChangingCompanyResetsNavigationAndSearch() {
        startApp()
        enterCompany("R&R")
        openPersonal()
        assertWorkerVisible("Juan", "Juan Pérez")
        assertWorkerVisible("Carlos", "Carlos López")
        assertNoMatchingWorker("Martín", "Martín Rodríguez")

        returnToCompanySelector()
        enterCompany("TRANSEO")
        openPersonal()
        assertWorkerVisible("Martín", "Martín Rodríguez")
        assertNoMatchingWorker("Juan", "Juan Pérez")
        assertNoMatchingWorker("Carlos", "Carlos López")

        returnToCompanySelector()
        enterCompany("R&R")
        openPersonal()
        compose.onNodeWithText("2 de 2 trabajadores").assertExists()
        assertWorkerVisible("Juan", "Juan Pérez")
    }

    @Test fun savingWorkerWithAllOptionalFieldsBlankOnlyChangesSelectedCompany() {
        startApp()
        enterCompany("R&R")
        openPersonal()
        compose.onNodeWithText("Nuevo trabajador").performClick()
        compose.onNodeWithText("Guardar trabajador").performClick()
        compose.onNodeWithText("3 de 3 trabajadores").assertExists()

        returnToCompanySelector()
        enterCompany("TRANSEO")
        openPersonal()
        compose.onNodeWithText("1 de 1 trabajadores").assertExists()
        assertNoMatchingWorker("sin nombre", "Trabajador sin nombre")

        returnToCompanySelector()
        enterCompany("R&R")
        openPersonal()
        compose.onNodeWithText("3 de 3 trabajadores").assertExists()
        assertWorkerVisible("sin nombre", "Trabajador sin nombre")
    }
}
