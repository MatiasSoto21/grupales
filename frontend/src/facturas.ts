import { ClienteExplorer } from './cliente-explorer.js';
import { anularFactura, getCatalogos, getComprobanteFacturaUrl, getFacturas, reactivarFactura, FACTURAS_API } from './api.js';
import { byId, clearAlert, currency, initTheme, initValidationFeedback, makeOption, showAlert } from './dom.js';
import type { Catalogos, FacturaResumen, PageResponse } from './types.js';

const tbody = byId<HTMLTableSectionElement>('facturas-tbody');
const form = byId<HTMLFormElement>('filtros-form');
const paginationNav = byId<HTMLElement>('pagination');
let currentPage = 0;
let totalPages = 1;
let sortBy: 'numero' | 'fechaEmision' | 'total' = 'fechaEmision';
let sortDir: 'asc' | 'desc' = 'desc';
let loadRequestId = 0;
let invoiceBusy = false;
let actionBusy = false;
const PAGE_SIZE = 10;

const clienteExplorer = new ClienteExplorer(client => {
  byId<HTMLInputElement>('clienteId').value = String(client.id);
  byId<HTMLInputElement>('cliente-nombre').value = client.denominacion;
});
byId('btn-buscar-cliente').addEventListener('click', () => clienteExplorer.open());
byId('btn-sin-cliente').addEventListener('click', () => {
  byId<HTMLInputElement>('clienteId').value = 'SIN_CLIENTE';
  byId<HTMLInputElement>('cliente-nombre').value = 'Consumidor final (sin cliente)';
});
byId('btn-todos-clientes').addEventListener('click', () => {
  byId<HTMLInputElement>('clienteId').value = '';
  byId<HTMLInputElement>('cliente-nombre').value = 'Todos los clientes';
});

initTheme();
initValidationFeedback();

function formatDate(value: string | null | undefined): string {
  if (!value) return '—';
  const [year, month, day] = String(value).slice(0, 10).split('-');
  return `${day}/${month}/${year}`;
}

function escapeHtml(value: unknown): string {
  return String(value ?? '').replace(/[&<>"']/g, (character: string) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character] ?? character);
}

function readFilters(): URLSearchParams {
  const params = new URLSearchParams();
  for (const key of ['fechaDesde', 'fechaHasta', 'estado', 'numero', 'numeroDesde', 'numeroHasta', 'puntoVentaId']) {
    const input = byId<HTMLInputElement | HTMLSelectElement>(key);
    const value = input.value.trim();
    if (value) params.set(key, value);
  }
  const clienteId = byId<HTMLInputElement>('clienteId').value;
  if (clienteId === 'SIN_CLIENTE') {
    params.set('sinCliente', 'true');
  } else if (clienteId) {
    params.set('clienteId', clienteId);
  }
  return params;
}

function validateFilters(): boolean {
  const fechaDesde = byId<HTMLInputElement>('fechaDesde').value;
  const fechaHasta = byId<HTMLInputElement>('fechaHasta').value;
  const numero = byId<HTMLInputElement>('numero').value;
  const numeroDesde = byId<HTMLInputElement>('numeroDesde').value;
  const numeroHasta = byId<HTMLInputElement>('numeroHasta').value;

  for (const value of [numero, numeroDesde, numeroHasta]) {
    if (value && (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)))) {
      showAlert('Los números de comprobante deben ser enteros no negativos.');
      return false;
    }
  }

  if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
    showAlert('La fecha "desde" no puede ser posterior a la fecha "hasta".');
    return false;
  }
  if (numeroDesde && numeroHasta && Number(numeroDesde) > Number(numeroHasta)) {
    showAlert('El número de comprobante "desde" no puede ser mayor que "hasta".');
    return false;
  }
  if (numero && ((numeroDesde && Number(numero) < Number(numeroDesde))
      || (numeroHasta && Number(numero) > Number(numeroHasta)))) {
    showAlert('El número exacto debe estar dentro del rango indicado.');
    return false;
  }
  return true;
}

async function loadClientFilter(): Promise<void> {
  const catalogos: Catalogos = await getCatalogos();
  if (catalogos.estadosFactura) {
    const stateSelect = byId<HTMLSelectElement>('estado');
    stateSelect.replaceChildren(makeOption('', 'Todos los estados'));
    for (const state of catalogos.estadosFactura) stateSelect.append(makeOption(state, state));
  }
  const points = byId<HTMLSelectElement>('puntoVentaId');
  for (const point of catalogos.puntosVenta) points.append(makeOption(point.id, `${String(point.numero ?? '').padStart(5, '0')} · ${point.descripcion ?? 'Punto de venta'}`));
  clienteExplorer.setTaxes(catalogos.condicionesIva);
}

function openInvoiceModal(id?: number): void {
  if (actionBusy || invoiceBusy) return;
  const modal = byId<HTMLDialogElement>('factura-modal');
  byId('factura-modal-title').textContent = id ? 'Editar comprobante' : 'Nuevo comprobante';
  const frame = byId<HTMLIFrameElement>('factura-modal-frame');
  frame.src = id ? `/factura-form.html?modal=1&id=${encodeURIComponent(id)}` : '/factura-form.html?modal=1';
  modal.showModal();
}

function closeInvoiceModal(reload = false, message?: string): void {
  if (invoiceBusy) return;
  const modal = byId<HTMLDialogElement>('factura-modal');
  modal.close();
  byId<HTMLIFrameElement>('factura-modal-frame').src = 'about:blank';
  if (reload) void reloadWithConfirmation(message);
}

function confirmAction(message: string, confirmLabel = 'Confirmar'): Promise<boolean> {
  const modal = byId<HTMLDialogElement>('confirm-modal');
  byId<HTMLElement>('confirm-modal-message').textContent = message;
  const ok = byId<HTMLButtonElement>('confirm-modal-ok');
  ok.textContent = confirmLabel;
  ok.className = `btn ${confirmLabel === 'Reactivar' ? 'btn-success' : 'btn-danger'}`;
  return new Promise(resolve => {
    const finish = (value: boolean): void => {
      ok.removeEventListener('click', onOk);
      cancel.removeEventListener('click', onCancel);
      modal.removeEventListener('cancel', onEscape);
      modal.close();
      resolve(value);
    };
    const cancel = byId<HTMLButtonElement>('confirm-modal-cancel');
    const onOk = (): void => finish(true);
    const onCancel = (): void => finish(false);
    const onEscape = (event: Event): void => { event.preventDefault(); finish(false); };
    modal.addEventListener('cancel', onEscape);
    ok.addEventListener('click', onOk);
    cancel.addEventListener('click', onCancel);
    modal.showModal();
    cancel.focus();
  });
}

function statusPill(status: string | null | undefined): string {
  const value = String(status || 'SIN ESTADO').toUpperCase();
  let className = 'pill';
  if (['ANULADA', 'RECHAZADA', 'ERROR_AFIP'].includes(value)) className += ' pill-anulada';
  else if (value === 'PAGADA') className += ' pill-pagada';
  else if (value === 'PENDIENTE') className += ' pill-pendiente';
  return `<span class="${className}">${escapeHtml(value)}</span>`;
}

function renderInvoices(pageResponse: PageResponse<FacturaResumen>): void {
  currentPage = pageResponse.number;
  totalPages = pageResponse.totalPages;

  tbody.innerHTML = '';
  if (pageResponse.content.length === 0) {
    tbody.innerHTML = '<tr><td colspan="9" class="loading">No hay facturas que coincidan con los filtros.</td></tr>';
  }

  for (const invoice of pageResponse.content) {
    const row = document.createElement('tr');
    const shortNumber = invoice.numeroFactura == null ? `Intento #${invoice.id}` : `${invoice.comprobanteInterno ? 'X-' : ''}${invoice.numeroFactura}`;
    const reference = invoice.referenciaListado ?? shortNumber;
    row.innerHTML = `<td class="document-reference"><strong title="${escapeHtml(invoiceReference(invoice))}">${escapeHtml(reference)}</strong></td>
      <td>${formatDate(invoice.fechaEmision)}</td>
      <td>${escapeHtml(invoice.clienteDenominacion || 'Consumidor final')}</td>
      <td>${escapeHtml(invoice.condicionIva || '—')}</td>
      <td>${escapeHtml(invoice.puntoVentaDescripcion || '—')}</td>
      <td>${statusPill(invoice.estado)}${invoice.receptorPrueba ? '<div class="muted">Receptor historico sin identificar</div>' : ''}</td>
      <td class="text-right"><strong>${currency(invoice.importeTotal)}</strong></td>
      <td class="text-center">${Number(invoice.cantidadItems ?? 0)}</td>
      <td><div class="actions"></div></td>`;
    const actions = row.querySelector<HTMLDivElement>('.actions');
    if (actions && invoice.id !== undefined && invoice.id !== null) {
      const isCancelled = String(invoice.estado).toUpperCase() === 'ANULADA';
      const receipt = document.createElement('a');
      receipt.className = 'btn btn-secondary btn-small';
      receipt.textContent = 'Ver';
      receipt.href = getComprobanteFacturaUrl(Number(invoice.id));
      receipt.target = '_blank';
      receipt.rel = 'noopener';
      receipt.title = `Ver ${invoiceReference(invoice)}`;
      actions.append(receipt);
      if (invoice.motivoRechazo) {
        const reason = document.createElement('button');
        reason.className = 'btn btn-secondary btn-small';
        reason.textContent = 'Motivo ARCA';
        reason.addEventListener('click', () => {
          byId<HTMLElement>('arca-result-message').textContent = invoice.motivoRechazo ?? '';
          byId<HTMLDialogElement>('arca-result-modal').showModal();
        });
        actions.append(reason);
      }

      if (!['AUTORIZADA', 'ERROR_AFIP', 'ANULADA'].includes(invoice.estado) && !invoice.tieneCae && !invoice.comprobanteDisponible && invoice.editable !== false) {
        const edit = document.createElement('button');
        edit.className = 'btn btn-secondary btn-small';
        edit.textContent = 'Editar';
        edit.addEventListener('click', () => { openInvoiceModal(Number(invoice.id)); });
        actions.append(edit);
      }

      if (isCancelled) {
        const reactivate = document.createElement('button');
        reactivate.className = 'btn btn-success btn-small';
        reactivate.textContent = 'Reactivar';
        reactivate.addEventListener('click', () => { void reactivateInvoice(invoice); });
        actions.append(reactivate);
      } else {
        const cancel = document.createElement('button');
        cancel.className = 'btn btn-danger btn-small';
        cancel.textContent = 'Anular';
        cancel.addEventListener('click', () => { void cancelInvoice(invoice); });
        actions.append(cancel);
      }
    }
    tbody.append(row);
  }

  byId<HTMLSpanElement>('resultado-texto').textContent = `${pageResponse.totalElements} resultado${pageResponse.totalElements === 1 ? '' : 's'} (página ${totalPages ? currentPage + 1 : 0} de ${totalPages})`;
  byId<HTMLElement>('stat-cantidad').textContent = pageResponse.totalElements.toLocaleString('es-AR');
  byId<HTMLElement>('stat-total').textContent = currency(pageResponse.content.reduce((sum, invoice) => sum + Number(invoice.importeTotal || 0), 0));

  renderPagination();
}

function renderPagination(): void {
  if (!paginationNav) return;

  if (totalPages <= 1) {
    paginationNav.innerHTML = '';
    return;
  }

  const maxVisiblePages = 5;
  let startPage = Math.max(0, currentPage - Math.floor(maxVisiblePages / 2));
  let endPage = Math.min(totalPages - 1, startPage + maxVisiblePages - 1);

  if (endPage - startPage + 1 < maxVisiblePages) {
    startPage = Math.max(0, endPage - maxVisiblePages + 1);
  }

  let html = '<div class="pagination">';

  html += `<button class="btn btn-secondary btn-small" ${currentPage === 0 ? 'disabled' : ''} data-page="${currentPage - 1}">« Anterior</button>`;

  if (startPage > 0) {
    html += `<button class="btn btn-secondary btn-small" data-page="0">1</button>`;
    if (startPage > 1) {
      html += `<span class="pagination-ellipsis">…</span>`;
    }
  }

  for (let i = startPage; i <= endPage; i++) {
    const isActive = i === currentPage;
    html += `<button class="btn btn-small ${isActive ? 'btn-primary' : 'btn-secondary'}" data-page="${i}" ${isActive ? 'disabled aria-current="page"' : ''}>${i + 1}</button>`;
  }

  if (endPage < totalPages - 1) {
    if (endPage < totalPages - 2) {
      html += `<span class="pagination-ellipsis">…</span>`;
    }
    html += `<button class="btn btn-secondary btn-small" data-page="${totalPages - 1}">${totalPages}</button>`;
  }

  html += `<button class="btn btn-secondary btn-small" ${currentPage >= totalPages - 1 ? 'disabled' : ''} data-page="${currentPage + 1}">Siguiente »</button>`;

  html += '</div>';
  paginationNav.innerHTML = html;

  paginationNav.querySelectorAll<HTMLButtonElement>('button[data-page]').forEach(btn => {
    btn.addEventListener('click', () => {
      const page = parseInt(btn.dataset.page || '0', 10);
      if (page >= 0 && page < totalPages && page !== currentPage) {
        currentPage = page;
        void loadInvoices(true);
      }
    });
  });
}

function sortLabel(): string {
  const field = sortBy === 'numero' ? 'Número' : sortBy === 'total' ? 'Total' : 'Fecha';
  return `Ordenar: ${field} ${sortDir === 'asc' ? '↑' : '↓'}`;
}

function setupSorting(): void {
  const modal = byId<HTMLDialogElement>('sort-modal');
  const field = byId<HTMLSelectElement>('sort-field');
  const direction = byId<HTMLSelectElement>('sort-direction');
  const button = byId<HTMLButtonElement>('btn-abrir-orden');

  button.textContent = sortLabel();
  button.addEventListener('click', () => {
    field.value = sortBy;
    direction.value = sortDir;
    modal.showModal();
  });
  byId<HTMLButtonElement>('btn-cerrar-sort-modal').addEventListener('click', () => modal.close());
  byId<HTMLButtonElement>('btn-cancelar-sort').addEventListener('click', () => modal.close());
  modal.addEventListener('click', event => {
    if (event.target === modal) modal.close();
  });
  byId<HTMLFormElement>('sort-form').addEventListener('submit', event => {
    event.preventDefault();
    sortBy = field.value as 'numero' | 'fechaEmision' | 'total';
    sortDir = direction.value as 'asc' | 'desc';
    currentPage = 0;
    button.textContent = sortLabel();
    modal.close();
    void loadInvoices(true);
  });
}

async function loadInvoices(preserveScrollPosition = false): Promise<boolean> {
  const scrollY = window.scrollY;
  const requestId = ++loadRequestId;
  clearAlert();
  if (!validateFilters()) return false;
  byId<HTMLElement>('api-status').textContent = 'Consultando backend…';
  if (!preserveScrollPosition) {
    tbody.innerHTML = '<tr><td colspan="9" class="loading">Cargando facturas…</td></tr>';
  }
  try {
    const baseParams = readFilters();
    const options = { page: currentPage, size: PAGE_SIZE, sortBy, sortDir };
    const pageResponse = await getFacturas(baseParams.toString(), options);
    if (requestId !== loadRequestId) return false;
    if (pageResponse.number >= pageResponse.totalPages && pageResponse.number > 0) {
      currentPage = Math.max(0, pageResponse.totalPages - 1);
      return await loadInvoices(preserveScrollPosition);
    }
    renderInvoices(pageResponse);
    byId<HTMLElement>('api-status').textContent = '● Backend conectado';
    byId<HTMLElement>('api-status').style.color = 'var(--success)';
    if (preserveScrollPosition) {
      window.scrollTo(0, scrollY);
    }
    return true;
  } catch (error: unknown) {
    if (requestId !== loadRequestId) return false;
    tbody.innerHTML = '<tr><td colspan="9" class="loading">No se pudieron cargar las facturas.</td></tr>';
    paginationNav.replaceChildren();
    byId<HTMLElement>('stat-cantidad').textContent = '\u2014';
    byId<HTMLElement>('stat-total').textContent = '\u2014';
    byId<HTMLElement>('resultado-texto').textContent = 'Error de conexión';
    byId<HTMLElement>('api-status').textContent = '● Backend no disponible';
    byId<HTMLElement>('api-status').style.color = 'var(--danger)';
    showAlert(`No se pudo conectar con ${FACTURAS_API}. Verificá que Spring Boot esté corriendo y que hayas aplicado los cambios de integración. Detalle: ${error instanceof Error ? error.message : String(error)}`);
    return false;
  }
}

function invoiceReference(invoice: FacturaResumen): string {
  if (invoice.referenciaCompleta) return invoice.referenciaCompleta;
  const number = invoice.numeroFactura == null ? `intento #${invoice.id}` : `${invoice.comprobanteInterno ? 'X-' : ''}${invoice.puntoVentaNumero ? `${String(invoice.puntoVentaNumero).padStart(5, '0')}-` : ''}${invoice.numeroFactura}`;
  return `${number}${invoice.puntoVentaDescripcion ? ` (${invoice.puntoVentaDescripcion})` : ''}`;
}

async function reloadWithConfirmation(message?: string): Promise<void> {
  const loaded = await loadInvoices();
  if (!message) return;
  if (loaded) showAlert(message, 'success');
  else {
    const detail = byId('alerta').textContent ?? '';
    showAlert(`${message} No se pudo actualizar el listado. ${detail}`);
  }
}

async function changeInvoiceState(invoice: FacturaResumen, reactivate: boolean): Promise<void> {
  if (actionBusy) return;
  actionBusy = true;
  let controls: Array<[HTMLButtonElement, boolean]> = [];
  try {
    const question = reactivate
      ? `¿Reactivar el comprobante ${invoiceReference(invoice)}? Se recuperara el estado anterior a la anulacion, conservando el resultado de ARCA.`
      : `¿Anular el comprobante ${invoiceReference(invoice)}? Es una baja local; no elimina el registro ni revoca un CAE en ARCA.`;
    if (!await confirmAction(question, reactivate ? 'Reactivar' : 'Anular')) return;
    controls = [...tbody.querySelectorAll<HTMLButtonElement>('button'), byId<HTMLButtonElement>('btn-nueva-factura')]
      .map(control => [control, control.disabled]);
    controls.forEach(([control]) => { control.disabled = true; });
    if (reactivate) await reactivarFactura(invoice.id);
    else await anularFactura(invoice.id);
    currentPage = 0;
    await reloadWithConfirmation(`Factura ${invoiceReference(invoice)} ${reactivate ? 'reactivada' : 'anulada'} correctamente.`);
  } catch (error: unknown) {
    showAlert(`No se pudo ${reactivate ? 'reactivar' : 'anular'} la factura: ${error instanceof Error ? error.message : String(error)}`);
  } finally {
    controls.forEach(([control, disabled]) => { control.disabled = disabled; });
    actionBusy = false;
  }
}

async function cancelInvoice(invoice: FacturaResumen): Promise<void> { await changeInvoiceState(invoice, false); }
async function reactivateInvoice(invoice: FacturaResumen): Promise<void> { await changeInvoiceState(invoice, true); }

form.addEventListener('submit', event => {
  event.preventDefault();
  if (!validateFilters()) return;
  currentPage = 0;
  void loadInvoices();
});
byId<HTMLButtonElement>('btn-limpiar').addEventListener('click', () => {
  form.reset();
  byId<HTMLInputElement>('clienteId').value = '';
  currentPage = 0;
  void loadInvoices();
});
byId<HTMLButtonElement>('btn-nueva-factura').addEventListener('click', () => openInvoiceModal());
byId<HTMLButtonElement>('btn-cerrar-factura-modal').addEventListener('click', () => closeInvoiceModal());
byId<HTMLDialogElement>('factura-modal').addEventListener('cancel', event => {
  if (invoiceBusy) event.preventDefault();
  else byId<HTMLIFrameElement>('factura-modal-frame').src = 'about:blank';
});
byId<HTMLDialogElement>('factura-modal').addEventListener('click', event => {
  if (event.target === event.currentTarget) closeInvoiceModal();
});
window.addEventListener('message', event => {
  if (event.origin !== window.location.origin) return;
  if (event.source !== byId<HTMLIFrameElement>('factura-modal-frame').contentWindow) return;
  if (event.data?.type === 'factura-modal-busy') {
    invoiceBusy = Boolean(event.data.busy);
    byId<HTMLButtonElement>('btn-cerrar-factura-modal').disabled = invoiceBusy;
  }
  if (event.data?.type === 'factura-modal-reload') void loadInvoices();
  if (event.data?.type === 'factura-modal-close') closeInvoiceModal(Boolean(event.data.reload), typeof event.data.message === 'string' ? event.data.message : undefined);
});

for (const [selector, path] of [['a[href="/api/facturas/pdf"]', '/pdf'], ['a[href="/api/facturas/excel"]', '/excel']] as const) {
  const link = document.querySelector<HTMLAnchorElement>(selector);
  if (link) {
    link.href = `${FACTURAS_API}${path}`;
    link.addEventListener('click', event => {
      event.preventDefault();
      if (!validateFilters()) return;
      const query = readFilters();
      query.set('sortBy', sortBy);
      query.set('sortDir', sortDir);
      window.location.href = `${FACTURAS_API}${path}${query ? `?${query}` : ''}`;
    });
  }
}

setupSorting();
let initialConfirmation: string | undefined;
try {
  initialConfirmation = sessionStorage.getItem('facturacion-confirmation') ?? undefined;
  sessionStorage.removeItem('facturacion-confirmation');
} catch { /* Almacenamiento no disponible. */ }
void reloadWithConfirmation(initialConfirmation);
void loadClientFilter().catch((error: unknown) => {
  byId<HTMLElement>('api-status').textContent = '● No se cargaron clientes';
  byId<HTMLElement>('api-status').style.color = 'var(--danger)';
  showAlert(`No se pudieron cargar los catálogos de filtros: ${error instanceof Error ? error.message : String(error)}`);
});