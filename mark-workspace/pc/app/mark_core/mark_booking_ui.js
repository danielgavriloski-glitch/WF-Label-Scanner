/* MarK Booking – real Android sync / HDD wizard v1.7.0 (embedded in desktop HTML). */
(()=>{'use strict';
const homeSummary=document.getElementById('bookingSummary');
const get=id=>document.getElementById(id),safe=t=>String(t??'');
let lastEventId=Number(sessionStorage.getItem('td-last-event')||0),initialized=false,selected=null,step=1,preview=null,existing=false;
const style=document.createElement('style');style.textContent=`
.bksync{border:1px solid var(--border);border-radius:14px;padding:17px;margin-top:16px;background:var(--panel2)}
.bksync h3{margin:0 0 9px}.bksync p{color:var(--muted);font-size:13px}.bkcols{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.bklist{max-height:260px;overflow:auto}.bkitem{padding:10px 12px;border-bottom:1px solid var(--border);display:flex;justify-content:space-between;align-items:center;gap:10px}.bkitem b{font-size:13px}
.bkbadge{color:var(--secondary);font-size:12px}.bkwizard{border:1px solid var(--accent);border-radius:14px;padding:18px;background:var(--panel);margin-top:16px;display:none}
.bkwizard.visible{display:block}.bkfield{width:100%;min-height:43px;padding:10px;border:1px solid var(--border);background:#0f202e;color:var(--ink);border-radius:9px}
.bkwizard label{display:block;margin:12px 0 5px;color:var(--muted)}.bkwizard .btn{margin:7px 5px 4px 0}
#bkAlert{display:none;border:1px solid var(--secondary);background:#322d24;color:#ffe9b5;padding:10px;margin:8px 0;border-radius:9px;cursor:pointer}
@media(max-width:900px){.bkcols{grid-template-columns:1fr}}`;
document.head.append(style);
const bookingPage=get('page-booking');if(!bookingPage)return;
const mount=document.createElement('div');mount.className='bksync';mount.innerHTML=`
<h3>📱 TD Booking ↔ MarK · Локална синхронизација</h3>
<p>Телефон и PC на иста Wi‑Fi мрежа. Споредете го истиот 6-цифрен код на двата уреди и потврдете го на PC. Старата v1 врска пренесува само продукциски податоци. Новата v2 синхронизација во рамките на Booking е целосна.</p>
<div id="bkAlert" role="status"></div>
<div class="bkcols"><div><b>Адреса за телефонот</b><input id="bkAddr" class="bkfield" readonly><b>TLS SHA-256 отпечаток</b><textarea id="bkFp" class="bkfield" rows="3" readonly></textarea><p>На телефонот: TD Booking → MarK поврзување → залепи адреса и отпечаток. Никогаш не внесувај го твојот MarK PIN на телефонот.</p></div><div><b>Барања за поврзување</b><div id="bkPending">Нема барања</div><p id="bkLanStatus"></p><button class="btn" id="bkReload">↻ Освежи</button></div></div>
<div style="margin-top:15px"><h3>📅 Свадби и нови известувања</h3><button class="btn gold" id="bkManualCreate">＋ Нова свадба рачно (без телефон)</button><p class="small">Ако APK не е поврзана, можеш да подготвиш локален проект и подоцна да синхронизираш.</p><div id="bkEvents" class="bklist">Нема известувања.</div><div id="bkBookings" class="bklist"></div></div>
<div class="bkwizard" id="bkWizard"><div class="bkbadge" id="bkStep">Чекор 1 / 4</div><h3 id="bkTitle">Креирај продукциски проект</h3>
<div id="bkWizardBody"></div><p id="bkWizardError" style="color:#e7a99f"></p>
<button class="btn" id="bkBack">← Назад</button><button class="btn primary" id="bkNext">Продолжи →</button><button class="btn quiet" id="bkCancel">Затвори</button></div>`;
bookingPage.insertBefore(mount, bookingPage.querySelector('details')||bookingPage.firstChild);
const showError=e=>{get('bkWizardError').textContent=e.message||safe(e)};
const escHTML=t=>safe(t).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
// Qt native folder picker is only available inside the real MarK Desktop window.
let markPicker=null;
if(window.qt && window.qt.webChannelTransport){
 const loader=document.createElement('script');loader.src='qrc:///qtwebchannel/qwebchannel.js';
 loader.onload=()=>{try{new QWebChannel(window.qt.webChannelTransport,channel=>{markPicker=channel.objects.markPicker})}catch(e){console.warn('MarK folder picker unavailable',e)}};
 document.head.append(loader);
}
function attachBrowse(input,parent){if(!markPicker)return;const browse=document.createElement('button');browse.type='button';browse.className='btn';browse.textContent='📁 Пребарај папка';browse.style.marginTop='5px';browse.onclick=()=>{markPicker.selectFolder(result=>{if(result)input.value=result})};parent.append(browse)}
let bookings=[],projectIndex={};
async function refresh(){try{
 const state=await api('/api/mark/sync/status');get('bkAddr').value=state.address;get('bkFp').value=state.lan.fingerprint||'Нема сертификат';
 get('bkLanStatus').textContent=state.lan.running?'LAN синхронизација: активна · поврзани уреди: '+state.devices.length:'LAN врската не работи: '+state.lan.error;
 const full=await fetch('/api/mark/booking/replica/status',{credentials:'same-origin'}).then(x=>x.ok?x.json():null).catch(()=>null);
 const fullCounts=full?.counts||{};
 const legacy=state.bookings.length;
 if(homeSummary){homeSummary.textContent=
  'Full Sync свадби: '+(fullCounts.bookings||0)+' · финансиски записи: '+(fullCounts.finance||0)+
  ' · локална екипа: '+(fullCounts.employees||0)+
  '\nСтар продукциски регистар: '+legacy+' свадби · Поврзани телефони: '+state.devices.length+
  '\nLAN: '+(state.lan.running?'активно':'неактивно')+' · HDD проекти: '+state.projects.length+
  (legacy&&!fullCounts.bookings?'\n⚠ Свадбата е само во стар канал — не значи дека финансии се пренесени.':'');}
 const badge=get('tdBkBadge');if(badge){const unseen=state.events.filter(e=>e.id>lastEventId).length;badge.textContent=unseen;badge.style.display=unseen?'inline-flex':'none'}
 const pending=get('bkPending');pending.replaceChildren();for(const p of state.pending){let el=document.createElement('div');el.className='bkitem';const tx=document.createElement('div');tx.textContent=p.deviceId+' · КОД '+p.code;const btn=document.createElement('button');btn.className='btn';btn.textContent=p.approved?'Потврдено':'✓ Потврди код';btn.disabled=p.approved;btn.onclick=async()=>{try{await api('/api/mark/sync/approve',{challenge:p.challenge,code:p.code});refresh()}catch(e){toast(e.message,true)}};el.append(tx,btn);pending.append(el)}if(!state.pending.length)pending.textContent='Нема барања';
 bookings=state.bookings;projectIndex=Object.fromEntries(state.projects.map(p=>[p.bookingId,p]));
 const rows=get('bkBookings');rows.replaceChildren();for(const b of bookings){let el=document.createElement('div');el.className='bkitem';let info=document.createElement('div');const strong=document.createElement('b');strong.textContent=b.date+' · '+(b.groom||'')+' и '+(b.bride||'');const small=document.createElement('div');small.className='small';small.textContent=(b.status||'')+' · '+(b.venue||'');info.append(strong,small);let btn=document.createElement('button');btn.className='btn';btn.textContent=projectIndex[b.id]?(projectIndex[b.id].status==='WAITING_FOR_MEDIA'?'＋ Додај материјали':'↻ Провери материјали'):'＋ Креирај проект';btn.onclick=()=>projectIndex[b.id]?openUpdateWizard(b):openWizard(b);el.append(info,btn);rows.append(el)}
 const ev=get('bkEvents');ev.replaceChildren();for(const e of state.events.slice(0,8)){const r=document.createElement('div');r.className='bkitem';r.textContent=(e.kind==='new'?'🟢 Нова свадба: ':'✏ Измена: ')+e.label;ev.append(r)}if(!state.events.length)ev.textContent='Нема известувања';
 let most=state.events[0];if(most && initialized && most.id>lastEventId){lastEventId=most.id;sessionStorage.setItem('td-last-event',String(lastEventId));const a=get('bkAlert');a.style.display='block';a.textContent=(most.kind==='new'?'🔔 Нова свадба: ':'Измена на свадба: ')+most.label+' · Отвори Booking за да направиш проект.';a.onclick=()=>a.style.display='none';toast('TD Booking: '+most.label)}else if(most&&!initialized){lastEventId=most.id;sessionStorage.setItem('td-last-event',String(lastEventId))}
 initialized=true;
 }catch(e){get('bkLanStatus').textContent='Врската не е достапна: '+e.message}
}
get('bkManualCreate').onclick=async()=>{
 const d=prompt('Датум на свадба (ГГГГ-ММ-ДД):');if(d===null)return;
 const groom=prompt('Име на зет:');if(groom===null)return;
 const bride=prompt('Име на невеста:');if(bride===null)return;
 try{const result=await api('/api/mark/sync/manual-booking',{date:d,groom,bride});if(!result.fullSyncReady)throw Error('Full Sync потврда недостига');await refresh();toast('Свадбата е запишана во Full Sync и е достапна за следното преземање на телефонот.');}
 catch(e){toast(e.message,true)}
};
async function watchCopy(id){let tries=0;while(tries++<2400){const x=await api('/api/mark/sync/media-copy/'+encodeURIComponent(id));const target=get('bkCopyProgress');
 if(target)target.textContent=x.state==='RUNNING'?'Копирање: '+x.filesCopied+'/'+x.filesTotal+' фајлови ('+x.bytesCopied+' bytes) · оригиналите се безбедни':(x.state==='DONE'?'✓ Копирањето е завршено · '+x.filesCopied+' фајлови. Подготвено за проверка.':'Грешка при копирање: '+(x.error||'Непозната'));
 if(x.state!=='RUNNING')break;await new Promise(resolve=>setTimeout(resolve,1500));}}
get('bkReload').onclick=refresh;
let root='',videoPaths='',copyChoice=true; 
function fields(){return [{label:'🎬 Видео · Камера 1',type:'VIDEO_CAM_1',id:'bkVideo1'},{label:'🎬 Видео · Камера 2',type:'VIDEO_CAM_2',id:'bkVideo2'},{label:'🚁 Видео · Дрон',type:'VIDEO_DRONE',id:'bkDrone'},{label:'🎥 Видео · Кран',type:'VIDEO_CRANE',id:'bkCrane'},{label:'📷 Фотографии · Камера 1',type:'PHOTO_CAM_1',id:'bkPhoto1'},{label:'📷 Фотографии · Камера 2',type:'PHOTO_CAM_2',id:'bkPhoto2'}]}
function saveFields(){if(get('bkTarget'))root=get('bkTarget').value.trim();if(get('bkVideo1'))videoPaths=JSON.stringify(fields().map(f=>({label:f.type,path:(get(f.id)?.value||'').trim()})))}
function restorePaths(){let val=[];try{val=JSON.parse(videoPaths||'[]')}catch(e){};return val}
function openWizard(b){selected=b;existing=false;step=1;root='';videoPaths='';copyChoice=true;preview=null;get('bkNext').disabled=false;get('bkWizard').classList.add('visible');renderWizard();get('bkWizard').scrollIntoView({behavior:'smooth',block:'nearest'})}
function openUpdateWizard(b){selected=b;existing=true;step=3;root=projectIndex[b.id].path;videoPaths='';copyChoice=true;preview=null;get('bkNext').disabled=false;get('bkWizard').classList.add('visible');renderWizard();get('bkWizard').scrollIntoView({behavior:'smooth',block:'nearest'})}
function renderWizard(){get('bkWizardError').textContent='';get('bkStep').textContent='Чекор '+step+' / 4';get('bkTitle').textContent=['','1. Проверка на свадбата','2. Избери HDD папка','3. Избери видео и фото извори','4. Преглед и креирање'][step];
 get('bkBack').disabled=step===1||(existing&&step===3);get('bkNext').textContent=step===4?'✓ Креирај проект':'Продолжи →';
 const p=get('bkWizardBody');p.replaceChildren();let box=document.createElement('div');
 if(step===1){box.innerHTML=`<p><b>${escHTML(selected?.groom)} и ${escHTML(selected?.bride)}</b><br>📅 ${escHTML(selected?.date)} · ${escHTML(selected?.venue)}</p><p>Договорено: ${escHTML(selected?.packageName||selected?.package)} · ${escHTML(selected?.services)}</p><p>MarK ќе зачува проект со информации за услугите. Оригиналите остануваат недопрени.</p>`}
 if(step===2){box.innerHTML=`<p>Избери веќе постоечка главна папка на диск, пример <code>H:\\TD_Projects</code>. MarK автоматски ќе додаде папка со датум и младенци.</p><label>Главна HDD папка</label><input id="bkTarget" class="bkfield" placeholder="H:\\TD_Projects" value="${escHTML(root)}"><p>Нема автоматско преместување на старите проекти.</p>`}
 if(step===3){box.innerHTML='<p><b>Видео – суров материјал</b> и <b>Фотографии – суров материјал</b> се одвоени. Прво избери папки од HDD; MarK ќе провери екстензии и број на фајлови (не AI сцени). Ако свадбата е во иднина, остави празно.</p>'; const saved=restorePaths();for(const f of fields()){const label=document.createElement('label');label.textContent=f.label;const inp=document.createElement('input');inp.className='bkfield';inp.id=f.id;inp.placeholder='H:\\Свадба\\'+f.type;inp.value=saved.find(x=>x.label===f.type)?.path||'';box.append(label,inp)} const choice=document.createElement('label');choice.innerHTML='<input type="checkbox" id="bkCopyChoice"> По креирање, копирај ги снимките во новиот проект (оригиналите остануваат недопрени)';box.append(choice);const check=choice.querySelector('input');check.checked=copyChoice;check.addEventListener('change',e=>{copyChoice=e.target.checked});}
 if(step===4){let ok=preview||{videos:0,photos:0,sources:[]};box.innerHTML='<p><b>Датотеки за проверка:</b> '+ok.videos+' видеа, '+ok.photos+' фотографии.</p><p>'+escHTML(root)+'</p><p>ВИДЕО → 01_СУРОВ_МАТЕРИЈАЛ/ВИДЕО; ФОТОГРАФИИ → 01_СУРОВ_МАТЕРИЈАЛ/ФОТОГРАФИИ. Оригиналите остануваат недопрени. Опција копирај: ' + (copyChoice?'ДА':'НЕ') + '</p><p><b>Статус:</b> '+(ok.ready?'Подготвен за проверка и избор на монтажа':'Чека материјали')+'</p>'}
 p.append(box);if(step===2 && get('bkTarget'))attachBrowse(get('bkTarget'),box);if(step===3)for(const f of fields()){const inp=get(f.id);if(inp)attachBrowse(inp,box)}}
get('bkBack').onclick=()=>{saveFields();if(step>1&&!(existing&&step===3)){step--;renderWizard()}};get('bkCancel').onclick=()=>get('bkWizard').classList.remove('visible');
get('bkNext').onclick=async()=>{try{if(step===2){saveFields();if(!root)throw Error('Внеси HDD папка')}
 if(step===3){saveFields();copyChoice=!!get('bkCopyChoice')?.checked;preview=await api('/api/mark/sync/media-preview',{folders:restorePaths()})}
 if(step===4){const result=existing?await api('/api/mark/sync/update-media',{bookingId:selected.id,folders:restorePaths()}):await api('/api/mark/sync/create-project',{bookingId:selected.id,target:root,folders:restorePaths()});get('bkWizardBody').replaceChildren();const msg=document.createElement('div');msg.textContent='✓ '+(result.ready_for_review?(copyChoice?'ПОДГОТОВКА ВО ТЕК – СЕ КОПИРААТ МАТЕРИЈАЛИТЕ':'МЕДИУМИТЕ СЕ ИНДЕКСИРАНИ – ПОДГОТВЕН ЗА ПРОВЕРКА'):'ПРОЕКТОТ Е СОЗДАДЕН – ЧЕКА МАТЕРИЈАЛИ')+'\n'+result.path;msg.style.whiteSpace='pre-wrap';get('bkWizardBody').append(msg);get('bkNext').disabled=true;toast('Проектот е создаден');if(copyChoice && preview && (preview.videos+preview.photos)>0){
  try {const copying=await api('/api/mark/sync/media-copy',{bookingId:selected.id});const note=document.createElement('div');note.id='bkCopyProgress';note.className='notice';note.textContent='Копирање во позадина: 0%';get('bkWizardBody').append(note);await watchCopy(copying.jobId)} catch(err){showError(err)}
 }await loadProjects();await refresh();return}
 step++;renderWizard()}catch(e){showError(e)}};
refresh();setInterval(refresh,12000);
})();
