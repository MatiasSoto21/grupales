import { calculateLineAmounts } from './money.js';
import { ArticuloExplorer } from './articulo-explorer.js';
import { ClienteExplorer } from './cliente-explorer.js';
import { emitirFiscalDesde, getComprobanteFacturaUrl, getPrecioArticulo, getCliente, crearCliente, getCatalogos, getFactura, getSiguienteNumeroFactura, guardarFactura } from './api.js';
import { byId, clearAlert, currency, initTheme, initValidationFeedback, makeOption, showAlert } from './dom.js';
import type { ArticuloBusqueda, Catalogos, DetalleFactura, DetalleRequest, FacturaRequest, ClienteBusqueda } from './types.js';

const params = new URLSearchParams(window.location.search);
const idParam = params.get('id');
const validId = idParam !== null && /^[1-9][0-9]*$/.test(idParam) && Number.isSafeInteger(Number(idParam));
const idEdicion = validId ? Number(idParam) : undefined;
const modalMode = params.has('modal');
let catalogos: Catalogos | null = null;
let lineas: DetalleFactura[] = [];
let estadoActual = 'EMITIDA';
let versionEdicion: number | undefined;
let guardando = false;
let facturaPersistida = false;
let creandoCliente = false;
let numeroRequest = 0;
let modoEmisionFiscal = false;
let puedeEmitirFiscal = false;
let referenciaOrigen = '';
let origenEsX = false;
let fechaOperacionOrigen = '';
let fechaOperacionAnterior = '';
let formularioListo = false;

function isEditing(): boolean { return idEdicion !== undefined && !modoEmisionFiscal; }
function isInternal(): boolean { return byId<HTMLSelectElement>('clase-documento').value === 'X'; }
function today(): string { return catalogos?.facturacion?.fechaActual ?? ''; }

let blockedControls: Array<[HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | HTMLButtonElement, boolean]> | null = null;

function setFormLocked(locked: boolean): void {
  if (locked && blockedControls === null) {
    blockedControls = [...byId<HTMLFormElement>('factura-form').querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement | HTMLButtonElement>('input, select, textarea, button')]
      .filter(control => control.id !== 'btn-cancelar-factura')
      .map(control => [control, control.disabled]);
    blockedControls.forEach(([control]) => { control.disabled = true; });
  } else if (!locked && blockedControls !== null) {
    blockedControls.forEach(([control, wasDisabled]) => { control.disabled = wasDisabled; });
    blockedControls = null;
  }
}

let clienteSeleccionado: Pick<ClienteBusqueda, 'id' | 'denominacion' | 'condicionIvaId'> | null = null;
const clienteExplorer = new ClienteExplorer(client => selectClient(client));

function selectClient(client: typeof clienteSeleccionado, preserveTax = false): void {
  clienteSeleccionado = client;
  byId<HTMLInputElement>('clienteId').value = client ? String(client.id) : '';
  byId<HTMLInputElement>('cliente-nombre').value = client?.denominacion ?? 'Consumidor final';
  syncClientTaxCondition(preserveTax);
  if (!preserveTax) void updateNextInvoiceNumber();
}

byId('btn-buscar-cliente').addEventListener('click', () => {
  if (!guardando && !facturaPersistida) clienteExplorer.open();
});
byId('btn-quitar-cliente').addEventListener('click', () => {
  if (!guardando && !facturaPersistida) selectClient(null);
});

const preciosCargados = new Map<number, ArticuloBusqueda>();
let productoSeleccionado: ArticuloBusqueda | null = null;
let editandoLinea: number | undefined;
const articuloExplorer = new ArticuloExplorer(selectProduct);

function selectProduct(product: ArticuloBusqueda | null): void {
  productoSeleccionado = product;
  if (product) preciosCargados.set(product.listaPrecioArticuloId, product);
  byId<HTMLInputElement>('producto-articulo').value = product ? String(product.articuloId) : '';
  byId<HTMLInputElement>('producto-precio').value = product ? String(product.listaPrecioArticuloId) : '';
  byId<HTMLInputElement>('producto-seleccionado').value = product ? `${product.codigo} · ${product.denominacion}` : '';
  byId<HTMLInputElement>('producto-precio-valor').value = product ? currency(product.precioVenta) : '';
  byId<HTMLButtonElement>('btn-confirmar-producto').disabled = !product;
  byId('producto-error').classList.add('hidden');
  updateProductPreview();
}

initTheme();
initValidationFeedback();

function notifySaving(busy: boolean): void {
  byId<HTMLButtonElement>('btn-cancelar-factura').disabled = busy;
  byId('factura-form').setAttribute('aria-busy', String(busy));
  if (modalMode && window.parent !== window) {
    window.parent.postMessage({ type: 'factura-modal-busy', busy }, window.location.origin);
  }
}

function priceById(id: string | number | undefined): ArticuloBusqueda | undefined {
  return id == null ? undefined : preciosCargados.get(Number(id));
}

function linePrice(line: DetalleFactura): number {
  return Number(priceById(line.listaPrecioArticuloId)?.precioVenta ?? 0);
}

function createLine(detail: Partial<DetalleFactura> = {}): DetalleFactura {
  return {
    articuloId: detail.articuloId ?? undefined,
    listaPrecioArticuloId: detail.listaPrecioArticuloId ?? undefined,
    descripcion: detail.descripcion ?? '',
    cantidad: Number(detail.cantidad ?? 1),
    porcentajeBonificacion: Number(detail.porcentajeBonificacion ?? 0)
  };
}

async function loadCatalogs(): Promise<void> {
  catalogos = await getCatalogos();
  clienteExplorer.setTaxes(catalogos.condicionesIva);

  const taxSelect = byId<HTMLSelectElement>('condicionIvaId');
  taxSelect.replaceChildren(makeOption('', 'Seleccionar'));
  const clientTaxSelect = byId<HTMLSelectElement>('nuevo-cliente-condicion-iva');
  clientTaxSelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const tax of catalogos.condicionesIva ?? []) {
    taxSelect.append(makeOption(tax.id, tax.denominacion));
    clientTaxSelect.append(makeOption(tax.id, tax.denominacion));
  }

  const pointSelect = byId<HTMLSelectElement>('puntoVentaId');
  pointSelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const point of catalogos.puntosVenta ?? []) {
    pointSelect.append(makeOption(point.id, `${point.numero ? `${point.numero} · ` : ''}${point.descripcion || 'Punto de venta'}`));
  }
  const firstPoint = catalogos.puntosVenta?.[0];
  if (firstPoint) {
    pointSelect.value = String(firstPoint.id);
  }

  const currencySelect = byId<HTMLSelectElement>('tipoMonedaId');
  currencySelect.replaceChildren(makeOption('', 'Seleccionar'));
  for (const money of catalogos.tiposMoneda ?? []) {
    currencySelect.append(makeOption(money.id, `${money.simbolo ?? ''} ${money.denominacion}`.trim()));
  }

  articuloExplorer.setLists(catalogos.listasPrecio);
}

async function updateNextInvoiceNumber(reportError = true): Promise<void> {
  if (guardando || facturaPersistida) return;
  const interno = byId<HTMLSelectElement>('clase-documento').value === 'X';
  const taxId = Number(byId<HTMLSelectElement>('condicionIvaId').value);
  const code = catalogos?.condicionesIva.find(tax => tax.id === taxId)?.codigoAfip;
  byId<HTMLInputElement>('tipo-comprobante').value = interno ? 'Comprobante X' : taxId ? ([1, 6, 13, 16].includes(code ?? 5) ? 'Factura A' : 'Factura B') : '';
  updateTotals();
  const requestId = ++numeroRequest;
  const pointId = Number(byId<HTMLSelectElement>('puntoVentaId').value);
  const numberField = byId<HTMLInputElement>('numero');
  if (!pointId || isEditing()) {
    if (!isEditing()) numberField.value = '';
    return;
  }

  numberField.value = 'Consultando…';
  try {
    const result = await getSiguienteNumeroFactura(pointId, Number(byId<HTMLSelectElement>("condicionIvaId").value) || undefined, interno);
    if (requestId === numeroRequest) numberField.value = String(result.numero);
  } catch (error: unknown) {
    if (requestId !== numeroRequest) return;
    numberField.value = '';
    if (reportError) showAlert(`No se pudo consultar el proximo numero ${interno ? "interno" : "en ARCA"}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

function closeClientPanel(): void {
  if (creandoCliente) return;
  const panel = byId<HTMLDialogElement>('nuevo-cliente-panel');
  if (panel.open) panel.close();
  byId<HTMLButtonElement>('btn-guardar').disabled = guardando || facturaPersistida;
  panel.querySelectorAll<HTMLInputElement>('input').forEach(input => { input.value = ''; });
  byId<HTMLSelectElement>('nuevo-cliente-condicion-iva').value = '';
}

async function saveNewClient(): Promise<void> {
  if (creandoCliente || guardando || facturaPersistida) return;

  const cuitCuil = byId<HTMLInputElement>('nuevo-cliente-cuit').value.trim();
  const denominacion = byId<HTMLInputElement>('nuevo-cliente-denominacion').value.trim();
  const condicionIvaId = Number(byId<HTMLSelectElement>('nuevo-cliente-condicion-iva').value);
  if (!cuitCuil || !denominacion || !condicionIvaId) {
    showAlert('CUIT/CUIL, denominación y condición de IVA son obligatorios para crear el cliente.');
    return;
  }

  creandoCliente = true;
  const clientControls = [...byId('nuevo-cliente-panel').querySelectorAll<HTMLInputElement | HTMLSelectElement | HTMLButtonElement>('input, select, button')]
    .map(control => [control, control.disabled] as const);
  clientControls.forEach(([control]) => { control.disabled = true; });
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
      condicionIvaId,
      email: byId<HTMLInputElement>('nuevo-cliente-email').value.trim(),
      telefono: byId<HTMLInputElement>('nuevo-cliente-telefono').value.trim(),
      celular: byId<HTMLInputElement>('nuevo-cliente-celular').value.trim(),
      nombreCalle: byId<HTMLInputElement>('nuevo-cliente-calle').value.trim(),
      numeroCalle: byId<HTMLInputElement>('nuevo-cliente-numero-calle').value.trim()
    });

    selectClient(cliente);
    creandoCliente = false;
    closeClientPanel();
    showAlert(`Cliente "${cliente.denominacion}" creado y seleccionado para la factura.`, 'success');
  } catch (error: unknown) {
    showAlert(`No se pudo crear el cliente: ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    creandoCliente = false;
    clientControls.forEach(([control, disabled]) => { control.disabled = disabled; });
    saveButton.disabled = false;
    saveButton.textContent = 'Guardar cliente';
    cancelButton.disabled = false;
  }
}

function calculateSubtotal(line: DetalleFactura): number {
  const price = linePrice(line);
  return calculateLineAmounts(price, line.cantidad, line.porcentajeBonificacion, isInternal()).neto;
}

function updateTotals(): void {
  const net = lineas.reduce((sum, line) => sum + calculateSubtotal(line), 0);
  const iva = lineas.reduce((sum, line) => sum + calculateLineAmounts(linePrice(line), line.cantidad, line.porcentajeBonificacion, isInternal()).iva, 0);
  byId<HTMLElement>('neto-total').textContent = currency(net);
  byId<HTMLElement>('iva-total').textContent = currency(iva);
  byId('neto-row').classList.toggle('hidden', isInternal());
  byId('iva-row').classList.toggle('hidden', isInternal());
  const type = byId<HTMLInputElement>('tipo-comprobante').value;
  byId('iva-label').textContent = type === 'Factura B' ? 'IVA contenido (21%)' : 'IVA (21%)';
  byId<HTMLElement>('total-final').textContent = currency(net + iva);
  byId<HTMLTableSectionElement>('detalles-tbody').querySelectorAll('tr').forEach((row, index) => {
    const line = lineas[index];
    if (!line) return;
    if (row.cells[4]) row.cells[4].textContent = currency(linePrice(line));
    if (row.cells[5]) row.cells[5].textContent = currency(calculateSubtotal(line));
  });
}

function renderLines(): void {
  const tableBody = byId<HTMLTableSectionElement>('detalles-tbody');
  tableBody.innerHTML = '';
  if (!catalogos) return;
  if (lineas.length === 0) {
    const row = document.createElement('tr');
    row.innerHTML = '<td colspan="7" class="loading">Agregá al menos un producto para continuar.</td>';
    tableBody.append(row);
    updateTotals();
    return;
  }

  lineas.forEach((line, index) => {
    const row = document.createElement('tr');
    const articleCell = document.createElement('td');
    articleCell.textContent = line.descripcion;
    const change = document.createElement('button');
    change.type = 'button'; change.className = 'btn btn-secondary btn-small'; change.textContent = 'Cambiar';
    change.addEventListener('click', () => openProductModal(index));
    articleCell.append(document.createElement('br'), change);
    const priceCell = document.createElement('td');
    priceCell.textContent = priceById(line.listaPrecioArticuloId)?.listaPrecioDenominacion ?? '';

    const quantityCell = document.createElement('td');
    const quantity = document.createElement('input');
    quantity.className = 'qty-input';
    quantity.type = 'number'; quantity.min = '1'; quantity.step = '1'; quantity.required = true;
    quantity.setAttribute('aria-label', `Cantidad de ${line.descripcion}`);
    quantity.value = String(line.cantidad);
    quantity.addEventListener('input', () => { line.cantidad = Number(quantity.value) || 0; updateTotals(); });
    quantityCell.append(quantity);

    const discountCell = document.createElement('td');
    const discount = document.createElement('input');
    discount.className = 'discount-input';
    discount.type = 'number'; discount.min = '0'; discount.max = '100'; discount.step = '0.01';
    discount.setAttribute('aria-label', `Bonificación de ${line.descripcion}`);
    discount.value = String(line.porcentajeBonificacion);
    discount.addEventListener('input', () => {
      line.porcentajeBonificacion = Number(discount.value);
      updateTotals();
    });
    discountCell.append(discount);

    const unitPriceCell = document.createElement('td');
    unitPriceCell.className = 'text-right';
    unitPriceCell.textContent = currency(linePrice(line));
    const subtotalCell = document.createElement('td');
    subtotalCell.className = 'text-right';
    subtotalCell.textContent = currency(calculateSubtotal(line));

    const actionsCell = document.createElement('td');
    const removeButton = document.createElement('button');
    removeButton.type = 'button'; removeButton.className = 'btn btn-danger btn-small'; removeButton.textContent = 'Quitar';
    removeButton.addEventListener('click', () => { lineas.splice(index, 1); renderLines(); });
    actionsCell.append(removeButton);
    row.append(articleCell, priceCell, quantityCell, discountCell, unitPriceCell, subtotalCell, actionsCell);
    tableBody.append(row);
  });
  updateTotals();
}

function selectValue(id: string, value: number | null | undefined): void {
  if (value !== null && value !== undefined) byId<HTMLSelectElement>(id).value = String(value);
}

function syncClientTaxCondition(preserveSelection = false): void {
  const client = clienteSeleccionado;
  const taxSelect = byId<HTMLSelectElement>('condicionIvaId');
  taxSelect.disabled = !formularioListo || client?.condicionIvaId != null;
  if (client?.condicionIvaId != null) {
    taxSelect.value = String(client.condicionIvaId);
  } else if (!preserveSelection) {
    taxSelect.value = "";
  }
}

async function loadInvoiceForEdit(id: number): Promise<void> {
  const invoice = await getFactura(id);
  if (invoice.editable === false || ['AUTORIZADA', 'ERROR_AFIP', 'ANULADA'].includes(invoice.estado)) {
    throw new Error('Este comprobante no permite edicion en su estado actual.');
  }
  origenEsX = invoice.comprobanteInterno === true;
  versionEdicion = invoice.version;
  fechaOperacionOrigen = invoice.fechaOperacion ?? invoice.fechaEmision ?? '';
  byId<HTMLInputElement>('fechaOperacion').value = invoice.fechaOperacion ?? '';
  byId('fecha-operacion-field').classList.toggle('hidden', !invoice.fechaOperacion);
  byId('fecha-solicitud-field').classList.toggle('hidden', !invoice.fechaSolicitudAfip);
  byId<HTMLInputElement>('fechaSolicitudAfip').value = invoice.fechaSolicitudAfip
    ? new Date(invoice.fechaSolicitudAfip).toLocaleString('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' }) : '';
  puedeEmitirFiscal = invoice.puedeEmitirFiscal === true;
  referenciaOrigen = invoice.referenciaComprobante ?? `${invoice.comprobanteInterno ? 'X-' : '#'}${invoice.numero ?? id} · ${invoice.fechaEmision ?? ''}`;
  if (invoice.comprobanteInterno && invoice.comprobanteFiscalDerivadoId) {
    byId('fiscal-vinculado-field').classList.remove('hidden');
    const linked = byId<HTMLAnchorElement>('fiscal-vinculado-link');
    linked.textContent = invoice.comprobanteFiscalReferencia ?? 'Ver factura fiscal vinculada';
    linked.href = getComprobanteFacturaUrl(invoice.comprobanteFiscalDerivadoId);
  }
  byId('btn-emitir-fiscal').classList.toggle('hidden', !puedeEmitirFiscal);
  byId('btn-emitir-fiscal').textContent = invoice.comprobanteInterno ? 'Emitir A/B en ARCA' : 'Nueva emisión A/B';
  byId<HTMLSelectElement>('clase-documento').value = invoice.comprobanteInterno ? 'X' : 'FISCAL';
  byId<HTMLSelectElement>('clase-documento').disabled = true;
  if (invoice.comprobanteInterno) byId<HTMLSelectElement>('puntoVentaId').disabled = true;
  byId<HTMLHeadingElement>('titulo').textContent = invoice.comprobanteInterno ? 'Editar X interno' : 'Editar factura';
  byId<HTMLButtonElement>('btn-guardar').textContent = 'Guardar cambios';
  byId<HTMLElement>('modo-label').textContent = 'Edición';
  byId<HTMLElement>('estado-badge').textContent = invoice.estado || 'EMITIDA';
  byId<HTMLInputElement>('numero').value = invoice.numero == null ? '' : String(invoice.numero);
  byId<HTMLInputElement>('fechaEmision').value = invoice.fechaEmision || '';
  selectValue('puntoVentaId', invoice.puntoVentaId);
  if (invoice.clienteId != null) selectClient(await getCliente(invoice.clienteId), true);
  selectValue('condicionIvaId', invoice.condicionIvaId);
  syncClientTaxCondition(true);
  await updateNextInvoiceNumber();
  selectValue('tipoMonedaId', invoice.tipoMonedaId);
  byId<HTMLInputElement>('observaciones').value = invoice.observaciones ?? '';
  estadoActual = invoice.estado || 'EMITIDA';
  lineas = (invoice.detalles ?? []).map(detail => createLine(detail));
  const ids = [...new Set(lineas.map(line => line.listaPrecioArticuloId).filter((id): id is number => id !== undefined))];
  const prices = await Promise.all(ids.map(id => getPrecioArticulo(id)));
  for (const price of prices) preciosCargados.set(price.listaPrecioArticuloId, price);
  renderLines();

}

function readForm(): FacturaRequest {
  const details: DetalleRequest[] = lineas.map(line => ({
    articuloId: Number(line.articuloId),
    listaPrecioArticuloId: Number(line.listaPrecioArticuloId),
    descripcion: line.descripcion,
    cantidad: Number(line.cantidad),
    porcentajeBonificacion: Number(line.porcentajeBonificacion || 0)
  }));
  return {
    version: versionEdicion,
    comprobanteInterno: byId<HTMLSelectElement>('clase-documento').value === 'X',
    numero: isEditing() ? Number(byId<HTMLInputElement>('numero').value) || null : null,
    fechaEmision: byId<HTMLInputElement>('fechaEmision').value || undefined,
    fechaOperacion: modoEmisionFiscal ? byId<HTMLInputElement>('fechaOperacion').value || undefined : undefined,
    clienteId: byId<HTMLInputElement>('clienteId').value ? Number(byId<HTMLInputElement>('clienteId').value) : null,
    condicionIvaId: Number(byId<HTMLSelectElement>('condicionIvaId').value),
    tipoMonedaId: Number(byId<HTMLSelectElement>('tipoMonedaId').value),
    puntoVentaId: Number(byId<HTMLSelectElement>('puntoVentaId').value),
    estado: estadoActual,
    observaciones: byId<HTMLInputElement>('observaciones').value.trim(),
    detalles: details
  };
}

function closeProductModal(): void {
  const modal = byId<HTMLDialogElement>('producto-modal');
  if (modal.open) modal.close();
}

function updateProductPreview(): void {
  const quantity = Number(byId<HTMLInputElement>('producto-cantidad').value || 0);
  const discount = Number(byId<HTMLInputElement>('producto-bonificacion').value || 0);
  byId<HTMLElement>('producto-subtotal').textContent = currency(calculateLineAmounts(productoSeleccionado?.precioVenta ?? 0, quantity, discount, isInternal()).neto);
}

function openProductModal(index?: number): void {
  if (guardando || facturaPersistida) return;
  editandoLinea = index;
  const line = index === undefined ? undefined : lineas[index];
  const price = line ? priceById(line.listaPrecioArticuloId) : undefined;
  byId<HTMLInputElement>('producto-cantidad').value = String(line?.cantidad ?? 1);
  byId<HTMLInputElement>('producto-bonificacion').value = String(line?.porcentajeBonificacion ?? 0);
  byId<HTMLButtonElement>('btn-confirmar-producto').textContent = line ? 'Actualizar producto' : 'Agregar a la factura';
  articuloExplorer.open(price?.listaPrecioId);
  if (price) selectProduct(price);
}

byId<HTMLButtonElement>('btn-agregar').addEventListener('click', () => openProductModal());
byId<HTMLButtonElement>('btn-cerrar-producto').addEventListener('click', closeProductModal);
byId<HTMLButtonElement>('btn-cancelar-producto').addEventListener('click', closeProductModal);
for (const id of ['producto-cantidad', 'producto-bonificacion']) {
  byId<HTMLInputElement>(id).addEventListener('input', updateProductPreview);
}
byId<HTMLFormElement>('producto-form').addEventListener('submit', event => {
  event.preventDefault();
  if (guardando || facturaPersistida) return;
  const quantity = Number(byId<HTMLInputElement>('producto-cantidad').value);
  const discount = Number(byId<HTMLInputElement>('producto-bonificacion').value || 0);
  const error = byId('producto-error');
  if (!productoSeleccionado || productoSeleccionado.listaPrecioId !== Number(byId<HTMLSelectElement>('producto-lista').value)
      || !Number.isInteger(quantity) || quantity < 1 || !Number.isFinite(discount) || discount < 0 || discount > 100) {
    error.textContent = 'Seleccioná un artículo, una cantidad entera positiva y una bonificación entre 0 y 100%.';
    error.classList.remove('hidden'); return;
  }
  const line = {
    articuloId: productoSeleccionado.articuloId,
    listaPrecioArticuloId: productoSeleccionado.listaPrecioArticuloId,
    descripcion: productoSeleccionado.denominacion,
    cantidad: quantity,
    porcentajeBonificacion: discount
  };
  if (editandoLinea === undefined) lineas.push(line);
  else lineas[editandoLinea] = line;
  renderLines(); closeProductModal();
});
byId<HTMLButtonElement>('btn-mostrar-cliente').addEventListener('click', () => {
  if (guardando || facturaPersistida) return;
  clearAlert();
  const panel = byId<HTMLDialogElement>('nuevo-cliente-panel');
  panel.showModal();
  byId<HTMLButtonElement>('btn-guardar').disabled = true;
  byId<HTMLInputElement>('nuevo-cliente-cuit').focus();
});
byId<HTMLButtonElement>('btn-cerrar-cliente-x').addEventListener('click', closeClientPanel);
byId<HTMLButtonElement>('btn-cancelar-cliente').addEventListener('click', closeClientPanel);
byId<HTMLFormElement>('nuevo-cliente-form').addEventListener('submit', event => { event.preventDefault(); void saveNewClient(); });
byId<HTMLSelectElement>('clase-documento').addEventListener('change', () => { clearAlert(); void updateNextInvoiceNumber(); });
byId<HTMLSelectElement>('condicionIvaId').addEventListener('change', () => { void updateNextInvoiceNumber(); });
byId<HTMLSelectElement>('puntoVentaId').addEventListener('change', () => {
  void updateNextInvoiceNumber();
});
byId<HTMLDialogElement>('nuevo-cliente-panel').addEventListener('cancel', event => { if (creandoCliente) event.preventDefault(); });
byId<HTMLDialogElement>('nuevo-cliente-panel').addEventListener('close', () => {
  byId<HTMLButtonElement>('btn-guardar').disabled = guardando || facturaPersistida;
});
byId<HTMLInputElement>('fechaOperacion').addEventListener('change', () => {
  if (modoEmisionFiscal && !guardando && !facturaPersistida) {
    const operation = byId<HTMLInputElement>('fechaOperacion').value;
    const fiscal = byId<HTMLInputElement>('fechaEmision');
    if (fiscal.value === fechaOperacionAnterior) fiscal.value = operation;
    fechaOperacionAnterior = operation;
  }
});
byId('btn-emitir-fiscal').addEventListener('click', () => {
  if (!puedeEmitirFiscal || idEdicion === undefined || guardando || facturaPersistida) return;
  modoEmisionFiscal = true;
  fechaOperacionAnterior = fechaOperacionOrigen;
  byId<HTMLSelectElement>('clase-documento').value = 'FISCAL';
  byId<HTMLSelectElement>('puntoVentaId').disabled = false;
  const fecha = byId<HTMLInputElement>('fechaEmision');
  fecha.readOnly = false; fecha.required = true;
  fecha.min = catalogos?.facturacion?.fechaFiscalMinima ?? '';
  fecha.max = catalogos?.facturacion?.fechaFiscalMaxima ?? '';
  fecha.value = fechaOperacionOrigen;
  byId('fecha-emision-label').textContent = 'Fecha del comprobante fiscal';
  const operacion = byId<HTMLInputElement>('fechaOperacion');
  operacion.value = fechaOperacionOrigen; operacion.readOnly = false; operacion.required = true;
  operacion.max = today();
  byId('fecha-operacion-field').classList.remove('hidden');
  byId('fecha-solicitud-field').classList.add('hidden');
  byId('titulo').textContent = 'Nueva emisión fiscal A/B';
  byId('modo-label').textContent = 'Emisión desde comprobante guardado';
  byId('estado-badge').textContent = 'NUEVA';
  byId('btn-guardar').textContent = 'Emitir en ARCA';
  byId('btn-emitir-fiscal').classList.add('hidden');
  byId('origen-comprobante-field').classList.remove('hidden');
  const link = byId<HTMLAnchorElement>('origen-comprobante-link');
  link.textContent = referenciaOrigen; link.href = getComprobanteFacturaUrl(idEdicion);
  clearAlert(); void updateNextInvoiceNumber();
  window.scrollTo({ top: 0, behavior: 'instant' });
  operacion.focus({ preventScroll: true });
});
byId<HTMLFormElement>('factura-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!formularioListo || document.querySelector('dialog[open]') || guardando || facturaPersistida) return;
  clearAlert();
  const body = readForm();
  if (body.detalles.length === 0) { showAlert('Agregá al menos un producto.'); return; }
  if (body.detalles.some(detail =>
    !detail.articuloId || !detail.listaPrecioArticuloId
    || !Number.isInteger(detail.cantidad) || detail.cantidad < 1)) {
    showAlert('Hay productos incompletos.'); return;
  }
  if (body.detalles.some(detail => !Number.isFinite(detail.porcentajeBonificacion) || detail.porcentajeBonificacion < 0 || detail.porcentajeBonificacion > 100)) {
    showAlert('La bonificación debe estar entre 0 y 100%.'); return;
  }
  guardando = true;
  ++numeroRequest;
  setFormLocked(true);
  notifySaving(true);
  const saveButton = byId<HTMLButtonElement>('btn-guardar');
  saveButton.disabled = true;
  saveButton.textContent = 'Guardando…';
  try {
    const savedInvoice = modoEmisionFiscal && idEdicion !== undefined
      ? await emitirFiscalDesde(idEdicion, body)
      : await guardarFactura(body, idEdicion);
    facturaPersistida = true;
    byId('btn-cancelar-factura').textContent = 'Cerrar';
    byId<HTMLElement>('estado-badge').textContent = savedInvoice.estado;
    byId<HTMLInputElement>('fechaEmision').value = savedInvoice.fechaEmision;
    byId<HTMLInputElement>('numero').value = savedInvoice.numeroFactura === null ? '' : String(savedInvoice.numeroFactura);
    if (!isEditing() && ['ERROR_AFIP', 'RECHAZADA'].includes(savedInvoice.estado)) {
      guardando = false;
      notifySaving(false);
      estadoActual = savedInvoice.estado;
      const resultMessage = savedInvoice.estado === 'RECHAZADA'
        ? 'La factura se guardo sin autorizacion de ARCA.'
        : 'La factura se guardo sin confirmacion de autorizacion de ARCA. No reenviar sin verificar el resultado.';
      showAlert(`${resultMessage} ${savedInvoice.motivoRechazo ?? 'Revisa el registro desde el listado.'}`);
      saveButton.textContent = savedInvoice.estado === 'RECHAZADA' ? 'Guardada sin autorizacion' : 'Guardada sin confirmacion';
      if (modalMode && window.parent !== window) {
        window.parent.postMessage({ type: 'factura-modal-reload' }, window.location.origin);
      }
      return;
    }
    const confirmation = modoEmisionFiscal && origenEsX && savedInvoice.estado === 'AUTORIZADA'
      ? 'Factura autorizada en ARCA. El X queda conservado como antecedente y ya no permite edicion.'
      : isEditing()
      ? savedInvoice.estado === 'RECHAZADA'
        ? 'Cambios guardados. El intento sigue rechazado; no se solicito una nueva autorizacion.'
        : savedInvoice.comprobanteInterno && savedInvoice.comprobanteFiscalDerivadoId
          ? 'Comprobante X actualizado. El intento fiscal vinculado conserva su resultado.'
          : 'Comprobante actualizado correctamente.'
      : savedInvoice.comprobanteInterno
        ? `Comprobante X guardado. Numero interno: ${savedInvoice.numeroFactura}.`
      : savedInvoice.numeroFactura === null
        ? 'Factura guardada. El número se asignará cuando ARCA la autorice.'
        : savedInvoice.estado === 'AUTORIZADA'
          ? `Factura autorizada por ARCA en homologacion. Numero: ${savedInvoice.numeroFactura}.`
          : `Factura guardada. Número registrado: ${savedInvoice.numeroFactura}.`;
    showAlert(confirmation, 'success');
    window.setTimeout(() => {
      notifySaving(false);
      if (modalMode && window.parent !== window) {
        window.parent.postMessage({ type: 'factura-modal-close', reload: true, message: confirmation }, window.location.origin);
      } else {
        try { sessionStorage.setItem('facturacion-confirmation', confirmation); } catch { /* Almacenamiento no disponible. */ }
        window.location.href = '/';
      }
    }, 900);
  } catch (error: unknown) {
    setFormLocked(false);
    showAlert(`No se pudo guardar la factura: ${error instanceof Error ? error.message : String(error)}`);
    guardando = false;
    notifySaving(false);
    saveButton.disabled = false;
    saveButton.textContent = modoEmisionFiscal ? 'Emitir en ARCA' : isEditing() ? 'Guardar cambios' : 'Guardar factura';
    if (!isEditing()) void updateNextInvoiceNumber(false);
  }
});

async function init(): Promise<void> {
  setFormLocked(true);
  byId('factura-form').setAttribute('aria-busy', 'true');
  if (modalMode) {
    document.body.classList.add('modal-mode');
    const cancelButton = byId<HTMLButtonElement>('btn-cancelar-factura');
    cancelButton.addEventListener('click', () => {
      if (window.parent !== window) window.parent.postMessage({ type: 'factura-modal-close', reload: false }, window.location.origin);
      else window.location.href = '/';
    });
  } else {
    byId<HTMLButtonElement>('btn-cancelar-factura').addEventListener('click', () => { window.location.href = '/'; });
  }
  try {
    if (idParam !== null && !validId) throw new Error('El identificador de factura no es valido.');
    byId<HTMLInputElement>('fechaEmision').value = today();
    await loadCatalogs();
    if (!today()) throw new Error('El backend no devolvio la fecha de facturacion.');
    byId<HTMLInputElement>('fechaEmision').value = today();
    if (idEdicion !== undefined) {
      await loadInvoiceForEdit(idEdicion);
    } else {
      await updateNextInvoiceNumber();
      lineas = [];
      renderLines();
    }
    formularioListo = true;
    setFormLocked(false);
    syncClientTaxCondition(true);
    byId<HTMLSelectElement>('clase-documento').disabled = idEdicion !== undefined;
    byId<HTMLSelectElement>('puntoVentaId').disabled = idEdicion !== undefined && origenEsX;
    byId('factura-form').setAttribute('aria-busy', 'false');
  } catch (error: unknown) {
    setFormLocked(true);
    byId('factura-form').setAttribute('aria-busy', 'false');
    showAlert(`No se pudo preparar el formulario. Verificá que el backend esté activo. Detalle: ${error instanceof Error ? error.message : String(error)}`);
    byId<HTMLButtonElement>('btn-guardar').disabled = true;
  }
}

void init();
