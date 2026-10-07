import { REPORT_TYPES, REPORT_FILTERS, REPORT_GROUP_FIELDS, REPORT_GROUP_LABELS, buildReport, reportTables, formatMillis } from './report-data.js';
import './reports.css';
const escape = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);

export function createReports(root, getRecords, notify, actions = {}) {
  let initialized = false;
  let current;
  root.innerHTML = `
    <div class="report-heading"><div><p class="section-kicker">VUE D’ENSEMBLE</p><h2>Vos rapports</h2><p class="muted">Choisissez votre période et retrouvez les revenus à déclarer.</p></div><div class="report-actions"><button type="button" id="print-report" class="icon-button">Imprimer</button><button type="button" id="pdf-report" class="primary-button">Enregistrer en PDF</button></div></div>
    <form id="report-filters" class="report-filters" aria-label="Préparer le rapport">
      <label>Rapport<select name="type">${Object.entries(REPORT_TYPES).map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}</select></label>
      <fieldset class="report-years"><legend>Exercices</legend><div id="report-years"></div></fieldset>
      <label>Recherche<input name="search" type="search" placeholder="Ex. BT DIV CEA" /></label>
      <div class="report-grouping">
      ${[1, 2, 3].map((level, index) => `<label>Niveau ${level}<select name="group${level}">${REPORT_GROUP_FIELDS.map((key, optionIndex) => `<option value="${key}" ${optionIndex === [5, 4, 3][index] ? 'selected' : ''}>${REPORT_GROUP_LABELS[key]}</option>`).join('')}</select></label>`).join('')}
      <label>Ordre des groupes<select name="direction"><option value="asc">A → Z</option><option value="desc">Z → A</option></select></label>
      <p>Chaque niveau est imbriqué dans le précédent et dispose de son propre total.</p></div>
      <details class="report-options"><summary>Filtres supplémentaires</summary><div class="report-options-grid">${Object.entries(REPORT_FILTERS).map(([key, label]) => `<label>${label}<select name="${key}"><option value="">Tous</option></select></label>`).join('')}
      </div><button type="button" id="reset-report" class="text-button">Réinitialiser les filtres</button></details>
    </form>
    <div id="report-live" class="sr-only" role="status"></div>
    <article id="report-document" class="report-document"></article>`;
  const form = root.querySelector('form');
  function options() {
    return { ...Object.fromEntries(new FormData(form)), years: [...form.querySelectorAll('[name="years"]:checked')].map(input => input.value) };
  }
  function refresh({ reset = false } = {}) {
    if (reset) { initialized = false; form.reset(); }
    const records = getRecords();
    const previousYears = [...form.querySelectorAll('[name="years"]:checked')].map(input => input.value);
    const recordedYears = [...new Set(records.map(record => String(record.exercice)))].sort((a, b) => b - a);
    const years = [...new Set([String(new Date().getFullYear()), ...recordedYears])].sort((a, b) => b - a);
    const selected = initialized ? previousYears : (recordedYears.length ? recordedYears : years).slice(0, 1);
    root.querySelector('#report-years').innerHTML = years.length ? `<label class="all-years"><input type="checkbox" data-all-years /> Tous</label>` + years.map(year => `<label><input type="checkbox" name="years" value="${year}" ${selected.includes(year) ? 'checked' : ''} /> ${year}</label>`).join('') : '<span>Aucun exercice</span>';
    syncAllYears();
    for (const key of Object.keys(REPORT_FILTERS)) {
      const select = form.elements[key];
      const previous = select.value;
      select.innerHTML = '<option value="">Tous</option>' + [...new Set(records.map(record => record[key]))].sort((a, b) => a.localeCompare(b, 'fr')).map(value => `<option value="${escape(value)}">${escape(value)}</option>`).join('');
      select.value = [...select.options].some(option => option.value === previous) ? previous : '';
    }
    initialized = years.length > 0;
    render();
  }
  function render() {
    const settings = options();
    const report = buildReport(getRecords(), settings);
    const filterLabels = [`Exercices : ${settings.years.length ? settings.years.join(', ') : 'tous'}`,
      ...Object.entries(REPORT_FILTERS).filter(([key]) => settings[key]).map(([key, label]) => `${label} : ${settings[key]}`),
      ...(settings.search.trim() ? [`Recherche : ${settings.search.trim()}`] : []),
      `Groupes : ${[settings.group1, settings.group2, settings.group3].map(key => REPORT_GROUP_LABELS[key]).join(' → ')} (${settings.direction === 'asc' ? 'A à Z' : 'Z à A'})`];
    const date = new Date().toLocaleDateString('fr-FR');
    const tables = reportTables(report, settings);
    current = { report, settings, filterLabels, date, tables };
    root.querySelector('#report-live').textContent = `${report.totals.count} entrée(s) dans le rapport`;
    root.querySelector('#print-report').disabled = !report.totals.count;
    root.querySelector('#pdf-report').disabled = !report.totals.count;
    root.querySelector('#report-document').innerHTML = `
      <header class="document-heading"><p class="document-brand">MES PLACEMENTS</p><h2>${report.title}</h2><p>${filterLabels.map(escape).join(' · ')}</p><small>Édité le ${date} · Devise : TND · Montants à trois décimales</small></header>
      <p class="report-note">Les totaux sont arithmétiques, pertes comprises. L’imposition et la déclaration sont indiquées sur chaque pièce. Aucun impôt ni compensation fiscale n’est calculé.</p>
      ${tables.length ? tables.map(table => `<section class="report-group"><h3>${escape(table.title)}</h3>${htmlTable(table.head, table.rows)}</section>`).join('') : '<p class="empty-state">Aucune entrée ne correspond à ces filtres.</p>'}
      ${tables.length ? `<div class="report-grand-total"><div class="total-caption"><span class="total-kicker">RÉCAPITULATIF</span><strong>Total général</strong><small>${report.totals.count} pièce${report.totals.count === 1 ? '' : 's'} dans le rapport sélectionné</small></div><div class="total-value"><span>Montant total</span><strong>${formatMillis(report.totals.montant)} <small>TND</small></strong></div><div class="total-value"><span>Retenue à la source</span><strong>${formatMillis(report.totals.rs)} <small>TND</small></strong></div></div>` : ''}`;
  }
  function syncGroupSelectors(changed) {
    const selects = [form.elements.group1, form.elements.group2, form.elements.group3];
    if (changed) {
      const duplicate = selects.find(select => select !== changed && select.value === changed.value);
      if (duplicate) {
        const replacement = REPORT_GROUP_FIELDS.find(field => !selects.some(select => select !== duplicate && select.value === field));
        if (replacement) duplicate.value = replacement;
      }
    }
    for (const select of selects) {
      for (const option of select.options) option.disabled = option.value !== select.value && selects.some(other => other !== select && other.value === option.value);
    }
  }
  form.addEventListener('submit', event => event.preventDefault());
  function syncAllYears() {
    const all = form.querySelector('[data-all-years]');
    if (!all) return;
    const years = [...form.querySelectorAll('[name="years"]')];
    all.checked = years.length > 0 && years.every(input => input.checked);
    all.indeterminate = years.some(input => input.checked) && !all.checked;
  }
  form.addEventListener('change', event => {
    if (event.target.matches('[name^="group"]')) syncGroupSelectors(event.target);
    if (event.target.matches('[data-all-years]')) for (const input of form.querySelectorAll('[name="years"]')) input.checked = event.target.checked;
    syncAllYears();
    render();
  });
  form.elements.search.addEventListener('input', render);
  root.querySelector('#reset-report').addEventListener('click', () => {
    form.reset();
    for (const checkbox of form.querySelectorAll('[name="years"]')) checkbox.checked = true;
    syncGroupSelectors(); syncAllYears(); render();
  });
  root.querySelector('#print-report').hidden = actions.hidePrint === true;
  root.querySelector('#print-report').addEventListener('click', async () => {
    const button = root.querySelector('#print-report');
    button.disabled = true;
    try {
      const { previewReportPdf } = await import('./report-pdf.js');
      await previewReportPdf(current);
      notify('Aperçu avant impression ouvert.');
    } catch (error) {
      console.error(error);
      notify('L’aperçu avant impression n’a pas pu être ouvert.');
    } finally { button.disabled = !current.report.totals.count; }
  });
  root.querySelector('#pdf-report').addEventListener('click', async () => {
    const snapshot = current;
    const button = root.querySelector('#pdf-report');
    button.disabled = true;
    button.textContent = 'Préparation…';
    try {
      if (actions.savePdf) { const result = await actions.savePdf(snapshot); notify(result === false ? 'Enregistrement du PDF annulé.' : 'PDF enregistré.'); }
      else { const { saveReportPdf } = await import('./report-pdf.js'); await saveReportPdf(snapshot); notify('Aperçu PDF ouvert.'); }
    }
    catch (error) { console.error(error); notify(actions.savePdf ? 'Le PDF n’a pas pu être enregistré. Réessayez.' : 'Le PDF n’a pas pu être créé. Vous pouvez utiliser Imprimer puis Enregistrer en PDF.'); }
    finally { button.disabled = !current.report.totals.count; button.textContent = 'Enregistrer en PDF'; }
  });
  syncGroupSelectors();
  return { refresh };
}

function htmlTable(head, rows) {
  return `<div class="report-table-wrap"><table class="report-table"><thead><tr>${head.map((label, i) => `<th scope="col" class="${i >= head.length - 2 ? 'number' : ''}">${escape(label)}</th>`).join('')}</tr></thead><tbody>${rows.map(item => {
    const row = item.values || item;
    if (item.kind === 'group') return `<tr class="report-row-group level-${item.level}"><td colspan="${head.length}">${escape(row[0])}</td></tr>`;
    if (item.kind === 'total') return `<tr class="report-row-total level-${item.level}"><td colspan="${head.length - 2}">${escape(row[0])}</td><td class="number" data-label="${escape(head.at(-2))}">${escape(row.at(-2))}</td><td class="number" data-label="${escape(head.at(-1))}">${escape(row.at(-1))}</td></tr>`;
    return `<tr class="report-row-detail">${row.map((value, i) => `<td data-label="${escape(head[i])}" class="${i >= row.length - 2 ? 'number' : ''}">${statusLabel(value, head[i])}</td>`).join('')}</tr>`;
  }).join('')}</tbody></table></div>`;
}

function statusLabel(value, column) {
  const kind = column === 'Imposition' && value === 'Exonéré' ? 'exempt'
    : column === 'Imposition' && value === 'Net d’impôts' ? 'net-tax'
    : column === 'Déclaration' && value === 'Déjà déclaré' ? 'declared' : null;
  if (kind) return `<span class="report-badge report-badge-${kind}">${escape(value)}</span>`;
  if (column === 'Imposition' || column === 'Déclaration') return `<span class="report-status">${escape(value)}</span>`;
  return escape(value);
}
