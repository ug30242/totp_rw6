import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {displayAccountName} from '../src/common/display-name.js';
import {createAccountStore} from '../src/common/account-store.js';

test('watch source text contains no non-ASCII labels, symbols or errors',()=>{
  function walk(dir) {
    for(const entry of fs.readdirSync(dir,{withFileTypes:true})) {
      const name=path.join(dir,entry.name);
      if(entry.isDirectory()) walk(name);
      else if(/\.(ux|js|json)$/.test(name)) assert(!/[^\x00-\x7f]/.test(fs.readFileSync(name,'utf8')),name);
    }
  }
  walk('src');
});
test('display labels keep usable ASCII and fall back for missing glyphs',()=>{
  assert.equal(displayAccountName('GitHub (alice)',0),'GitHub (alice)');
  assert.equal(displayAccountName('네이버 (alice@example.com)',0),'(alice@example.com)');
  assert.equal(displayAccountName('네이버 🔒',1),'Account 2');
  assert.equal(displayAccountName('() 🔒',0),'Account 1');
});
test('loading a non-ASCII account preserves its stored name and secret',async()=>{
  const original={id:'keep',name:'네이버 🔒',secret:'JBSWY3DPEHPK3PXP',period:30,digits:6,algorithm:'SHA1'};
  let writes=0;
  const store=createAccountStore({get({success}){success(JSON.stringify([original]));},set(){writes++;}});
  const [account]=await store.loadAccounts();
  assert.equal(displayAccountName(account.name,0),'Account 1');
  assert.deepEqual(account,original);
  assert.equal(writes,0);
});
