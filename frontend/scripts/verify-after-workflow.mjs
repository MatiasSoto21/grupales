import { chromium, expect } from '@playwright/test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';

const reportFile = process.argv[2] ?? '.test-evidence/completed-run/result.json';
const report = JSON.parse(await fs.readFile(reportFile, 'utf8'));
const output = path.dirname(reportFile);
const invoice = report.invoices.find(i => i.id === report.authorizedInvoiceId);
const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({ baseURL: 'http://localhost:3000', acceptDownloads: true });
const page = await context.newPage();
const errors = [];
const requests = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
page.on('request', request => { if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/facturas') requests.push(request.url()); });
try {
  const params = new URLSearchParams({ clienteId: String(report.client.id), numeroDesde: String(invoice.numeroFactura), numeroHasta: String(invoice.numeroFactura),
    fechaDesde: invoice.fechaEmision, fechaHasta: invoice.fechaEmision, montoMinimo: String(invoice.importeTotal), sortBy: 'total', sortDir: 'asc' });
  const combined = await context.request.get(`/api/facturas?${params}`);
  assert.equal(combined.status(), 200);
  const result = await combined.json();
  assert.equal(result.totalElements, 1);
  assert.equal(result.content[0].id, invoice.id);
  const withoutClient = await (await context.request.get(`/api/facturas?sinCliente=true&numero=${invoice.numeroFactura}`)).json();
  assert.equal(withoutClient.totalElements, 0);
  await page.goto('/');
  await expect(page.locator('#api-status')).toContainText('Backend conectado');
  await page.locator('#clienteId').selectOption(String(report.client.id));
  await page.locator('#numeroDesde').fill(String(invoice.numeroFactura));
  await page.locator('#numeroHasta').fill(String(invoice.numeroFactura));
  await page.locator('#fechaDesde').fill(invoice.fechaEmision);
  await page.locator('#fechaHasta').fill(invoice.fechaEmision);
  await page.locator('#montoMinimo').fill(String(invoice.importeTotal));
  await page.locator('#filtros-form button[type=submit]').click();
  await expect(page.locator('#stat-cantidad')).toHaveText('1');
  await expect(page.locator('#facturas-tbody tr')).toContainText(report.client.denominacion);
  await page.screenshot({ path: path.join(output, '05-filtros-combinados.png'), fullPage: true });
  for (const [extension, name] of [['pdf', 'reporte-combinado.pdf'], ['excel', 'reporte-combinado.xlsx']]) {
    const download = page.waitForEvent('download');
    await page.locator(`a[href^="/api/facturas/${extension}"]`).click();
    await (await download).saveAs(path.join(output, name));
  }
  await page.locator('#btn-limpiar').click();
  const nextResponse = await context.request.get('/api/facturas/siguiente-numero?puntoVentaId=1');
  assert.equal(nextResponse.status(), 200);
  const nextNumber = (await nextResponse.json()).numero;
  await page.locator('#btn-nueva-factura').click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#numero')).toHaveValue(String(nextNumber), { timeout: 45_000 });
  await frame.locator('#clienteId').selectOption(String(report.client.id));
  await expect(frame.locator('#condicionIvaId')).toHaveValue(String(report.client.condicionIvaId));
  await expect(frame.locator('#condicionIvaId')).toBeDisabled();
  await expect(frame.locator('#detalles-tbody .qty-input')).toHaveCount(0);
  await page.screenshot({ path: path.join(output, '06-iva-futuro.png'), fullPage: true });
  await frame.locator('#btn-cancelar-factura').click();
  await expect(page.locator('#factura-modal')).not.toBeVisible();
  assert.deepEqual(errors, []);
  assert.deepEqual(requests, []);
  const resultReport = { success: true, combinedFiltersInvoiceId: invoice.id, matchedCount: 1, vatRestoredOnFutureInvoice: true, nextArcaNumber: nextNumber, newInvoicesCreated: 0, consoleErrors: errors };
  await fs.writeFile(path.join(output, 'after-flow.json'), JSON.stringify(resultReport, null, 2));
  console.log(JSON.stringify(resultReport));
} finally { await browser.close(); }
