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
 const ids=new Set(),visits=new Map();
 for(const item of log.items){
  if(!item||!validId(item.id)||ids.has(item.id)||!text(item.from,200)||!item.from.trim()||!text(item.goods,2000)||!item.goods.trim()||!text(item.address,500)||!text(item.note,4000)||!['pending','picked_up'].includes(item.status)||!stamp(item.createdAt)||!stamp(item.updatedAt))throw Error('Внеси од каде и што треба да се подигне.');
  if(item.packages!==undefined&&(!Number.isSafeInteger(item.packages)||item.packages<0||!Number.isSafeInteger(item.pickedPackages)||item.pickedPackages<0||item.pickedPackages>item.packages))throw Error('Невалиден број пакети.');
  if(item.dueDate!==undefined&&item.dueDate!==''&&!Core.dateValid(item.dueDate))throw Error('Невалиден датум за подигање.');
  if(item.weightKg!==undefined&&item.weightKg!==null&&(!Number.isFinite(item.weightKg)||item.weightKg<0))throw Error('Невалидна тежина на товарот.');
  if(item.status==='picked_up'){
   if(!stamp(item.pickedAt)||!validId(item.visitId)||(visits.has(item.visitId)&&!(item.legacyCargoId&&visits.get(item.visitId))))throw Error('Невалиден запис за подигната роба.');
   visits.set(item.visitId,!!item.legacyCargoId);
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
 for(const key of ['packages','dueDate','weightKg'])if(Object.prototype.hasOwnProperty.call(input,key))item[key]=input[key];
 item.updatedAt=at;
 validate(out);return {log:out,item};
}
function legacyValidate(log){
 if(!log||log.version!==1||!Array.isArray(log.shipments)||log.shipments.length>20000)throw Error('Невалидна постара евиденција на товари. Податоците не се избришани.');
 const ids=new Set();
 for(const s of log.shipments){
  if(!s||typeof s.id!=='string'||!/^cargo_[a-z0-9_]+$/.test(s.id)||ids.has(s.id)||!['pending','partial','picked_up','delivered','cancelled'].includes(s.status)||!text(s.company,200)||!s.company.trim()||!text(s.goods,2000)||!text(s.location,500)||!text(s.notes,4000)||!text(s.dueDate,10)||(s.dueDate&&!Core.dateValid(s.dueDate))||!Number.isSafeInteger(s.packages)||s.packages<0||!Number.isSafeInteger(s.pickedPackages)||s.pickedPackages<0||s.pickedPackages>s.packages||!Array.isArray(s.documents)||s.documents.length>100||s.documents.some(d=>typeof d!=='string'||d.length>100))throw Error('Постариот товар содржи невалидни податоци. Провери го бекапот; записите не се прескокнати.');
  if(s.weightKg!==null&&(!Number.isFinite(s.weightKg)||s.weightKg<0))throw Error('Невалидна тежина во постариот товар.');
  if(s.visitIds!==undefined&&(!Array.isArray(s.visitIds)||s.visitIds.some(id=>typeof id!=='string')))throw Error('Невалидна врска со документ во постариот товар.');
  ids.add(s.id);
 }
 return log;
}
function legacyId(id){
 const direct='pickup_'+id;if(direct.length<=100)return direct;
 let hash=2166136261;for(let i=0;i<id.length;i++)hash=Math.imul(hash^id.charCodeAt(i),16777619);
 return direct.slice(0,86)+'_'+(hash>>>0).toString(16);
}
// Keep the original cargo log, all extra fields and every attachment link.
// Completed cargo reuses its existing document; never invent a pickup document.
function migrate(state){
 const out=copy(state);
 if(out.cargoLog===undefined)return out;
 legacyValidate(out.cargoLog);
 const log=out.pickupLog||fresh(),visits=((out.warehouseLog||{}).visits||[]);
 validate(log);
 for(const shipment of out.cargoLog.shipments){
  const existing=log.items.find(item=>item.legacyCargoId===shipment.id);
  if(existing){shipment.pickupItemId=existing.id;continue;}
  const active=shipment.status==='pending'||shipment.status==='partial';
  if(shipment.status==='cancelled')continue;
  const linked=visits.filter(visit=>(shipment.visitIds||[]).includes(visit.id));
  if(!active&&!linked.length)continue; // Visible in the legacy archive, without a fabricated date/document.
  const id=legacyId(shipment.id);
  if(log.items.some(item=>item.id===id))throw Error('Две евиденции имаат ист број на товар. Податоците не се заменети.');
  const createdAt=stamp(shipment.createdAt)?shipment.createdAt:stamp(shipment.updatedAt)?shipment.updatedAt:stamp(out.createdAt)?out.createdAt:'2000-01-01T00:00:00.000Z';
  const updatedAt=stamp(shipment.updatedAt)?shipment.updatedAt:createdAt;
  log.items.push({id,from:shipment.company,goods:shipment.goods.trim()?shipment.goods:'Товар без внесен опис',address:shipment.location,note:shipment.notes,
   status:active?'pending':'picked_up',createdAt,updatedAt,pickedAt:active?'':stamp(shipment.pickedAt)?shipment.pickedAt:updatedAt,visitId:active?'':linked[0].id,
   legacyCargoId:shipment.id,legacyStatus:shipment.status,legacyCreatedAtUnknown:!stamp(shipment.createdAt),legacyPickedAtUnknown:!active&&!stamp(shipment.pickedAt),
   packages:shipment.packages,pickedPackages:shipment.pickedPackages,weightKg:shipment.weightKg,dueDate:shipment.dueDate,
   legacyVisitIds:(shipment.visitIds||[]).slice(),legacyDocumentIds:shipment.documents.slice()});
  shipment.pickupItemId=id;
 }
 out.pickupLog=validate(log);return out;
}
function updateLegacy(out,item,at){
 if(!item.legacyCargoId||!out.cargoLog)return;
 const shipment=out.cargoLog.shipments.find(s=>s.id===item.legacyCargoId);if(!shipment)return;
 Object.assign(shipment,{company:item.from,goods:item.goods,location:item.address,notes:item.note,updatedAt:at,pickupItemId:item.id});
 for(const key of ['packages','dueDate','weightKg'])if(item[key]!==undefined)shipment[key]=item[key];
 if(item.status==='picked_up'){
  shipment.status=item.legacyStatus==='delivered'?'delivered':'picked_up';
  if(!item.legacyPickedAtUnknown)shipment.pickedAt=item.pickedAt;
  if(Number.isSafeInteger(item.pickedPackages))shipment.pickedPackages=item.pickedPackages;
  shipment.visitIds=Array.from(new Set([...(shipment.visitIds||[]),item.visitId]));
 }else if(Number.isSafeInteger(item.pickedPackages)){
  shipment.pickedPackages=item.pickedPackages;shipment.status=item.pickedPackages>0?'partial':'pending';
 }
}
function collect(state,itemId,count,now=new Date()){
 const out=copy(state),item=(out.pickupLog||fresh()).items.find(item=>item.id===itemId);
 if(!item||item.status!=='pending')throw Error('Товарот не чека подигање.');
 if(!Number.isSafeInteger(item.packages)||item.packages<1||!Number.isSafeInteger(count)||count<1||item.pickedPackages+count>item.packages)throw Error('Внеси точен број на подигнати пакети.');
 item.pickedPackages+=count;item.updatedAt=now.toISOString();
 if(item.pickedPackages===item.packages)return complete(out,itemId,now);
 item.legacyStatus='partial';updateLegacy(out,item,item.updatedAt);Core.validate(out);
 return {state:out,changed:true,visitId:'',partial:true};
}
function deliver(state,itemId,now=new Date()){
 const out=copy(state),item=(out.pickupLog||fresh()).items.find(item=>item.id===itemId);
 if(!item||item.status!=='picked_up'||!item.legacyCargoId)throw Error('Товарот прво треба да биде подигнат.');
 const shipment=((out.cargoLog||{}).shipments||[]).find(s=>s.id===item.legacyCargoId);
 if(!shipment)throw Error('Постариот товар не е пронајден.');
 item.legacyStatus='delivered';item.updatedAt=now.toISOString();shipment.status='delivered';shipment.updatedAt=item.updatedAt;
 Core.validate(out);return out;
}
function syncLegacy(state,itemId){
 const out=copy(state);
 for(const item of (out.pickupLog||fresh()).items)if(item.legacyCargoId&&(!itemId||item.id===itemId))updateLegacy(out,item,item.updatedAt);
 return out;
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
  action:'pickup',note:['Роба: '+item.goods,item.address?'Адреса: '+item.address:'',item.note&&item.note.length<=1000?'Белешка: '+item.note:item.note?'Целосната белешка е зачувана во записот за роба.':''].filter(Boolean).join('\n'),
  stopId:'',sessionId:'',pickupId:item.id
 });
 item.status='picked_up';item.pickedAt=at;item.updatedAt=at;item.visitId=result.visit.id;
 if(item.legacyCargoId){item.legacyStatus='picked_up';item.legacyPickedAtUnknown=false;}
 if(Number.isSafeInteger(item.packages))item.pickedPackages=item.packages;
 out.pickupLog=validate(log);out.warehouseLog=result.log;updateLegacy(out,item,at);Core.validate(out);
 return {state:out,visitId:item.visitId,changed:true};
}
function preserveOnImport(current,incoming){
 const out=copy(incoming);
 if(current&&current.cargoLog&&out.cargoLog===undefined)out.cargoLog=copy(current.cargoLog);
 const preservedCargo=!!(current&&current.cargoLog&&incoming.cargoLog===undefined);
 const linked=new Set();
 if(current&&current.pickupLog&&out.pickupLog===undefined){
  out.pickupLog=copy(current.pickupLog);
  out.pickupLog.items.filter(x=>x.status==='picked_up').forEach(x=>linked.add(x.visitId));
 }
 if(preservedCargo)out.cargoLog.shipments.forEach(s=>(s.visitIds||[]).forEach(id=>linked.add(id)));
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
 const migrated=migrate(out);Core.validate(migrated);return migrated;
}
if(!Core.__pickupsV1){
 const previous=Core.validate;
 Core.validate=raw=>{
  previous(raw);
  if(raw.cargoLog!==undefined)legacyValidate(raw.cargoLog);
  if(raw.pickupLog!==undefined){
   validate(raw.pickupLog);
   const visits=new Set(((raw.warehouseLog||{}).visits||[]).map(v=>v.id));
   if(raw.pickupLog.items.some(x=>x.status==='picked_up'&&!visits.has(x.visitId)))throw Error('Недостасува документ за подигната роба.');
  }
  return raw;
 };Core.__pickupsV1=true;
}
return {fresh,validate,pending,put,complete,collect,deliver,syncLegacy,migrate,legacyValidate,preserveOnImport};
});
