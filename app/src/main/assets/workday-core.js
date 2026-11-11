/* WFAG workday session engine. Pure calculations; existing records remain untouched. */
(function(root,factory){
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  else root.WFAGWorkday=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const MODES=['fixed','geofence','manual'];
function assert(cond,msg){if(!cond)throw Error(msg);}
function settings(raw){
 const s=Object.assign({mode:'fixed',start:'08:00',radiusM:100,workplace:null},raw||{});
 assert(MODES.includes(s.mode),'Невалиден начин на почеток');
 assert(/^([01]\d|2[0-3]):[0-5]\d$/.test(s.start),'Невалиден час');
 assert(Number.isFinite(s.radiusM)&&s.radiusM>=50&&s.radiusM<=1000,'GPS радиусот мора да биде 50–1000 m');
 if(s.workplace){assert(Number.isFinite(s.workplace.lat)&&Math.abs(s.workplace.lat)<=90,'Невалидна географска ширина');assert(Number.isFinite(s.workplace.lon)&&Math.abs(s.workplace.lon)<=180,'Невалидна географска должина');}
 return s;
}
function distanceM(a,b){
 const rad=Math.PI/180,dlat=(b.lat-a.lat)*rad,dlon=(b.lon-a.lon)*rad;
 const h=Math.sin(dlat/2)**2+Math.cos(a.lat*rad)*Math.cos(b.lat*rad)*Math.sin(dlon/2)**2;
 return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
}
function newDay(date){assert(/^\d{4}-\d{2}-\d{2}$/.test(date),'Невалиден датум');return {date,startAt:null,endAt:null,travel:[],status:'not-started',source:null};}
function start(day,at,source){assert(day.startAt===null,'Работниот ден е веќе започнат');assert(Number.isFinite(at),'Невалидно време');return Object.assign({},day,{startAt:at,status:'working',source:source||'manual'});}
function depart(day,at){assert(day.startAt!==null&&day.endAt===null,'Прво започни работен ден');assert(day.status!=='travel','Веќе си на пат');assert(at>=day.startAt,'Невалидно време');return Object.assign({},day,{status:'travel',travel:day.travel.concat([{departureAt:at,arrivalAt:null}])});}
function arrive(day,at){assert(day.status==='travel','Нема активно патување');const trips=day.travel.slice(),i=trips.length-1;assert(at>=trips[i].departureAt,'Невалидно време');trips[i]=Object.assign({},trips[i],{arrivalAt:at});return Object.assign({},day,{status:'working',travel:trips});}
function finish(day,at){assert(day.startAt!==null&&day.endAt===null&&at>=day.startAt,'Невалидно завршување');const d=day.status==='travel'?arrive(day,at):day;return Object.assign({},d,{endAt:at,status:'finished'});}
function totals(day,now,standardMinutes=480){
 assert(Number.isFinite(standardMinutes)&&standardMinutes>=0,'Невалиден фонд');
 const end=day.endAt===null?now:day.endAt;
 const minutes=day.startAt===null?0:Math.max(0,Math.floor((end-day.startAt)/60000));
 const travelMinutes=day.travel.reduce((n,t)=>n+Math.max(0,Math.floor(((t.arrivalAt===null?end:t.arrivalAt)-t.departureAt)/60000)),0);
 return {workedMinutes:minutes,travelMinutes:Math.min(minutes,travelMinutes),overtimeDelta:day.endAt===null?0:minutes-standardMinutes};
}
function geofenceStart(day,config,position,at){
 const s=settings(config);
 if(s.mode!=='geofence'||!s.workplace||day.startAt!==null||!position||position.accuracy>Math.min(s.radiusM,150))return day;
 return distanceM(s.workplace,position)<=s.radiusM?start(day,at,'geofence'):day;
}
return {settings,distanceM,newDay,start,depart,arrive,finish,totals,geofenceStart};
});