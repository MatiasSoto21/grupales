export const API_BASE = 'http://localhost:8080/api';
export const FACTURAS_API = `${API_BASE}/facturas`;
async function request(url, init) {
    const response = await fetch(url, init);
    if (!response.ok) {
        const raw = await response.text();
        let message = raw;
        try {
            const parsed = JSON.parse(raw);
            message = parsed.message ?? parsed.error ?? parsed.detail ?? raw;
        }
        catch { /* La respuesta no era JSON. */ }
        throw new Error(message || `Error HTTP ${response.status}`);
    }
    if (response.status === 204)
        return undefined;
    return await response.json();
}
export async function getFacturas(query = '', options) {
    const params = new URLSearchParams(query);
    if (options?.fechaDesde)
        params.set('fechaDesde', options.fechaDesde);
    if (options?.fechaHasta)
        params.set('fechaHasta', options.fechaHasta);
    if (options?.estado)
        params.set('estado', options.estado);
    if (options?.montoMinimo !== undefined)
        params.set('montoMinimo', options.montoMinimo.toString());
    if (options?.clienteId !== undefined)
        params.set('clienteId', options.clienteId.toString());
    if (options?.sinCliente)
        params.set('sinCliente', 'true');
    if (options?.numero !== undefined)
        params.set('numero', options.numero.toString());
    if (options?.numeroDesde !== undefined)
        params.set('numeroDesde', options.numeroDesde.toString());
    if (options?.numeroHasta !== undefined)
        params.set('numeroHasta', options.numeroHasta.toString());
    if (options?.page !== undefined)
        params.set('page', options.page.toString());
    if (options?.size !== undefined)
        params.set('size', options.size.toString());
    return request(`${FACTURAS_API}${params.toString() ? '?' + params.toString() : ''}`);
}
export async function getAllFacturas(options = {}) {
    const size = options.size ?? 100;
    let page = options.page ?? 0;
    const facturas = [];
    let response;
    do {
        response = await getFacturas('', { ...options, page, size });
        facturas.push(...response.content);
        page += 1;
    } while (!response.last);
    return facturas;
}
export function getFacturasByCliente(clienteId) {
    return getAllFacturas({ clienteId });
}
export function getFacturasByNumero(numero) {
    return getAllFacturas({ numero });
}
export function getFacturasByNumeroRango(numeroDesde, numeroHasta) {
    return getAllFacturas({ numeroDesde, numeroHasta });
}
export function getFacturasByFilters(filters) {
    return getAllFacturas(filters);
}
export function getFactura(id) {
    return request(`${FACTURAS_API}/${id}`);
}
export function getCatalogos() {
    return request(`${API_BASE}/catalogos`);
}
export function crearCliente(body) {
    return request(`${API_BASE}/clientes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
}
export function guardarFactura(body, id) {
    return request(id ? `${FACTURAS_API}/${id}` : FACTURAS_API, {
        method: id ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body)
    });
}
export function anularFactura(id) {
    return request(`${FACTURAS_API}/${id}`, { method: 'DELETE' });
}
export function reactivarFactura(id) {
    return request(`${FACTURAS_API}/${id}/reactivar`, { method: 'POST' });
}
