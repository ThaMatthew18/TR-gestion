package uy.gestionpersonal.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*

private const val MAX_ARCHIVE = 200L * 1024 * 1024

/** Portable company copy. Content-addressed documents make the archive independent of local URIs. */
fun writeCompanyArchive(context: Context, output: OutputStream, company: Empresa, source: CompanyData) {
    require(source.workers.all { it.empresa == company } && source.deliveries.all { it.empresa == company } && source.trainings.all { it.empresa == company })
    val temporary = mutableListOf<File>()
    val documents = sortedMapOf<String, File>()
    val mapped = mutableMapOf<String, String>()
    var total = 0L
    fun portable(uri: String): String {
        if (!uri.startsWith("content://")) return ""
        return mapped.getOrPut(uri) {
            val file = File.createTempFile("document-", ".tmp", context.cacheDir).also { temporary.add(it) }
            val hash = MessageDigest.getInstance("SHA-256")
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: error("No se pudo leer un documento. No se creó la copia.")
            input.use { from -> file.outputStream().use { to -> val buffer = ByteArray(65536); while (true) { val n = from.read(buffer); if (n < 0) break; total += n; require(total <= MAX_ARCHIVE) { "La copia supera 200 MB" }; hash.update(buffer, 0, n); to.write(buffer, 0, n) } } }
            val key = hash.digest().joinToString("") { "%02x".format(it) }
            documents.putIfAbsent(key, file)
            "archive:$key"
        }
    }
    try {
        val portable = source.copy(workers = source.workers.map { w -> w.copy(attachments = w.attachments.map { it.copy(uri = portable(it.uri)) }) }, deliveries = source.deliveries.map { it.copy(receiptUri = portable(it.receiptUri)) })
        val snapshot = Empresa.entries.associateWith { if (it == company) portable else CompanyData() }
        val manifest = JSONObject().put("format", 1).put("company", company.name).put("data", encodeSnapshot(snapshot))
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, input: InputStream) { zip.putNextEntry(ZipEntry(name).apply { time = 0 }); input.use { it.copyTo(zip) }; zip.closeEntry() }
            entry("workspace.json", canonicalJson(manifest).toByteArray(Charsets.UTF_8).inputStream())
            documents.forEach { (key, file) -> entry("documents/$key", file.inputStream()) }
        }
    } finally { temporary.forEach { it.delete() } }
}

private fun canonicalJson(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonicalJson(value.get(it)) }
    is org.json.JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonicalJson(value.get(it)) }
    null, JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    else -> value.toString()
}

class PreparedCompany(val data: CompanyData, private val imported: List<File>) {
    fun discard() { imported.forEach { it.delete() } }
}

/** Validates every entry before returning any record; extracts only generated filenames. */
fun readCompanyArchive(context: Context, input: InputStream, company: Empresa): PreparedCompany {
    val temporary = mutableMapOf<String, File>()
    val imported = mutableListOf<File>()
    var manifest: JSONObject? = null
    var total = 0L
    val seen = mutableSetOf<String>()
    try {
        ZipInputStream(input).use { zip -> while (true) {
            val entry = zip.nextEntry ?: break
            require(seen.add(entry.name) && seen.size <= 20000) { "Copia con entradas duplicadas o excesivas" }
            require(entry.name == "workspace.json" || entry.name.matches(Regex("documents/[a-f0-9]{64}"))) { "Entrada de copia no válida" }
            val file = File.createTempFile("restore-", ".tmp", context.cacheDir)
            temporary[entry.name] = file
            file.outputStream().use { out -> val buffer = ByteArray(65536); while (true) { val n = zip.read(buffer); if (n < 0) break; total += n; require(total <= MAX_ARCHIVE); out.write(buffer, 0, n) } }
            if (entry.name == "workspace.json") { require(file.length() <= 20L * 1024 * 1024); manifest = JSONObject(file.readText()) }
            else { val hash = MessageDigest.getInstance("SHA-256"); file.inputStream().use { stream -> val buffer = ByteArray(65536); while (true) { val n = stream.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }; require(hash.digest().joinToString("") { "%02x".format(it) } == entry.name.substringAfter('/')) { "Documento dañado en la copia" } }
        } }
        val json = manifest ?: error("La copia no tiene datos")
        require(json.getInt("format") == 1 && json.getString("company") == company.name) { "La copia pertenece a otra empresa" }
        val snapshot = decodeSnapshot(json.getJSONObject("data"))
        require(snapshot.filterKeys { it != company }.values.all { it == CompanyData() }) { "La copia mezcla empresas" }
        val data = snapshot.getValue(company)
        require(data.workers.map { it.id }.distinct().size == data.workers.size && data.deliveries.map { it.id }.distinct().size == data.deliveries.size && data.trainings.map { it.id }.distinct().size == data.trainings.size) { "La copia contiene identificadores repetidos" }
        val restored = mutableMapOf<String, String>()
        fun local(uri: String, name: String): String {
            if (uri.isBlank()) return ""
            require(uri.matches(Regex("archive:[a-f0-9]{64}"))) { "Referencia de documento no válida" }
            return restored.getOrPut(uri) {
                val file = temporary["documents/${uri.substringAfter(':')}"] ?: error("Falta un documento en la copia")
                val directory = File(context.filesDir, "documents").apply { check(exists() || mkdirs()) }
                val extension = name.substringAfterLast('.', "bin").lowercase().takeIf { it in setOf("pdf", "png", "jpg", "jpeg", "webp", "gif", "bmp") } ?: "bin"
                val target = File(directory, "${UUID.randomUUID()}.$extension").also { imported.add(it) }
                file.copyTo(target)
                FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString()
            }
        }
        val result = data.copy(workers = data.workers.map { w -> w.copy(attachments = w.attachments.map { it.copy(uri = local(it.uri, it.name)) }) }, deliveries = data.deliveries.map { it.copy(receiptUri = local(it.receiptUri, it.receiptName)) })
        MemoryRepository(seedDemoData = false, initialData = Empresa.entries.associateWith { if (it == company) result else CompanyData() })
        return PreparedCompany(result, imported)
    } catch (failure: Exception) { imported.forEach { it.delete() }; throw failure }
    finally { temporary.values.forEach { it.delete() } }
}
