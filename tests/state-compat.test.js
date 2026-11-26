'use strict';
const assert=require('node:assert/strict');
const test=require('node:test');
const StateCompat=require('../app/src/main/assets/state-compat.js');

function current(){
  return {appId:'workfashion-personal-time',schemaVersion:1,settings:{name:'current'},records:{'2026-11-02':{type:'work'}},
    trips:[{id:'current-trip'}],budgets:{2026:{annual:20}},audit:[{text:'current'}],
    pickupLog:{items:[{id:'goods-1',what:'metal'}]},warehouseLog:{visits:[{id:'doc-1'}]},
    vehicleLog:{fuel:[{id:'fuel-1'}],tires:[{id:'tire-1'}],serviceReminders:[{id:'service-1'}]},
    notificationHistory:{notifications:[{id:'notice-1'}]},customModule:{value:'preserve'}};
}

test('Attendance-only backup preserves all newer optional modules without mutating sources',()=>{
  const previous=current();
  const incoming={appId:previous.appId,schemaVersion:1,settings:{name:'restored'},records:{},trips:[],budgets:{},audit:[]};
  const oldText=JSON.stringify(previous),incomingText=JSON.stringify(incoming);
  const restored=StateCompat.preserve(previous,incoming);
  for(const key of ['pickupLog','warehouseLog','vehicleLog','notificationHistory','customModule'])assert.deepEqual(restored[key],previous[key]);
  assert.deepEqual(restored.records,{});
  assert.deepEqual(restored.trips,[]);
  assert.equal(restored.settings.name,'restored');
  restored.pickupLog.items[0].what='changed copy';
  assert.equal(JSON.stringify(previous),oldText);
  assert.equal(JSON.stringify(incoming),incomingText);
});

test('Explicit module contents replace old contents, including deliberately empty lists',()=>{
  const previous=current();
  const restored=StateCompat.preserve(previous,{pickupLog:{items:[]},warehouseLog:{visits:[{id:'restored-doc'}]},notificationHistory:{notifications:[]}});
  assert.deepEqual(restored.pickupLog,{items:[]});
  assert.deepEqual(restored.warehouseLog,{visits:[{id:'restored-doc'}]});
  assert.deepEqual(restored.notificationHistory,{notifications:[]});
  assert.deepEqual(restored.vehicleLog,previous.vehicleLog);
});

test('Older vehicle backups preserve omitted newer sections while restoring included sections',()=>{
  const previous=current();
  const incoming={vehicleLog:{fuel:[{id:'restored-fuel'}],tires:[]}};
  const restored=StateCompat.preserve(previous,incoming);
  assert.deepEqual(restored.vehicleLog.fuel,[{id:'restored-fuel'}]);
  assert.deepEqual(restored.vehicleLog.tires,[]);
  assert.deepEqual(restored.vehicleLog.serviceReminders,[{id:'service-1'}]);
  restored.vehicleLog.serviceReminders[0].id='copy';
  assert.equal(previous.vehicleLog.serviceReminders[0].id,'service-1');
  assert.deepEqual(incoming,{vehicleLog:{fuel:[{id:'restored-fuel'}],tires:[]}});
});

test('First restore produces an independent copy and invalid backup roots are rejected',()=>{
  const incoming={records:{},pickupLog:{items:[]}};
  const restored=StateCompat.preserve(null,incoming);
  restored.pickupLog.items.push({id:'new'});
  assert.deepEqual(incoming.pickupLog.items,[]);
  for(const value of [null,[],true,'backup'])assert.throws(()=>StateCompat.preserve(current(),value),/Невалиден бекап/);
});
