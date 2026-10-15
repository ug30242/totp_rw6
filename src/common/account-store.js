import { normalizeAccount } from './totp.js';

const STORAGE_KEY = 'rw6_totp_accounts_v1';
const MAX_ACCOUNTS = 50;

export function createAccountStore(storage) {
  let queue = Promise.resolve();
  function loadAccounts() {
    return new Promise((resolve, reject) => storage.get({
      key: STORAGE_KEY,
      default: '[]',
      success(data) {
        try {
          const parsed = JSON.parse(data || '[]');
          if (!Array.isArray(parsed)) throw new Error('Invalid account data format');
          // Preserve unreadable data: an import must never overwrite it as [].
          parsed.forEach(normalizeAccount);
          resolve(parsed);
        } catch (_) { reject(new Error('Cannot read accounts. Data kept.')); }
      },
      fail: () => reject(new Error('Storage unavailable. Try again.'))
    }));
  }
  function saveAccounts(accounts) {
    return new Promise((resolve, reject) => storage.set({
      key: STORAGE_KEY, value: JSON.stringify(accounts),
      success: () => resolve(accounts),
      fail: () => reject(new Error('Save failed. Try again.'))
    }));
  }
  function mutate(fn) {
    const operation = queue.then(async () => {
      const current = await loadAccounts();
      return saveAccounts(fn(current));
    });
    queue = operation.catch(() => {});
    return operation;
  }
  function identity(a) { return [a.name, a.secret, a.algorithm, a.digits, a.period].join('\u0000'); }
  function appendAccounts(items) {
    if (!Array.isArray(items) || !items.length || items.length > MAX_ACCOUNTS) {
      return Promise.reject(new Error('Import 1-50 accounts at a time.'));
    }
    let validated;
    try { validated = items.map(normalizeAccount); } catch (e) { return Promise.reject(e); }
    return mutate(current => {
      const existing = current.map(a => identity(normalizeAccount(a)));
      validated.forEach(item => {
        const key = identity(item);
        if (existing.includes(key)) return;
        existing.push(key);
        current.push({...item, id:'acc_' + Date.now() + '_' + current.length + '_' + Math.random().toString(36).slice(2)});
      });
      if (current.length > MAX_ACCOUNTS) throw new Error('Maximum 50 accounts allowed.');
      return current;
    });
  }
  function moveAccountOrder(index, direction) {
    return mutate(current => {
      const target = index + direction;
      if (!Number.isInteger(index) || ![-1, 1].includes(direction) || index < 0 || index >= current.length || target < 0 || target >= current.length) return current;
      const item = current[index]; current[index] = current[target]; current[target] = item;
      return current;
    });
  }
  function removeAccountAt(index) {
    return mutate(current => {
      if (Number.isInteger(index) && index >= 0 && index < current.length) current.splice(index, 1);
      return current;
    });
  }
  return {loadAccounts, appendAccounts, moveAccountOrder, removeAccountAt};
}
