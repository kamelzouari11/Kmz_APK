const pending = new Map();
let sequence = 0;
window.__reportsNativeReply = (id, result) => {
  const request = pending.get(id);
  if (!request) return;
  pending.delete(id);
  clearTimeout(request.timeout);
  if (result.error) request.reject(new Error(result.error));
  else request.resolve(result.value);
};
export function nativeRequest(action, payload = {}) {
  if (!window.AndroidReports?.request) throw new Error('Cette fonction nécessite l’application Android.');
  const id = String(++sequence);
  return new Promise((resolve, reject) => {
    // A document picker may remain open until the user chooses a destination.
    const timeout = action === 'savePdf' ? null : setTimeout(() => { pending.delete(id); reject(new Error('Le délai de réponse est dépassé. Réessayez.')); }, 45_000);
    pending.set(id, { resolve, reject, timeout });
    try { window.AndroidReports.request(id, action, JSON.stringify(payload)); }
    catch { clearTimeout(timeout); pending.delete(id); reject(new Error('Communication Android indisponible.')); }
  });
}
