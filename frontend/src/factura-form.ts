import { crearCliente, getCatalogos, getFactura, getSiguienteNumeroFactura, guardarFactura } from './api.js';
import { byId, clearAlert, currency, makeOption, showAlert } from './dom.js';
import type { ArticuloCatalogo, Catalogos, DetalleFactura, DetalleRequest, FacturaRequest } from './types.js';

const API_URL = 'http://localhost:8080/api/facturas';
const params = new URLSearchParams(window.location.search);
const idParam = params.get('id');
const idEdicion = idParam && Number.isInteger(Number(idParam)) ? Number(idParam) : undefined;
const modalMode = params.has('modal');
let catalogos: Catalogos | null = null;
let lineas: DetalleFactura[] = [];
let estadoActual = 'EMITIDA';
let guardando = false;
let creandoCliente = false;

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
  taxSelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const tax of catalogos.condicionesIva ?? []) taxSelect.append(makeOption(tax.id, tax.denominacion));

  const pointSelect = byId<HTMLSelectElement>('puntoVentaId');
  pointSelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const point of catalogos.puntosVenta ?? []) {
    pointSelect.append(makeOption(point.id, `${point.numero ? `${point.numero} · ` : ''}${point.descripcion || 'Punto de venta'}`));
  }

  const currencySelect = byId<HTMLSelectElement>('tipoMonedaId');
  currencySelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const money of catalogos.tiposMoneda ?? []) {
    currencySelect.append(makeOption(money.id, `${money.simbolo ?? ''} ${money.denominacion}`.trim()));
  }

  const productSelect = byId<HTMLSelectElement>('producto-articulo');
  productSelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const article of catalogos.articulos ?? []) {
    productSelect.append(makeOption(article.id, `${article.codigo ? `${article.codigo} · ` : ''}${article.denominacion}`));
  }
}

function closeClientPanel(): void {
  const panel = byId<HTMLDialogElement>('nuevo-cliente-panel');
  if (panel.open) panel.close();
  byId<HTMLButtonElement>('btn-guardar').disabled = false;
  panel.querySelectorAll<HTMLInputElement>('input').forEach(input => { input.value = ''; });
}

async function saveNewClient(): Promise<void> {
  if (creandoCliente) return;

  const cuitCuil = byId<HTMLInputElement>('nuevo-cliente-cuit').value.trim();
  const denominacion = byId<HTMLInputElement>('nuevo-cliente-denominacion').value.trim();
  if (!cuitCuil || !denominacion) {
    showAlert('CUIT/CUIL y denominación son obligatorios para crear el cliente.');
    return;
  }

  creandoCliente = true;
  const saveButton = byId<HTMLButtonElement>('btn-guardar-cliente');
  const cancelButton = byId<HTMLButtonElement>('btn-cancelar-cliente');
  saveButton.disabled = true;
  saveButton.textContent = 'Guardando…';
  cancelButton.disabled = true;
  clearAlert();

  try {
    const cliente = await crearCliente({
      cuitCuil,
      denominacion,
      email: byId<HTMLInputElement>('nuevo-cliente-email').value.trim(),
      telefono: byId<HTMLInputElement>('nuevo-cliente-telefono').value.trim(),
      celular: byId<HTMLInputElement>('nuevo-cliente-celular').value.trim(),
      nombreCalle: byId<HTMLInputElement>('nuevo-cliente-calle').value.trim(),
      numeroCalle: byId<HTMLInputElement>('nuevo-cliente-numero-calle').value.trim()
    });

    const clientSelect = byId<HTMLSelectElement>('clienteId');
    clientSelect.append(makeOption(cliente.id, cliente.denominacion));
    clientSelect.value = String(cliente.id);
    closeClientPanel();
    showAlert(`Cliente "${cliente.denominacion}" creado y seleccionado para la factura.`, 'success');
  } catch (error: unknown) {
    showAlert(`No se pudo crear el cliente: ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    creandoCliente = false;
    saveButton.disabled = false;
    saveButton.textContent = 'Guardar cliente';
    cancelButton.disabled = false;
  }
}

function calculateSubtotal(line: DetalleFactura): number {
  const price = Number(articleById(line.articuloId)?.precioVenta ?? 0);
  return price * Number(line.cantidad || 0) * (1 - Number(line.porcentajeBonificacion || 0) / 100);
}

function updateTotals(): void {
  const net = lineas.reduce((sum, line) => sum + calculateSubtotal(line), 0);
  const iva = net * 0.21;
  byId<HTMLElement>('neto-total').textContent = currency(net);
  byId<HTMLElement>('iva-total').textContent = currency(iva);
  byId<HTMLElement>('total-final').textContent = currency(net + iva);
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
    articleSelect.append(makeOption('', 'Seleccionar'));
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
    quantity.type = 'number'; quantity.min = '1'; quantity.step = '1'; quantity.required = true;
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
  byId<HTMLInputElement>('observaciones').value = invoice.observaciones ?? '';
  estadoActual = invoice.estado || 'EMITIDA';
  lineas = (invoice.detalles ?? []).map(detail => createLine(detail));
  renderLines();
  if (estadoActual.toUpperCase() === 'ANULADA') {
    const reactivationField = byId<HTMLDivElement>('reactivacion-field');
    const reactivationStatus = byId<HTMLSelectElement>('estado-reactivacion');
    reactivationField.classList.remove('hidden');
    reactivationStatus.required = true;
    showAlert('La factura está anulada. Podés editarla y elegir un estado para reactivarla.');
  }
}

function readForm(): FacturaRequest {
  const details: DetalleRequest[] = lineas.map(line => ({
    articuloId: Number(line.articuloId),
    descripcion: line.descripcion || articleById(line.articuloId)?.denominacion || '',
    cantidad: Number(line.cantidad),
    porcentajeBonificacion: Number(line.porcentajeBonificacion || 0)
  }));
  return {
    clienteId: byId<HTMLSelectElement>('clienteId').value ? Number(byId<HTMLSelectElement>('clienteId').value) : null,
    condicionIvaId: Number(byId<HTMLSelectElement>('condicionIvaId').value),
    tipoMonedaId: Number(byId<HTMLSelectElement>('tipoMonedaId').value),
    puntoVentaId: Number(byId<HTMLSelectElement>('puntoVentaId').value),
    estado: estadoActual.toUpperCase() === 'ANULADA'
      ? byId<HTMLSelectElement>('estado-reactivacion').value
      : estadoActual,
    observaciones: byId<HTMLInputElement>('observaciones').value.trim(),
    detalles: details
  };
}

function closeProductModal(): void {
  const modal = byId<HTMLDialogElement>('producto-modal');
  if (modal.open) modal.close();
}

function updateProductPreview(): void {
  const productId = byId<HTMLSelectElement>('producto-articulo').value;
  const quantity = Number(byId<HTMLInputElement>('producto-cantidad').value || 0);
  const discount = Number(byId<HTMLInputElement>('producto-bonificacion').value || 0);
  const price = Number(articleById(productId)?.precioVenta ?? 0);
  byId<HTMLElement>('producto-subtotal').textContent = currency(price * quantity * (1 - discount / 100));
}

byId<HTMLButtonElement>('btn-agregar').addEventListener('click', () => {
  byId<HTMLSelectElement>('producto-articulo').value = '';
  byId<HTMLInputElement>('producto-cantidad').value = '1';
  byId<HTMLInputElement>('producto-bonificacion').value = '0';
  updateProductPreview();
  byId<HTMLDialogElement>('producto-modal').showModal();
  byId<HTMLSelectElement>('producto-articulo').focus();
});
byId<HTMLButtonElement>('btn-cerrar-producto').addEventListener('click', closeProductModal);
byId<HTMLButtonElement>('btn-cancelar-producto').addEventListener('click', closeProductModal);
for (const id of ['producto-articulo', 'producto-cantidad', 'producto-bonificacion']) {
  byId<HTMLInputElement | HTMLSelectElement>(id).addEventListener('input', updateProductPreview);
}
byId<HTMLFormElement>('producto-form').addEventListener('submit', event => {
  event.preventDefault();
  const productId = byId<HTMLSelectElement>('producto-articulo').value;
  const quantity = Number(byId<HTMLInputElement>('producto-cantidad').value);
  const discount = Number(byId<HTMLInputElement>('producto-bonificacion').value || 0);
  if (!productId || !Number.isInteger(quantity) || quantity < 1) {
    showAlert('Datos de producto incompletos.');
    return;
  }
  if (!Number.isFinite(discount) || discount < 0 || discount > 100) {
    showAlert('La bonificación debe estar entre 0 y 100%.');
    return;
  }
  lineas.push({
    articuloId: Number(productId),
    descripcion: articleById(productId)?.denominacion ?? '',
    cantidad: quantity,
    porcentajeBonificacion: discount
  });
  renderLines();
  closeProductModal();
});
byId<HTMLButtonElement>('btn-mostrar-cliente').addEventListener('click', () => {
  clearAlert();
  const panel = byId<HTMLDialogElement>('nuevo-cliente-panel');
  panel.showModal();
  byId<HTMLButtonElement>('btn-guardar').disabled = true;
  byId<HTMLInputElement>('nuevo-cliente-cuit').focus();
});
byId<HTMLButtonElement>('btn-cerrar-cliente-x').addEventListener('click', closeClientPanel);
byId<HTMLButtonElement>('btn-cancelar-cliente').addEventListener('click', closeClientPanel);
byId<HTMLButtonElement>('btn-guardar-cliente').addEventListener('click', () => { void saveNewClient(); });
byId<HTMLDialogElement>('nuevo-cliente-panel').addEventListener('close', () => {
  byId<HTMLButtonElement>('btn-guardar').disabled = false;
});
byId<HTMLFormElement>('factura-form').addEventListener('submit', async event => {
  event.preventDefault();
  clearAlert();
  if (byId<HTMLDialogElement>('nuevo-cliente-panel').open) return;
  if (guardando) return;
  const body = readForm();
  if (estadoActual.toUpperCase() === 'ANULADA' && !body.estado) {
    showAlert('Elegí el nuevo estado para reactivar la factura.');
    return;
  }
  if (body.detalles.length === 0) { showAlert('Agregá al menos un producto.'); return; }
  if (body.detalles.some(detail => !detail.articuloId || !Number.isInteger(detail.cantidad) || detail.cantidad < 1)) {
    showAlert('Hay productos incompletos.'); return;
  }
  if (body.detalles.some(detail => detail.porcentajeBonificacion < 0 || detail.porcentajeBonificacion > 100)) {
    showAlert('La bonificación debe estar entre 0 y 100%.'); return;
  }
  guardando = true;
  const saveButton = byId<HTMLButtonElement>('btn-guardar');
  saveButton.disabled = true;
  saveButton.textContent = 'Guardando…';
  try {
    await guardarFactura(body, idEdicion);
    showAlert(idEdicion ? 'Factura actualizada correctamente.' : 'Factura creada correctamente.', 'success');
    window.setTimeout(() => {
      if (modalMode && window.parent !== window) {
        window.parent.postMessage({ type: 'factura-modal-close', reload: true }, '*');
      } else {
        window.location.href = '/';
      }
    }, 900);
  } catch (error: unknown) {
    showAlert(`No se pudo guardar la factura: ${error instanceof Error ? error.message : String(error)}`);
    guardando = false;
    saveButton.disabled = false;
    saveButton.textContent = 'Guardar factura';
  }
});

async function init(): Promise<void> {
  if (modalMode) {
    document.body.classList.add('modal-mode');
    const cancelButton = byId<HTMLButtonElement>('btn-cancelar-factura');
    cancelButton.addEventListener('click', () => {
      if (window.parent !== window) window.parent.postMessage({ type: 'factura-modal-close', reload: false }, '*');
      else window.location.href = '/';
    });
  } else {
    byId<HTMLButtonElement>('btn-cancelar-factura').addEventListener('click', () => { window.location.href = '/'; });
  }
  try {
    const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    byId<HTMLInputElement>('fechaEmision').value = today;
    await loadCatalogs();
    if (idEdicion !== undefined) {
      await loadInvoiceForEdit(idEdicion);
    } else {
      try {
        const proximo = await getSiguienteNumeroFactura();
        byId<HTMLInputElement>('numero').value = String(proximo.numero);
      } catch {
        byId<HTMLInputElement>('numero').value = '';
      }
      lineas = [];
      renderLines();
    }
  } catch (error: unknown) {
    showAlert(`No se pudo preparar el formulario. Verificá que el backend esté activo y que hayas agregado los endpoints de integración. Detalle: ${error instanceof Error ? error.message : String(error)}`);
    byId<HTMLButtonElement>('btn-guardar').disabled = true;
  }
}

void API_URL;
void init();
