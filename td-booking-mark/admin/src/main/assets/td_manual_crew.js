/* TD Booking / MarK — manual, offline-first staff and payments.
   Use existing finance ledger: weddingId -> crew[]; general roles in finance[GENERAL].
   No Firebase account is created, edited or required. No data migration.
*/
(()=>{'use strict';
const GENERAL='__td_manual_general_crew_v1__';
const host=document.getElementById('staffView');
const oldList=document.getElementById('staffList');
if(!host||!oldList||document.getElementById('tdManualCrew'))return;
const styles=document.createElement('style');styles.textContent=`
#tdManualCrew{margin:12px 0;padding:14px;border:1px solid #bddbd1;background:#f8fcfa;border-radius:16px}
#tdManualCrew [hidden]{display:none!important}\n#tdManualCrew h3{margin:4px 0 10px}
#tdManualCrew .mc-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
#tdManualCrew .mc-grid>label{margin:0;min-width:0}
#tdManualCrew label{font-size:14px;display:block}
#tdManualCrew input,#tdManualCrew select,#tdManualCrew textarea{display:block;width:100%;margin:5px 0}
#tdManualCrew .mc-actions{display:flex;gap:8px;flex-wrap:wrap;margin:8px 0}
#tdManualCrew .mc-record{padding:11px 2px;border-bottom:1px solid #d6e7e1}
#tdManualCrew .mc-record button{font-size:13px;padding:8px 12px;min-height:38px}
#tdManualCrew .mc-muted{font-size:13px;color:#4e6d65}
#tdManualCrew .mc-error{color:#962b26;font-weight:650;white-space:pre-wrap}
#tdManualCrew .mc-ok{color:#146348;font-weight:650}
@media(max-width:580px){#tdManualCrew .mc-grid{grid-template-columns:1fr}}
@media print{#tdManualCrew{display:none!important}}
`;document.head.append(styles);
const area=document.createElement('section');area.id='tdManualCrew';
area.innerHTML=`
<h3>👥 Рачно додавање во екипа</h3>
<p class="mc-muted">Работи без Cloud/Firebase. Записите се во истата локална евиденција за финансии и може да се синхронизираат преку MarK целосна синхронизација кога е достапна.</p>
<button type="button" id="mcNew">＋ Додај соработник / ангажман</button>
<div id="mcEditor" hidden>
<form id="mcForm">
 <div class="mc-grid">
  <label>Настан / ангажман<select id="mcWedding" required></select></label>
  <label>Име и презиме *<input id="mcName" maxlength="140" required autocomplete="name"></label>
  <label>Телефон (по желба)<input id="mcPhone" maxlength="50" type="tel"></label>
  <label>Улога *<select id="mcRole" required>
   <option>Фотограф</option><option>Видеограф</option><option>Дрон оператор</option>
   <option>Кран оператор</option><option>Love Story</option><option>Монтажа / обработка</option>
   <option>Асистент</option><option>Друго</option>
  </select></label>
  <label>Договорен хонорар (€) *<input id="mcFee" type="number" min="0" max="9999999" step="0.01" inputmode="decimal" required></label>
  <label id="mcInitialPaidWrap">Веќе исплатено (€)<input id="mcPaid" type="number" min="0" max="9999999" step="0.01" inputmode="decimal" value="0"></label>
  <label id="mcInitialDateWrap">Датум на исплата<input id="mcDate" type="date"></label>
  <label id="mcInitialMethodWrap">Начин на исплата<select id="mcMethod"><option>Готовина</option><option>Трансфер</option><option>Друго</option></select></label>
 </div>
 <label>За што е ангажиран? *<textarea id="mcTasks" required maxlength="1200" rows="2" placeholder="На пр. фотографирање кај невеста, дрон, монтажа..."></textarea></label>
 <label>Забелешка<input id="mcNotes" maxlength="500" placeholder="По желба"></label>
 <div class="mc-actions"><button type="submit" id="mcSubmit">Зачувај</button><button type="button" class="secondary" id="mcCancel">Откажи</button></div>
 <div id="mcError" role="alert" class="mc-error"></div>
</form></div>
<div id="mcPaymentBox" hidden>
<h3>＋ Нова исплата</h3><div id="mcPaymentTo" class="mc-muted"></div>
<form id="mcPaymentForm">
<div class="mc-grid">
 <label>Износ (€) *<input id="mcPayAmount" type="number" min="0.01" max="9999999" step="0.01" inputmode="decimal" required></label>
 <label>Датум *<input id="mcPayDate" type="date" required></label>
 <label>Начин<select id="mcPayMethod"><option>Готовина</option><option>Трансфер</option><option>Друго</option></select></label>
 <label>За што / забелешка<input id="mcPayNote" maxlength="500"></label>
</div><div class="mc-actions"><button type="submit">Зачувај исплата</button><button type="button" class="secondary" id="mcPayCancel">Откажи</button></div>
<div id="mcPayError" role="alert" class="mc-error"></div>
</form></div>
<h3>Рачно внесени ангажмани</h3>
<div id="mcTotals" class="mc-muted"></div><div id="mcEntries"></div>
<p class="mc-muted">Другите ангажмани внесени преку Финансии се прикажани подолу. „Општ ангажман“ се следи одделно од сметката на конкретен настан.</p>
`;
host.insertBefore(area,oldList);
const el=id=>document.getElementById(id);
let editing=null,paying=null;
const allBookings=()=>typeof data!=='undefined'&&Array.isArray(data)?data:[];
const fLedger=()=>typeof finance!=='undefined'&&finance&&typeof finance==='object'?finance:{};
const money=v=>(Number(v)||0).toFixed(2)+' €';
const numeric=v=>{const s=String(v??'').trim().replace(',','.');return s===''?NaN:Number(s)};
const day=()=>{const d=new Date();return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,10)};
const paid=c=>(Array.isArray(c.payments)?c.payments:[]).reduce((sum,p)=>sum+(Number(p.amount)||0),0);
function wedLabel(id){const w=allBookings().find(x=>String(x.id)===String(id));return id===GENERAL?'Општ ангажман':w?String(w.date||'')+' · '+((w.eventType&&w.eventType!=='wedding')?String(w.bride||'Настан'):String(w.bride||'')+' и '+String(w.groom||'')):'Настан (архивиран)'}
function options(wanted){
 const sel=el('mcWedding');sel.replaceChildren();
 const general=document.createElement('option');general.value=GENERAL;general.textContent='Општ ангажман (без настан)';sel.append(general);
 for(const w of allBookings().slice().sort((a,b)=>String(b.date||'').localeCompare(String(a.date||'')))){
  if(!w||!w.id)continue;const opt=document.createElement('option');opt.value=String(w.id);opt.textContent=wedLabel(w.id);sel.append(opt);
 }
 sel.value=wanted&&[...sel.options].some(x=>x.value===wanted)?wanted:(allBookings()[0]?.id||GENERAL);
}
function snapshot(ids){const obj=fLedger();return Object.fromEntries(ids.map(id=>[id,Object.prototype.hasOwnProperty.call(obj,id)?JSON.stringify(obj[id]):null]));}
function restore(before){const obj=fLedger();for(const [id,s]of Object.entries(before))if(s===null)delete obj[id];else obj[id]=JSON.parse(s)}
function commit(ids,callback){
 const before=snapshot(ids);
 try{callback();if(typeof finSave!=='function')throw Error('Не е достапно зачувување.');finSave()}
 catch(err){restore(before);throw err}
 if(typeof refreshViews==='function')refreshViews();
 if(typeof financeRefresh==='function')financeRefresh();
}
function locate(wid,cid){const ledger=fLedger()[wid],arr=ledger?.crew;return Array.isArray(arr)?arr.find(x=>String(x.id)===String(cid)):null}
function items(){const out=[];for(const [wid,v]of Object.entries(fLedger())){
 if(!Array.isArray(v?.crew))continue;
 for(const c of v.crew){if(c?.manualEntry===true)out.push({wid,c})}
}return out.sort((a,b)=>String(b.c.created||'').localeCompare(String(a.c.created||'')));}
function showForm(record=null){
 editing=record?{wid:record.wid,cid:record.c.id}:null;options(record?.wid);
 el('mcWedding').disabled=!!editing;
 el('mcName').value=record?.c.name||'';el('mcPhone').value=record?.c.phone||'';
 el('mcRole').value=record?.c.role||'Фотограф';
 if(!el('mcRole').value){const opt=document.createElement('option');opt.value=record.c.role;opt.textContent=record.c.role;el('mcRole').append(opt);el('mcRole').value=record.c.role}
 el('mcFee').value=record?Number(record.c.fee):'';
 el('mcTasks').value=record?.c.tasks||'';
 el('mcNotes').value=record?.c.notes||'';
 el('mcPaid').value='0';el('mcDate').value=day();el('mcError').textContent='';
 for(const id of ['mcInitialPaidWrap','mcInitialDateWrap','mcInitialMethodWrap'])el(id).hidden=!!editing;
 el('mcSubmit').textContent=editing?'Зачувај измени':'Зачувај соработник';
 el('mcEditor').hidden=false;el('mcPaymentBox').hidden=true;
 el('mcEditor').scrollIntoView({behavior:'smooth',block:'nearest'});
}
function hideForm(){editing=null;el('mcEditor').hidden=true}
el('mcNew').addEventListener('click',()=>showForm());
el('mcCancel').addEventListener('click',hideForm);
el('mcForm').addEventListener('submit',e=>{
 e.preventDefault();el('mcError').textContent='';
 const wid=el('mcWedding').value,name=el('mcName').value.trim(),phone=el('mcPhone').value.trim(),
 role=el('mcRole').value,tasks=el('mcTasks').value.trim(),notes=el('mcNotes').value.trim();
 const fee=numeric(el('mcFee').value),initial=editing?0:numeric(el('mcPaid').value||'0');
 if(!wid||!name||!role||!tasks||!Number.isFinite(fee)||fee<0||fee>9999999||!Number.isFinite(initial)||initial<0||initial>fee){
  el('mcError').textContent='Провери ги името, работата, хонорарот и исплатата (не смее да биде поголема од хонорарот).';return;
 }
 try{
  const targetWid=editing?.wid||wid,existing=editing?locate(editing.wid,editing.cid):null;
  if(editing&&!existing)throw Error('Ангажманот повеќе не постои. Освежи ја Екипа.');
  if(existing&&fee+0.0001<paid(existing))throw Error('Хонорарот не смее да биде помал од веќе исплатеното.');
  commit([targetWid],()=>{
   const f=finRecord(targetWid);if(!Array.isArray(f.crew))f.crew=[];
   if(existing)Object.assign(existing,{name,phone,role,fee:fee.toFixed(2),tasks,notes,updated:new Date().toISOString()});
   else{
    const payments=initial>0?[{id:finId(),amount:Number(initial.toFixed(2)),date:el('mcDate').value||day(),method:el('mcMethod').value,note:'Рачно внесена почетна исплата'}]:[];
    f.crew.push({id:finId(),name,phone,role,fee:fee.toFixed(2),tasks,notes,payments,manualEntry:true,created:new Date().toISOString(),handoff:'По договор со TD Production'});
   }
  });
  hideForm();refresh();
 }catch(err){el('mcError').textContent='Не е зачувано: '+(err.message||err)}
});
function showPay(wid,cid){
 const c=locate(wid,cid);if(!c)return;
 paying={wid,cid};el('mcPaymentTo').textContent=c.name+' · '+wedLabel(wid)+' · останато '+money(Math.max(0,Number(c.fee)-paid(c)));
 el('mcPayAmount').value='';el('mcPayDate').value=day();el('mcPayNote').value='';el('mcPayError').textContent='';
 el('mcPaymentBox').hidden=false;el('mcEditor').hidden=true;el('mcPaymentBox').scrollIntoView({behavior:'smooth',block:'nearest'});
}
el('mcPayCancel').addEventListener('click',()=>{paying=null;el('mcPaymentBox').hidden=true});
el('mcPaymentForm').addEventListener('submit',e=>{
 e.preventDefault();el('mcPayError').textContent='';
 const c=paying&&locate(paying.wid,paying.cid),amount=numeric(el('mcPayAmount').value);
 if(!c){el('mcPayError').textContent='Ангажманот не е пронајден.';return}
 const remaining=Math.max(0,Number(c.fee)-paid(c));
 if(!Number.isFinite(amount)||amount<=0||amount>9999999||amount-remaining>0.001){
  el('mcPayError').textContent='Внеси позитивен износ, најмногу '+money(remaining)+'.';return;
 }
 try{
  commit([paying.wid],()=>{
   c.payments=c.payments||[];
   c.payments.push({id:finId(),amount:Number(amount.toFixed(2)),date:el('mcPayDate').value,method:el('mcPayMethod').value,note:el('mcPayNote').value.trim()});
  });
  paying=null;el('mcPaymentBox').hidden=true;refresh();
 }catch(err){el('mcPayError').textContent='Исплатата не е зачувана: '+(err.message||err)}
});
function appendText(parent,tag,t,cls){const x=document.createElement(tag);x.textContent=t;if(cls)x.className=cls;parent.append(x);return x}
function drawEntries(){
 const entries=items(),total=entries.reduce((s,x)=>s+Number(x.c.fee||0),0),spent=entries.reduce((s,x)=>s+paid(x.c),0);
 el('mcTotals').textContent='Ангажмани: '+entries.length+' · Договорено: '+money(total)+' · Исплатено: '+money(spent)+' · За исплата: '+money(Math.max(0,total-spent));
 const list=el('mcEntries');list.replaceChildren();
 if(!entries.length){appendText(list,'p','Нема рачно внесени ангажмани.');return}
 for(const entry of entries){
  const {c,wid}=entry,row=document.createElement('div');row.className='mc-record';
  appendText(row,'strong',String(c.name||'Непознат соработник'));
  appendText(row,'div',wedLabel(wid)+' · '+String(c.role||''),'mc-muted');
  appendText(row,'div','За што: '+String(c.tasks||'—'),'mc-muted');
  appendText(row,'div','Хонорар '+money(c.fee)+' · Исплатено '+money(paid(c))+' · За исплата '+money(Math.max(0,Number(c.fee)-paid(c))));
  const actions=document.createElement('div');actions.className='mc-actions';
  const edit=document.createElement('button');edit.type='button';edit.className='secondary';edit.textContent='Уреди';edit.onclick=()=>showForm(entry);actions.append(edit);
  const payout=document.createElement('button');payout.type='button';payout.textContent='＋ Исплата';payout.disabled=paid(c)>=Number(c.fee);payout.onclick=()=>showPay(wid,c.id);actions.append(payout);
  row.append(actions);list.append(row);
 }
}
function refresh(){drawEntries()}
const oldDrawStaff=window.drawStaff;
if(typeof oldDrawStaff==='function')window.drawStaff=function(...args){const r=oldDrawStaff.apply(this,args);refresh();return r};
window.tdManualCrewRefresh=refresh;
refresh();
})();
