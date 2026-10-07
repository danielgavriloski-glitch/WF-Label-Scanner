(function(root,factory){
  if(typeof module==='object'&&module.exports)module.exports=factory(require('./core.js'));
  else factory(root.Core);
})(typeof globalThis!=='undefined'?globalThis:this,function(Core){
'use strict';
if(!Core)throw new Error('Core is required');
if(Core.__wfagV12)return Core;
Core.__wfagV12=true;

const baseValidate=Core.validate;
const baseDay=Core.day;

Core.TYPES.holiday='Неработен ден – празник';

Core.validate=function(raw){
  baseValidate(raw);
  for(const t of raw.trips||[]){
    if(t.workStart!==undefined&&t.workStart!=='')Core.minutes(t.workStart);
  }
  return raw;
};

Core.putTrip=function(state,input){
  if(!input.id&&input.date<state.settings.trackStart)throw Error('Датумот е пред почетокот на евиденцијата.');
  const copy=JSON.parse(JSON.stringify(state));
  if(!copy.trips)copy.trips=[];
  const old=input.id?copy.trips.find(t=>t.id===input.id):null;
  const id=input.id||('trip_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,10));
  const sameDay=copy.trips.find(t=>t.date===input.date&&t.id!==id&&t.workStart);
  const workStart=input.workStart||old&&old.workStart||sameDay&&sameDay.workStart||state.settings.start||'08:00';
  Core.minutes(workStart);
  Core.minutes(input.departure);
  if(Core.timestamp(input.date,workStart)>Core.timestamp(input.date,input.departure))throw Error('Почетокот на работа не може да биде по поаѓањето на пат.');
  const trip={
    id:id,
    date:input.date,
    departure:input.departure,
    arrival:input.arrival||'',
    arrivalDate:input.arrival?input.arrivalDate:input.date,
    note:input.note||'',
    workStart:workStart
  };
  const index=copy.trips.findIndex(t=>t.id===id);
  if(index<0)copy.trips.push(trip);else copy.trips[index]=trip;
  copy.trips.forEach(t=>{if(t.date===trip.date)t.workStart=workStart;});
  Core.validate(copy);
  return {state:copy,trip:trip};
};

function tripActualEnd(t,nowMs){
  if(t.departureAt>nowMs)return null;
  if(t.arrivalAt===null)return nowMs;
  return Math.min(t.arrivalAt,nowMs);
}
function tripPlannedEnd(t,nowMs){
  if(t.arrivalAt!==null)return t.arrivalAt;
  if(t.departureAt<=nowMs)return nowMs;
  return null;
}
function shiftTimes(state,date,r){
  const rec=state.records&&state.records[date];
  const pause=rec&&Number.isInteger(rec.pause)?rec.pause:0;
  const pausePaid=rec&&Object.prototype.hasOwnProperty.call(rec,'pausePaid')?rec.pausePaid:state.settings.breaksIncluded;
  return {pause:pause,pausePaid:pausePaid};
}
function adjustedMinutes(startAt,endAt,pause,pausePaid){
  const span=Math.max(0,Math.floor((endAt-startAt)/60000));
  return Math.max(0,span-(pausePaid?0:Math.min(pause,span)));
}
function timeAt(ms){
  const p=Core.parts(new Date(ms));
  return p.hour+':'+p.minute;
}

Core.day=function(state,date,now=new Date()){
  const r=baseDay(state,date,now);
  if(!r.covered||!['work','holidayWork'].includes(r.type)||!r.trips.length)return r;

  const starts=r.trips.map(t=>t.workStart).filter(Boolean);
  const workStart=starts.length?starts[0]:r.start;
  Core.minutes(workStart);
  const startAt=Core.timestamp(date,workStart);
  const originalShiftEnd=Core.timestamp(r.overnight?Core.plus(date,1):date,r.end);
  const nowMs=now.getTime();

  let actualEnd=Math.min(originalShiftEnd,nowMs);
  let plannedEnd=originalShiftEnd;
  for(const t of r.trips){
    const a=tripActualEnd(t,nowMs);
    const p=tripPlannedEnd(t,nowMs);
    if(a!==null)actualEnd=Math.max(actualEnd,a);
    if(p!==null)plannedEnd=Math.max(plannedEnd,p);
  }
  actualEnd=Math.max(startAt,actualEnd);
  plannedEnd=Math.max(startAt,plannedEnd);

  const shift=shiftTimes(state,date,r);
  const worked=adjustedMinutes(startAt,actualEnd,shift.pause,shift.pausePaid);
  const planWork=adjustedMinutes(startAt,plannedEnd,shift.pause,shift.pausePaid);
  const displayEnd=plannedEnd>originalShiftEnd?plannedEnd:originalShiftEnd;
  const endParts=Core.parts(new Date(displayEnd));
  const overnight=endParts.date!==date;

  return Object.assign({},r,{
    start:workStart,
    end:timeAt(displayEnd),
    overnight:overnight,
    worked:worked,
    planWork:planWork,
    workStart:workStart,
    workEndAt:actualEnd
  });
};

Core.week=function(state,monday,now=new Date()){
  const rows=Core.dates(monday,Core.plus(monday,6)).map(d=>Core.day(state,d,now));
  const s=state.settings;
  const coveredDays=rows.filter(r=>r.covered&&s.workdays.includes(Core.weekday(r.date))).length;
  const nominalDays=s.workdays.length;
  const target=Math.round(s.weekMinutes*coveredDays/nominalDays);
  let normal=0;
  for(const r of rows){
    if(r.scheduled){
      r.regular=Math.min(r.worked,s.dayMinutes);
      r.overtime=Math.max(0,r.worked-s.dayMinutes);
      normal+=Math.min(s.dayMinutes,r.regular+r.credit);
    }
  }
  let gap=Math.max(0,target-normal);
  for(const r of rows){
    if(!r.scheduled){
      r.regular=Math.min(r.worked,gap);
      gap-=r.regular;
      r.overtime=r.worked-r.regular;
      normal+=r.regular;
    }
  }
  const grossOvertime=rows.reduce((a,r)=>a+r.overtime,0);
  const compDays=rows.filter(r=>r.covered&&!r.future&&!r.auto&&r.scheduled&&r.type==='off').length;
  const compTime=compDays*s.dayMinutes;
  const compApplied=Math.min(grossOvertime,compTime);
  return {
    rows:rows,
    target:target,
    normal:normal,
    missing:Math.max(0,target-normal),
    grossOvertime:grossOvertime,
    compDays:compDays,
    compTime:compTime,
    compApplied:compApplied,
    overtime:Math.max(0,grossOvertime-compTime),
    worked:rows.reduce((a,r)=>a+r.worked,0)
  };
};

Core.period=function(state,from,to,now=new Date()){
  const all=Core.dates(from,to),cache={};
  const rows=all.map(d=>{
    const m=Core.weekStart(d);
    if(!cache[m])cache[m]=Core.week(state,m,now);
    return cache[m].rows.find(r=>r.date===d);
  });
  const sum=k=>rows.reduce((a,r)=>a+(r[k]||0),0);
  const counts={};
  Object.keys(Core.TYPES).forEach(t=>counts[t]=rows.filter(r=>r.covered&&!r.future&&r.type===t&&(t!=='vacation'||r.scheduled)).length);
  const planned={};
  Object.keys(Core.TYPES).forEach(t=>planned[t]=rows.filter(r=>r.covered&&r.future&&r.type===t&&(t!=='vacation'||r.scheduled)).length);
  const closedWeeks=Object.entries(cache).filter(([m])=>m>=from&&Core.plus(m,6)<=to&&Core.plus(m,6)<Core.today(now));
  const grossOvertime=sum('overtime');
  const compDays=rows.filter(r=>r.covered&&!r.future&&!r.auto&&r.scheduled&&r.type==='off').length;
  const compTime=compDays*state.settings.dayMinutes;
  const compApplied=Math.min(grossOvertime,compTime);
  const travel=sum('travel');
  const worked=sum('worked');
  const workplace=rows.reduce((a,r)=>a+Math.max(0,r.worked-r.travel),0);
  const workDays=rows.filter(r=>r.covered&&!r.future&&r.worked>0).length;
  return {
    from:from,to:to,rows:rows,
    worked:worked,
    regular:sum('regular'),
    grossOvertime:grossOvertime,
    compDays:compDays,
    compTime:compTime,
    compApplied:compApplied,
    overtime:Math.max(0,grossOvertime-compTime),
    pause:sum('pause'),
    credit:sum('credit'),
    expected:sum('expectedElapsed'),
    monthPlan:sum('expected'),
    counts:counts,
    planned:planned,
    missing:closedWeeks.reduce((a,[m,w])=>a+w.missing,0),
    holidayWork:rows.filter(r=>r.type==='holidayWork').reduce((a,r)=>a+r.worked,0),
    travel:travel,
    workplace:workplace,
    workDays:workDays,
    tripRows:rows.flatMap(r=>r.trips)
  };
};

return Core;
});