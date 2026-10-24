# Departamentos y localidades: fuente oficial INE

La aplicación incluye el nomenclátor del **Censo 2023 del Instituto Nacional de Estadística (Uruguay)** y lo consulta sin conexión. Se descargó el 23/10/2026 desde la publicación oficial [Codificadores de departamentos y de localidades censales](https://www.gub.uy/instituto-nacional-estadistica/comunicacion/publicaciones/codificadores-departamentos-localidades-censales).

Archivo de origen: [Codificador de Departamentos y de Localidades según censos.xlsx](https://www5.ine.gub.uy/documents/CENSO%202023/Cartografia/Tablas/Codificador%20de%20Departamentos%20y%20de%20Localidades%20seg%C3%BAn%20censos.xlsx).

## Transformación reproducible

1. Se toma la hoja `DEPARTAMENTOS_codificador` para asociar código y nombre de los 19 departamentos.
2. De `LOCALIDADES_codificador censos` se toman únicamente las columnas de código departamental, código de localidad 2023 y nombre de localidad 2023.
3. Se descartan filas sin código/nombre de 2023 y se deduplican por `(código de departamento, código de localidad 2023)`. Las repeticiones del archivo original corresponden a equivalencias de censos históricos; no son localidades adicionales de 2023.
4. Se conservan los nombres oficiales, acentos y códigos como texto. Se genera el archivo UTF-8 `app/src/main/assets/localidades_ine.csv` separado por punto y coma.

Resultado: **671 registros en 19 departamentos**, incluidos **19 registros “Rural”** (uno por departamento). “Rural” representa la categoría territorial del codificador, no una ciudad. Montevideo contiene “Montevideo” y “Rural” según esta edición; no se agregaron barrios ni localidades históricas absorbidas.

La interfaz ordena alfabéticamente por español y permite elegir solamente las localidades del departamento seleccionado. Un cambio de departamento borra una localidad anterior incompatible.

## Verificación

- XLSX de origen SHA-256: `c304b1207cbfa9030791720a3634b62e4e5100b40bb131577606095c75df2e7b`
- CSV incorporado SHA-256: `5366553c804bf1945fbbae6be97eb7f3d92ca3621be65d7f90b2660fe904d3be`
- Se verificó presencia de los 19 departamentos y unicidad de cada código 2023.
- No se usaron listas comerciales, geocodificadores externos ni localidades inventadas.

La actualización del catálogo requiere reemplazar este recurso estático a partir de una nueva publicación oficial. No se realiza actualización de red automática.
