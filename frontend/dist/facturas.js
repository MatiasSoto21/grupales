import { anularFactura, getCatalogos, getFacturas, reactivarFactura, FACTURAS_API } from './api.js';
import { byId, clearAlert, currency, makeOption, showAlert } from './dom.js';
const tbody = byId('facturas-tbody');
const form = byId('filtros-form');
const paginationNav = byId('pagination');
let currentPage = 0;
let totalPages = 1;
const PAGE_SIZE = 10;
function formatDate(value) {
    if (!value)
        return '—';
    const [year, month, day] = String(value).slice(0, 10).split('-');
    return `${day}/${month}/${year}`;
}
function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, (character) => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    })[character] ?? character);
}
function readFilters() {
    const params = new URLSearchParams();
    for (const key of ['fechaDesde', 'fechaHasta', 'estado', 'montoMinimo', 'numero', 'numeroDesde', 'numeroHasta']) {
        const input = byId(key);
        const value = input.value.trim();
        if (value)
            params.set(key, value);
    }
    const clienteId = byId('clienteId').value;
    if (clienteId === 'SIN_CLIENTE') {
        params.set('sinCliente', 'true');
    }
    else if (clienteId) {
        params.set('clienteId', clienteId);
    }
    return params;
}
function validateFilters() {
    const fechaDesde = byId('fechaDesde').value;
    const fechaHasta = byId('fechaHasta').value;
    const numero = byId('numero').value;
    const numeroDesde = byId('numeroDesde').value;
    const numeroHasta = byId('numeroHasta').value;
    const montoMinimo = byId('montoMinimo').value;
    for (const value of [numero, numeroDesde, numeroHasta]) {
        if (value && (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)))) {
            showAlert('Los números de comprobante deben ser enteros no negativos.');
            return false;
        }
    }
    if (montoMinimo && (!Number.isFinite(Number(montoMinimo)) || Number(montoMinimo) < 0)) {
        showAlert('El importe mínimo debe ser un número igual o mayor que cero.');
        return false;
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
async function loadClientFilter() {
    const catalogos = await getCatalogos();
    const clientSelect = byId('clienteId');
    clientSelect.replaceChildren(makeOption('', 'Todos los clientes'));
    for (const client of catalogos.clientes) {
        clientSelect.append(makeOption(client.id, client.denominacion));
    }
    clientSelect.append(makeOption('SIN_CLIENTE', 'Consumidor final (sin cliente)'));
}
function statusPill(status) {
    const value = String(status || 'SIN ESTADO').toUpperCase();
    let className = 'pill';
    if (value === 'ANULADA')
        className += ' pill-anulada';
    else if (value === 'PAGADA')
        className += ' pill-pagada';
    return `<span class="${className}">${escapeHtml(value)}</span>`;
}
function renderInvoices(pageResponse) {
    currentPage = pageResponse.number;
    totalPages = pageResponse.totalPages;
    tbody.innerHTML = '';
    if (pageResponse.content.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">No hay facturas que coincidan con los filtros.</td></tr>';
    }
    for (const invoice of pageResponse.content) {
        const row = document.createElement('tr');
        row.innerHTML = `<td><strong>${escapeHtml(invoice.numeroFactura ?? '—')}</strong></td>
      <td>${formatDate(invoice.fechaEmision)}</td>
      <td>${escapeHtml(invoice.clienteDenominacion || 'Consumidor final')}</td>
      <td>${escapeHtml(invoice.condicionIva || '—')}</td>
      <td>${escapeHtml(invoice.puntoVentaDescripcion || '—')}</td>
      <td>${statusPill(invoice.estado)}</td>
      <td class="text-right"><strong>${currency(invoice.importeTotal)}</strong></td>
      <td class="text-center">${Number(invoice.cantidadItems ?? 0)}</td>
      <td><div class="actions"></div></td>`;
        const actions = row.querySelector('.actions');
        if (actions && invoice.id !== undefined && invoice.id !== null) {
            const isCancelled = String(invoice.estado).toUpperCase() === 'ANULADA';
            const edit = document.createElement('button');
            edit.className = 'btn btn-secondary btn-small';
            edit.textContent = 'Editar';
            edit.addEventListener('click', () => { window.location.href = `/factura-form.html?id=${encodeURIComponent(invoice.id)}`; });
            actions.append(edit);
            if (isCancelled) {
                const reactivate = document.createElement('button');
                reactivate.className = 'btn btn-success btn-small';
                reactivate.textContent = 'Reactivar';
                reactivate.addEventListener('click', () => { void reactivateInvoice(invoice); });
                actions.append(reactivate);
            }
            else {
                const cancel = document.createElement('button');
                cancel.className = 'btn btn-danger btn-small';
                cancel.textContent = 'Anular';
                cancel.addEventListener('click', () => { void cancelInvoice(invoice); });
                actions.append(cancel);
            }
        }
        tbody.append(row);
    }
    byId('resultado-texto').textContent = `${pageResponse.totalElements} resultado${pageResponse.totalElements === 1 ? '' : 's'} (página ${currentPage + 1} de ${totalPages})`;
    byId('stat-cantidad').textContent = pageResponse.totalElements.toLocaleString('es-AR');
    byId('stat-total').textContent = currency(pageResponse.content.reduce((sum, invoice) => sum + Number(invoice.importeTotal || 0), 0));
    renderPagination();
}
function renderPagination() {
    if (!paginationNav)
        return;
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
    paginationNav.querySelectorAll('button[data-page]').forEach(btn => {
        btn.addEventListener('click', () => {
            const page = parseInt(btn.dataset.page || '0', 10);
            if (page >= 0 && page < totalPages && page !== currentPage) {
                currentPage = page;
                void loadInvoices(true);
            }
        });
    });
}
async function loadInvoices(preserveScrollPosition = false) {
    const scrollY = window.scrollY;
    clearAlert();
    byId('api-status').textContent = 'Consultando backend…';
    if (!preserveScrollPosition) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">Cargando facturas…</td></tr>';
    }
    try {
        const baseParams = readFilters();
        const options = { page: currentPage, size: PAGE_SIZE };
        const pageResponse = await getFacturas(baseParams.toString(), options);
        renderInvoices(pageResponse);
        byId('api-status').textContent = '● Backend conectado';
        byId('api-status').style.color = '#087443';
        if (preserveScrollPosition) {
            window.scrollTo(0, scrollY);
        }
    }
    catch (error) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">No se pudieron cargar las facturas.</td></tr>';
        byId('resultado-texto').textContent = 'Error de conexión';
        byId('api-status').textContent = '● Backend no disponible';
        byId('api-status').style.color = '#b42318';
        showAlert(`No se pudo conectar con ${FACTURAS_API}. Verificá que Spring Boot esté corriendo y que hayas aplicado los cambios de integración. Detalle: ${error instanceof Error ? error.message : String(error)}`);
        if (preserveScrollPosition) {
            window.scrollTo(0, scrollY);
        }
    }
}
async function cancelInvoice(invoice) {
    if (!window.confirm(`¿Anular la factura N.º ${invoice.numeroFactura}? Esta acción es lógica y no elimina el registro.`))
        return;
    try {
        await anularFactura(invoice.id);
        showAlert(`Factura ${invoice.numeroFactura} anulada correctamente.`, 'success');
        currentPage = 0;
        await loadInvoices();
    }
    catch (error) {
        showAlert(`No se pudo anular la factura: ${error instanceof Error ? error.message : String(error)}`);
    }
}
async function reactivateInvoice(invoice) {
    if (!window.confirm(`¿Reactivar la factura N.º ${invoice.numeroFactura}? Su estado volverá a EMITIDA.`))
        return;
    try {
        await reactivarFactura(invoice.id);
        showAlert(`Factura ${invoice.numeroFactura} reactivada correctamente.`, 'success');
        currentPage = 0;
        await loadInvoices();
    }
    catch (error) {
        showAlert(`No se pudo reactivar la factura: ${error instanceof Error ? error.message : String(error)}`);
    }
}
form.addEventListener('submit', event => {
    event.preventDefault();
    if (!validateFilters())
        return;
    currentPage = 0;
    void loadInvoices();
});
byId('btn-limpiar').addEventListener('click', () => {
    form.reset();
    currentPage = 0;
    void loadInvoices();
});
for (const [selector, path] of [['a[href="/api/facturas/pdf"]', '/pdf'], ['a[href="/api/facturas/excel"]', '/excel']]) {
    const link = document.querySelector(selector);
    if (link) {
        link.href = `${FACTURAS_API}${path}`;
        link.addEventListener('click', event => {
            event.preventDefault();
            if (!validateFilters())
                return;
            const query = readFilters();
            window.location.href = `${FACTURAS_API}${path}${query ? `?${query}` : ''}`;
        });
    }
}
void loadInvoices();
void loadClientFilter().catch((error) => {
    byId('api-status').textContent = '● No se cargaron clientes';
    byId('api-status').style.color = '#b42318';
    showAlert(`No se pudieron cargar los clientes para filtrar: ${error instanceof Error ? error.message : String(error)}`);
});
