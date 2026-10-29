package uy.gestionpersonal.reports

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import uy.gestionpersonal.data.*
import java.io.OutputStream

/** Flow layout: keeps complete values and adds pages instead of abbreviating them. */
private class FlowPdf(private val title: String, private val subtitle: String) : AutoCloseable {
    val document = PdfDocument()
    private var page: PdfDocument.Page? = null
    private var number = 0
    private var y = 0f
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.rgb(20, 45, 66) }
    fun newPage() {
        finish(); number++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
        page!!.canvas.drawRect(0f, 0f, 595f, 91f, Paint().apply { color = Color.rgb(20, 45, 66) })
        val white = Paint(body).apply { color = Color.WHITE; textSize = 19f; typeface = Typeface.DEFAULT_BOLD }
        page!!.canvas.drawText(title, 38f, 42f, white)
        page!!.canvas.drawText(subtitle.take(100), 38f, 67f, Paint(body).apply { color = Color.WHITE; textSize = 9f })
        y = 119f
    }
    private fun finish() { page?.let { it.canvas.drawText("Gestión de Personal · Página $number", 38f, 814f, Paint(body).apply { textSize = 8f }); document.finishPage(it) }; page = null }
    private fun space(height: Float) { if (page == null || y + height > 780f) newPage() }
    fun line(value: String, bold: Boolean = false) {
        val paint = Paint(body).apply { if (bold) { typeface = Typeface.DEFAULT_BOLD; color = Color.rgb(0, 117, 111) } }
        value.replace('\t', ' ').lines().forEach { original ->
            var remaining = original.ifBlank { " " }
            while (remaining.isNotEmpty()) {
                space(15f)
                val max = paint.breakText(remaining, true, 519f, null).coerceAtLeast(1)
                val boundary = if (max < remaining.length) remaining.take(max).indexOfLast { it.isWhitespace() } else -1
                val count = if (boundary > 0) boundary else max
                page!!.canvas.drawText(remaining.take(count), 38f, y, paint); y += 15f
                remaining = remaining.drop(count).trimStart()
            }
        }
    }
    fun section(value: String) { space(45f); y += 12f; line(value, true) }
    fun signature(signature: ReceiptSignature?) {
        space(190f)
        val canvas = page!!.canvas
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = 1.6f }
        if (signature != null) signature.strokes.forEach { stroke -> stroke.zipWithNext().forEach { (a, b) -> canvas.drawLine(45f + a.x * 480f, y + a.y * 130f, 45f + b.x * 480f, y + b.y * 130f, ink) } }
        y += 145f; canvas.drawLine(38f, y, 557f, y, ink); y += 16f
        line(if (signature == null) "Firma del trabajador: __________________________" else "Firma capturada · ${signature.signedAt}")
    }
    fun image(bitmap: Bitmap) {
        newPage()
        val scale = minOf(519f / bitmap.width, 630f / bitmap.height)
        val w = bitmap.width * scale; val h = bitmap.height * scale
        page!!.canvas.drawBitmap(bitmap, null, RectF(38f + (519f - w) / 2, 126f, 38f + (519f + w) / 2, 126f + h), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)); y = 780f
    }
    fun write(output: OutputStream) { if (page == null && number == 0) newPage(); finish(); document.writeTo(output) }
    override fun close() { document.close() }
}

fun exportDeliveryReceipt(output: OutputStream, delivery: Delivery, worker: Worker?) {
    require(worker == null || (worker.empresa == delivery.empresa && worker.id == delivery.workerId))
    val signature = delivery.validSignature()
    FlowPdf("Constancia de entrega", delivery.empresa.displayName + " · " + delivery.id).use { pdf ->
        pdf.section(if (delivery.kind == DeliveryKind.EPP) "Equipos de protección personal" else "Uniformes")
        pdf.line("Trabajador: ${signature?.name ?: worker?.displayName ?: "Sin asignar"}")
        pdf.line("Documento: ${(signature?.document ?: worker?.fields?.get("cedula")).orEmpty().ifBlank { "Sin completar" }}")
        pdf.line("Fecha de entrega: ${delivery.date.ifBlank { "Sin completar" }}")
        pdf.section("Elementos entregados")
        pdf.line("Artículo: ${delivery.item.ifBlank { "Sin completar" }}")
        pdf.line("Cantidad: ${delivery.quantity.ifBlank { "Sin completar" }} · Talle / medida: ${delivery.size.ifBlank { "Sin completar" }}")
        pdf.line("Observaciones: ${delivery.notes.ifBlank { "Sin observaciones" }}")
        pdf.section("Recepción")
        pdf.line(if (signature != null) "El firmante confirmó la recepción en conformidad de los elementos detallados." else if (delivery.conformity) "Conformidad registrada manualmente. Firma pendiente en esta constancia." else "Recepción pendiente de conformidad. Documento para completar y firmar.")
        pdf.signature(signature)
        pdf.line("Aclaración: ${signature?.name.orEmpty()}")
        pdf.line("Documento: ${signature?.document.orEmpty()}")
        pdf.line("Entregado por: __________________________")
        pdf.write(output)
    }
}

fun exportCompleteWorkerPdf(context: Context, output: OutputStream, worker: Worker, source: CompanyData) {
    FlowPdf("Legajo completo", worker.empresa.displayName + " · " + worker.id).use { pdf ->
        pdf.section(worker.displayName)
        workerExportFields.forEach { (key, label) -> pdf.line("$label: ${workerExportValue(worker, key).ifBlank { "Sin completar" }}") }
        (worker.fields.keys - workerExportFields.map { it.first }.toSet()).sorted().forEach { pdf.line("$it: ${worker.fields[it]}") }
        pdf.section("Historial de reingresos")
        if (worker.reentryHistory.isEmpty()) pdf.line("Sin reingresos registrados.")
        worker.reentryHistory.forEachIndexed { i, r -> pdf.line("${i + 1}. Entrada: ${r.date.ifBlank { "Sin fecha" }} · Baja: ${r.endDate.ifBlank { "Sin registrar" }}"); pdf.line("Observaciones: ${r.notes.ifBlank { "Sin observaciones" }}") }
        pdf.section("Entregas de uniformes y EPP")
        val deliveries = source.deliveries.filter { it.empresa == worker.empresa && it.workerId == worker.id }
        if (deliveries.isEmpty()) pdf.line("Sin entregas registradas.")
        deliveries.forEach { d ->
            pdf.section("${d.date.ifBlank { "Sin fecha" }} · ${d.item.ifBlank { "Sin artículo" }}")
            pdf.line("${d.kind} · Cantidad: ${d.quantity} · Talle: ${d.size}")
            pdf.line("Conformidad: ${if (d.conformity) "Sí" else "Pendiente"}")
            pdf.line("Observaciones: ${d.notes.ifBlank { "Sin observaciones" }}")
            d.validSignature()?.let { pdf.line("Firmante: ${it.name} · Documento: ${it.document}"); pdf.signature(it) }
        }
        pdf.section("Capacitaciones")
        val trainings = source.trainings.filter { it.empresa == worker.empresa && it.workerId == worker.id }
        if (trainings.isEmpty()) pdf.line("Sin capacitaciones registradas.")
        trainings.forEach { t -> pdf.section(t.title.ifBlank { "Sin título" }); pdf.line("Fecha: ${t.date} · Vigencia: ${t.validityMonths?.let { "$it meses" } ?: "Sin vencimiento"}"); pdf.line("Vencimiento: ${t.expiryDate() ?: "Sin vencimiento"} · Estado: ${reportStatusLabel(t.status())}"); pdf.line("Observaciones: ${t.notes.ifBlank { "Sin observaciones" }}") }
        val attachments = worker.attachments.map { Triple(it.kind, it.name, it.uri) } + deliveries.filter { it.receiptUri.isNotBlank() }.map { Triple("Constancia de entrega", it.receiptName, it.receiptUri) }
        pdf.section("Documentos adjuntos")
        attachments.forEach { (kind, name, uri) ->
            pdf.section("$kind · $name")
            if (uri.isBlank() || !uri.startsWith("content://")) pdf.line("Ejemplo sin archivo vinculado.")
            else {
                val parsed = Uri.parse(uri)
                val mime = context.contentResolver.getType(parsed).orEmpty()
                if (mime == "application/pdf" || name.endsWith(".pdf", true)) {
                    val descriptor = context.contentResolver.openFileDescriptor(parsed, "r") ?: error("No se pudo leer $name")
                    descriptor.use { PdfRenderer(it).use { renderer ->
                        (0 until renderer.pageCount).forEach { index -> renderer.openPage(index).use { page ->
                            val width = 1500; val height = (page.height.toLong() * width / page.width).coerceIn(1, 4500).toInt()
                            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try { image.eraseColor(Color.WHITE); page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); pdf.image(image) } finally { image.recycle() }
                        } }
                    } }
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El adjunto $name no es una imagen o PDF legible" }
                    val options = BitmapFactory.Options().apply { while (bounds.outWidth / inSampleSize.coerceAtLeast(1) > 2000 || bounds.outHeight / inSampleSize.coerceAtLeast(1) > 3000) inSampleSize = inSampleSize.coerceAtLeast(1) * 2 }
                    val image = context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, options) } ?: error("No se pudo leer $name")
                    try { pdf.image(image) } finally { image.recycle() }
                }
            }
        }
        pdf.write(output)
    }
}
