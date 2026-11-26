'use strict';
// Full application smoke check. Uses synthetic data and a mock Android bridge;
// native storage/notification delivery is covered by Android instrumentation.
const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const assert=require('node:assert/strict');
const {chromium}=require(process.env.WFAG_PLAYWRIGHT_MODULE||'playwright');
const Core=require('../app/src/main/assets/core.js');
require('../app/src/main/assets/v12-core.js');
const Vehicle=require('../app/src/main/assets/vehicle-core.js');
const Warehouse=require('../app/src/main/assets/documents-core.js');
const Pickups=require('../app/src/main/assets/pickups-core.js');

const project=path.resolve(__dirname,'..');
const assets=path.join(project,'app/src/main/assets');
const delivery=path.join(project,'delivery');
const now=Date.parse('2026-11-26T21:00:00+01:00');
const day='2026-11-26';
const shift=start=>({type:'work',start,end:'16:00',overnight:false,pause:0,pausePaid:true,note:'Synthetic UI fixture'});
const initial=Core.fresh(new Date(now));
initial.settings.name='UI проверка';initial.settings.trackStart='2026-10-01';
initial.records['2026-11-25']=shift('08:30');
initial.records['2026-11-24']=shift('09:30');
initial.vehicleLog=Vehicle.fresh();
initial.vehicleLog.services.push({id:'van_ui_service',at:'2026-11-20T10:00',title:'Масло и филтри',odo:100000,nextOdo:110000,nextDate:'2026-11-30',cost:4500,note:'Synthetic service fixture',deleted:false});
const visit=Warehouse.putVisit(null,{date:'2026-11-20',time:'12:30',warehouseId:'',warehouseName:'Архивски магацин',action:'pickup',note:'Synthetic existing document',stopId:'',sessionId:''});
visit.visit.documents.push({id:'ui_document',fileId:'ui_document.jpg',name:'Испратница.jpg',mime:'image/jpeg',bytes:123,sha256:'a'.repeat(64)});
initial.warehouseLog=visit.log;
const pending=Pickups.put(null,{from:'Јуба · проверка',goods:'3 пакети текстил',address:'Скопје',note:'Synthetic pickup fixture'},new Date(now-86400000));
pending.item.id='pickup_ui_notice';initial.pickupLog=pending.log;
const cargo=(id,status,extra={})=>({id,status,company:'Стар товар · проверка',goods:'Текстил',location:'Скопје',dueDate:day,packages:10,pickedPackages:status==='partial'?3:10,weightKg:120.5,notes:'Белешка '.repeat(500),documents:['ui_document'],warehouseId:'',visitIds:[],createdAt:'2026-11-19T10:00:00Z',updatedAt:'2026-11-20T10:00:00Z',...extra});
initial.cargoLog={version:1,shipments:[cargo('cargo_ui_partial','partial'),cargo('cargo_ui_done','delivered',{visitIds:[visit.visit.id]})]};
Core.validate(initial);
const history={version:1,notifications:[
 {id:'notice_ui_pickup',type:'pickup',title:'Роба за подигање',body:'Јуба · проверка\n3 пакети текстил\nЦелосната порака останува читлива и по затворање.\n<b>Ова е текст, а не HTML.</b>',createdAt:now-5000,read:false,targetId:'pickup_ui_notice'},
 {id:'notice_ui_service',type:'service',title:'Потсетник за сервис',body:'Масло и филтри · следен сервис до 30.11.2026.',createdAt:now-10000,read:false,targetId:'van_ui_service'}
]};

function serve(){
 const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png','.svg':'image/svg+xml'};
 const server=http.createServer((req,res)=>{
  try{
   const url=new URL(req.url,'http://127.0.0.1');
   if(url.pathname==='/favicon.ico'){res.writeHead(204);res.end();return;}
   const relative=decodeURIComponent(url.pathname==='/'?'/index.html':url.pathname);
   const file=path.resolve(assets,'.'+relative);
   if(!file.startsWith(assets+path.sep)){res.writeHead(403);res.end();return;}
   fs.readFile(file,(error,bytes)=>{if(error){res.writeHead(404);res.end('Not found');return;}res.writeHead(200,{'content-type':types[path.extname(file)]||'application/octet-stream','cache-control':'no-store'});res.end(bytes);});
  }catch(error){res.writeHead(400);res.end('Bad request');}
 });
 return new Promise(resolve=>server.listen(0,'127.0.0.1',()=>resolve(server)));
}

async function run(){
 fs.mkdirSync(delivery,{recursive:true});
 const summary={ok:false,fixture:'synthetic-only',checks:[],javascriptErrors:[],consoleErrors:[],failedRequests:[]};
 let server,browser,page;
 const passed=(name,details={})=>summary.checks.push({name,passed:true,...details});
 try{
  server=await serve();
  const options={headless:true,args:['--no-sandbox','--disable-dev-shm-usage']};
  if(process.env.WFAG_CHROME_PATH)options.executablePath=process.env.WFAG_CHROME_PATH;
  browser=await chromium.launch(options);
  const context=await browser.newContext({viewport:{width:390,height:844},deviceScaleFactor:1,timezoneId:'Europe/Skopje'});
  await context.addInitScript(({seed,notices,stamp})=>{
   const NativeDate=Date;
   window.__uiNow=stamp;
   window.Date=class extends NativeDate{constructor(...args){super(...(args.length?args:[window.__uiNow]));}static now(){return window.__uiNow;}};
   if(localStorage.getItem('wfag-ui-state')===null)localStorage.setItem('wfag-ui-state',JSON.stringify(seed));
   if(localStorage.getItem('wfag-ui-notices')===null)localStorage.setItem('wfag-ui-notices',JSON.stringify(notices));
   if(localStorage.getItem('wfag-ui-gps-starts')===null)localStorage.setItem('wfag-ui-gps-starts','0');
   window.__uiCalls={load:0,save:0,gpsStop:0,routeRead:0,permissionRequest:0};
   window.__uiWrites={app:0,nav:0};
   const html=Object.getOwnPropertyDescriptor(Element.prototype,'innerHTML');
   Object.defineProperty(Element.prototype,'innerHTML',{...html,set(value){if(this.id==='app'||this.id==='nav')window.__uiWrites[this.id]++;return html.set.call(this,value);}});
   const mark=id=>{
    const h=JSON.parse(localStorage.getItem('wfag-ui-notices'));
    h.notifications.forEach(row=>{if(!id||row.id===id)row.read=true;});
    localStorage.setItem('wfag-ui-notices',JSON.stringify(h));
    queueMicrotask(()=>{if(window.onNotificationHistoryChanged)window.onNotificationHistoryChanged();});
    return true;
   };
   window.Android={
    loadState(){window.__uiCalls.load++;return localStorage.getItem('wfag-ui-state');},
    saveState(raw){JSON.parse(raw);window.__uiCalls.save++;localStorage.setItem('wfag-ui-state',raw);return true;},
    getLastSaveError(){return '';},
    getRouteLog(){window.__uiCalls.routeRead++;return JSON.stringify({tracking:false,sessions:[],savedPlaces:[]});},
    startRouteTracking(){localStorage.setItem('wfag-ui-gps-starts',String(Number(localStorage.getItem('wfag-ui-gps-starts'))+1));return true;},
    stopRouteTracking(){window.__uiCalls.gpsStop++;return true;},
    pickupNotificationsEnabled(){return true;},
    requestPickupNotifications(){window.__uiCalls.permissionRequest++;},
    getNotificationHistory(){return localStorage.getItem('wfag-ui-notices');},
    markNotificationRead:mark,
    markAllNotificationsRead(){return mark('');},
    documentsAvailable(){return true;},
    hasStagedArchive(){return false;},
    keepBeforeRestore(raw){localStorage.setItem('wfag-ui-before-restore',raw);return true;},
    discardArchive(){return true;}
   };
  },{seed:initial,notices:history,stamp:now});
  page=await context.newPage();
  page.setDefaultTimeout(15000);
  page.on('pageerror',error=>summary.javascriptErrors.push(error.message));
  page.on('console',message=>{if(message.type()==='error')summary.consoleErrors.push(message.text());});
  page.on('requestfailed',request=>summary.failedRequests.push({url:request.url(),error:request.failure()?.errorText}));
  page.on('dialog',dialog=>dialog.accept());
  const origin='http://127.0.0.1:'+server.address().port;
  await page.goto(origin,{waitUntil:'load'});
  await page.waitForFunction(()=>window.WFApp&&window.WFPerformance&&document.getElementById('liveWorkClock')&&document.getElementById('notificationBell'));
  const boot=await page.evaluate(()=>({writes:window.__uiWrites,calls:window.__uiCalls,state:WFApp.getState()}));
  assert.equal(boot.writes.app,1,'startup renders the main content once');
  assert.equal(boot.writes.nav,1,'startup renders navigation once');
  assert.equal(boot.state.cargoLog.shipments.length,2);
  assert.equal(boot.state.pickupLog.items.length,3);
  assert.equal(boot.state.pickupLog.items.find(x=>x.legacyCargoId==='cargo_ui_partial').pickedPackages,3);
  assert.equal(boot.state.pickupLog.items.find(x=>x.legacyCargoId==='cargo_ui_partial').note.length,4000);
  assert.equal(boot.state.warehouseLog.visits.length,1);
  assert.equal(boot.state.warehouseLog.visits[0].documents[0].fileId,'ui_document.jpg');
  passed('single-boot-and-legacy-preservation',{mainRenders:boot.writes.app,navigationRenders:boot.writes.nav,goods:3,existingDocuments:1});

  await page.locator('.hero [data-day]').click();
  await page.locator('#editStart').fill('08:30');
  await page.locator('#dayForm button[type="submit"]').click();
  await page.locator('#modal').waitFor({state:'hidden'});
  assert.equal(await page.evaluate(()=>WFApp.getState().records['2026-11-26'].start),'08:30');
  await page.locator('#goOnTrip').click();
  assert.equal(await page.locator('#tripWorkStart').inputValue(),'08:30');
  assert.equal(await page.locator('#tripWorkStart').isDisabled(),true);
  await page.locator('#tripDeparture').fill('10:30');
  await page.locator('#tripArrivalDate').fill(day);
  await page.locator('#tripArrival').fill('20:00');
  await page.locator('#tripNote').fill('ui-main-day');
  assert.match(await page.locator('#tripDuration').innerText(),/11:30/);
  await page.locator('#tripSubmit').click();
  await page.locator('#modal').waitFor({state:'hidden'});
  assert.equal(await page.locator('#liveWorkClock').innerText(),'11:30');
  const mainDay=await page.evaluate(()=>({row:Core.day(state,'2026-11-26'),trip:state.trips.find(t=>t.note==='ui-main-day')}));
  assert.equal(mainDay.row.worked,690);assert.equal(mainDay.trip.workStart,'08:30');assert.equal(mainDay.row.end,'20:00');
  passed('manual-start-continuous-workday',{start:'08:30',departure:'10:30',arrival:'20:00',workedMinutes:690});

  await page.locator('#goOnTrip').click();
  await page.locator('#tripDate').fill('2026-11-25');
  await page.locator('#tripDeparture').fill('10:30');
  await page.locator('#tripArrivalDate').fill('2026-11-25');
  await page.locator('#tripArrival').fill('20:00');
  assert.equal(await page.locator('#tripWorkStart').inputValue(),'08:30');
  await page.locator('#tripDate').fill('2026-11-24');
  await page.locator('#tripArrivalDate').fill('2026-11-24');
  assert.equal(await page.locator('#tripWorkStart').inputValue(),'09:30','changing the date loads that day’s actual start');
  assert.match(await page.locator('#tripDuration').innerText(),/10:30/);
  await page.locator('#tripNote').fill('ui-date-change');
  await page.locator('#tripSubmit').click();await page.locator('#modal').waitFor({state:'hidden'});
  const changed=await page.evaluate(()=>({row:Core.day(state,'2026-11-24'),trip:state.trips.find(t=>t.note==='ui-date-change')}));
  assert.equal(changed.trip.workStart,'09:30');assert.equal(changed.row.worked,630);
  passed('trip-date-change-updates-preview-and-save',{start:'09:30',workedMinutes:630});

  await page.locator('#goOnTrip').click();
  await page.locator('#tripDate').fill('2026-11-25');
  await page.locator('#tripDeparture').fill('10:30');
  await page.locator('#tripArrivalDate').fill('2026-11-25');
  await page.locator('#tripArrival').fill('13:00');
  await page.locator('#tripNote').fill('ui-early-arrival');
  assert.match(await page.locator('#tripDuration').innerText(),/4:30/);
  await page.locator('#tripSubmit').click();await page.locator('#modal').waitFor({state:'hidden'});
  const early=await page.evaluate(()=>Core.day(state,'2026-11-25'));
  assert.equal(early.worked,270);assert.equal(early.end,'13:00');
  passed('arrival-before-schedule-closes-day',{end:'13:00',workedMinutes:270});

  const performance=await page.evaluate(()=>{
   const hero=document.querySelector('.hero'),navigation=document.getElementById('nav').firstElementChild;
   const oldWrites={...window.__uiWrites},closed=Core.week(state,'2026-10-05');
   window.__uiNow+=60000;WFPerformance.refresh();WFPerformance.refresh();
   return {heroPreserved:hero===document.querySelector('.hero'),navigationPreserved:navigation===document.getElementById('nav').firstElementChild,
    closedWeekReused:closed===Core.week(state,'2026-10-05'),before:oldWrites,after:{...window.__uiWrites}};
  });
  assert(performance.heroPreserved&&performance.navigationPreserved);
  assert(performance.closedWeekReused);assert.deepEqual(performance.after,performance.before);
  passed('live-refresh-preserves-dom-and-closed-cache',performance);

  assert.equal(await page.locator('#notificationBadge').innerText(),'2');
  await page.locator('#notificationBell').click();
  assert.equal(await page.locator('[data-notification]').count(),2);
  assert.equal(await page.locator('[data-notification]').first().getAttribute('data-notification'),'notice_ui_pickup');
  await page.locator('[data-notification="notice_ui_pickup"]').click();
  assert.equal(await page.locator('.notification-body').innerText(),history.notifications[0].body);
  assert.equal(await page.locator('.notification-body b').count(),0,'notice text cannot inject HTML');
  assert.equal(await page.locator('#notificationBadge').innerText(),'1');
  await page.locator('#notificationOpenTarget').click();
  await page.locator('#modal').waitFor({state:'hidden'});
  assert.equal(await page.locator('[data-pickup-id="pickup_ui_notice"].notification-focus').count(),1);
  assert.equal(await page.locator('[data-pickup-id="pickup_cargo_ui_partial"]').count(),1);
  passed('bell-full-readable-notice-and-goods-link');

  await page.reload({waitUntil:'load'});
  await page.waitForFunction(()=>window.WFApp&&document.getElementById('notificationBell')&&document.getElementById('liveWorkClock'));
  assert.equal(await page.locator('#notificationBadge').innerText(),'1','read status survives a full app-page restart');
  assert.equal(await page.locator('#liveWorkClock').innerText(),'11:30');
  const reopened=await page.evaluate(()=>WFApp.getState());
  assert.equal(reopened.cargoLog.shipments.length,2);assert.equal(reopened.pickupLog.items.length,3);
  assert.equal(reopened.warehouseLog.visits[0].documents[0].fileId,'ui_document.jpg');
  await page.locator('#notificationBell').click();
  await page.locator('[data-notification="notice_ui_service"]').click();
  await page.locator('#notificationOpenTarget').click();
  await page.locator('#modal').waitFor({state:'hidden'});
  assert.equal(await page.locator('[data-van-add="services"]').count(),1);
  assert((await page.locator('#app').innerText()).includes('Масло и филтри'));
  passed('restart-keeps-read-goods-documents-and-service-link');

  await page.locator('#nav [data-view="home"]').click();
  const gps=await page.evaluate(()=>Number(localStorage.getItem('wfag-ui-gps-starts')));
  assert.equal(gps,0,'opening the app and entering work/travel must not start GPS');
  passed('no-automatic-gps-start',{calls:gps});
  const mobileTripLayouts=[];
  for(const width of [320,390]){
   await page.setViewportSize({width,height:844});
   const headerWidth=await page.locator('header.top').evaluate(el=>({scroll:el.scrollWidth,client:el.clientWidth}));
   assert(headerWidth.scroll<=headerWidth.client+1,'bell and settings fit a narrow phone header');
   const layout=await page.locator('.trip-times').first().evaluate(el=>{
    const cells=[...el.children],clocks=[...el.querySelectorAll('b')];
    return {columns:getComputedStyle(el).gridTemplateColumns.split(/\s+/).length,
     rows:cells.map(cell=>cell.getBoundingClientRect().top),
     clocks:clocks.map(clock=>{const range=document.createRange();range.selectNodeContents(clock);return {
      text:clock.textContent,lines:[...new Set([...range.getClientRects()].map(rect=>Math.round(rect.top)))].length,
      scroll:clock.scrollWidth,client:clock.clientWidth};})};
   });
   assert.equal(layout.columns,5,'work start, departure, arrow, arrival and duration each have their own column');
   assert.equal(new Set(layout.rows.map(top=>Math.round(top))).size,1,'all five trip cells remain in one row');
   for(const clock of layout.clocks){
    assert.equal(clock.lines,1,'a same-day trip clock must not wrap its digits at '+width+'px: '+clock.text);
    assert(clock.scroll<=clock.client+1,'trip clocks fit their column at '+width+'px: '+clock.text);
   }
   mobileTripLayouts.push({width,...layout});
  }
  await page.locator('#notificationBell').click();
  assert.equal(await page.locator('#notificationBadge').isVisible(),false);
  passed('phone-layout-and-final-inbox',{tripLayouts:mobileTripLayouts});
  assert.deepEqual(summary.javascriptErrors,[],'application must have no JavaScript errors');
  assert.deepEqual(summary.consoleErrors,[],'application must have no console errors');
  assert.deepEqual(summary.failedRequests,[],'all application assets must load');
  passed('no-javascript-errors-or-missing-assets');
  summary.ok=true;
 }catch(error){summary.error=error.stack||error.message;process.exitCode=1;}
 finally{
  summary.passedChecks=summary.checks.filter(check=>check.passed).length;summary.plannedChecks=10;
  summary.score=summary.passedChecks+'/10';
  if(page)try{await page.screenshot({path:path.join(delivery,'ui-smoke.png'),fullPage:true});summary.screenshot='delivery/ui-smoke.png';}catch(error){summary.screenshotError=error.message;}
  fs.writeFileSync(path.join(delivery,'ui-smoke.json'),JSON.stringify(summary,null,2)+'\n');
  console.log(JSON.stringify({test:'WFAG_UI_SMOKE',...summary},null,2));
  if(browser)await browser.close();
  if(server)await new Promise(resolve=>server.close(resolve));
 }
}
run().catch(error=>{console.error(error);process.exitCode=1;});
