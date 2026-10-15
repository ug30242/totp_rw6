const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {verifyRpk}=require('../scripts/verify-rpk.cjs');
test('the supplied RPK is rejected for a signed content digest mismatch',async()=>{
  await assert.rejects(verifyRpk(fs.readFileSync('tests/fixtures/original-invalid.rpk')),/RPK content digest mismatch/);
});
test('v1.0.1 signatures remain valid independently of its startup failure',async()=>{
  const crypto=require('node:crypto');
  const result=await verifyRpk(fs.readFileSync('releases/v1.0.1/com.vela.rw6totp.release.1.0.1.rpk'));
  assert.equal(result.version,'1.0.1');assert.equal(result.versionCode,2);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
});
test('v1.0.2 has valid signatures and preserves the release signing identity',async()=>{
  const crypto=require('node:crypto');
  const result=await verifyRpk(fs.readFileSync('releases/v1.0.2/com.vela.rw6totp.release.1.0.2.rpk'));
  assert.equal(result.version,'1.0.2');assert.equal(result.versionCode,3);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
});
test('v1.0.3 uses the existing signing identity and ASCII launcher name',async()=>{
  const crypto=require('node:crypto');
  const JSZip=require('jszip');
  const bytes=fs.readFileSync('releases/v1.0.3/com.vela.rw6totp.release.1.0.3.rpk');
  const result=await verifyRpk(bytes);
  assert.equal(result.version,'1.0.3');assert.equal(result.versionCode,4);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
  const zip=await JSZip.loadAsync(bytes);
  assert.equal(JSON.parse(await zip.file('manifest.json').async('string')).name,'TOTP');
});
test('v1.1.0 preserves the signing identity and exposes only offline entry',async()=>{
  const crypto=require('node:crypto');
  const JSZip=require('jszip');
  const bytes=fs.readFileSync('releases/v1.1.0/com.vela.rw6totp.release.1.1.0.rpk');
  const result=await verifyRpk(bytes);
  assert.equal(result.version,'1.1.0');assert.equal(result.versionCode,5);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
  const zip=await JSZip.loadAsync(bytes);
  const manifest=JSON.parse(await zip.file('manifest.json').async('string'));
  assert(!manifest.features.some(f=>/fetch|interconnect|bluetooth|network/.test(f.name)));
  assert(zip.file('pages/manual/index.js'));assert(!zip.file('pages/add/index.js'));
});
test('modified and truncated release archives fail verification',async()=>{
  const b=fs.readFileSync('releases/v1.0.1/com.vela.rw6totp.release.1.0.1.rpk');
  const corrupt=Buffer.from(b);corrupt[40]^=1;
  await assert.rejects(verifyRpk(corrupt),/RPK content digest mismatch/);
  await assert.rejects(verifyRpk(b.subarray(0,b.length-1)),/Missing ZIP end record/);
});
test('v1.2.0 keeps the signing identity and adds only local phone interconnect',async()=>{
  const crypto=require('node:crypto'),JSZip=require('jszip');
  const bytes=fs.readFileSync('releases/v1.2.0/com.vela.rw6totp.release.1.2.0.rpk');
  const result=await verifyRpk(bytes);assert.equal(result.version,'1.2.0');assert.equal(result.versionCode,6);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
  const zip=await JSZip.loadAsync(bytes),manifest=JSON.parse(await zip.file('manifest.json').async('string'));
  assert(manifest.features.some(f=>f.name==='system.interconnect'));
  assert(!manifest.features.some(f=>/fetch|network|websocket/.test(f.name)));
  assert(zip.file('pages/receive/index.js'));assert(zip.file('pages/manual/index.js'));
});
test('v1.2.1 keeps the signing identity and adds only local phone interconnect',async()=>{
  const crypto=require('node:crypto'),JSZip=require('jszip');
  const bytes=fs.readFileSync('releases/v1.2.1/com.vela.rw6totp.release.1.2.1.rpk');
  const result=await verifyRpk(bytes);assert.equal(result.version,'1.2.1');assert.equal(result.versionCode,7);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
  const zip=await JSZip.loadAsync(bytes),manifest=JSON.parse(await zip.file('manifest.json').async('string'));
  assert(manifest.features.some(f=>f.name==='system.interconnect'));
  assert(!manifest.features.some(f=>/fetch|network|websocket/.test(f.name)));
  assert(zip.file('pages/receive/index.js'));assert(zip.file('pages/manual/index.js'));
});
test('current release keeps the signing identity and uses only local phone interconnect',async()=>{
  const crypto=require('node:crypto'),JSZip=require('jszip');
  const {version}=require('../package.json'),{versionCode}=require('../src/manifest.json');
  const bytes=fs.readFileSync(`releases/v${version}/com.vela.rw6totp.release.${version}.rpk`);
  const result=await verifyRpk(bytes);assert.equal(result.version,version);assert.equal(result.versionCode,versionCode);
  assert.equal(result.certificate,new crypto.X509Certificate(fs.readFileSync('certificates/release.pem')).fingerprint256);
  const zip=await JSZip.loadAsync(bytes),manifest=JSON.parse(await zip.file('manifest.json').async('string'));
  assert(manifest.features.some(f=>f.name==='system.interconnect'));
  assert(!manifest.features.some(f=>/fetch|network|websocket/.test(f.name)));
  assert(zip.file('pages/receive/index.js'));assert(zip.file('pages/manual/index.js'));
});
