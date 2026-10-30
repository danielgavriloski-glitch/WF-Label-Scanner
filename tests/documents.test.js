'use strict';
const assert=require('assert'),Core=require('../app/src/main/assets/core.js'),W=require('../app/src/main/assets/documents-core.js');
let log=W.fresh(),r=W.putWarehouse(log,{name:'Магацин Охрид',address:'Адреса'});log=r.log;const wid=r.warehouse.id;
r=W.putVisit(log,{date:'2024-01-01',time:'08:35',warehouseId:wid,warehouseName:'Магацин Охрид',action:'delivery',note:'Однесов 3 пакети',stopId:'',sessionId:''});log=r.log;const vid=r.visit.id;
assert.equal(W.filter(log,{from:'2024-01-01',to:'2024-12-31'}).length,1);assert.equal(W.filter(log,{query:'3 пакети'}).length,1);assert.equal(W.filter(log,{query:'ОХРИД'}).length,1);
const d={id:'doc_first',fileId:'0123456789abcdef0123456789abcdef.jpg',name:'Испратница.jpg',mime:'image/jpeg',bytes:20000,sha256:'a'.repeat(64)};log.visits[0].documents.push(d);W.validate(log);
r=W.putVisit(log,{id:vid,note:'Исправена белешка'});log=r.log;assert.deepEqual(log.visits[0].documents,[d]);
r=W.putWarehouse(log,{id:wid,name:'Магацин Охрид ново име'});log=r.log;assert.equal(W.filter(log,{query:'ново име'}).length,1);assert.equal(log.visits[0].warehouseId,wid);
r=W.putVisit(log,{date:'2026-10-26',time:'10:00',warehouseId:wid,warehouseName:'Магацин Охрид',action:'both',note:'Зедов и однесов',stopId:'stop_one',sessionId:'session_one'});log=r.log;
const same=W.putVisit(log,{date:'2026-10-26',time:'10:00',warehouseId:wid,warehouseName:'Магацин Охрид',action:'pickup',note:'Уредено',stopId:'stop_one',sessionId:'session_one'});assert.equal(same.log.visits.length,2);assert.equal(same.visit.id,r.visit.id);
const removed=W.removeDocument(log,vid,'doc_first');assert.equal(removed.visits[0].documents.length,0);assert.equal(log.visits[0].documents.length,1);assert.throws(()=>W.removeDocument(log,vid,'missing'));
const replacement={...d,fileId:'abcdef0123456789abcdef0123456789.png',name:'Нова слика.png',mime:'image/png',sha256:'b'.repeat(64)};const replaced=W.replaceDocument(log,vid,'doc_first',replacement);assert.equal(replaced.visits[0].documents[0].name,'Нова слика.png');assert.equal(replaced.visits[0].documents[0].id,'doc_first');assert.equal(log.visits[0].documents[0].name,'Испратница.jpg');
assert.throws(()=>W.removeWarehouse(log,wid));const empty=W.putWarehouse(log,{name:'Празен магацин'});assert.equal(W.removeWarehouse(empty.log,empty.warehouse.id).warehouses.length,log.warehouses.length);
const saved=JSON.stringify(log);assert.deepEqual(W.validate(JSON.parse(saved)),log);log.visits[0].deleted=true;assert.equal(W.filter(log).length,1);assert.equal(W.filter(log,{deleted:true}).length,1);assert.equal(log.visits[0].documents.length,1);log.visits[0].deleted=false;
const state=Core.fresh();state.settings.trackStart='2026-01-01';state.warehouseLog=log;assert(Core.validate(state));assert.equal(state.warehouseLog.visits[0].date,'2024-01-01');
for(const change of [x=>x.visits[0].date='2024-02-30',x=>x.visits[0].documents[0].sha256='bad',x=>x.visits[0].warehouseId='missing',x=>x.visits[0].documents[0].fileId='../secret',x=>x.visits.push({...x.visits[1],id:'duplicate_stop'}),x=>x.visits[0].documents[0].bytes=26*1024*1024]){const bad=JSON.parse(JSON.stringify(log));change(bad);assert.throws(()=>W.validate(bad));}
assert.throws(()=>W.filter(log,{from:'2026-01-01',to:'2024-01-01'}));assert.equal(W.filter(log,{action:'both'}).length,1);
const old=Core.fresh();const before=JSON.stringify(old);Core.validate(old);assert.equal(JSON.stringify(old),before);
console.log('WFAG_DOCUMENTS_OK: old dates, warehouse reuse and rename, pickup/delivery, stop deduplication, attachments, recovery, long-term search, validation and legacy state');
