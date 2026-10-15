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
