export function byId(id) {
    const element = document.getElementById(id);
    if (!element)
        throw new Error(`No se encontró el elemento HTML #${id}`);
    return element;
}
export function showAlert(message, type = 'error') {
    const alert = byId('alerta');
    alert.textContent = message;
    alert.className = `alert ${type === 'success' ? 'alert-success' : 'alert-error'}`;
}
export function clearAlert() {
    byId('alerta').className = 'alert hidden';
}
export function currency(value) {
    return Number(value ?? 0).toLocaleString('es-AR', { style: 'currency', currency: 'ARS' });
}
export function makeOption(value, label) {
    const option = document.createElement('option');
    option.value = String(value);
    option.textContent = label;
    return option;
}
export function initTheme() {
    const storageKey = 'facturacion-theme';
    const storedTheme = localStorage.getItem(storageKey);
    const initialTheme = storedTheme === 'dark' || storedTheme === 'light'
        ? storedTheme
        : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
    const applyTheme = (theme) => {
        document.documentElement.dataset.theme = theme;
        document.querySelectorAll('[data-theme-toggle]').forEach(button => {
            const isDark = theme === 'dark';
            button.setAttribute('aria-label', isDark ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro');
            button.setAttribute('title', isDark ? 'Modo claro' : 'Modo oscuro');
            button.textContent = isDark ? '☀' : '☾';
        });
    };
    applyTheme(initialTheme);
    document.querySelectorAll('[data-theme-toggle]').forEach(button => {
        button.addEventListener('click', () => {
            const nextTheme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
            localStorage.setItem(storageKey, nextTheme);
            applyTheme(nextTheme);
        });
    });
}
