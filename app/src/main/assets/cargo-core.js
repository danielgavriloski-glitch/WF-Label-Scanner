/* WFAG cargo pickup domain model. Offline-first, no changes to GPS or warehouse storage. */
(function(root,factory){const api=factory();if(typeof module==='object'&&module.exports)module.exports=api;else root.WFCargo=api;})(typeof globalThis!=='undefined'?globalThis:this,function(){
'use strict';
const statuses=['pending','partial','picked_up','delivered','cancelled'];
const id=()=> 'cargo_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,10);
const clone=x=>JSON.parse(JSON.stringify(x));
const fresh=()=>({version:1,shipments:[]});
const safe=(s,n=500)=>typeof s==='string'&&s.length<=n;
function validate(log){
 if(!log||log.version!==1||!Array.isArray(log.shipments)||log.shipments.length>20000)throw Error('Невалидна евиденција на товари.');
 const ids=new Set();
 for(const s of log.shipments){
  if(!s||!/^cargo_[a-z0-9_]+$/.test(s.id)||ids.has(s.id)||!statuses.includes(s.status)||!safe(s.company,200)||!s.company.trim()||!safe(s.goods,2000)||!safe(s.location,500)||!safe(s.notes,4000)||!safe(s.dueDate,10)||s.dueDate&&!/^\d{4}-\d{2}-\d{2}$/.test(s.dueDate)||!Number.isSafeInteger(s.packages)||s.packages<0||!Number.isSafeInteger(s.pickedPackages)||s.pickedPackages<0||s.pickedPackages>s.packages||!Array.isArray(s.documents)||s.documents.length>100)throw Error('Невалидни податоци за товар.');
  if(s.weightKg!==null&&(!Number.isFinite(s.weightKg)||s.weightKg<0))throw Error('Невалидна тежина.');
  if(!s.documents.every(d=>typeof d==='string'&&d.length<=100))throw Error('Невалидна врска со документ.');
  ids.add(s.id);
 }
 return log;
}
function put(log,data){
 const next=clone(log||fresh()),existing=data.id&&next.shipments.find(s=>s.id===data.id);
 if(data.id&&!existing)throw Error('Товарот не постои.');
 const s=existing||{id:id(),createdAt:new Date().toISOString(),status:'pending',company:'',goods:'',location:'',dueDate:'',notes:'',packages:0,pickedPackages:0,weightKg:null,documents:[],warehouseId:'',visitIds:[]};
 Object.assign(s,data,{id:s.id,updatedAt:new Date().toISOString()});
 if(!existing)next.shipments.push(s);
 validate(next);return {log:next,shipment:s};
}
function pickup(log,shipmentId,count){
 const next=clone(log),s=next.shipments.find(x=>x.id===shipmentId);
 if(!s)throw Error('Товарот не постои.');
 if(s.status==='delivered'||s.status==='cancelled')throw Error('Товарот е затворен.');
 if(!Number.isSafeInteger(count)||count<1||s.pickedPackages+count>s.packages)throw Error('Невалиден број пакети.');
 s.pickedPackages+=count;s.status=s.pickedPackages===s.packages?'picked_up':'partial';s.updatedAt=new Date().toISOString();
 validate(next);return next;
}
function due(log,today){return validate(log).shipments.filter(s=>['pending','partial'].includes(s.status)&&(!s.dueDate||s.dueDate<=today)).sort((a,b)=>(a.dueDate||'').localeCompare(b.dueDate||''));}
if(typeof module!=='object'&&typeof Core!=='undefined'&&!Core.__cargoV1){const previous=Core.validate;Core.validate=raw=>{previous(raw);if(raw.cargoLog!==undefined)validate(raw.cargoLog);return raw;};Core.__cargoV1=true;}
return {fresh,validate,put,pickup,due,statuses};
});
