import { createApp } from './app.js';

const port = Number(process.env.PORT ?? 3000);
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  console.error('PORT debe ser un entero entre 1 y 65535.');
  process.exitCode = 1;
} else {
  try {
    const server = createApp().listen(port, () => console.log(`Frontend listo en http://localhost:${port}`));
    server.on('error', (error: NodeJS.ErrnoException) => {
      console.error(error.code === 'EADDRINUSE' ? `El puerto ${port} esta ocupado. Detene la instancia anterior o cambia PORT.` : error.message);
      process.exitCode = 1;
    });
  } catch (error: unknown) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
