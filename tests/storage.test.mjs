import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createAccountStore} from '../src/common/account-store.js';
const item=name=>({name,secret:'JBSWY3DPEHPK3PXP'});
function fixture(initial='[]') {
  let data=initial,writes=0,failRead=false,failWrite=false;
  const store=createAccountStore({
    get({success,fail}) {setTimeout(()=>failRead?fail():success(data),1);},
    set({value,success,fail}) {setTimeout(()=>{if(failWrite)return fail();data=value;writes++;success();},1);}
  });
  return {store,get data(){return data},get writes(){return writes},set failRead(v){failRead=v},set failWrite(v){failWrite=v}};
}
test('concurrent imports are serialized and duplicate retry is idempotent',async()=>{
  const f=fixture();await Promise.all([f.store.appendAccounts([item('A')]),f.store.appendAccounts([item('B')]),f.store.appendAccounts([item('A')])]);
  assert.deepEqual((await f.store.loadAccounts()).map(a=>a.name),['A','B']);
});
test('invalid batch, capacity and corrupted/read-failed storage never overwrite accounts',async()=>{
  const f=fixture();await assert.rejects(f.store.appendAccounts([item('A'),{secret:'INVALID!'}]));assert.equal(f.writes,0);
  await assert.rejects(f.store.appendAccounts(Array.from({length:51},(_,i)=>item(String(i)))));assert.equal(f.writes,0);
  const broken=fixture('{broken');await assert.rejects(broken.store.appendAccounts([item('A')]));assert.equal(broken.data,'{broken');assert.equal(broken.writes,0);
  f.failRead=true;await assert.rejects(f.store.appendAccounts([item('A')]));assert.equal(f.writes,0);
});
test('write failures are retryable and order/delete bounds preserve state',async()=>{
  const f=fixture();f.failWrite=true;await assert.rejects(f.store.appendAccounts([item('A')]));f.failWrite=false;
  await f.store.appendAccounts([item('A'),item('B')]);
  await f.store.moveAccountOrder(0,-1);await f.store.removeAccountAt(-1);await f.store.removeAccountAt(0.5);
  assert.deepEqual((await f.store.loadAccounts()).map(a=>a.name),['A','B']);
  await f.store.moveAccountOrder(0,1);assert.deepEqual((await f.store.loadAccounts()).map(a=>a.name),['B','A']);
  await f.store.removeAccountAt(0);assert.equal((await f.store.loadAccounts())[0].name,'A');
});
test('phone sync updates by stable ID and preserves manual accounts and ordering',async()=>{
  const f=fixture(); await f.store.appendAccounts([item('Manual'),item('Phone')]);
  const before=await f.store.loadAccounts();
  const phone={...item('Phone'),syncId:'a'.repeat(32)};
  await f.store.syncPhoneAccounts([phone]);
  await f.store.syncPhoneAccounts([{...phone,name:'Renamed',algorithm:'SHA512',digits:8,period:60}]);
  const after=await f.store.loadAccounts();
  assert.equal(after.length,2);assert.deepEqual(after[0],before[0]);assert.equal(after[1].id,before[1].id);
  assert.equal(after[1].name,'Renamed');assert.equal(after[1].algorithm,'SHA512');assert.equal(after[1].digits,8);
});
test('phone sync can update at capacity, but overflow and invalid IDs preserve storage',async()=>{
  const f=fixture();const phone={...item('Phone'),syncId:'a'.repeat(32)};
  await f.store.syncPhoneAccounts([phone]);await f.store.appendAccounts(Array.from({length:49},(_,i)=>item('Manual '+i)));
  await f.store.syncPhoneAccounts([{...phone,name:'Updated'}]);const before=f.data;
  await assert.rejects(f.store.syncPhoneAccounts([{...phone,syncId:'b'.repeat(32)}]));assert.equal(f.data,before);
  await assert.rejects(f.store.syncPhoneAccounts([{...phone,syncId:'broken'}]));assert.equal(f.data,before);
});
