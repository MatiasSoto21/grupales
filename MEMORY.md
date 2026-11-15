# MEMORY.md - FacturacionApp

## Estado actual
- Java 21, Spring Boot 3.3.4, PostgreSQL y frontend TypeScript/Express; solo homologacion ARCA.
- X interno y emision fiscal A/B vinculada; nunca convertir el origen en el mismo registro.
- ARCA determina numeracion A/B. X usa numeracion local por punto de venta.
- Solo autorizacion confirmada reemplaza el X en el listado y bloquea su edicion.
- Rechazos conservan motivo; ERROR_AFIP es incierto y no se reintenta automaticamente.
- Reactivar recupera el estado respaldado por la evidencia y nunca solicita CAE.
- Servicios deciden reglas; repositorios hacen consultas, filtros, orden y paginacion.
- Clientes/articulos/facturas se consultan por paginas de 10; catalogos no descarga colecciones completas.
- Los codigos publicos de articulos son numeroCatalogo, consecutivos; BEB es una clave interna.
- Cantidades enteras positivas, precios autoritativos y fechas de negocio del backend.
- Fechas del origen inmutables; emision vinculada admite fechas validadas sin reescribir el X.

## Usabilidad
- Guardar revela y enfoca el aviso; la confirmacion sobrevive al cierre/navegacion al listado.
- Errores conservan borradores; previews tardios no reemplazan resultados ni errores del guardado.
- Cargas/guardados bloquean controles y restauran restricciones al finalizar.
- Selectores deshabilitados sin flecha; X a A/B habilita fechas y punto de venta.
- La fecha fiscal manual no se reemplaza al cambiar la fecha de operacion.
- Anular/reactivar impiden dobles solicitudes. Resultados vacios: pagina 0 de 0.

## Validacion y publicacion
- 2026-11-14: npm test, 7 aprobados; invoices.spec.ts, 64 escenarios aprobados.
- Guardados/emisiones de navegador simulados; API real verificada solo con lecturas y reportes.
- No se volvio a ejecutar la suite Java en la ultima revision de usabilidad.
- Repositorio: https://github.com/MatiasSoto21/grupales; destino main, sin force push.
- Verificar Git root antes de operar; las copias extraidas pueden heredar un repositorio del Escritorio.
- No publicar claves, certificados locales, contrasenas, tickets WSAA ni artefactos compilados.
- PostgreSQL: DB_PASSWORD o db-local.properties ignorado. ARCA: credenciales locales.
- VS Code mantiene desactivado null analysis y oculta recomendaciones de inyeccion/repositorios/versiones; no cambia el modelo ni las dependencias.
- README.md, AGENTS.md y FUNCIONAMIENTO-ARCA.md contienen las reglas detalladas.
