import { test, expect, type Page } from '@playwright/test';

const catalog = {
  facturacion: { ambiente: 'HOMOLOGACION', fechaActual: '2026-11-14', fechaFiscalMinima: '2026-11-09', fechaFiscalMaxima: '2026-11-14' },
  clientes: [{ id: 1, denominacion: 'Cliente con IVA', condicionIvaId: 1 }, { id: 2, denominacion: 'Cliente sin IVA', condicionIvaId: null }],
  condicionesIva: [{ id: 1, denominacion: 'Consumidor Final', codigoAfip: 5 }],
  puntosVenta: [{ id: 1, numero: 1, descripcion: 'Central' }, { id: 2, numero: 2, descripcion: 'Sucursal' }],
  tiposMoneda: [{ id: 1, denominacion: 'Pesos', simbolo: '$' }],
  listasPrecio: [{ id: 1, codigo: 'LP01', denominacion: 'Minorista' }, { id: 2, codigo: 'LP02', denominacion: 'Mayorista' }],
  articulos: [{ id: 1, codigo: 'PROD01', denominacion: 'Producto', precios: [
    { id: 10, listaPrecioId: 1, listaPrecioDenominacion: 'Minorista', precioVenta: 1.005 },
    { id: 11, listaPrecioId: 2, listaPrecioDenominacion: 'Mayorista', precioVenta: 100 }
  ] }]
};
const invoice = { id: 1, numeroFactura: 10, fechaEmision: '2026-11-12', clienteDenominacion: 'Cliente',
  condicionIva: 'Consumidor Final', puntoVentaDescripcion: 'Central', importeTotal: 121, cantidadItems: 1,
  estado: 'EMITIDA', tieneCae: false, comprobanteDisponible: false, editable: true };

async function fixtures(page: Page) {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let json: unknown;
    if (url.pathname === '/api/catalogos') json = { ...catalog, clientes: [], articulos: [] };
    else if (url.pathname === '/api/articulos') {
      const listId = Number(url.searchParams.get('listaPrecioId'));
      const content = catalog.articulos.flatMap(article => article.precios.filter(price => price.listaPrecioId === listId).map(price => ({
        listaPrecioArticuloId: price.id, articuloId: article.id, codigo: article.codigo, denominacion: article.denominacion,
        listaPrecioId: price.listaPrecioId, listaPrecioDenominacion: price.listaPrecioDenominacion, precioVenta: price.precioVenta
      })));
      json = { content, number: 0, size: 10, totalPages: content.length ? 1 : 0, totalElements: content.length, first: true, last: true };
    }
    else if (url.pathname.startsWith('/api/articulos/precios/')) {
      const article = catalog.articulos[0];
      const price = article.precios.find(p => p.id === Number(url.pathname.split('/').pop()))!;
      json = { listaPrecioArticuloId: price.id, articuloId: article.id, codigo: article.codigo, denominacion: article.denominacion,
        listaPrecioId: price.listaPrecioId, listaPrecioDenominacion: price.listaPrecioDenominacion, precioVenta: price.precioVenta };
    }
    else if (url.pathname.endsWith('/siguiente-numero')) json = { numero: url.searchParams.get('puntoVentaId') === '2' ? 22 : 11 };
    else if (url.pathname === '/api/facturas' && route.request().method() === 'GET') {
      const number = Number(url.searchParams.get('page') ?? 0);
      json = { content: [invoice], number, size: 10, totalPages: 3, totalElements: 21, first: number === 0, last: number === 2 };
    } else if (url.pathname === '/api/facturas' && route.request().method() === 'POST') json = { ...invoice, estado: 'AUTORIZADA', editable: false };
    else if (url.pathname === '/api/facturas/1') json = { numero: 10, fechaEmision: '2026-11-12', clienteId: 1,
      condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', observaciones: 'Historica',
      detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] };
    else if (/^\/api\/clientes\/\d+$/.test(url.pathname)) json = { ...catalog.clientes.find(c => c.id === Number(url.pathname.split('/').pop())), cuitCuil: '20123456789', condicionIvaDenominacion: 'Consumidor Final' };
    else if (url.pathname === '/api/clientes' && route.request().method() === 'GET') json = {
      content: catalog.clientes.map(c => ({ ...c, cuitCuil: '20123456789', condicionIvaDenominacion: null })),
      number: 0, size: 10, totalPages: 1, totalElements: 2, first: true, last: true
    };
    else if (url.pathname === '/api/clientes') json = { id: 3, denominacion: 'Nuevo Cliente', condicionIvaId: 1 };
    else json = {};
    await route.fulfill({ json });
  });
}

async function openNew(page: Page) {
  await page.goto('/');
  await expect(page.locator('#facturas-tbody tr')).toHaveCount(1);
  await page.locator('#btn-nueva-factura').click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#numero')).toHaveValue('11');
  return frame;
}

async function addProduct(page: Page) {
  const frame = page.frameLocator('#factura-modal-frame');
  await frame.locator('#btn-agregar').click();
  await frame.locator('#producto-lista').selectOption('1');
  await frame.getByRole('button', { name: 'Seleccionar PROD01 Producto', exact: true }).click();
  await frame.locator('#producto-form button[type=submit]').click();
  return frame;
}

test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  (page as Page & { errors: string[] }).errors = errors;
  await fixtures(page);
});
test.afterEach(async ({ page }) => {
  expect((page as Page & { errors: string[] }).errors).toEqual([]);
});

test('listing loads, paginates and resets to page one when sorting', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('#stat-cantidad')).toHaveText('21');
  await page.getByRole('button', { name: '2', exact: true }).click();
  await expect(page.locator('#resultado-texto')).toContainText('2 de 3');
  await page.locator('#btn-abrir-orden').click();
  await page.locator('#sort-field').selectOption('total');
  await page.locator('#sort-direction').selectOption('asc');
  const listing = page.waitForRequest(r => r.url().includes('sortBy=total') && r.url().includes('page=0'));
  await page.locator('#sort-form button[type=submit]').click();
  await listing;
  await expect(page.locator('#resultado-texto')).toContainText('1 de 3');
});

test('new invoice starts empty, uses decimal totals and refreshes the ARCA number', async ({ page }) => {
  const frame = await openNew(page);
  await expect(frame.locator('#detalles-tbody select')).toHaveCount(0);
  await addProduct(page);
  await expect(frame.locator('#neto-total')).toContainText('1,01');
  await expect(frame.locator('#total-final')).toContainText('1,22');
  await frame.locator('#puntoVentaId').selectOption('2');
  await expect(frame.locator('#numero')).toHaveValue('22');
  await expect(frame.locator('#numero')).toHaveAttribute('readonly', '');
});

test('creating a client preserves the draft and selects the new client', async ({ page }) => {
  const frame = await openNew(page);
  await addProduct(page);
  await frame.locator('#observaciones').fill('Borrador a preservar');
  await frame.locator('#btn-mostrar-cliente').click();
  await frame.locator('#nuevo-cliente-cuit').fill('20-12345678-9');
  await frame.locator('#nuevo-cliente-denominacion').fill('Nuevo Cliente');
  await frame.locator('#nuevo-cliente-condicion-iva').selectOption('1');
  await frame.locator('#btn-guardar-cliente').click();
  await expect(frame.locator('#nuevo-cliente-panel')).not.toBeVisible();
  await expect(frame.locator('#clienteId')).toHaveValue('3');
  await expect(frame.locator('#observaciones')).toHaveValue('Borrador a preservar');
  await expect(frame.locator('#detalles-tbody .qty-input')).toHaveCount(1);
});

test('unregistered VAT is cleared when changing clients, and draft survives Escape', async ({ page }) => {
  const frame = await openNew(page);
  await frame.locator('#btn-buscar-cliente').click();
  await frame.getByRole('button', { name: 'Seleccionar Cliente con IVA', exact: true }).click();
  await expect(frame.locator('#condicionIvaId')).toBeDisabled();
  await frame.locator('#btn-buscar-cliente').click();
  await frame.getByRole('button', { name: 'Seleccionar Cliente sin IVA', exact: true }).click();
  await expect(frame.locator('#condicionIvaId')).toBeEnabled();
  await expect(frame.locator('#condicionIvaId')).toHaveValue('');
  await frame.locator('#observaciones').fill('Conservar');
  await frame.locator('#btn-mostrar-cliente').click();
  await frame.locator('#nuevo-cliente-email').fill('correo-invalido');
  await frame.locator('#nuevo-cliente-email').press('Escape');
  await expect(frame.locator('#btn-guardar')).toBeEnabled();
  await expect(frame.locator('#observaciones')).toHaveValue('Conservar');
});

test('saving sends the price-list ID, closes the modal and reloads the listing', async ({ page }) => {
  const frame = await openNew(page);
  await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  const save = page.waitForRequest(r => r.method() === 'POST' && new URL(r.url()).pathname === '/api/facturas');
  await frame.locator('#btn-guardar').click();
  const body = (await save).postDataJSON();
  expect(body.detalles[0].listaPrecioArticuloId).toBe(10);
  expect(body.detalles[0]).not.toHaveProperty('precioUnitario');
  await expect(page.locator('#factura-modal')).not.toBeVisible();
  await expect(page.locator('#facturas-tbody tr')).toHaveCount(1);
});

test('theme stays synchronized between listing, iframe and nested dialog', async ({ page }) => {
  const frame = await openNew(page);
  await page.locator('#btn-cerrar-factura-modal').click();
  await page.locator('.top-actions [data-theme-toggle]').click();
  const theme = await page.locator('html').getAttribute('data-theme');
  await page.locator('#btn-nueva-factura').click();
  await expect(frame.locator('html')).toHaveAttribute('data-theme', theme!);
  await frame.locator('#btn-mostrar-cliente').click();
  await frame.locator('#nuevo-cliente-panel [data-theme-toggle]').click();
  await expect(page.locator('html')).not.toHaveAttribute('data-theme', theme!);
});

test('Escape on confirmation leaves no stale listeners or accidental cancellation', async ({ page }) => {
  await page.goto('/');
  await page.locator('#facturas-tbody button').filter({ hasText: 'Anular' }).click();
  await page.locator('#confirm-modal-ok').press('Escape');
  await expect(page.locator('#confirm-modal')).not.toBeVisible();
  await page.locator('#facturas-tbody button').filter({ hasText: 'Anular' }).click();
  await page.locator('#confirm-modal-cancel').click();
  await expect(page.locator('#confirm-modal')).not.toBeVisible();
});

test('invalid edit ID disables saving without fetching an invalid invoice', async ({ page }) => {
  await page.goto('/factura-form.html?id=-1');
  await expect(page.locator('#alerta')).toContainText('identificador');
  await expect(page.locator('#btn-guardar')).toBeDisabled();
});

test('pending save keeps the invoice modal open until the response arrives', async ({ page }) => {
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/facturas', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    await waiting;
    await route.fulfill({ json: { ...invoice, estado: 'AUTORIZADA', editable: false } });
  });
  const frame = await openNew(page);
  await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  await frame.locator('#btn-guardar').click();
  await expect(page.locator('#btn-cerrar-factura-modal')).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.locator('#factura-modal')).toBeVisible();
  release();
  await expect(page.locator('#factura-modal')).not.toBeVisible();
});


test('cancellation and reactivation keep their confirmation visible after reloading', async ({ page }) => {
  let cancelled = false;
  await page.route('**/api/facturas**', async route => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'DELETE') { cancelled = true; return route.fulfill({ status: 204 }); }
    if (url.pathname.endsWith('/reactivar')) { cancelled = false; return route.fulfill({ status: 204 }); }
    if (url.pathname === '/api/facturas') return route.fulfill({ json: {
      content: [{ ...invoice, estado: cancelled ? 'ANULADA' : 'EMITIDA' }],
      number: 0, size: 10, totalPages: 1, totalElements: 1, first: true, last: true
    } });
    return route.fallback();
  });
  await page.goto('/');
  const row = page.locator('#facturas-tbody tr');
  await row.getByRole('button', { name: 'Anular', exact: true }).click();
  await page.locator('#confirm-modal-ok').click();
  await expect(row).toContainText('ANULADA');
  await expect(page.locator('#alerta')).toContainText('anulada correctamente');
  await row.getByRole('button', { name: 'Reactivar', exact: true }).click();
  await page.locator('#confirm-modal-ok').click();
  await expect(row).toContainText('EMITIDA');
  await expect(page.locator('#alerta')).toContainText('reactivada correctamente');
});


test('VAT changes refresh the authoritative ARCA number for the correct receipt class', async ({ page }) => {
  await page.route('**/api/catalogos?**', route => route.fulfill({ json: { ...catalog, clientes: [], articulos: [],
    condicionesIva: [...catalog.condicionesIva, { id: 2, denominacion: 'Responsable Inscripto', codigoAfip: 1 }]
  } }));
  const frame = await openNew(page);
  await frame.locator('#condicionIvaId').selectOption('2');
  await expect(frame.locator('#tipo-comprobante')).toHaveValue('Factura A');
  await frame.locator('#condicionIvaId').selectOption('1');
  await expect(frame.locator('#tipo-comprobante')).toHaveValue('Factura B');
  await expect(frame.locator('#modalidad-arca')).toHaveCount(0);
});

test('saved rejection reason remains available from the listing', async ({ page }) => {
  await page.route('**/api/facturas**', async route => {
    if (new URL(route.request().url()).pathname !== '/api/facturas') return route.fallback();
    return route.fulfill({ json: { content: [{ ...invoice, estado: 'RECHAZADA', numeroFactura: null,
      motivoRechazo: '10017: CUIT no registrado' }], number: 0, size: 10, totalPages: 1, totalElements: 1 } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Motivo ARCA' }).click();
  await expect(page.locator('#arca-result-modal')).toBeVisible();
  await expect(page.locator('#arca-result-message')).toHaveText('10017: CUIT no registrado');
  await page.locator('#arca-result-modal').getByRole('button', { name: 'Cerrar' }).click();
  await expect(page.locator('#arca-result-modal')).not.toBeVisible();
});

test('rejection keeps the ARCA reason and cannot resubmit a saved invoice', async ({ page }) => {
  const frame = await openNew(page);
  await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  let saves = 0;
  await page.route('**/api/facturas', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    saves++;
    return route.fulfill({ json: { ...invoice, numeroFactura: null, estado: 'RECHAZADA', tieneCae: false,
      receptorPrueba: false, motivoRechazo: 'CUIT emisor no registrado en padron' } });
  });
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('CUIT emisor no registrado en padron');
  await expect(frame.locator('#btn-guardar')).toBeDisabled();
  await expect(frame.locator('#btn-mostrar-cliente')).toBeDisabled();
  await expect(frame.locator('#btn-agregar')).toBeDisabled();
  await expect(frame.locator('#btn-guardar')).toBeDisabled();
  await frame.locator('#factura-form').dispatchEvent('submit');
  expect(saves).toBe(1);
  await frame.locator('#btn-cancelar-factura').click();
  await expect(page.locator('#factura-modal')).not.toBeVisible();
});


test('internal X uses local numbering, preserves the choice and is identified in the listing', async ({ page }) => {
  const frame = await openNew(page);
  await page.route('**/api/facturas/siguiente-numero**', async route => {
    const url = new URL(route.request().url());
    expect(url.searchParams.get('comprobanteInterno')).toBe('true');
    return route.fulfill({ json: { numero: 35 } });
  });
  await frame.locator('#clase-documento').selectOption('X');
  await expect(frame.locator('#tipo-comprobante')).toHaveValue('Comprobante X');
  await expect(frame.locator('#numero')).toHaveValue('35');
  await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  await page.route('**/api/facturas**', async route => {
    if (new URL(route.request().url()).pathname !== '/api/facturas') return route.fallback();
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON().comprobanteInterno).toBe(true);
      return route.fulfill({ json: { ...invoice, numeroFactura: 35, comprobanteInterno: true } });
    }
    return route.fulfill({ json: { content: [{ ...invoice, numeroFactura: 35, comprobanteInterno: true }],
      number: 0, size: 10, totalPages: 1, totalElements: 1 } });
  });
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('Comprobante X guardado');
  await expect(page.locator('#factura-modal')).not.toBeVisible();
  await expect(page.locator('#facturas-tbody')).toContainText('X-35');
});


test('catalog owns status filters and unused payment states are absent', async ({ page }) => {
  const states = ['EMITIDA', 'AUTORIZADA', 'RECHAZADA', 'ERROR_AFIP', 'ANULADA'];
  await page.route('**/api/catalogos?**', route => route.fulfill({ json: { ...catalog, clientes: [], articulos: [], estadosFactura: states } }));
  await page.goto('/');
  await expect(page.locator('#estado option')).toHaveText(['Todos los estados', ...states]);
  await expect(page.locator('#estado option[value=PAGADA], #estado option[value=PENDIENTE]')).toHaveCount(0);
});

for (const state of ['AUTORIZADA', 'ERROR_AFIP', 'ANULADA']) {
  test(`${state} cannot be edited through a stale listing or a direct form URL`, async ({ page }) => {
    await page.route('**/api/facturas**', async route => {
      const pathname = new URL(route.request().url()).pathname;
      if (pathname === '/api/facturas') return route.fulfill({ json: { content: [{ ...invoice, estado: state, editable: true }],
        number: 0, totalPages: 1, totalElements: 1 } });
      if (pathname === '/api/facturas/1') return route.fulfill({ json: { estado: state, editable: false } });
      return route.fallback();
    });
    await page.goto('/');
    await expect(page.locator('#facturas-tbody')).toContainText(state);
    await expect(page.getByRole('button', { name: 'Editar', exact: true })).toHaveCount(0);
    await page.goto('/factura-form.html?id=1');
    await expect(page.locator('#btn-guardar')).toBeDisabled();
    await expect(page.locator('#alerta')).toContainText('no permite edicion');
    await expect(page.locator('#estado-reactivacion')).toHaveCount(0);
  });
}

test('reactivating an unnumbered rejection preserves its rejection and never promises emission', async ({ page }) => {
  let cancelled = false;
  await page.route('**/api/facturas**', async route => {
    const pathname = new URL(route.request().url()).pathname;
    if (route.request().method() === 'DELETE') { cancelled = true; return route.fulfill({ status: 204 }); }
    if (pathname.endsWith('/reactivar')) { cancelled = false; return route.fulfill({ status: 204 }); }
    if (pathname === '/api/facturas') return route.fulfill({ json: { content: [{ ...invoice, numeroFactura: null,
      estado: cancelled ? 'ANULADA' : 'RECHAZADA', motivoRechazo: '10015: DocNro invalido' }],
      number: 0, totalPages: 1, totalElements: 1 } });
    return route.fallback();
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Anular', exact: true }).click();
  await expect(page.locator('#confirm-modal-message')).toContainText('intento #1');
  await page.locator('#confirm-modal-ok').click();
  await expect(page.locator('#facturas-tbody')).toContainText('ANULADA');
  await expect(page.getByRole('button', { name: 'Editar', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Reactivar', exact: true }).click();
  await expect(page.locator('#confirm-modal-message')).not.toContainText('EMITIDA');
  await page.locator('#confirm-modal-ok').click();
  await expect(page.locator('#facturas-tbody')).toContainText('RECHAZADA');
  await expect(page.locator('#facturas-tbody')).not.toContainText('EMITIDA');
});


test('customer names and ARCA errors are rendered as text rather than executable HTML', async ({ page }) => {
  const payload = '<img src=x onerror="window.injected=true">';
  await page.route('**/api/facturas**', async route => {
    if (new URL(route.request().url()).pathname !== '/api/facturas') return route.fallback();
    return route.fulfill({ json: { content: [{ ...invoice, clienteDenominacion: payload,
      estado: 'RECHAZADA', motivoRechazo: payload }], number: 0, totalPages: 1, totalElements: 1 } });
  });
  await page.goto('/');
  await expect(page.locator('#facturas-tbody')).toContainText(payload);
  await expect(page.locator('#facturas-tbody img')).toHaveCount(0);
  await page.getByRole('button', { name: 'Motivo ARCA' }).click();
  await expect(page.locator('#arca-result-message')).toHaveText(payload);
  expect(await page.evaluate(() => (window as Window & { injected?: boolean }).injected)).toBeUndefined();
});

test('editing sends the loaded version and preserves a draft when the API detects a stale form', async ({ page }) => {
  await page.route('**/api/facturas/1', async route => {
    if (route.request().method() === 'PUT') {
      expect(route.request().postDataJSON().version).toBe(4);
      return route.fulfill({ status: 409, json: { detail: 'El comprobante cambio. Recarga el formulario.' } });
    }
    return route.fulfill({ json: { numero: 10, fechaEmision: '2026-11-12', clienteId: 1,
      condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', version: 4,
      observaciones: 'Version original', detalles: [{ articuloId: 1, listaPrecioArticuloId: 11,
        descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] } });
  });
  page.on('console', message => {
    if (message.type() === 'error' && message.text().includes('409')) {
      const errors = (page as Page & { errors: string[] }).errors;
      const index = errors.indexOf(message.text()); if (index >= 0) errors.splice(index, 1);
    }
  });
  await page.goto('/factura-form.html?id=1');
  await page.locator('#observaciones').fill('Mi borrador');
  await page.locator('#btn-guardar').click();
  await expect(page.locator('#alerta')).toContainText('Recarga el formulario');
  await expect(page.locator('#observaciones')).toHaveValue('Mi borrador');
});


test('mobile client modal uses one column and the page contains its horizontal scrolling', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const frame = await openNew(page);
  await frame.locator('#btn-mostrar-cliente').click();
  const columns = await frame.locator('#nuevo-cliente-form').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length);
  expect(columns).toBe(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/mobile-client.png', fullPage: true });
});

test('saving locks invoice contents and restores the previous control states after failure', async ({ page }) => {
  page.on('console', message => {
    if (message.type() === 'error' && message.text().includes('400')) {
      const errors = (page as Page & { errors: string[] }).errors;
      const index = errors.indexOf(message.text()); if (index >= 0) errors.splice(index, 1);
    }
  });
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/facturas', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    await waiting;
    return route.fulfill({ status: 400, json: { detail: 'Error de validacion controlado' } });
  });
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#btn-buscar-cliente').click();
  await frame.getByRole('button', { name: 'Seleccionar Cliente con IVA', exact: true }).click();
  await frame.locator('#tipoMonedaId').selectOption('1');
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#btn-buscar-cliente')).toBeDisabled();
  await expect(frame.locator('#btn-agregar')).toBeDisabled();
  await expect(frame.locator('#btn-mostrar-cliente')).toBeDisabled();
  release();
  await expect(frame.locator('#alerta')).toContainText('Error de validacion controlado');
  await expect(frame.locator('#btn-buscar-cliente')).toBeEnabled();
  await expect(frame.locator('#condicionIvaId')).toBeDisabled();
  await expect(frame.locator('#btn-agregar')).toBeEnabled();
});


test('a delayed number preview cannot replace the result of an already saved invoice', async ({ page }) => {
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/facturas/siguiente-numero**', async route => {
    await waiting; await route.fulfill({ json: { numero: 999 } });
  });
  await frame.locator('#puntoVentaId').selectOption('2');
  await expect(frame.locator('#numero')).toHaveValue(/Consultando/);
  await page.route('**/api/facturas', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    return route.fulfill({ json: { ...invoice, estado: 'RECHAZADA', numeroFactura: null, motivoRechazo: 'Rechazo controlado' } });
  });
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#estado-badge')).toHaveText('RECHAZADA');
  const preview = page.waitForResponse(response => response.url().includes('/siguiente-numero'));
  release(); await (await preview).finished();
  await frame.locator('body').evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await expect(frame.locator('#numero')).toHaveValue('');
});

for (const theme of ['light', 'dark']) {
  test(`modal theme controls remain visible in ${theme} mode on mobile`, async ({ page }) => {
    await page.addInitScript(value => localStorage.setItem('facturacion-theme', value), theme);
    await page.setViewportSize({ width: 390, height: 844 });
    const frame = await openNew(page);
    await frame.locator('#btn-mostrar-cliente').click();
    for (const button of [page.locator('#factura-modal [data-theme-toggle]'), frame.locator('#nuevo-cliente-panel [data-theme-toggle]'), frame.locator('#btn-guardar-cliente')]) {
      const contrast = await button.evaluate(element => {
        const style = getComputedStyle(element);
        const luminance = (color: string) => {
          const channels = color.match(/[0-9.]+/g)!.slice(0, 3).map(value => {
            const c = Number(value) / 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
          });
          return channels[0]! * 0.2126 + channels[1]! * 0.7152 + channels[2]! * 0.0722;
        };
        const foreground = luminance(style.color), background = luminance(style.backgroundColor);
        return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05);
      });
      expect(contrast).toBeGreaterThanOrEqual(await button.getAttribute('id') === 'btn-guardar-cliente' ? 4.5 : 3);
    }
    await frame.locator('#btn-guardar-cliente').scrollIntoViewIfNeeded();
    await expect(frame.locator('#btn-guardar-cliente')).toBeInViewport();
    await frame.locator('#nuevo-cliente-cuit').scrollIntoViewIfNeeded();
    await page.screenshot({ path: `test-results/mobile-client-${theme}.png`, fullPage: true });
  });
}


test('client explorer sends combined filters and ordering, paginates and preserves draft on cancel', async ({ page }) => {
  const calls: URL[] = [];
  await page.route('**/api/clientes?**', async route => {
    const url = new URL(route.request().url()); calls.push(url);
    const number = Number(url.searchParams.get('page'));
    await route.fulfill({ json: { content: [{ id: number + 1, denominacion: number ? 'Segunda pagina' : 'Cliente resultado', cuitCuil: '20123456789', condicionIvaId: 1, condicionIvaDenominacion: 'Consumidor Final' }],
      number, size: 10, totalElements: 12, totalPages: 2, first: number === 0, last: number === 1 } });
  });
  const frame = await openNew(page);
  await addProduct(page);
  await frame.locator('#observaciones').fill('Conservar borrador');
  await frame.locator('#btn-buscar-cliente').click();
  await expect(frame.locator('#cliente-explorer-status')).toContainText('12 clientes');
  await frame.locator('#cliente-buscar-nombre').fill('Cliente');
  await frame.locator('#cliente-buscar-cuit').fill('20-12345678-9');
  await frame.locator('#cliente-coincidencia').selectOption('exacta');
  await frame.locator('#cliente-iva-filtro').selectOption('1');
  await frame.locator('#cliente-orden').selectOption('cuitCuil');
  await frame.locator('#cliente-direccion').selectOption('desc');
  await frame.locator('#cliente-explorer-form button[type=submit]').click();
  await expect.poll(() => calls.at(-1)?.searchParams.get('sortDir')).toBe('desc');
  const filters = calls.at(-1)!.searchParams;
  expect(Object.fromEntries(filters)).toMatchObject({ denominacion: 'Cliente', cuitCuil: '20-12345678-9', coincidencia: 'exacta', condicionIvaId: '1', sortBy: 'cuitCuil', sortDir: 'desc', page: '0', size: '10' });
  await frame.locator('#cliente-siguiente').click();
  await expect(frame.locator('#cliente-pagina')).toHaveText('Página 2 de 2');
  await expect(frame.locator('#cliente-siguiente')).toBeDisabled();
  await frame.locator('#cliente-explorer-cancel').click();
  await expect(frame.locator('#clienteId')).toHaveValue('');
  await expect(frame.locator('#observaciones')).toHaveValue('Conservar borrador');
  await expect(frame.locator('#detalles-tbody .qty-input')).toHaveCount(1);
  await frame.locator('#btn-buscar-cliente').click();
  await frame.locator('#cliente-filtros-limpiar').click();
  await expect(frame.locator('#cliente-pagina')).toHaveText('Página 1 de 2');
  await expect(frame.locator('#cliente-coincidencia')).toHaveValue('parcial');
  await frame.getByRole('button', { name: 'Seleccionar Cliente resultado', exact: true }).click();
  await expect(frame.locator('#clienteId')).toHaveValue('1');
  await expect(frame.locator('#cliente-nombre')).toHaveValue('Cliente resultado');
  await expect(frame.locator('#condicionIvaId')).toBeDisabled();
  await frame.locator('#btn-quitar-cliente').click();
  await expect(frame.locator('#clienteId')).toHaveValue('');
  await expect(frame.locator('#condicionIvaId')).toBeEnabled();
});

test('client explorer selection filters invoices and exports, clearing restores all clients', async ({ page }) => {
  await page.goto('/');
  await page.locator('#btn-buscar-cliente').click();
  await page.getByRole('button', { name: 'Seleccionar Cliente con IVA', exact: true }).click();
  const listing = page.waitForRequest(r => new URL(r.url()).pathname === '/api/facturas' && new URL(r.url()).searchParams.get('clienteId') === '1');
  await page.locator('#filtros-form button[type=submit]').click(); await listing;
  for (const path of ['pdf', 'excel']) {
    await page.route(`**/api/facturas/${path}?**`, route => route.fulfill({ status: 204 }));
    const exported = page.waitForRequest(r => new URL(r.url()).pathname === `/api/facturas/${path}`);
    await page.locator(`a[href='/api/facturas/${path}']`).click();
    expect(new URL((await exported).url()).searchParams.get('clienteId')).toBe('1');
  }
  await page.locator('#btn-limpiar').click();
  await expect(page.locator('#clienteId')).toHaveValue('');
  await expect(page.locator('#cliente-nombre')).toHaveValue('Todos los clientes');
  await page.locator('#btn-sin-cliente').click();
  const anonymous = page.waitForRequest(r => new URL(r.url()).searchParams.get('sinCliente') === 'true');
  await page.locator('#filtros-form button[type=submit]').click(); await anonymous;
});

test('client explorer ignores a late search and shows empty results', async ({ page }) => {
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/clientes?**', async route => {
    const old = new URL(route.request().url()).searchParams.get('denominacion') === 'vieja';
    if (old) await waiting;
    await route.fulfill({ json: { content: old ? [{ id: 1, denominacion: 'Resultado viejo', cuitCuil: '20123456789' }] : [], number: 0, size: 10, totalPages: old ? 1 : 0, totalElements: old ? 1 : 0, first: true, last: true } });
  });
  await page.goto('/'); await page.locator('#btn-buscar-cliente').click();
  await page.locator('#cliente-buscar-nombre').fill('vieja');
  const sent = page.waitForRequest(r => new URL(r.url()).searchParams.get('denominacion') === 'vieja');
  await page.locator('#cliente-explorer-form button[type=submit]').click(); await sent;
  await page.locator('#cliente-buscar-nombre').fill('sin resultados');
  await page.locator('#cliente-explorer-form button[type=submit]').click();
  await expect(page.locator('#cliente-explorer-status')).toHaveText('No se encontraron clientes.');
  const received = page.waitForResponse(r => new URL(r.url()).searchParams.get('denominacion') === 'vieja'); release(); await received;
  await expect(page.locator('#cliente-resultados tr')).toHaveCount(0);
  await expect(page.locator('#cliente-anterior')).toBeDisabled();
  await expect(page.locator('#cliente-siguiente')).toBeDisabled();
});


test('mobile client explorer contains scrolling and supports keyboard dismissal without changing selection', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const frame = await openNew(page);
  await frame.locator('#btn-buscar-cliente').click();
  await expect(frame.locator('#cliente-resultados tr')).toHaveCount(2);
  const columns = await frame.locator('#cliente-explorer-form').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length);
  expect(columns).toBe(1);
  expect(await frame.locator('body').evaluate(element => element.scrollWidth <= window.innerWidth)).toBe(true);
  await frame.locator('#cliente-buscar-nombre').press('Escape');
  await expect(frame.locator('#cliente-explorer')).not.toBeVisible();
  await expect(frame.locator('#clienteId')).toHaveValue('');
  await expect(frame.locator('#btn-guardar')).toBeEnabled();
});

test('client explorer presents API errors and can search again', async ({ page }) => {
  page.on('console', message => {
    if (message.type() === 'error' && message.text().includes('400')) {
      const errors = (page as Page & { errors: string[] }).errors;
      const index = errors.indexOf(message.text()); if (index >= 0) errors.splice(index, 1);
    }
  });
  let failed = false;
  await page.route('**/api/clientes?**', route => {
    if (!failed) { failed = true; return route.fulfill({ status: 400, json: { detail: 'Filtro invalido' } }); }
    return route.fulfill({ json: { content: [], number: 0, totalElements: 0, totalPages: 0, first: true, last: true } });
  });
  await page.goto('/'); await page.locator('#btn-buscar-cliente').click();
  await expect(page.locator('#cliente-explorer-status')).toContainText('Filtro invalido');
  await expect(page.locator('#cliente-siguiente')).toBeDisabled();
  await page.locator('#cliente-filtros-limpiar').click();
  await expect(page.locator('#cliente-explorer-status')).toHaveText('No se encontraron clientes.');
});


test('article explorer defaults to wholesale, clears selection on list changes and saves the exact price ID', async ({ page }) => {
  const frame = await openNew(page);
  await frame.locator('#btn-agregar').click();
  await expect(frame.locator('#producto-lista')).toHaveValue('2');
  await expect(frame.locator('#producto-resultados tr')).toHaveCount(1);
  await expect(frame.locator('#producto-articulo')).toHaveJSProperty('tagName', 'INPUT');
  await frame.getByRole('button', { name: 'Seleccionar PROD01 Producto', exact: true }).click();
  await expect(frame.locator('#producto-precio')).toHaveValue('11');
  await expect(frame.locator('#producto-subtotal')).toContainText('100,00');
  await frame.locator('#producto-lista').selectOption('1');
  await expect(frame.locator('#producto-articulo')).toHaveValue('');
  await expect(frame.locator('#btn-confirmar-producto')).toBeDisabled();
  await frame.locator('#producto-lista').selectOption('2');
  await frame.getByRole('button', { name: 'Seleccionar PROD01 Producto', exact: true }).click();
  await frame.locator('#producto-cantidad').fill('2');
  await frame.locator('#producto-bonificacion').fill('10');
  await frame.locator('#btn-confirmar-producto').click();
  await expect(frame.locator('#detalles-tbody select')).toHaveCount(0);
  await expect(frame.locator('#detalles-tbody')).toContainText('Mayorista');
  await expect(frame.locator('#neto-total')).toContainText('180,00');
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  const saved = page.waitForRequest(r => new URL(r.url()).pathname === '/api/facturas' && r.method() === 'POST');
  await frame.locator('#btn-guardar').click();
  expect((await saved).postDataJSON().detalles[0]).toMatchObject({ articuloId: 1, listaPrecioArticuloId: 11, cantidad: 2, porcentajeBonificacion: 10 });
});

test('article explorer searches server pages with exact and partial filters and changes a line through the explorer', async ({ page }) => {
  const calls: URL[] = [];
  await page.route('**/api/articulos?**', route => {
    const url = new URL(route.request().url()); calls.push(url);
    const number = Number(url.searchParams.get('page'));
    return route.fulfill({ json: { content: [{ listaPrecioArticuloId: 20 + number, articuloId: 10 + number, codigo: `ART${number}`, denominacion: `Producto ${number}`, listaPrecioId: 2, listaPrecioDenominacion: 'Mayorista', precioVenta: 200 + number }],
      number, size: 10, totalPages: 100, totalElements: 1000, first: number === 0, last: number === 99 } });
  });
  const frame = await openNew(page);
  await frame.locator('#observaciones').fill('Borrador de mil productos');
  await frame.locator('#btn-agregar').click();
  await frame.locator('#producto-buscar-codigo').fill('ART');
  await frame.locator('#producto-buscar-nombre').fill('Producto');
  await frame.locator('#producto-coincidencia').selectOption('exacta');
  await frame.locator('#producto-orden').selectOption('precioVenta');
  await expect(frame.locator('#producto-direccion')).toHaveCount(0);
  await frame.locator('#articulo-explorer-form button[type=submit]').click();
  await expect(frame.locator('#producto-resultados-status')).toContainText('1000');
  await expect.poll(() => calls.at(-1)?.searchParams.get('sortDir')).toBe('asc');
  expect(Object.fromEntries(calls.at(-1)!.searchParams)).toMatchObject({ listaPrecioId: '2', codigo: 'ART', denominacion: 'Producto', coincidencia: 'exacta', sortBy: 'precioVenta', sortDir: 'asc', size: '10', page: '0' });
  await frame.locator('#producto-siguiente').click();
  await expect(frame.locator('#producto-pagina')).toHaveText('Página 2 de 100');
  await frame.getByRole('button', { name: 'Seleccionar ART1 Producto 1', exact: true }).click();
  await frame.locator('#btn-confirmar-producto').click();
  await frame.locator('#detalles-tbody').getByRole('button', { name: 'Cambiar' }).click();
  await expect(frame.locator('#producto-seleccionado')).toHaveValue(/Producto 1/);
  await frame.getByRole('button', { name: 'Seleccionar ART0 Producto 0', exact: true }).click();
  await frame.locator('#btn-confirmar-producto').click();
  await expect(frame.locator('#detalles-tbody .qty-input')).toHaveCount(1);
  await expect(frame.locator('#detalles-tbody')).toContainText('Producto 0');
  await expect(frame.locator('#observaciones')).toHaveValue('Borrador de mil productos');
});

test('article explorer ignores late responses from a previous price list', async ({ page }) => {
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/articulos?**', async route => {
    const listId = Number(new URL(route.request().url()).searchParams.get('listaPrecioId'));
    if (listId === 2) await waiting;
    return route.fulfill({ json: { content: [{ listaPrecioArticuloId: listId, articuloId: 1, codigo: `LISTA${listId}`, denominacion: 'Producto', listaPrecioId: listId, listaPrecioDenominacion: String(listId), precioVenta: listId }],
      number: 0, size: 10, totalPages: 1, totalElements: 1, first: true, last: true } });
  });
  const frame = await openNew(page);
  const sent = page.waitForRequest(r => new URL(r.url()).pathname === '/api/articulos' && new URL(r.url()).searchParams.get('listaPrecioId') === '2');
  await frame.locator('#btn-agregar').click(); await sent;
  await frame.locator('#producto-lista').selectOption('1');
  await expect(frame.locator('#producto-resultados')).toContainText('LISTA1');
  const received = page.waitForResponse(r => new URL(r.url()).pathname === '/api/articulos' && new URL(r.url()).searchParams.get('listaPrecioId') === '2');
  release(); await received;
  await expect(frame.locator('#producto-resultados')).toContainText('LISTA1');
  await expect(frame.locator('#producto-resultados')).not.toContainText('LISTA2');
});

test('mobile article explorer preserves draft on Escape and shows an empty list', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/articulos?**', route => route.fulfill({ json: { content: [], number: 0, size: 10, totalPages: 0, totalElements: 0, first: true, last: true } }));
  const frame = await openNew(page);
  await frame.locator('#observaciones').fill('Conservar');
  await frame.locator('#btn-agregar').click();
  await expect(frame.locator('#producto-resultados-status')).toContainText('No se encontraron');
  await expect(frame.locator('#producto-siguiente')).toBeDisabled();
  await expect(frame.locator('#btn-confirmar-producto')).toBeDisabled();
  expect(await frame.locator('#articulo-explorer-form').evaluate(element => getComputedStyle(element).gridTemplateColumns.split(' ').length)).toBe(1);
  expect(await frame.locator('body').evaluate(element => element.scrollWidth <= window.innerWidth)).toBe(true);
  await frame.locator('#producto-lista').press('Escape');
  await expect(frame.locator('#producto-modal')).not.toBeVisible();
  await expect(frame.locator('#observaciones')).toHaveValue('Conservar');
  await expect(frame.locator('#detalles-tbody .qty-input')).toHaveCount(0);
});


test('opening the listing and explorers fetches only the requested page with no background collection download', async ({ page }) => {
  const requests: URL[] = [];
  page.on('request', request => {
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/')) requests.push(url);
  });
  await page.goto('/');
  await expect(page.locator('#facturas-tbody tr')).toHaveCount(1);
  expect(requests.filter(url => url.pathname === '/api/facturas')).toHaveLength(1);
  expect(requests.filter(url => url.pathname === '/api/clientes' || url.pathname === '/api/articulos')).toHaveLength(0);
  const catalogRequest = requests.find(url => url.pathname === '/api/catalogos')!;
  expect(catalogRequest.searchParams.get('incluirClientes')).toBe('false');
  expect(catalogRequest.searchParams.get('incluirArticulos')).toBe('false');
  await page.locator('#btn-buscar-cliente').click();
  await expect(page.locator('#cliente-resultados tr')).toHaveCount(2);
  const clients = requests.filter(url => url.pathname === '/api/clientes');
  expect(clients).toHaveLength(1);
  expect(clients[0].searchParams.get('page')).toBe('0'); expect(clients[0].searchParams.get('size')).toBe('10');
  await page.locator('#cliente-explorer-cancel').click();
  await page.locator('#btn-nueva-factura').click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#numero')).toHaveValue('11');
  await frame.locator('#btn-agregar').click();
  await expect(frame.locator('#producto-resultados tr')).toHaveCount(1);
  const products = requests.filter(url => url.pathname === '/api/articulos');
  expect(products).toHaveLength(1);
  expect(products[0].searchParams.get('page')).toBe('0'); expect(products[0].searchParams.get('size')).toBe('10');
  expect(products[0].searchParams.get('listaPrecioId')).toBe('2');
});


test('X preview has no fiscal VAT and changing to fiscal restores the tax calculation', async ({ page }) => {
  const frame = await openNew(page); await addProduct(page);
  await expect(frame.locator('#total-final')).toContainText('1,22');
  await frame.locator('#clase-documento').selectOption('X');
  await expect(frame.locator('#total-final')).toContainText('1,01');
  await expect(frame.locator('#iva-row')).not.toBeVisible();
  await expect(frame.locator('#neto-row')).not.toBeVisible();
  await frame.locator('#clase-documento').selectOption('FISCAL');
  await frame.locator('#condicionIvaId').selectOption('1');
  await expect(frame.locator('#total-final')).toContainText('1,22');
  await expect(frame.locator('#iva-label')).toHaveText('IVA contenido (21%)');
});

for (const source of ['X', 'RECHAZADA']) {
  test(`${source} editing can explicitly create a new linked fiscal invoice with a new number and selected date`, async ({ page }) => {
    const operations: string[] = [];
    const oldDate = '2026-11-12';
    await page.route('**/api/facturas/1', route => {
      operations.push(route.request().method());
      return route.fulfill({ json: { numero: source === 'X' ? 35 : null, fechaEmision: oldDate, clienteId: 1,
        condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: source === 'X' ? 'EMITIDA' : 'RECHAZADA',
        comprobanteInterno: source === 'X', puedeEmitirFiscal: true, editable: true, version: 7,
        observaciones: 'Borrador original', detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] } });
    });
    await page.route('**/api/facturas/1/emitir-fiscal', route => {
      operations.push(route.request().method() + ' fiscal');
      const body = route.request().postDataJSON();
      expect(body.version).toBe(7); expect(body.comprobanteInterno).toBe(false); expect(body.numero).toBeNull();
      expect(body.fechaEmision).toBe(oldDate); expect(body.fechaOperacion).toBe(oldDate); expect(body.observaciones).toBe('Correcciones para ARCA');
      return route.fulfill({ status: 201, json: { ...invoice, id: 20, numeroFactura: 200, estado: 'AUTORIZADA',
        fechaEmision: oldDate, editable: false, tieneCae: true, origenComprobanteId: 1 } });
    });
    await page.goto('/'); await page.getByRole('button', { name: 'Editar', exact: true }).click();
    const frame = page.frameLocator('#factura-modal-frame');
    await expect(frame.locator('#fechaEmision')).toHaveValue(oldDate);
    await expect(frame.locator('#btn-emitir-fiscal')).toBeVisible();
    await frame.locator('#observaciones').fill('Correcciones para ARCA');
    await frame.locator('#btn-emitir-fiscal').click();
    await expect(frame.locator('#fechaEmision')).toHaveValue(oldDate);
    await expect(frame.locator('#fechaEmision')).not.toHaveAttribute('readonly', '');
    await expect(frame.locator('#numero')).toHaveValue('11');
    await expect(frame.locator('#clase-documento')).toHaveValue('FISCAL');
    await expect(frame.locator('#clase-documento')).toBeDisabled();
    await expect(frame.locator('#total-final')).toContainText('121,00');
    await expect(frame.locator('#origen-comprobante-link')).toContainText(oldDate);
    await frame.locator('#btn-guardar').click();
    await expect(frame.locator('#alerta')).toContainText('Factura autorizada');
    expect(operations).toEqual(['GET', 'POST fiscal']);
    await expect(frame.locator('#numero')).toHaveValue('200');
  });
}

test('a new uncertain fiscal attempt from X is retained read-only and never resubmitted automatically', async ({ page }) => {
  let emissions = 0;
  await page.route('**/api/facturas/1', route => route.fulfill({ json: { numero: 35, fechaEmision: '2026-11-12', clienteId: 1,
    condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', comprobanteInterno: true,
    puedeEmitirFiscal: true, editable: true, version: 2, detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] } }));
  await page.route('**/api/facturas/1/emitir-fiscal', route => {
    emissions++;
    return route.fulfill({ status: 201, json: { ...invoice, id: 20, estado: 'ERROR_AFIP', numeroFactura: null, motivoRechazo: 'Resultado sin confirmar', origenComprobanteId: 1 } });
  });
  await page.goto('/'); await page.getByRole('button', { name: 'Editar', exact: true }).click();
  const frame = page.frameLocator('#factura-modal-frame');
  await frame.locator('#btn-emitir-fiscal').click();
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('sin confirmacion');
  await expect(frame.locator('#btn-guardar')).toBeDisabled();
  await expect(frame.locator('#numero')).toHaveValue('');
  expect(emissions).toBe(1);
});

test('an editable X has one Ver and one Editar action without linked variants', async ({ page }) => {
  await page.route('**/api/facturas?**', route => route.fulfill({ json: {
    content: [{ ...invoice, comprobanteInterno: true, comprobanteFiscalDerivadoId: 20, editable: true }],
    number: 0, size: 10, totalPages: 1, totalElements: 1
  } }));
  await page.goto('/');
  const row = page.locator('#facturas-tbody tr');
  await expect(row.getByRole('link', { name: 'Ver', exact: true })).toHaveAttribute('href', '/api/facturas/1/comprobante.pdf');
  await expect(row.getByRole('link')).toHaveCount(1);
  await expect(row.getByRole('button', { name: 'Editar', exact: true })).toBeVisible();
});

test('Thursday X can be invoiced with Thursday or Saturday dates using the backend calendar', async ({ page }) => {
  let emission: any;
  await page.route('**/api/facturas/1', route => route.fulfill({ json: { numero: 35, fechaEmision: '2026-11-12', clienteId: 1,
    condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', comprobanteInterno: true,
    puedeEmitirFiscal: true, editable: true, version: 2, detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] } }));
  await page.route('**/api/facturas/1/emitir-fiscal', route => {
    emission = route.request().postDataJSON();
    return route.fulfill({ status: 201, json: { ...invoice, id: 20, estado: 'AUTORIZADA', fechaEmision: emission.fechaEmision, origenComprobanteId: 1 } });
  });
  await page.goto('/'); await page.getByRole('button', { name: 'Editar', exact: true }).click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#fechaEmision')).toHaveValue('2026-11-12');
  await expect(frame.locator('#fechaEmision')).toHaveAttribute('readonly', '');
  await frame.locator('#btn-emitir-fiscal').click();
  await expect(frame.locator('#fechaEmision')).toHaveValue('2026-11-12');
  await expect(frame.locator('#fechaOperacion')).toHaveValue('2026-11-12');
  await expect(frame.locator('#fechaEmision')).toHaveAttribute('min', '2026-11-09');
  await expect(frame.locator('#fechaEmision')).toHaveAttribute('max', '2026-11-14');
  await frame.locator('#fechaOperacion').fill('2026-11-14');
  await frame.locator('#fechaOperacion').press('Tab');
  await expect(frame.locator('#fechaEmision')).toHaveValue('2026-11-14');
  await frame.locator('#btn-guardar').click();
  await expect.poll(() => emission?.fechaEmision).toBe('2026-11-14');
  expect(emission.fechaOperacion).toBe('2026-11-14');
  await expect(frame.locator('#origen-comprobante-link')).toContainText('2026-11-12');
});

test('an out-of-window fiscal date is not silently replaced or submitted', async ({ page }) => {
  let emissions = 0;
  await page.route('**/api/facturas/1', route => route.fulfill({ json: { numero: 35, fechaEmision: '2026-10-01', clienteId: 1,
    condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', comprobanteInterno: true,
    puedeEmitirFiscal: true, editable: true, version: 2, detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] } }));
  page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/emitir-fiscal')) emissions++; });
  await page.goto('/'); await page.getByRole('button', { name: 'Editar', exact: true }).click();
  const frame = page.frameLocator('#factura-modal-frame');
  await frame.locator('#btn-emitir-fiscal').click();
  await expect(frame.locator('#fechaEmision')).toHaveValue('2026-10-01');
  await frame.locator('#btn-guardar').click();
  expect(await frame.locator('#fechaEmision').evaluate((input: HTMLInputElement) => input.validity.rangeUnderflow)).toBe(true);
  await expect(frame.locator('#fechaEmision')).toHaveValue('2026-10-01');
  await expect(frame.locator('#btn-guardar')).toBeEnabled();
  expect(emissions).toBe(0);
});


test('an authorized fiscal row only views its own receipt and cannot edit the source indirectly', async ({ page }) => {
  await page.route('**/api/facturas?**', route => route.fulfill({ json: {
    content: [{ ...invoice, id: 20, estado: 'AUTORIZADA', editable: false, tieneCae: true,
      origenInternoId: 1, origenInternoEditable: true }], number: 0, size: 10, totalPages: 1, totalElements: 1
  } }));
  await page.goto('/');
  const row = page.locator('#facturas-tbody tr');
  await expect(row.getByRole('link', { name: 'Ver', exact: true })).toHaveAttribute('href', '/api/facturas/20/comprobante.pdf');
  await expect(row.getByRole('link')).toHaveCount(1);
  await expect(row.getByRole('button', { name: /Editar/ })).toHaveCount(0);
});

test('only the branch filter remains and is sent to the backend with page reset', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: '2', exact: true }).click();
  await page.locator('#puntoVentaId').selectOption('2');
  await expect(page.locator('#montoMinimo, #claseDocumento, #incluirVinculados')).toHaveCount(0);
  const listing = page.waitForRequest(r => {
    const p = new URL(r.url()).searchParams;
    return r.url().includes('/api/facturas?') && p.get('puntoVentaId') === '2' && !p.has('claseDocumento') && !p.has('incluirVinculados') && !p.has('montoMinimo') && p.get('page') === '0';
  });
  await page.getByRole('button', { name: 'Buscar facturas', exact: true }).click();
  await listing;
});


test('invoice references stay compact and show the branch for repeated numbers', async ({ page }) => {
  await page.route('**/api/facturas?**', route => route.fulfill({ json: {
    content: [{ ...invoice, numeroFactura: 1, referenciaListado: '1' },
      { ...invoice, id: 2, comprobanteInterno: true, numeroFactura: 1, referenciaListado: 'X-1 (Central)' },
      { ...invoice, id: 3, comprobanteInterno: true, numeroFactura: 1, referenciaListado: 'X-1 (Sucursal)' }],
    number: 0, size: 10, totalPages: 1, totalElements: 3
  } }));
  await page.goto('/');
  await expect(page.locator('.document-reference strong')).toHaveText(['1', 'X-1 (Central)', 'X-1 (Sucursal)']);
});


for (const width of [1024, 1366]) {
  test(`all invoice columns and actions fit the listing at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.route('**/api/facturas?**', route => route.fulfill({ json: {
      content: [{ ...invoice, clienteDenominacion: 'Cliente con una denominacion comercial muy extensa',
        condicionIva: 'IVA Responsable Inscripto', puntoVentaDescripcion: 'Sucursal comercial principal' }],
      number: 0, size: 10, totalPages: 1, totalElements: 1
    } }));
    await page.goto('/');
    await expect(page.locator('#facturas-tbody tr')).toHaveCount(1);
    const fits = await page.locator('#invoice-table').evaluate(table => {
      const wrap = table.parentElement!;
      return wrap.scrollWidth <= wrap.clientWidth + 1;
    });
    expect(fits).toBe(true);
    const headerLines = await page.locator('#invoice-table th').first().evaluate(header => {
      const range = document.createRange(); range.selectNodeContents(header);
      return range.getClientRects().length;
    });
    expect(headerLines).toBe(1);
    const actions = await page.locator('#facturas-tbody td:last-child').boundingBox();
    expect(actions!.x + actions!.width).toBeLessThanOrEqual(width);
    await expect(page.getByRole('button', { name: 'Editar', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Anular', exact: true })).toBeVisible();
  });
}


test('authorized A/B replaces X in the listing and neither record can be edited', async ({ page }) => {
  let authorized = false;
  const source = { numero: 35, fechaEmision: '2026-11-12', clienteId: 1, condicionIvaId: 1,
    tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', comprobanteInterno: true, version: 2,
    detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] };
  await page.route('**/api/facturas?**', route => route.fulfill({ json: {
    content: authorized ? [{ ...invoice, id: 20, estado: 'AUTORIZADA', editable: false, tieneCae: true, origenInternoId: 1 }]
      : [{ ...invoice, comprobanteInterno: true, numeroFactura: 35 }],
    number: 0, size: 10, totalPages: 1, totalElements: 1
  } }));
  await page.route('**/api/facturas/1', route => route.fulfill({ json: {
    ...source, editable: !authorized, puedeEmitirFiscal: !authorized, comprobanteFiscalDerivadoId: authorized ? 20 : null
  } }));
  await page.route('**/api/facturas/1/emitir-fiscal', route => {
    authorized = true;
    return route.fulfill({ status: 201, json: { ...invoice, id: 20, estado: 'AUTORIZADA', editable: false,
      tieneCae: true, origenComprobanteId: 1 } });
  });
  await page.goto('/'); await page.getByRole('button', { name: 'Editar', exact: true }).click();
  const frame = page.frameLocator('#factura-modal-frame');
  await frame.locator('#btn-emitir-fiscal').click();
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('antecedente');
  await expect(page.locator('#factura-modal')).not.toBeVisible();
  await expect(page.locator('#facturas-tbody tr')).toHaveCount(1);
  await expect(page.locator('#facturas-tbody').getByRole('link', { name: 'Ver', exact: true }))
    .toHaveAttribute('href', '/api/facturas/20/comprobante.pdf');
  await expect(page.locator('#facturas-tbody').getByRole('button', { name: 'Editar', exact: true })).toHaveCount(0);
  await page.goto('/factura-form.html?id=1');
  await expect(page.locator('#alerta')).toContainText('no permite edicion');
  await expect(page.locator('#btn-guardar')).toBeDisabled();
});


test('compact product codes and names fit the explorer on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.route('**/api/articulos?**', route => route.fulfill({ json: {
    content: [{ listaPrecioArticuloId: 11, articuloId: 715, codigo: '715', denominacion: 'Levite Limonada 1.5 L',
      listaPrecioId: 2, listaPrecioDenominacion: 'Mayorista', precioVenta: 1200 }],
    number: 0, size: 10, totalPages: 1, totalElements: 1, first: true, last: true
  } }));
  const frame = await openNew(page); await frame.locator('#btn-agregar').click();
  await expect(frame.locator('#producto-resultados tr')).toHaveCount(1);
  await expect(frame.locator('#producto-resultados td').nth(0)).toHaveText('715');
  await expect(frame.locator('#producto-resultados td').nth(1)).toHaveText('Levite Limonada 1.5 L');
  expect(await frame.locator('.article-results').evaluate(table => table.parentElement!.scrollWidth <= table.parentElement!.clientWidth + 1)).toBe(true);
  await frame.getByRole('button', { name: 'Seleccionar 715 Levite Limonada 1.5 L', exact: true }).click();
  await expect(frame.locator('#producto-seleccionado')).toHaveValue('715 · Levite Limonada 1.5 L');
});


test('product explorer defaults to consecutive numeric codes and continues on the next page', async ({ page }) => {
  const requests: URL[] = [];
  await page.route('**/api/articulos?**', route => {
    const url = new URL(route.request().url()); requests.push(url);
    const number = Number(url.searchParams.get('page'));
    return route.fulfill({ json: {
      content: Array.from({ length: 10 }, (_, i) => ({ listaPrecioArticuloId: 50 + number * 10 + i,
        articuloId: 9000 + number * 10 + i, codigo: String(number * 10 + i + 1), denominacion: 'Levite Limonada 1.5 L',
        listaPrecioId: 2, listaPrecioDenominacion: 'Mayorista', precioVenta: 1200 })),
      number, size: 10, totalPages: 101, totalElements: 1001, first: number === 0, last: false
    } });
  });
  const frame = await openNew(page); await frame.locator('#btn-agregar').click();
  await expect(frame.locator('#producto-orden')).toHaveValue('codigo');
  await expect(frame.locator('#producto-resultados tr td:first-child')).toHaveText(Array.from({ length: 10 }, (_, i) => String(i + 1)));
  await frame.locator('#producto-siguiente').click();
  await expect(frame.locator('#producto-resultados tr td:first-child')).toHaveText(Array.from({ length: 10 }, (_, i) => String(i + 11)));
  expect(requests.every(url => url.searchParams.get('sortBy') === 'codigo' && url.searchParams.get('sortDir') === 'asc')).toBe(true);
});


function allowHttpErrors(page: Page, ...statuses: number[]): void {
  page.on('console', message => {
    if (message.type() !== 'error' || !statuses.some(status => message.text().includes(String(status)))) return;
    const errors = (page as Page & { errors: string[] }).errors;
    const index = errors.indexOf(message.text());
    if (index >= 0) errors.splice(index, 1);
  });
}

test('saving from the bottom reveals feedback and keeps the success on the listing', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 650 });
  const frame = await openNew(page);
  await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('autorizada');
  await expect(frame.locator('#alerta')).toBeInViewport();
  await expect(frame.locator('#alerta')).toBeFocused();
  await page.screenshot({ path: test.info().outputPath('mobile-save-feedback.png') });
  await expect(page.locator('#factura-modal')).not.toBeVisible();
  await expect(page.locator('#alerta')).toContainText('autorizada');
  await expect(page.locator('#alerta')).toBeInViewport();
});

test('save failure reveals the error, preserves the draft and is not overwritten by a failed number preview', async ({ page }) => {
  allowHttpErrors(page, 409, 502);
  await page.setViewportSize({ width: 390, height: 650 });
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  await frame.locator('#observaciones').fill('No perder este borrador');
  await page.route('**/api/facturas', route => route.request().method() === 'POST'
    ? route.fulfill({ status: 409, json: { message: 'El registro cambió. Revisá los datos.' } }) : route.fallback());
  await page.route('**/api/facturas/siguiente-numero?**', route => route.fulfill({ status: 502, json: { message: 'Numeración no disponible' } }));
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('El registro cambió');
  await expect(frame.locator('#alerta')).toBeInViewport();
  await expect(frame.locator('#alerta')).toBeFocused();
  await expect(frame.locator('#numero')).toHaveValue('');
  await expect(frame.locator('#alerta')).not.toContainText('Numeración no disponible');
  await expect(frame.locator('#observaciones')).toHaveValue('No perder este borrador');
  await expect(frame.locator('#detalles-tbody tr')).toHaveCount(1);
  await expect(frame.locator('#btn-guardar')).toBeEnabled();
});

test('native required validation shows an accessible error at the top without submitting', async ({ page }) => {
  let writes = 0;
  page.on('request', request => { if (request.method() === 'POST') writes++; });
  await page.setViewportSize({ width: 390, height: 650 });
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('Moneda');
  await expect(frame.locator('#alerta')).toBeInViewport();
  await expect(frame.locator('#alerta')).toHaveAttribute('role', 'alert');
  expect(writes).toBe(0);
});

test('a rejected new invoice scrolls to its ARCA reason and stays available to close', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 650 });
  await page.route('**/api/facturas', route => route.request().method() === 'POST'
    ? route.fulfill({ json: { ...invoice, numeroFactura: null, estado: 'RECHAZADA', motivoRechazo: 'CUIT no registrado en homologación' } }) : route.fallback());
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1'); await frame.locator('#tipoMonedaId').selectOption('1');
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('CUIT no registrado');
  await expect(frame.locator('#alerta')).toBeInViewport();
  await expect(frame.locator('#btn-guardar')).toBeDisabled();
  await expect(frame.locator('#btn-cancelar-factura')).toHaveText('Cerrar');
  await expect(page.locator('#btn-cerrar-factura-modal')).toBeEnabled();
});

test('creating a client locks every field while pending and reveals a duplicate error inside its dialog', async ({ page }) => {
  allowHttpErrors(page, 409);
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/clientes', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    await waiting;
    await route.fulfill({ status: 409, json: { message: 'Ya existe un cliente con ese CUIT/CUIL.' } });
  });
  await page.setViewportSize({ width: 390, height: 650 });
  const frame = await openNew(page);
  await frame.locator('#btn-mostrar-cliente').click();
  await frame.locator('#nuevo-cliente-cuit').fill('20123456789');
  await frame.locator('#nuevo-cliente-denominacion').fill('Cliente duplicado');
  await frame.locator('#nuevo-cliente-condicion-iva').selectOption('1');
  await frame.locator('#btn-guardar-cliente').click();
  await expect(frame.locator('#nuevo-cliente-cuit')).toBeDisabled();
  await expect(frame.locator('#btn-cerrar-cliente-x')).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(frame.locator('#nuevo-cliente-panel')).toBeVisible();
  release();
  await expect(frame.locator('[data-dialog-alert]')).toContainText('Ya existe');
  await expect(frame.locator('[data-dialog-alert]')).toBeInViewport();
  await expect(frame.locator('#nuevo-cliente-cuit')).toBeEnabled();
  await expect(frame.locator('#nuevo-cliente-denominacion')).toHaveValue('Cliente duplicado');
  await expect(frame.locator('#btn-cerrar-cliente-x')).toBeEnabled();
});

test('X to A/B enables fiscal fields, distinguishes locked selectors and restores them after a failed emission', async ({ page }) => {
  allowHttpErrors(page, 400);
  await page.route('**/api/facturas/1', route => route.fulfill({ json: { numero: 35, fechaEmision: '2026-11-12', clienteId: 1,
    condicionIvaId: 1, tipoMonedaId: 1, puntoVentaId: 1, estado: 'EMITIDA', comprobanteInterno: true,
    puedeEmitirFiscal: true, editable: true, version: 2, detalles: [{ articuloId: 1, listaPrecioArticuloId: 11, descripcion: 'Producto', cantidad: 1, porcentajeBonificacion: 0 }] } }));
  await page.route('**/api/facturas/1/emitir-fiscal', route => route.fulfill({ status: 400, json: { message: 'Corregí la fecha fiscal.' } }));
  await page.goto('/'); await page.getByRole('button', { name: 'Editar', exact: true }).click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#puntoVentaId')).toBeDisabled();
  await expect(frame.locator('#puntoVentaId')).toHaveCSS('cursor', 'not-allowed');
  await expect(frame.locator('#puntoVentaId')).toHaveCSS('appearance', 'none');
  await page.screenshot({ path: test.info().outputPath('x-locked-selectors.png') });
  await frame.locator('#btn-emitir-fiscal').click();
  await expect(frame.locator('#puntoVentaId')).toBeEnabled();
  await frame.locator('#puntoVentaId').selectOption('2');
  await expect(frame.locator('#numero')).toHaveValue('22');
  await expect(frame.locator('#clase-documento')).toBeDisabled();
  await expect(frame.locator('#condicionIvaId')).toBeDisabled();
  await frame.locator('#fechaEmision').fill('2026-11-14');
  await frame.locator('#fechaOperacion').fill('2026-11-13');
  await frame.locator('#fechaOperacion').press('Tab');
  await expect(frame.locator('#fechaEmision')).toHaveValue('2026-11-14');
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('Corregí la fecha fiscal');
  await expect(frame.locator('#alerta')).toBeInViewport();
  await expect(frame.locator('#puntoVentaId')).toBeEnabled();
  await expect(frame.locator('#puntoVentaId')).toHaveValue('2');
  await expect(frame.locator('#clase-documento')).toBeDisabled();
  await expect(frame.locator('#condicionIvaId')).toBeDisabled();
  await expect(frame.locator('#btn-guardar')).toBeEnabled();
});

test('empty invoice results show page zero and recover when filters are cleared', async ({ page }) => {
  await page.route('**/api/facturas?**', route => {
    const url = new URL(route.request().url());
    if (!url.searchParams.has('numero')) return route.fallback();
    return route.fulfill({ json: { content: [], number: 0, size: 10, totalPages: 0, totalElements: 0, first: true, last: true } });
  });
  await page.goto('/');
  await page.locator('#numero').fill('999999');
  await page.getByRole('button', { name: 'Buscar facturas', exact: true }).click();
  await expect(page.locator('#resultado-texto')).toContainText('página 0 de 0');
  await expect(page.locator('#stat-cantidad')).toHaveText('0');
  await expect(page.locator('#facturas-tbody')).toContainText('No hay facturas');
  await page.locator('#btn-limpiar').click();
  await expect(page.locator('#resultado-texto')).toContainText('21 resultados');
});

test('a failed form initialization locks actions but still allows closing', async ({ page }) => {
  allowHttpErrors(page, 502);
  await page.goto('/');
  await page.route('**/api/catalogos?**', route => route.fulfill({ status: 502, json: { message: 'Servicio no disponible' } }));
  await page.locator('#btn-nueva-factura').click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#alerta')).toContainText('No se pudo preparar');
  await expect(frame.locator('#btn-guardar')).toBeDisabled();
  await expect(frame.locator('#btn-agregar')).toBeDisabled();
  await expect(frame.locator('#btn-mostrar-cliente')).toBeDisabled();
  await frame.locator('#btn-cancelar-factura').click();
  await expect(page.locator('#factura-modal')).not.toBeVisible();
});

test('pending cancellation blocks repeated actions and confirms the result at the top', async ({ page }) => {
  let calls = 0; let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/facturas/1', async route => {
    if (route.request().method() !== 'DELETE') return route.fallback();
    calls++; await waiting; await route.fulfill({ status: 204 });
  });
  await page.goto('/');
  const cancel = page.getByRole('button', { name: 'Anular', exact: true });
  await cancel.click();
  await expect(page.locator('#confirm-modal-cancel')).toBeFocused();
  await page.locator('#confirm-modal-ok').click();
  await expect(cancel).toBeDisabled();
  await expect(page.locator('#btn-nueva-factura')).toBeDisabled();
  expect(calls).toBe(1);
  release();
  await expect(page.locator('#alerta')).toContainText('anulada correctamente');
  await expect(page.locator('#alerta')).toBeInViewport();
  await expect(page.locator('#btn-nueva-factura')).toBeEnabled();
});


test('standalone invoice success survives navigation back to the listing', async ({ page }) => {
  await page.goto('/factura-form.html');
  await expect(page.locator('#numero')).toHaveValue('11');
  await page.locator('#btn-agregar').click();
  await page.locator('#producto-resultados button').first().click();
  await page.locator('#btn-confirmar-producto').click();
  await page.locator('#condicionIvaId').selectOption('1');
  await page.locator('#tipoMonedaId').selectOption('1');
  await page.locator('#btn-guardar').click();
  await expect(page).toHaveURL('/');
  await expect(page.locator('#alerta')).toContainText('autorizada');
  await expect(page.locator('#alerta')).toBeInViewport();
  await page.reload();
  await expect(page.locator('#facturas-tbody tr')).toHaveCount(1);
  await expect(page.locator('#alerta')).not.toBeVisible();
});

test('invalid quantity cannot submit and its visible error keeps all draft lines', async ({ page }) => {
  let writes = 0;
  page.on('request', request => { if (request.method() === 'POST') writes++; });
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  await frame.locator('.qty-input').fill('1.5');
  await frame.locator('#btn-guardar').click();
  await expect(frame.locator('#alerta')).toContainText('Cantidad');
  await expect(frame.locator('#alerta')).toContainText('entero');
  await expect(frame.locator('#alerta')).toBeInViewport();
  await expect(frame.locator('.qty-input')).toHaveValue('1.5');
  expect(writes).toBe(0);
});

test('saving succeeds even when refreshing the listing fails and the user sees both outcomes', async ({ page }) => {
  allowHttpErrors(page, 502);
  const frame = await openNew(page); await addProduct(page);
  await frame.locator('#condicionIvaId').selectOption('1');
  await frame.locator('#tipoMonedaId').selectOption('1');
  await page.route('**/api/facturas?**', route => route.fulfill({ status: 502, json: { message: 'Listado temporalmente no disponible' } }));
  await frame.locator('#btn-guardar').click();
  await expect(page.locator('#factura-modal')).not.toBeVisible();
  await expect(page.locator('#alerta')).toContainText('autorizada');
  await expect(page.locator('#alerta')).toContainText('No se pudo actualizar el listado');
  await expect(page.locator('#alerta')).toBeInViewport();
});


test('slow form loading disables actions, permits closing and restores editable fields after loading', async ({ page }) => {
  let release!: () => void;
  const waiting = new Promise<void>(resolve => { release = resolve; });
  await page.goto('/');
  await page.route('**/api/catalogos?**', async route => { await waiting; await route.fallback(); });
  await page.locator('#btn-nueva-factura').click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#btn-agregar')).toBeDisabled();
  await expect(frame.locator('#btn-buscar-cliente')).toBeDisabled();
  await expect(frame.locator('#btn-guardar')).toBeDisabled();
  await expect(frame.locator('#clase-documento')).toBeDisabled();
  await expect(frame.locator('#btn-cancelar-factura')).toBeEnabled();
  release();
  await expect(frame.locator('#numero')).toHaveValue('11');
  await expect(frame.locator('#btn-agregar')).toBeEnabled();
  await expect(frame.locator('#btn-buscar-cliente')).toBeEnabled();
  await expect(frame.locator('#btn-guardar')).toBeEnabled();
  await expect(frame.locator('#clase-documento')).toBeEnabled();
  await expect(frame.locator('#condicionIvaId')).toBeEnabled();
  await expect(frame.locator('#puntoVentaId')).toBeEnabled();
});
