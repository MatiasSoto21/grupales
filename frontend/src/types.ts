export interface FacturaResumen {
  id: number;
  numeroFactura: number;
  fechaEmision: string;
  clienteDenominacion: string;
  condicionIva: string;
  puntoVentaDescripcion: string;
  importeTotal: number;
  cantidadItems: number;
  estado: string;
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

export interface Catalogos {
  clientes: OpcionCatalogo[];
  condicionesIva: OpcionCatalogo[];
  puntosVenta: OpcionCatalogo[];
  tiposMoneda: OpcionCatalogo[];
  articulos: ArticuloCatalogo[];
}

export interface DetalleRequest {
  articuloId: number;
  descripcion: string;
  cantidad: number;
  porcentajeBonificacion: number;
}

export interface DetalleFactura {
  id?: number;
  articuloId?: number;
  descripcion: string;
  cantidad: number;
  porcentajeBonificacion: number;
}

export interface FacturaRequest {
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
