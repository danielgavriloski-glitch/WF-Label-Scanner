/* TD Booking 6.20: calendar-first pages using the existing booking and finance records. */
(() => {
  'use strict';
  if (window.TDPages || !document.querySelector('main') || typeof navigate !== 'function') return;
  const el = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const raw = {};
  ['navigate','openBooking','newBooking','closeEditor','preview','backToEdit','saveBooking','values','doc','closeFinanceDocument','printReport','drawFinance','financeRefresh'].forEach(key => raw[key] = window[key]);
  const main = document.querySelector('main');
  const allSections = ['dashboard','overview','listCard','editor','printCard','finance','staffView','reportsView','backupView','settingsView','financePrint','reportPrint','tdPageSettings','tdSyncPage','tdWeddingContext','tdWeddingOverview','tdEditorTrail','tdDocumentTrail'];
  const iso = value => `${value.getFullYear()}-${String(value.getMonth()+1).padStart(2,'0')}-${String(value.getDate()).padStart(2,'0')}`;
  const today = () => iso(new Date());
  const dateObject = value => /^\d{4}-\d{2}-\d{2}$/.test(String(value || '')) ? new Date(Number(value.slice(0,4)),Number(value.slice(5,7))-1,Number(value.slice(8,10)),12) : null;
  const months = ['јануари','февруари','март','април','мај','јуни','јули','август','септември','октомври','ноември','декември'];
  const dateLabel = value => { const d = dateObject(value); return d ? `${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()}` : 'Датум не е внесен'; };
  const weddings = () => Array.isArray(data) ? data.filter(b => b && b.id) : [];
  const wedding = id => weddings().find(b => String(b.id) === String(id));
  const ledger = id => {
    const f = typeof finance === 'object' && finance ? finance[id] || {} : {};
    return {...f, ...Object.fromEntries(['clientPayments','crew','express','extras','expenses'].map(k => [k,Array.isArray(f[k]) ? f[k] : []]))};
  };
  const money = n => typeof euro === 'function' ? euro(n) : (Number(n)||0).toFixed(2)+' €';
  const sum = (a, key) => a.reduce((n,x) => n+(Number(x?.[key])||0),0);
  const paidCrew = c => sum(Array.isArray(c.payments) ? c.payments : [],'amount');
  function figures(b) {
    const f = ledger(b.id), billed = totalContracted(b,f), received = totalClient(b,f), due = totalDue(b,f);
    const fee = sum(f.crew,'fee'), paid = f.crew.reduce((n,c) => n+paidCrew(c),0);
    return {f,billed,received,due,fee,paid,remaining:Math.max(0,fee-paid),express:sum(f.express,'amount')};
  }
  let route = {page:'calendar'}, historyIndex = 0, rendering = false, queued = false, legacyDepth = 0;
  let selectedDay = today(), calendarMonth = new Date(new Date().getFullYear(),new Date().getMonth(),1,12);
  let editorBaseline = null, signatureDirty = false, documentReturn = null, financeTab = 'client', staffTab = 'local';
  function legacy(fn, ...args) {
    legacyDepth++;
    try { return fn(...args); } finally { legacyDepth--; }
  }

  function section(id, html, className = 'card no-print') {
    const node = document.createElement('section'); node.id = id; node.className = className+' viewhidden';
    node.innerHTML = html; main.append(node); return node;
  }
  function showOnly(ids) {
    allSections.forEach(id => el(id)?.classList.toggle('viewhidden',!ids.includes(id)));
    ids.forEach(id => el(id)?.classList.remove('hidden'));
  }
  function icon(name) {
    const paths = {calendar:'M8 3v4m8-4v4M4 10h16M5 5h14a1 1 0 0 1 1 1v14H4V6a1 1 0 0 1 1-1Z',weddings:'M5 4h14v17H5zM8 8h8M8 12h8M8 16h5',finance:'M4 6h16v14H4zM4 10h16m-4 4h2',staff:'M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8 0a3 3 0 1 0 0-6M2 21v-3a6 6 0 0 1 12 0v3m3-7a5 5 0 0 1 5 5v2',settings:'M4 7h16M4 17h16M8 4v6m8 4v6'};
    return `<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="${paths[name]||paths.weddings}"/></svg>`;
  }
  const navItems = [['calendar','Календар'],['weddings','Свадби'],['finance','Финансии'],['staff','Екипа'],['settings','Поставки']];
  el('bottomNav').innerHTML = navItems.map(([id,label]) => `<button type="button" data-route="${id}">${icon(id)}<span>${label}</span></button>`).join('');
  el('drawer').innerHTML = '<h2>TD Booking</h2>'+[...navItems.slice(0,2),['dashboard','Бизнис преглед'],...navItems.slice(2,4),['reports','Извештаи'],['settings','Поставки']].map(([id,label])=>`<button type="button" class="secondary" data-route="${id}">${label}</button>`).join('')+'<button type="button" data-action="close-drawer">Затвори</button>';
  const drawerTitle = document.createElement('span'); drawerTitle.id = 'tdCurrentPage'; drawerTitle.className = 'td-current-page'; (document.querySelector('header>div') || document.querySelector('header')).append(drawerTitle);
  const headerInfo = document.querySelector('header>div'), defaultHeaderLogo = document.querySelector('header>img:first-child');
  if (headerInfo && el('markBrandLabel')) headerInfo.append(el('markBrandLabel'));
  const alignHeaderBrand = () => { if(defaultHeaderLogo)defaultHeaderLogo.hidden = !!el('markCustomerLogo')?.classList.contains('visible'); };
  alignHeaderBrand();
  if(el('markCustomerLogo'))new MutationObserver(alignHeaderBrand).observe(el('markCustomerLogo'),{attributes:true,attributeFilter:['class']});

  el('overview').innerHTML = `<div class="td-page-heading"><div><p class="td-eyebrow">ТВОИТЕ СВАДБИ</p><h2>Календар</h2></div><button type="button" data-action="new">+ Нова свадба</button></div>
    <div class="td-month-controls"><button type="button" class="secondary" data-action="previous-month" aria-label="Претходен месец">‹</button><h3 id="monthTitle" aria-live="polite"></h3><button type="button" class="secondary" data-action="next-month" aria-label="Следен месец">›</button></div>
    <div class="td-calendar-meta"><span id="tdMonthCount"></span><button type="button" class="secondary td-small" data-action="today">Денес</button></div>
    <div id="calendar" aria-label="Календар со свадби"></div><p class="td-calendar-key"><span></span> Ден со свадба · бројката означува повеќе свадби</p>
    <div class="td-day-heading"><h3 id="tdSelectedDate"></h3><button type="button" class="secondary td-small" data-action="new-on-day">+ На овој датум</button></div><div id="tdDayBookings" aria-live="polite"></div>
    <div class="td-upcoming"><h3>Следни свадби</h3><div id="tdUpcomingBookings"></div><button type="button" class="secondary" data-route="weddings">Види ги сите свадби →</button></div>`;
  el('listCard').innerHTML = `<div class="td-page-heading"><div><p class="td-eyebrow">ЕДНА СВАДБА, СИТЕ ДЕТАЛИ</p><h2>Свадби</h2></div><button type="button" data-action="new">+ Нова</button></div>
    <label class="td-search-label" for="searchWedding">Пребарај свадба</label><input id="searchWedding" type="search" placeholder="Младенци, телефон, место или датум">
    <div class="td-list-filter"><label for="tdWeddingStatus">Статус<select id="tdWeddingStatus"><option value="">Сите статуси</option>Резервирана</option><option>Завршена</option><option>Предадена</option><option>Откажана</option></select></label><span id="tdWeddingCount" class="muted" aria-live="polite"></span></div><div id="bookings"></div>`;
  section('tdWeddingContext',''); section('tdWeddingOverview','');
  section('tdEditorTrail',''); section('tdDocumentTrail','');
  section('tdPageSettings',`<p class="td-eyebrow">ОРГАНИЗАЦИЈА И ПОВРЗУВАЊЕ</p><h2>Поставки</h2><div class="td-settings-grid">
    <button type="button" class="td-settings-card" data-route="sync"><span class="td-settings-symbol">↔</span><strong>Синхронизација</strong><span>MarK PC, спарување и состојба на пренос</span></button>
    <button type="button" class="td-settings-card" data-route="backup"><span class="td-settings-symbol">↥</span><strong>Резервни копии</strong><span>Зачувај или врати ги твоите податоци</span></button>
    <button type="button" class="td-settings-card" data-route="packages"><span class="td-settings-symbol">€</span><strong>Мои пакети</strong><span>Пакети и редовни цени</span></button>
    <button type="button" class="td-settings-card" data-action="brand"><span class="td-settings-symbol">◈</span><strong>Изглед и лого</strong><span>Твое лого, бои и икона на почетниот екран</span></button>
    <button type="button" class="td-settings-card" data-action="cloud"><span class="td-settings-symbol">☁</span><strong>TD Cloud</strong><span>Интернет поврзување со апликацијата за екипа</span></button>
    <button type="button" class="td-settings-card" data-route="reports"><span class="td-settings-symbol">▤</span><strong>Извештаи</strong><span>Период, свадби, екипа и PDF извештаи</span></button></div><p class="td-app-version">TD Booking 6.20</p>`);
  section('tdSyncPage','<button type="button" class="secondary td-back" data-route="settings">‹ Поставки</button><h2>Синхронизација со MarK</h2><p class="muted">Поврзувањето и преносот се управуваат од оваа страница.</p>');
  const syncPanel = el('marKFullSyncPanel');
  if (syncPanel) el('tdSyncPage').append(syncPanel);
  const pairPanel = el('tdMarKPanel');
  if (pairPanel) {
    const details = document.createElement('details'); details.className = 'td-pairing-details'; details.id = 'tdPairingDetails';
    details.innerHTML = '<summary>Спарување на телефон и PC / поставки на врската</summary>';
    pairPanel.style.display = 'block'; details.append(pairPanel); el('tdSyncPage').append(details);
    el('tdMarkClose').onclick = () => { details.open = false; pairPanel.style.display = 'block'; };
    try { const info = JSON.parse(window.TDNative?.marKInfo?.() || '{}'); details.open = !info.paired; if (el('tdMarkURL') && info.endpoint) el('tdMarkURL').value = info.endpoint; } catch (_) {}
  }
  if (!syncPanel && !pairPanel) el('tdSyncPage').insertAdjacentHTML('beforeend','<p>Поврзувањето е достапно во Android апликацијата.</p>');

  const fin = el('finance'), groups = {};
  ['client','express','extras','expenses','crew'].forEach(id => { const d = document.createElement('div'); d.id = 'tdFinance_'+id; d.dataset.financeGroup = id; groups[id] = d; });
  let group = null;
  [...fin.children].forEach(node => {
    if (node.id === 'tdGuestPhotoPolicy') group = 'extras';
    if (node.tagName === 'H3') {
      const title = node.textContent.trim();
      if (title.startsWith('Уплата од клиент')) group = 'client';
      else if (title.startsWith('Експрес')) group = 'express';
      else if (title.startsWith('Дополнителни услуги')) group = 'extras';
      else if (title.startsWith('Други трошоци')) group = 'expenses';
      else if (title.startsWith('Ангажмани')) group = 'crew';
    }
    if (group) groups[group].append(node);
  });
  const finTabs = document.createElement('div'); finTabs.id = 'tdFinanceTabs'; finTabs.className = 'td-subtabs no-print';
  finTabs.innerHTML = [['client','Уплати'],['express','Експрес'],['extras','Услуги'],['expenses','Трошоци'],['crew','Екипа']].map(([id,label])=>`<button type="button" data-finance-tab="${id}">${label}</button>`).join('');
  el('financeSummary').after(finTabs); Object.values(groups).forEach(d => fin.append(d));
  el('finWedding').previousElementSibling?.classList.add('td-wedding-chooser-label');
  const staffTabs = document.createElement('div'); staffTabs.className = 'td-subtabs'; staffTabs.id = 'tdStaffTabs';
  staffTabs.innerHTML = '<button type="button" data-staff-tab="local">Локална екипа</button><button type="button" data-staff-tab="cloud">Покани и Cloud</button>';
  el('staffView').querySelector('h2').after(staffTabs);

  function bookingCard(b) {
    const f = figures(b), cancelled = b.status === 'Откажана';
    return `<button type="button" class="td-wedding-card${cancelled?' td-cancelled':''}" data-wedding="${esc(b.id)}"><span class="td-card-date">${esc(dateLabel(b.date))}${b.start?' · '+esc(b.start):''}</span><strong>${esc(b.bride||'—')} и ${esc(b.groom||'—')}</strong><span class="td-card-location">${esc(b.venue||'Местото не е внесено')}</span><span class="td-card-bottom"><span class="td-status${cancelled?' td-status-cancelled':''}">${esc(b.status||'Резервирана')}</span><span>${f.due>0?'За наплата '+money(f.due):'Платена'} <span aria-hidden="true">›</span></span></span></button>`;
  }
  function monthStamp() { return `${calendarMonth.getFullYear()}-${String(calendarMonth.getMonth()+1).padStart(2,'0')}`; }
  function drawCalendar() {
    const year = calendarMonth.getFullYear(), m = calendarMonth.getMonth(), start = (new Date(year,m,1).getDay()+6)%7, days = new Date(year,m+1,0).getDate();
    const items = weddings(), prefix = monthStamp();
    el('monthTitle').textContent = months[m][0].toUpperCase()+months[m].slice(1)+' '+year;
    el('tdMonthCount').textContent = items.filter(b=>String(b.date||'').startsWith(prefix+'-')).length+' свадби овој месец';
    let html = ['Пон','Вто','Сре','Чет','Пет','Саб','Нед'].map(d=>`<span class="td-day-name">${d}</span>`).join('');
    for (let i=0;i<42;i++) {
      const day = i-start+1;
      if (day<1 || day>days) { html += '<span class="td-calendar-empty" aria-hidden="true"></span>'; continue; }
      const value = `${prefix}-${String(day).padStart(2,'0')}`, events = items.filter(b=>b.date===value);
      html += `<button type="button" class="td-calendar-day${events.length?' has-wedding':''}${value===selectedDay?' selected':''}${value===today()?' today':''}" data-day="${value}" aria-pressed="${value===selectedDay}" aria-label="${esc(dateLabel(value)+', '+events.length+' свадби')}"><span>${day}</span>${events.length?`<span class="td-calendar-dot">${events.length>1?events.length:''}</span>`:''}</button>`;
    }
    el('calendar').innerHTML = html;
    el('tdSelectedDate').textContent = dateLabel(selectedDay);
    const same = items.filter(b=>b.date===selectedDay).sort((a,b)=>String(a.start||'').localeCompare(String(b.start||'')));
    el('tdDayBookings').innerHTML = same.length ? same.map(bookingCard).join('') : '<p class="td-empty">Нема свадби на избраниот ден.</p>';
    const upcoming = items.filter(b=>b.date>=today() && b.status!=='Откажана').sort((a,b)=>String(a.date+a.start).localeCompare(String(b.date+b.start))).slice(0,3);
    el('tdUpcomingBookings').innerHTML = upcoming.length ? upcoming.map(bookingCard).join('') : '<p class="td-empty">Нема претстојни свадби.</p>';
  }
  function drawList() {
    const q = el('searchWedding').value.trim().toLocaleLowerCase(), status = el('tdWeddingStatus').value;
    const items = weddings().filter(b=>!status || (b.status||'Резервирана')===status).filter(b=>!q || [b.bride,b.groom,b.phone,b.venue,b.date,dateLabel(b.date),b.status].join(' ').toLocaleLowerCase().includes(q));
    items.sort((a,b)=> {
      const futureA = a.date>=today(), futureB = b.date>=today();
      if (futureA!==futureB) return futureA ? -1 : 1;
      return futureA ? String(a.date).localeCompare(String(b.date)) : String(b.date).localeCompare(String(a.date));
    });
    el('tdWeddingCount').textContent = items.length+' од '+weddings().length+' свадби';
    el('bookings').innerHTML = items.length ? items.map(bookingCard).join('') : '<p class="td-empty">Нема свадби што одговараат на пребарувањето.</p>';
  }
  function metric(label, value, special = '') { return `<div class="td-metric ${special}"><span>${label}</span><strong>${money(value)}</strong></div>`; }
  function drawWedding() {
    const b = wedding(route.id); if (!b) return;
    const f = figures(b), tab = route.tab || 'overview';
    el('tdWeddingContext').innerHTML = `<button type="button" class="secondary td-back" data-action="back">‹ Назад</button><p class="td-eyebrow">ПРЕГЛЕД НА СВАДБА</p><h2>${esc(b.bride||'—')} и ${esc(b.groom||'—')}</h2><p class="td-wedding-when">${esc(dateLabel(b.date))}${b.start?' · '+esc(b.start):''}<br>${esc(b.venue||'Местото не е внесено')}</p><div class="td-profile-actions"><span class="td-status">${esc(b.status||'Резервирана')}</span><span class="muted">${b.locked?'Потпишан договор':'Договор во нацрт'}</span>${b.locked?'':`<button type="button" class="secondary td-small" data-action="edit-wedding">Уреди податоци</button>`}</div>
      <div class="td-profile-tabs" role="tablist" aria-label="Страници за свадбата">${[['overview','Преглед'],['money','Пари'],['crew','Екипа'],['contract','Договор']].map(([id,label])=>`<button type="button" role="tab" aria-selected="${tab===id}" data-wedding-tab="${id}" class="${tab===id?'active':''}">${label}</button>`).join('')}</div>`;
    el('tdWeddingOverview').innerHTML = `<h3>Сметка за оваа свадба</h3><div class="td-metrics">${metric('Пакет + услуги',f.billed)}${metric('Наплатено од клиенти',f.received)}${metric('За наплата',f.due,f.due>0?'td-amount-due':'')}</div><p class="td-express-note">Експрес · посебен приход: <strong>${money(f.express)}</strong></p><button type="button" class="secondary" data-wedding-tab="money">Уплати и трошоци →</button>
      <h3 class="td-section-heading">Податоци и услуги</h3><dl class="td-details"><div><dt>Пакет</dt><dd>${esc(packageName(b.package))}</dd></div><div><dt>Телефон</dt><dd>${esc(b.phone||'—')}</dd></div><div><dt>Рок за остаток</dt><dd>${b.due?esc(dateLabel(b.due)):'—'}</dd></div><div><dt>Испорака</dt><dd>${esc(b.delivery||'По договор')}</dd></div></dl><p class="td-preline">${esc(b.services||'Нема внесени услуги.')}</p>${b.giftNotes?`<h4>Вклучено гратис</h4><p class="td-preline">${esc(b.giftNotes)}</p>`:''}${b.notes?`<h4>Забелешки</h4><p class="td-preline">${esc(b.notes)}</p>`:''}
      <h3 class="td-section-heading">Екипа и хонорари</h3><div class="td-metrics">${metric('Договорено',f.fee)}${metric('Исплатено',f.paid)}${metric('За исплата',f.remaining)}</div>${f.f.crew.length?'<div class="td-crew-preview">'+f.f.crew.map(c=>`<p><strong>${esc(c.name)}</strong><span>${esc(c.role||'Соработник')} · ${money(c.fee)}</span></p>`).join('')+'</div>':'<p class="td-empty">Сè уште нема внесена екипа.</p>'}<button type="button" class="secondary" data-wedding-tab="crew">Екипа и исплати →</button>`;
  }
  function setFinanceTab(tab, update = true) {
    financeTab = Object.prototype.hasOwnProperty.call(groups,tab) ? tab : 'client';
    Object.entries(groups).forEach(([key,node]) => node.hidden = key!==financeTab);
    finTabs.hidden = route.page==='wedding' && route.tab==='crew';
    finTabs.querySelectorAll('button').forEach(button => {
      button.hidden = route.page==='wedding' && button.dataset.financeTab==='crew';
      button.classList.toggle('active',button.dataset.financeTab===financeTab);
    });
    if (update) { route.financeTab = financeTab; saveHistory(true); }
  }
  function setStaffTab(tab) {
    staffTab = tab==='cloud' ? 'cloud' : 'local';
    ['staffList','tdManualCrew'].forEach(id=>{if(el(id)) el(id).hidden = staffTab!=='local';});
    if (el('tdTeamPanel')) el('tdTeamPanel').hidden = staffTab!=='cloud';
    staffTabs.querySelectorAll('button').forEach(button=>button.classList.toggle('active',button.dataset.staffTab===staffTab));
  }
  function formSnapshot() { try { return JSON.stringify(raw.values()); } catch (_) { return null; } }
  function mayLeave() {
    const dirty = route.page==='editor' && editorBaseline!==null && formSnapshot()!==editorBaseline;
    const unsigned = signatureDirty && route.page==='wedding' && route.tab==='contract' && !wedding(route.id)?.locked;
    if (!dirty && !unsigned) return true;
    return confirm(unsigned ? 'Има незачувани потписи. Да ја напуштам страницата и да ги отфрлам?' : 'Има незачувани измени на свадбата. Да ја напуштам страницата и да ги отфрлам?');
  }
  function stateValue() { return {...route,tdBookingPagesV620:true,index:historyIndex,day:selectedDay,month:monthStamp()}; }
  function saveHistory(replace) {
    try {
      if (!replace) historyIndex++;
      // Keep the document path when the PC host supplies a base URL for assets.
      const nextUrl = new URL(location.href);
      nextUrl.hash = 'td/'+route.page+(route.id?'/'+encodeURIComponent(route.id):'');
      history[replace?'replaceState':'pushState'](stateValue(),'', nextUrl.href);
    } catch (_) {}
  }
  function updateNav() {
    const parent = ['sync','backup','packages','reports'].includes(route.page) ? 'settings' : ['wedding','editor','document'].includes(route.page) ? 'weddings' : route.page;
    document.querySelectorAll('#bottomNav button').forEach(button=>{
      const active = button.dataset.route===parent; button.classList.toggle('active',active);
      if (active) button.setAttribute('aria-current','page'); else button.removeAttribute('aria-current');
    });
    const names = {calendar:'Календар',weddings:'Свадби',wedding:'Преглед на свадба',editor:'Резервација',finance:'Финансии',staff:'Екипа',settings:'Поставки',sync:'Синхронизација',backup:'Резервни копии',packages:'Мои пакети',dashboard:'Бизнис преглед',reports:'Извештаи',document:'Документ','report-document':'Извештај'};
    el('tdCurrentPage').textContent = names[route.page] || 'TD Booking';
    document.body.dataset.tdPage = route.page; document.body.dataset.tdWeddingTab = route.tab || '';
    document.body.classList.add('td-pages-ready');
  }
  function prepareFinance(id) {
    raw.financeRefresh();
    if (id) { el('finWedding').value = id; raw.drawFinance(); }
  }
  function applyRoute(next, options = {}) {
    if (!options.force && !mayLeave()) return false;
    signatureDirty = false;
    route = {...next};
    if (['wedding','editor'].includes(route.page) && route.id && !wedding(route.id)) route = {page:'weddings'};
    toggleDrawer(false);
    const oldPage = {calendar:'weddings',wedding:'weddings',editor:'weddings',packages:'settings',settings:'',sync:'',document:'','report-document':''}[route.page] ?? route.page;
    legacy(raw.navigate,oldPage);
    if (route.page==='calendar') { showOnly(['overview']); drawCalendar(); }
    else if (route.page==='weddings') { showOnly(['listCard']); drawList(); }
    else if (route.page==='wedding') {
      current = route.id;
      if (route.tab==='money' || route.tab==='crew') {
        prepareFinance(route.id); showOnly(['tdWeddingContext','finance']);
        setFinanceTab(route.tab==='crew'?'crew':route.financeTab||'client',false);
      } else if (route.tab==='contract') {
        legacy(raw.openBooking,route.id); showContract(wedding(route.id));
        showOnly(['tdWeddingContext','printCard']);
        if (!wedding(route.id).locked) setTimeout(initCanvases,30);
      } else { route.tab='overview'; showOnly(['tdWeddingContext','tdWeddingOverview']); }
      drawWedding();
    } else if (route.page==='editor') {
      if (!options.keepEditor) {
        if (route.id) legacy(raw.openBooking,route.id); else legacy(raw.newBooking);
        if (route.date && !route.id) el('date').value = route.date;
      }
      if (route.id && wedding(route.id)?.locked) { return applyRoute({page:'wedding',id:route.id,tab:'contract'},{force:true,replace:true}); }
      showOnly(['tdEditorTrail','editor']);
      el('tdEditorTrail').innerHTML = '<button type="button" class="secondary td-back" data-action="close-editor">‹ Назад</button><strong>'+(route.id?'Уредување на свадба':'Нова свадба')+'</strong>';
      editorBaseline = formSnapshot();
    } else if (route.page==='finance') { showOnly(['finance']); setFinanceTab(route.financeTab||financeTab,false); }
    else if (route.page==='staff') { showOnly(['staffView']); setStaffTab(staffTab); }
    else if (route.page==='settings') showOnly(['tdPageSettings']);
    else if (route.page==='sync') showOnly(['tdSyncPage']);
    else if (route.page==='packages') showOnly(['settingsView']);
    else if (route.page==='backup') showOnly(['backupView']);
    else if (route.page==='reports') showOnly(['reportsView']);
    else if (route.page==='dashboard') showOnly(['dashboard']);
    else if (route.page==='document') {
      showOnly(['tdDocumentTrail','financePrint']);
      el('tdDocumentTrail').innerHTML = '<button type="button" class="secondary td-back" data-action="close-document">‹ Назад кон свадбата</button>';
    } else if (route.page==='report-document') showOnly(['reportPrint']);
    else { route={page:'calendar'}; showOnly(['overview']); drawCalendar(); }
    updateNav();
    if (!options.fromHistory) saveHistory(!!options.replace);
    window.scrollTo(0,0);
    return true;
  }
  function openWedding(id, tab='overview') { return applyRoute({page:'wedding',id:String(id),tab}); }
  function refreshDerived() {
    if (rendering) return;
    rendering = true;
    try {
      drawCalendar(); drawList();
      if (route.page==='wedding') {
        if (wedding(route.id)) drawWedding(); else applyRoute({page:'weddings'},{force:true,replace:true});
      }
      if (route.page==='editor' && current && wedding(current)) { route.id=current; saveHistory(true); }
    } finally { rendering=false; }
  }
  function scheduleRefresh() {
    if (queued) return; queued=true;
    Promise.resolve().then(()=>{queued=false;refreshDerived();});
  }
  window.navigate = page => legacyDepth ? raw.navigate(page) : applyRoute({page:page==='home'?'calendar':page});
  window.openBooking = id => legacyDepth ? raw.openBooking(id) : openWedding(id);
  window.goWedding = id => openWedding(id);
  window.goFinance = id => openWedding(id,'money');
  window.newBooking = () => legacyDepth ? raw.newBooking() : applyRoute({page:'editor'});
  window.closeEditor = () => {
    const id = current && wedding(current) ? current : route.id;
    return applyRoute(id ? {page:'wedding',id,tab:'overview'} : {page:'calendar'},{replace:true});
  };
  window.shiftMonth = amount => {
    calendarMonth = new Date(calendarMonth.getFullYear(),calendarMonth.getMonth()+amount,1,12);
    month = new Date(calendarMonth); selectedDay=iso(calendarMonth); drawCalendar(); saveHistory(true);
  };
  window.filterBookings = drawList;
  window.saveBooking = function(...args) {
    const result = raw.saveBooking.apply(this,args);
    if (result) { editorBaseline=formSnapshot(); scheduleRefresh(); }
    return result;
  };
  window.preview = function(...args) {
    const result = legacy(raw.preview,...args);
    if (current && wedding(current) && !el('printCard').classList.contains('hidden')) {
      applyRoute({page:'wedding',id:current,tab:'contract'},{force:true});
    }
    return result;
  };
  window.backToEdit = () => applyRoute({page:'editor',id:current});
  window.doc = function(...args) {
    documentReturn={...route}; const result=legacy(raw.doc,...args);
    applyRoute({page:'document',id:el('finWedding').value},{force:true}); return result;
  };
  window.closeFinanceDocument = () => {
    raw.closeFinanceDocument();
    applyRoute(documentReturn || {page:'finance'},{force:true,replace:true});
  };
  window.printReport = function(...args) {
    const result=raw.printReport.apply(this,args);
    if (!el('reportPrint').classList.contains('viewhidden')) applyRoute({page:'report-document'},{force:true});
    return result;
  };
  ['persist','finSave','render','drawFinance','refreshViews','financeRefresh','tdManualCrewRefresh'].forEach(name=>{
    const original=window[name]; if(typeof original!=='function')return;
    window[name]=function(...args){const value=original.apply(this,args);scheduleRefresh();return value;};
  });
  el('searchWedding').oninput=drawList; el('tdWeddingStatus').onchange=drawList;
  document.addEventListener('pointerdown',event=>{
    if(route.page==='wedding' && route.tab==='contract' && !wedding(route.id)?.locked && event.target.closest('#printCard canvas'))signatureDirty=true;
  },true);
  document.addEventListener('click',event=>{
    const target=event.target.closest('button'); if(!target)return;
    if(target.dataset.route){applyRoute({page:target.dataset.route});return;}
    if(target.dataset.wedding){openWedding(target.dataset.wedding);return;}
    if(target.dataset.day){selectedDay=target.dataset.day;drawCalendar();saveHistory(true);return;}
    if(target.dataset.weddingTab){applyRoute({...route,tab:target.dataset.weddingTab,financeTab:'client'},{replace:true});return;}
    if(target.dataset.financeTab){setFinanceTab(target.dataset.financeTab);return;}
    if(target.dataset.staffTab){setStaffTab(target.dataset.staffTab);return;}
    switch(target.dataset.action){
      case 'new': window.newBooking();break;
      case 'new-on-day': applyRoute({page:'editor',date:selectedDay});break;
      case 'edit-wedding': applyRoute({page:'editor',id:route.id});break;
      case 'previous-month':window.shiftMonth(-1);break;
      case 'next-month':window.shiftMonth(1);break;
      case 'today':calendarMonth=new Date(new Date().getFullYear(),new Date().getMonth(),1,12);month=new Date(calendarMonth);selectedDay=today();drawCalendar();saveHistory(true);break;
      case 'back':if(historyIndex>0)history.back();else applyRoute({page:'weddings'},{replace:true});break;
      case 'close-editor':window.closeEditor();break;
      case 'close-document':window.closeFinanceDocument();break;
      case 'brand':el('markSettingsBtn')?.click();break;
      case 'cloud':window.tdCloudSetup?.();break;
      case 'close-drawer':toggleDrawer(false);break;
    }
  });
  window.addEventListener('popstate',event=>{
    const target=event.state;
    if(!target?.tdBookingPagesV620)return;
    if(!mayLeave()){saveHistory(false);return;}
    historyIndex=Number(target.index)||0;
    if(target.day)selectedDay=target.day;
    if(/^\d{4}-\d{2}$/.test(target.month||''))calendarMonth=new Date(Number(target.month.slice(0,4)),Number(target.month.slice(5,7))-1,1,12);
    applyRoute(target,{force:true,fromHistory:true});
  });
  window.TDPages={openWedding,navigate:page=>applyRoute({page}),refresh:refreshDerived,route:()=>({...route}),localDate:iso};
  refreshDerived(); applyRoute({page:'calendar'},{force:true,replace:true});
})();
