'use strict';
function tripArrivalLabel(t){return t.arrival?(t.arrivalDate!==t.date?Core.displayDate(t.arrivalDate)+' ':'')+t.arrival:(t.future?'Чека пристигнување':'Во тек');}
function tripStatus(t){return t.planned?'План':t.pending?'На пат':'Завршено';}
function tripListMarkup(trips,empty='Нема запишани патувања.'){
 if(!trips.length)return `<p class="trip-empty">${esc(empty)}</p>`;
 return trips.map(t=>`<article class="trip-item"><div class="row"><strong>${Core.displayDate(t.date)} · ${esc(t.departure)}</strong><span class="badge ${t.pending&&!t.future?'trip-active':''}">${tripStatus(t)}</span></div><div class="trip-times"><span><small>Поаѓање</small><b>${esc(t.departure)}</b></span><span class="trip-arrow" aria-hidden="true">→</span><span><small>Пристигнување</small><b>${esc(tripArrivalLabel(t))}</b></span><span><small>На пат${t.planned?' · план':''}</small><b>${Core.hh(t.planned?t.plannedMinutes:t.travel)}</b></span></div>${t.note?`<p class="trip-note">${esc(t.note)}</p>`:''}<div class="actions">${t.pending?`<button class="btn" data-trip-arrive="${esc(t.id)}">Пристигнав</button>`:''}<button class="btn ${t.pending?'light':'secondary'}" data-trip-edit="${esc(t.id)}">${icon('edit')}Измени</button></div></article>`).join('');
}
function tripHomeMarkup(day){
 const now=new Date(),trips=(state.trips||[]).map(t=>Core.tripView(t,now)).filter(t=>t.date===day.date||t.pending&&!t.future).sort((a,b)=>a.departureAt-b.departureAt);
 return `<section class="card trip-card"><div class="row"><div><h2>Патувања</h2><span class="small muted">Денешни патувања · ${Core.hh(day.travel)} на пат</span></div><span class="trip-icon" aria-hidden="true">${icon('clock')}</span></div><button class="btn full trip-start" id="goOnTrip" data-trip-new="${day.date}">${icon('plus')}Одам на пат</button>${tripListMarkup(trips,'Внеси час на поаѓање, а пристигнувањето допиши го кога ќе стигнеш.')}</section>`;
}
function tripHistoryMarkup(period){
 const date=Core.today().startsWith(month)?Core.today():period.from<state.settings.trackStart?state.settings.trackStart:period.from;
 return `<section class="card trip-card"><div class="row"><h2>Патувања во месецот</h2><strong class="trip-total">${Core.hh(period.travel)}</strong></div><button class="btn secondary full" data-trip-new="${date}">${icon('plus')}Додади патување</button>${tripListMarkup([...period.tripRows].reverse())}</section>`;
}
function tripReportMarkup(period){
 return `<section class="card trip-card"><div class="row"><h2>Време на пат</h2><strong class="trip-total">${Core.hh(period.travel)}</strong></div><p class="small muted">${period.tripRows.length} патувања во избраниот период. Траењето се води посебно од вкупните работни часови.</p>${tripListMarkup([...period.tripRows].reverse())}</section>`;
}
function bindTripButtons(){
 document.querySelectorAll('[data-trip-new]').forEach(b=>b.onclick=()=>editTrip(null,b.dataset.tripNew));
 document.querySelectorAll('[data-trip-edit]').forEach(b=>b.onclick=()=>editTrip(b.dataset.tripEdit));
 document.querySelectorAll('[data-trip-arrive]').forEach(b=>b.onclick=()=>editTrip(b.dataset.tripArrive,null,true));
}
function editTrip(id=null,date=null,arriving=false){
 const old=id?(state.trips||[]).find(t=>t.id===id):null;
 if(id&&!old){toast('Патувањето не е пронајдено.');return;}
 const now=Core.parts(),currentTime=now.hour+':'+now.minute;
 const selected=old?old.date:date||now.date;
 if(!old&&selected<state.settings.trackStart){toast('Датумот е пред почетокот на евиденцијата.');return;}
 const minDate=old&&old.date<state.settings.trackStart?old.date:state.settings.trackStart;
 const arrivalDate=arriving?now.date:old?old.arrivalDate:selected;
 const arrival=arriving?currentTime:old?old.arrival:'';
 showModal(arriving?'Пристигнав':old?'Измени патување':'Одам на пат',`<form id="tripForm"><div class="trip-form-section"><h3>Поаѓање</h3><div class="grid"><label class="field"><span>Датум на поаѓање</span><input id="tripDate" type="date" min="${minDate}" max="2100-12-31" value="${selected}" required></label><label class="field"><span>Час на поаѓање</span><input id="tripDeparture" type="time" value="${old?old.departure:currentTime}" required></label></div><button class="link-btn" id="tripNowDeparture" type="button">Внеси го тековниот час</button></div><div class="trip-form-section"><h3>Пристигнување</h3><div class="grid"><label class="field"><span>Датум на пристигнување</span><input id="tripArrivalDate" type="date" min="${selected}" max="2100-12-31" value="${arrivalDate}"></label><label class="field"><span>Час на пристигнување</span><input id="tripArrival" type="time" value="${arrival}"></label></div><button class="link-btn" id="tripNowArrival" type="button">Пристигнав сега</button><p class="small muted" style="margin:6px 0 0">За пат преку полноќ избери го следниот датум. Ако си уште на пат, остави го часот на пристигнување празен.</p></div><div id="tripDuration" class="trip-duration" aria-live="polite"></div><label class="field"><span>Дестинација / белешка (по желба)</span><textarea id="tripNote" maxlength="500" placeholder="На пример: Охрид - Скопје">${esc(old?old.note:'')}</textarea></label><div class="error-text" id="tripError" role="alert"></div><button class="btn full" id="tripSubmit" type="submit">Зачувај патување</button>${old?'<button class="btn danger full" id="deleteTrip" type="button" style="margin-top:12px">Избриши го патувањето</button>':''}</form>`);
 const input=()=>({id:old?old.id:null,date:$('tripDate').value,departure:$('tripDeparture').value,arrivalDate:$('tripArrivalDate').value,arrival:$('tripArrival').value,note:$('tripNote').value.trim()});
 const update=()=>{
  $('tripArrivalDate').min=$('tripDate').value;
  if(!$('tripArrival').value&&$('tripArrivalDate').value<$('tripDate').value)$('tripArrivalDate').value=$('tripDate').value;
  $('tripSubmit').textContent=$('tripArrival').value?'Зачувај патување':'Зачувај поаѓање';
  try{const data=input();if(!Core.dateValid(data.date)||data.arrival&&!Core.dateValid(data.arrivalDate))throw Error();const t=Core.tripView(data);if(t.arrivalAt!==null&&t.arrivalAt<t.departureAt)throw Error();$('tripDuration').textContent=data.arrival?'Траење на патувањето: '+Core.hh(t.plannedMinutes):'Поаѓањето ќе се зачува. Пристигнувањето можеш да го внесеш подоцна.';}catch(e){$('tripDuration').textContent='Провери ги датумите и часовите.';}
 };
 ['tripDate','tripDeparture','tripArrivalDate','tripArrival'].forEach(id=>$(id).oninput=update);
 $('tripNowDeparture').onclick=()=>{const n=Core.parts();$('tripDate').value=n.date;$('tripDeparture').value=n.hour+':'+n.minute;update();};
 $('tripNowArrival').onclick=()=>{const n=Core.parts();$('tripArrivalDate').value=n.date;$('tripArrival').value=n.hour+':'+n.minute;update();};
 $('tripForm').onsubmit=e=>{e.preventDefault();try{const result=Core.putTrip(state,input());commit(result.state,'Патување: '+Core.displayDate(result.trip.date)+' '+result.trip.departure+(result.trip.arrival?' - '+Core.displayDate(result.trip.arrivalDate)+' '+result.trip.arrival:' (на пат)'));closeModal();toast(result.trip.arrival?'Патувањето е зачувано.':'Поаѓањето е зачувано.');}catch(error){$('tripError').textContent=error.message;}};
 if($('deleteTrip'))$('deleteTrip').onclick=()=>{if(!confirm('Да се избрише ова патување?'))return;commit(Core.removeTrip(state,old.id),'Избришано патување: '+Core.displayDate(old.date)+' '+old.departure);closeModal();toast('Патувањето е избришано.');};
 update();
}
