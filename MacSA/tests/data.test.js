import test from 'node:test';
import assert from 'node:assert/strict';
import { amount, validateRecord, makeCsv, parseCsv, fingerprint, sortRecords, SORT_FIELDS, matchesSearch } from '../src/data.js';

const record = { exercice: 2025, etablissement: 'Banque; "Tunis"\nAgence', placement: 'SICAV', revenu: 'Intérêts', montant: '1234.567', rs: '0.000', imposition: 'Exonéré', declaration: 'Non encore déclaré' };
test('montants exacts avec virgule, zéro et trois décimales', () => {
  assert.equal(amount('00012,340'), '12.340');
  assert.equal(amount('0'), '0.000');
  assert.equal(amount('0.001'), '0.001');
  for (const value of ['', 'NaN', 'Infinity', '--1', '-1.2345', '1.2345', '1e3', '9007199254740992']) assert.throws(() => amount(value));
});
test('aller-retour CSV avec BOM, accents, séparateurs, guillemets et sauts de ligne', () => {
  assert.deepEqual(parseCsv(makeCsv([record, { ...record, montant: '0.000' }])), [record, { ...record, montant: '0.000' }]);
});
test('import invalide rejeté intégralement', () => {
  assert.throws(() => parseCsv('autre;format\n1;2'));
  assert.throws(() => parseCsv(makeCsv([record]) + '\r\n2025;Banque'));
  assert.throws(() => parseCsv(makeCsv([record]).slice(0, -1)));
  assert.throws(() => parseCsv(makeCsv([{ ...record, montant: 'invalide' }])));
});
test('validation des champs et RS par défaut', () => {
  assert.equal(validateRecord({ ...record, rs: '' }).rs, '0.000');
  for (const change of [{ exercice: 2025.5 }, { etablissement: '  ' }, { revenu: '' }, { imposition: 'Autre' }]) assert.throws(() => validateRecord({ ...record, ...change }));
});
test('détection des doublons indépendante de leur identifiant', () => {
  assert.equal(fingerprint(record), fingerprint({ ...record, id: 'autre' }));
  assert.notEqual(fingerprint(record), fingerprint({ ...record, exercice: 2024 }));
});

test('pertes : signe conservé en saisie et dans le CSV, y compris sous un dinar', () => {
  for (const [input, expected] of [['-125,750', '-125.750'], ['-0.001', '-0.001'], ['-0001.2', '-1.200'], ['-0', '0.000']]) {
    const loss = validateRecord({ ...record, montant: input });
    assert.equal(loss.montant, expected);
    assert.deepEqual(parseCsv(makeCsv([loss])), [loss]);
  }
  assert.throws(() => validateRecord({ ...record, rs: '-0.001' }), /retenue à la source/);
  assert.throws(() => amount('-9007199254740992'));
});

test('déclaration : migration des anciennes données et anciens CSV', () => {
  const { declaration, ...legacy } = record;
  assert.equal(validateRecord(legacy).declaration, 'Non encore déclaré');
  const csv = 'Exercice;Etablissement;Placement;Revenu;Montant;RS;Imposition\n2025;Banque;Actions;Plus-value;-10.125;0;Imposable';
  assert.equal(parseCsv(csv)[0].declaration, 'Non encore déclaré');
  const declared = { ...record, declaration: 'Déjà déclaré' };
  assert.deepEqual(parseCsv(makeCsv([declared])), [declared]);
  assert.throws(() => validateRecord({ ...record, declaration: 'Autre' }));
  assert.throws(() => parseCsv(makeCsv([{ ...record, declaration: '' }])));
});

test('tri à plusieurs niveaux, français, stable et sans modification des données', () => {
  const rows = [
    { exercice: 2024, etablissement: 'Épargne', placement: 'SICAV', revenu: 'Intérêts', id: 1 },
    { exercice: 2025, etablissement: 'Zebra', placement: 'Actions', revenu: 'Dividendes', id: 2 },
    { exercice: 2025, etablissement: 'Amen', placement: 'SICAV', revenu: 'Intérêts', id: 3 },
    { exercice: 2025, etablissement: 'Amen', placement: 'Actions', revenu: 'Intérêts', id: 4 },
    { exercice: 2025, etablissement: 'Amen', placement: 'Actions', revenu: 'Dividendes', id: 5 },
  ];
  const criteria = ['exercice', 'etablissement', 'placement', 'revenu'].map(field => ({ field, direction: field === 'exercice' ? 'desc' : 'asc' }));
  assert.deepEqual(sortRecords(rows, criteria).map(row => row.id), [5, 4, 3, 2, 1]);
  assert.deepEqual(rows.map(row => row.id), [1, 2, 3, 4, 5]);
  assert.deepEqual(sortRecords(rows, []).map(row => row.id), [1, 2, 3, 4, 5]);
  assert.deepEqual(sortRecords(rows.filter(row => row.exercice === 2025), criteria).map(row => row.id), [5, 4, 3, 2]);
  assert.equal(sortRecords(rows, [{ field: 'etablissement', direction: 'desc' }])[0].id, 2);
  assert.deepEqual(Object.keys(SORT_FIELDS), ['exercice', 'etablissement', 'placement', 'revenu']);
});

test('recherche de plusieurs termes partiels dans des champs différents', () => {
  const entry = { ...record, etablissement: 'BT', placement: 'CEA', revenu: 'Dividendes' };
  assert.equal(matchesSearch(entry, 'BT DIV CEA'), true);
  assert.equal(matchesSearch(entry, ' cea   div BT '), true);
  assert.equal(matchesSearch(entry, 'BT DIV SICAV'), false);
  assert.equal(matchesSearch(entry, '   '), true);
  assert.equal(matchesSearch(record, 'INTERETS tunis'), true);
});
