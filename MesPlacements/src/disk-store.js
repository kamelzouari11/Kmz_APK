import { validateRecords } from './disk-format.js';

export function createDiskStore() {
  let revision = null;
  let path = '';
  async function request(input) {
    let data;
    if (globalThis.window?.__TAURI_INTERNALS__) {
      const { invoke } = await import('@tauri-apps/api/core');
      try { data = await invoke('data_request', { request: input }); }
      catch (error) { throw new Error(String(error)); }
    } else {
      let response;
      try { response = await fetch('/__mesplacements/data', { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-MesPlacements': 'data' }, body: JSON.stringify(input), cache: 'no-store' }); }
      catch { throw new Error('Le fichier local est inaccessible. Vérifiez que le serveur de l’application fonctionne.'); }
      try { data = await response.json(); } catch { throw new Error('Redémarrez npm run dev pour activer le fichier de données local.'); }
      if (!response.ok) throw new Error(data.error || 'Enregistrement du fichier impossible.');
    }
    if (data.records !== null) data.records = validateRecords(data.records);
    revision = data.revision; path = data.path;
    return data;
  }
  return {
    load: () => request({ action: 'load' }),
    save: records => request({ action: 'save', records, revision }),
    get path() { return path; },
  };
}
