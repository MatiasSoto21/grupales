export interface FacturaReporteDTO {
  referenciaListado?: string;
  referenciaComprobante?: string;
  referenciaCompleta?: string;
  puntoVentaId?: number;
  puntoVentaNumero?: number;
  tipoComprobanteAfip?: number;
  origenInternoId?: number | null;
  origenInternoReferencia?: string;
  origenInternoEditable?: boolean;
  comprobanteInterno?: boolean;
  origenComprobanteId?: number | null;
  comprobanteFiscalDerivadoId?: number | null;
  id: number;
  numeroFactura: number | null;
  fechaEmision: string;
  clienteDenominacion: string;
  condicionIva: string;
  puntoVentaDescripcion: string;
  importeTotal: number;
  cantidadItems: number;
  estado: string;
  tieneCae: boolean;
  comprobanteDisponible: boolean;
  editable: boolean;
  receptorPrueba?: boolean;
  motivoRechazo?: string | null;
}

export type FacturaResumen = FacturaReporteDTO;

export interface GetFacturasOptions {
  puntoVentaId?: number;
  claseDocumento?: 'X' | 'FISCAL';
  incluirVinculados?: boolean;
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
  sortBy?: 'numero' | 'fechaEmision' | 'total';
  sortDir?: 'asc' | 'desc';
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
  precios: PrecioArticuloCatalogo[];
}

export interface PrecioArticuloCatalogo {
  id: number;
  listaPrecioId: number;
  listaPrecioDenominacion: string;
  precioVenta: number;
}

export interface OpcionCatalogo {
  codigoAfip?: number;
  id: number;
  denominacion: string;
  condicionIvaId?: number | null;
  descripcion?: string;
  numero?: number;
  simbolo?: string;
}

export interface ClienteCreado {
  id: number;
  denominacion: string;
  condicionIvaId: number;
}

export interface ClienteRequest {
  cuitCuil: string;
  denominacion: string;
  condicionIvaId: number;
  email?: string;
  telefono?: string;
  celular?: string;
  nombreCalle?: string;
  numeroCalle?: string;
}

export interface Catalogos {
  estadosFactura?: string[];
  facturacion?: { ambiente: string; fechaActual: string; fechaFiscalMinima: string; fechaFiscalMaxima: string };
  clientes: OpcionCatalogo[];
  condicionesIva: OpcionCatalogo[];
  puntosVenta: OpcionCatalogo[];
  tiposMoneda: OpcionCatalogo[];
  articulos: ArticuloCatalogo[];
  listasPrecio: Array<{ id: number; codigo: string; denominacion: string }>;
}

export interface FacturaDetalleRequestDTO {
  articuloId: number;
  listaPrecioArticuloId: number;
  descripcion: string;
  cantidad: number;
  porcentajeBonificacion: number;
}

export type DetalleRequest = FacturaDetalleRequestDTO;

export interface DetalleFactura {
  id?: number;
  articuloId?: number;
  listaPrecioArticuloId?: number;
  descripcion: string;
  cantidad: number;
  porcentajeBonificacion: number;
}

export interface FacturaRequestDTO {
  referenciaComprobante?: string;
  comprobanteFiscalReferencia?: string;
  version?: number;
  editable?: boolean;
  puedeEmitirFiscal?: boolean;
  comprobanteInterno?: boolean;
  origenComprobanteId?: number | null;
  comprobanteFiscalDerivadoId?: number | null;
  numero?: number | null;
  fechaEmision?: string;
  fechaOperacion?: string | null;
  fechaSolicitudAfip?: string | null;
  clienteId: number | null;
  condicionIvaId: number;
  tipoMonedaId: number;
  puntoVentaId: number;
  estado: string;
  observaciones: string;
  detalles: DetalleRequest[];
}

export type FacturaRequest = FacturaRequestDTO;

export interface ClienteBusqueda {
  id: number;
  denominacion: string;
  cuitCuil: string;
  condicionIvaId: number | null;
  condicionIvaDenominacion: string | null;
}

export interface ArticuloBusqueda {
  listaPrecioArticuloId: number;
  articuloId: number;
  codigo: string;
  denominacion: string;
  listaPrecioId: number;
  listaPrecioDenominacion: string;
  precioVenta: number;
}
