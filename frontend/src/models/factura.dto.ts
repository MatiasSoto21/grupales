export interface FacturaReporteDTO {
  id: number;
  numeroFactura: number | null;
  fechaEmision: string;
  estado: string;
  clienteDenominacion: string;
  condicionIva: string;
  puntoVentaDescripcion: string;
  importeTotal: number;
  cantidadItems: number;
}

export interface FacturaDetalleRequestDTO {
  articuloId: number | null;
  descripcion: string | null;
  cantidad: number;
  porcentajeBonificacion: number;
}

export interface FacturaRequestDTO {
  numero: number | null;
  fechaEmision: string | null;
  clienteId: number | null;
  condicionIvaId: number | null;
  tipoMonedaId: number | null;
  puntoVentaId: number | null;
  importeCobrado: number;
  importeSaldo: number;
  estado: string | null;
  observaciones: string | null;
  detalles: FacturaDetalleRequestDTO[];
}

export interface FacturaFiltrosDTO {
  fechaDesde?: string;
  fechaHasta?: string;
  estado?: string;
  montoMinimo?: number;
  cliente?: string;
  numeroComprobante?: number;
}

export interface ArticuloDTO {
  id: number;
  codigo: string;
  denominacion: string;
}

export interface ArticuloFiltrosDTO {
  codigo?: string;
  denominacion?: string;
}

export interface OpcionDTO {
  id: number;
  descripcion: string;
}

export interface ClienteDTO {
  id: number;
  denominacion: string;
  documento: string;
}

export interface ClienteFiltrosDTO {
  denominacion?: string;
  documento?: string;
}

export interface FacturaEstadoRequestDTO {
  estado: string;
  observaciones?: string;
}
