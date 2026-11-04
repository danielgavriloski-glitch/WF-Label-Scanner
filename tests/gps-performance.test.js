'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const vm=require('node:vm');

const service=fs.readFileSync('app/src/main/java/mk/workfashion/personal/RouteTrackingService.java','utf8');
const main=fs.readFileSync('app/src/main/java/mk/workfashion/personal/MainActivity.java','utf8');
const ui=fs.readFileSync('app/src/main/assets/v12-ui.js','utf8');

// GPS accuracy and recording cadence must not be weakened to make the UI faster.
assert.match(service,/GPS_MIN_TIME_MS=2000L/);
assert.match(service,/MIN_POINT_DISTANCE_M=3f/);
assert.match(service,/locationWorker\.post\(/,'Session initialization must run off the main thread');
assert.match(service,/requestLocationUpdates\(LocationManager\.GPS_PROVIDER,[^;]*locationThread\.getLooper\(\)\)/);
assert.match(service,/requestLocationUpdates\(LocationManager\.NETWORK_PROVIDER,[^;]*locationThread\.getLooper\(\)\)/);
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
assert.ok(ui.includes('tracking?15000:30000'), 'Active route cache should last 15 seconds');
assert.ok(!ui.includes("if(view!=='home')refreshRoutePanel();checkRoutePrompt();"), 'Avoid automatic GPS panel redraw');
assert.ok(ui.includes("document.visibilityState==='visible')checkRoutePrompt();"), 'Stop prompts remain active');
console.log('WFAG_GPS_PERFORMANCE_OK');
