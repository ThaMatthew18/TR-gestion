# Correspondencia con el pliego de requerimientos

Documento de referencia: **Pliego de Requerimientos Técnicos — Sistema Integral de Gestión de Personal, EPP & Legajos Digitales, especificación V2.0**, de dos páginas, aportado por el usuario. La adaptación agrega dos espacios, **R&R** y **TRANSEO**, con las mismas funciones.

Esta matriz describe la implementación y sus límites. **No constituye un registro de pruebas ejecutadas ni una certificación normativa.** La entrega actual es una aplicación Android con guardado local persistente y generación local de archivos. Drive requiere completar el registro OAuth descrito en [google-drive.md](google-drive.md).

| Requisito del pliego / solicitud | Pantalla o componente | Implementación y alcance |
| --- | --- | --- |
| Pantalla inicial con selección de empresa | Selección de empresa | Tarjetas R&R y TRANSEO; acceso a espacios equivalentes. El botón Cambiar empresa regresa al inicio. |
| Información completamente separada | Todas las pantallas; `MemoryRepository` | Cada trabajador, entrega, capacitación y adjunto tiene asociación empresarial. El repositorio y las exportaciones verifican la empresa; no se fusionan registros al cambiar de espacio. La separación se conserva en el almacenamiento privado. La cuenta de Drive se elige dentro de la app; no hay un backend de usuarios o roles. |
| Regla general: ningún campo obligatorio | Formularios de Personal, Uniformes, EPP y Capacitaciones | Guardado habilitado con campos y adjuntos vacíos. Empresa e identificadores internos se generan automáticamente. Se admite una entrega o capacitación sin trabajador asignado. |
| 1.1 Código interno, nombres y apellidos | Personal → ficha → bloque 01 | UUID interno y campos libres para nombres y apellidos. Una ficha sin nombre se presenta como Trabajador sin nombre. |
| 1.1 Cédula y fotos frente/dorso | Personal → bloque 01; Legajos | Número libre y dos espacios de adjuntos. Selección real de imágenes o PDF mediante el selector de documentos Android; apertura de archivos con una aplicación compatible. |
| 1.1 Nacimiento, país de nacimiento y país del documento | Personal → bloque 01 | Selector de fecha; países como texto libre. Las fechas se muestran en formato ISO `AAAA-MM-DD` y se pueden quitar. |
| 1.1 Carné de salud: vencimiento y frente/dorso | Personal → bloque 01; Legajos | Fecha opcional y dos adjuntos independientes. No se verifica la autenticidad del carné ni se envían avisos de vencimiento. |
| 1.2 Calle, número y bis | Personal → bloque 02 | Tres campos opcionales separados. |
| 1.2 Departamento y localidad en cascada | Personal → bloque 02; catálogo INE | 19 departamentos y 671 registros INE 2023, incluidas 19 categorías Rural. La localidad se filtra por departamento y se limpia al cambiarlo. Catálogo incorporado y consultable sin conexión; actualización manual del recurso. |
| 1.2 Teléfono/WhatsApp y email | Personal → bloque 02 | Campos opcionales para guardar los canales de contacto. No se envían mensajes, correos ni se valida el número con WhatsApp. |
| 1.3 Hijos y cónyuge/concubino a cargo de Fonasa | Personal → bloque 03 | Opciones Sí, No o Sin completar. |
| 1.4 Banco, tipo y número de cuenta/CBU | Personal → bloque 04 | Banco y número libres; Caja de ahorro o Cuenta corriente. Sin validación bancaria ni ejecución de pagos. |
| 1.5 Talles de remera, camisa, pantalón, buzo, campera y calzado | Personal → bloque 05 | Seis talles opcionales. |
| 1.5 Histórico individual de uniformes | Uniformes | Cada registro conserva prenda, trabajador, talle, cantidad y fecha propia; permite buscar, filtrar, editar y eliminar con confirmación. |
| 1.6 Empresa contratante | Personal → bloque 06 | Se toma del espacio seleccionado y no se puede cambiar en la ficha. |
| 1.6 Tres tipos de aportación | Personal → bloque 06 | Industria y Comercio Común; Industria y Comercio - No Ley 14.411; Aportación 4: Construcción (Ley 14.411). Selección opcional. |
| 1.6 Proyecto/obra y número BPS para Aportación 4 | Personal → bloque 06 | Proyecto libre; número de obra BPS visible al seleccionar Construcción o cuando ya existe un valor. No consulta registros del BPS. |
| 1.6 Categoría, contratación y fechas de alta/baja | Personal → bloque 06 | Puesto/categoría libre; modalidades incluidas A término; selectores de alta y baja. Personal permite filtrar Activos y Con baja. |
| 2 Exportación consolidada a Excel `.xlsx` | Reportes → Guardar Excel | Archivo OOXML real con hojas Resumen, Personal, Entregas y Capacitaciones; encabezados fijos y filtros de columna. Incluye los seis bloques y referencias a adjuntos; los originales no se incorporan al libro. |
| 2 Filtros por obra/proyecto, empresa, aportación, indumentaria y capacitación | Reportes | La empresa es el espacio actual. Los otros cuatro filtros son combinables. Indumentaria y estado seleccionan trabajadores y registros coincidentes. Los registros sin trabajador se incluyen cuando los filtros no necesitan esa asociación. No se generan tablas dinámicas de Excel. |
| 2 Ficha individual de legajo en una página PDF | Personal → opciones; Legajos → descargar; Reportes → ficha PDF | PDF A4 real con datos de los seis bloques y asignación laboral. Resume valores largos, hasta cuatro referencias a documentos, hasta tres miniaturas de imágenes, cinco entregas y cinco capacitaciones; indica información adicional. Los PDF adjuntos y originales permanecen separados. |
| 3.A Entrega de EPP | EPP | Registro editable de equipo, trabajador, fecha, talle/medida, cantidad y observaciones. Catálogo habitual de casco, lentes/antiparras, guantes, arnés, protección auditiva y calzado; admite texto personalizado. |
| 3.A Fecha y recepción en conformidad | EPP; Uniformes | Fecha individual, casilla manual de recepción en conformidad y constancia opcional de imagen/PDF. No se implementa firma electrónica ni se afirma validación automática ante IGTSS/MTSS. |
| 3.B Títulos habituales y personalizados | Capacitaciones | Inducción Decreto 125/014, Trabajo en Altura y Riesgo Eléctrico; título editable para otras charlas. |
| 3.B Fecha de dictado y vigencia seleccionable | Capacitaciones | Fecha opcional; 6 meses, 1 año, 2 años o Sin vencimiento. La fecha de vencimiento se calcula por meses de calendario. |
| 3.B Semáforo Vigente / Por vencer / Vencida | Resumen; Capacitaciones; Reportes | Indicadores calculados con la fecha del dispositivo. Por vencer: desde hoy hasta 30 días inclusive. Vencida: fecha de vencimiento anterior a hoy. Se agregan Sin fecha y Programada para evitar considerar dictadas las charlas futuras. No se generan notificaciones externas. |
| 4 Base geográfica oficial INE | Personal; recurso `localidades_ine.csv` | Catálogo oficial Censo 2023 incorporado. [Fuente, transformación y hashes](fuentes-geografia.md). No se inventan barrios ni se agregan localidades históricas absorbidas. |
| 5 Comparación de tecnologías | Proyecto entregado | Se adopta desarrollo Android a medida con Kotlin y Jetpack Compose, siguiendo la solicitud del usuario. No se implementan variantes AppSheet, Glide ni Airtable/Softr. |
| 6 Plazo, cotización y licencias | Fuera del frontend | El pliego los pide para una cotización. No se inventan una oferta comercial, precios ni un compromiso de plazo. Este proyecto no depende de una suscripción a los servicios no-code enumerados; servicios futuros pueden tener costos propios. |
| 6 Carga inicial del catálogo y formatos de reporte | Catálogo INE; Reportes | Se incluye el catálogo oficial y los generadores de Excel/PDF descritos arriba. Los formatos están definidos en el código y se pueden adaptar en una siguiente etapa. |

## Límites transversales

Los registros se conservan localmente con escritura atómica y se restauran al iniciar. Los adjuntos se copian al almacenamiento privado para conservar su lectura. Los borradores abiertos no sobreviven a una recreación de pantalla. Desinstalar la aplicación o borrar sus datos elimina el almacenamiento local.

La aplicación es Android 8.0+ con AMOLED/Claro/Sistema. Drive permite elegir una cuenta por el usuario, autorizar drive.file y actualizar adjuntos por empresa; la autorización real está pendiente del registro OAuth. La sincronización es de la app a Drive, manual o al guardar con la app abierta. No restaura legajos completos ni elimina archivos remotos al quitar registros locales. No se entrega web desplegada, API propia, roles, auditoría de usuarios ni sincronización bidireccional.

## Verificación

Los comandos están documentados en [README.md](../README.md). La compilación y las 46 pruebas ejecutadas se detallan en [verificacion.md](verificacion.md). La versión 1.1 agrega temas AMOLED, Claro y Sistema con preferencia local persistente.
