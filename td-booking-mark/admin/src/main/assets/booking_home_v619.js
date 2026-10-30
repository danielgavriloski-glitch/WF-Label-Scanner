/* MarK Booking 6.19: focused calendar-first navigation, safe presentation-only layer.
   Booking/finance keys and existing signed documents stay unchanged. */
(()=>{'use strict';
const $=id=>document.getElementById(id);
const nav=window.navigate;
if(typeof nav!=='function'||!$('calendar')||!$('searchWedding'))return;
const style=document.createElement('style');
style.textContent=`
#calendar{gap:5px}
#calendar .booked{background:#d7ece3;color:#153d35;font-weight:750}
#calendar .booked .booking-day-count{display:block;background:#146e5a;color:#fff;border-radius:999px;min-width:23px;padding:2px 4px;margin:3px auto 0;width:max-content;font-size:12px;font-weight:750}
#calendar .today{outline:2px solid #b69b57}
#calendar [onclick]{cursor:pointer}
#calendar [onclick]:focus-visible{outline:3px solid #b69b57;outline-offset:2px}
#selectedDayBookings .event{padding:14px 6px;border-bottom:1px solid #dbeae4}
#selectedDayBookings .event:focus-visible{outline:2px solid #b69b57}
#homeSearchStatus{min-height:22px}
`;
document.head.appendChild(style);
const listCard=$('listCard'),search=$('searchWedding');
const title=listCard?.querySelector('h2');if(title)title.textContent='Пребарај настан';
search.placeholder='Име, датум (2026-11-20), вид или пакет';
search.setAttribute('aria-label','Пребарај настан по име, датум, вид или пакет');
const hint=document.createElement('p');hint.id='homeSearchStatus';hint.className='muted';hint.setAttribute('aria-live','polite');search.insertAdjacentElement('afterend',hint);
const calendarTitle=$('overview')?.querySelector('h2');if(calendarTitle)calendarTitle.textContent='Календар на настани';
const add=$('overview')?.querySelector('button[onclick="newBooking()"]');if(add)add.textContent='+ Додај настан';
const dashAdd=$('dashboard')?.querySelector('button[onclick="newBooking()"]');if(dashAdd)dashAdd.textContent='+ Додај настан';
const navHome=document.querySelector('#bottomNav button[data-view="dashboard"]'),navSearch=document.querySelector('#bottomNav button[data-view="weddings"]');
if(navHome){navHome.textContent='Календар';navHome.onclick=()=>window.navigate('dashboard')}
if(navSearch){navSearch.textContent='Пребарај';navSearch.onclick=()=>{window.navigate('weddings');search.focus();search.scrollIntoView({block:'center'})}}
function styleCalendar(){
 for(const cell of $('calendar').querySelectorAll('div.booked')){
   if(cell.querySelector('.booking-day-count'))continue;
   const match=cell.textContent.trim().match(/^(\d+)\s*·\s*(\d+)$/);
   if(!match)continue;
   cell.innerHTML='<span>'+match[1]+'</span><span class="booking-day-count" aria-label="'+match[2]+' настани">'+match[2]+'</span>';
 }
 for(const cell of $('calendar').querySelectorAll('div[onclick]')){
   cell.setAttribute('role','button');cell.tabIndex=0;
   cell.onkeydown=event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();cell.click()}};
 }
}
function applySearch(){
 const q=search.value.trim().toLocaleLowerCase('mk-MK');
 const records=new Map((Array.isArray(window.data)?window.data:typeof data!=='undefined'?data:[]).map(x=>[String(x.id),x]));
 let found=0;
 for(const card of document.querySelectorAll('#bookings .event')){
   const match=String(card.getAttribute('onclick')||'').match(/openBookingSummary\('([^']+)'\)/);
   const b=records.get(match?.[1]||'');
   const hay=[card.textContent,b?.phone,b?.date,b?.eventType,b?.start,b?.venue,typeof eventTypeLabel==='function'&&b?eventTypeLabel(b.eventType):'',typeof packageName==='function'&&b?packageName(b.package):''].join(' ').toLocaleLowerCase('mk-MK');
   const show=!!q&&hay.includes(q);
   card.hidden=!show;if(show)found++;
 }
 hint.textContent=!q?'Внеси име, датум или пакет за да се појават резултати.':found?found+' пронајдени настани':'Нема резултати за ова пребарување.';
}
const oldFilter=window.filterBookings;
window.filterBookings=function(){if(typeof oldFilter==='function')oldFilter();applySearch()};
const originalRender=window.render;
window.render=function(){originalRender();styleCalendar();applySearch()};
const originalSummary=window.openBookingSummary;
window.openBookingSummary=function(id){
 originalSummary(id);
 const b=(typeof data!=='undefined'&&Array.isArray(data)?data:[]).find(x=>x.id===id);
 if(!b)return;
 const area=$('bookingSummary');
 const f=typeof finance!=='undefined'&&finance&&typeof finance==='object'?finance[id]:null;
 const crew=Array.isArray(f?.crew)?f.crew:[];
 const section=document.createElement('section');section.id='bookingSummaryTeam';
 const heading=document.createElement('h3');heading.textContent='Екипа на настанот';section.append(heading);
 if(crew.length){
   for(const c of crew){
     const entry=document.createElement('p');entry.textContent=String(c.name||'Непознат')+' · '+String(c.role||'Без улога');section.append(entry);
   }
 }else{
   const note=document.createElement('p');note.className='muted';note.textContent='Сè уште нема запишана екипа за овој настан.';section.append(note);
 }
 const anchor=area.querySelector('#bookingSummaryEdit');if(anchor)area.insertBefore(section,anchor);else area.append(section);
};
window.navigate=function(view){nav(view==='dashboard'?'weddings':view);
 if(navHome)navHome.classList.toggle('active',view==='dashboard'||view==='weddings');
 if(navSearch)navSearch.classList.toggle('active',false);
};
search.addEventListener('input',applySearch);
styleCalendar();applySearch();
window.navigate('dashboard');
})();