import { makeCsv } from './data.js';
import { githubClient, makeBackup, parseBackup, encryptBackup, BACKUP_PATH, DEFAULT_REPOSITORY } from './github-backup.js';

export function createGithubDialog({ getRecords, canOpen, restore, notify }) {
  const dialog = document.createElement('dialog');
  dialog.className = 'github-dialog';
  dialog.innerHTML = `
    <form method="dialog"><button class="text-button github-close" aria-label="Fermer">Fermer ×</button></form>
    <h2>Sauvegarde GitHub</h2>
    <p id="github-status" class="github-result" role="status" aria-live="polite" aria-atomic="true" hidden></p>
    <p>Conservez une copie chiffrée de toutes vos entrées dans le même dépôt que TaskManager. Chaque sauvegarde reste dans l’historique GitHub.</p>
    <label class="field"><span>Dépôt GitHub</span><input id="github-repository" placeholder="propriétaire/dépôt" autocomplete="off" spellcheck="false" /></label>
    <label class="field"><span>Code ou mot de passe de sauvegarde (6 caractères minimum)</span><input id="github-password" type="password" autocomplete="off" minlength="6" /></label>
    <div class="github-actions"><button type="button" class="primary-button" id="github-save">Sauvegarder sur GitHub</button><button type="button" class="github-restore-button" id="github-restore">Restaurer depuis GitHub</button></div>
    <p class="muted">Le mot de passe protège vos données avant leur envoi, même dans un dépôt public. Gardez-le en lieu sûr : il est indispensable pour restaurer.</p>
    <p class="muted">La connexion GitHub utilise le jeton configuré sur ce PC. Le mot de passe de sauvegarde est effacé à la fermeture de cette fenêtre.</p>
    <button type="button" class="text-button" id="github-export-previous">Exporter la copie locale avant restauration (CSV)</button>
    <p class="muted">La restauration remplace les entrées locales après confirmation. Une copie locale de l’état précédent est conservée. Les exports CSV restent disponibles.</p>`;
  document.body.append(dialog);
  const repository = dialog.querySelector('#github-repository');
  const password = dialog.querySelector('#github-password');
  const status = dialog.querySelector('#github-status');
  let busy = false;
  const resultKey = 'mesplacements.github.last-result';
  function showResult(kind, message, remember = false) {
    status.hidden = false;
    status.dataset.kind = kind;
    status.setAttribute('role', kind === 'error' ? 'alert' : 'status');
    status.textContent = message;
    if (remember) {
      try { localStorage.setItem(resultKey, JSON.stringify({ repository: repository.value.trim(), kind, message })); } catch { /* The visible result remains available. */ }
    }
    if (dialog.open) status.scrollIntoView({ block: 'nearest' });
  }
  function previousResult() {
    status.hidden = true;
    status.textContent = '';
    try {
      const result = JSON.parse(localStorage.getItem(resultKey));
      if (result?.repository === repository.value.trim() && ['success', 'error', 'info'].includes(result.kind) && typeof result.message === 'string') showResult(result.kind, result.message);
    } catch { /* No previous result. */ }
  }
  repository.addEventListener('input', previousResult);
  repository.value = DEFAULT_REPOSITORY;
  try { repository.value = localStorage.getItem('mesplacements.github.repository') || DEFAULT_REPOSITORY; } catch { /* Optional preference. */ }
  dialog.addEventListener('cancel', event => { if (busy) event.preventDefault(); });
  dialog.addEventListener('close', () => { password.value = ''; });
  function setBusy(value) {
    busy = value;
    dialog.querySelectorAll('button, input').forEach(element => { element.disabled = value; });
  }
  async function run(action) {
    if (busy || !canOpen()) return;
    setBusy(true);
    showResult('pending', action === 'save' ? 'Sauvegarde GitHub en cours… Connexion au dépôt.' : 'Restauration GitHub en cours… Lecture du dépôt.');
    try {
      const client = githubClient(repository.value);
      if (password.value.length < 6) throw new Error('Renseignez le mot de passe de sauvegarde (6 caractères minimum, chiffres acceptés).');
      const state = await client.inspect(password.value, { decrypt: action !== 'save' });
      try { localStorage.setItem('mesplacements.github.repository', repository.value.trim()); } catch { /* Optional preference. */ }
      if (action === 'save') {
        const text = await encryptBackup(makeBackup(getRecords()), password.value);
        const existing = state.sha ? 'Une sauvegarde existe déjà. Elle sera remplacée par les données locales et restera dans l’historique GitHub. Le code saisi servira à restaurer cette nouvelle sauvegarde.\n\n' : '';
        if (!window.confirm(`${existing}Sauvegarder sous forme chiffrée les ${getRecords().length} entrée(s) locales dans ${repository.value.trim()}/${BACKUP_PATH} ?`)) { showResult('info', 'Sauvegarde GitHub annulée : aucun envoi effectué.', true); return; }
        showResult('pending', 'Sauvegarde GitHub en cours… Envoi puis vérification du fichier.');
        await client.save(text, state);
        showResult('success', `✓ Sauvegarde GitHub réussie — ${getRecords().length} entrée(s) envoyées et vérifiées le ${new Date().toLocaleString('fr-FR')}. Dépôt : ${repository.value.trim()}.`, true);
      } else {
        if (!state.backup) throw new Error('Aucune sauvegarde Mes Placements dans ce dépôt.');
        const backup = state.backup;
        if (!window.confirm(`Restaurer ${backup.records.length} entrée(s) sauvegardées le ${new Date(backup.savedAt).toLocaleString('fr-FR')} ?\n\nLes ${getRecords().length} entrée(s) locales seront remplacées. Une copie locale de l’état précédent sera conservée.`)) { showResult('info', 'Restauration GitHub annulée : les données locales sont conservées.', true); return; }
        if (!await restore(backup.records)) throw new Error('La restauration n’a pas été enregistrée. Les données précédentes sont conservées.');
        showResult('success', `✓ Restauration GitHub réussie — ${backup.records.length} entrée(s) enregistrées sur cet appareil le ${new Date().toLocaleString('fr-FR')}.`, true);
      }
      notify(status.textContent);
    } catch (error) {
      showResult('error', `✕ ${action === 'save' ? 'Échec de la sauvegarde GitHub' : 'Échec de la restauration GitHub'} — ${error.message} (${new Date().toLocaleString('fr-FR')})`, true);
      notify(status.textContent);
    }
    finally { setBusy(false); }
  }
  dialog.querySelector('#github-export-previous').addEventListener('click', () => {
    try {
      const previous = localStorage.getItem('mesplacements.before-github-restore');
      if (!previous) { showResult('info', 'Aucune copie avant restauration pour le moment.'); return; }
      const backup = parseBackup(previous);
      const link = document.createElement('a');
      link.href = URL.createObjectURL(new Blob([makeCsv(backup.records)], { type: 'text/csv;charset=utf-8' }));
      link.download = `mes-placements-avant-restauration-${backup.savedAt.slice(0, 10)}.csv`;
      link.click();
      setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      showResult('info', 'Export CSV local demandé. Cet export n’est pas une sauvegarde GitHub.');
    } catch { showResult('error', 'La copie locale précédente est inaccessible ou illisible.'); }
  });
  dialog.querySelector('#github-save').addEventListener('click', () => run('save'));
  dialog.querySelector('#github-restore').addEventListener('click', () => run('restore'));
  return {
    open() {
      if (!canOpen()) return;
      previousResult();
      dialog.showModal();
      (repository.value ? password : repository).focus();
    },
    isBusy: () => busy,
  };
}
