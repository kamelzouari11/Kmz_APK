import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { readGithubToken, githubRequest, validateGithubRequest } from '../server/github-proxy.js';

test('local.properties takes precedence over shared configuration and is reread after changes', async () => {
  const parent = await mkdtemp(join(tmpdir(), 'mesplacements-token-'));
  const root = join(parent, 'app');
  try {
    await mkdir(root);
    await writeFile(join(parent, 'local.properties'), 'github.token=shared-test-token\n');
    assert.equal(await readGithubToken(root), 'shared-test-token');
    await writeFile(join(root, 'local.properties'), '#github.token=ignored\ngithub.token = local-test-token\n');
    assert.equal(await readGithubToken(root), 'local-test-token');
    await writeFile(join(root, 'local.properties'), 'github.token=replaced-test-token');
    assert.equal(await readGithubToken(root), 'replaced-test-token');
  } finally { await rm(parent, { recursive: true }); }
});
test('proxy attaches stored credentials only upstream and omits error bodies and headers', async () => {
  const root = await mkdtemp(join(tmpdir(), 'mesplacements-proxy-'));
  try {
    await writeFile(join(root, 'local.properties'), 'github.token=test-secret-not-for-browser');
    const result = await githubRequest({ repository: 'owner/repo', path: '', method: 'GET' }, root, async (url, options) => {
      assert.equal(url, 'https://api.github.com/repos/owner/repo');
      assert.equal(options.headers.Authorization, 'Bearer test-secret-not-for-browser');
      assert.equal(options.redirect, 'error');
      return { status: 401, ok: false, json: async () => ({ secret: 'must-not-be-returned' }) };
    });
    assert.deepEqual(result, { status: 401, body: {} });
  } finally { await rm(root, { recursive: true }); }
});
test('proxy rejects unrelated endpoints, URL escapes, destructive methods and plaintext', () => {
  const valid = { repository: 'owner/repo', path: '', method: 'GET' };
  for (const change of [
    { repository: 'owner/../other' }, { repository: 'owner/repo?x=y' },
    { path: '/issues' }, { method: 'DELETE' },
    { path: '/contents/MySharedFolder/task_manager_backup.json' },
    { path: '/contents/MySharedFolder/mes_placements_backup.json', method: 'PUT', body: { content: Buffer.from('{"records":[]}').toString('base64') } },
  ]) assert.throws(() => validateGithubRequest({ ...valid, ...change }));
});
