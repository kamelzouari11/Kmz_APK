import test from 'node:test';
import assert from 'node:assert/strict';
import { buildReport, reportTables, totals, formatMillis } from '../src/report-data.js';
import { createReportPdf } from '../src/report-pdf.js';
const base = { exercice: 2025, etablissement: 'BT', placement: 'CEA', revenu: 'Dividendes', montant: '100.001', rs: '10.000', imposition: 'Imposable', declaration: 'Non encore déclaré' };
const options = { type: 'detail', years: ['2025'], search: '', groupBy: 'etablissement', direction: 'asc' };
const records = [base, { ...base, montant: '-20.002', rs: '0.000' }, { ...base, montant: '0.001', imposition: 'Exonéré', declaration: 'Déjà déclaré' }, { ...base, exercice: 2024 }];
test('totaux exacts au millime et statuts indépendants du regroupement', () => {
  const report = buildReport(records, options);
  assert.equal(report.totals.count, 3);
  assert.equal(report.totals.montant, 80000n);
  assert.equal(report.totals.rs, 20000n);
  assert.equal(report.sections.length, 1);
  assert.equal(report.sections[0].totals.montant, 80000n);
  assert.equal(formatMillis(-1n), '-0,001');
  assert.equal(totals(Array(100).fill({ ...base, montant: '0.001' })).montant, 100n);
});
test('filtres combinés, plusieurs exercices et résultat vide', () => {
  assert.equal(buildReport(records, { ...options, years: ['2024', '2025'], search: 'BT DIV CEA', declaration: 'Déjà déclaré' }).totals.count, 1);
  assert.equal(buildReport(records, { ...options, years: [] }).totals.count, 4);
  assert.equal(buildReport(records, { ...options, etablissement: 'Autre' }).sections.length, 0);
});
test('chaque pièce est conservée dans le détail, état de déclaration regroupé', () => {
  const report = buildReport(records, options);
  const tables = reportTables(report, options);
  const detailRows = tables[0].rows.filter(row => row.kind === 'detail');
  assert.equal(detailRows.length, 3);
  assert.equal(detailRows[0].values.at(-2), '100,001');
  assert.equal(detailRows[1].values.at(-2), '-20,002');
  const detail = buildReport(records, { ...options, type: 'detail' });
  assert.deepEqual(detail.totals, report.totals);
  const statuses = buildReport(records, { ...options, type: 'declaration' });
  assert.equal(statuses.sections.length, 1);
  assert.equal(statuses.sections[0].records.length, 3);
});
test('PDF multipage avec numérotation, filtres et totaux', () => {
  const settings = { ...options, type: 'detail' };
  const report = buildReport(Array.from({ length: 160 }, (_, i) => ({ ...base, revenu: `Dividendes ${i}` })), settings);
  const doc = createReportPdf({ report, tables: reportTables(report, settings), filterLabels: ['Exercice : 2025', 'Établissement : BT'], date: '17/09/2026' });
  assert.ok(doc.getNumberOfPages() > 2);
  const pdf = doc.output();
  assert.ok(pdf.startsWith('%PDF-'));
  assert.ok(pdf.includes('2025'));
  assert.ok(!pdf.includes('Sous-total'));
  assert.ok(!pdf.includes('entrées)'));
  assert.ok(pdf.includes('16 000,160'));
  assert.ok(pdf.includes(`${doc.getNumberOfPages()} / ${doc.getNumberOfPages()}`));
});

test('imposition et déclaration figurent uniquement sur les pièces', () => {
  const report = buildReport([base, { ...base, declaration: 'Déjà déclaré', imposition: 'Exonéré' }], options);
  assert.equal(report.sections.length, 1);
  const table = reportTables(report, options)[0];
  assert.equal(table.title, 'Regroupement hiérarchique');
  assert.equal(table.declarationLabel, undefined);
  assert.equal(table.imposition, undefined);
  const details = table.rows.filter(row => row.kind === 'detail');
  assert.equal(details[1].values[table.head.indexOf('Imposition')], 'Exonéré');
  assert.equal(details[1].values[table.head.indexOf('Déclaration')], 'Déjà déclaré');
});

test('trois niveaux hiérarchiques avec un total pour chaque groupe', () => {
  const settings = { ...options, years: ['2024', '2025'], group1: 'exercice', group2: 'declaration', group3: 'imposition' };
  const report = buildReport(records, settings);
  assert.equal(report.groups.length, 2);
  assert.equal(report.groups[0].level, 1);
  assert.equal(report.groups[0].children[0].level, 2);
  assert.equal(report.groups[0].children[0].children[0].level, 3);
  const totalRows = reportTables(report, settings)[0].rows.filter(row => row.kind === 'total');
  assert.ok(totalRows.some(row => row.level === 1));
  assert.ok(totalRows.some(row => row.level === 2));
  assert.ok(totalRows.some(row => row.level === 3));
});

test('les détails commencent par exercice quand il ne sert pas au regroupement', () => {
  const withoutYear = { ...options, group1: 'declaration', group2: 'imposition', group3: 'revenu' };
  const table = reportTables(buildReport(records, withoutYear), withoutYear)[0];
  assert.equal(table.head[0], 'Exercice');
  assert.equal(table.rows.find(row => row.kind === 'detail').values[0], '2025');

  const withYear = { ...withoutYear, group3: 'exercice' };
  const groupedByYear = reportTables(buildReport(records, withYear), withYear)[0];
  assert.equal(groupedByYear.head[0], 'Établissement');
});
