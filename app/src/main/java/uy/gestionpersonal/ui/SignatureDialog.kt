package uy.gestionpersonal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uy.gestionpersonal.data.*
import java.time.Instant

@Composable
fun SignatureDialog(delivery: Delivery, worker: Worker?, onDismiss: () -> Unit, onSigned: (ReceiptSignature) -> Unit) {
    var name by remember { mutableStateOf(worker?.displayName.orEmpty()) }
    var document by remember { mutableStateOf(worker?.fields?.get("cedula").orEmpty()) }
    var consent by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<InkPoint>>() }
    fun signature() = ReceiptSignature(name, document, Instant.now().toString(), delivery.copy(conformity = true).signingHash(), strokes.map { it.toList() })
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Firmar recepción") }, text = {
        Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${delivery.empresa.displayName} · ${delivery.item.ifBlank { "Artículo sin completar" }} · Cantidad: ${delivery.quantity.ifBlank { "Sin completar" }}")
            OutlinedTextField(name, { name = it }, label = { Text("Nombre del firmante") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(document, { document = it }, label = { Text("Documento del firmante") }, modifier = Modifier.fillMaxWidth())
            Text("Dibujá tu firma en el recuadro.")
            Canvas(Modifier.fillMaxWidth().height(180.dp).background(Color.White).testTag("signature-canvas").pointerInput(Unit) {
                detectDragGestures(onDragStart = { p -> if (strokes.size < 2000) strokes.add(listOf(InkPoint((p.x / size.width).coerceIn(0f, 1f), (p.y / size.height).coerceIn(0f, 1f)))) }, onDrag = { change, _ ->
                    change.consume()
                    if (strokes.isNotEmpty() && strokes.sumOf { it.size } < 100000) strokes[strokes.lastIndex] = strokes.last() + InkPoint((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f))
                })
            }) {
                strokes.forEach { line -> line.zipWithNext().forEach { (a, b) -> drawLine(Color.Black, Offset(a.x * size.width, a.y * size.height), Offset(b.x * size.width, b.y * size.height), strokeWidth = 3f) } }
            }
            TextButton(onClick = { strokes.clear() }) { Text("Limpiar firma") }
            Row { Checkbox(consent, { consent = it }, modifier = Modifier.testTag("signature-consent")); Text("Confirmo la recepción en conformidad de los elementos detallados.") }
        }
    }, confirmButton = { TextButton(onClick = { onSigned(signature()) }, enabled = consent && signature().hasInk()) { Text("Usar firma") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
