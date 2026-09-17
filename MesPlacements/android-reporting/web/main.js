import '../../src/styles.css';
import { createReports } from '../../src/reports.js';
import { nativeRequest } from './bridge.js';
import { readEncryptedBackup, SOURCE, BACKUP_URL } from './reader.js';
import './mobile.css';

let records = [], password = '', cached = null, busy = false;
const isAndroid = Boolean(window.AndroidReports?.request);
const app = document.querySelector('#app');
app.innerHTML = `
  <header class="mobile-header"><div><p class="section-kicker">CONSULTATION</p><h1>Mes Placements</h1></div><button type="button" id="lock" hidden>Verrouiller</button></header>
  <section class="connection" aria-label="Sauvegarde GitHub">
    <div class="source-heading"><h2>Vos données GitHub</h2><span class="readonly-label">Lecture seule</span></div>
    <p>Source : ${SOURCE.repository}</p>
    <form id="unlock-form"><label for="backup-code">Code de la sauvegarde</label><div class="unlock-fields"><input id="backup-code" type="password" minlength="6" autocomplete="off" placeholder="6 caractères minimum" required /><button id="sync" type="submit">Ouvrir la copie locale</button></div></form>
    <div class="connection-actions"><button type="button" id="offline" disabled>Ouvrir la copie hors connexion</button><button type="button" id="refresh" hidden>Actualiser depuis GitHub</button></div>
    <p id="connection-status" role="status" aria-live="polite">Saisissez le code utilisé sur le PC pour la dernière sauvegarde GitHub.</p>
    <p id="backup-info" hidden></p>
  </section>
  <section id="reports-view" hidden aria-label="Rapports"></section>
  <p id="mobile-message" role="status" aria-live="polite" hidden></p>`;
const status = document.querySelector('#connection-status');
function showStatus(message, kind = 'info') { status.textContent = message; status.dataset.kind = kind; }
function notify(message) { const element = document.querySelector('#mobile-message'); element.hidden = false; element.textContent = message; element.dataset.kind = /pas pu|impossible/i.test(message) ? 'error' : 'info'; }
async function savePdf(snapshot) {
  const { createReportPdf } = await import('../../src/report-pdf.js');
  const doc = createReportPdf(snapshot);
  const filename = `mes-placements-${snapshot.settings.type}-${new Date().toISOString().slice(0, 10)}.pdf`;
  if (!isAndroid) { await doc.save(filename, { returnPromise: true }); return true; }
  const bytes = new Uint8Array(doc.output('arraybuffer'));
  if (bytes.length > 15_000_000) throw new Error('PDF trop volumineux.');
  const base64 = btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''));
  const result = await nativeRequest('savePdf', { filename, base64 });
  return result.saved;
}
const reports = createReports(document.querySelector('#reports-view'), () => records, notify, { savePdf, hidePrint: true });
// All report controls are shared with the desktop version; expose the optional filters on mobile.
document.querySelector('.report-options').open = true;
function setBusy(value) {
  busy = value;
  document.querySelectorAll('.connection button, #backup-code, #lock').forEach(element => { element.disabled = value; });
  document.querySelector('#offline').disabled = value || !cached;
}
async function fetchBackup() {
  if (isAndroid) return nativeRequest('fetchBackup');
  const response = await fetch(BACKUP_URL, { headers: { Accept: 'application/vnd.github.raw+json', 'X-GitHub-Api-Version': '2026-03-10' }, cache: 'no-store', signal: AbortSignal.timeout(30_000) });
  if (!response.ok) throw new Error(`Lecture GitHub impossible (${response.status}).`);
  const encrypted = await response.text();
  if (encrypted.length > 900_000) throw new Error('Sauvegarde trop volumineuse.');
  return encrypted;
}
async function load(fromCache) {
  if (busy) return;
  const code = password || document.querySelector('#backup-code').value;
  if (code.length < 6) { showStatus('Saisissez le code de sauvegarde (6 caractères minimum).', 'error'); return; }
  setBusy(true);
  showStatus(fromCache ? 'Ouverture de la copie locale…' : 'Lecture de la sauvegarde GitHub…');
  try {
    const encrypted = fromCache ? cached?.encrypted : await fetchBackup();
    if (!encrypted) throw new Error('Aucune copie hors connexion disponible.');
    const backup = await readEncryptedBackup(encrypted, code);
    let cacheWarning = '';
    const downloadedAt = fromCache ? cached.downloadedAt : new Date().toISOString();
    if (!fromCache) {
      const nextCache = { encrypted, downloadedAt };
      try {
        if (isAndroid) await nativeRequest('writeCache', nextCache);
        else localStorage.setItem('mesplacements.reports.encrypted-cache', JSON.stringify(nextCache));
        cached = nextCache;
      } catch { cacheWarning = ' La copie hors connexion n’a pas pu être enregistrée.'; }
    }
    const firstUnlock = !password;
    records = backup.records; password = code;
    document.querySelector('#backup-code').value = '';
    document.querySelector('#unlock-form').hidden = true;
    document.querySelector('#refresh').hidden = false;
    document.querySelector('#lock').hidden = false;
    document.querySelector('#reports-view').hidden = false;
    const info = document.querySelector('#backup-info'); info.hidden = false;
    info.textContent = `${records.length} entrée(s) · Sauvegarde PC du ${new Date(backup.savedAt).toLocaleString('fr-FR')} · Copie téléchargée le ${new Date(downloadedAt).toLocaleString('fr-FR')}`;
    reports.refresh({ reset: firstUnlock });
    showStatus((fromCache ? 'Copie hors connexion ouverte. Elle peut être plus ancienne que GitHub.' : 'Données GitHub chargées et déchiffrées.') + cacheWarning, cacheWarning ? 'error' : 'success');
  } catch (error) {
    showStatus(`${error.message}${records.length ? ' Les données déjà affichées sont conservées.' : ''}`, 'error');
  } finally { setBusy(false); }
}
document.querySelector('#unlock-form').addEventListener('submit', event => { event.preventDefault(); load(Boolean(cached)); });
document.querySelector('#refresh').addEventListener('click', () => load(false));
document.querySelector('#offline').addEventListener('click', () => load(true));
document.querySelector('#lock').addEventListener('click', () => {
  password = ''; records = [];
  document.querySelector('#reports-view').hidden = true; reports.refresh();
  document.querySelector('#backup-info').hidden = true;
  document.querySelector('#mobile-message').hidden = true;
  document.querySelector('#unlock-form').hidden = false;
  document.querySelector('#refresh').hidden = true;
  document.querySelector('#lock').hidden = true;
  showStatus('Rapports verrouillés. Saisissez votre code pour les ouvrir.');
  document.querySelector('#backup-code').focus();
});
(async () => {
  try { cached = isAndroid ? await nativeRequest('readCache') : JSON.parse(localStorage.getItem('mesplacements.reports.encrypted-cache')); }
  catch { showStatus('La copie locale est inaccessible. Vous pouvez recharger depuis GitHub.', 'error'); }
  document.querySelector('#sync').textContent = cached ? 'Ouvrir la copie locale' : 'Charger depuis GitHub';
  document.querySelector('#offline').disabled = !cached;
})();
