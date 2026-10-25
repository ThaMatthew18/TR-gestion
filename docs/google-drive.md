# Activar Google Drive para las cuentas elegidas por los usuarios

## Estado de esta versión

La versión 1.2 incorpora selección de cuenta dentro de Android, autorización oficial de Google, actualización de adjuntos por archivo y guardado local persistente. **El registro OAuth de Google Cloud sigue pendiente. No se ha conectado ni utilizado ninguna cuenta real de Drive durante las pruebas.**

El proyecto de Google Cloud identifica a la aplicación y a su desarrollador. No fija una cuenta de destino. Cada persona elige su propia cuenta de Google en el celular, concede el permiso y ve la cuenta conectada antes de sincronizar.

## Registro único de la aplicación

1. Abrí [Crear un proyecto de Google Cloud](https://console.cloud.google.com/projectcreate) con la cuenta que administrará la aplicación. Creá un proyecto con nombre **TRANSEO**. No hace falta contratar almacenamiento ni vincular un proyecto de Firebase para este código.
2. Con ese proyecto seleccionado, abrí [Google Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com) y habilitala.
3. Abrí [Google Auth Platform](https://console.cloud.google.com/auth/overview). Configurá el nombre visible de la aplicación, el correo de soporte y el contacto del desarrollador. Elegí audiencia **Externa** para permitir cuentas de Google de distintos usuarios.
4. En Acceso a datos, agregá únicamente `https://www.googleapis.com/auth/drive.file`. Este permiso permite trabajar con archivos creados o seleccionados para esta aplicación. El código no solicita acceso a todo el Drive.
5. En [Clientes](https://console.cloud.google.com/auth/clients), creá un cliente OAuth de tipo **Android** con estos valores del APK entregado:

| Campo | Valor |
| --- | --- |
| Nombre orientativo | TRANSEO Android debug |
| Nombre del paquete | `uy.gestionpersonal` |
| SHA-1 | `B1:11:92:56:59:79:A0:19:7E:29:D9:34:2C:E0:69:2D:41:8B:F3:39` |

El cliente Android se identifica por paquete y certificado. No hay que introducir una cuenta de usuario, una contraseña, un `client_secret` ni una cuenta de servicio en el APK. La huella anterior es pública y corresponde al certificado debug de esta computadora. Para una distribución firmada con otro certificado o Google Play App Signing, registrá también el cliente con su propia huella.

6. Si la app está en modo **Pruebas**, agregá como usuarios de prueba las cuentas que usarás para comprobar la conexión. Para que puedan usarla otras cuentas, cambiá la audiencia a producción y completá los requisitos de marca, privacidad o verificación que Google muestre para el proyecto. Una cuenta que no esté admitida por la configuración de audiencia no podrá autorizar la app.
7. Volvé a la aplicación, entrá a una empresa y abrí **Google Drive → Conectar Google Drive**. Elegí una cuenta del dispositivo y aceptá el permiso. La app muestra esa cuenta; conectar no carga documentos por sí solo.

## Prueba real después del registro

1. Creá un trabajador de prueba y adjuntá un PDF o una imagen de prueba. Guardá la ficha.
2. En Google Drive, comprobá la cuenta mostrada y tocá **Sincronizar documentos**.
3. Abrí la carpeta desde la aplicación. Se crea la estructura `Gestión de Personal / R&R o TRANSEO / Trabajador` en esa cuenta.
4. Reemplazá el mismo frente/dorso en la ficha y sincronizá de nuevo. Debe actualizarse el mismo archivo, conservando su identificador y enlace.
5. Repetí sin modificar el archivo: debe aparecer como sin cambios. El código compara SHA-256 con el checksum de Drive.
6. Activá **Actualizar al guardar** si querés cargas automáticas para esa empresa mientras la app está abierta. Ante un error, los archivos locales se conservan y podés reintentar. No hay un servicio de carga en segundo plano con la app cerrada.
7. **Cambiar cuenta de Google** vuelve a mostrar el selector y desactiva las actualizaciones automáticas. La siguiente sincronización se realiza solo después de autorizar la nueva cuenta y de una acción explícita del usuario. No se migran archivos entre cuentas.

## Alcance y conservación

- Se sincronizan los adjuntos de identificación/salud y las constancias de entrega. Los datos bancarios, formularios completos y reportes generados no se exportan automáticamente a Drive.
- Las actualizaciones van de la app a Drive. Editar un documento directamente en Drive no cambia el legajo del dispositivo.
- Quitar un adjunto o trabajador de la app no elimina sus archivos de Drive. Desconectar tampoco borra archivos de Drive.
- Los originales seleccionados se copian al almacenamiento privado de la app. Las referencias y los registros se guardan de forma atómica en el dispositivo. Desinstalar la app o borrar sus datos elimina ese almacenamiento local; Drive conserva lo que ya se haya subido.
- La autorización utiliza Google Identity Services y `drive.file`. Los tokens se mantienen en memoria durante cada operación y no se escriben en los datos del proyecto. El usuario puede desconectar la cuenta desde la app o quitar su permiso desde su cuenta de Google.
- La implementación no restaura legajos completos desde Drive ni combina cambios de varios dispositivos. Para eso hace falta una etapa adicional de sincronización de registros y resolución de conflictos.

## Referencias oficiales

- [Autorización de Google en Android y registro del cliente](https://developer.android.com/identity/authorization).
- [Permisos de Google Drive](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).
- [Carga y actualización de archivos](https://developers.google.com/workspace/drive/api/guides/manage-uploads).
