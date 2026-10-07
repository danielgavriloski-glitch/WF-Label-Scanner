(function(){
'use strict';

function periodLabel(p){
  return p.from.endsWith('-01')&&p.to===Core.monthBounds(p.from.slice(0,7))[1]?'Месечен · '+monthLabel(p.from.slice(0,7)):'Период '+Core.displayDate(p.from)+' – '+Core.displayDate(p.to);
}
function dayWord(n){return n===1?'ден':'дена';}
function recordDates(p,type){
  return p.rows.filter(r=>r.covered&&!r.future&&!r.auto&&r.type===type).map(r=>Core.displayDate(r.date));
}
function listDates(p,type){
  const a=recordDates(p,type);
  return a.length?' · '+a.join(', '):'';
}

tripListMarkup=function(trips,empty){
  empty=empty||'Нема запишани патувања.';
  if(!trips.length)return '<p class="trip-empty">'+esc(empty)+'</p>';
  return trips.map(function(t){
    const ws=t.workStart||state.settings.start||'08:00';
    return '<article class="trip-item"><div class="row"><strong>'+Core.displayDate(t.date)+' · '+esc(t.departure)+'</strong><span class="badge '+(t.pending&&!t.future?'trip-active':'')+'">'+tripStatus(t)+'</span></div>'+
      '<div class="trip-times"><span><small>Почеток на работа</small><b>'+esc(ws)+'</b></span><span><small>Поаѓање</small><b>'+esc(t.departure)+'</b></span><span class="trip-arrow" aria-hidden="true">→</span><span><small>Пристигнување</small><b>'+esc(tripArrivalLabel(t))+'</b></span><span><small>На пат'+(t.planned?' · план':'')+'</small><b>'+Core.hh(t.planned?t.plannedMinutes:t.travel)+'</b></span></div>'+
      (t.note?'<p class="trip-note">'+esc(t.note)+'</p>':'')+
      '<div class="actions">'+(t.pending?'<button class="btn" data-trip-arrive="'+esc(t.id)+'">Пристигнав</button>':'')+'<button class="btn '+(t.pending?'light':'secondary')+'" data-trip-edit="'+esc(t.id)+'">'+icon('edit')+'Измени</button></div></article>';
  }).join('');
};

tripHomeMarkup=function(day){
  const now=new Date();
  const trips=(state.trips||[]).map(t=>Core.tripView(t,now)).filter(t=>t.date===day.date||t.pending&&!t.future).sort((a,b)=>a.departureAt-b.departureAt);
  const start=day.workStart||day.start||state.settings.start;
  return '<section class="card trip-card"><div class="row"><div><h2>Службен пат</h2><span class="small muted">Почеток на работа '+esc(start)+' · '+Core.hh(day.travel)+' на пат</span></div><span class="trip-icon" aria-hidden="true">'+icon('clock')+'</span></div>'+
    '<button class="btn full trip-start" id="goOnTrip" data-trip-new="'+day.date+'">'+icon('plus')+'Тргнувам на пат</button>'+
    tripListMarkup(trips,'Кога ќе тргнеш, избери дали работниот ден почнал во 08:00 или внеси друго време.')+'</section>';
};

tripHistoryMarkup=function(period){
  const date=Core.today().startsWith(month)?Core.today():period.from<state.settings.trackStart?state.settings.trackStart:period.from;
  return '<section class="card trip-card"><div class="row"><h2>Службени патувања</h2><strong class="trip-total">'+Core.hh(period.travel)+'</strong></div><button class="btn secondary full" data-trip-new="'+date+'">'+icon('plus')+'Додади патување</button>'+tripListMarkup([].concat(period.tripRows).reverse())+'</section>';
};

tripReportMarkup=function(period){
  return '<section class="card trip-card"><div class="row"><h2>Време на пат / возење</h2><strong class="trip-total">'+Core.hh(period.travel)+'</strong></div><p class="small muted">'+period.tripRows.length+' патувања. Времето на пат е дел од работниот ден; во вториот PDF се прикажува одделно од времето без возење.</p>'+tripListMarkup([].concat(period.tripRows).reverse())+'</section>';
};

editTrip=function(id,date,arriving){
  id=id||null;date=date||null;arriving=!!arriving;
  const old=id?(state.trips||[]).find(t=>t.id===id):null;
  if(id&&!old){toast('Патувањето не е пронајдено.');return;}
  const now=Core.parts(),currentTime=now.hour+':'+now.minute;
  const selected=old?old.date:date||now.date;
  if(!old&&selected<state.settings.trackStart){toast('Датумот е пред почетокот на евиденцијата.');return;}
  const minDate=old&&old.date<state.settings.trackStart?old.date:state.settings.trackStart;
  const sameDay=(state.trips||[]).find(t=>t.date===selected&&t.id!==id&&t.workStart);
  const standard='08:00';
  let existingStart=old&&old.workStart?old.workStart:sameDay&&sameDay.workStart?sameDay.workStart:standard;
  if(old&&!old.workStart&&Core.minutes(old.departure)<Core.minutes(existingStart))existingStart=old.departure;
  const standardChecked=existingStart===standard;
  const arrivalDate=arriving?now.date:old?old.arrivalDate:selected;
  const arrival=arriving?currentTime:old?old.arrival:'';

  showModal(arriving?'Пристигнав':old?'Измени патување':'Тргнувам на пат',
    '<form id="tripForm">'+
    '<div class="trip-form-section"><h3>Почеток на работниот ден</h3>'+
    '<label class="check trip-choice"><input type="radio" name="workStartChoice" id="workStartStandard" value="standard" '+(standardChecked?'checked':'')+'><span>Дојдов на работа во 08:00</span></label>'+
    '<label class="check trip-choice"><input type="radio" name="workStartChoice" id="workStartCustom" value="custom" '+(!standardChecked?'checked':'')+'><span>Друго време</span></label>'+
    '<label class="field" id="customWorkStartWrap" '+(standardChecked?'hidden':'')+'><span>Кога почнав со работа</span><input id="tripWorkStart" type="time" value="'+esc(existingStart)+'"></label>'+
    '<p class="small muted">Работните часови се бројат од ова време. Ако пристигнеш по 8 работни часа, остатокот оди во прекувремени.</p></div>'+
    '<div class="trip-form-section"><h3>Поаѓање на пат</h3><div class="grid"><label class="field"><span>Датум</span><input id="tripDate" type="date" min="'+minDate+'" max="2100-12-31" value="'+selected+'" required></label><label class="field"><span>Час</span><input id="tripDeparture" type="time" value="'+(old?old.departure:currentTime)+'" required></label></div><button class="link-btn" id="tripNowDeparture" type="button">Тргнувам сега</button></div>'+
    '<div class="trip-form-section"><h3>Пристигнување</h3><div class="grid"><label class="field"><span>Датум</span><input id="tripArrivalDate" type="date" min="'+selected+'" max="2100-12-31" value="'+arrivalDate+'"></label><label class="field"><span>Час</span><input id="tripArrival" type="time" value="'+arrival+'"></label></div><button class="link-btn" id="tripNowArrival" type="button">Пристигнав сега</button><p class="small muted" style="margin:6px 0 0">Не мора да се вратиш во фирма. Со „Пристигнав“ завршува работното време за тој ден ако пристигнувањето е по редовниот крај. За пат преку полноќ избери го следниот датум.</p></div>'+
    '<div id="tripDuration" class="trip-duration" aria-live="polite"></div>'+
    '<label class="field"><span>Дестинација / белешка</span><textarea id="tripNote" maxlength="500" placeholder="На пример: Велес – Скопје">'+esc(old?old.note:'')+'</textarea></label>'+
    '<div class="error-text" id="tripError" role="alert"></div><button class="btn full" id="tripSubmit" type="submit">Зачувај</button>'+
    (old?'<button class="btn danger full" id="deleteTrip" type="button" style="margin-top:12px">Избриши го патувањето</button>':'')+'</form>');

  const workStart=function(){return $('workStartStandard').checked?standard:$('tripWorkStart').value;};
  const input=function(){return {id:old?old.id:null,date:$('tripDate').value,workStart:workStart(),departure:$('tripDeparture').value,arrivalDate:$('tripArrivalDate').value,arrival:$('tripArrival').value,note:$('tripNote').value.trim()};};
  const update=function(){
    $('customWorkStartWrap').hidden=$('workStartStandard').checked;
    $('tripArrivalDate').min=$('tripDate').value;
    if(!$('tripArrival').value&&$('tripArrivalDate').value<$('tripDate').value)$('tripArrivalDate').value=$('tripDate').value;
    $('tripSubmit').textContent=$('tripArrival').value?'Зачувај пристигнување':'Зачувај поаѓање';
    try{
      const data=input();
      if(!data.workStart)throw Error('Внеси кога си почнал со работа.');
      if(Core.timestamp(data.date,data.workStart)>Core.timestamp(data.date,data.departure))throw Error('Почетокот на работа мора да биде пред поаѓањето.');
      const t=Core.tripView(data);
      if(t.arrivalAt!==null&&t.arrivalAt<t.departureAt)throw Error('Пристигнувањето е пред поаѓањето.');
      const startAt=Core.timestamp(data.date,data.workStart);
      const endAt=data.arrival?t.arrivalAt:new Date().getTime();
      const work=Math.max(0,Math.floor((endAt-startAt)/60000));
      $('tripDuration').textContent=(data.arrival?'Работен ден до пристигнување: ':'Работното време се брои од '+data.workStart+' · досега ')+Core.hh(work)+(work>480?' · '+Core.hh(work-480)+' прекувремено':'');
      $('tripError').textContent='';
    }catch(e){$('tripDuration').textContent='Провери ги часовите.';$('tripError').textContent=e.message||'';}
  };
  ['workStartStandard','workStartCustom'].forEach(x=>$(x).onchange=update);
  ['tripWorkStart','tripDate','tripDeparture','tripArrivalDate','tripArrival'].forEach(x=>$(x).oninput=update);
  $('tripNowDeparture').onclick=function(){const n=Core.parts();$('tripDate').value=n.date;$('tripDeparture').value=n.hour+':'+n.minute;update();};
  $('tripNowArrival').onclick=function(){const n=Core.parts();$('tripArrivalDate').value=n.date;$('tripArrival').value=n.hour+':'+n.minute;update();};
  $('tripForm').onsubmit=function(e){
    e.preventDefault();
    try{
      const data=input();
      const result=Core.putTrip(state,data);
      commit(result.state,'Службен пат: '+Core.displayDate(result.trip.date)+' '+result.trip.departure+(result.trip.arrival?' – '+Core.displayDate(result.trip.arrivalDate)+' '+result.trip.arrival:' (на пат)'));
      closeModal();
      toast(result.trip.arrival?'Пристигнувањето е зачувано.':'Поаѓањето е зачувано. Работното време продолжува да се брои.');
    }catch(error){$('tripError').textContent=error.message;}
  };
  if($('deleteTrip'))$('deleteTrip').onclick=function(){
    if(!confirm('Да се избрише ова патување?'))return;
    commit(Core.removeTrip(state,old.id),'Избришано патување: '+Core.displayDate(old.date)+' '+old.departure);
    closeModal();toast('Патувањето е избришано.');
  };
  update();
};

renderHome=function(){
  const now=new Date(),d=Core.today(now),r=Core.week(state,Core.weekStart(d),now).rows.find(x=>x.date===d),w=Core.week(state,Core.weekStart(d),now),b=Core.monthBounds(d.slice(0,7)),p=Core.period(state,b[0],b[1],now),l=Core.leave(state,Number(d.slice(0,4)),now);
  const onTrip=r.trips.some(t=>t.pending&&!t.future);
  const status=onTrip?'На службен пат':r.type==='work'?(Core.parts(now).minuteOfDay<Core.minutes(r.start)?'Смената почнува во '+r.start:r.worked<r.planWork?'Работниот ден е во тек':'Работниот ден е завршен'):Core.TYPES[r.type];
  $('app').innerHTML=header(state.settings.name?'Здраво, '+esc(state.settings.name.split(' ')[0]):'Твоето работно време',Core.displayDate(d)+' · '+['Недела','Понеделник','Вторник','Среда','Четврток','Петок','Сабота'][Core.weekday(d)])+
    '<section class="card hero"><div class="hero-top"><span class="small">'+esc(status)+'</span>'+badge(r)+'</div><div class="clock">'+Core.hh(r.worked)+'</div><span class="muted small">реално работени часови денес'+(r.start?' · од '+r.start:'')+'</span><button class="btn hero-btn" data-day="'+d+'">'+icon('edit')+'Измени го денешниот ден</button></section>'+
    tripHomeMarkup(r)+
    '<div class="grid stats">'+stat('Работено месецов',Core.hh(p.worked),p.workDays+' работни '+dayWord(p.workDays),'accent')+stat('Прекувремено',Core.hh(p.overtime),p.compDays?'по '+p.compDays+' слободен '+dayWord(p.compDays):'овој месец','warn')+stat('На пат / возење',Core.hh(p.travel),'овој месец')+stat('Годишен одмор',p.counts.vacation+' дена','евидентирано')+'</div>'+
    '<section class="card"><div class="row"><h3>Оваа недела</h3><strong>'+Core.hh(w.normal)+' / '+Core.hh(w.target)+'</strong></div><div class="progress"><span style="width:'+Math.min(100,w.normal/Math.max(1,w.target)*100)+'%"></span></div><div class="mini-row"><span class="muted">Прекувремено по слободни денови</span><strong>'+Core.hh(w.overtime)+'</strong></div><div class="mini-row"><span class="muted">Искористени слободни денови</span><strong>'+w.compDays+'</strong></div><p class="small muted" style="margin:8px 0 0">Секој рачно означен слободен работен ден одзема 8 часа од прекувремените.</p></section>'+
    '<section class="card"><div class="row"><div><h3>Автоматски распоред</h3><span class="small muted">Понеделник–петок · 08:00–16:00</span></div>'+icon('shield')+'</div><div class="note">Годишниот одмор и неработниот празник се евидентираат посебно. Во „работено“ влегуваат само реално работените часови.</div><div class="actions"><button id="homeAbsence" class="btn secondary">'+icon('plus')+'Одмор / отсуство</button><button id="homePdf" class="btn light">'+icon('report')+'PDF работно време</button></div></section>'+
    '<p class="small muted">Почеток на евиденцијата: '+Core.displayDate(state.settings.trackStart)+'.</p>';
  $('homeAbsence').onclick=function(){editDay(d,'vacation',true);};
  $('homePdf').onclick=function(){exportWorkPdf(Core.period(state,b[0],b[1]));};
};

renderHistory=function(){
  const b=Core.monthBounds(month),p=Core.period(state,b[0],b[1]),offset=(Core.weekday(b[0])+6)%7,days=p.rows;
  const typeShort={vacation:'одмор',sick:'болед.',holiday:'празник',paid:'платено',unpaid:'неплат.',absent:'отсуство',off:'слобод.'};
  $('app').innerHTML=header('Евиденција','Избери ден за да го смениш времето или видот на отсуството.')+monthSwitch()+
    '<section class="card"><div class="calendar">'+['ПОН','ВТО','СРЕ','ЧЕТ','ПЕТ','САБ','НЕД'].map(x=>'<div class="weekday">'+x+'</div>').join('')+'<span></span>'.repeat(offset)+days.map(function(r){return '<button class="cal-day '+(r.auto&&r.type==='work'?'auto ':'')+r.type+' '+(r.future?'future ':'')+(r.date===Core.today()?'today ':'')+(!r.auto?'edit':'')+'" data-day="'+r.date+'"><b>'+Number(r.date.slice(8))+'</b><small>'+(!r.covered?'—':r.type==='work'||r.type==='holidayWork'?Core.hh(r.future?r.planWork:r.worked):(typeShort[r.type]||Core.TYPES[r.type]))+'</small></button>';}).join('')+'</div><div class="legend"><span><i class="dot"></i>работа</span><span><i class="dot purple"></i>одмор</span><span><i class="dot orange"></i>боледување</span><span><i class="dot gold"></i>рачна измена</span></div></section>'+
    '<div class="grid">'+stat('Работени часови',Core.hh(p.worked),p.workDays+' работни дена','accent')+stat('Прекувремено',Core.hh(p.overtime),p.compDays?'по слободните денови':'во избраниот месец','warn')+'</div>'+
    '<div class="actions"><button class="btn full" id="rangeButton">'+icon('plus')+'Додади период / исклучок</button></div>'+tripHistoryMarkup(p)+
    '<div class="section-head"><h2>Дневни записи</h2><span class="small muted">'+monthLabel(month)+'</span></div><section class="card">'+(days.filter(r=>r.covered&&(r.scheduled||!r.auto||r.trips.length)).map(dayItem).join('')||'<p class="empty">Нема записи во овој месец.</p>')+'</section>';
  bindMonth();$('rangeButton').onclick=function(){editDay(Core.today(),'vacation',true);};
};

function workReportJson(p){
  const title=periodLabel(p);
  const details=[
    'Прекувремено пред слободни денови: '+Core.hh(p.grossOvertime),
    'Искористен слободен ден: '+p.compDays+' '+dayWord(p.compDays)+' · '+Core.hh(p.compTime)+' се одземаат од прекувремените.',
    'Годишен одмор: '+p.counts.vacation+' '+dayWord(p.counts.vacation)+listDates(p,'vacation'),
    'Неработен ден – празник: '+p.counts.holiday+' '+dayWord(p.counts.holiday)+listDates(p,'holiday'),
    'Боледување: '+p.counts.sick+' '+dayWord(p.counts.sick)+listDates(p,'sick'),
    'Годишниот одмор и празникот се евидентираат посебно и не се додаваат во физички работените часови.'
  ];
  return {
    company:state.settings.company,name:state.settings.name,title:'Работно време · '+title,from:p.from,to:p.to,generated:Core.displayDate(Core.today()),
    summary:[['Работни денови',String(p.workDays)],['Работени часови',Core.hh(p.worked)],['Прекувремено',Core.hh(p.overtime)],['Слободен ден',p.compDays+' дена']],
    details:details,
    headers:['Датум','Вид / статус','Време','Работа','Редовно','Прекувр.','Пауза','Белешка'],
    rows:p.rows.filter(r=>r.covered&&(r.scheduled||!r.auto||r.trips.length)).map(r=>[r.date.slice(8)+'.'+r.date.slice(5,7),Core.TYPES[r.type]+(r.future?' · план':''),r.start?r.start+'–'+r.end+(r.overnight?' +1':''):'—',Core.hh(r.worked),Core.hh(r.regular),Core.hh(r.overtime),Core.hh(r.pause),r.note||'']),
    notes:[]
  };
}

function travelReportJson(p){
  return {
    company:state.settings.company,name:state.settings.name,title:'Работа и службен пат · '+periodLabel(p),from:p.from,to:p.to,generated:Core.displayDate(Core.today()),
    summary:[['Работни часови',Core.hh(p.worked)],['На пат / возење',Core.hh(p.travel)],['Работа без пат',Core.hh(p.workplace)],['Патувања',String(p.tripRows.length)]],
    details:[
      'Времето на пат е дел од вкупното работно време.',
      'Работа без пат = вкупни работни часови минус часови на пат/возење.',
      'Годишен одмор: '+p.counts.vacation+' дена · Неработен ден – празник: '+p.counts.holiday+' дена.',
      'Прекувремено по искористени слободни денови: '+Core.hh(p.overtime)+'.'
    ],
    headers:['Датум','Вид / статус','Работен ден','Работа','На пат','Без пат','Прекувр.','Белешка'],
    rows:p.rows.filter(r=>r.covered&&(r.scheduled||!r.auto||r.trips.length)).map(r=>[r.date.slice(8)+'.'+r.date.slice(5,7),Core.TYPES[r.type]+(r.future?' · план':''),r.start?r.start+'–'+r.end+(r.overnight?' +1':''):'—',Core.hh(r.worked),Core.hh(r.travel),Core.hh(Math.max(0,r.worked-r.travel)),Core.hh(r.overtime),r.note||'']),
    tripHeaders:['Датум на поаѓање','Час','Пристигнување','На пат','Дестинација / белешка'],
    tripRows:p.tripRows.map(t=>[Core.displayDate(t.date),t.departure,tripArrivalLabel(t),Core.hh(t.planned?t.plannedMinutes:t.travel)+(t.planned?' (план)':t.pending?' (во тек)':''),(t.workStart?'Работа од '+t.workStart+' · ':'')+(t.note||'')]),
    notes:[]
  };
}

reportJson=workReportJson;
exportPdf=function(p){exportWorkPdf(p);};
function exportWorkPdf(p){
  const data=workReportJson(p);
  const name='Workfashion_Rabotno_vreme_'+p.from+'_'+p.to+'.pdf';
  if(typeof Android!=='undefined')Android.exportPdf(name,JSON.stringify(data));else{window.lastPdfReport=data;toast('PDF за работно време е подготвен.');}
}
function exportTravelPdf(p){
  const data=travelReportJson(p);
  const name='Workfashion_Rabota_Pat_'+p.from+'_'+p.to+'.pdf';
  if(typeof Android!=='undefined')Android.exportPdf(name,JSON.stringify(data));else{window.lastTravelPdfReport=data;toast('PDF за работа и пат е подготвен.');}
}

exportRows=function(p){
  const rows=[['Датум','Вид','Почеток','Крај','Работено (ч)','Редовно (ч)','Прекувремено ден (ч)','На пат (ч)','Работа без пат (ч)','Пауза (ч)','Статус','Белешка','Поаѓање на пат','Пристигнување','Почеток на работа за пат']];
  p.rows.filter(r=>r.covered).forEach(r=>rows.push([Core.displayDate(r.date),Core.TYPES[r.type],r.start,r.end+(r.overnight?' (+1)':''),r.worked/60,r.regular/60,r.overtime/60,r.travel/60,Math.max(0,r.worked-r.travel)/60,r.pause/60,r.future?'План':r.auto?'Автоматски':'Рачна измена',r.note,r.trips.map(t=>t.departure).join(' | '),r.trips.map(tripArrivalLabel).join(' | '),r.trips.map(t=>t.workStart||state.settings.start).join(' | ')]));
  rows.push(['ВКУПНО','','','',p.worked/60,p.regular/60,p.overtime/60,p.travel/60,p.workplace/60,p.pause/60,'Нето прекувремено по '+p.compDays+' слободни денови','','','','']);
  return rows;
};

renderReports=function(){
  let p;try{p=reportPeriod();}catch(e){reportFrom=Core.monthBounds(month)[0];reportTo=Core.monthBounds(month)[1];p=reportPeriod();}
  $('app').innerHTML=header('Извештаи','Два одделни PDF извештаи: работно време и работа/службен пат.')+
    '<div class="switch-tabs"><button id="monthlyTab" class="'+(reportMode==='month'?'selected':'')+'">Месечен</button><button id="rangeTab" class="'+(reportMode==='range'?'selected':'')+'">Избран период</button></div>'+
    (reportMode==='month'?monthSwitch():'<div class="grid"><label class="field"><span>Од</span><input type="date" id="reportFrom" value="'+reportFrom+'" min="2000-01-01" max="2100-12-31"></label><label class="field"><span>До</span><input type="date" id="reportTo" value="'+reportTo+'" min="2000-01-01" max="2100-12-31"></label></div><button id="applyPeriod" class="btn secondary full">Прикажи го периодот</button>')+
    '<div class="grid stats">'+stat('Работни денови',String(p.workDays),'со реално работени часови','accent')+stat('Работени часови',Core.hh(p.worked),'без одмор и празник')+stat('Прекувремено',Core.hh(p.overtime),'по слободни денови','warn')+stat('На пат / возење',Core.hh(p.travel),p.tripRows.length+' патувања')+'</div>'+
    '<section class="card"><h2>Одмор, празник и прекувремено</h2>'+
      [['Прекувремено пред слободни',Core.hh(p.grossOvertime)],['Искористен слободен ден',p.compDays+' дена · −'+Core.hh(p.compTime)],['Годишен одмор',p.counts.vacation+' дена'],['Неработен ден – празник',p.counts.holiday+' дена'],['Боледување',p.counts.sick+' дена'],['Работа на празник',Core.hh(p.holidayWork)]].map(x=>'<div class="mini-row"><span class="muted">'+x[0]+'</span><strong>'+x[1]+'</strong></div>').join('')+
    '</section>'+tripReportMarkup(p)+
    '<section class="card"><h2>PDF документи</h2><p class="small muted">Се зачувуваат како два посебни документи.</p><button class="btn full" id="exportWorkPdf">'+icon('report')+'PDF 1 · Работно време</button><button class="btn secondary full" id="exportTravelPdf" style="margin-top:10px">'+icon('report')+'PDF 2 · Работа / пат</button><div class="actions"><button class="btn light" id="exportExcel">'+icon('download')+'Excel</button><button class="btn light" id="exportCsv">CSV</button></div></section>'+
    '<section class="card"><h2>Преглед по ден</h2><div class="table-wrap"><table class="table"><thead><tr><th>Датум</th><th>Вид</th><th>Работено</th><th>На пат</th></tr></thead><tbody>'+p.rows.filter(r=>r.covered).map(r=>'<tr><td>'+r.date.slice(8)+'.'+r.date.slice(5,7)+'</td><td>'+Core.TYPES[r.type]+(r.future?' · план':'')+'</td><td>'+Core.hh(r.worked)+'</td><td>'+Core.hh(r.travel)+'</td></tr>').join('')+'</tbody></table></div></section>';
  $('monthlyTab').onclick=function(){reportMode='month';const b=Core.monthBounds(month);reportFrom=b[0];reportTo=b[1];render();};
  $('rangeTab').onclick=function(){reportMode='range';render();};
  if(reportMode==='month')bindMonth();else $('applyPeriod').onclick=function(){try{const f=$('reportFrom').value,t=$('reportTo').value;Core.dates(f,t);reportFrom=f;reportTo=t;render();}catch(e){toast(e.message);}};
  $('exportWorkPdf').onclick=function(){safeExport(()=>exportWorkPdf(reportPeriod()));};
  $('exportTravelPdf').onclick=function(){safeExport(()=>exportTravelPdf(reportPeriod()));};
  $('exportExcel').onclick=function(){safeExport(()=>exportExcel(reportPeriod()));};
  $('exportCsv').onclick=function(){safeExport(()=>exportCsv(reportPeriod()));};
};

const renderSettingsPrevious=renderSettings;
renderSettings=function(){
  renderSettingsPrevious();
  const ps=document.querySelectorAll('#app p');
  for(const p of ps)if(p.textContent.indexOf('Workfashion · Мои часови · v1.1')>=0)p.textContent='Workfashion · Мои часови · v1.2 · WFAG';
};

if(window.WFApp){
  window.WFApp.editTrip=editTrip;
  window.WFApp.reportJson=workReportJson;
  window.WFApp.travelReportJson=travelReportJson;
  window.WFApp.exportRows=exportRows;
  window.WFApp.exportWorkPdf=exportWorkPdf;
  window.WFApp.exportTravelPdf=exportTravelPdf;
}
if(state)render();
})();