# Ficha general de empleados — versión 1.6

Versión: 1.6-ficha-general, código 7. Android 8.0 o superior.

## Comprobaciones ejecutadas

- Compilación de la app y del APK de pruebas correcta.
- 55 pruebas unitarias aprobadas, incluidas cinco nuevas de la ficha general: ocho columnas para una empresa, Empresa adicional para ambas, conservación de CI con ceros iniciales y personas con la misma CI en empresas distintas, fechas del último período iniciado, fichas incompletas y filas/encabezados del Excel real.
- Dos pruebas en Android API 37 aprobadas mediante AndroidJUnitRunner: selección R&R/TRANSEO/Ambas con actualización de cantidad y generación de tres archivos XLSX reales. La primera invocación de Gradle no encontró un dispositivo conectado; se abrió el emulador y las pruebas se ejecutaron directamente mediante ADB, con resultado OK (2 tests).
- Los tres archivos fueron abiertos con openpyxl: hoja única Empleados, encabezados en fila 1, empleados hacia abajo, ocho o nueve columnas según el alcance, CI como texto, filtros y encabezados inmovilizados. Se verificó el ingreso del reingreso actual y egreso vacío para un período sin baja.

El alcance de esta validación es la ficha general y las pruebas unitarias del proyecto. No afirma que se haya repetido toda la suite de interfaz ni activado Google Drive. El registro OAuth y la prueba con cuentas reales siguen pendientes.

## Uso

Reportes → Ficha general de empleados → elegir empresa → Descargar ficha general Excel.

Las columnas son CI, Nombre, Apellido, Obra, Categoría, Ingreso, Egreso y Tipo de aportación. Empresa aparece como primera columna únicamente en Ambas empresas. La descarga toma una copia de los registros al solicitarla, incluye activos y bajas y es independiente de los filtros del consolidado inferior.
