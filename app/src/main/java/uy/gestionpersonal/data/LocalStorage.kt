package uy.gestionpersonal.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Durable metadata, written atomically; the previous file is retained if a write fails. */
class LocalStorage(context: Context, name: String = "personnel-v1.json") {
    private val file = AtomicFile(File(context.filesDir, name))

    fun load(): Map<Empresa, CompanyData>? {
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        val root = file.openRead().bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        require(root.getInt("version") == 1) { "Versión de datos no compatible" }
        return Empresa.entries.associateWith { company ->
            val data = root.getJSONObject(company.name)
            CompanyData(
                data.getJSONArray("workers").objects().map { value ->
                    val fields = value.getJSONObject("fields")
                    Worker(value.getString("id"), company, fields.keys().asSequence().associateWith { fields.getString(it) },
                        value.getJSONArray("attachments").objects().map { a -> Attachment(a.getString("id"), a.getString("kind"), a.getString("name"), a.getString("uri")) },
                        value.optJSONArray("reentries")?.objects()?.map { r -> Reentry(r.getString("id"), r.optString("date"), r.optString("endDate"), r.optString("notes")) }.orEmpty())
                },
                data.getJSONArray("deliveries").objects().map { d -> Delivery(d.getString("id"), company, d.getString("workerId"), DeliveryKind.valueOf(d.getString("kind")), d.getString("item"), d.getString("size"), d.getString("date"), d.getString("quantity"), d.getBoolean("conformity"), d.getString("notes"), d.getString("receiptName"), d.getString("receiptUri")) },
                data.getJSONArray("trainings").objects().map { t -> Training(t.getString("id"), company, t.getString("workerId"), t.getString("title"), t.getString("date"), if (t.isNull("months")) null else t.getInt("months"), t.getString("notes")) }
            )
        }
    }

    fun save(snapshot: Map<Empresa, CompanyData>) {
        val root = JSONObject().put("version", 1)
        Empresa.entries.forEach { company ->
            val data = snapshot.getValue(company)
            root.put(company.name, JSONObject()
                .put("workers", JSONArray(data.workers.map { w -> JSONObject().put("id", w.id).put("fields", JSONObject(w.fields)).put("attachments", JSONArray(w.attachments.map { a -> JSONObject().put("id", a.id).put("kind", a.kind).put("name", a.name).put("uri", a.uri) })).put("reentries", JSONArray(w.reentries.map { r -> JSONObject().put("id", r.id).put("date", r.date).put("endDate", r.endDate).put("notes", r.notes) })) }))
                .put("deliveries", JSONArray(data.deliveries.map { d -> JSONObject().put("id", d.id).put("workerId", d.workerId).put("kind", d.kind.name).put("item", d.item).put("size", d.size).put("date", d.date).put("quantity", d.quantity).put("conformity", d.conformity).put("notes", d.notes).put("receiptName", d.receiptName).put("receiptUri", d.receiptUri) }))
                .put("trainings", JSONArray(data.trainings.map { t -> JSONObject().put("id", t.id).put("workerId", t.workerId).put("title", t.title).put("date", t.date).put("months", t.validityMonths ?: JSONObject.NULL).put("notes", t.notes) })))
        }
        val stream = file.startWrite()
        try { stream.write(root.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)

data class ImportedDocument(val name: String, val uri: String)

/** The copy remains readable even if the user moves the original or revokes its provider grant. */
fun importDocument(context: Context, source: Uri): ImportedDocument {
    val name = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Documento"
    val directory = File(context.filesDir, "documents").apply { check(exists() || mkdirs()) }
    val safeName = name.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_").take(100).ifBlank { "documento" }
    val destination = File(directory, "${UUID.randomUUID()}_$safeName")
    try {
        val input = context.contentResolver.openInputStream(source) ?: error("No se pudo abrir el archivo")
        input.use { from -> destination.outputStream().use { to -> from.copyTo(to) } }
        return ImportedDocument(name, FileProvider.getUriForFile(context, "${context.packageName}.files", destination).toString())
    } catch (error: Exception) { destination.delete(); throw error }
}
