package uy.gestionpersonal.reports

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import java.io.*
import java.util.zip.*

@RunWith(AndroidJUnit4::class)
class CompleteExportsTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory get() = File(context.filesDir, "documents").apply { mkdirs() }
    private val qa get() = File(context.filesDir, "qa").apply { mkdirs() }
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()

    private fun fixture(): Pair<Worker, CompanyData> {
        val scan = File(directory, "original-scan.pdf")
        val pdf = PdfDocument()
        try { (1..2).forEach { number ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, number).create())
            page.canvas.drawColor(Color.rgb(235, 245, 240)); page.canvas.drawText("DOCUMENTO ORIGINAL - PAGINA $number", 45f, 110f, Paint().apply { textSize = 22f; color = Color.BLACK }); pdf.finishPage(page)
        }; scan.outputStream().use { pdf.writeTo(it) } } finally { pdf.close() }
        val photo = File(directory, "original-image.png")
        val bitmap = Bitmap.createBitmap(500, 350, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(0, 117, 111)); Canvas(bitmap).drawText("FOTO ORIGINAL", 40f, 160f, Paint().apply { color = Color.WHITE; textSize = 35f })
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val worker = Worker("COMPLETE", Empresa.RR, mapOf("nombres" to "Ana", "apellidos" to "Pérez", "cedula" to "1234567", "proyecto" to "Proyecto extenso " + "con todos los detalles ".repeat(30) + "FIN_PROYECTO"), listOf(Attachment("scan", "Cédula", scan.name, uri(scan)), Attachment("photo", "Foto", photo.name, uri(photo))), (1..5).map { Reentry("r$it", "2026-01-01", notes = "Período $it completo") })
        val delivery = Delivery("signed", Empresa.RR, worker.id, DeliveryKind.EPP, "Casco de seguridad", "M", "2026-10-25", "1", true, "Observación " + "completa ".repeat(40) + "FIN_ENTREGA")
        val signature = ReceiptSignature("Ana Pérez", "1234567", "2026-10-25T12:00:00Z", delivery.signingHash(), listOf(listOf(InkPoint(.1f, .7f), InkPoint(.25f, .2f), InkPoint(.5f, .8f), InkPoint(.8f, .4f))))
        val training = Training("training", Empresa.RR, worker.id, "Capacitación completa", "2026-10-01", 12, "Observaciones de capacitación FIN_CHARLA")
        return worker to CompanyData(listOf(worker, Worker("foreign", Empresa.TRANSEO, mapOf("nombres" to "FOREIGN_COMPLETE"))), listOf(delivery.copy(signature = signature)), listOf(training))
    }

    @Test fun completePdfOriginalsAndSignedReceiptAreRealFilesWithAllPages() {
        val (worker, source) = fixture()
        val complete = File(qa, "legajo-completo.pdf")
        complete.outputStream().use { exportCompleteWorkerPdf(context, it, worker, source) }
        ParcelFileDescriptor.open(complete, ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { renderer -> assertTrue(renderer.pageCount >= 5) } }
        val receipt = File(qa, "constancia-firmada.pdf")
        receipt.outputStream().use { exportDeliveryReceipt(it, source.deliveries.single(), worker) }
        val unsigned = File(qa, "constancia-para-firmar.pdf")
        unsigned.outputStream().use { exportDeliveryReceipt(it, source.deliveries.single().copy(signature = null), worker) }
        val archive = File(qa, "legajo-originales.zip")
        archive.outputStream().use { exportWorkerPackage(context, it, worker, source) }
        ZipFile(archive).use { zip ->
            assertNotNull(zip.getEntry("Legajo-completo.pdf")); assertNotNull(zip.getEntry("Constancias/Entrega-1.pdf"))
            val original = zip.getInputStream(zip.getEntry("Originales/1-original-scan.pdf")).use { it.readBytes() }
            assertArrayEquals(File(directory, "original-scan.pdf").readBytes(), original)
            val excel = zip.getInputStream(zip.getEntry("Ficha.xlsx")).use { it.readBytes() }
            val xml = ZipInputStream(excel.inputStream()).use { book -> buildString { while (true) { book.nextEntry ?: break; append(book.readBytes().toString(Charsets.UTF_8)) } } }
            assertFalse(xml.contains("FOREIGN_COMPLETE")); assertTrue(xml.contains("FIN_PROYECTO"))
        }
    }

    @Test fun portableCompanyRoundTripRestoresDocumentsSignaturesAndReentriesWithoutTouchingOtherCompany() {
        val (worker, mixed) = fixture()
        val source = mixed.copy(workers = listOf(worker))
        val archive = ByteArrayOutputStream().also { writeCompanyArchive(context, it, Empresa.RR, source) }.toByteArray()
        val prepared = readCompanyArchive(context, archive.inputStream(), Empresa.RR)
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("kept-tr", Empresa.TRANSEO))
        repository.replaceCompany(Empresa.RR, repository.data(Empresa.RR), prepared.data)
        assertEquals("kept-tr", repository.data(Empresa.TRANSEO).workers.single().id)
        assertEquals(5, repository.data(Empresa.RR).workers.single().reentries.size)
        assertNotNull(repository.data(Empresa.RR).deliveries.single().validSignature())
        val storage = LocalStorage(context, "qa-signature-storage.json")
        storage.save(repository.snapshot())
        assertEquals(repository.snapshot(), storage.load())
        val restored = repository.data(Empresa.RR).workers.single()
        val original = File(directory, "original-scan.pdf").readBytes()
        context.contentResolver.openInputStream(android.net.Uri.parse(restored.attachments.first().uri)).use { assertArrayEquals(original, it!!.readBytes()) }
        val second = ByteArrayOutputStream().also { writeCompanyArchive(context, it, Empresa.RR, repository.data(Empresa.RR)) }.toByteArray()
        assertArrayEquals(archive, second)
        assertThrows(IllegalArgumentException::class.java) { readCompanyArchive(context, archive.inputStream(), Empresa.TRANSEO) }
        File(context.filesDir, "qa-signature-storage.json").delete()
    }

    @Test fun archiveRejectsPathTraversalAndMissingDocumentsBeforeChangingRecords() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("../outside")); it.write("x".toByteArray()); it.closeEntry() }
        assertThrows(IllegalArgumentException::class.java) { readCompanyArchive(context, bytes.toByteArray().inputStream(), Empresa.RR) }
        val (worker, source) = fixture()
        val archive = ByteArrayOutputStream().also { writeCompanyArchive(context, it, Empresa.RR, source.copy(workers = listOf(worker))) }.toByteArray()
        val missing = ByteArrayOutputStream()
        ZipInputStream(archive.inputStream()).use { input -> ZipOutputStream(missing).use { output -> while (true) { val entry = input.nextEntry ?: break; if (entry.name == "workspace.json") { output.putNextEntry(ZipEntry(entry.name)); output.write(input.readBytes()); output.closeEntry() } } } }
        assertThrows(IllegalStateException::class.java) { readCompanyArchive(context, missing.toByteArray().inputStream(), Empresa.RR) }
    }
}
