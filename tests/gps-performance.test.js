'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

const service=fs.readFileSync('app/src/main/java/mk/workfashion/personal/RouteTrackingService.java','utf8');
const main=fs.readFileSync('app/src/main/java/mk/workfashion/personal/MainActivity.java','utf8');
const ui=fs.readFileSync('app/src/main/assets/v12-ui.js','utf8');

// This branch deliberately replaces continuous GPS points with one-shot stop sampling.
assert.match(service,/STOP_MS=10L\*60L\*1000L/);
assert.match(service,/SAMPLE_INTERVAL_MS=2L\*60L\*1000L/);
assert.match(service,/locationWorker\.post\(/,'Session initialization must run off the main thread');
assert.match(service,/requestSingleUpdate\(LocationManager\.GPS_PROVIDER/);
assert.match(service,/requestSingleUpdate\(LocationManager\.NETWORK_PROVIDER/);
assert.doesNotMatch(service,/requestLocationUpdates\(/,'Do not stream GPS updates all day');
assert.match(main,/"wfag-route-stop"/,'Session completion must not freeze the UI');
assert.match(main,/WFRoute\.invalidateCache/,'Resume must invalidate the GPS snapshot');

// Evaluate the actual routeNativeState() implementation with a mock native bridge.
const begin=ui.indexOf('let routeStateCache='),end=ui.indexOf('\nfunction routeDate(',begin);
assert.ok(begin>=0&&end>begin,'GPS cache function must be present');
let nativeReads=0;
const ctx=vm.createContext({Android:{getRouteLog:()=>{nativeReads++;return JSON.stringify({tracking:true,sessions:[{id:'1'}],savedPlaces:[]});}}});
vm.runInContext(ui.slice(begin,end),ctx);
assert.equal(vm.runInContext('routeNativeState().sessions.length',ctx),1);
assert.equal(vm.runInContext('routeNativeState().tracking',ctx),true);
assert.equal(nativeReads,1,'Repeated rendering should reuse the same native snapshot');
vm.runInContext('routeNativeState(true)',ctx);
assert.equal(nativeReads,2,'Forced lookup should fetch current GPS state');
vm.runInContext('invalidateRouteCache();routeNativeState()',ctx);
assert.equal(nativeReads,3,'GPS events must invalidate cached data');
new vm.Script(ui);
assert.ok(ui.includes('tracking?60000:120000'), 'Cached stop history should refresh only about once per minute');
assert.ok(!ui.includes("if(view!=='home')refreshRoutePanel();checkRoutePrompt();"), 'Avoid automatic GPS panel redraw');
assert.ok(ui.includes("document.visibilityState==='visible')checkRoutePrompt();"), 'Stop prompts remain active');
console.log('WFAG_GPS_PERFORMANCE_OK');
