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
    const context = vm.createContext({console:{log() {},warn() {},error() {}}});
    if (timestampMs !== undefined) {
      assert(Number.isSafeInteger(timestampMs) && timestampMs >= 0);
      vm.runInContext('Date.now = () => ' + timestampMs, context);
    }
    const native = {
      '@app-module/system.storage': {
        get({success,fail}) { if (failStorage) fail('unavailable',300); else success(JSON.stringify(accounts)); },
        set({success}) { success(); }
      },
      '@app-module/system.router': {push(data) {navigation.push(data.uri);}, back() {}},
      '@app-module/system.vibrator': {vibrate() {}},
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
    assert(values.some(v=>String(v).includes(route==='pages/index'?'TOTP':'QR')), 'Missing visible screen heading');
    assert(nodes.some(n=>n.opts.events && n.opts.events.click),'Missing interactive controls');
    if (route === 'pages/index') {
      if (failStorage) assert(values.some(v=>String(v).includes('저장소')), 'Storage failure must be visible');
      else if (!accounts.length) assert(values.some(v=>String(v).includes('등록된 TOTP')), 'Missing empty state');
      else assert(values.some(v=>/^\d{3} \d{3}$/.test(v)),'Stored account must render an OTP');
      page.goAddPage();assert.equal(navigation.at(-1),'pages/add');
    } else {
      assert(/^https:\/\/.*#sid=RW6/.test(page.qrValue),'Pairing screen did not initialize');
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
