package uy.gestionpersonal.data

import android.content.Context
import java.text.Collator
import java.util.Locale

/** Offline cascade from INE's 2023 census codifier. See docs/fuentes-geografia.md. */
object UruguayGeography {
    val departments: List<String> = listOf(
        "Artigas", "Canelones", "Cerro Largo", "Colonia", "Durazno", "Flores",
        "Florida", "Lavalleja", "Maldonado", "Montevideo", "Paysandú", "Río Negro",
        "Rivera", "Rocha", "Salto", "San José", "Soriano", "Tacuarembó", "Treinta y Tres"
    )

    @Volatile private var cache: Map<String, List<String>>? = null

    fun localities(context: Context, department: String): List<String> =
        (cache ?: synchronized(this) {
            cache ?: load(context.applicationContext).also { cache = it }
        })[department].orEmpty()

    private fun load(context: Context): Map<String, List<String>> {
        val records = context.assets.open("localidades_ine.csv").bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().drop(1).filter(String::isNotBlank).map { line ->
                val columns = line.split(';', limit = 4)
                require(columns.size == 4) { "Fila inválida del nomenclátor INE" }
                columns[1] to columns[3]
            }.toList()
        }
        val collator = Collator.getInstance(Locale.forLanguageTag("es-UY"))
        val grouped = records.groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.distinct().sortedWith(collator) }
        check(grouped.keys == departments.toSet()) { "Nomenclátor INE incompleto" }
        return grouped
    }
}
