package uy.gestionpersonal.drive

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

class DriveHttpError(val status: Int, message: String) : IOException(message)

class DriveHttp(private val token: String, private val checkActive: () -> Unit = {}) : DriveGateway {
    private val api = "https://www.googleapis.com/drive/v3/files"

    override fun folder(key: String, name: String, parentId: String?): String {
        val found = lookup(key, parentId, folder = true)
        if (found != null) return found.getString("id")
        val metadata = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder").put("appProperties", properties(key))
        if (parentId != null) metadata.put("parents", org.json.JSONArray(listOf(parentId)))
        return json("POST", "$api?fields=id", metadata).getString("id")
    }

    override fun find(key: String, parentId: String): RemoteDriveFile? = lookup(key, parentId, folder = false)?.let {
        RemoteDriveFile(it.getString("id"), it.optString("name"), it.optString("sha256Checksum"))
    }

    private fun lookup(key: String, parentId: String?, folder: Boolean): JSONObject? {
        require(key.none { it == '\'' || it == '\\' })
        parentId?.let(::safeId)
        val query = "trashed = false and appProperties has { key='gpNamespace' and value='$DRIVE_NAMESPACE' } and appProperties has { key='gpKey' and value='$key' }" +
            (parentId?.let { " and '$it' in parents" } ?: " and 'root' in parents") +
            if (folder) " and mimeType='application/vnd.google-apps.folder'" else " and mimeType!='application/vnd.google-apps.folder'"
        val result = json("GET", "$api?q=${encode(query)}&fields=files(id,name,appProperties,sha256Checksum),nextPageToken&pageSize=2")
        val files = result.getJSONArray("files")
        check(files.length() <= 1 && result.optString("nextPageToken").isBlank()) { "Drive contiene duplicados de este registro. Revisá la carpeta antes de volver a sincronizar." }
        return if (files.length() == 0) null else files.getJSONObject(0)
    }

    override fun upload(document: DriveDocument, content: DriveContent, parentId: String, existingId: String?): RemoteDriveFile = uploadData(document, content, parentId, existingId)

    private fun uploadData(document: DriveDocument, content: DriveContent, parentId: String, existingId: String?, parents: Set<String>? = null): RemoteDriveFile {
        safeId(parentId)
        existingId?.let(::safeId)
        val metadata = JSONObject().put("name", document.name).put("appProperties", properties(document.key).put("gpSha256", content.sha256).put("gpCompany", document.empresa.name))
        parents?.forEachIndexed { index, id -> safeId(id); require(id.length <= 120); metadata.getJSONObject("appProperties").put("gpParent$index", id) }
        if (existingId == null) metadata.put("parents", org.json.JSONArray(listOf(parentId)))
        val url = "https://www.googleapis.com/upload/drive/v3/files" + (existingId?.let { "/$it" } ?: "") + "?uploadType=resumable&fields=id,name,appProperties"
        val initial = connection(if (existingId == null) "POST" else "PATCH", url)
        val location: String
        try {
            initial.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            initial.setRequestProperty("X-Upload-Content-Type", content.mime)
            initial.setRequestProperty("X-Upload-Content-Length", content.size.toString())
            val bytes = metadata.toString().toByteArray(Charsets.UTF_8)
            initial.doOutput = true; initial.setFixedLengthStreamingMode(bytes.size)
            initial.outputStream.use { it.write(bytes) }
            requireSuccess(initial)
            location = initial.getHeaderField("Location") ?: throw IOException("Drive no devolvió una sesión de carga.")
        } finally { initial.disconnect() }
        val upload = connection("PUT", location)
        try {
            upload.doOutput = true
            upload.setFixedLengthStreamingMode(content.size)
            upload.setRequestProperty("Content-Type", content.mime)
            if (content.size > 0) upload.setRequestProperty("Content-Range", "bytes 0-${content.size - 1}/${content.size}")
            content.open().use { input -> upload.outputStream.use { output ->
                val buffer = ByteArray(64 * 1024)
                var count = input.read(buffer)
                while (count >= 0) { checkActive(); output.write(buffer, 0, count); count = input.read(buffer) }
            } }
            requireSuccess(upload)
            val result = upload.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            return RemoteDriveFile(result.getString("id"), document.name, content.sha256)
        } finally { upload.disconnect() }
    }

    private fun json(method: String, url: String, body: JSONObject? = null): JSONObject {
        val connection = connection(method, url)
        try {
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setFixedLengthStreamingMode(bytes.size); connection.outputStream.use { it.write(bytes) }
            }
            requireSuccess(connection)
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }

    private fun connection(method: String, address: String): HttpsURLConnection {
        checkActive()
        val url = URL(address)
        require(url.protocol == "https" && url.host == "www.googleapis.com" && (url.port == -1 || url.port == 443)) { "Destino de Drive no válido." }
        return (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = method; instanceFollowRedirects = false; connectTimeout = 30_000; readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun requireSuccess(connection: HttpsURLConnection) {
        val status = connection.responseCode
        if (status !in 200..299) {
            val message = when (status) {
                401 -> "La autorización de Google venció. Volvé a conectar la cuenta."
                403 -> "Drive rechazó la operación. Revisá los permisos, el espacio y la habilitación de la aplicación."
                404 -> "El archivo o carpeta ya no está disponible en Drive. Reintentá la sincronización."
                429 -> "Google limitó temporalmente las cargas. Reintentá más tarde."
                else -> "No se pudo actualizar Drive (HTTP $status). Los archivos locales están conservados."
            }
            throw DriveHttpError(status, message)
        }
    }

    private fun properties(key: String) = JSONObject().put("gpNamespace", DRIVE_NAMESPACE).put("gpKey", key)

    private fun workspaceFolder(company: uy.gestionpersonal.data.Empresa): String {
        val root = folder("root", "Gestión de Personal")
        return folder("empresa:${company.name}", company.displayName, root)
    }

    fun workspaceRevisions(company: uy.gestionpersonal.data.Empresa): List<WorkspaceRevision> {
        val parent = workspaceFolder(company)
        val key = DriveDocument(company, "workspace", "", "workspace", "", "").key
        val query = "trashed=false and '$parent' in parents and appProperties has { key='gpNamespace' and value='$DRIVE_NAMESPACE' } and appProperties has { key='gpKey' and value='$key' }"
        val revisions = mutableListOf<WorkspaceRevision>()
        var page = ""
        do {
            val result = json("GET", "$api?q=${encode(query)}&fields=files(id,name,appProperties,sha256Checksum),nextPageToken&pageSize=1000" + if (page.isBlank()) "" else "&pageToken=${encode(page)}")
            val files = result.getJSONArray("files")
            (0 until files.length()).forEach { index -> val f = files.getJSONObject(index); val props = f.getJSONObject("appProperties"); val parents = (0..9).mapNotNull { props.optString("gpParent$it").takeIf(String::isNotBlank) }.toSet(); revisions.add(WorkspaceRevision(f.getString("id"), parents, f.getString("name"), f.optString("sha256Checksum"))) }
            page = result.optString("nextPageToken")
            require(revisions.size <= 10000) { "El historial supera el límite de esta versión" }
        } while (page.isNotBlank())
        return revisions
    }

    fun publishWorkspace(company: uy.gestionpersonal.data.Empresa, content: DriveContent, parents: Set<String>): WorkspaceRevision {
        require(parents.size <= 10)
        val name = "Datos-${company.name}-${java.time.Instant.now().toString().replace(':', '-')}-${java.util.UUID.randomUUID().toString().take(8)}.zip"
        val document = DriveDocument(company, "workspace", "Datos compartidos", "workspace", name, "")
        // Workspace keys are company-scoped, and every upload creates a new immutable file.
        val keyDocument = document.copy(slot = "workspace")
        val remote = uploadData(keyDocument, content, workspaceFolder(company), null, parents)
        return WorkspaceRevision(remote.id, parents, name, content.sha256)
    }

    fun downloadWorkspace(id: String, output: java.io.OutputStream) {
        safeId(id)
        val request = connection("GET", "$api/$id?alt=media")
        try { requireSuccess(request); request.inputStream.use { input -> val buffer = ByteArray(65536); var total = 0L; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= 200L * 1024 * 1024) { "La copia supera 200 MB" }; output.write(buffer, 0, n) } } }
        finally { request.disconnect() }
    }
    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun safeId(value: String) { require(value.matches(Regex("[A-Za-z0-9_-]+"))) { "Identificador de Drive no válido." } }
}

fun documentContent(context: Context, document: DriveDocument): DriveContent {
    val uri = Uri.parse(document.uri)
    require(uri.scheme == "content") { "Volvé a adjuntar el documento para poder sincronizarlo." }
    val hash = MessageDigest.getInstance("SHA-256")
    var size = 0L
    (context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo leer ${document.name}.")).use { input ->
        val buffer = ByteArray(64 * 1024)
        var count = input.read(buffer)
        while (count >= 0) { hash.update(buffer, 0, count); size += count; count = input.read(buffer) }
    }
    val sha = hash.digest().joinToString("") { "%02x".format(it) }
    return DriveContent(context.contentResolver.getType(uri) ?: "application/octet-stream", size, sha) {
        context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir ${document.name}.")
    }
}
