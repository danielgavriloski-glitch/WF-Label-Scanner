'use strict';
const assert=require('assert');
const Core=require('../app/src/main/assets/v12-core.js');

function fresh(){
  const s=Core.fresh(new Date('2026-10-01T08:00:00Z'));
  s.settings.trackStart='2026-10-01';
  return s;
}
function trip(s,date,workStart,departure,arrival){
  return Core.putTrip(s,{date:date,workStart:workStart,departure:departure,arrivalDate:date,arrival:arrival,note:'службен пат'}).state;
}

{
  let s=fresh();
  s=trip(s,'2026-10-07','08:00','10:00','19:00');
  const d=Core.day(s,'2026-10-07',new Date('2026-10-08T12:00:00Z'));
  assert.strictEqual(d.worked,660,'08:00-19:00 must be 11 worked hours');
  const w=Core.week(s,'2026-10-05',new Date('2026-10-12T12:00:00Z'));
  const row=w.rows.find(x=>x.date==='2026-10-07');
  assert.strictEqual(row.overtime,180,'11 hour day must have 3 overtime hours');
}

{
  let s=fresh();
  s=trip(s,'2026-10-07','09:00','10:00','19:00');
  const d=Core.day(s,'2026-10-07',new Date('2026-10-08T12:00:00Z'));
  assert.strictEqual(d.worked,600,'custom 09:00 start must produce 10 worked hours');
}

{
  let s=fresh();
  s=trip(s,'2026-10-07','08:00','10:00','19:00');
  s=trip(s,'2026-10-08','08:00','10:00','22:00');
  s.records['2026-10-09']={type:'off',pause:0,note:'слободен ден од прекувремени'};
  Core.validate(s);
  const p=Core.period(s,'2026-10-05','2026-10-11',new Date('2026-10-12T12:00:00Z'));
  assert.strictEqual(p.grossOvertime,540,'gross overtime should be 9 hours');
  assert.strictEqual(p.compDays,1,'one explicit weekday off should be compensatory');
  assert.strictEqual(p.compTime,480,'free day should deduct 8 hours');
  assert.strictEqual(p.overtime,60,'net overtime should remain 1 hour');
}

{
  let s=fresh();
  s.records['2026-10-12']={type:'holiday',pause:0,note:'празник'};
  const p=Core.period(s,'2026-10-12','2026-10-12',new Date('2026-10-13T12:00:00Z'));
  assert.strictEqual(p.counts.holiday,1,'holiday must be reported');
  assert.strictEqual(p.worked,0,'holiday must not add physical worked hours');
}

{
  let s=fresh();
  assert.throws(()=>Core.putTrip(s,{date:'2026-10-07',workStart:'11:00',departure:'10:00',arrivalDate:'2026-10-07',arrival:'19:00',note:''}),/Почетокот на работа/);
}

console.log('WFAG v1.2 calculations OK');