import { test, expect, type Page, type APIRequestContext } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

test.skip(process.env.RUN_WRITING_TESTS !== '1', 'Opt-in: crea registros y puede emitir comprobantes de homologacion');
test.setTimeout(180_000);

test('complete real user workflow with persisted invoices and homologation authorization', async ({ page, request }, testInfo) => {
  const directory = path.resolve('test-results/full-workflow');
  await fs.mkdir(directory, { recursive: true });
  const resume = process.env.WORKFLOW_RESUME_REPORT ? JSON.parse(await fs.readFile(process.env.WORKFLOW_RESUME_REPORT, 'utf8')) : null;
  const report: any = { ...(resume ?? {}), invoices: resume?.invoices ?? [], checks: resume?.checks ?? [], resumed: Boolean(resume), startedAt: new Date().toISOString(), client: resume?.client ?? null, consoleErrors: [], pageErrors: [], failedRequests: [] };
  page.on('pageerror', error => report.pageErrors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') report.consoleErrors.push(message.text()); });
  page.on('requestfailed', req => { if (!req.failure()?.errorText.includes('ERR_ABORTED')) report.failedRequests.push({ url: req.url(), reason: req.failure()?.errorText }); });
  const check = (description: string, details: unknown = true) => report.checks.push({ description, details });
  const persist = async () => fs.writeFile(path.join(directory, 'result.json'), JSON.stringify(report, null, 2));
  try {
    const catalogResponse = await request.get('/api/catalogos');
    expect(catalogResponse.status()).toBe(200);
    const catalog = await catalogResponse.json();
    const tax = catalog.condicionesIva.find((t: any) => t.codigoAfip === 1);
    const consumerTax = catalog.condicionesIva.find((t: any) => t.codigoAfip === 5);
    const currency = catalog.tiposMoneda.find((m: any) => m.denominacion.toLowerCase().includes('pesos'));
    const point = catalog.puntosVenta.find((v: any) => v.numero === 1);
    expect(tax && consumerTax && currency && point).toBeTruthy();
    const minorista = catalog.listasPrecio.find((l: any) => l.codigo === 'LP01');
    const mayorista = catalog.listasPrecio.find((l: any) => l.codigo === 'LP02');
    const firstPage = await request.get(`/api/articulos?listaPrecioId=${minorista.id}&page=0&size=1&sortBy=codigo&sortDir=asc`);
    const secondPage = await request.get(`/api/articulos?listaPrecioId=${mayorista.id}&page=1&size=1&sortBy=codigo&sortDir=asc`);
    expect(firstPage.status()).toBe(200); expect(secondPage.status()).toBe(200);
    const selectedProducts = [(await firstPage.json()).content[0], (await secondPage.json()).content[0]];
    const price1 = { id: selectedProducts[0].listaPrecioArticuloId, precioVenta: selectedProducts[0].precioVenta };
    const price2 = { id: selectedProducts[1].listaPrecioArticuloId, precioVenta: selectedProducts[1].precioVenta };
    const lines = selectedProducts.map((product: any, i: number) => ({
      articuloId: product.articuloId, listaPrecioArticuloId: product.listaPrecioArticuloId,
      cantidad: i === 0 ? 2 : 1, porcentajeBonificacion: i === 0 ? 12.5 : 0, precio: product.precioVenta
    }));
    const expectedTotal = lines.reduce((sum, line) => {
      const net = Math.round(line.precio * line.cantidad * (1 - line.porcentajeBonificacion / 100) * 100) / 100;
      return sum + net + Math.round(net * 21) / 100;
    }, 0);
    const marker = resume?.client.marker ?? `PRUEBA E2E ${new Date().toISOString().replace(/[:.]/g, '-')}`;
    // A synthetic, checksum-valid CUIT identifies test data and is never represented as a real customer.
    let base = '20' + String(Date.now()).slice(-8);
    const weights = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
    let digit = 11 - [...base].reduce((sum, char, i) => sum + Number(char) * weights[i]!, 0) % 11;
    if (digit === 11) digit = 0;
    while (digit === 10) {
      base = '20' + String((Number(base.slice(2)) + 1) % 100_000_000).padStart(8, '0');
      digit = 11 - [...base].reduce((sum, char, i) => sum + Number(char) * weights[i]!, 0) % 11;
      if (digit === 11) digit = 0;
    }
    const cuit = resume?.client.cuit ?? ('00' + base.slice(2) + '9');

    let saved: any;
    if (resume) {
      saved = resume.invoices.find((invoice: any) => invoice.estado === 'AUTORIZADA');
      if (!saved) throw new Error('No hay factura autorizada para retomar.');
      delete report.error;
      await page.goto('/');
      await expect(page.locator('#api-status')).toContainText('Backend conectado');
    } else {
    await page.goto('/');
    await expect(page.locator('#api-status')).toContainText('Backend conectado');
    await page.screenshot({ path: path.join(directory, '01-listado.png'), fullPage: true });
    await page.locator('#btn-nueva-factura').click();
    const frame = page.frameLocator('#factura-modal-frame');
    await expect(frame.locator('#numero')).toHaveValue(/^[0-9]+$/, { timeout: 60_000 });
    await frame.locator('#puntoVentaId').selectOption(String(point.id));
    await frame.locator('#tipoMonedaId').selectOption(String(currency.id));
    await frame.locator('#observaciones').fill(marker);
    for (const line of lines) {
      await frame.locator('#btn-agregar').click();
      const article = selectedProducts.find((p: any) => p.listaPrecioArticuloId === line.listaPrecioArticuloId)!;
      const price = article;
      await frame.locator('#producto-lista').selectOption(String(price.listaPrecioId));
      await frame.locator('#producto-buscar-codigo').fill(String(article.codigo));
      await frame.locator('#producto-coincidencia').selectOption('exacta');
      await frame.getByRole('button', { name: `Seleccionar ${article.codigo} ${article.denominacion}`, exact: true }).click();
      await frame.locator('#producto-cantidad').fill(String(line.cantidad));
      await frame.locator('#producto-bonificacion').fill(String(line.porcentajeBonificacion));
      await frame.locator('#producto-form button[type=submit]').click();
    }
    await frame.locator('#btn-mostrar-cliente').click();
    await frame.locator('#nuevo-cliente-cuit').fill(cuit);
    await frame.locator('#nuevo-cliente-denominacion').fill(marker);
    await frame.locator('#nuevo-cliente-condicion-iva').selectOption(String(tax.id));
    await frame.locator('#nuevo-cliente-email').fill('prueba-e2e@example.invalid');
    await frame.locator('#nuevo-cliente-calle').fill('Domicilio de prueba E2E');
    await frame.locator('#nuevo-cliente-numero-calle').fill('123');
    const clientResponse = page.waitForResponse(r => new URL(r.url()).pathname === '/api/clientes' && r.request().method() === 'POST');
    await frame.locator('#btn-guardar-cliente').click();
    const client = await clientResponse;
    expect(client.status()).toBe(201);
    report.client = { ...(await client.json()), marker, cuit };
    await persist();
    await expect(frame.locator('#clienteId')).toHaveValue(String(report.client.id));
    await expect(frame.locator('#condicionIvaId')).toBeDisabled();
    await expect(frame.locator('#condicionIvaId')).toHaveValue(String(tax.id));
    await expect(frame.locator('#detalles-tbody .qty-input')).toHaveCount(2);
    await expect(frame.locator('#observaciones')).toHaveValue(marker);
    const uiTotal = await frame.locator('#total-final').textContent();
    expect(uiTotal).toBe(expectedTotal.toLocaleString('es-AR', { style: 'currency', currency: 'ARS' }));
    check('Alta real de cliente conserva borrador, productos y condicion IVA', { clientId: report.client.id, expectedTotal, uiTotal });
    await page.screenshot({ path: path.join(directory, '02-borrador.png'), fullPage: true });

    async function saveInvoice() {
      const responsePromise = page.waitForResponse(r => new URL(r.url()).pathname === '/api/facturas' && r.request().method() === 'POST', { timeout: 100_000 });
      await frame.locator('#btn-guardar').click();
      const response = await responsePromise;
      expect(response.status()).toBe(201);
      const saved = await response.json();
      report.invoices.push({ ...saved, expectedTotal, expectedLines: lines, marker });
      await persist();
      expect(saved.importeTotal).toBeCloseTo(expectedTotal, 2);
      return saved;
    }
    saved = await saveInvoice();
    expect(saved.estado, 'El CUIT ficticio declarado debe ser rechazado por ARCA').toBe('RECHAZADA');
    expect(saved.tieneCae).toBe(false);
    expect(saved.numeroFactura).toBeNull();
    if (saved.estado === 'RECHAZADA') {
      check('Rechazo del receptor sintetico se informa sin mostrar autorizacion exitosa', { invoiceId: saved.id });
      await expect(frame.locator('#alerta')).toContainText('sin autorizacion de ARCA');
      await frame.locator('#btn-cancelar-factura').click();
      await expect(page.locator('#factura-modal')).not.toBeVisible();
      // A separate consumer-final purchase tests authorization without inventing registry identities.
      await page.locator('#btn-nueva-factura').click();
      await expect(frame.locator('#numero')).toHaveValue(/^[0-9]+$/, { timeout: 60_000 });
      await frame.locator('#condicionIvaId').selectOption(String(consumerTax.id));
      await frame.locator('#tipoMonedaId').selectOption(String(currency.id));
      await frame.locator('#observaciones').fill(marker + ' consumidor final');
      for (const line of lines) {
        await frame.locator('#btn-agregar').click();
        const article = selectedProducts.find((p: any) => p.listaPrecioArticuloId === line.listaPrecioArticuloId)!;
      const price = article;
        await frame.locator('#producto-lista').selectOption(String(price.listaPrecioId));
        await frame.locator('#producto-buscar-codigo').fill(String(article.codigo));
        await frame.locator('#producto-coincidencia').selectOption('exacta');
        await frame.getByRole('button', { name: `Seleccionar ${article.codigo} ${article.denominacion}`, exact: true }).click();
        await frame.locator('#producto-cantidad').fill(String(line.cantidad));
        await frame.locator('#producto-bonificacion').fill(String(line.porcentajeBonificacion));
        await frame.locator('#producto-form button[type=submit]').click();
      }
      saved = await saveInvoice();
    }
    }
    expect(saved.estado, 'La compra de consumidor final debe autorizarse en homologacion').toBe('AUTORIZADA');
    expect(saved.tieneCae).toBe(true);
    expect(saved.receptorPrueba).toBe(false);
    if (process.env.REQUIRE_FISCAL_PDF === '1') expect(saved.comprobanteDisponible).toBe(true);
    expect(saved.editable).toBe(false);
    await expect(page.locator('#factura-modal')).not.toBeVisible({ timeout: 10_000 });
    check('Guardado real y autorizacion ARCA de homologacion', { id: saved.id, numero: saved.numeroFactura, total: saved.importeTotal, comprobanteDisponible: saved.comprobanteDisponible });

    const detailResponse = await request.get(`/api/facturas/${saved.id}`);
    expect(detailResponse.status()).toBe(200);
    const detail = await detailResponse.json();
    expect(detail.numero).toBe(saved.numeroFactura);
    expect(detail.detalles).toHaveLength(2);
    for (const [i, line] of lines.entries()) {
      expect(detail.detalles[i].listaPrecioArticuloId).toBe(line.listaPrecioArticuloId);
      expect(detail.detalles[i].cantidad).toBe(line.cantidad);
      expect(detail.detalles[i].porcentajeBonificacion).toBe(line.porcentajeBonificacion);
    }
    const editRejected = await request.put(`/api/facturas/${saved.id}`, { data: { ...detail, observaciones: 'NO DEBE GUARDARSE' } });
    expect(editRejected.status()).toBe(409);
    check('API conserva detalles y rechaza edicion de factura autorizada con HTTP 409');
    const countBeforeInvalid = (await (await request.get('/api/facturas?size=1')).json()).totalElements;
    for (const invalid of [
      { ...detail, detalles: [{ ...detail.detalles[0], cantidad: 1.5 }] },
      { ...detail, detalles: [{ ...detail.detalles[0], listaPrecioArticuloId: price2.id }] }
    ]) {
      expect((await request.post('/api/facturas', { data: invalid })).status()).toBe(400);
    }
    expect((await (await request.get('/api/facturas?size=1')).json()).totalElements).toBe(countBeforeInvalid);
    expect((await request.get('/api/facturas/9007199254740991')).status()).toBe(404);
    for (const extension of ['', '/pdf', '/excel']) {
      expect((await request.get(`/api/facturas${extension}?numeroDesde=20&numeroHasta=10`)).status()).toBe(400);
    }
    check('Backend rechaza cantidades fraccionarias, precio ajeno, filtros invalidos y factura inexistente sin crear registros');

    await page.locator('#numero').fill(String(saved.numeroFactura));
    await page.locator('#filtros-form button[type=submit]').click();
    const row = page.locator('#facturas-tbody tr').filter({ hasText: String(saved.numeroFactura) });
    await expect(row).toHaveCount(1);
    await expect(row.getByRole('button', { name: 'Editar', exact: true })).toHaveCount(0);
    const pdfUrl = await row.getByRole('link', { name: /Ver/ }).getAttribute('href');
    const pdfResponse = page.context().waitForEvent('response', { predicate: response => new URL(response.url()).pathname === pdfUrl, timeout: 15_000 });
    const popup = page.waitForEvent('popup');
    await row.getByRole('link', { name: /Ver/ }).click();
    const viewer = await popup;
    const openedPdf = await pdfResponse;
    expect(openedPdf.status()).toBe(200);
    expect(openedPdf.headers()['content-type']).toContain('application/pdf');
    await viewer.close();
    const pdf = await request.get(pdfUrl!);
    expect(pdf.status()).toBe(200);
    expect((await pdf.body()).subarray(0, 4).toString()).toBe('%PDF');
    await fs.writeFile(path.join(directory, 'comprobante.pdf'), await pdf.body());
    for (const extension of ['pdf', 'excel']) {
      const downloadEvent = page.waitForEvent('download');
      await page.locator(`a[href^="/api/facturas/${extension}"]`).click();
      await (await downloadEvent).saveAs(path.join(directory, extension === 'pdf' ? 'reporte.pdf' : 'reporte.xlsx'));
    }
    check('Ver abre el PDF; PDF y Excel se descargan desde los botones con el filtro aplicado');

    const cancelResponse = page.waitForResponse(r => new URL(r.url()).pathname === `/api/facturas/${saved.id}` && r.request().method() === 'DELETE');
    await row.getByRole('button', { name: 'Anular', exact: true }).click();
    await page.locator('#confirm-modal-ok').click();
    expect((await cancelResponse).status()).toBe(204);
    await expect(row).toContainText('ANULADA');
    await expect(page.locator('#alerta')).toContainText('anulada correctamente');
    await page.screenshot({ path: path.join(directory, '03-anulada.png'), fullPage: true });
    const reactivateResponse = page.waitForResponse(r => new URL(r.url()).pathname === `/api/facturas/${saved.id}/reactivar`);
    await row.getByRole('button', { name: 'Reactivar', exact: true }).click();
    await page.locator('#confirm-modal-ok').click();
    expect((await reactivateResponse).status()).toBe(204);
    await expect(row).toContainText('AUTORIZADA');
    await expect(page.locator('#alerta')).toContainText('reactivada correctamente');
    check('Anulacion y reactivacion reales desde la interfaz, con confirmaciones y estados persistidos');
    const finalDetail = await (await request.get(`/api/facturas/${saved.id}`)).json();
    expect(finalDetail.numero).toBe(detail.numero);
    expect(finalDetail.fechaEmision).toBe(detail.fechaEmision);
    report.authorizedInvoiceId = saved.id;
    expect((await (await request.get(`/api/facturas?numero=${saved.numeroFactura}`)).json()).content.find((i: any) => i.id === saved.id).editable).toBe(false);
    const duplicate = await request.post('/api/clientes', { data: { cuitCuil: cuit.slice(0, 2) + '-' + cuit.slice(2, 10) + '-' + cuit.slice(10), denominacion: marker, condicionIvaId: tax.id } });
    expect(duplicate.status()).toBe(409);
    check('CUIT duplicado con otro formato rechazado con HTTP 409');
    await page.screenshot({ path: path.join(directory, '04-final.png'), fullPage: true });
    expect(report.consoleErrors).toEqual([]);
    expect(report.pageErrors).toEqual([]);
    expect(report.failedRequests).toEqual([]);
    report.success = true;
  } catch (error) {
    report.success = false;
    report.error = String(error);
    await page.screenshot({ path: path.join(directory, 'error.png'), fullPage: true }).catch(() => {});
    throw error;
  } finally {
    report.finishedAt = new Date().toISOString();
    await persist();
    await testInfo.attach('workflow-report', { path: path.join(directory, 'result.json'), contentType: 'application/json' });
    console.log(JSON.stringify({ workflow: report.success, clientId: report.client?.id, invoices: report.invoices.map((i: any) => ({ id: i.id, numero: i.numeroFactura, estado: i.estado })), checks: report.checks.length }));
  }
});
