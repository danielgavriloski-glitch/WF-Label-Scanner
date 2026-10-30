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

console.log('PASS: 6 contract displays, 7 signing cases, month rollover, event names, full sync, legacy-only sync, '+scripts+' inline scripts, JS modules and Calendar title.');
