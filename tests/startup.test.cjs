const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {smokeRpk}=require('../scripts/smoke-rpk.cjs');
const current=()=>fs.readFileSync('releases/v1.0.2/com.vela.rw6totp.release.1.0.2.rpk');
test('v1.0.1 reproduces startup failure without Node CommonJS globals',async()=>{
  await assert.rejects(smokeRpk(fs.readFileSync('releases/v1.0.1/com.vela.rw6totp.release.1.0.1.rpk')),/exports is not defined/);
});
test('compiled release renders the first frame, empty home and pairing screen',async()=>{
  const screens=await smokeRpk(current());
  assert(screens['pages/index'].values.includes('등록된 TOTP 계정이 없습니다.'));
  assert(screens['pages/add'].values.includes('웹 QR 등록'));
});
test('compiled storage and crypto modules load and render the RFC account OTP',async()=>{
  const screens=await smokeRpk(current(),{timestampMs:59000,accounts:[{
    id:'rfc6238',name:'RFC 6238',secret:'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ',algorithm:'SHA1',digits:6,period:30
  }]});
  assert(screens['pages/index'].values.includes('287 082'));
  assert(screens['pages/index'].values.includes('RFC 6238'));
});
test('compiled release displays storage errors instead of losing the screen',async()=>{
  const screens=await smokeRpk(current(),{failStorage:true});
  assert(screens['pages/index'].values.some(v=>String(v).includes('저장소')));
});
