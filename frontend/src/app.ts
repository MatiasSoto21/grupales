import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

export function createApp(backendUrl = process.env.BACKEND_URL ?? 'http://localhost:8080') {
  const upstream = new URL(backendUrl);
  if (!['http:', 'https:'].includes(upstream.protocol)) throw new Error('BACKEND_URL debe usar HTTP o HTTPS');
  const app = express();
  app.disable('x-powered-by');
  app.use('/api', express.json({ limit: '1mb' }));
  app.use('/api', async (request, response) => {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 120_000);
    response.on('close', () => controller.abort());
    try {
      const headers: Record<string, string> = {};
      for (const name of ['accept', 'content-type']) {
        const value = request.get(name);
        if (value) headers[name] = value;
      }
      const result = await fetch(new URL(request.originalUrl, upstream.origin), {
        method: request.method, headers, signal: controller.signal,
        body: ['GET', 'HEAD'].includes(request.method) || request.body === undefined
          ? undefined : JSON.stringify(request.body)
      });
      const body = Buffer.from(await result.arrayBuffer());
      for (const name of ['content-type', 'content-disposition', 'cache-control']) {
        const value = result.headers.get(name);
        if (value) response.setHeader(name, value);
      }
      response.status(result.status).end(body);
    } catch (error: unknown) {
      if (response.destroyed) return;
      const timedOut = controller.signal.aborted;
      response.status(timedOut ? 504 : 502).type('application/problem+json').json({
        status: timedOut ? 504 : 502,
        detail: timedOut ? 'El backend no respondio a tiempo.' : 'No se pudo conectar con el backend de facturacion.'
      });
    } finally { clearTimeout(timeout); }
  });
  app.use('/dist', express.static(path.join(projectRoot, 'dist')));
  app.use(express.static(path.join(projectRoot, 'public')));
  app.use((error: unknown, _request: express.Request, response: express.Response, _next: express.NextFunction) => {
    const status = typeof error === 'object' && error !== null && 'status' in error ? Number(error.status) : 500;
    response.status(status >= 400 && status <= 599 ? status : 500).json({
      detail: status === 413 ? 'La solicitud supera el limite permitido.' : 'No se pudo procesar la solicitud.'
    });
  });
  return app;
}
