// Execute the actual compiled component entrypoints without Node's module globals.
// This checks the bundle ABI and template bindings, not native watch rendering.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const JSZip = require('jszip');

async function smokeRpk(buffer, {accounts = [], failStorage = false, timestampMs} = {}) {
  const zip = await JSZip.loadAsync(buffer);
  const manifest = JSON.parse(await zip.file('manifest.json').async('string'));
  const trees = {};
  const navigation = [];
  for (const [route, pageConfig] of Object.entries(manifest.router.pages)) {
    const scheduled = new Map(); let timer = 0;
    let storedValue = JSON.stringify(accounts);
    const context = vm.createContext({console:{log() {},warn() {},error() {}}});
    const phoneMessages = [];
    const connect = {getReadyState({success}) {success({status:1});},send({data,success}) {
      assert(data && typeof data==='object' && typeof data.data==='string','XMS send requires an Object payload');
      phoneMessages.push(JSON.parse(data.data));if(success)success();
    }};
    if (timestampMs !== undefined) {
      assert(Number.isSafeInteger(timestampMs) && timestampMs >= 0);
      vm.runInContext('Date.now = () => ' + timestampMs, context);
    }
    const native = {
      '@app-module/system.storage': {
        get({success,fail}) { if (failStorage) fail('unavailable',300); else success(storedValue); },
        set({value,success}) { storedValue=value;success(); }
      },
      '@app-module/system.router': {push(data) {navigation.push(data.uri);}, back() {}},
      '@app-module/system.vibrator': {vibrate() {}},
      '@app-module/system.interconnect': {instance() {return connect;}},
      '@app-module/system.fetch': {fetch() {throw new Error('Unexpected network request during startup');}}
    };
    const requireNative = name => {
      assert(Object.hasOwn(native,name), 'Unexpected runtime import: ' + name);
      return native[name];
    };
    context.$app_require$ = requireNative;
    context.setInterval = context.setTimeout = fn => { scheduled.set(++timer,fn); return timer; };
    context.clearInterval = context.clearTimeout = id => scheduled.delete(id);
    context.aiot = {
      __ce__(tag, config, children) {return {tag, opts:config.__opts__, children};},
      __ci__(config, render) {return config.__opts__.shown() ? render() : [];},
      __cf__(config, render) {return config.__opts__.exp().map((value,index)=>render(index,value));}
    };
    function evaluate(source, path) {
      return vm.runInContext('(' + source.replace(/^export default /,'') + ')',context,{filename:path,timeout:2000});
    }
    const app = {};
    evaluate(await zip.file('app.js').async('string'),'app.js')(context,context,context,app,{});
    assert.equal(app.default.manifest.package,manifest.package);
    if (app.default.onCreate) app.default.onCreate();
    const compiledPath = route + '/' + pageConfig.component + '.js';
    const exports = {};
    evaluate(await zip.file(compiledPath).async('string'),compiledPath)(context,context,context,exports,{});
    assert.equal(typeof exports.entry,'function','Missing component entry');
    const result = {}; exports.entry(result);
    const definition = result.default;
    const page = Object.assign({},definition,definition.data);
    if (page.onInit) await page.onInit();
    // The first frame must render before storage callbacks and onShow complete.
    const firstFrame = definition.template(page);
    assert.equal(firstFrame.tag,'div');
    if (page.onShow) await page.onShow();
    await new Promise(resolve=>setImmediate(resolve));
    const tree = definition.template(page);
    const nodes = [];
    const walk = n => {
      if (Array.isArray(n)) {n.forEach(walk);return;}
      if (!n) return;
      nodes.push(n);(n.children||[]).forEach(walk);
    };
    walk(tree);
    const values = nodes.map(n => typeof n.opts.value === 'function' ? n.opts.value() : n.opts.value).filter(v=>v!==undefined);
    assert(values.some(v=>String(v).includes(route==='pages/index'?'TOTP':route==='pages/receive'?'Phone':'Add')), 'Missing visible screen heading');
    assert(nodes.some(n=>n.opts.events && n.opts.events.click),'Missing interactive controls');
    if (route === 'pages/index') {
      if (failStorage) assert(values.some(v=>String(v).includes('Storage')), 'Storage failure must be visible');
      else if (!accounts.length) assert(values.some(v=>String(v).includes('No accounts')), 'Missing empty state');
      else assert(values.some(v=>/^\d{3} \d{3}$/.test(v)),'Stored account must render an OTP');
      page.goAddPage();assert.equal(navigation.at(-1),'pages/manual');
      if(page.goPhonePage) {page.goPhonePage();assert.equal(navigation.at(-1),'pages/receive');}
    } else if (route === 'pages/receive') {
      assert.equal(page.connectionText,'Phone connected.');
      const id='a'.repeat(32);
      connect.onmessage({data:JSON.stringify({type:'rw6-totp-hello',version:1,id})});
      assert(phoneMessages.some(p=>p.type==='rw6-totp-ready' && p.id===id),'Watch did not complete phone handshake');
      await connect.onmessage({data:JSON.stringify({type:'rw6-totp-sync',version:1,id,account:{
        name:'Phone fixture',secret:'JBSWY3DPEHPK3PXP',algorithm:'SHA1',digits:6,period:30,syncId:'f'.repeat(32)
      }})});
      await new Promise(resolve=>setImmediate(resolve));
      assert(phoneMessages.some(p=>p.type==='rw6-totp-result' && p.id===id && p.status===(failStorage?'error':'saved')),'Missing persistent sync acknowledgement');
      if(!failStorage) assert(JSON.parse(storedValue).some(a=>a.name==='Phone fixture' && a.phoneSyncId==='f'.repeat(32)), 'Compiled receiver did not persist its phone account');
      assert.equal(page.pending,false,'Sync draft leaked after save');
    } else {
      assert.equal(page.step,0,'Manual entry did not initialize');
      assert.equal(page.keyRows.length,4,'Missing on-watch Base32 keyboard');
      assert.equal(page.secret,'','Secret draft should start empty');
      const keys = nodes.filter(n=>n.tag==='input' && typeof n.opts.value==='function' && /^[A-Z2-7]$/.test(n.opts.value()));
      assert.equal(keys.length,32,'Every Base32 key must be rendered');
      const key = keys.find(n=>n.opts.value()==='M');
      key.opts.events.click({});
      assert.equal(page.secret,'M','Compiled keyboard event did not reach its field');
      page.clearField();
    }
    trees[route] = {values,nodes:nodes.length,firstFrame:true};
    if (page.onHide) page.onHide();
    if (page.onDestroy) page.onDestroy();
    assert.equal(scheduled.size,0,'Page timers leaked');
    if (app.default.onDestroy) app.default.onDestroy();
  }
  return trees;
}
module.exports = {smokeRpk};
if (require.main === module) {
  smokeRpk(fs.readFileSync(process.argv[2])).then(result=>console.log('Compiled RPK startup passed:',
    Object.fromEntries(Object.entries(result).map(([route,screen])=>[route,{nodes:screen.nodes,firstFrame:screen.firstFrame}]))))
    .catch(e=>{console.error('Compiled RPK startup failed:',e.stack);process.exitCode=1;});
}
