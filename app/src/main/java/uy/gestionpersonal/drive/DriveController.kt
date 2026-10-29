package uy.gestionpersonal.drive

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.*
import uy.gestionpersonal.data.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DriveController(context: Context, preferencesName: String = "drive-connection") {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client by lazy { Identity.getAuthorizationClient(this.context) }
    private var pendingAccount: String? = null
    private val queue = linkedMapOf<Empresa, CompanyData>()
    private var job: Job? = null
    private var workspaceBusy = false
    var workspaceVersions by mutableStateOf<Map<Empresa, List<WorkspaceRevision>>>(emptyMap())
        private set
    var recoveryUri by mutableStateOf(preferences.getString("recoveryUri", null))
        private set
    var account by mutableStateOf(preferences.getString("account", null))
        private set
    var authorized by mutableStateOf(false)
        private set
    var connecting by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var authorizationIntent by mutableStateOf<PendingIntent?>(null)
    var accountPickerRequested by mutableStateOf(false)
    var automatic by mutableStateOf(Empresa.entries.filter { preferences.getBoolean("auto:${it.name}", false) }.toSet())
        private set
    var message by mutableStateOf("")
        private set
    var error by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0 to 0)
        private set
    var folders by mutableStateOf<Map<Empresa, String>>(emptyMap())
        private set

    fun chooseAccount() {
        if (busy || connecting) return
        disableAutomatic()
        connecting = true
        accountPickerRequested = true
    }

    fun accountChosen(name: String?) {
        if (name.isNullOrBlank()) { cancelAuthorization(); return }
        pendingAccount = name
        authorize(name)
    }

    fun reconnect() {
        if (!busy && !connecting) account?.let { connecting = true; pendingAccount = it; authorize(it) }
    }

    private fun authorize(name: String) {
        scope.launch {
            try {
                val result = client.authorize(request(name)).awaitResult()
                if (result.hasResolution()) {
                    authorizationIntent = result.pendingIntent ?: throw IllegalStateException("Google no devolvió una solicitud de autorización.")
                } else acceptAuthorization(name, result)
            } catch (failure: Exception) { if (failure is CancellationException) throw failure; authorizationFailed(failure) }
        }
    }

    fun finishAuthorization(data: Intent?) {
        val name = pendingAccount
        if (name == null || data == null) { cancelAuthorization(); return }
        try { acceptAuthorization(name, client.getAuthorizationResultFromIntent(data)) }
        catch (failure: Exception) { authorizationFailed(failure) }
    }

    private fun acceptAuthorization(name: String, result: AuthorizationResult) {
        check(DRIVE_SCOPE in result.grantedScopes && !result.accessToken.isNullOrBlank()) { "No se concedió el permiso para guardar archivos en Drive." }
        if (account != name) { disableAutomatic(); folders = emptyMap(); workspaceVersions = emptyMap() }
        account = name
        preferences.edit().putString("account", name).apply()
        authorized = true; connecting = false; pendingAccount = null
        message = "Cuenta conectada. Elegí qué documentos querés sincronizar."
        error = false
    }

    fun cancelAuthorization() { connecting = false; pendingAccount = null; authorizationIntent = null; message = "Conexión cancelada. No se subieron archivos."; error = false }

    fun authorizationFailed(failure: Exception) {
        connecting = false; pendingAccount = null; authorizationIntent = null; error = true
        message = if (failure is ApiException && failure.statusCode in listOf(10, 12500))
            "La conexión de Google todavía no está habilitada para esta versión. Avisá al administrador de la aplicación."
        else "No se pudo autorizar Google Drive. Verificá la conexión y volvé a intentarlo."
    }

    fun setAutomatic(empresa: Empresa, enabled: Boolean) {
        if (enabled && !authorized) return
        automatic = if (enabled) automatic + empresa else automatic - empresa
        preferences.edit().putBoolean("auto:${empresa.name}", enabled).apply()
    }

    private fun disableAutomatic() {
        automatic = emptySet()
        preferences.edit().apply { Empresa.entries.forEach { putBoolean("auto:${it.name}", false) } }.apply()
    }

    fun disconnect() {
        if (busy || connecting) return
        val old = account ?: return
        connecting = true
        disableAutomatic(); queue.clear(); folders = emptyMap(); workspaceVersions = emptyMap()
        authorized = false; account = null; preferences.edit().remove("account").apply()
        scope.launch {
            try {
                client.revokeAccess(RevokeAccessRequest.builder().setAccount(Account(old, "com.google")).setScopes(listOf(Scope(DRIVE_SCOPE))).build()).awaitResult()
                message = "Cuenta desconectada. Los archivos de Drive se conservan."; error = false
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                message = "La cuenta quedó desconectada en esta app. No se pudo revocar el permiso en Google; podés quitarlo desde tu cuenta de Google."; error = true
            } finally { connecting = false }
        }
    }

    fun autoSync(snapshot: Map<Empresa, CompanyData>) {
        if (!authorized || connecting) return
        automatic.forEach { company -> snapshot[company]?.let { if (driveDocuments(company, it).isNotEmpty()) sync(company, it) } }
    }

    fun sync(empresa: Empresa, source: CompanyData) {
        if (workspaceBusy) return
        if (!authorized || connecting || account == null) return
        if (driveDocuments(empresa, source).isEmpty()) return
        queue[empresa] = source
        if (job?.isActive == true) return
        job = scope.launch {
            busy = true
            try {
                while (queue.isNotEmpty()) {
                    val entry = queue.entries.first()
                    val company = entry.key
                    val data = entry.value
                    queue.remove(company)
                    val selectedAccount = account ?: break
                    val authorization = client.authorize(request(selectedAccount)).awaitResult()
                    if (authorization.hasResolution() || DRIVE_SCOPE !in authorization.grantedScopes || authorization.accessToken.isNullOrBlank()) {
                        authorized = false
                        throw IllegalStateException("Google necesita una nueva autorización. Tocá Autorizar cuenta para continuar.")
                    }
                    val accessToken = authorization.accessToken!!
                    try {
                        val documents = driveDocuments(company, data)
                        progress = 0 to documents.size
                        val result = withContext(Dispatchers.IO) {
                            val coroutine = currentCoroutineContext()
                            syncDocuments(company, documents, DriveHttp(accessToken) { coroutine.ensureActive() }, { documentContent(context, it) }) { done, total ->
                                scope.launch { progress = done to total }
                            }
                        }
                        folders = folders + (company to result.folderId)
                        message = "${company.displayName}: ${result.uploaded} documentos actualizados y ${result.unchanged} sin cambios en $selectedAccount."
                        error = false
                    } catch (failure: Exception) {
                        if (failure is DriveHttpError && failure.status == 401) {
                            authorized = false
                            runCatching { client.clearToken(ClearTokenRequest.builder().setToken(accessToken).build()).awaitResult() }
                        }
                        throw failure
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                queue.clear(); error = true
                message = failure.message?.take(240) ?: "No se pudo actualizar Drive. Los archivos locales se conservan; reintentá la sincronización."
            } finally { busy = false }
        }
    }

    private fun workspaceAction(action: suspend (DriveHttp, String) -> Unit) {
        if (!authorized || connecting || busy || account == null) return
        val selected = account!!
        job = scope.launch {
            busy = true; workspaceBusy = true
            try {
                val authorization = client.authorize(request(selected)).awaitResult()
                check(!authorization.hasResolution() && DRIVE_SCOPE in authorization.grantedScopes && !authorization.accessToken.isNullOrBlank()) { "Volvé a autorizar la cuenta para acceder a los datos compartidos." }
                val coroutine = currentCoroutineContext()
                action(DriveHttp(authorization.accessToken!!) { coroutine.ensureActive() }, selected)
                error = false
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is DriveHttpError && failure.status == 401) authorized = false
                error = true; message = failure.message?.take(300) ?: "No se pudo completar la operación. Los datos locales se conservan."
            } finally { busy = false; workspaceBusy = false }
        }
    }

    fun refreshWorkspace(company: Empresa) = workspaceAction { gateway, _ ->
        val versions = withContext(Dispatchers.IO) { gateway.workspaceRevisions(company) }
        workspaceVersions = workspaceVersions + (company to versions)
        val heads = workspaceHeads(versions)
        message = if (heads.isEmpty()) "Todavía no hay una copia compartida de ${company.displayName}. Podés publicar la de este celular." else "${heads.size} versión(es) principal(es) disponibles. Revisá el historial antes de descargar o publicar."
    }

    fun publishWorkspace(company: Empresa, repository: MemoryRepository, resolve: Boolean = false) = workspaceAction { gateway, selected ->
        check(repository.persistenceError == null) { "Primero resolvé el guardado local." }
        val source = repository.data(company)
        val file = java.io.File.createTempFile("workspace-", ".zip", context.cacheDir)
        try {
            val revisions = withContext(Dispatchers.IO) { gateway.workspaceRevisions(company) }
            workspaceVersions = workspaceVersions + (company to revisions)
            val base = preferences.getString("base:$selected:${company.name}", null)
            val parents = workspacePublishParents(revisions, base, resolve)
            val revision = withContext(Dispatchers.IO) {
                file.outputStream().use { writeCompanyArchive(context, it, company, source) }
                val content = fileContent(file)
                gateway.publishWorkspace(company, content, parents)
            }
            preferences.edit().putString("base:$selected:${company.name}", revision.id).apply()
            val updated = withContext(Dispatchers.IO) { gateway.workspaceRevisions(company) }
            workspaceVersions = workspaceVersions + (company to updated)
            check(updated.any { it.id == revision.id } && workspaceHeads(updated).size == 1) { "La copia se publicó, pero hay versiones simultáneas o Drive aún no la muestra. Actualizá el historial; ninguna versión anterior fue borrada." }
            message = "Copia completa publicada para $selected. En el otro celular elegí esa cuenta y descargá esta versión." + if (repository.data(company) != source) " Hay cambios locales posteriores que todavía no se publicaron." else ""
        } finally { file.delete() }
    }

    fun downloadWorkspace(company: Empresa, revision: WorkspaceRevision, repository: MemoryRepository) = workspaceAction { gateway, selected ->
        check(repository.persistenceError == null) { "Primero resolvé el guardado local." }
        val expected = repository.data(company)
        val file = java.io.File.createTempFile("download-", ".zip", context.cacheDir)
        var prepared: PreparedCompany? = null
        var committed = false
        try {
            withContext(Dispatchers.IO) {
                val available = gateway.workspaceRevisions(company)
                check(available.any { it.id == revision.id && it.sha256 == revision.sha256 }) { "La versión no está disponible para esta empresa" }
                file.outputStream().use { gateway.downloadWorkspace(revision.id, it) }
                check(revision.sha256.isNotBlank() && fileContent(file).sha256 == revision.sha256) { "La copia descargada no pasó la verificación de integridad" }
                prepared = file.inputStream().use { readCompanyArchive(context, it, company) }
                val directory = java.io.File(context.filesDir, "documents").apply { check(exists() || mkdirs()) }
                val backup = java.io.File(directory, "Respaldo-${company.name}-${java.util.UUID.randomUUID()}.zip")
                try { backup.outputStream().use { writeCompanyArchive(context, it, company, expected) } }
                catch (failure: Exception) { backup.delete(); throw failure }
                recoveryUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", backup).toString()
                preferences.edit().putString("recoveryUri", recoveryUri).apply()
            }
            repository.replaceCompany(company, expected, prepared!!.data)
            committed = true
            preferences.edit().putString("base:$selected:${company.name}", revision.id).apply()
            message = "${company.displayName}: datos y documentos restaurados. Se conservó una copia local anterior que podés exportar. La otra empresa no cambió."
        } finally { if (!committed) prepared?.discard(); file.delete() }
    }

    private fun fileContent(file: java.io.File): DriveContent {
        val hash = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
        return DriveContent("application/zip", file.length(), hash.digest().joinToString("") { "%02x".format(it) }) { file.inputStream() }
    }

    private fun request(name: String) = AuthorizationRequest.builder().setAccount(Account(name, "com.google")).setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
    fun cancelSync() { queue.clear(); job?.cancel(); message = "Carga interrumpida. Los documentos locales se conservan; podés reintentar."; error = false }
    fun close() { scope.cancel() }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
