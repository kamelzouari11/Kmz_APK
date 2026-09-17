export async function localGithubFetch(url, options = {}) {
  const prefix = 'https://api.github.com/repos/';
  if (!url.startsWith(prefix)) throw new Error('Destination GitHub invalide.');
  const match = url.slice(prefix.length).match(/^([^/]+\/[^/?]+)(.*)$/);
  if (!match) throw new Error('Destination GitHub invalide.');
  const request = { repository: match[1], path: match[2], method: options.method || 'GET', ...(options.body ? { body: JSON.parse(options.body) } : {}) };
  let result;
  if (globalThis.window?.__TAURI_INTERNALS__) {
    const { invoke } = await import('@tauri-apps/api/core');
    try { result = await invoke('github_request', { request }); }
    catch (error) { throw new Error(typeof error === 'string' ? error : 'Connexion GitHub indisponible.'); }
  } else {
    const response = await fetch('/__mesplacements/github', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-MesPlacements': 'github' },
      body: JSON.stringify(request), signal: options.signal, cache: 'no-store',
    });
    try { result = await response.json(); }
    catch { throw new Error('Redémarrez npm run dev pour activer la connexion GitHub locale.'); }
    if (!response.ok) throw new Error(result.error || 'Connexion GitHub locale indisponible.');
  }
  return { status: result.status, ok: result.status >= 200 && result.status < 300, json: async () => result.body };
}
