package uy.gestionpersonal.reports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.gestionpersonal.data.*
import java.time.LocalDate

private data class PendingExcel(val empresa: Empresa, val source: CompanyData, val filters: ReportFilters)
private data class PendingPdf(val worker: Worker, val source: CompanyData)

@Composable
fun ReportesScreen(empresa: Empresa, repository: MemoryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val individualExporter = rememberWorkerExporter(empresa, repository)
    val source = repository.data(empresa)
    val workers = source.workers.filter { it.empresa == empresa }
    var project by remember(empresa) { mutableStateOf("") }
    var contribution by remember(empresa) { mutableStateOf("") }
    var garment by remember(empresa) { mutableStateOf("") }
    var trainingStatus by remember(empresa) { mutableStateOf<TrainingStatus?>(null) }
    var selectedWorkerId by remember(empresa) { mutableStateOf("") }
    var pendingExcel by remember { mutableStateOf<PendingExcel?>(null) }
    var pendingPdf by remember { mutableStateOf<PendingPdf?>(null) }
    var busy by remember { mutableStateOf(false) }
    var feedback by remember(empresa) { mutableStateOf("") }
    var hasError by remember(empresa) { mutableStateOf(false) }
    val filters = ReportFilters(project, contribution, garment, trainingStatus)
    val selected = selectReportData(empresa, source, filters)
    val selectedWorker = workers.find { it.id == selectedWorkerId }

    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        val export = pendingExcel
        pendingExcel = null
        if (uri == null) {
            feedback = "Exportación cancelada."
            hasError = false
        } else if (export != null) {
            scope.launch {
                busy = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "w") ?: error("No se pudo abrir el destino.")
                        output.use { writeCompanyExcel(it, export.empresa, export.source, export.filters) }
                    }
                }
                busy = false
                hasError = result.isFailure
                feedback = if (result.isSuccess) "Excel de ${export.empresa.displayName} guardado correctamente." else "No se pudo guardar el Excel: ${result.exceptionOrNull()?.localizedMessage.orEmpty()}"
            }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val export = pendingPdf
        pendingPdf = null
        if (uri == null) {
            feedback = "Exportación cancelada."
            hasError = false
        } else if (export != null) {
            scope.launch {
                busy = true
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "w") ?: error("No se pudo abrir el destino.")
                        output.use { exportWorkerPdf(context, it, export.worker, export.source) }
                    }
                }
                busy = false
                hasError = result.isFailure
                feedback = if (result.isSuccess) "Ficha PDF guardada correctamente." else "No se pudo guardar el PDF: ${result.exceptionOrNull()?.localizedMessage.orEmpty()}"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Reportes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Información de ${empresa.displayName} lista para compartir.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        GeneralRosterSection(empresa, repository)
        HorizontalDivider()

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Consolidado de personal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${selected.workers.size} trabajadores · ${selected.deliveries.size} entregas · ${selected.trainings.size} capacitaciones")
                Text("La exportación incluye únicamente registros de ${empresa.displayName}.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("Filtrar Excel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        FilterMenu("Proyecto / obra", project, workers.mapNotNull { it.fields["proyecto"] }.filter(String::isNotBlank).distinct().sorted(), !busy) { project = it }
        FilterMenu("Tipo de aportación", contribution, workers.mapNotNull { it.fields["aportacion"] }.filter(String::isNotBlank).distinct().sorted(), !busy) { contribution = it }
        FilterMenu("Indumentaria / EPP", garment, source.deliveries.filter { it.empresa == empresa }.map { it.item }.filter(String::isNotBlank).distinct().sorted(), !busy) { garment = it }
        FilterMenu("Estado de capacitación", trainingStatus?.let(::reportStatusLabel).orEmpty(), TrainingStatus.entries.map(::reportStatusLabel), !busy) { label ->
            trainingStatus = TrainingStatus.entries.find { reportStatusLabel(it) == label }
        }
        Text("Los filtros se combinan. Indumentaria y estado seleccionan trabajadores y filas coincidentes; los seis bloques del legajo se incluyen completos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { project = ""; contribution = ""; garment = ""; trainingStatus = null }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Limpiar") }
            Button(onClick = {
                feedback = ""
                pendingExcel = PendingExcel(empresa, source, filters)
                excelLauncher.launch("Personal_${empresa.name}_${LocalDate.now()}.xlsx")
            }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Guardar Excel") }
        }
        Text("Archivo .xlsx con hojas de resumen, personal, entregas, capacitaciones y reingresos, filtros de columna y encabezados fijos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("Ficha individual", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Seleccioná cualquier trabajador de ${empresa.displayName}. También podés generar una ficha con datos incompletos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        WorkerReportMenu(workers, selectedWorkerId, !busy && !individualExporter.busy) { selectedWorkerId = it }
        Button(onClick = {
            selectedWorker?.let { worker ->
                feedback = ""
                pendingPdf = PendingPdf(worker, source)
                pdfLauncher.launch("Legajo_${empresa.name}_${worker.id.take(24)}.pdf")
            }
        }, enabled = !busy && !individualExporter.busy && selectedWorker != null, modifier = Modifier.fillMaxWidth()) { Text("Guardar ficha PDF") }
        OutlinedButton(onClick = { selectedWorker?.let { individualExporter.export(it, WorkerExportFormat.EXCEL) } }, enabled = !busy && !individualExporter.busy && selectedWorker != null, modifier = Modifier.fillMaxWidth()) { Text("Guardar ficha Excel") }
        Text("Resumen de los seis bloques, referencias y hasta tres miniaturas. El historial de reingresos agrega un anexo cuando necesita más espacio o contiene observaciones. Los otros históricos se resumen; Excel conserva los datos completos y los archivos originales se mantienen por separado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text("El Excel individual incluye una ficha por bloques, adjuntos y los históricos completos del trabajador seleccionado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { selectedWorker?.let { individualExporter.export(it, WorkerExportFormat.COMPLETE_PDF) } }, enabled = !busy && !individualExporter.busy && selectedWorker != null, modifier = Modifier.fillMaxWidth()) { Text("PDF completo con documentos") }
        OutlinedButton(onClick = { selectedWorker?.let { individualExporter.export(it, WorkerExportFormat.PACKAGE) } }, enabled = !busy && !individualExporter.busy && selectedWorker != null, modifier = Modifier.fillMaxWidth()) { Text("ZIP con archivos originales") }
        if (busy || individualExporter.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (feedback.isNotBlank()) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (hasError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
                Text(feedback, Modifier.padding(16.dp), color = if (hasError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilterMenu(label: String, selected: String, options: List<String>, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(15.dp)) {
                Text(selected.ifBlank { "Todos" }, Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 330.dp)) {
                DropdownMenuItem(text = { Text("Todos") }, onClick = { onChange(""); expanded = false })
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
            }
        }
    }
}

@Composable
private fun WorkerReportMenu(workers: List<Worker>, selectedId: String, enabled: Boolean, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && workers.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), contentPadding = PaddingValues(15.dp)) {
            Text(workers.find { it.id == selectedId }?.displayName ?: if (workers.isEmpty()) "No hay trabajadores registrados" else "Seleccionar trabajador", Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 330.dp)) {
            workers.forEach { worker ->
                DropdownMenuItem(text = { Text("${worker.displayName} · ${worker.id.takeLast(6)}") }, onClick = { onChange(worker.id); expanded = false })
            }
        }
    }
}
