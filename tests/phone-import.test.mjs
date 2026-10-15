import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createPhoneImporter} from '../src/common/phone-import.js';
import {createAccountStore} from '../src/common/account-store.js';

const account={name:'Example',secret:'JBSWY3DPEHPK3PXP',algorithm:'SHA1',digits:6,period:30,syncId:'f'.repeat(32)};
const packet=(id='a'.repeat(32),overrides={})=>JSON.stringify({type:'rw6-totp-sync',version:1,id,account,...overrides});
function fixture(save=async()=>{}) {
  const sent=[],states=[];
  const receiver=createPhoneImporter({save,send:s=>sent.push(JSON.parse(s)),update:s=>states.push(s)});
  return {receiver,sent,states};
}
test('phone sync persists directly and acknowledges only after storage success',async()=>{
  let complete;const f=fixture(()=>new Promise(r=>complete=r));
  const saving=f.receiver.receive(packet());
  assert.equal(f.sent.length,0);assert.equal(f.states.at(-1).busy,true);
  complete();await saving;
  assert.deepEqual(f.sent.map(m=>m.status),['saved']);
  assert.equal(f.states.at(-1).pending,false);
  assert(!JSON.stringify(f.states).includes(account.secret),'UI state must not expose the secret');
});
test('malformed, oversized, unknown-protocol and invalid accounts never reach storage',async()=>{
  let saves=0;const f=fixture(async()=>{saves++;});
  for(const raw of ['',null,'{','x'.repeat(2049),packet(undefined,{version:2}),packet(undefined,{type:'rw6-totp-import'}),
    packet(undefined,{account:{...account,secret:'bad!'}}),packet(undefined,{account:{...account,name:'x'.repeat(65)}}),
    packet(undefined,{account:{...account,name:'line\nbreak'}}),packet(undefined,{account:{...account,secret:'A'.repeat(257)}}),
    packet(undefined,{account:{...account,period:'30'}}),packet('wrong-id')]) await f.receiver.receive(raw);
  assert.equal(saves,0);assert(f.sent.every(p=>p.status==='invalid'));
});
test('storage failures acknowledge error and preserve existing unreadable data',async()=>{
  const value='{broken';let writes=0;
  const store=createAccountStore({get({success}){success(value);},set(){writes++;}});
  const f=fixture(store.syncPhoneAccounts);
  await f.receiver.receive(packet());
  assert.equal(writes,0);assert.equal(f.sent.at(-1).status,'error');assert.equal(f.states.at(-1).pending,false);
});
test('busy requests do not race saves; repeated acknowledgements never save twice',async()=>{
  let complete,saves=0;const f=fixture(()=>{saves++;return new Promise(r=>complete=r);});
  const saving=f.receiver.receive(packet());
  f.receiver.receive(packet());f.receiver.receive(packet('b'.repeat(32)));
  assert.equal(saves,1);assert.equal(f.sent.at(-1).status,'busy');
  complete();await saving;await f.receiver.receive(packet());
  assert.equal(saves,1);assert.equal(f.sent.at(-1).status,'saved');
});
test('closed receiver rejects new traffic and an already authorized save can finish',async()=>{
  let complete,saves=0;const f=fixture(()=>{saves++;return new Promise(r=>complete=r);});
  const saving=f.receiver.receive(packet());f.receiver.close();complete();await saving;
  const stateCount=f.states.length;await f.receiver.receive(packet('b'.repeat(32)));
  assert.equal(saves,1);assert.equal(f.sent.at(-1).status,'saved');assert.equal(f.states.length,stateCount);
});
test('sync retries across sessions deduplicate through the existing account store',async()=>{
  let value='[]';const store=createAccountStore({get({success}){success(value);},set({value:v,success}){value=v;success();}});
  const a=fixture(store.syncPhoneAccounts);await a.receiver.receive(packet());a.receiver.close();
  const b=fixture(store.syncPhoneAccounts);await b.receiver.receive(packet('b'.repeat(32)));
  assert.equal(JSON.parse(value).length,1);assert.equal(b.sent.at(-1).status,'saved');
});
