import test from 'node:test';
import assert from 'node:assert/strict';
import { githubClient, makeBackup, parseBackup, encryptBackup, decryptBackup } from '../src/github-backup.js';
const record = { id: 'piece-1', createdAt: '2026-09-17T09:12:54.595Z', exercice: 2025, etablissement: 'Établissement été', placement: 'Épargne', revenu: 'Intérêts', montant: '123.456', rs: '0.000', imposition: 'Exonéré', declaration: 'Déjà déclaré' };
const password = 'test-password-for-backup-only';
const encrypted = await encryptBackup(makeBackup([record]), password);
const response = (body, status = 200) => ({ ok: status >= 200 && status < 300, status, json: async () => body });
const file = text => ({ type: 'file', encoding: 'base64', content: Buffer.from(text).toString('base64'), sha: 'old-sha' });

test('backup preserves accents, dates, statuses and legitimate duplicate pieces', () => {
  const records = [record, { ...record, id: 'piece-2' }];
  assert.deepEqual(parseBackup(makeBackup(records)).records, records);
});
test('rejects malformed backups and duplicate identifiers before restoration', () => {
  assert.throws(() => parseBackup('{}'));
  assert.throws(() => makeBackup([record, record]));
  assert.throws(() => makeBackup([{ ...record, montant: 'invalid' }]));
  assert.throws(() => makeBackup([{ ...record, etablissement: 'x'.repeat(900_000) }]));
});
test('public repositories accept encrypted backups, plaintext uploads are rejected before network access', async () => {
  let calls = 0;
  const client = githubClient('owner/repo', 'test-token', async () => { calls++; return response({ private: false }); });
  await assert.rejects(client.save(makeBackup([record]), { branch: 'main' }), /chiffrée/);
  assert.equal(calls, 0);
});
test('encryption hides records and rejects wrong passwords and tampering', async () => {
  assert.equal(encrypted.includes(record.etablissement), false);
  assert.deepEqual((await decryptBackup(encrypted, password)).records, [record]);
  await assert.rejects(decryptBackup(encrypted, 'incorrect-password-value'), /incorrect/);
  const changed = JSON.parse(encrypted);
  changed.ciphertext = (changed.ciphertext[0] === 'A' ? 'B' : 'A') + changed.ciphertext.slice(1);
  await assert.rejects(decryptBackup(JSON.stringify(changed), password), /altérée/);
  const second = JSON.parse(await encryptBackup(makeBackup([record]), password));
  assert.notEqual(second.salt, JSON.parse(encrypted).salt);
  assert.notEqual(second.iv, JSON.parse(encrypted).iv);
});
test('upload uses optimistic concurrency and verifies exact committed content', async () => {
  const text = encrypted;
  const requests = [];
  const client = githubClient('owner/repo', 'test-token', async (url, options) => {
    requests.push({ url, ...options });
    if (requests.length === 1 || requests.length === 3) return response({ private: true, default_branch: 'main' });
    if (requests.length === 2) return response(file(text));
    if (requests.length === 4) return response({ content: { sha: 'new-sha' }, commit: { sha: 'commit-sha' } }, 201);
    return response(file(text));
  });
  const state = await client.inspect(password);
  await client.save(text, state);
  const body = JSON.parse(requests[3].body);
  assert.equal(body.sha, 'old-sha');
  assert.equal(body.branch, 'main');
  assert.equal(Buffer.from(body.content, 'base64').toString(), text);
  assert.match(requests[4].url, /ref=commit-sha$/);
});
test('missing backup allows creation but authentication failures never masquerade as missing data', async () => {
  for (const status of [401, 403, 500]) {
    const client = githubClient('owner/repo', 'test-token', async url => url.endsWith('/repo') ? response({ private: true, default_branch: 'main' }) : response({}, status));
    await assert.rejects(client.inspect(password));
  }
  const client = githubClient('owner/repo', 'test-token', async url => url.endsWith('/repo') ? response({ private: true, default_branch: 'main' }) : response({}, 404));
  assert.deepEqual(await client.inspect(password), { branch: 'main', sha: undefined, backup: null });
});
test('concurrent writes are not retried over the other backup', async () => {
  let calls = 0;
  const client = githubClient('owner/repo', 'test-token', async () => ++calls === 1 ? response({ private: true }) : response({}, 409));
  await assert.rejects(client.save(encrypted, { branch: 'main', sha: 'stale' }), /changé/);
  assert.equal(calls, 2);
});
test('invalid remote file and verification mismatch are reported', async () => {
  const client = githubClient('owner/repo', 'test-token', async url => url.endsWith('/repo') ? response({ private: true, default_branch: 'main' }) : response(file('{}')));
  await assert.rejects(client.inspect(password), /reconnue/);
  let calls = 0;
  const mismatch = githubClient('owner/repo', 'test-token', async () => {
    calls++;
    if (calls === 1) return response({ private: true });
    if (calls === 2) return response({ content: { sha: 'new' }, commit: { sha: 'revision' } });
    return response(file('{}'));
  });
  await assert.rejects(mismatch.save(encrypted, { branch: 'main' }), /vérification/);
});

test('six digit code works, including leading zeros, while shorter codes are rejected', async () => {
  const text = await encryptBackup(makeBackup([record]), '001234');
  assert.deepEqual((await decryptBackup(text, '001234')).records, [record]);
  await assert.rejects(encryptBackup(makeBackup([record]), '12345'), /6 caractères/);
});
test('saving with a new code reads the previous SHA without requiring the old password', async () => {
  const client = githubClient('owner/repo', 'test-token', async url => url.endsWith('/repo') ? response({ default_branch: 'main' }) : response(file(encrypted)));
  const state = await client.inspect('001234', { decrypt: false });
  assert.equal(state.sha, 'old-sha');
  assert.equal(state.backup, null);
  await assert.rejects(client.inspect('001234'), /incorrect/);
});
