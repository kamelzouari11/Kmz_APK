import { REPORT_TYPES, REPORT_FILTERS, buildReport, reportTables } from './report-data.js';
import { formatMoney } from './data.js';
const SORT_FIELDS={year:'Exercice',label:'Valeur / bénéficiaire',type:'Type d’opération',direction:'Sens'};
const formatMillis=formatMoney;
import './reports.css';
const escape = value => String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);

export function createReports(root, getRecords, notify) {
  let initialized = false;
  let current;
  root.innerHTML = `
    <div class="report-heading"><div><p class="section-kicker">PORTEFEUILLE MAC SA</p><h2>Rapports du portefeuille</h2><p class="muted">Filtrez les opérations de votre compte Mac SA.</p></div><div class="report-actions"><button type="button" id="print-report" class="icon-button">Imprimer</button><button type="button" id="pdf-report" class="primary-button">Enregistrer en PDF</button></div></div>
    <form id="report-filters" class="report-filters" aria-label="Préparer le rapport">
      <label>Rapport<select name="reportType">${Object.entries(REPORT_TYPES).map(([value, label]) => `<option value="${value}">${label}</option>`).join('')}</select></label>
      <fieldset class="report-years"><legend>Exercices</legend><div id="report-years"></div><small>Aucun coché : tous les exercices</small></fieldset>
      <label>Recherche<input name="search" type="search" placeholder="Ex. BT DIV CEA" /></label>
      <details class="report-options" open><summary>Filtres et présentation</summary><div class="report-options-grid">${Object.entries(REPORT_FILTERS).map(([key, label]) => `<label>${label}<select name="${key}"><option value="">Tous</option></select></label>`).join('')}
      <label>Regrouper par<select name="groupBy">${['year', 'label', 'type', 'direction'].map(key => `<option value="${key}">${SORT_FIELDS[key]}</option>`).join('')}</select></label>
      <label>Ordre des groupes<select name="groupDirection"><option value="asc">A → Z</option><option value="desc">Z → A</option></select></label>
      </div><button type="button" id="reset-report" class="text-button">Réinitialiser les filtres</button></details>
    </form>
    <div id="report-live" class="sr-only" role="status"></div>
    <article id="report-document" class="report-document"></article>`;
  const form = root.querySelector('form');
  function options() {
    const values = Object.fromEntries(new FormData(form));
    return { ...values, type: values.reportType, years: [...form.querySelectorAll('[name="years"]:checked')].map(input => input.value) };
  }
  function refresh() {
    const records = getRecords();
    const previousYears = [...form.querySelectorAll('[name="years"]:checked')].map(input => input.value);
    const years = [...new Set(records.map(record => record.timestamp ? String(new Date(record.timestamp).getFullYear()) : ''))].filter(Boolean).sort((a, b) => b - a);
    const selected = initialized ? previousYears : years.slice(0, 1);
    root.querySelector('#report-years').innerHTML = years.length ? years.map(year => `<label><input type="checkbox" name="years" value="${year}" ${selected.includes(year) ? 'checked' : ''} /> ${year}</label>`).join('') : '<span>Aucun exercice</span>';
    for (const key of Object.keys(REPORT_FILTERS)) {
      const select = form.elements[key];
      const previous = select.value;
      const sourceKey = key === 'operationType' ? 'type' : key;
      select.innerHTML = '<option value="">Tous</option>' + [...new Set(records.map(record => String(record[sourceKey] ?? '').trim()).filter(Boolean))].sort((a, b) => a.localeCompare(b, 'fr')).map(value => `<option value="${escape(value)}">${escape(value)}</option>`).join('');
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
      `Groupes : ${SORT_FIELDS[settings.groupBy] || 'Valeur'} (${settings.groupDirection === 'asc' ? 'A à Z' : 'Z à A'})`];
    const date = new Date().toLocaleDateString('fr-FR');
    const tables = reportTables(report, settings);
    current = { report, settings, filterLabels, date, tables };
    root.querySelector('#report-live').textContent = `${report.totals.count} entrée(s) dans le rapport`;
    root.querySelector('#print-report').disabled = !report.totals.count;
    root.querySelector('#pdf-report').disabled = !report.totals.count;
    root.querySelector('#report-document').innerHTML = `
      <header class="document-heading"><p class="document-brand">MACSA · PORTEFEUILLE</p><h2>${report.title}</h2><p>${filterLabels.map(escape).join(' · ')}</p><small>Édité le ${date} · Devise : TND · Montants à trois décimales</small></header>
      <p class="report-note">Les montants sont présentés selon les mouvements du compte : crédits positifs, débits négatifs.</p>
      ${tables.length ? tables.map(table => `<section class="report-group"><h3>${escape(table.title)}</h3>${htmlTable(table.head, table.rows)}${table.summary ? `<div class="group-summary"><span>DEBIT <strong>${formatMillis(table.summary.debit)} TND</strong></span><span>CREDIT <strong>${formatMillis(table.summary.credit)} TND</strong></span><span>BILAN <strong>${formatMillis(table.summary.net)} TND</strong></span></div>` : ''}</section>`).join('') : '<p class="empty-state">Aucune entrée ne correspond à ces filtres.</p>'}
      ${tables.length ? `<div class="report-grand-total"><div class="grand-total-heading"><strong>BILAN GENERAL</strong><span>${report.totals.count} opération${report.totals.count === 1 ? '' : 's'}</span></div><div class="grand-total-values"><div class="total-value"><span>DEBIT</span><strong>${formatMillis(report.totals.debit)} <small>TND</small></strong></div><div class="total-value"><span>CREDIT</span><strong>${formatMillis(report.totals.credit)} <small>TND</small></strong></div><div class="total-value"><span>BILAN</span><strong>${formatMillis(report.totals.net)} <small>TND</small></strong></div></div></div>` : ''}`;
  }
  form.addEventListener('submit', event => event.preventDefault());
  form.addEventListener('change', render);
  form.elements.search.addEventListener('input', render);
  root.querySelector('#reset-report').addEventListener('click', () => { form.reset(); for (const checkbox of form.querySelectorAll('[name="years"]')) checkbox.checked = false; render(); });
  root.querySelector('#print-report').addEventListener('click', () => window.print());
  root.querySelector('#pdf-report').addEventListener('click', async () => {
    const snapshot = current;
    const button = root.querySelector('#pdf-report');
    button.disabled = true;
    button.textContent = 'Préparation…';
    try { const { saveReportPdf } = await import('./report-pdf.js'); await saveReportPdf(snapshot); notify('PDF préparé : enregistrement demandé.'); }
    catch (error) { console.error(error); notify('Le PDF n’a pas pu être créé. Vous pouvez utiliser Imprimer puis Enregistrer en PDF.'); }
    finally { button.disabled = !current.report.totals.count; button.textContent = 'Enregistrer en PDF'; }
  });
  return { refresh };
}

function htmlTable(head, rows) {
  return `<div class="report-table-wrap"><table class="report-table"><thead><tr>${head.map((label, i) => `<th scope="col" class="${i >= head.length - 2 ? 'number' : ''}">${escape(label)}</th>`).join('')}</tr></thead><tbody>${rows.map(row => `<tr>${row.map((value, i) => `<td class="${i >= row.length - 2 ? 'number' : ''}">${statusLabel(value, head[i])}</td>`).join('')}</tr>`).join('')}</tbody></table></div>`;
}

function statusLabel(value, column) {
  const kind = column === 'Imposition' && value === 'Exonéré' ? 'exempt'
    : column === 'Déclaration' && value === 'Déjà déclaré' ? 'declared' : null;
  if (kind) return `<span class="report-badge report-badge-${kind}">${escape(value)}</span>`;
  if (column === 'Imposition' || column === 'Déclaration') return `<span class="report-status">${escape(value)}</span>`;
  return column === 'Référence' ? `<span title="${escape(value)}" class="report-reference">${escape(value)}</span>` : escape(value);
}
