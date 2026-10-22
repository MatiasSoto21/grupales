import type { Catalogos, ClienteRequest, FacturaRequest, FacturaResumen, GetFacturasOptions, OpcionCatalogo, PageResponse } from './types.js';

export const API_BASE = 'http://localhost:8080/api';
export const FACTURAS_API = `${API_BASE}/facturas`;

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const raw = await response.text();
    let message = raw;
    try {
      const parsed: { message?: string; error?: string; detail?: string } = JSON.parse(raw) as { message?: string; error?: string; detail?: string };
      message = parsed.message ?? parsed.error ?? parsed.detail ?? raw;
    } catch { /* La respuesta no era JSON. */ }
    throw new Error(message || `Error HTTP ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export async function getFacturas(query = '', options?: GetFacturasOptions): Promise<PageResponse<FacturaResumen>> {
  const params = new URLSearchParams(query);

  if (options?.fechaDesde) params.set('fechaDesde', options.fechaDesde);
  if (options?.fechaHasta) params.set('fechaHasta', options.fechaHasta);
  if (options?.estado) params.set('estado', options.estado);
  if (options?.montoMinimo !== undefined) params.set('montoMinimo', options.montoMinimo.toString());
  if (options?.clienteId !== undefined) params.set('clienteId', options.clienteId.toString());
  if (options?.sinCliente) params.set('sinCliente', 'true');
  if (options?.numero !== undefined) params.set('numero', options.numero.toString());
  if (options?.numeroDesde !== undefined) params.set('numeroDesde', options.numeroDesde.toString());
  if (options?.numeroHasta !== undefined) params.set('numeroHasta', options.numeroHasta.toString());
  if (options?.page !== undefined) params.set('page', options.page.toString());
  if (options?.size !== undefined) params.set('size', options.size.toString());

  return request<PageResponse<FacturaResumen>>(`${FACTURAS_API}${params.toString() ? '?' + params.toString() : ''}`);
}

export async function getAllFacturas(options: GetFacturasOptions = {}): Promise<FacturaResumen[]> {
  const size = options.size ?? 100;
  let page = options.page ?? 0;
  const facturas: FacturaResumen[] = [];
  let response: PageResponse<FacturaResumen>;

  do {
    response = await getFacturas('', { ...options, page, size });
    facturas.push(...response.content);
    page += 1;
  } while (!response.last);

  return facturas;
}

export function getFacturasByCliente(clienteId: number): Promise<FacturaResumen[]> {
  return getAllFacturas({ clienteId });
}

export function getFacturasByNumero(numero: number): Promise<FacturaResumen[]> {
  return getAllFacturas({ numero });
}

export function getFacturasByNumeroRango(
  numeroDesde: number,
  numeroHasta: number
): Promise<FacturaResumen[]> {
  return getAllFacturas({ numeroDesde, numeroHasta });
}

export function getFacturasByFilters(filters: GetFacturasOptions): Promise<FacturaResumen[]> {
  return getAllFacturas(filters);
}

export function getFactura(id: number): Promise<FacturaRequest> {
  return request<FacturaRequest>(`${FACTURAS_API}/${id}`);
}

export function getCatalogos(): Promise<Catalogos> {
  return request<Catalogos>(`${API_BASE}/catalogos`);
}

export function crearCliente(body: ClienteRequest): Promise<OpcionCatalogo> {
  return request<OpcionCatalogo>(`${API_BASE}/clientes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
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

export function reactivarFactura(id: number): Promise<void> {
  return request<void>(`${FACTURAS_API}/${id}/reactivar`, { method: 'POST' });
}
