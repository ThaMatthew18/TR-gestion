# Gestión de Personal — R&R y TRANSEO

Aplicación Android en **Kotlin y Jetpack Compose** basada en el pliego suministrado. Las dos empresas tienen las mismas pantallas y mantienen separados sus trabajadores, legajos, uniformes, EPP, capacitaciones y reportes.

## Probar en un teléfono

1. Descargá **`GestionPersonal-demo.apk`** desde la [versión de demostración en GitHub](https://github.com/ThaMatthew18/TRANSEO/releases/tag/v1.0.0-demo) o usá el archivo entregado, y copialo a un teléfono con **Android 8.0 o superior**.
2. Abrilo desde Archivos y autorizá la instalación desde esa aplicación si Android lo solicita. Es un APK de demostración firmado para depuración.
3. Iniciá **Gestión de Personal**, elegí **R&R** o **TRANSEO** y recorré el menú. **Cambiar empresa** vuelve a la selección inicial.

No requiere una cuenta ni un servidor para usar la demostración. La carga inicial incluye datos de ejemplo independientes para cada empresa.

## Pantallas disponibles

Además del **Resumen**, el menú ofrece seis módulos:

| Módulo | Funciones |
| --- | --- |
| Personal | Alta, edición, búsqueda, estado activo/baja y eliminación; ficha en seis bloques. |
| Legajos | Documentos por trabajador, selección de imágenes/PDF, apertura de archivos y ficha individual PDF. |
| Uniformes | Historial de entregas por prenda, trabajador, talle, cantidad y fecha. |
| EPP | Entregas de equipos, recepción en conformidad y constancia adjunta opcional. |
| Capacitaciones | Títulos habituales o personalizados, fecha, vigencia y alertas por estado. |
| Reportes | Excel consolidado filtrado y PDF individual. |

**Todos los campos de carga son opcionales.** Se puede guardar una ficha, entrega o capacitación incompleta y completarla después. Los identificadores internos y la empresa se asignan automáticamente.

## Abrir el código en Android Studio

Abrí **esta carpeta `GestionPersonal`**, que contiene `settings.gradle.kts`, y esperá la sincronización de Gradle. En SDK Manager instalá **Android SDK Platform 37** y las herramientas que solicite Android Studio. Usá un **JDK 17 o superior** compatible con Gradle; el JBR incluido con Android Studio es una opción.

| Componente | Versión del proyecto |
| --- | --- |
| Gradle Wrapper | 9.6.0 |
| Android Gradle Plugin | 9.4.1 |
| Plugin Kotlin Compose | 2.2.10 |
| Compose BOM | 2025.04.01 |
| SDK de compilación / objetivo / mínimo | 37 / 36 / 26 |
| Bytecode Java / Kotlin | 17 |

Se incluyen `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` y su configuración. La primera sincronización necesita conexión para descargar Gradle y las dependencias. Android Studio configura la ruta local del SDK; no se incluye una ruta de otra computadora en el proyecto.

Desde PowerShell, situado en esta carpeta:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

El primer comando genera `app\build\outputs\apk\debug\app-debug.apk`. El último requiere un emulador o teléfono conectado con depuración USB habilitada. También podés ejecutar la app con **Run** en Android Studio. La entrega compiló correctamente y pasó 24 pruebas unitarias y 5 pruebas en Android; los resultados están en [docs/verificacion.md](docs/verificacion.md).

## Alcance de la demostración

- **Memoria de sesión:** el repositorio vive en un `ViewModel`. Los registros se conservan al rotar el dispositivo y al cambiar de empresa. Se pierden si Android termina el proceso o al comenzar un proceso nuevo; se vuelven a cargar los ejemplos. Los borradores de formularios abiertos no se conservan al recrear la pantalla.
- **Adjuntos:** se seleccionan archivos originales del dispositivo o de un proveedor de documentos. La aplicación guarda su referencia URI en la sesión; los originales permanecen en su ubicación. Un permiso revocado o un archivo movido puede impedir abrirlo. No se realiza una carga a un servidor.
- **Excel real:** archivo `.xlsx` con cuatro hojas —Resumen, Personal, Entregas y Capacitaciones—, encabezados fijos y filtros de columna. Los filtros de obra/proyecto, aportación, indumentaria y estado de capacitación se combinan dentro de la empresa seleccionada. Se exportan datos y nombres de adjuntos; no los archivos originales.
- **PDF real:** ficha A4 de una página con los seis bloques, referencias a documentos, hasta tres miniaturas de imágenes disponibles y resumen de seguridad. Los valores largos se abrevian; se muestran hasta cinco entregas y cinco capacitaciones, indicando si hay más. El Excel conserva el detalle de los registros exportados. Los PDF adjuntos no se convierten en miniaturas.
- **Capacitaciones:** “Por vencer” abarca los próximos 30 días; también se distinguen vigentes, vencidas, programadas y sin fecha. Son indicadores dentro de la aplicación, sin notificaciones externas.
- **Siguiente etapa:** almacenamiento persistente, backend, usuarios/permisos, copias de seguridad y sincronización entre dispositivos quedan pendientes. La recepción conforme es un registro manual con adjunto opcional; no se implementa firma electrónica ni validación automática ante organismos.

La aplicación incluye sin conexión **671 registros del nomenclátor INE Censo 2023 para los 19 departamentos**, incluidas sus categorías rurales. La fuente y el procedimiento están en [docs/fuentes-geografia.md](docs/fuentes-geografia.md). La correspondencia detallada con el pliego está en [docs/cobertura-requisitos.md](docs/cobertura-requisitos.md).
