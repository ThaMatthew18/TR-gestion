package uy.gestionpersonal.reports

import uy.gestionpersonal.data.*
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ReportFilters(
    val project: String = "",
    val contribution: String = "",
    val garment: String = "",
    val trainingStatus: TrainingStatus? = null
)

data class ReportSelection(
    val workers: List<Worker>,
    val deliveries: List<Delivery>,
    val trainings: List<Training>
)

fun reportStatusLabel(status: TrainingStatus): String = when (status) {
    TrainingStatus.PROGRAMADA -> "Programada"
    TrainingStatus.VIGENTE -> "Vigente"
    TrainingStatus.POR_VENCER -> "Por vencer"
    TrainingStatus.VENCIDA -> "Vencida"
    TrainingStatus.SIN_FECHA -> "Sin fecha"
}

/** Company scoping is enforced again here, independently of the screen or repository. */
fun selectReportData(
    empresa: Empresa,
    source: CompanyData,
    filters: ReportFilters,
    today: LocalDate = LocalDate.now()
): ReportSelection {
    val deliveries = source.deliveries.filter { it.empresa == empresa }
    val trainings = source.trainings.filter { it.empresa == empresa }
    val workers = source.workers.filter { worker ->
        worker.empresa == empresa &&
            (filters.project.isEmpty() || worker.fields["proyecto"] == filters.project) &&
            (filters.contribution.isEmpty() || worker.fields["aportacion"] == filters.contribution) &&
            (filters.garment.isEmpty() || deliveries.any { it.workerId == worker.id && it.item == filters.garment }) &&
            (filters.trainingStatus == null || trainings.any { it.workerId == worker.id && it.status(today) == filters.trainingStatus })
    }
    val workerIds = workers.mapTo(mutableSetOf()) { it.id }
    val noAssignmentFilter = filters.project.isEmpty() && filters.contribution.isEmpty()
    return ReportSelection(
        workers,
        deliveries.filter {
            (it.workerId in workerIds || (it.workerId.isEmpty() && noAssignmentFilter && filters.trainingStatus == null)) &&
                (filters.garment.isEmpty() || it.item == filters.garment)
        },
        trainings.filter {
            (it.workerId in workerIds || (it.workerId.isEmpty() && noAssignmentFilter && filters.garment.isEmpty())) &&
                (filters.trainingStatus == null || it.status(today) == filters.trainingStatus)
        }
    )
}

internal val workerExportFields: List<Pair<String, String>> = listOf(
    "nombres" to "Nombres", "apellidos" to "Apellidos", "cedula" to "Cédula de identidad",
    "nacimiento" to "Fecha de nacimiento", "paisNacimiento" to "País de nacimiento",
    "paisDocumento" to "País del documento", "carneSaludVencimiento" to "Vencimiento carné de salud",
    "calle" to "Calle", "numero" to "Número", "bis" to "Bis", "departamento" to "Departamento",
    "localidad" to "Localidad", "telefono" to "Teléfono / WhatsApp", "email" to "Email",
    "hijosFonasa" to "Hijos a cargo Fonasa", "conyugeFonasa" to "Cónyuge / concubino a cargo",
    "banco" to "Banco", "tipoCuenta" to "Tipo de cuenta", "numeroCuenta" to "Número de cuenta",
    "talleRemera" to "Talle remera", "talleCamisa" to "Talle camisa", "tallePantalon" to "Talle pantalón",
    "talleBuzo" to "Talle buzo", "talleCampera" to "Talle campera", "talleCalzado" to "Talle calzado de seguridad",
    "aportacion" to "Tipo de aportación", "proyecto" to "Proyecto / obra", "obraBps" to "Número de obra BPS",
    "categoria" to "Categoría / puesto", "modalidad" to "Modalidad de contratación",
    "alta" to "Fecha de alta", "baja" to "Fecha de baja"
)

/** Real OOXML workbook; strings use inlineStr and cannot execute as spreadsheet formulas. */
fun writeCompanyExcel(
    output: OutputStream,
    empresa: Empresa,
    source: CompanyData,
    filters: ReportFilters = ReportFilters(),
    today: LocalDate = LocalDate.now()
) {
    val selected = selectReportData(empresa, source, filters, today)
    val names = selected.workers.associate { it.id to it.displayName }
    val knownKeys = workerExportFields.map { it.first }.toSet()
    val extraKeys = selected.workers.flatMap { it.fields.keys }.filterNot { it in knownKeys }.distinct().sorted()
    val fields = workerExportFields + extraKeys.map { it to it }
    val sheets: List<Pair<String, List<List<String>>>> = listOf(
        "Resumen" to listOf(
            listOf("Concepto", "Valor"), listOf("Empresa", empresa.displayName), listOf("Generado", today.toString()),
            listOf("Proyecto / obra", filters.project.ifBlank { "Todos" }),
            listOf("Tipo de aportación", filters.contribution.ifBlank { "Todos" }),
            listOf("Indumentaria / EPP", filters.garment.ifBlank { "Todos" }),
            listOf("Estado de capacitación", filters.trainingStatus?.let(::reportStatusLabel) ?: "Todos"),
            listOf("Trabajadores", selected.workers.size.toString()),
            listOf("Entregas", selected.deliveries.size.toString()),
            listOf("Capacitaciones", selected.trainings.size.toString()),
            listOf("Adjuntos", "Se exportan sus nombres y referencias. Los archivos originales se mantienen separados."),
            listOf("Alcance", "Datos disponibles en memoria al exportar; no constituye una copia de los archivos adjuntos."),
            listOf("Filtros", "Los filtros se combinan. Indumentaria y estado seleccionan trabajadores y registros coincidentes."),
            listOf("Registros sin trabajador", "Se incluyen si ningún filtro requiere un trabajador asignado.")
        ),
        "Personal" to (listOf(listOf("ID trabajador", "Empresa contratante") + fields.map { it.second } + listOf("Adjuntos (tipo y nombre)")) +
            selected.workers.map { worker ->
                listOf(worker.id, empresa.displayName) + fields.map { worker.fields[it.first].orEmpty() } +
                    listOf(worker.attachments.joinToString(" | ") { "${it.kind}: ${it.name}" })
            }),
        "Entregas" to (listOf(listOf("ID entrega", "Empresa", "ID trabajador", "Trabajador", "Tipo", "Prenda / EPP", "Talle", "Fecha", "Cantidad", "Recepción en conformidad registrada", "Observaciones", "Constancia adjunta")) +
            selected.deliveries.map { delivery ->
                listOf(delivery.id, empresa.displayName, delivery.workerId, names[delivery.workerId] ?: "Sin asignar",
                    if (delivery.kind == DeliveryKind.UNIFORME) "Uniforme" else "EPP", delivery.item, delivery.size,
                    delivery.date, delivery.quantity, if (delivery.conformity) "Sí" else "No registrada", delivery.notes, delivery.receiptName)
            }),
        "Capacitaciones" to (listOf(listOf("ID capacitación", "Empresa", "ID trabajador", "Trabajador", "Título", "Fecha de dictado", "Vigencia", "Vencimiento", "Estado al exportar", "Observaciones")) +
            selected.trainings.map { training ->
                listOf(training.id, empresa.displayName, training.workerId, names[training.workerId] ?: "Sin asignar",
                    training.title, training.date, training.validityMonths?.let { "$it meses" } ?: "Sin vencimiento",
                    training.expiryDate()?.toString().orEmpty(), reportStatusLabel(training.status(today)), training.notes)
            })
    )
    ZipOutputStream(output).use { zip ->
        fun entry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        val declaration = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        entry("[Content_Types].xml", declaration + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" + sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>")
        entry("_rels/.rels", declaration + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        entry("xl/workbook.xml", declaration + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" + sheets.mapIndexed { i, sheet -> "<sheet name=\"${xml(sheet.first)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
        entry("xl/_rels/workbook.xml.rels", declaration + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" + sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } + "<Relationship Id=\"rIdStyles\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
        entry("xl/styles.xml", declaration + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF142D42\"/><bgColor indexed=\"64\"/></patternFill></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"><alignment vertical=\"top\" wrapText=\"1\"/></xf><xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFill=\"1\" applyFont=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
        sheets.forEachIndexed { index, (_, rows) ->
            val width = rows.maxOfOrNull { it.size } ?: 1
            val lastCell = columnName(width - 1) + rows.size.coerceAtLeast(1)
            val content = buildString {
                append(declaration)
                append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1:$lastCell\"/><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"$width\" width=\"24\" customWidth=\"1\"/></cols><sheetData>")
                rows.forEachIndexed { r, cells ->
                    append("<row r=\"${r + 1}\"${if (r == 0) " ht=\"32\" customHeight=\"1\"" else ""}>")
                    cells.forEachIndexed { c, value -> append("<c r=\"${columnName(c)}${r + 1}\" t=\"inlineStr\" s=\"${if (r == 0) 1 else 0}\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>") }
                    append("</row>")
                }
                append("</sheetData><autoFilter ref=\"A1:$lastCell\"/></worksheet>")
            }
            entry("xl/worksheets/sheet${index + 1}.xml", content)
        }
    }
}

private fun columnName(index: Int): String {
    var number = index + 1
    val result = StringBuilder()
    while (number > 0) { number--; result.append(('A'.code + number % 26).toChar()); number /= 26 }
    return result.reverse().toString()
}

private fun xml(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
            '"' -> append("&quot;"); '\'' -> append("&apos;")
            else -> if (character >= ' ' || character == '\n' || character == '\r' || character == '\t') append(character)
        }
    }
}
