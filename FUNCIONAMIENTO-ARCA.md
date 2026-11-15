# Funcionamiento del proyecto con ARCA

El proyecto usa un unico flujo: HOMOLOGACION real de ARCA. Se inicia normalmente desde Spring Boot, sin perfiles adicionales. No hay un modo que cambie el receptor para forzar autorizaciones. Los comprobantes de este entorno dicen SIN VALIDEZ FISCAL.

## Ejecutar

Con PostgreSQL disponible, ejecutar FacturacionApplication desde el IDE. Por terminal, desde la raiz del proyecto en Windows:

```powershell
subst R: "$PWD"
Set-Location R:\backend
.\mvnw.cmd spring-boot:run
```

Frontend desde frontend: `npm start`. Si ya estaba ejecutandose el backend, reiniciarlo para cargar la nueva configuracion. Quitar argumentos antiguos de perfil o receptor-modo de la configuracion del IDE; ya no son necesarios.

El emisor usa el CUIT asociado al certificado WSAA y a WSFE. No depende del administrador del DataInitializer. La denominacion y domicilio de presentacion conservan los datos de prueba elegidos. Se pueden configurar con FACTURACION_EMISOR_DENOMINACION, FACTURACION_EMISOR_CONDICION_IVA y FACTURACION_EMISOR_DOMICILIO. Estos datos no cambian el padron fiscal del emisor. El alcance implementado es emisor Responsable Inscripto, productos con IVA 21 %, moneda PES y comprobantes A/B en homologacion; no es un facturador de produccion ni implementa comprobantes C o exportaciones.

## Que determina la autorizacion

El sistema envia el CUIT/CUIL declarado del cliente y su condicion de IVA. Para el emisor Responsable Inscripto del proyecto, corresponde A a Responsable Inscripto o Monotributista; B a Consumidor Final o Exento. El formulario selecciona la clase y consulta la numeracion correspondiente. La numeracion es independiente por punto de venta y tipo de comprobante.

Un cliente inventado puede guardarse para la simulacion, pero su alta local no lo inscribe en ARCA. Tener once digitos, un digito verificador correcto o una condicion IVA seleccionada no garantiza una autorizacion: ARCA valida padrones, compatibilidad fiscal, punto de venta, importes y otros requisitos. No se consulta un padron local ficticio ni se declara validado un CUIT por su formato.

Sin cliente seleccionado se envia una compra a Consumidor Final sin identificar: documento 99, numero 0, condicion IVA 5, Factura B. Esto es un escenario distinto de elegir un cliente con CUIT ficticio y esta sujeto a los limites de identificacion que aplica ARCA.

| Resultado | Se guarda | CAE | QR de verificacion |
| --- | --- | --- | --- |
| AUTORIZADA: ARCA confirma la solicitud | Si | Real de homologacion | Si, con instantanea fiscal completa |
| RECHAZADA: ARCA devuelve un rechazo explicito | Si, con motivo | No | No; PDF de vista previa |
| ERROR_AFIP: comunicacion fallida o respuesta incompleta | Si, con motivo | Sin autorizacion confirmada | No; PDF de vista previa |
| Datos locales invalidos, por ejemplo cantidad cero | No; HTTP 400 | No | No |

En ERROR_AFIP no hay que asumir que ARCA rechazo: un corte puede ocurrir despues de que ARCA procese la solicitud. No se reenvia automaticamente; la conciliacion y reemision de esos casos quedan fuera del flujo implementado. El sistema no inventa un CAE para completar el registro.

## Escenarios para presentar en la facultad

1. Autorizacion: nueva factura, cliente Consumidor final (sin cliente), IVA Consumidor Final, pesos, punto de venta habilitado y uno o dos productos de importe pequeno. Guardar. Si ARCA acepta, queda con CAE y en Ver aparece el QR de homologacion.
2. Rechazo: crear un cliente claramente identificado como ficticio, CUIT 00-12345678-9 y condicion Responsable Inscripto. Emitir una factura A con productos validos. Se envia ese documento declarado; el rechazo fiscal queda guardado con su codigo y motivo. Si ese CUIT ya existe, reutilizar el cliente. El boton Motivo ARCA permite revisar la respuesta desde el listado.
3. Validacion local: intentar agregar cantidad cero o fraccionaria, o enviar un precio de otro articulo. El sistema lo impide antes de solicitar CAE.

Los clientes del DataInitializer son datos de muestra y no tienen autorizacion garantizada. Las facturas iniciales sin CAE se conservan como muestras. Las autorizaciones antiguas mantienen la instantanea con la que fueron emitidas, incluso las que antes usaban receptor de prueba. Anular o reactivar localmente no revoca el CAE en ARCA. Editar un rechazo no lo reenvia a ARCA ni lo convierte en autorizado.

## Fuentes oficiales

- Tipos de comprobante segun emisor y receptor: https://www.arca.gob.ar/facturacion/regimen-general/comprobantes.asp
- Manual WSFE y validaciones: https://www.arca.gob.ar/ws/documentacion/manuales/manual-desarrollador-ARCA-COMPG.pdf

## Verificaciones

Backend: `.\mvnw.cmd clean test` desde R:\backend. Frontend: `npm test` y `npm run test:e2e -- invoices.spec.ts` desde frontend. Las pruebas ordinarias simulan solo los servicios externos y no emiten comprobantes. El recorrido completo contra ARCA requiere RUN_WRITING_TESTS=1 y un backend con esta version en TEST_BACKEND_URL; crea registros y conserva evidencia en frontend/test-results/full-workflow.


Registro historico de una verificacion real anterior (los IDs dependen de esa base): intento ID 60 rechazado con codigo ARCA 10015 (DocNro invalido), sin CAE; factura ID 61, comprobante B 9, autorizada en homologacion con PDF. La prueba tambien anulo y reactivo localmente esa factura, conservando su CAE. Con la correccion vigente, reactivar recupera AUTORIZADA. Evidencia conservada en .test-evidence/unified-homologacion. Pasaron 52 pruebas ordinarias del backend, 5 pruebas de frontend y 16 pruebas de navegador, incluidas las de integracion real.


## Comprobantes X

En el mismo formulario, elegir Comprobante X en el selector Comprobante. Son documentos internos, sin autorizacion fiscal: no se consulta WSAA/WSFE ni el padron de ARCA, no se solicita CAE ni se genera QR ARCA. Pueden usarse con clientes ficticios. Se mantienen las validaciones locales de cliente, productos y precios.

El numero se asigna localmente por punto de venta, independiente de A/B; listado y reportes muestran X- para distinguirlo. El PDF dice COMPROBANTE X y DOCUMENTO NO VALIDO COMO FACTURA, sin presentarlo como una factura fiscal rechazada. Su estado inicial es EMITIDA y se puede editar/anular/reactivar localmente. La clase y el punto de venta quedan fijos en la edicion normal. Para emitir A/B desde un X se usa la accion explicita que crea otro comprobante vinculado; el X original conserva sus datos. No se convierte un rechazo fiscal en X.

La denominacion coloquial factura X corresponde aqui a un comprobante interno, no a una factura fiscal que reemplace A/B. Referencia oficial: https://www.arca.gob.ar/facturacion/comprobantes/situaciones-especiales.asp


## Estados, edicion y anulacion

| Estado | Editar | Anular localmente | Al reactivar |
| --- | --- | --- | --- |
| EMITIDA, sin CAE (X o muestra local) | Si | Si | EMITIDA |
| AUTORIZADA o cualquier registro con CAE | No | Si; no revoca el CAE | AUTORIZADA |
| RECHAZADA, sin CAE | Si, conserva el rechazo | Si; archiva el intento | RECHAZADA |
| ERROR_AFIP, sin confirmacion | No; requiere verificar resultado en ARCA | Si; no resuelve la incertidumbre | ERROR_AFIP |
| ANULADA | No; primero reactivar | Ya esta anulada | Estado anterior respaldado por su evidencia fiscal |

Editar no permite elegir un estado, autorizar ni reenviar una factura. Corregir una rechazada guarda los cambios y mantiene RECHAZADA: no se solicita un nuevo CAE. Las anulaciones son bajas locales y no son notas de credito fiscales. La conciliacion de errores ambiguos y las notas de credito no estan implementadas.

PENDIENTE y PAGADA no se generan en este proyecto: no hay circuito de cobros parciales, y el importe cobrado coincide siempre con el total por la consigna. Solo apareceran como filtros historicos si ya hay registros existentes con esas etiquetas. Las opciones vigentes del filtro las informa el backend.

Se elimina el selector de estado al editar una anulada. Al reiniciar, las antiguas etiquetas EMITIDA incompatibles con resultados ARCA confirmados A/R se reparan sin tocar CAE, QR, importes ni anulaciones.


## Cambios simultaneos

Cada comprobante tiene una version. Si dos pestañas lo abren y una guarda primero, la otra recibe un conflicto al intentar guardar su version vieja. Se conserva el borrador y se indica recargar el formulario. Esto tambien protege frente a una edicion que compite con anulacion o reactivacion.

Para preparar otra PC y el ZIP, consultar README.md. Para estudiar el proyecto, consultar GUIA-EXPOSICION.md.


## Emitir A/B desde un X o corregir un rechazo

1. Abrir **Editar** en un X activo o en un rechazo fiscal confirmado. Corregir cliente, productos u observaciones si corresponde.
2. **Guardar cambios** conserva el registro y su estado; no llama a ARCA.
3. **Emitir A/B en ARCA** (X) o **Nueva emisión A/B** (rechazo) prepara un nuevo borrador fiscal. Se muestran el comprobante de origen, la fecha de la operación, la fecha fiscal elegida, el número consultado a ARCA y el total fiscal. La clase A/B se deriva de la condición de IVA del receptor.
4. **Emitir en ARCA** realiza la solicitud de homologación y crea un registro nuevo vinculado. No sustituye el X ni el rechazo original.

La fecha del original nunca cambia. El nuevo borrador propone la fecha de la operación del origen (o la del X como valor inicial a revisar) y permite elegir una fecha fiscal dentro del rango que informa el backend. No se fuerza la fecha del día al enviar. La numeración fiscal corresponde a ARCA y es independiente de X. Usar un X interno no posterga las obligaciones legales de emitir el comprobante fiscal de la operación.

Una vez registrado el intento vinculado, el origen no puede volver a enviarse. El X sigue visible y editable si el intento fue rechazado o quedó sin confirmar. Cuando ARCA autoriza un A/B vinculado, ese X queda como antecedente de solo lectura y desaparece del listado operativo. Un origen fiscal con un intento posterior queda protegido contra edición. Esto también se cumple si la nueva solicitud queda rechazada o con resultado incierto. En un nuevo rechazo confirmado puede corregirse ese nuevo registro y solicitarse, explícitamente, otro intento. Un `ERROR_AFIP` sigue bloqueado hasta una conciliación que este proyecto no implementa. No hay reintentos automáticos.

**A/B a X:** no se permite. Una factura autorizada no se reescribe para quitarle su carácter fiscal; para corregirla o anularla corresponde la nota fiscal pertinente, fuera del alcance de este proyecto. La anulación local no revoca el CAE.

### Importes e IVA

Las listas de este proyecto se interpretan como precios netos. Para los artículos fiscales del modelo se aplica 21%, con redondeo por línea y cálculo definitivo en el backend:

| Documento | Precio neto de lista de $100, sin descuentos | Presentación |
| --- | --- | --- |
| X interno | Total interno $100 | Sin añadir ni discriminar IVA fiscal; no válido como factura |
| A | Neto $100 + IVA $21 = total $121 | IVA discriminado |
| B | Total $121, IVA contenido $21 | Desglose de transparencia fiscal; no se agrega otro IVA al total |

X no significa que una venta gravada se vuelva exenta: documenta un importe interno. Su posterior emisión fiscal se recalcula desde la lista seleccionada y muestra ese resultado antes de confirmar. Los X antiguos y las autorizaciones históricas no se recalculan en el arranque.

Fuentes: [documentos X](https://www.arca.gob.ar/facturacion/comprobantes/situaciones-especiales.asp), [anulación de comprobantes con CAE](https://servicioscf.afip.gob.ar/publico/abc/consultas_detalle.aspx?id=6490326), [transparencia fiscal](https://www.afip.gob.ar/comunicacion/transparencia-fiscal/), [momento de emisión](https://www.afip.gov.ar/facturacion/comprobantes/emision.asp).


## Ejemplo de jueves a sábado: tres fechas distintas

| Situación | Fecha del X | Fecha de operación | Fecha fiscal elegida | Solicitud del CAE |
| --- | --- | --- | --- | --- |
| Venta realizada el jueves, solicitud el sábado | Jueves | Jueves | Jueves, si cumple plazo y correlatividad | Sábado |
| Presupuesto del jueves, venta realizada el sábado | Jueves | Sábado | Sábado | Sábado |

En **Emitir A/B en ARCA** se revisan **Fecha de la operación** y **Fecha del comprobante fiscal**. Cambiar la operación propone esa misma fecha para el comprobante, que sigue siendo seleccionable. El backend valida ambas y conserva el X original. Si la fecha está fuera de rango no se sustituye automáticamente: hay que revisar el caso.

Para el modelo de productos de este proyecto, se admiten fechas fiscales desde cinco días corridos antes hasta el día actual de Argentina. La aplicación restringe intencionalmente las fechas futuras, mantiene el mes de la operación y no admite una fecha fiscal anterior a la operación registrada. No implementa el régimen de servicios de diez días.

También se consulta la fecha de la última factura autorizada del mismo punto de venta y clase. Si esa fecha es posterior a la elegida, se devuelve un error antes de pedir CAE. El sistema no cambia la fecha para eludir esa restricción. La fecha que se envía en `CbteFch` es exactamente la almacenada y usada en el PDF y QR.

El instante de envío se guarda aparte en `fechaSolicitudAfip` y puede consultarse en el detalle REST. Las fechas fiscales ya persistidas no cambian por editar, archivar o reactivar. Las reglas de fecha están en `FechaFacturacionPolicy`; las consultas y bloqueos de base continúan en repositorios.

El plazo de transferencia de la solicitud de autorización no reemplaza la obligación de emitir y entregar el comprobante cuando corresponda a la operación. Referencias: [RG 4291, artículo 9](https://biblioteca.afip.gob.ar/search/query/norma.aspx?p=t:RAG%7Cn:4291%7Co:3%7Ca:2018%7Cf:02/08/2018), [momento de emisión](https://www.afip.gov.ar/facturacion/comprobantes/emision.asp), [manual WSFE](https://www.afip.gob.ar/fe/ayuda/documentos/wsfev1-COMPG.pdf).

## Listado y comprobante de origen
Al confirmar ARCA la autorización, el A/B reemplaza al X en el listado y en las exportaciones habituales. El X se conserva en la base con sus datos originales y deja de ser editable; el A/B autorizado también queda bloqueado. Si la autorización llega después de corregir un rechazo, se aplica la misma regla al X inicial.

Un rechazo o una falla sin confirmación no reemplazan al X: conserva su número, fecha, datos y visibilidad. El intento fiscal queda registrado por separado con su resultado. Un error incierto no habilita otro envío automático ni una segunda solicitud desde el mismo X.

Cada fila usa Ver para su propio PDF y Editar solamente si ese registro admite cambios. No hay botones alternativos para editar el X desde una factura fiscal. Los filtros de la página incluyen punto de venta, sin importe mínimo, tipo de documento ni selector de historial. Los números del listado son compactos (1, X-1), con la sucursal entre paréntesis si se repiten.

## Respuesta visual y verificacion actual (14/11/2026)

Al guardar, el formulario sube al aviso y muestra si el registro fue autorizado, rechazado o quedo sin confirmacion. Un rechazo o resultado incierto conserva su motivo y el boton Cerrar; no habilita reenvios automaticos. Un error HTTP conserva el borrador y restaura las restricciones de los campos.

La confirmacion de exito permanece en el listado al cerrar el modal o volver desde el formulario independiente. Si la recarga falla despues de guardar, se informa esa diferencia para evitar repetir una operacion que ya termino.

Al preparar una emision A/B desde X se habilitan el punto de venta y las fechas. La clase fiscal y el IVA almacenado del cliente permanecen bloqueados y sin apariencia de desplegable activo. Corregir la fecha de operacion no reemplaza una fecha fiscal que ya se eligio manualmente. Las reglas fiscales siguen validandose en el backend.

Ultima pasada de frontend: 7 pruebas con npm test y 64 escenarios con npm run test:e2e -- invoices.spec.ts, todos aprobados. Se usaron respuestas interceptadas para guardados y emisiones. Las consultas reales fueron de lectura: catalogos pequenos, paginacion, filtros invalidos, PDF individual y reportes PDF/Excel. Esta pasada no emitio nuevos comprobantes ni ejecuto nuevamente la suite Java.

Las credenciales ARCA y la configuracion personal de PostgreSQL se mantienen fuera de Git. Consultar README.md para configurar otra computadora.
