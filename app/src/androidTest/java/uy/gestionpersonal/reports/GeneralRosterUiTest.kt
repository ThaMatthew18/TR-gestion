package uy.gestionpersonal.reports

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import uy.gestionpersonal.ui.GestionTheme
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class GeneralRosterUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun scopeSelectorOffersEachCompanyAndCombinedAndUpdatesTheCount() {
        val repository = MemoryRepository()
        compose.setContent { GestionTheme { Column(Modifier.verticalScroll(rememberScrollState())) { GeneralRosterSection(Empresa.RR, repository) } } }
        compose.onNodeWithTag("roster-summary").assertTextEquals("2 empleados · Sin columna Empresa")
        compose.onNodeWithTag("roster-scope").performScrollTo().performClick()
        compose.onNodeWithText("Ambas empresas").performClick()
        compose.onNodeWithTag("roster-summary").assertTextEquals("3 empleados · Incluye columna Empresa")
        compose.onNodeWithTag("roster-scope").performClick()
        compose.onNodeWithText("TRANSEO").performClick()
        compose.onNodeWithTag("roster-summary").assertTextEquals("1 empleado · Sin columna Empresa")
        compose.onNodeWithText("Descargar ficha general Excel").performScrollTo().assertIsEnabled()
    }

    @Test fun generateRealWorkbooksForEveryScope() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("rr-example", Empresa.RR, mapOf("cedula" to "00123456", "nombres" to "Ana María", "apellidos" to "Pérez", "proyecto" to "Obra Sur", "categoria" to "Oficial", "alta" to "2026-01-01", "baja" to "2026-05-01", "aportacion" to "Aportación 4")).withReentries(listOf(Reentry("r1", "2026-06-01", "2026-07-01"), Reentry("r2", "2026-09-01"))))
        repository.saveWorker(Worker("tr-example", Empresa.TRANSEO, mapOf("cedula" to "87654321", "nombres" to "Luis", "apellidos" to "García", "proyecto" to "Obra Norte", "categoria" to "Peón", "alta" to "2026-02-01", "baja" to "2026-10-01", "aportacion" to "Industria y Comercio Común")))
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "qa").apply { mkdirs() }
        RosterScope.entries.forEach { option -> File(directory, "general-${option.filename}.xlsx").outputStream().use { writeGeneralRosterExcel(it, option, repository.snapshot(), LocalDate.of(2026, 10, 29)) } }
    }
}
