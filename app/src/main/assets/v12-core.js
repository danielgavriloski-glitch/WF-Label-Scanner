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
const basePutRange=Core.putRange;
const isWork=record=>record&&['work','holidayWork'].includes(record.type);

function firstTripStart(trips){
  let first=null;
  for(const trip of trips){
    if(trip.workStart&&(!first||trip.departure<first.departure))first=trip;
  }
  return first&&first.workStart;
}
Core.defaultWorkStart=function(state,date){
  const record=state.records&&state.records[date];
  return isWork(record)?record.start:firstTripStart((state.trips||[]).filter(t=>t.date===date))||state.settings.start||'08:00';
};

// Review status for the all-time balance without recalculating every past day.
Core.reviewDates=function(state,now=new Date()){
  const current=Core.today(now),days=new Map();
  for(const trip of state.trips||[]){
    if(trip.date<state.settings.trackStart||trip.date>current)continue;
    let day=days.get(trip.date);
    if(!day){day={first:null,departure:trip.departure,pending:false};days.set(trip.date,day);}
    if(trip.departure<day.departure)day.departure=trip.departure;
    if(trip.workStart&&(!day.first||trip.departure<day.first.departure))day.first=trip;
    if(!trip.arrival&&trip.date<current)day.pending=true;
  }
  const review=[];
  for(const [date,day] of days){
    const record=state.records&&state.records[date];
    const works=record?isWork(record):true; // Every grouped automatic day has a work trip.
    const start=isWork(record)?record.start:day.first&&day.first.workStart||state.settings.start;
    if(day.pending||(works&&day.departure<start))review.push(date);
  }
  return review.sort();
};

function checkStartBeforeTrips(state,date,start){
  if((state.trips||[]).some(t=>t.date===date&&Core.minutes(t.departure)<Core.minutes(start))){
    throw Error('Почетокот на работа не може да биде по поаѓањето на пат ('+Core.displayDate(date)+'). Исправи го почетокот или поаѓањето.');
  }
}

Core.putRange=function(state,from,to,record,weekdaysOnly=true){
  const result=basePutRange(state,from,to,record,weekdaysOnly);
  if(isWork(record)){
    for(const date of Core.dates(from,to)){
      if(!weekdaysOnly||state.settings.workdays.includes(Core.weekday(date)))checkStartBeforeTrips(result.state,date,record.start);
    }
  }
  return result;
};

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
  const record=state.records&&state.records[input.date];
  const sameDay=copy.trips.filter(t=>t.date===input.date&&t.id!==id);
  // A new trip must not reset a day that has already begun. A day edit is the
  // authoritative start; otherwise preserve the first saved start for that day.
  const workStart=isWork(record)?record.start:firstTripStart(sameDay)||input.workStart||
    (old&&old.date===input.date&&old.workStart)||state.settings.start||'08:00';
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
  checkStartBeforeTrips(copy,trip.date,workStart);
  Core.validate(copy);
  return {state:copy,trip:trip};
};

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
  if(!r.covered||!r.trips.length)return r;
  // A trip explicitly records work on an otherwise automatic weekend/off day.
  // A saved leave/off decision still wins and must never be silently changed.
  if(r.auto&&r.type==='off'){
    r.type='work';r.start=state.settings.start;r.end=state.settings.end;
  }
  if(!['work','holidayWork'].includes(r.type))return r;

  const record=state.records&&state.records[date];
  const workStart=isWork(record)?record.start:firstTripStart(r.trips)||r.start;
  Core.minutes(workStart);
  const startAt=Core.timestamp(date,workStart);
  const nowMs=now.getTime();
  const workStartConflict=r.trips.some(t=>t.departureAt<startAt);
  const trips=r.trips.map(t=>Object.assign({},t,{workStart:workStart}));
  if(r.pendingTripReview||workStartConflict){
    return Object.assign({},r,{
      start:workStart,end:'',overnight:false,worked:null,planWork:null,
      workStart:workStart,workEndAt:null,workStartConflict:workStartConflict,
      pendingTripReview:true,totalsComplete:false,trips:trips,
      reviewReason:workStartConflict?'Почетокот на работа е по поаѓањето. Исправи ги времињата.':'Недостасува пристигнување. Внеси го вистинскиот датум и час.'
    });
  }

  // Trips describe parts of one continuous workday. The last entered arrival is
  // its actual end, including early finishes; never add a trip to the shift.
  const last=trips[trips.length-1];
  const plannedEnd=last.arrivalAt;
  const actualEnd=Math.max(startAt,Math.min(plannedEnd===null?nowMs:plannedEnd,nowMs));
  const shift=shiftTimes(state,date,r);
  const worked=adjustedMinutes(startAt,actualEnd,shift.pause,shift.pausePaid);
  const planWork=plannedEnd===null?null:adjustedMinutes(startAt,plannedEnd,shift.pause,shift.pausePaid);
  const overnight=plannedEnd!==null&&Core.parts(new Date(plannedEnd)).date!==date;

  return Object.assign({},r,{
    start:workStart,
    end:plannedEnd===null?'':timeAt(plannedEnd),
    overnight:overnight,
    worked:worked,
    planWork:planWork,
    workStart:workStart,
    workEndAt:actualEnd,
    workInProgress:plannedEnd===null||plannedEnd>nowMs,
    workStartConflict:false,
    trips:trips
  });
};

// Signed overtime is recalculated from original day records on every view.
// No migration and no persisted adjustment: old and new days use the same rules.
Core.hhSigned=function(value){
  if(value==null||!Number.isFinite(Number(value)))return '—';
  return (value<0?'−':value>0?'+':'')+Core.hh(Math.abs(value));
};

Core.week=function(state,monday,now=new Date()){
  const rows=Core.dates(monday,Core.plus(monday,6)).map(d=>Core.day(state,d,now));
  const s=state.settings;
  const coveredDays=rows.filter(r=>r.covered&&s.workdays.includes(Core.weekday(r.date))).length;
  const nominalDays=s.workdays.length;
  const target=Math.round(s.weekMinutes*coveredDays/nominalDays);
  let normal=0;
  for(const r of rows){
    if(r.worked===null){
      r.regular=null;r.overtime=null;
      if(r.type==='holidayWork'&&r.scheduled)normal+=Math.min(s.dayMinutes,r.credit);
      continue;
    }
    if(r.type==='holidayWork'){
      r.overtime=r.worked;
      if(r.scheduled)normal+=Math.min(s.dayMinutes,r.credit);
    }else if(r.scheduled){
      r.regular=Math.min(r.worked,s.dayMinutes);
      r.overtime=Math.max(0,r.worked-s.dayMinutes);
      normal+=Math.min(s.dayMinutes,r.regular+r.credit);
    }
  }
  // Non-scheduled work, including Saturdays and Sundays, is always overtime.
  // Missing weekday hours remain a separate negative balance.
  for(const r of rows){
    if(!r.scheduled&&r.type!=='holidayWork'){
      r.regular=r.worked===null?null:0;
      r.overtime=r.worked;
    }
  }
  const currentDate=Core.today(now);
  for(const r of rows){
    // An unedited default day is a full 8h day, not evidence of an absence.
    // A shift in progress must not be debited before it has actually finished.
    const shiftEnded=!r.workInProgress&&!r.pendingTripReview&&(r.date<currentDate ||
      (r.date===currentDate&&r.type==='work'&&
        Core.timestamp(r.overnight?Core.plus(r.date,1):r.date,r.end)<=now.getTime()));
    const completedTrip=Array.isArray(r.trips)&&r.trips.some(t=>!t.future&&!t.pending&&t.arrivalAt!==null);
    const workConfirmed=!r.auto||completedTrip;
    r.shortfall=r.worked===null?null:r.covered&&r.scheduled&&r.type==='work'&&!r.future&&
      shiftEnded&&workConfirmed?Math.max(0,s.dayMinutes-r.worked):0;
    // Existing manually entered compensatory off days keep their old −8h rule.
    // Holidays, vacation, sick leave, paid leave and holiday work are exempt.
    r.compDebit=r.covered&&!r.future&&!r.auto&&r.scheduled&&r.type==='off'?s.dayMinutes:0;
    r.overtimeNet=r.worked===null?null:r.overtime-r.shortfall-r.compDebit;
  }
  const grossOvertime=rows.reduce((a,r)=>a+r.overtime,0);
  const shortfall=rows.reduce((a,r)=>a+r.shortfall,0);
  const compDays=rows.filter(r=>r.compDebit>0).length;
  const compTime=rows.reduce((a,r)=>a+r.compDebit,0);
  const net=grossOvertime-shortfall-compTime;
  const reviewDates=rows.filter(r=>r.pendingTripReview).map(r=>r.date);
  const unknownTarget=rows.filter(r=>r.covered&&r.scheduled&&r.worked===null&&r.type!=='holidayWork').length*s.dayMinutes;
  return {
    rows:rows,target:target,normal:normal,missing:Math.max(0,target-unknownTarget-normal),
    grossOvertime:grossOvertime,shortfall:shortfall,compDays:compDays,compTime:compTime,
    compApplied:compTime,overtime:net,
    worked:rows.reduce((a,r)=>a+r.worked,0),
    holidayWork:rows.filter(r=>r.type==='holidayWork').reduce((a,r)=>a+r.worked,0),
    pendingTripReview:reviewDates.length>0,totalsComplete:reviewDates.length===0,reviewDates:reviewDates
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
  const grossOvertime=sum('overtime'),shortfall=sum('shortfall'),compTime=sum('compDebit');
  const compDays=rows.filter(r=>r.compDebit>0).length;
  const travel=sum('travel'),worked=sum('worked');
  const workplace=rows.reduce((a,r)=>a+Math.max(0,r.worked-r.travel),0);
  const workDays=rows.filter(r=>r.covered&&!r.future&&r.worked>0).length;
  const reviewDates=rows.filter(r=>r.pendingTripReview).map(r=>r.date);
  return {
    from:from,to:to,rows:rows,worked:worked,regular:sum('regular'),
    grossOvertime:grossOvertime,shortfall:shortfall,
    compDays:compDays,compTime:compTime,compApplied:compTime,
    overtime:grossOvertime-shortfall-compTime,
    pause:sum('pause'),credit:sum('credit'),expected:sum('expectedElapsed'),monthPlan:sum('expected'),
    counts:counts,planned:planned,
    missing:closedWeeks.reduce((a,[m,w])=>a+w.missing,0),
    holidayWork:rows.filter(r=>r.type==='holidayWork').reduce((a,r)=>a+r.worked,0),
    travel:travel,workplace:workplace,workDays:workDays,tripRows:rows.flatMap(r=>r.trips),
    pendingTripReview:reviewDates.length>0,totalsComplete:reviewDates.length===0,reviewDates:reviewDates
  };
};

// All-time balance from the beginning of the saved records, including past months.
Core.balance=function(state,now=new Date()){
  const end=Core.today(now),start=state.settings.trackStart;
  return start>end?0:Core.period(state,start,end,now).overtime;
};

function overtimeReason(r){
  if(r.type==='holidayWork'){
    return 'Работа на празник (неработен ден): сите '+Core.hh(r.worked)+' работени часови се пресметани како прекувремени.';
  }
  if(!r.scheduled){
    return 'Работа во викенд / неработен ден: сите '+Core.hh(r.overtime)+' работени часови се прекувремени, без дополнување на редовниот неделен фонд.';
  }
  if(r.trips&&r.trips.length){
    return 'Продолжен работен ден со службен пат: '+Core.hh(r.worked)+' работено − '+Core.hh(r.regular)+' редовно = '+Core.hh(r.overtime)+' прекувремено.';
  }
  return 'Продолжен работен ден: '+Core.hh(r.worked)+' работено − '+Core.hh(r.regular)+' редовно = '+Core.hh(r.overtime)+' прекувремено.';
}

Core.overtimeBreakdown=function(state,from,to,now=new Date()){
  const p=Core.period(state,from,to,now);
  const earned=p.rows.filter(r=>r.covered&&!r.future&&r.overtime>0).map(r=>({
    date:r.date,type:r.type,scheduled:r.scheduled,start:r.start||'',end:r.end||'',
    overnight:!!r.overnight,worked:r.worked,regular:r.regular,overtime:r.overtime,
    travel:r.travel||0,note:r.note||'',reason:overtimeReason(r)
  }));
  const deductions=[];
  for(const r of p.rows){
    if(r.compDebit>0)deductions.push({
      date:r.date,kind:'off',applied:r.compDebit,note:r.note||'',
      reason:'Искористен слободен ден од прекувремени: −'+Core.hh(r.compDebit)+'.'
    });
    if(r.shortfall>0)deductions.push({
      date:r.date,kind:'short',worked:r.worked,applied:r.shortfall,note:r.note||'',
      reason:'Пократок работен ден: '+Core.hh(r.worked)+' од '+Core.hh(state.settings.dayMinutes)+
        ' планирани; од прекувремените се одземаат '+Core.hh(r.shortfall)+'.'
    });
  }
  return {
    period:p,earned:earned,deductions:deductions,gross:p.grossOvertime,
    holiday:p.holidayWork,other:Math.max(0,p.grossOvertime-p.holidayWork),
    shortfall:p.shortfall,compApplied:p.compTime,net:p.overtime
  };
};

return Core;
});
