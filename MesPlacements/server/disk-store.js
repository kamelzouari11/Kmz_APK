import { mkdir, readFile, open, rename, unlink, copyFile } from 'node:fs/promises';
import { constants } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';
import { parseDiskFile, validateRecords } from '../src/disk-format.js';

export function diskStore(root) {
  const dir = resolve(root, 'data'), path = resolve(dir, 'mesplacements.json');
  async function load() {
    try { return { ...parseDiskFile(await readFile(path, 'utf8')), path }; }
    catch (error) { if (error.code === 'ENOENT') return { records: null, revision: null, path }; throw new Error('Lecture du fichier local impossible ou fichier endommagé. Aucune donnée écrasée.'); }
  }
  async function save(records, revision) {
    records = validateRecords(records);
    await mkdir(dir, { recursive: true, mode: 0o700 });
    const lockPath = resolve(dir, '.write-lock');
    let lock;
    try { lock = await open(lockPath, 'wx', 0o600); }
    catch { throw new Error('Le fichier local est verrouillé par une autre écriture. Réessayez ; si le problème persiste après un arrêt brutal, vérifiez data/.write-lock.'); }
    const temporary = resolve(dir, `.pending-${randomUUID()}`);
    try {
      const current = await load();
      if (revision !== current.revision) throw new Error('Les données ont changé dans une autre fenêtre. Votre saisie reste à l’écran : copiez-la puis rechargez avant de réessayer.');
      const next = { application: 'mesplacements.local', version: 1, revision: (revision || 0) + 1, savedAt: new Date().toISOString(), records };
      if (current.revision !== null) {
        await mkdir(resolve(dir, 'backups'), { recursive: true, mode: 0o700 });
        await copyFile(path, resolve(dir, 'backups', `revision-${current.revision}-${randomUUID()}.json`), constants.COPYFILE_EXCL);
      }
      const file = await open(temporary, 'wx', 0o600);
      try { await file.writeFile(JSON.stringify(next, null, 2) + '\n'); await file.sync(); }
      finally { await file.close(); }
      await rename(temporary, path);
      return { ...next, path };
    } finally {
      await unlink(temporary).catch(() => {});
      await lock.close();
      await unlink(lockPath);
    }
  }
  return { load, save };
}

export function diskLocalPlugin(root) {
  const store = diskStore(root);
  return { name: 'mesplacements-disk', configureServer(server) {
    server.middlewares.use(async (req, res, next) => {
      let path;
      try { path = decodeURIComponent((req.url || '').split('?')[0]); } catch { res.writeHead(400).end(); return; }
      // The data directory must never be served as static files or transformed modules.
      if (path !== '/__mesplacements/data' && /(^|\/)data(?:\/|$)/.test(path)) { res.writeHead(403).end(); return; }
      if (path !== '/__mesplacements/data') return next();
      const host = req.headers.host;
      if (req.method !== 'POST' || !/^(localhost|127\.0\.0\.1):\d+$/.test(host || '') || req.headers.origin !== `http://${host}` || req.headers['x-mesplacements'] !== 'data') { res.writeHead(403).end(); return; }
      res.setHeader('Content-Type', 'application/json'); res.setHeader('Cache-Control', 'no-store');
      try {
        let size = 0; const chunks = [];
        for await (const chunk of req) { size += chunk.length; if (size > 10_000_000) throw new Error('Fichier trop volumineux.'); chunks.push(chunk); }
        const input = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        let result;
        if (input.action === 'load') result = await store.load();
        else if (input.action === 'save') result = await store.save(input.records, input.revision);
        else throw new Error('Opération inconnue.');
        res.end(JSON.stringify(result));
      } catch (error) { res.statusCode = 400; res.end(JSON.stringify({ error: error.message })); }
    });
  } };
}
