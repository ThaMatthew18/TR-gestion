# Verificación de la integración de Drive

Versión compilada: **1.2-drive**, código de versión 3, Android 8.0+.

## Pruebas ejecutadas

- `:app:assembleDebug`: compilación correcta.
- `:app:testDebugUnitTest`: **35 pruebas aprobadas**, 0 fallas. Incluyen las comprobaciones previas del dominio, restauración de snapshots y errores de almacenamiento, más 9 pruebas de planificación y sincronización de Drive.
- `:app:connectedDebugAndroidTest`: **11 pruebas aprobadas**, 0 fallas, en emulador Android 17/API 37. Incluyen aislamiento empresarial, campos opcionales, exportaciones, INE, temas y preferencia, restauración de registros, copia durable de documentos, transporte PATCH de Android y bloqueo de acciones de Drive sin autorización.
- Las pruebas del motor de Drive usan un adaptador simulado. Verifican actualización del mismo ID, detección de cambios, selección empresarial, cambio de cuenta sin reutilizar IDs previos, exclusión de adjuntos de ejemplo sin archivo y propagación de fallas sin informar éxito falso.
- Las pruebas de almacenamiento verifican que los registros se reconstruyan y que una copia de documento continúe legible después de quitar su archivo de origen.

## Límite de validación

**No se probó autorización, carga ni reemplazo contra una cuenta real de Google Drive.** Falta crear/configurar el proyecto OAuth de Google Cloud y registrar paquete/certificado. No se utilizó una cuenta de Drive conectada al chat ni se cargaron documentos reales.

La automatización del navegador se detuvo al no poder verificar la URL actual de la ventana disponible. El registro de Cloud no se completó. La guía [google-drive.md](google-drive.md) deja los pasos, el paquete, la huella SHA-1 y la prueba real que debe ejecutarse después.

Esta versión se entrega como integración en revisión, con APK de prueba. La versión AMOLED 1.1 publicada sigue disponible y no se sustituye hasta completar la activación y prueba real de Drive.
