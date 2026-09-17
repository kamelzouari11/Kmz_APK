import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';
export default defineConfig({
  root: fileURLToPath(new URL('./web', import.meta.url)),
  base: './',
  publicDir: false,
  build: { outDir: fileURLToPath(new URL('./app/src/main/assets', import.meta.url)), emptyOutDir: true, target: 'es2020' },
  server: { host: '127.0.0.1', port: 1422, strictPort: true },
});
