export function byId<T extends HTMLElement = HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`No se encontró el elemento HTML #${id}`);
  return element as T;
}

export function showAlert(message: string, type: 'success' | 'error' = 'error'): void {
  clearAlert();
  const modal = [...document.querySelectorAll<HTMLDialogElement>('dialog[open]')].at(-1);
  const alert = modal ? document.createElement('div') : byId<HTMLDivElement>('alerta');
  if (modal) {
    alert.dataset.dialogAlert = '';
    alert.setAttribute('role', 'alert');
    const header = modal.querySelector('.modal-header');
    if (header) header.insertAdjacentElement('afterend', alert);
    else modal.prepend(alert);
  }
  alert.textContent = message;
  alert.className = `alert ${type === 'success' ? 'alert-success' : 'alert-error'}`;
  alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
  alert.setAttribute('aria-atomic', 'true');
  alert.tabIndex = -1;
  alert.focus({ preventScroll: true });
  if (modal) modal.scrollTo({ top: 0, behavior: 'instant' });
  else window.scrollTo({ top: 0, behavior: 'instant' });
}

export function clearAlert(): void {
  document.querySelectorAll('[data-dialog-alert]').forEach(alert => alert.remove());
  byId<HTMLDivElement>('alerta').className = 'alert hidden';
}

export function initValidationFeedback(): void {
  let pending = false;
  document.addEventListener('invalid', event => {
    const control = event.target;
    if (!(control instanceof HTMLInputElement || control instanceof HTMLSelectElement || control instanceof HTMLTextAreaElement)) return;
    event.preventDefault();
    if (pending) return;
    pending = true;
    const label = control.labels?.[0]?.textContent?.replace(/\s*\*\s*/g, '').trim()
      || control.getAttribute('aria-label') || 'el campo indicado';
    const validity = control.validity;
    let message = 'Ingresá un valor válido.';
    if (validity.valueMissing) message = 'Completá este campo.';
    else if (validity.typeMismatch && control instanceof HTMLInputElement && control.type === 'email')
      message = 'Ingresá un correo electrónico válido.';
    else if (validity.badInput) message = 'Ingresá un número válido.';
    else if (control instanceof HTMLInputElement) {
      if (validity.rangeUnderflow) message = `El valor mínimo permitido es ${control.min}.`;
      else if (validity.rangeOverflow) message = `El valor máximo permitido es ${control.max}.`;
      else if (validity.stepMismatch) message = control.step === '1'
        ? 'Ingresá un número entero.' : 'Revisá los decimales del valor ingresado.';
    }
    queueMicrotask(() => {
      pending = false;
      showAlert(`Revisá ${label}: ${message}`);
    });
  }, true);
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

export function initTheme(): void {
  const storageKey = 'facturacion-theme';
  let storedTheme: string | null = null;
  try { storedTheme = localStorage.getItem(storageKey); } catch { /* Almacenamiento no disponible. */ }
  const initialTheme: 'light' | 'dark' =
    storedTheme === 'dark' || storedTheme === 'light'
      ? storedTheme
      : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');

  const applyTheme = (theme: 'light' | 'dark'): void => {
    document.documentElement.dataset.theme = theme;
    document.querySelectorAll<HTMLButtonElement>('[data-theme-toggle]').forEach(button => {
      const isDark = theme === 'dark';
      button.setAttribute('aria-label', isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro');
      button.setAttribute('title', isDark ? 'Modo claro' : 'Modo oscuro');
      button.textContent = isDark ? '☀' : '☾';
    });
  };

  applyTheme(initialTheme);
  window.addEventListener('storage', event => {
    if (event.key === storageKey && (event.newValue === 'light' || event.newValue === 'dark')) applyTheme(event.newValue);
  });
  document.querySelectorAll<HTMLButtonElement>('[data-theme-toggle]').forEach(button => {
    button.addEventListener('click', () => {
      const nextTheme: 'light' | 'dark' =
        document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      try { localStorage.setItem(storageKey, nextTheme); } catch { /* Mantener el tema en memoria. */ }
      applyTheme(nextTheme);
    });
  });
}
