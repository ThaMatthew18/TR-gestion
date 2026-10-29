package uy.gestionpersonal.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import uy.gestionpersonal.data.*
import java.time.LocalDate

private data class RosterRequest(val scope: RosterScope, val snapshot: Map<Empresa, CompanyData>, val date: LocalDate)

@Composable
fun GeneralRosterSection(company: Empresa, repository: MemoryRepository) {
    val context = LocalContext.current
    val coroutine = rememberCoroutineScope()
    var selected by remember(company) { mutableStateOf(if (company == Empresa.RR) RosterScope.RR else RosterScope.TRANSEO) }
    var expanded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<RosterRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember(company) { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val count = selected.companies.sumOf { c -> repository.data(c).workers.count { it.empresa == c } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val request = pending; pending = null
        if (uri != null && request == null) { feedback = "La exportación se interrumpió. Volvé a descargar la ficha general."; failed = true }
        if (uri != null && request != null) coroutine.launch {
            busy = true; feedback = ""
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w") ?: error("No se pudo abrir el destino")
                    output.use { writeGeneralRosterExcel(it, request.scope, request.snapshot, request.date) }
                }
                failed = false; feedback = "Ficha general de ${request.scope.label} guardada correctamente."
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                failed = true; feedback = "No se pudo guardar la ficha general: ${failure.localizedMessage}"
            } finally { busy = false }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Ficha general de empleados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Descargá una planilla de R&R, TRANSEO o ambas empresas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Empresa de la planilla", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("roster-scope")) { Text(selected.label, Modifier.weight(1f)); Text("▾") }
                DropdownMenu(expanded, { expanded = false }) {
                    RosterScope.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { selected = option; expanded = false; feedback = "" }) }
                }
            }
            Text("$count ${if (count == 1) "empleado" else "empleados"} · ${if (selected == RosterScope.BOTH) "Incluye columna Empresa" else "Sin columna Empresa"}", modifier = Modifier.testTag("roster-summary"), style = MaterialTheme.typography.bodyMedium)
            Text("CI · Nombre · Apellido · Obra · Categoría · Ingreso · Egreso · Tipo de aportación", style = MaterialTheme.typography.bodySmall)
            Text("Incluye activos y bajas. Ingreso y egreso corresponden al último período iniciado. Los campos sin completar quedan vacíos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = {
                val date = LocalDate.now()
                pending = RosterRequest(selected, repository.snapshot(), date)
                launcher.launch("Ficha_general_${selected.filename}_$date.xlsx")
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Descargar ficha general Excel") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (feedback.isNotBlank()) Text(feedback, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
    }
}
