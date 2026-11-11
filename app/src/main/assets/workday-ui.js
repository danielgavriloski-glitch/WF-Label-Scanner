/* WFAG workday controls; stored inside the existing app backup/state. */
(function(){
'use strict';
const W=window.WFAGWorkday;
if(!W||typeof window.WFApp==='undefined')return;
const date=()=>Core.today(),now=()=>Date.now();
function config(){return W.settings(WFApp.getState().workdaySettings||{});}
let pendingGps=null;
window.onWFAGWorkdayLocation=raw=>{
 if(!pendingGps)return;
 const p=pendingGps;pendingGps=null;clearTimeout(p.timeout);
 try{const result=JSON.parse(raw);if(result.error)throw Error(result.error);p.resolve(result);}catch(e){p.reject(e);}
};
function gpsPosition(){
 if(typeof Android!=='undefined'&&typeof Android.requestWorkdayLocation==='function'){
  return new Promise((resolve,reject)=>{
   if(pendingGps){reject(Error('GPS проверката е во тек.'));return;}
   const timeout=setTimeout(()=>{if(pendingGps){pendingGps=null;reject(Error('GPS не одговори.'));}},17000);
   pendingGps={resolve,reject,timeout};
   try{Android.requestWorkdayLocation();}catch(e){pendingGps=null;clearTimeout(timeout);reject(e);}
  });
 }
 return new Promise((resolve,reject)=>{
  if(!navigator.geolocation){reject(Error('GPS не е достапен.'));return;}
  navigator.geolocation.getCurrentPosition(p=>resolve({lat:p.coords.latitude,lon:p.coords.longitude,accuracy:p.coords.accuracy}),()=>reject(Error('GPS локацијата не може да се потврди.')),{enableHighAccuracy:true,timeout:12000,maximumAge:0});
 });
}
async function authorisedStart(day){
 const cfg=config();
 if(cfg.mode==='geofence'){
  if(!cfg.workplace)throw Error('Администраторот треба прво да постави работна локација.');
  const p=await gpsPosition();
  const result=W.geofenceStart(day,cfg,p,now());
  if(result.startAt===null)throw Error('Не си во работната локација или GPS точноста е недоволна.');
  return result;
 }
 if(cfg.mode==='fixed'){
  const scheduled=Core.timestamp(date(),cfg.start);
  if(now()<scheduled)throw Error('Работниот ден започнува во '+cfg.start);
  return W.start(day,scheduled,'fixed');
 }
 return W.start(day,now(),'manual');
}
function getDay(){const s=WFApp.getState();return s.workdaySessions&&s.workdaySessions[date()]||W.newDay(date());}
function saveDay(d,label){
 const s=WFApp.getState();
 s.workdaySessions=s.workdaySessions||{};
 s.workdaySessions[d.date]=d;
 WFApp.commit(s,label);
}
function escText(v){return String(v).replace(/[&<>\"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[ch]));}
function button(label,action,cls){return '<button type="button" class="btn '+(cls||'secondary')+'" data-workday="'+action+'">'+label+'</button>';}
function authoriseSettings(){
 if(typeof Android==='undefined'||typeof Android.isAdminConfigured!=='function')throw Error('За заштитени поставки е потребна Android верзијата.');
 if(!Android.isAdminConfigured()){
  const first=prompt('Постави администраторска лозинка (најмалку 6 знаци):');
  if(first===null)throw Error('Откажано.');
  if(first.length<6)throw Error('Лозинката мора да има најмалку 6 знаци.');
  const second=prompt('Повтори ја администраторската лозинка:');
  if(first!==second)throw Error('Лозинките не се совпаѓаат.');
  if(!Android.setupAdminPassword(first))throw Error('Лозинката не се зачува.');
  return first;
 }
 const password=prompt('Администраторска лозинка:');
 if(password===null)throw Error('Откажано.');
 if(!Android.verifyAdminPassword(password))throw Error('Погрешна администраторска лозинка.');
 return password;
}
function drawSettings(){
 const host=document.getElementById('app');if(!host||!document.getElementById('settingsForm'))return;
 const old=document.getElementById('wfagWorkdaySettings');if(old)old.remove();
 const cfg=config(),place=cfg.workplace||{};
 const panel=document.createElement('section');panel.className='card';panel.id='wfagWorkdaySettings';
 panel.innerHTML='<h2>Почеток на работен ден</h2><label class="field"><span>Начин на започнување</span><select id="wdMode"><option value="fixed">Фиксно време</option><option value="geofence">GPS работна локација</option><option value="manual">Рачно</option></select></label><label class="field"><span>Фиксен почеток</span><input id="wdStart" type="time" value="'+escText(cfg.start)+'"></label><label class="field"><span>GPS радиус (50–1000 метри)</span><input id="wdRadius" type="number" min="50" max="1000" value="'+cfg.radiusM+'"></label><label class="field"><span>Географска ширина</span><input id="wdLat" type="number" step="any" placeholder="Latitude" value="'+(place.lat??'')+'"></label><label class="field"><span>Географска должина</span><input id="wdLon" type="number" step="any" placeholder="Longitude" value="'+(place.lon??'')+'"></label><button class="btn secondary" type="button" id="wdCurrentLocation">Користи ја сегашната GPS локација</button><button class="btn full" type="button" id="wdSaveSettings">Зачувај GPS поставки</button><p class="small muted">GPS режимот проверува локација при започнување. Автоматско следење во позадина сè уште не е вклучено.</p>';
 document.getElementById('settingsForm').insertAdjacentElement('afterend',panel);
 panel.querySelector('#wdMode').value=cfg.mode;
 panel.querySelector('#wdCurrentLocation').onclick=async()=>{try{const p=await gpsPosition();panel.querySelector('#wdLat').value=p.lat;panel.querySelector('#wdLon').value=p.lon;}catch(e){alert(e.message);}};
 panel.querySelector('#wdSaveSettings').onclick=()=>{try{
  const adminPassword=authoriseSettings();
  const lat=panel.querySelector('#wdLat').value.trim(),lon=panel.querySelector('#wdLon').value.trim();
  if((lat==='')!==(lon===''))throw Error('Внеси ги двете GPS координати.');
  const settings=W.settings({mode:panel.querySelector('#wdMode').value,start:panel.querySelector('#wdStart').value,radiusM:Number(panel.querySelector('#wdRadius').value),workplace:lat===''?null:{lat:Number(lat),lon:Number(lon)}});
  if(settings.mode==='geofence'&&!settings.workplace)throw Error('Прво постави работна GPS локација.');
  const state=WFApp.getState();settings.activatedOn=state.workdaySettings&&state.workdaySettings.activatedOn||Core.today();state.workdaySettings=settings;WFApp.commitWithAdmin(state,'Променети поставки за работен ден',adminPassword);
 }catch(e){alert(e.message);}};
}
function draw(){
 const host=document.getElementById('app');if(!host||!window.WFApp||!host.querySelector('.hero'))return;
 const old=document.getElementById('wfagWorkdayControls');if(old)old.remove();
 const d=getDay(),t=W.totals(d,now());
 const panel=document.createElement('section');panel.id='wfagWorkdayControls';panel.className='card';
 let actions='';
 if(d.status==='not-started')actions=button(config().mode==='geofence'?'Провери GPS и започни':'Започни работен ден','start','primary');
 else if(d.status==='travel')actions=button('Пристигнав','arrive')+button('Заврши работен ден','finish','danger');
 else if(d.status==='working')actions=button('На пат','depart')+button('Заврши работен ден','finish','danger');
 panel.innerHTML='<h3>Работен ден · контрола</h3><p class="small muted">Статус: '+({ 'not-started':'Не е започнат',working:'Работа',travel:'На пат',finished:'Завршен'}[d.status]||d.status)+'</p><div class="mini-row"><span>Од почеток</span><strong>'+Core.hh(t.workedMinutes)+'</strong></div><div class="mini-row"><span>Време на пат</span><strong>'+Core.hh(t.travelMinutes)+'</strong></div>'+(d.endAt!==null?'<div class="mini-row"><span>Разлика од 8 часа</span><strong>'+Core.hhSigned(t.overtimeDelta)+'</strong></div>':'')+'<div class="actions">'+actions+'</div><p class="small muted">Евиденцијата е поврзана со дневниот фонд и пресметката на прекувремени часови.</p>';
 host.querySelector('.hero').insertAdjacentElement('afterend',panel);
 panel.querySelectorAll('[data-workday]').forEach(b=>b.addEventListener('click',async()=>{
  try{
   const action=b.dataset.workday,current=getDay(),time=now();
   const updated=action==='start'?await authorisedStart(current):action==='depart'?W.depart(current,time):action==='arrive'?W.arrive(current,time):W.finish(current,time);
   saveDay(updated,'Работен ден: '+action);
  }catch(e){alert(e.message);}
 }));
}
let automaticInProgress=false;
async function autoStart(){
 if(automaticInProgress||!document.querySelector('.hero')||getDay().status!=='not-started')return;
 const cfg=config();
 if(cfg.mode==='manual')return;
 if(cfg.mode==='fixed'){
  if(!WFApp.getState().settings.workdays.includes(Core.weekday(date())))return;
  if(now()<Core.timestamp(date(),cfg.start))return;
 }
 if(cfg.mode==='geofence'){
  if(!cfg.workplace)return;
  if(typeof Android!=='undefined'&&typeof Android.hasWorkdayLocationPermission==='function'&&!Android.hasWorkdayLocationPermission())return;
 }
 automaticInProgress=true;
 try{
  const updated=await authorisedStart(getDay());
  if(updated.startAt!==null&&getDay().status==='not-started')saveDay(updated,'Автоматски започнат работен ден ('+cfg.mode+')');
 }catch(e){/* Retry on next foreground check; do not display repeated alerts. */}
 finally{automaticInProgress=false;}
}
window.WFAGWorkdayAutoStart=autoStart;
setTimeout(autoStart,1200);
setInterval(autoStart,60000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)autoStart();});
const previous=window.WFApp.setView;
window.WFApp.setView=function(v){previous(v);if(v==='home')draw();if(v==='settings')drawSettings();};
draw();
const observer=new MutationObserver(()=>{if(document.querySelector('.hero')&&!document.getElementById('wfagWorkdayControls'))draw();if(document.getElementById('settingsForm')&&!document.getElementById('wfagWorkdaySettings'))drawSettings();});
observer.observe(document.getElementById('app'),{childList:true});
})();
