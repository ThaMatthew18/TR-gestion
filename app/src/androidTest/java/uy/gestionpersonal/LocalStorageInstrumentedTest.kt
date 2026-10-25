package uy.gestionpersonal

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uy.gestionpersonal.data.*
import uy.gestionpersonal.drive.*
import java.io.File
import java.net.URL
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class LocalStorageInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun metadataSurvivesRepositoryRecreationWithCompanyIsolation() {
        val storage = LocalStorage(context, "qa-persistence.json")
        val source = MemoryRepository(seedDemoData = false, onChanged = storage::save)
        val worker = Worker("qa-rr", Empresa.RR, mapOf("nombres" to "Ana", "banco" to "Banco de prueba", "calle" to "Dirección de ejemplo"), listOf(Attachment("a", "cedula_frente", "Cédula.pdf", "content://test/a")))
        source.saveWorker(worker)
        source.saveWorker(Worker("qa-tr", Empresa.TRANSEO))
        source.saveDelivery(Delivery("delivery", Empresa.RR, worker.id, DeliveryKind.EPP, conformity = true, receiptName = "Conforme.pdf", receiptUri = "content://test/b"))
        source.saveTraining(Training("training", Empresa.TRANSEO, "qa-tr", "Charla", "2026-10-24", null))
        val restored = MemoryRepository(seedDemoData = false, initialData = storage.load())
        assertEquals(source.snapshot(), restored.snapshot())
        assertEquals(listOf("qa-rr"), restored.data(Empresa.RR).workers.map { it.id })
        assertEquals(listOf("qa-tr"), restored.data(Empresa.TRANSEO).workers.map { it.id })
        File(context.filesDir, "qa-persistence.json").delete()
    }

    @Test fun importedDocumentRemainsReadableAfterTheOriginalIsRemoved() {
        val directory = File(context.filesDir, "documents").apply { mkdirs() }
        val original = File(directory, "qa-source.pdf").apply { writeBytes("PDF de prueba".toByteArray()) }
        val originalUri = FileProvider.getUriForFile(context, "${context.packageName}.files", original)
        val imported = importDocument(context, originalUri)
        original.delete()
        val plan = DriveDocument(Empresa.RR, "qa", "Prueba", "cedula_frente", imported.name, imported.uri)
        val content = documentContent(context, plan)
        content.open().use { assertEquals("PDF de prueba", it.readBytes().toString(Charsets.UTF_8)) }
        assertEquals(64, content.sha256.length)
        assertEquals("application/pdf", content.mime)
    }

    @Test fun androidTransportSupportsDrivePatchRequestsWithoutOpeningANetworkConnection() {
        val connection = URL("https://www.googleapis.com/drive/v3/files").openConnection() as HttpsURLConnection
        try { connection.requestMethod = "PATCH"; assertEquals("PATCH", connection.requestMethod) }
        finally { connection.disconnect() }
    }
}
