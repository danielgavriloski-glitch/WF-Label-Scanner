'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const Core=require('../app/src/main/assets/v12-core.js');
const afterMonth=new Date('2026-12-01T18:00:00Z');

function fresh(){
  const state=Core.fresh(new Date('2026-11-01T12:00:00Z'));
  state.settings.trackStart='2026-11-01';
  return state;
}
function work(start='08:00',end='16:00',extra={}){
  return {type:'work',start,end,pause:0,pausePaid:true,overnight:false,note:'',...extra};
}
function trip(state,date,departure,arrival,extra={}){
  return Core.putTrip(state,{date,departure,arrival,arrivalDate:date,note:'',...extra});
}

test('Default schedule remains 08:00–16:00 and today only counts elapsed minutes',()=>{
  const state=fresh();
  assert.equal(Core.defaultWorkStart(state,'2026-11-02'),'08:00');
  assert.equal(Core.day(state,'2026-11-02',afterMonth).worked,480);
  assert.equal(Core.day(state,'2026-11-02',new Date('2026-11-02T09:31:00Z')).worked,151);
});

test('Explicit daily start wins over an old trip start and defaults the next trip',()=>{
  let state=trip(fresh(),'2026-11-02','10:30','18:15',{workStart:'08:00'}).state;
  state.records['2026-11-02']=work('09:30','16:00');
  const before=JSON.stringify(state),row=Core.day(state,'2026-11-02',afterMonth);
  assert.equal(row.start,'09:30');
  assert.equal(row.worked,525);
  assert.equal(row.travel,465);
  assert.equal(row.trips[0].workStart,'09:30');
  assert.equal(Core.defaultWorkStart(state,'2026-11-02'),'09:30');
  assert.equal(JSON.stringify(state),before,'reading must not rewrite saved history');
  const next=trip(state,'2026-11-02','18:30','19:00');
  assert.equal(next.trip.workStart,'09:30');
  assert.equal(Core.day(next.state,'2026-11-02',afterMonth).worked,570);
});

test('Arrival before 16:00 ends the day exactly and produces the correct shortfall',()=>{
  const state=trip(fresh(),'2026-11-02','10:00','14:00').state;
  const period=Core.period(state,'2026-11-02','2026-11-02',afterMonth),row=period.rows[0];
  assert.equal(row.end,'14:00');
  assert.equal(period.worked,360);
  assert.equal(period.travel,240);
  assert.equal(period.workplace,120);
  assert.equal(period.shortfall,120);
  assert.equal(period.overtime,-120);
  assert.equal(period.totalsComplete,true);
});

test('Late arrival counts one continuous day with exact minutes, not shift plus trip',()=>{
  const state=trip(fresh(),'2026-11-03','10:00','23:31').state;
  const period=Core.period(state,'2026-11-03','2026-11-03',afterMonth);
  assert.equal(period.worked,931);
  assert.equal(period.travel,811);
  assert.equal(period.workplace,120);
  assert.equal(period.overtime,451);
});

test('Multiple trips use the last arrival without resetting the original start',()=>{
  let state=trip(fresh(),'2026-11-04','10:00','12:00',{workStart:'08:00'}).state;
  state=trip(state,'2026-11-04','13:00','15:30',{workStart:'11:00'}).state;
  const period=Core.period(state,'2026-11-04','2026-11-04',afterMonth);
  assert.equal(period.rows[0].start,'08:00');
  assert.equal(period.rows[0].end,'15:30');
  assert.equal(period.worked,450);
  assert.equal(period.travel,270);
  assert.equal(period.workplace,180);
  assert.equal(period.shortfall,30);
});

test('Overnight final arrival uses its actual date and is included once across months',()=>{
  let state=fresh();
  state.records['2026-11-30']=work('22:00','06:00',{overnight:true});
  state=trip(state,'2026-11-30','23:00','05:15',{arrivalDate:'2026-12-01'}).state;
  const row=Core.day(state,'2026-11-30',afterMonth);
  assert.equal(row.worked,435);
  assert.equal(row.travel,375);
  assert.equal(row.end,'05:15');
  assert.equal(row.overnight,true);
  assert.equal(Core.period(state,'2026-11-30','2026-11-30',afterMonth).worked,435);
  assert.equal(Core.period(state,'2026-12-01','2026-12-01',afterMonth).travel,0);
});

test('Open trip counts current elapsed time but becomes an unresolved total after midnight',()=>{
  const result=trip(fresh(),'2026-11-02','10:00','');
  const during=new Date('2026-11-02T12:00:00Z');
  const current=Core.week(result.state,'2026-11-02',during).rows[0];
  assert.equal(current.worked,300);
  assert.equal(current.workInProgress,true);
  assert.equal(current.shortfall,0,'an active shift cannot create an early-end debit');
  assert.equal(current.end,'');
  assert.equal(current.planWork,null);
  const later=new Date('2026-11-03T13:00:00Z'); // The old bug reported 30 hours here.
  const unresolved=Core.period(result.state,'2026-11-02','2026-11-02',later);
  assert.equal(unresolved.rows[0].worked,null);
  assert.equal(unresolved.rows[0].workEndAt,null);
  assert.equal(unresolved.rows[0].trips[0].travel,null);
  assert.equal(unresolved.worked,0,'unknown day is excluded from the known subtotal');
  assert.equal(unresolved.shortfall,0,'missing arrival cannot invent a missing-work debit');
  assert.equal(unresolved.overtime,0);
  assert.equal(unresolved.totalsComplete,false);
  assert.deepEqual(unresolved.reviewDates,['2026-11-02']);
  assert.deepEqual(Core.reviewDates(result.state,afterMonth),['2026-11-02'],'all-time warning includes an unresolved previous month');
  assert.equal(Core.hh(unresolved.rows[0].worked),'—');
  assert.equal(Core.hhSigned(unresolved.rows[0].overtimeNet),'—');
  const resolved=Core.putTrip(result.state,{...result.trip,arrival:'14:00'}).state;
  const fixed=Core.period(resolved,'2026-11-02','2026-11-02',later);
  assert.equal(fixed.worked,360);
  assert.equal(fixed.overtime,-120);
  assert.equal(fixed.totalsComplete,true);
  assert.deepEqual(fixed.reviewDates,[]);
  assert.deepEqual(Core.reviewDates(resolved,later),[]);
});

test('Legacy start/departure conflicts stay recoverable and new conflicts are rejected',()=>{
  let state=trip(fresh(),'2026-11-02','10:00','14:00').state;
  const original=JSON.stringify(state);
  assert.throws(()=>Core.putRange(state,'2026-11-02','2026-11-02',work('11:00','16:00')),/Почетокот на работа/);
  assert.equal(JSON.stringify(state),original);
  state.records['2026-11-02']=work('11:00','16:00');
  const legacy=Core.validate(JSON.parse(JSON.stringify(state)));
  const row=Core.day(legacy,'2026-11-02',afterMonth);
  assert.equal(row.workStartConflict,true);
  assert.equal(row.worked,null);
  assert.equal(row.totalsComplete,false);
  assert.deepEqual(Core.reviewDates(legacy,afterMonth),['2026-11-02']);
  assert.throws(()=>Core.putTrip(legacy,{...legacy.trips[0],arrival:'15:00'}),/Почетокот на работа/);
});

test('Unpaid break is deducted once from the authoritative start-to-arrival duration',()=>{
  let state=fresh();
  state.records['2026-11-02']=work('09:15','16:00',{pause:20,pausePaid:false});
  state=trip(state,'2026-11-02','10:00','14:40',{workStart:'08:00'}).state;
  assert.equal(Core.day(state,'2026-11-02',afterMonth).worked,305);
});

test('Monthly totals reconcile early/late trips and weekend work while retaining leave',()=>{
  let state=fresh();
  state.budgets['2026']={annual:20,carry:2,previous:1};
  state.records['2026-11-10']={type:'vacation',pause:0,note:'одмор'};
  state.records['2026-11-11']={type:'sick',pause:0,note:'боледување'};
  const before=Core.period(state,'2026-11-01','2026-11-30',afterMonth);
  const leaveBefore=Core.leave(state,2026,afterMonth);
  state=trip(state,'2026-11-04','10:00','14:00').state; // -120 min
  state=trip(state,'2026-11-05','10:00','18:15').state; // +135 min
  state.records['2026-11-06']=work('09:00','16:00');
  state=trip(state,'2026-11-06','10:00','15:00',{workStart:'08:00'}).state; // -120 min
  state.records['2026-11-07']=work('10:00','12:30'); // Saturday +150 min
  const saved=JSON.stringify(state),period=Core.period(state,'2026-11-01','2026-11-30',afterMonth);
  assert.equal(period.worked,before.worked+45);
  assert.equal(period.grossOvertime,285);
  assert.equal(period.shortfall,240);
  assert.equal(period.overtime,45);
  assert.equal(Core.balance(state,afterMonth),45);
  assert.equal(period.credit,960);
  assert.equal(period.counts.vacation,1);
  assert.equal(period.counts.sick,1);
  assert.deepEqual(Core.leave(state,2026,afterMonth),leaveBefore);
  assert.equal(JSON.stringify(state),saved);
});

test('A trip-only weekend counts the continuous workday, while explicit off and leave still win',()=>{
  const state=trip(fresh(),'2026-11-21','10:00','18:00',{workStart:'08:00'}).state;
  const saved=JSON.stringify(state),period=Core.period(state,'2026-11-21','2026-11-21',afterMonth);
  assert.equal(period.rows[0].type,'work');
  assert.equal(period.rows[0].auto,true);
  assert.equal(period.worked,600);
  assert.equal(period.travel,480);
  assert.equal(period.workplace,120);
  assert.equal(period.regular,0);
  assert.equal(period.overtime,600,'all Saturday work is overtime');
  assert.equal(JSON.stringify(state),saved,'automatic calculation must not create a manual daily record');
  for(const type of ['off','vacation','sick','holiday']){
    const explicit=JSON.parse(saved);
    explicit.records['2026-11-21']={type,pause:0,note:'explicit selection'};
    const result=Core.period(explicit,'2026-11-21','2026-11-21',afterMonth);
    assert.equal(result.rows[0].type,type);
    assert.equal(result.worked,0);
    assert.equal(result.overtime,0);
  }
});
