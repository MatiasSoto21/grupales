import type { Catalogos, FacturaRequest, FacturaResumen } from './types.js';

export const API_BASE = 'http://localhost:8080/api';
export const FACTURAS_API = `${API_BASE}/facturas`;

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const raw = await response.text();
    let message = raw;
    try {
      const parsed: { message?: string; error?: string } = JSON.parse(raw) as { message?: string; error?: string };
      message = parsed.message ?? parsed.error ?? raw;
    } catch { /* La respuesta no era JSON. */ }
    throw new Error(message || `Error HTTP ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export function getFacturas(query = ''): Promise<FacturaResumen[]> {
  return request<FacturaResumen[]>(`${FACTURAS_API}${query ? `?${query}` : ''}`);
}

export function getFactura(id: number): Promise<FacturaRequest> {
  return request<FacturaRequest>(`${FACTURAS_API}/${id}`);
}

export function getCatalogos(): Promise<Catalogos> {
  return request<Catalogos>(`${API_BASE}/catalogos`);
}

export function guardarFactura(body: FacturaRequest, id?: number): Promise<FacturaResumen> {
  return request<FacturaResumen>(id ? `${FACTURAS_API}/${id}` : FACTURAS_API, {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
}

export function anularFactura(id: number): Promise<void> {
  return request<void>(`${FACTURAS_API}/${id}`, { method: 'DELETE' });
}
