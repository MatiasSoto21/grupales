export function byId<T extends HTMLElement = HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`No se encontró el elemento HTML #${id}`);
  return element as T;
}

export function showAlert(message: string, type: 'success' | 'error' = 'error'): void {
  const alert = byId<HTMLDivElement>('alerta');
  alert.textContent = message;
  alert.className = `alert ${type === 'success' ? 'alert-success' : 'alert-error'}`;
}

export function clearAlert(): void {
  byId<HTMLDivElement>('alerta').className = 'alert hidden';
}

export function currency(value: number | null | undefined): string {
  return Number(value ?? 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS' });
}

export function makeOption(value: string | number, label: string): HTMLOptionElement {
  const option = document.createElement('option');
  option.value = String(value);
  option.textContent = label;
  return option;
}
