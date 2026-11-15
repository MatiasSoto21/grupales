import type { Catalogos, ClienteCreado, ClienteRequest, FacturaRequest, FacturaResumen, GetFacturasOptions, PageResponse } from './types.js';

export const API_BASE = '/api';
export const FACTURAS_API = `${API_BASE}/facturas`;

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    const raw = await response.text();
    let message = raw;
    try {
      const parsed: { message?: string; error?: string; detail?: string } = JSON.parse(raw) as { message?: string; error?: string; detail?: string };
      message = parsed.detail ?? parsed.message ?? parsed.error ?? raw;
    } catch { /* La respuesta no era JSON. */ }
    throw new Error(message || `Error HTTP ${response.status}`);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export async function getFacturas(query = '', options?: GetFacturasOptions): Promise<PageResponse<FacturaResumen>> {
  const params = new URLSearchParams(query);

  if (options?.puntoVentaId !== undefined) params.set('puntoVentaId', String(options.puntoVentaId));
  if (options?.claseDocumento) params.set('claseDocumento', options.claseDocumento);
  if (options?.incluirVinculados !== undefined) params.set('incluirVinculados', String(options.incluirVinculados));
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
  if (options?.sortBy) params.set('sortBy', options.sortBy);
  if (options?.sortDir) params.set('sortDir', options.sortDir);

  return request<PageResponse<FacturaResumen>>(`${FACTURAS_API}${params.toString() ? '?' + params.toString() : ''}`);
}

export function getSiguienteNumeroFactura(puntoVentaId: number, condicionIvaId?: number, comprobanteInterno = false): Promise<{ numero: number }> {
  const params = new URLSearchParams({ puntoVentaId: String(puntoVentaId) });
  if (comprobanteInterno) params.set("comprobanteInterno", "true");
  if (condicionIvaId) params.set("condicionIvaId", String(condicionIvaId));
  return request<{ numero: number }>(`${FACTURAS_API}/siguiente-numero?${params}`);
}

export function getFacturasByCliente(clienteId: number, options: GetFacturasOptions = {}): Promise<PageResponse<FacturaResumen>> {
  return getFacturas('', { ...options, clienteId });
}

export function getFacturasByNumero(numero: number, options: GetFacturasOptions = {}): Promise<PageResponse<FacturaResumen>> {
  return getFacturas('', { ...options, numero });
}

export function getFacturasByNumeroRango(
  numeroDesde: number, numeroHasta: number, options: GetFacturasOptions = {}
): Promise<PageResponse<FacturaResumen>> {
  return getFacturas('', { ...options, numeroDesde, numeroHasta });
}

export function getFacturasByFilters(filters: GetFacturasOptions): Promise<PageResponse<FacturaResumen>> {
  return getFacturas('', filters);
}

export function getFactura(id: number): Promise<FacturaRequest> {
  return request<FacturaRequest>(`${FACTURAS_API}/${id}`);
}

export function getComprobanteFacturaUrl(id: number): string {
  return `${FACTURAS_API}/${id}/comprobante.pdf`;
}

export function getCatalogos(): Promise<Catalogos> {
  return request<Catalogos>(`${API_BASE}/catalogos?incluirClientes=false&incluirArticulos=false`);
}

export function crearCliente(body: ClienteRequest): Promise<ClienteCreado> {
  return request<ClienteCreado>(`${API_BASE}/clientes`, {
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

export function buscarClientes(params: URLSearchParams): Promise<PageResponse<import('./types.js').ClienteBusqueda>> {
  return request(`${API_BASE}/clientes?${params}`);
}

export function getCliente(id: number): Promise<import('./types.js').ClienteBusqueda> {
  return request(`${API_BASE}/clientes/${id}`);
}

export function buscarArticulos(params: URLSearchParams): Promise<PageResponse<import('./types.js').ArticuloBusqueda>> {
  return request(`${API_BASE}/articulos?${params}`);
}

export function getPrecioArticulo(id: number): Promise<import('./types.js').ArticuloBusqueda> {
  return request(`${API_BASE}/articulos/precios/${id}`);
}

export function emitirFiscalDesde(id: number, body: FacturaRequest): Promise<FacturaResumen> {
  return request(`${FACTURAS_API}/${id}/emitir-fiscal`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
  });
}
