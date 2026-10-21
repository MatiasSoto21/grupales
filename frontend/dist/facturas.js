import { anularFactura, getFacturas, FACTURAS_API } from './api.js';
import { byId, clearAlert, currency, showAlert } from './dom.js';
const tbody = byId('facturas-tbody');
const form = byId('filtros-form');
let facturasActuales = [];
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
    for (const key of ['fechaDesde', 'fechaHasta', 'estado', 'montoMinimo']) {
        const input = byId(key);
        const value = input.value.trim();
        if (value)
            params.set(key, value);
    }
    return params.toString();
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
function renderInvoices(invoices) {
    facturasActuales = invoices;
    tbody.innerHTML = '';
    if (invoices.length === 0) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">No hay facturas que coincidan con los filtros.</td></tr>';
    }
    for (const invoice of invoices) {
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
            edit.disabled = isCancelled;
            edit.title = isCancelled ? 'No se puede editar una factura anulada' : '';
            edit.addEventListener('click', () => { window.location.href = `/factura-form.html?id=${encodeURIComponent(invoice.id)}`; });
            actions.append(edit);
            const cancel = document.createElement('button');
            cancel.className = 'btn btn-danger btn-small';
            cancel.textContent = 'Anular';
            cancel.disabled = isCancelled;
            cancel.addEventListener('click', () => { void cancelInvoice(invoice); });
            actions.append(cancel);
        }
        tbody.append(row);
    }
    byId('resultado-texto').textContent = `${invoices.length} resultado${invoices.length === 1 ? '' : 's'}`;
    byId('stat-cantidad').textContent = invoices.length.toLocaleString('es-AR');
    byId('stat-total').textContent = currency(invoices.reduce((sum, invoice) => sum + Number(invoice.importeTotal || 0), 0));
    byId('stat-anuladas').textContent = invoices.filter(invoice => String(invoice.estado).toUpperCase() === 'ANULADA').length.toLocaleString('es-AR');
}
async function loadInvoices() {
    clearAlert();
    byId('api-status').textContent = 'Consultando backend…';
    tbody.innerHTML = '<tr><td colspan="9" class="loading">Cargando facturas…</td></tr>';
    try {
        const invoices = await getFacturas(readFilters());
        renderInvoices(Array.isArray(invoices) ? invoices : []);
        byId('api-status').textContent = '● Backend conectado';
        byId('api-status').style.color = '#087443';
    }
    catch (error) {
        tbody.innerHTML = '<tr><td colspan="9" class="loading">No se pudieron cargar las facturas.</td></tr>';
        byId('resultado-texto').textContent = 'Error de conexión';
        byId('api-status').textContent = '● Backend no disponible';
        byId('api-status').style.color = '#b42318';
        showAlert(`No se pudo conectar con ${FACTURAS_API}. Verificá que Spring Boot esté corriendo y que hayas aplicado los cambios de integración. Detalle: ${error instanceof Error ? error.message : String(error)}`);
    }
}
async function cancelInvoice(invoice) {
    if (!window.confirm(`¿Anular la factura N.º ${invoice.numeroFactura}? Esta acción es lógica y no elimina el registro.`))
        return;
    try {
        await anularFactura(invoice.id);
        showAlert(`Factura ${invoice.numeroFactura} anulada correctamente.`, 'success');
        await loadInvoices();
    }
    catch (error) {
        showAlert(`No se pudo anular la factura: ${error instanceof Error ? error.message : String(error)}`);
    }
}
form.addEventListener('submit', event => { event.preventDefault(); void loadInvoices(); });
byId('btn-limpiar').addEventListener('click', () => { form.reset(); void loadInvoices(); });
for (const [selector, path] of [['a[href="/api/facturas/pdf"]', '/pdf'], ['a[href="/api/facturas/excel"]', '/excel']]) {
    const link = document.querySelector(selector);
    if (link) {
        link.href = `${FACTURAS_API}${path}`;
        link.addEventListener('click', event => {
            event.preventDefault();
            const query = readFilters();
            window.location.href = `${FACTURAS_API}${path}${query ? `?${query}` : ''}`;
        });
    }
}
void facturasActuales;
void loadInvoices();
