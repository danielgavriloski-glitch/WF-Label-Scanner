/* Presentation controls only. No automatic AI jobs or business-data writes. */
(() => {
 'use strict';
 if(window.MarKWorkspace)return;
 if(!document.getElementById('markSharedBar')){
  document.body.classList.add('mark-module-frame');
  try{document.documentElement.dataset.workspaceTheme=JSON.parse(localStorage.getItem('markWorkspaceAppearanceV1')||'{}').theme||'gray';}catch{}
  const label=()=>document.querySelectorAll('#tdSimplePanel .td-nav').forEach(b=>{b.title=b.textContent.trim();b.setAttribute('aria-label',b.textContent.trim());});
  label();document.addEventListener('DOMContentLoaded',label,{once:true});return;
 }
 const root=document.documentElement, body=document.body, bar=document.getElementById('markSharedBar');
 const KEY='markWorkspaceAppearanceV1';
 let prefs={theme:'gray',expanded:false};try{prefs={...prefs,...JSON.parse(localStorage.getItem(KEY)||'{}')};}catch{}
 const persist=()=>{try{localStorage.setItem(KEY,JSON.stringify(prefs));}catch{}};
 function theme(value){prefs.theme=['gray','light','dark'].includes(value)?value:'gray';root.dataset.workspaceTheme=prefs.theme;persist();
  const frame=document.getElementById('markTDFrame');try{if(frame?.contentDocument)frame.contentDocument.documentElement.dataset.workspaceTheme=prefs.theme;}catch{}
 }
 theme(prefs.theme);
 body.classList.toggle('ws-rail-expanded',!!prefs.expanded);
 const nav=document.querySelector('.sidebar');
 const labelNav=()=>nav.querySelectorAll('.nav').forEach(b=>b.setAttribute('aria-label',b.textContent.trim()));labelNav();document.addEventListener('DOMContentLoaded',labelNav,{once:true});
 const button=(label,text,action)=>{const b=document.createElement('button');b.type='button';b.className='ws-tool';b.textContent=text;b.title=label;b.setAttribute('aria-label',label);b.addEventListener('click',action);return b;};
 const expand=button('Прошири / склопи мени','☰',()=>{prefs.expanded=!prefs.expanded;body.classList.toggle('ws-rail-expanded',prefs.expanded);persist();});expand.id='wsExpandMenu';nav.insertBefore(expand,nav.querySelector('.nav'));
 const settings=button('Поставки','⚙',()=>navigate('settings'));settings.style.marginTop='auto';nav.append(settings);
 const tools=document.createElement('div');tools.className='ws-tools';
 const appearance=document.createElement('select');appearance.id='wsTheme';appearance.setAttribute('aria-label','Изглед');
 for(const [value,label] of [['gray','Сива'],['light','Светла'],['dark','Темна']]){const o=document.createElement('option');o.value=value;o.textContent=label;appearance.append(o);}appearance.value=prefs.theme;appearance.onchange=()=>theme(appearance.value);tools.append(appearance);
 const toggleFocus=()=>{body.classList.toggle('ws-focus');focus.setAttribute('aria-pressed',String(body.classList.contains('ws-focus')));};
 const focus=button('Работен режим · Ctrl+Shift+F','⛶',toggleFocus);focus.id='wsFocus';focus.setAttribute('aria-pressed','false');tools.append(focus);bar.append(tools);
 const exit=button('Излези од работен режим','⛶',toggleFocus);exit.classList.add('ws-exit-focus');body.append(exit);
 document.addEventListener('keydown',e=>{if(e.ctrlKey&&e.shiftKey&&e.key.toLowerCase()==='f'){e.preventDefault();toggleFocus();}else if(e.key==='Escape'&&body.classList.contains('ws-focus'))toggleFocus();});
 const host=document.createElement('div');host.className='ws-native-host';host.id='wsNativeHost';document.querySelector('.main').append(host);
 window.markAttachWorkFrame=frame=>{
  if(frame.parentNode!==host)host.append(frame);
  frame.addEventListener('load',()=>{try{
   const d=frame.contentDocument;d.body.classList.add('mark-module-frame');d.documentElement.dataset.workspaceTheme=prefs.theme;
   if(!d.getElementById('markFrameWorkspaceStyle')){const link=d.createElement('link');link.id='markFrameWorkspaceStyle';link.rel='stylesheet';link.href='/api/mark/workspace/assets/workspace_ui.css?v=20261021-1';d.head.append(link);}
  }catch{}});
 };
 // One permanent iframe: switching module pages must not detach/reload it.
 const before=window.markBeforeNavigate;
 window.markBeforeNavigate=name=>{if(typeof before==='function')before(name);body.classList.toggle('ws-native-active',['director','video','photo','tdstudio'].includes(name));};
 const settingsPage=document.getElementById('page-settings');
 const bookingPage=document.getElementById('page-booking');
 if(bookingPage&&settingsPage&&window.MarKWorkspaceCapabilities?.booking){
  const previous=document.createElement('details');previous.className='card';const summary=document.createElement('summary');summary.textContent='Поврзување со телефон и продукциски проекти';previous.append(summary);
  while(bookingPage.firstChild)previous.append(bookingPage.firstChild);settingsPage.append(previous);
  const frame=document.createElement('iframe');frame.id='wsBookingFrame';frame.src='/api/mark/booking/app';frame.title='TD Booking';frame.loading='lazy';bookingPage.append(frame);
 }
 if(settingsPage){const card=document.createElement('section');card.className='card';const title=document.createElement('h3');title.textContent='Приказ при AI работа';card.append(title);
  const label=document.createElement('label');label.className='label';label.textContent='Графички приказ на интерфејсот';
  const renderer=document.createElement('select');renderer.className='field';renderer.setAttribute('aria-label',label.textContent);
  for(const [value,text] of [['stable','Стабилен приказ'],['automatic','Автоматски / GPU']]){const o=document.createElement('option');o.value=value;o.textContent=text;renderer.append(o);}label.append(renderer);card.append(label);
  const status=document.createElement('p');status.className='ws-help-status';status.setAttribute('aria-live','polite');status.textContent='Промената се применува при следно стартување. AI забрзувањето е одделно.';card.append(status);settingsPage.append(card);
  fetch('/api/mark/workspace/renderer',{credentials:'same-origin'}).then(r=>r.json()).then(s=>{renderer.value=s.mode||'stable';}).catch(()=>{});
  renderer.onchange=async()=>{try{const r=await fetch('/api/mark/workspace/renderer',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json','X-TD-Client':'TD-v1.4'},body:JSON.stringify({mode:renderer.value})});if(!r.ok)throw Error('Поставката не е зачувана');status.textContent='Зачувано. Затвори го MarK и отвори го повторно.';}catch(e){status.textContent=e.message;}};
 }
 window.MarKWorkspace={version:'2026.10.21-test1',theme,toggleFocus};
})();
