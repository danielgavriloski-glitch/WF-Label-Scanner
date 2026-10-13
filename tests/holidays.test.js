'use strict';
const assert=require('assert');
const Base=require('../app/src/main/assets/core.js');
const now=new Date('2026-10-19T12:00:00Z');
function fresh(){
  const s=Base.fresh(new Date('2026-10-01T08:00:00Z'));
  s.settings.trackStart='2026-10-01';
  return s;
}
function record(start='08:00',end='16:00',pause=0,pausePaid=true,overnight=false){
  return {type:'holidayWork',start,end,pause,pausePaid,overnight,note:'Работа за празник'};
}
let checks=0;
function check(name,fn){fn();checks++;console.log('OK '+name);}
function holidayCases(Core,label){
  check(label+': eight holiday hours are all overtime and keep the weekly fund',()=>{
    const s=fresh();s.records['2026-10-12']=record();
    const before=JSON.stringify(s),w=Core.week(s,'2026-10-12',now);
    const r=w.rows[0];
    assert.equal(r.worked,480);assert.equal(r.regular,0);assert.equal(r.overtime,480);
    assert.equal(w.normal,2400);assert.equal(w.missing,0);assert.equal(w.overtime,480);
    assert.equal(w.holidayWork,480);
    assert.equal(JSON.stringify(s),before,'Recalculation must not change saved records');
  });
  check(label+': short and long holiday shifts have no eight-hour deduction',()=>{
    for(const [end,expected] of [['12:00',240],['20:00',720]]){
      const s=fresh();s.records['2026-10-12']=record('08:00',end);
      const p=Core.period(s,'2026-10-12','2026-10-12',now);
      assert.equal(p.holidayWork,expected);assert.equal(p.overtime,expected);
      assert.equal(p.regular,0);assert.equal(p.counts.holidayWork,1);
    }
  });
  check(label+': a weekend holiday cannot fill a missing regular weekday',()=>{
    const s=fresh();s.records['2026-10-16']={type:'absent',pause:0,note:'отсуство'};
    s.records['2026-10-17']=record();
    const w=Core.week(s,'2026-10-12',now),r=w.rows[5];
    assert.equal(r.regular,0);assert.equal(r.overtime,480);
    assert.equal(w.missing,480);assert.equal(w.overtime,480);
  });
  check(label+': holiday hours do not create an artificial weekend shortfall',()=>{
    const s=fresh();s.records['2026-10-12']=record();
    s.records['2026-10-17']={...record('08:00','12:00'),type:'work'};
    const w=Core.week(s,'2026-10-12',now);
    assert.equal(w.rows[5].regular,0);assert.equal(w.rows[5].overtime,240);
    assert.equal(w.overtime,720);assert.equal(w.missing,0);
  });
  check(label+': pauses and midnight shifts count actual holiday work',()=>{
    const s=fresh();s.records['2026-10-12']=record('22:00','06:00',30,false,true);
    const p=Core.period(s,'2026-10-12','2026-10-12',now);
    assert.equal(p.worked,450);assert.equal(p.overtime,450);assert.equal(p.holidayWork,450);
  });
  check(label+': future holiday plans do not earn overtime early',()=>{
    const s=fresh();s.records['2026-10-20']=record();
    const p=Core.period(s,'2026-10-20','2026-10-20',now);
    assert.equal(p.overtime,0);assert.equal(p.holidayWork,0);
    assert.equal(p.counts.holidayWork,0);assert.equal(p.planned.holidayWork,1);
    assert.equal(p.rows[0].planWork,480);
  });
  check(label+': a nonworked holiday stays separate from holiday work',()=>{
    const s=fresh();s.records['2026-10-12']={type:'holiday',pause:0,note:'Празник'};
    const w=Core.week(s,'2026-10-12',now);
    assert.equal(w.worked,1920);assert.equal(w.overtime,0);assert.equal(w.missing,0);
  });
}
holidayCases(Base,'Base');
const Core=require('../app/src/main/assets/v12-core.js');
holidayCases(Core,'WFAG');
check('Travel on a holiday extends holiday overtime without double counting',()=>{
  let s=fresh();s.records['2026-10-12']=record();
  s=Core.putTrip(s,{date:'2026-10-12',workStart:'08:00',departure:'10:00',arrivalDate:'2026-10-12',arrival:'19:00',note:'Охрид – Скопје'}).state;
  const p=Core.period(s,'2026-10-12','2026-10-12',now);
  assert.equal(p.worked,660);assert.equal(p.travel,540);
  assert.equal(p.overtime,660);assert.equal(p.holidayWork,660);
  assert.equal(p.regular,0);assert.equal(p.workplace,120);
});
check('Holiday appears in the overtime report and existing free-day deductions still apply',()=>{
  const s=fresh();s.records['2026-10-12']=record('08:00','20:00');
  s.records['2026-10-13']={type:'off',pause:0,note:'Искористени прекувремени'};
  const b=Core.overtimeBreakdown(s,'2026-10-12','2026-10-18',now);
  assert.equal(b.gross,720);assert.equal(b.compApplied,480);assert.equal(b.net,240);
  assert.equal(b.earned.length,1);assert.equal(b.earned[0].date,'2026-10-12');
  assert.match(b.earned[0].reason,/Работа на празник \(неработен ден\): сите 12:00/);
});
check('Holiday and other overtime are separate categories of one total',()=>{
  const s=fresh();s.records['2026-10-12']=record();
  s.records['2026-10-13']={...record('08:00','18:00'),type:'work'};
  s.records['2026-10-17']={...record('08:00','12:00'),type:'work'};
  const b=Core.overtimeBreakdown(s,'2026-10-12','2026-10-18',now);
  assert.equal(b.holiday,480);assert.equal(b.other,360);assert.equal(b.gross,840);
  assert.equal(b.holiday+b.other,b.gross);assert.equal(b.net,840);
});
check('Old backups retain records, trips, leave balances, settings and audit',()=>{
  let s=fresh();s.settings.name='Даниел';s.records['2026-10-12']=record();
  s.budgets['2026']={annual:20,carry:3,previous:1};
  s.audit.push({at:'2026-10-12T16:00:00Z',text:'Внесен празник'});
  s=Core.putTrip(s,{date:'2026-10-14',workStart:'08:00',departure:'10:00',arrivalDate:'2026-10-14',arrival:'18:00',note:'Скопје'}).state;
  const saved=JSON.stringify(s),loaded=Core.validate(JSON.parse(saved));
  Core.period(loaded,'2026-10-01','2026-10-31',now);
  assert.equal(JSON.stringify(loaded),saved);
  assert.equal(Core.APP_ID,'workfashion-personal-time');assert.equal(loaded.schemaVersion,1);
});
console.log(checks+' holiday and upgrade calculations passed');
