const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {verifyRpk}=require('../scripts/verify-rpk.cjs');
test('the supplied RPK is rejected for a signed content digest mismatch',async()=>{
  await assert.rejects(verifyRpk(fs.readFileSync('tests/fixtures/original-invalid.rpk')),/RPK content digest mismatch/);
});
test('release has valid inner/outer signatures and the expected version and certificate',async()=>{
  const crypto=require('node:crypto');
  const result=await verifyRpk(fs.readFileSync('releases/v1.0.1/com.vela.rw6totp.release.1.0.1.rpk'));
  assert.equal(result.version,'1.0.1');assert.equal(result.versionCode,2);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
});
test('modified and truncated release archives fail verification',async()=>{
  const b=fs.readFileSync('releases/v1.0.1/com.vela.rw6totp.release.1.0.1.rpk');
  const corrupt=Buffer.from(b);corrupt[40]^=1;
  await assert.rejects(verifyRpk(corrupt),/RPK content digest mismatch/);
  await assert.rejects(verifyRpk(b.subarray(0,b.length-1)),/Missing ZIP end record/);
});
