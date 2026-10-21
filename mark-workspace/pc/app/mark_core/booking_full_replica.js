/* MarK Booking full two-way LAN replica v2. Phone and desktop share this protocol.
   Phone keeps all existing WebView/localStorage data; PC runs the same Booking UI.
   Per-wedding optimistic revisions reject concurrent modification; do not silently overwrite.
*/
(()=>{'use strict';
// PC UI may be embedded by the Qt desktop under a local route or alias.
// Always trust the server-side PIN/origin guard, never bypass authentication.
const PC=(location.hostname==='127.0.0.1'||location.hostname==='localhost') &&
         location.port==='8765' && !(window.TDNative&&typeof window.TDNative.marKReplica==='function');
const NATIVE=!!(window.TDNative&&typeof window.TDNative.marKReplica==='function');
const BASE_KEY='tdMarKReplicaV2_Base';
const BOOK_KEY='tdBookingsV1',FIN_KEY='tdBookingFinanceV1',PKG_KEY='tdPackageSettings',CACHE_KEY='tdCloudCacheV1',ROSTER_KEY='tdMarKRemoteRosterV2',EMP_KEY='tdMarKLocalEmployeesV1';
const TYPE=['bookings','finance','settings','roster',...(PC?['employees']:[])];
let busy=false, pending=null, stamp=0, timer=null;
function json(s,fallback){if(s===null||s===undefined||s==='')return fallback;try{const v=JSON.parse(s);return v===null?fallback:v}catch(_){return fallback}}
function saved(key,fallback){return json(localStorage.getItem(key),fallback)}
function snap(){
 const a=saved(BOOK_KEY,[]),fin=saved(FIN_KEY,{}),packages=saved(PKG_KEY,[]),cache=saved(CACHE_KEY,{});
 const bookings={};for(const b of (Array.isArray(a)?a:[]))if(b&&typeof b.id==='string')bookings[b.id]=b;
 const roster={};for(const [id,e] of Object.entries(PC?saved(ROSTER_KEY,{}):cache?.employees||{}))if(e&&typeof e==='object')roster[id]={id,name:String(e.name||''),role:String(e.role||''),status:String(e.status||''),active:!!e.active};
 return {bookings,finance:(fin&&typeof fin==='object'&&!Array.isArray(fin)?fin:{}),settings:{global:Array.isArray(packages)?packages:[]},roster:{global:roster},...(PC?{employees:saved(EMP_KEY,{})}:{})};
}
function base(){let b=saved(BASE_KEY,null);if(!b||b.version!==2||typeof b.records!=='object')b={version:2,records:{}};for(const t of TYPE)b.records[t]??={};return b}
function eq(a,b){return JSON.stringify(a)===JSON.stringify(b)}
function makeOps(current,prior){let ops=[];for(const kind of TYPE){
 if(PC&&kind==='roster')continue; // cloud roster is phone-origin read-only
 const old=prior.records[kind]||{},now=current[kind]||{};
 for(const id of new Set([...Object.keys(old),...Object.keys(now)])){
   const currentValue=Object.prototype.hasOwnProperty.call(now,id)?now[id]:null;
   const prev=old[id]||null;
   if(!prev&&currentValue===null)continue;
   if(prev&&eq(prev.data,currentValue))continue;
   ops.push({kind,id,baseRev:prev?.rev||0,data:currentValue});
 }
}return ops}
function setStatus(message,err=false){
 const el=document.getElementById('markReplicaStatus');if(!el)return;
 el.textContent=message;el.style.color=err?'#9a4334':'#31594f';
}
function renderLink(){
 const top=document.createElement('section');top.id='marKFullSyncPanel';top.className='card no-print';
 top.style.cssText='padding:13px 16px;border:2px solid #b49756;background:#fffdf7;margin:10px auto;max-width:860px;'
 top.innerHTML='<h2 style="margin:0 0 7px">🔁 MarK · TD Booking — целосна синхронизација</h2>'+
 '<p class="muted" style="margin:0 0 9px">'+(PC?'MarK PC ↔ TD Booking Android':'Телефон ↔ MarK PC')+' · свадби, договори, финансии и исплати. Само на локална поврзана мрежа. Ако истиот запис е сменет на двата уреди, синхронизацијата запира за тој запис наместо да избрише податоци.</p>'+
 '<button id="marKFullSyncNow" type="button">↻ Синхронизирај сè</button> <span id="markReplicaStatus" style="font-size:12px">Подготвено.</span><div id="markReplicaConflicts" style="display:none;white-space:pre-wrap;border:1px solid #be8b45;border-radius:8px;padding:10px;margin-top:9px"></div>';
 if(PC){
  const details=document.createElement('details');details.style.cssText='border-top:1px solid #e5d6b5;margin-top:8px;padding-top:9px';
  details.innerHTML='<summary>Безбеден прв пренос преку TD Booking JSON копија (ако Wi-Fi Sync не префрла)</summary><p class="muted">На телефонот отвори Целосна копија, копирај го JSON текстот и залепи го тука. Се увезуваат само свадбите, финансиите и пакетите — не и лозинки, Firebase токени или Cloud профили.</p><textarea id="markImportBackup" placeholder="Залепи целосна TD Booking JSON копија" style="width:100%;height:90px;min-height:75px"></textarea><button type="button" id="markImportBackupDo">Увези во PC и синхронизирај</button>';
  top.append(details);
  details.querySelector('#markImportBackupDo').onclick=()=>{
   try{const o=JSON.parse(details.querySelector('#markImportBackup').value);const bs=Array.isArray(o)?o:o.bookings;
    if(!Array.isArray(bs)||!bs.every(x=>x&&typeof x.id==='string')||!o.finance||typeof o.finance!=='object')
      throw Error('Очекувам TD Booking целосна JSON копија со свадби и финансии');
    const existing=snap(),merged={...existing.bookings},incoming=o.finance;
    if(Array.isArray(incoming)||!incoming||typeof incoming!=='object')throw Error('Недостасуваат финансии во JSON копијата');
    for(const b of bs){if(Object.prototype.hasOwnProperty.call(merged,b.id)&&!eq(merged[b.id],b))throw Error('Свадба со истиот ID е изменета на PC: '+b.id+'. Не заменувам податоци автоматски.');merged[b.id]=b;}
    const mergedFin={...existing.finance};
    for(const [id,f] of Object.entries(incoming)){if(Object.prototype.hasOwnProperty.call(mergedFin,id)&&!eq(mergedFin[id],f))throw Error('Финансиски конфликт за свадба '+id+'. Нема бришење.');mergedFin[id]=f;}
    if(!confirm('Безбедно додади '+bs.length+' свадби и нивните финансии на PC? Нема да се бришат постојните записи.'))return;
    localStorage.setItem('tdMarKBeforeManualImport_'+Date.now(),JSON.stringify({bookings:saved(BOOK_KEY,[]),finance:saved(FIN_KEY,{})}));
    localStorage.setItem(BOOK_KEY,JSON.stringify(Object.values(merged)));localStorage.setItem(FIN_KEY,JSON.stringify(mergedFin));
    if(typeof data!=='undefined')data.splice(0,data.length,...Object.values(merged));
    if(typeof finance!=='undefined')finance=mergedFin;
    if(typeof render==='function')render();details.querySelector('#markImportBackup').value='';sync(true);
   }catch(e){alert('Увозот не успеа: '+e.message)}
  };
  const fileBox=document.createElement('details');fileBox.style.cssText='border-top:1px solid #e5d6b5;padding:9px 0';
  fileBox.innerHTML='<summary><b>💾 Вистински Backup.json — отвори / зачувај датотека</b></summary><p class="muted">Пренос без Firebase. Новите записи се додаваат, изменетите со ист ID одат на рачна проверка. Не се бришат старите податоци.</p><input id="markBackupFile" type="file" accept=".json,application/json" style="max-width:100%"><button id="markBackupImportFile" type="button">Прегледај и увези Backup.json</button> <button id="markBackupExportFile" type="button">Зачувај PC Backup.json</button><p id="markBackupFileStatus" class="muted"></p>';
  top.append(fileBox);
  fileBox.querySelector('#markBackupImportFile').onclick=()=>importFile(fileBox);
  fileBox.querySelector('#markBackupExportFile').onclick=()=>exportFile(fileBox);
  const crew=document.createElement('div');crew.id='markReplicaRosterList';crew.style.cssText='font-size:13px;color:#41645c;padding:9px 0';top.append(crew);
  const team=document.createElement('details');team.style.cssText='border-top:1px solid #e5d6b5;padding:11px 0';
  team.innerHTML='<summary><strong>👥 Локална екипа — без Firebase</strong></summary><p class="muted">Локалните профили се независни од Cloud. Хонорарите и исплатите внесувај ги во Финансии → Екипа, по свадба.</p><div id="markLocalStaff"></div><button id="markLocalStaffAdd" type="button">＋ Додај член рачно</button>';
  top.append(team);team.querySelector('#markLocalStaffAdd').onclick=()=>editEmployee(null);
  const diag=document.createElement('details');diag.style.cssText='border-top:1px solid #e5d6b5;padding:10px 0';
  diag.innerHTML='<summary>🔎 Провери ја вистинската синхронизација</summary><button id="markDiagNow" type="button">Провери телефон / целосни финансии</button><pre id="markDiagText" style="white-space:pre-wrap;overflow-wrap:anywhere;font-size:12px"></pre>';
  top.append(diag);diag.querySelector('#markDiagNow').onclick=diagnose;

 }
 const header=document.querySelector('header');
 (header?.parentElement||document.body||document.documentElement).insertBefore(top,header?header.nextSibling:null);
 const action=top.querySelector('#marKFullSyncNow');
 action.addEventListener('click',()=>{
   setStatus('Кликот е примен · проверувам PC синхронизација…');
   sync(true);
 });
 if(!PC&&!NATIVE)setStatus('Потребна е нова TD Booking APK со целосна синхронизација.',true);
}
function applyLocal(records,previous,current,ops,conflictSet){
 let changed=false,latest=JSON.parse(JSON.stringify(previous));let next={...current};
 for(const t of TYPE){next[t]={...current[t]};latest.records[t]??={};
  const server=records[t]||{};
  for(const id of new Set([...Object.keys(server),...Object.keys(latest.records[t])])){
   const key=t+'|'+id,remote=server[id]||null,prior=latest.records[t][id]||null;
   if(conflictSet.has(key))continue;
   const was=Object.prototype.hasOwnProperty.call(current[t]||{},id)?current[t][id]:null;
   const transmitted=ops.find(x=>x.kind===t&&x.id===id);
   if(!eq(was,transmitted?transmitted.data:(prior?prior.data:null)))continue; // local editor changed while network in flight
   if(!remote)continue; // Missing server row is never permission to delete local data.
   const value=remote.data??null;
   if(!eq(was,value)){if(value===null)delete next[t][id];else next[t][id]=value;changed=true;}
   if(remote)latest.records[t][id]=remote;else delete latest.records[t][id];
  }
 }
 if(changed){
   // Apply independently and atomically to WebView storage, then redraw. Never touch cloud credentials.
   localStorage.setItem(BOOK_KEY,JSON.stringify(Object.values(next.bookings)));
   localStorage.setItem(FIN_KEY,JSON.stringify(next.finance));
   localStorage.setItem(PKG_KEY,JSON.stringify(next.settings?.global||[]));
   if(PC)localStorage.setItem(EMP_KEY,JSON.stringify(next.employees||{}));
   if(typeof data!=='undefined'&&Array.isArray(data)){data.splice(0,data.length,...Object.values(next.bookings))}
   if(typeof finance!=='undefined'){finance=next.finance}
   if(typeof packageSettings!=='undefined')packageSettings=next.settings?.global||[];
   if(typeof PACKAGES!=='undefined'&&Array.isArray(PACKAGES)){
      (next.settings?.global||[]).forEach((p,i)=>{if(PACKAGES[i]&&Number.isFinite(p?.price))PACKAGES[i][1]=p.price});
   }
   // Live team roster is shown in MarK PC but cloud account provisioning remains via real Firebase login.
   if(PC){window.marKTeamRoster=next.roster?.global||{};localStorage.setItem(ROSTER_KEY,JSON.stringify(window.marKTeamRoster));}
   try{if(typeof render==='function')render();else if(typeof financeRefresh==='function')financeRefresh();}catch(e){console.warn('MarK redraw',e)}
   try{if(typeof refreshViews==='function')refreshViews()}catch(_){}
 }
 localStorage.setItem(BASE_KEY,JSON.stringify(latest));
 if(PC){drawRoster();drawEmployees();}
 return changed;
}
async function transport(msg){
 if(PC){
   const controller=new AbortController();
   const timeout=setTimeout(()=>controller.abort(),25000);
   try{
     const res=await fetch('/api/mark/booking/replica',{
       method:'POST',credentials:'same-origin',
       headers:{'X-TD-Client':'TD-v1.4','Content-Type':'application/json'},
       body:JSON.stringify(msg),signal:controller.signal
     });
     const d=await res.json().catch(()=>({error:'Серверот не врати JSON — можно е истечена PIN сесија'}));
     if(res.status===401)throw Error('HTTP 401: потребна е нова MarK PIN најава на PC');
     if(res.status===403)throw Error('HTTP 403: нема дозвола за PC Sync');
     if(!res.ok)throw Error(d.error||'HTTP '+res.status);
     if(!d||!d.snapshot||!Array.isArray(d.applied)||!Array.isArray(d.conflicts))
       throw Error('Невалиден одговор од PC Sync модулот');
     return d;
   }catch(e){if(e?.name==='AbortError')throw Error('PC серверот не одговори во 25 секунди');throw e;}
   finally{clearTimeout(timeout)}
 }
 return new Promise((resolve,reject)=>{
   let handle=setTimeout(()=>{if(pending===holder){pending=null;reject(Error('Телефонот не одговори за 25 секунди. Провери дали е спарен со истиот PC.'))}},25000);
   const holder={resolve:(v)=>{clearTimeout(handle);resolve(v)},reject:(e)=>{clearTimeout(handle);reject(e)}};
   pending=holder;
   try{window.TDNative.marKReplica(JSON.stringify(msg))}catch(e){pending=null;holder.reject(e)}
 });
}
let conflictSnapshot=null;
function drawRoster(){const p=document.getElementById('markReplicaRosterList');if(!p)return;const rows=Object.values(saved(ROSTER_KEY,{}));p.replaceChildren();const headline=document.createElement('b');headline.textContent='👥 Екипа (поврзани профили): '+rows.length;p.append(headline);
 for(const e of rows.slice(0,50)){const item=document.createElement('div');item.textContent=(e.name||e.id)+' · '+(e.role||'соработник')+' · '+(e.active?'Активен':'Неактивен');p.append(item)}
 if(!rows.length){const x=document.createElement('p');x.textContent='Нема синхронизирани Cloud профили. Хонорарите и исплатите се во Финансии → Екипа.';p.append(x)}
}
function writeLocalRecord(kind,id,value){const cur=snap();if(value===null)delete cur[kind][id];else cur[kind][id]=value;
if(kind==='bookings')localStorage.setItem(BOOK_KEY,JSON.stringify(Object.values(cur.bookings)));
if(kind==='finance')localStorage.setItem(FIN_KEY,JSON.stringify(cur.finance));
if(kind==='settings')localStorage.setItem(PKG_KEY,JSON.stringify(cur.settings.global||[]));
if(kind==='roster'&&PC)localStorage.setItem(ROSTER_KEY,JSON.stringify(cur.roster.global||{}));
if(kind==='employees'&&PC)localStorage.setItem(EMP_KEY,JSON.stringify(cur.employees||{}));
if(typeof data!=='undefined'&&kind==='bookings'){data.splice(0,data.length,...Object.values(cur.bookings));}
if(typeof finance!=='undefined'&&kind==='finance')finance=cur.finance;
try{if(typeof render==='function')render()}catch(_){}
if(PC)drawEmployees();
}
function showConflicts(conflicts){const box=document.getElementById('markReplicaConflicts');if(!box)return;
 if(!conflicts.length){box.style.display='none';box.textContent='';return}
 box.style.display='block';box.replaceChildren();const title=document.createElement('p');title.textContent='⚠ '+conflicts.length+' конфликт(и). Ништо не е избришано. Одлучи која верзија да се зачува за секој запис.';box.append(title);
 for(const c of conflicts){const line=document.createElement('div');line.style.cssText='border-top:1px solid #bda770;padding:8px 0';
  const name=document.createElement('b');name.textContent=c.kind+' / '+c.id+(c.reason==='signed_document_is_immutable'?' · ЗАКЛУЧЕН ДОГОВОР':'');line.append(name);
  const theirs=document.createElement('button');theirs.textContent='Прими ја верзијата од другиот уред';theirs.type='button';theirs.className='secondary';theirs.onclick=()=>{if(!confirm('Да ги применам туѓите измени за овој запис? Локалната различна верзија ќе биде заменета.'))return;const rev=conflictSnapshot?.[c.kind]?.[c.id];if(!rev)return;writeLocalRecord(c.kind,c.id,rev.data);const b=base();b.records[c.kind][c.id]=rev;localStorage.setItem(BASE_KEY,JSON.stringify(b));sync(true)};line.append(theirs);
  if(c.reason!=='signed_document_is_immutable'){const mine=document.createElement('button');mine.textContent='Задржи мои измени';mine.type='button';mine.className='secondary';mine.onclick=()=>{if(!confirm('Со ова ќе се замени верзијата на другиот уред со твоите локални измени. Продолжи?'))return;const rev=conflictSnapshot?.[c.kind]?.[c.id];if(!rev)return;const b=base();b.records[c.kind][c.id]=rev;localStorage.setItem(BASE_KEY,JSON.stringify(b));sync(true)};line.append(mine)}
  box.append(line);
 }
}
async function sync(manual=false){
 // Never silently ignore a real user click. No data is changed by a failed call.
 if(busy){if(manual)setStatus('Синхронизацијата веќе е во тек. Почекај до 25 секунди.');return;}
 if(!PC&&!NATIVE){if(manual)setStatus('PC режимот не е препознаен. Отвори TD Booking од MarK Desktop, не од HTML датотека.',true);return;}
 const el=document.activeElement;
 if(!manual&&PC&&el&&['INPUT','TEXTAREA','SELECT'].includes(el.tagName))return;
 busy=true;
 const label=document.getElementById('marKFullSyncNow');
 if(label)label.disabled=true;
 setStatus('Се подготвуваат локалните промени…');
 try{
   const initial=snap(),prior=base(),ops=makeOps(initial,prior);
   setStatus('Синхронизирам '+ops.length+' локални промени…');
   const response=await transport({protocol:2,deviceId:PC?'MarK-PC':'TD-Booking-Android',operations:ops});
   const conflicts=response.conflicts||[];
   conflictSnapshot=response.snapshot||{};showConflicts(conflicts);
   const changed=applyLocal(response.snapshot||{},prior,initial,ops,new Set(conflicts.map(x=>x.kind+'|'+x.id)));
   const when=new Date().toLocaleTimeString('mk-MK',{hour:'2-digit',minute:'2-digit'});
   setStatus((conflicts.length?'⚠ Потребна проверка: '+conflicts.length+' конфликти. ':'✓ Поврзано. ')+
     'Последно: '+when+' · '+(response.applied||[]).length+' испратени'+(changed?', примени нови податоци':'')+
     ' · Сервер: '+Object.keys(response.snapshot?.bookings||{}).length+' свадби / '+Object.keys(response.snapshot?.finance||{}).length+' финансии.',!!conflicts.length);
   stamp=Date.now();
   if(PC){const cache=Object.keys(window.marKTeamRoster||{}).length;
     const ex=document.getElementById('markReplicaRoster');if(ex)ex.textContent='Профили на екипа (информативно): '+cache;
     drawEmployees();}
 }catch(error){
   const msg=error&&error.message?error.message:String(error);
   setStatus('Нема синхронизација: '+msg+'. Локалните податоци се зачувани.',true);
   console.error('MarK Full Sync error:',error);
 }finally{busy=false;if(label)label.disabled=false;}
}
async function importFile(panel){
 const status=panel.querySelector('#markBackupFileStatus'),input=panel.querySelector('#markBackupFile');
 const file=input?.files?.[0];if(!file){status.textContent='Избери .json датотека прво.';return}
 if(file.size>16*1024*1024){status.textContent='Датотеката е преголема (максимум 16 MB).';return}
 try{
  const obj=JSON.parse(await file.text());
  if(!['TD-Booking-backup','TD-Booking-FULL-backup'].includes(obj?.format))throw Error('Не е TD Booking Backup');
  if(!Array.isArray(obj.bookings)||!obj.finance||Array.isArray(obj.finance)||typeof obj.finance!=='object')throw Error('Недостасуваат финансии или свадби');
  if(!obj.bookings.every(b=>b&&typeof b.id==='string'&&b.id))throw Error('Свадба без ID');
  const original=snap(),books={...original.bookings},fin={...original.finance};
  let next=0;
  for(const b of obj.bookings){if(b.id in books){if(!eq(books[b.id],b))throw Error('Различна верзија на свадбата '+b.id+' — рачна проверка, нема пребришување');}
   else{books[b.id]=b;next++}}
  for(const [id,v] of Object.entries(obj.finance)){if(id in fin){if(!eq(fin[id],v))throw Error('Различна верзија на финансии за '+id+' — рачна проверка');}else{fin[id]=v;next++}}
  const nr=Object.keys(books).length,nf=Object.keys(fin).length;
  if(!confirm('Увези '+next+' НОВИ записи? Вкупно: '+nr+' свадби и '+nf+' финансиски ставки. Постојните податоци нема да се пребришат.'))return;
  // Single pre-import snapshot, then merge. Never imports tokens / cloud config.
  localStorage.setItem('tdMarKSafeBackupBeforeImport_'+Date.now(),JSON.stringify({bookings:saved(BOOK_KEY,[]),finance:saved(FIN_KEY,{})}));
  localStorage.setItem(BOOK_KEY,JSON.stringify(Object.values(books)));localStorage.setItem(FIN_KEY,JSON.stringify(fin));
  if(typeof data!=='undefined'&&Array.isArray(data))data.splice(0,data.length,...Object.values(books));
  if(typeof finance!=='undefined')finance=fin;
  try{if(typeof render==='function')render()}catch(_){}
  status.textContent='Локално увезено: '+next+' нови записи. Синхронизирам со MarK LAN базата…';
  await sync(true);
 }catch(e){status.textContent='Нема промени. '+e.message}
}
function exportFile(panel){
 try{
  const a=snap(),obj={format:'TD-Booking-backup',version:3,exported:new Date().toISOString(),bookings:Object.values(a.bookings),finance:a.finance,packageSettings:a.settings.global};
  const content=JSON.stringify(obj,null,2);const blob=new Blob([content],{type:'application/json'});
  const url=URL.createObjectURL(blob),link=document.createElement('a');link.href=url;link.download='MarK_PC_TD_Booking_'+new Date().toISOString().slice(0,10)+'.json';link.click();
  setTimeout(()=>URL.revokeObjectURL(url),12000);
  panel.querySelector('#markBackupFileStatus').textContent='Се презема датотека со '+obj.bookings.length+' свадби и '+Object.keys(obj.finance).length+' финансиски записи.';
 }catch(e){panel.querySelector('#markBackupFileStatus').textContent='Грешка: '+e.message}
}
function editEmployee(id){
 if(!PC)return;
 const current=saved(EMP_KEY,{}),e=id?current[id]:null;
 const name=prompt('Име и презиме на членот:',e?.name||'');if(name===null)return;
 if(!name.trim())return alert('Името е задолжително.');
 const role=prompt('Улога (фотограф, видео, дрон, монтажа...):',e?.role||'Фотограф');if(role===null)return;
 const phone=prompt('Телефон (незадолжително):',e?.phone||'');if(phone===null)return;
 const safeId=id||('td-'+Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,8));
 const record={id:safeId,name:name.trim().slice(0,180),role:role.trim().slice(0,180),phone:phone.trim().slice(0,180),active:e?.active!==false,notes:e?.notes||''};
 writeLocalRecord('employees',safeId,record);sync(true);
}
function drawEmployees(){
 if(!PC)return;const mount=document.getElementById('markLocalStaff');if(!mount)return;mount.replaceChildren();
 const people=Object.values(saved(EMP_KEY,{}));
 if(!people.length){const p=document.createElement('p');p.textContent='Сѐ уште нема локални профили. Додај член без Firebase.';mount.append(p)}
 for(const e of people){if(!e||typeof e!=='object')continue;
 const line=document.createElement('div');line.style.cssText='display:flex;align-items:center;gap:8px;padding:5px 0;flex-wrap:wrap';
 const name=document.createElement('span');name.textContent=(e.name||'Без име')+' · '+(e.role||'соработник')+(e.active===false?' · Неактивен':'');line.append(name);
 const edit=document.createElement('button');edit.type='button';edit.textContent='Измени';edit.onclick=()=>editEmployee(e.id);line.append(edit);
 const toggle=document.createElement('button');toggle.type='button';toggle.textContent=e.active===false?'Активирај':'Деактивирај';toggle.onclick=()=>{writeLocalRecord('employees',e.id,{...e,active:e.active===false});sync(true)};line.append(toggle);
 mount.append(line);
 }
}
async function diagnose(){
 if(!PC)return;const target=document.getElementById('markDiagText');if(!target)return;target.textContent='Се проверува…';
 try{
  const [rep,legacy]=await Promise.all([fetch('/api/mark/booking/replica/status',{credentials:'same-origin'}).then(r=>r.json()),fetch('/api/mark/sync/status',{credentials:'same-origin'}).then(r=>r.json())]);
  const counts=rep.counts||{};const old=(legacy.bookings||[]).length;
  const locally=snap();const report={oldMetadataBookings:old,fullSyncWeddings:counts.bookings||0,fullSyncFinance:counts.finance||0,fullSyncTeam:counts.employees||0,pcLocalWeddings:Object.keys(locally.bookings).length,pcLocalFinance:Object.keys(locally.finance).length,connectedPhones:(legacy.devices||[]).length,lanServer:!!legacy.lan?.running};
  const text='MarK — локална дијагностика (без имиња и финансиски износи)\n'+Object.entries(report).map(([k,v])=>k+': '+v).join('\n');
  target.textContent=text+'\n\n'+(old>0&&!(counts.bookings||0)?'ВНИМАНИЕ: Свадбите се само во стариот канал. На телефон стартувај „Синхронизирај сè“ или увези целосна Booking копија.':'Провери дали бројот на финансии одговара на бројот на свадби.');
 }catch(e){target.textContent='Не може да се прочита статус: '+e.message}
}
window.tdMarKReplicaResult=(message)=>{if(pending){let cb=pending;pending=null;if(!message.ok)cb.reject(Error(message.error||'LAN грешка'));else cb.resolve(message)}};
// User sees sync state, and can choose to reconcile conflicts without destructive auto-merge.
function initialize(){
 if(document.getElementById('marKFullSyncPanel'))return;
 try{renderLink();if(PC){drawRoster();drawEmployees();}}
 catch(e){console.error('MarK Sync UI initialization failed',e);return;}
 if(PC)setTimeout(()=>sync(false),1500);
 else setTimeout(()=>{let info=json(window.TDNative?.marKInfo?.()||'{}',{});if(info.paired)sync(false)},2200);
 timer=setInterval(()=>{if(!document.hidden){if(PC)sync(false);else{let info=json(window.TDNative?.marKInfo?.()||'{}',{});if(info.paired)sync(false)}}},45000);
 document.addEventListener('visibilitychange',()=>{if(!document.hidden&&Date.now()-stamp>10000)sync(false)});
 window.tdFullSync=()=>sync(true);
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',initialize,{once:true});
else initialize();
})();
