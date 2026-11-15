import { buscarArticulos } from './api.js';
import { byId, currency, makeOption } from './dom.js';
import type { ArticuloBusqueda, Catalogos } from './types.js';

export class ArticuloExplorer {
  private page = 0;
  private requestId = 0;
  private applied = new URLSearchParams();
  private defaultList = '';
  private readonly modal = byId<HTMLDialogElement>('producto-modal');

  constructor(private readonly onSelect: (product: ArticuloBusqueda | null) => void) {
    byId<HTMLFormElement>('articulo-explorer-form').addEventListener('submit', event => {
      event.preventDefault(); this.applyFilters();
    });
    for (const id of ['producto-lista', 'producto-coincidencia', 'producto-orden']) {
      byId(id).addEventListener('change', () => this.applyFilters());
    }
    byId('producto-limpiar-filtros').addEventListener('click', () => {
      const list = byId<HTMLSelectElement>('producto-lista').value;
      byId<HTMLFormElement>('articulo-explorer-form').reset();
      byId<HTMLSelectElement>('producto-lista').value = list;
      this.applyFilters();
    });
    byId('producto-anterior').addEventListener('click', () => { this.page--; void this.load(); });
    byId('producto-siguiente').addEventListener('click', () => { this.page++; void this.load(); });
    this.modal.addEventListener('close', () => { ++this.requestId; });
  }

  setLists(lists: Catalogos['listasPrecio']): void {
    const select = byId<HTMLSelectElement>('producto-lista');
    select.replaceChildren(...lists.map(list => makeOption(list.id, list.denominacion)));
    const wholesale = lists.find(list => list.codigo === 'LP02')
      ?? lists.find(list => list.denominacion.toLocaleLowerCase('es').includes('mayorista'));
    this.defaultList = String(wholesale?.id ?? lists[0]?.id ?? '');
    select.value = this.defaultList;
  }

  open(listId?: number): void {
    byId<HTMLFormElement>('articulo-explorer-form').reset();
    byId<HTMLSelectElement>('producto-lista').value = listId ? String(listId) : this.defaultList;
    this.modal.showModal();
    this.applyFilters();
    byId<HTMLSelectElement>('producto-lista').focus();
  }

  private applyFilters(): void {
    this.onSelect(null);
    ++this.requestId;
    const form = byId<HTMLFormElement>('articulo-explorer-form');
    if (!form.reportValidity()) return;
    this.applied = new URLSearchParams();
    new FormData(form).forEach((value, key) => {
      if (String(value).trim()) this.applied.set(key, String(value).trim());
    });
    this.applied.set('sortDir', 'asc');
    this.page = 0; void this.load();
  }

  private async load(): Promise<void> {
    const requestId = ++this.requestId;
    const previous = byId<HTMLButtonElement>('producto-anterior');
    const next = byId<HTMLButtonElement>('producto-siguiente');
    const status = byId('producto-resultados-status');
    const tbody = byId('producto-resultados');
    previous.disabled = next.disabled = true;
    tbody.replaceChildren(); byId('producto-pagina').textContent = '';
    status.textContent = 'Buscando artículos…';
    const params = new URLSearchParams(this.applied);
    params.set('page', String(this.page)); params.set('size', '10');
    try {
      const result = await buscarArticulos(params);
      if (requestId !== this.requestId || !this.modal.open) return;
      if (result.number > 0 && result.number >= result.totalPages) {
        this.page = Math.max(0, result.totalPages - 1); void this.load(); return;
      }
      status.textContent = result.totalElements ? `${result.totalElements} artículos encontrados` : 'No se encontraron artículos en esta lista.';
      byId('producto-pagina').textContent = `Página ${result.totalPages ? result.number + 1 : 0} de ${result.totalPages}`;
      previous.disabled = result.first; next.disabled = result.last;
      for (const product of result.content) {
        const row = document.createElement('tr');
        for (const value of [product.codigo, product.denominacion, currency(product.precioVenta)]) {
          const cell = document.createElement('td'); cell.textContent = value; row.append(cell);
        }
        row.cells[2]!.className = 'text-right';
        const cell = document.createElement('td'); const button = document.createElement('button');
        button.type = 'button'; button.className = 'btn btn-primary btn-small'; button.textContent = 'Seleccionar';
        button.setAttribute('aria-label', `Seleccionar ${product.codigo} ${product.denominacion}`);
        button.addEventListener('click', () => {
          this.onSelect(product);
          byId<HTMLInputElement>('producto-cantidad').focus();
        });
        cell.append(button); row.append(cell); tbody.append(row);
      }
    } catch (error: unknown) {
      if (requestId !== this.requestId || !this.modal.open) return;
      status.textContent = `No se pudieron buscar artículos: ${error instanceof Error ? error.message : String(error)}`;
    }
  }
}
