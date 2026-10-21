/* MarK v1.7.0: one organism: one selected wedding, original TD Studio inside MarK. */
(()=>{'use strict';
const el=id=>document.getElementById(id);
const existingChat=el('page-home').querySelector('.chat-card');
const homeChatParent=existingChat.parentElement;
const chatSlot=el('markChatSlot');const dock=el('markChatDock');
const unified=el('markUnifiedProject');const original=el('project');
const marker={'X-TD-Client':'TD-v1.4','Content-Type':'application/json'};
let lastWorkspace=null;let tdFrame=null;let active='home';
const panels=new Set(['tdstudio','director','video','photo']);
async function send(url,data){const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),12000);try{const r=await fetch(url,{method:data?'POST':'GET',credentials:'same-origin',headers:marker,body:data?JSON.stringify(data):undefined,cache:'no-store',signal:controller.signal});const o=await r.json().catch(()=>({error:'Непознат одговор'}));if(!r.ok)throw Error(o.error||'HTTP '+r.status);return o;}finally{clearTimeout(timer);}}
function desktop(){if(!tdFrame){tdFrame=document.createElement('iframe');tdFrame.id='markTDFrame';tdFrame.src='/';tdFrame.title='Оригинален TD AI Studio — вграден во MarK';tdFrame.loading='lazy';tdFrame.setAttribute('referrerpolicy','same-origin');}return tdFrame;}
function workArea(page){const frame=desktop();if(typeof window.markAttachWorkFrame==='function'){if(!frame.dataset.workspaceAttached){window.markAttachWorkFrame(frame);frame.dataset.workspaceAttached='1';}}else{const x=page==='tdstudio'?el('tdNativeSlot'):el('tdNative-'+page);if(x&&!x.contains(frame))x.append(frame);}}
function moveChat(page){if(page==='home'){if(!homeChatParent.contains(existingChat))homeChatParent.prepend(existingChat);dock.classList.remove('show');}else{if(!chatSlot.contains(existingChat))chatSlot.append(existingChat);}}
window.markBeforeNavigate=page=>{active=page;moveChat(page);if(panels.has(page))workArea(page);if(panels.has(page)||page==='booking')refresh();};
function labels(s){const x=s?.workspace||{};const m=x.media||{};
 const title=x.found?(x.status==='WAITING_FOR_MEDIA'?'ЧЕКА СНИМКИ':'МАТЕРИЈАЛИТЕ СЕ ИНДЕКСИРАНИ'):'НЕМА HDD ПРОЕКТ';
 const desc=['Проект: '+(s?.selected||'Општо'),'HDD: '+(x.path||'Сè уште не е избрана папка'),
     'Видео фајлови: '+(m.videos||0),'Фотографии: '+(m.photos||0),
     'Статус: '+title,'Анализа: преглед на фајлови, без потврдена AI обработка'];
 return desc.join('\n');}
let pendingRefresh=null;
async function refresh(){if(pendingRefresh)return pendingRefresh;pendingRefresh=(async()=>{try{const s=await send('/api/mark/unified/status');lastWorkspace=s;
 const names=s.projects||['Општо'];const changed=JSON.stringify(Array.from(unified.options,o=>o.value))!==JSON.stringify(names);if(changed){const children=names.map(n=>{const o=document.createElement('option');o.value=n;o.textContent=n;return o;});unified.replaceChildren(...children);}
 if(document.activeElement!==unified)unified.value=names.includes(s.selected)?s.selected:'Општо';
 if(original&&original.value!==unified.value){if(!Array.from(original.options).some(x=>x.value===unified.value)){const opt=document.createElement('option');opt.value=unified.value;opt.textContent=unified.value;original.add(opt);}original.value=unified.value;}
 const h=s.workspace||{};el('markUnifiedState').textContent=h.found?(h.status==='WAITING_FOR_MEDIA'?'Чека материјали':'Проектот е индексиран'):'Избери Booking свадба за HDD';
 for(const name of ['director','video','photo']){const box=el('unifiedMedia-'+name);if(box)box.textContent=labels(s);}
 if(!s.endpoints?.['/api/mark/sync/status']){const diag=el('syncDiag');if(diag)diag.textContent='Booking Sync backend недостасува. Затвори го MarK и серверот и стартувај ја проверката на ажурирањето. Не увезувај JSON наместо APK.';}
 return s;
 }catch(e){el('markUnifiedState').textContent='⚠ '+e.message;return null;}finally{pendingRefresh=null;}})();return pendingRefresh;}
async function choose(name){try{if(pendingRefresh)await pendingRefresh;await send('/api/mark/unified/select',{project:name});if(original){if(!Array.from(original.options).some(o=>o.value===name)){const opt=document.createElement('option');opt.value=name;opt.textContent=name;original.add(opt);}original.value=name;}
 localStorage.setItem('mark-active-project',name);if(typeof window.onProjectChanged==='function')window.onProjectChanged();else if(typeof onProjectChanged==='function')onProjectChanged();await refresh();
 }catch(e){el('markUnifiedState').textContent='⚠ '+e.message;}}
window.markPickProject=async name=>{await refresh();await choose(name);};
unified.addEventListener('change',()=>choose(unified.value));
original.addEventListener('change',()=>{const n=original.value;if(n!==unified.value)choose(n)});
el('markUnifiedChat').onclick=()=>{if(active==='home'){el('prompt')?.focus();window.scrollTo({top:0,behavior:'smooth'});}
 else {moveChat(active);dock.classList.toggle('show');if(dock.classList.contains('show'))el('prompt')?.focus();}};
el('markDockClose').onclick=()=>dock.classList.remove('show');
el('markUnifiedReload').onclick=refresh;el('markUnifiedWedding').onclick=()=>navigate('booking');
// All previously redirecting buttons now switch inside MarK, never leave the desktop window.
for(const [id,page] of Object.entries({weddingQuick:'director',tdQuick:'tdstudio',photoQuick:'photo',weddingTile:'director',tdTile:'tdstudio',photoTile:'photo',directorOpen:'director',videoOpen:'video',assistantToChat:'home',mStudio:'tdstudio'})){
 const btn=el(id);if(btn)btn.onclick=()=>navigate(page);
}
// Preserve dedicated native Lightroom launch if the user explicitly presses its button.
// Embed original TD panel on demand. A frame uses current same-origin PIN session.
let initialized=false;
const ready=async()=>{if(initialized)return;initialized=true;await refresh();};
ready();setInterval(()=>{if(document.visibilityState==='visible')refresh()},25000);
})();
