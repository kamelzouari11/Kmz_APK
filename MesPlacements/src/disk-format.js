import { validateRecord } from './data.js';

export function validateRecords(records) {
  if (!Array.isArray(records)) throw new Error('Liste des entrées invalide.');
  const ids = new Set();
  return records.map(item => {
    const record = validateRecord(item);
    if (typeof item.id !== 'string' || !item.id || ids.has(item.id)) throw new Error('Identifiant d’entrée manquant ou dupliqué.');
    ids.add(item.id);
    return { ...record, id: item.id, ...(item.createdAt ? { createdAt: item.createdAt } : {}) };
  });
}
export function parseDiskFile(text) {
  const data = JSON.parse(text);
  if (data?.application !== 'mesplacements.local' || data.version !== 1 || !Number.isSafeInteger(data.revision) || data.revision < 1) throw new Error('Fichier de données invalide. Il n’a pas été écrasé.');
  return { ...data, records: validateRecords(data.records) };
}
