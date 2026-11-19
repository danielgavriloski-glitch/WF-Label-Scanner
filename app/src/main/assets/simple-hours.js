/* Normal WFAG hours: manual record > trip > automatic 08:00-16:00.
   Legacy GPS workday sessions are ignored without deleting any saved data. */
(function(root,factory){
 if(typeof module==='object'&&module.exports)module.exports=factory(require('./core.js'),require('./v12-core.js'));
 else factory(root.Core);
})(typeof globalThis!=='undefined'?globalThis:this,function(Core){
 'use strict';
 if(!Core||Core.__simpleHours)return Core;
 Core.__simpleHours=true;
 const previous=Core.day;
 Core.day=function(state,date,now=new Date()){
   const explicit=Object.prototype.hasOwnProperty.call(state.records||{},date);
   const today=Core.today(now);
   const originalTrips=state.trips||[];
   // An unfinished trip from an earlier day must never count through today.
   // Manual shift edits are authoritative, even when trips exist.
   const ignored=originalTrips.filter(t=>t.date===date&&(explicit||(!t.arrival&&date<today)));
   if(!ignored.length)return previous(state,date,now);
   const clean={...state,trips:originalTrips.filter(t=>!ignored.includes(t))};
   const row=previous(clean,date,now);
   const visible=originalTrips.filter(t=>t.date===date).map(t=>Core.tripView(t,now));
   return {...row,trips:visible,travel:explicit?Math.min(row.worked,visible.reduce((sum,t)=>sum+(t.pending?0:t.travel),0)):Math.min(row.worked,visible.reduce((sum,t)=>sum+(t.pending&&date<today?0:t.travel),0)),
      pendingTripReview:ignored.some(t=>!t.arrival&&date<today)};
 };
 return Core;
});
