import { matchesSearch, sortRecords } from './data.js';

export const REPORT_TYPES = { detail: 'Détail des revenus', declaration: 'Suivi des déclarations' };
export const REPORT_FILTERS = { etablissement: 'Établissement', placement: 'Placement', revenu: 'Revenu', imposition: 'Imposition', declaration: 'Déclaration' };
export function toMillis(value) { return BigInt(String(value).replace('.', '')); }
export function formatMillis(value) {
  const negative = value < 0n;
  const absolute = negative ? -value : value;
  return `${negative ? '-' : ''}${(absolute / 1000n).toLocaleString('fr-FR')},${String(absolute % 1000n).padStart(3, '0')}`;
}
export function totals(records) {
  return records.reduce((total, record) => ({ count: total.count + 1, montant: total.montant + toMillis(record.montant), rs: total.rs + toMillis(record.rs) }), { count: 0, montant: 0n, rs: 0n });
}
export function buildReport(records, options) {
  const filtered = records.filter(record => (!options.years.length || options.years.includes(String(record.exercice)))
    && Object.keys(REPORT_FILTERS).every(key => !options[key] || record[key] === options[key])
    && matchesSearch(record, options.search));
  const ordered = sortRecords(filtered, [{ field: 'exercice', direction: 'desc' }, { field: options.groupBy || 'etablissement', direction: options.direction || 'asc' }, { field: 'etablissement', direction: 'asc' }, { field: 'revenu', direction: 'asc' }, { field: 'placement', direction: 'asc' }]);
  const groups = new Map();
  for (const record of ordered) {
    const parts = [String(record.exercice), record[options.groupBy || 'etablissement']];
    const key = JSON.stringify(parts);
    if (!groups.has(key)) groups.set(key, { title: parts.join(' · '), records: [] });
    groups.get(key).records.push(record);
  }
  const sections = [...groups.values()].map(group => ({ ...group, totals: totals(group.records) }));
  return { title: REPORT_TYPES[options.type], sections, totals: totals(filtered), records: filtered };
}

export function reportTables(report, options) {
  const detailHead = ['Établissement', 'Placement', 'Revenu', 'Imposition', 'Déclaration', 'Montant (TND)', 'RS (TND)'];
  const detailRows = records => records.map(record => [record.etablissement, record.placement, record.revenu, record.imposition, record.declaration, formatMillis(toMillis(record.montant)), formatMillis(toMillis(record.rs))]);
  return report.sections.map(section => ({
    title: section.title,
    head: detailHead,
    rows: detailRows(section.records),
    totals: section.totals,
  }));
}
