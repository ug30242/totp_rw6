import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {normalizeAccount,generateTOTP} from '../src/common/totp.js';
function fixture({failSave=false}={}) {
  const saved=[];let backs=0;
  const code=fs.readFileSync('src/pages/manual/index.ux','utf8').split('<script>')[1].split('</script>')[0]
    .replace(/^import .*;$/gm,'').replace('export default','globalThis.definition =');
  const context={normalizeAccount,appendAccounts:async items=>{if(failSave)throw new Error('Save failed. Try again.');saved.push(...items);},
    router:{back(){backs++;}},vibrator:{vibrate(){}}};
  vm.runInNewContext(code,context);const page=Object.assign({},context.definition,context.definition.private);page.onInit();page.onShow();
  return {page,saved,get backs(){return backs}};
}
const key='GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ';
test('offline keys save an RFC account without network or native text input APIs',async()=>{
  const f=fixture();for(const char of key)f.page.typeChar(char);
  assert(!f.page.previewText.includes(key));
  await f.page.nextStep();for(const char of 'RFC TEST')f.page.typeChar(char);await f.page.nextStep();await f.page.nextStep();
  assert.equal(f.page.step,3);await f.page.nextStep();
  assert.equal(f.saved.length,1);assert.equal(f.saved[0].name,'RFC TEST');
  assert.equal(generateTOTP(f.saved[0],59000).otp,'287082');assert.equal(f.backs,1);assert.equal(f.page.secret,'');
});
test('invalid/empty secrets and periods block saving; save failures preserve input for retry',async()=>{
  const f=fixture({failSave:true});await f.page.nextStep();assert.equal(f.page.step,0);assert.match(f.page.statusText,/Base32/);
  for(const char of key)f.page.typeChar(char);await f.page.nextStep();await f.page.nextStep();
  f.page.clearField();f.page.typeChar('0');await f.page.nextStep();assert.equal(f.page.step,2);
  f.page.clearField();f.page.typeChar('6');f.page.typeChar('0');await f.page.nextStep();f.page.cycleAlgorithm();f.page.cycleDigits();
  await f.page.nextStep();assert.match(f.page.statusText,/Save failed/);assert.equal(f.page.secret,key);assert.equal(f.backs,0);
  assert.equal(f.page.algorithm,'SHA256');assert.equal(f.page.digits,8);
});
test('only Base32 keys can be entered in the secret field and closing clears the draft',()=>{
  const f=fixture();f.page.typeChar('0');f.page.typeChar('!');assert.equal(f.page.secret,'');
  f.page.typeChar('M');f.page.typeChar('Y');f.page.toggleSecret();assert.equal(f.page.previewText,'MY');
  f.page.goBack();assert.equal(f.page.secret,'');assert.equal(f.backs,1);
});
test('custom SHA512, 8 digits and 60-second period survive offline entry',async()=>{
  const f=fixture();for(const char of key)f.page.typeChar(char);await f.page.nextStep();await f.page.nextStep();
  f.page.clearField();f.page.typeChar('6');f.page.typeChar('0');await f.page.nextStep();
  f.page.cycleAlgorithm();f.page.cycleAlgorithm();f.page.cycleDigits();await f.page.nextStep();
  assert.equal(f.saved[0].algorithm,'SHA512');assert.equal(f.saved[0].digits,8);assert.equal(f.saved[0].period,60);
});
