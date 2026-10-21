export const API_BASE = 'http://localhost:8080/api';
export const FACTURAS_API = `${API_BASE}/facturas`;
async function request(url, init) {
    const response = await fetch(url, init);
    if (!response.ok) {
        const raw = await response.text();
        let message = raw;
        try {
            const parsed = JSON.parse(raw);
            message = parsed.message ?? parsed.error ?? raw;
        }
        catch { /* La respuesta no era JSON. */ }
        throw new Error(message || `Error HTTP ${response.status}`);
    }
    if (response.status === 204)
        return undefined;
    return await response.json();
}
export function getFacturas(query = '') {
    return request(`${FACTURAS_API}${query ? `?${query}` : ''}`);
}
export function getFactura(id) {
    return request(`${FACTURAS_API}/${id}`);
}
export function getCatalogos() {
    return request(`${API_BASE}/catalogos`);
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
