package uy.gestionpersonal.reports

import org.junit.Assert.*
import org.junit.Test
import uy.gestionpersonal.data.*
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class GeneralRosterTest {
    private val today = LocalDate.of(2026, 10, 29)
    private val rr = Worker("rr", Empresa.RR, mapOf("cedula" to "00123456", "nombres" to "Ana María", "apellidos" to "Pérez", "proyecto" to "Obra Sur", "categoria" to "Oficial", "alta" to "2026-01-01", "baja" to "2026-05-01", "aportacion" to "Aportación 4", "banco" to "PRIVATE_BANK"))
    private val tr = Worker("tr", Empresa.TRANSEO, mapOf("cedula" to "87654321", "nombres" to "Luis", "apellidos" to "García", "proyecto" to "Obra Norte"))
    private val snapshot = mapOf(Empresa.RR to CompanyData(listOf(rr)), Empresa.TRANSEO to CompanyData(listOf(tr)))

    @Test fun singleCompanyHasExactlyTheRequestedEightColumnsAndOneRowPerEmployee() {
        val roster = generalRoster(RosterScope.RR, snapshot, today)
        assertEquals(listOf("CI", "Nombre", "Apellido", "Obra", "Categoría", "Ingreso", "Egreso", "Tipo de aportación"), roster.columns)
        assertEquals(listOf(listOf("00123456", "Ana María", "Pérez", "Obra Sur", "Oficial", "2026-01-01", "2026-05-01", "Aportación 4")), roster.rows)
        assertEquals("Luis", generalRoster(RosterScope.TRANSEO, snapshot, today).rows.single()[1])
    }

    @Test fun combinedExportAddsCompanyWithoutDroppingPeopleWithTheSameCi() {
        val sameCi = snapshot + (Empresa.TRANSEO to CompanyData(listOf(tr.copy(fields = tr.fields + ("cedula" to "00123456")))) )
        val roster = generalRoster(RosterScope.BOTH, sameCi, today)
        assertEquals("Empresa", roster.columns.first())
        assertEquals(9, roster.columns.size)
        assertEquals(listOf("R&R", "TRANSEO"), roster.rows.map { it.first() })
        assertEquals(listOf("00123456", "00123456"), roster.rows.map { it[1] })
    }

    @Test fun latestStartedPeriodIsUsedAndFutureAndUndatedReentriesDoNotReplaceIt() {
        val worker = rr.withReentries(listOf(Reentry("old", "2026-06-01", "2026-07-01"), Reentry("current", "2026-09-01"), Reentry("future", "2026-12-01"), Reentry("blank")))
        val source = snapshot + (Empresa.RR to CompanyData(listOf(worker)))
        val row = generalRoster(RosterScope.RR, source, today).rows.single()
        assertEquals("2026-09-01", row[5]); assertEquals("", row[6])
        val ended = worker.withReentries(worker.reentryHistory.map { if (it.id == "current") it.copy(endDate = "2026-10-20") else it })
        assertEquals("2026-10-20", generalRoster(RosterScope.RR, source + (Empresa.RR to CompanyData(listOf(ended))), today).rows.single()[6])
    }

    @Test fun blankWorkersAreIncludedButWrongCompanyRecordsAreNot() {
        val source = mapOf(Empresa.RR to CompanyData(listOf(Worker("blank", Empresa.RR), tr)))
        val roster = generalRoster(RosterScope.RR, source, today)
        assertEquals(listOf(List(8) { "" }), roster.rows)
        assertTrue(generalRoster(RosterScope.TRANSEO, emptyMap(), today).rows.isEmpty())
    }

    @Test fun excelPlacesHeadersInFirstRowAndEmployeesBelowAsLiteralText() {
        val output = ByteArrayOutputStream()
        writeGeneralRosterExcel(output, RosterScope.BOTH, snapshot, today)
        val entries = mutableMapOf<String, String>()
        ZipInputStream(output.toByteArray().inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8) } }
        val sheet = entries.getValue("xl/worksheets/sheet1.xml")
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(sheet.byteInputStream())
        val rows = xml.getElementsByTagName("row")
        assertEquals(3, rows.length)
        assertEquals("Empresa", rows.item(0).firstChild.textContent)
        assertEquals("R&R", rows.item(1).firstChild.textContent)
        assertEquals("TRANSEO", rows.item(2).firstChild.textContent)
        assertTrue(sheet.contains("00123456")); assertFalse(sheet.contains("PRIVATE_BANK"))
        assertTrue(sheet.contains("topLeftCell=\"A2\"")); assertTrue(sheet.contains("autoFilter ref=\"A1:I3\""))
        assertEquals(1, entries.keys.count { it.startsWith("xl/worksheets/") })
        assertFalse(sheet.contains("<f>"))
    }
}
