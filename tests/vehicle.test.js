'use strict';
const assert=require('node:assert/strict');
const Core=require('../app/src/main/assets/v12-core.js');
const V=require('../app/src/main/assets/vehicle-core.js');
let checks=0;
function test(name,fn){fn();checks++;console.log('PASS',name);}
const near=(a,b)=>assert.ok(Math.abs(a-b)<1e-8,`${a} != ${b}`);
const put=(l,k,data)=>V.put(l,k,data);
function fuelFixture(){
 let l=V.fresh();
 l=put(l,'readings',{at:'2026-10-01T08:00',odo:10000});
 l=put(l,'averages',{at:'2026-10-01T08:00',l100:'10,0'});
 l=put(l,'fills',{at:'2026-10-01T09:00',odo:10000,litres:60,full:true});
 l=put(l,'fills',{at:'2026-10-02T09:00',odo:10300,litres:30,full:false,cost:2250});
 l=put(l,'fills',{at:'2026-10-03T09:00',odo:10500,litres:20,full:true,cost:1500});
 return l;
}
test('No manufactured odometer or average on setup',()=>{
 const l=V.fresh();assert.equal(V.latest(l),null);assert.equal(V.rateAt(l,'2026-10-01T00:00'),null);assert.equal(V.summary(l).measuredL100,null);
 assert.equal(V.number('0','km'),0);assert.equal(V.number('','km',true),null);assert.throws(()=>V.number('','km'));assert.throws(()=>V.number('-2','km'));assert.throws(()=>V.number('1,2,3','L'));
});
test('Initial full tank is a baseline, not consumed fuel',()=>{
 const l=put(V.fresh(),'fills',{at:'2026-10-01T09:00',odo:0,litres:60,full:true});
 const a=V.fuelAnalysis(l);assert.equal(a[0].cycle,null);assert.equal(a[0].status,'baseline');assert.equal(V.summary(l).measuredL100,null);
});
test('Partial fill has distance and estimate, never a fabricated measured ratio',()=>{
 const l=fuelFixture(),a=V.fuelAnalysis(l);
 assert.equal(a[1].kmSincePrevious,300);assert.equal(a[1].cycle,null);near(a[1].estimatedLitres,30);
 near(a[2].cycle.litres,50);near(a[2].cycle.km,500);near(a[2].cycle.l100,10);
 const s=V.summary(l);near(s.fuelAdded,110);near(s.measuredLitres,50);near(s.measuredKm,500);near(s.fuelCost,3750);
});
test('Several partial fills accumulate from the last full reference',()=>{
 let l=put(V.fresh(),'fills',{at:'2026-10-01T08:00',odo:1000,litres:70,full:true});
 l=put(l,'fills',{at:'2026-10-02T08:00',odo:1100,litres:10,full:false});
 l=put(l,'fills',{at:'2026-10-03T08:00',odo:1200,litres:8,full:false});
 l=put(l,'fills',{at:'2026-10-04T08:00',odo:1300,litres:12,full:true});
 near(V.fuelAnalysis(l)[3].cycle.l100,10);near(V.summary(l).measuredLitres,30);
});
test('Editing an older fill recomputes its cycle and weighted totals',()=>{
 let l=fuelFixture();
 l=put(l,'fills',{at:'2026-10-05T09:00',odo:10700,litres:20,full:true});
 const partial=l.fills[1];l=put(l,'fills',{...partial,litres:20});
 const a=V.fuelAnalysis(l);near(a[2].cycle.l100,8);near(a[3].cycle.l100,10);
 near(V.summary(l).measuredL100,60/700*100);assert.notEqual(V.summary(l).measuredL100,9);
 assert.equal(l.audit[l.audit.length-1].before.litres,30);assert.equal(l.audit[l.audit.length-1].after.litres,20);
});
test('Backdated fill updates its enclosing interval',()=>{
 let l=put(V.fresh(),'fills',{at:'2026-10-01T08:00',odo:1000,litres:50,full:true});
 l=put(l,'fills',{at:'2026-10-03T08:00',odo:1400,litres:20,full:true});
 l=put(l,'fills',{at:'2026-10-02T08:00',odo:1200,litres:20,full:false});
 near(V.fuelAnalysis(l)[2].cycle.l100,10);
});
test('No reference, zero distance and invalid chronological odometer are handled',()=>{
 let l=put(V.fresh(),'fills',{at:'2026-10-01T08:00',odo:1000,litres:10,full:false});
 assert.equal(V.fuelAnalysis(l)[0].cycle,null);
 l=put(l,'fills',{at:'2026-10-02T08:00',odo:1000,litres:10,full:true});
 l=put(l,'fills',{at:'2026-10-03T08:00',odo:1000,litres:2,full:true});
 assert.equal(V.fuelAnalysis(l)[2].cycle.l100,null);assert.equal(V.summary(l).measuredL100,null);
 assert.throws(()=>put(l,'readings',{at:'2026-10-04T08:00',odo:999}),/намалува/);
 assert.throws(()=>put(l,'fills',{at:'2026-10-04T08:00',odo:1000,litres:0,full:true}));
});
test('Period measurements retain the whole full-to-full coverage',()=>{
 const s=V.summary(fuelFixture(),'2026-10-03','2026-10-03');
 near(s.fuelAdded,20);near(s.measuredLitres,50);near(s.measuredKm,500);near(s.measuredL100,10);
 assert.equal(s.cycles[0].from,'2026-10-01T09:00');
 assert.throws(()=>V.summary(fuelFixture(),'2026-11-01','2026-10-01'));
});
test('Tire mileage survives reinstallation in the next season',()=>{
 let l=put(V.fresh(),'tireChanges',{at:'2026-09-01T08:00',odo:9000,setId:'summer'});
 l=put(l,'tireChanges',{at:'2026-10-01T08:00',odo:10000,setId:'winter'});
 l=put(l,'tireChanges',{at:'2026-10-04T08:00',odo:10600,setId:'summer'});
 l=put(l,'readings',{at:'2026-10-05T08:00',odo:10800});
 const sets=V.tires(l),summer=sets.find(s=>s.id==='summer'),winter=sets.find(s=>s.id==='winter');
 near(summer.totalKm,1200);near(summer.currentKm,200);assert.equal(summer.active,true);near(winter.totalKm,600);assert.equal(winter.active,false);
 near(V.tires(l,'2026-10-02T23:59').find(s=>s.id==='summer').totalKm,1000);
 const previous=l.tireChanges[1];l=put(l,'tireChanges',{...previous,odo:10100});
 near(V.tires(l).find(s=>s.id==='summer').totalKm,1300);near(V.tires(l).find(s=>s.id==='winter').totalKm,500);
});
test('Replacement tire set has its own lifetime mileage',()=>{
 let l=put(V.fresh(),'tireSets',{name:'Нови летни 2027',season:'summer'}),set=l.tireSets[l.tireSets.length-1];
 l=put(l,'tireChanges',{at:'2026-10-01T08:00',odo:0,setId:set.id});
 l=put(l,'readings',{at:'2026-10-02T08:00',odo:100});
 near(V.tires(l).find(s=>s.id===set.id).totalKm,100);near(V.tires(l).find(s=>s.id==='summer').totalKm,0);
 assert.throws(()=>put(l,'tireChanges',{at:'2026-10-01T08:00',odo:0,setId:'winter'}),/ист датум/);
});
test('Odometer trips and GPS distance are not added to the odometer twice',()=>{
 let l=put(V.fresh(),'readings',{at:'2026-10-01T08:00',odo:10000});
 l=put(l,'averages',{at:'2026-10-01T08:00',l100:10});
 l=put(l,'trips',{at:'2026-10-01T10:00',endAt:'2026-10-01T12:00',startOdo:10000,endOdo:10300,km:299,from:'Охрид',to:'Скопје'});
 l=put(l,'trips',{at:'2026-10-02T10:00',endAt:'2026-10-02T12:00',startOdo:null,endOdo:null,km:100,source:'gps',sourceId:'wfag:trip1'});
 near(V.latest(l).odo,10300);near(V.summary(l).tripKm,400);assert.equal(V.summary(l).tripCount,2);near(V.summary(l).estimatedTripLitres,40);
 assert.throws(()=>put(l,'trips',{...l.trips[1],id:undefined}),/веќе поврзано/);
 assert.throws(()=>put(l,'trips',{at:'2026-10-03T10:00',endAt:'2026-10-03T11:00',startOdo:11000,endOdo:null,km:100}));
});
test('Average changes are effective-dated, with measurements unchanged',()=>{
 let l=fuelFixture();
 l=put(l,'averages',{at:'2026-10-02T00:00',l100:12});
 near(V.rateAt(l,'2026-10-01T12:00'),10);near(V.rateAt(l,'2026-10-02T12:00'),12);near(V.summary(l).measuredL100,10);
});
test('Average predicts fuel from odometer readings without a logged trip',()=>{
 let l=put(V.fresh(),'readings',{at:'2026-10-01T08:00',odo:10000});
 l=put(l,'averages',{at:'2026-10-01T08:00',l100:12});
 l=put(l,'readings',{at:'2026-10-02T08:00',odo:10500});
 const s=V.summary(l);near(s.estimatedOdoKm,500);near(s.estimatedOdoLitres,60);assert.equal(s.estimatedTripLitres,null);assert.equal(s.measuredL100,null);
});
test('Deleting and restoring retains records and recomputes derived values',()=>{
 const l=fuelFixture(),row=l.fills[1];let changed=V.setDeleted(l,'fills',row.id,true);
 assert.equal(changed.fills.length,3);near(V.summary(changed).measuredL100,4);
 changed=V.setDeleted(changed,'fills',row.id,false);near(V.summary(changed).measuredL100,10);
 assert.equal(l.fills[1].deleted,false);
});
test('Legacy state and existing attendance data survive the additive vehicle field',()=>{
 const original=Core.fresh(new Date('2026-10-01T08:00:00Z'));original.settings.name='Даниел';original.records['2026-10-02']={type:'off',pause:0,note:'Зачувај'};
 assert.equal(Core.validate(original),original);assert.equal(original.vehicleLog,undefined);
 const s=JSON.parse(JSON.stringify(original));s.vehicleLog=fuelFixture();Core.validate(s);
 const restored=Core.validate(JSON.parse(JSON.stringify(s)));assert.deepEqual(restored.records,original.records);assert.deepEqual(restored.vehicleLog,s.vehicleLog);
 assert.throws(()=>Core.validate({...s,vehicleLog:null}));
});
test('GPS distance uses the recorded value, with a geometry fallback',()=>{
 near(V.gpsKm({distanceM:12345,points:[]}),12.345);
 const n=V.gpsKm({points:[{lat:41,lon:20,at:0},{lat:41.001,lon:20,at:10000}]});assert.ok(n>.1&&n<.12);
});
test('Automatic GPS linking starts at opt-in, deduplicates, and respects removal',()=>{
 let l=put(V.fresh(),'readings',{at:'2026-10-01T08:00',odo:10000});
 l.autoLinkFrom=Core.timestamp('2026-10-02','08:00');
 const sessions=[{id:'old',startedAt:Core.timestamp('2026-10-01','09:00'),endedAt:Core.timestamp('2026-10-01','10:00'),distanceM:10000},{id:'new',tripId:'work1',startedAt:Core.timestamp('2026-10-02','09:00'),endedAt:Core.timestamp('2026-10-02','10:00'),distanceM:50000}];
 let result=V.autoLink(l,sessions);assert.equal(result.added,1);near(V.estimatedOdometer(result.log).odo,10050);near(V.latest(result.log).odo,10000);
 assert.equal(V.autoLink(result.log,sessions).added,0);
 result.log=V.setDeleted(result.log,'trips',result.log.trips[0].id,true);assert.equal(V.autoLink(result.log,sessions).added,0);
});
console.log('WFAG_VEHICLE_OK: '+checks+' scenarios');
