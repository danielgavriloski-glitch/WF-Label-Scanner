(function(){
'use strict';
const $=id=>document.getElementById(id),e=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const log=()=>state.cargoLog||WFCargo.fresh();
const save=(l,msg)=>{const next=JSON.parse(JSON.stringify(state));next.cargoLog=WFCargo.validate(l);commit(next,msg);};
function form(id){
 const s=id?log().shipments.find(x=>x.id===id):null;
 const v=s||{company:'',goods:'',location:'',dueDate:Core.today(),packages:0,pickedPackages:0,weightKg:null,notes:'',documents:[]};
 showModal(s?'Измени товар':'Нов товар',`<form id="cargoForm" class="warehouse-form">
 <label class="field"><span>Фирма / испраќач</span><input id="cargoCompany" maxlength="200" required value="${e(v.company)}"></label>
 <label class="field"><span>Што треба да се подигне</span><textarea id="cargoGoods" maxlength="2000">${e(v.goods)}</textarea></label>
 <label class="field"><span>Локација / магацин</span><input id="cargoLocation" maxlength="500" value="${e(v.location)}"></label>
 <label class="field"><span>Датум за подигање</span><input type="date" id="cargoDate" value="${e(v.dueDate)}"></label>
 <label class="field"><span>Број пакети</span><input type="number" min="${v.pickedPackages}" max="100000" id="cargoPackages" value="${v.packages}"></label>
 <label class="field"><span>Тежина (kg), ако е позната</span><input type="number" min="0" step="0.01" id="cargoWeight" value="${v.weightKg??''}"></label>
 <label class="field"><span>Белешка</span><textarea id="cargoNotes" maxlength="4000">${e(v.notes)}</textarea></label>
 <p class="hint">Документите се прикачуваат преку постоечкиот модул „Документи“. Во белешката можеш да го запишеш бројот на фактура или испратница.</p>
 <div id="cargoError" class="error-text"></div><button class="btn full" type="submit">Зачувај товар</button></form>`);
 $('cargoForm').onsubmit=ev=>{ev.preventDefault();try{
 const r=WFCargo.put(log(),{...(s?{id:s.id}:{}),company:$('cargoCompany').value.trim(),goods:$('cargoGoods').value,location:$('cargoLocation').value,dueDate:$('cargoDate').value,packages:Number($('cargoPackages').value),weightKg:$('cargoWeight').value===''?null:Number($('cargoWeight').value),notes:$('cargoNotes').value});
 save(r.log,'Товар: '+r.shipment.company);closeModal();toast('Товарот е зачуван.');
 }catch(err){$('cargoError').textContent=err.message;}};
}
function renderCargo(){
 const items=log().shipments.slice().sort((a,b)=>(a.dueDate||'').localeCompare(b.dueDate||'')),pending=WFCargo.due(log(),Core.today());
 $('app').innerHTML=header('Роба и подигање',pending.length+' товари за проверка')+`<section class="card"><button class="btn full" id="cargoAdd">+ Додај товар</button><p class="hint">За порака од Viber/WhatsApp препиши ги податоците или внеси ги од приложениот документ. Автоматско читање на слики ќе се додаде по проверка на Android интеграцијата.</p></section>`+
 (items.length?items.map(s=>`<section class="card"><h2>${e(s.company)}</h2><p>${e(s.goods)}</p><p class="small">${e(s.location)} · ${e(s.dueDate||'Без датум')}</p><p><strong>${s.pickedPackages}/${s.packages} пакети</strong> · ${s.weightKg===null?'Непозната тежина':s.weightKg+' kg'} · ${e(s.status)}</p><p class="small">${e(s.notes)}</p><div class="warehouse-tools"><button class="btn light cargoEdit" data-id="${e(s.id)}">Измени</button>${s.status==='pending'||s.status==='partial'?`<button class="btn cargoPickup" data-id="${e(s.id)}" ${s.packages===s.pickedPackages?'disabled':''}>Подигнав</button>`:''}${s.status==='picked_up'?`<button class="btn cargoDeliver" data-id="${e(s.id)}">Испорачано</button>`:''}</div></section>`).join(''):'<section class="card">Нема внесени товари.</section>');
 $('cargoAdd').onclick=()=>form();
 document.querySelectorAll('.cargoEdit').forEach(b=>b.onclick=()=>form(b.dataset.id));
 document.querySelectorAll('.cargoPickup').forEach(b=>b.onclick=()=>{const s=log().shipments.find(x=>x.id===b.dataset.id);const raw=prompt('Колку пакети подигна?',String(s.packages-s.pickedPackages));if(raw===null)return;try{save(WFCargo.pickup(log(),s.id,Number(raw)),'Подигнати пакети: '+s.company);}catch(err){toast(err.message);}});
 document.querySelectorAll('.cargoDeliver').forEach(b=>b.onclick=()=>{if(!confirm('Потврди дека товарот е испорачан?'))return;const s=log().shipments.find(x=>x.id===b.dataset.id);save(WFCargo.put(log(),{id:s.id,status:'delivered'}).log,'Испорачан товар: '+s.company);});
}
window.renderCargo=renderCargo;
})();
