import { matchesSearch, sortRecords } from './data.js';

export const REPORT_TYPES = { detail: 'Détail des revenus', declaration: 'Suivi des déclarations' };
export const REPORT_FILTERS = { etablissement: 'Établissement', placement: 'Placement', revenu: 'Revenu', imposition: 'Imposition', declaration: 'Déclaration' };
export const REPORT_GROUP_FIELDS = ['declaration', 'imposition', 'revenu', 'placement', 'etablissement', 'exercice'];
export const REPORT_GROUP_LABELS = { declaration: 'Déclaration', imposition: 'Imposition', revenu: 'Revenu', placement: 'Placement', etablissement: 'Établissement', exercice: 'Exercice' };
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
  const groupFields = [options.group1 || options.groupBy || 'exercice', options.group2 || 'etablissement', options.group3 || 'placement'];
  const ordered = sortRecords(filtered, [
    ...groupFields.map(field => ({ field, direction: options.direction || 'asc' })),
    { field: 'etablissement', direction: 'asc' }, { field: 'revenu', direction: 'asc' }, { field: 'placement', direction: 'asc' },
  ]);
  function groupLevel(items, level = 0) {
    if (level === groupFields.length) return [];
    const groups = new Map();
    for (const record of items) {
      const value = String(record[groupFields[level]]);
      if (!groups.has(value)) groups.set(value, []);
      groups.get(value).push(record);
    }
    return [...groups].map(([value, groupRecords]) => ({
      level: level + 1, field: groupFields[level], value, records: groupRecords,
      totals: totals(groupRecords), children: groupLevel(groupRecords, level + 1),
    }));
  }
  const groups = groupLevel(ordered);
  // `sections` remains available to integrations that used the former top-level groups.
  return { title: REPORT_TYPES[options.type], groups, sections: groups, groupFields, totals: totals(filtered), records: filtered };
}

export function reportTables(report, options) {
  const showExercice = !(report.groupFields || []).includes('exercice');
  const detailHead = [...(showExercice ? ['Exercice'] : []), 'Établissement', 'Placement', 'Revenu', 'Imposition', 'Déclaration', 'Montant (TND)', 'RS (TND)'];
  const detailRows = records => records.map(record => [...(showExercice ? [String(record.exercice)] : []), record.etablissement, record.placement, record.revenu, record.imposition, record.declaration, formatMillis(toMillis(record.montant)), formatMillis(toMillis(record.rs))]);
  const blankRow = () => Array(detailHead.length).fill('');
  const rows = [];
  function append(groups) {
    for (const group of groups) {
      const caption = `${REPORT_GROUP_LABELS[group.field]} : ${group.value}`;
      const heading = blankRow();
      heading[0] = `Niveau ${group.level} · ${caption}`;
      rows.push({ kind: 'group', level: group.level, values: heading });
      if (group.children.length) append(group.children);
      else rows.push(...detailRows(group.records).map(values => ({ kind: 'detail', values })));
      const total = blankRow();
      total[0] = `Total niveau ${group.level} · ${caption} (${group.totals.count})`;
      total[total.length - 2] = formatMillis(group.totals.montant);
      total[total.length - 1] = formatMillis(group.totals.rs);
      rows.push({ kind: 'total', level: group.level, values: total });
    }
  }
  append(report.groups || []);
  return rows.length ? [{ title: 'Regroupement hiérarchique', head: detailHead, rows }] : [];
}
