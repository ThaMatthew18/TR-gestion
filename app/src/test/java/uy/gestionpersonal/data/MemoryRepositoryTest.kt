package uy.gestionpersonal.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MemoryRepositoryTest {
    private val today = LocalDate.of(2026, 10, 23)

    private fun repositoryWithWorkers() = MemoryRepository(seedDemoData = false).apply {
        saveWorker(Worker("rr-worker", Empresa.RR))
        saveWorker(Worker("transeo-worker", Empresa.TRANSEO))
    }

    private fun trainingExpiringOn(date: LocalDate) = Training(
        "training", Empresa.RR, date = date.minusMonths(6).toString(), validityMonths = 6
    )

    @Test fun emptyRepositoryCreatesBothIndependentCompanies() {
        val repository = MemoryRepository(seedDemoData = false)
        assertEquals(CompanyData(), repository.data(Empresa.RR))
        assertEquals(CompanyData(), repository.data(Empresa.TRANSEO))
    }

    @Test fun blankUserFieldsAndMissingAttachmentsNeverBlockSave() {
        val repository = MemoryRepository(seedDemoData = false)
        val worker = Worker("blank", Empresa.RR)
        val delivery = Delivery("blank-delivery", Empresa.RR, kind = DeliveryKind.EPP)
        val training = Training("blank-training", Empresa.RR)
        repository.saveWorker(worker)
        repository.saveDelivery(delivery)
        repository.saveTraining(training)
        assertEquals(listOf(worker), repository.data(Empresa.RR).workers)
        assertEquals(listOf(delivery), repository.data(Empresa.RR).deliveries)
        assertEquals(listOf(training), repository.data(Empresa.RR).trainings)
        assertEquals(CompanyData(), repository.data(Empresa.TRANSEO))
    }

    @Test fun workerDisplayNameTrimsFieldsAndHasFallback() {
        assertEquals("Trabajador sin nombre", Worker("blank", Empresa.RR).displayName)
        assertEquals("Juan Pérez", Worker("name", Empresa.RR, mapOf("nombres" to " Juan ", "apellidos" to " Pérez ")).displayName)
        assertEquals("Juan", Worker("first", Empresa.RR, mapOf("nombres" to "Juan")).displayName)
    }

    @Test fun workersAreReadAndUpdatedOnlyInsideTheirCompany() {
        val repository = repositoryWithWorkers()
        val otherCompany = repository.data(Empresa.TRANSEO)
        repository.saveWorker(Worker("rr-worker", Empresa.RR, mapOf("nombres" to "Carlos")))
        assertEquals("Carlos", repository.data(Empresa.RR).workers.single().displayName)
        assertEquals(otherCompany, repository.data(Empresa.TRANSEO))
    }

    @Test fun workerIdCannotMoveToAnotherCompany() {
        val repository = repositoryWithWorkers()
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveWorker(Worker("rr-worker", Empresa.TRANSEO))
        }
        assertEquals("rr-worker", repository.data(Empresa.RR).workers.single().id)
        assertEquals("transeo-worker", repository.data(Empresa.TRANSEO).workers.single().id)
    }

    @Test fun savedFieldsAndAttachmentsDoNotShareMutableCallerCollections() {
        val repository = MemoryRepository(seedDemoData = false)
        val fields = mutableMapOf("nombres" to "Carlos")
        val attachments = mutableListOf(Attachment("document", "cedula_frente", "Documento demo", "content://demo"))
        repository.saveWorker(Worker("worker", Empresa.RR, fields, attachments))
        fields["nombres"] = "Modified externally"
        attachments.clear()
        assertEquals("Carlos", repository.data(Empresa.RR).workers.single().displayName)
        assertEquals(1, repository.data(Empresa.RR).workers.single().attachments.size)
    }

    @Test fun attachmentIdCannotBeReusedByAnotherCompany() {
        val repository = repositoryWithWorkers()
        val document = Attachment("rr-document", "cedula_frente", "Documento demo", "content://demo")
        repository.saveWorker(Worker("rr-worker", Empresa.RR, attachments = listOf(document)))
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveWorker(Worker("transeo-worker", Empresa.TRANSEO, attachments = listOf(document)))
        }
        assertTrue(repository.data(Empresa.TRANSEO).workers.single().attachments.isEmpty())
    }

    @Test fun deliveryCannotReferenceWorkerFromAnotherCompany() {
        val repository = repositoryWithWorkers()
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveDelivery(Delivery("delivery", Empresa.RR, "transeo-worker", DeliveryKind.EPP))
        }
        assertTrue(repository.data(Empresa.RR).deliveries.isEmpty())
    }

    @Test fun deliveryCannotReferenceUnknownWorker() {
        val repository = repositoryWithWorkers()
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveDelivery(Delivery("delivery", Empresa.RR, "unknown", DeliveryKind.UNIFORME))
        }
    }

    @Test fun existingDeliveryCannotMoveCompanyOrChangeToForeignWorker() {
        val repository = repositoryWithWorkers()
        val delivery = Delivery("delivery", Empresa.RR, "rr-worker", DeliveryKind.UNIFORME)
        repository.saveDelivery(delivery)
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveDelivery(delivery.copy(empresa = Empresa.TRANSEO, workerId = "transeo-worker"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveDelivery(delivery.copy(workerId = "transeo-worker"))
        }
        assertEquals(listOf(delivery), repository.data(Empresa.RR).deliveries)
        assertTrue(repository.data(Empresa.TRANSEO).deliveries.isEmpty())
    }

    @Test fun trainingCannotReferenceWorkerFromAnotherCompanyOrUnknownWorker() {
        val repository = repositoryWithWorkers()
        listOf("transeo-worker", "unknown").forEach { workerId ->
            assertThrows(IllegalArgumentException::class.java) {
                repository.saveTraining(Training("training", Empresa.RR, workerId))
            }
        }
        assertTrue(repository.data(Empresa.RR).trainings.isEmpty())
    }

    @Test fun existingTrainingCannotMoveCompanyOrChangeToForeignWorker() {
        val repository = repositoryWithWorkers()
        val training = Training("training", Empresa.RR, "rr-worker")
        repository.saveTraining(training)
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveTraining(training.copy(empresa = Empresa.TRANSEO, workerId = "transeo-worker"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.saveTraining(training.copy(workerId = "transeo-worker"))
        }
        assertEquals(listOf(training), repository.data(Empresa.RR).trainings)
        assertTrue(repository.data(Empresa.TRANSEO).trainings.isEmpty())
    }

    @Test fun identicalIdsInDifferentRecordTypesRemainIndependent() {
        val repository = MemoryRepository(seedDemoData = false)
        repository.saveWorker(Worker("shared-id", Empresa.RR))
        val delivery = Delivery("shared-id", Empresa.TRANSEO, kind = DeliveryKind.EPP)
        val training = Training("shared-id", Empresa.TRANSEO)
        repository.saveDelivery(delivery)
        repository.saveTraining(training)
        repository.deleteWorker(Empresa.RR, "shared-id")
        assertTrue(repository.data(Empresa.RR).workers.isEmpty())
        assertEquals(listOf(delivery), repository.data(Empresa.TRANSEO).deliveries)
        assertEquals(listOf(training), repository.data(Empresa.TRANSEO).trainings)
    }

    @Test fun deletingWorkerCascadesOnlyTheirOwnCompanyRecords() {
        val repository = repositoryWithWorkers()
        repository.saveDelivery(Delivery("rr-delivery", Empresa.RR, "rr-worker", DeliveryKind.UNIFORME))
        repository.saveDelivery(Delivery("rr-unassigned", Empresa.RR, kind = DeliveryKind.EPP))
        repository.saveTraining(Training("rr-training", Empresa.RR, "rr-worker"))
        repository.saveTraining(Training("rr-unassigned-training", Empresa.RR))
        repository.saveDelivery(Delivery("transeo-delivery", Empresa.TRANSEO, "transeo-worker", DeliveryKind.EPP))
        repository.saveTraining(Training("transeo-training", Empresa.TRANSEO, "transeo-worker"))
        val otherCompany = repository.data(Empresa.TRANSEO)
        repository.deleteWorker(Empresa.RR, "rr-worker")
        assertTrue(repository.data(Empresa.RR).workers.isEmpty())
        assertEquals(listOf("rr-unassigned"), repository.data(Empresa.RR).deliveries.map { it.id })
        assertEquals(listOf("rr-unassigned-training"), repository.data(Empresa.RR).trainings.map { it.id })
        assertEquals(otherCompany, repository.data(Empresa.TRANSEO))
    }

    @Test fun wrongCompanyAndMissingWorkerDeletesHaveNoEffect() {
        val repository = repositoryWithWorkers()
        repository.saveDelivery(Delivery("rr-unassigned", Empresa.RR, kind = DeliveryKind.EPP))
        repository.saveTraining(Training("rr-unassigned-training", Empresa.RR))
        val rrBefore = repository.data(Empresa.RR)
        val transeoBefore = repository.data(Empresa.TRANSEO)
        repository.deleteWorker(Empresa.RR, "transeo-worker")
        repository.deleteWorker(Empresa.RR, "")
        repository.deleteDelivery(Empresa.TRANSEO, "rr-unassigned")
        repository.deleteTraining(Empresa.TRANSEO, "rr-unassigned-training")
        assertEquals(rrBefore, repository.data(Empresa.RR))
        assertEquals(transeoBefore, repository.data(Empresa.TRANSEO))
    }

    @Test fun deliveryAndTrainingUpdatesReplaceOnlyMatchingRecord() {
        val repository = repositoryWithWorkers()
        repository.saveDelivery(Delivery("delivery", Empresa.RR, "rr-worker", DeliveryKind.EPP))
        repository.saveTraining(Training("training", Empresa.RR, "rr-worker"))
        repository.saveDelivery(Delivery("delivery", Empresa.RR, "rr-worker", DeliveryKind.EPP, item = "Casco"))
        repository.saveTraining(Training("training", Empresa.RR, "rr-worker", title = "Inducción"))
        assertEquals("Casco", repository.data(Empresa.RR).deliveries.single().item)
        assertEquals("Inducción", repository.data(Empresa.RR).trainings.single().title)
        repository.deleteDelivery(Empresa.RR, "delivery")
        repository.deleteTraining(Empresa.RR, "training")
        assertTrue(repository.data(Empresa.RR).deliveries.isEmpty())
        assertTrue(repository.data(Empresa.RR).trainings.isEmpty())
    }

    @Test fun missingOrMalformedTrainingDateHasNeutralStatus() {
        listOf("", "not-a-date", "2026-02-30").forEach { date ->
            val training = Training("training", Empresa.RR, date = date, validityMonths = 6)
            assertEquals(TrainingStatus.SIN_FECHA, training.status(today))
            assertNull(training.expiryDate())
        }
    }

    @Test fun trainingWithoutExpirationStaysCurrentAfterItWasTaught() {
        val training = Training("training", Empresa.RR, date = "2020-01-01")
        assertEquals(TrainingStatus.VIGENTE, training.status(today))
        assertNull(training.expiryDate())
    }

    @Test fun trainingIsExpiredOnlyAfterItsExpirationDay() {
        assertEquals(TrainingStatus.VENCIDA, trainingExpiringOn(today.minusDays(1)).status(today))
        assertEquals(TrainingStatus.POR_VENCER, trainingExpiringOn(today).status(today))
    }

    @Test fun warningThresholdIncludesDay30AndExcludesDay31() {
        assertEquals(TrainingStatus.POR_VENCER, trainingExpiringOn(today.plusDays(30)).status(today))
        assertEquals(TrainingStatus.VIGENTE, trainingExpiringOn(today.plusDays(31)).status(today))
    }

    @Test fun futureTrainingIsScheduledAndNeverIndicatesCompletedCompliance() {
        listOf<Int?>(null, 6, 12, 24).forEach { months ->
            val training = Training("future", Empresa.RR, date = today.plusDays(1).toString(), validityMonths = months)
            assertEquals(TrainingStatus.PROGRAMADA, training.status(today))
        }
        assertEquals(TrainingStatus.VIGENTE, Training("today", Empresa.RR, date = today.toString(), validityMonths = 6).status(today))
    }

    @Test fun invalidValidityIsNeutralAndLeapYearUsesCalendarMonths() {
        assertEquals(TrainingStatus.SIN_FECHA, Training("invalid", Empresa.RR, date = "2026-01-01", validityMonths = -1).status(today))
        assertEquals(LocalDate.of(2025, 2, 28), Training("leap", Empresa.RR, date = "2024-02-29", validityMonths = 12).expiryDate())
    }

    @Test fun generatedIdsAreValidDistinctUuids() {
        val first = newId()
        val second = newId()
        assertNotEquals(first, second)
        assertEquals(first, java.util.UUID.fromString(first).toString())
    }

    @Test fun demoPeopleAndAllTheirRecordsBelongToTheirOwnCompany() {
        val repository = MemoryRepository()
        assertEquals(listOf("Juan Pérez", "Carlos López"), repository.data(Empresa.RR).workers.map { it.displayName })
        assertEquals(listOf("Martín Rodríguez"), repository.data(Empresa.TRANSEO).workers.map { it.displayName })
        Empresa.entries.forEach { company ->
            val data = repository.data(company)
            assertTrue(data.workers.all { it.empresa == company })
            assertTrue(data.deliveries.all { record -> record.empresa == company && data.workers.any { it.id == record.workerId } })
            assertTrue(data.trainings.all { record -> record.empresa == company && data.workers.any { it.id == record.workerId } })
        }
        assertEquals("cedula_frente", repository.data(Empresa.RR).workers.first().attachments.single().kind)
        assertEquals("carne_frente", repository.data(Empresa.TRANSEO).workers.single().attachments.single().kind)
    }
}
