import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {encryptPayload, decryptPayload} from '../src/common/totp.js';
function addPage() {
  let time=1700000000000,request,saves=0,backs=0,close;
  const script=fs.readFileSync('src/pages/add/index.ux','utf8').split('<script>')[1].split('</script>')[0]
    .replace(/^import .*;$/gm,'').replace('export default','globalThis.definition =');
  const ctx={
    router:{back(){backs++;}},fetch:{fetch(args){request=args;}},vibrator:{vibrate(){}},
    decryptPayload,appendAccounts:async items=>{assert(items.length);saves++;},
    Date:{now:()=>time},Math,setTimeout(fn){close=fn;return 1;},clearTimeout(){close=null;}
  };
  vm.runInNewContext(script,ctx);
  const p=Object.assign({},ctx.definition,ctx.definition.private);p.onInit();p.onShow();
  return {p,get request(){return request},get saves(){return saves},get backs(){return backs},get close(){return close},tick(ms){time+=ms},
    packet(sid=p.sessionId){return encryptPayload({sid,expiresAt:time+300000,accounts:[{name:'A',secret:'MY'}]},p.sessionKey)}};
}
test('poll receives authenticated current-session packet once and reports HTTP failures',async()=>{
  const f=addPage();f.p.pollRelayNow();assert(f.p.busy);const original=f.request;
  f.p.pollRelayNow();assert.equal(f.request,original);
  await f.request.success({code:403,data:'not authorized'});assert.match(f.p.statusText,/403/);assert(!f.p.busy);
  f.p.pollRelayNow();
  await f.request.success({code:200,data:JSON.stringify({event:'message',message:f.packet()})});
  assert.equal(f.saves,1);assert(f.close);f.close();assert.equal(f.backs,1);
  await f.p.processIncomingPacket(f.packet(),f.p._generation);assert.equal(f.saves,1);
});
test('stale responses, expired sessions and wrong-session envelopes do not import',async()=>{
  const f=addPage();f.p.pollRelayNow();const old=f.request;const oldPacket=f.packet();
  f.p.initSession();await old.success({code:200,data:JSON.stringify({event:'message',message:oldPacket})});assert.equal(f.saves,0);
  await assert.rejects(f.p.processIncomingPacket(f.packet('WRONG'),f.p._generation),/session/);assert.equal(f.saves,0);
  f.tick(300001);f.p.pollRelayNow();assert.match(f.p.statusText,/expired/);assert.equal(f.saves,0);
});
test('hiding during a request allows a fresh request after returning',async()=>{
  const f=addPage();f.p.pollRelayNow();const old=f.request;f.p.onHide();
  await old.success({code:200,data:''});assert(!f.p.busy);f.p.onShow();f.p.pollRelayNow();assert.notEqual(f.request,old);
});
