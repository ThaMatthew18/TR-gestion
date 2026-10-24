package uy.gestionpersonal.reports

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class ReportExportInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun officialGeographyHasEveryDepartmentAndOfflineCascade() {
        assertEquals(19, UruguayGeography.departments.size)
        assertEquals(671, UruguayGeography.departments.sumOf { UruguayGeography.localities(context, it).size })
        assertTrue("Las Piedras" in UruguayGeography.localities(context, "Canelones"))
        assertEquals(listOf("Montevideo", "Rural"), UruguayGeography.localities(context, "Montevideo"))
        assertTrue(UruguayGeography.localities(context, "Departamento inexistente").isEmpty())
    }

    @Test
    fun exportRealOnePagePdfAndCompanySeparatedWorkbooks() {
        val directory = File(context.filesDir, "qa").apply { mkdirs() }
        val image = File(directory, "foto-demostracion.png")
        val bitmap = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(217, 239, 235))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 117, 111) }
            drawCircle(180f, 90f, 49f, paint)
            drawRoundRect(95f, 146f, 265f, 265f, 35f, 35f, paint)
            paint.color = Color.WHITE; paint.textSize = 20f
            drawText("FOTO DEMO", 121f, 222f, paint)
        }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val worker = Worker("RR-2026-001", Empresa.RR, mapOf(
            "nombres" to "Marcela Valentina", "apellidos" to "Pérez Rodríguez", "cedula" to "4.123.456-7",
            "nacimiento" to "1988-05-19", "paisNacimiento" to "Uruguay", "paisDocumento" to "Uruguay",
            "carneSaludVencimiento" to "2027-06-30", "calle" to "Avenida 18 de Julio", "numero" to "1234", "bis" to "Apartamento 302",
            "departamento" to "Montevideo", "localidad" to "Montevideo", "telefono" to "+598 99 123 456", "email" to "marcela.perez@example.test",
            "hijosFonasa" to "Sí", "conyugeFonasa" to "No", "banco" to "Banco República", "tipoCuenta" to "Caja de ahorro", "numeroCuenta" to "123456789012",
            "talleRemera" to "M", "talleCamisa" to "M", "tallePantalon" to "40", "talleBuzo" to "M", "talleCampera" to "M", "talleCalzado" to "38",
            "aportacion" to "Aportación 4: Construcción (Ley 14.411)",
            "proyecto" to "Construcción y acondicionamiento del complejo administrativo Centro Sur — Etapa 2",
            "obraBps" to "123456789", "categoria" to "Oficial especializada en mantenimiento", "modalidad" to "A término hasta finalización de obra",
            "alta" to "2026-02-10", "baja" to ""
        ), (1..6).map { index -> Attachment("a$index", if (index < 3) "Cédula · Foto $index" else "Legajo", "Documento de demostración número $index.png", if (index < 3) Uri.fromFile(image).toString() else "") })
        val foreign = Worker("TR-001", Empresa.TRANSEO, mapOf("nombres" to "TRANSEO_ONLY_RECORD", "proyecto" to "Proyecto Norte"))
        val deliveries = (1..8).map { index -> Delivery("d$index", Empresa.RR, worker.id, if (index % 2 == 0) DeliveryKind.EPP else DeliveryKind.UNIFORME,
            listOf("Casco", "Remera", "Calzado de seguridad", "Protección auditiva")[index % 4], "M / 38", "2026-09-${index.toString().padStart(2, '0')}", "1", index % 2 == 0, "Registro de demostración con observación completa para exportación Excel.") }
        val trainings = (1..8).map { index -> Training("t$index", Empresa.RR, worker.id,
            listOf("Inducción Decreto 125/014", "Trabajo en Altura", "Riesgo Eléctrico", "Uso y conservación de EPP")[index % 4], "2026-01-15", 6, "Información completa de la capacitación $index.") }
        val source = CompanyData(listOf(worker, foreign), deliveries + Delivery("foreign-delivery", Empresa.TRANSEO, foreign.id, DeliveryKind.EPP, "TRANSEO_ITEM"), trainings)
        val pdf = File(directory, "ficha-rr-completa.pdf")
        pdf.outputStream().use { exportWorkerPdf(context, it, worker, source) }
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page -> assertEquals(595, page.width); assertEquals(842, page.height) }
            }
        }
        val rr = File(directory, "personal-rr.xlsx")
        val transeo = File(directory, "personal-transeo.xlsx")
        rr.outputStream().use { writeCompanyExcel(it, Empresa.RR, source, today = LocalDate.of(2026, 10, 23)) }
        transeo.outputStream().use { writeCompanyExcel(it, Empresa.TRANSEO, source, today = LocalDate.of(2026, 10, 23)) }
        fun xml(file: File): String = ZipFile(file).use { zip -> zip.entries().asSequence().joinToString("\n") { entry -> zip.getInputStream(entry).bufferedReader().use { it.readText() } } }
        val rrXml = xml(rr)
        val transeoXml = xml(transeo)
        assertFalse(rrXml.contains("TRANSEO_ONLY_RECORD"))
        assertFalse(rrXml.contains("TRANSEO_ITEM"))
        assertFalse(transeoXml.contains("Marcela"))
        assertTrue(transeoXml.contains("TRANSEO_ONLY_RECORD"))
        assertTrue(rrXml.contains("Etapa 2"))
        assertTrue(pdf.length() > 5000)
        val blankPdf = File(directory, "ficha-rr-vacia.pdf")
        blankPdf.outputStream().use { exportWorkerPdf(context, it, Worker("SIN-DATOS", Empresa.RR), source) }
        ParcelFileDescriptor.open(blankPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use { assertEquals(1, it.pageCount) } }
    }
}
