import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { once } from 'node:events';
import { createApp } from '../dist/app.js';

test('proxy forwards query, JSON, response status and PDF headers', async t => {
  const backend = http.createServer(async (request, response) => {
    if (request.url.startsWith('/api/report')) {
      response.writeHead(200, { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="factura.pdf"' });
      response.end('%PDF-test'); return;
    }
    let body = '';
    for await (const chunk of request) body += chunk;
    response.writeHead(409, { 'Content-Type': 'application/problem+json' });
    response.end(JSON.stringify({ detail: 'Duplicado', url: request.url, body: JSON.parse(body) }));
  }).listen(0, '127.0.0.1');
  await once(backend, 'listening');
  const frontend = createApp(`http://127.0.0.1:${backend.address().port}`).listen(0, '127.0.0.1');
  await once(frontend, 'listening');
  t.after(() => { frontend.closeAllConnections(); frontend.close(); backend.closeAllConnections(); backend.close(); });
  const base = `http://127.0.0.1:${frontend.address().port}`;
  const result = await fetch(`${base}/api/clientes?x=1`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ nombre: 'Cliente' }) });
  assert.equal(result.status, 409);
  assert.deepEqual(await result.json(), { detail: 'Duplicado', url: '/api/clientes?x=1', body: { nombre: 'Cliente' } });
  const pdf = await fetch(`${base}/api/report`);
  assert.equal(pdf.headers.get('content-type'), 'application/pdf');
  assert.match(pdf.headers.get('content-disposition'), /factura.pdf/);
  assert.equal(await pdf.text(), '%PDF-test');
});

test('unavailable backend produces a controlled 502', async t => {
  const closed = http.createServer().listen(0, '127.0.0.1'); await once(closed, 'listening');
  const port = closed.address().port;
  await new Promise(resolve => closed.close(resolve));
  const frontend = createApp(`http://127.0.0.1:${port}`).listen(0, '127.0.0.1'); await once(frontend, 'listening');
  t.after(() => { frontend.closeAllConnections(); frontend.close(); });
  const result = await fetch(`http://127.0.0.1:${frontend.address().port}/api/facturas`);
  assert.equal(result.status, 502);
  assert.match((await result.json()).detail, /backend/);
});
