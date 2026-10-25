package uy.gestionpersonal.drive

import uy.gestionpersonal.data.*
import java.io.InputStream
import java.security.MessageDigest

const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"
const val DRIVE_NAMESPACE = "gestion-personal-v1"

data class DriveDocument(val empresa: Empresa, val ownerId: String, val ownerName: String, val slot: String, val name: String, val uri: String) {
    val key: String get() = digest("${empresa.name}|$ownerId|$slot".toByteArray())
}

fun driveDocuments(empresa: Empresa, source: CompanyData): List<DriveDocument> {
    val workers = source.workers.filter { it.empresa == empresa }.associateBy { it.id }
    val documents = workers.values.flatMap { worker ->
        worker.attachments.filter { it.uri.isNotBlank() }.map { attachment ->
            val slot = if (attachment.kind in setOf("cedula_frente", "cedula_dorso", "carne_frente", "carne_dorso")) attachment.kind else "documento:${attachment.id}"
            DriveDocument(empresa, worker.id, worker.displayName, slot, attachment.name.ifBlank { "Documento" }, attachment.uri)
        }
    } + source.deliveries.filter { it.empresa == empresa && it.receiptUri.isNotBlank() && (it.workerId.isEmpty() || it.workerId in workers) }.map { delivery ->
        DriveDocument(empresa, delivery.workerId.ifEmpty { "sin-asignar" }, workers[delivery.workerId]?.displayName ?: "Sin trabajador asignado", "constancia:${delivery.id}", delivery.receiptName.ifBlank { "Constancia de entrega" }, delivery.receiptUri)
    }
    require(documents.map { it.key }.distinct().size == documents.size) { "Hay documentos duplicados en un mismo espacio del legajo." }
    return documents
}

data class DriveContent(val mime: String, val size: Long, val sha256: String, val open: () -> InputStream)
data class RemoteDriveFile(val id: String, val name: String, val sha256: String)
data class DriveSyncResult(val uploaded: Int, val unchanged: Int, val folderId: String)

interface DriveGateway {
    fun folder(key: String, name: String, parentId: String? = null): String
    fun find(key: String, parentId: String): RemoteDriveFile?
    fun upload(document: DriveDocument, content: DriveContent, parentId: String, existingId: String?): RemoteDriveFile
}

/** Always re-queries the selected account. No file IDs or tokens are shared across accounts. */
fun syncDocuments(empresa: Empresa, documents: List<DriveDocument>, gateway: DriveGateway, content: (DriveDocument) -> DriveContent, progress: (Int, Int) -> Unit = { _, _ -> }): DriveSyncResult {
    require(documents.all { it.empresa == empresa }) { "Los documentos pertenecen a otra empresa." }
    require(documents.map { it.key }.distinct().size == documents.size)
    val root = gateway.folder("root", "Gestión de Personal")
    val companyFolder = gateway.folder("empresa:${empresa.name}", empresa.displayName, root)
    val owners = mutableMapOf<String, String>()
    var uploaded = 0
    var unchanged = 0
    documents.forEachIndexed { index, document ->
        val parent = owners.getOrPut(document.ownerId) {
            gateway.folder("${empresa.name}:persona:${document.ownerId}", document.ownerName.take(100) + " · " + document.ownerId.take(8), companyFolder)
        }
        val payload = content(document)
        val remote = gateway.find(document.key, parent)
        if (remote != null && remote.sha256 == payload.sha256 && remote.name == document.name) unchanged++
        else { gateway.upload(document, payload, parent, remote?.id); uploaded++ }
        progress(index + 1, documents.size)
    }
    return DriveSyncResult(uploaded, unchanged, companyFolder)
}

internal fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
