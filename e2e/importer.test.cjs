const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const http=require('node:http');
const {chromium}=require('playwright-core');
const validPairing='https://ug30242.github.io/totp_rw6/#sid=RW6ABCDEFGHJKLMNPQR&key=ABCDEFGHJKLMNPQRSTUV';
const uri='otpauth://totp/Example:alice?secret=JBSWY3DPEHPK3PXP';
const executablePath=process.env.CHROMIUM_PATH||['/usr/bin/chromium','/usr/bin/google-chrome'].find(fs.existsSync);
test('standalone importer encrypts before POST, rejects invalid input, clears secrets',async()=>{
  assert(executablePath,'Install Chromium or set CHROMIUM_PATH');
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html; charset=utf-8');res.end(fs.readFileSync('web/index.html'));});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const browser=await chromium.launch({executablePath,args:['--no-sandbox']});
  try {
    const page=await browser.newPage(); const errors=[];const sent=[];
    page.on('pageerror',e=>errors.push(e.message));
    await page.route('https://ntfy.sh/**',async route=>{
      sent.push(route.request().postData());
      await route.fulfill({status:200,contentType:'application/json',body:'{"id":"test-message"}'});
    });
    await page.goto('http://127.0.0.1:'+server.address().port);
    await page.locator('#pairing').fill(validPairing);
    await page.locator('#accounts').fill(uri);
    await page.locator('#send').click();
    await page.waitForFunction(()=>document.getElementById('status').textContent.includes('동의'));
    assert.equal(sent.length,0);
    await page.locator('#consent').check();
    await page.locator('#send').click();
    await page.waitForFunction(()=>document.getElementById('status').textContent.includes('전송 완료'));
    assert.equal(sent.length,1);assert(sent[0].startsWith('RW6E1:'));assert(!sent[0].includes('JBSWY3DPEHPK3PXP'));
    const {decryptPayload}=await import('../src/common/totp.js');
    const decrypted=decryptPayload(sent[0],'ABCDEFGHJKLMNPQRSTUV');
    assert.equal(decrypted.sid,'RW6ABCDEFGHJKLMNPQR');
    assert.equal(decrypted.accounts[0].name,'Example (alice)');
    assert.equal(await page.locator('#accounts').inputValue(),'');
    await page.locator('#accounts').fill('otpauth://hotp/a?secret=MY');
    await page.locator('#send').click();
    await page.waitForFunction(()=>document.getElementById('status').textContent.includes('TOTP'));
    assert.equal(sent.length,1);
    await page.locator('#clear').click();
    assert.equal(await page.locator('#pairing').inputValue(),'');
    assert.equal(await page.locator('#accounts').inputValue(),'');
    assert.equal(await page.locator('#consent').isChecked(),false);
    assert.deepEqual(errors,[]);
  } finally {await browser.close();await new Promise(resolve=>server.close(resolve));}
});
