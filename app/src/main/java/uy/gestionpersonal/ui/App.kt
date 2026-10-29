package uy.gestionpersonal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uy.gestionpersonal.data.*
import uy.gestionpersonal.reports.ReportesScreen
import uy.gestionpersonal.drive.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Module(val label: String, val icon: ImageVector) {
    INICIO("Resumen", Icons.Outlined.Dashboard),
    PERSONAL("Personal", Icons.Outlined.People),
    LEGAJOS("Legajos", Icons.Outlined.FolderOpen),
    UNIFORMES("Uniformes", Icons.Outlined.Checkroom),
    EPP("EPP", Icons.Outlined.HealthAndSafety),
    CAPACITACIONES("Capacitaciones", Icons.Outlined.School),
    REPORTES("Reportes", Icons.Outlined.Assessment),
    DRIVE("Google Drive", Icons.Outlined.CloudSync)
}

@Composable
fun GestionApp(repository: MemoryRepository, driveController: DriveController? = null) {
    val context = LocalContext.current
    val drive = remember(driveController) { driveController ?: DriveController(context) }
    DisposableEffect(drive) { onDispose { if (driveController == null) drive.close() } }
    DriveHost(drive, repository) { GestionContent(repository, drive) }
}

@Composable
private fun GestionContent(repository: MemoryRepository, drive: DriveController) {
    var companyName by rememberSaveable { mutableStateOf<String?>(null) }
    var moduleName by rememberSaveable { mutableStateOf(Module.INICIO.name) }
    val empresa = companyName?.let(Empresa::valueOf)
    val module = Module.valueOf(moduleName)
    val switchCompany = { companyName = null; moduleName = Module.INICIO.name }
    BackHandler(enabled = empresa != null) {
        if (module != Module.INICIO) moduleName = Module.INICIO.name else switchCompany()
    }
    Surface(Modifier.fillMaxSize(), color = Canvas) {
        if (empresa == null) {
            CompanySelection { companyName = it.name; moduleName = Module.INICIO.name }
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        Column(Modifier.width(226.dp).fillMaxHeight().background(BrandPanel).padding(20.dp)) {
                            Brand(light = true)
                            Spacer(Modifier.height(34.dp))
                            Text("ESPACIO DE TRABAJO", color = Color(0xFF9DB1C2), fontSize = 10.sp, letterSpacing = 1.5.sp)
                            Text(empresa.displayName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                            Module.entries.forEach { item ->
                                val selected = item == module
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp)).clickable { moduleName = item.name }.padding(horizontal = 12.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(item.icon, null, tint = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFBAC8D4), modifier = Modifier.size(20.dp))
                                    Text(item.label, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text("Cada empresa tiene su propio\nespacio de información.", color = Color(0xFFB7C9D5), fontSize = 11.sp, lineHeight = 17.sp)
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = if (wide) 28.dp else 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(if (wide) "ADMINISTRACIÓN / ${module.label.uppercase()}" else "GESTIÓN DE PERSONAL", fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
                                Text(empresa.displayName, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            }
                            ThemeSelector()
                            OutlinedButton(onClick = switchCompany, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(16.dp))
                                Text("Cambiar empresa", fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        if (!wide) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Module.entries.forEach { item ->
                                FilterChip(selected = item == module, onClick = { moduleName = item.name }, label = { Text(item.label) }, leadingIcon = { Icon(item.icon, null, Modifier.size(17.dp)) })
                            }
                        }
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Science, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text(" DEMO · Incluye ejemplos. Los cambios se guardan en este dispositivo.", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 11.sp)
                        }
                        repository.persistenceError?.let { warning -> Surface(color = MaterialTheme.colorScheme.errorContainer) { Text(warning, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp) } }
                        key(empresa, module) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                when (module) {
                                    Module.INICIO -> Dashboard(empresa, repository) { moduleName = it.name }
                                    Module.PERSONAL -> PersonalScreen(empresa, repository)
                                    Module.LEGAJOS -> LegajosScreen(empresa, repository)
                                    Module.UNIFORMES -> DeliveriesScreen(empresa, repository, DeliveryKind.UNIFORME)
                                    Module.EPP -> DeliveriesScreen(empresa, repository, DeliveryKind.EPP)
                                    Module.CAPACITACIONES -> TrainingsScreen(empresa, repository)
                                    Module.REPORTES -> ReportesScreen(empresa, repository)
                                    Module.DRIVE -> DriveScreen(empresa, repository, drive)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Brand(light: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(if (light) MaterialTheme.colorScheme.primaryContainer else Teal, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Badge, null, tint = if (light) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
        }
        Column {
            Text("Gestión de", color = if (light) Color.White else Navy, fontSize = 12.sp)
            Text("Personal", color = if (light) Color.White else Navy, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
    }
}

@Composable
private fun CompanySelection(onSelect: (Empresa) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val wide = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) Column(Modifier.fillMaxHeight().weight(.39f).background(BrandPanel).padding(48.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Brand(light = true)
                Column {
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Groups, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp)) }
                    Text("Las personas,\nen el centro.", fontSize = 38.sp, lineHeight = 45.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 24.dp))
                    Text("Personal, legajos y seguridad laboral en un solo lugar.", color = Color(0xFFBCD0DC), fontSize = 15.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 18.dp))
                }
                Text("DOS EMPRESAS. UNA MISMA EXPERIENCIA.", color = Color(0xFF9DB8C7), fontSize = 10.sp, letterSpacing = 1.sp)
            }
            Column(Modifier.weight(if (wide) .61f else 1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 56.dp else 24.dp, vertical = if (wide) 54.dp else 28.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (!wide) Brand()
                    Spacer(Modifier.weight(1f))
                    ThemeSelector()
                }
                Spacer(Modifier.height(if (wide) 20.dp else 28.dp))
                Text("SISTEMA INTEGRAL DE ADMINISTRACIÓN", color = Teal, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                Text("Seleccioná una\nempresa", fontSize = if (wide) 36.sp else 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                Text("Elegí el espacio de trabajo que querés administrar.", fontSize = 14.sp, lineHeight = 22.sp, color = Muted, modifier = Modifier.padding(top = 12.dp, bottom = 30.dp))
                Empresa.entries.forEach { company ->
                    val accent = if (company == Empresa.RR) Teal else MaterialTheme.colorScheme.secondary
                    Card(onClick = { onSelect(company) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(20.dp)) {
                        Row(Modifier.padding(22.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(54.dp).background(accent.copy(alpha = .09f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Text(if (company == Empresa.RR) "R&R" else "T", fontSize = if (company == Empresa.RR) 16.sp else 24.sp, fontWeight = FontWeight.ExtraBold, color = accent)
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(company.displayName, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                Text("Gestión independiente de personal", fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                                Text("Personal, legajos, EPP y capacitaciones", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                            }
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "Ingresar a ${company.displayName}", tint = accent, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = Teal, modifier = Modifier.size(18.dp))
                    Text("Los registros de cada empresa se mantienen separados", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text("GESTIÓN DE PERSONAL · VERSIÓN 1.6", fontSize = 10.sp, letterSpacing = 1.sp, color = Muted)
                Text("Incluye ejemplos ficticios. Los registros se conservan en el dispositivo. Conectá tu cuenta desde Google Drive para actualizar documentos.", color = Muted, fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Dashboard(empresa: Empresa, repository: MemoryRepository, onNavigate: (Module) -> Unit) {
    val data = repository.data(empresa)
    val today = LocalDate.now()
    val expired = data.trainings.count { it.status(today) == TrainingStatus.VENCIDA }
    val soon = data.trainings.count { it.status(today) == TrainingStatus.POR_VENCER }
    val health = data.workers.count { runCatching { LocalDate.parse(it.fields["carneSaludVencimiento"]) < today.plusDays(31) }.getOrDefault(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column {
            Text(today.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es-UY"))).replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 12.sp)
            Text("Tu equipo, al día", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("Resumen de la gestión de ${empresa.displayName}.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth > 600.dp) 4 else 2
            val metrics = listOf(Triple("Personas registradas", data.workers.size.toString(), Icons.Outlined.People), Triple("Documentos", data.workers.sumOf { it.attachments.size }.toString(), Icons.Outlined.FolderOpen), Triple("Entregas de EPP", data.deliveries.count { it.kind == DeliveryKind.EPP }.toString(), Icons.Outlined.HealthAndSafety), Triple("Capacitaciones", data.trainings.size.toString(), Icons.Outlined.School))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                metrics.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { metric -> Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(18.dp)) { Icon(metric.third, null, tint = Teal, modifier = Modifier.size(23.dp)); Text(metric.second, fontWeight = FontWeight.Bold, fontSize = 30.sp, modifier = Modifier.padding(top = 12.dp)); Text(metric.first, color = Muted, fontSize = 11.sp) }
                    } }
                } }
            }
        }
        if (expired + soon + health > 0) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onTertiaryContainer); Text("Requiere atención", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp)) }
                Text("$expired capacitaciones vencidas · $soon por vencer\n$health carnés de salud vencidos o próximos a vencer", color = MaterialTheme.colorScheme.onTertiaryContainer, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
                TextButton(onClick = { onNavigate(Module.CAPACITACIONES) }) { Text("Ver capacitaciones →") }
            }
        }
        Text("Accesos rápidos", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Module.entries.filter { it != Module.INICIO }.forEach { item ->
            Card(onClick = { onNavigate(item) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, tint = Teal)
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(item.label, fontWeight = FontWeight.SemiBold)
                        Text(when(item) { Module.PERSONAL -> "Ficha personal y relación laboral"; Module.LEGAJOS -> "Identificación y documentos adjuntos"; Module.UNIFORMES -> "Talles e historial de entrega"; Module.EPP -> "Equipos y recepción en conformidad"; Module.CAPACITACIONES -> "Charlas, vigencias y alertas"; Module.DRIVE -> "Cuenta de Google y actualización de adjuntos"; else -> "Excel consolidado y ficha individual en PDF" }, color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = Muted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
