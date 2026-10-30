(function(root,factory){const api=factory(typeof module==='object'&&module.exports?require('./core.js'):root.Core);if(typeof module==='object'&&module.exports)module.exports=api;else root.Warehouse=api;})(typeof globalThis!=='undefined'?globalThis:this,function(Core){
'use strict';
const actions={delivery:'Растоварив',pickup:'Зедов',both:'Растоварив и зедов',stop:'Само застанав'};
const copy=o=>JSON.parse(JSON.stringify(o)),id=()=>('wh_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,12));
const validId=s=>typeof s==='string'&&/^[-a-zA-Z0-9_]{1,100}$/.test(s);
const text=(s,max)=>typeof s==='string'&&s.length<=max;
function fresh(){return {version:1,promptFrom:Date.now(),warehouses:[],visits:[]};}
function validate(log){
 if(!log||log.version!==1||!Array.isArray(log.warehouses)||!Array.isArray(log.visits)||log.warehouses.length>10000||log.visits.length>100000)throw Error('Невалидна евиденција на магацини.');
 const places=new Set(),visits=new Set(),files=new Set(),stops=new Set();
 for(const w of log.warehouses){if(!w||!validId(w.id)||places.has(w.id)||!text(w.name,200)||!w.name.trim()||!text(w.address||'',500)||!text(w.note||'',2000))throw Error('Провери го името на магацинот.');places.add(w.id);if(w.lat!==null&&w.lat!==undefined&&(!Number.isFinite(w.lat)||Math.abs(w.lat)>90||!Number.isFinite(w.lon)||Math.abs(w.lon)>180))throw Error('Невалидна локација.');}
 for(const v of log.visits){
  if(!v||!validId(v.id)||visits.has(v.id)||!Core.dateValid(v.date)||!/^([01]\d|2[0-3]):[0-5]\d$/.test(v.time)||!Object.hasOwnProperty.call(actions,v.action)||!text(v.note,4000)||!text(v.warehouseName,200)||!text(v.stopId||'',100)||!text(v.sessionId||'',100)||!Array.isArray(v.documents)||v.documents.length>100)throw Error('Провери го датумот, магацинот и описот.');
  if(v.warehouseId&&!places.has(v.warehouseId))throw Error('Магацинот од записот не постои.');
  if(v.stopId){if(stops.has(v.stopId))throw Error('Застанувањето веќе има запис.');stops.add(v.stopId);}visits.add(v.id);
  for(const d of v.documents){if(!d||!validId(d.id)||files.has(d.id)||!/^[-a-zA-Z0-9_.]{1,100}$/.test(d.fileId)||!text(d.name,250)||!text(d.mime,150)||!Number.isSafeInteger(d.bytes)||d.bytes<1||d.bytes>25*1024*1024||!(/^[a-f0-9]{64}$/i.test(d.sha256)))throw Error('Невалиден приложен документ.');files.add(d.id);}
 }
 return log;
}
function putWarehouse(log,input){const out=copy(log||fresh());let w=input.id?out.warehouses.find(w=>w.id===input.id):null;
 if(input.id&&!w)throw Error('Магацинот не е пронајден.');
 const name=String(input.name||'').trim();if(!name)throw Error('Внеси име на магацинот.');
 if(!w){w={id:id(),lat:null,lon:null,nativeId:'',address:'',note:'',archived:false};out.warehouses.push(w);}
 Object.assign(w,input,{id:w.id,name});validate(out);return {log:out,warehouse:w};
}
function putVisit(log,input){const out=copy(log||fresh());let v=input.id?out.visits.find(v=>v.id===input.id):input.stopId?out.visits.find(v=>v.stopId===input.stopId):null;
 if(input.id&&!v)throw Error('Записот не е пронајден.');
 if(!v){v={id:id(),documents:[],deleted:false,createdAt:new Date().toISOString()};out.visits.push(v);}
 const docs=v.documents;Object.assign(v,input,{id:v.id,documents:docs,updatedAt:new Date().toISOString()});validate(out);return {log:out,visit:v};
}
function filter(log,{query='',from='',to='',warehouseId='',action='',deleted=false}={}){if(from&&!Core.dateValid(from)||to&&!Core.dateValid(to)||from&&to&&from>to)throw Error('Провери го периодот.');const q=query.trim().toLocaleLowerCase('mk');return log.visits.filter(v=>!!v.deleted===!!deleted&&(!from||v.date>=from)&&(!to||v.date<=to)&&(!warehouseId||v.warehouseId===warehouseId)&&(!action||v.action===action)&&(!q||[v.warehouseName,(log.warehouses.find(w=>w.id===v.warehouseId)||{}).name,v.note,actions[v.action],...v.documents.map(d=>d.name)].join(' ').toLocaleLowerCase('mk').includes(q))).sort((a,b)=>(b.date+b.time).localeCompare(a.date+a.time));}
function removeDocument(log,visitId,documentId){const out=copy(log);const visit=out.visits.find(v=>v.id===visitId);if(!visit)throw Error('Записот не е пронајден.');const before=visit.documents.length;visit.documents=visit.documents.filter(d=>d.id!==documentId);if(before===visit.documents.length)throw Error('Документот не е пронајден.');validate(out);return out;}
function replaceDocument(log,visitId,documentId,replacement){const out=copy(log);const visit=out.visits.find(v=>v.id===visitId);const index=visit&&visit.documents.findIndex(d=>d.id===documentId);if(index===undefined||index<0)throw Error('Документот не е пронајден.');visit.documents[index]={...replacement,id:documentId};validate(out);return out;}
function removeWarehouse(log,warehouseId){const out=copy(log);if(out.visits.some(v=>v.warehouseId===warehouseId))throw Error('Магацинот има поврзани записи.');const before=out.warehouses.length;out.warehouses=out.warehouses.filter(w=>w.id!==warehouseId);if(before===out.warehouses.length)throw Error('Магацинот не е пронајден.');validate(out);return out;}
const api={actions,fresh,validate,putWarehouse,putVisit,filter,removeDocument,replaceDocument,removeWarehouse,id};
if(Core&&!Core.__warehouseV1){const previous=Core.validate;Core.validate=raw=>{previous(raw);if(raw.warehouseLog!==undefined)validate(raw.warehouseLog);return raw;};Core.__warehouseV1=true;}
return api;
});
