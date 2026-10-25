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

/** All user-editable fields and all attachments are optional. */
data class Worker(
    val id: String,
    val empresa: Empresa,
    val fields: Map<String, String> = emptyMap(),
    val attachments: List<Attachment> = emptyList()
) {
    val hasReentry: Boolean
        get() = fields["tieneReingreso"] == "Sí" || (fields["tieneReingreso"].isNullOrBlank() && !fields["reingreso"].isNullOrBlank())
    val reentryAnswer: String get() = if (hasReentry) "Sí" else "No"
    val reentryDateText: String get() = if (hasReentry) fields["reingreso"].orEmpty() else ""

    fun isActive(today: LocalDate = LocalDate.now()): Boolean {
        val lastEnd = fields["baja"].orEmpty()
        if (lastEnd.isBlank()) return true
        val end = parseDate(lastEnd) ?: return false
        if (end.isAfter(today)) return true
        val reentry = parseDate(reentryDateText) ?: return false
        return reentry.isAfter(end) && !reentry.isAfter(today)
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
    val receiptUri: String = ""
)

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
