# MEMORY.md - FacturacionApp

Memoria del proyecto entre sesiones. Maximo 50 lineas: resume o elimina lo que ya no aporte.

## Estado actual
- v2 implementada: CRUD/reactivación de facturas, alta de cliente desde factura, filtros combinables, paginación y validación de cantidades enteras.

## Decisiones y por que
- Mantener el proyecto con Java 21 respetando el AGENTS.md.
- Mantener la creación/edición en una pantalla de formulario separada del listado; la consigna del TP es una guía funcional, no obliga a concentrar todo en `index.html`.
- Para la API REST, los datos inválidos de factura responden 400 y las operaciones sobre facturas inexistentes responden 404.
- Los filtros de cliente y comprobante usan query parameters REST; `sinCliente=true` cubre la relación opcional nula.
- Listado paginado y exportaciones consumen los mismos filtros; las exportaciones no se limitan a la página actual.
- Reactivar una factura limpia la fecha de anulación/baja y el usuario de baja; la acción directa restablece `EMITIDA`.
- El alta de cliente desde factura persiste contacto, domicilio y auditoría en una transacción; CUIT/CUIL duplicado devuelve conflicto.
- Las cantidades de ítems se validan como enteros positivos en UI y servicio; los filtros inválidos de fecha, comprobante e importe se validan antes de buscar o exportar.

## Aprendizajes y errores a evitar
- Validar que el límite inferior del rango de comprobantes no supere al límite superior.

## Proximos pasos
- Revisar validación formal del CUIT/CUIL y los flujos de cliente si el alta evoluciona a gestión completa.
