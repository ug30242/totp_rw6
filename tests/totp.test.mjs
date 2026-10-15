import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHmac, randomBytes} from 'node:crypto';
import {base32ToBytes, generateTOTP, normalizeAccount, parseOtpAuthUri, encryptPayload, decryptPayload} from '../src/common/totp.js';
function base32(bytes) {
  let bits=0, value=0, out=''; const chars='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  for (const b of bytes) { value=(value<<8)|b; bits+=8; while(bits>=5) { bits-=5; out+=chars[(value>>>bits)&31]; } }
  if(bits) out+=chars[(value<<(5-bits))&31]; return out;
}
const times=[59,1111111109,1111111111,1234567890,2000000000,20000000000];
const vectors={
  SHA1:['94287082','07081804','14050471','89005924','69279037','65353130'],
  SHA256:['46119246','68084774','67062674','91819424','90698825','77737706'],
  SHA512:['90693936','25091201','99943326','93441116','38618901','47863826']
};
for(const [algorithm,expected] of Object.entries(vectors)) {
  const length={SHA1:20,SHA256:32,SHA512:64}[algorithm];
  const secret=base32(Buffer.from('1234567890'.repeat(7).slice(0,length)));
  times.forEach((time,i)=>test(`RFC 6238 ${algorithm} at ${time}`,()=>{
    assert.equal(generateTOTP({secret,algorithm,digits:8},time*1000).otp,expected[i]);
  }));
}
test('all algorithms agree with native HMAC for long keys and counters',()=>{
  for(const algorithm of Object.keys(vectors)) for(const size of [10,63,65,129,180]) {
    const bytes=randomBytes(size); const secret=base32(bytes);
    const counter=Buffer.alloc(8); counter.writeBigUInt64BE(0x100000001n);
    const digest=createHmac(algorithm.toLowerCase(),bytes).update(counter).digest();
    const offset=digest.at(-1)&15;
    const expected=String((digest.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');
    assert.equal(generateTOTP({secret,algorithm},Number(0x100000001n)*30000).otp,expected);
  }
});
test('time boundary and strict account validation',()=>{
  const a={secret:'JBSWY3DPEHPK3PXP'};
  assert.equal(generateTOTP(a,29999).remaining,1);
  assert.equal(generateTOTP(a,30000).remaining,30);
  for(const fields of [{digits:7},{period:Infinity},{period:0},{period:3.5},{algorithm:'MD5'},{secret:''},{secret:'A'},{secret:'MZ'},{secret:'AA!A'}]) {
    assert.throws(()=>normalizeAccount({...a,...fields}));
  }
  assert.throws(()=>generateTOTP(a,-1));
  assert.deepEqual([...base32ToBytes('my======')],[102]);
});
test('OTP URI parser works without URL APIs and rejects ambiguous/unsupported fields',()=>{
  const old=globalThis.URL; globalThis.URL=undefined;
  try {
    const a=parseOtpAuthUri('otpauth://totp/Example%3Aalice%40mail?secret=JBSWY3DPEHPK3PXP&issuer=Example&algorithm=SHA-256&digits=8&period=60');
    assert.equal(a.name,'Example (alice@mail)');assert.equal(a.algorithm,'SHA256');assert.equal(a.period,60);
  } finally { globalThis.URL=old; }
  for(const uri of ['otpauth://hotp/a?secret=MY','otpauth://totp/a?secret=MY&secret=MY','otpauth://totp/a?secret=MY&digits=7','otpauth://totp/a?secret=MY&period=Infinity','otpauth://totp/a?secret=MY&algorithm=MD5','otpauth://totp/a?secret=MY&period=']) assert.throws(()=>parseOtpAuthUri(uri));
  assert.throws(()=>parseOtpAuthUri('otpauth://totp/a?secret=JBS=WY3DPEHPK3PXP'));
});
test('encrypted Unicode round-trip, unique nonces, authentication and framing',()=>{
  const payload={sid:'RW6EXAMPLE',accounts:[{name:'한글 🔒',secret:'MY'}]};
  const a=encryptPayload(payload,'ABCDEFGH23456789'); const b=encryptPayload(payload,'ABCDEFGH23456789');
  assert.notEqual(a.split(':')[1],b.split(':')[1]);
  assert.deepEqual(decryptPayload(a,'ABCDEFGH23456789'),payload);
  assert.throws(()=>decryptPayload(a,'WRONGKEY'));
  const parts=a.split(':'); parts[2]=parts[2].slice(0,-2);assert.throws(()=>decryptPayload(parts.join(':'),'ABCDEFGH23456789'));
  parts[2]=a.split(':')[2]+'00'; assert.throws(()=>decryptPayload(parts.join(':'),'ABCDEFGH23456789'));
  parts[2]=a.split(':')[2];parts[3]='!'+parts[3];assert.throws(()=>decryptPayload(parts.join(':'),'ABCDEFGH23456789'));
  assert.throws(()=>decryptPayload('RW6E1:'+('a'.repeat(50000)),'key'));
});
