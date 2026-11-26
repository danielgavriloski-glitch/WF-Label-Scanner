(function(){
  'use strict';
  let timer=null,renderedDay='';
  const baseRender=render;
  function reviewDates(){
    if(view==='home')return Core.reviewDates(state);
    if(view==='history')return Core.period(state,...Core.monthBounds(month)).reviewDates||[];
    if(view==='reports')return reportPeriod().reviewDates||[];
    return [];
  }
  function showReview(){
    const old=$('hoursReview');if(old)old.remove();
    const dates=reviewDates();if(!dates.length)return;
    const box=document.createElement('section');box.id='hoursReview';box.className='card hours-review';box.setAttribute('role','status');
    box.innerHTML='<h2>Потребна проверка на часовите</h2><p>За '+dates.length+' '+(dates.length===1?'ден недостига':'дена недостига')+' пристигнување или времињата не се усогласени. Збировите се делумни и ги содржат само пресметливите записи.</p>'+dates.slice(0,15).map(date=>'<button class="btn light" type="button" data-review-date="'+date+'">'+Core.displayDate(date)+'</button>').join('');
    $('app').prepend(box);
    box.querySelectorAll('[data-review-date]').forEach(button=>button.onclick=()=>{
      const date=button.dataset.reviewDate,row=Core.day(state,date);
      const trip=(state.trips||[]).find(t=>t.date===date&&!t.arrival);
      if(row.workStartConflict)editDay(date);else if(trip)editTrip(trip.id);else editDay(date);
    });
  }
  render=function(){
    baseRender();if(!appReady||!state)return;
    renderedDay=Core.today();showReview();
  };
  function setText(selector,text){const el=document.querySelector(selector);if(el&&el.textContent!==text)el.textContent=text;}
  function statValue(title,value,foot){
    const el=document.querySelector('[data-stat-title="'+title+'"]');if(!el)return;
    const strong=el.querySelector('strong');if(strong)strong.textContent=value;
    if(foot!==undefined){const small=el.querySelector('small');if(small)small.textContent=foot;}
  }
  function miniValue(label,value){
    document.querySelectorAll('#app .mini-row').forEach(el=>{const first=el.firstElementChild,strong=el.querySelector('strong');if(first&&first.textContent===label&&strong)strong.textContent=value;});
  }
  function refresh(){
    if(!state||!appReady||document.hidden||view!=='home'||!$('modal').hidden)return;
    const now=new Date(),date=Core.today(now);
    if(date!==renderedDay){render();return;}
    const week=Core.week(state,Core.weekStart(date),now),row=week.rows.find(r=>r.date===date);
    const period=Core.period(state,...Core.monthBounds(date.slice(0,7)),now);
    setText('#liveWorkClock',Core.hh(row.worked));
    const travelling=row.trips.some(t=>t.pending&&!t.future);
    const status=row.pendingTripReview?'Потребна проверка на времето':travelling?'На службен пат':row.type==='work'?(Core.parts(now).minuteOfDay<Core.minutes(row.start)?'Смената почнува во '+row.start:row.worked<row.planWork?'Работниот ден е во тек':'Работниот ден е завршен'):Core.TYPES[row.type];
    setText('#liveWorkStatus',status);
    setText('#liveTravelSummary','Почеток на работа '+(row.workStart||row.start||state.settings.start)+' · '+Core.hh(row.travel)+' на пат');
    statValue('Работено месецов',Core.hh(period.worked),period.workDays+' работни '+(period.workDays===1?'ден':'дена'));
    statValue('Прекувремено месецов',Core.hhSigned(period.overtime));
    statValue('Вкупен биланс',Core.hhSigned(Core.balance(state,now)));
    statValue('На пат / возење',Core.hh(period.travel));
    miniValue('Нето по кратки и слободни денови',Core.hhSigned(week.overtime));
    miniValue('Прекувремено – работа на празник',Core.hh(week.holidayWork));
    miniValue('Други прекувремени (пред слободни)',Core.hh(Math.max(0,week.grossOvertime-week.holidayWork)));
    miniValue('Минус за кратки денови','−'+Core.hh(week.shortfall));
    miniValue('Искористени слободни денови',String(week.compDays));
    document.querySelectorAll('#app h3').forEach(h=>{if(h.textContent==='Оваа недела'){const card=h.closest('.card'),strong=h.parentElement.querySelector('strong'),bar=card.querySelector('.progress span');if(strong)strong.textContent=Core.hh(week.normal)+' / '+Core.hh(week.target);if(bar)bar.style.width=Math.min(100,week.normal/Math.max(1,week.target)*100)+'%';}});
    document.querySelectorAll('[data-live-trip]').forEach(el=>{const trip=(state.trips||[]).find(t=>t.id===el.dataset.liveTrip);if(trip){const t=Core.tripView(trip,now);el.textContent=Core.hh(t.planned?t.plannedMinutes:t.travel);}});
  }
  function schedule(){
    clearTimeout(timer);if(document.hidden)return;
    timer=setTimeout(()=>{refresh();schedule();},60000-Date.now()%60000+30);
  }
  document.addEventListener('visibilitychange',()=>{if(!document.hidden)refresh();schedule();});
  window.WFPerformance={refresh,start:()=>{refresh();schedule();}};
})();
