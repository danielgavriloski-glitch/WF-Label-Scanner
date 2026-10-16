(function(){
'use strict';


let routeHistoryCount=30;
let routeMode='list',routePromptStopId='';
function routeNativeState(){
  try{
    if(typeof Android==='undefined'||!Android.getRouteLog)return {tracking:false,sessions:[],savedPlaces:[]};
    const x=JSON.parse(Android.getRouteLog()||'{}');
    x.sessions=Array.isArray(x.sessions)?x.sessions:[];x.savedPlaces=Array.isArray(x.savedPlaces)?x.savedPlaces:[];return x;
  }catch(e){return {tracking:false,sessions:[],savedPlaces:[]};}
}
function routeDate(ms){const d=new Date(Number(ms)||0),p=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+p(d.getMonth()+1)+'-'+p(d.getDate());}
function routeClock(ms){if(!ms)return '—';const d=new Date(Number(ms)),p=n=>String(n).padStart(2,'0');return p(d.getHours())+':'+p(d.getMinutes());}
function routeSpan(a,b){const m=Math.max(0,Math.round(((Number(b)||Date.now())-Number(a))/60000));return Core.hh(m);}
function routePointMeters(a,b){
  if(!a||!b)return 0;
  const r=6371000,rad=x=>Number(x)*Math.PI/180;
  const p1=rad(a.lat),p2=rad(b.lat),dp=rad(Number(b.lat)-Number(a.lat)),dl=rad(Number(b.lon)-Number(a.lon));
  const h=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
  return 2*r*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
}
function routeSessionMeters(s){
  const stored=Number(s&&s.distanceM);
  if(Number.isFinite(stored)&&stored>0)return stored;
  const p=s&&Array.isArray(s.points)?s.points:[];let total=0;
  for(let i=1;i<p.length;i++){
    const d=routePointMeters(p[i-1],p[i]);
    const dt=Math.max(1,(Number(p[i].at)-Number(p[i-1].at))/1000);
    if(d<=Math.max(500,dt*80))total+=d;
  }
  return total;
}
function routeKm(m){const km=Math.max(0,Number(m)||0)/1000;return (km<10?km.toFixed(1):km.toFixed(0))+' km';}
function routeSessions(from,to){
  return routeNativeState().sessions.filter(s=>{const d=routeDate(s.startedAt);return d>=from&&d<=to;});
}
function routeSvg(sessions){
  const colors=['#2563eb','#16a34a','#ea580c','#9333ea','#0891b2','#be185d'];
  const lines=[],allPoints=[];
  sessions.forEach(s=>{
    let nextColor=0;
    (s.hourSegments||[]).forEach((seg,idx)=>{
      const ci=Number.isFinite(Number(seg.colorIndex))?Number(seg.colorIndex)%colors.length:idx%colors.length;
      nextColor=(ci+1)%colors.length;
      const road=seg.lineSegments||[];
      if(road.length){
        road.forEach(arr=>{
          const pts=(arr||[]).map(p=>({lat:Number(p[0]),lon:Number(p[1])})).filter(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon));
          if(pts.length>1){lines.push({pts,color:colors[ci]});pts.forEach(p=>allPoints.push(p));}
        });
      }else{
        const pts=(seg.rawPoints||[]).map(p=>({lat:Number(p.lat),lon:Number(p.lon)})).filter(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon));
        if(pts.length>1){lines.push({pts,color:colors[ci]});pts.forEach(p=>allPoints.push(p));}
      }
    });
    const current=(s.points||[]).map(p=>({lat:Number(p.lat),lon:Number(p.lon)})).filter(p=>Number.isFinite(p.lat)&&Number.isFinite(p.lon));
    if(current.length>1){lines.push({pts:current,color:colors[nextColor]});current.forEach(p=>allPoints.push(p));}
  });
  if(allPoints.length<2)return '<div class="route-map-empty">Рутата ќе се појави штом GPS сними движење.</div>';
  const lats=allPoints.map(p=>p.lat),lons=allPoints.map(p=>p.lon);
  let minLat=Math.min.apply(null,lats),maxLat=Math.max.apply(null,lats),minLon=Math.min.apply(null,lons),maxLon=Math.max.apply(null,lons);
  if(maxLat-minLat<0.0001){maxLat+=0.00005;minLat-=0.00005}if(maxLon-minLon<0.0001){maxLon+=0.00005;minLon-=0.00005}
  const xy=p=>{const x=10+(Number(p.lon)-minLon)/(maxLon-minLon)*280,y=150-(Number(p.lat)-minLat)/(maxLat-minLat)*140;return [x,y];};
  const paths=lines.map(l=>'<polyline style="stroke:'+l.color+'" points="'+l.pts.map(p=>xy(p).join(',')).join(' ')+'"></polyline>').join('');
  let pins='',n=0;sessions.forEach(s=>(s.stops||[]).forEach(st=>{n++;const q=xy(st);pins+='<g><circle cx="'+q[0].toFixed(1)+'" cy="'+q[1].toFixed(1)+'" r="8"></circle><text x="'+q[0].toFixed(1)+'" y="'+(q[1]+3).toFixed(1)+'">'+n+'</text></g>'; }));
  return '<svg class="route-map-svg" viewBox="0 0 300 160" role="img" aria-label="Снимена рута">'+paths+pins+'</svg><p class="small muted route-map-note">Секој час е посебна боја; линиите се поврзани. Завршените часови се чуваат како компактна линија.</p>';
}
function routeStopMarkup(stop,index){
  const name=(stop.name||'').trim()||'Локација',lat=Number(stop.lat),lon=Number(stop.lon),end=Number(stop.endAt)||Date.now();
  return '<div class="route-stop"><div class="route-pin">'+index+'</div><div class="route-stop-main"><div class="row"><strong>'+esc(name)+'</strong><span class="small muted">'+routeSpan(stop.startAt,end)+'</span></div><div class="small muted">'+routeClock(stop.startAt)+' – '+routeClock(end)+(stop.active?' · сè уште тука':'')+'</div><div class="route-coords">'+lat.toFixed(5)+', '+lon.toFixed(5)+'</div><button class="link-btn route-map-open" data-lat="'+lat+'" data-lon="'+lon+'">Отвори офлајн мапа</button></div></div>';
}
function routePanelMarkup(from,to){
  const data=routeNativeState(),sessions=data.sessions.filter(s=>{const d=routeDate(s.startedAt);return d>=from&&d<=to;});
  const allStops=[];sessions.forEach(s=>(s.stops||[]).forEach(st=>allStops.push(st)));
  const active=data.tracking,totalMeters=sessions.reduce((sum,s)=>sum+routeSessionMeters(s),0);
  let body='';
  if(routeMode==='map')body=routeSvg(sessions);
  else if(!sessions.length)body='<p class="trip-empty">Нема снимена GPS рута во овој период.</p>';
  else{
    body=sessions.slice().reverse().map(s=>{
      const stops=(s.stops||[]);
      const hours=(s.hourSegments||[]).length,currentGps=(s.points||[]).length;
      return '<div class="route-session"><div class="mini-row"><span class="muted">'+Core.displayDate(routeDate(s.startedAt))+' · '+routeClock(s.startedAt)+' – '+(s.endedAt?routeClock(s.endedAt):'во тек')+'</span><strong>'+hours+' часови · '+currentGps+' тековни GPS</strong></div>'+(stops.length?stops.map((x,i)=>routeStopMarkup(x,i+1)).join(''):'<p class="trip-empty">Нема застанување подолго од 5 минути.</p>')+'</div>';
    }).join('');
  }
  return '<div class="route-box"><div class="row"><div><h3>GPS рута и застанувања</h3><span class="small muted">'+(active?'Следењето е активно':'Следењето не е активно')+' · '+allStops.length+' застанувања · '+routeKm(totalMeters)+'</span></div><span class="route-live '+(active?'on':'')+'">'+(active?'GPS':'OFF')+'</span></div><button class="btn secondary full" id="routeHistory">Историја по датуми · Мапа и застанувања</button><div class="switch-tabs route-tabs"><button data-route-mode="list" class="'+(routeMode==='list'?'selected':'')+'">Листа</button><button data-route-mode="map">Офлајн мапа</button></div>'+body+'<button class="btn light full" id="routeMileage">Километража · '+routeKm(totalMeters)+'</button><button class="btn '+(active?'danger':'secondary')+' full" id="routeToggle">'+(active?'Стоп GPS следење':'Старт GPS следење')+'</button><p class="small muted route-privacy">Ова е посебна лична евиденција и не влегува во работните часови или PDF извештаите.</p></div>';
}
function openRouteMileage(defaultFrom,defaultTo){
  const all=routeNativeState().sessions||[];
  const today=Core.today();
  const from=defaultFrom||today.slice(0,4)+'-01-01',to=defaultTo||today;
  showModal('Километража','<div class="grid"><label class="field"><span>Од датум</span><input id="routeKmFrom" type="date" value="'+esc(from)+'"></label><label class="field"><span>До датум</span><input id="routeKmTo" type="date" value="'+esc(to)+'"></label></div><div id="routeKmResult"></div>');
  const draw=function(){
    const f=$('routeKmFrom').value,t=$('routeKmTo').value;
    if(!f||!t||f>t){$('routeKmResult').innerHTML='<p class="error-text">Провери го периодот.</p>';return;}
    const sessions=all.filter(s=>{const d=routeDate(s.startedAt);return d>=f&&d<=t;});
    let total=0;const months={},years={};
    sessions.forEach(s=>{
      const m=routeSessionMeters(s);total+=m;
      const d=routeDate(s.startedAt),ym=d.slice(0,7),y=d.slice(0,4);
      months[ym]=(months[ym]||0)+m;years[y]=(years[y]||0)+m;
    });
    const monthRows=Object.keys(months).sort().reverse().map(k=>'<div class="mini-row"><span>'+esc(monthLabel(k))+'</span><strong>'+routeKm(months[k])+'</strong></div>').join('');
    const yearRows=Object.keys(years).sort().reverse().map(k=>'<div class="mini-row"><span>'+k+'</span><strong>'+routeKm(years[k])+'</strong></div>').join('');
    $('routeKmResult').innerHTML='<section class="card" style="margin-top:12px"><div class="row"><div><h3>Вкупно</h3><span class="small muted">'+Core.displayDate(f)+' – '+Core.displayDate(t)+' · '+sessions.length+' рути</span></div><strong class="trip-total">'+routeKm(total)+'</strong></div></section>'+
      (monthRows?'<section class="card"><h3>По месеци</h3>'+monthRows+'</section>':'')+
      (yearRows?'<section class="card"><h3>По години</h3>'+yearRows+'</section>':'')+
      (!sessions.length?'<p class="trip-empty">Нема GPS рути во избраниот период.</p>':'')+
      '<p class="small muted">Километрите се пресметани од снимената GPS трага и може малку да отстапуваат од километражата на возилото.</p>';
  };
  $('routeKmFrom').onchange=draw;$('routeKmTo').onchange=draw;draw();
}

function openRouteHistory() {
  const source=routeNativeState(),all=source.sessions.slice().sort((a,b)=>(Number(b.startedAt)||0)-(Number(a.startedAt)||0));
  const today=Core.today();
  const oldest=all.length?routeDate(all[all.length-1].startedAt):today.slice(0,4)+'-01-01';
  routeHistoryCount=30;
  showModal('Историја на GPS патувања',
    '<p class="small muted">Избери датум или период. Рутите, времето на застанувањата и километражата се читаат од снимената GPS историја на телефонот.</p>'+
    '<div class="grid"><label class="field"><span>Од датум</span><input id="gpsHistoryFrom" type="date" value="'+esc(oldest)+'"></label>'+
    '<label class="field"><span>До датум</span><input id="gpsHistoryTo" type="date" value="'+esc(today)+'"></label></div>'+
    '<div id="gpsHistoryResult"></div>'+
    '<button id="gpsHistoryBackup" class="btn light full" type="button">Зачувај GPS архива (JSON)</button>'+
    '<p class="small muted">Во отворената офлајн мапа избери „Зачувај слика PNG“ за фотографија од рутата. Извозот на архива не ги брише податоците.</p>');
  const draw=()=>{
    const from=$('gpsHistoryFrom').value,to=$('gpsHistoryTo').value;
    if(!Core.dateValid(from)||!Core.dateValid(to)||from>to){
      $('gpsHistoryResult').innerHTML='<p class="error-text">Провери ги датумите.</p>';return;
    }
    const matches=all.filter(s=>{const d=routeDate(s.startedAt);return d>=from&&d<=to;});
    const total=matches.reduce((a,s)=>a+routeSessionMeters(s),0);
    const stops=matches.reduce((a,s)=>a+(s.stops||[]).length,0);
    const shown=matches.slice(0,routeHistoryCount);
    $('gpsHistoryResult').innerHTML='<section class="card"><div class="row"><div><h3>Снимени патувања</h3><span class="small muted">'+matches.length+' рути · '+stops+' застанувања</span></div><strong class="trip-total">'+routeKm(total)+'</strong></div></section>'+
      (shown.length?shown.map(s=>{
        const date=routeDate(s.startedAt),st=s.stops||[],km=routeKm(routeSessionMeters(s));
        const hasGeo=(s.points||[]).length>0||(s.hourSegments||[]).some(h=>(h.rawPoints||[]).length>0||(h.lineSegments||[]).length>0)||st.length>0;
        return '<section class="card route-session"><div class="row"><strong>'+Core.displayDate(date)+'</strong><span class="small muted">'+routeClock(s.startedAt)+' – '+(s.endedAt?routeClock(s.endedAt):'во тек')+'</span></div>'+
          '<div class="mini-row"><span>Поминато</span><strong>'+km+'</strong></div>'+
          '<div class="mini-row"><span>Застанувања</span><strong>'+st.length+'</strong></div>'+
          (st.length?st.map((p,i)=>routeStopMarkup(p,i+1)).join(''):'<p class="small muted">Нема снимено застанување подолго од 5 минути.</p>')+
          (hasGeo?'<button type="button" class="btn secondary full" data-route-session="'+esc(s.id||'')+'">Отвори ја оваа рута на мапа</button>':'<p class="small muted">За ова патување нема зачувани GPS координати. Рутата не може да се реконструира.</p>')+
          '</section>';
      }).join(''):'<p class="trip-empty">Нема снимени GPS патувања во избраниот период. За нови рути дозволи Локација и стартувај службен пат.</p>')+
      (matches.length>shown.length?'<button type="button" class="btn light full" id="gpsHistoryMore">Прикажи уште '+Math.min(30,matches.length-shown.length)+' рути</button>':'');
    document.querySelectorAll('[data-route-session]').forEach(b=>b.onclick=()=>{
      if(typeof Android!=='undefined'&&Android.openRouteSessionMap)Android.openRouteSessionMap(b.dataset.routeSession);
      else if(typeof Android!=='undefined'&&Android.openRouteHistoryMap){
        const found=all.find(s=>s.id===b.dataset.routeSession);
        if(found)Android.openRouteHistoryMap(routeDate(found.startedAt),routeDate(found.startedAt));
      }else{
        const found=all.find(s=>s.id===b.dataset.routeSession);
        if(found)showModal('GPS трага',routeSvg([found]));
      }
    });
    document.querySelectorAll('.route-map-open').forEach(b=>b.onclick=()=>{
      if(typeof Android!=='undefined'&&Android.openRouteMap)Android.openRouteMap(Number(b.dataset.lat),Number(b.dataset.lon));
    });
    const more=$('gpsHistoryMore');if(more)more.onclick=()=>{routeHistoryCount+=30;draw();};
  };
  ['gpsHistoryFrom','gpsHistoryTo'].forEach(id=>$(id).onchange=()=>{routeHistoryCount=30;draw();});
  $('gpsHistoryBackup').onclick=()=>{
    if(typeof Android!=='undefined'&&Android.exportText){
      Android.exportText('WFAG_GPS_istorija_'+today+'.json',JSON.stringify(source,null,2),'application/json');
    }else toast('Архивата е достапна во Android апликацијата.');
  };
  draw();
}

function refreshRoutePanel(){
  const host=$('routePanel');if(!host)return;
  let from=host.dataset.from,to=host.dataset.to;
  host.innerHTML=routePanelMarkup(from,to);bindRouteButtons();
}
function bindRouteButtons(){
  const history=$('routeHistory');if(history)history.onclick=openRouteHistory;
  document.querySelectorAll('[data-route-mode]').forEach(b=>b.onclick=function(){if(b.dataset.routeMode==='map'&&typeof Android!=='undefined'&&Android.openRouteHistoryMap){const host=$('routePanel');Android.openRouteHistoryMap(host?host.dataset.from:'',host?host.dataset.to:'');return;}routeMode=b.dataset.routeMode;refreshRoutePanel();});
  document.querySelectorAll('.route-map-open').forEach(b=>b.onclick=function(){if(typeof Android!=='undefined'&&Android.openRouteMap)Android.openRouteMap(Number(b.dataset.lat),Number(b.dataset.lon));});
  const km=$('routeMileage');if(km)km.onclick=function(){const host=$('routePanel');openRouteMileage(host?host.dataset.from:'',host?host.dataset.to:'');};
  const toggle=$('routeToggle');if(toggle)toggle.onclick=function(){
    if(typeof Android==='undefined')return;
    const d=routeNativeState();
    if(d.tracking&&Android.stopRouteTracking)Android.stopRouteTracking();
    else if(Android.startRouteTracking)Android.startRouteTracking('manual_'+Date.now());
    setTimeout(refreshRoutePanel,800);
  };
}
function checkRoutePrompt(){
  if(typeof Android==='undefined'||!Android.getRouteLog)return;
  if(routePromptStopId&&$('modal').hidden)routePromptStopId='';
  if(!$('modal').hidden||routePromptStopId)return;
  const data=routeNativeState();let stop=null;
  for(const s of data.sessions||[]){for(const x of s.stops||[]){if(x.pending){stop=x;break;}}if(stop)break;}
  if(!stop)return;
  routePromptStopId=stop.id;
  const lat=Number(stop.lat),lon=Number(stop.lon);
  showModal('Застанување над 5 минути','<p>Се задржа тука повеќе од 5 минути. Дали е ова стандардно место?</p><div class="route-prompt-place"><strong>Локација</strong><span>'+lat.toFixed(5)+', '+lon.toFixed(5)+'</span><button class="link-btn" id="promptOpenMap">Отвори офлајн мапа</button></div><label class="field"><span>Име на местото (по желба)</span><input id="routeStopName" maxlength="80" placeholder="На пример: Магацин Скопје"></label><label class="check"><input id="rememberRoutePlace" type="checkbox" checked><span>Запамети го ова име и препознај го следниот пат.</span></label><button class="btn full" id="saveRoutePlace">Зачувај</button><button class="btn light full" id="leaveRouteLocation" style="margin-top:10px">Остави како „Локација“</button>');
  $('promptOpenMap').onclick=function(){if(Android.openRouteMap)Android.openRouteMap(lat,lon);};
  $('saveRoutePlace').onclick=function(){const name=$('routeStopName').value.trim();Android.setRouteStop(stop.id,name,!!$('rememberRoutePlace').checked&&!!name);routePromptStopId='';closeModal();refreshRoutePanel();toast(name?'Местото е зачувано како '+name+'.':'Зачувано е како „Локација“.');};
  $('leaveRouteLocation').onclick=function(){Android.setRouteStop(stop.id,'',false);routePromptStopId='';closeModal();refreshRoutePanel();toast('Застанувањето остана како „Локација“.');};
}
const bindTripButtonsRouteBase=bindTripButtons;
bindTripButtons=function(){bindTripButtonsRouteBase();bindRouteButtons();setTimeout(checkRoutePrompt,100);};

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
    tripListMarkup(trips,'Кога ќе тргнеш, избери дали работниот ден почнал во 08:00 или внеси друго време.')+'<div id="routePanel" data-from="'+day.date+'" data-to="'+day.date+'">'+routePanelMarkup(day.date,day.date)+'</div></section>';
};

tripHistoryMarkup=function(period){
  const date=Core.today().startsWith(month)?Core.today():period.from<state.settings.trackStart?state.settings.trackStart:period.from;
  return '<section class="card trip-card"><div class="row"><h2>Службени патувања</h2><strong class="trip-total">'+Core.hh(period.travel)+'</strong></div><button class="btn secondary full" data-trip-new="'+date+'">'+icon('plus')+'Додади патување</button>'+tripListMarkup([].concat(period.tripRows).reverse())+'<div id="routePanel" data-from="'+period.from+'" data-to="'+period.to+'">'+routePanelMarkup(period.from,period.to)+'</div></section>';
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
      if(typeof Android!=='undefined'){
        if(result.trip.arrival&&Android.stopRouteTracking)Android.stopRouteTracking();
        else if(!result.trip.arrival&&Android.startRouteTracking)Android.startRouteTracking(result.trip.id);
      }
      closeModal();
      toast(result.trip.arrival?'Пристигнувањето е зачувано. GPS следењето е стопирано.':'Поаѓањето е зачувано. GPS рутата се следи.');
    }catch(error){$('tripError').textContent=error.message;}
  };
  if($('deleteTrip'))$('deleteTrip').onclick=function(){
    if(!confirm('Да се избрише ова патување?'))return;
    commit(Core.removeTrip(state,old.id),'Избришано патување: '+Core.displayDate(old.date)+' '+old.departure);
    if(old&&!old.arrival&&typeof Android!=='undefined'&&Android.stopRouteTracking)Android.stopRouteTracking();
    closeModal();toast('Патувањето е избришано.');
  };
  update();
};

function overtimeFoot(p,fallback){
  return p.holidayWork?'со работа на празник'+(p.compDays?' · по слободни денови':''):fallback;
}

renderHome=function(){
  const now=new Date(),d=Core.today(now),r=Core.week(state,Core.weekStart(d),now).rows.find(x=>x.date===d),w=Core.week(state,Core.weekStart(d),now),b=Core.monthBounds(d.slice(0,7)),p=Core.period(state,b[0],b[1],now),l=Core.leave(state,Number(d.slice(0,4)),now);
  const onTrip=r.trips.some(t=>t.pending&&!t.future);
  const status=onTrip?'На службен пат':r.type==='work'?(Core.parts(now).minuteOfDay<Core.minutes(r.start)?'Смената почнува во '+r.start:r.worked<r.planWork?'Работниот ден е во тек':'Работниот ден е завршен'):Core.TYPES[r.type];
  $('app').innerHTML=header(state.settings.name?'Здраво, '+esc(state.settings.name.split(' ')[0]):'Твоето работно време',Core.displayDate(d)+' · '+['Недела','Понеделник','Вторник','Среда','Четврток','Петок','Сабота'][Core.weekday(d)])+
    '<section class="card hero"><div class="hero-top"><span class="small">'+esc(status)+'</span>'+badge(r)+'</div><div class="clock">'+Core.hh(r.worked)+'</div><span class="muted small">реално работени часови денес'+(r.start?' · од '+r.start:'')+'</span><button class="btn hero-btn" data-day="'+d+'">'+icon('edit')+'Измени го денешниот ден</button></section>'+
    tripHomeMarkup(r)+
    '<div class="grid stats">'+stat('Работено месецов',Core.hh(p.worked),p.workDays+' работни '+dayWord(p.workDays),'accent')+stat('Прекувремено',Core.hh(p.overtime),overtimeFoot(p,p.compDays?'по '+p.compDays+' слободен '+dayWord(p.compDays):'овој месец'),'warn')+stat('На пат / возење',Core.hh(p.travel),'овој месец')+stat('Годишен одмор',p.counts.vacation+' дена','евидентирано')+'</div>'+
    '<section class="card"><div class="row"><h3>Оваа недела</h3><strong>'+Core.hh(w.normal)+' / '+Core.hh(w.target)+'</strong></div><div class="progress"><span style="width:'+Math.min(100,w.normal/Math.max(1,w.target)*100)+'%"></span></div><div class="mini-row"><span class="muted">Прекувремено по слободни денови</span><strong>'+Core.hh(w.overtime)+'</strong></div><div class="mini-row"><span class="muted">Прекувремено – работа на празник</span><strong>'+Core.hh(w.holidayWork)+'</strong></div><div class="mini-row"><span class="muted">Други прекувремени (пред слободни)</span><strong>'+Core.hh(Math.max(0,w.grossOvertime-w.holidayWork))+'</strong></div><div class="mini-row"><span class="muted">Искористени слободни денови</span><strong>'+w.compDays+'</strong></div><p class="small muted" style="margin:8px 0 0">Работата на празник е целосно во прекувремените. Секој рачно означен слободен работен ден одзема 8 часа од нив.</p></section>'+
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
    '<div class="grid">'+stat('Работени часови',Core.hh(p.worked),p.workDays+' работни дена','accent')+stat('Прекувремено',Core.hh(p.overtime),overtimeFoot(p,p.compDays?'по слободните денови':'во избраниот месец'),'warn')+'</div>'+
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
    'Работа на празник: '+Core.hh(p.holidayWork)+' · '+p.counts.holidayWork+' '+dayWord(p.counts.holidayWork)+listDates(p,'holidayWork')+'. Сите работени часови на празник се вклучени во прекувремените.',
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
      'Работа на празник: '+Core.hh(p.holidayWork)+listDates(p,'holidayWork')+' · вклучено во прекувремените.',
      'Прекувремено по искористени слободни денови: '+Core.hh(p.overtime)+'.'
    ],
    headers:['Датум','Вид / статус','Работен ден','Работа','На пат','Без пат','Прекувр.','Белешка'],
    rows:p.rows.filter(r=>r.covered&&(r.scheduled||!r.auto||r.trips.length)).map(r=>[r.date.slice(8)+'.'+r.date.slice(5,7),Core.TYPES[r.type]+(r.future?' · план':''),r.start?r.start+'–'+r.end+(r.overnight?' +1':''):'—',Core.hh(r.worked),Core.hh(r.travel),Core.hh(Math.max(0,r.worked-r.travel)),Core.hh(r.overtime),r.note||'']),
    tripHeaders:['Датум на поаѓање','Час','Пристигнување','На пат','Дестинација / белешка'],
    tripRows:p.tripRows.map(t=>[Core.displayDate(t.date),t.departure,tripArrivalLabel(t),Core.hh(t.planned?t.plannedMinutes:t.travel)+(t.planned?' (план)':t.pending?' (во тек)':''),(t.workStart?'Работа од '+t.workStart+' · ':'')+(t.note||'')]),
    notes:[]
  };
}


function overtimeReportJson(p){
  const b=Core.overtimeBreakdown(state,p.from,p.to);
  const fullDays=['Недела','Понеделник','Вторник','Среда','Четврток','Петок','Сабота'];
  const rows=b.earned.map(function(r){
    const when=Core.displayDate(r.date)+' · '+fullDays[Core.weekday(r.date)];
    const time=r.start?r.start+'–'+r.end+(r.overnight?' +1':''):'—';
    const note=r.note?' · Белешка: '+r.note:'';
    return [when,time,Core.hh(r.worked),Core.hh(r.overtime),r.reason+note];
  });
  b.deductions.forEach(function(d){
    rows.push([Core.displayDate(d.date)+' · '+fullDays[Core.weekday(d.date)],'Слободен ден','—','−'+Core.hh(d.applied),d.reason+(d.note?' · '+d.note:'')]);
  });
  if(!rows.length)rows.push(['—','—','—','0ч','Нема прекувремени часови во избраниот период.']);
  return {
    company:state.settings.company,
    name:state.settings.name,
    title:'Само прекувремени · '+periodLabel(p),
    from:p.from,to:p.to,generated:Core.displayDate(Core.today()),
    summary:[
      ['Нето прекувремено',Core.hh(b.net)],
      ['Создадено прекувремено',Core.hh(b.gross)],
      ['Работа на празник',Core.hh(b.holiday)],
      ['Други прекувремени',Core.hh(b.other)],
      ['Искористено за слободни',b.compApplied?'−'+Core.hh(b.compApplied):'0ч'],
      ['Денови со прекувремено',String(b.earned.length)]
    ],
    details:[
      'Создадено прекувремено = работа на празник + други прекувремени. Празничните часови се бројат еднаш.',
      'Нето прекувремено = создадено прекувремено − часови искористени за слободни денови.',
      'Секој ред покажува на кој датум и ден се направени часовите и како се добиени во пресметката.',
      'Работа на празник: '+Core.hh(p.holidayWork)+' · '+p.counts.holidayWork+' '+dayWord(p.counts.holidayWork)+listDates(p,'holidayWork')+'. Сите работени часови се прекувремени, без одземање 8 редовни часа.',
      'За работен ден: над дневниот фонд од 8 часа оди во прекувремено. За викенд: прво се дополнува неделниот фонд до 40 часа, а остатокот е прекувремен.'
    ],
    headers:['Датум / ден','Работно време','Работено','Прекувр.','Како е пресметано'],
    rows:rows,
    notes:[]
  };
}

function exportOvertimePdf(p){
  const data=overtimeReportJson(p);
  const name='Workfashion_Prekuvremeni_'+p.from+'_'+p.to+'.pdf';
  if(typeof Android!=='undefined')Android.exportPdf(name,JSON.stringify(data));
  else{window.lastOvertimePdfReport=data;toast('PDF само за прекувремени е подготвен.');}
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
  $('app').innerHTML=header('Извештаи','Три одделни PDF извештаи: работно време, работа/службен пат и само прекувремени.')+
    '<div class="switch-tabs"><button id="monthlyTab" class="'+(reportMode==='month'?'selected':'')+'">Месечен</button><button id="rangeTab" class="'+(reportMode==='range'?'selected':'')+'">Избран период</button></div>'+
    (reportMode==='month'?monthSwitch():'<div class="grid"><label class="field"><span>Од</span><input type="date" id="reportFrom" value="'+reportFrom+'" min="2000-01-01" max="2100-12-31"></label><label class="field"><span>До</span><input type="date" id="reportTo" value="'+reportTo+'" min="2000-01-01" max="2100-12-31"></label></div><button id="applyPeriod" class="btn secondary full">Прикажи го периодот</button>')+
    '<div class="grid stats">'+stat('Работни денови',String(p.workDays),'со реално работени часови','accent')+stat('Работени часови',Core.hh(p.worked),'реално работени часови')+stat('Прекувремено',Core.hh(p.overtime),overtimeFoot(p,'по слободни денови'),'warn')+stat('На пат / возење',Core.hh(p.travel),p.tripRows.length+' патувања')+'</div>'+
    '<section class="card"><h2>Одмор, празник и прекувремено</h2>'+
      [['Прекувремено пред слободни',Core.hh(p.grossOvertime)],['Искористен слободен ден',p.compDays+' дена · −'+Core.hh(p.compTime)],['Годишен одмор',p.counts.vacation+' дена'],['Неработен ден – празник',p.counts.holiday+' дена'],['Боледување',p.counts.sick+' дена'],['Прекувремено – работа на празник',Core.hh(p.holidayWork)+' · '+p.counts.holidayWork+' '+dayWord(p.counts.holidayWork)],['Други прекувремени (пред слободни)',Core.hh(Math.max(0,p.grossOvertime-p.holidayWork))]].map(x=>'<div class="mini-row"><span class="muted">'+x[0]+'</span><strong>'+x[1]+'</strong></div>').join('')+
    '</section>'+tripReportMarkup(p)+
    '<section class="card"><h2>PDF документи</h2><p class="small muted">Се зачувуваат како три посебни документи.</p><button class="btn full" id="exportWorkPdf">'+icon('report')+'PDF 1 · Работно време</button><button class="btn secondary full" id="exportTravelPdf" style="margin-top:10px">'+icon('report')+'PDF 2 · Работа / пат</button><button class="btn secondary full" id="exportOvertimePdf" style="margin-top:10px">'+icon('report')+'PDF 3 · Само прекувремени</button><div class="actions"><button class="btn light" id="exportExcel">'+icon('download')+'Excel</button><button class="btn light" id="exportCsv">CSV</button></div></section>'+
    '<section class="card"><h2>Преглед по ден</h2><div class="table-wrap"><table class="table"><thead><tr><th>Датум</th><th>Вид</th><th>Работено</th><th>Прекувр.</th><th>На пат</th></tr></thead><tbody>'+p.rows.filter(r=>r.covered).map(r=>'<tr><td>'+r.date.slice(8)+'.'+r.date.slice(5,7)+'</td><td>'+Core.TYPES[r.type]+(r.future?' · план':'')+'</td><td>'+Core.hh(r.worked)+'</td><td>'+Core.hh(r.overtime)+'</td><td>'+Core.hh(r.travel)+'</td></tr>').join('')+'</tbody></table></div></section>';
  $('monthlyTab').onclick=function(){reportMode='month';const b=Core.monthBounds(month);reportFrom=b[0];reportTo=b[1];render();};
  $('rangeTab').onclick=function(){reportMode='range';render();};
  if(reportMode==='month')bindMonth();else $('applyPeriod').onclick=function(){try{const f=$('reportFrom').value,t=$('reportTo').value;Core.dates(f,t);reportFrom=f;reportTo=t;render();}catch(e){toast(e.message);}};
  $('exportWorkPdf').onclick=function(){safeExport(()=>exportWorkPdf(reportPeriod()));};
  $('exportTravelPdf').onclick=function(){safeExport(()=>exportTravelPdf(reportPeriod()));};
  $('exportOvertimePdf').onclick=function(){safeExport(()=>exportOvertimePdf(reportPeriod()));};
  $('exportExcel').onclick=function(){safeExport(()=>exportExcel(reportPeriod()));};
  $('exportCsv').onclick=function(){safeExport(()=>exportCsv(reportPeriod()));};
};

const renderSettingsPrevious=renderSettings;
renderSettings=function(){
  renderSettingsPrevious();
  const tracking=$('trackingStart');
  if(tracking&&!$('startThisMonth')){
    const quick=document.createElement('button');
    quick.type='button';
    quick.id='startThisMonth';
    quick.className='btn light full';
    quick.style.margin='8px 0 12px';
    quick.textContent='Почни од 1-ви овој месец';
    tracking.closest('label').insertAdjacentElement('afterend',quick);
    quick.onclick=function(){
      const first=Core.today().slice(0,7)+'-01';
      const next=JSON.parse(JSON.stringify(state));
      next.settings.trackStart=first;
      commit(next,'Почеток на евиденцијата од '+Core.displayDate(first));
      toast('Евиденцијата е поставена од '+Core.displayDate(first)+'. Поминатите денови од месецот се вклучени.');
    };
  }
  const ps=document.querySelectorAll('#app p');
  for(const p of ps)if(p.textContent.indexOf('Workfashion · Мои часови · v1.1')>=0)p.textContent='Workfashion · Мои часови · v2.6 · WFAG';
};


/* WFAG v2.8: daily GPS navigation and visible travel-history page. */
let routeDaySelected=Core.today();
let routeShownCount=12;
const routeGPSList=()=>routeNativeState().sessions.slice().sort((a,b)=>(Number(b.startedAt)||0)-(Number(a.startedAt)||0));
const routeDayFix=()=>{
  const today=Core.today();
  if(routeDaySelected>today)routeDaySelected=today;
  return routeDaySelected;
};
function routeDayCardMarkup(compact) {
  const day=routeDayFix(),all=routeGPSList(),sessions=all.filter(s=>routeDate(s.startedAt)===day);
  const travel=(state.trips||[]).filter(t=>t.date===day);
  const stops=sessions.reduce((v,s)=>v+(s.stops||[]).length,0);
  const distance=sessions.reduce((v,s)=>v+routeSessionMeters(s),0);
  const dates=[];
  for(let i=-3;i<=3;i++){const d=Core.plus(day,i);if(d<=Core.today())dates.push(d);}
  const nav='<div class="gps-day-controls">'+
    '<button type="button" class="gps-day-arrow" data-gps-offset="-1" aria-label="Претходен ден">‹</button>'+
    '<label class="gps-day-date"><span>Избран ден</span><input id="gpsDayPicker" type="date" max="'+Core.today()+'" value="'+day+'"></label>'+
    '<button type="button" class="gps-day-arrow" data-gps-offset="1" '+(day>=Core.today()?'disabled':'')+' aria-label="Следен ден">›</button></div>'+
    '<div class="gps-day-strip">'+dates.map(d=>'<button type="button" data-gps-date="'+d+'" class="gps-day-chip '+(d===day?'active':'')+'"><span>'+['Нед','Пон','Вто','Сре','Чет','Пет','Саб'][Core.weekday(d)]+'</span><b>'+Number(d.slice(8))+'</b></button>').join('')+'</div>';
  let content='<div class="gps-day-stats"><span><b>'+routeKm(distance)+'</b> поминато</span><span><b>'+sessions.length+'</b> GPS рути</span><span><b>'+stops+'</b> застанувања</span></div>';
  if(sessions.length){
    content+=sessions.map(s=>{
      const stopList=(s.stops||[]),start=routeClock(s.startedAt),end=s.endedAt?routeClock(s.endedAt):'во тек';
      const points=(s.points||[]).length+(s.hourSegments||[]).reduce((k,seg)=>k+(seg.rawPoints||[]).length+(seg.lineSegments||[]).length,0);
      return '<div class="gps-day-route"><div class="row"><strong>'+start+' – '+end+'</strong><span class="small muted">'+routeKm(routeSessionMeters(s))+'</span></div>'+
        routeSvg([s])+
        '<button type="button" class="btn secondary full" data-gps-open="'+esc(s.id||'')+'">Отвори вистинска офлајн мапа</button>'+
        (stopList.length?'<div class="gps-stop-heading">Каде си застанал</div>'+stopList.map((p,i)=>routeStopMarkup(p,i+1)).join(''):'<p class="small muted" style="margin-top:10px">Нема регистрирано застанувања подолги од 5 минути.</p>')+
        (points<2?'<p class="small muted">Се чекаат GPS координати за прикажување на целата линија.</p>':'')+
        '</div>';
    }).join('');
  }else {
    content+='<p class="gps-empty">За '+Core.displayDate(day)+' нема снимена GPS рута во оваа инсталација. '+
      'Работни патувања без зачувани координати не можат да се прикажат на мапа.</p>';
  }
  if(travel.length){
    content+='<div class="gps-stop-heading">Евидентирани службени патувања</div>'+travel.map(t=>
      '<div class="mini-row"><span>'+esc(t.departure||'—')+' → '+esc(t.arrival||'во тек')+'</span><strong>'+esc(t.note||'Службен пат')+'</strong></div>').join('');
  }
  return '<section class="card gps-day-card" id="gpsDayCard"><div class="row"><div><h2>Мапа и историја</h2><p class="small muted" style="margin:3px 0 7px">Листај денови со прст налево/надесно или со стрелките</p></div><span class="route-live '+(routeNativeState().tracking?'on':'')+'">'+(routeNativeState().tracking?'GPS':'OFF')+'</span></div>'+
    nav+content+(compact?'<button type="button" id="gpsSeeAll" class="btn light full">Сите поминати денови →</button>':'')+
    '</section>';
}
function navigateGPSDay(date){
  if(!Core.dateValid(date)||date>Core.today())return;
  routeDaySelected=date;
  const host=$('gpsDayHost');
  if(host){host.innerHTML=routeDayCardMarkup(view==='home');bindGPSDayControls();}
  if(view==='routes')renderGPSAllHistory();
}
function bindGPSDayControls(){
  const host=$('gpsDayHost');if(!host)return;
  host.querySelectorAll('[data-gps-offset]').forEach(btn=>btn.onclick=()=>navigateGPSDay(Core.plus(routeDayFix(),Number(btn.dataset.gpsOffset))));
  host.querySelectorAll('[data-gps-date]').forEach(btn=>btn.onclick=()=>navigateGPSDay(btn.dataset.gpsDate));
  const picker=$('gpsDayPicker');if(picker)picker.onchange=()=>navigateGPSDay(picker.value);
  host.querySelectorAll('[data-gps-open]').forEach(btn=>btn.onclick=()=>{
    if(typeof Android!=='undefined'&&Android.openRouteSessionMap)Android.openRouteSessionMap(btn.dataset.gpsOpen);
    else toast('Мапата е достапна во Android апликацијата.');
  });
  host.querySelectorAll('.route-map-open').forEach(btn=>btn.onclick=()=>{
    if(typeof Android!=='undefined'&&Android.openRouteMap)Android.openRouteMap(Number(btn.dataset.lat),Number(btn.dataset.lon));
  });
  const all=$('gpsSeeAll');if(all)all.onclick=()=>{view='routes';render();window.scrollTo(0,0);};
  let start=null;
  host.addEventListener('touchstart',e=>{
    if(e.touches.length!==1)return;
    start={x:e.touches[0].clientX,y:e.touches[0].clientY};
  },{passive:true});
  host.addEventListener('touchend',e=>{
    if(!start||!e.changedTouches.length)return;
    const dx=e.changedTouches[0].clientX-start.x,dy=e.changedTouches[0].clientY-start.y;start=null;
    if(Math.abs(dx)>75&&Math.abs(dx)>Math.abs(dy)*1.6){
      navigateGPSDay(Core.plus(routeDayFix(),dx>0?-1:1));
    }
  },{passive:true});
}
function renderGPSAllHistory(){
  const host=$('gpsAllHistory');if(!host)return;
  const all=routeGPSList(),today=Core.today(),shown=all.slice(0,routeShownCount);
  host.innerHTML='<div class="section-head"><h2>Сите снимени патувања</h2><span class="small muted">'+all.length+' GPS рути</span></div>'+
    (shown.length?shown.map(s=>{
      const d=routeDate(s.startedAt),st=s.stops||[];
      return '<section class="card gps-history-entry"><button type="button" class="gps-history-day" data-gps-select="'+d+'"><strong>'+Core.displayDate(d)+'</strong><span>'+routeClock(s.startedAt)+'–'+(s.endedAt?routeClock(s.endedAt):'во тек')+' · '+routeKm(routeSessionMeters(s))+'</span></button>'+
        routeSvg([s])+
        '<button type="button" class="btn secondary full" data-gps-history-map="'+esc(s.id||'')+'">Прикажи пат на офлајн мапа</button>'+
        '<p class="small muted" style="margin-top:9px">'+st.length+' застанувања'+(st.length?' · '+st.map(p=>(p.name||'Локација')+' ('+routeSpan(p.startAt,p.endAt)+')').map(esc).join(', '):'')+'</p></section>';
    }).join(''):'<section class="card"><p>Сè уште нема снимени GPS патувања во оваа Preview инсталација.</p><p class="small muted">За ново снимање избери „Тргнувам на пат“ и дозволи точна GPS локација. Старата WFAG има одделна GPS база.</p></section>')+
    (all.length>shown.length?'<button id="gpsHistoryLoadMore" class="btn secondary full">Прикажи повеќе денови</button>':'');
  host.querySelectorAll('[data-gps-select]').forEach(b=>b.onclick=()=>{
    navigateGPSDay(b.dataset.gpsSelect);
    const el=$('gpsDayCard');if(el)el.scrollIntoView({behavior:'smooth',block:'start'});
  });
  host.querySelectorAll('[data-gps-history-map]').forEach(b=>b.onclick=()=>{
    if(typeof Android!=='undefined'&&Android.openRouteSessionMap)Android.openRouteSessionMap(b.dataset.gpsHistoryMap);
  });
  const more=$('gpsHistoryLoadMore');
  if(more)more.onclick=()=>{routeShownCount+=12;renderGPSAllHistory();};
}
const homeWithGPS=renderHome;
renderHome=function(){
  homeWithGPS();
  const hero=document.querySelector('#app .hero');
  if(hero){
    hero.insertAdjacentHTML('afterend','<div id="gpsDayHost">'+routeDayCardMarkup(true)+'</div>');
    bindGPSDayControls();
  }
};
function renderRoutes(){
  $('app').innerHTML=header('GPS Мапа и историја','Види го денешниот или кој било претходен ден со застанувања, километража и мапа.')+
    '<div id="gpsDayHost">'+routeDayCardMarkup(false)+'</div>'+
    '<div id="gpsAllHistory"></div>'+
    '<section class="card"><p class="small muted">За вистинска офлајн мапа притисни „Отвори вистинска офлајн мапа“. Скицата погоре е GPS преглед, а не улична карта. За вклучување GPS снимање оди во „Денес“ → „Тргнувам на пат“.</p></section>';
  bindGPSDayControls();
  renderGPSAllHistory();
}
const previousRouteNativeChanged=window.onRouteNativeChanged;
window.onRouteNativeChanged=function(){
  if(typeof previousRouteNativeChanged==='function')previousRouteNativeChanged();
  const host=$('gpsDayHost');
  if(host){host.innerHTML=routeDayCardMarkup(view==='home');bindGPSDayControls();}
  if(view==='routes')renderGPSAllHistory();
};

if(window.WFApp){
  window.WFApp.editTrip=editTrip;
  window.WFApp.reportJson=workReportJson;
  window.WFApp.travelReportJson=travelReportJson;
  window.WFApp.exportRows=exportRows;
  window.WFApp.exportWorkPdf=exportWorkPdf;
  window.WFApp.exportTravelPdf=exportTravelPdf;
  window.WFApp.overtimeReportJson=overtimeReportJson;
  window.WFApp.exportOvertimePdf=exportOvertimePdf;
}
// GPS v2.8 callback above refreshes both route list and day panel.

setInterval(function(){refreshRoutePanel();checkRoutePrompt();},30000);
if(state)render();
})();
