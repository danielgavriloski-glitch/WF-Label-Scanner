(function(root,factory){
 'use strict';
 const api=factory();
 if(typeof module==='object'&&module.exports){module.exports=api;return;}
 root.WFNotifications=api;
 api.install(root);
})(typeof globalThis!=='undefined'?globalThis:this,function(){
 'use strict';
 const copy=value=>JSON.parse(JSON.stringify(value));
 const safe=value=>String(value==null?'':value);
 function normalize(raw){
  if(typeof raw==='string')raw=raw.trim()?JSON.parse(raw):null;
  if(raw==null)return {version:1,notifications:[]};
  const rows=Array.isArray(raw)?raw:(raw.notifications||raw.items);
  if(!Array.isArray(rows))throw Error('Историјата на известувања не може да се прочита.');
  const byId=new Map();
  for(const row of rows){
   if(!row||typeof row.id!=='string'||!row.id)throw Error('Известувањето нема валиден број.');
   const stamp=typeof row.createdAt==='number'?row.createdAt:Date.parse(row.createdAt);
   if(!Number.isFinite(stamp)||stamp<0)throw Error('Известувањето нема валиден датум.');
   const item={id:row.id,title:safe(row.title),body:safe(row.body),createdAt:stamp,
    read:typeof row.read==='boolean'?row.read:Number(row.readAt)>0,
    type:safe(row.type||row.kind),targetId:safe(row.targetId||row.recordId),
    target:safe(row.target),recordIds:Array.isArray(row.recordIds)?row.recordIds.filter(id=>typeof id==='string'):[]};
   const before=byId.get(item.id);
   if(!before||item.createdAt>before.createdAt)byId.set(item.id,item);
   else if(item.createdAt===before.createdAt&&item.read)before.read=true;
  }
  return {version:1,notifications:Array.from(byId.values()).sort((a,b)=>b.createdAt-a.createdAt||a.id.localeCompare(b.id))};
 }
 function unread(history){return normalize(history).notifications.filter(row=>!row.read).length;}
 function readCopy(history,id){
  const next=normalize(history);
  for(const row of next.notifications)if(!id||row.id===id)row.read=true;
  return next;
 }
 function parseOpened(value){
  if(typeof value==='string'){
   const text=value.trim();
   if(text[0]==='{'){const row=JSON.parse(text);return typeof row.id==='string'?row.id:'';}
   return value;
  }
  return value&&typeof value.id==='string'?value.id:'';
 }
 function install(root){
  if(!root.document||root.document.getElementById('notificationBell'))return;
  const document=root.document,byId=id=>document.getElementById(id);
  const esc=value=>safe(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let history={version:1,notifications:[]},error='',onlyUnread=false,queued=false;
  const bridge=()=>root.Android;
  const currentState=()=>typeof state!=='undefined'?state:null;
  const openModal=(title,body)=>root.showModal(title,body);
  const toast=message=>{if(root.toast)root.toast(message);};
  const dateLabel=value=>new Intl.DateTimeFormat('mk-MK',{dateStyle:'medium',timeStyle:'short',timeZone:'Europe/Skopje'}).format(new Date(value));
  function load(){
   try{
    const android=bridge(),s=currentState();
    history=normalize(android&&android.getNotificationHistory?android.getNotificationHistory():s&&s.notificationHistory);
    error='';
   }catch(err){error=err.message||'Историјата на известувања не се отвори.';}
   updateBadge();return !error;
  }
  function saveRead(id){
   try{
    const android=bridge();
    if(android&&android.markNotificationRead){
     const result=!id&&android.markAllNotificationsRead?android.markAllNotificationsRead():android.markNotificationRead(id||'');
     if(result===false)throw Error('Прочитаното известување не се зачува. Обиди се повторно.');
    }else{
     const s=currentState();
     if(!s)throw Error('Евиденцијата не е отворена.');
     const next=copy(s);next.notificationHistory=readCopy(history,id);
     if(typeof persist!=='function')throw Error('Евиденцијата не може да се зачува.');
     persist(next);state=next;
    }
    return load();
   }catch(err){error=err.message;toast(error);return false;}
  }
  function updateBadge(){
   const bell=byId('notificationBell');if(!bell)return;
   const count=history.notifications.filter(row=>!row.read).length,badge=byId('notificationBadge');
   badge.textContent=count>99?'99+':String(count);badge.hidden=!count;
   bell.setAttribute('aria-label',count?'Известувања, непрочитани: '+count:'Известувања');
   bell.title=count?'Непрочитани известувања: '+count:'Известувања';
  }
  function activeGoods(){
   const s=currentState();
   return ((s&&s.pickupLog||{}).items||[]).filter(item=>item.status==='pending').length;
  }
  function openTarget(row){
   if(!currentState())return false;
   const type=row.type||row.target;
   if(type==='pickup'||type==='pickups'){
    if(row.targetId&&root.WFPickups&&root.WFPickups.openItem)return root.WFPickups.openItem(row.targetId);
    if(root.WFApp){root.closeModal();root.WFApp.setView('pickups');return true;}
   }
   if(type==='service'||type==='vehicle'){
    if(root.WFVehicle&&root.WFVehicle.setTab){root.closeModal();root.WFVehicle.setTab('services');return true;}
    if(root.WFApp){root.closeModal();root.WFApp.setView('vehicle');return true;}
   }
   return false;
  }
  function showDetail(id){
   let row=history.notifications.find(item=>item.id===id);
   if(!row){showList();toast('Известувањето не е пронајдено во зачуваната историја.');return;}
   if(!row.read)saveRead(id);
   row=history.notifications.find(item=>item.id===id)||row;
   const target=['pickup','pickups','service','vehicle'].includes(row.type||row.target);
   openModal('Известување',`<section data-notification-center="detail" data-notification-id="${esc(row.id)}"><p class="notification-date">${esc(dateLabel(row.createdAt))}</p><h3 class="notification-detail-title">${esc(row.title||'Известување')}</h3><p class="notification-body">${esc(row.body)}</p>${error?'<p class="note error" role="alert">'+esc(error)+'</p>':''}<div class="actions">${target?'<button class="btn" id="notificationOpenTarget">'+((row.type||row.target)==='pickup'||row.target==='pickups'?'Отвори роба':'Отвори комбе')+'</button>':''}<button class="btn secondary" id="notificationBack">Сите известувања</button></div></section>`);
   byId('notificationBack').onclick=()=>{load();showList();};
   if(byId('notificationOpenTarget'))byId('notificationOpenTarget').onclick=()=>{if(!openTarget(row))toast('Поврзаниот запис не е достапен. Известувањето е зачувано тука.');};
  }
  function showList(){
   const all=history.notifications,rows=all.filter(row=>!onlyUnread||!row.read),count=all.filter(row=>!row.read).length,goods=activeGoods();
   openModal('Известувања',`<section data-notification-center="list"><div class="notification-toolbar"><div class="notification-filters" aria-label="Приказ на известувања"><button class="btn ${onlyUnread?'light':'secondary'}" id="notificationAll" aria-pressed="${!onlyUnread}">Сите (${all.length})</button><button class="btn ${onlyUnread?'secondary':'light'}" id="notificationUnread" aria-pressed="${onlyUnread}">Непрочитани (${count})</button></div>${count?'<button class="link-btn" id="notificationReadAll">Означи ги сите како прочитани</button>':''}</div>${error?'<p class="note error" role="alert">'+esc(error)+'</p><button class="btn secondary full" id="notificationRetry">Обиди се повторно</button>':''}${rows.length?'<ol class="notification-list">'+rows.map(row=>`<li><button class="notification-entry ${row.read?'is-read':'is-unread'}" data-notification="${esc(row.id)}"><span class="notification-entry-heading"><strong>${esc(row.title||'Известување')}</strong>${row.read?'':'<span class="notification-new">Ново</span>'}</span><span class="notification-preview">${esc(row.body)}</span><span class="notification-date">${esc(dateLabel(row.createdAt))}</span></button></li>`).join('')+'</ol>':'<p class="empty">'+(onlyUnread?'Нема непрочитани известувања.':'Сè уште нема зачувани известувања. Новите потсетници ќе можеш да ги прочиташ тука.')+'</p>'}${!all.length&&goods?'<section class="note"><strong>Активни потсетници за роба: '+goods+'</strong><p class="small">Робата чека подигање; ова е листа на активни потсетници, а не историја на испратени известувања.</p><button class="btn secondary full" id="notificationActiveGoods">Отвори роба за подигање</button></section>':''}</section>`);
   byId('notificationAll').onclick=()=>{onlyUnread=false;showList();};
   byId('notificationUnread').onclick=()=>{onlyUnread=true;showList();};
   if(byId('notificationReadAll'))byId('notificationReadAll').onclick=()=>{saveRead('');showList();};
   if(byId('notificationRetry'))byId('notificationRetry').onclick=()=>{load();showList();};
   if(byId('notificationActiveGoods'))byId('notificationActiveGoods').onclick=()=>openTarget({type:'pickup'});
   document.querySelectorAll('[data-notification]').forEach(button=>button.onclick=()=>showDetail(button.dataset.notification));
  }
  function refresh(){
   load();
   const panel=document.querySelector('[data-notification-center]'),modal=byId('modal');
   if(panel&&modal&&!modal.hidden&&panel.dataset.notificationCenter==='list')showList();
  }
  function scheduleRefresh(){
   if(queued)return;queued=true;
   Promise.resolve().then(()=>{queued=false;refresh();});
  }
  function init(){
   const settings=byId('settingsButton');if(!settings)return;
   const controls=document.createElement('div');controls.className='notification-header-actions';
   const bell=document.createElement('button');bell.id='notificationBell';bell.type='button';bell.className='icon-btn notification-bell';bell.setAttribute('aria-label','Известувања');bell.setAttribute('aria-haspopup','dialog');
   bell.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/></svg><span id="notificationBadge" class="notification-badge" hidden aria-hidden="true">0</span>';
   settings.parentNode.insertBefore(controls,settings);controls.appendChild(bell);controls.appendChild(settings);
   bell.onclick=()=>{onlyUnread=false;load();showList();};
   load();
   const previousChanged=root.onNotificationHistoryChanged;
   root.onNotificationHistoryChanged=function(){if(previousChanged)previousChanged();scheduleRefresh();};
   const previousOpened=root.onNotificationOpened;
   root.onNotificationOpened=function(value){
    if(previousOpened)previousOpened(value);
    let id='';try{id=parseOpened(value);}catch(err){toast('Известувањето не може да се отвори.');}
    load();if(id)showDetail(id);else showList();
   };
   document.addEventListener('visibilitychange',()=>{if(!document.hidden)scheduleRefresh();});
   root.addEventListener('focus',scheduleRefresh);
   api.open=()=>{load();showList();};api.refresh=scheduleRefresh;
  }
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',init,{once:true});else init();
 }
 const api={normalize,unread,readCopy,parseOpened,install};
 return api;
});
