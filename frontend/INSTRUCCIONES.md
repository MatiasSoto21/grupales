# Sistema de facturación — HTML5 + TypeScript

Frontend del CRUD de facturas. Las vistas están hechas con HTML5 semántico, los estilos con CSS y la lógica del navegador y el servidor local con TypeScript. El TypeScript se compila a JavaScript dentro de `dist/` antes de ejecutarse en el navegador.

## Requisitos
- Node.js LTS y npm.
- Backend Spring Boot funcionando en `http://localhost:8080`.
- PostgreSQL disponible para el backend.

## Instalación y ejecución
1. Descomprimí esta carpeta.
2. Abrí una terminal en la carpeta `FacturacionFrontTS`.
3. Instalá las dependencias: `npm install`.
4. Iniciá el frontend: `npm start`.
5. Abrí `http://localhost:3000`.

Para desarrollar y recompilar automáticamente, ejecutá `npm run dev`.

## Estructura
- `public/index.html`: listado, filtros y accesos a reportes (HTML5).
- `public/factura-form.html`: formulario de alta y edición (HTML5).
- `public/app.css`: estilos responsive.
- `src/facturas.ts`: listado, filtros y anulación.
- `src/factura-form.ts`: formulario de alta y edición.
- `src/api.ts`: comunicación tipada con la API REST de Spring Boot.
- `src/types.ts`: interfaces TypeScript para facturas y catálogos.
- `src/dom.ts`: utilidades tipadas para el DOM.
- `src/server.ts`: servidor local Express escrito en TypeScript.
- `backend-patches/`: archivos Java requeridos para completar los endpoints de integración.

## Endpoints del backend usados
- `GET /api/facturas?page=0&size=10` para el listado paginado (la página comienza en 0; valores predeterminados: página 0 y 10 registros), además de filtros por cliente, comprobante exacto o rango, fecha, estado e importe mínimo.
- `GET /api/facturas/{id}` para recuperar una factura al editar.
- `POST /api/facturas` para crear.
- `PUT /api/facturas/{id}` para actualizar.
- `DELETE /api/facturas/{id}` para anular lógicamente.
- `POST /api/facturas/{id}/reactivar` para volver a activar una factura anulada (estado `EMITIDA`); desde la edición también se puede elegir `EMITIDA`, `PENDIENTE` o `PAGADA`.
- `GET /api/catalogos` para clientes, condiciones de IVA, puntos de venta, monedas y artículos.
- `POST /api/clientes` para crear un cliente desde el formulario de factura. CUIT/CUIL y denominación son obligatorios; los datos de contacto y domicilio son opcionales. El cliente creado se selecciona en la factura sin perder el borrador.
- La cantidad de cada ítem de factura debe ser un entero mayor que cero; las bonificaciones pueden expresarse con decimales.
- `GET /api/facturas/pdf` y `GET /api/facturas/excel` para exportar informes.

Los controles de paginación se muestran debajo del listado. Las exportaciones incluyen todos los resultados que coinciden con los filtros, independientemente de la página seleccionada.
El filtro `sinCliente=true` devuelve facturas sin cliente asignado; también se aplica a las exportaciones.

## Integración
Los archivos de `backend-patches/` son referencia para los cambios de integración explicados en la versión anterior del proyecto. No reemplaces archivos del backend que ya hayas modificado y probado sin comparar primero el contenido. El frontend espera que el DTO del listado incluya `id`, `numeroFactura`, `fechaEmision`, `clienteDenominacion`, `condicionIva`, `puntoVentaDescripcion`, `importeTotal`, `cantidadItems` y `estado`.

El frontend calcula un total orientativo para ayudar al usuario; el importe definitivo lo calcula el backend usando los precios de la base de datos.
