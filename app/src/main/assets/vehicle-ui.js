(function(){
'use strict';
let tab='overview',from=Core.monthBounds(Core.today().slice(0,7))[0],to=Core.today(),shown=40;
const labels={readings:'Километража',fills:'Гориво',tireSets:'Комплет гуми',tireChanges:'Промена на гуми',trips:'Патување',averages:'Зададен просек'};
const getLog=()=>state&&state.vehicleLog?state.vehicleLog:Vehicle.fresh();
const fmt=(n,d=1)=>n===null||n===undefined?'—':Number(n).toLocaleString('mk-MK',{minimumFractionDigits:0,maximumFractionDigits:d});
const stamp=at=>Core.displayDate(at.slice(0,10))+' · '+at.slice(11);
const now=()=>{const p=Core.parts(new Date());return p.date+'T'+p.hour+':'+p.minute;};
const atMillis=ms=>{const p=Core.parts(new Date(Number(ms)));return p.date+'T'+p.hour+':'+p.minute;};
const currentOdo=()=>{const r=Vehicle.latest(getLog());return r?r.odo:'';};
const noteField=r=>'<label class="field"><span>Белешка (по желба)</span><textarea id="vanNote" maxlength="1500">'+esc(r&&r.note||'')+'</textarea></label>';
const input=(name,title,value='',type='text',extra='')=>'<label class="field"><span>'+title+'</span><input id="'+name+'" type="'+type+'" value="'+esc(value??'')+'" '+extra+'></label>';
const atField=r=>input('vanAt','Датум и час',r&&r.at||now(),'datetime-local','required');
const odoField=r=>input('vanOdo','Километража од таблата (km)',r?r.odo:currentOdo(),'text','inputmode="decimal" placeholder="Пример: 10500" required');
const empty=text=>'<p class="empty">'+text+'</p>';
function saveLog(log,text){const next=JSON.parse(JSON.stringify(state));next.vehicleLog=log;commit(next,text);closeModal();toast('Зачувано. Пресметките се обновени.');}
function form(title,body,submit,kind,row){
  showModal(title,'<form id="vanForm" class="van-form">'+body+'<div class="error-text" id="vanError" role="alert"></div><button type="submit" class="btn full">Зачувај</button>'+(row?'<button id="vanDelete" type="button" class="btn danger full" style="margin-top:10px">Отстрани запис</button>':'')+'</form>');
  $('vanForm').onsubmit=e=>{e.preventDefault();try{submit();}catch(error){$('vanError').textContent=error.message;}};
  if(row)$('vanDelete').onclick=()=>{if(!confirm('Да се отстрани записот? Може да го вратиш во „Историја“.'))return;try{saveLog(Vehicle.setDeleted(getLog(),kind,row.id,true),'Комбе: отстранет запис · '+labels[kind]);}catch(error){$('vanError').textContent=error.message;}};
}
function setup(){
  form('Постави го комбето',input('vanName','Име на комбето',getLog().name,'text','maxlength="100" required')+atField()+input('vanOdo','Точна моментална километража (km)','','text','inputmode="decimal" placeholder="Пример: 10500" required')+input('vanL100','Просек L/100 km (по желба)','','text','inputmode="decimal" placeholder="Остави празно ако не знаеш"')+'<p class="small muted">Внеси број без разделувач на илјади. Просекот служи за проценка; измерената потрошувачка се пресметува од точењата.</p>',()=>{
    let log=Vehicle.rename(getLog(),$('vanName').value);
    log=Vehicle.put(log,'readings',{at:$('vanAt').value,odo:$('vanOdo').value,note:'Почетна потврдена состојба'});
    if($('vanL100').value.trim())log=Vehicle.put(log,'averages',{at:$('vanAt').value,l100:$('vanL100').value,note:'Почетен зададен просек'});
    saveLog(log,'Комбе: почетна километража');
  });
}
function edit(kind,rowId,seed){
  const row=rowId?getLog()[kind].find(r=>r.id===rowId&&!r.deleted):null;
  if(rowId&&!row){toast('Записот не е пронајден.');return;}
  const r=row||seed||{};let body='';
  if(kind==='readings')body=atField(row)+odoField(row)+noteField(row);
  if(kind==='fills')body=atField(row)+odoField(row)+input('vanLitres','Наточени литри',r.litres??'','text','inputmode="decimal" required')+'<label class="check"><input id="vanFull" type="checkbox" '+(r.full?'checked':'')+'><span>Наполнето до полн резервоар</span></label>'+input('vanCost','Вкупно платено (денари, по желба)',r.cost??'','text','inputmode="decimal"')+noteField(row)+'<p class="small muted">Делумните точења се собираат до следното полнење до полно. Првото полно е почетна референца.</p>';
  if(kind==='averages')body=atField(row)+input('vanL100','Зададен просек (L/100 km)',r.l100??'','text','inputmode="decimal" required')+noteField(row)+'<p class="small muted">Важи од овој датум. Измената ги прерачунува проценките, а измерените резултати остануваат според точењата.</p>';
  if(kind==='tireSets')body=input('vanSetName','Име на комплетот',r.name||'','text','maxlength="100" required')+'<label class="field"><span>Вид</span><select id="vanSeason">'+[['summer','Летни'],['winter','Зимски'],['all','Сите сезони']].map(([v,t])=>'<option value="'+v+'" '+(v===(r.season||'summer')?'selected':'')+'>'+t+'</option>').join('')+'</select></label>'+noteField(row);
  if(kind==='tireChanges')body=atField(row)+odoField(row)+'<label class="field"><span>Монтиран комплет</span><select id="vanSet">'+Vehicle.live(getLog().tireSets).map(s=>'<option value="'+esc(s.id)+'" '+(s.id===r.setId?'selected':'')+'>'+esc(s.name)+'</option>').join('')+'<option value="" '+(r.setId===null?'selected':'')+'>Демонтирани / нема заведено комплет</option></select></label>'+noteField(row)+'<p class="small muted">Нова монтажа го затвора претходниот интервал. Вкупните километри на истиот комплет се задржуваат низ сезоните.</p>';
  if(kind==='trips')body=atField(r)+input('vanEndAt','Пристигнување',r.endAt||now(),'datetime-local','required')+'<div class="grid">'+input('vanFrom','Од',r.from||'','text','maxlength="200"')+input('vanTo','До',r.to||'','text','maxlength="200"')+'</div><div class="grid">'+input('vanStartOdo','Почетна километража',r.startOdo??'','text','inputmode="decimal"')+input('vanEndOdo','Крајна километража',r.endOdo??'','text','inputmode="decimal"')+'</div>'+input('vanKm','Или растојание (km)',r.km??'','text','inputmode="decimal"')+'<p class="small muted">Кога се внесени двете состојби од таблата, нивната разлика е растојанието. Само растојание не ја менува потврдената состојба на таблата.</p>'+noteField(r)+(r.sourceId?'<p class="small muted">Поврзано '+(r.source==='gps'?'GPS':'WFAG')+' патување. Корекцијата овде не ги менува работните часови.</p>':'');
  form((row?'Измени · ':'Додај · ')+labels[kind],body,()=>{
    const data={id:row&&row.id,note:$('vanNote')?$('vanNote').value:''};
    if(kind!=='tireSets')data.at=$('vanAt').value;
    if(['readings','fills','tireChanges'].includes(kind))data.odo=$('vanOdo').value;
    if(kind==='fills')Object.assign(data,{litres:$('vanLitres').value,full:$('vanFull').checked,cost:$('vanCost').value});
    if(kind==='averages')data.l100=$('vanL100').value;
    if(kind==='tireSets')Object.assign(data,{name:$('vanSetName').value,season:$('vanSeason').value});
    if(kind==='tireChanges')data.setId=$('vanSet').value||null;
    if(kind==='trips')Object.assign(data,{endAt:$('vanEndAt').value,startOdo:$('vanStartOdo').value,endOdo:$('vanEndOdo').value,km:$('vanKm').value,source:r.source||'manual',sourceId:r.sourceId||'',from:$('vanFrom').value,to:$('vanTo').value});
    saveLog(Vehicle.put(getLog(),kind,data),'Комбе: '+(row?'изменет':'додаден')+' запис · '+labels[kind]);
  },kind,kind==='tireSets'?null:row);
}
function editButton(kind,row){return '<button class="btn light van-edit" type="button" data-van-kind="'+kind+'" data-van-id="'+esc(row.id)+'">Измени</button>';}
function entry(title,detail,actions='',extra=''){return '<article class="van-entry"><div class="row"><strong>'+title+'</strong>'+actions+'</div><div class="small muted van-entry-detail">'+detail+'</div>'+extra+'</article>';}
function fuelMarkup(limit=shown){
  const rows=Vehicle.fuelAnalysis(getLog()).reverse();
  return rows.slice(0,limit).map(r=>{
    let result;
    if(r.cycle)result=r.cycle.l100===null?'Нема поминати km за L/100 пресметка.':'Измерено: <b>'+fmt(r.cycle.l100,2)+' L/100 km</b> · '+fmt(r.cycle.litres,2)+' L за '+fmt(r.cycle.km,1)+' km<br>Интервал: '+stamp(r.cycle.from)+' → '+stamp(r.cycle.to);
    else result=r.full?'Почетна референца до полно; се чека следното полно.':'Се чека следно полнење до полно за измерен резултат.';
    const estimate=r.estimatedLitres===null?'':'<br>Проценка од претходното точење: '+fmt(r.estimatedLitres,2)+' L, со зададен просек '+fmt(r.estimateRate,2)+' L/100 km.';
    return entry(stamp(r.at),fmt(r.odo,1)+' km на таблата · <b>'+fmt(r.litres,2)+' L</b> · '+(r.full?'ДО ПОЛНО':'ДЕЛУМНО')+(r.kmSincePrevious===null?'':'<br>'+fmt(r.kmSincePrevious,1)+' km од претходното точење')+'<br>'+result+estimate+(r.cost===null?'':'<br>Платено: '+fmt(r.cost,2)+' ден.')+(r.note?'<br>'+esc(r.note):''),editButton('fills',r));
  }).join('')||empty('Нема заведено точења.');
}
function overview(){
  const log=getLog(),last=Vehicle.latest(log),baseline=Vehicle.sorted(log.readings)[0],s=Vehicle.summary(log),active=Vehicle.tires(log).find(x=>x.active),estimate=Vehicle.estimatedOdometer(log);
  return '<section class="card van-hero"><span class="small muted">Последна потврдена состојба</span><div class="van-odometer">'+(last?fmt(last.odo,1):'—')+' <small>km</small></div><p class="small muted">'+(last?stamp(last.at):'Внеси ја километражата од таблата')+'</p><button class="btn full" data-van-add="readings">Внеси / коригирај километража</button></section>'+
    (estimate&&estimate.additionalKm?'<p class="small muted">Проценка со новите GPS патувања: '+fmt(estimate.odo,1)+' km. Потврдената состојба од таблата се внесува одделно.</p>':'')+
    '<div class="grid stats">'+stat('Од почетниот внес',last&&baseline?fmt(last.odo-baseline.odo,1)+' km':'—',baseline?stamp(baseline.at):'нема почетна состојба','accent')+stat('Заведени патувања',s.tripCount,fmt(s.tripKm,1)+' km во патувања')+stat('Измерен просек',s.measuredL100===null?'—':fmt(s.measuredL100,2),s.measuredL100===null?'потребен е полно → полно интервал':'L/100 km · '+s.cycles.length+' интервали')+stat('Наточено гориво',fmt(s.fuelAdded,1)+' L','наточено; потрошеното се води одделно')+'</div>'+
    '<section class="card"><h3>Проценка според зададен просек</h3><p class="small muted">'+(s.estimatedOdoLitres===null?'Внеси просек во Историја → Зададени просеци.':fmt(s.estimatedOdoLitres,2)+' L за '+fmt(s.estimatedOdoKm,2)+' потврдени km. Ова е проценка, одделна од измерената потрошувачка.')+'</p></section>'+
    '<section class="card"><h3>Активни гуми</h3>'+(active?'<strong>'+esc(active.name)+'</strong><p class="small muted">'+fmt(active.currentKm,1)+' km од последната монтажа · '+fmt(active.totalKm,1)+' km вкупно</p>':'<p class="small muted">Сè уште нема заведена монтажа.</p>')+'<button type="button" class="btn secondary full" data-van-add="tireChanges">Заведи промена на гуми</button></section>'+
    '<div class="actions"><button class="btn" data-van-add="fills">Додај гориво</button><button class="btn secondary" data-van-add="trips">Додај патување</button></div><div class="section-head"><h2>Последни точења</h2></div><section class="card">'+fuelMarkup(3)+'</section>';
}
function tiresMarkup(){
  const log=getLog(),sets=Vehicle.tires(log);
  return '<div class="actions"><button class="btn" data-van-add="tireChanges">Промена на гуми</button><button class="btn secondary" data-van-add="tireSets">Нов комплет</button></div><section class="card">'+sets.map(s=>entry(esc(s.name)+(s.active?' <span class="badge">МОНТИРАНИ</span>':''),fmt(s.totalKm,1)+' km вкупно'+(s.active?' · '+fmt(s.currentKm,1)+' km од последна монтажа':'')+'<br>'+s.intervals.length+' интервали'+(s.note?'<br>'+esc(s.note):''),editButton('tireSets',s))).join('')+'</section><h2>Историја на промени</h2><section class="card">'+(Vehicle.sorted(log.tireChanges).reverse().slice(0,shown).map(r=>entry(stamp(r.at),fmt(r.odo,1)+' km · '+esc((log.tireSets.find(s=>s.id===r.setId)||{name:'Демонтирани'}).name)+(r.note?'<br>'+esc(r.note):''),editButton('tireChanges',r))).join('')||empty('Нема заведено промени.'))+'</section><p class="small muted">Километрите на активниот комплет се до последната потврдена километража. Истиот комплет ги задржува старите километри при повторна монтажа.</p>';
}
function tripMarkup(){
  const log=getLog();
  return '<section class="card"><label class="check"><input id="vanAutoGPS" type="checkbox" '+(log.autoLinkFrom?'checked':'')+'><span>Автоматски поврзи нови GPS патувања со комбето</span></label><p class="small muted">Важи за патувања започнати по вклучувањето. Старите се поврзуваат рачно. GPS километражата е проценка што можеш да ја коригираш.</p></section><div class="actions"><button class="btn" data-van-add="trips">Рачно патување</button><button class="btn secondary" id="vanLinkTrip">Поврзи WFAG / GPS пат</button></div><section class="card">'+(Vehicle.sorted(log.trips).reverse().slice(0,shown).map(r=>{
    const rate=Vehicle.rateAt(log,r.at),km=Vehicle.tripKm(r);
    return entry(stamp(r.at),esc(r.from||'Поаѓање')+' → '+esc(r.to||'Пристигнување')+'<br><b>'+fmt(km,2)+' km</b> · '+(r.startOdo!==null?'според табла '+fmt(r.startOdo)+'–'+fmt(r.endOdo)+' km':r.source==='gps'?'GPS проценка / рачна корекција':'рачно растојание')+'<br>Крај: '+stamp(r.endAt)+(rate===null?'':'<br>Проценето гориво: '+fmt(km*rate/100,2)+' L со '+fmt(rate,2)+' L/100 km')+(r.sourceId?'<br>Поврзано '+(r.source==='gps'?'GPS':'WFAG')+' патување':'')+(r.note?'<br>'+esc(r.note):''),editButton('trips',r));
  }).join('')||empty('Нема патувања заведени за комбето.'))+'</section><p class="small muted">Се бројат заведени патувања, не GPS точки или попатни застанувања. Километрите од таблата и GPS не се собираат двапати.</p>';
}
function linkCandidates(){
  const used=new Set(Vehicle.live(getLog().trips).map(t=>t.sourceId)),map=new Map();
  for(const t of state.trips||[])if(t.arrival){const key='wfag:'+t.id;map.set(key,{source:'wfag',sourceId:key,at:t.date+'T'+t.departure,endAt:t.arrivalDate+'T'+t.arrival,from:'',to:(t.note||'').slice(0,200),km:null,note:t.note||''});}
  try{
    const native=typeof Android!=='undefined'&&Android.getRouteLog?JSON.parse(Android.getRouteLog()||'{}'):{};
    for(const s of native.sessions||[])if(s.id&&s.startedAt&&s.endedAt){
      const key=s.tripId?'wfag:'+s.tripId:'gps:'+s.id;
      const old=map.get(key)||{};
      map.set(key,{source:'gps',sourceId:key,at:old.at||atMillis(s.startedAt),endAt:old.endAt||atMillis(s.endedAt),from:old.from||'',to:old.to||((s.stops||[]).slice(-1)[0]||{}).name||'',km:Math.round(Vehicle.gpsKm(s)*1000)/1000,note:old.note||''});
    }
  }catch(error){toast('GPS листата не се прочита. Рачните патувања се достапни.');}
  return [...map.values()].filter(t=>!used.has(t.sourceId)).sort((a,b)=>b.at.localeCompare(a.at));
}
function linkTrip(){
  const rows=linkCandidates();
  if(!rows.length){showModal('Поврзи патување',empty('Нема неповрзани завршени WFAG / GPS патувања.')+'<button type="button" class="btn full" id="vanManualInstead">Внеси рачно</button>');$('vanManualInstead').onclick=()=>edit('trips');return;}
  showModal('Избери патување','<p class="small muted">Се поврзуваат само избраните патувања. Сè уште не се зачувуваат додека не ги потврдиш километражата и формата.</p>'+rows.slice(0,150).map((r,i)=>'<button type="button" class="btn light full van-link-choice" data-van-choice="'+i+'">'+stamp(r.at)+' · '+esc(r.to||'Патување')+' · '+(r.km===null?'внеси km':fmt(r.km,2)+' km')+'</button>').join(''));
  document.querySelectorAll('[data-van-choice]').forEach(button=>button.onclick=()=>edit('trips',null,rows[Number(button.dataset.vanChoice)]));
}
function analysisMarkup(){
  const log=getLog(),s=Vehicle.summary(log,from,to);
  return '<section class="card"><div class="grid">'+input('vanFromDate','Од',from,'date','required')+input('vanToDate','До',to,'date','required')+'</div><button id="vanApplyRange" class="btn secondary full" type="button">Прикажи период</button></section><div class="grid stats">'+
    stat('Патувања',s.tripCount,fmt(s.tripKm,2)+' km во заведени патувања','accent')+stat('Наточено',fmt(s.fuelAdded,2)+' L',s.fuel.length+' точења')+stat('Измерено',s.measuredL100===null?'—':fmt(s.measuredL100,2)+' L/100',fmt(s.measuredLitres,2)+' L за '+fmt(s.measuredKm,2)+' km')+stat('Платено',fmt(s.fuelCost,2)+' ден.',s.costCount+' точења со внесен износ')+'</div><section class="card"><h3>Километри и проценка</h3><p>'+fmt(s.tripKm,2)+' km · '+s.tripCount+' заведени патувања</p><p class="small muted">'+(s.first&&s.last?'Разлика меѓу потврдените состојби во периодот: '+fmt(s.odoKm,2)+' km<br>'+stamp(s.first.at)+' → '+stamp(s.last.at):'Нема потврдени состојби од таблата во овој период.')+'</p><p class="small muted">'+(s.estimatedOdoLitres===null?'Нема проценка по табла. ':'Проценка по табла: '+fmt(s.estimatedOdoLitres,2)+' L за '+fmt(s.estimatedOdoKm,2)+' km. ')+(s.estimatedTripLitres===null?'Внеси просек за проценка на горивото во патувањата.':'Проценето гориво: '+fmt(s.estimatedTripLitres,2)+' L за '+fmt(s.estimatedTripKm,2)+' km со зададен просек.')+'</p></section><section class="card"><h3>Покриеност на измерената потрошувачка</h3><p class="small muted">Се собираат затворените полно → полно интервали што завршуваат во избраниот период. Датумите подолу ја покажуваат точната покриеност.</p>'+(s.cycles.map(c=>'<div class="van-interval">'+stamp(c.from)+' → '+stamp(c.to)+'<br>'+fmt(c.litres,2)+' L / '+fmt(c.km,2)+' km · <strong>'+fmt(c.l100,2)+' L/100 km</strong></div>').join('')||empty('Нема затворен полно → полно интервал.'))+'</section><section class="card"><h3>Гуми вкупно до '+Core.displayDate(to)+'</h3>'+Vehicle.tires(log,to+'T23:59').map(s=>'<div class="mini-row"><span>'+esc(s.name)+'</span><strong>'+fmt(s.totalKm,2)+' km</strong></div>').join('')+'</section><div class="actions"><button class="btn" id="vanExportPdf">PDF</button><button class="btn secondary" id="vanExportCsv">CSV</button></div>';
}
function historyMarkup(){
  const log=getLog();let removed=[];
  for(const kind of Object.keys(labels))for(const r of log[kind]||[])if(r.deleted)removed.push({kind,r});
  return '<section class="card"><h3>Километража</h3><button class="btn secondary full" data-van-add="readings">Нова потврдена состојба</button>'+Vehicle.sorted(log.readings).reverse().slice(0,shown).map(r=>entry(stamp(r.at),fmt(r.odo,2)+' km'+(r.note?'<br>'+esc(r.note):''),editButton('readings',r))).join('')+'</section><section class="card"><h3>Зададени просеци</h3><button class="btn secondary full" data-van-add="averages">Додај просек од датум</button>'+(Vehicle.sorted(log.averages).reverse().map(r=>entry(stamp(r.at),fmt(r.l100,2)+' L/100 km'+(r.note?'<br>'+esc(r.note):''),editButton('averages',r))).join('')||empty('Нема зададен просек.'))+'</section><section class="card"><h3>Отстранети записи</h3>'+(removed.slice(-shown).reverse().map(({kind,r})=>entry(labels[kind],r.at?stamp(r.at):esc(r.name),'<button class="btn secondary" type="button" data-van-restore-kind="'+kind+'" data-van-restore-id="'+esc(r.id)+'">Врати</button>')).join('')||empty('Нема отстранети записи.'))+'</section><section class="card"><h3>Последни измени</h3>'+(log.audit.slice(-shown).reverse().map(a=>'<div class="audit-row"><small>'+esc(a.at.slice(0,19).replace('T',' '))+'</small>'+esc(labels[a.kind]||a.kind)+' · '+({add:'додадено',edit:'коригирано',remove:'отстрането',restore:'вратено'}[a.action]||esc(a.action))+'</div>').join('')||empty('Сè уште нема измени.'))+'</section><button id="vanRename" type="button" class="btn light full">Измени име на комбето</button>';
}
function renderVehicle(){
  const log=getLog();
  const tabs=[['overview','Преглед'],['fuel','Гориво'],['tires','Гуми'],['trips','Патувања'],['analysis','Анализа'],['history','Историја']];
  $('app').innerHTML=header(esc(log.name),'Комбе · километража, гориво, гуми и патувања')+'<div class="van-tabs" role="group" aria-label="Евиденција за комбето">'+tabs.map(([key,title])=>'<button type="button" data-van-tab="'+key+'" aria-pressed="'+(tab===key)+'">'+title+'</button>').join('')+'</div>'+
    (!state.vehicleLog?'<section class="card"><h2>Постави почетна километража</h2><p class="small muted">Внеси ја точната состојба од таблата. Потоа додавај гориво, гуми и патувања.</p><button id="vanSetup" class="btn full">Постави го комбето</button></section>':
    tab==='overview'?overview():tab==='fuel'?'<button class="btn full" data-van-add="fills">Додај точење</button><section class="card">'+fuelMarkup()+'</section>':tab==='tires'?tiresMarkup():tab==='trips'?tripMarkup():tab==='analysis'?analysisMarkup():historyMarkup())+
    (state.vehicleLog&&['fuel','tires','trips','history'].includes(tab)?'<button type="button" class="btn light full" id="vanMore">Прикажи повеќе записи</button>':'');
  document.querySelectorAll('[data-van-tab]').forEach(b=>b.onclick=()=>{tab=b.dataset.vanTab;shown=40;render();});
  document.querySelectorAll('[data-van-add]').forEach(b=>b.onclick=()=>state.vehicleLog?edit(b.dataset.vanAdd):setup());
  document.querySelectorAll('[data-van-kind]').forEach(b=>b.onclick=()=>edit(b.dataset.vanKind,b.dataset.vanId));
  document.querySelectorAll('[data-van-restore-kind]').forEach(b=>b.onclick=()=>{try{saveLog(Vehicle.setDeleted(getLog(),b.dataset.vanRestoreKind,b.dataset.vanRestoreId,false),'Комбе: вратен запис');}catch(error){toast(error.message);}});
  if($('vanSetup'))$('vanSetup').onclick=setup;
  if($('vanLinkTrip'))$('vanLinkTrip').onclick=linkTrip;
  if($('vanAutoGPS'))$('vanAutoGPS').onchange=()=>{const next=JSON.parse(JSON.stringify(getLog()));next.autoLinkFrom=$('vanAutoGPS').checked?Date.now():0;saveLog(Vehicle.validate(next),'Комбе: '+(next.autoLinkFrom?'вклучено':'исклучено')+' автоматско поврзување нови GPS патувања');};
  if($('vanMore'))$('vanMore').onclick=()=>{shown+=40;render();};
  if($('vanApplyRange'))$('vanApplyRange').onclick=()=>{try{Vehicle.summary(log,$('vanFromDate').value,$('vanToDate').value);from=$('vanFromDate').value;to=$('vanToDate').value;render();}catch(error){toast(error.message);}};
  if($('vanExportCsv'))$('vanExportCsv').onclick=()=>safeExport(exportVehicleCsv);
  if($('vanExportPdf'))$('vanExportPdf').onclick=()=>safeExport(()=>{const report=vehicleReport();if(typeof Android!=='undefined')Android.exportPdf('WFAG_Crafter_'+from+'_'+to+'.pdf',JSON.stringify(report));else{window.lastVehicleReport=report;toast('PDF податоците се подготвени.');}});
  if($('vanRename'))$('vanRename').onclick=()=>form('Име на комбето',input('vanName','Име',log.name,'text','maxlength="100" required'),()=>saveLog(Vehicle.rename(getLog(),$('vanName').value),'Комбе: изменето име'));
}
function vehicleReport(){
  const log=getLog(),s=Vehicle.summary(log,from,to),rows=[];
  for(const f of s.fuel)rows.push([stamp(f.at)+'\nГориво',fmt(f.odo,1)+' табла',fmt(f.litres,2),f.cycle?fmt(f.cycle.l100,2):'—',(f.full?'До полно':'Делумно')+(f.cycle?' · '+fmt(f.cycle.litres,2)+' L за '+fmt(f.cycle.km,1)+' km · '+stamp(f.cycle.from)+' → '+stamp(f.cycle.to):' · се чека полно → полно')+(f.cost===null?'':' · '+fmt(f.cost,2)+' ден.')+(f.note?' · '+f.note:'')]);
  for(const t of s.trips)rows.push([stamp(t.at)+'\nПатување',fmt(Vehicle.tripKm(t),2),'—','—',(t.from||'')+' → '+(t.to||'')+' · '+(t.startOdo===null?'растојание / GPS проценка':'според табла')+(t.note?' · '+t.note:'')]);
  return {company:state.settings.company||'Workfashion',name:log.name,title:'Комбе · гориво и патувања',from:Core.displayDate(from),to:Core.displayDate(to),summary:[['Патувања',String(s.tripCount)],['km во патувања',fmt(s.tripKm,1)],['Наточено L',fmt(s.fuelAdded,1)],['Измерено L/100',fmt(s.measuredL100,2)]],details:['Измерен просек: '+fmt(s.measuredLitres,2)+' L / '+fmt(s.measuredKm,1)+' km во затворени полно → полно интервали што завршуваат во периодот.','Платено: '+fmt(s.fuelCost,2)+' денари ('+s.costCount+' точења со износ).'],headers:['Датум / запис','km / табла','Литри','L/100','Детали'],rows,notes:['Делумните точења не даваат самостоен измерен просек. Првото полно е референца. Проценките според зададен просек не се измерена потрошувачка.','Гуми вкупно до '+Core.displayDate(to)+': '+Vehicle.tires(log,to+'T23:59').map(t=>t.name+' '+fmt(t.totalKm,1)+' km').join('; ')]};
}
function exportVehicleCsv(){
  const log=getLog(),s=Vehicle.summary(log,from,to);
  const rows=[['Вид','Датум','Километража од табла','Наточени L','До полно','Измерен интервал km','Литри во интервал','Измерени L/100 km','Проценети L','Платено денари','Од','До','Растојание km','Белешка']];
  for(const f of s.fuel)rows.push(['Гориво',f.at,f.odo,f.litres,f.full?'Да':'Не',f.cycle?f.cycle.km:'',f.cycle?f.cycle.litres:'',f.cycle&&f.cycle.l100!==null?f.cycle.l100:'',f.estimatedLitres??'',f.cost??'','','','',f.note]);
  for(const t of s.trips){const rate=Vehicle.rateAt(log,t.at);rows.push(['Патување',t.at,t.endOdo??'','','','','','',rate===null?'':Vehicle.tripKm(t)*rate/100,'',t.from,t.to,Vehicle.tripKm(t),t.note]);}
  for(const t of Vehicle.sorted(log.tireChanges).filter(t=>t.at.slice(0,10)>=from&&t.at.slice(0,10)<=to))rows.push(['Гуми',t.at,t.odo,'','','','','','','','','','',(log.tireSets.find(s=>s.id===t.setId)||{name:'Демонтирани'}).name+' '+t.note]);
  rows.push(['ЗБИР: наточено / измерени интервали','','',s.fuelAdded,'',s.measuredKm,s.measuredLitres,s.measuredL100??'',s.estimatedTripLitres??'',s.fuelCost,'','',s.tripKm,'Проценетите литри во збирот се за заведени патувања со зададен просек.']);
  const cell=value=>{let text=typeof value==='number'?String(value).replace('.',','):String(value);if(/^[\s]*[=+@-]/.test(text))text="'"+text;return '"'+text.replace(/"/g,'""')+'"';};
  exportText('WFAG_Crafter_'+from+'_'+to+'.csv','\ufeff'+rows.map(r=>r.map(cell).join(';')).join('\r\n'),'text/csv');
}
window.renderVehicle=renderVehicle;
window.WFVehicle={getLog:()=>JSON.parse(JSON.stringify(getLog())),setup,edit,linkCandidates,vehicleReport,setTab:value=>{if(['overview','fuel','tires','trips','analysis','history'].includes(value))tab=value;view='vehicle';render();}};
function syncNewTrips(){
  if(!state||!state.vehicleLog||!state.vehicleLog.autoLinkFrom||!$('modal').hidden||typeof Android==='undefined'||!Android.getRouteLog)return;
  try{const native=JSON.parse(Android.getRouteLog()||'{}'),result=Vehicle.autoLink(getLog(),native.sessions||[]);if(result.added){const next=JSON.parse(JSON.stringify(state));next.vehicleLog=result.log;commit(next,'Комбе: автоматски поврзани GPS патувања · '+result.added);toast('Поврзани '+result.added+' нови GPS патувања.');}}catch(error){toast('Патувањата не се поврзаа: '+error.message);}
}
const previousRouteChange=window.onRouteNativeChanged;
window.onRouteNativeChanged=function(){if(previousRouteChange)previousRouteChange();syncNewTrips();};
setInterval(syncNewTrips,30000);
setTimeout(syncNewTrips,0);
})();
