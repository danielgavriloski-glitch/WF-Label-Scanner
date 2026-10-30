'use strict';
// Run with: node td-booking-mark/admin/tests/event_contracts.test.cjs
// Self-contained regression tests for both legacy weddings and newer event categories.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const html = fs.readFileSync(path.join(__dirname, '../src/main/assets/index.html'), 'utf8');
const backup = fs.readFileSync(path.join(__dirname, '../src/main/assets/backup_export_v618.js'), 'utf8');
const kotlin = fs.readFileSync(path.join(__dirname, '../src/main/java/mk/td/booking/MainActivity.kt'), 'utf8');
function between(start, end) {
  const a = html.indexOf(start), b = html.indexOf(end, a + start.length);
  assert(a >= 0 && b > a, 'Missing function: ' + start);
  return html.slice(a, b);
}
const escapeHtml = x => String(x ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const typeLabels = {wedding:'Свадба',christening:'Крштевка',sunet:'Сунет',photoshoot:'Фотосесија',other:'Друг настан'};
const ids = {contractText:{innerHTML:''},client1Label:{textContent:''},client2Label:{textContent:''},client2Signature:{style:{display:''}},signaturePrint:{innerHTML:''}};
const $ = id => ids[id] || (ids[id] = {});
const showContract = new Function('$','escapeHtml','lines','eventName','eventTypeLabel','packageName','document',
  between('function showContract(b){','function preview(){') + ';return showContract;')(
  $, escapeHtml, x => escapeHtml(x).replace(/\n/g,'<br>'),
  b => b.eventType && b.eventType !== 'wedding' ? b.bride : b.bride + ' и ' + b.groom,
  t => typeLabels[t || 'wedding'], () => 'Посебен пакет',
  {querySelector: () => ({classList:{toggle(){}}})}
);
function booking(type) {
  return {id:'12345678-001', eventType:type === 'legacy' ? undefined : type,
    bride:'Ана',groom:'Петар',phone:'070000000',provider:'TD Production',
    providerSigner:'Изведувач',date:'2026-11-10',start:'11:00',
    package:'custom',services:'Фото',price:'200',deposit:'20',terms:'Услови',
    locked:true,signedAt:'2026-10-30T15:15:00.000Z',
    sigProvider:'p',sigClient1:'c1',sigClient2:'c2'};
}
for (const type of ['legacy','wedding','christening','sunet','photoshoot','other']) {
  const b=booking(type);
  showContract(b);
  const wedding = type === 'legacy' || type === 'wedding';
  assert.equal((ids.signaturePrint.innerHTML.match(/class="pdf-signer"/g)||[]).length, wedding ? 3 : 2, 'Signatures for '+type);
  assert.equal(ids.client2Signature.style.display, wedding ? '' : 'none', 'Second client field for '+type);
  if (!wedding) assert(!ids.signaturePrint.innerHTML.includes('Младоженец'), 'Unexpected groom for '+type);
}
const finalizeSource = between('function finalize(){', 'function printContract(){');
for (const [type, third, expectedLock] of [
  ['legacy', false, false], ['wedding', false, false], ['wedding', true, true],
  ['christening', false, true], ['sunet', false, true], ['photoshoot', false, true], ['other', false, true]
]) {
  const b=booking(type);b.locked=false; delete b.sigClient2;
  const canvas={sigProvider:{ink:true,toDataURL:()=> 'p'},
    sigClient1:{ink:true,toDataURL:()=> 'c1'},sigClient2:{ink:third,toDataURL:()=> 'c2'}};
  let writes=0;
  const finalize = new Function('data','current','$','hasInk','persist','render','showContract','alert','confirm',
    finalizeSource+';return finalize;')(
    [b],b.id,id=>canvas[id],c=>c.ink,()=>writes++,()=>{},()=>{},()=>{},()=>true
  );
  finalize();
  assert.equal(!!b.locked, expectedLock, 'Locking '+type+' (third='+third+')');
  assert.equal(writes, expectedLock ? 1 : 0, 'Persisting '+type);
  if (expectedLock && type !== 'wedding') assert.equal(b.sigClient2, undefined, 'Unneeded third signature '+type);
}
let scripts=0;
for(const match of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
  if (/\bsrc\s*=/.test(match[1])) continue;
  new Function(match[2]);
  scripts++;
}
assert(scripts >= 5, 'Expected main inline scripts');
new Function(backup);
assert(kotlin.includes('put(CalendarContract.Events.TITLE, "$eventLabel · $eventName")'), 'Android Calendar must use actual event type');

const allEvents = fs.readFileSync(path.join(__dirname, '../src/main/assets/booking_full_replica.js'), 'utf8');
const manualCrew = fs.readFileSync(path.join(__dirname, '../src/main/assets/td_manual_crew.js'), 'utf8');
const oldWeddingSync = fs.readFileSync(path.join(__dirname, '../src/main/assets/td_mark_ui_v612.js'), 'utf8');
for (const [name, code] of [['full sync',allEvents],['manual crew',manualCrew],['legacy wedding sync',oldWeddingSync]]) {
  assert.doesNotThrow(() => new Function(code), name + ' must have valid JavaScript');
}
const eventNameCode=between('function eventName(b){','function updateEventTypeFields(){');
const eventName=new Function(eventNameCode + ';return eventName;')();
assert.equal(eventName({bride:'Ана',groom:'Петар'}),'Ана и Петар','Legacy weddings remain compatible');
assert.equal(eventName({eventType:'photoshoot',bride:'Фото сесија'}),'Фото сесија','Other event name');
const shiftSource=between('function shiftMonth(n){','let selectedBookingDay=null;');
const shiftResult=new Function('render',
  "let month=new Date(2027,0,31),selectedBookingDay='2027-01-31';"+shiftSource+
  ";shiftMonth(1);return {year:month.getFullYear(),month:month.getMonth(),day:month.getDate(),selectedBookingDay};"
)(()=>{});
assert.deepEqual(shiftResult,{year:2027,month:1,day:1,selectedBookingDay:null},'Do not skip February when current date is January 31');
const fullSyncSnap=allEvents.slice(allEvents.indexOf('function snap(){'),allEvents.indexOf('function base(){'));
assert(fullSyncSnap.startsWith('function snap(){'),'Cannot locate full sync snapshot');
const snap=new Function('saved','BOOK_KEY','FIN_KEY','PKG_KEY','CACHE_KEY','ROSTER_KEY','PC',
  fullSyncSnap+';return snap;')(
  key=>key==='bookings'?[{id:'photo1',eventType:'photoshoot',bride:'Фото сесија'}]:[],
  'bookings','finance','settings','cache','roster',false);
assert.equal(snap().bookings.photo1.eventType,'photoshoot','Full sync must carry new event types without dropping fields');
assert(oldWeddingSync.includes("(!b.eventType||b.eventType==='wedding')"),'Legacy wedding-only sync should exclude other categories');


const syncOpsCode=allEvents.slice(allEvents.indexOf('function eq(a,b){'),allEvents.indexOf('function setStatus('));
assert(syncOpsCode.startsWith('function eq(a,b){'),'Cannot locate sync ops');
const makeOps=new Function('TYPE','PC',syncOpsCode+';return makeOps;')(
  ['bookings','finance','settings','roster'],false
);
const emptyReplica={version:2,records:{bookings:{},finance:{},settings:{},roster:{}}};
const phoneRecord={id:'camera1',eventType:'christening',bride:'Крштевка Марко',
  groom:'',date:'2026-12-13',phone:'070111222',locked:false,price:'250'};
const outbound=makeOps({bookings:{camera1:phoneRecord},finance:{},settings:{global:[]},roster:{global:{}}},emptyReplica);
const bookingOp=outbound.find(op=>op.kind==='bookings'&&op.id==='camera1');
assert(bookingOp,'Full sync must send a booking operation');
assert.equal(bookingOp.data.eventType,'christening','Full sync must preserve eventType in outgoing operations');
const receivedRecords={bookings:{camera1:{rev:1,data:bookingOp.data}}};
const mem={};
const storage={setItem:(key,value)=>mem[key]=value};
const applySource=allEvents.slice(allEvents.indexOf('function applyLocal('),allEvents.indexOf('async function transport('));
assert(applySource.startsWith('function applyLocal('),'Cannot locate sync applyLocal');
const applyLocal=new Function('TYPE','PC','eq','localStorage','BOOK_KEY','FIN_KEY','PKG_KEY','BASE_KEY',
  applySource+';return applyLocal;')(
  ['bookings','finance','settings','roster'],false,
  (a,b)=>JSON.stringify(a)===JSON.stringify(b),storage,'tdBookingsV1','tdBookingFinanceV1','tdPackageSettings','replicaBase'
);
const applied=applyLocal(receivedRecords,emptyReplica,
  {bookings:{},finance:{},settings:{global:[]},roster:{global:{}}},[],new Set());
assert(applied,'Incoming booking should apply');
const received=JSON.parse(mem.tdBookingsV1);
assert.equal(received.length,1,'One booking should be applied');
assert.equal(received[0].eventType,'christening','Event category survives phone-to-PC transfer');
assert.equal(received[0].bride,'Крштевка Марко','Event name survives transfer');

const home = fs.readFileSync(path.join(__dirname,'../src/main/assets/booking_home_v619.js'),'utf8');
assert.doesNotThrow(()=>new Function(home),'Calendar home enhancement must parse');
assert(html.includes('<script src="booking_home_v619.js"></script>'),'Calendar-first navigation module must load');
assert(home.includes("window.navigate('dashboard')"),'Calendar-first initial navigation must run');
assert(home.includes("view==='dashboard'?'weddings':view"),'Home button must navigate to the calendar');
assert(!html.includes("let clash=data.find(x=>x.date===b.date"),'Same-date events must not be blocked by repeated conflict confirmations');
assert(home.includes("booking-day-count"),'Calendar must display numeric per-day event counts');
assert(html.includes("items=data.filter(b=>b.date===selectedBookingDay)"),'Selected-day event list remains uncapped');

const financeTabs=fs.readFileSync(path.join(__dirname,'../src/main/assets/booking_finance_tabs_v619.js'),'utf8');
assert.doesNotThrow(()=>new Function(financeTabs),'Finance navigation should have valid JavaScript');
assert(html.includes('<script src="booking_finance_tabs_v619.js"></script>'),'Finance tab module must load');
for(const key of ['overview','client','extras','expenses','crew']){
 assert(financeTabs.includes("['"+key+"'"),'Finance must preserve '+key+' controls');
}
assert(!financeTabs.includes("localStorage.setItem"),'Finance navigation must not modify stored data');
const selectedDaySource=between('function renderSelectedBookingDay(){','function render(){');
const hundredTwenty=Array.from({length:120},(_,i)=>({
 id:'day-'+i,date:'2026-11-20',eventType:i%2?'wedding':'photoshoot',
 bride:'Клиент '+i,groom:'Партнер '+i,start:String(8+i%12).padStart(2,'0')+':00',package:'custom'
}));
const selectedHost={innerHTML:'',querySelectorAll:()=>[]};
const selectedDay=new Function('document','selectedBookingDay','data','escapeHtml','eventTypeLabel','eventName','packageName',
 selectedDaySource+';return renderSelectedBookingDay;')(
 {getElementById:()=>selectedHost},'2026-11-20',hundredTwenty,escapeHtml,
 t=>typeLabels[t||'wedding'],b=>b.eventType==='wedding'?b.bride+' и '+b.groom:b.bride,
 ()=> 'Посебен пакет'
);
selectedDay();
assert(selectedHost.innerHTML.includes('120 настани'),'120 events on a single date must be visible');
assert((selectedHost.innerHTML.match(/data-booking-id=/g)||[]).length===120,'Do not silently cap event lists at 100');
console.log('PASS: 6 contract displays, 7 signing cases, month rollover, full sync operation and apply round-trip, '+scripts+' inline scripts, JS modules and Calendar title.');
