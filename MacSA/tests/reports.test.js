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
  assert.equal(tables[0].rows.length, 3);
  assert.equal(tables[0].rows[0].at(-2), '100,001');
  assert.equal(tables[0].rows[1].at(-2), '-20,002');
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
  assert.equal(table.title, '2025 · BT');
  assert.equal(table.declarationLabel, undefined);
  assert.equal(table.imposition, undefined);
  assert.equal(table.rows[1][table.head.indexOf('Imposition')], 'Exonéré');
  assert.equal(table.rows[1][table.head.indexOf('Déclaration')], 'Déjà déclaré');
});
