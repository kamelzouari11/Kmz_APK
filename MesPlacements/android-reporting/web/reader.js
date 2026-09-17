import { decryptBackup, DEFAULT_REPOSITORY, BACKUP_PATH } from '../../src/github-backup.js';
export const SOURCE = { repository: DEFAULT_REPOSITORY, path: BACKUP_PATH };
export const BACKUP_URL = `https://api.github.com/repos/${SOURCE.repository}/contents/${SOURCE.path}`;
export async function readEncryptedBackup(text, password) {
  const backup = await decryptBackup(text, password);
  return { records: backup.records, savedAt: backup.savedAt };
}
