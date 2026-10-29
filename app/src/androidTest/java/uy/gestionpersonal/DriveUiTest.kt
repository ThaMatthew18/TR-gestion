package uy.gestionpersonal

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import uy.gestionpersonal.drive.DriveController
import uy.gestionpersonal.ui.GestionApp
import uy.gestionpersonal.ui.GestionTheme

@RunWith(AndroidJUnit4::class)
class DriveUiTest {
    @get:Rule val compose = createComposeRule()
    private val controller = DriveController(InstrumentationRegistry.getInstrumentation().targetContext, "qa-drive-no-account")
    @After fun close() { controller.close() }

    @Test fun accountIsChosenInsideTheAppAndCloudActionsRequireAuthorization() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("worker", Empresa.RR, attachments = listOf(Attachment("a", "cedula_frente", "Prueba.pdf", "content://qa/document"))))
        repository.saveWorker(Worker("foreign", Empresa.TRANSEO, attachments = listOf(Attachment("b", "cedula_frente", "Otro.pdf", "content://qa/foreign"))))
        compose.setContent { GestionTheme { GestionApp(repository, controller) } }
        compose.onNodeWithContentDescription("Ingresar a R&R").performScrollTo().performClick()
        compose.onAllNodes(hasText("Google Drive", substring = false) and hasClickAction()).onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Conectar Google Drive").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("1 documentos adjuntos de R&R").assertIsDisplayed()
        compose.onNodeWithText("Sincronizar documentos").assertIsNotEnabled()
        compose.onNode(isToggleable()).assertIsOff().assertIsNotEnabled()
    }
}
