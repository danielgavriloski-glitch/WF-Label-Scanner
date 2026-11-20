(function(root,factory){
 const node=typeof module==='object'&&module.exports;
 const api=factory(node?require('./core.js'):root.Core,node?require('./documents-core.js'):root.Warehouse);
 if(node)module.exports=api;else root.Pickups=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(Core,Warehouse){
'use strict';
const copy=x=>JSON.parse(JSON.stringify(x));
const validId=s=>typeof s==='string'&&/^[-a-zA-Z0-9_]{1,100}$/.test(s);
const text=(s,max)=>typeof s==='string'&&s.length<=max;
const stamp=s=>typeof s==='string'&&/^\d{4}-\d{2}-\d{2}T/.test(s)&&Number.isFinite(Date.parse(s));
const fresh=()=>({version:1,items:[]});
function validate(log){
 if(!log||log.version!==1||!Array.isArray(log.items)||log.items.length>20000)throw Error('Невалидна листа за подигање.');
 const ids=new Set(),visits=new Set();
 for(const item of log.items){
  if(!item||!validId(item.id)||ids.has(item.id)||!text(item.from,200)||!item.from.trim()||!text(item.goods,2000)||!item.goods.trim()||!text(item.address,500)||!text(item.note,1000)||!['pending','picked_up'].includes(item.status)||!stamp(item.createdAt)||!stamp(item.updatedAt))throw Error('Внеси од каде и што треба да се подигне.');
  if(item.status==='picked_up'){
   if(!stamp(item.pickedAt)||!validId(item.visitId)||visits.has(item.visitId))throw Error('Невалиден запис за подигната роба.');
   visits.add(item.visitId);
  }else if(item.pickedAt!==''||item.visitId!=='')throw Error('Невалидна состојба на робата.');
  ids.add(item.id);
 }
 return log;
}
function pending(log){return validate(log||fresh()).items.filter(x=>x.status==='pending').sort((a,b)=>a.createdAt.localeCompare(b.createdAt));}
function put(log,input,now=new Date()){
 const out=copy(validate(log||fresh())),at=now.toISOString();
 let item=input.id?out.items.find(x=>x.id===input.id):null;
 if(input.id&&!item)throw Error('Записот не е пронајден.');
 if(item&&item.status!=='pending')throw Error('Подигнатата роба е во Документи.');
 if(!item){item={id:'pickup_'+Warehouse.id(),status:'pending',createdAt:at,pickedAt:'',visitId:''};out.items.push(item);}
 for(const key of ['from','goods','address','note'])item[key]=String(input[key]??'').trim();
 item.updatedAt=at;
 validate(out);return {log:out,item};
}
// Both logs are persisted together by the existing atomic state writer.
function complete(state,itemId,now=new Date()){
 Core.validate(state);
 const out=copy(state),log=out.pickupLog||fresh(),item=log.items.find(x=>x.id===itemId);
 if(!item)throw Error('Записот не е пронајден.');
 if(item.status==='picked_up'){
  if(!(out.warehouseLog&&out.warehouseLog.visits.some(v=>v.id===item.visitId)))throw Error('Поврзаниот документ не е пронајден.');
  return {state:out,visitId:item.visitId,changed:false};
 }
 const p=Core.parts(now),at=now.toISOString();
 const result=Warehouse.putVisit(out.warehouseLog||Warehouse.fresh(),{
  date:p.date,time:p.hour+':'+p.minute,warehouseId:'',warehouseName:item.from,
  action:'pickup',note:['Роба: '+item.goods,item.address?'Адреса: '+item.address:'',item.note?'Белешка: '+item.note:''].filter(Boolean).join('\n'),
  stopId:'',sessionId:'',pickupId:item.id
 });
 item.status='picked_up';item.pickedAt=at;item.updatedAt=at;item.visitId=result.visit.id;
 out.pickupLog=validate(log);out.warehouseLog=result.log;Core.validate(out);
 return {state:out,visitId:item.visitId,changed:true};
}
function preserveOnImport(current,incoming){
 const out=copy(incoming);
 if(current&&current.pickupLog&&out.pickupLog===undefined){
  out.pickupLog=copy(current.pickupLog);
  const linked=new Set(out.pickupLog.items.filter(x=>x.status==='picked_up').map(x=>x.visitId));
  if(linked.size){
   const source=current.warehouseLog||Warehouse.fresh();
   out.warehouseLog=out.warehouseLog||Warehouse.fresh();
   for(const visit of source.visits.filter(v=>linked.has(v.id))){
    const target=out.warehouseLog.visits.find(v=>v.id===visit.id);
    if(target){if(JSON.stringify(target)!==JSON.stringify(visit))throw Error('Бекапот содржи различен запис со ист број на документ.');continue;}
    if(visit.warehouseId&&!out.warehouseLog.warehouses.some(w=>w.id===visit.warehouseId)){
     const w=source.warehouses.find(w=>w.id===visit.warehouseId);if(w)out.warehouseLog.warehouses.push(copy(w));
    }
    out.warehouseLog.visits.push(copy(visit));
   }
  }
 }
 Core.validate(out);return out;
}
if(!Core.__pickupsV1){
 const previous=Core.validate;
 Core.validate=raw=>{
  previous(raw);
  if(raw.pickupLog!==undefined){
   validate(raw.pickupLog);
   const visits=new Set(((raw.warehouseLog||{}).visits||[]).map(v=>v.id));
   if(raw.pickupLog.items.some(x=>x.status==='picked_up'&&!visits.has(x.visitId)))throw Error('Недостасува документ за подигната роба.');
  }
  return raw;
 };Core.__pickupsV1=true;
}
return {fresh,validate,pending,put,complete,preserveOnImport};
});
