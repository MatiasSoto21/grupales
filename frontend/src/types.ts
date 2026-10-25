export interface FacturaReporteDTO {
  id: number;
  numeroFactura: number | null;
  fechaEmision: string;
  clienteDenominacion: string;
  condicionIva: string;
  puntoVentaDescripcion: string;
  importeTotal: number;
  cantidadItems: number;
  estado: string;
}

export type FacturaResumen = FacturaReporteDTO;

export interface GetFacturasOptions {
  fechaDesde?: string;
  fechaHasta?: string;
  estado?: string;
  montoMinimo?: number;
  clienteId?: number;
  sinCliente?: boolean;
  numero?: number;
  numeroDesde?: number;
  numeroHasta?: number;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  hasContent: boolean;
}

export interface ArticuloCatalogo {
  id: number;
  codigo?: string | number;
  denominacion: string;
  precioVenta: number;
}

export interface OpcionCatalogo {
  id: number;
  denominacion: string;
  descripcion?: string;
  numero?: number;
  simbolo?: string;
}

export interface ClienteRequest {
  cuitCuil: string;
  denominacion: string;
  email?: string;
  telefono?: string;
  celular?: string;
  nombreCalle?: string;
  numeroCalle?: string;
}

export interface Catalogos {
  clientes: OpcionCatalogo[];
  condicionesIva: OpcionCatalogo[];
  puntosVenta: OpcionCatalogo[];
  tiposMoneda: OpcionCatalogo[];
  articulos: ArticuloCatalogo[];
}

export interface FacturaDetalleRequestDTO {
  articuloId: number;
  descripcion: string;
  cantidad: number;
  porcentajeBonificacion: number;
}

export type DetalleRequest = FacturaDetalleRequestDTO;

export interface DetalleFactura {
  id?: number;
  articuloId?: number;
  descripcion: string;
  cantidad: number;
  porcentajeBonificacion: number;
}

export interface FacturaRequestDTO {
  numero: number | null;
  fechaEmision: string;
  clienteId: number | null;
  condicionIvaId: number;
  tipoMonedaId: number;
  puntoVentaId: number;
  importeCobrado: number;
  importeSaldo: number;
  estado: string;
  observaciones: string;
  detalles: DetalleRequest[];
}

export type FacturaRequest = FacturaRequestDTO;
