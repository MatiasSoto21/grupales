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
- `GET /api/facturas` y filtros por fecha, estado e importe mínimo.
- `GET /api/facturas/{id}` para recuperar una factura al editar.
- `POST /api/facturas` para crear.
- `PUT /api/facturas/{id}` para actualizar.
- `DELETE /api/facturas/{id}` para anular lógicamente.
- `GET /api/catalogos` para clientes, condiciones de IVA, puntos de venta, monedas y artículos.
- `GET /api/facturas/pdf` y `GET /api/facturas/excel` para exportar informes.

## Integración
Los archivos de `backend-patches/` son referencia para los cambios de integración explicados en la versión anterior del proyecto. No reemplaces archivos del backend que ya hayas modificado y probado sin comparar primero el contenido. El frontend espera que el DTO del listado incluya `id`, `numeroFactura`, `fechaEmision`, `clienteDenominacion`, `condicionIva`, `puntoVentaDescripcion`, `importeTotal`, `cantidadItems` y `estado`.

El frontend calcula un total orientativo para ayudar al usuario; el importe definitivo lo calcula el backend usando los precios de la base de datos.
