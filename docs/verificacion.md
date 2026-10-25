# Verificación de reingreso y exportación individual

Versión compilada: **1.3-reingreso**, código de versión 4, Android 8.0+.

## Pruebas ejecutadas

- `:app:assembleDebug`: compilación correcta.
- `:app:testDebugUnitTest`: **40 pruebas aprobadas**, 0 fallas. Incluyen dominio, reingreso pasado/futuro y baja posterior, normalización Sí/No y eliminación de fecha, Excel individual con registros mezclados de dos personas/empresas, y planificación de Drive.
- `:app:connectedDebugAndroidTest`: **12 pruebas aprobadas**, 0 fallas, en emulador Android 17/API 37. Incluyen aislamiento empresarial, campos opcionales, exportaciones, INE, temas y preferencia, restauración de registros con reingreso, copia durable de documentos, transporte PATCH de Android y bloqueo de acciones de Drive sin autorización. La prueba nueva abre la ficha, marca No, guarda, comprueba que la fecha anterior se quitó y verifica las dos opciones de exportación.
- Se generaron PDF y Excel reales desde Android. `pypdf` comprobó una página y el reingreso; `openpyxl` abrió las cuatro hojas del Excel individual y verificó todos los historiales del trabajador sin registros de otra empresa. Se renderizó el PDF con Poppler y se revisó visualmente la distribución, incluido el reingreso.
- Una primera ejecución de interfaz falló por un cierre de menú realizado con Espresso y una búsqueda inestable. Se quitó ese cierre innecesario de la prueba nueva y la ejecución completa final pasó.
- Las pruebas del motor de Drive usan un adaptador simulado. Verifican actualización del mismo ID, detección de cambios, selección empresarial, cambio de cuenta sin reutilizar IDs previos, exclusión de adjuntos de ejemplo sin archivo y propagación de fallas sin informar éxito falso.
- Las pruebas de almacenamiento verifican que los registros se reconstruyan y que una copia de documento continúe legible después de quitar su archivo de origen.

## Límite de validación

**No se probó autorización, carga ni reemplazo contra una cuenta real de Google Drive.** Falta crear/configurar el proyecto OAuth de Google Cloud y registrar paquete/certificado. No se utilizó una cuenta de Drive conectada al chat ni se cargaron documentos reales.

El registro de Cloud no se completó. La guía [google-drive.md](google-drive.md) deja los pasos, el paquete, la huella SHA-1 y la prueba real que debe ejecutarse después.

Se entrega el APK de prueba `GestionPersonal-reingreso.apk`. Reingreso, guardado local y reportes funcionan sin activar Drive. La propuesta de código sigue en la rama de revisión con Drive pendiente de activar; no se afirma sincronización real con Google.
