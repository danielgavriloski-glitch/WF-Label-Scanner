(function(){
'use strict';
const log=()=>state.pickupLog||Pickups.fresh();
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
 <label class="field"><span>Белешка · по избор</span><textarea id="pickupNote" maxlength="1000">${esc(item.note)}</textarea></label>
 <p class="hint">Дневен потсетник околу 08:00, сè додека не притиснеш „Подигната“.</p>
 <div id="pickupError" class="error-text" role="alert"></div><button class="btn full" type="submit">Зачувај</button></form>`);
 $('pickupForm').onsubmit=event=>{
  event.preventDefault();
  try{
   const result=Pickups.put(log(),{id:old&&old.id,from:$('pickupFrom').value,goods:$('pickupGoods').value,address:$('pickupAddress').value,note:$('pickupNote').value});
   const next=JSON.parse(JSON.stringify(state));next.pickupLog=result.log;
   commit(next,'Роба за подигање: '+result.item.from);closeModal();
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
function renderPickups(){
 const items=Pickups.pending(log()),ready=notificationReady();
 $('app').innerHTML=header('Роба за подигање',items.length?'Чекаат подигање: '+items.length:'Нема неподигната роба.')+
 `<section class="card"><button class="btn full" id="pickupAdd">${icon('plus')}Додај роба за подигање</button><p class="hint">Потсетник секој ден околу 08:00, и кога апликацијата е затворена. Со „Подигната“ записот се префрла во Документи.</p>${!ready?'<div class="note warn">За дневните потсетници дозволи известувања на телефонот.<button class="btn secondary full" id="pickupPermission">Дозволи известувања</button></div>':''}</section>`+
 (items.length?items.map(item=>`<section class="card pickup-card"><span class="badge manual">Чека подигање</span><h2>${esc(item.from)}</h2><p class="pickup-text">${esc(item.goods)}</p>${item.address?'<p class="small pickup-text">'+esc(item.address)+'</p>':''}${item.note?'<p class="small muted pickup-text">'+esc(item.note)+'</p>':''}<div class="warehouse-tools"><button class="btn" data-pickup-complete="${esc(item.id)}">Подигната</button><button class="btn light" data-pickup-edit="${esc(item.id)}">Измени</button></div></section>`).join(''):'<section class="card"><p class="warehouse-empty">Додај од каде и што треба да се подигне.</p></section>')+
 '<button class="btn secondary full" id="pickupDocuments">Отвори Документи · подигната роба</button>';
 $('pickupAdd').onclick=()=>form();
 $('pickupDocuments').onclick=()=>WFDocuments.setFilters({query:'',from:'',to:'',warehouseId:'',action:'pickup',deleted:false});
 if($('pickupPermission'))$('pickupPermission').onclick=requestNotifications;
 document.querySelectorAll('[data-pickup-edit]').forEach(b=>b.onclick=()=>form(b.dataset.pickupEdit));
 document.querySelectorAll('[data-pickup-complete]').forEach(b=>b.onclick=()=>complete(b.dataset.pickupComplete));
}
window.renderPickups=renderPickups;
window.WFPickups={form,complete};
const previousHome=renderHome;
renderHome=function(){
 previousHome();const count=Pickups.pending(log()).length;
 const card=document.createElement('section');card.className='card';
 card.innerHTML='<h3>Роба за подигање</h3><p class="small muted">'+(count?'Чекаат подигање: '+count:'Нема неподигната роба.')+'</p><button class="btn secondary full" id="homePickups">'+(count?'Отвори ја листата':'Додај роба за подигање')+'</button>';
 $('app').prepend(card);$('homePickups').onclick=()=>{view='pickups';render();};
};
if(state)render();
})();
