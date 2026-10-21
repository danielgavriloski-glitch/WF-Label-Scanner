(() => {
  'use strict';
  const KEY = 'tdMarKBrandSettings_v1';
  const D = {name: 'TD Production', accent: '#167a74', gold: '#be9858', logo: ''};
  const lookup = id => document.getElementById(id);
  const native = window.TDShortcut;
  let chosen = null, loading = false, imageJob = 0;

  const hdr = document.querySelector('header');
  const logoTag = document.createElement('img');
  logoTag.id = 'tdMarkBrandBadge'; logoTag.src = 'mark_m_web.png'; logoTag.alt = 'MarK M';
  const customer = document.createElement('img');
  customer.id = 'markCustomerLogo'; customer.alt = 'Лого на корисник';
  const brandLabel = document.createElement('span');
  brandLabel.id = 'markBrandLabel';
  brandLabel.style.cssText = 'display:block;font-size:12px;opacity:.9;margin-top:3px;letter-spacing:.04em';
  if (hdr) hdr.append(logoTag, customer, brandLabel);

  const btn = document.createElement('button');
  btn.id = 'markSettingsBtn'; btn.type = 'button'; btn.title = 'MarK изглед и лого';
  btn.innerHTML = '<img src="mark_m_web.png" alt="MarK">';
  document.body.append(btn);
  const overlay = document.createElement('div');
  overlay.id = 'markBrandSettings';
  overlay.innerHTML = `<div id="markBrandBody" role="dialog" aria-modal="true" aria-label="MarK изглед">
    <h2>MarK · TD Booking</h2>
    <p>Основниот изглед е TD Production. Додади свое лого и избери бои по желба.</p>
    <label for="markBrandName">Назив на бренд</label><input id="markBrandName" maxlength="75" placeholder="TD Production">
    <label for="markBrandFile">Лого од телефон (PNG/JPG/WebP)</label><input type="file" id="markBrandFile" accept="image/png,image/jpeg,image/webp"><div id="markBrandPreview" role="status"></div>
    <label for="markBrandAccent">Главна боја</label><input type="color" id="markBrandAccent" value="#167a74">
    <label for="markBrandGold">Златен акцент</label><input type="color" id="markBrandGold" value="#be9858">
    <section id="markShortcutCard" aria-label="Икона на почетен екран">
      <h3>Икона на почетен екран</h3>
      <div class="markShortcutSample"><img id="markShortcutPreview" src="td_default_logo.png" alt="Избрано лого за кратенката"><strong id="markShortcutName">TD Booking</strong></div>
      <p>Стандардно е TD логото. Кога ќе избереш свое лого, тоа ќе се користи и за кратенката.</p>
      <button type="button" id="markBrandShortcut">Додај на почетен екран</button>
      <button type="button" class="secondary" id="markShortcutResetLogo">Врати TD лого</button>
      <p id="markShortcutStatus" role="status" aria-live="polite"></p>
      <p class="muted">Во списокот со апликации останува M иконата. Кратенката ја отвора истата TD Booking апликација.</p>
    </section>
    <div class="brandActions"><button type="button" id="markBrandSave">Зачувај</button><button type="button" class="secondary" id="markBrandReset">TD стандард</button><button type="button" class="secondary" id="markBrandClose">Затвори</button></div>
    <p>Логото и боите се зачувуваат локално на уредот. TD Booking 6.19.</p>
  </div>`;
  document.body.append(overlay);

  const state = () => {
    try { return {...D, ...JSON.parse(localStorage.getItem(KEY) || '{}')}; }
    catch (_) { return {...D}; }
  };
  const safeHex = value => /^#[0-9a-f]{6}$/i.test(value || '') ? value : D.accent;
  const hexRgb = value => [1, 3, 5].map(n => parseInt(value.slice(n, n + 2), 16));
  const shade = (rgb, k) => '#' + rgb.map(v => Math.max(0, Math.min(255, Math.round(v * k))).toString(16).padStart(2, '0')).join('');
  const homeName = v => (v.name && v.name.trim() !== D.name ? v.name.trim() : 'TD Booking');
  const message = value => { lookup('markShortcutStatus').textContent = value; };
  let supported = false;
  try { supported = !!native && native.isSupported(); } catch (_) {}
  function busy(value) {
    loading = value;
    lookup('markBrandSave').disabled = value;
    lookup('markBrandShortcut').disabled = value || !supported;
  }
  function pending() {
    const old = state();
    return {name: lookup('markBrandName').value.trim() || D.name,
      accent: lookup('markBrandAccent').value, gold: lookup('markBrandGold').value,
      logo: chosen === null ? old.logo : chosen};
  }
  function preview(v) {
    lookup('markShortcutPreview').src = v.logo && v.logo.startsWith('data:image/') ? v.logo : 'td_default_logo.png';
    lookup('markShortcutName').textContent = homeName(v);
  }
  function apply(v) {
    const accent = safeHex(v.accent), gold = /^#[0-9a-f]{6}$/i.test(v.gold || '') ? v.gold : D.gold;
    document.documentElement.style.setProperty('--mb-accent', accent);
    document.documentElement.style.setProperty('--mb-deep', shade(hexRgb(accent), .65));
    document.documentElement.style.setProperty('--mb-gold', gold);
    if (v.logo && v.logo.startsWith('data:image/')) { customer.src = v.logo; customer.classList.add('visible'); }
    else { customer.removeAttribute('src'); customer.classList.remove('visible'); }
    lookup('markBrandName').value = (v.name || D.name).slice(0, 75);
    brandLabel.textContent = (v.name || D.name).slice(0, 75);
    lookup('markBrandAccent').value = accent; lookup('markBrandGold').value = gold;
    preview(v);
  }
  function updatePinned(v) {
    if (native && typeof native.updateShortcut === 'function') {
      try { native.updateShortcut(homeName(v), v.logo || ''); }
      catch (_) { message('Логото е зачувано. Притисни „Додај на почетен екран“ за повторно освежување.'); }
    }
  }
  function commit(v, close, update) {
    try {
      localStorage.setItem(KEY, JSON.stringify(v));
      chosen = null; apply(v);
      if (update) updatePinned(v);
      if (close) overlay.classList.remove('open');
      return v;
    } catch (_) { message('Не можам да го зачувам логото; пробај со помала слика.'); return null; }
  }
  function discardPending() {
    imageJob++; chosen = null; busy(false);
    lookup('markBrandFile').value = ''; lookup('markBrandPreview').textContent = '';
    apply(state());
  }

  window.TDBrandShortcutResult = (status, text) => { busy(false); message(text || ''); };
  apply(state()); busy(false);
  if (!supported) message('Додавањето кратенка не е поддржано од овој почетен екран. Логото во апликацијата можеш да го менуваш.');
  if (!window.TDNative || typeof window.TDNative.marKInfo !== 'function') lookup('markBrandFile').disabled = true;
  btn.onclick = () => { discardPending(); overlay.classList.add('open'); };
  lookup('markBrandClose').onclick = () => { discardPending(); overlay.classList.remove('open'); };
  overlay.onclick = e => { if (e.target === overlay) { discardPending(); overlay.classList.remove('open'); } };
  lookup('markBrandName').oninput = () => preview(pending());
  lookup('markBrandSave').onclick = () => { if (!loading) commit(pending(), true, true); };
  lookup('markBrandShortcut').onclick = () => {
    if (loading || !supported) return;
    const v = commit(pending(), false, false);
    if (!v) return;
    message('Се подготвува кратенката…');
    lookup('markBrandShortcut').disabled = true;
    try { native.pinShortcut(homeName(v), v.logo || ''); }
    catch (_) { busy(false); message('Барањето не успеа. Пробај повторно.'); }
  };
  lookup('markShortcutResetLogo').onclick = () => {
    imageJob++; chosen = null; busy(false); lookup('markBrandFile').value = '';
    lookup('markBrandPreview').textContent = '';
    if (commit({...state(), logo: ''}, false, true)) message('Избрано е стандардното TD лого.');
  };
  lookup('markBrandReset').onclick = () => {
    try { localStorage.removeItem(KEY); discardPending(); apply(D); updatePinned(D); message('Вратен е стандардниот TD изглед и лого.'); }
    catch (_) { message('Не успеа враќањето на TD изгледот. Пробај повторно.'); }
  };
  lookup('markBrandFile').onchange = e => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    if (file.size > 8 * 1024 * 1024) { message('Избери лого до 8 MB.'); return; }
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) { message('Избери PNG, JPG или WebP.'); return; }
    const job = ++imageJob;
    busy(true); lookup('markBrandPreview').textContent = 'Се подготвува логото…';
    const failed = () => { if (job !== imageJob) return; busy(false); lookup('markBrandPreview').textContent = ''; message('Сликата не може да се прочита. Избери друго лого.'); };
    const reader = new FileReader(); reader.onerror = failed;
    reader.onload = () => {
      if (job !== imageJob) return;
      const img = new Image(); img.onerror = failed;
      img.onload = () => {
        if (job !== imageJob) return;
        try {
          const c = document.createElement('canvas'), scale = Math.min(1, 420 / Math.max(img.width, img.height));
          c.width = Math.max(1, Math.round(img.width * scale)); c.height = Math.max(1, Math.round(img.height * scale));
          const ctx = c.getContext('2d'); ctx.drawImage(img, 0, 0, c.width, c.height);
          chosen = c.toDataURL('image/png');
          const pixels = ctx.getImageData(0, 0, c.width, c.height).data;
          let best = null, bestScore = 0;
          for (let i = 0; i < pixels.length; i += Math.max(4, Math.floor(pixels.length / 3000 / 4) * 4)) {
            const r = pixels[i], g = pixels[i + 1], b = pixels[i + 2], a = pixels[i + 3];
            const range = Math.max(r, g, b) - Math.min(r, g, b), lum = (r + g + b) / 3, score = range * a / 255;
            if (lum > 30 && lum < 225 && score > bestScore) { best = [r, g, b]; bestScore = score; }
          }
          if (best && bestScore > 30) lookup('markBrandAccent').value = shade(best, .77);
          busy(false); lookup('markBrandPreview').textContent = '✓ Логото е подготвено';
          message('Зачувај го логото или додај ја кратенката.'); preview(pending());
        } catch (_) { failed(); }
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  };
})();
