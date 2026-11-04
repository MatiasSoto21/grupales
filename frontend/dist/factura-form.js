import { crearCliente, getCatalogos, getFactura, getSiguienteNumeroFactura, guardarFactura } from './api.js';
import { byId, clearAlert, currency, initTheme, makeOption, showAlert } from './dom.js';
const API_URL = 'http://localhost:8080/api/facturas';
const params = new URLSearchParams(window.location.search);
const idParam = params.get('id');
const idEdicion = idParam && Number.isInteger(Number(idParam)) ? Number(idParam) : undefined;
const modalMode = params.has('modal');
let catalogos = null;
let lineas = [];
let estadoActual = 'EMITIDA';
let guardando = false;
let creandoCliente = false;
initTheme();
function articleById(id) {
    return (catalogos?.articulos ?? []).find(article => String(article.id) === String(id));
}
function priceById(id) {
    return (catalogos?.articulos ?? [])
        .flatMap(article => article.precios ?? [])
        .find(price => String(price.id) === String(id));
}
function linePrice(line) {
    return Number(priceById(line.listaPrecioArticuloId)?.precioVenta ?? 0);
}
function createLine(detail = {}) {
    return {
        articuloId: detail.articuloId ?? undefined,
        listaPrecioArticuloId: detail.listaPrecioArticuloId ?? undefined,
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
    taxSelect.replaceChildren(makeOption('', 'Seleccionar'));
    for (const tax of catalogos.condicionesIva ?? [])
        taxSelect.append(makeOption(tax.id, tax.denominacion));
    const pointSelect = byId('puntoVentaId');
    pointSelect.replaceChildren(makeOption('', 'Seleccionar'));
    for (const point of catalogos.puntosVenta ?? []) {
        pointSelect.append(makeOption(point.id, `${point.numero ? `${point.numero} · ` : ''}${point.descripcion || 'Punto de venta'}`));
    }
    const currencySelect = byId('tipoMonedaId');
    currencySelect.replaceChildren(makeOption('', 'Seleccionar'));
    for (const money of catalogos.tiposMoneda ?? []) {
        currencySelect.append(makeOption(money.id, `${money.simbolo ?? ''} ${money.denominacion}`.trim()));
    }
    const productSelect = byId('producto-articulo');
    productSelect.replaceChildren(makeOption('', 'Seleccionar'));
    for (const article of catalogos.articulos ?? []) {
        productSelect.append(makeOption(article.id, `${article.codigo ? `${article.codigo} · ` : ''}${article.denominacion}`));
    }
}
function closeClientPanel() {
    const panel = byId('nuevo-cliente-panel');
    if (panel.open)
        panel.close();
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
    const price = linePrice(line);
    return price * Number(line.cantidad || 0) * (1 - Number(line.porcentajeBonificacion || 0) / 100);
}
function updateTotals() {
    const net = lineas.reduce((sum, line) => sum + calculateSubtotal(line), 0);
    const iva = net * 0.21;
    byId('neto-total').textContent = currency(net);
    byId('iva-total').textContent = currency(iva);
    byId('total-final').textContent = currency(net + iva);
    byId('detalles-tbody').querySelectorAll('tr').forEach((row, index) => {
        const line = lineas[index];
        if (!line)
            return;
        if (row.cells[4])
            row.cells[4].textContent = currency(linePrice(line));
        if (row.cells[5])
            row.cells[5].textContent = currency(calculateSubtotal(line));
    });
}
function renderLines() {
    const tableBody = byId('detalles-tbody');
    tableBody.innerHTML = '';
    if (!catalogos)
        return;
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
            line.listaPrecioArticuloId = articleById(articleSelect.value)?.precios?.[0]?.id;
            renderLines();
        });
        articleCell.append(articleSelect);
        const priceCell = document.createElement('td');
        const priceSelect = document.createElement('select');
        priceSelect.className = 'price-select';
        priceSelect.required = true;
        priceSelect.append(makeOption('', 'Seleccionar precio'));
        for (const price of articleById(line.articuloId)?.precios ?? []) {
            priceSelect.append(makeOption(price.id, `${price.listaPrecioDenominacion} · ${currency(price.precioVenta)}`));
        }
        priceSelect.value = line.listaPrecioArticuloId == null ? '' : String(line.listaPrecioArticuloId);
        priceSelect.addEventListener('change', () => {
            line.listaPrecioArticuloId = priceSelect.value ? Number(priceSelect.value) : undefined;
            updateTotals();
        });
        priceCell.append(priceSelect);
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
        const unitPriceCell = document.createElement('td');
        unitPriceCell.className = 'text-right';
        unitPriceCell.textContent = currency(linePrice(line));
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
        row.append(articleCell, priceCell, quantityCell, discountCell, unitPriceCell, subtotalCell, actionsCell);
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
    const details = lineas.map(line => ({
        articuloId: Number(line.articuloId),
        listaPrecioArticuloId: Number(line.listaPrecioArticuloId),
        descripcion: line.descripcion || articleById(line.articuloId)?.denominacion || '',
        cantidad: Number(line.cantidad),
        porcentajeBonificacion: Number(line.porcentajeBonificacion || 0)
    }));
    return {
        numero: idEdicion ? Number(byId('numero').value) || null : null,
        fechaEmision: byId('fechaEmision').value || undefined,
        clienteId: byId('clienteId').value ? Number(byId('clienteId').value) : null,
        condicionIvaId: Number(byId('condicionIvaId').value),
        tipoMonedaId: Number(byId('tipoMonedaId').value),
        puntoVentaId: Number(byId('puntoVentaId').value),
        estado: estadoActual.toUpperCase() === 'ANULADA'
            ? byId('estado-reactivacion').value
            : estadoActual,
        observaciones: byId('observaciones').value.trim(),
        detalles: details
    };
}
function closeProductModal() {
    const modal = byId('producto-modal');
    if (modal.open)
        modal.close();
}
function updateProductPreview() {
    const productId = byId('producto-articulo').value;
    const quantity = Number(byId('producto-cantidad').value || 0);
    const discount = Number(byId('producto-bonificacion').value || 0);
    const priceId = byId('producto-precio').value;
    const price = Number(priceById(priceId)?.precioVenta ?? 0);
    byId('producto-subtotal').textContent = currency(price * quantity * (1 - discount / 100));
}
function updateProductPriceOptions() {
    const productId = byId('producto-articulo').value;
    const priceSelect = byId('producto-precio');
    priceSelect.replaceChildren(makeOption('', 'Seleccionar'));
    for (const price of articleById(productId)?.precios ?? []) {
        priceSelect.append(makeOption(price.id, `${price.listaPrecioDenominacion} · ${currency(price.precioVenta)}`));
    }
    updateProductPreview();
}
byId('btn-agregar').addEventListener('click', () => {
    byId('producto-articulo').value = '';
    byId('producto-precio').replaceChildren(makeOption('', 'Seleccionar artículo primero'));
    byId('producto-cantidad').value = '1';
    byId('producto-bonificacion').value = '0';
    updateProductPreview();
    byId('producto-modal').showModal();
    byId('producto-articulo').focus();
});
byId('btn-cerrar-producto').addEventListener('click', closeProductModal);
byId('btn-cancelar-producto').addEventListener('click', closeProductModal);
for (const id of ['producto-precio', 'producto-cantidad', 'producto-bonificacion']) {
    byId(id).addEventListener('input', updateProductPreview);
}
byId('producto-articulo').addEventListener('change', updateProductPriceOptions);
byId('producto-form').addEventListener('submit', event => {
    event.preventDefault();
    const productId = byId('producto-articulo').value;
    const priceId = byId('producto-precio').value;
    const quantity = Number(byId('producto-cantidad').value);
    const discount = Number(byId('producto-bonificacion').value || 0);
    if (!productId || !priceId || !Number.isInteger(quantity) || quantity < 1) {
        showAlert('Datos de producto incompletos.');
        return;
    }
    if (!Number.isFinite(discount) || discount < 0 || discount > 100) {
        showAlert('La bonificación debe estar entre 0 y 100%.');
        return;
    }
    lineas.push({
        articuloId: Number(productId),
        listaPrecioArticuloId: Number(priceId),
        descripcion: articleById(productId)?.denominacion ?? '',
        cantidad: quantity,
        porcentajeBonificacion: discount
    });
    renderLines();
    closeProductModal();
});
byId('btn-mostrar-cliente').addEventListener('click', () => {
    clearAlert();
    const panel = byId('nuevo-cliente-panel');
    panel.showModal();
    byId('btn-guardar').disabled = true;
    byId('nuevo-cliente-cuit').focus();
});
byId('btn-cerrar-cliente-x').addEventListener('click', closeClientPanel);
byId('btn-cancelar-cliente').addEventListener('click', closeClientPanel);
byId('btn-guardar-cliente').addEventListener('click', () => { void saveNewClient(); });
byId('nuevo-cliente-panel').addEventListener('close', () => {
    byId('btn-guardar').disabled = false;
});
byId('factura-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    clearAlert();
    if (byId('nuevo-cliente-panel').open)
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
    if (body.detalles.some(detail => !detail.articuloId || !detail.listaPrecioArticuloId
        || !Number.isInteger(detail.cantidad) || detail.cantidad < 1)) {
        showAlert('Hay productos incompletos.');
        return;
    }
    if (body.detalles.some(detail => detail.porcentajeBonificacion < 0 || detail.porcentajeBonificacion > 100)) {
        showAlert('La bonificación debe estar entre 0 y 100%.');
        return;
    }
    guardando = true;
    const saveButton = byId('btn-guardar');
    saveButton.disabled = true;
    saveButton.textContent = 'Guardando…';
    try {
        await guardarFactura(body, idEdicion);
        showAlert(idEdicion ? 'Factura actualizada correctamente.' : 'Factura creada correctamente.', 'success');
        window.setTimeout(() => {
            if (modalMode && window.parent !== window) {
                window.parent.postMessage({ type: 'factura-modal-close', reload: true }, '*');
            }
            else {
                window.location.href = '/';
            }
        }, 900);
    }
    catch (error) {
        showAlert(`No se pudo guardar la factura: ${error instanceof Error ? error.message : String(error)}`);
        guardando = false;
        saveButton.disabled = false;
        saveButton.textContent = 'Guardar factura';
    }
});
async function init() {
    if (modalMode) {
        document.body.classList.add('modal-mode');
        const cancelButton = byId('btn-cancelar-factura');
        cancelButton.addEventListener('click', () => {
            if (window.parent !== window)
                window.parent.postMessage({ type: 'factura-modal-close', reload: false }, '*');
            else
                window.location.href = '/';
        });
    }
    else {
        byId('btn-cancelar-factura').addEventListener('click', () => { window.location.href = '/'; });
    }
    try {
        const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
        byId('fechaEmision').value = today;
        await loadCatalogs();
        if (idEdicion !== undefined) {
            await loadInvoiceForEdit(idEdicion);
        }
        else {
            try {
                const proximo = await getSiguienteNumeroFactura();
                const numeroField = byId('numero');
                if (proximo?.numero !== undefined && proximo?.numero !== null) {
                    numeroField.value = String(proximo.numero);
                }
            }
            catch {
                byId('numero').value = '';
            }
            lineas = [];
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
