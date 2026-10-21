import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(currentDirectory, '..');
const app = express();
const port = Number(process.env.PORT ?? 3000);

app.use('/dist', express.static(path.join(projectRoot, 'dist')));
app.use(express.static(path.join(projectRoot, 'public')));
app.get('/', (_request, response) => response.sendFile(path.join(projectRoot, 'public', 'index.html')));
app.listen(port, () => console.log(`Frontend TypeScript listo en http://localhost:${port}`));
