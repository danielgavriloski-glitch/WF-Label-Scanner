const assert=require('node:assert/strict');
const C=require('../app/src/main/assets/core.js');
let count=0;
const now=new Date('2026-11-01T12:00:00Z');
function test(name,fn){fn();count++;console.log('OK '+name);}
function base(){const s=C.fresh(now);s.settings.trackStart='2026-01-01';return s;}
function add(s,date='2026-10-05',departure='06:30',arrival='07:45',arrivalDate=date,note='Охрид – Скопје'){return C.putTrip(s,{date,departure,arrivalDate,arrival,note});}
test('Version 1 backup migrates without changing existing records, budgets or settings',()=>{
 const s=base();delete s.trips;s.settings.name='Даниел';s.budgets['2026']={annual:20,carry:2,previous:1};s.records['2026-10-06']={type:'sick',pause:0,note:'Постоен запис'};
 const before=JSON.parse(JSON.stringify(s));const migrated=C.validate(s);assert.deepEqual(migrated.trips,[]);delete migrated.trips;assert.deepEqual(migrated,before);
});
test('Departure remains pending and counts only elapsed real minutes',()=>{
 const {state,trip}=add(base(),'2026-10-05','06:30','');const t=C.tripView(trip,new Date('2026-10-05T05:15:30Z'));assert.equal(t.pending,true);assert.equal(t.travel,45);assert.equal(C.day(state,'2026-10-05',new Date('2026-10-05T05:15:30Z')).travel,45);
});
test('Arrival updates the same record and survives a JSON backup round trip',()=>{
 const a=add(base(),'2026-10-05','06:30','');const b=C.putTrip(a.state,{...a.trip,arrival:'07:45'});assert.equal(b.state.trips.length,1);assert.equal(b.trip.id,a.trip.id);assert.equal(C.tripView(b.trip,now).travel,75);assert.equal(a.state.trips[0].arrival,'');assert.deepEqual(C.validate(JSON.parse(JSON.stringify(b.state))),b.state);
});
test('Multiple trips in one day total correctly without affecting work or leave',()=>{
 const s=base();s.budgets['2026']={annual:20,carry:0,previous:0};s.records['2026-10-06']={type:'vacation',pause:0,note:'Одмор'};const old=C.period(s,'2026-10-01','2026-10-31',now),leave=C.leave(s,2026,now);
 const a=add(s).state,b=add(a,'2026-10-05','16:30','18:00').state,p=C.period(b,'2026-10-01','2026-10-31',now);assert.equal(p.travel,165);assert.equal(p.tripRows.length,2);for(const k of ['worked','credit','regular','overtime','pause','monthPlan','missing'])assert.equal(p[k],old[k]);assert.deepEqual(C.leave(b,2026,now),leave);assert.deepEqual(b.records,s.records);
});
test('Overnight travel is assigned to departure date and selected periods include it once',()=>{
 const {state,trip}=add(base(),'2026-10-03','22:30','01:15','2026-10-04');assert.equal(C.tripView(trip,now).travel,165);assert.equal(C.period(state,'2026-10-03','2026-10-03',now).travel,165);assert.equal(C.period(state,'2026-10-04','2026-10-04',now).travel,0);
});
test('Daylight saving changes count actual elapsed time in Europe/Skopje',()=>{
 const spring=add(base(),'2026-03-28','23:00','07:00','2026-03-29');assert.equal(C.tripView(spring.trip,now).travel,420);
 const autumn=add(base(),'2026-10-24','23:00','07:00','2026-10-25');assert.equal(C.tripView(autumn.trip,now).travel,540);
});
test('Arrival before departure is rejected with the original state intact',()=>{
 const s=base();assert.throws(()=>add(s,'2026-10-05','22:30','01:15'),/пред поаѓањето/);assert.equal(s.trips.length,0);
});
test('Overlapping or simultaneous pending trips are rejected; adjacent trips are allowed',()=>{
 let s=add(base()).state;assert.throws(()=>add(s,'2026-10-05','07:00','08:00'),/преклопува/);s=add(s,'2026-10-05','07:45','08:00').state;assert.equal(s.trips.length,2);
 s=add(base(),'2026-10-05','06:30','').state;assert.throws(()=>add(s,'2026-10-06','08:00','09:00'),/преклопува/);
});
test('Future journeys count zero actual minutes; partial journeys stop at now',()=>{
 const {trip}=add(base(),'2026-10-05','06:30','07:45');const before=C.tripView(trip,new Date('2026-10-05T03:00:00Z'));assert.equal(before.travel,0);assert.equal(before.plannedMinutes,75);assert.equal(before.planned,true);assert.equal(C.tripView(trip,new Date('2026-10-05T05:00:00Z')).travel,30);
});
test('Invalid dates, times, duplicate IDs and oversized notes are rejected',()=>{
 const {state,trip}=add(base());for(const patch of [t=>t.date='2026-02-30',t=>t.departure='25:00',t=>t.arrival='abc',t=>t.arrivalDate='bad',t=>t.note='x'.repeat(501),t=>t.id='<script>']){const s=JSON.parse(JSON.stringify(state));patch(s.trips[0]);assert.throws(()=>C.validate(s));}
 state.trips.push({...trip});assert.throws(()=>C.validate(state),/дупликат/);
});
test('Deleting a trip preserves other entries and existing records',()=>{
 const s=base();s.records['2026-10-06']={type:'sick',pause:0,note:'Тест'};const a=add(s),b=add(a.state,'2026-10-05','16:00','17:00'),z=C.removeTrip(b.state,a.trip.id);assert.equal(z.trips.length,1);assert.equal(z.trips[0].id,b.trip.id);assert.deepEqual(z.records,s.records);assert.equal(b.state.trips.length,2);
});
test('Changing tracking start retains older journeys and reveals them again',()=>{
 const {state}=add(base());state.settings.trackStart='2026-10-06';C.validate(state);assert.equal(state.trips.length,1);assert.equal(C.period(state,'2026-10-01','2026-10-31',now).travel,0);state.settings.trackStart='2026-10-01';assert.equal(C.period(state,'2026-10-01','2026-10-31',now).travel,75);assert.throws(()=>add(state,'2026-09-30'),/почетокот/);
});
test('XLSX includes all 17 columns in its filter and preserves Cyrillic text',()=>{
 const X=require('../app/src/main/assets/xlsx.js');const data=X.create([Array.from({length:17},(_,i)=>'Колона '+i),['Тест',...Array(12).fill(''),'06:00','07:00',1,'Охрид & Скопје']]);
 const body=Buffer.from(data).toString('utf8');assert.ok(body.includes('autoFilter ref="A1:Q2"'));assert.ok(body.includes('Охрид &amp; Скопје'));assert.ok(body.includes('min="17" max="17"'));assert.ok(body.includes('r="P2" s="2"><v>1</v>'));
});
console.log(count+' travel checks passed');
