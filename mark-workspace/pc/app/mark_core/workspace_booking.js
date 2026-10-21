/* Compact presentation added after the unchanged Booking 6.20 business code. */
(() => {
 'use strict';
 if(window.MarKBookingWorkspace)return;
 const KEY='markWorkspaceAppearanceV1';let prefs={theme:'gray'};
 try{prefs={...prefs,...JSON.parse(localStorage.getItem(KEY)||'{}')};}catch{}
 document.body.classList.add('mark-workspace-booking');
 function theme(value){prefs.theme=['gray','light','dark'].includes(value)?value:'gray';document.documentElement.dataset.workspaceTheme=prefs.theme;try{localStorage.setItem(KEY,JSON.stringify(prefs));}catch{}}
 theme(prefs.theme);
 const settings=document.getElementById('tdPageSettings')||document.getElementById('settingsView');
 if(settings){const section=document.createElement('section');section.className='card ws-booking-settings';const heading=document.createElement('h3');heading.textContent='Работен изглед';section.append(heading);
 const label=document.createElement('label');label.textContent='Тема';const select=document.createElement('select');select.id='wsBookingTheme';select.setAttribute('aria-label','Тема на интерфејсот');
 for(const [value,text] of [['gray','Сива'],['light','Светла'],['dark','Темна']]){const option=document.createElement('option');option.value=value;option.textContent=text;select.append(option);}select.value=prefs.theme;select.onchange=()=>theme(select.value);label.append(select);section.append(label);settings.append(section);}
 const badge=document.createElement('span');badge.className='ws-test-label';badge.textContent='TEST';
 const header=document.querySelector('header'),logo=document.getElementById('tdMarkBrandBadge'),pageTitle=document.getElementById('tdCurrentPage');
 if(header){
  if(logo){logo.style.setProperty('position','static','important');header.prepend(logo);}
  if(pageTitle){pageTitle.style.flex='1';header.append(pageTitle);}
  header.append(badge);
  const menu=Array.from(document.querySelectorAll('button')).find(b=>b.textContent.trim()==='☰');
  if(menu){menu.style.setProperty('position','static','important');menu.style.setProperty('color','var(--ws-text)','important');menu.style.setProperty('background','var(--ws-inset)','important');menu.setAttribute('aria-label','Отвори мени');header.append(menu);}
 }
 if(location.pathname==='/api/mark/booking/app')document.body.classList.add('ws-booking-pc');
 const finishLayout=()=>{const panel=document.getElementById('marKFullSyncPanel'),target=document.getElementById('tdSyncPage')||document.getElementById('tdPageSettings');if(panel&&target&&panel.parentNode!==target)target.append(panel);};
 finishLayout();document.addEventListener('DOMContentLoaded',finishLayout,{once:true});window.addEventListener('load',finishLayout,{once:true});
 // No resize polling, broad MutationObserver, auto-sync or automatic business writes.
 window.MarKBookingWorkspace={theme,version:'2026.10.21-test1'};
})();
