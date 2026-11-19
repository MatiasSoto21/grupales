# Facturación ARCA — proyecto académico

Aplicación con Java 21, Spring Boot, PostgreSQL y frontend HTML/CSS/TypeScript con Express. Integra ARCA en HOMOLOGACION y permite comprobantes A/B y documentos X internos.

## Documentación

- [Guía de mantenimiento del repositorio](AGENTS.md).

## Preparar otra computadora

Clonar el repositorio y entrar en su carpeta:

    git clone https://github.com/MatiasSoto21/grupales.git
    cd grupales

Necesitás:

- JDK 21 disponible desde el IDE o la terminal.
- Node.js y npm; en la PC de desarrollo se verificó con Node 24.
- PostgreSQL instalado y ejecutándose.
- Conexión a Internet para descargar dependencias y utilizar ARCA.

No necesitás instalar Maven por separado: se incluye el wrapper.

### 1. Crear la base de datos

Con pgAdmin u otra herramienta de PostgreSQL, crear:

    CREATE DATABASE "TP_GRUPAL_ORM";

La configuración predeterminada utiliza:

| Dato | Valor |
| --- | --- |
| Servidor | localhost |
| Puerto | 5432 |
| Base | TP_GRUPAL_ORM |
| Usuario | postgres |

El usuario debe poder crear y actualizar las tablas y los esquemas del proyecto.

### 2. Configurar PostgreSQL en tu PC

La configuración general está en backend/src/main/resources/application.properties.

Si tenés los mismos datos de servidor, puerto, base y usuario, solamente cambia la contraseña de la conexión. Si alguno es diferente, también hay que ajustar ese dato.

Para mantener la configuración personal separada, crear este archivo:

    backend/src/main/resources/db-local.properties

Con el contenido correspondiente a tu instalación:

    spring.datasource.url=jdbc:postgresql://localhost:5432/TP_GRUPAL_ORM
    spring.datasource.username=postgres
    spring.datasource.password=TU_PASSWORD_LOCAL

La aplicación ya importa ese archivo si existe y está ignorado por Git. No debe entrar al ZIP compartido. No hace falta cambiar el admin del DataInitializer para configurar la base ni el emisor fiscal.

application.properties no incluye una contraseña compartida: utiliza DB_PASSWORD si está definida. También podés usar el archivo db-local.properties indicado arriba. Cada integrante configura sus propios datos.

### 3. Ejecutar el backend

Desde el IDE, abrir el proyecto Maven y ejecutar:

    backend/src/main/java/com/example/facturacion/FacturacionApplication.java

Por terminal de Windows, si la ruta contiene espacios o caracteres como Ñ y tildes, usar una unidad libre con ruta ASCII. Desde la raíz del proyecto:

    subst R: "$PWD"
    Set-Location R:\backend
    .\mvnw.cmd spring-boot:run

Usar otra letra si R: ya está ocupada. Al terminar y cerrar la aplicación y las terminales que usen esa unidad:

    subst R: /d

La API queda en http://localhost:8080/api. El puerto 8080 debe estar libre.

Hibernate crea/actualiza el esquema configurado. Si la base está vacía, DataInitializer carga los datos de muestra. No crea la base PostgreSQL: esa se prepara en el paso 1.

### 4. Instalar y ejecutar el frontend

En otra terminal, desde la carpeta frontend:

    npm ci
    npm start

Abrir http://localhost:3000. El puerto 3000 debe estar libre.

npm ci instala las versiones del package-lock.json y npm start compila TypeScript antes de iniciar el servidor. Para desarrollo también se puede usar npm run dev.

## ARCA en otra computadora

No hay un perfil demo que activar. El arranque normal usa un único flujo de homologación.

- Los comprobantes X se guardan internamente y no solicitan CAE.
- Para A/B hacen falta Internet, el certificado y la clave privada de homologación y el CUIT del emisor configurado.
- El repositorio no incluye certificado ni clave privada. Cada instalación debe aportar sus credenciales autorizadas de homologación. Por compatibilidad, se buscan certificado_test.crt y privada_test.key dentro de backend/src/main/resources; ambos quedan ignorados por Git.
- El CUIT del emisor debe corresponder con esas credenciales. Si se usan otras, deben ajustarse de forma conjunta.
- El usuario admin de la aplicación no determina la identidad fiscal del emisor.
- No se necesitan volver a tramitar credenciales solamente por cambiar de PC si se mantienen las credenciales de homologación autorizadas para este proyecto.

Las rutas externas se pueden configurar con FACTURACION_ARCA_CLAVE_PATH y FACTURACION_ARCA_CERTIFICADO_PATH; el CUIT, con FACTURACION_ARCA_CUIT_EMISOR.

El ticket WSAA se guarda de forma local en el perfil de cada usuario. No es parte de la base de datos ni del ZIP. La disponibilidad de ARCA y la validez de las credenciales siguen siendo necesarias; cambiar la contraseña de PostgreSQL no soluciona un error de autenticación fiscal.

Las credenciales de prueba incluyen una clave privada. Se configuran localmente y no se publican en GitHub. Compartilas únicamente por un medio privado con quien esté autorizado a utilizarlas.

## Qué incluir y qué excluir del ZIP

Conservar:

- backend/ y frontend/ con sus fuentes, configuración general y pruebas.
- Los wrappers Maven y las carpetas .mvn que correspondan al proyecto.
- pom.xml, los package.json y package-lock.json, configuraciones, README.md y AGENTS.md.
- Las credenciales de homologación, si se autoriza utilizarlas en la otra PC; si se excluyen, quien reciba el ZIP debe aportar las suyas y configurar las rutas.

Excluir del ZIP:

- frontend/node_modules/.
- Cualquier carpeta target/ o dist/: se vuelve a compilar.
- frontend/test-results/ y frontend/playwright-report/.
- .test-evidence/, archivos de log y cachés de ejecución.
- .git/, si existiera.
- backend/src/main/resources/db-local.properties y otras configuraciones personales con contraseñas.
- Tickets o cachés de WSAA.

Comprimir manualmente una carpeta incluye también archivos ignorados por Git. El .gitignore no los elimina del ZIP: verificá el contenido que vas a compartir.

## Datos y facturas de esta PC

El ZIP del código no transporta los datos de PostgreSQL.

Con una base vacía se cargan nuevos datos de muestra; no aparecen automáticamente las facturas autorizadas, los clientes ni las modificaciones de la PC original. Los números e IDs mostrados en documentación de pruebas anteriores pueden no existir en esa base.

Para conservar exactamente los mismos datos, se necesita además un respaldo de PostgreSQL y restaurarlo en destino. Ese es un procedimiento distinto de copiar el proyecto.

Si ambos equipos usan la misma identidad fiscal de homologación, comparten el mismo flujo de numeración en ARCA por punto de venta y tipo. Tener bases locales distintas no reinicia esa numeración; la aplicación consulta el servicio.

## Comprobación inicial

El inicializador prepara datos locales (`SIMULADO`), sin CAE ni autorización inventados. Al terminar esa transacción, el arranque normal envía los ejemplos activos pendientes a ARCA homologación mediante el mismo servicio del formulario. Cada uno queda `AUTORIZADA`, `RECHAZADA` o `ERROR_AFIP` según la respuesta real; `RECHAZADA` requiere un rechazo real de WSFE. También se corrigen únicamente los CAE ficticios de la carga antigua antes de procesarla.

Para ejecutar solamente esa carga, sin levantar otro servidor HTTP, después de configurar las credenciales, desde `backend`:

```powershell
.\mvnw.cmd package -DskipTests
java -jar target/facturacion-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --facturacion.datos-iniciales.emitir-arca=true
```

Esta carga conserva los IDs de las muestras, clientes y líneas, aplica los precios e IVA actuales y usa la fecha argentina del servidor y la numeración real de ARCA. Sustituye exclusivamente los ejemplos locales activos sin emisión previa; no envía las muestras anuladas. Cada resultado se confirma en su propia transacción. No vuelve a enviar ejemplos procesados y se detiene ante `ERROR_AFIP`. Para desactivar la carga se puede configurar `facturacion.datos-iniciales.emitir-arca=false`; todas las pruebas de contexto la desactivan expresamente para evitar emisiones reales. Los CUIT ficticios y puntos de venta no habilitados pueden producir rechazos reales; solamente la autorización confirmada permite CAE y, con datos completos del emisor, QR.

1. Ver el listado y confirmar la conexión con el backend.
2. Crear un X con un producto y abrir su PDF.
3. Para A/B, configurar las credenciales locales de homologacion y revisar cliente, IVA, punto de venta, productos y fechas antes de confirmar la emision.
4. Para una demostración fiscal, utilizar un punto habilitado y datos compatibles; los CUIT ficticios de los clientes de muestra no garantizan autorización.

## Pruebas opcionales

Backend, desde backend o su unidad ASCII:

    .\mvnw.cmd test

Frontend, desde frontend:

    npm test
    npm run test:e2e -- invoices.spec.ts

Las pruebas de navegador requieren el navegador de Playwright. Para instalarlo en esa PC:

    npx playwright install chromium

Las pruebas ordinarias no emiten nuevos comprobantes en ARCA. Los escenarios live.spec.ts requieren RUN_LIVE_TESTS=1. El recorrido full-workflow.spec.ts requiere RUN_WRITING_TESTS=1: crea registros y puede emitir comprobantes reales de homologacion; habilitarlo solo intencionalmente con TEST_BACKEND_URL apuntando al backend elegido.


## Revision de usabilidad (14/11/2026)

- Guardar muestra el resultado arriba y enfoca el aviso, tanto en escritorio como en movil. La confirmacion permanece al volver al listado.
- Los errores conservan el borrador y nombran el campo a corregir en espanol. Una consulta de numeracion fallida no tapa el error del guardado.
- Los selectores bloqueados se diferencian visualmente; la emision desde X habilita punto de venta y fechas sin modificar la identidad del origen.
- Cargas y guardados bloquean acciones prematuras; anulacion/reactivacion impiden solicitudes repetidas. La fecha fiscal elegida manualmente se conserva.
- Una pagina sin resultados muestra pagina 0 de 0. Si el guardado termina pero falla la recarga, el aviso informa ambos resultados.

Validacion realizada: **7 pruebas de logica/API/proxy y 64 escenarios de navegador aprobados**, incluyendo temas claro/oscuro, movil, respuestas tardias, errores y X a A/B. Ademas se verificaron consultas de lectura del backend, filtros invalidos y archivos PDF/Excel. Los guardados del navegador se probaron con respuestas simuladas; esta revision no emitio comprobantes reales. No se cambiaron el modelo ni las dependencias.

La configuracion de VS Code y las guias personales se conservan localmente y estan excluidas por .gitignore. Las preferencias del editor no modifican el modelo ni las dependencias.

## Validación del arranque y publicación (18/11/2026)

El envío de muestras pendientes está habilitado por defecto mediante EmisionMuestrasInicialesRunner, después de DataInitializer. El diagnóstico opcional facturacion.arca-test.enabled es independiente y permanece desactivado por defecto. Para iniciar sin enviar muestras, agregar --facturacion.datos-iniciales.emitir-arca=false al comando del backend.

Las pruebas del runner verifican la activación predeterminada, la desactivación explícita, la continuidad tras un rechazo confirmado, la detención ante ERROR_AFIP y la ausencia de reenvío de muestras ya procesadas. La detención corresponde al lote actual: un arranque posterior solo considera las muestras que todavía siguen pendientes y no reintenta el registro incierto.

Verificación realizada el 18/11/2026: Maven finalizó con 106 pruebas, sin fallos ni errores y con una omitida; npm test compiló TypeScript y aprobó las 7 pruebas. Las pruebas de contexto desactivan el envío inicial y utilizan respuestas fiscales simuladas. Los 64 escenarios de navegador corresponden a la revisión del 14/11/2026; no se repitieron en esta verificación.

Se publican únicamente fuentes, pruebas y documentación compartida. Las credenciales ARCA, la configuración de PostgreSQL, las guías personales y los artefactos generados permanecen locales e ignorados por Git.
