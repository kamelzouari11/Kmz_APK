import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm, readFile, readdir, writeFile, mkdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { diskStore } from '../server/disk-store.js';
const record = { id: 'one', exercice: 2026, etablissement: 'Épargne', placement: 'Test', revenu: 'Intérêts', montant: '12.345', rs: '0.000', imposition: 'Exonéré', declaration: 'Déjà déclaré' };
async function fixture(fn) { const root = await mkdtemp(join(tmpdir(), 'mesplacements-data-')); try { await fn(diskStore(root), root); } finally { await rm(root, { recursive: true }); } }
test('file persists across store instances and every replacement preserves previous records', () => fixture(async (store, root) => {
  assert.equal((await store.load()).records, null);
  const first = await store.save([record], null);
  assert.equal(first.revision, 1);
  assert.deepEqual((await diskStore(root).load()).records, [record]);
  await store.save([], first.revision);
  const backups = await readdir(join(root, 'data/backups'));
  assert.equal(backups.length, 1);
  assert.deepEqual(JSON.parse(await readFile(join(root, 'data/backups', backups[0]), 'utf8')).records, [record]);
}));
test('stale windows and simultaneous saves cannot overwrite new data', () => fixture(async (store) => {
  await store.save([record], null);
  const results = await Promise.allSettled([store.save([], 1), store.save([{ ...record, montant: '20.000' }], 1)]);
  assert.equal(results.filter(result => result.status === 'fulfilled').length, 1);
  await assert.rejects(store.save([record], 1), /changé/);
  assert.equal((await store.load()).revision, 2);
}));
test('invalid or corrupted data is never replaced with an empty file', () => fixture(async (store, root) => {
  await store.save([record], null);
  await assert.rejects(store.save([{ ...record, montant: 'bad' }], 1));
  await writeFile(join(root, 'data/mesplacements.json'), 'damaged');
  await assert.rejects(store.save([], 1), /endommagé/);
  assert.equal(await readFile(join(root, 'data/mesplacements.json'), 'utf8'), 'damaged');
}));
test('backup failure aborts the write and releases its lock', () => fixture(async (store, root) => {
  await store.save([record], null);
  await writeFile(join(root, 'data/backups'), 'not-a-directory');
  await assert.rejects(store.save([], 1));
  assert.deepEqual((await store.load()).records, [record]);
  assert.equal((await readdir(join(root, 'data'))).includes('.write-lock'), false);
}));
