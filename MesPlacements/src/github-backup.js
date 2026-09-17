import { localGithubFetch } from './github-transport.js';
import { validateRecord } from './data.js';

export const DEFAULT_REPOSITORY = 'kamelzouari11/Kmz_APK';
export const BACKUP_PATH = 'MySharedFolder/mes_placements_backup.json';
const MAX_BYTES = 900_000;

export function parseBackup(text) {
  if (new TextEncoder().encode(text).length > MAX_BYTES) throw new Error('Sauvegarde trop volumineuse (maximum 900 Ko).');
  const backup = JSON.parse(text);
  if (backup?.application !== 'mesplacements' || backup.version !== 1 || !Array.isArray(backup.records) || !Number.isFinite(Date.parse(backup.savedAt))) {
    throw new Error('Ce fichier n’est pas une sauvegarde Mes Placements reconnue.');
  }
  const ids = new Set();
  const records = backup.records.map(item => {
    const record = validateRecord(item);
    if (typeof item.id !== 'string' || !item.id || ids.has(item.id)) throw new Error('Identifiants manquants ou dupliqués dans la sauvegarde.');
    ids.add(item.id);
    return { ...record, id: item.id, ...(item.createdAt ? { createdAt: item.createdAt } : {}) };
  });
  return { ...backup, records };
}

export function makeBackup(records) {
  const text = JSON.stringify({ application: 'mesplacements', version: 1, savedAt: new Date().toISOString(), records }, null, 2);
  parseBackup(text);
  return text;
}

function encode(text) {
  return btoa(Array.from(new TextEncoder().encode(text), byte => String.fromCharCode(byte)).join(''));
}
function decode(content) {
  return new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(atob(content.replace(/\s/g, '')), char => char.charCodeAt(0)));
}

export function githubClient(repository, token = '', fetcher = localGithubFetch) {
  repository = repository.trim();
  token = token.trim();
  if (!/^[\w-]+\/[\w.-]+$/.test(repository) || repository.split('/').some(part => part === '.' || part === '..')) throw new Error('Indiquez le dépôt sous la forme propriétaire/dépôt.');
  const base = `https://api.github.com/repos/${repository}`;
  async function request(path, { method = 'GET', body, missing = false } = {}) {
    let response;
    try {
      response = await fetcher(base + path, {
        method, headers: { Accept: 'application/vnd.github+json', ...(token ? { Authorization: `Bearer ${token}` } : {}), 'X-GitHub-Api-Version': '2026-03-10', ...(body ? { 'Content-Type': 'application/json' } : {}) },
        ...(body ? { body: JSON.stringify(body) } : {}),
        signal: AbortSignal.timeout(30_000), cache: 'no-store', redirect: 'error',
      });
    } catch (error) {
      if (fetcher === localGithubFetch && error instanceof Error && error.name !== 'TimeoutError') throw error;
      throw new Error('GitHub est injoignable ou le délai est dépassé. Vérifiez la connexion ; en cas d’envoi, vérifiez le dépôt avant de réessayer.');
    }
    if (missing && response.status === 404) return null;
    if (!response.ok) {
      const messages = { 401: 'Jeton GitHub invalide ou expiré.', 403: 'Accès refusé : vérifiez les droits du jeton et les limites GitHub.', 404: 'Dépôt ou sauvegarde introuvable, ou accès non autorisé.', 409: 'La sauvegarde a changé sur GitHub. Relancez l’opération.', 422: 'GitHub a refusé l’écriture. Vérifiez les droits et les règles de la branche.' };
      throw new Error(messages[response.status] || `Erreur GitHub (${response.status}).`);
    }
    return response.json();
  }
  async function repositoryInfo() { return request(''); }
  async function file(branch, missing = false) {
    const result = await request(`/contents/${BACKUP_PATH}?ref=${encodeURIComponent(branch)}`, { missing });
    if (result && (result.type !== 'file' || result.encoding !== 'base64' || typeof result.content !== 'string' || !result.sha)) throw new Error('Fichier GitHub invalide ou trop volumineux.');
    return result;
  }
  return {
    async inspect(password, { decrypt = true } = {}) {
      const repo = await repositoryInfo();
      const current = await file(repo.default_branch, true);
      // Never silently replace an unrelated or unreadable file.
      let backup = null;
      if (current) {
        const text = decode(current.content);
        parseEncryptedBackup(text);
        if (decrypt) backup = await decryptBackup(text, password);
      }
      return { branch: repo.default_branch, sha: current?.sha, backup };
    },
    async save(text, state) {
      parseEncryptedBackup(text);
      await repositoryInfo();
      const result = await request(`/contents/${BACKUP_PATH}`, { method: 'PUT', body: {
        message: 'Sauvegarde Mes Placements', content: encode(text), branch: state.branch, ...(state.sha ? { sha: state.sha } : {}),
      } });
      if (!result.content?.sha) throw new Error('Réponse GitHub inattendue : vérifiez le dépôt avant de réessayer.');
      // Read the exact committed revision, not a possibly newer branch tip.
      const verified = await file(result.commit?.sha || state.branch);
      if (!verified || decode(verified.content) !== text) throw new Error('Envoi effectué, mais la vérification a échoué. Vérifiez le dépôt.');
    },
  };
}

// Web Crypto: PBKDF2-SHA256 derives a non-exportable AES-256-GCM key.
const ITERATIONS = 600_000;
const AAD = new TextEncoder().encode('mesplacements.encrypted.v1');
const toBase64 = bytes => btoa(Array.from(bytes, byte => String.fromCharCode(byte)).join(''));
function fromBase64(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9+/]*={0,2}$/.test(value)) throw new Error('Sauvegarde chiffrée invalide.');
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}
function parseEncryptedBackup(text) {
  if (new TextEncoder().encode(text).length > MAX_BYTES) throw new Error('Sauvegarde chiffrée trop volumineuse (maximum 900 Ko).');
  const envelope = JSON.parse(text);
  if (envelope?.application !== 'mesplacements.encrypted' || envelope.version !== 1 || envelope.algorithm !== 'AES-256-GCM' || envelope.kdf !== 'PBKDF2-SHA256' || envelope.iterations !== ITERATIONS) {
    throw new Error('Ce fichier n’est pas une sauvegarde chiffrée Mes Placements reconnue.');
  }
  const salt = fromBase64(envelope.salt), iv = fromBase64(envelope.iv), ciphertext = fromBase64(envelope.ciphertext);
  if (salt.length !== 16 || iv.length !== 12 || ciphertext.length < 16) throw new Error('Sauvegarde chiffrée invalide.');
  return { salt, iv, ciphertext };
}
async function deriveKey(password, salt) {
  if (typeof password !== 'string' || password.length < 6) throw new Error('Utilisez un mot de passe de sauvegarde d’au moins 6 caractères (un code à 6 chiffres convient).');
  if (!globalThis.crypto?.subtle) throw new Error('Le chiffrement est indisponible dans cet environnement.');
  const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey({ name: 'PBKDF2', salt, iterations: ITERATIONS, hash: 'SHA-256' }, material, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
}
export async function encryptBackup(text, password) {
  parseBackup(text);
  const salt = crypto.getRandomValues(new Uint8Array(16)), iv = crypto.getRandomValues(new Uint8Array(12));
  const key = await deriveKey(password, salt);
  const encrypted = await crypto.subtle.encrypt({ name: 'AES-GCM', iv, additionalData: AAD, tagLength: 128 }, key, new TextEncoder().encode(text));
  const result = JSON.stringify({ application: 'mesplacements.encrypted', version: 1, algorithm: 'AES-256-GCM', kdf: 'PBKDF2-SHA256', iterations: ITERATIONS, salt: toBase64(salt), iv: toBase64(iv), ciphertext: toBase64(new Uint8Array(encrypted)) }, null, 2);
  parseEncryptedBackup(result);
  return result;
}
export async function decryptBackup(text, password) {
  const { salt, iv, ciphertext } = parseEncryptedBackup(text);
  const key = await deriveKey(password, salt);
  let decrypted;
  try {
    decrypted = await crypto.subtle.decrypt({ name: 'AES-GCM', iv, additionalData: AAD, tagLength: 128 }, key, ciphertext);
  } catch { throw new Error('Mot de passe incorrect ou sauvegarde altérée. Aucune donnée n’a été restaurée.'); }
  return parseBackup(new TextDecoder('utf-8', { fatal: true }).decode(decrypted));
}
