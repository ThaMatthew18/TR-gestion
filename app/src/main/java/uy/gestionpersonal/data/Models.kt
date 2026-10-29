package uy.gestionpersonal.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

enum class Empresa(val displayName: String) {
    RR("R&R"),
    TRANSEO("TRANSEO")
}

enum class DeliveryKind { UNIFORME, EPP }

data class Attachment(
    val id: String,
    val kind: String,
    val name: String,
    val uri: String
)

data class Reentry(val id: String, val date: String = "", val endDate: String = "", val notes: String = "")

/** All user-editable fields and all attachments are optional. */
data class Worker(
    val id: String,
    val empresa: Empresa,
    val fields: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList(),
    val reentries: List<Reentry> = emptyList()
) {
    /** Old single-date records remain readable without modifying their identity or documents. */
    val reentryHistory: List<Reentry> get() = reentries.ifEmpty {
        if (fields["tieneReingreso"] == "Sí" || (fields["tieneReingreso"].isNullOrBlank() && !fields["reingreso"].isNullOrBlank()))
            listOf(Reentry("legacy-$id", fields["reingreso"].orEmpty())) else emptyList()
    }
    val hasReentry: Boolean
        get() = reentryHistory.isNotEmpty()
    val reentryAnswer: String get() = if (hasReentry) "Sí" else "No"
    val reentryDateText: String get() = reentryHistory.filter { parseDate(it.date) != null }.maxByOrNull { it.date }?.date.orEmpty()

    fun withReentries(history: List<Reentry>): Worker = copy(
        fields = workerFieldsForSave(fields - "reingreso" + ("tieneReingreso" to if (history.isEmpty()) "No" else "Sí")),
        reentries = history.toList()
    )

    fun employmentDates(today: LocalDate = LocalDate.now()): Pair<String, String> {
        val lastEnd = fields["baja"].orEmpty()
        val end = parseDate(lastEnd)
        val latest = reentryHistory.mapIndexedNotNull { index, entry ->
            val start = parseDate(entry.date) ?: return@mapIndexedNotNull null
            if (start.isAfter(today) || (end != null && !start.isAfter(end))) null else Triple(start, index, entry)
        }.sortedWith(compareBy<Triple<LocalDate, Int, Reentry>> { it.first }.thenBy { it.second }).lastOrNull()?.third
        return if (latest != null) latest.date to latest.endDate else fields["alta"].orEmpty() to lastEnd
    }

    fun isActive(today: LocalDate = LocalDate.now()): Boolean {
        val end = employmentDates(today).second
        return end.isBlank() || parseDate(end)?.isAfter(today) == true
    }

    val displayName: String
        get() = listOf(fields["nombres"].orEmpty(), fields["apellidos"].orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(" ")
            .ifBlank { "Trabajador sin nombre" }
}

fun workerFieldsForSave(fields: Map<String, String>): Map<String, String> = fields.filterValues { it.isNotBlank() }.toMutableMap().apply {
    val enabled = this["tieneReingreso"] == "Sí" || (this["tieneReingreso"].isNullOrBlank() && !this["reingreso"].isNullOrBlank())
    this["tieneReingreso"] = if (enabled) "Sí" else "No"
    if (!enabled) remove("reingreso")
}

data class Delivery(
    val id: String,
    val empresa: Empresa,
    val workerId: String = "",
    val kind: DeliveryKind,
    val item: String = "",
    val size: String = "",
    val date: String = "",
    val quantity: String = "",
    val conformity: Boolean = false,
    val notes: String = "",
    val receiptName: String = "",
    val receiptUri: String = "",
    val signature: ReceiptSignature? = null
)

data class InkPoint(val x: Float, val y: Float)
data class ReceiptSignature(val name: String, val document: String, val signedAt: String, val payloadHash: String, val strokes: List<List<InkPoint>>)

fun Delivery.signingHash(): String {
    val payload = listOf(id, empresa.name, workerId, kind.name, item, size, date, quantity, notes, conformity.toString()).joinToString("") { "${it.length}:$it" }
    return java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

fun ReceiptSignature.hasInk(): Boolean = strokes.all { stroke -> stroke.all { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f } } && strokes.any { stroke -> stroke.size > 1 && stroke.zipWithNext().sumOf { (a, b) -> kotlin.math.hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()) } > 0.01 }
fun Delivery.validSignature(): ReceiptSignature? = signature?.takeIf { it.payloadHash == signingHash() && it.hasInk() }

enum class TrainingStatus { VIGENTE, POR_VENCER, VENCIDA, SIN_FECHA, PROGRAMADA }

data class Training(
    val id: String,
    val empresa: Empresa,
    val workerId: String = "",
    val title: String = "",
    val date: String = "",
    /** null means no expiration. The interface offers 6, 12, or 24 months. */
    val validityMonths: Int? = null,
    val notes: String = ""
) {
    fun expiryDate(): LocalDate? {
        val taughtOn = parseDate(date) ?: return null
        val months = validityMonths ?: return null
        if (months <= 0) return null
        return try {
            taughtOn.plusMonths(months.toLong())
        } catch (_: DateTimeException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun status(today: LocalDate = LocalDate.now()): TrainingStatus {
        val taughtOn = parseDate(date) ?: return TrainingStatus.SIN_FECHA
        if (taughtOn.isAfter(today)) return TrainingStatus.PROGRAMADA
        if (validityMonths == null) return TrainingStatus.VIGENTE
        val expires = expiryDate() ?: return TrainingStatus.SIN_FECHA
        return when {
            expires.isBefore(today) -> TrainingStatus.VENCIDA
            !expires.isAfter(today.plusDays(30)) -> TrainingStatus.POR_VENCER
            else -> TrainingStatus.VIGENTE
        }
    }
}

data class CompanyData(
    val workers: List<Worker> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val trainings: List<Training> = emptyList()
)

fun newId(): String = UUID.randomUUID().toString()

private fun parseDate(value: String): LocalDate? = try {
    LocalDate.parse(value)
} catch (_: DateTimeParseException) {
    null
}
