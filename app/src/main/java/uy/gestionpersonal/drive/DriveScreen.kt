package uy.gestionpersonal.drive

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.android.gms.common.AccountPicker
import kotlinx.coroutines.delay
import uy.gestionpersonal.data.*
import uy.gestionpersonal.ui.ScreenHeading

/** The launchers stay at app root, so changing module/company cannot redirect a Google result. */
@Composable
fun DriveHost(controller: DriveController, repository: MemoryRepository, content: @Composable () -> Unit) {
    val snapshot = repository.snapshot()
    val latestSnapshot by rememberUpdatedState(snapshot)
    val accountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val google = data?.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE) == "com.google"
        controller.accountChosen(if (result.resultCode == Activity.RESULT_OK && google) data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME) else null)
    }
    val authorizationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.finishAuthorization(result.data) else controller.cancelAuthorization()
    }
    LaunchedEffect(controller.accountPickerRequested) {
        if (controller.accountPickerRequested) {
            controller.accountPickerRequested = false
            try {
                accountLauncher.launch(AccountPicker.newChooseAccountIntent(AccountPicker.AccountChooserOptions.Builder()
                    .setAllowableAccountsTypes(listOf("com.google")).setAlwaysShowAccountPicker(true).build()))
            } catch (failure: Exception) { controller.authorizationFailed(failure) }
        }
    }
    LaunchedEffect(controller.authorizationIntent) {
        controller.authorizationIntent?.let { pending ->
            controller.authorizationIntent = null
            try { authorizationLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build()) }
            catch (failure: Exception) { controller.authorizationFailed(failure) }
        }
    }
    LaunchedEffect(snapshot, controller.authorized, controller.automatic) {
        delay(1_000)
        if (repository.persistenceError == null) controller.autoSync(snapshot)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (repository.persistenceError == null) controller.autoSync(latestSnapshot)
    }
    content()
}

@Composable
fun DriveScreen(empresa: Empresa, repository: MemoryRepository, controller: DriveController) {
    val context = LocalContext.current
    val source = repository.data(empresa)
    val documents = remember(empresa, source) { driveDocuments(empresa, source) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        ScreenHeading("Google Drive", "Documentos de ${empresa.displayName} en la cuenta que elijas.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CloudSync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                Text(controller.account ?: "Elegí tu cuenta de Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (controller.authorized) "Cuenta autorizada para guardar documentos." else "Conectá Google Drive para guardar y actualizar los archivos adjuntos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (controller.account == null) {
                    Button(onClick = controller::chooseAccount, enabled = !controller.busy && !controller.connecting) { Text("Conectar Google Drive") }
                } else {
                    if (!controller.authorized) Button(onClick = controller::reconnect, enabled = !controller.busy && !controller.connecting) { Text("Autorizar cuenta") }
                    OutlinedButton(onClick = controller::chooseAccount, enabled = !controller.busy && !controller.connecting) { Text("Cambiar cuenta de Google") }
                    TextButton(onClick = { confirmDisconnect = true }, enabled = !controller.busy && !controller.connecting) { Text("Desconectar") }
                }
            }
        }
        Text("${documents.size} documentos adjuntos de ${empresa.displayName}", style = MaterialTheme.typography.titleMedium)
        Text("Se guardan fotos, PDF de identificación y salud, y constancias de entrega. Al reemplazar un adjunto, se actualiza su archivo en Drive.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
            Text("Gestión de Personal / ${empresa.displayName} / Trabajador\n\nR&R y TRANSEO mantienen carpetas independientes dentro de la cuenta seleccionada.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Actualizar al guardar", fontWeight = FontWeight.SemiBold)
                Text("Solo ${empresa.displayName}, con la aplicación abierta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(empresa in controller.automatic, { controller.setAutomatic(empresa, it) }, enabled = controller.authorized && !controller.connecting && !controller.busy)
        }
        Button(onClick = { controller.sync(empresa, repository.data(empresa)) }, enabled = controller.authorized && !controller.connecting && !controller.busy && documents.isNotEmpty() && repository.persistenceError == null, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp)); Text("Sincronizar documentos", Modifier.padding(start = 8.dp))
        }
        if (controller.busy || controller.connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (controller.busy) {
            Text("Actualizando Drive: ${controller.progress.first} / ${controller.progress.second}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = controller::cancelSync) { Text("Cancelar carga") }
        }
        controller.folders[empresa]?.let { id ->
            OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://drive.google.com/drive/folders/$id"))) } }) { Text("Abrir carpeta en Drive") }
        }
        if (controller.message.isNotBlank()) Surface(color = if (controller.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(controller.message, Modifier.padding(16.dp), color = if (controller.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text("Los archivos y registros se conservan en este dispositivo. Si falla la conexión, reintentá desde acá. Las actualizaciones van de la aplicación a Drive; la edición de un archivo desde Drive no modifica el legajo local. Los adjuntos que quites de la app seguirán en Drive hasta que los elimines allí.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false }, title = { Text("Desconectar Google Drive") }, text = { Text("Se detendrán las actualizaciones de ambas empresas para esta cuenta. Los archivos de Drive se conservan.") }, confirmButton = { TextButton(onClick = { controller.disconnect(); confirmDisconnect = false }) { Text("Desconectar") } }, dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancelar") } })
}
