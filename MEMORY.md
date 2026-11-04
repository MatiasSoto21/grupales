# MEMORY.md - FacturacionApp

Memoria del proyecto entre sesiones. Maximo 50 lineas: resume o elimina lo que ya no aporte.

## Estado actual
- v2 implementada: CRUD/reactivación de facturas, alta de cliente desde factura, filtros combinables, paginación, modales de factura/cliente/producto y validación de cantidades enteras.
- Las nuevas facturas inician sin líneas de producto precargadas.
- El importe cobrado no es editable ni parcial: siempre coincide con el total calculado y el saldo es cero.
- Número y fecha de emisión son inmutables desde el formulario: el backend genera ambos al crear; al editar, el payload los conserva y el backend no los modifica. La numeración usa el máximo persistido, incluidas facturas anuladas.
- La interfaz de facturas se mantiene limpia de textos guía, placeholders explicativos y etiquetas "estimado".
- El listado ordena por número, fecha o total desde un único botón con modal; el cambio vuelve a la página 1 y solo aplica la respuesta más reciente para evitar rebotes entre solicitudes.

## Decisiones y por que
- Mantener el proyecto con Java 21 respetando el AGENTS.md.
- Mantener el formulario en `factura-form.html`, pero abrirlo como modal desde el listado sin abandonar la página principal.
- Usar modales para crear clientes y agregar productos, además de confirmaciones modales para anular/reactivar.
- Para la API REST, los datos inválidos de factura responden 400 y las operaciones sobre facturas inexistentes responden 404.
- Los filtros de cliente y comprobante usan query parameters REST; `sinCliente=true` cubre la relación opcional nula.
- Listado paginado y exportaciones consumen los mismos filtros; las exportaciones no se limitan a la página actual.
- Reactivar una factura limpia la fecha de anulación/baja y el usuario de baja; la acción directa restablece `EMITIDA`.
- El alta de cliente desde factura persiste contacto, domicilio y auditoría en una transacción; CUIT/CUIL duplicado devuelve conflicto.
- Las cantidades de ítems se validan como enteros positivos en UI y servicio; los filtros inválidos de fecha, comprobante e importe se validan antes de buscar o exportar.
- Los artículos manejan precios mediante `ListaPrecioArticulo`: Minorista y Mayorista son ejemplos iniciales cargados por el `DataInitializer`.
- Cada línea de factura conserva el ID de `ListaPrecioArticulo` seleccionado; al editar se recalcula usando ese precio, sin permitir que el frontend envíe precios arbitrarios.
- La interfaz permite cambiar entre modo claro y oscuro desde el encabezado; la preferencia se conserva en `localStorage`.
- La interfaz usa una paleta visual compartida de alto contraste y superficies consistentes para tarjetas, tablas, formularios y modales en ambos temas.

## Aprendizajes y errores a evitar
- Validar que el límite inferior del rango de comprobantes no supere al límite superior.

## Proximos pasos
- Revisar validación formal del CUIT/CUIL y los flujos de cliente si el alta evoluciona a gestión completa.
