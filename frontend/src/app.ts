import "./styles.css";
import { FacturaService } from "./FacturaService";
import type {
  ArticuloDTO,
  ArticuloFiltrosDTO,
  ClienteDTO,
  ClienteFiltrosDTO,
  FacturaDetalleRequestDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  OpcionDTO
} from "./models/factura.dto";

const demoMode = document.querySelector<HTMLInputElement>("#demo-mode")!;
const demoNotice = document.querySelector<HTMLParagraphElement>("#demo-notice")!;
const servicio = new FacturaService(() => demoMode.checked);
const filtroForm = document.querySelector<HTMLFormElement>("#filter-form")!;
const facturaForm = document.querySelector<HTMLFormElement>("#invoice-form")!;
const itemsBody = document.querySelector<HTMLTableSectionElement>("#items-body")!;
const facturasBody = document.querySelector<HTMLTableSectionElement>("#invoices-body")!;
const feedback = document.querySelector<HTMLParagraphElement>("#feedback")!;
const resultCount = document.querySelector<HTMLSpanElement>("#result-count")!;
const homeButton = document.querySelector<HTMLButtonElement>("#home-button")!;
const articleDialog = document.querySelector<HTMLDialogElement>("#article-dialog")!;
const articleForm = document.querySelector<HTMLFormElement>("#article-search-form")!;
const articleResults = document.querySelector<HTMLTableSectionElement>("#article-results-body")!;
const clientDialog = document.querySelector<HTMLDialogElement>("#client-dialog")!;
const clientForm = document.querySelector<HTMLFormElement>("#client-search-form")!;
const clientResults = document.querySelector<HTMLTableSectionElement>("#client-results-body")!;
const refreshButton = document.querySelector<HTMLButtonElement>("#refresh-button")!;

type Vista = "inicio" | "consulta" | "alta";

function mostrarVista(vista: Vista): void {
  document.querySelectorAll<HTMLElement>("[data-view]").forEach((seccion) => {
    seccion.hidden = seccion.dataset.view !== vista;
  });
  homeButton.hidden = vista === "inicio";
  refreshButton.hidden = vista !== "consulta";
  informar("");
  if (vista === "consulta") void cargarFacturas();
}

function informar(mensaje: string, esError = false): void {
  feedback.textContent = mensaje;
  feedback.classList.toggle("feedback-error", esError);
  feedback.classList.toggle("feedback-success", !esError && mensaje.length > 0);
}

function valor(form: HTMLFormElement, nombre: string): string {
  const campo = form.elements.namedItem(nombre);
  return campo instanceof HTMLInputElement || campo instanceof HTMLSelectElement ? campo.value.trim() : "";
}

function llenarCombo(nombre: string, opciones: OpcionDTO[]): void {
  const combo = facturaForm.elements.namedItem(nombre) as HTMLSelectElement;
  combo.replaceChildren(new Option("Seleccioná…", ""));
  for (const opcion of opciones) combo.append(new Option(opcion.descripcion, String(opcion.id)));
}

async function cargarOpciones(): Promise<void> {
  try {
    const [condiciones, puntos] = await Promise.all([servicio.obtenerCondicionesIva(), servicio.obtenerPuntosVenta()]);
    llenarCombo("condicionIvaId", condiciones);
    llenarCombo("puntoVentaId", puntos);
  } catch (error) {
    informar(error instanceof Error ? error.message : "No se pudieron cargar los combos.", true);
  }
}

function elegirCliente(cliente: ClienteDTO | null): void {
  (facturaForm.elements.namedItem("clienteId") as HTMLInputElement).value = cliente ? String(cliente.id) : "";
  (facturaForm.elements.namedItem("clienteNombre") as HTMLInputElement).value = cliente ? cliente.denominacion : "";
}

function agregarRenglon(articulo: ArticuloDTO): void {
  const fila = document.createElement("tr");
  fila.dataset.articuloId = String(articulo.id);
  fila.innerHTML = `
    <td></td>
    <td></td>
    <td><input aria-label="Cantidad" data-field="cantidad" type="number" min="0.001" step="0.001" value="1" required></td>
    <td><input aria-label="Bonificación porcentual" data-field="porcentajeBonificacion" type="number" min="0" max="100" step="0.01" value="0" required></td>
    <td><button class="button button-danger button-small" type="button" data-action="remove-item" aria-label="Quitar renglón">Quitar</button></td>`;
  fila.cells[0].textContent = articulo.codigo;
  fila.cells[1].textContent = articulo.denominacion;
  itemsBody.append(fila);
}

function leerDetalles(): FacturaDetalleRequestDTO[] {
  return Array.from(itemsBody.rows).map((fila) => {
    const dato = (nombre: string): string =>
      fila.querySelector<HTMLInputElement>(`[data-field="${nombre}"]`)?.value.trim() ?? "";
    return {
      articuloId: Number(fila.dataset.articuloId),
      descripcion: null,
      cantidad: Number(dato("cantidad")),
      porcentajeBonificacion: Number(dato("porcentajeBonificacion"))
    };
  });
}

function filtrosActuales(): FacturaFiltrosDTO {
  const desde = valor(filtroForm, "fechaDesde");
  const hasta = valor(filtroForm, "fechaHasta");
  const estado = valor(filtroForm, "estado");
  const monto = valor(filtroForm, "montoMinimo");
  const cliente = valor(filtroForm, "cliente");
  const numero = valor(filtroForm, "numeroComprobante");
  return {
    ...(desde ? { fechaDesde: desde } : {}),
    ...(hasta ? { fechaHasta: hasta } : {}),
    ...(estado ? { estado } : {}),
    ...(monto ? { montoMinimo: Number(monto) } : {}),
    ...(cliente ? { cliente } : {}),
    ...(numero ? { numeroComprobante: Number(numero) } : {})
  };
}

function celda(fila: HTMLTableRowElement, contenido: string): void {
  const elemento = document.createElement("td");
  elemento.textContent = contenido;
  fila.append(elemento);
}

function dibujarFacturas(facturas: FacturaReporteDTO[]): void {
  facturasBody.replaceChildren();
  resultCount.textContent = `${facturas.length} resultado${facturas.length === 1 ? "" : "s"}`;
  if (facturas.length === 0) {
    const fila = facturasBody.insertRow();
    const vacio = fila.insertCell();
    vacio.colSpan = 8;
    vacio.className = "empty-state";
    vacio.textContent = "No hay facturas que coincidan con los filtros.";
    return;
  }

  for (const factura of facturas) {
    const fila = facturasBody.insertRow();
    celda(fila, String(factura.numeroFactura ?? "—"));
    celda(fila, factura.fechaEmision);
    celda(fila, factura.estado);
    celda(fila, factura.clienteDenominacion);
    celda(fila, factura.condicionIva);
    celda(fila, factura.puntoVentaDescripcion);
    celda(fila, new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(factura.importeTotal));
    celda(fila, String(factura.cantidadItems));

    const acciones = fila.insertCell();
    acciones.className = "row-actions";
    const eliminar = document.createElement("button");
    eliminar.type = "button";
    eliminar.className = "button button-danger button-small";
    eliminar.textContent = "Eliminar";
    eliminar.addEventListener("click", () => void eliminarFactura(factura.id, factura.numeroFactura));
    acciones.append(eliminar);
  }
}

async function buscarArticulos(filtros: ArticuloFiltrosDTO): Promise<void> {
  const mensaje = (texto: string): void => {
    articleResults.innerHTML = '<tr><td colspan="3" class="empty-state"></td></tr>';
    articleResults.rows[0].cells[0].textContent = texto;
  };
  mensaje("Buscando…");
  try {
    const articulos = await servicio.buscarArticulos(filtros);
    if (articulos.length === 0) {
      mensaje("No hay artículos que coincidan.");
      return;
    }
    articleResults.replaceChildren();
    for (const articulo of articulos) {
      const fila = articleResults.insertRow();
      celda(fila, articulo.codigo);
      celda(fila, articulo.denominacion);
      const elegir = document.createElement("button");
      elegir.type = "button";
      elegir.className = "button button-secondary button-small";
      elegir.textContent = "Agregar";
      elegir.addEventListener("click", () => {
        agregarRenglon(articulo);
        articleDialog.close();
      });
      fila.insertCell().append(elegir);
    }
  } catch (error) {
    mensaje(error instanceof Error ? error.message : "No se pudieron cargar los artículos.");
  }
}

async function buscarClientes(filtros: ClienteFiltrosDTO): Promise<void> {
  const mensaje = (texto: string): void => {
    clientResults.innerHTML = '<tr><td colspan="3" class="empty-state"></td></tr>';
    clientResults.rows[0].cells[0].textContent = texto;
  };
  mensaje("Buscando…");
  try {
    const clientes = await servicio.buscarClientes(filtros);
    if (clientes.length === 0) {
      mensaje("No hay clientes que coincidan.");
      return;
    }
    clientResults.replaceChildren();
    for (const cliente of clientes) {
      const fila = clientResults.insertRow();
      celda(fila, cliente.denominacion);
      celda(fila, cliente.documento);
      const elegir = document.createElement("button");
      elegir.type = "button";
      elegir.className = "button button-secondary button-small";
      elegir.textContent = "Elegir";
      elegir.addEventListener("click", () => {
        elegirCliente(cliente);
        clientDialog.close();
      });
      fila.insertCell().append(elegir);
    }
  } catch (error) {
    mensaje(error instanceof Error ? error.message : "No se pudieron cargar los clientes.");
  }
}

async function cargarFacturas(filtros: FacturaFiltrosDTO = filtrosActuales()): Promise<void> {
  informar("");
  facturasBody.innerHTML = '<tr><td colspan="9" class="empty-state">Cargando facturas…</td></tr>';
  try {
    dibujarFacturas(await servicio.obtenerFacturas(filtros));
  } catch (error) {
    facturasBody.replaceChildren();
    resultCount.textContent = "";
    informar(error instanceof Error ? error.message : "No se pudieron cargar las facturas.", true);
  }
}

async function eliminarFactura(id: number, numero: number | null): Promise<void> {
  if (!window.confirm(`¿Eliminar definitivamente la factura ${numero ?? id}?`)) return;
  try {
    await servicio.eliminarFactura(id);
    informar(`Factura ${numero ?? id} eliminada.`);
    await cargarFacturas();
  } catch (error) {
    informar(error instanceof Error ? error.message : "No se pudo eliminar la factura.", true);
  }
}

document.querySelector("#add-item")!.addEventListener("click", () => {
  articleForm.reset();
  articleDialog.showModal();
  void buscarArticulos({});
});
articleForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const codigo = valor(articleForm, "codigo");
  const denominacion = valor(articleForm, "denominacion");
  void buscarArticulos({
    ...(codigo ? { codigo } : {}),
    ...(denominacion ? { denominacion } : {})
  });
});
document.querySelector("#article-close")!.addEventListener("click", () => articleDialog.close());
document.querySelector("#pick-client")!.addEventListener("click", () => {
  clientForm.reset();
  clientDialog.showModal();
  void buscarClientes({});
});
document.querySelector("#clear-client")!.addEventListener("click", () => elegirCliente(null));
clientForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const denominacion = valor(clientForm, "denominacion");
  const documento = valor(clientForm, "documento");
  void buscarClientes({
    ...(denominacion ? { denominacion } : {}),
    ...(documento ? { documento } : {})
  });
});
document.querySelector("#client-close")!.addEventListener("click", () => clientDialog.close());
refreshButton.addEventListener("click", () => void cargarFacturas());
homeButton.addEventListener("click", () => mostrarVista("inicio"));
document.querySelectorAll<HTMLButtonElement>("[data-goto]").forEach((boton) => {
  boton.addEventListener("click", () => mostrarVista(boton.dataset.goto as Vista));
});
demoMode.addEventListener("change", () => {
  demoNotice.hidden = !demoMode.checked;
  void cargarOpciones();
  void cargarFacturas();
});
itemsBody.addEventListener("click", (event) => {
  const boton = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-action="remove-item"]');
  if (boton) boton.closest("tr")?.remove();
});
filtroForm.addEventListener("submit", (event) => {
  event.preventDefault();
  void cargarFacturas(filtrosActuales());
});
filtroForm.addEventListener("reset", () => {
  window.setTimeout(() => void cargarFacturas({}), 0);
});
facturaForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const detalles = leerDetalles();
  if (detalles.length === 0) {
    informar("Agregá al menos un artículo a la factura.", true);
    return;
  }

  const clienteId = valor(facturaForm, "clienteId");
  const request: FacturaRequestDTO = {
    numero: Number(valor(facturaForm, "numero")),
    fechaEmision: valor(facturaForm, "fechaEmision"),
    clienteId: clienteId ? Number(clienteId) : null,
    condicionIvaId: Number(valor(facturaForm, "condicionIvaId")),
    tipoMonedaId: Number(valor(facturaForm, "tipoMonedaId")),
    puntoVentaId: Number(valor(facturaForm, "puntoVentaId")),
    importeCobrado: Number(valor(facturaForm, "importeCobrado")),
    importeSaldo: 0,
    estado: null,
    observaciones: valor(facturaForm, "observaciones") || null,
    detalles
  };

  try {
    const creada = await servicio.crearFactura(request);
    facturaForm.reset();
    elegirCliente(null);
    itemsBody.replaceChildren();
    mostrarVista("consulta");
    informar(`Factura ${creada.numeroFactura ?? ""} registrada correctamente.`);
  } catch (error) {
    informar(error instanceof Error ? error.message : "No se pudo registrar la factura.", true);
  }
});

void cargarOpciones();
mostrarVista("inicio");
