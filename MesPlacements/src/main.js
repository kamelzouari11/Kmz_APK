import './styles.css';
import { createReports } from './reports.js';
import { createGithubDialog } from './github-dialog.js';
import { makeBackup } from './github-backup.js';
import { createDiskStore } from './disk-store.js';

import { validateRecord, parseCsv, makeCsv, fingerprint, DECLARATIONS, SORT_FIELDS, sortRecords, matchesSearch } from './data.js';

async function start() {
const STORAGE_KEY = 'mesplacements.records';
let storageError = '';
let editingId = null;
let entryActive = false;
let toastTimer;
let sortCriteria = [{ field: 'exercice', direction: 'desc' }, { field: 'etablissement', direction: 'asc' }];
const defaultRecord = { exercice: new Date().getFullYear(), etablissement: '', placement: '', revenu: '', montant: '0.000', rs: '0.000', imposition: 'Imposable', declaration: DECLARATIONS[0] };

const disk = createDiskStore();
let saving = false;
let records = [];
document.querySelector('#app').textContent = 'Chargement du fichier de données…';
try {
  const stored = await disk.load();
  if (stored.records === null) {
    records = loadRecords();
    if (storageError) throw new Error(storageError);
    await disk.save(records);
  } else {
    records = stored.records;
  }
} catch (error) { storageError = error.message; }


const app = document.querySelector('#app');
app.innerHTML = `
  <main class="shell">
    <header class="topbar">
      <div class="brand">
        <img class="brand-mark" src="/mesplacements.svg" alt="" />
        <div><p class="eyebrow">GESTION FISCALE</p><h1>Mes Placements</h1></div>
      </div>
      <div class="topbar-actions">
        <span class="save-state"><i></i><span id="save-state-label">Fichier local</span></span>
        <button class="icon-button" id="import-button" title="Importer un fichier CSV" aria-label="Importer un fichier CSV">⇩ Importer</button>
        <button class="icon-button" id="export-button" title="Exporter en CSV" aria-label="Exporter en CSV">⇧ Exporter</button>
        <button class="icon-button" id="github-button" type="button" title="Sauvegarder ou restaurer avec GitHub">GitHub</button>
        <input id="file-input" type="file" accept=".csv,text/csv" hidden />
      </div>
    </header>

    <nav class="view-nav" aria-label="Navigation principale"><button type="button" data-view="entry" aria-pressed="true">Saisie</button><button type="button" data-view="reports" aria-pressed="false">Rapports</button></nav>
    <div id="entry-view">
    <section class="intro">
      <div><p class="section-kicker">NOUVELLE SAISIE</p><h2>Ajouter un revenu</h2><p class="muted">Centralisez vos revenus de placements pour retrouver rapidement les informations de votre déclaration.</p></div>
      <div class="record-count"><strong id="record-count">0</strong><span>enregistrement<span id="record-plural">s</span></span></div>
    </section>

    <section class="workspace">
      <form class="entry-card" id="entry-form">
        <div class="card-heading"><div><h3>Détails du revenu</h3><p>Les champs marqués d’un astérisque sont nécessaires.</p></div><span class="form-step">01 <b>/</b> 01</span></div>
        <div class="field-grid">
          <label class="field"><span>Exercice <em>*</em></span><input name="exercice" type="text" inputmode="numeric" pattern="[0-9]{4}" maxlength="4" autocomplete="off" required /></label>
          <label class="field field-wide"><span>Établissement <em>*</em></span><input name="etablissement" list="etablissements" placeholder="Ex. BIAT, Amen Bank..." required /><datalist id="etablissements"></datalist></label>
          <label class="field"><span>Placement <em>*</em></span><input name="placement" list="placements" placeholder="Ex. SICAV, action..." required /><datalist id="placements"></datalist></label>
          <label class="field"><span>Revenu <em>*</em></span><input name="revenu" list="revenus" placeholder="Dividendes, intérêts..." required /><datalist id="revenus"></datalist></label>
          <label class="field"><span>Montant <em>*</em></span><div class="input-with-suffix"><input name="montant" type="text" inputmode="decimal" autocomplete="off" placeholder="0.000" required /><span>TND</span></div></label>
          <label class="field"><span>Retenue à la source (RS)</span><div class="input-with-suffix"><input name="rs" type="text" inputmode="decimal" autocomplete="off" placeholder="0.000" /><span>TND</span></div></label>
          <fieldset class="field field-wide"><legend>Imposition <em>*</em></legend><div class="choice-row"><label class="choice"><input type="radio" name="imposition" value="Imposable" checked /><span>Imposable</span></label><label class="choice"><input type="radio" name="imposition" value="Exonéré" /><span>Exonéré</span></label></div></fieldset>
          <fieldset class="field field-wide"><legend>Déclaration <em>*</em></legend><div class="choice-row">${DECLARATIONS.map((value, index) => `<label class="choice"><input type="radio" name="declaration" value="${value}" ${index === 0 ? 'checked' : ''} /><span>${value}</span></label>`).join('')}</div></fieldset>
        </div>
        <p id="entry-notice" class="entry-notice" role="status" hidden>Saisie en cours : enregistrez ou annulez avant de quitter cette fiche.</p><p id="form-error" class="error-message" role="alert" hidden></p><div class="form-footer"><p class="tip"><span>i</span> Entrée : champ suivant. Cliquez sur Enregistrer pour valider.</p><button class="text-button" id="cancel-edit" type="button">Annuler</button><button class="primary-button" id="save-entry" type="button"><span>Enregistrer</span><b>↗</b></button></div>
      </form>

      <aside class="side-panel"><div class="side-icon">▤</div><h3>Votre suivi commence ici</h3><p>Chaque entrée sera conservée sur cet appareil et pourra être exportée en CSV pour vos archives ou votre déclaration.</p><div class="side-line"></div><div class="side-note"><span>⌁</span><div><strong>Format prêt pour la suite</strong><small>Les rapports fiscaux pourront s’appuyer sur ces données.</small></div></div></aside>
    </section>

    <section class="recent-section"><div class="recent-heading"><div><p class="section-kicker">HISTORIQUE</p><h2>Vos entrées</h2></div><button class="text-button" id="clear-button" type="button">Effacer l’historique</button></div><div class="history-tools"><input id="search" type="search" placeholder="Rechercher… ex. BT DIV CEA" title="Tous les mots sont recherchés, dans n’importe quel ordre et dans les différents champs." aria-label="Rechercher dans les entrées" /><select id="year-filter" aria-label="Filtrer par exercice"><option value="">Tous les exercices</option></select><span id="visible-count" aria-live="polite"></span></div><details class="sort-panel"><summary>Trier les entrées <span id="sort-summary"></span></summary><div id="sort-criteria"></div><button type="button" class="text-button" id="add-sort">+ Ajouter un critère</button></details><div id="records-list"></div></section>
    </div><section id="reports-view" hidden aria-label="Rapports"></section>
  </main>
  <div class="toast" id="toast" role="status"></div>
`;

const reports = createReports(document.querySelector('#reports-view'), () => records, showToast);
document.querySelectorAll('[data-view]').forEach(button => button.addEventListener('click', () => {
  if (entryActive) { showToast('Enregistrez ou annulez la saisie avant de changer d’écran.'); return; }
  const reporting = button.dataset.view === 'reports';
  document.querySelector('#entry-view').hidden = reporting;
  document.querySelector('#reports-view').hidden = !reporting;
  document.body.classList.toggle('report-mode', reporting);
  document.querySelectorAll('[data-view]').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
  if (reporting) reports.refresh();
}));
const githubDialog = createGithubDialog({
  getRecords: () => records,
  canOpen: () => {
    if (entryActive || storageError) { showToast(storageError || 'Enregistrez ou annulez la saisie avant de continuer.'); return false; }
    return true;
  },
  restore: async next => {
    if (entryActive || storageError) return false;
    try {
      localStorage.setItem('mesplacements.before-github-restore', makeBackup(records));
    } catch { showError('Impossible de conserver la copie de sécurité locale. Restauration annulée.'); return false; }
    if (!await commit(next)) return false;
    resetForm(); render();
    return true;
  },
  notify: showToast,
});
document.querySelector('#github-button').addEventListener('click', () => githubDialog.open());
const entryForm = document.querySelector('#entry-form');
resetForm();
// Select numeric values on entry, including when focus comes from Enter or Tab.
for (const name of ['exercice', 'montant', 'rs']) {
  const input = entryForm.elements[name];
  let selectOnMouseUp = false;
  input.addEventListener('mousedown', () => {
    selectOnMouseUp = document.activeElement !== input;
  });
  input.addEventListener('focus', () => input.select());
  input.addEventListener('mouseup', event => {
    if (selectOnMouseUp) { event.preventDefault(); input.select(); }
    selectOnMouseUp = false;
  });
}
entryForm.addEventListener('submit', event => event.preventDefault());
entryForm.addEventListener('input', () => setEntryActive(true));
entryForm.addEventListener('change', () => setEntryActive(true));
entryForm.addEventListener('keydown', event => {
  if (event.key !== 'Enter' || event.isComposing) return;
  event.preventDefault();
  const fields = [...entryForm.querySelectorAll('input:not([type="radio"]), input[type="radio"]:checked')];
  const index = fields.indexOf(event.target);
  if (index >= 0) (fields[index + 1] || document.querySelector('#save-entry')).focus();
});
window.addEventListener('beforeunload', event => {
  if (!entryActive && !saving && !githubDialog.isBusy()) return;
  event.preventDefault();
  event.returnValue = '';
});
// In the desktop app, closing is blocked until Save or Cancel.
if (window.__TAURI_INTERNALS__) {
  import('@tauri-apps/api/window').then(({ getCurrentWindow }) =>
    getCurrentWindow().onCloseRequested(event => {
      if (entryActive || saving || githubDialog.isBusy()) { event.preventDefault(); showToast('Terminez la saisie ou le transfert GitHub avant de fermer.'); }
    })
  ).catch(() => showError('La protection de fermeture est indisponible. Enregistrez votre saisie avant de fermer.'));
}
document.querySelector('#save-entry').addEventListener('click', async () => {
  if (!entryForm.reportValidity()) return;
  try {
    const record = validateRecord(Object.fromEntries(new FormData(entryForm)));
    const next = editingId
      ? records.map(item => item.id === editingId ? { ...item, ...record } : item)
      : [{ ...record, id: crypto.randomUUID(), createdAt: new Date().toISOString() }, ...records];
    if (!await commit(next)) return;
    const message = editingId ? 'Entrée modifiée' : 'Entrée ajoutée et conservée localement';
    resetForm(record.exercice); render(); showToast(message);
    entryForm.elements.etablissement.focus();
  } catch (error) { showError(error.message); }
});
document.querySelector('#cancel-edit').addEventListener('click', () => { resetForm(); entryForm.elements.etablissement.focus(); });
document.querySelector('#add-sort').addEventListener('click', () => {
  if (entryActive) return;
  const field = Object.keys(SORT_FIELDS).find(key => !sortCriteria.some(item => item.field === key));
  if (!field) return;
  sortCriteria.push({ field, direction: 'asc' });
  renderSort(); render();
  document.querySelector('#sort-criteria').lastElementChild.querySelector('select').focus();
});
document.querySelector('#sort-criteria').addEventListener('change', event => {
  if (entryActive) return;
  const index = Number(event.target.dataset.index);
  if (event.target.dataset.kind === 'field') sortCriteria[index].field = event.target.value;
  else sortCriteria[index].direction = event.target.value;
  renderSort(); render();
  document.querySelector(`[data-index="${index}"][data-kind="${event.target.dataset.kind}"]`).focus();
});
document.querySelector('#sort-criteria').addEventListener('click', event => {
  const button = event.target.closest('[data-remove]');
  if (!button || entryActive) return;
  sortCriteria.splice(Number(button.dataset.remove), 1);
  renderSort(); render();
  document.querySelector('#add-sort').focus();
});
document.querySelector('#search').addEventListener('input', render);
document.querySelector('#year-filter').addEventListener('change', render);
document.querySelector('#clear-button').addEventListener('click', async () => {
  if (entryActive) return;
  if (!records.length || !window.confirm('Effacer définitivement toutes les entrées de cet appareil ? Pensez à exporter un CSV avant de continuer.')) return;
  if (await commit([])) { resetForm(); render(); showToast('Historique effacé'); }
});
document.querySelector('#export-button').addEventListener('click', exportCsv);
document.querySelector('#import-button').addEventListener('click', () => { if (!entryActive) document.querySelector('#file-input').click(); });
document.querySelector('#file-input').addEventListener('change', importCsv);

function setEntryActive(active) {
  entryActive = active;
  document.querySelector('#entry-notice').hidden = !active;
  for (const selector of ['.topbar-actions', '.recent-section', '.view-nav']) {
    const section = document.querySelector(selector);
    section.inert = active;
    section.classList.toggle('entry-locked', active);
  }
}

function resetForm(year = entryForm.elements.exercice.value || defaultRecord.exercice) {
  editingId = null;
  setEntryActive(false);
  entryForm.reset();
  entryForm.elements.exercice.value = year;
  entryForm.elements.montant.value = defaultRecord.montant;
  entryForm.elements.rs.value = defaultRecord.rs;
  entryForm.querySelector('.primary-button span').textContent = 'Enregistrer';
  document.querySelector('.intro h2').textContent = 'Ajouter un revenu';
  document.querySelector('#form-error').hidden = true;
}
function showError(message) {
  const error = document.querySelector('#form-error'); error.textContent = message; error.hidden = false;
  showToast(message);
}
async function commit(next) {
  if (saving) return false;
  if (storageError) { showError(storageError); return false; }
  saving = true;
  app.inert = true;
  const label = document.querySelector('#save-state-label');
  label.textContent = 'Enregistrement…';
  try {
    const saved = await disk.save(next);
    records = saved.records;
    // A browser copy remains useful, but disk is the source of truth.
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(records)); } catch { /* Disk save already succeeded. */ }
    label.textContent = 'Enregistré sur le PC';
    label.title = disk.path;
    return true;
  } catch (error) {
    label.textContent = 'Échec de l’enregistrement';
    showError(error.message);
    return false;
  } finally { saving = false; app.inert = false; }
}

function renderSort() {
  document.querySelector('#sort-summary').textContent = sortCriteria.length
    ? sortCriteria.map(item => `${SORT_FIELDS[item.field]} ${item.direction === 'asc' ? '↑' : '↓'}`).join(' · ')
    : 'Ordre de saisie';
  document.querySelector('#sort-criteria').innerHTML = sortCriteria.map((item, index) => `
    <div class="sort-row"><span>${index === 0 ? 'D’abord' : 'Puis'}</span>
    <select data-index="${index}" data-kind="field" aria-label="Champ de tri ${index + 1}">${Object.entries(SORT_FIELDS).filter(([key]) => key === item.field || !sortCriteria.some(other => other.field === key)).map(([key, label]) => `<option value="${key}" ${key === item.field ? 'selected' : ''}>${label}</option>`).join('')}</select>
    <select data-index="${index}" data-kind="direction" aria-label="Sens du tri ${index + 1}"><option value="asc" ${item.direction === 'asc' ? 'selected' : ''}>${item.field === 'exercice' ? 'Plus ancien d’abord' : 'A → Z'}</option><option value="desc" ${item.direction === 'desc' ? 'selected' : ''}>${item.field === 'exercice' ? 'Plus récent d’abord' : 'Z → A'}</option></select>
    <button type="button" class="text-button" data-remove="${index}" aria-label="Retirer le tri ${SORT_FIELDS[item.field]}">Retirer</button></div>`).join('');
  document.querySelector('#add-sort').disabled = sortCriteria.length === Object.keys(SORT_FIELDS).length;
}

function render() {
  if (!document.querySelector('#reports-view').hidden) reports.refresh();
  document.querySelector('#record-count').textContent = records.length;
  document.querySelector('#record-plural').textContent = records.length === 1 ? '' : 's';
  renderSuggestions();
  const yearFilter = document.querySelector('#year-filter');
  const selected = yearFilter.value;
  yearFilter.innerHTML = '<option value="">Tous les exercices</option>' + [...new Set([new Date().getFullYear(), ...records.map(record => record.exercice)])].sort((a, b) => b - a).map(year => `<option value="${year}">${year}</option>`).join('');
  yearFilter.value = [...yearFilter.options].some(option => option.value === selected) ? selected : '';
  const query = document.querySelector('#search').value;
  const visible = sortRecords(records.filter(record => (!yearFilter.value || String(record.exercice) === yearFilter.value) && matchesSearch(record, query)), sortCriteria);
  document.querySelector('#visible-count').textContent = `${visible.length} / ${records.length}`;
  const list = document.querySelector('#records-list');
  if (!records.length) {
    list.innerHTML = '<div class="empty-state"><span>＋</span><p>Aucune entrée pour le moment</p><small>Votre première saisie apparaîtra ici.</small></div>';
    return;
  }
  if (!visible.length) { list.innerHTML = '<p class="empty-state">Aucune entrée ne correspond à votre recherche.</p>'; return; }
  list.innerHTML = visible.map((record) => `
    <article class="record-row"><div class="record-main"><span class="record-year">${record.exercice}</span><div><strong>${escapeHtml(record.etablissement)}</strong><small>${escapeHtml(record.placement)} · ${escapeHtml(record.revenu)}</small></div></div><div class="record-amount"><strong>${formatAmount(record.montant)} <small>TND</small></strong><span>RS ${formatAmount(record.rs)} · ${escapeHtml(record.imposition)}</span></div><label class="record-declaration"><span>Déclaration</span><select class="declaration-select${record.declaration === 'Déjà déclaré' ? ' is-declared' : ''}" data-id="${escapeHtml(record.id)}" aria-label="Déclaration de ${escapeHtml(record.etablissement)} (${record.exercice})">${DECLARATIONS.map(value => `<option value="${value}" ${record.declaration === value ? 'selected' : ''}>${value}</option>`).join('')}</select></label><button class="text-button edit-button" data-id="${escapeHtml(record.id)}" aria-label="Modifier cette entrée">Modifier</button><button class="delete-button" data-id="${escapeHtml(record.id)}" title="Supprimer" aria-label="Supprimer cette entrée">×</button></article>
  `).join('');
  list.querySelectorAll('.declaration-select').forEach(select => select.addEventListener('change', async () => {
    const record = records.find(item => item.id === select.dataset.id);
    if (entryActive || !DECLARATIONS.includes(select.value)) { select.value = record.declaration; return; }
    const declaration = select.value;
    if (await commit(records.map(item => item.id === record.id ? { ...item, declaration } : item))) {
      render();
      [...list.querySelectorAll('.declaration-select')].find(item => item.dataset.id === record.id)?.focus();
      showToast('Déclaration mise à jour');
    } else select.value = record.declaration;
  }));
  list.querySelectorAll('.edit-button').forEach(button => button.addEventListener('click', () => {
    if (entryActive) return;
    const record = records.find(item => item.id === button.dataset.id);
    setEntryActive(true);
    editingId = record.id;
    for (const key of Object.keys(defaultRecord)) entryForm.elements[key].value = record[key];
    document.querySelector('#cancel-edit').hidden = false;
    entryForm.querySelector('.primary-button span').textContent = 'Enregistrer';
    document.querySelector('.intro h2').textContent = 'Modifier un revenu';
    document.querySelector('#form-error').hidden = true;
    entryForm.scrollIntoView({ behavior: 'smooth', block: 'start' });
    entryForm.elements.etablissement.focus({ preventScroll: true });
  }));
  list.querySelectorAll('.delete-button').forEach(button => button.addEventListener('click', async () => {
    if (entryActive || !window.confirm('Supprimer cette entrée ?')) return;
    if (await commit(records.filter(record => record.id !== button.dataset.id))) {
      if (editingId === button.dataset.id) resetForm();
      render(); showToast('Entrée supprimée');
    }
  }));
}

function renderSuggestions() {
  const fields = { etablissements: 'etablissement', placements: 'placement', revenus: 'revenu' };
  Object.entries(fields).forEach(([id, key]) => { document.querySelector(`#${id}`).innerHTML = [...new Set(records.map((record) => record[key]).filter(Boolean))].sort().map((value) => `<option value="${escapeHtml(value)}"></option>`).join(''); });
}
function loadRecords() {
  try {
    const loaded = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
    if (!Array.isArray(loaded)) throw new Error();
    const ids = new Set();
    return loaded.map(item => {
      const record = validateRecord(item);
      const id = typeof item.id === 'string' && !ids.has(item.id) ? item.id : crypto.randomUUID();
      ids.add(id);
      return { ...record, id, createdAt: item.createdAt };
    });
  } catch {
    storageError = 'Les données locales sont illisibles ou inaccessibles. Elles n’ont pas été écrasées. Une récupération est nécessaire avant de poursuivre.';
    return [];
  }
}
function formatAmount(value) { return Number(value || 0).toLocaleString('fr-FR', { minimumFractionDigits: 3, maximumFractionDigits: 3 }); }
function escapeHtml(value) { return String(value).replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#039;', '"': '&quot;' })[character]); }
function showToast(message) { const toast = document.querySelector('#toast'); clearTimeout(toastTimer); toast.textContent = message; toast.classList.add('visible'); toastTimer = setTimeout(() => toast.classList.remove('visible'), 6500); }
function exportCsv() {
  if (entryActive) return;
  if (storageError) { showError(storageError); return; }
  const blob = new Blob([makeCsv(records)], { type: 'text/csv;charset=utf-8' });
  const link = document.createElement('a'); link.href = URL.createObjectURL(blob);
  link.download = `mes-placements-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 1000);
  showToast('Export CSV demandé');
}
async function importCsv(event) {
  const file = event.target.files[0]; if (!file) return;
  event.target.value = '';
  if (entryActive) return;
  try {
    const imported = parseCsv(await file.text());
    if (entryActive) { showToast('Enregistrez ou annulez la saisie avant d’importer.'); return; }
    const seen = new Set(records.map(fingerprint));
    const unique = imported.filter(record => { const key = fingerprint(record); if (seen.has(key)) return false; seen.add(key); return true; });
    const skipped = imported.length - unique.length;
    if (!unique.length) { showToast(`Aucune nouvelle entrée. ${skipped} doublon(s) ignoré(s).`); return; }
    if (!window.confirm(`Ajouter ${unique.length} entrée(s) ? ${skipped} doublon(s) exact(s) seront ignorés. Les entrées existantes seront conservées.`)) return;
    if (await commit([...unique.map(record => ({ ...record, id: crypto.randomUUID(), createdAt: new Date().toISOString() })), ...records])) {
      render(); showToast(`${unique.length} entrée(s) importée(s), ${skipped} doublon(s) ignoré(s).`);
    }
  } catch (error) { showError(`Import annulé : ${error.message}`); }
}

document.querySelector('#save-state-label').title = disk.path;
renderSort();
render();
if (storageError) {
  document.querySelector('#save-state-label').textContent = 'Stockage à vérifier';
  showError(storageError);
}

}
start().catch(() => { document.querySelector('#app').textContent = 'L’application n’a pas pu démarrer. Vos fichiers de données sont conservés.'; });
