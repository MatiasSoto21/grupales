import { getCatalogos, getFactura, getFacturas, guardarFactura } from './api.js';
import { byId, clearAlert, currency, makeOption, showAlert } from './dom.js';
import type { ArticuloCatalogo, Catalogos, DetalleFactura, DetalleRequest, FacturaRequest } from './types.js';

const API_URL = 'http://localhost:8080/api/facturas';
const params = new URLSearchParams(window.location.search);
const idParam = params.get('id');
const idEdicion = idParam && Number.isInteger(Number(idParam)) ? Number(idParam) : undefined;
let catalogos: Catalogos | null = null;
let lineas: DetalleFactura[] = [];
let estadoActual = 'EMITIDA';
let guardando = false;

function articleById(id: string | number | undefined): ArticuloCatalogo | undefined {
  return (catalogos?.articulos ?? []).find(article => String(article.id) === String(id));
}

function createLine(detail: Partial<DetalleFactura> = {}): DetalleFactura {
  return {
    articuloId: detail.articuloId ?? undefined,
    descripcion: detail.descripcion ?? '',
    cantidad: Number(detail.cantidad ?? 1),
    porcentajeBonificacion: Number(detail.porcentajeBonificacion ?? 0)
  };
}

async function loadCatalogs(): Promise<void> {
  catalogos = await getCatalogos();
  const clientSelect = byId<HTMLSelectElement>('clienteId');
  clientSelect.replaceChildren(makeOption('', 'Consumidor final'));
  for (const client of catalogos.clientes ?? []) clientSelect.append(makeOption(client.id, client.denominacion));

  const taxSelect = byId<HTMLSelectElement>('condicionIvaId');
  taxSelect.replaceChildren(makeOption('', 'Seleccioná una condición'));
  for (const tax of catalogos.condicionesIva ?? []) taxSelect.append(makeOption(tax.id, tax.denominacion));

  const pointSelect = byId<HTMLSelectElement>('puntoVentaId');
  pointSelect.replaceChildren(makeOption('', 'Seleccioná un punto de venta'));
  for (const point of catalogos.puntosVenta ?? []) {
    pointSelect.append(makeOption(point.id, `${point.numero ? `${point.numero} · ` : ''}${point.descripcion || 'Punto de venta'}`));
  }

  const currencySelect = byId<HTMLSelectElement>('tipoMonedaId');
  currencySelect.replaceChildren(makeOption('', 'Seleccioná una moneda'));
  for (const money of catalogos.tiposMoneda ?? []) {
    currencySelect.append(makeOption(money.id, `${money.simbolo ?? ''} ${money.denominacion}`.trim()));
  }
}

function calculateSubtotal(line: DetalleFactura): number {
  const price = Number(articleById(line.articuloId)?.precioVenta ?? 0);
  return price * Number(line.cantidad || 0) * (1 - Number(line.porcentajeBonificacion || 0) / 100);
}

function updateTotals(): void {
  const net = lineas.reduce((sum, line) => sum + calculateSubtotal(line), 0);
  const iva = net * 0.21;
  byId<HTMLElement>('neto-estimado').textContent = currency(net);
  byId<HTMLElement>('iva-estimado').textContent = currency(iva);
  byId<HTMLElement>('total-estimado').textContent = currency(net + iva);
  byId<HTMLTableSectionElement>('detalles-tbody').querySelectorAll('tr').forEach((row, index) => {
    const line = lineas[index];
    if (!line) return;
    if (row.cells[3]) row.cells[3].textContent = currency(articleById(line.articuloId)?.precioVenta ?? 0);
    if (row.cells[4]) row.cells[4].textContent = currency(calculateSubtotal(line));
  });
}

function renderLines(): void {
  const tableBody = byId<HTMLTableSectionElement>('detalles-tbody');
  tableBody.innerHTML = '';
  if (!catalogos) return;
  if (lineas.length === 0) {
    const row = document.createElement('tr');
    row.innerHTML = '<td colspan="6" class="loading">Agregá al menos un producto para continuar.</td>';
    tableBody.append(row);
    updateTotals();
    return;
  }

  lineas.forEach((line, index) => {
    const row = document.createElement('tr');
    const articleCell = document.createElement('td');
    const articleSelect = document.createElement('select');
    articleSelect.className = 'product-select';
    articleSelect.required = true;
    articleSelect.append(makeOption('', 'Seleccioná un artículo'));
    for (const article of catalogos?.articulos ?? []) {
      articleSelect.append(makeOption(article.id, `${article.codigo ? `${article.codigo} · ` : ''}${article.denominacion}`));
    }
    articleSelect.value = line.articuloId == null ? '' : String(line.articuloId);
    articleSelect.addEventListener('change', () => {
      line.articuloId = articleSelect.value ? Number(articleSelect.value) : undefined;
      line.descripcion = articleById(articleSelect.value)?.denominacion ?? '';
      updateTotals();
    });
    articleCell.append(articleSelect);

    const quantityCell = document.createElement('td');
    const quantity = document.createElement('input');
    quantity.className = 'qty-input';
    quantity.type = 'number'; quantity.min = '0.01'; quantity.step = '0.01'; quantity.required = true;
    quantity.value = String(line.cantidad);
    quantity.addEventListener('input', () => { line.cantidad = Number(quantity.value) || 0; updateTotals(); });
    quantityCell.append(quantity);

    const discountCell = document.createElement('td');
    const discount = document.createElement('input');
    discount.className = 'discount-input';
    discount.type = 'number'; discount.min = '0'; discount.max = '100'; discount.step = '0.01';
    discount.value = String(line.porcentajeBonificacion);
    discount.addEventListener('input', () => {
      line.porcentajeBonificacion = Math.min(100, Math.max(0, Number(discount.value) || 0));
      updateTotals();
    });
    discountCell.append(discount);

    const priceCell = document.createElement('td');
    priceCell.className = 'text-right';
    priceCell.textContent = currency(articleById(line.articuloId)?.precioVenta ?? 0);
    const subtotalCell = document.createElement('td');
    subtotalCell.className = 'text-right';
    subtotalCell.textContent = currency(calculateSubtotal(line));

    const actionsCell = document.createElement('td');
    const removeButton = document.createElement('button');
    removeButton.type = 'button'; removeButton.className = 'btn btn-danger btn-small'; removeButton.textContent = 'Quitar';
    removeButton.addEventListener('click', () => { lineas.splice(index, 1); renderLines(); });
    actionsCell.append(removeButton);
    row.append(articleCell, quantityCell, discountCell, priceCell, subtotalCell, actionsCell);
    tableBody.append(row);
  });
  updateTotals();
}

function selectValue(id: string, value: number | null | undefined): void {
  if (value !== null && value !== undefined) byId<HTMLSelectElement>(id).value = String(value);
}

async function loadInvoiceForEdit(id: number): Promise<void> {
  const invoice = await getFactura(id);
  byId<HTMLHeadingElement>('titulo').textContent = 'Editar factura';
  byId<HTMLElement>('modo-label').textContent = 'Edición';
  byId<HTMLElement>('estado-badge').textContent = invoice.estado || 'EMITIDA';
  byId<HTMLInputElement>('numero').value = invoice.numero == null ? '' : String(invoice.numero);
  byId<HTMLInputElement>('fechaEmision').value = invoice.fechaEmision || '';
  selectValue('puntoVentaId', invoice.puntoVentaId);
  selectValue('clienteId', invoice.clienteId);
  selectValue('condicionIvaId', invoice.condicionIvaId);
  selectValue('tipoMonedaId', invoice.tipoMonedaId);
  byId<HTMLInputElement>('importeCobrado').value = String(invoice.importeCobrado ?? 0);
  byId<HTMLInputElement>('observaciones').value = invoice.observaciones ?? '';
  estadoActual = invoice.estado || 'EMITIDA';
  lineas = (invoice.detalles ?? []).map(detail => createLine(detail));
  renderLines();
  if (estadoActual.toUpperCase() === 'ANULADA') {
    byId<HTMLButtonElement>('btn-guardar').disabled = true;
    showAlert('Esta factura está anulada y no se puede editar.');
  }
}

function readForm(): FacturaRequest {
  const numberValue = Number(byId<HTMLInputElement>('numero').value);
  const details: DetalleRequest[] = lineas.map(line => ({
    articuloId: Number(line.articuloId),
    descripcion: line.descripcion || articleById(line.articuloId)?.denominacion || '',
    cantidad: Number(line.cantidad),
    porcentajeBonificacion: Number(line.porcentajeBonificacion || 0)
  }));
  return {
    numero: Number.isFinite(numberValue) && byId<HTMLInputElement>('numero').value.trim() !== '' ? numberValue : null,
    fechaEmision: byId<HTMLInputElement>('fechaEmision').value,
    clienteId: byId<HTMLSelectElement>('clienteId').value ? Number(byId<HTMLSelectElement>('clienteId').value) : null,
    condicionIvaId: Number(byId<HTMLSelectElement>('condicionIvaId').value),
    tipoMonedaId: Number(byId<HTMLSelectElement>('tipoMonedaId').value),
    puntoVentaId: Number(byId<HTMLSelectElement>('puntoVentaId').value),
    importeCobrado: Number(byId<HTMLInputElement>('importeCobrado').value || 0),
    importeSaldo: 0,
    estado: estadoActual || 'EMITIDA',
    observaciones: byId<HTMLInputElement>('observaciones').value.trim(),
    detalles: details
  };
}

byId<HTMLButtonElement>('btn-agregar').addEventListener('click', () => { lineas.push(createLine()); renderLines(); });
byId<HTMLFormElement>('factura-form').addEventListener('submit', async event => {
  event.preventDefault();
  clearAlert();
  if (guardando) return;
  const body = readForm();
  if (body.detalles.length === 0) { showAlert('Agregá al menos un producto.'); return; }
  if (body.detalles.some(detail => !detail.articuloId || !Number.isFinite(detail.cantidad) || detail.cantidad <= 0)) {
    showAlert('Revisá los productos: todos deben tener artículo y cantidad mayor a cero.'); return;
  }
  if (body.detalles.some(detail => detail.porcentajeBonificacion < 0 || detail.porcentajeBonificacion > 100)) {
    showAlert('La bonificación debe estar entre 0 y 100%.'); return;
  }
  if (body.importeCobrado < 0) { showAlert('El importe cobrado no puede ser negativo.'); return; }

  guardando = true;
  const saveButton = byId<HTMLButtonElement>('btn-guardar');
  saveButton.disabled = true;
  saveButton.textContent = 'Guardando…';
  try {
    await guardarFactura(body, idEdicion);
    showAlert(idEdicion ? 'Factura actualizada correctamente.' : 'Factura creada correctamente.', 'success');
    window.setTimeout(() => { window.location.href = '/'; }, 900);
  } catch (error: unknown) {
    showAlert(`No se pudo guardar la factura: ${error instanceof Error ? error.message : String(error)}`);
    guardando = false;
    saveButton.disabled = false;
    saveButton.textContent = 'Guardar factura';
  }
});

async function init(): Promise<void> {
  try {
    const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    byId<HTMLInputElement>('fechaEmision').value = today;
    await loadCatalogs();
    if (idEdicion !== undefined) {
      await loadInvoiceForEdit(idEdicion);
    } else {
      try {
        const invoices = await getFacturas();
        const highestNumber = invoices.reduce((max, invoice) => Math.max(max, Number(invoice.numeroFactura) || 0), 0);
        byId<HTMLInputElement>('numero').value = String(highestNumber + 1 || 1);
      } catch { /* El número se puede ingresar manualmente si falla la consulta. */ }
      lineas = [createLine()];
      renderLines();
    }
  } catch (error: unknown) {
    showAlert(`No se pudo preparar el formulario. Verificá que el backend esté activo y que hayas agregado los endpoints de integración. Detalle: ${error instanceof Error ? error.message : String(error)}`);
    byId<HTMLButtonElement>('btn-guardar').disabled = true;
  }
}

void API_URL;
void init();
