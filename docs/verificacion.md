# Verificación del historial de reingresos y exportaciones

Versión compilada: **1.4-historial**, código de versión 5, Android 8.0+.

## Pruebas ejecutadas

- `:app:assembleDebug`: compilación correcta.
- `:app:testDebugUnitTest`: **43 pruebas aprobadas**, 0 fallas. Incluyen dominio, varios ciclos de reingreso/baja, fechas futuras, baja del último período, orden de carga diferente al cronológico, conservación del reingreso único, eliminación explícita sin recuperar datos antiguos y Excel aislado por trabajador/empresa.
- `:app:connectedDebugAndroidTest`: **15 pruebas aprobadas**, 0 fallas, en emulador Android 17/API 37. Incluyen aislamiento empresarial, campos opcionales, temas, restauración de registros con varios reingresos, lectura del formato anterior sin perder documentos, copias durables y Drive sin autorización. Las pruebas de interfaz agregan dos períodos al reingreso anterior, guardan y verifican que siga siendo una única persona; también comprueban la confirmación al quitar un período sin eliminar al trabajador.
- Se generaron PDF y Excel reales desde Android. Caso extenso: **40 reingresos**, con baja y observaciones largas, en un PDF paginado y 40 filas de la hoja Reingresos. `pypdf` verifica que cada final de observación esté presente; `openpyxl` abre las cinco hojas y verifica los períodos. El consolidado conserva la separación empresarial. La ficha con un reingreso y la ficha vacía mantienen una página.
- Se renderizan los PDF con Poppler y se revisan distribución, cortes de línea y márgenes. El resumen conserva hasta tres reingresos; el anexo guarda todos los períodos y observaciones cuando corresponde.
- Las pruebas del motor de Drive usan un adaptador simulado. Verifican actualización del mismo ID, detección de cambios, selección empresarial, cambio de cuenta sin reutilizar IDs previos, exclusión de adjuntos de ejemplo sin archivo y propagación de fallas sin informar éxito falso.
- Las pruebas de almacenamiento verifican que los registros se reconstruyan y que una copia de documento continúe legible después de quitar su archivo de origen.

## Límite de validación

**No se probó autorización, carga ni reemplazo contra una cuenta real de Google Drive.** Falta crear/configurar el proyecto OAuth de Google Cloud y registrar paquete/certificado. No se utilizó una cuenta de Drive conectada al chat ni se cargaron documentos reales.

El registro de Cloud no se completó. La guía [google-drive.md](google-drive.md) deja los pasos, el paquete, la huella SHA-1 y la prueba real que debe ejecutarse después.

Se entrega el APK de prueba `GestionPersonal-historial.apk`. El historial, guardado local y reportes funcionan sin activar Drive. La propuesta de código sigue en la rama de revisión con Drive pendiente de activar; no se afirma sincronización real con Google.
