# Verificación de la entrega

Comprobaciones ejecutadas el 23 de octubre de 2026 (America/Montevideo).

- Compilación `:app:assembleDebug`: correcta. APK debug instalable en Android 8.0 o superior.
- `:app:testDebugUnitTest`: **24 pruebas aprobadas**, 0 fallas. Cubren guardado incompleto, CRUD, adjuntos, separación empresarial, referencias cruzadas, cascada, ID y cálculo de vencimientos.
- `:app:connectedDebugAndroidTest`: **7 pruebas aprobadas**, 0 fallas, en emulador Android 17 / API 37. Tres pruebas de interfaz verifican selección y cambio de empresa, navegación, búsqueda, personas visibles y alta con todos los campos vacíos. Dos verifican geografía y exportaciones reales. Otras dos comprueban el fondo negro AMOLED real, alternancia con Claro/Sistema, conservación de empresa y personas, guardado de preferencia y recreación de la actividad.
- Geografía: 19 departamentos y 671 registros oficiales, consulta en cascada sin conexión.
- Excel: archivos generados con el código Kotlin y abiertos con openpyxl; cuatro hojas, encabezados, filtros, acentos y valores de texto conservados. Exportaciones separadas aun ante una fuente de prueba mezclada.
- PDF: Android genera una página A4 en fichas completas y vacías. Renderizadas con Poppler y revisadas visualmente: bloques legibles, miniaturas, resumen de seguridad, avisos de truncado y pie de página sin superposición.
- Inicio y panel de empresa revisados en el emulador. Esta verificación no equivale a una certificación de compatibilidad con todos los modelos Android.
- Instalación y arranque correctos en Samsung Galaxy A55 (SM-A556E), conectado por USB y autorizado. La suite instrumentada se ejecutó en el emulador; en el teléfono se comprobó instalación y apertura.

Para repetir las pruebas con PowerShell y un dispositivo de prueba conectado:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Las pruebas usan Espresso 3.7.0 para la compatibilidad de la herramienta de pruebas con Android 17. Los archivos de prueba y los datos usados son ficticios. La aplicación conserva los registros en memoria durante la sesión, sin almacenamiento persistente ni backend.

Versión verificada: **1.1-demo (AMOLED)**. Solo la preferencia de tema se guarda de forma persistente; el alcance de los registros de demostración sigue siendo de sesión.
