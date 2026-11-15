import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as api from '../dist/api.js';

test('invoice API helpers request a single server page even when more pages exist', async t => {
  const calls = [];
  const response = { content: [{ id: 42 }], number: 3, size: 10, totalPages: 1000, totalElements: 10000, first: false, last: false };
  t.mock.method(globalThis, 'fetch', async url => {
    calls.push(new URL(url, 'http://localhost'));
    return new Response(JSON.stringify(response), { status: 200 });
  });
  const options = { page: 3, size: 10, sortBy: 'total', sortDir: 'desc' };
  for (const [invoke, filters] of [
    [() => api.getFacturasByCliente(7, options), { clienteId: '7' }],
    [() => api.getFacturasByNumero(15, options), { numero: '15' }],
    [() => api.getFacturasByNumeroRango(10, 20, options), { numeroDesde: '10', numeroHasta: '20' }],
    [() => api.getFacturasByFilters({ ...options, estado: 'EMITIDA' }), { estado: 'EMITIDA' }]
  ]) {
    const previous = calls.length;
    assert.deepEqual(await invoke(), response);
    assert.equal(calls.length, previous + 1);
    for (const [key, value] of Object.entries({ ...options, ...filters })) {
      assert.equal(calls.at(-1).searchParams.get(key), String(value));
    }
  }
  assert.equal(api.getAllFacturas, undefined);
});
