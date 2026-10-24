@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package uy.gestionpersonal.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uy.gestionpersonal.data.Attachment
import uy.gestionpersonal.data.Empresa
import uy.gestionpersonal.data.MemoryRepository
import uy.gestionpersonal.data.UruguayGeography
import uy.gestionpersonal.data.Worker
import uy.gestionpersonal.data.newId
import uy.gestionpersonal.reports.exportWorkerPdf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val WorkerNavy = Color(0xFF172E45)
private val WorkerTeal = Color(0xFF047F80)
private val WorkerMuted = Color(0xFF667789)
private val WorkerLine = Color(0xFFE4EAF0)

@Composable
fun PersonalScreen(empresa: Empresa, repository: MemoryRepository) {
    val workers = repository.data(empresa).workers
    var search by remember(empresa) { mutableStateOf("") }
    var status by remember(empresa) { mutableStateOf("Todos") }
    var editor by remember(empresa) { mutableStateOf<Worker?>(null) }
    var deleting by remember(empresa) { mutableStateOf<Worker?>(null) }
    val exportPdf = rememberWorkerPdfExport(empresa, repository)
    val query = search.trim()
    val visible = workers.filter {
        val active = isWorkerActive(it)
        (status == "Todos" || (status == "Activos" && active) || (status == "Con baja" && !active)) &&
            (query.isBlank() || it.displayName.contains(query, true) || (it.fields.values + it.id).any { value -> value.contains(query, true) })
    }.sortedBy { it.displayName.lowercase() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            WorkerPageHeading("Personal", "Administrá el equipo de ${empresa.displayName}.", "Nuevo trabajador") {
                editor = Worker(id = newId(), empresa = empresa, fields = emptyMap(), attachments = emptyList())
            }
        }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar por nombre, cédula, proyecto o teléfono") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                        trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Close, "Limpiar búsqueda") } },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("Todos", "Activos", "Con baja").forEach { value ->
                            FilterChip(selected = status == value, onClick = { status = value }, label = { Text(value) })
                        }
                    }
                    Text("${visible.size} de ${workers.size} trabajadores", style = MaterialTheme.typography.labelMedium, color = WorkerMuted)
                }
            }
        }
        if (visible.isEmpty()) {
            item { WorkersEmptyState(if (workers.isEmpty()) "Tu equipo empieza acá" else "No encontramos coincidencias", if (workers.isEmpty()) "Agregá un trabajador. Todos los campos son opcionales." else "Probá con otra búsqueda o cambiá el filtro.") }
        } else {
            items(visible, key = { it.id }) { worker ->
                WorkerCard(worker, onEdit = { editor = worker }, onDelete = { deleting = worker }, onPdf = { exportPdf(worker) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    editor?.let { worker ->
        WorkerEditorDialog(worker, onDismiss = { editor = null }, onSave = { repository.saveWorker(it); editor = null })
    }
    deleting?.let { worker ->
        AlertDialog(
            onDismissRequest = { deleting = null }, icon = { Icon(Icons.Outlined.DeleteOutline, null) },
            title = { Text("Eliminar trabajador") },
            text = { Text("Se eliminará a ${worker.displayName}, sus documentos y los registros de entregas y capacitaciones de ${empresa.displayName}. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { repository.deleteWorker(empresa, worker.id); deleting = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun WorkerCard(worker: Worker, onEdit: () -> Unit, onDelete: () -> Unit, onPdf: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkerLine)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkerAvatar(worker)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(worker.displayName, fontWeight = FontWeight.SemiBold, color = WorkerNavy, style = MaterialTheme.typography.titleMedium)
                    Text(worker.fields["categoria"].orEmpty().ifBlank { "Categoría sin completar" }, color = WorkerMuted, style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Opciones de ${worker.displayName}", tint = WorkerMuted) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Editar ficha") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Descargar ficha PDF") }, leadingIcon = { Icon(Icons.Outlined.Download, null) }, onClick = { menu = false; onPdf() })
                        DropdownMenuItem(text = { Text("Eliminar trabajador", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            HorizontalDivider(color = WorkerLine)
            BoxWithConstraints {
                if (maxWidth > 600.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        WorkerDetail("CÉDULA", worker.fields["cedula"], Modifier.weight(1f))
                        WorkerDetail("PROYECTO / OBRA", worker.fields["proyecto"], Modifier.weight(1.5f))
                        WorkerDetail("TELÉFONO", worker.fields["telefono"], Modifier.weight(1f))
                        WorkerDetail("DOCUMENTOS", "${worker.attachments.size} adjuntos", Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkerDetail("CÉDULA", worker.fields["cedula"], Modifier.weight(1f))
                            WorkerDetail("PROYECTO / OBRA", worker.fields["proyecto"], Modifier.weight(1f))
                        }
                        WorkerDetail("TELÉFONO", worker.fields["telefono"])
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkerStatusChip(isWorkerActive(worker))
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("Ver ficha"); Spacer(Modifier.width(5.dp)); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
fun LegajosScreen(empresa: Empresa, repository: MemoryRepository) {
    val context = LocalContext.current
    val workers = repository.data(empresa).workers.sortedBy { it.displayName.lowercase() }
    var search by remember(empresa) { mutableStateOf("") }
    var selectedId by remember(empresa) { mutableStateOf("") }
    var editor by remember(empresa) { mutableStateOf<Worker?>(null) }
    val exportPdf = rememberWorkerPdfExport(empresa, repository)
    val query = search.trim()
    val visible = workers.filter { (selectedId.isBlank() || it.id == selectedId) && (query.isBlank() || it.displayName.contains(query, true) || it.fields["cedula"].orEmpty().contains(query, true) || it.attachments.any { file -> file.name.contains(query, true) }) }
    val documentCount = workers.sumOf { it.attachments.size }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { WorkerPageHeading("Legajos digitales", "Documentación y fichas del personal de ${empresa.displayName}.") }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = WorkerTeal, modifier = Modifier.size(28.dp))
                        Column { Text("$documentCount documentos", fontWeight = FontWeight.SemiBold, color = WorkerNavy); Text("${workers.size} legajos · ${empresa.displayName}", style = MaterialTheme.typography.bodySmall, color = WorkerMuted) }
                    }
                    OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("Buscar trabajador, cédula o archivo") }, singleLine = true, shape = RoundedCornerShape(12.dp))
                    WorkerSelectDropdown(
                        value = selectedId, label = "Trabajador", options = listOf("" to "Todos los trabajadores") + workers.map { it.id to it.displayName },
                        onSelect = { selectedId = it }, modifier = Modifier.fillMaxWidth(), allowClear = false
                    )
                }
            }
        }
        if (visible.isEmpty()) item { WorkersEmptyState("No hay legajos para mostrar", if (workers.isEmpty()) "Creá un trabajador en Personal para iniciar su legajo." else "Cambiá la búsqueda o seleccioná otro trabajador.") }
        items(visible, key = { it.id }) { worker ->
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, WorkerLine)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WorkerAvatar(worker)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(worker.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = WorkerNavy)
                            Text(worker.fields["cedula"].orEmpty().ifBlank { "Cédula sin completar" }, style = MaterialTheme.typography.bodySmall, color = WorkerMuted)
                        }
                        IconButton(onClick = { exportPdf(worker) }) { Icon(Icons.Outlined.Download, "Descargar ficha de ${worker.displayName} en PDF", tint = WorkerTeal) }
                        IconButton(onClick = { editor = worker }) { Icon(Icons.Outlined.Edit, "Editar documentos de ${worker.displayName}", tint = WorkerMuted) }
                    }
                    HorizontalDivider(color = WorkerLine)
                    if (worker.attachments.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Este legajo todavía no tiene archivos.", modifier = Modifier.weight(1f), color = WorkerMuted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { editor = worker }) { Text("Adjuntar") }
                        }
                    }
                    worker.attachments.forEach { file ->
                        val realFile = file.uri.startsWith("content://")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFF1F6F7)) { Icon(Icons.Outlined.Description, null, Modifier.padding(10.dp), tint = WorkerTeal) }
                            Column(Modifier.weight(1f)) {
                                Text(attachmentTitle(file.kind), style = MaterialTheme.typography.labelLarge, color = WorkerNavy)
                                Text(file.name, style = MaterialTheme.typography.bodySmall, color = WorkerMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!realFile) Text("Ejemplo · sin archivo vinculado", color = Color(0xFF9C641A), style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { openWorkerAttachment(context, file) }, enabled = realFile) { Icon(Icons.Outlined.OpenInNew, "Abrir ${file.name}") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { editor = worker }, label = { Text("Administrar legajo") }, leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(16.dp)) })
                    }
                }
            }
        }
    }
    editor?.let { worker -> WorkerEditorDialog(worker, onDismiss = { editor = null }, onSave = { repository.saveWorker(it); editor = null }) }
}

private data class WorkerFieldSpec(val key: String, val label: String, val date: Boolean = false, val options: List<String>? = null, val keyboard: KeyboardType = KeyboardType.Text)

@Composable
private fun WorkerEditorDialog(worker: Worker, onDismiss: () -> Unit, onSave: (Worker) -> Unit) {
    val context = LocalContext.current
    val fields = remember(worker.id) { mutableStateMapOf<String, String>().apply { putAll(worker.fields) } }
    val attachments = remember(worker.id) { mutableStateListOf<Attachment>().apply { addAll(worker.attachments) } }
    var pendingKind by remember { mutableStateOf("") }
    var discardConfirmation by remember { mutableStateOf(false) }
    val changed = fields.toMap().filterValues { it.isNotBlank() } != worker.fields.filterValues { it.isNotBlank() } || attachments.toList() != worker.attachments
    val dismiss = { if (changed) discardConfirmation = true else onDismiss() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && pendingKind.isNotBlank()) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                }
            }.getOrNull() ?: uri.lastPathSegment ?: "Documento"
            attachments.removeAll { it.kind == pendingKind }
            attachments.add(Attachment(id = newId(), kind = pendingKind, name = name, uri = uri.toString()))
        }
    }
    val department = fields["departamento"].orEmpty()
    val localities = remember(context, department) { UruguayGeography.localities(context, department) }

    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.widthIn(max = 1080.dp).fillMaxWidth(0.96f).fillMaxHeight(0.94f), shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (worker.fields.isEmpty() && worker.attachments.isEmpty()) "Ficha de trabajador" else worker.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = WorkerNavy)
                        Text("${worker.empresa.displayName} · Todos los campos son opcionales", style = MaterialTheme.typography.bodySmall, color = WorkerMuted)
                    }
                    IconButton(onClick = dismiss) { Icon(Icons.Outlined.Close, "Cerrar ficha") }
                }
                HorizontalDivider(color = WorkerLine)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    WorkerFormSection("01", "Datos personales e identificación", "Identidad y documentación del trabajador") {
                        Text("Código interno: ${worker.id}", color = WorkerMuted, style = MaterialTheme.typography.labelSmall)
                        WorkerFieldGrid(listOf(
                            WorkerFieldSpec("nombres", "Nombres"), WorkerFieldSpec("apellidos", "Apellidos"),
                            WorkerFieldSpec("cedula", "Cédula de identidad"), WorkerFieldSpec("nacimiento", "Fecha de nacimiento", date = true),
                            WorkerFieldSpec("paisNacimiento", "País de nacimiento"), WorkerFieldSpec("paisDocumento", "País del documento"),
                            WorkerFieldSpec("carneSaludVencimiento", "Vencimiento del carné de salud", date = true)
                        ), fields)
                        WorkerAttachmentGrid(attachments, onChoose = { pendingKind = it; picker.launch(arrayOf("image/*", "application/pdf")) }, onRemove = { kind -> attachments.removeAll { it.kind == kind } })
                    }
                    WorkerFormSection("02", "Contacto y dirección", "Departamento y localidades del nomenclátor oficial del INE") {
                        WorkerFieldGrid(listOf(
                            WorkerFieldSpec("calle", "Calle"), WorkerFieldSpec("numero", "Número"), WorkerFieldSpec("bis", "Bis / apartamento"),
                            WorkerFieldSpec("departamento", "Departamento", options = UruguayGeography.departments),
                            WorkerFieldSpec("localidad", "Localidad / ciudad", options = localities),
                            WorkerFieldSpec("telefono", "Teléfono / WhatsApp", keyboard = KeyboardType.Phone),
                            WorkerFieldSpec("email", "Correo electrónico", keyboard = KeyboardType.Email)
                        ), fields)
                    }
                    WorkerFormSection("03", "Fonasa y salud", "Personas a cargo del trabajador") {
                        WorkerFieldGrid(listOf(WorkerFieldSpec("hijosFonasa", "Hijos a cargo (Fonasa)", options = listOf("Sí", "No")), WorkerFieldSpec("conyugeFonasa", "Cónyuge / concubino a cargo", options = listOf("Sí", "No"))), fields)
                    }
                    WorkerFormSection("04", "Datos bancarios", "Cuenta para depósito de haberes") {
                        WorkerFieldGrid(listOf(WorkerFieldSpec("banco", "Banco"), WorkerFieldSpec("tipoCuenta", "Tipo de cuenta", options = listOf("Caja de ahorro", "Cuenta corriente")), WorkerFieldSpec("numeroCuenta", "Número de cuenta / CBU")), fields)
                    }
                    WorkerFormSection("05", "Talles de uniforme", "Las entregas individuales se registran en Uniformes y EPP") {
                        WorkerFieldGrid(listOf(WorkerFieldSpec("talleRemera", "Remera"), WorkerFieldSpec("talleCamisa", "Camisa"), WorkerFieldSpec("tallePantalon", "Pantalón"), WorkerFieldSpec("talleBuzo", "Buzo"), WorkerFieldSpec("talleCampera", "Campera"), WorkerFieldSpec("talleCalzado", "Calzado de seguridad")), fields)
                    }
                    WorkerFormSection("06", "Relación laboral y asignación", "Empresa contratante: ${worker.empresa.displayName}") {
                        WorkerFieldGrid(buildList {
                            add(WorkerFieldSpec("aportacion", "Tipo de aportación", options = listOf("Industria y Comercio Común", "Industria y Comercio - No Ley 14.411", "Aportación 4: Construcción (Ley 14.411)")))
                            add(WorkerFieldSpec("proyecto", "Proyecto / nombre de obra"))
                            if (fields["aportacion"].orEmpty().contains("Construcción") || !fields["obraBps"].isNullOrBlank()) add(WorkerFieldSpec("obraBps", "Número de obra BPS"))
                            add(WorkerFieldSpec("categoria", "Categoría / puesto")); add(WorkerFieldSpec("modalidad", "Modalidad de contratación", options = listOf("A término", "Por tiempo indeterminado", "Por obra", "Jornalero", "Otra")))
                            add(WorkerFieldSpec("alta", "Fecha de alta", date = true)); add(WorkerFieldSpec("baja", "Fecha de baja", date = true))
                        }, fields)
                    }
                    Spacer(Modifier.height(4.dp))
                }
                HorizontalDivider(color = WorkerLine)
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = dismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = { onSave(worker.copy(fields = fields.toMap().filterValues { it.isNotBlank() }, attachments = attachments.toList())) }, shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("Guardar trabajador")
                    }
                }
            }
        }
    }
    if (discardConfirmation) AlertDialog(
        onDismissRequest = { discardConfirmation = false }, title = { Text("¿Descartar los cambios?") }, text = { Text("Los cambios de esta ficha todavía no se guardaron.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Descartar") } }, dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("Seguir editando") } }
    )
}

@Composable
private fun WorkerFormSection(number: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = Color(0xFFE7F3F2), shape = RoundedCornerShape(10.dp)) { Text(number, Modifier.padding(10.dp), color = WorkerTeal, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
            Column { Text(title, fontWeight = FontWeight.SemiBold, color = WorkerNavy, style = MaterialTheme.typography.titleMedium); Text(subtitle, color = WorkerMuted, style = MaterialTheme.typography.bodySmall) }
        }
        content()
    }
}

@Composable
private fun WorkerFieldGrid(specs: List<WorkerFieldSpec>, fields: MutableMap<String, String>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 580.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            specs.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { spec -> WorkerFormField(spec, fields, Modifier.weight(1f)) }
                    if (row.size < columns) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WorkerFormField(spec: WorkerFieldSpec, fields: MutableMap<String, String>, modifier: Modifier) {
    val value = fields[spec.key].orEmpty()
    val update: (String) -> Unit = { selected ->
        fields[spec.key] = selected
        if (spec.key == "departamento" && selected != value) fields.remove("localidad")
    }
    when {
        spec.date -> WorkerDateField(value, update, spec.label, modifier)
        spec.options != null -> WorkerSelectDropdown(
            value, spec.label, spec.options.map { it to it }, update, modifier,
            enabled = spec.key != "localidad" || !fields["departamento"].isNullOrBlank()
        )
        else -> OutlinedTextField(value = value, onValueChange = update, label = { Text(spec.label) }, modifier = modifier, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = spec.keyboard), shape = RoundedCornerShape(10.dp))
    }
}

@Composable
private fun WorkerSelectDropdown(value: String, label: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, allowClear: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = !expanded }, modifier = modifier) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value, onValueChange = {}, readOnly = true, enabled = enabled,
            label = { Text(label) }, modifier = Modifier.menuAnchor().fillMaxWidth(), singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) }, shape = RoundedCornerShape(10.dp),
            placeholder = { Text(if (!enabled) "Primero elegí un departamento" else "Sin completar") }
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (allowClear) DropdownMenuItem(text = { Text("Sin completar", color = WorkerMuted) }, onClick = { onSelect(""); expanded = false })
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(key); expanded = false }) }
        }
    }
}

@Composable
private fun WorkerDateField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) }, placeholder = { Text("AAAA-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp), trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Outlined.CalendarMonth, "Elegir $label") } })
        Box(Modifier.matchParentSize().padding(end = 52.dp).clickable { showPicker = true })
    }
    if (showPicker) {
        val initialDate = runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate)
        DatePickerDialog(onDismissRequest = { showPicker = false }, confirmButton = { TextButton(onClick = { picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; showPicker = false }) { Text("Aceptar") } }, dismissButton = {
            Row { if (value.isNotBlank()) TextButton(onClick = { onChange(""); showPicker = false }) { Text("Quitar fecha") }; TextButton(onClick = { showPicker = false }) { Text("Cancelar") } }
        }) { DatePicker(state = picker, title = { Text(label, Modifier.padding(start = 24.dp, top = 16.dp)) }, showModeToggle = true) }
    }
}

@Composable
private fun WorkerAttachmentGrid(attachments: List<Attachment>, onChoose: (String) -> Unit, onRemove: (String) -> Unit) {
    val context = LocalContext.current
    val kinds = listOf("cedula_frente", "cedula_dorso", "carne_frente", "carne_dorso")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DOCUMENTOS ADJUNTOS", color = WorkerMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        kinds.forEach { kind ->
            val file = attachments.firstOrNull { it.kind == kind }
            Surface(color = Color(0xFFF6F8FA), shape = RoundedCornerShape(12.dp), border = androidx.compose.foundation.BorderStroke(1.dp, WorkerLine)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(if (file == null) Icons.Outlined.UploadFile else Icons.Outlined.Description, null, tint = WorkerTeal)
                        Column(Modifier.weight(1f)) {
                            Text(attachmentTitle(kind), style = MaterialTheme.typography.labelLarge, color = WorkerNavy)
                            Text(file?.name ?: "Imagen o PDF · opcional", color = WorkerMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (file != null && !file.uri.startsWith("content://")) Text("Ejemplo · sin archivo vinculado", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9C641A))
                        }
                        if (file != null && file.uri.startsWith("content://")) IconButton(onClick = { openWorkerAttachment(context, file) }) { Icon(Icons.Outlined.OpenInNew, "Abrir ${file.name}", Modifier.size(18.dp)) }
                        if (file != null) IconButton(onClick = { onRemove(kind) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.Close, "Quitar ${attachmentTitle(kind)}", Modifier.size(18.dp)) }
                    }
                    TextButton(onClick = { onChoose(kind) }, modifier = Modifier.align(Alignment.End), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (file == null) "Adjuntar archivo" else "Cambiar archivo") }
                }
            }
        }
    }
}

@Composable
private fun rememberWorkerPdfExport(empresa: Empresa, repository: MemoryRepository): (Worker) -> Unit {
    val context = LocalContext.current
    var pending by remember(empresa) { mutableStateOf<Worker?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val worker = pending
        if (uri != null && worker != null) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el archivo")
                output.use { exportWorkerPdf(context, it, worker, repository.data(worker.empresa)) }
            }.onSuccess { Toast.makeText(context, "Ficha PDF guardada", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "No se pudo guardar la ficha PDF: ${it.localizedMessage}", Toast.LENGTH_LONG).show() }
        }
        pending = null
    }
    return { worker -> pending = worker; launcher.launch("Ficha_${worker.empresa.name}_${worker.displayName.replace(Regex("[^\\p{L}\\p{N}_-]"), "_")}.pdf") }
}

private fun openWorkerAttachment(context: android.content.Context, file: Attachment) {
    if (!file.uri.startsWith("content://")) {
        Toast.makeText(context, "Este documento de ejemplo no tiene un archivo vinculado.", Toast.LENGTH_LONG).show()
        return
    }
    runCatching {
        val uri = Uri.parse(file.uri)
        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Abrir documento"))
    }.onFailure { Toast.makeText(context, "No se pudo abrir el documento. Volvé a adjuntarlo o instalá una aplicación compatible.", Toast.LENGTH_LONG).show() }
}

private fun attachmentTitle(kind: String) = when (kind) {
    "cedula_frente" -> "Cédula · frente"
    "cedula_dorso" -> "Cédula · dorso"
    "carne_frente" -> "Carné de salud · frente"
    "carne_dorso" -> "Carné de salud · dorso"
    else -> kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun isWorkerActive(worker: Worker): Boolean {
    val date = worker.fields["baja"].orEmpty()
    return date.isBlank() || runCatching { LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
}

@Composable
private fun WorkerPageHeading(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 600.dp
        if (compact) Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = WorkerNavy)
            Text(description, color = WorkerMuted, style = MaterialTheme.typography.bodyMedium)
            if (action != null) Button(onClick = onAction, shape = RoundedCornerShape(10.dp)) { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(action) }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = WorkerNavy); Spacer(Modifier.height(5.dp)); Text(description, color = WorkerMuted) }
            if (action != null) Button(onClick = onAction, shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)) { Icon(Icons.Outlined.PersonAdd, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text(action) }
        }
    }
}

@Composable
private fun WorkerAvatar(worker: Worker) {
    val initials = listOf(worker.fields["nombres"].orEmpty().trim().firstOrNull(), worker.fields["apellidos"].orEmpty().trim().firstOrNull()).filterNotNull().joinToString("").uppercase().ifBlank { "—" }
    Box(Modifier.size(46.dp).background(Color(0xFFE9F2F4), CircleShape), contentAlignment = Alignment.Center) { Text(initials, fontWeight = FontWeight.Bold, color = WorkerTeal, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun WorkerDetail(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.labelSmall, color = WorkerMuted); Text(value.orEmpty().ifBlank { "Sin completar" }, style = MaterialTheme.typography.bodyMedium, color = WorkerNavy, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun WorkerStatusChip(active: Boolean) {
    Surface(color = if (active) Color(0xFFE7F4ED) else Color(0xFFF1F3F5), shape = RoundedCornerShape(7.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).background(if (active) Color(0xFF228358) else WorkerMuted, CircleShape))
            Text(if (active) "Activo" else "Con baja", color = if (active) Color(0xFF247A53) else WorkerMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WorkersEmptyState(title: String, description: String) {
    Surface(Modifier.fillMaxWidth(), color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.FolderOpen, null, tint = WorkerTeal, modifier = Modifier.size(42.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = WorkerNavy, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = WorkerMuted)
        }
    }
}
