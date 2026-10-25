package uy.gestionpersonal.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReentryTest {
    private val today = LocalDate.of(2026, 10, 25)
    private fun worker(vararg fields: Pair<String, String>) = Worker("test", Empresa.RR, mapOf(*fields))

    @Test fun missingAnswerDefaultsToNoAndLegacyDateIsPreserved() {
        assertEquals("No", worker().reentryAnswer)
        assertEquals("Sí", worker("reingreso" to "2026-10-20").reentryAnswer)
        assertEquals("Sí", workerFieldsForSave(mapOf("reingreso" to "2026-10-20"))["tieneReingreso"])
    }

    @Test fun selectingNoClearsTheDateAndYesAllowsAnOptionalBlankDate() {
        val fields = mapOf("tieneReingreso" to "No", "reingreso" to "2026-10-20")
        assertFalse(workerFieldsForSave(fields).containsKey("reingreso"))
        assertEquals("", Worker("test", Empresa.RR, fields).reentryDateText)
        assertEquals("Sí", workerFieldsForSave(mapOf("tieneReingreso" to "Sí"))["tieneReingreso"])
    }

    @Test fun reentryReactivatesOnlyAfterTheLatestTerminationAndWhenItsDateArrives() {
        assertTrue(worker("baja" to "2026-09-01", "tieneReingreso" to "Sí", "reingreso" to "2026-10-25").isActive(today))
        assertFalse(worker("baja" to "2026-09-01", "tieneReingreso" to "Sí", "reingreso" to "2026-10-26").isActive(today))
        assertFalse(worker("baja" to "2026-10-24", "tieneReingreso" to "Sí", "reingreso" to "2026-10-20").isActive(today))
        assertFalse(worker("baja" to "2026-09-01", "tieneReingreso" to "No", "reingreso" to "2026-10-20").isActive(today))
    }

    @Test fun multiplePeriodsKeepIdentityAndUseTheLatestPeriodThatHasStarted() {
        val original = worker("baja" to "2026-03-01", "reingreso" to "2026-04-01")
        val history = original.reentryHistory.map { it.copy(endDate = "2026-05-01") } +
            Reentry("second", "2026-06-01", "2026-09-01") + Reentry("third", "2026-10-20") + Reentry("future", "2026-12-01")
        val saved = original.withReentries(history)
        assertEquals(original.id, saved.id)
        assertEquals(4, saved.reentryHistory.size)
        assertEquals("2026-04-01", saved.reentryHistory.first().date)
        assertFalse(saved.isActive(LocalDate.of(2026, 10, 19)))
        assertTrue(saved.isActive(today))
        assertFalse(saved.withReentries(history.map { if (it.id == "third") it.copy(endDate = "2026-10-24") else it }).isActive(today))
    }

    @Test fun removingTheMigratedEntryDoesNotBringBackTheOldSingleDate() {
        val original = worker("tieneReingreso" to "Sí", "reingreso" to "2026-10-20")
        assertEquals(1, original.reentryHistory.size)
        val cleared = original.withReentries(emptyList())
        assertFalse(cleared.hasReentry)
        assertFalse(cleared.fields.containsKey("reingreso"))
    }

    @Test fun mostRecentDatedPeriodIsUsedRegardlessOfInsertionOrder() {
        val saved = worker("baja" to "2026-01-01").withReentries(listOf(Reentry("new", "2026-09-01", "2026-10-01"), Reentry("old", "2026-03-01"), Reentry("blank")))
        assertFalse(saved.isActive(today))
        assertEquals("2026-09-01", saved.reentryDateText)
    }
}
