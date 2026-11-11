/* WFAG workday controls; stored inside the existing app backup/state. */
(function(){
'use strict';
const W=window.WFAGWorkday;
if(!W||typeof window.WFApp==='undefined')return;
const date=()=>Core.today(),now=()=>Date.now();
function getDay(){const s=WFApp.getState();return s.workdaySessions&&s.workdaySessions[date()]||W.newDay(date());}
function saveDay(d,label){
 const s=WFApp.getState();
 s.workdaySessions=s.workdaySessions||{};
 s.workdaySessions[d.date]=d;
 Core.validate(s);
 s.audit.push({at:new Date().toISOString(),text:label});
 if(typeof Android!=='undefined'){if(!Android.saveState(JSON.stringify(s)))throw Error('Неуспешно зачувување');}
 else localStorage.setItem(Core.APP_ID,JSON.stringify(s));
 // Synchronise the existing app's in-memory state by reloading its saved state.
 location.reload();
}
function button(label,action,cls){return '<button type="button" class="btn '+(cls||'secondary')+'" data-workday="'+action+'">'+label+'</button>';}
function draw(){
 const host=document.getElementById('app');if(!host||!window.WFApp||!host.querySelector('.hero'))return;
 const old=document.getElementById('wfagWorkdayControls');if(old)old.remove();
 const d=getDay(),t=W.totals(d,now());
 const panel=document.createElement('section');panel.id='wfagWorkdayControls';panel.className='card';
 let actions='';
 if(d.status==='not-started')actions=button('Започни работен ден','start','primary');
 else if(d.status==='travel')actions=button('Пристигнав','arrive')+button('Заврши работен ден','finish','danger');
 else if(d.status==='working')actions=button('На пат','depart')+button('Заврши работен ден','finish','danger');
 panel.innerHTML='<h3>Работен ден · контрола</h3><p class="small muted">Статус: '+({ 'not-started':'Не е започнат',working:'Работа',travel:'На пат',finished:'Завршен'}[d.status]||d.status)+'</p><div class="mini-row"><span>Од почеток</span><strong>'+Core.hh(t.workedMinutes)+'</strong></div><div class="mini-row"><span>Време на пат</span><strong>'+Core.hh(t.travelMinutes)+'</strong></div>'+(d.endAt!==null?'<div class="mini-row"><span>Разлика од 8 часа</span><strong>'+Core.hhSigned(t.overtimeDelta)+'</strong></div>':'')+'<div class="actions">'+actions+'</div><p class="small muted">Ова е пробна евиденција. Не го менува постојниот фонд додека не се поврзат двете пресметки.</p>';
 host.querySelector('.hero').insertAdjacentElement('afterend',panel);
 panel.querySelectorAll('[data-workday]').forEach(b=>b.addEventListener('click',()=>{
  try{
   const action=b.dataset.workday,current=getDay(),time=now();
   const updated=action==='start'?W.start(current,time,'manual'):action==='depart'?W.depart(current,time):action==='arrive'?W.arrive(current,time):W.finish(current,time);
   saveDay(updated,'Работен ден: '+action);
  }catch(e){alert(e.message);}
 }));
}
const previous=window.WFApp.setView;
window.WFApp.setView=function(v){previous(v);if(v==='home')draw();};
draw();
const observer=new MutationObserver(()=>{if(document.querySelector('.hero')&&!document.getElementById('wfagWorkdayControls'))draw();});
observer.observe(document.getElementById('app'),{childList:true});
})();
