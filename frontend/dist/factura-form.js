import { crearCliente, getAllFacturas, getCatalogos, getFactura, guardarFactura } from './api.js';
import { byId, clearAlert, currency, makeOption, showAlert } from './dom.js';
const API_URL = 'http://localhost:8080/api/facturas';
const params = new URLSearchParams(window.location.search);
const idParam = params.get('id');
const idEdicion = idParam && Number.isInteger(Number(idParam)) ? Number(idParam) : undefined;
let catalogos = null;
let lineas = [];
let estadoActual = 'EMITIDA';
let guardando = false;
let creandoCliente = false;
function articleById(id) {
    return (catalogos?.articulos ?? []).find(article => String(article.id) === String(id));
}
function createLine(detail = {}) {
    return {
        articuloId: detail.articuloId ?? undefined,
        descripcion: detail.descripcion ?? '',
        cantidad: Number(detail.cantidad ?? 1),
        porcentajeBonificacion: Number(detail.porcentajeBonificacion ?? 0)
    };
}
async function loadCatalogs() {
    catalogos = await getCatalogos();
    const clientSelect = byId('clienteId');
    clientSelect.replaceChildren(makeOption('', 'Consumidor final'));
    for (const client of catalogos.clientes ?? [])
        clientSelect.append(makeOption(client.id, client.denominacion));
    const taxSelect = byId('condicionIvaId');
    taxSelect.replaceChildren(makeOption('', 'Seleccioná una condición'));
    for (const tax of catalogos.condicionesIva ?? [])
        taxSelect.append(makeOption(tax.id, tax.denominacion));
    const pointSelect = byId('puntoVentaId');
    pointSelect.replaceChildren(makeOption('', 'Seleccioná un punto de venta'));
    for (const point of catalogos.puntosVenta ?? []) {
        pointSelect.append(makeOption(point.id, `${point.numero ? `${point.numero} · ` : ''}${point.descripcion || 'Punto de venta'}`));
    }
    const currencySelect = byId('tipoMonedaId');
    currencySelect.replaceChildren(makeOption('', 'Seleccioná una moneda'));
    for (const money of catalogos.tiposMoneda ?? []) {
        currencySelect.append(makeOption(money.id, `${money.simbolo ?? ''} ${money.denominacion}`.trim()));
    }
}
function closeClientPanel() {
    const panel = byId('nuevo-cliente-panel');
    panel.disabled = true;
    panel.classList.add('hidden');
    byId('btn-guardar').disabled = false;
    panel.querySelectorAll('input').forEach(input => { input.value = ''; });
}
async function saveNewClient() {
    if (creandoCliente)
        return;
    const cuitCuil = byId('nuevo-cliente-cuit').value.trim();
    const denominacion = byId('nuevo-cliente-denominacion').value.trim();
    if (!cuitCuil || !denominacion) {
        showAlert('CUIT/CUIL y denominación son obligatorios para crear el cliente.');
        return;
    }
    creandoCliente = true;
    const saveButton = byId('btn-guardar-cliente');
    const cancelButton = byId('btn-cancelar-cliente');
    saveButton.disabled = true;
    saveButton.textContent = 'Guardando…';
    cancelButton.disabled = true;
    clearAlert();
    try {
        const cliente = await crearCliente({
            cuitCuil,
            denominacion,
            email: byId('nuevo-cliente-email').value.trim(),
            telefono: byId('nuevo-cliente-telefono').value.trim(),
            celular: byId('nuevo-cliente-celular').value.trim(),
            nombreCalle: byId('nuevo-cliente-calle').value.trim(),
            numeroCalle: byId('nuevo-cliente-numero-calle').value.trim()
        });
        const clientSelect = byId('clienteId');
        clientSelect.append(makeOption(cliente.id, cliente.denominacion));
        clientSelect.value = String(cliente.id);
        closeClientPanel();
        showAlert(`Cliente "${cliente.denominacion}" creado y seleccionado para la factura.`, 'success');
    }
    catch (error) {
        showAlert(`No se pudo crear el cliente: ${error instanceof Error ? error.message : String(error)}`);
    }
    finally {
        creandoCliente = false;
        saveButton.disabled = false;
        saveButton.textContent = 'Guardar cliente';
        cancelButton.disabled = false;
    }
}
function calculateSubtotal(line) {
    const price = Number(articleById(line.articuloId)?.precioVenta ?? 0);
    return price * Number(line.cantidad || 0) * (1 - Number(line.porcentajeBonificacion || 0) / 100);
}
function updateTotals() {
    const net = lineas.reduce((sum, line) => sum + calculateSubtotal(line), 0);
    const iva = net * 0.21;
    byId('neto-estimado').textContent = currency(net);
    byId('iva-estimado').textContent = currency(iva);
    byId('total-estimado').textContent = currency(net + iva);
    byId('detalles-tbody').querySelectorAll('tr').forEach((row, index) => {
        const line = lineas[index];
        if (!line)
            return;
        if (row.cells[3])
            row.cells[3].textContent = currency(articleById(line.articuloId)?.precioVenta ?? 0);
        if (row.cells[4])
            row.cells[4].textContent = currency(calculateSubtotal(line));
    });
}
function renderLines() {
    const tableBody = byId('detalles-tbody');
    tableBody.innerHTML = '';
    if (!catalogos)
        return;
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
        quantity.type = 'number';
        quantity.min = '1';
        quantity.step = '1';
        quantity.required = true;
        quantity.value = String(line.cantidad);
        quantity.addEventListener('input', () => { line.cantidad = Number(quantity.value) || 0; updateTotals(); });
        quantityCell.append(quantity);
        const discountCell = document.createElement('td');
        const discount = document.createElement('input');
        discount.className = 'discount-input';
        discount.type = 'number';
        discount.min = '0';
        discount.max = '100';
        discount.step = '0.01';
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
        removeButton.type = 'button';
        removeButton.className = 'btn btn-danger btn-small';
        removeButton.textContent = 'Quitar';
        removeButton.addEventListener('click', () => { lineas.splice(index, 1); renderLines(); });
        actionsCell.append(removeButton);
        row.append(articleCell, quantityCell, discountCell, priceCell, subtotalCell, actionsCell);
        tableBody.append(row);
    });
    updateTotals();
}
function selectValue(id, value) {
    if (value !== null && value !== undefined)
        byId(id).value = String(value);
}
async function loadInvoiceForEdit(id) {
    const invoice = await getFactura(id);
    byId('titulo').textContent = 'Editar factura';
    byId('modo-label').textContent = 'Edición';
    byId('estado-badge').textContent = invoice.estado || 'EMITIDA';
    byId('numero').value = invoice.numero == null ? '' : String(invoice.numero);
    byId('fechaEmision').value = invoice.fechaEmision || '';
    selectValue('puntoVentaId', invoice.puntoVentaId);
    selectValue('clienteId', invoice.clienteId);
    selectValue('condicionIvaId', invoice.condicionIvaId);
    selectValue('tipoMonedaId', invoice.tipoMonedaId);
    byId('importeCobrado').value = String(invoice.importeCobrado ?? 0);
    byId('observaciones').value = invoice.observaciones ?? '';
    estadoActual = invoice.estado || 'EMITIDA';
    lineas = (invoice.detalles ?? []).map(detail => createLine(detail));
    renderLines();
    if (estadoActual.toUpperCase() === 'ANULADA') {
        const reactivationField = byId('reactivacion-field');
        const reactivationStatus = byId('estado-reactivacion');
        reactivationField.classList.remove('hidden');
        reactivationStatus.required = true;
        showAlert('La factura está anulada. Podés editarla y elegir un estado para reactivarla.');
    }
}
function readForm() {
    const numberValue = Number(byId('numero').value);
    const details = lineas.map(line => ({
        articuloId: Number(line.articuloId),
        descripcion: line.descripcion || articleById(line.articuloId)?.denominacion || '',
        cantidad: Number(line.cantidad),
        porcentajeBonificacion: Number(line.porcentajeBonificacion || 0)
    }));
    return {
        numero: Number.isFinite(numberValue) && byId('numero').value.trim() !== '' ? numberValue : null,
        fechaEmision: byId('fechaEmision').value,
        clienteId: byId('clienteId').value ? Number(byId('clienteId').value) : null,
        condicionIvaId: Number(byId('condicionIvaId').value),
        tipoMonedaId: Number(byId('tipoMonedaId').value),
        puntoVentaId: Number(byId('puntoVentaId').value),
        importeCobrado: Number(byId('importeCobrado').value || 0),
        importeSaldo: 0,
        estado: estadoActual.toUpperCase() === 'ANULADA'
            ? byId('estado-reactivacion').value
            : estadoActual,
        observaciones: byId('observaciones').value.trim(),
        detalles: details
    };
}
byId('btn-agregar').addEventListener('click', () => { lineas.push(createLine()); renderLines(); });
byId('btn-mostrar-cliente').addEventListener('click', () => {
    clearAlert();
    byId('nuevo-cliente-panel').disabled = false;
    byId('nuevo-cliente-panel').classList.remove('hidden');
    byId('btn-guardar').disabled = true;
    byId('nuevo-cliente-cuit').focus();
});
byId('btn-cancelar-cliente').addEventListener('click', closeClientPanel);
byId('btn-guardar-cliente').addEventListener('click', () => { void saveNewClient(); });
byId('factura-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    clearAlert();
    if (!byId('nuevo-cliente-panel').disabled)
        return;
    if (guardando)
        return;
    const body = readForm();
    if (estadoActual.toUpperCase() === 'ANULADA' && !body.estado) {
        showAlert('Elegí el nuevo estado para reactivar la factura.');
        return;
    }
    if (body.detalles.length === 0) {
        showAlert('Agregá al menos un producto.');
        return;
    }
    if (body.detalles.some(detail => !detail.articuloId || !Number.isInteger(detail.cantidad) || detail.cantidad < 1)) {
        showAlert('Revisá los productos: todos deben tener artículo y una cantidad entera mayor a cero.');
        return;
    }
    if (body.detalles.some(detail => detail.porcentajeBonificacion < 0 || detail.porcentajeBonificacion > 100)) {
        showAlert('La bonificación debe estar entre 0 y 100%.');
        return;
    }
    if (body.importeCobrado < 0) {
        showAlert('El importe cobrado no puede ser negativo.');
        return;
    }
    guardando = true;
    const saveButton = byId('btn-guardar');
    saveButton.disabled = true;
    saveButton.textContent = 'Guardando…';
    try {
        await guardarFactura(body, idEdicion);
        showAlert(idEdicion ? 'Factura actualizada correctamente.' : 'Factura creada correctamente.', 'success');
        window.setTimeout(() => { window.location.href = '/'; }, 900);
    }
    catch (error) {
        showAlert(`No se pudo guardar la factura: ${error instanceof Error ? error.message : String(error)}`);
        guardando = false;
        saveButton.disabled = false;
        saveButton.textContent = 'Guardar factura';
    }
});
async function init() {
    try {
        const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
        byId('fechaEmision').value = today;
        await loadCatalogs();
        if (idEdicion !== undefined) {
            await loadInvoiceForEdit(idEdicion);
        }
        else {
            try {
                const invoices = await getAllFacturas();
                const highestNumber = invoices.reduce((max, invoice) => Math.max(max, Number(invoice.numeroFactura) || 0), 0);
                byId('numero').value = String(highestNumber + 1 || 1);
            }
            catch { /* El número se puede ingresar manualmente si falla la consulta. */ }
            lineas = [createLine()];
            renderLines();
        }
    }
    catch (error) {
        showAlert(`No se pudo preparar el formulario. Verificá que el backend esté activo y que hayas agregado los endpoints de integración. Detalle: ${error instanceof Error ? error.message : String(error)}`);
        byId('btn-guardar').disabled = true;
    }
}
void API_URL;
void init();
