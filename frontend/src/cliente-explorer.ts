import { buscarClientes } from './api.js';
import { byId, makeOption } from './dom.js';
import type { ClienteBusqueda, OpcionCatalogo } from './types.js';

export class ClienteExplorer {
  private page = 0;
  private requestId = 0;
  private applied = new URLSearchParams();
  private readonly dialog: HTMLDialogElement;

  constructor(private readonly onSelect: (client: ClienteBusqueda) => void) {
    const host = document.createElement('div');
    host.innerHTML = `
      <dialog id="cliente-explorer" class="modal client-explorer" aria-labelledby="cliente-explorer-title">
        <div class="modal-header"><h2 id="cliente-explorer-title">Explorar clientes</h2><button type="button" class="theme-toggle" data-theme-toggle aria-label="Cambiar tema"></button><button type="button" id="cliente-explorer-close" class="modal-close" aria-label="Cerrar explorador">×</button></div>
        <form id="cliente-explorer-form" class="client-explorer-filters">
          <div class="field"><label for="cliente-buscar-nombre">Denominación</label><input id="cliente-buscar-nombre" name="denominacion" type="search" maxlength="200"></div>
          <div class="field"><label for="cliente-buscar-cuit">CUIT/CUIL</label><input id="cliente-buscar-cuit" name="cuitCuil" type="search" maxlength="30"></div>
          <div class="field"><label for="cliente-coincidencia">Coincidencia</label><select id="cliente-coincidencia" name="coincidencia"><option value="parcial">Parcial</option><option value="exacta">Exacta</option></select></div>
          <div class="field"><label for="cliente-iva-filtro">Condición IVA</label><select id="cliente-iva-filtro" name="condicionIvaId"><option value="">Todas</option></select></div>
          <div class="field"><label for="cliente-orden">Ordenar por</label><select id="cliente-orden" name="sortBy"><option value="denominacion">Denominación</option><option value="cuitCuil">CUIT/CUIL</option></select></div>
          <div class="field"><label for="cliente-direccion">Dirección</label><select id="cliente-direccion" name="sortDir"><option value="asc">Ascendente</option><option value="desc">Descendente</option></select></div>
          <div class="client-explorer-actions"><button type="submit" class="btn btn-primary">Buscar</button><button type="button" id="cliente-filtros-limpiar" class="btn btn-secondary">Limpiar filtros</button></div>
        </form>
        <p id="cliente-explorer-status" class="client-explorer-status" role="status" aria-live="polite"></p>
        <div class="table-wrap"><table class="table"><thead><tr><th>Denominación</th><th>CUIT/CUIL</th><th>Condición IVA</th><th>Acción</th></tr></thead><tbody id="cliente-resultados"></tbody></table></div>
        <nav class="client-explorer-pagination" aria-label="Paginación de clientes"><button type="button" id="cliente-anterior" class="btn btn-secondary">Anterior</button><span id="cliente-pagina"></span><button type="button" id="cliente-siguiente" class="btn btn-secondary">Siguiente</button></nav>
        <div class="modal-actions"><button type="button" id="cliente-explorer-cancel" class="btn btn-secondary">Cancelar</button></div>
      </dialog>`;
    document.body.append(host);
    this.dialog = byId<HTMLDialogElement>('cliente-explorer');
    const close = (): void => this.dialog.close();
    byId('cliente-explorer-close').addEventListener('click', close);
    byId('cliente-explorer-cancel').addEventListener('click', close);
    this.dialog.addEventListener('close', () => { ++this.requestId; });
    byId<HTMLFormElement>('cliente-explorer-form').addEventListener('submit', event => {
      event.preventDefault(); this.applyFilters();
    });
    byId('cliente-filtros-limpiar').addEventListener('click', () => {
      byId<HTMLFormElement>('cliente-explorer-form').reset(); this.applyFilters();
    });
    for (const id of ['cliente-coincidencia', 'cliente-iva-filtro', 'cliente-orden', 'cliente-direccion']) {
      byId(id).addEventListener('change', () => this.applyFilters());
    }
    byId('cliente-anterior').addEventListener('click', () => { this.page--; void this.load(); });
    byId('cliente-siguiente').addEventListener('click', () => { this.page++; void this.load(); });
  }

  setTaxes(taxes: OpcionCatalogo[]): void {
    const select = byId<HTMLSelectElement>('cliente-iva-filtro');
    select.replaceChildren(makeOption('', 'Todas'), ...taxes.map(tax => makeOption(tax.id, tax.denominacion)));
  }

  open(): void {
    this.dialog.showModal();
    byId<HTMLInputElement>('cliente-buscar-nombre').focus();
    this.applyFilters();
  }

  private applyFilters(): void {
    const form = byId<HTMLFormElement>('cliente-explorer-form');
    if (!form.reportValidity()) return;
    this.applied = new URLSearchParams();
    new FormData(form).forEach((value, key) => {
      if (String(value).trim()) this.applied.set(key, String(value).trim());
    });
    this.page = 0;
    void this.load();
  }

  private async load(): Promise<void> {
    const requestId = ++this.requestId;
    const status = byId('cliente-explorer-status');
    const tbody = byId('cliente-resultados');
    const previous = byId<HTMLButtonElement>('cliente-anterior');
    const next = byId<HTMLButtonElement>('cliente-siguiente');
    previous.disabled = next.disabled = true;
    tbody.replaceChildren();
    status.textContent = 'Buscando clientes…';
    byId('cliente-pagina').textContent = '';
    const params = new URLSearchParams(this.applied);
    params.set('page', String(this.page)); params.set('size', '10');
    try {
      const result = await buscarClientes(params);
      if (requestId !== this.requestId || !this.dialog.open) return;
      if (result.number > 0 && result.number >= result.totalPages) {
        this.page = Math.max(0, result.totalPages - 1); void this.load(); return;
      }
      status.textContent = result.totalElements ? `${result.totalElements} clientes encontrados` : 'No se encontraron clientes.';
      byId('cliente-pagina').textContent = `Página ${result.totalPages ? result.number + 1 : 0} de ${result.totalPages}`;
      previous.disabled = result.first; next.disabled = result.last;
      for (const client of result.content) {
        const row = document.createElement('tr');
        for (const value of [client.denominacion, client.cuitCuil, client.condicionIvaDenominacion ?? 'Sin registrar']) {
          const cell = document.createElement('td'); cell.textContent = value; row.append(cell);
        }
        const cell = document.createElement('td');
        const select = document.createElement('button');
        select.type = 'button'; select.className = 'btn btn-primary btn-small'; select.textContent = 'Seleccionar';
        select.setAttribute('aria-label', `Seleccionar ${client.denominacion}`);
        select.addEventListener('click', () => { this.onSelect(client); this.dialog.close(); });
        cell.append(select); row.append(cell); tbody.append(row);
      }
    } catch (error: unknown) {
      if (requestId !== this.requestId || !this.dialog.open) return;
      status.textContent = `No se pudieron buscar clientes: ${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
