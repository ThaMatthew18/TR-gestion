package uy.gestionpersonal.data

import org.junit.Assert.*
import org.junit.Test

class ReceiptSignatureTest {
    private val delivery = Delivery("receipt", Empresa.RR, "person", DeliveryKind.EPP, "Casco", "M", "2026-10-25", "1", true, "Notas")
    private fun signature(d: Delivery = delivery) = ReceiptSignature("Ana", "123", "2026-10-25T12:00:00Z", d.signingHash(), listOf(listOf(InkPoint(.1f, .2f), InkPoint(.8f, .7f))))

    @Test fun capturedSignatureIsBoundToEveryDeliveryDetail() {
        val signed = delivery.copy(signature = signature())
        assertNotNull(signed.validSignature())
        listOf(signed.copy(id = "other"), signed.copy(empresa = Empresa.TRANSEO), signed.copy(workerId = "other"), signed.copy(kind = DeliveryKind.UNIFORME), signed.copy(item = "Guantes"), signed.copy(size = "L"), signed.copy(date = "2026-10-26"), signed.copy(quantity = "2"), signed.copy(notes = "Cambio"), signed.copy(conformity = false)).forEach { assertNull(it.validSignature()) }
        assertNotNull(signed.copy(receiptName = "Adjunto.pdf", receiptUri = "content://receipt").validSignature())
    }

    @Test fun dotsEmptyStrokesAndInvalidCoordinatesCannotCountAsASignature() {
        assertFalse(signature().copy(strokes = emptyList()).hasInk())
        assertFalse(signature().copy(strokes = listOf(listOf(InkPoint(.2f, .2f)))).hasInk())
        assertFalse(signature().copy(strokes = signature().strokes + listOf(listOf(InkPoint(Float.NaN, 0f)))).hasInk())
    }

    @Test fun repositoryDropsASignatureAfterAnEditAndDoesNotMoveTheDelivery() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("person", Empresa.RR))
        repository.saveDelivery(delivery.copy(signature = signature()))
        assertNotNull(repository.data(Empresa.RR).deliveries.single().signature)
        repository.saveDelivery(repository.data(Empresa.RR).deliveries.single().copy(quantity = "2"))
        assertNull(repository.data(Empresa.RR).deliveries.single().signature)
        assertFalse(repository.data(Empresa.RR).deliveries.single().conformity)
        assertTrue(repository.data(Empresa.TRANSEO).deliveries.isEmpty())
    }

    @Test fun failedOrConcurrentRestoreKeepsTheCurrentRecords() {
        val repository = MemoryRepository(seedDemoData = false)
        val before = repository.data(Empresa.RR)
        repository.saveWorker(Worker("local", Empresa.RR))
        assertThrows(IllegalStateException::class.java) { repository.replaceCompany(Empresa.RR, before, CompanyData()) }
        val initial = mapOf(Empresa.RR to CompanyData(listOf(Worker("kept", Empresa.RR))), Empresa.TRANSEO to CompanyData())
        val failing = MemoryRepository(initialData = initial, onChanged = { error("Disk full") })
        assertThrows(IllegalStateException::class.java) { failing.replaceCompany(Empresa.RR, failing.data(Empresa.RR), CompanyData()) }
        assertEquals("kept", failing.data(Empresa.RR).workers.single().id)
    }
}
