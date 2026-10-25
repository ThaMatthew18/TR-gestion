package uy.gestionpersonal.data

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.LocalDate

/**
 * Observable repository. Production persists snapshots through onChanged; tests can use memory only.
 * Company keys are mandatory and never inferred from the currently visible screen.
 * Replacing this repository with persistent storage must preserve the same checks.
 */
class MemoryRepository(
    seedDemoData: Boolean = true,
    initialData: Map<Empresa, CompanyData>? = null,
    private val onChanged: (Map<Empresa, CompanyData>) -> Unit = {}
) {
    private var ready = false
    var persistenceError by mutableStateOf<String?>(null)
        private set
    private val companies = mutableStateMapOf(
        Empresa.RR to CompanyData(),
        Empresa.TRANSEO to CompanyData()
    )

    init {
        if (initialData != null) {
            initialData.forEach { (company, data) ->
                require(data.workers.all { it.empresa == company } && data.deliveries.all { it.empresa == company } && data.trainings.all { it.empresa == company })
                data.workers.forEach(::saveWorker)
            }
            initialData.values.forEach { data -> data.deliveries.forEach(::saveDelivery); data.trainings.forEach(::saveTraining) }
        } else if (seedDemoData) seedDemo()
        ready = true
        persist()
    }

    fun data(empresa: Empresa): CompanyData = companies.getValue(empresa)

    fun saveWorker(worker: Worker) {
        checkCompany(worker.id, worker.empresa) { it.workers.map { record -> record.id } }
        val documentIds = worker.attachments.map { it.id }.toSet()
        require(companies.none { (otherCompany, records) ->
            otherCompany != worker.empresa && records.workers.any { existing ->
                existing.attachments.any { it.id in documentIds }
            }
        }) { "El documento pertenece a otra empresa y no puede cambiarse de espacio de trabajo." }
        val current = data(worker.empresa)
        val snapshot = worker.copy(fields = worker.fields.toMap(), attachments = worker.attachments.toList())
        companies[worker.empresa] = current.copy(workers = current.workers.upsert(snapshot, Worker::id))
        persist()
    }

    /** Deleting a worker also deletes their own associated records in this company. */
    fun deleteWorker(empresa: Empresa, id: String) {
        val current = data(empresa)
        if (current.workers.none { it.id == id }) return
        companies[empresa] = current.copy(
            workers = current.workers.filterNot { it.id == id },
            deliveries = current.deliveries.filterNot { it.workerId == id },
            trainings = current.trainings.filterNot { it.workerId == id }
        )
        persist()
    }

    fun saveDelivery(delivery: Delivery) {
        checkCompany(delivery.id, delivery.empresa) { it.deliveries.map { record -> record.id } }
        checkWorkerReference(delivery.empresa, delivery.workerId)
        val current = data(delivery.empresa)
        companies[delivery.empresa] = current.copy(deliveries = current.deliveries.upsert(delivery, Delivery::id))
        persist()
    }

    fun deleteDelivery(empresa: Empresa, id: String) {
        val current = data(empresa)
        companies[empresa] = current.copy(deliveries = current.deliveries.filterNot { it.id == id })
        persist()
    }

    fun saveTraining(training: Training) {
        checkCompany(training.id, training.empresa) { it.trainings.map { record -> record.id } }
        checkWorkerReference(training.empresa, training.workerId)
        val current = data(training.empresa)
        companies[training.empresa] = current.copy(trainings = current.trainings.upsert(training, Training::id))
        persist()
    }

    fun deleteTraining(empresa: Empresa, id: String) {
        val current = data(empresa)
        companies[empresa] = current.copy(trainings = current.trainings.filterNot { it.id == id })
        persist()
    }

    fun snapshot(): Map<Empresa, CompanyData> = Empresa.entries.associateWith(::data)

    private fun persist() {
        if (!ready) return
        try { onChanged(snapshot()); persistenceError = null }
        catch (_: Exception) { persistenceError = "No se pudieron guardar los cambios en el dispositivo. Liberá espacio y volvé a guardar; no cierres la aplicación hasta resolverlo." }
    }

    private fun checkCompany(id: String, empresa: Empresa, ids: (CompanyData) -> List<String>) {
        require(companies.none { (otherCompany, records) -> otherCompany != empresa && id in ids(records) }) {
            "El registro pertenece a otra empresa y no puede cambiarse de espacio de trabajo."
        }
    }

    private fun checkWorkerReference(empresa: Empresa, workerId: String) {
        require(workerId.isEmpty() || data(empresa).workers.any { it.id == workerId }) {
            "El trabajador debe pertenecer a ${empresa.displayName}."
        }
    }

    private fun seedDemo() {
        val today = LocalDate.now()
        saveWorker(Worker(
            id = "demo-rr-juan",
            empresa = Empresa.RR,
            fields = mapOf(
                "nombres" to "Juan", "apellidos" to "Pérez", "categoria" to "Oficial",
                "proyecto" to "Obra Centro · DEMO", "departamento" to "Montevideo",
                "localidad" to "Montevideo", "modalidad" to "A término",
                "aportacion" to "Aportación 4: Construcción (Ley 14.411)",
                "alta" to today.minusMonths(4).toString(),
                "carneSaludVencimiento" to today.plusMonths(7).toString(),
                "talleRemera" to "L", "talleCamisa" to "L", "tallePantalon" to "44",
                "talleBuzo" to "L", "talleCampera" to "L", "talleCalzado" to "42"
            ),
            attachments = listOf(Attachment(
                "demo-rr-documento", "cedula_frente", "Ejemplo de adjunto · DEMO", ""
            ))
        ))
        saveWorker(Worker(
            id = "demo-rr-carlos",
            empresa = Empresa.RR,
            fields = mapOf(
                "nombres" to "Carlos", "apellidos" to "López", "categoria" to "Medio oficial",
                "proyecto" to "Obra Centro · DEMO", "departamento" to "Canelones",
                "localidad" to "Las Piedras", "aportacion" to "Industria y Comercio Común",
                "alta" to today.minusMonths(2).toString(), "talleRemera" to "M", "talleCalzado" to "41"
            )
        ))
        saveWorker(Worker(
            id = "demo-transeo-martin",
            empresa = Empresa.TRANSEO,
            fields = mapOf(
                "nombres" to "Martín", "apellidos" to "Rodríguez", "categoria" to "Encargado",
                "proyecto" to "Proyecto Norte · DEMO", "departamento" to "Maldonado",
                "localidad" to "Maldonado", "aportacion" to "Industria y Comercio - No Ley 14.411",
                "alta" to today.minusMonths(6).toString(), "talleRemera" to "XL", "talleCalzado" to "43"
            ),
            attachments = listOf(Attachment(
                "demo-transeo-documento", "carne_frente", "Ejemplo de adjunto · DEMO", ""
            ))
        ))
        saveDelivery(Delivery(
            id = "demo-rr-uniforme", empresa = Empresa.RR, workerId = "demo-rr-juan",
            kind = DeliveryKind.UNIFORME, item = "Remera", size = "L",
            date = today.minusDays(14).toString(), quantity = "2", conformity = true,
            notes = "Registro de demostración."
        ))
        saveDelivery(Delivery(
            id = "demo-rr-epp", empresa = Empresa.RR, workerId = "demo-rr-carlos",
            kind = DeliveryKind.EPP, item = "Casco", date = today.minusDays(7).toString(),
            quantity = "1", conformity = true, notes = "Registro de demostración."
        ))
        saveDelivery(Delivery(
            id = "demo-transeo-epp", empresa = Empresa.TRANSEO, workerId = "demo-transeo-martin",
            kind = DeliveryKind.EPP, item = "Guantes", date = today.minusDays(3).toString(),
            quantity = "1", notes = "Registro de demostración."
        ))
        saveTraining(Training(
            id = "demo-rr-vigente", empresa = Empresa.RR, workerId = "demo-rr-juan",
            title = "Inducción Decreto 125/014", date = today.minusMonths(2).toString(),
            validityMonths = 12, notes = "Registro de demostración."
        ))
        saveTraining(Training(
            id = "demo-rr-proxima", empresa = Empresa.RR, workerId = "demo-rr-carlos",
            title = "Trabajo en Altura", date = today.plusDays(15).minusMonths(6).toString(),
            validityMonths = 6, notes = "Registro de demostración."
        ))
        saveTraining(Training(
            id = "demo-transeo-vencida", empresa = Empresa.TRANSEO, workerId = "demo-transeo-martin",
            title = "Riesgo Eléctrico", date = today.minusMonths(8).toString(),
            validityMonths = 6, notes = "Registro de demostración."
        ))
    }
}

private fun <T> List<T>.upsert(record: T, id: (T) -> String): List<T> =
    if (any { id(it) == id(record) }) map { if (id(it) == id(record)) record else it }
    else this + record
