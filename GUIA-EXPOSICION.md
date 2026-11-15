# Guion de exposición y defensa del proyecto

Esta guía está basada en la implementación actual. El guion central está pensado para unos 7–8 minutos, incluyendo una demostración breve. Después tenés un mapa del código y preguntas para preparar la defensa.

No hace falta memorizar todos los archivos: entendé el recorrido de una factura y prepará los archivos señalados para poder mostrar ejemplos concretos.

## 1. Idea central que tenés que poder explicar

> Es una aplicación web para gestionar comprobantes de venta. Permite trabajar con clientes, artículos y listas de precios, calcular los importes, guardar la operación en PostgreSQL y, para comprobantes fiscales A/B, solicitar autorización a ARCA en homologación. También contempla comprobantes X internos, rechazos, errores y reportes.

Tres distinciones fundamentales:

- Guardar una operación en nuestra base no significa que ARCA la haya autorizado.
- Un comprobante X es un documento interno, no una factura A/B que falló.
- Una baja local no modifica lo que ARCA autorizó.

## 2. Guion hablado de 7–8 minutos

### 0:00–0:40 — Presentación y objetivo

**Mostrá:** el listado principal.

**Podés decir:**

> Nuestro proyecto es un sistema web de gestión de facturación. La idea fue integrar los contenidos de programación y persistencia con un caso concreto: registrar ventas, asociarlas a clientes, trabajar con artículos y listas de precios y consultar los comprobantes.
>
> Además, integramos ARCA en homologación para solicitar autorizaciones de comprobantes A y B. No todas las operaciones terminan autorizadas: el sistema distingue los errores de datos, los rechazos fiscales y los problemas de comunicación. También permite generar comprobantes X de uso interno.

**Lo que conviene destacar:** hay un flujo de negocio completo, no solamente un formulario que guarda una fila.

### 0:40–1:30 — Arquitectura y tecnologías

**Mostrá:** las carpetas backend y frontend, sin recorrer todo el árbol.

**Podés decir:**

> Separamos el proyecto en un backend con Java 21 y Spring Boot, y un frontend con HTML, CSS y TypeScript.
>
> El backend expone una API REST. Los controladores reciben las solicitudes, los servicios aplican las reglas de negocio y los repositorios y consultas JPA acceden a PostgreSQL.
>
> El frontend corre sobre Node con Express: sirve la interfaz y reenvía las solicitudes de la ruta /api al backend. La integración de ARCA y sus credenciales quedan del lado del servidor.

**Recorrido para recordar:**

    Navegador → Express /api → controlador Spring → servicio → JPA/PostgreSQL
                                                        → WSAA/WSFE de ARCA

**Si te preguntan:** Express no reemplaza al backend Java ni decide los importes fiscales; sirve la web y funciona como intermediario de sus llamadas HTTP.

### 1:30–2:30 — Modelo de datos y ORM

**Mostrá:** FacturaVenta.java, FacturaVentaDetalle.java y ListaPrecioArticulo.java.

**Podés decir:**

> La entidad central es FacturaVenta. Contiene la cabecera: cliente, condición de IVA, moneda, punto de venta, fecha, número, estado y totales.
>
> Una factura tiene varios detalles. Cada detalle guarda el artículo seleccionado mediante su precio de lista, la cantidad, el descuento y los importes calculados.
>
> Articulo representa el producto. ListaPrecio agrupa alternativas, como Minorista y Mayorista. ListaPrecioArticulo une ambos y agrega el precio de venta. Esta entidad intermedia es necesaria porque la relación tiene un dato propio: el precio.
>
> Además, al emitir guardamos el precio unitario en el detalle. Así, si después cambia el catálogo, una factura ya guardada conserva sus valores históricos.

**Cerrá este bloque con:**

> Usamos JPA y Hibernate para mapear estas clases y sus relaciones a tablas de PostgreSQL, mediante anotaciones como Entity, ManyToOne y OneToMany.

**Detalle útil:** el cliente puede ser nulo para una compra a Consumidor Final sin identificar. Eso no significa que la factura no tenga receptor fiscal.

### 2:30–4:10 — Demostración de un comprobante X

**Mostrá:** nueva factura → selector Comprobante X → productos → guardar → Ver.

**Podés decir mientras operás:**

> Primero voy a mostrar un comprobante X. Lo elegimos desde el mismo formulario, pero sigue un flujo interno: no solicita CAE a ARCA.
>
> Agrego un producto, selecciono una lista de precios, una cantidad entera y, si corresponde, un descuento. El frontend calcula una vista previa, pero el backend vuelve a validar los datos y calcula el importe definitivo a partir del precio guardado en la base.
>
> El navegador no puede imponer un precio arbitrario: manda el identificador del artículo y del precio de lista, y el servidor verifica que correspondan.
>
> Al guardar se asigna un número interno. En el listado se identifica con X y su PDF dice “DOCUMENTO NO VÁLIDO COMO FACTURA”. No lleva CAE ni QR de autorización de ARCA.

**Después:** editá ese X y cambiá una observación, si el tiempo alcanza.

> Como no es una autorización fiscal y está activo, puede editarse. Su número y su fecha originales se mantienen.

**Por qué empezar por X:** permite mostrar la aplicación aunque el servicio externo no esté disponible. Sigue siendo un caso implementado del proyecto, no un modo alternativo o una autorización simulada.

### 4:10–5:25 — Flujo de ARCA, CAE y QR

**Mostrá:** una factura A/B autorizada que ya exista → Ver.

**Podés decir:**

> Para una factura fiscal, primero el backend obtiene las credenciales de acceso a través de WSAA. Usa el certificado y la clave privada configurados para homologación y obtiene un ticket con token y sign.
>
> Después utiliza WSFE para consultar el último número autorizado del punto de venta y tipo de comprobante, y solicita el siguiente. La clase A o B se determina según la condición de IVA del receptor dentro del escenario de emisor Responsable Inscripto que implementamos.
>
> Si ARCA acepta, devuelve un CAE. Guardamos ese resultado y una instantánea de los datos fiscales usados en la autorización.
>
> El QR se construye con esos datos y el CAE, y apunta a la verificación de ARCA. No es el QR el que autoriza la factura: la autorización viene primero.
>
> Todo esto corre en homologación, por lo que los documentos se identifican como sin validez fiscal.

**Si decidís emitir en vivo:** elegí Fiscal A/B, cliente Consumidor final sin cliente, IVA Consumidor Final, pesos, un punto de venta habilitado y un importe pequeño. La respuesta depende del servicio externo; no prometas autorización garantizada.

### 5:25–6:25 — Estados, rechazos y edición

**Mostrá:** una rechazada → Motivo ARCA. Si alcanza el tiempo, anulá y reactivá esa rechazada.

**Podés decir:**

> El sistema distingue una autorización de un rechazo. Un cliente puede existir en nuestra base y tener un CUIT inventado, pero eso no lo registra en los padrones de ARCA. Por eso un intento rechazado se guarda con su motivo, sin inventar CAE.
>
> Una factura autorizada no se puede editar. Una rechazada activa sí puede corregirse localmente, pero guardar esa corrección no solicita otra autorización.
>
> También distinguimos ERROR_AFIP: un fallo de comunicación no permite saber con certeza si ARCA procesó la solicitud. Por eso lo dejamos bloqueado para edición y no reintentamos automáticamente.
>
> La anulación es una baja lógica local. Al reactivar recuperamos el estado anterior: una rechazada vuelve a rechazada y una autorizada vuelve a autorizada.

**Frase que evita una confusión frecuente:**

> Reactivar un registro no equivale a emitir una factura.

### 6:25–7:15 — Validaciones, consultas y concurrencia

**Mostrá:** filtros y ordenamiento; después, si te piden código, FacturaService.java.

**Podés decir:**

> Tenemos validaciones en ambos lados: el frontend ayuda al usuario y el backend protege la información aunque alguien invoque directamente la API.
>
> Validamos cantidades enteras positivas, descuentos dentro del rango permitido, relación entre producto y precio y coherencia de filtros. El ordenamiento se realiza en el backend antes de paginar, y los reportes reciben los mismos filtros.
>
> Las operaciones de persistencia se manejan con transacciones. Además, cada factura tiene una versión: si dos pestañas abren el mismo registro y una guarda primero, la otra recibe un conflicto al intentar sobrescribirlo.
>
> Para la numeración se utilizan bloqueos transaccionales en PostgreSQL, diferenciando los flujos de ARCA y los comprobantes X.

**Si vas justo de tiempo:** mencioná solamente validación en backend y control de versión.

### 7:15–8:00 — Pruebas, alcance y cierre

**Podés decir:**

> Verificamos reglas de negocio con pruebas del backend y la interfaz con pruebas de navegador. La última revisión ejecutó 61 pruebas del backend, 5 del frontend y 26 de navegador. También se realizaron recorridos reales contra ARCA en homologación, documentados por separado.
>
> El alcance actual cubre productos con IVA 21 %, pesos, facturas A/B y comprobantes X. No implementamos todavía notas de crédito, conciliación de resultados inciertos ni un circuito completo de pagos.
>
> Como mejora técnica, separaríamos responsabilidades de FacturaService y llevaríamos los importes a BigDecimal de extremo a extremo.
>
> El resultado es una aplicación que combina el modelo relacional, las reglas de negocio, la interfaz y un servicio externo, manteniendo diferenciados los documentos internos y las autorizaciones fiscales.

## 3. Si solamente te dan cinco minutos

Usá este orden:

| Tiempo aproximado | Tema |
| --- | --- |
| 0:00–0:30 | Objetivo y alcance de homologación |
| 0:30–1:10 | Arquitectura: frontend, API, servicios y PostgreSQL |
| 1:10–1:50 | Factura, detalles y precio de lista |
| 1:50–3:00 | Crear X y abrir su PDF |
| 3:00–4:00 | Mostrar una autorizada con CAE/QR y una rechazada con motivo |
| 4:00–5:00 | Reglas de edición, pruebas y límites |

No intentes mostrar todos los filtros ni abrir todas las clases. Si te dan diez minutos, ampliá las relaciones JPA y una prueba de concurrencia.

## 4. Mapa del código: dónde está cada cosa

Todas las rutas siguientes son relativas a la raíz del proyecto. El backend activo es backend/; la copia antigua de src/ en la raíz no es la implementación que se compila en el módulo actual.

### Arranque y configuración

| Qué buscás | Archivo |
| --- | --- |
| Arranque Spring Boot | [FacturacionApplication.java](backend/src/main/java/com/example/facturacion/FacturacionApplication.java) |
| Base de datos y configuración general | [application.properties](backend/src/main/resources/application.properties) |
| Datos de presentación del emisor y validación de su CUIT configurado | [EmisorComprobanteProperties.java](backend/src/main/java/com/example/facturacion/config/EmisorComprobanteProperties.java) |
| Datos iniciales y adecuaciones de una base existente | [DataInitializer.java](backend/src/main/java/com/example/facturacion/DataInitializer.java) |
| Dependencias y compilación del backend | [pom.xml](backend/pom.xml) |

El usuario admin del inicializador se utiliza para auditoría. No es la identidad fiscal del emisor ni equivale a un sistema de login con roles. El perfil homologacion-demo anterior ya no forma parte del flujo vigente.

### Backend y API

| Qué buscás | Archivo y referencia |
| --- | --- |
| Endpoints del listado, alta, edición, baja, reactivación y PDF individual | [FacturaRestController.java](backend/src/main/java/com/example/facturacion/controllers/FacturaRestController.java) |
| Reglas de facturación | [FacturaService.java](backend/src/main/java/com/example/facturacion/services/FacturaService.java): crearFactura, actualizarFactura, anularFactura, reactivarFactura |
| Filtros, orden y paginación | FacturaService: buscarFacturasFiltradas y contarFacturasFiltradas |
| Preparación de la instantánea para el PDF | FacturaService: obtenerComprobante |
| DTO de entrada y datos de edición | [FacturaRequestDTO.java](backend/src/main/java/com/example/facturacion/dto/FacturaRequestDTO.java) |
| DTO del listado | [FacturaReporteDTO.java](backend/src/main/java/com/example/facturacion/dto/FacturaReporteDTO.java) |
| DTO del comprobante individual | [FacturaComprobanteDTO.java](backend/src/main/java/com/example/facturacion/dto/FacturaComprobanteDTO.java) |
| Repositorios JPA | [FacturaVentaRepository.java](backend/src/main/java/com/example/facturacion/repositories/FacturaVentaRepository.java), carpeta repositories |
| Alta de cliente y CUIT duplicado | [ClienteService.java](backend/src/main/java/com/example/facturacion/services/ClienteService.java) |
| Catálogos del formulario y estados del filtro | [CatalogosRestController.java](backend/src/main/java/com/example/facturacion/controllers/CatalogosRestController.java) |
| Validación programática de DTO | [RequestValidation.java](backend/src/main/java/com/example/facturacion/services/RequestValidation.java) |
| Respuestas de errores y conflictos concurrentes | [RestExceptionHandler.java](backend/src/main/java/com/example/facturacion/controllers/RestExceptionHandler.java) |
| Consultas académicas adicionales en JPQL | [ConsultasRepository.java](backend/src/main/java/com/example/facturacion/repositories/ConsultasRepository.java) |

El listado operativo pasa por FacturaService, que valida y delega las consultas a FacturaVentaConsultasRepository (consultas) y FacturaVentaRepository (persistencia de entidades). ConsultasRepository contiene los ejemplos académicos adicionales de JPQL.

### ARCA y documentos

| Qué buscás | Archivo |
| --- | --- |
| Autenticación, certificado, firma CMS y ticket | [AfipWsaaService.java](backend/src/main/java/com/example/facturacion/services/AfipWsaaService.java) |
| Conservación y reutilización del ticket | [WsaaTicketStore.java](backend/src/main/java/com/example/facturacion/services/WsaaTicketStore.java) |
| Consulta de último número y solicitud de CAE por SOAP/XML | [AfipWsfeService.java](backend/src/main/java/com/example/facturacion/services/AfipWsfeService.java) |
| Receptor declarado y selección de A/B según IVA | [ReceptorArcaPolicy.java](backend/src/main/java/com/example/facturacion/services/ReceptorArcaPolicy.java) |
| Diferencia entre rechazo explícito y otro fallo | [ArcaRechazoException.java](backend/src/main/java/com/example/facturacion/services/ArcaRechazoException.java) |
| PDF individual y contenido del QR | [ComprobanteFacturaService.java](backend/src/main/java/com/example/facturacion/services/ComprobanteFacturaService.java) |
| Reportes generales PDF y Excel | [ReporteService.java](backend/src/main/java/com/example/facturacion/services/ReporteService.java) |

AfipController.java y AfipTestRunner.java son herramientas de diagnóstico deshabilitadas por defecto. El flujo normal se inicia desde FacturaRestController, no desde ese controlador de diagnóstico.

### Frontend

| Qué buscás | Archivo |
| --- | --- |
| Listado, filtros y diálogos principales | [index.html](frontend/public/index.html) |
| Formulario de factura y modales de cliente/producto | [factura-form.html](frontend/public/factura-form.html) |
| Listar, ordenar, paginar, anular y reactivar | [facturas.ts](frontend/src/facturas.ts) |
| Borrador, productos, cliente, consulta de número y guardado | [factura-form.ts](frontend/src/factura-form.ts) |
| Solicitudes HTTP y lectura de errores | [api.ts](frontend/src/api.ts) |
| Tipos TypeScript de las solicitudes y respuestas | [types.ts](frontend/src/types.ts) |
| Cálculo decimal de la vista previa | [money.ts](frontend/src/money.ts) |
| Tema y utilidades del DOM | [dom.ts](frontend/src/dom.ts) |
| Estilos y colores compartidos | [app.css](frontend/public/app.css) |
| Express y proxy al backend | [app.ts](frontend/src/app.ts) |
| Arranque del servidor frontend | [server.ts](frontend/src/server.ts) |

Se usa HTML y TypeScript con manipulación del DOM; no se está usando React ni Angular.

### Pruebas que vale la pena conocer

| Archivo | Qué demuestra |
| --- | --- |
| [FacturacionApplicationTests.java](backend/src/test/java/com/example/facturacion/FacturacionApplicationTests.java) | API, validaciones, estados y operaciones generales |
| [ReceptorIntegrationTest.java](backend/src/test/java/com/example/facturacion/services/ReceptorIntegrationTest.java) | A/B, rechazos, X, edición, numeración y concurrencia |
| [AfipWsfeServiceTest.java](backend/src/test/java/com/example/facturacion/services/AfipWsfeServiceTest.java) | Lectura de respuestas y armado de solicitudes, sin emitir realmente |
| [ComprobanteFacturaServiceTest.java](backend/src/test/java/com/example/facturacion/services/ComprobanteFacturaServiceTest.java) | PDF, QR y bloqueo de autorizaciones |
| [invoices.spec.ts](frontend/test/browser/invoices.spec.ts) | Interacción de la interfaz con respuestas controladas |
| [live.spec.ts](frontend/test/browser/live.spec.ts) | Comprobaciones contra un backend real, habilitadas explícitamente |
| [full-workflow.spec.ts](frontend/test/browser/full-workflow.spec.ts) | Recorrido real que crea registros y puede emitir en homologación; habilitación explícita |

No digas que cada ejecución de las pruebas emite 60 facturas reales: los servicios externos se simulan en las pruebas ordinarias y las pruebas con escritura real son optativas.

## 5. Relaciones JPA para defender el modelo

| Relación | Cómo está implementada | Cómo explicarla |
| --- | --- | --- |
| Factura → Cliente | ManyToOne, cliente opcional | Un cliente puede tener varias facturas |
| Factura → Detalles | OneToMany con cascade ALL y orphanRemoval | La cabecera administra sus renglones; cada detalle referencia su factura |
| Detalle → Factura | ManyToOne | Cada renglón pertenece a una cabecera |
| Detalle → ListaPrecioArticulo | ManyToOne | Cada renglón referencia el precio de catálogo seleccionado |
| ListaPrecioArticulo → Articulo / ListaPrecio | Dos ManyToOne y precioVenta | Resuelve una relación con atributo propio |
| Cliente → Contacto / Domicilio | OneToOne | El cliente referencia sus registros de contacto y domicilio |
| Factura → IVA / Moneda / Punto de venta | ManyToOne | Las facturas comparten valores de catálogo |
| Auditoría → Usuario | ManyToOne | Se registra quién cargó, modificó o dio de baja |
| Articulo → Marca / Rubro | ManyToOne | Clasificación de los productos |

**Herencia:** EntityId define el ID autogenerado. AuditoriaApp agrega fechas y referencias a usuarios. Son MappedSuperclass: transmiten el mapeo a las entidades concretas, no generan una tabla independiente por esas clases base.

**Lombok:** genera métodos repetitivos, como getters, setters y constructores. No reemplaza a JPA ni persiste datos por sí mismo.

**Versionado:** FacturaVenta incorpora Version para detectar modificaciones concurrentes. Su versión técnica no es el número del comprobante.

## 6. Preguntas probables y respuestas cortas

### ¿Qué diferencia hay entre entidad y DTO?

> La entidad representa los datos persistidos y sus relaciones. El DTO define lo que recibe o devuelve la API. Así no exponemos todo el grafo JPA ni permitimos que el navegador decida campos que corresponden al servidor.

El DTO de edición incluye la versión, pero el backend sigue decidiendo si el comprobante es editable.

### ¿Qué aporta un servicio frente a poner todo en el controlador?

> El controlador se ocupa de HTTP. El servicio concentra validaciones, cálculos, estados y operaciones transaccionales. Eso permite reutilizar esas reglas y probarlas sin depender de la pantalla.

Reconocé que FacturaService todavía es grande y se podría dividir más.

### ¿Qué es una transacción en este proyecto?

> Agrupa los cambios de persistencia: cabecera, detalles, importes y auditoría deben quedar consistentes. Si una validación falla y se lanza una excepción, no deberían quedar escrituras parciales de esa operación.

**Aclaración importante:** la transacción de PostgreSQL no incluye al servidor de ARCA. Si ARCA autoriza y luego falla el guardado local, no podemos deshacer automáticamente la autorización externa. Esa conciliación es una mejora pendiente.

### ¿Por qué guardan una factura rechazada?

> Porque representa un intento de operación que queremos conservar y revisar. Tiene su estado y motivo, pero no se presenta como autorizada ni se inventa un CAE.

Una validación local inválida, como una cantidad cero, se rechaza antes y no crea ese intento fiscal.

### ¿Qué diferencia hay entre RECHAZADA y ERROR_AFIP?

> En RECHAZADA recibimos un rechazo explícito. En ERROR_AFIP no tenemos una autorización confirmada: puede haber un problema de conexión o una respuesta incompleta. Por eso no es seguro reenviar ni editar automáticamente ese caso.

### ¿Al corregir una rechazada se autoriza?

> No. Se guardan correcciones locales y se mantiene rechazada. La reemisión controlada es una funcionalidad pendiente; no la presentamos como implementada.

### ¿Por qué una autorizada no puede editarse?

> Porque sus datos fiscales y su CAE corresponden a una autorización ya registrada. Si cambiáramos cliente, importe o detalles, el PDF local dejaría de representar la operación autorizada.

La anulación local tampoco crea una nota de crédito.

### ¿Qué significa EMITIDA si no tiene CAE?

> En nuestro modelo puede indicar un documento X emitido internamente o un registro local de muestra. El estado no convierte un documento interno en una autorización fiscal. Para eso verificamos el resultado de ARCA y la instantánea.

### ¿Por qué un cliente inventado falla en ARCA?

> Porque guardarlo en nuestra base no lo registra ante ARCA. El sistema envía los datos declarados y el servicio externo aplica sus validaciones. Homologación no significa que cualquier CUIT vaya a ser aceptado.

### ¿Cuál es la diferencia entre WSAA y WSFE?

> WSAA nos permite autenticarnos y obtener el ticket de acceso. WSFE recibe las operaciones de facturación: consultamos numeración y solicitamos el CAE. Las solicitudes a ARCA son SOAP/XML; nuestra API hacia el navegador es REST/JSON.

### ¿Qué es el CAE y qué es el QR?

> El CAE es el código de autorización devuelto por ARCA. El QR contiene datos de la operación y del CAE para acceder a su verificación. Generar una imagen QR por sí solo no autoriza una factura.

### ¿Quién es el emisor?

> Es la identidad fiscal configurada para usar el certificado y WSFE. No es necesariamente el usuario admin de la aplicación. El usuario local se usa para auditoría.

No muestres la clave privada ni uses su contenido como material de la exposición.

### ¿De dónde sale el número?

> Para A/B lo consultamos a ARCA por punto de venta y tipo de comprobante, y volvemos a comprobarlo al guardar. Para X hay una secuencia lógica local independiente por punto de venta, calculada con protección transaccional.

No lo confundas con el ID de la tabla ni digas que se obtiene del máximo global de facturas.

### ¿Qué pasa con dos usuarios al mismo tiempo?

> Para editar usamos versionado optimista: si el registro cambió, se devuelve un conflicto 409. Para numerar utilizamos bloqueos transaccionales por flujo. El synchronized de Java ayuda dentro de una instancia, pero no es la única protección.

### ¿Cómo calculan el total?

> Por renglón calculamos precio por cantidad, aplicamos descuento, redondeamos el neto a centavos, calculamos el IVA del 21 % y sumamos. El backend usa cálculo decimal y redondeo HALF_UP. La vista previa del frontend también aplica cálculo decimal.

**Ejemplo sencillo:** precio 1.000, cantidad 2, descuento 10 %: neto 1.800, IVA 378, total 2.178.

**Límite que debés reconocer:** varios atributos monetarios todavía están almacenados como double; no está migrado todo el modelo a BigDecimal.

### ¿Por qué el backend vuelve a calcular?

> Porque el navegador puede ser modificado. El backend consulta el precio real de la base, verifica que pertenezca al artículo y no confía en un importe arbitrario enviado por el cliente.

### ¿Por qué no usan PAGADA y PENDIENTE como estados nuevos?

> Porque esta versión no implementa un circuito completo de pagos. Por la regla del proyecto, el importe cobrado coincide con el total y el saldo es cero. Esas etiquetas solo se conservan como filtros si existen registros históricos.

### ¿Qué códigos HTTP usan?

| Código | Ejemplo del proyecto |
| --- | --- |
| 200 | Consulta o actualización correcta |
| 201 | Alta de un comprobante o cliente |
| 204 | Anulación o reactivación sin cuerpo de respuesta |
| 400 | JSON, cantidad, filtros o datos inválidos |
| 404 | ID inexistente |
| 409 | CUIT duplicado, edición bloqueada o versión desactualizada |
| 503 | No se puede consultar la numeración ARCA |

Un alta puede responder 201 y devolver un registro RECHAZADA: el registro local fue creado correctamente, aunque ARCA no autorizó la operación. El proxy también informa 502/504 si no puede comunicarse con el backend.

### ¿El sistema ya sirve para producción?

> No es lo que estamos presentando. Está restringido a homologación y tiene un alcance académico definido. No implementa todo el circuito fiscal, conciliación ni gestión completa de usuarios y pagos.

### ¿Qué probaron?

> Validaciones, precios, cantidades y descuentos; filtros y exportaciones; estados y bloqueos; PDF y QR; formularios y modales; versiones obsoletas y conflictos concurrentes. Además, hubo ensayos reales con autorizaciones y rechazos de homologación.

No afirmes que no existen más bugs: explicá qué casos fueron verificados.

## 7. Preparación práctica para mañana

1. Comprobá PostgreSQL, el backend actualizado y el frontend antes de empezar.
2. Abrí localhost:3000 y verificá que el listado indique conexión con el backend.
3. Tené identificados un X, una autorizada con CAE/QR y una rechazada con motivo. No dependas de IDs o números de documentación: pueden corresponder a una base anterior.
4. Tené disponible un PDF autorizado ya generado por si ARCA no responde durante la presentación.
5. Ensayá una creación X. Si vas a emitir A/B en vivo, probá previamente el punto de venta y un importe pequeño a Consumidor Final sin identificar.
6. Abrí solo los archivos clave: FacturaVenta, FacturaVentaDetalle, ListaPrecioArticulo, FacturaRestController, FacturaService y ReceptorArcaPolicy.
7. No ejecutes pruebas con escritura real durante la exposición: pueden crear nuevos registros y consumir tiempo.

**Arranque habitual:**

- Backend: ejecutar FacturacionApplication desde el IDE.
- Si usás Maven en Windows con la ruta actual: mapear la raíz a R: si está libre, entrar a R:\backend y ejecutar .\mvnw.cmd spring-boot:run.
- Frontend: desde frontend, ejecutar npm start.
- URLs habituales: frontend localhost:3000 y API localhost:8080/api.
- No hacen falta perfiles demo ni parámetros alternativos de receptor.

**Si algo falla durante la demo:**

- Si ARCA no responde: explicá que es una dependencia externa, mostrá un documento autorizado existente y continuá con X.
- Si un CUIT ficticio es rechazado: mostrá el motivo; ese es un resultado esperado del flujo.
- Si aparece un 409 al editar: explicá si el registro está protegido o si quedó desactualizado. No intentes forzar el guardado.
- Si no recordás una respuesta: ubicá la regla en el servicio o reconocé el límite. Es preferible a atribuir una función que no existe.

## 8. Frases que conviene evitar

| Evitá decir | Mejor decir |
| --- | --- |
| “El QR hace válida la factura.” | “El QR representa datos de una autorización previa.” |
| “En homologación acepta cualquier cliente.” | “Es un entorno de pruebas con validaciones del servicio.” |
| “Si está guardada, está autorizada.” | “Distinguimos persistencia local y autorización ARCA.” |
| “La factura X es una B rechazada.” | “X es un documento interno elegido explícitamente.” |
| “Anular elimina la factura de ARCA.” | “La anulación implementada es una baja local.” |
| “Editar una rechazada la vuelve a emitir.” | “La edición guarda correcciones; no reenvía.” |
| “Usamos BigDecimal en toda la base.” | “Calculamos con redondeo decimal, pero quedan campos double.” |
| “El admin es el contribuyente emisor.” | “El admin registra auditoría; el emisor es la identidad fiscal configurada.” |
| “Todas las pruebas llaman a ARCA.” | “Las ordinarias simulan el servicio externo; los recorridos reales son optativos.” |
| “Está terminado y no tiene errores.” | “Este es el alcance implementado y estos son los casos verificados.” |

## 9. Repaso de un minuto antes de entrar

- Objetivo: gestión de comprobantes con persistencia e integración de homologación.
- Arquitectura: TypeScript/Express → REST Spring → servicio → PostgreSQL/ARCA.
- Modelo central: Factura, Detalle, Cliente, Articulo y ListaPrecioArticulo.
- Precio: lo decide el backend y queda guardado históricamente.
- Fiscal: WSAA autentica, WSFE procesa la solicitud de CAE.
- X: número local, sin CAE ni QR ARCA.
- Estados: autorización, rechazo, incertidumbre y baja local se distinguen.
- Edición: con CAE no; rechazada activa sí, sin reenvío; error incierto y anulada no.
- Concurrencia: Version y conflictos 409; numeración con bloqueo transaccional.
- Alcance: homologación, PES, IVA 21 %, A/B y X; sin conciliación ni notas de crédito.



## Explorador de clientes

**Demostración:** abrí una factura y pulsá **Buscar cliente**. Buscá por una parte de la denominación o del CUIT/CUIL, cambiá a coincidencia exacta, filtrá por condición de IVA y ordená los resultados. Seleccioná un cliente y mostrá que el borrador conserva sus productos y observaciones.

> La selección se hace con un explorador modal. La búsqueda, los filtros y el ordenamiento se resuelven en la base antes de paginar. El navegador recibe diez clientes por página; no descarga la lista completa. La búsqueda por nombre ignora mayúsculas y minúsculas, y la de CUIT admite espacios y guiones. Los filtros se pueden combinar.

El mismo explorador permite elegir un cliente para filtrar el listado de facturas y sus exportaciones. **Sin cliente** filtra consumidores finales sin cliente asociado; **Todos** elimina esa restricción.

**Archivos para defenderlo:** `frontend/src/cliente-explorer.ts`, `backend/src/main/java/com/example/facturacion/controllers/ClienteRestController.java`, `backend/src/main/java/com/example/facturacion/services/ClienteService.java` y `backend/src/main/java/com/example/facturacion/repositories/ClienteRepository.java`.


## Explorador de artículos por lista de precios

**Demostración:** abrí una factura y pulsá **Agregar producto**. La primera selección es la lista de precios: abre en **Mayorista**. Mostrá que al elegir **Minorista** cambian los precios y se limpia la selección del artículo anterior. Buscá por código o denominación, probá coincidencia exacta o parcial, ordená por precio y avanzá de página. Seleccioná un producto, indicá cantidad y bonificación y agregalo. **Cambiar** vuelve a abrir el explorador para ese ítem.

> Primero elegimos la lista de precios y después buscamos entre los artículos que tienen un precio válido en esa lista. Los filtros y el orden se resuelven antes de paginar en el backend: el navegador recibe diez resultados, aunque haya miles de productos. Cada selección conserva el ID del artículo y el ID exacto de su precio; el servidor vuelve a validar esa relación al guardar.

El inicializador agrega **1000 productos adicionales**, con nombres de bebidas diferenciados por marca, sabor, volumen y envase, códigos descriptivos `BEB-`, marcas, rubros y precios minoristas y mayoristas. También funciona sobre una base existente. Reiniciar no los duplica ni sobrescribe los precios ya guardados. Los detalles de facturas anteriores mantienen sus importes históricos.

**Archivos para mostrar:** `frontend/src/articulo-explorer.ts`, `frontend/src/factura-form.ts`, `backend/src/main/java/com/example/facturacion/services/ArticuloService.java` y `backend/src/main/java/com/example/facturacion/DataInitializer.java`.


## Del comprobante X a una emisión fiscal

**Demostración:** crear un X de importe pequeño. Mostrar que el total interno no suma IVA. Abrir **Editar** y pulsar **Emitir A/B en ARCA**. Revisar el enlace al origen, la fecha de la operación, la fecha fiscal elegida, la nueva numeración consultada y el total fiscal antes de confirmar **Emitir en ARCA**. Solo hacerlo intencionalmente en homologación.

> El X no cambia de identidad. Creamos una factura A/B nueva, vinculada al X, porque cada uno tiene su numeración y fecha. El original se conserva. Guardar una corrección no emite nada; la emisión fiscal es una acción separada. En un rechazo confirmado también se conserva el intento y su motivo antes de pedir uno nuevo. Un resultado incierto no se reenvía.

Con precios netos de $100, el modelo guarda X por $100 y una A/B fiscal por $121. X no es una exención impositiva. En A se muestra el IVA discriminado; en B se informa el IVA contenido dentro del total y el desglose de transparencia. Los importes definitivos y todas las restricciones los decide el backend.

**Para defender la concurrencia:** el endpoint `/api/facturas/{id}/emitir-fiscal` exige la versión cargada y bloquea el origen durante la transacción. Dos solicitudes simultáneas no emiten dos veces. La relación de origen es única: un X con intento fiscal no puede originar un duplicado, pero sigue editable mientras no haya autorización confirmada. Un origen fiscal con un intento posterior queda sin edición.


## Separación entre servicios y repositorios

> El servicio decide si una operación es válida y coordina su transacción. El repositorio conoce cómo consultar o persistir los datos. Por eso las consultas JPQL, sus filtros, el ORDER BY y los límites de paginación se encuentran en repositories. FacturaService conserva las reglas de importes, IVA, estados, fecha e integración con ARCA.

**Ejemplo para mostrar:** `FacturaService.buscarFacturasFiltradas` valida los filtros y llama al repositorio. `FacturaVentaConsultasRepository` construye la consulta y comparte los mismos predicados con el conteo para que el total y la página coincidan. En clientes y artículos, las Specifications también se construyen dentro de sus repositorios.

Los bloqueos de numeración se ejecutan desde `BloqueoTransaccionalRepository`, pero la transacción sigue en el servicio: dura hasta terminar de guardar la operación. Mover la consulta no debe acortar ese bloqueo ni permitir emisiones duplicadas. Los 22 ejemplos académicos JPQL se muestran ahora en `ConsultasRepository`.


## Cómo explicar la fecha al emitir desde un X

> La fecha del X, la de la operación, la de la factura fiscal y el momento de pedir CAE no tienen por qué coincidir. Si vendimos el jueves y solicitamos autorización el sábado, podemos consignar jueves dentro del plazo admitido y si no contradice la fecha de la última factura de la serie. Si el X era un presupuesto y vendimos el sábado, indicamos sábado como operación y fecha fiscal. El X original se conserva en ambos casos.

**Demostración:** abrir **Emitir A/B en ARCA**, revisar las dos fechas y mostrar que el backend fija el rango de selección. Una fecha inválida no se cambia automáticamente por hoy. Las pruebas con reloj fijo reproducen el jueves 12/11/2026 y el sábado 14/11/2026 sin emitir comprobantes reales.

**Archivos:** `FechaFacturacionPolicy.java` contiene la validación; `FechaFacturacionConfig.java` define el reloj de Argentina; `AfipWsfeService.java` recibe la fecha explícita y consulta la última autorizada. La base guarda `fechaOperacion`, `fechaEmision` y `fechaSolicitudAfip`; PDF y QR usan la misma fecha fiscal enviada a ARCA.

### Flujo X y fiscal
El listado reemplaza visualmente al X solo cuando ARCA confirma la autorización del A/B. Ambos registros quedan bloqueados para edición; el X permanece como antecedente sin alterar sus datos originales. Si hay rechazo o resultado incierto, el X permanece visible y el intento fiscal conserva su resultado. No hay reintentos automáticos. La autorización de un intento posterior también reemplaza al X de origen.

La página utiliza Ver y Editar para el comprobante de la fila, con Anular/Reactivar y Motivo ARCA cuando corresponda. Las consultas, conteos y exportaciones aplican la misma regla de visibilidad en la base de datos antes de paginar. Las referencias compactas distinguen sucursales repetidas.

## Detalles de usabilidad para mostrar en la defensa

- Guardar desde el final del formulario: el resultado aparece arriba y se mantiene al volver al listado.
- Intentar una cantidad fraccionaria: aparece un error en espanol identificando el producto y el borrador se conserva.
- Mostrar un X en edicion: clase y punto de venta se ven bloqueados. Emitir A/B en ARCA abre el borrador fiscal y habilita punto de venta y fechas; la clase fiscal y el IVA del cliente permanecen protegidos. No confirmar una emision real salvo que sea parte intencional de la demostracion.
- Mostrar que una busqueda sin resultados se recupera con Limpiar y que los errores de carga no habilitan un formulario incompleto.
- Explicar que la interfaz evita dobles acciones mientras espera y diferencia un guardado exitoso de un fallo posterior al actualizar el listado.

Pruebas actuales de frontend: 7 verificaciones de logica/API/proxy y 64 escenarios de navegador aprobados el 14/11/2026. Incluyen movil, teclado, temas, borradores, errores y flujo X a A/B. Los escenarios de guardado usan respuestas simuladas; las comprobaciones reales de esta revision fueron de lectura. No se modifico el modelo de datos.
