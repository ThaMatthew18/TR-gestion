package uy.gestionpersonal.reports

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.gestionpersonal.data.*

class WorkerExporter(val busy: Boolean, val export: (Worker, WorkerExportFormat) -> Unit)
private data class WorkerExportRequest(val worker: Worker, val source: CompanyData, val format: WorkerExportFormat)

@Composable
fun rememberWorkerExporter(empresa: Empresa, repository: MemoryRepository): WorkerExporter {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember(empresa) { mutableStateOf<WorkerExportRequest?>(null) }
    var busy by remember { mutableStateOf(false) }
    val save: (Uri?) -> Unit = { uri ->
        val request = pending
        pending = null
        if (uri != null && request == null) Toast.makeText(context, "La exportación se interrumpió. Volvé a exportar la ficha.", Toast.LENGTH_LONG).show()
        if (uri != null && request != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "w") ?: error("No se pudo abrir el destino")
                    output.use {
                        when (request.format) {
                            WorkerExportFormat.PDF -> exportWorkerPdf(context, it, request.worker, request.source)
                            WorkerExportFormat.COMPLETE_PDF -> exportCompleteWorkerPdf(context, it, request.worker, request.source)
                            WorkerExportFormat.PACKAGE -> exportWorkerPackage(context, it, request.worker, request.source)
                            WorkerExportFormat.EXCEL -> writeWorkerExcel(it, request.worker, request.source)
                        }
                    }
                }
                Toast.makeText(context, "Exportación guardada", Toast.LENGTH_LONG).show()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Toast.makeText(context, "No se pudo guardar la ficha: ${failure.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(WorkerExportFormat.PDF.mime), save)
    val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(WorkerExportFormat.EXCEL.mime), save)
    val complete = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), save)
    val archive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip"), save)
    return WorkerExporter(busy) { worker, format ->
        require(worker.empresa == empresa) { "El trabajador pertenece a otra empresa." }
        if (!busy) {
            val source = repository.data(empresa)
            val draft = worker.copy(fields = workerFieldsForSave(worker.fields))
            val snapshot = source.copy(workers = source.workers.filterNot { it.id == draft.id } + draft)
            pending = WorkerExportRequest(draft, snapshot, format)
            val filename = workerExportFilename(draft, format)
            when (format) { WorkerExportFormat.PDF -> pdf.launch(filename); WorkerExportFormat.EXCEL -> excel.launch(filename); WorkerExportFormat.COMPLETE_PDF -> complete.launch(filename); WorkerExportFormat.PACKAGE -> archive.launch(filename) }
        }
    }
}
