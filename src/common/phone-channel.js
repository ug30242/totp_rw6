import {createPhoneImporter} from './phone-import.js';
import {decodePhoneMessage} from './phone-wire.js';

export function openPhoneChannel(connect, options) {
  let live = true;
  let callbacks = options;
  let received = 0;
  function connection(text) { if (live && callbacks.connection) callbacks.connection(text); }
  function send(json, announce = false) {
    connect.send({data:{data:json},
      success:() => { if (announce && live && callbacks.delivery) callbacks.delivery('Ready reply sent.'); },
      fail:(data, code) => { if (live && callbacks.delivery) callbacks.delivery('Reply failed (' + (Number(code) || Number(data && data.code) || 0) + ').'); }
    });
  }
  const receiver = createPhoneImporter({save:options.save, send,
    update:state => { if (live && callbacks.update) callbacks.update(state); }});
  options = null;
  connect.onmessage = event => {
    if (!live) return;
    let message;
    try { message = decodePhoneMessage(event); } catch (_) { return; }
    received++;
    connection('Phone data received (' + received + ').');
    if (message.type === 'rw6-totp-hello') {
      send(JSON.stringify({type:'rw6-totp-ready', version:1, id:message.id}),true);
      return;
    }
    return receiver.receive(JSON.stringify(message));
  };
  connect.onopen = () => connection('Phone connected.');
  connect.onclose = () => connection('Phone disconnected.');
  connect.onerror = event => connection(event && event.code === 1001
    ? 'Install RW6 TOTP on Android.' : 'Connection error. Check Mi Fitness.');
  function check() {
    connect.getReadyState({success:data => connection(data.status === 1
      ? 'Phone connected.' : 'Open Android app and Mi Fitness.'),
    fail:() => connection('Cannot connect. Check Mi Fitness.')});
  }
  function close() {
    if (!live) return;
    live = false; receiver.close(); callbacks = {};
    // Do not overwrite singleton handlers: another page may already own them.
    // Retained native callbacks capture this closed lease, never the old page.
  }
  return {check, close};
}
