(function(root,factory){
  const api=factory(typeof module==='object'&&module.exports?require('./core.js'):root.Core);
  if(typeof module==='object'&&module.exports)module.exports=api;else root.Vehicle=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(Core){
'use strict';
const collections=['readings','fills','tireSets','tireChanges','trips','averages'];
const clone=x=>JSON.parse(JSON.stringify(x));
const live=xs=>(xs||[]).filter(x=>!x.deleted);
const order=(a,b)=>a.at.localeCompare(b.at)||((a.odo??0)-(b.odo??0))||a.id.localeCompare(b.id);
const sorted=xs=>live(xs).slice().sort(order);
const id=()=>('van_'+Date.now().toString(36)+'_'+Math.random().toString(36).slice(2,11));
function number(value,label,optional=false){
  if(value===null||value===undefined||String(value).trim()===''){
    if(optional)return null;throw Error('Внеси '+label+'.');
  }
  const text=String(value).trim().replace(/\s/g,'').replace(',','.');
  if(!/^\d+(\.\d+)?$/.test(text))throw Error('Провери '+label+' (број без разделувач на илјади).');
  const n=Number(text);if(!Number.isFinite(n)||n>10000000)throw Error('Невалидна '+label+'.');return n;
}
function dateTime(value){
  if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)||!Core.dateValid(value.slice(0,10)))throw Error('Внеси валиден датум и час.');
  Core.minutes(value.slice(11));return value;
}
function fresh(){return {version:1,name:'Volkswagen Crafter',autoLinkFrom:0,readings:[],fills:[],tireSets:[{id:'summer',name:'Летни',season:'summer',note:''},{id:'winter',name:'Зимски',season:'winter',note:''}],tireChanges:[],trips:[],averages:[],audit:[]};}
function tripKm(t){return t.startOdo!==null&&t.endOdo!==null?t.endOdo-t.startOdo:t.km;}
function observations(log){
  const rows=[];
  for(const kind of ['readings','fills','tireChanges'])for(const r of live(log[kind]))rows.push({id:kind+'_'+r.id,at:r.at,odo:r.odo,kind});
  for(const r of live(log.trips)){
    if(r.startOdo!==null)rows.push({id:'start_'+r.id,at:r.at,odo:r.startOdo,kind:'trip'});
    if(r.endOdo!==null)rows.push({id:'end_'+r.id,at:r.endAt,odo:r.endOdo,kind:'trip'});
  }
  return rows.sort(order);
}
function validate(log){
  if(!log||log.version!==1||typeof log.name!=='string'||!log.name.trim()||log.name.length>100)throw Error('Невалидна евиденција за комбето.');
  if(log.autoLinkFrom!==undefined&&(!Number.isFinite(log.autoLinkFrom)||log.autoLinkFrom<0))throw Error('Невалидна поставка за GPS патувањата.');
  const ids=new Set();
  for(const kind of collections){
    if(!Array.isArray(log[kind])||log[kind].length>37000)throw Error('Невалидна листа: '+kind);
    for(const r of log[kind]){
      if(!r||typeof r.id!=='string'||!/^[-a-zA-Z0-9_]{1,100}$/.test(r.id)||ids.has(r.id))throw Error('Невалиден или дупликат запис за комбето.');
      ids.add(r.id);
      if(r.deleted!==undefined&&typeof r.deleted!=='boolean')throw Error('Невалиден статус на записот.');
      if(typeof r.note!=='string'||r.note.length>1500)throw Error('Предолга или невалидна белешка.');
      if(kind==='tireSets'){
        if(typeof r.name!=='string'||!r.name.trim()||r.name.length>100||!['summer','winter','all'].includes(r.season))throw Error('Провери го името и видот на гумите.');
        continue;
      }
      dateTime(r.at);
      if(['readings','fills','tireChanges'].includes(kind)&&(!Number.isFinite(r.odo)||r.odo<0||r.odo>10000000))throw Error('Невалидна километража.');
      if(kind==='fills'){
        if(!Number.isFinite(r.litres)||r.litres<=0||r.litres>2000||typeof r.full!=='boolean')throw Error('Внеси литри поголеми од нула и статус до полно.');
        if(r.cost!==null&&(!Number.isFinite(r.cost)||r.cost<0||r.cost>10000000))throw Error('Невалиден платен износ.');
      }
      if(kind==='averages'&&(!Number.isFinite(r.l100)||r.l100<=0||r.l100>100))throw Error('Просекот мора да биде над 0 и најмногу 100 L/100 km.');
      if(kind==='tireChanges'&&r.setId!==null&&!live(log.tireSets).some(s=>s.id===r.setId))throw Error('Избери постоечки комплет гуми.');
      if(kind==='trips'){
        dateTime(r.endAt);if(r.endAt<r.at)throw Error('Крајот на патувањето е пред почетокот.');
        for(const k of ['startOdo','endOdo','km'])if(r[k]!==null&&(!Number.isFinite(r[k])||r[k]<0||r[k]>10000000))throw Error('Невалидни километри за патувањето.');
        if((r.startOdo===null)!==(r.endOdo===null))throw Error('Внеси почетна и крајна километража, или само растојание.');
        if(r.startOdo===null&&r.km===null)throw Error('Внеси километри за патувањето.');
        if(tripKm(r)<0)throw Error('Крајната километража е помала од почетната.');
        if(!['manual','gps','wfag'].includes(r.source)||typeof r.sourceId!=='string'||r.sourceId.length>160||typeof r.from!=='string'||r.from.length>200||typeof r.to!=='string'||r.to.length>200)throw Error('Невалиден извор или опис на патувањето.');
      }
    }
  }
  const points=observations(log);
  for(let i=1;i<points.length;i++)if(points[i].odo+0.00001<points[i-1].odo)throw Error('Километражата се намалува по датум. Провери ги датумот, часот и состојбата на таблата.');
  const links=new Set();
  for(const t of live(log.trips))if(t.sourceId){if(links.has(t.sourceId))throw Error('Ова патување е веќе поврзано со комбето.');links.add(t.sourceId);}
  const changes=sorted(log.tireChanges);
  for(let i=1;i<changes.length;i++)if(changes[i].at===changes[i-1].at)throw Error('Две промени на гуми имаат ист датум и час.');
  if(!Array.isArray(log.audit)||log.audit.length>5000||log.audit.some(r=>!r||typeof r.at!=='string'||typeof r.action!=='string'))throw Error('Невалидна историја на комбето.');
  return log;
}
function record(kind,input){
  const r={id:input.id||id(),note:String(input.note||'').trim(),deleted:!!input.deleted};
  if(kind==='tireSets')return {...r,name:String(input.name||'').trim(),season:input.season};
  r.at=dateTime(input.at);
  if(['readings','fills','tireChanges'].includes(kind))r.odo=number(input.odo,'километража');
  if(kind==='fills')Object.assign(r,{litres:number(input.litres,'литри'),full:!!input.full,cost:number(input.cost,'платен износ',true)});
  if(kind==='tireChanges')r.setId=input.setId||null;
  if(kind==='averages')r.l100=number(input.l100,'просечна потрошувачка');
  if(kind==='trips')Object.assign(r,{endAt:dateTime(input.endAt),startOdo:number(input.startOdo,'почетна километража',true),endOdo:number(input.endOdo,'крајна километража',true),km:number(input.km,'растојание',true),source:input.source||'manual',sourceId:String(input.sourceId||''),from:String(input.from||'').trim(),to:String(input.to||'').trim()});
  return r;
}
function audit(log,kind,action,before,after){
  log.audit.push({at:new Date().toISOString(),kind,id:(after||before).id,action,before:before?clone(before):null,after:after?clone(after):null});
  if(log.audit.length>5000)log.audit=log.audit.slice(-5000);
}
function put(log,kind,input){
  if(!collections.includes(kind))throw Error('Непознат вид на запис.');
  const next=clone(log),r=record(kind,input),index=next[kind].findIndex(x=>x.id===r.id);
  if(input.id&&index<0)throw Error('Записот повеќе не постои.');
  const before=index<0?null:clone(next[kind][index]);
  if(index<0)next[kind].push(r);else next[kind][index]=r;
  audit(next,kind,before?'edit':'add',before,r);return validate(next);
}
function setDeleted(log,kind,rowId,deleted){
  if(!collections.includes(kind))throw Error('Непознат запис.');
  const next=clone(log),r=next[kind].find(x=>x.id===rowId);if(!r)throw Error('Записот не постои.');
  const before=clone(r);r.deleted=deleted;
  audit(next,kind,deleted?'remove':'restore',before,r);return validate(next);
}
function rename(log,name){const next=clone(log);next.name=String(name||'').trim();return validate(next);}
function latest(log,until='2100-12-31T23:59'){return observations(log).filter(r=>r.at<=until).pop()||null;}
function rateAt(log,at){const rate=sorted(log.averages).filter(r=>r.at<=at).pop();return rate?rate.l100:null;}
function fuelAnalysis(log){
  const rows=[];let anchor=null,pending=0,previous=null;
  for(const r of sorted(log.fills)){
    const km=previous?r.odo-previous.odo:null;
    const rate=previous?rateAt(log,previous.at):null;
    const row={...r,kmSincePrevious:km,estimatedLitres:km!==null&&rate!==null?km*rate/100:null,estimateRate:rate,cycle:null,status:'no-baseline'};
    if(anchor){
      pending+=r.litres;row.status='partial';
      if(r.full){const distance=r.odo-anchor.odo;row.cycle={from:anchor.at,to:r.at,km:distance,litres:pending,l100:distance>0?pending/distance*100:null};row.status=distance>0?'measured':'zero-distance';}
    }
    if(r.full){anchor=r;pending=0;if(!row.cycle)row.status='baseline';}
    rows.push(row);previous=r;
  }
  return rows;
}
function tires(log,until='2100-12-31T23:59'){
  const changes=sorted(log.tireChanges).filter(r=>r.at<=until),last=latest(log,until);
  const sets=live(log.tireSets).map(s=>({...s,totalKm:0,active:false,currentKm:0,intervals:[]}));
  changes.forEach((change,i)=>{
    const set=sets.find(s=>s.id===change.setId);if(!set)return;
    const next=changes[i+1];const end=next?next.odo:last?last.odo:change.odo;
    const km=Math.max(0,end-change.odo),open=!next;
    set.totalKm+=km;set.intervals.push({id:change.id,from:change.at,to:next?next.at:null,start:change.odo,end,km});
    if(open){set.active=true;set.currentKm=km;}
  });
  return sets;
}
function summary(log,from='2000-01-01',to='2100-12-31'){
  if(!Core.dateValid(from)||!Core.dateValid(to)||from>to)throw Error('Провери го периодот.');
  const within=r=>r.at.slice(0,10)>=from&&r.at.slice(0,10)<=to;
  const fuel=fuelAnalysis(log).filter(within),trips=sorted(log.trips).filter(within);
  const cycles=fuel.filter(r=>r.cycle).map(r=>r.cycle);
  const litres=cycles.reduce((n,c)=>n+c.litres,0),km=cycles.reduce((n,c)=>n+c.km,0);
  const obs=observations(log).filter(within),first=obs[0]||null,last=obs[obs.length-1]||null;
  const readings=sorted(log.readings).filter(within);
  let estimatedOdoLitres=null,estimatedOdoKm=0;
  for(let i=1;i<obs.length;i++){
    const distance=obs[i].odo-obs[i-1].odo,rate=rateAt(log,obs[i-1].at);
    if(rate!==null&&distance>0){estimatedOdoLitres=(estimatedOdoLitres||0)+distance*rate/100;estimatedOdoKm+=distance;}
  }
  const forecastTrips=trips.map(t=>({km:tripKm(t),rate:rateAt(log,t.at)}));
  const known=forecastTrips.filter(t=>t.rate!==null);
  return {from,to,fuel,trips,cycles,tripCount:trips.length,tripKm:trips.reduce((n,t)=>n+tripKm(t),0),fuelAdded:fuel.reduce((n,r)=>n+r.litres,0),fuelCost:fuel.reduce((n,r)=>n+(r.cost||0),0),costCount:fuel.filter(r=>r.cost!==null).length,measuredLitres:litres,measuredKm:km,measuredL100:km>0?litres/km*100:null,estimatedTripLitres:known.length?known.reduce((n,t)=>n+t.km*t.rate/100,0):null,estimatedTripKm:known.reduce((n,t)=>n+t.km,0),estimatedOdoLitres,estimatedOdoKm,odoKm:first&&last?last.odo-first.odo:null,first,last,readings};
}
function gpsKm(session){
  if(Number.isFinite(Number(session.distanceM))&&Number(session.distanceM)>0)return Number(session.distanceM)/1000;
  const points=Array.isArray(session.points)?session.points:[];let metres=0;const rad=x=>Number(x)*Math.PI/180;
  for(let i=1;i<points.length;i++){
    const a=points[i-1],b=points[i];if(![a.lat,a.lon,b.lat,b.lon].every(x=>Number.isFinite(Number(x))))continue;
    const h=Math.sin(rad(b.lat-a.lat)/2)**2+Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(rad(b.lon-a.lon)/2)**2;
    const d=6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
    const seconds=Math.max(1,(Number(b.at)-Number(a.at))/1000);
    if(Number.isFinite(d)&&d<=Math.max(500,seconds*80))metres+=d;
  }
  return metres/1000;
}
function autoLink(log,sessions){
  if(!log.autoLinkFrom)return {log,added:0};
  let next=log,added=0;const known=new Set(log.trips.map(t=>t.sourceId));
  const local=ms=>{const p=Core.parts(new Date(Number(ms)));return p.date+'T'+p.hour+':'+p.minute;};
  for(const s of sessions||[]){
    if(!s.id||!Number.isFinite(Number(s.startedAt))||!Number.isFinite(Number(s.endedAt))||!s.endedAt||Number(s.startedAt)<log.autoLinkFrom||Number(s.endedAt)<Number(s.startedAt))continue;
    const sourceId=s.tripId?'wfag:'+s.tripId:'gps:'+s.id;if(known.has(sourceId))continue;
    next=put(next,'trips',{at:local(s.startedAt),endAt:local(s.endedAt),km:Math.round(gpsKm(s)*1000)/1000,startOdo:null,endOdo:null,source:'gps',sourceId,from:'',to:String(((s.stops||[]).slice(-1)[0]||{}).name||'').slice(0,200),note:'Автоматски поврзано GPS патување; растојанието може да се коригира.'});
    known.add(sourceId);added++;
  }
  return {log:next,added};
}
function estimatedOdometer(log){
  const confirmed=latest(log);if(!confirmed)return null;
  const km=live(log.trips).filter(t=>t.source==='gps'&&t.startOdo===null&&t.at>=confirmed.at).reduce((n,t)=>n+tripKm(t),0);
  return {odo:confirmed.odo+km,additionalKm:km,from:confirmed.at};
}
const api={fresh,validate,put,setDeleted,rename,latest,observations,rateAt,fuelAnalysis,tires,summary,number,dateTime,tripKm,gpsKm,autoLink,estimatedOdometer,live,sorted};
// Optional additive field: old backups and attendance-only installations stay valid.
if(Core&&!Core.__vehicleV1){
  const previous=Core.validate;
  Core.validate=function(raw){previous(raw);if(raw.vehicleLog!==undefined)validate(raw.vehicleLog);return raw;};
  Core.__vehicleV1=true;
}
return api;
});
