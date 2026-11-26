(function(){
'use strict';
const log=()=>state.pickupLog||Pickups.fresh();
let visibleLimit=40;
const statusLabel=item=>item.legacyStatus==='delivered'?'Испорачана':item.status==='picked_up'?'Подигната':item.pickedPackages>0?'Делумно подигната':'Чека подигање';
function extraDetails(item){return [item.packages!==undefined?item.pickedPackages+' / '+item.packages+' пакети':'',item.weightKg!==undefined&&item.weightKg!==null?item.weightKg+' kg':'',item.dueDate?'За подигање: '+Core.displayDate(item.dueDate):''].filter(Boolean).map(esc).join(' · ');}
function notificationReady(){return typeof Android==='undefined'||!Android.pickupNotificationsEnabled||Android.pickupNotificationsEnabled();}
function requestNotifications(){if(typeof Android!=='undefined'&&Android.requestPickupNotifications)Android.requestPickupNotifications();}
function form(id){
 const old=id?log().items.find(x=>x.id===id):null;
 if(id&&!old){toast('Записот не е пронајден.');return;}
 const item=old||{from:'',goods:'',address:'',note:''};
 showModal(old?'Измени роба за подигање':'Додај роба за подигање',`<form id="pickupForm">
 <label class="field"><span>Од каде · фирма / магацин</span><input id="pickupFrom" required maxlength="200" value="${esc(item.from)}" placeholder="На пример: Јуба, Скопје"></label>
 <label class="field"><span>Што треба да подигнам</span><textarea id="pickupGoods" required maxlength="2000" placeholder="На пример: 3 пакети текстил">${esc(item.goods)}</textarea></label>
 <label class="field"><span>Адреса / локација · по избор</span><input id="pickupAddress" maxlength="500" value="${esc(item.address)}"></label>
 <label class="field"><span>Белешка · по избор</span><textarea id="pickupNote" maxlength="4000">${esc(item.note)}</textarea></label>
 ${item.legacyCargoId?`<label class="field"><span>Датум за подигање · по избор</span><input id="pickupDueDate" type="date" value="${esc(item.dueDate||'')}"></label><div class="grid"><label class="field"><span>Вкупно пакети</span><input id="pickupPackages" type="number" min="${item.pickedPackages}" step="1" value="${item.packages}"></label><label class="field"><span>Тежина (kg) · по избор</span><input id="pickupWeight" type="number" min="0" step="0.01" value="${item.weightKg==null?'':item.weightKg}"></label></div><p class="small muted">Веќе подигнати: ${item.pickedPackages} пакети. Поврзаните документи се зачувани.</p>`:''}
 <p class="hint">Дневен потсетник околу 08:00, сè додека не притиснеш „Подигната“.</p>
 <div id="pickupError" class="error-text" role="alert"></div><button class="btn full" type="submit">Зачувај</button></form>`);
 $('pickupForm').onsubmit=event=>{
  event.preventDefault();
  try{
   const fields={id:old&&old.id,from:$('pickupFrom').value,goods:$('pickupGoods').value,address:$('pickupAddress').value,note:$('pickupNote').value};
   if(item.legacyCargoId)Object.assign(fields,{dueDate:$('pickupDueDate').value,packages:Number($('pickupPackages').value),weightKg:$('pickupWeight').value===''?null:Number($('pickupWeight').value)});
   const result=Pickups.put(log(),fields);
   const next=JSON.parse(JSON.stringify(state));next.pickupLog=result.log;
   commit(Pickups.syncLegacy(next,result.item.id),'Роба за подигање: '+result.item.from);closeModal();
   toast('Зачувано. Потсетникот продолжува до подигањето.');
   if(!notificationReady())requestNotifications();
  }catch(error){$('pickupError').textContent=error.message;}
 };
}
function complete(id){
 try{
  const result=Pickups.complete(state,id);
  if(result.changed)commit(result.state,'Подигната роба · префрлена во Документи');
  WFDocuments.setFilters({query:'',from:'',to:'',warehouseId:'',action:'pickup',deleted:false});
  WFDocuments.openVisit(result.visitId);
  toast('Подигната. Записот е во Документи; потсетникот за него престана.');
 }catch(error){toast(error.message);}
}
function collectPackages(id){
 const item=log().items.find(x=>x.id===id);if(!item)return;
 const remaining=item.packages-item.pickedPackages;
 if(!Number.isSafeInteger(remaining)||remaining<1){complete(id);return;}
 showModal('Подигнати пакети',`<form id="pickupPackagesForm"><p class="pickup-text">${esc(item.from)} · ${esc(item.goods)}</p><label class="field"><span>Колку пакети подигна? Преостанати: ${remaining}</span><input id="pickupCount" type="number" min="1" max="${remaining}" step="1" value="${remaining}" required></label><div id="pickupCountError" class="error-text" role="alert"></div><button class="btn full" type="submit">Зачувај подигање</button></form>`);
 $('pickupPackagesForm').onsubmit=event=>{event.preventDefault();try{
  const result=Pickups.collect(state,id,Number($('pickupCount').value));
  commit(result.state,result.partial?'Делумно подигната роба: '+item.from:'Подигната роба · префрлена во Документи');closeModal();
  if(result.partial)toast('Пакетите се зачувани. Потсетникот продолжува за преостанатата роба.');
  else{WFDocuments.setFilters({query:'',from:'',to:'',warehouseId:'',action:'pickup',deleted:false});WFDocuments.openVisit(result.visitId);toast('Робата е подигната и зачувана во Документи.');}
 }catch(error){$('pickupCountError').textContent=error.message;}};
}
function showDetails(id){
 const item=log().items.find(x=>x.id===id);
 const cargo=!item&&((state.cargoLog||{}).shipments||[]).find(s=>s.id===id);
 if(!item&&!cargo){toast('Записот не е пронајден.');return;}
 const data=item||{from:cargo.company,goods:cargo.goods,address:cargo.location,note:cargo.notes,packages:cargo.packages,pickedPackages:cargo.pickedPackages,weightKg:cargo.weightKg,dueDate:cargo.dueDate};
 const ids=Array.from(new Set(item?[...(item.legacyVisitIds||[]),item.visitId].filter(Boolean):cargo.visitIds||[]));
 const visits=((state.warehouseLog||{}).visits||[]).filter(v=>ids.includes(v.id));
 const status=item?statusLabel(item):({cancelled:'Откажана',delivered:'Испорачана',picked_up:'Подигната'}[cargo.status]||'Зачуван товар');
 showModal('Роба · '+status,`<section><h3>${esc(data.from)}</h3><p class="pickup-text">${esc(data.goods||'Без внесен опис')}</p><p class="small pickup-legacy-details">${extraDetails(data)}</p>${data.address?'<p class="pickup-text">'+esc(data.address)+'</p>':''}${data.note?'<p class="pickup-text">'+esc(data.note)+'</p>':''}${visits.length?'<h3>Поврзани документи</h3>'+visits.map((v,index)=>'<button class="btn secondary full" style="margin-top:8px" data-pickup-visit="'+esc(v.id)+'">'+esc(Core.displayDate(v.date))+' · '+(v.documents||[]).length+' прилози'+(visits.length>1?' · '+(index+1):'')+'</button>').join(''):'<p class="small muted">Нема поврзан документ во оваа евиденција. Податоците за товарот се зачувани.</p>'}${item&&item.legacyCargoId&&item.status==='picked_up'&&item.legacyStatus!=='delivered'?'<button class="btn full" style="margin-top:14px" id="pickupDelivered">Означи како испорачана</button>':''}</section>`);
 document.querySelectorAll('[data-pickup-visit]').forEach(button=>button.onclick=()=>{closeModal();WFDocuments.setFilters({query:'',from:'',to:'',warehouseId:'',action:'',deleted:false});WFDocuments.openVisit(button.dataset.pickupVisit);});
 if($('pickupDelivered'))$('pickupDelivered').onclick=()=>{try{commit(Pickups.deliver(state,id),'Испорачана роба: '+data.from);showDetails(id);toast('Робата е означена како испорачана.');}catch(error){toast(error.message);}};
}
function openItem(id){
 const item=log().items.find(x=>x.id===id||x.legacyCargoId===id);
 if(item&&item.status==='picked_up'){showDetails(item.id);return true;}
 if(!item){if(((state.cargoLog||{}).shipments||[]).some(s=>s.id===id)){showDetails(id);return true;}toast('Записот за роба не е пронајден.');return false;}
 closeModal();const pending=Pickups.pending(log()),position=pending.findIndex(x=>x.id===item.id);visibleLimit=Math.max(visibleLimit,position+1);
 view='pickups';render();
 const card=Array.from(document.querySelectorAll('[data-pickup-id]')).find(node=>node.dataset.pickupId===item.id);
 if(card){card.classList.add('notification-focus');card.setAttribute('tabindex','-1');card.focus({preventScroll:true});card.scrollIntoView({block:'center',behavior:'smooth'});}
 return true;
}
function archiveMarkup(){
 const completed=log().items.filter(item=>item.status==='picked_up').map(item=>({id:item.id,from:item.from,goods:item.goods,status:statusLabel(item),at:item.pickedAt||item.updatedAt}));
 const mirrored=new Set(log().items.map(item=>item.legacyCargoId).filter(Boolean));
 const old=((state.cargoLog||{}).shipments||[]).filter(s=>!mirrored.has(s.id)&&['cancelled','picked_up','delivered'].includes(s.status)).map(s=>({id:s.id,from:s.company,goods:s.goods,status:{cancelled:'Откажана',picked_up:'Подигната',delivered:'Испорачана'}[s.status],at:String(s.pickedAt||s.updatedAt||s.createdAt||'')}));
 return completed.concat(old).sort((a,b)=>b.at.localeCompare(a.at));
}
function renderPickups(){
 const items=Pickups.pending(log()),ready=notificationReady(),archived=archiveMarkup();
 $('app').innerHTML=header('Роба за подигање',items.length?'Чекаат подигање: '+items.length:'Нема неподигната роба.')+
 `<section class="card"><button class="btn full" id="pickupAdd">${icon('plus')}Додај роба за подигање</button><p class="hint">Потсетник секој ден околу 08:00, и кога апликацијата е затворена. Со „Подигната“ записот се префрла во Документи.</p>${!ready?'<div class="note warn">За дневните потсетници дозволи известувања на телефонот.<button class="btn secondary full" id="pickupPermission">Дозволи известувања</button></div>':''}</section>`+
 (items.length?items.slice(0,visibleLimit).map(item=>`<section class="card pickup-card" data-pickup-id="${esc(item.id)}"><span class="badge manual">${statusLabel(item)}</span><h2>${esc(item.from)}</h2><p class="pickup-text">${esc(item.goods)}</p>${extraDetails(item)?'<p class="small pickup-legacy-details">'+extraDetails(item)+'</p>':''}${item.address?'<p class="small pickup-text">'+esc(item.address)+'</p>':''}${item.note?'<p class="small muted pickup-text">'+esc(item.note)+'</p>':''}<div class="warehouse-tools"><button class="btn" data-pickup-complete="${esc(item.id)}">${item.packages>item.pickedPackages?'Подигни пакети':'Подигната'}</button><button class="btn light" data-pickup-edit="${esc(item.id)}">Измени</button>${(item.legacyVisitIds||[]).length?'<button class="btn light" data-pickup-details="'+esc(item.id)+'">Документи</button>':''}</div></section>`).join(''):'<section class="card"><p class="warehouse-empty">Додај од каде и што треба да се подигне.</p></section>')+
 (items.length>visibleLimit?'<button class="btn secondary full" id="pickupMore">Прикажи уште роба ('+(items.length-visibleLimit)+')</button>':'')+
 '<button class="btn secondary full" id="pickupDocuments">Отвори Документи · подигната роба</button>'+
 (archived.length?'<details class="card pickup-archive" id="pickupArchive" style="margin-top:15px"><summary>Зачувана и подигната роба ('+archived.length+')</summary><div id="pickupArchiveRows"></div></details>':'');
 $('pickupAdd').onclick=()=>form();
 $('pickupDocuments').onclick=()=>WFDocuments.setFilters({query:'',from:'',to:'',warehouseId:'',action:'pickup',deleted:false});
 if($('pickupPermission'))$('pickupPermission').onclick=requestNotifications;
 document.querySelectorAll('[data-pickup-edit]').forEach(b=>b.onclick=()=>form(b.dataset.pickupEdit));
 document.querySelectorAll('[data-pickup-complete]').forEach(b=>b.onclick=()=>collectPackages(b.dataset.pickupComplete));
 document.querySelectorAll('[data-pickup-details]').forEach(b=>b.onclick=()=>showDetails(b.dataset.pickupDetails));
 if($('pickupMore'))$('pickupMore').onclick=()=>{visibleLimit+=40;renderPickups();};
 if($('pickupArchive')){
  let archiveLimit=40;
  const fill=()=>{$('pickupArchiveRows').innerHTML=archived.slice(0,archiveLimit).map(item=>'<div class="pickup-archive-item"><span class="badge">'+esc(item.status)+'</span><h3>'+esc(item.from)+'</h3><p class="small pickup-text">'+esc(item.goods)+'</p><button class="btn light" data-pickup-details="'+esc(item.id)+'">Податоци и документи</button></div>').join('')+(archived.length>archiveLimit?'<button class="btn secondary full" id="pickupArchiveMore">Прикажи уште</button>':'');$('pickupArchiveRows').querySelectorAll('[data-pickup-details]').forEach(b=>b.onclick=()=>showDetails(b.dataset.pickupDetails));if($('pickupArchiveMore'))$('pickupArchiveMore').onclick=()=>{archiveLimit+=40;fill();};};
  $('pickupArchive').ontoggle=()=>{if($('pickupArchive').open&&!$('pickupArchiveRows').childNodes.length)fill();};
 }
}
window.renderPickups=renderPickups;
window.WFPickups={form,complete,collectPackages,openItem,showDetails};
const previousHome=renderHome;
renderHome=function(){
 previousHome();const count=Pickups.pending(log()).length;
 const card=document.createElement('section');card.className='card';
 card.innerHTML='<h3>Роба за подигање</h3><p class="small muted">'+(count?'Чекаат подигање: '+count:'Нема неподигната роба.')+'</p><button class="btn secondary full" id="homePickups">'+(count?'Отвори ја листата':'Додај роба за подигање')+'</button>';
 $('app').prepend(card);$('homePickups').onclick=()=>{view='pickups';render();};
};
if(state)render();
})();
