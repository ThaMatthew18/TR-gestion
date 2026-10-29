package uy.gestionpersonal.reports

import uy.gestionpersonal.data.*
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

enum class RosterScope(val label: String, val filename: String, val companies: List<Empresa>) {
    RR("R&R", "RR", listOf(Empresa.RR)),
    TRANSEO("TRANSEO", "TRANSEO", listOf(Empresa.TRANSEO)),
    BOTH("Ambas empresas", "RR_y_TRANSEO", Empresa.entries.toList())
}

data class GeneralRoster(val columns: List<String>, val rows: List<List<String>>)

/** The combined roster is explicit; ordinary single-company exports keep their scope. */
fun generalRoster(scope: RosterScope, snapshot: Map<Empresa, CompanyData>, today: LocalDate = LocalDate.now()): GeneralRoster {
    val combined = scope == RosterScope.BOTH
    val columns = (if (combined) listOf("Empresa") else emptyList()) + listOf("CI", "Nombre", "Apellido", "Obra", "Categoría", "Ingreso", "Egreso", "Tipo de aportación")
    val rows = scope.companies.flatMap { company ->
        snapshot[company]?.workers.orEmpty().filter { it.empresa == company }
            .sortedWith(compareBy<Worker> { it.fields["apellidos"].orEmpty().lowercase(Locale.ROOT) }.thenBy { it.fields["nombres"].orEmpty().lowercase(Locale.ROOT) }.thenBy { it.id })
            .map { worker ->
                val (start, end) = worker.employmentDates(today)
                (if (combined) listOf(company.displayName) else emptyList()) +
                    listOf(worker.fields["cedula"].orEmpty(), worker.fields["nombres"].orEmpty(), worker.fields["apellidos"].orEmpty(), worker.fields["proyecto"].orEmpty(), worker.fields["categoria"].orEmpty(), start, end, worker.fields["aportacion"].orEmpty())
            }
    }
    return GeneralRoster(columns, rows)
}

fun writeGeneralRosterExcel(output: OutputStream, scope: RosterScope, snapshot: Map<Empresa, CompanyData>, today: LocalDate = LocalDate.now()) {
    val roster = generalRoster(scope, snapshot, today)
    writeWorkbook(output, listOf("Empleados" to (listOf(roster.columns) + roster.rows)))
}
