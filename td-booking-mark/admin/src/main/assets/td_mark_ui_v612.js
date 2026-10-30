/* TD Booking 6.12 MarK LAN Sync UI. Original v6.10 data/payment modules remain unmodified. */
(()=>{'use strict';
if (!window.TDNative || typeof window.TDNative.marKInfo !== 'function')return;
const $m=id=>document.getElementById(id);
const originalPersist=persist;
let status='Неповрзано',timer=null;
const wrap=document.createElement('section');wrap.id='tdMarKPanel';wrap.className='card no-print';
wrap.style.cssText='display:none;margin:14px;border:2px solid #0c7066;background:#f4faf8;padding:16px;border-radius:15px';
wrap.innerHTML=`<h2>🔗 TD Booking ↔ MarK</h2><p>Поврзи го телефонот и PC на иста локална Wi‑Fi мрежа. Отвори MarK Desktop → Booking и копирај ги адресата и TLS отпечатокот.</p>
<label>MarK LAN адреса (пример https://192.168.1.10:8766)</label><input id="tdMarkURL" type="text" autocomplete="off" placeholder="https://192.168.1.10:8766">
<label>TLS SHA‑256 отпечаток (64 знаци)</label><textarea id="tdMarkFP" rows="3" style="width:100%;min-height:85px" placeholder="Залепи го отпечатокот од PC"></textarea>
<div style="display:flex;gap:8px;flex-wrap:wrap;margin:12px 0"><button id="tdMarkPair">1. Започни спарување</button><button id="tdMarkFinish">2. Заврши по PC потврда</button></div>
<div id="tdMarkCode" style="font-size:28px;font-weight:bold;color:#164d42"></div>
<div id="tdMarkStatus" style="padding:10px;border-radius:9px;background:#e7f0eb;white-space:pre-wrap">Не е поврзано.</div>
<div style="display:flex;gap:8px;flex-wrap:wrap;margin-top:12px"><button id="tdMarkNow">↻ Само свадби (стара врска)</button><button class="secondary" id="tdMarkDisconnect">Раздвој уред</button><button class="secondary" id="tdMarkClose">Затвори</button></div>
<p style="font-size:12px">Ова е стариот пренос САМО за свадби. За финансии, хонорари и исплати користи „Синхронизирај сè“ во панелот MarK · TD Booking. Се пренесуваат само потребни податоци за продукцијата: датум, младенци, локација, пакет и услуги. Уплати, телефонски броеви, потписи и финансиски информации НЕ се испраќаат. Известувањето се појавува во MarK додека е отворен.</p>`;
document.body.append(wrap);
// Keep MarK reachable without a floating pill obscuring bottom navigation at any display scale.
const open=document.createElement('button');
open.type='button';open.className='secondary';open.textContent='🔗 MarK поврзување';
open.onclick=()=>{
  if(typeof toggleDrawer==='function')toggleDrawer(false);
  wrap.style.display='block';wrap.scrollIntoView({behavior:'smooth',block:'start'});
  try{const cfg=JSON.parse(TDNative.marKInfo());$m('tdMarkURL').value=cfg.endpoint||$m('tdMarkURL').value}catch(e){}
};
const drawer=document.getElementById('drawer');
if(drawer){
  const close=drawer.lastElementChild;
  if(close&&close.tagName==='BUTTON')drawer.insertBefore(open,close);
  else drawer.append(open);
}else{
  // Fallback when the drawer markup is not available, never use fixed positioning.
  const header=document.querySelector('header');
  if(header)header.append(open);else document.body.prepend(open);
}
$m('tdMarkClose').onclick=()=>wrap.style.display='none';
$m('tdMarkStatus').textContent='Не е поврзано';
function update(text){status=text;$m('tdMarkStatus').textContent=text}
function metadata(){return (Array.isArray(data)?data:[]).filter(b=>b&&b.id&&b.date&&(!b.eventType||b.eventType==='wedding')).map(b=>{
 const pkg=Array.isArray(PACKAGES)?PACKAGES[Number(b.package)]:null;
 const pkgName=pkg?String(pkg[0]):(b.package==='custom'?'Посебен пакет':'');
 const text=String(b.services||'')+' '+String(b.giftNotes||'')+' '+pkgName;
 const contains=(...words)=>words.some(word=>text.toLocaleLowerCase().includes(word));
 return {id:String(b.id),bride:String(b.bride||''),groom:String(b.groom||''),date:String(b.date||''),start:String(b.start||''),venue:String(b.venue||''),
 package:String(b.package||''),packageName:pkgName,services:String(b.services||'').slice(0,600),status:String(b.status||''),
 options:{video:contains('видео','камера','video'),photo:contains('фото','photo'),drone:contains('дрон','drone'),
 crane:contains('кран','crane'),spot:contains('спот','spot','best moments'),loveStory:contains('love story'),prints:contains('печате','13×18','13x18')}}
 })}
function sync(){try{const info=JSON.parse(TDNative.marKInfo());if(!info.paired){update('Потребно е спарување со MarK.');return}TDNative.marKSync(JSON.stringify(metadata()));update('Се синхронизира…')}catch(e){update('Грешка: '+e.message)}}
function schedule(){clearTimeout(timer);try{const info=JSON.parse(TDNative.marKInfo());if(info.paired)timer=setTimeout(sync,900)}catch(e){}}
$m('tdMarkPair').onclick=()=>{update('Проверувам шифрирана врска…');$m('tdMarkCode').textContent='';TDNative.marKPair($m('tdMarkURL').value.trim(),$m('tdMarkFP').value.trim())};
$m('tdMarkFinish').onclick=()=>{update('Чекам потврда од PC…');TDNative.marKConfirm()};
$m('tdMarkNow').onclick=sync;
$m('tdMarkDisconnect').onclick=()=>{if(confirm('Да го одвоиме телефонот? За целосно укинување на пристапот избриши го уредот и од MarK Desktop.')){TDNative.marKDisconnect();update('Локално раздвоен уред.');$m('tdMarkCode').textContent=''}};
window.tdMarKResult=(msg)=>{
 if(!msg.ok){update('Грешка: '+(msg.error||'Нема врска'));return}
 if(msg.type==='pair-request'){ $m('tdMarkCode').textContent=msg.code;update('ПРОВЕРИ: дали на PC се прикажува ИСТИОТ код? Ако да, притисни Потврди на PC и потоа Заврши тука.') }
 if(msg.type==='pair-finish'){if(msg.pending)update('PC сè уште не потврдил. Кликни „Заврши“ повторно.');else {update('✓ Поврзано со MarK! Синхронизирам свадби…');sync()}}
 if(msg.type==='sync'){const projects=Array.from(msg.projects||[]).map(p=>'• '+p.name+': '+(p.status==='READY_FOR_REVIEW'?'подготвен за проверка':'чека материјали'));update('✓ Синхронизација завршена: '+msg.received+' свадби, '+msg.changed+' нови/изменети.'+(projects.length?'\nMarK проекти:\n'+projects.join('\n'):''))}
};
persist=function(){originalPersist();schedule()}; // Do not alter TD Booking storage format.
setTimeout(schedule,1900);
document.addEventListener('visibilitychange',()=>{if(!document.hidden)schedule()});
setInterval(()=>{if(!document.hidden)schedule()},45000);
})();
