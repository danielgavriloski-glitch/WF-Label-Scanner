const fs = require('node:fs');
const assert = require('node:assert/strict');
const {JSDOM} = require('jsdom');
const code = fs.readFileSync('assets/mark_brand_v619.js', 'utf8');
const KEY = 'tdMarKBrandSettings_v1';
const IMAGE = 'data:image/png;base64,iVBORw0KGgo=';
const results = [];
const domains = {
  tdBooking6: '[{"id":"wedding-unchanged","price":640,"deposit":100,"signatures":["original-ink"]}]',
  tdBookingFinanceV1: '{"wedding-unchanged":{"crew":[{"fee":500,"payments":[120]}],"express":[300]}}',
  tdCloudConfigV1: '{"fixture":"original"}',
  tdCloudAuthV1: '{"fixture":"original-auth"}',
  markPairingFixture: 'original-pairing'
};
function fixture({brand, supported = true, noNative = false, quota = false, nativeThrows = false, deferredImage = false, badImage = false} = {}) {
  const dom = new JSDOM('<!doctype html><html><body><header><b>TD Booking</b></header></body></html>', {url: 'https://local-test.invalid/', runScripts: 'outside-only'});
  const w = dom.window, calls = [], writes = [], pendingImages = [];
  for (const [key, value] of Object.entries(domains)) w.localStorage.setItem(key, value);
  if (brand) w.localStorage.setItem(KEY, JSON.stringify(brand));
  const originalSet = w.Storage.prototype.setItem;
  const originalRemove = w.Storage.prototype.removeItem;
  w.Storage.prototype.setItem = function(key, value) { writes.push(key); if (quota) throw new Error('quota'); return originalSet.call(this, key, value); };
  w.Storage.prototype.removeItem = function(key) { writes.push(key); return originalRemove.call(this, key); };
  w.TDNative = {marKInfo() { return '{}'; }};
  if (!noNative) w.TDShortcut = {
    isSupported: () => supported,
    pinShortcut(name, image) {
      if (nativeThrows) throw new Error('native failure');
      calls.push({type: 'pin', name, image});
      w.TDBrandShortcutResult('requested', 'Потврди „Додај“ на телефонот.');
    },
    updateShortcut(name, image) { calls.push({type: 'update', name, image}); }
  };
  w.FileReader = class { readAsDataURL() { this.result = IMAGE; this.onload(); } };
  w.Image = class {
    constructor() { this.width = 1000; this.height = 500; }
    set src(value) {
      const finish = () => badImage ? this.onerror() : this.onload();
      if (deferredImage) pendingImages.push(finish); else finish();
    }
  };
  w.HTMLCanvasElement.prototype.getContext = function() {
    return {drawImage() {}, getImageData() { return {data: [20, 150, 80, 255]}; }};
  };
  w.HTMLCanvasElement.prototype.toDataURL = function() { assert.equal(this.width, 420); assert.equal(this.height, 210); return IMAGE; };
  w.eval(code);
  const el = id => w.document.getElementById(id);
  const click = id => el(id).click();
  const state = () => JSON.parse(w.localStorage.getItem(KEY) || '{}');
  const select = (type = 'image/png', size = 128) => {
    Object.defineProperty(el('markBrandFile'), 'files', {configurable: true, value: [{type, size}]});
    el('markBrandFile').dispatchEvent(new w.Event('change'));
  };
  const check = () => {
    for (const [key, value] of Object.entries(domains)) assert.equal(w.localStorage.getItem(key), value, key + ' must not change');
    assert.ok(writes.every(key => key === KEY), 'Only the existing brand preference key may change');
    assert.equal(el('tdMarkBrandBadge').getAttribute('src'), 'mark_m_web.png');
    dom.window.close();
  };
  return {w, el, calls, click, state, select, check, pendingImages};
}
function test(name, run) { run(); results.push(name); }
test('Default uses original TD logo and TD Booking name without writing data', () => {
  const f = fixture(); assert.equal(f.el('markShortcutPreview').getAttribute('src'), 'td_default_logo.png');
  assert.equal(f.el('markShortcutName').textContent, 'TD Booking'); assert.equal(f.calls.length, 0); f.check();
});
test('Pinning default TD passes the default image to native and requests launcher confirmation', () => {
  const f = fixture(); f.click('markSettingsBtn'); f.click('markBrandShortcut');
  assert.deepEqual(f.calls, [{type: 'pin', name: 'TD Booking', image: ''}]);
  assert.match(f.el('markShortcutStatus').textContent, /Потврди/); assert.ok(f.el('markBrandSettings').classList.contains('open')); f.check();
});
test('Existing user brand and custom logo survive the update', () => {
  const brand = {name: 'Studio X', accent: '#225566', gold: '#bbaa77', logo: IMAGE};
  const f = fixture({brand}); assert.deepEqual(f.state(), brand); assert.equal(f.el('markShortcutPreview').getAttribute('src'), IMAGE); f.check();
});
test('Choosing a custom file previews the full image before saving', () => {
  const f = fixture(); f.select(); assert.equal(f.el('markShortcutPreview').getAttribute('src'), IMAGE);
  assert.equal(f.state().logo, undefined); assert.equal(f.el('markBrandShortcut').disabled, false); f.check();
});
test('Save changes only brand preferences and updates an existing shortcut', () => {
  const f = fixture(); f.click('markSettingsBtn'); f.select(); f.el('markBrandName').value = 'Photo Studio'; f.click('markBrandSave');
  assert.equal(f.state().logo, IMAGE); assert.equal(f.state().name, 'Photo Studio');
  assert.deepEqual(f.calls, [{type: 'update', name: 'Photo Studio', image: IMAGE}]); f.check();
});
test('Pin saves the current selection and sends the chosen logo and brand name', () => {
  const f = fixture(); f.select('image/webp'); f.el('markBrandName').value = 'My Studio'; f.click('markBrandShortcut');
  assert.equal(f.state().logo, IMAGE); assert.deepEqual(f.calls, [{type: 'pin', name: 'My Studio', image: IMAGE}]); f.check();
});
test('Reset logo restores TD while preserving the selected name and theme', () => {
  const f = fixture({brand: {name: 'Studio X', accent: '#225566', gold: '#bbaa77', logo: IMAGE}});
  f.click('markShortcutResetLogo'); assert.deepEqual(f.state(), {name: 'Studio X', accent: '#225566', gold: '#bbaa77', logo: ''});
  assert.deepEqual(f.calls, [{type: 'update', name: 'Studio X', image: ''}]); f.check();
});
test('TD standard restores the prior TD settings and updates the shortcut', () => {
  const f = fixture({brand: {name: 'Studio X', accent: '#225566', gold: '#bbaa77', logo: IMAGE}});
  f.click('markBrandReset'); assert.deepEqual(f.state(), {}); assert.equal(f.el('markShortcutPreview').getAttribute('src'), 'td_default_logo.png');
  assert.deepEqual(f.calls, [{type: 'update', name: 'TD Booking', image: ''}]); f.check();
});
test('Closing without saving discards the new logo', () => {
  const f = fixture(); f.click('markSettingsBtn'); f.select(); f.click('markBrandClose'); f.click('markSettingsBtn');
  assert.equal(f.el('markShortcutPreview').getAttribute('src'), 'td_default_logo.png'); assert.equal(f.calls.length, 0); f.check();
});
test('Oversized images are rejected without changing saved preferences', () => {
  const f = fixture(); f.select('image/png', 9 * 1024 * 1024); assert.match(f.el('markShortcutStatus').textContent, /8 MB/); assert.deepEqual(f.state(), {}); f.check();
});
test('Unsupported file types do not change the logo', () => {
  const f = fixture(); f.select('application/pdf'); assert.match(f.el('markShortcutStatus').textContent, /PNG/); assert.deepEqual(f.state(), {}); f.check();
});
test('Undecodable images leave controls usable and keep saved data', () => {
  const f = fixture({badImage: true}); f.select(); assert.equal(f.el('markBrandSave').disabled, false); assert.match(f.el('markShortcutStatus').textContent, /не може/); f.check();
});
test('Image processing disables save and pin until the image is ready', () => {
  const f = fixture({deferredImage: true}); f.select(); assert.equal(f.el('markBrandSave').disabled, true); assert.equal(f.el('markBrandShortcut').disabled, true);
  f.pendingImages[0](); assert.equal(f.el('markBrandSave').disabled, false); assert.equal(f.el('markBrandShortcut').disabled, false); f.check();
});
test('Reset during file processing cannot restore the discarded custom image', () => {
  const f = fixture({deferredImage: true}); f.select(); f.click('markShortcutResetLogo'); f.pendingImages[0]();
  assert.equal(f.state().logo, ''); assert.equal(f.el('markShortcutPreview').getAttribute('src'), 'td_default_logo.png'); f.check();
});
test('Full storage prevents pinning and preserves all previous records', () => {
  const f = fixture({quota: true}); f.click('markBrandShortcut'); assert.equal(f.calls.length, 0); assert.match(f.el('markShortcutStatus').textContent, /зачувам/); f.check();
});
test('Unsupported launchers disable pinning and still allow in-app branding', () => {
  const f = fixture({supported: false}); assert.equal(f.el('markBrandShortcut').disabled, true); f.select(); f.click('markBrandSave'); assert.equal(f.state().logo, IMAGE); f.check();
});
test('Missing native bridge disables only the new shortcut action', () => {
  const f = fixture({noNative: true}); assert.equal(f.el('markBrandShortcut').disabled, true); f.select(); f.click('markBrandSave'); assert.equal(f.state().logo, IMAGE); f.check();
});
test('Native failure is reported without losing branding or business data', () => {
  const f = fixture({nativeThrows: true}); f.select(); f.click('markBrandShortcut'); assert.match(f.el('markShortcutStatus').textContent, /не успеа/); assert.equal(f.state().logo, IMAGE); f.check();
});
test('Damaged brand preferences fall back to TD without touching any business record', () => {
  const f = fixture(); f.w.localStorage.setItem(KEY, '{invalid'); f.click('markSettingsBtn'); assert.equal(f.el('markShortcutName').textContent, 'TD Booking'); f.check();
});
console.log(JSON.stringify({passed: results.length, tests: results}, null, 2));
