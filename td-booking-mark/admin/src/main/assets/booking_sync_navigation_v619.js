/* MarK Booking 6.19 — keep synchronization in ☰, not over the calendar.
   Presentation-only: both existing sync engines and their state remain unchanged. */
(()=>{
  'use strict';
  const $=id=>document.getElementById(id);
  const main=document.querySelector('main');
  const drawer=$('drawer');
  const full=$('marKFullSyncPanel');
  if(!main||!drawer||!full||typeof window.navigate!=='function')return;

  const page=document.createElement('section');
  page.id='bookingSyncView';
  page.className='viewhidden no-print';
  page.setAttribute('aria-label','Синхронизација со MarK');
  page.innerHTML='<h2 style="margin:16px 0 6px">Синхронизација со MarK</h2>'
    +'<p class="muted">Поврзување и целосна синхронизација со компјутерот.</p>';
  main.appendChild(page);

  // Move, never rebuild: preserve all existing listeners, conflicts, pairing and sync state.
  page.appendChild(full);
  const pairing=$('tdMarKPanel');
  if(pairing){
    page.appendChild(pairing);
    pairing.style.display='block'; // parent page controls visibility
  }

  const oldEntry=[...drawer.querySelectorAll('button')]
    .find(btn=>btn.textContent.includes('MarK поврзување'));
  const entry=oldEntry||document.createElement('button');
  entry.type='button';
  entry.className='secondary';
  entry.id='bookingSyncMenu';
  entry.textContent='↻ Синхронизација со MarK';
  entry.onclick=()=>window.navigate('markSync');
  if(!oldEntry){
    const last=drawer.lastElementChild;
    if(last&&last.tagName==='BUTTON')drawer.insertBefore(entry,last);
    else drawer.appendChild(entry);
  }

  const oldNavigate=window.navigate;
  window.navigate=function(view){
    if(view==='markSync'){
      // Use existing navigation cleanup first, then isolate only this page.
      oldNavigate('weddings');
      for(const section of main.querySelectorAll(':scope > section')){
        if(section!==page)section.classList.add('viewhidden');
      }
      page.classList.remove('viewhidden');
      page.scrollIntoView({block:'start'});
      if(typeof window.toggleDrawer==='function')window.toggleDrawer(false);
      return;
    }
    page.classList.add('viewhidden');
    oldNavigate(view);
  };

  const pairingClose=$('tdMarkClose');
  if(pairingClose)pairingClose.onclick=()=>window.navigate('dashboard');
  // On launch calendar remains visible, while this page is always tucked away.
  page.classList.add('viewhidden');
})();