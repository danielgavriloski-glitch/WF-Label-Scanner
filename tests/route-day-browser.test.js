'use strict';
const fs=require('node:fs');
const assert=require('node:assert/strict');
const ui=fs.readFileSync('app/src/main/assets/v12-ui.js','utf8');
const app=fs.readFileSync('app/src/main/assets/app.js','utf8');
const native=fs.readFileSync('app/src/main/java/mk/workfashion/personal/MainActivity.java','utf8');
const storage=fs.readFileSync('app/src/main/java/mk/workfashion/personal/RouteTrackingService.java','utf8');
const gradle=fs.readFileSync('app/build.gradle','utf8');
for(const [name,source] of [['app.js',app],['v12-ui.js',ui]]){
  try{new Function(source);}catch(e){throw new Error(name+' JavaScript syntax: '+e.message);}
}
assert.match(app,/\['routes','route','Мапа'\]/,'Map must be in main bottom navigation');
assert.match(app,/view==='routes'\)renderRoutes\(\)/,'Render dispatch must show the map page');
assert.match(ui,/window\.renderRoutes=renderRoutes/,'Route view must be globally callable');
assert.match(ui,/function routeDayCardMarkup\(/,'Main page must have route day selector');
assert.match(ui,/hero\.insertAdjacentHTML\('afterend'/,'Today route history should be visible below hero');
assert.match(ui,/data-gps-offset/,'Date browsing with arrows');
assert.match(ui,/data-gps-date/,'Scrollable dates');
assert.match(ui,/touchstart/,'Swipe support');
assert.match(ui,/touchend/,'Swipe support');
assert.match(ui,/routeSvg\(\[s\]\)/,'Inline GPS preview');
assert.match(ui,/Android\.openRouteSessionMap/,'Real offline map opened on tap');
assert.match(ui,/routeStopMarkup\(p,i\+1\)/,'Stop detail in selected date');
assert.match(ui,/id="gpsAllHistory"/,'History must show directly on map page');
assert.match(ui,/gpsArchiveImport/,'History import for earlier Preview apps');
assert.match(native,/openRouteArchive\(/,'Android import picker');
assert.match(storage,/importArchive\(/,'Merge into existing GPS history');
assert.match(storage,/wfag-routes\.json/,'Persistent original GPS file');
assert.match(gradle,/applicationIdSuffix '\.preview2'/,'Use separate package, no modification of original app');
assert.ok(Number(gradle.match(/versionCode ([0-9]+)/)?.[1]||0)>=20,'New version code must be at least 20');
console.log('WFAG_ROUTE_DAY_BROWSER_OK');
