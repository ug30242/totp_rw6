import {test} from 'node:test';
import assert from 'node:assert/strict';
import {openPhoneChannel} from '../src/common/phone-channel.js';
import {decodePhoneMessage} from '../src/common/phone-wire.js';

const hello={type:'rw6-totp-hello',version:1,id:'a'.repeat(32)};
function fixture() {
  const sent=[];
  const connect={send({data,success}){sent.push(JSON.parse(data.data));if(success)success();},getReadyState({success}){success({status:1});}};
  return {sent,connect};
}
test('an old page closing cannot erase a newer page native receiver',()=>{
  const {connect,sent}=fixture();let oldUpdates=0,newUpdates=0;
  const old=openPhoneChannel(connect,{save:async()=>{},connection(){oldUpdates++;}});
  const current=openPhoneChannel(connect,{save:async()=>{},connection(){newUpdates++;}});
  old.close();connect.onmessage({data:JSON.stringify(hello)});
  assert.equal(sent.at(-1).type,'rw6-totp-ready');assert.equal(oldUpdates,0);assert.equal(newUpdates,1);
  current.close();connect.onmessage({data:JSON.stringify(hello)});assert.equal(sent.length,1);
});
test('accepts native event, object, string-wrapped JSON and UTF-8 payloads',()=>{
  const raw=JSON.stringify(hello);
  for(const input of [hello,raw,{data:raw},{data:{data:raw}},{data:hello},JSON.stringify({data:raw}),
    {data:new TextEncoder().encode(raw)},{data:new TextEncoder().encode(raw).buffer},JSON.stringify(raw)]){
    assert.deepEqual(decodePhoneMessage(input),hello);
  }
  for(const input of [null,'{','x'.repeat(2049),{data:{data:{data:{data:{data:{data:raw}}}}}}])assert.throws(()=>decodePhoneMessage(input));
});
test('round trip stores a wrapped sync request and returns its correlated acknowledgement',async()=>{
  const {connect,sent}=fixture();let saved;
  const channel=openPhoneChannel(connect,{save:async values=>{saved=values;},update(){}});
  const packet={type:'rw6-totp-sync',version:1,id:'b'.repeat(32),account:{
    name:'Account',secret:'JBSWY3DPEHPK3PXP',algorithm:'SHA1',digits:6,period:30,syncId:'c'.repeat(32)}};
  await connect.onmessage({data:{data:JSON.stringify(packet)}});
  assert.equal(saved[0].secret,packet.account.secret);assert.equal(sent.at(-1).status,'saved');assert.equal(sent.at(-1).id,packet.id);
  channel.close();
});
test('native send failures appear without pretending a reply was delivered',()=>{
  let delivery='';const connect={send({fail}){fail('ignored',1006);}};
  const channel=openPhoneChannel(connect,{save:async()=>{},delivery:text=>{delivery=text;}});
  connect.onmessage({data:JSON.stringify(hello)});assert.equal(delivery,'Reply failed (1006).');channel.close();
});
