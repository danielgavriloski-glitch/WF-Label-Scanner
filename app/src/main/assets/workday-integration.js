/* WFAG simple hours: fixed schedule, manual records win, travel extends the day. No GPS dependency. */
(function(root,factory){if(typeof module==='object'&&module.exports)module.exports=factory(require('./core.js'));else factory(root.Core);})(typeof globalThis!=='undefined'?globalThis:this,function(Core){
'use strict';
if(!Core||Core.__wfagSimpleHours)return Core;
Core.__wfagSimpleHours=true;
const original=Core.day;
Core.day=function(state,date,now=new Date()){
 const r=original(state,date,now);
 // Explicit user corrections always override automatic sessions and trips.
 if(!r.covered||!r.auto||!r.scheduled||r.type!=='work')return r;
 const trips=(state.trips||[]).filter(t=>t.date===date);
 if(!trips.length)return r;
 const shiftEnd=Core.timestamp(date,state.settings.end);
 const startAt=Core.timestamp(date,state.settings.start);
 let latest=shiftEnd;
 for(const t of trips){
  const departure=Core.timestamp(t.date,t.departure);
  if(departure>now.getTime())continue;
  const end=t.arrival?Core.timestamp(t.arrivalDate,t.arrival):now.getTime();
  if(end>latest)latest=end;
 }
 // An open trip extends the shift only up to the current moment.
 const effective=Math.min(latest,now.getTime());
 const worked=Math.max(0,Math.floor((effective-startAt)/60000));
 const endParts=Core.parts(new Date(latest));
 return {...r,worked,planWork:Math.max(r.planWork,Math.floor((latest-startAt)/60000)),
   end:endParts.hour+':'+endParts.minute,overnight:endParts.date!==date,
   travel:r.travel,workdayPending:false};
};
return Core;
});
