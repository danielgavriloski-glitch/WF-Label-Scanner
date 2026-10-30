/* MarK Booking full two-way LAN replica v2. Phone and desktop share this protocol.
   Phone keeps all existing WebView/localStorage data; PC runs the same Booking UI.
   Per-wedding optimistic revisions reject concurrent modification; do not silently overwrite.
*/
(()=>{'use strict';
const PC=location.pathname==='/api/mark/booking/app';
const NATIVE=!!(window.TDNative&&typeof window.TDNative.marKReplica==='function');
const BASE_KEY='tdMarKReplicaV2_Base';
const BOOK_KEY='tdBookingsV1',FIN_KEY='tdBookingFinanceV1',PKG_KEY='tdPackageSettings',CACHE_KEY='tdCloudCacheV1',ROSTER_KEY='tdMarKRemoteRosterV2';
const TYPE=['bookings','finance','settings','roster'];
let busy=false, pending=null, stamp=0, timer=null;
function json(s,fallback){try{return JSON.parse(s)}catch(_){return fallback}}
function saved(key,fallback){return json(localStorage.getItem(key),fallback)}
function snap(){
 const a=saved(BOOK_KEY,[]),fin=saved(FIN_KEY,{}),packages=saved(PKG_KEY,[]),cache=saved(CACHE_KEY,{});
 const bookings={};for(const b of (Array.isArray(a)?a:[]))if(b&&typeof b.id==='string')bookings[b.id]=b;
 const roster={};for(const [id,e] of Object.entries(PC?saved(ROSTER_KEY,{}):cache?.employees||{}))if(e&&typeof e==='object')roster[id]={id,name:String(e.name||''),role:String(e.role||''),status:String(e.status||''),active:!!e.active};
 return {bookings,finance:(fin&&typeof fin==='object'&&!Array.isArray(fin)?fin:{}),settings:{global:Array.isArray(packages)?packages:[]},roster:{global:roster}};
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
 '<p class="muted" style="margin:0 0 9px">'+(PC?'MarK PC ↔ TD Booking Android':'Телефон ↔ MarK PC')+' · настани, договори, финансии и исплати. Само на локална поврзана мрежа. Ако истиот запис е сменет на двата уреди, синхронизацијата запира за тој запис наместо да избрише податоци.</p>'+
 '<button id="marKFullSyncNow" type="button">↻ Синхронизирај сè</button> <span id="markReplicaStatus" style="font-size:12px">Подготвено.</span><div id="markReplicaConflicts" style="display:none;white-space:pre-wrap;border:1px solid #be8b45;border-radius:8px;padding:10px;margin-top:9px"></div>';
 if(PC){const crew=document.createElement('div');crew.id='markReplicaRosterList';crew.style.cssText='font-size:13px;color:#41645c;padding:9px 0';top.append(crew)}
 const header=document.querySelector('header');header?.insertAdjacentElement('afterend',top);
 document.getElementById('marKFullSyncNow').onclick=()=>sync(true);
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
   // An absent server record is never a deletion instruction. Only explicit tombstones may remove data.
   if(!remote)continue;
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
 if(PC)drawRoster();
 return changed;
}
async function transport(msg){
 if(PC){const res=await fetch('/api/mark/booking/replica',{method:'POST',credentials:'same-origin',headers:{'X-TD-Client':'TD-v1.4','Content-Type':'application/json'},body:JSON.stringify(msg)});const d=await res.json();if(!res.ok)throw Error(d.error||'HTTP '+res.status);return d;}
 return new Promise((resolve,reject)=>{
   const holder={resolve,reject};
   const timeout=setTimeout(()=>{if(pending===holder){pending=null;reject(Error('MarK не одговори за 30 секунди — провери LAN и спарување.'))}},30000);
   holder.resolve=(v)=>{clearTimeout(timeout);resolve(v)};
   holder.reject=(e)=>{clearTimeout(timeout);reject(e)};
   pending=holder;
   try{window.TDNative.marKReplica(JSON.stringify(msg))}catch(e){pending=null;holder.reject(e)}
 });
}
let conflictSnapshot=null;
function drawRoster(){
 const p=document.getElementById('markReplicaRosterList');if(!p)return;
 const bookings=saved(BOOK_KEY,[]),fin=saved(FIN_KEY,{}),cloud=saved(ROSTER_KEY,{});
 const records=Array.isArray(bookings)?bookings:[];
 const finance=fin&&typeof fin==='object'&&!Array.isArray(fin)?fin:{};
 const groups=new Map();let engagements=0;
 const allRecords=[...records,{id:'__td_manual_general_crew_v1__',bride:'Општ',groom:'ангажман'}];
 for(const w of allRecords){
  if(!w||!w.id)continue;
  const people=Array.isArray(finance[w.id]?.crew)?finance[w.id].crew:[];
  for(const c of people){
   if(!c||typeof c!=='object')continue;
   const name=String(c.name||'Непознат соработник').trim();
   const key=(name.toLocaleLowerCase('mk-MK')+'|'+String(c.phone||'').trim());
   const fee=Number(c.fee)||0;
   const paid=(Array.isArray(c.payments)?c.payments:[]).reduce((s,x)=>s+(Number(x?.amount)||0),0);
   const g=groups.get(key)||{name,role:String(c.role||''),fee:0,paid:0,weddings:new Set(),entries:0};
   g.fee+=fee;g.paid+=paid;g.entries++;g.weddings.add(w.eventType&&w.eventType!=='wedding'?String(w.bride||'Настан'):String(w.bride||'')+' и '+String(w.groom||''));
   groups.set(key,g);engagements++;
  }
 }
 p.replaceChildren();
 const title=document.createElement('b');title.textContent='Екипа · локални ангажмани и исплати';p.append(title);
 const desc=document.createElement('div');desc.style.cssText='margin:5px 0 8px';
 desc.textContent='Соработници: '+groups.size+' · Ангажмани: '+engagements+
 ' · Договорено: '+[...groups.values()].reduce((s,x)=>s+x.fee,0).toFixed(2)+' €'+
 ' · Исплатено: '+[...groups.values()].reduce((s,x)=>s+x.paid,0).toFixed(2)+' €';
 p.append(desc);
 for(const g of [...groups.values()].sort((a,b)=>a.name.localeCompare(b.name,'mk-MK')).slice(0,100)){
  const item=document.createElement('div');item.style.cssText='padding:7px 0;border-top:1px solid #d6e6df';
  const head=document.createElement('b');head.textContent=g.name+(g.role?' · '+g.role:'');item.append(head);
  const summary=document.createElement('div');
  summary.textContent=g.entries+' ангажман(и) · Хонорар '+g.fee.toFixed(2)+' € · Исплатено '+g.paid.toFixed(2)+' € · За исплата '+Math.max(0,g.fee-g.paid).toFixed(2)+' €';
  item.append(summary);p.append(item);
 }
 if(!engagements){
  const note=document.createElement('p');note.textContent='На овој PC нема примени финансиски ангажмани. Провери „Синхронизирај сè“ на телефонот и PC. Не прави празен увоз што би ги заменил телефонските податоци.';p.append(note);
 }
 const roster=cloud&&typeof cloud==='object'?Object.values(cloud):[];
 const cloudHeading=document.createElement('div');cloudHeading.style.cssText='margin-top:10px;font-weight:bold';
 cloudHeading.textContent='Cloud профили (одделно): '+roster.length;p.append(cloudHeading);
 if(!roster.length){const note=document.createElement('div');note.textContent='За локални хонорари не е потребен Cloud.';p.append(note)}
 else for(const e of roster.slice(0,50)){const item=document.createElement('div');item.textContent=(e.name||e.id)+' · '+(e.role||'соработник')+' · '+(e.active?'Активен':'Неактивен');p.append(item)}
}
function writeLocalRecord(kind,id,value){const cur=snap();if(value===null)delete cur[kind][id];else cur[kind][id]=value;
if(kind==='bookings')localStorage.setItem(BOOK_KEY,JSON.stringify(Object.values(cur.bookings)));
if(kind==='finance')localStorage.setItem(FIN_KEY,JSON.stringify(cur.finance));
if(kind==='settings')localStorage.setItem(PKG_KEY,JSON.stringify(cur.settings.global||[]));
if(kind==='roster'&&PC)localStorage.setItem(ROSTER_KEY,JSON.stringify(cur.roster.global||{}));
if(typeof data!=='undefined'&&kind==='bookings'){data.splice(0,data.length,...Object.values(cur.bookings));}
if(typeof finance!=='undefined'&&kind==='finance')finance=cur.finance;
try{if(typeof render==='function')render()}catch(_){}
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
async function sync(manual=false){if(busy||(!PC&&!NATIVE))return;const el=document.activeElement;
 if(!manual&&PC&&el&&['INPUT','TEXTAREA','SELECT'].includes(el.tagName))return; // never clobber a form being edited
 busy=true;let label=document.getElementById('marKFullSyncNow');if(label)label.disabled=true;
 let initial=snap(),prior=base(),ops=makeOps(initial,prior);
 setStatus('Синхронизирам '+ops.length+' локални промени…');
 try{const response=await transport({protocol:2,deviceId:PC?'MarK-PC':'TD-Booking-Android',operations:ops});
 const conflicts=response.conflicts||[];conflictSnapshot=response.snapshot||{};showConflicts(conflicts);
 const changed=applyLocal(response.snapshot||{},prior,initial,ops,new Set(conflicts.map(x=>x.kind+'|'+x.id)));
 const when=new Date().toLocaleTimeString('mk-MK',{hour:'2-digit',minute:'2-digit'});
 setStatus((conflicts.length?'⚠ Потребна проверка: '+conflicts.length+' конфликти. ':'✓ Поврзано. ')+
   'Последно: '+when+' · '+(response.applied||[]).length+' прифатени измени'+
   (changed?', преземени нови податоци':'')+
   ' · На MarK сервер: '+Object.keys(response.snapshot?.bookings||{}).length+' настани / '+Object.keys(response.snapshot?.finance||{}).length+' финансии',!!conflicts.length);
 if(PC){const cache=Object.keys(window.marKTeamRoster||{}).length;const ex=document.getElementById('markReplicaRoster');if(ex)ex.textContent='Профили на екипа (информативно): '+cache}
 stamp=Date.now();}
 catch(error){setStatus('Нема синхронизација: '+error.message+'. Локалните податоци се зачувани.',true)}
 finally{busy=false;if(label)label.disabled=false}
}
window.tdMarKReplicaResult=(message)=>{if(pending){let cb=pending;pending=null;if(!message.ok)cb.reject(Error(message.error||'LAN грешка'));else cb.resolve(message)}};
// User sees sync state, and can choose to reconcile conflicts without destructive auto-merge.
renderLink();if(PC)drawRoster();
if(PC)setTimeout(()=>sync(false),1500);
else setTimeout(()=>{let info=json(window.TDNative?.marKInfo?.()||'{}',{});if(info.paired)sync(false)},2200);
timer=setInterval(()=>{if(!document.hidden){if(PC)sync(false);else{let info=json(window.TDNative?.marKInfo?.()||'{}',{});if(info.paired)sync(false)}}},45000);
document.addEventListener('visibilitychange',()=>{if(!document.hidden&&Date.now()-stamp>10000)sync(false)});
window.tdFullSync=()=>sync(true);
})();