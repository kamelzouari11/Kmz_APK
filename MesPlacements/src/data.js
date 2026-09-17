export const CSV_HEADERS = ['Exercice', 'Etablissement', 'Placement', 'Revenu', 'Montant', 'RS', 'Imposition', 'Déclaration'];
export const FIELDS = ['exercice', 'etablissement', 'placement', 'revenu', 'montant', 'rs', 'imposition', 'declaration'];
export const DECLARATIONS = ['Non encore déclaré', 'Déjà déclaré'];

export function amount(value, { allowNegative = true } = {}) {
  const text = String(value ?? '').trim().replace(',', '.');
  if (!/^-?\d+(\.\d{1,3})?$/.test(text)) throw new Error('Saisissez un montant avec au maximum trois décimales.');
  const negative = text.startsWith('-');
  const [whole, fraction = ''] = text.replace(/^-/, '').split('.');
  const decimals = fraction.padEnd(3, '0');
  const mills = BigInt(whole) * 1000n + BigInt(decimals);
  if (mills > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('Montant trop élevé.');
  if (negative && mills > 0n && !allowNegative) throw new Error('La retenue à la source doit être positive ou nulle.');
  return `${negative && mills > 0n ? '-' : ''}${BigInt(whole)}.${decimals}`;
}

export function validateRecord(data) {
  const exercice = Number(data.exercice);
  if (!Number.isInteger(exercice) || exercice < 1900 || exercice > 2100) throw new Error('L’exercice doit être une année entre 1900 et 2100.');
  const result = { exercice };
  for (const key of ['etablissement', 'placement', 'revenu']) {
    result[key] = String(data[key] ?? '').trim();
    if (!result[key]) throw new Error('Renseignez l’établissement, le placement et le type de revenu.');
  }
  result.montant = amount(data.montant);
  result.rs = amount(String(data.rs ?? '').trim() || '0', { allowNegative: false });
  if (!['Imposable', 'Exonéré'].includes(data.imposition)) throw new Error('L’imposition doit être « Imposable » ou « Exonéré ».');
  result.imposition = data.imposition;
  result.declaration = data.declaration ?? DECLARATIONS[0];
  if (!DECLARATIONS.includes(result.declaration)) throw new Error('Choisissez « Non encore déclaré » ou « Déjà déclaré ».');
  return result;
}

export function parseCsv(text) {
  text = text.replace(/^\ufeff/, '');
  const rows = [];
  let row = [], cell = '', quoted = false, closed = false;
  const pushCell = () => { row.push(cell); cell = ''; closed = false; };
  const pushRow = () => { pushCell(); if (row.some(value => value.trim())) rows.push(row); row = []; };
  for (let i = 0; i < text.length; i++) {
    const char = text[i];
    if (quoted) {
      if (char === '"') {
        if (text[i + 1] === '"') { cell += '"'; i++; }
        else { quoted = false; closed = true; }
      } else cell += char;
    } else if (char === ';') pushCell();
    else if (char === '\n' || char === '\r') { if (char === '\r' && text[i + 1] === '\n') i++; pushRow(); }
    else if (char === '"' && !cell && !closed) quoted = true;
    else { if (closed || char === '"') throw new Error('CSV mal formé : guillemets incorrects.'); cell += char; }
  }
  if (quoted) throw new Error('CSV mal formé : guillemets non fermés.');
  pushRow();
  if (!rows.length || ![7, 8].includes(rows[0].length) || rows[0].some((value, i) => value.trim() !== CSV_HEADERS[i])) {
    throw new Error(`En-tête attendu : ${CSV_HEADERS.join(';')}`);
  }
  return rows.slice(1).map((values, index) => {
    try {
      if (values.length !== rows[0].length) throw new Error(`${rows[0].length} colonnes sont nécessaires.`);
      return validateRecord(Object.fromEntries(FIELDS.map((key, i) => [key, values[i]])));
    } catch (error) { throw new Error(`Entrée CSV ${index + 1} : ${error.message}`); }
  });
}

export function makeCsv(records) {
  return '\ufeff' + [CSV_HEADERS, ...records.map(record => FIELDS.map(key => key === 'declaration' ? (record[key] ?? DECLARATIONS[0]) : record[key]))]
    .map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(';')).join('\r\n');
}
export function fingerprint(record) { return JSON.stringify(FIELDS.map(key => key === 'declaration' ? (record[key] ?? DECLARATIONS[0]) : record[key])); }

export const SORT_FIELDS = { exercice: 'Exercice', etablissement: 'Établissement', placement: 'Placement', revenu: 'Revenu' };
const frenchOrder = new Intl.Collator('fr', { sensitivity: 'base', numeric: true });
export function sortRecords(records, criteria) {
  return [...records].sort((a, b) => {
    for (const { field, direction } of criteria) {
      if (!Object.hasOwn(SORT_FIELDS, field)) continue;
      const difference = field === 'exercice'
        ? a.exercice - b.exercice
        : frenchOrder.compare(a[field], b[field]);
      if (difference) return direction === 'desc' ? -difference : difference;
    }
    return 0;
  });
}

const normalizeSearch = value => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase('fr');
export function matchesSearch(record, query) {
  const terms = normalizeSearch(query).trim().split(/\s+/).filter(Boolean);
  const text = normalizeSearch([record.etablissement, record.placement, record.revenu, record.imposition, record.declaration].join(' '));
  return terms.every(term => text.includes(term));
}
