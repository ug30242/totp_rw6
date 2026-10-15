import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';

test('current watch app has no network permission, broken QR link or relay code',()=>{
  const manifest=JSON.parse(fs.readFileSync('src/manifest.json'));
  assert.deepEqual(manifest.features.map(f=>f.name).sort(),['system.interconnect','system.router','system.storage','system.vibrator']);
  assert(manifest.router.pages['pages/receive']);
  assert(manifest.router.pages['pages/manual']);assert(!manifest.router.pages['pages/add']);
  function inspect(dir) {
    for(const file of fs.readdirSync(dir,{withFileTypes:true})) {
      const name=path.join(dir,file.name);
      if(file.isDirectory()) inspect(name);
      else if(/\.(js|ux|json)$/.test(name)) assert(!/ntfy\.sh|github\.io|@system\.fetch|pollRelayNow/.test(fs.readFileSync(name,'utf8')),name);
    }
  }
  inspect('src');
});
