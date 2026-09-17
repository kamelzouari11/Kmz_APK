import { defineConfig } from 'vite';
import { fileURLToPath } from 'node:url';
import { diskLocalPlugin } from './server/disk-store.js';
import { githubLocalPlugin } from './server/github-proxy.js';

export default defineConfig({
  plugins: [diskLocalPlugin(fileURLToPath(new URL('.', import.meta.url))), githubLocalPlugin(fileURLToPath(new URL('.', import.meta.url)))],
  server: {
    host: '127.0.0.1', port: 1420, strictPort: true,
    watch: { ignored: [fileURLToPath(new URL('./data/**', import.meta.url))] },
    fs: { deny: ['.env', '.env.*', '*.{crt,pem}', '**/.git/**', '**/local.properties', '**/local.proprieties', fileURLToPath(new URL('./data/**', import.meta.url))] },
  },
});
