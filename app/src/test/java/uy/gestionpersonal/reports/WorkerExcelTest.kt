package uy.gestionpersonal.reports

import org.junit.Assert.*
import org.junit.Test
import uy.gestionpersonal.data.*
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class WorkerExcelTest {
    @Test fun individualWorkbookIncludesReentryAndOnlyTheSelectedWorkersHistory() {
        val worker = Worker("same-id", Empresa.RR, mapOf("nombres" to "CHOSEN_PERSON")).withReentries(listOf(Reentry("r1", "2026-05-01", "2026-06-01", "FIRST_PERIOD"), Reentry("r2", "2026-10-20", notes = "SECOND_PERIOD")))
        val source = CompanyData(
            listOf(worker, Worker("other", Empresa.RR, mapOf("nombres" to "OTHER_PERSON"), reentries = listOf(Reentry("hidden", notes = "OTHER_REENTRY"))), Worker(worker.id, Empresa.TRANSEO, mapOf("nombres" to "FOREIGN_PERSON"), reentries = listOf(Reentry("foreign", notes = "FOREIGN_REENTRY")))),
            listOf(Delivery("a", Empresa.RR, worker.id, DeliveryKind.EPP, "CHOSEN_ITEM"), Delivery("b", Empresa.TRANSEO, worker.id, DeliveryKind.EPP, "FOREIGN_ITEM"), Delivery("c", Empresa.RR, "other", DeliveryKind.EPP, "OTHER_ITEM"), Delivery("d", Empresa.RR, "", DeliveryKind.EPP, "UNASSIGNED_ITEM")),
            listOf(Training("t1", Empresa.RR, worker.id, "CHOSEN_TRAINING"), Training("t2", Empresa.TRANSEO, worker.id, "FOREIGN_TRAINING"))
        )
        val output = ByteArrayOutputStream()
        writeWorkerExcel(output, worker, source)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
        }
        val xml = entries.values.joinToString()
        listOf("CHOSEN_PERSON", "CHOSEN_ITEM", "CHOSEN_TRAINING", "Tiene fecha de reingreso", "2026-10-20", "FIRST_PERIOD", "SECOND_PERIOD").forEach { assertTrue(it, xml.contains(it)) }
        listOf("OTHER_PERSON", "FOREIGN_PERSON", "FOREIGN_ITEM", "OTHER_ITEM", "UNASSIGNED_ITEM", "FOREIGN_TRAINING", "OTHER_REENTRY", "FOREIGN_REENTRY").forEach { assertFalse(it, xml.contains(it)) }
        assertEquals(5, entries.keys.count { it.startsWith("xl/worksheets/") })
    }

    @Test fun blankWorkerStillExportsAndAnAbsentWorkerIsRejected() {
        val blank = Worker("blank", Empresa.RR)
        writeWorkerExcel(ByteArrayOutputStream(), blank, CompanyData(listOf(blank)))
        assertThrows(IllegalArgumentException::class.java) { writeWorkerExcel(ByteArrayOutputStream(), blank, CompanyData()) }
    }
}
