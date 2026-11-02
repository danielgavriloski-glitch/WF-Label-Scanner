'use strict';
const assert=require('assert');
const Core=require('../app/src/main/assets/v12-core.js');
const now=new Date('2026-10-27T17:00:00Z'); // 18:00 Europe/Skopje

function fresh(){
  const state=Core.fresh(new Date('2026-08-01T12:00:00Z'));
  state.settings.trackStart='2026-08-01';
  return state;
}
function work(start,end){
  return {type:'work',start:start||'08:00',end:end||'16:00',
          pause:0,pausePaid:true,overnight:false,note:'зачуван работен ден'};
}
{
  const s=fresh();
  // 20 older +2h days and one +57m day are accumulated in August.
  let date='2026-08-01',count=0;
  while(count<20){
    if(s.settings.workdays.includes(Core.weekday(date))){
      s.records[date]=work('08:00','18:00');
      count++;
    }
    date=Core.plus(date,1);
  }
  while(!s.settings.workdays.includes(Core.weekday(date)))date=Core.plus(date,1);
  s.records[date]=work('08:00','16:57');
  assert.strictEqual(Core.period(s,'2026-08-01','2026-08-31',now).overtime,2457,
    'the original 40h57m must exist in older saved months');
  s.records['2026-10-27']=work('08:00','15:00');
  const saved=JSON.stringify(s);
  assert.strictEqual(Core.validate(JSON.parse(saved)).records['2026-10-27'].end,'15:00');
  const week=Core.week(s,'2026-10-26',now);
  assert.strictEqual(week.shortfall,60);
  assert.strictEqual(week.overtime,-60);
  const month=Core.period(s,'2026-10-01','2026-10-31',now);
  assert.strictEqual(month.shortfall,60,'monthly historical shortfall');
  assert.strictEqual(month.overtime,-60,'negative month balance, not clamped to zero');
  assert.strictEqual(Core.balance(s,now),2397,'40:57 minus 1:00 is +39:57');
  const breakdown=Core.overtimeBreakdown(s,'2026-10-01','2026-10-31',now);
  assert.strictEqual(breakdown.deductions.length,1);
  assert.strictEqual(breakdown.deductions[0].kind,'short');
  assert.strictEqual(breakdown.deductions[0].date,'2026-10-27');
  assert.strictEqual(breakdown.deductions[0].applied,60);
  assert.strictEqual(Core.hhSigned(Core.balance(s,now)),'+39:57');
  assert.strictEqual(JSON.stringify(s),saved,'retrospective math must never edit archived records');
}
{
  const s=fresh();
  s.records['2026-10-27']=work('08:00','15:00');
  const during=new Date('2026-10-27T13:00:00Z'); // 14:00 local: still on shift
  assert.strictEqual(Core.week(s,'2026-10-26',during).shortfall,0,
    'do not charge shortfall while a manually recorded shift is still in progress');
  assert.strictEqual(Core.week(s,'2026-10-26',now).shortfall,60);
  s.records['2026-10-27']=work('08:00','14:00');
  assert.strictEqual(Core.week(s,'2026-10-26',now).shortfall,120,
    'editing a historical day must recalculate without storing another deduction');
}
{
  const s=fresh();
  s.records['2026-10-19']={type:'holiday',pause:0,note:'празник'};
  s.records['2026-10-20']={type:'vacation',pause:0,note:'годишен'};
  s.records['2026-10-21']={type:'sick',pause:0,note:'боледување'};
  s.records['2026-10-22']={type:'paid',pause:0,note:'платено отсуство'};
  s.records['2026-10-23']={type:'off',pause:0,note:'искористен слободен ден'};
  s.records['2026-10-26']={type:'holidayWork',start:'08:00',end:'12:00',pause:0,pausePaid:true,overnight:false,note:'работа на празник'};
  const p=Core.period(s,'2026-10-19','2026-10-27',now);
  assert.strictEqual(p.shortfall,0,'leave and holidays have no hourly shortfall');
  assert.strictEqual(p.compTime,480,'only explicitly taken off day debits eight hours');
  assert.strictEqual(p.grossOvertime,240,'four hours of holiday work credit');
  assert.strictEqual(p.overtime,-240,'negative carry should be visible rather than silently erased');
}

{
  const s=fresh();
  const dateNow=new Date('2026-11-02T10:00:00Z');
  s.records['2026-10-26']=work('08:00','23:17'); // +7:17
  s.records['2026-10-27']=work('08:00','15:00'); // -1:00
  s.records['2026-10-28']={type:'off',pause:0,note:'слободен ден'}; // -8:00
  s.records['2026-10-29']=work('08:00','16:00');
  s.records['2026-10-30']=work('08:00','22:00'); // +6:00
  s.records['2026-10-31']=work('08:00','14:10'); // Saturday: +6:10
  const before=JSON.stringify(s);
  const w=Core.week(s,'2026-10-26',dateNow);
  const saturday=w.rows.find(r=>r.date==='2026-10-31');
  assert.strictEqual(saturday.worked,370,'October 31 recorded 6h10m');
  assert.strictEqual(saturday.regular,0,'Saturday must never fill the weekday 40h fund');
  assert.strictEqual(saturday.overtime,370,'Saturday must show +6:10');
  assert.strictEqual(w.overtime,627,'weekly net balance must be +10:27 after -1:00 and -8:00');
  const p=Core.period(s,'2026-10-26','2026-11-01',dateNow);
  assert.strictEqual(p.overtime,627,'week-spanning period must show the same +10:27');
  const b=Core.overtimeBreakdown(s,'2026-10-26','2026-11-01',dateNow);
  assert.ok(b.earned.some(r=>r.date==='2026-10-31'&&r.overtime===370));
  assert.strictEqual(JSON.stringify(s),before,'recalculating archived records must not rewrite stored data');
  s.records['2026-11-01']=work('08:00','10:30'); // Sunday: +2:30
  const sunday=Core.week(s,'2026-10-26',dateNow).rows[6];
  assert.strictEqual(sunday.overtime,150,'Sunday must be full overtime');
  assert.strictEqual(Core.week(s,'2026-10-26',dateNow).overtime,777,'Sunday adds +2:30 to existing +10:27');
}

console.log('WFAG v3.4 retrospective overtime tests OK');
