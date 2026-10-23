import type {
  ArticuloDTO,
  ArticuloFiltrosDTO,
  ClienteDTO,
  ClienteFiltrosDTO,
  FacturaEstadoRequestDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  OpcionDTO
} from "./models/factura.dto";

const API_URL = "/api/facturas";
const ARTICULOS_URL = "/api/articulos";
const CLIENTES_URL = "/api/clientes";
const CONDICIONES_IVA_URL = "/api/condiciones-iva";
const PUNTOS_VENTA_URL = "/api/puntos-venta";

const CONDICIONES_IVA_DEMO: OpcionDTO[] = [
  { id: 1, descripcion: "IVA Responsable Inscripto" },
  { id: 2, descripcion: "Monotributo" },
  { id: 3, descripcion: "IVA Exento" },
  { id: 4, descripcion: "Consumidor Final" }
];

const PUNTOS_VENTA_DEMO: OpcionDTO[] = [
  { id: 1, descripcion: "Casa Central" },
  { id: 2, descripcion: "Sucursal 1" },
  { id: 3, descripcion: "Sucursal 2" }
];

const CLIENTES_DEMO: ClienteDTO[] = [
  { id: 1, denominacion: "Almacén Los Andes", documento: "30-71234567-8" },
  { id: 2, denominacion: "Distribuidora Centro", documento: "30-70987654-3" },
  { id: 3, denominacion: "Kiosco El Sol", documento: "20-28456789-1" },
  { id: 4, denominacion: "Vinícola Valle Alto", documento: "30-68123456-5" },
  { id: 5, denominacion: "Panadería La Espiga", documento: "27-30123456-9" }
];

const ARTICULOS_DEMO: ArticuloDTO[] = [
  { id: 1, codigo: "ART-001", denominacion: "Yerba mate 1 kg" },
  { id: 2, codigo: "ART-002", denominacion: "Azúcar común 1 kg" },
  { id: 3, codigo: "ART-003", denominacion: "Aceite de girasol 900 ml" },
  { id: 4, codigo: "ART-004", denominacion: "Fideos tallarín 500 g" },
  { id: 5, codigo: "ART-005", denominacion: "Harina 000 1 kg" },
  { id: 6, codigo: "ART-006", denominacion: "Vino malbec 750 ml" },
  { id: 7, codigo: "ART-007", denominacion: "Gaseosa cola 2,25 l" },
  { id: 8, codigo: "ART-008", denominacion: "Galletitas de agua" }
];
const DEMO_STORAGE_KEY = "facturacion-demo-facturas-v1";

const FACTURAS_DEMO_INICIALES: FacturaReporteDTO[] = [
  {
    id: 1,
    numeroFactura: 1001,
    fechaEmision: "2026-10-15",
    estado: "EMITIDA",
    clienteDenominacion: "Almacén Los Andes",
    condicionIva: "IVA Responsable Inscripto",
    puntoVentaDescripcion: "Casa Central",
    importeTotal: 14520,
    cantidadItems: 3
  },
  {
    id: 2,
    numeroFactura: 1002,
    fechaEmision: "2026-10-14",
    estado: "PENDIENTE",
    clienteDenominacion: "Consumidor Final",
    condicionIva: "Consumidor Final",
    puntoVentaDescripcion: "Sucursal 1",
    importeTotal: 8250.5,
    cantidadItems: 2
  },
  {
    id: 3,
    numeroFactura: 1003,
    fechaEmision: "2026-10-12",
    estado: "ANULADA",
    clienteDenominacion: "Distribuidora Centro",
    condicionIva: "IVA Responsable Inscripto",
    puntoVentaDescripcion: "Casa Central",
    importeTotal: 30100,
    cantidadItems: 5
  }
];

function esFacturaReporte(value: unknown): value is FacturaReporteDTO {
  if (typeof value !== "object" || value === null) return false;
  const factura = value as Record<string, unknown>;
  return typeof factura.id === "number" &&
    (typeof factura.numeroFactura === "number" || factura.numeroFactura === null) &&
    typeof factura.fechaEmision === "string" &&
    typeof factura.estado === "string" &&
    typeof factura.clienteDenominacion === "string" &&
    typeof factura.condicionIva === "string" &&
    typeof factura.puntoVentaDescripcion === "string" &&
    typeof factura.importeTotal === "number" &&
    typeof factura.cantidadItems === "number";
}

export class FacturaService {
  constructor(private readonly modoDemo: () => boolean = () => false) {}

  async obtenerFacturas(filtros: FacturaFiltrosDTO = {}): Promise<FacturaReporteDTO[]> {
    if (this.modoDemo()) {
      return this.leerDemo().filter((factura) =>
        (!filtros.fechaDesde || factura.fechaEmision >= filtros.fechaDesde) &&
        (!filtros.fechaHasta || factura.fechaEmision <= filtros.fechaHasta) &&
        (!filtros.estado || factura.estado.toLowerCase().includes(filtros.estado.toLowerCase())) &&
        (filtros.montoMinimo === undefined || factura.importeTotal >= filtros.montoMinimo) &&
        (!filtros.cliente || factura.clienteDenominacion.toLowerCase().includes(filtros.cliente.toLowerCase())) &&
        (filtros.numeroComprobante === undefined || factura.numeroFactura === filtros.numeroComprobante)
      );
    }
    const parametros = new URLSearchParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== "") {
        parametros.set(clave, String(valor));
      }
    });
    const query = parametros.size > 0 ? `?${parametros.toString()}` : "";
    return this.solicitar<FacturaReporteDTO[]>(`${API_URL}${query}`);
  }

  async buscarArticulos(filtros: ArticuloFiltrosDTO = {}): Promise<ArticuloDTO[]> {
    if (this.modoDemo()) {
      const incluye = (texto: string, parte?: string): boolean =>
        !parte || texto.toLowerCase().includes(parte.toLowerCase());
      return ARTICULOS_DEMO.filter((articulo) =>
        incluye(articulo.codigo, filtros.codigo) && incluye(articulo.denominacion, filtros.denominacion)
      );
    }
    const parametros = new URLSearchParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== "") {
        parametros.set(clave, String(valor));
      }
    });
    const query = parametros.size > 0 ? `?${parametros.toString()}` : "";
    return this.solicitar<ArticuloDTO[]>(`${ARTICULOS_URL}${query}`);
  }

  async buscarClientes(filtros: ClienteFiltrosDTO = {}): Promise<ClienteDTO[]> {
    if (this.modoDemo()) {
      const incluye = (texto: string, parte?: string): boolean =>
        !parte || texto.toLowerCase().includes(parte.toLowerCase());
      return CLIENTES_DEMO.filter((cliente) =>
        incluye(cliente.denominacion, filtros.denominacion) && incluye(cliente.documento, filtros.documento)
      );
    }
    const parametros = new URLSearchParams();
    Object.entries(filtros).forEach(([clave, valor]) => {
      if (valor !== undefined && valor !== "") {
        parametros.set(clave, String(valor));
      }
    });
    const query = parametros.size > 0 ? `?${parametros.toString()}` : "";
    return this.solicitar<ClienteDTO[]>(`${CLIENTES_URL}${query}`);
  }

  async obtenerCondicionesIva(): Promise<OpcionDTO[]> {
    if (this.modoDemo()) return CONDICIONES_IVA_DEMO;
    return this.solicitar<OpcionDTO[]>(CONDICIONES_IVA_URL);
  }

  async obtenerPuntosVenta(): Promise<OpcionDTO[]> {
    if (this.modoDemo()) return PUNTOS_VENTA_DEMO;
    return this.solicitar<OpcionDTO[]>(PUNTOS_VENTA_URL);
  }

  async crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    if (this.modoDemo()) {
      const facturas = this.leerDemo();
      const id = Math.max(0, ...facturas.map((item) => item.id)) + 1;
      const creada: FacturaReporteDTO = {
        id,
        numeroFactura: factura.numero ?? id,
        fechaEmision: factura.fechaEmision ?? new Date().toISOString().slice(0, 10),
        estado: factura.estado || "PENDIENTE",
        clienteDenominacion: factura.clienteId === null
          ? "Consumidor Final"
          : CLIENTES_DEMO.find((cliente) => cliente.id === factura.clienteId)?.denominacion ?? `Cliente #${factura.clienteId}`,
        condicionIva: CONDICIONES_IVA_DEMO.find((opcion) => opcion.id === factura.condicionIvaId)?.descripcion
          ?? `Condición IVA #${factura.condicionIvaId}`,
        puntoVentaDescripcion: PUNTOS_VENTA_DEMO.find((opcion) => opcion.id === factura.puntoVentaId)?.descripcion
          ?? `Punto de venta #${factura.puntoVentaId}`,
        importeTotal: factura.detalles.reduce(
          (total, detalle) => total + detalle.cantidad * 100 * (1 - detalle.porcentajeBonificacion / 100) * 1.21,
          0
        ),
        cantidadItems: factura.detalles.length
      };
      this.guardarDemo([creada, ...facturas]);
      return creada;
    }
    return this.solicitar<FacturaReporteDTO>(API_URL, {
      method: "POST",
      body: JSON.stringify(factura)
    });
  }

  async actualizarEstado(
    id: number,
    estado: string,
    observaciones?: string
  ): Promise<FacturaReporteDTO> {
    if (this.modoDemo()) {
      const facturas = this.leerDemo();
      const actualizada = facturas.find((factura) => factura.id === id);
      if (!actualizada) throw new Error(`No existe la factura con ID ${id}`);
      actualizada.estado = estado;
      this.guardarDemo(facturas);
      return actualizada;
    }
    const body: FacturaEstadoRequestDTO = { estado, observaciones };
    return this.solicitar<FacturaReporteDTO>(`${API_URL}/${id}/estado`, {
      method: "PUT",
      body: JSON.stringify(body)
    });
  }

  async eliminarFactura(id: number): Promise<void> {
    if (this.modoDemo()) {
      const facturas = this.leerDemo();
      if (!facturas.some((factura) => factura.id === id)) {
        throw new Error(`No existe la factura con ID ${id}`);
      }
      this.guardarDemo(facturas.filter((factura) => factura.id !== id));
      return;
    }
    await this.solicitar<void>(`${API_URL}/${id}`, { method: "DELETE" });
  }

  private leerDemo(): FacturaReporteDTO[] {
    try {
      const guardadas = localStorage.getItem(DEMO_STORAGE_KEY);
      if (guardadas === null) {
        this.guardarDemo(FACTURAS_DEMO_INICIALES);
        return [...FACTURAS_DEMO_INICIALES];
      }
      const datos: unknown = JSON.parse(guardadas);
      if (!Array.isArray(datos) || !datos.every(esFacturaReporte)) {
        throw new Error("El almacenamiento local de demostración no tiene el formato esperado.");
      }
      return datos;
    } catch (error) {
      if (error instanceof SyntaxError) {
        throw new Error("No se pudieron leer las facturas de demostración almacenadas en el navegador.");
      }
      throw error;
    }
  }

  private guardarDemo(facturas: FacturaReporteDTO[]): void {
    try {
      localStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(facturas));
    } catch {
      throw new Error("El navegador no pudo guardar los datos de demostración. Revisá el espacio disponible.");
    }
  }

  private async solicitar<T>(url: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(url, {
      ...init,
      headers: {
        Accept: "application/json",
        ...(init.body ? { "Content-Type": "application/json" } : {}),
        ...init.headers
      }
    });
    if (!response.ok) {
      const texto = await response.text();
      let mensaje = texto || `Error HTTP ${response.status}`;
      try {
        const detalle: unknown = JSON.parse(texto);
        if (typeof detalle === "object" && detalle !== null && "detail" in detalle) {
          const problem = detalle as { detail?: unknown };
          if (typeof problem.detail === "string") mensaje = problem.detail;
        }
      } catch {
        // La respuesta puede no ser JSON; se conserva el texto del servidor.
      }
      throw new Error(mensaje);
    }
    if (response.status === 204) return undefined as T;
    return response.json() as Promise<T>;
  }
}
