package uy.gestionpersonal.drive

import org.junit.Assert.*
import org.junit.Test
import uy.gestionpersonal.data.*

class DriveSyncTest {
    private fun doc(company: Empresa = Empresa.RR, uri: String = "content://test/first") = DriveDocument(company, "worker-1", "Persona de prueba", "cedula_frente", "Cédula.pdf", uri)
    private fun content(value: String) = DriveContent("application/pdf", value.toByteArray().size.toLong(), digest(value.toByteArray())) { value.byteInputStream() }

    private class FakeDrive : DriveGateway {
        val files = mutableMapOf<String, RemoteDriveFile>()
        val folders = mutableMapOf<String, String>()
        val uploads = mutableListOf<Pair<String, String?>>()
        override fun folder(key: String, name: String, parentId: String?): String = folders.getOrPut("$parentId|$key") { "folder${folders.size}" }
        override fun find(key: String, parentId: String): RemoteDriveFile? = files["$parentId|$key"]
        override fun upload(document: DriveDocument, content: DriveContent, parentId: String, existingId: String?): RemoteDriveFile {
            uploads += document.key to existingId
            content.open().use { assertEquals(content.size, it.readBytes().size.toLong()) }
            return RemoteDriveFile(existingId ?: "file${files.size}", document.name, content.sha256).also { files["$parentId|${document.key}"] = it }
        }
    }

    @Test fun filesAreScopedByCompanyAndByTheirWorker() {
        val rr = Worker("same-id", Empresa.RR, attachments = listOf(Attachment("a", "cedula_frente", "RR.pdf", "content://rr")))
        val other = Worker("foreign", Empresa.TRANSEO, attachments = listOf(Attachment("b", "cedula_frente", "TR.pdf", "content://tr")))
        val source = CompanyData(listOf(rr, other), listOf(
            Delivery("own", Empresa.RR, rr.id, DeliveryKind.EPP, receiptUri = "content://own"),
            Delivery("foreign", Empresa.TRANSEO, other.id, DeliveryKind.EPP, receiptUri = "content://foreign"),
            Delivery("invalid-link", Empresa.RR, other.id, DeliveryKind.EPP, receiptUri = "content://bad")))
        assertEquals(setOf("content://rr", "content://own"), driveDocuments(Empresa.RR, source).map { it.uri }.toSet())
    }

    @Test fun replacingAnAttachmentKeepsItsRemoteSlotEvenWithANewAttachmentId() {
        fun plan(id: String, uri: String) = driveDocuments(Empresa.RR, CompanyData(workers = listOf(Worker("worker", Empresa.RR, attachments = listOf(Attachment(id, "cedula_frente", "Documento.pdf", uri)))))).single()
        assertEquals(plan("old", "content://old").key, plan("new", "content://new").key)
        assertNotEquals(doc().key, doc(Empresa.TRANSEO).key)
    }

    @Test fun anIdenticalFileIsNotUploadedAgain() {
        val drive = FakeDrive()
        assertEquals(1, syncDocuments(Empresa.RR, listOf(doc()), drive, { content("v1") }).uploaded)
        val repeated = syncDocuments(Empresa.RR, listOf(doc()), drive, { content("v1") })
        assertEquals(0, repeated.uploaded); assertEquals(1, repeated.unchanged); assertEquals(1, drive.uploads.size)
    }

    @Test fun contentChangesUpdateTheExistingFileInsteadOfCreatingCopies() {
        val drive = FakeDrive()
        syncDocuments(Empresa.RR, listOf(doc()), drive, { content("v1") })
        val id = drive.files.values.single().id
        syncDocuments(Empresa.RR, listOf(doc(uri = "content://replacement")), drive, { content("v2") })
        assertEquals(id, drive.uploads.last().second); assertEquals(1, drive.files.size)
    }

    @Test fun changingAccountNeverUsesThePreviousAccountsFileIdentifiers() {
        val first = FakeDrive(); val second = FakeDrive()
        syncDocuments(Empresa.RR, listOf(doc()), first, { content("data") })
        syncDocuments(Empresa.RR, listOf(doc()), second, { content("data") })
        assertNull(second.uploads.single().second)
    }

    @Test fun eachCompanyGetsItsOwnFolderAndDocument() {
        val drive = FakeDrive()
        syncDocuments(Empresa.RR, listOf(doc()), drive, { content("RR") })
        syncDocuments(Empresa.TRANSEO, listOf(doc(Empresa.TRANSEO)), drive, { content("TRANSEO") })
        assertEquals(2, drive.files.size)
        assertTrue(drive.folders.keys.any { it.endsWith("empresa:RR") })
        assertTrue(drive.folders.keys.any { it.endsWith("empresa:TRANSEO") })
    }

    @Test fun crossCompanyBatchesAreRejectedBeforeAnyRemoteMutation() {
        val drive = FakeDrive()
        try { syncDocuments(Empresa.RR, listOf(doc(Empresa.TRANSEO)), drive, { content("bad") }); fail("Debe rechazar la otra empresa") }
        catch (_: IllegalArgumentException) { assertTrue(drive.folders.isEmpty()); assertTrue(drive.files.isEmpty()) }
    }

    @Test fun examplesWithoutLinkedFilesAreNotTreatedAsDocumentsToUpload() {
        val repository = MemoryRepository()
        assertTrue(driveDocuments(Empresa.RR, repository.data(Empresa.RR)).isEmpty())
        assertTrue(driveDocuments(Empresa.TRANSEO, repository.data(Empresa.TRANSEO)).isEmpty())
    }

    @Test fun failedUploadsDoNotReportSuccess() {
        val fake = FakeDrive()
        val failing = object : DriveGateway by fake {
            override fun upload(document: DriveDocument, content: DriveContent, parentId: String, existingId: String?): RemoteDriveFile = throw java.io.IOException("Sin conexión")
        }
        var progress = 0
        try { syncDocuments(Empresa.RR, listOf(doc()), failing, { content("v1") }) { done, _ -> progress = done }; fail("Debe propagar el error") }
        catch (_: java.io.IOException) { assertEquals(0, progress); assertTrue(fake.files.isEmpty()) }
    }
}
