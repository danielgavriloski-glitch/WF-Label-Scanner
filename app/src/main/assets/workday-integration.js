/* Explicit workday sessions override only their own dates; legacy days stay unchanged. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./core.js'),require('./workday-core.js'));else factory(root.Core,root.WFAGWorkday);})(typeof globalThis!=='undefined'?globalThis:this,function(Core,W){
'use strict';
if(!Core||!W||Core.__wfagWorkdayIntegration)return Core;
Core.__wfagWorkdayIntegration=true;
const previous=Core.day;
Core.day=function(state,date,now=new Date()){
 let r=previous(state,date,now);
 const session=state.workdaySessions&&state.workdaySessions[date];
 if(!r.covered)return r;
 // An explicitly started Saturday/Sunday workday is work, not a day off.
 if(session&&session.startAt!==null&&r.type==='off'&&!r.scheduled)
  r=Object.assign({},r,{type:'work'});
 if(!['work','holidayWork'].includes(r.type))return r;
 const cfg=state.workdaySettings;
 if((!session||session.startAt===null)&&cfg&&cfg.activatedOn&&date>=cfg.activatedOn&&
    r.auto&&(!r.trips||r.trips.length===0)){
   return Object.assign({},r,{worked:0,travel:0,planWork:0,workdayPending:true});
 }
 if(!session||session.startAt===null)return r;
 const t=W.totals(session,now.getTime(),state.settings.dayMinutes);
 const start=Core.parts(new Date(session.startAt)),end=Core.parts(new Date(session.endAt===null?now.getTime():session.endAt));
 return Object.assign({},r,{
  worked:t.workedMinutes,travel:Math.min(t.workedMinutes,Math.max(t.travelMinutes,r.travel||0)),
  start:start.hour+':'+start.minute,end:end.hour+':'+end.minute,
  overnight:end.date!==date,auto:false,
  workdaySession:true,workdayFinished:session.endAt!==null,
  workEndAt:session.endAt===null?now.getTime():session.endAt
 });
};
const previousValidate=Core.validate;
Core.validate=function(raw){
 previousValidate(raw);
 if(raw.workdaySettings)W.settings(raw.workdaySettings);
 if(raw.workdaySessions){
  if(typeof raw.workdaySessions!=='object'||Array.isArray(raw.workdaySessions)||Object.keys(raw.workdaySessions).length>37000)throw Error('Невалидни работни денови');
  for(const [date,d] of Object.entries(raw.workdaySessions)){
   if(!/^\d{4}-\d{2}-\d{2}$/.test(date)||!d||d.date!==date||!['not-started','working','travel','finished'].includes(d.status))throw Error('Невалиден работен ден');
   if(d.startAt!==null&&(!Number.isFinite(d.startAt)||d.startAt<0))throw Error('Невалиден почеток');
   if(d.endAt!==null&&(!Number.isFinite(d.endAt)||d.endAt<d.startAt))throw Error('Невалиден крај');
   if(!Array.isArray(d.travel)||d.travel.length>1000)throw Error('Невалидни патувања');
  }
 }
 return raw;
};
return Core;
});
