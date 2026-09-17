import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const FILE = '/contents/MySharedFolder/mes_placements_backup.json';
export async function readGithubToken(root) {
  for (const path of [resolve(root, 'local.properties'), resolve(root, '../local.properties')]) {
    let text;
    try { text = await readFile(path, 'utf8'); }
    catch (error) { if (error.code === 'ENOENT') continue; throw new Error('Impossible de lire local.properties.'); }
    const token = text.split(/\r?\n/).map(line => line.trim()).filter(line => !line.startsWith('#'))
      .map(line => line.match(/^github\.token\s*=\s*(.+)$/)?.[1].trim()).find(Boolean);
    if (token) return token;
  }
  throw new Error('Ajoutez github.token dans local.properties, puis réessayez.');
}

export function validateGithubRequest({ repository, path, method, body }) {
  if (typeof repository !== 'string' || !/^[\w-]+\/[\w.-]+$/.test(repository) || repository.split('/').some(part => part === '.' || part === '..')) throw new Error('Dépôt GitHub invalide.');
  if (method === 'GET' && (path === '' || (typeof path === 'string' && path.startsWith(FILE + '?ref=') && !path.slice(FILE.length + 5).includes('&')))) return;
  if (method === 'PUT' && path === FILE && body && typeof body.content === 'string' && body.content.length <= 1_200_000) {
    let data;
    try { data = JSON.parse(Buffer.from(body.content, 'base64').toString('utf8')); } catch { throw new Error('Sauvegarde invalide.'); }
    if (data.application === 'mesplacements.encrypted' && data.version === 1 && typeof data.ciphertext === 'string') return;
  }
  throw new Error('Cette requête GitHub n’est pas autorisée.');
}

export async function githubRequest(input, root, fetcher = fetch) {
  validateGithubRequest(input);
  const token = await readGithubToken(root);
  let response;
  try {
    response = await fetcher(`https://api.github.com/repos/${input.repository}${input.path}`, {
      method: input.method,
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json', 'X-GitHub-Api-Version': '2026-03-10', 'User-Agent': 'MesPlacements', 'Content-Type': 'application/json' },
      ...(input.method === 'PUT' ? { body: JSON.stringify({ message: 'Sauvegarde Mes Placements', content: input.body.content, branch: input.body.branch, ...(input.body.sha ? { sha: input.body.sha } : {}) }) } : {}),
      signal: AbortSignal.timeout(30_000), redirect: 'error',
    });
    // Never return headers, credentials, or upstream error bodies to the web page.
    return { status: response.status, body: response.ok ? await response.json() : {} };
  } catch { throw new Error('GitHub est injoignable. Vérifiez la connexion et le dépôt avant de réessayer.'); }
}

export function githubLocalPlugin(root) {
  return {
    name: 'mesplacements-local-github',
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        // Block direct requests and Vite source transforms for configuration files.
        let pathname;
        try { pathname = decodeURIComponent((req.url || '').split('?')[0]); } catch { res.writeHead(400).end(); return; }
        if (/(^|\/)local\.propert(?:ies|ieties)(?:$|\/)/i.test(pathname)) { res.writeHead(403).end(); return; }
        if (pathname !== '/__mesplacements/github') return next();
        const origin = req.headers.origin;
        const host = req.headers.host;
        if (req.method !== 'POST' || !/^(localhost|127\.0\.0\.1):\d+$/.test(host || '') || origin !== `http://${host}` || req.headers['x-mesplacements'] !== 'github') {
          res.writeHead(403).end(); return;
        }
        res.setHeader('Content-Type', 'application/json');
        res.setHeader('Cache-Control', 'no-store');
        try {
          let size = 0, chunks = [];
          for await (const chunk of req) {
            size += chunk.length;
            if (size > 1_250_000) throw new Error('Sauvegarde trop volumineuse.');
            chunks.push(chunk);
          }
          const result = await githubRequest(JSON.parse(Buffer.concat(chunks).toString('utf8')), root);
          res.end(JSON.stringify(result));
        } catch (error) {
          res.statusCode = 400;
          res.end(JSON.stringify({ error: error instanceof SyntaxError ? 'Requête invalide.' : error.message }));
        }
      });
    },
  };
}
