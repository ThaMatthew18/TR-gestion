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
        if (account != name) { disableAutomatic(); folders = emptyMap() }
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
        disableAutomatic(); queue.clear(); folders = emptyMap()
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

    private fun request(name: String) = AuthorizationRequest.builder().setAccount(Account(name, "com.google")).setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
    fun cancelSync() { queue.clear(); job?.cancel(); message = "Carga interrumpida. Los documentos locales se conservan; podés reintentar."; error = false }
    fun close() { scope.cancel() }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
