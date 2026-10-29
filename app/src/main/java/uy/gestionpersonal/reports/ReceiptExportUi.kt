package uy.gestionpersonal.reports

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import uy.gestionpersonal.data.*

@Composable
fun rememberReceiptExporter(): (Delivery, Worker?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Pair<Delivery, Worker?>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val request = pending; pending = null
        if (uri != null && request != null) scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { val out = context.contentResolver.openOutputStream(uri, "w") ?: error("No se pudo abrir el destino"); out.use { exportDeliveryReceipt(it, request.first, request.second) } }; Toast.makeText(context, "Constancia guardada", Toast.LENGTH_LONG).show() }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; Toast.makeText(context, "No se pudo exportar: ${failure.localizedMessage}", Toast.LENGTH_LONG).show() }
            finally { busy = false }
        }
    }
    return { delivery, worker -> if (!busy) { pending = delivery to worker; launcher.launch("Constancia_${delivery.empresa.name}_${delivery.id.takeLast(12)}.pdf") } }
}
