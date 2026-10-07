(function(root,factory){ const api=factory(); if(typeof module==='object'&&module.exports)module.exports=api; else root.Core=api; })(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const APP_ID='workfashion-personal-time', TZ='Europe/Skopje';
const TYPES={work:'Работа',holidayWork:'Работа на празник',vacation:'Годишен одмор',sick:'Боледување',holiday:'Празник',paid:'Платено отсуство',unpaid:'Неплатено отсуство',absent:'Отсуство',off:'Слободен ден'};
const PAID=['vacation','sick','holiday','paid'];
const DAY=86400000;
const has=(o,k)=>Object.prototype.hasOwnProperty.call(o,k);
const pad=n=>String(n).padStart(2,'0');
function dateValid(s){ if(typeof s!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(s))return false;const d=new Date(s+'T12:00:00Z');return !isNaN(d)&&d.toISOString().slice(0,10)===s&&s>='2000-01-01'&&s<='2100-12-31'; }
function plus(date,n){return new Date(Date.parse(date+'T12:00:00Z')+n*DAY).toISOString().slice(0,10);}
function weekday(date){return new Date(date+'T12:00:00Z').getUTCDay();}
function weekStart(date){return plus(date,-((weekday(date)+6)%7));}
function dates(from,to){if(!dateValid(from)||!dateValid(to)||from>to)throw Error('Провери ги датумите.');const out=[];for(let d=from;d<=to;d=plus(d,1)){out.push(d);if(out.length>37000)throw Error('Периодот е предолг.');}return out;}
function minutes(t){if(typeof t!=='string'||!/^([01]\d|2[0-3]):[0-5]\d$/.test(t))throw Error('Внеси валидно време.');return Number(t.slice(0,2))*60+Number(t.slice(3));}
function parts(now=new Date()){const a=new Intl.DateTimeFormat('en-CA',{timeZone:TZ,year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).formatToParts(now);const p={};a.forEach(x=>p[x.type]=x.value);return {...p,date:`${p.year}-${p.month}-${p.day}`,minuteOfDay:Number(p.hour)*60+Number(p.minute)};}
function today(now){return parts(now).date;}
function timestamp(date,time){const [y,m,d]=date.split('-').map(Number), min=minutes(time);const wanted=Date.UTC(y,m-1,d,Math.floor(min/60),min%60);let t=wanted;for(let i=0;i<3;i++){const p=parts(new Date(t));const shown=Date.UTC(Number(p.year),Number(p.month)-1,Number(p.day),Number(p.hour),Number(p.minute));const correction=wanted-shown;if(!correction)break;t+=correction;}return t;}
function monthBounds(month){if(!/^\d{4}-(0[1-9]|1[0-2])$/.test(month))throw Error('Невалиден месец.');const from=month+'-01';return [from,new Date(Date.UTC(Number(month.slice(0,4)),Number(month.slice(5)),0,12)).toISOString().slice(0,10)];}
function fresh(now=new Date()){return {appId:APP_ID,schemaVersion:1,createdAt:now.toISOString(),settings:{name:'',company:'Workfashion',trackStart:today(now),start:'08:00',end:'16:00',workdays:[1,2,3,4,5],dayMinutes:480,weekMinutes:2400,breaksIncluded:true},budgets:{},records:{},trips:[],audit:[],lastBackupAt:null};}
function tripView(trip,now=new Date()){
 const departureAt=timestamp(trip.date,trip.departure),arrivalAt=trip.arrival?timestamp(trip.arrivalDate,trip.arrival):null;
 const future=departureAt>now.getTime(),pending=arrivalAt===null;
 const travel=Math.max(0,Math.floor((Math.min(arrivalAt===null?now.getTime():arrivalAt,now.getTime())-departureAt)/60000));
 return {...trip,departureAt,arrivalAt,future,pending,planned:future||(arrivalAt!==null&&arrivalAt>now.getTime()),travel,plannedMinutes:arrivalAt===null?0:Math.max(0,Math.floor((arrivalAt-departureAt)/60000))};
}
function validateTrips(raw){
 if(!has(raw,'trips'))raw.trips=[]; // Version 1 records and leave balances remain intact.
 if(!Array.isArray(raw.trips)||raw.trips.length>37000)throw Error('Невалидна евиденција на патувања.');
 const ids=new Set(),ranges=[];
 for(const t of raw.trips){
  if(!t||typeof t.id!=='string'||!/^[-a-zA-Z0-9_]{1,80}$/.test(t.id)||ids.has(t.id))throw Error('Невалиден или дупликат запис за патување.');
  ids.add(t.id);
  if(!dateValid(t.date)||!dateValid(t.arrivalDate)||typeof t.note!=='string'||t.note.length>500||typeof t.arrival!=='string')throw Error('Провери го датумот и белешката за патувањето.');
  minutes(t.departure);if(t.arrival)minutes(t.arrival);
  const start=timestamp(t.date,t.departure),end=t.arrival?timestamp(t.arrivalDate,t.arrival):Infinity;
  if(end<start)throw Error('Пристигнувањето не може да биде пред поаѓањето. За пат преку полноќ избери го следниот датум.');
  ranges.push({start,end});
 }
 ranges.sort((a,b)=>a.start-b.start);
 for(let i=1;i<ranges.length;i++)if(ranges[i].start<ranges[i-1].end)throw Error('Времето се преклопува со друго патување. Прво внеси пристигнување за започнатиот пат.');
}
function putTrip(state,input){
 if(!input.id&&input.date<state.settings.trackStart)throw Error('Датумот е пред почетокот на евиденцијата.');
 const copy=JSON.parse(JSON.stringify(state));if(!copy.trips)copy.trips=[];
 const id=input.id||('trip_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,10));
 const trip={id,date:input.date,departure:input.departure,arrival:input.arrival||'',arrivalDate:input.arrival?input.arrivalDate:input.date,note:input.note||''};
 const index=copy.trips.findIndex(t=>t.id===id);if(index<0)copy.trips.push(trip);else copy.trips[index]=trip;
 validate(copy);return {state:copy,trip};
}
function removeTrip(state,id){const copy=JSON.parse(JSON.stringify(state));copy.trips=(copy.trips||[]).filter(t=>t.id!==id);validate(copy);return copy;}
function validate(raw){
 if(!raw||raw.appId!==APP_ID||raw.schemaVersion!==1)throw Error('Ова не е бекап од Workfashion · Мои часови.');
 const s=raw.settings;if(!s||!dateValid(s.trackStart))throw Error('Невалиден почетен датум.');
 minutes(s.start);minutes(s.end);if(minutes(s.end)<=minutes(s.start))throw Error('Автоматскиот распоред мора да заврши по почетното време.');
 if(!Array.isArray(s.workdays)||s.workdays.length<1||s.workdays.some(x=>!Number.isInteger(x)||x<0||x>6)||new Set(s.workdays).size!==s.workdays.length)throw Error('Невалидни работни денови.');
 for(const k of ['dayMinutes','weekMinutes'])if(!Number.isInteger(s[k])||s[k]<1||s[k]>(k==='dayMinutes'?1440:10080))throw Error('Невалиден фонд на часови.');
 if(typeof s.breaksIncluded!=='boolean'||typeof s.name!=='string'||s.name.length>120||typeof s.company!=='string'||s.company.length>120)throw Error('Невалидни поставки.');
 if(!raw.records||Array.isArray(raw.records)||typeof raw.records!=='object'||Object.keys(raw.records).length>37000)throw Error('Невалидна евиденција.');
 for(const [d,r] of Object.entries(raw.records)){
  if(!dateValid(d)||!r||!has(TYPES,r.type)||typeof r.note!=='string'||r.note.length>1500)throw Error('Невалиден дневен запис.');
  if(!Number.isInteger(r.pause)||r.pause<0||r.pause>1440)throw Error('Невалидна пауза.');
  if(has(r,'pausePaid')&&typeof r.pausePaid!=='boolean')throw Error('Невалидна поставка за пауза.');
  if(['work','holidayWork'].includes(r.type)){
   minutes(r.start);minutes(r.end);if(typeof r.overnight!=='boolean')throw Error('Невалидна смена.');
   const len=minutes(r.end)-minutes(r.start)+(r.overnight?1440:0);if(len<=0||len>1440||r.pause>len)throw Error('Провери го времето и паузата.');
  }
 }
 if(!raw.budgets||typeof raw.budgets!=='object'||Array.isArray(raw.budgets))throw Error('Невалиден годишен одмор.');
 for(const [y,b] of Object.entries(raw.budgets)){if(!/^20\d{2}$/.test(y)||!b)throw Error('Невалидна година.');for(const k of ['annual','carry','previous'])if(!Number.isFinite(b[k])||b[k]<0||b[k]>500)throw Error('Невалидна состојба на одморот.');}
 validateTrips(raw);
 if(!Array.isArray(raw.audit)||raw.audit.length>10000||raw.audit.some(x=>!x||typeof x.at!=='string'||typeof x.text!=='string'||x.text.length>2000))throw Error('Невалидна историја.');
 return raw;
}
function day(state,date,now=new Date()){
 const s=state.settings,p=parts(now),scheduled=s.workdays.includes(weekday(date)),covered=date>=s.trackStart;
 const r=has(state.records,date)?state.records[date]:null;
 const type=covered?(r?r.type:(scheduled?'work':'off')):'off';
 const isWork=type==='work'||type==='holidayWork';const start=r&&isWork?r.start:s.start,end=r&&isWork?r.end:s.end,overnight=!!(r&&r.overnight&&isWork);
 const shiftEnd=timestamp(overnight?plus(date,1):date,end),shiftStart=timestamp(date,start);
 const span=isWork?Math.max(0,(shiftEnd-shiftStart)/60000):0;
 const elapsed=isWork?Math.max(0,Math.min(shiftEnd,now.getTime())-shiftStart)/60000:0;
 const future=date>p.date;
 const pause=isWork?Math.min(r?r.pause:0,Math.floor(elapsed)):0;
 const pausePaid=r&&has(r,'pausePaid')?r.pausePaid:s.breaksIncluded;
 const worked=isWork?Math.max(0,Math.floor(elapsed)-(pausePaid?0:pause)):0;
 const expected=covered&&scheduled?s.dayMinutes:0;
 const scheduleSpan=Math.max(1,minutes(s.end)-minutes(s.start));
 const ratio=date<p.date?1:date>p.date?0:Math.min(1,Math.max(0,(p.minuteOfDay-minutes(s.start))/scheduleSpan));
 const expectedElapsed=Math.floor(expected*ratio);
 const credit=PAID.includes(type)&&covered&&scheduled?Math.floor(s.dayMinutes*ratio):0;
 const planWork=isWork?Math.max(0,Math.floor(span)-(pausePaid?0:(r?r.pause:0))):0;
 const planCredit=PAID.includes(type)&&covered&&scheduled?s.dayMinutes:0;
 const trips=covered?(state.trips||[]).filter(t=>t.date===date).map(t=>tripView(t,now)).sort((a,b)=>a.departureAt-b.departureAt):[];
 return {date,type,start:isWork?start:'',end:isWork?end:'',overnight,auto:!r,covered,scheduled,future,pause,worked,planWork,planCredit,credit,expected,expectedElapsed,note:r?r.note:'',regular:0,overtime:0,trips,travel:trips.reduce((a,t)=>a+t.travel,0)};
}
function week(state,monday,now=new Date()){
 const rows=dates(monday,plus(monday,6)).map(d=>day(state,d,now)),s=state.settings;
 // Recording can begin midweek: exclude the unknown earlier weekdays from the fund.
 const coveredDays=rows.filter(r=>r.covered&&s.workdays.includes(weekday(r.date))).length;
 const nominalDays=s.workdays.length;
 const target=Math.round(s.weekMinutes*coveredDays/nominalDays);
 let normal=0;
 for(const r of rows){if(r.scheduled){r.regular=Math.min(r.worked,s.dayMinutes);r.overtime=Math.max(0,r.worked-s.dayMinutes);normal+=Math.min(s.dayMinutes,r.regular+r.credit);}}
 let gap=Math.max(0,target-normal);
 for(const r of rows){if(!r.scheduled){r.regular=Math.min(r.worked,gap);gap-=r.regular;r.overtime=r.worked-r.regular;normal+=r.regular;}}
 return {rows,target,normal,missing:Math.max(0,target-normal),overtime:rows.reduce((a,r)=>a+r.overtime,0),worked:rows.reduce((a,r)=>a+r.worked,0)};
}
function period(state,from,to,now=new Date()){
 const all=dates(from,to),cache={};const rows=all.map(d=>{const m=weekStart(d);if(!cache[m])cache[m]=week(state,m,now);return cache[m].rows.find(r=>r.date===d);});
 const sum=k=>rows.reduce((a,r)=>a+r[k],0);
 const counts={};Object.keys(TYPES).forEach(t=>counts[t]=rows.filter(r=>r.covered&&!r.future&&r.type===t&&(t!=='vacation'||r.scheduled)).length);
 const planned={};Object.keys(TYPES).forEach(t=>planned[t]=rows.filter(r=>r.covered&&r.future&&r.type===t&&(t!=='vacation'||r.scheduled)).length);
 const closedWeeks=Object.entries(cache).filter(([m])=>m>=from&&plus(m,6)<=to&&plus(m,6)<today(now));
 return {from,to,rows,worked:sum('worked'),regular:sum('regular'),overtime:sum('overtime'),pause:sum('pause'),credit:sum('credit'),expected:sum('expectedElapsed'),monthPlan:sum('expected'),counts,planned,missing:closedWeeks.reduce((a,[m,w])=>a+w.missing,0),holidayWork:rows.filter(r=>r.type==='holidayWork').reduce((a,r)=>a+r.worked,0),travel:sum('travel'),tripRows:rows.flatMap(r=>r.trips)};
}
function leave(state,year,now=new Date()){
 const b=has(state.budgets,String(year))?state.budgets[String(year)]:{annual:0,carry:0,previous:0};let used=0,planned=0,sick=0;
 for(const [d,r] of Object.entries(state.records)){if(d.slice(0,4)!==String(year)||d<state.settings.trackStart)continue;if(r.type==='vacation'&&state.settings.workdays.includes(weekday(d))){if(d<=today(now))used++;else planned++;}if(r.type==='sick'&&d<=today(now))sick++;}
 const total=b.annual+b.carry;return {...b,total,used,planned,sick,balance:total-b.previous-used,available:total-b.previous-used-planned,isSet:Object.hasOwn(state.budgets,String(year))};
}
function putRange(state,from,to,record,weekdaysOnly=true){
 const days=dates(from,to);if(days.length>366)throw Error('Додади најмногу една година одеднаш.');
 if(from<state.settings.trackStart)throw Error('Датумот е пред почетокот на евиденцијата. Смени го почетокот во Поставки.');
 const chosen=days.filter(d=>!weekdaysOnly||state.settings.workdays.includes(weekday(d)));
 if(!chosen.length)throw Error('Во периодот нема избрани работни денови.');
 const copy=JSON.parse(JSON.stringify(state));chosen.forEach(d=>copy.records[d]={...record});validate(copy);return {state:copy,count:chosen.length,overwritten:chosen.filter(d=>has(state.records,d)).length};
}
function hh(min){const n=Math.max(0,Math.round(min));return `${Math.floor(n/60)}:${pad(n%60)}`;}
function displayDate(d){return `${d.slice(8)}.${d.slice(5,7)}.${d.slice(0,4)}`;}
return {APP_ID,TZ,TYPES,PAID,pad,dateValid,plus,weekday,weekStart,dates,minutes,parts,today,timestamp,monthBounds,fresh,validate,day,week,period,leave,putRange,tripView,putTrip,removeTrip,hh,displayDate};
});
