import { normalizeAccount } from './totp.js';
import { displayAccountName } from './display-name.js';

// The signed Android companion initiates sync; accept only while this screen is open.
export function createPhoneImporter({save, send, update}) {
  let pending = null;
  let busy = false;
  let closed = false;
  const savedIds = [];
  function reply(id, status) {
    try { send(JSON.stringify({type:'rw6-totp-result', version:1, id, status})); } catch (_) {}
  }
  function clear() {
    pending = null;
  }
  function show(status, account) {
    if (!closed) update({status, name:account ? displayAccountName(account.name, 0) : '',
      algorithm:account ? account.algorithm : '', digits:account ? account.digits : 6,
      period:account ? account.period : 30, pending:!!account, busy});
  }
  function receive(raw) {
    if (closed) return;
    let packet;
    try {
      if (typeof raw !== 'string' || raw.length > 2048) return;
      packet = JSON.parse(raw);
      if (!packet || packet.type !== 'rw6-totp-sync' || packet.version !== 1 ||
          typeof packet.id !== 'string' || !/^[a-f0-9]{32}$/.test(packet.id)) return;
      if (savedIds.includes(packet.id)) { reply(packet.id, 'saved'); return; }
      if (pending || busy) {
        if (!pending || pending.id !== packet.id) reply(packet.id, 'busy');
        return;
      }
      const a = packet.account;
      if (!a || typeof a.name !== 'string' || a.name.length > 64 || /[\x00-\x1f\x7f]/.test(a.name) ||
          typeof a.secret !== 'string' || a.secret.length > 256 ||
          typeof a.syncId !== 'string' || !/^[a-f0-9]{32}$/.test(a.syncId) ||
          !Number.isInteger(a.digits) || !Number.isInteger(a.period) || typeof a.algorithm !== 'string') {
        throw new Error('Invalid account');
      }
      const account = {...normalizeAccount(a), syncId:a.syncId};
      pending = {id:packet.id, account};
      show('Syncing account...', account);
      return approve();
    } catch (_) { if (packet && /^[a-f0-9]{32}$/.test(packet.id)) reply(packet.id, 'invalid'); }
  }
  async function approve() {
    if (!pending || busy || closed) return;
    busy = true;
    const request = pending;
    show('Saving...', request.account);
    try {
      await save([request.account]);
      savedIds.push(request.id);
      if (savedIds.length > 8) savedIds.shift();
      reply(request.id, 'saved');
      clear(); busy = false; show('Saved. You can send another.');
    } catch (_) {
      reply(request.id, 'error');
      clear(); busy = false; show('Save failed. Send again. Data kept.');
    }
  }
  function close() {
    if (closed) return;
    closed = true;
    if (!busy && pending) reply(pending.id, 'cancelled');
    clear();
  }
  return {receive, close};
}
