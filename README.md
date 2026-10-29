# Gestión de Personal

Aplicación Android en **Kotlin y Jetpack Compose** basada en el pliego suministrado. Las dos empresas tienen las mismas pantallas y mantienen separados sus trabajadores, legajos, uniformes, EPP, capacitaciones y reportes.

> **Google Drive: registro OAuth pendiente.** El código de conexión y actualización está implementado, pero necesita registrar la app en Google Cloud para autorizar cuentas reales. No se usó ninguna cuenta real de Drive durante las pruebas. [Guía de activación](docs/google-drive.md).

## Probar en un teléfono

1. Usá el APK de prueba `GestionPersonal-ficha-general.apk`, versión **1.6-ficha-general**, entregado junto al código, en un teléfono con **Android 8.0 o superior**. La conexión real con Google necesita completar primero la [configuración de Cloud](docs/google-drive.md).
2. Abrilo desde Archivos y autorizá la instalación desde esa aplicación si Android lo solicita. Es un APK de demostración firmado para depuración.
3. Iniciá **Gestión de Personal**, elegí **R&R** o **TRANSEO** y recorré el menú. **Cambiar empresa** vuelve a la selección inicial.

No requiere una cuenta ni un servidor para usar la demostración. La carga inicial incluye datos de ejemplo independientes para cada empresa.

## Apariencia AMOLED

La aplicación inicia en **AMOLED**, con fondo negro puro `#000000`, tarjetas oscuras y acentos verdes/azules. El botón de luna/sol en la pantalla inicial y en el encabezado de cada empresa permite elegir **AMOLED**, **Claro** o **Sistema**. La preferencia se guarda en el dispositivo y se conserva al volver a abrir la aplicación; no afecta los registros ni la empresa seleccionada. Formularios, adjuntos, menús, selectores de fecha, alertas y barras del sistema se adaptan al tema. Las exportaciones PDF conservan su diseño claro para impresión.

## Pantallas disponibles

Además del **Resumen**, el menú ofrece siete módulos:

| Módulo | Funciones |
| --- | --- |
| Personal | Alta, edición, búsqueda, estado activo/baja y eliminación; ficha en seis bloques. |
| Legajos | Documentos por trabajador, selección de imágenes/PDF, apertura de archivos y ficha individual PDF/Excel. |
| Uniformes | Historial de entregas por prenda, trabajador, talle, cantidad y fecha. |
| EPP | Entregas de equipos, recepción en conformidad y constancia adjunta opcional. |
| Capacitaciones | Títulos habituales o personalizados, fecha, vigencia y alertas por estado. |
| Reportes | Excel consolidado filtrado y ficha individual PDF/Excel. |
| Google Drive | Selección de cuenta en el dispositivo y actualización de adjuntos por empresa. |

**Todos los campos de carga son opcionales.** Se puede guardar una ficha, entrega o capacitación incompleta y completarla después. Los identificadores internos y la empresa se asignan automáticamente.

## Ficha general de empleados

En **Reportes → Ficha general de empleados**, elegí **R&R**, **TRANSEO** o **Ambas empresas** y tocá **Descargar ficha general Excel**. Android permite elegir carpeta y nombre del archivo `.xlsx`.

La única hoja, **Empleados**, tiene los encabezados en la primera fila y una fila por empleado debajo, con estas ocho columnas en orden: **CI, Nombre, Apellido, Obra, Categoría, Ingreso, Egreso y Tipo de aportación**. Para Ambas empresas se agrega **Empresa** al principio de cada fila; al elegir una sola empresa se omite esa columna. La CI se guarda como texto para conservar ceros iniciales.

Incluye activos, bajas y fichas incompletas, dejando vacíos los campos sin completar. Ingreso y egreso corresponden al último período ya iniciado; los reingresos futuros no reemplazan al período actual. Los filtros del consolidado que aparece más abajo son independientes y no restringen esta ficha general. No incluye datos bancarios, adjuntos ni otros campos que no se pidieron.

La validación de esta versión está en [verificacion-ficha-general.md](docs/verificacion-ficha-general.md).

## Reingreso y exportación individual

En **Personal → Ver ficha → Relación laboral y asignación → Agregar reingreso**, se pueden registrar varios períodos de la misma persona. Cada uno tiene **fecha de reingreso, fecha de baja opcional y observaciones**. El indicador Tiene reingresos muestra Sí/No y la cantidad. Las fechas de alta y baja iniciales se conservan por separado; las nuevas bajas se cargan dentro del período correspondiente. La ficha, identidad, documentos, entregas y capacitaciones se conservan sin borrar o duplicar al trabajador.

El reingreso único de versiones anteriores aparece como el primer registro y se conserva al guardar. El estado Activo/Con baja usa el período más reciente cuya fecha ya llegó: una baja de ese período lo desactiva y un reingreso futuro no adelanta su activación. Los períodos sin fecha se guardan pero no cambian el estado. Para corregir una carga se puede quitar un reingreso con confirmación y guardar la ficha.

En el encabezado de la ficha, tocá **Exportar ficha** y elegí **PDF** o **Excel**. También están disponibles en las opciones de Personal, Legajos y Reportes. Android permite elegir el nombre y carpeta de destino. Exportar usa los datos actuales del formulario; para conservar una edición en la aplicación hay que tocar **Guardar trabajador**.

El PDF es un resumen A4 que muestra hasta tres reingresos en su primera página. Cuando hay más períodos o hay observaciones, agrega un anexo paginado con el historial completo. El Excel individual incluye cinco hojas: **Ficha**, **Adjuntos**, **Entregas**, **Capacitaciones** y **Reingresos**, con los valores e historiales del trabajador elegido y su empresa. El consolidado también incluye una hoja Reingresos para los trabajadores seleccionados por los filtros. Las referencias de adjuntos no incorporan los archivos originales.

## Constancias, firma y legajo completo

En **EPP o Uniformes → Registrar/Editar entrega**, tocá **Firmar en la pantalla**. El trabajador puede completar nombre/documento, dibujar su firma y confirmar la recepción. **Guardar** conserva la firma en la entrega. Cambiar trabajador, artículo, talle, cantidad, fecha, observaciones o conformidad invalida esa firma y requiere volver a capturarla. **Generar constancia PDF** permite imprimir/exportar tanto una constancia firmada como una plantilla para firmar en papel. La captura es una firma manuscrita en pantalla; no se presenta como firma digital certificada ni certificación de cumplimiento normativo.

En **Ficha → Exportar**, **Legajos** y **Reportes** se agregan:

- **PDF completo con documentos:** conserva todos los valores e historiales en páginas adicionales e incorpora las imágenes y cada página de los PDF adjuntos. Los PDF originales se representan como páginas rasterizadas; no se conserva una firma criptográfica ni el texto seleccionable de esos originales dentro del PDF combinado.
- **ZIP con archivos originales:** incluye el PDF completo, Excel, constancias generadas y los adjuntos originales sin modificarlos. Para conservar exactamente un PDF firmado digitalmente, usá su archivo original dentro del ZIP.

Un adjunto real ilegible o protegido que no pueda abrirse hace fallar la exportación completa; se informa el error en lugar de mostrar éxito con un legajo incompleto. Las referencias de demostración sin archivo se identifican como ejemplos.

## Datos entre celulares

En **Google Drive → Datos completos entre celulares** se preparan publicación y descarga manuales de una empresa completa, incluidos documentos, firmas e historiales. Ambos celulares deben elegir **la misma cuenta de Google**. No se configura una cuenta fija en el APK. Cada publicación crea una versión nueva y conserva las anteriores. Si hay publicaciones simultáneas, se muestran ambas y se requiere una elección explícita de la copia principal; no se combinan ediciones automáticamente.

La descarga requiere confirmación, verifica empresa e integridad, conserva un respaldo previo exportable y mantiene intacta la otra empresa. Una edición local durante la descarga cancela el reemplazo. Cada copia admite hasta 200 MB. La función necesita registrar OAuth y una prueba real en dos dispositivos; está preparada y probada con datos locales/adaptadores, **todavía no activada ni validada contra Drive real**. No es un backend de usuarios con roles ni colaboración en tiempo real.

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

El primer comando genera `app\build\outputs\apk\debug\app-debug.apk`. El último requiere un emulador o teléfono conectado con depuración USB habilitada. También podés ejecutar la app con **Run** en Android Studio. Los resultados y los límites de validación están en [docs/verificacion.md](docs/verificacion.md).

## Guardado local y Google Drive

- **Registros persistentes:** se conservan al cerrar el proceso y se restauran al abrir. Se usa almacenamiento privado con escritura atómica. Los borradores de formularios abiertos no sobreviven a una recreación de pantalla.
- **Adjuntos:** las imágenes/PDF seleccionados se copian al almacenamiento privado. La lectura y copia se ejecutan fuera del hilo de interfaz. Mover el original o revocar el permiso de su proveedor no afecta a la copia. Desinstalar la app o borrar sus datos elimina el almacenamiento local.
- **Cuenta elegida dentro de la app:** cada usuario toca Conectar Google Drive, elige una cuenta del dispositivo y autoriza `drive.file`. El proyecto Cloud identifica al desarrollador; no fija la cuenta de destino. El código no contiene una cuenta de servicio ni una cuenta de Drive predefinida.
- **Sincronización de adjuntos:** identificación/salud y constancias adjuntas, en Gestión de Personal / Empresa / Trabajador. Reemplazar un adjunto actualiza el mismo archivo remoto. SHA-256 identifica cambios. Conectar una cuenta no sube archivos por sí solo. Actualizar al guardar se activa por empresa con la app abierta y solo sube adjuntos. La publicación de fichas completas y firmas es una acción manual aparte.
- **Cambio de cuenta:** desactiva las actualizaciones automáticas y requiere una nueva autorización y acción del usuario. Las bases de versiones se guardan por cuenta y empresa. Quitar un registro local o desconectar no elimina archivos de Drive. Descargar una copia completa es una acción separada y explícita; editar un adjunto desde Drive no modifica el formulario local.
- **Excel y PDF:** exportaciones locales con los seis bloques, historiales de reingresos y separación empresarial. El PDF conserva un resumen con hasta tres miniaturas y agrega anexos de reingresos cuando corresponde. Estos reportes no se suben automáticamente.
- **Pendiente:** registro OAuth de Google Cloud y prueba de autorización/carga real. Backend de usuarios, roles, auditoría y combinación de cambios entre dispositivos no están implementados. [Activación y prueba real](docs/google-drive.md).

La aplicación incluye sin conexión **671 registros del nomenclátor INE Censo 2023 para los 19 departamentos**, incluidas sus categorías rurales. La fuente y el procedimiento están en [docs/fuentes-geografia.md](docs/fuentes-geografia.md). La correspondencia detallada con el pliego está en [docs/cobertura-requisitos.md](docs/cobertura-requisitos.md).
