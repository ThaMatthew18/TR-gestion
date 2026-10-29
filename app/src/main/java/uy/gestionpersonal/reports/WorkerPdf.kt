package uy.gestionpersonal.reports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import uy.gestionpersonal.data.*
import java.io.OutputStream
import java.time.LocalDate

/** A4 overview with extra pages when the reentry history needs more space. */
fun exportWorkerPdf(context: Context, outputStream: OutputStream, worker: Worker, companyData: CompanyData) {
    val deliveries = companyData.deliveries.filter { it.empresa == worker.empresa && it.workerId == worker.id }
    val trainings = companyData.trainings.filter { it.empresa == worker.empresa && it.workerId == worker.id }
    val thumbnails = worker.attachments.asSequence().mapNotNull { attachment ->
        if (attachment.uri.isBlank()) null else decodeThumbnail(context, attachment.uri)
    }.take(3).toList()
    val document = PdfDocument()
    try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val navy = Color.rgb(20, 45, 66)
        val teal = Color.rgb(0, 117, 111)
        val muted = Color.rgb(89, 106, 119)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 8.7f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        val heading = Paint(body).apply { textSize = 10f; typeface = Typeface.create("sans-serif", Typeface.BOLD); color = teal }
        val history = worker.reentryHistory
        val appendix = history.size > 3 || history.any { it.notes.isNotBlank() }
        fun wrap(value: String): List<String> = value.lines().flatMap { line ->
            buildList {
                var remaining = line.replace('\t', ' ')
                if (remaining.isBlank()) add("")
                while (remaining.isNotEmpty()) {
                    val maximum = body.breakText(remaining, true, 510f, null).coerceAtLeast(1)
                    val wordBreak = if (maximum < remaining.length) remaining.take(maximum).indexOfLast { it.isWhitespace() } else -1
                    val count = if (wordBreak > 0) wordBreak else maximum
                    add(remaining.take(count)); remaining = remaining.drop(count).trimStart()
                }
            }
        }
        val historyPages = if (appendix) history.flatMapIndexed { index, entry ->
            listOf("Reingreso ${index + 1} | Entrada: ${entry.date.ifBlank { "Sin fecha" }} | Baja: ${entry.endDate.ifBlank { "Sin registrar" }}") +
                wrap("Observaciones: ${entry.notes.ifBlank { "Sin observaciones" }}") + listOf("")
        }.chunked(42) else emptyList()
        val pageCount = 1 + historyPages.size
        fun text(value: String, x: Float, y: Float, width: Float = 519f, paint: Paint = body) {
            val clean = value.replace(Regex("[\\r\\n\\t]+"), " ").ifBlank { "—" }
            val safe = if (paint.measureText(clean) <= width) clean else {
                val count = paint.breakText(clean, true, (width - paint.measureText("…")).coerceAtLeast(0f), null)
                clean.take(count) + "…"
            }
            canvas.drawText(safe, x, y, paint)
        }
        fun section(title: String, fields: List<Pair<String, String>>, x: Float, top: Float): Float {
            text(title, x, top, 249f, heading)
            var y = top + 18f
            fields.forEach { (label, value) -> text("$label: ${value.ifBlank { "Sin dato" }}", x, y, 249f); y += 13f }
            return y + 12f
        }
        fun values(vararg keys: String): List<Pair<String, String>> = keys.map { key ->
            (workerExportFields.find { it.first == key }?.second ?: key) to workerExportValue(worker, key)
        }
        val background = Paint().apply { color = navy }
        canvas.drawRect(0f, 0f, 595f, 91f, background)
        val whiteTitle = Paint(heading).apply { color = Color.WHITE; textSize = 22f }
        text("Ficha de personal", 38f, 43f, 400f, whiteTitle)
        text(worker.empresa.displayName + " · Legajo individual", 38f, 67f, 519f, Paint(body).apply { color = Color.WHITE; textSize = 10f })
        text(worker.displayName, 38f, 119f, 519f, Paint(heading).apply { color = navy; textSize = 16f })
        text("ID: ${worker.id}  ·  Emitido: ${LocalDate.now()}", 38f, 138f, 519f, Paint(body).apply { color = muted; textSize = 8f })
        var leftY = section("01  Identificación y salud", values("cedula", "nacimiento", "paisNacimiento", "paisDocumento", "carneSaludVencimiento"), 38f, 167f)
        leftY = section("02  Contacto y dirección", values("calle", "numero", "bis", "departamento", "localidad", "telefono", "email"), 38f, leftY)
        section("03  Fonasa", values("hijosFonasa", "conyugeFonasa"), 38f, leftY)
        var rightY = section("04  Datos bancarios", values("banco", "tipoCuenta", "numeroCuenta"), 307f, 167f)
        rightY = section("05  Talles", values("talleRemera", "talleCamisa", "tallePantalon", "talleBuzo", "talleCampera", "talleCalzado"), 307f, rightY)
        section("06  Relación laboral", listOf("Empresa contratante" to worker.empresa.displayName) + values("aportacion", "proyecto", "obraBps", "categoria", "modalidad", "alta", "baja", "tieneReingreso", "reingreso"), 307f, rightY)

        text("Historial de reingresos (${history.size})", 38f, 450f, 249f, heading)
        if (history.isEmpty()) text("Sin reingresos registrados.", 38f, 468f, 249f)
        history.take(3).forEachIndexed { index, entry ->
            text("${index + 1}. ${entry.date.ifBlank { "Sin fecha" }} · Baja: ${entry.endDate.ifBlank { "Sin registrar" }}", 38f, 468f + index * 13f, 249f)
        }
        if (appendix) text("Historial completo y observaciones en anexo.", 38f, 510f, 249f, Paint(body).apply { color = muted; textSize = 8f })

        text("Adjuntos · ${worker.attachments.size} referencia(s)", 38f, 529f, 519f, heading)
        val attachmentNames = worker.attachments.map { "${it.kind}: ${it.name}" }
        if (attachmentNames.isEmpty()) text("Sin adjuntos registrados.", 38f, 548f, 322f)
        attachmentNames.take(4).forEachIndexed { index, name -> text(name, 38f, 548f + index * 12f, 322f) }
        if (attachmentNames.size > 4) text("+ ${attachmentNames.size - 4} referencias adicionales en Excel.", 38f, 596f, 322f)
        thumbnails.forEachIndexed { index, bitmap ->
            val left = 376f + index * 61f
            val scale = minOf(56f / bitmap.width, 56f / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            val target = RectF(left + (56f - width) / 2, 540f + (56f - height) / 2, left + (56f + width) / 2, 540f + (56f + height) / 2)
            canvas.drawBitmap(bitmap, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        if (thumbnails.isNotEmpty()) text("${thumbnails.size} miniatura(s) disponible(s)", 376f, 607f, 181f, Paint(body).apply { textSize = 7f; color = muted })
        else text("Sin miniaturas disponibles", 376f, 553f, 181f, Paint(body).apply { textSize = 7f; color = muted })

        text("Seguridad · Uniformes y EPP (${deliveries.size})", 38f, 627f, 249f, heading)
        if (deliveries.isEmpty()) text("Sin entregas registradas.", 38f, 646f, 249f)
        deliveries.take(5).forEachIndexed { index, item ->
            text("${item.date.ifBlank { "Sin fecha" }} · ${item.item.ifBlank { "Sin detalle" }} · ${if (item.conformity) "Conforme" else "Sin conformidad"}", 38f, 646f + index * 13f, 249f)
        }
        if (deliveries.size > 5) text("+ ${deliveries.size - 5} entregas adicionales en Excel.", 38f, 716f, 249f)
        text("Capacitaciones (${trainings.size})", 307f, 627f, 249f, heading)
        if (trainings.isEmpty()) text("Sin capacitaciones registradas.", 307f, 646f, 249f)
        trainings.take(5).forEachIndexed { index, training ->
            val status = training.status()
            val expiry = training.expiryDate()?.toString()
                ?: if (status == TrainingStatus.SIN_FECHA) "Sin fecha válida" else "Sin vencimiento"
            text("${training.title.ifBlank { "Sin título" }} · ${reportStatusLabel(status)} · $expiry", 307f, 646f + index * 13f, 249f)
        }
        if (trainings.size > 5) text("+ ${trainings.size - 5} capacitaciones adicionales en Excel.", 307f, 716f, 249f)
        canvas.drawLine(38f, 750f, 557f, 750f, Paint().apply { color = Color.LTGRAY; strokeWidth = 1f })
        val footer = Paint(body).apply { color = muted; textSize = 8f }
        text("Resumen de 1 página: valores largos e históricos extensos se abrevian. Excel conserva los datos completos.", 38f, 769f, 519f, footer)
        text("Adjuntos: nombres y hasta 3 miniaturas disponibles. Los archivos originales se conservan por separado.", 38f, 784f, 519f, footer)
        text("Gestión de Personal · ${worker.empresa.displayName} · 1 / $pageCount", 38f, 814f, 519f, footer)
        document.finishPage(page)
        historyPages.forEachIndexed { index, lines ->
            val historyPage = document.startPage(PdfDocument.PageInfo.Builder(595, 842, index + 2).create())
            val target = historyPage.canvas
            target.drawRect(0f, 0f, 595f, 91f, background)
            target.drawText("Historial de reingresos", 38f, 43f, whiteTitle)
            target.drawText("${worker.empresa.displayName} · ID: ${worker.id}", 38f, 67f, Paint(body).apply { color = Color.WHITE })
            val titleCount = heading.breakText(worker.displayName, true, 519f, null)
            target.drawText(worker.displayName.take(titleCount), 38f, 119f, heading)
            lines.forEachIndexed { row, line -> target.drawText(line, 38f, 150f + row * 14f, body) }
            target.drawLine(38f, 788f, 557f, 788f, Paint().apply { color = Color.LTGRAY })
            target.drawText("Gestión de Personal · ${worker.empresa.displayName} · ${index + 2} / $pageCount", 38f, 814f, footer)
            document.finishPage(historyPage)
        }
        document.writeTo(outputStream)
    } finally {
        document.close()
        thumbnails.forEach(Bitmap::recycle)
    }
}

private fun decodeThumbnail(context: Context, value: String): Bitmap? = runCatching {
    val uri = Uri.parse(value)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    val options = BitmapFactory.Options().apply {
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        inSampleSize = sample
    }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}.getOrNull()
