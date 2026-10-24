package uy.gestionpersonal.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uy.gestionpersonal.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DeliveriesScreen(empresa: Empresa, repository: MemoryRepository, kind: DeliveryKind) {
    val context = LocalContext.current
    val data = repository.data(empresa)
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Delivery?>(null) }
    var deleting by remember { mutableStateOf<Delivery?>(null) }
    val title = if (kind == DeliveryKind.EPP) "Equipos de protección personal" else "Uniformes"
    val records = data.deliveries.filter { delivery ->
        delivery.kind == kind && (filter.isEmpty() || delivery.workerId == filter) &&
            listOf(delivery.item, delivery.size, delivery.date, data.workers.find { it.id == delivery.workerId }?.displayName.orEmpty()).any { it.contains(query, true) }
    }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(title, if (kind == DeliveryKind.EPP) "Entregas y constancias de recepción · ${empresa.displayName}" else "Talles y registro individual de cada entrega · ${empresa.displayName}")
        FilledTonalButton(onClick = { editing = null; dialog = true }) { Icon(Icons.Outlined.Add, null); Text("Registrar entrega", Modifier.padding(start = 6.dp)) }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar persona, artículo o fecha") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SafetySelect("Trabajador", filter, listOf("" to "Todos los trabajadores") + data.workers.map { it.id to it.displayName }) { filter = it }
        Text("${records.size} entregas", color = Muted, fontSize = 12.sp)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (records.isEmpty()) item { EmptySafety("Sin entregas para mostrar", "Registrá una entrega para comenzar el historial de ${empresa.displayName}.") }
            items(records, key = { it.id }) { record ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(record.item.ifBlank { "Artículo sin especificar" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(data.workers.find { it.id == record.workerId }?.displayName ?: "Sin trabajador asignado", color = Muted, modifier = Modifier.padding(top = 5.dp))
                        Text("${record.date.ifBlank { "Sin fecha" }} · Talle ${record.size.ifBlank { "—" }} · Cantidad ${record.quantity.ifBlank { "—" }}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(if (record.conformity) "✓ Recepción en conformidad registrada" else "Recepción pendiente de conformidad", color = if (record.conformity) Teal else MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        if (record.notes.isNotBlank()) Text(record.notes, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                        if (record.receiptName.isNotBlank()) Text("Constancia: ${record.receiptName}", color = Teal, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { editing = record; dialog = true }) { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)); Text("Editar", Modifier.padding(start = 5.dp)) }
                            if (record.receiptUri.isNotBlank()) TextButton(onClick = {
                                runCatching {
                                    val uri = Uri.parse(record.receiptUri)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/octet-stream").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Abrir constancia"))
                                }.onFailure { android.widget.Toast.makeText(context, "No se pudo abrir la constancia. Volvé a adjuntarla.", android.widget.Toast.LENGTH_LONG).show() }
                            }) { Text("Constancia") }
                            TextButton(onClick = { deleting = record }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (dialog) DeliveryForm(empresa, kind, data.workers, editing, onDismiss = { dialog = false }) { repository.saveDelivery(it); dialog = false }
    deleting?.let { value -> ConfirmDelete("¿Eliminar esta entrega?") { confirmed -> if (confirmed) repository.deleteDelivery(empresa, value.id); deleting = null } }
}

@Composable
private fun DeliveryForm(empresa: Empresa, kind: DeliveryKind, workers: List<Worker>, existing: Delivery?, onDismiss: () -> Unit, onSave: (Delivery) -> Unit) {
    val context = LocalContext.current
    var worker by remember { mutableStateOf(existing?.workerId.orEmpty()) }
    var item by remember { mutableStateOf(existing?.item.orEmpty()) }
    var size by remember { mutableStateOf(existing?.size.orEmpty()) }
    var date by remember { mutableStateOf(existing?.date.orEmpty()) }
    var quantity by remember { mutableStateOf(existing?.quantity.orEmpty()) }
    var conformity by remember { mutableStateOf(existing?.conformity ?: false) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var receiptName by remember { mutableStateOf(existing?.receiptName.orEmpty()) }
    var receiptUri by remember { mutableStateOf(existing?.receiptUri.orEmpty()) }
    var fileError by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                receiptName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: "Constancia adjunta"
                receiptUri = uri.toString(); fileError = ""
            }.onFailure { fileError = "No se pudo leer el documento. Probá con otro archivo." }
        }
    }
    SafetyFormDialog(if (existing == null) "Registrar entrega" else "Editar entrega", onDismiss, onSave = {
        onSave(Delivery(id = existing?.id ?: newId(), empresa = empresa, workerId = worker, kind = kind, item = item, size = size, date = date, quantity = quantity, conformity = conformity, notes = notes, receiptName = receiptName, receiptUri = receiptUri))
    }) {
        Text("${empresa.displayName} · ${if (kind == DeliveryKind.EPP) "EPP" else "Uniformes"}", color = Teal, fontWeight = FontWeight.SemiBold)
        Text("Todos los campos son opcionales. Podés completar la información después.", fontSize = 12.sp, color = Muted)
        SafetySelect("Trabajador", worker, listOf("" to "Sin asignar") + workers.map { it.id to it.displayName }) { worker = it }
        OutlinedTextField(item, { item = it }, label = { Text(if (kind == DeliveryKind.EPP) "Equipo / elemento" else "Prenda") }, modifier = Modifier.fillMaxWidth())
        val options = if (kind == DeliveryKind.EPP) listOf("Casco", "Lentes / Antiparras", "Guantes", "Arnés", "Protección auditiva", "Calzado de seguridad") else listOf("Remera", "Camisa", "Pantalón", "Buzo", "Campera", "Calzado de seguridad")
        SafetySelect("Opciones habituales", "", listOf("" to "Elegir o escribir arriba") + options.map { it to it }) { if (it.isNotBlank()) item = it }
        OutlinedTextField(size, { size = it }, label = { Text("Talle / medida") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(quantity, { quantity = it }, label = { Text("Cantidad") }, modifier = Modifier.fillMaxWidth())
        AppDateField("Fecha de entrega", date) { date = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(conformity, { conformity = it }); Text("Recepción en conformidad", fontSize = 14.sp) }
        Text("Marcá esta opción cuando cuentes con la conformidad del trabajador. Podés adjuntar la constancia firmada.", color = Muted, fontSize = 12.sp)
        OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }) { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Text(if (receiptName.isBlank()) "Adjuntar constancia" else "Reemplazar constancia", Modifier.padding(start = 6.dp)) }
        if (receiptName.isNotBlank()) { Text(receiptName, color = Teal, fontSize = 12.sp); TextButton(onClick = { receiptName = ""; receiptUri = "" }) { Text("Quitar adjunto") } }
        if (fileError.isNotBlank()) Text(fileError, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
fun TrainingsScreen(empresa: Empresa, repository: MemoryRepository) {
    val data = repository.data(empresa)
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("") }
    var workerFilter by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Training?>(null) }
    var deleting by remember { mutableStateOf<Training?>(null) }
    val today = LocalDate.now()
    val records = data.trainings.filter { training ->
        (statusFilter.isBlank() || training.status(today).name == statusFilter) && (workerFilter.isBlank() || training.workerId == workerFilter) &&
            listOf(training.title, data.workers.find { it.id == training.workerId }?.displayName.orEmpty()).any { it.contains(query, true) }
    }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading("Capacitaciones", "Historial de charlas y vigencias · ${empresa.displayName}")
        FilledTonalButton(onClick = { editing = null; dialog = true }) { Icon(Icons.Outlined.Add, null); Text("Registrar capacitación", Modifier.padding(start = 6.dp)) }
        Text("Por vencer: hasta 30 días. Las charlas futuras figuran como programadas.", color = Muted, fontSize = 11.sp)
        OutlinedTextField(query, { query = it }, label = { Text("Buscar charla o trabajador") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { SafetySelect("Estado", statusFilter, listOf("" to "Todos los estados") + TrainingStatus.entries.map { it.name to statusLabel(it) }) { statusFilter = it } }
            Box(Modifier.weight(1f)) { SafetySelect("Trabajador", workerFilter, listOf("" to "Todos") + data.workers.map { it.id to it.displayName }) { workerFilter = it } }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (records.isEmpty()) item { EmptySafety("Sin capacitaciones para mostrar", "Registrá una charla con un título existente o personalizado.") }
            items(records, key = { it.id }) { record ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        TrainingBadge(record.status(today))
                        Text(record.title.ifBlank { "Capacitación sin título" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                        Text(data.workers.find { it.id == record.workerId }?.displayName ?: "Sin trabajador asignado", color = Muted, modifier = Modifier.padding(top = 6.dp))
                        Text("Dictada: ${record.date.ifBlank { "Sin fecha" }}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        Text("Vigencia: ${record.validityMonths?.let { "$it meses" } ?: "Sin vencimiento"} · Vence: ${record.expiryDate()?.toString() ?: "—"}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        if (record.notes.isNotBlank()) Text(record.notes, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 6.dp))
                        Row {
                            TextButton(onClick = { editing = record; dialog = true }) { Text("Editar") }
                            TextButton(onClick = { deleting = record }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (dialog) TrainingForm(empresa, data.workers, editing, { dialog = false }) { repository.saveTraining(it); dialog = false }
    deleting?.let { value -> ConfirmDelete("¿Eliminar esta capacitación?") { confirmed -> if (confirmed) repository.deleteTraining(empresa, value.id); deleting = null } }
}

@Composable
private fun TrainingForm(empresa: Empresa, workers: List<Worker>, existing: Training?, onDismiss: () -> Unit, onSave: (Training) -> Unit) {
    var worker by remember { mutableStateOf(existing?.workerId.orEmpty()) }
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var date by remember { mutableStateOf(existing?.date.orEmpty()) }
    var validity by remember { mutableStateOf(existing?.validityMonths?.toString().orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    SafetyFormDialog(if (existing == null) "Registrar capacitación" else "Editar capacitación", onDismiss, onSave = {
        onSave(Training(id = existing?.id ?: newId(), empresa = empresa, workerId = worker, title = title, date = date, validityMonths = validity.toIntOrNull(), notes = notes))
    }) {
        Text(empresa.displayName, color = Teal, fontWeight = FontWeight.SemiBold)
        Text("Todos los campos son opcionales. Las alertas se calculan a partir de la fecha de dictado.", fontSize = 12.sp, color = Muted)
        SafetySelect("Trabajador", worker, listOf("" to "Sin asignar") + workers.map { it.id to it.displayName }) { worker = it }
        OutlinedTextField(title, { title = it }, label = { Text("Nombre de la charla (editable)") }, modifier = Modifier.fillMaxWidth())
        SafetySelect("Títulos habituales", "", listOf("" to "Elegir o escribir arriba", "Inducción Decreto 125/014" to "Inducción Decreto 125/014", "Trabajo en Altura" to "Trabajo en Altura", "Riesgo Eléctrico" to "Riesgo Eléctrico")) { if (it.isNotBlank()) title = it }
        AppDateField("Fecha de dictado", date) { date = it }
        SafetySelect("Plazo de vigencia", validity, listOf("" to "Sin vencimiento", "6" to "6 meses", "12" to "1 año", "24" to "2 años")) { validity = it }
        OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    }
}

@Composable
fun ScreenHeading(title: String, subtitle: String) {
    Column { Text(title, fontWeight = FontWeight.Bold, fontSize = 25.sp); Text(subtitle, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
fun SafetySelect(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp), shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(label, fontSize = 10.sp, color = Muted); Text(options.find { it.first == value }?.second ?: value.ifBlank { "Sin especificar" }, fontSize = 13.sp, color = Navy) }
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 330.dp)) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option.second) }, onClick = { onChange(option.first); expanded = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDateField(label: String, value: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val millis = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    val state = rememberDatePickerState(initialSelectedDateMillis = millis)
    Column {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(label, color = Muted, fontSize = 10.sp); Text(value.ifBlank { "Sin fecha" }, color = Navy, fontSize = 14.sp) }
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(19.dp))
        }
        if (value.isNotBlank()) TextButton(onClick = { onChange("") }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Quitar fecha", fontSize = 11.sp) }
    }
    if (open) DatePickerDialog(onDismissRequest = { open = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; open = false }) { Text("Aceptar") } }, dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } }) { DatePicker(state = state) }
}

@Composable
private fun SafetyFormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth(.96f).fillMaxHeight(.93f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Cerrar") } }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDismiss) { Text("Cancelar") }; Button(onClick = onSave) { Text("Guardar") } }
            }
        }
    }
}

@Composable
private fun ConfirmDelete(title: String, onResult: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = { onResult(false) }, title = { Text(title) }, text = { Text("El registro se quitará de esta empresa.") }, confirmButton = { TextButton(onClick = { onResult(true) }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { onResult(false) }) { Text("Cancelar") } })
}

@Composable
private fun EmptySafety(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.Inventory2, null, tint = Muted, modifier = Modifier.size(42.dp)); Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)); Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 8.dp)) }
}

fun statusLabel(status: TrainingStatus): String = when (status) { TrainingStatus.VIGENTE -> "Vigente"; TrainingStatus.POR_VENCER -> "Por vencer"; TrainingStatus.VENCIDA -> "Vencida"; TrainingStatus.SIN_FECHA -> "Sin fecha"; TrainingStatus.PROGRAMADA -> "Programada" }

@Composable
fun TrainingBadge(status: TrainingStatus) {
    val color = when (status) { TrainingStatus.VIGENTE -> Teal; TrainingStatus.POR_VENCER -> androidx.compose.ui.graphics.Color(0xFF9B6500); TrainingStatus.VENCIDA -> MaterialTheme.colorScheme.error; TrainingStatus.SIN_FECHA -> Muted; TrainingStatus.PROGRAMADA -> androidx.compose.ui.graphics.Color(0xFF3568AD) }
    Surface(color = color.copy(alpha = .09f), shape = MaterialTheme.shapes.small) { Text("● ${statusLabel(status)}", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
}
