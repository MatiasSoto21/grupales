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
