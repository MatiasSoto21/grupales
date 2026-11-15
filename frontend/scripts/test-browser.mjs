import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const browserEnv = { ...process.env };
delete browserEnv.NO_COLOR;
const cli = fileURLToPath(new URL('../node_modules/@playwright/test/cli.js', import.meta.url));
const child = spawn(process.execPath, [cli, 'test', ...process.argv.slice(2)], { stdio: 'inherit', env: browserEnv, windowsHide: true });
child.on('error', error => { console.error(error.message); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
