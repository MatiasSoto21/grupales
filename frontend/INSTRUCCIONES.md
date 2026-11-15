# Frontend de facturacion

## Ejecutar

Desde frontend, instalar con npm ci y ejecutar npm start. Abrir http://localhost:3000. npm run dev permite desarrollo con recompilacion.

El backend se configura mediante BACKEND_URL y por defecto escucha en http://localhost:8080. Express sirve public y dist y reenvia /api al backend. El proyecto utiliza Java 21 y PostgreSQL; ver ../README.md para configurar la instalacion completa y las credenciales locales.

## Recorrido de usuario

- El listado usa paginas de diez registros con filtros/orden en el backend y exportaciones con los mismos parametros.
- Nueva factura y Editar abren factura-form.html en un modal; clientes y productos se seleccionan desde exploradores nativos paginados.
- El mismo formulario permite X interno o A/B fiscal; emitir desde un guardado abre un borrador separado, nunca cambia el origen en el mismo registro.
- Guardar muestra y enfoca el aviso arriba. El exito permanece al volver al listado; los errores mantienen el borrador.
- Las cargas y guardados bloquean acciones prematuras. Los desplegables bloqueados se distinguen visualmente; al emitir desde X se habilitan fechas y punto de venta.
- Cantidades enteras positivas y bonificaciones decimales; el backend valida precios y recalcula importes.
- Tema claro/oscuro compartido entre listado, formulario y modales.

## Verificar

    npm test
    npx playwright install chromium
    npm run test:e2e -- invoices.spec.ts

Ultima validacion: 7 pruebas de logica/API/proxy y 64 escenarios de navegador aprobados el 14/11/2026. Los guardados del navegador se simulan mediante respuestas interceptadas.

Los recorridos contra un backend real son opt-in; RUN_WRITING_TESTS=1 puede crear registros y solicitar autorizaciones de homologacion. No habilitarlo para una comprobacion ordinaria. Ver ../FUNCIONAMIENTO-ARCA.md.

No se versionan node_modules, dist, reportes Playwright ni evidencia local. npm start compila TypeScript antes de servir la aplicacion.
