package uy.gestionpersonal.reports

import android.content.Context
import android.net.Uri
import uy.gestionpersonal.data.*
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun exportWorkerPackage(context: Context, output: OutputStream, worker: Worker, source: CompanyData) {
    require(source.workers.any { it.id == worker.id && it.empresa == worker.empresa })
    val deliveries = source.deliveries.filter { it.empresa == worker.empresa && it.workerId == worker.id }
    ZipOutputStream(output).use { zip ->
        fun bytes(name: String, writer: (OutputStream) -> Unit) {
            val buffer = ByteArrayOutputStream(); writer(buffer)
            zip.putNextEntry(ZipEntry(name)); zip.write(buffer.toByteArray()); zip.closeEntry()
        }
        bytes("Ficha.xlsx") { writeWorkerExcel(it, worker, source) }
        bytes("Legajo-completo.pdf") { exportCompleteWorkerPdf(context, it, worker, source) }
        deliveries.forEachIndexed { i, d -> bytes("Constancias/Entrega-${i + 1}.pdf") { exportDeliveryReceipt(it, d, worker) } }
        val originals = worker.attachments.map { it.name to it.uri } + deliveries.filter { it.receiptUri.isNotBlank() }.map { it.receiptName to it.receiptUri }
        originals.filter { it.second.startsWith("content://") }.forEachIndexed { index, (name, uri) ->
            val safeName = name.map { if (it.isLetterOrDigit() || it in " ._-()") it else '_' }.joinToString("").take(120).ifBlank { "Documento" }
            zip.putNextEntry(ZipEntry("Originales/${index + 1}-$safeName"))
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: error("No se pudo leer $name")
            input.use { it.copyTo(zip) }; zip.closeEntry()
        }
    }
}
