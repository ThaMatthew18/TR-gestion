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
}
