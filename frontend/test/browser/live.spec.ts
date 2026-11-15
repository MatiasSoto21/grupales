import { test, expect } from '@playwright/test';

test.skip(process.env.RUN_LIVE_TESTS !== '1', 'Requiere backend real en TEST_BACKEND_URL');
test.beforeEach(async ({ page }) => {
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
  (page as typeof page & { errors: string[] }).errors = errors;
});
test.afterEach(async ({ page }) => {
  expect((page as typeof page & { errors: string[] }).errors).toEqual([]);
});

test('real listing and exports work through the same-origin proxy', async ({ page, request }) => {
  await page.goto('/');
  await expect(page.locator('#api-status')).toContainText('Backend conectado');
  const count = Number((await page.locator('#stat-cantidad').textContent())?.replaceAll('.', ''));
  expect(count).toBeGreaterThan(0);
  await page.locator('#btn-abrir-orden').click();
  await page.locator('#sort-field').selectOption('numero');
  await page.locator('#sort-direction').selectOption('asc');
  await page.locator('#sort-form button[type=submit]').click();
  await expect(page.locator('#api-status')).toContainText('Backend conectado');
  const pdf = await request.get('/api/facturas/pdf?sortBy=numero&sortDir=asc');
  expect(pdf.status()).toBe(200);
  expect((await pdf.body()).subarray(0, 4).toString()).toBe('%PDF');
  const excel = await request.get('/api/facturas/excel?sortBy=numero&sortDir=asc');
  expect(excel.status()).toBe(200);
  expect((await excel.body()).subarray(0, 2).toString()).toBe('PK');
});

test('real invoice form consults ARCA without emitting and can view a saved invoice', async ({ page, request }) => {
  await page.goto('/');
  await expect(page.locator('#api-status')).toContainText('Backend conectado');
  const receipt = page.locator('#facturas-tbody a').first().getAttribute('href');
  const pdf = await request.get((await receipt)!);
  expect(pdf.status()).toBe(200);
  await page.locator('#btn-nueva-factura').click();
  const frame = page.frameLocator('#factura-modal-frame');
  await expect(frame.locator('#numero')).toHaveValue(/^[0-9]+$/, { timeout: 45_000 });
  await expect(frame.locator('#detalles-tbody select')).toHaveCount(0);
  await expect(frame.locator('#alerta')).not.toBeVisible();
  await page.locator('#btn-cerrar-factura-modal').click();
  await expect(page.locator('#factura-modal')).not.toBeVisible();
});
