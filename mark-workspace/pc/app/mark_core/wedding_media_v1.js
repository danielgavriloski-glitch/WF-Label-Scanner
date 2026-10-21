/* MarK Wedding Project v2.7 — editable legend, folder views, no selection scroll jumps. 
 * Operates only on existing signed-in local MarK host and /api/mark/wedding/v1.
 * Non-destructive unless user reviews plan and confirms COPY/MOVE.
 */
(()=>{'use strict';
const VERSION="2.7";const BASE='/api/mark/wedding/v1',el=id=>document.getElementById(id);
if(!document.querySelector('nav.sidebar')||!el('page-booking'))return;
const COLORS={'Зет':'сина','Невеста':'виолетова','Црква':'зелена','Ресторан':'жолта','Дрон':'црвена','Друго':'без боја','За проверка':'жолта'};
const SCENES=['Зет','Невеста','Црква','Ресторан','Дрон','Друго','За проверка'];
const QUALITY=['Убави','Заматени','Дупликати','За проверка','За обработка','Албум'];
const CAMERA=['Cam 1','Cam 2','Cam 3','Cam 4','Drone','Crane','Other'];
const fileCollator=new Intl.Collator('mk',{numeric:true,sensitivity:'base'});
const compareName=(a,b)=>fileCollator.compare(String(a||''),String(b||''));
const esc=t=>String(t??'').replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]));
const state={project:null,items:[],selected:{video:new Set(),photo:new Set()},anchor:{video:null,photo:null},view:{video:[],photo:[]},page:{video:0,photo:0},job:null,plan:[],mode:'COPY',tab:'video',zoom:{video:5,photo:5},focused:{video:null,photo:null},folderFilter:{video:null,photo:null},hideConfirmed:{video:false,photo:false}};
const styles=document.createElement('style');styles.textContent=`
/* v2.2: all MarK panels use available monitor width, not a fixed 1600px canvas */
.main>.page{width:100%!important;max-width:none!important;margin:0!important;padding:clamp(10px,1.35vw,26px)!important;min-width:0}
.main{width:100%;min-width:0}
nav.sidebar .nav[hidden]{display:none!important}
#page-media-step1{min-width:0}
#page-media-step1 .mw-panel{background:#172d35;border:1px solid #52666c;border-radius:12px;padding:clamp(10px,1.1vw,16px);margin:9px 0}
#page-media-step1 .mw-panel summary{cursor:pointer;color:#e6dbc9;font-weight:650;padding:4px 2px;list-style-position:inside}
#page-media-step1 .mw-panel[open] summary{margin-bottom:10px}
#page-media-step1 .mw-form{display:flex;gap:9px;flex-wrap:wrap;align-items:end}
#page-media-step1 .mw-form label{display:grid;gap:5px;min-width:118px;flex:1;color:#d6e6e8;font-size:12px}
#page-media-step1 .mw-form label.wide{flex:3;min-width:190px}
#page-media-step1 .mw-field{padding:9px;color:#ecf4f5;background:#1f3b44;border:1px solid #708185;border-radius:8px;min-width:0}
#page-media-step1 input.mw-field,#page-media-step1 select.mw-field{width:100%}
#page-media-step1 .mw-controls{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin:8px 0}
#page-media-step1 .mw-controls input[type=search]{flex:1 1 230px;min-width:120px;max-width:640px}
#page-media-step1 .mw-muted{font-size:11px;opacity:.82}
#page-media-step1 .mw-selected-counter{color:#ecd5b4;font-size:12px}
#page-media-step1 .mw-tabs{display:flex;gap:8px;border-bottom:1px solid #52666c;margin:12px 0 0;padding-bottom:8px}
#page-media-step1 .mw-tabs button{cursor:pointer;padding:11px clamp(10px,2vw,30px);border:1px solid #496777;background:#193442;color:#c7dae0;border-radius:10px;font-size:15px;font-weight:700;flex:0 1 260px}
#page-media-step1 .mw-tabs button[aria-selected=true]{background:#2b5b69;border-color:#6dbbba;color:#fff;box-shadow:inset 0 -3px 0 #6bcabf}
#page-media-step1 .mw-workspace{width:100%;min-width:0;border:1px solid #466473;background:#132936;border-radius:0 0 12px 12px;padding:clamp(9px,1vw,14px);margin:0 0 10px}
#page-media-step1 .mw-workspace[hidden]{display:none!important}
#page-media-step1 .mw-workspace h3{margin:0 0 6px;color:#f1dab0}
#page-media-step1 .mw-board{display:grid;grid-template-columns:minmax(0,1fr) clamp(128px,11vw,175px);gap:12px;min-width:0;align-items:start}
#page-media-step1 .mw-grid{--mw-cols:5;display:grid;grid-template-columns:repeat(var(--mw-cols),minmax(0,1fr));gap:clamp(5px,.6vw,11px);min-width:0;max-height:clamp(380px,calc(100dvh - 265px),1350px);min-height:min(48vh,450px);overflow:auto;align-content:start;padding:5px;overscroll-behavior:contain;scrollbar-color:#477483 transparent}
#page-media-step1 .mw-tile{border:2px solid #3d5863;border-radius:9px;background:#1a3441;color:#dbe9ef;cursor:pointer;text-align:left;overflow:hidden;min-width:0;padding:0;position:relative}
#page-media-step1 .mw-tile.selected{border-color:#f6d394;box-shadow:0 0 0 2px #f6d39455}
#page-media-step1 .mw-tile:focus-visible{outline:3px solid white;outline-offset:1px}
#page-media-step1 .mw-poster{width:100%;aspect-ratio:16/10;background:#071d2a;display:flex;align-items:center;justify-content:center;overflow:hidden;font-size:27px;position:relative}
#page-media-step1 .mw-poster img{width:100%;height:100%;object-fit:cover}
#page-media-step1 .mw-video-icon{position:absolute;bottom:5px;left:6px;background:#061824d9;color:#f8ffff;border-radius:5px;padding:3px 5px;font-size:11px;pointer-events:none}
#page-media-step1 .mw-duration{position:absolute;bottom:4px;right:5px;background:#061824e9;color:#fff;border-radius:5px;padding:3px 5px;font-size:11px;font-weight:700;pointer-events:none}
#page-media-step1 .mw-check{position:absolute;top:5px;left:5px;background:#0c2b36db;color:#fff;border:2px solid #d5ebec;border-radius:5px;width:24px;height:24px;display:grid;place-items:center;font-size:16px;font-weight:800;pointer-events:none;z-index:1}
#page-media-step1 .mw-tile.selected .mw-check{background:#f2d28c;color:#132b35;border-color:#f2d28c}
#page-media-step1 .mw-camera{position:absolute;top:5px;right:5px;font-size:10px;background:#071d2acc;border-radius:4px;padding:2px 4px;pointer-events:none}
#page-media-step1 .mw-stripe{height:5px;background:#667d87}
#page-media-step1 .mw-stripe.зелена,#page-media-step1 .mw-dot.зелена{background:#62ca91}
#page-media-step1 .mw-stripe.жолта,#page-media-step1 .mw-dot.жолта{background:#e6c25f}
#page-media-step1 .mw-stripe.црвена,#page-media-step1 .mw-dot.црвена{background:#e8777d}
#page-media-step1 .mw-stripe.сина,#page-media-step1 .mw-dot.сина{background:#7bacf0}
#page-media-step1 .mw-stripe.виолетова,#page-media-step1 .mw-dot.виолетова{background:#ba91ee}
#page-media-step1 .mw-meta{padding:5px 7px;font-size:11px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
#page-media-step1 .mw-meta.mw-muted{padding-top:0}
#page-media-step1 .mw-legend{position:sticky;top:76px;display:flex;flex-direction:column;gap:5px;min-width:0;max-height:calc(100dvh - 95px);overflow-y:auto;scrollbar-color:#477483 transparent}
#page-media-step1 .mw-legend button{font-size:12px;text-align:left;border:1px solid #67818c;border-radius:7px;background:#23414c;color:#f3f5f4;cursor:pointer;padding:10px 6px}
#page-media-step1 .mw-legend button:hover{background:#345666}
#page-media-step1 .mw-legend .mw-legend-btn{display:flex;align-items:center;min-width:0;gap:4px}
#page-media-step1 .mw-legend .mw-legend-btn span:last-child{overflow-wrap:anywhere}
#page-media-step1 .mw-legend .mw-check-label{display:flex;gap:8px;align-items:flex-start;color:#e3ebeb;font-size:12px;line-height:1.35;cursor:pointer;padding:7px 2px}
#page-media-step1 .mw-legend input[type=checkbox]{width:16px;height:16px;accent-color:#6fb9b3;flex-shrink:0}
#page-media-step1 .mw-legend details{border-top:1px solid #496470;padding:7px 0;margin-top:5px}
#page-media-step1 .mw-legend details summary{font-size:12px;cursor:pointer;color:#d6ebef}
#page-media-step1 .mw-edit-row{display:grid;gap:3px;border-bottom:1px solid #334f59;padding:5px 0}
#page-media-step1 .mw-legend input[type=text],#page-media-step1 .mw-legend select{width:100%;min-width:0;background:#142d39;color:#eaf4f4;border:1px solid #557782;border-radius:6px;font-size:12px;padding:6px}
#page-media-step1 .mw-edit-actions{display:flex;gap:4px}
#page-media-step1 .mw-edit-actions button{flex:1;font-size:11px;padding:6px 3px;min-width:0}
#page-media-step1 .mw-folder-menu{position:relative;max-width:330px;min-width:175px}
#page-media-step1 .mw-folder-menu>summary{background:#1f3b44;border:1px solid #708185;color:#e8eff0;padding:9px;border-radius:8px;cursor:pointer;list-style:revert;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#page-media-step1 .mw-folder-items{position:absolute;top:100%;left:0;z-index:10;min-width:min(365px,88vw);max-height:300px;overflow-y:auto;background:#1c3642;border:1px solid #708185;border-radius:8px;box-shadow:0 12px 30px #071721bd;padding:8px}
#page-media-step1 .mw-folder-items label{display:flex;gap:8px;align-items:start;padding:5px 2px;font-size:12px;color:#eff4f4;overflow-wrap:anywhere}
#page-media-step1 .mw-folder-items input{flex:none;width:17px;height:17px;accent-color:#6fb9b3}
#page-media-step1 .mw-folder-items button{font-size:12px;padding:5px 8px}
#page-media-step1 .mw-legend .mw-legend-btn[disabled]{opacity:.5}

#page-media-step1 .mw-dot{display:inline-block;width:10px;height:10px;border-radius:50%;background:#aaa;vertical-align:middle;margin-right:5px}
#page-media-step1 .mw-note{background:#24424c;border-radius:8px;padding:9px;margin-top:8px;color:#e0e9e8;font-size:12px}
#page-media-step1 #mwPlan{white-space:pre-wrap;font-size:11px;overflow-wrap:anywhere;max-height:170px;overflow:auto}
#page-media-step1 .mw-msg{min-height:24px;margin-top:8px;color:#f0d69c;white-space:pre-wrap}
#page-media-step1 .mw-scan-status{display:block;background:#183440;border:1px solid #789a9f;border-radius:9px;padding:10px;margin-top:9px;color:#ffdda0;min-height:40px;font-size:13px;overflow-wrap:anywhere;white-space:pre-wrap}
#page-media-step1 .mw-scan-status[data-error="true"]{color:#ffd1d1;border-color:#cd7d7d;background:#493038}
#page-media-step1 .mw-scan-status[data-busy="true"]{border-color:#81cbc6;background:#194a4a;color:#e8ffff}
#page-media-step1 .mw-scan-results{font-size:13px;color:#caeadd;white-space:pre-wrap;padding:7px 0}
#page-media-step1 .mw-zoom{width:clamp(95px,10vw,175px)!important;min-width:95px;flex:none!important;accent-color:#8ac9c3}
#page-media-step1 .mw-zoom-wrap{display:flex;align-items:center;gap:8px;border:1px solid #415f69;border-radius:9px;padding:5px 9px}
#page-media-step1 .mw-zoom-label{font-size:12px;color:#ecd6a5;min-width:60px;white-space:nowrap}
#page-media-step1 .mw-tools{display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;margin-bottom:8px}
#page-media-step1 .mw-lightbox{position:fixed;inset:0;background:#020b12e8;z-index:9999;display:flex;align-items:center;justify-content:center;padding:clamp(8px,2vw,36px)}
#page-media-step1 .mw-lightbox[hidden]{display:none!important}
#page-media-step1 .mw-lightbox-panel{background:#122936;border:1px solid #557482;border-radius:13px;width:min(1400px,96vw);max-height:96dvh;padding:12px;display:flex;flex-direction:column;gap:8px;overflow:hidden}
#page-media-step1 .mw-lightbox-head{display:flex;justify-content:space-between;align-items:center;gap:12px;min-width:0}
#page-media-step1 .mw-lightbox-media{flex:1;min-height:0;display:grid;place-items:center;background:#071824;border-radius:8px;overflow:auto}
#page-media-step1 .mw-lightbox-media img,#page-media-step1 .mw-lightbox-media video{display:block;max-width:100%;max-height:min(78dvh,1000px);object-fit:contain}
#page-media-step1 .mw-lightbox-caption{color:#cedbdc;font-size:12px;overflow-wrap:anywhere}
@media(max-width:720px){#page-media-step1 .mw-board{grid-template-columns:1fr}#page-media-step1 .mw-legend{position:static;max-height:none;overflow:visible;display:grid;grid-template-columns:repeat(auto-fit,minmax(100px,1fr));grid-row:1}#page-media-step1 .mw-legend strong{grid-column:1/-1}#page-media-step1 .mw-grid{max-height:65dvh}#page-media-step1 .mw-tabs button{flex:1}}

/* v2.4 — compact top navigation, readable controls, maximize media workspace.
   Changes in header are scoped exclusively to the wedding project page. */
body:has(#page-media-step1.active) nav.sidebar{min-height:52px;padding:5px 12px;gap:4px}
body:has(#page-media-step1.active) nav.sidebar .mark-small{width:38px;height:39px;margin-right:8px}
body:has(#page-media-step1.active) nav.sidebar .nav{min-height:35px;padding:6px 9px;font-size:11.5px}
body:has(#page-media-step1.active) .mast{min-height:64px;padding:7px clamp(10px,1vw,22px);gap:12px}
body:has(#page-media-step1.active) .mast .mark-hero{width:52px;height:49px}
body:has(#page-media-step1.active) .mast .brand-title{gap:7px;flex-shrink:0}
body:has(#page-media-step1.active) .mast .brand-title h1{font-size:19px;margin:0}
body:has(#page-media-step1.active) .mast .brand-title .eyebrow{font-size:9px;letter-spacing:.8px}
body:has(#page-media-step1.active) .mast .brand-title .subline{display:none}
body:has(#page-media-step1.active) .mast .header-right{gap:5px;flex-shrink:0}
body:has(#page-media-step1.active) .mast .status-chip{padding:6px 9px;border-radius:7px;font-size:11px}
body:has(#page-media-step1.active) .mast .header-right .btn{padding:6px 9px;min-height:32px}
body:has(#page-media-step1.active) .mark-shared{top:51px;min-height:44px;padding:5px 12px;gap:6px;box-shadow:none}
body:has(#page-media-step1.active) .mark-shared .btn{min-height:33px;padding:6px 9px;font-size:12px}
body:has(#page-media-step1.active) .mark-shared .mark-shared-left{gap:5px}
#mwProjectMemory{flex:1 1 240px;min-width:160px;max-width:none;display:flex;flex-direction:column;justify-content:center;background:#193541;border:1px solid #46616b;border-radius:8px;padding:5px 12px;overflow:hidden}
#mwProjectMemory strong{font-size:10px;color:#ebd8aa;font-weight:700;letter-spacing:.03em}
#mwProjectMemory span{font-size:12px;color:#d7e9e8;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
body:not(:has(#page-media-step1.active)) #mwProjectMemory{display:none}
#page-media-step1{padding:clamp(7px,.65vw,14px)!important}
#page-media-step1>.sectiontitle{font-size:17px;margin:0 0 4px}
#page-media-step1>.sectionintro{font-size:12px;margin:0 0 7px}
#page-media-step1 .mw-panel{padding:clamp(7px,.65vw,12px);margin:5px 0;border-radius:9px}
#page-media-step1 .mw-panel summary{font-size:12px;padding:2px}
#page-media-step1 .mw-panel[open] summary{margin-bottom:6px}
#page-media-step1 .mw-form{gap:6px}
#page-media-step1 .mw-controls{gap:6px;margin:4px 0}
#page-media-step1 .mw-field{padding:6px 8px;min-height:34px;font-size:12px}
#page-media-step1 .btn{padding:6px 10px;min-height:34px;font-size:12px;line-height:1.25;border-radius:7px}
#page-media-step1 .mw-tabs{gap:6px;margin:6px 0 0;padding-bottom:5px}
#page-media-step1 .mw-tabs button{padding:7px 14px;font-size:13px;flex:0 1 175px;border-radius:8px}
#page-media-step1 .mw-workspace{padding:6px 8px;margin-bottom:6px}
#page-media-step1 .mw-tools{margin-bottom:5px}
#page-media-step1 .mw-board{grid-template-columns:minmax(0,1fr) clamp(108px,10vw,152px);gap:8px}
#page-media-step1 .mw-grid{gap:6px;max-height:clamp(350px,calc(100dvh - 315px),1800px);min-height:min(53vh,590px);padding:4px}
#page-media-step1 .mw-legend{top:58px;gap:4px}
#page-media-step1 .mw-legend button{font-size:11.5px;padding:7px 5px;min-height:32px}
#page-media-step1 .mw-meta{font-size:11px;padding:4px 6px}
#page-media-step1 .mw-scan-status{min-height:30px;padding:6px;font-size:12px;margin-top:5px}
#page-media-step1 .mw-note{padding:6px;font-size:11px;margin-top:5px}
#page-media-step1 .mw-fallback-note{color:#eec78a;font-size:12px;padding:5px 7px;min-height:20px}
#page-media-step1 .mw-preview-wait{font-size:11px;color:#b3cbd5;padding:8px;text-align:center}
@media(max-width:1100px){body:has(#page-media-step1.active) .mast .status-chip{display:none}#mwProjectMemory{min-width:90px;padding:4px 6px}}
@media(max-width:720px){body:has(#page-media-step1.active) .mast{flex-wrap:wrap}#mwProjectMemory{order:3;flex:1 1 100%}#page-media-step1 .mw-board{grid-template-columns:1fr}#page-media-step1 .mw-grid{max-height:65dvh}#page-media-step1 .mw-legend{position:static;max-height:none;overflow:visible;display:grid;grid-template-columns:repeat(auto-fit,minmax(94px,1fr));grid-row:1}}
`;document.head.append(styles);
const nav=document.querySelector('nav.sidebar');for(const old of ['video','photo']){const btn=nav.querySelector('[data-nav="'+old+'"]');if(btn)btn.hidden=true;}
let link=nav.querySelector('[data-nav="media-step1"]');if(!link){link=document.createElement('button');link.className='nav';link.dataset.nav='media-step1';link.innerHTML='<span class="ico">▦</span> Креирај проект · Свадба';(nav.querySelector('[data-nav="director"]')||nav.lastElementChild).insertAdjacentElement('afterend',link);}
const mast=document.querySelector('.mast');
const memoryBar=document.createElement('div');memoryBar.id='mwProjectMemory';memoryBar.setAttribute('aria-live','polite');
memoryBar.innerHTML='<strong>ПРОЕКТНА МЕМОРИЈА</strong><span id="mwMemoryInfo">Избери свадба за да ги видиш материјалите</span>';
if(mast)mast.insertBefore(memoryBar,mast.querySelector('.header-right'));
const page=document.createElement('section');page.className='page';page.id='page-media-step1';
const pane=(kind,title)=>`<div class="mw-workspace" role="tabpanel" id="mw-pane-${kind}" ${kind==='photo'?'hidden':''}>
<div class="mw-tools"><div class="mw-controls" style="flex:1;min-width:0"><input type="search" class="mw-field" id="mw-search-${kind}" placeholder="Барај по име, папка, камера"><select class="mw-field" id="mw-sort-${kind}" aria-label="Подреди" style="width:auto;flex:0 1 175px"><option value="name">По име · 1, 2, 10</option><option value="folder">По папка, па име</option></select><details class="mw-folder-menu" id="mw-folders-${kind}"><summary id="mw-folder-summary-${kind}">📁 Сите папки</summary><div class="mw-folder-items" id="mw-folder-items-${kind}"></div></details><span class="mw-selected-counter" id="mw-count-${kind}"></span></div>
<div class="mw-zoom-wrap"><button class="btn" type="button" id="mw-minus-${kind}" aria-label="Помал преглед">−</button><label class="mw-zoom-label" for="mw-zoom-${kind}" id="mw-zoom-text-${kind}">5 во ред</label><input class="mw-zoom" type="range" min="1" max="15" step="1" value="5" id="mw-zoom-${kind}" aria-label="Број на слики во еден ред"><button class="btn" type="button" id="mw-plus-${kind}" aria-label="Повеќе колони">＋</button></div>
<button class="btn" type="button" id="mw-openpreview-${kind}">🔎 Голем преглед</button></div>
<div class="mw-board"><div class="mw-grid" id="mw-grid-${kind}" aria-label="Галерија ${title}"></div><aside class="mw-legend" id="mw-legend-${kind}"><strong style="font-size:12px">Сцена · додели боја</strong><label class="mw-check-label"><input id="mw-hide-${kind}" type="checkbox">Скриј ги потврдените · прикажи само неселектирани</label><div id="mw-legend-list-${kind}"></div><details id="mw-edit-legend-${kind}"><summary>✎ Уреди легенда · + Додај</summary><div id="mw-legend-editor-${kind}"></div></details></aside></div>
<div class="mw-controls"><button class="btn" id="mw-prev-${kind}">←</button><span class="mw-muted" id="mw-page-${kind}"></span><button class="btn" id="mw-next-${kind}">→</button><button class="btn" id="mw-selectall-${kind}">Селектирај видливи</button><button class="btn" id="mw-clear-${kind}">Исчисти избор</button><span class="mw-muted">Клик = селекција · Shift = од–до · Ctrl = повеќе · двоен клик = преглед</span></div>
${kind==='photo'?`<div class="mw-controls"><label class="mw-muted">Фото квалитет:</label><select id="mw-quality" class="mw-field" style="width:auto">${QUALITY.map(x=>`<option>${x}</option>`).join('')}</select><button class="btn" id="mw-set-quality">Потврди квалитет</button></div>`:''}
</div>`;
page.innerHTML=`<h2 class="sectiontitle">Креирај проект · Свадба</h2>
<p class="sectionintro" style="max-width:none">Сите видеа и фотографии како мали слики · автоматско длабоко скенирање · прво преглед, потоа твоја селекција со бои · COPY/MOVE само со потврда.</p>
<div class="mw-panel" id="mwWorkflow"><div class="mw-controls" style="flex-wrap:wrap"><strong>MarK Workflow:</strong><button type="button" class="btn mw-jump" data-step="1">1 · Проект</button><button type="button" class="btn mw-jump" data-step="2">2 · Сортирање</button><button type="button" class="btn mw-jump" data-step="3">3 · Анализа</button><button type="button" class="btn mw-jump" data-step="4">4 · Кратки</button><button type="button" class="btn mw-jump" data-step="5">5 · Долги</button><button type="button" class="btn mw-jump" data-step="6">6 · Love Story</button><span class="mw-muted">Може да прескокнеш чекор; анализата не е задолжителна за рачна селекција.</span></div><div class="mw-muted" id="mwWorkflowHint">Избери чекор. Оригиналите не се менуваат.</div></div>
<div class="mw-panel"><div class="mw-controls"><strong>Активна свадба</strong><select class="mw-field" id="mwProject" style="flex:1;max-width:520px"><option value="">Избери проект</option></select><button class="btn" id="mwReload">Освежи</button><span class="mw-muted" id="mwCapabilities"></span></div></div>
<details class="mw-panel" id="mwCreateDetails"><summary>＋ Нова свадба / Креирај проект</summary><div class="mw-form"><label class="wide">Име на свадба<input id="mwName" class="mw-field" placeholder="Кирил и Марија"></label><label>Датум<input id="mwDate" type="date" class="mw-field"></label><label class="wide">Каде да се создаде проектот?<input id="mwOutputNew" class="mw-field" placeholder="H:\\TD_Projects"></label><button class="btn" id="mwOutputNewBrowse">📁 Избери</button><button class="btn primary" id="mwCreate">＋ Креирај</button></div></details>
<div id="mwWorkspace" hidden><details class="mw-panel" id="mwImportDetails" open><summary>📁 Извори · додади папка и скенирај ги сите потпапки</summary><div class="mw-form"><label class="wide">Папка со видеа и фотографии<input class="mw-field" id="mwSource" placeholder="H:\\Wedding\\Cam 1"></label><button class="btn" id="mwSourceBrowse">📁 Избери папка</button><label>Материјали<select class="mw-field" id="mwSourceKind"><option value="all">ВИДЕО + ФОТО</option><option value="video">Само видео</option><option value="photo">Само фотографии</option></select></label><label>Камера<select class="mw-field" id="mwCamera">${CAMERA.map(x=>`<option>${x}</option>`).join('')}</select></label><button class="btn primary" type="button" id="mwAddFolder">＋ Додај и сортирај</button></div><div class="mw-scan-status" role="status" id="mwScanStatus">Избери изворна папка и притисни „Додај и сортирај“. Скенот ќе ги пребара сите потпапки.</div><div class="mw-scan-results" id="mwScanResults"></div><div class="mw-muted" id="mwFolders" style="margin-top:9px;white-space:pre-wrap"></div><div class="mw-note">MarK ги пребарува сите вложени папки, подредува природно по име, JPG + RAW ги здружува во една ставка. По скенирањето ништо не се преместува. Cam 1 е стандардна камера.</div></details>
<div class="mw-tabs" role="tablist" aria-label="Материјали"><button type="button" role="tab" id="mw-tab-video" aria-controls="mw-pane-video" aria-selected="true">🎬 ВИДЕО <span id="mw-tabcount-video"></span></button><button type="button" role="tab" id="mw-tab-photo" aria-controls="mw-pane-photo" aria-selected="false">📷 ФОТО <span id="mw-tabcount-photo"></span></button></div>
<div id="mwFallbackNote" class="mw-fallback-note" role="status" hidden></div><div class="mw-stage">${pane('video','ВИДЕО')}${pane('photo','ФОТОГРАФИИ')}</div>
<details class="mw-panel"><summary>🔎 Анализа · повтори предлози</summary><div class="mw-controls"><button class="btn" id="mwAnalyze">Повтори анализа на внесените</button><span class="mw-muted">МарК предлага; ти потврдуваш. Видео-сцените се предлагаат според имињата, не преку AI препознавање кадри.</span></div></details>
<details class="mw-panel" id="mwSortDetails"><summary>📦 Организирај проектни папки · COPY / MOVE</summary><div class="mw-form"><label class="wide">Папка на проектот<input id="mwOutput" class="mw-field"></label><button class="btn" id="mwOutputBrowse">📁 Избери</button><button class="btn" id="mwSaveOutput">Зачувај патека</button><label>Начин<select id="mwMode" class="mw-field"><option value="COPY">COPY · копирај</option><option value="MOVE">MOVE · премести</option></select></label><button class="btn primary" id="mwPreviewSort">Прегледај план</button></div><div class="mw-note">Само потврдени материјали. VIDEO/Зет/Cam 1 и PHOTO/Невеста/Cam 1/Убави итн. Има дневник за веќе сортираните датотеки. Ако се избрани папки преку филтерот, само тие ќе влезат во планот за сортирање.</div><div class="mw-note" id="mwPlan" hidden></div><button class="btn primary" id="mwDoSort" hidden>Потврди</button></details>
<details class="mw-panel" id="mwLongDetails"><summary>🎞 ДОЛГИ — проверка и Premiere XML</summary><div class="mw-note">Секое видео задолжително е целосно, во природен редослед според името (1, 2, 10) во соодветното поглавје. Не се избираат само „најдобрите“. За извоз неопходно е траење за сите клипови, без тивко изоставување.</div><div class="mw-controls"><button type="button" class="btn" id="mwDirectorPreview">Провери редослед</button><button type="button" class="btn primary" id="mwDirectorXml">Извези Premiere XML</button></div><pre id="mwDirectorSummary" class="mw-note" style="white-space:pre-wrap;max-height:230px;overflow:auto"></pre></details>
<details class="mw-panel" id="mwShortDetails"><summary>🎼 КРАТКИ СПОТОВИ — режисерски насоки</summary><div class="mw-note">Планирано: музика прво, чисти намерни движења, без местeње на камерманот, избор на најдобар повторен обид, сечење по ритам. Извор над 5 минути не смее во краток спот. ДЕТАЛНА AI монтажа не е активна во оваа верзија.</div></details>
<details class="mw-panel" id="mwLoveDetails"><summary>💞 Love Story — креативна приказна</summary><div class="mw-note">Планирано: MarK осмислува сценарио, емоционален тек, музика, кулминација и цела editable Premiere монтажа. Овој чекор во v2.7 е водич, не готов AI режисер.</div></details></div>
<div class="mw-msg" id="mwMessage" role="status"></div>
<div id="mwLightbox" class="mw-lightbox" role="dialog" aria-modal="true" aria-label="Голем преглед" hidden><div class="mw-lightbox-panel"><div class="mw-lightbox-head"><b id="mwLightboxTitle"></b><button class="btn" id="mwClosePreview" type="button">✕ Затвори (Esc)</button></div><div class="mw-lightbox-media" id="mwLightboxMedia"></div><div class="mw-lightbox-caption" id="mwLightboxInfo"></div></div></div>`;
el('page-booking').insertAdjacentElement('beforebegin',page);
link.addEventListener('click',()=>{if(typeof navigate==='function')navigate('media-step1');else{document.querySelectorAll('.page').forEach(p=>p.classList.toggle('active',p===page))}window.scrollTo(0,0);});
const unified=el('markUnifiedWedding');if(unified){unified.addEventListener('click',event=>{event.preventDefault();event.stopImmediatePropagation();link.click()},true);unified.textContent='＋ Креирај проект · Свадба';}
function msg(t,isError=false){el('mwMessage').textContent=t;el('mwMessage').style.color=isError?'#f4a4a4':'#f4db9d';}
function scanMsg(t,isError=false,busy=false){const status=el('mwScanStatus');if(!status)return;status.textContent=t;status.dataset.error=String(isError);status.dataset.busy=String(busy);msg(t,isError);}
function countStatus(){const v=state.items.filter(x=>x.kind==='video').length,p=state.items.filter(x=>x.kind==='photo').length;el('mwScanResults').textContent='Во проектот: '+v+' видеа · '+p+' фото ставки · '+(state.project?.folders?.length||0)+' изворни папки. '+(!v&&!p?'Нема внесен материјал. Додај изворна папка погоре.':'Отвори ги табовите VIDEO и PHOTO за да ги видиш.');updateMemory();}
function updateMemory(){const mem=el('mwMemoryInfo');if(!mem)return;
const v=state.items.filter(x=>x.kind==='video').length,p=state.items.filter(x=>x.kind==='photo').length;
const approved=state.items.filter(x=>x.confirmed).length;
mem.textContent=state.project?state.project.name+'  ·  '+v+' видеа  ·  '+p+' фотографии  ·  '+approved+' потврдени  ·  '+(state.project.folders?.length||0)+' папки':'Избери свадба за да ги видиш материјалите';}
function showImport(){el('mwImportDetails').open=true;el('mwImportDetails').scrollIntoView({behavior:'smooth',block:'center'});}

async function api(path,data){const res=await fetch(BASE+path,{method:data===undefined?'GET':'POST',credentials:'same-origin',cache:'no-store',headers:data===undefined?{}:{'Content-Type':'application/json','X-TD-Client':'TD-v1.4'},body:data===undefined?undefined:JSON.stringify(data)});const out=await res.json().catch(()=>({error:'Невалиден одговор од локалниот сервер'}));if(!res.ok)throw Error(out.error||('HTTP '+res.status));return out;}
async function browse(field){const btn=el(field+'Browse');btn.disabled=true;try{
 // Native Windows Folder dialog from authenticated local MarK server. No HTML file-picker fake paths.
 const result=await api('/pick-folder',{});if(result.path){el(field).value=result.path;msg('Избрана папка: '+result.path);}
}catch(e){
 if(typeof window.markWeddingPickFolder==='function'){window.markWeddingPickFolder(path=>{if(path)el(field).value=path;});}
 else msg('Не успеа отворањето на Windows папката: '+e.message+' (можеш и рачно да внесеш патека)',true);
}finally{btn.disabled=false;}}
[['mwOutputNew','mwOutputNew'],['mwSource','mwSource'],['mwOutput','mwOutput']].forEach(([field])=>el(field+'Browse').onclick=()=>browse(field));
function resetSelect(){for(const kind of ['video','photo']){state.selected[kind].clear();state.anchor[kind]=null;state.page[kind]=0;state.folderFilter[kind]=null;}}
async function refreshProjects(preferred){const r=await api('/projects'),old=preferred||state.project?.id||'',sel=el('mwProject');sel.replaceChildren();const ph=document.createElement('option');ph.textContent='Избери свадба';ph.value='';sel.append(ph);for(const pr of r.projects){const opt=document.createElement('option');opt.value=pr.id;opt.textContent=pr.name+(pr.date?' · '+pr.date:'');sel.append(opt);}if(r.projects.some(x=>x.id===old)){sel.value=old;await openProject(old);}else{state.project=null;state.items=[];updateMemory();el('mwWorkspace').hidden=true;el('mwCreateDetails').open=true;msg('Прво креирај или избери свадбен проект, потоа додај изворна папка.');}}
async function openProject(id){if(!id){state.project=null;state.items=[];updateMemory();el('mwWorkspace').hidden=true;return;}const r=await api('/project/'+encodeURIComponent(id));state.project=r.project;state.items=r.project.items;updateMemory();resetSelect();el('mwWorkspace').hidden=false;el('mwCreateDetails').open=false;el('mwOutput').value=r.project.output||'';el('mwFolders').textContent=r.project.folders.map(x=>`${x.kind.toUpperCase()} · ${x.camera} · ${x.count} ставки${x.mediaFolders!==undefined?' · '+x.mediaFolders+' папки со материјали':''} · ${x.path}`).join('\n')||'Уште нема материјали';refreshFolders('video');refreshFolders('photo');renderLegends();renderAll();countStatus();el('mwImportDetails').open=!state.items.length;switchTab(state.tab);}
function folderKey(x){return JSON.stringify([x.sourceFolder||'',x.relativeFolder||'.']);}
function refreshFolders(kind){
 const entries=new Map();for(const it of state.items.filter(x=>x.kind===kind)){
  const key=folderKey(it);if(!entries.has(key))entries.set(key,{key,source:it.sourceFolder||'',rel:it.relativeFolder||'.',count:0});entries.get(key).count++;
 }
 const menu=el('mw-folder-items-'+kind);menu.replaceChildren();
 const all=document.createElement('button');all.type='button';all.className='btn';all.textContent='✓ Сите папки';all.onclick=()=>{state.folderFilter[kind]=null;state.page[kind]=0;state.anchor[kind]=null;refreshFolders(kind);render(kind,false);};menu.append(all);
 for(const x of [...entries.values()].sort((a,b)=>compareName(a.source,b.source)||compareName(a.rel,b.rel))){
  const lab=document.createElement('label'),check=document.createElement('input');check.type='checkbox';check.checked=!!state.folderFilter[kind]?.has(x.key);check.onchange=()=>{
   if(!state.folderFilter[kind])state.folderFilter[kind]=new Set();
   if(check.checked)state.folderFilter[kind].add(x.key);else state.folderFilter[kind].delete(x.key);
   state.page[kind]=0;state.anchor[kind]=null;updateFolderSummary(kind);render(kind,false);
  };const name=document.createElement('span');name.textContent=(x.source.split(/[\\/]/).filter(Boolean).pop()||x.source)+' / '+x.rel+' ('+x.count+')';name.title=x.source+' / '+x.rel;lab.append(check,name);menu.append(lab);
 }updateFolderSummary(kind);
}
function updateFolderSummary(kind){const filter=state.folderFilter[kind];el('mw-folder-summary-'+kind).textContent=filter===null?'📁 Сите папки':'📁 Избрани папки: '+filter.size;}
function listed(kind){const needle=el('mw-search-'+kind).value.trim().toLocaleLowerCase();
const sortBy=el('mw-sort-'+kind).value;
const items=state.items.filter(x=>x.kind===kind&&(!state.hideConfirmed[kind]||!x.confirmed)&&(state.folderFilter[kind]===null||state.folderFilter[kind].has(folderKey(x)))&&(!needle||(x.fileName+' '+x.camera+' '+(x.relativeFolder||x.sourceFolder||'')+' '+(x.scene||x.suggestedScene||'')).toLocaleLowerCase().includes(needle)));
const byName=(a,b)=>compareName(a.fileName,b.fileName)||compareName(a.relativeFolder||a.sourceFolder,b.relativeFolder||b.sourceFolder)||compareName(a.camera,b.camera)||compareName(a.id,b.id);
if(sortBy==='folder')items.sort((a,b)=>compareName(a.relativeFolder||a.sourceFolder,b.relativeFolder||b.sourceFolder)||byName(a,b));
else items.sort(byName);
return items;}
const browserPosters=new Map(),posterQueue=[];let postersBusy=0;
function videoSrc(it){return BASE+'/project/'+encodeURIComponent(state.project.id)+'/play/'+encodeURIComponent(it.id);}
function notifyPosterIssue(note){const elNote=el('mwFallbackNote');if(!elNote)return;elNote.hidden=false;elNote.textContent=note;}
function runPosterQueue(){while(postersBusy<2&&posterQueue.length){const job=posterQueue.shift();
if(!job.img.isConnected)continue;postersBusy++;browserFrame(job.it).then(url=>{
 if(url){browserPosters.set(job.key,url);if(job.img.isConnected){job.img.onload=()=>{const wait=job.img.parentElement?.querySelector('.mw-preview-wait');if(wait)wait.remove();};job.img.onerror=()=>showFallbackIcon(job.img,'Грешка при прикажување на кадар');job.img.src=url;}}
 else {if(job.img.isConnected)showFallbackIcon(job.img,'Видео форматот не може да се прочита во прелистувачот. Провери FFmpeg.');}
}).catch(()=>{if(job.img.isConnected)showFallbackIcon(job.img,'Не може да се направи преглед. Инсталирај или поврзи FFmpeg.');})
.finally(()=>{postersBusy--;runPosterQueue();});}}
function showFallbackIcon(img,reason){const holder=img.parentElement;if(!holder)return;img.remove();
 const icon=document.createElement('span');icon.textContent='🎬  Нема кадар';icon.className='mw-preview-wait';icon.title=reason;holder.prepend(icon);
 notifyPosterIssue('Некои видеа немаат preview. FFmpeg мора да биде достапен локално за MOV/H.265 и други формати што прелистувачот не ги поддржува.');}
function browserFrame(it){return new Promise((resolve,reject)=>{
 const video=document.createElement('video');video.muted=true;video.playsInline=true;video.preload='auto';
 let finished=false;const timeout=setTimeout(()=>finish(null),11000);
 const finish=out=>{if(finished)return;finished=true;clearTimeout(timeout);video.pause();video.removeAttribute('src');video.load();resolve(out);};
 const capture=()=>{if(!video.videoWidth||!video.videoHeight)return;
 try {const c=document.createElement('canvas'),scale=Math.min(1,480/video.videoWidth);
 c.width=Math.max(1,Math.round(video.videoWidth*scale));c.height=Math.max(1,Math.round(video.videoHeight*scale));
 c.getContext('2d').drawImage(video,0,0,c.width,c.height);finish(c.toDataURL('image/jpeg',.72));}catch(e){finish(null);}};
 video.onerror=()=>finish(null);video.onseeked=capture;
 video.onloadedmetadata=()=>{const dur=Number(video.duration);const sec=Number.isFinite(dur)&&dur>0?Math.min(.6,Math.max(0,dur-.06)):.35;
 try{video.currentTime=sec;}catch(e){/* some sources only expose frame zero */}};
 video.onloadeddata=()=>{if(video.currentTime===0)capture();};
 video.src=videoSrc(it);video.load();
 });}
function browserPoster(it,img){const key=state.project.id+':'+it.id;
 if(browserPosters.has(key)){img.onerror=null;img.src=browserPosters.get(key);return;}
 img.onerror=null;img.removeAttribute('src');img.alt='Видеото се подготвува';const label=document.createElement('span');label.className='mw-preview-wait';label.textContent='⏳ Се вчитува кадар…';img.after(label);
 posterQueue.push({it,img,key});runPosterQueue();}
function thumbnail(it,second=false){return BASE+'/project/'+encodeURIComponent(state.project.id)+'/thumbnail/'+encodeURIComponent(it.id)+(second?'?frame=2':'');}
function fitGrid(kind){const grid=el('mw-grid-'+kind);if(!grid)return;const chosen=state.zoom[kind],max=Math.max(1,Math.floor((Math.max(grid.clientWidth,180)+7)/62));const actual=Math.min(chosen,max);grid.style.setProperty('--mw-cols',String(actual));el('mw-zoom-text-'+kind).textContent=chosen+' во ред'+(chosen!==actual?' ('+actual+' сега)':'');}
function switchTab(kind){if(!['video','photo'].includes(kind))return;state.tab=kind;for(const k of ['video','photo']){el('mw-pane-'+k).hidden=k!==kind;el('mw-tab-'+k).setAttribute('aria-selected',String(k===kind));}requestAnimationFrame(()=>fitGrid(kind));}
function openPreview(kind,id){const item=state.items.find(it=>it.kind===kind&&it.id===id);if(!item)return;state.focused[kind]=id;const modal=el('mwLightbox'),holder=el('mwLightboxMedia');holder.replaceChildren();
if(kind==='video'){const v=document.createElement('video');v.controls=true;v.preload='metadata';v.autoplay=false;v.poster=thumbnail(item);v.src=BASE+'/project/'+encodeURIComponent(state.project.id)+'/play/'+encodeURIComponent(item.id);holder.append(v);}else{const img=document.createElement('img');img.src=thumbnail(item);img.alt=item.fileName;holder.append(img);}
el('mwLightboxTitle').textContent=(kind==='video'?'🎬 ':'📷 ')+item.fileName;
el('mwLightboxInfo').textContent='Камера: '+item.camera+' · Папка: '+(item.relativeFolder||item.sourceFolder||'.')+' · '+(item.confirmed?'ТВОЈ ИЗБОР: '+item.scene:'MarK ПРЕДЛАГА: '+(item.suggestedScene||'За проверка'));
modal.hidden=false;el('mwClosePreview').focus();}
function closePreview(){const modal=el('mwLightbox');if(modal.hidden)return;modal.hidden=true;const v=el('mwLightboxMedia').querySelector('video');if(v){v.pause();v.removeAttribute('src');v.load();}el('mwLightboxMedia').replaceChildren();}
el('mwClosePreview').onclick=closePreview;
el('mwLightbox').addEventListener('click',e=>{if(e.target===el('mwLightbox'))closePreview();});
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!el('mwLightbox').hidden){closePreview();e.preventDefault();}});
for(const k of ['video','photo']){el('mw-tab-'+k).onclick=()=>switchTab(k);el('mw-openpreview-'+k).onclick=()=>{const id=state.focused[k]||[...state.selected[k]][0]||state.view[k][0]?.id;openPreview(k,id);};
const range=el('mw-zoom-'+k);const setZoom=n=>{state.zoom[k]=Math.max(1,Math.min(15,n));range.value=String(state.zoom[k]);fitGrid(k);try{localStorage.setItem('markWeddingZoom_'+k,String(state.zoom[k]));}catch(e){}};
try{const saved=Number(localStorage.getItem('markWeddingZoom_'+k));if(saved>=1&&saved<=15)setZoom(saved);}catch(e){}
range.oninput=()=>setZoom(Number(range.value));el('mw-minus-'+k).onclick=()=>setZoom(state.zoom[k]-1);el('mw-plus-'+k).onclick=()=>setZoom(state.zoom[k]+1);
}
if(window.ResizeObserver){const obs=new ResizeObserver(()=>fitGrid(state.tab));for(const k of ['video','photo'])obs.observe(el('mw-grid-'+k));}else window.addEventListener('resize',()=>fitGrid(state.tab));
function choose(kind,item,originalIndex,evt){const chosen=state.selected[kind],anchor=state.anchor[kind];if(evt.shiftKey&&anchor!==null){if(!evt.ctrlKey&&!evt.metaKey)chosen.clear();const all=state.view[kind];for(let i=Math.min(anchor,originalIndex);i<=Math.max(anchor,originalIndex);i++)chosen.add(all[i].id);}
else if(evt.ctrlKey||evt.metaKey){if(chosen.has(item.id))chosen.delete(item.id);else chosen.add(item.id);state.anchor[kind]=originalIndex;}else{chosen.clear();chosen.add(item.id);state.anchor[kind]=originalIndex;}
state.focused[kind]=item.id;
 // Patch only selection decorations instead of recreating hundreds of thumbnails.
 const grid=el('mw-grid-'+kind);grid.querySelectorAll('.mw-tile').forEach(tile=>{
 const selected=chosen.has(tile.dataset.itemId);tile.classList.toggle('selected',selected);tile.setAttribute('aria-pressed',String(selected));tile.querySelector('.mw-check').textContent=selected?'✓':'';
 });el('mw-count-'+kind).textContent=state.view[kind].length+' / избрани '+chosen.size;
}
function formatDuration(seconds){if(!Number.isFinite(Number(seconds))||Number(seconds)<=0)return '—:—';const sec=Math.floor(Number(seconds));const h=Math.floor(sec/3600),m=Math.floor((sec%3600)/60),s=sec%60;return (h?h+':'+String(m).padStart(2,'0'):m)+':'+String(s).padStart(2,'0');}
const PAGE=400;
function render(kind,preserveScroll=true){if(!state.project)return;const list=listed(kind);state.view[kind]=list;const max=Math.max(0,Math.ceil(list.length/PAGE)-1);state.page[kind]=Math.min(state.page[kind],max);
const from=state.page[kind]*PAGE,grid=el('mw-grid-'+kind),scrollTop=preserveScroll?grid.scrollTop:0;grid.replaceChildren();const visible=list.slice(from,from+PAGE);let countVideo=0;
for(let i=0;i<visible.length;i++){const item=visible[i],idx=from+i,tile=document.createElement('button');tile.type='button';tile.className='mw-tile'+(state.selected[kind].has(item.id)?' selected':'');tile.dataset.itemId=item.id;tile.setAttribute('aria-pressed',String(state.selected[kind].has(item.id)));tile.title=item.fileName+'\nПапка: '+(item.relativeFolder||item.sourceFolder||'.')+'\n'+(item.scene||item.suggestedScene||'');
const frame=document.createElement('div');frame.className='mw-poster';const img=document.createElement('img');img.loading='lazy';img.alt='Преглед '+item.fileName;img.src=thumbnail(item);img.onerror=()=>{if(img.isConnected){if(kind==='video'){browserPoster(item,img);}else{img.remove();frame.textContent='📷';}}};frame.append(img);const check=document.createElement('span');check.className='mw-check';check.textContent=state.selected[kind].has(item.id)?'✓':'';frame.append(check);
if(kind==='video'){const symbol=document.createElement('span');symbol.className='mw-video-icon';symbol.textContent='▶';frame.append(symbol);const dur=document.createElement('span');dur.className='mw-duration';dur.textContent=formatDuration(item.analysis?.durationSeconds);dur.title='Времетраење на видеото';frame.append(dur);
let timer;tile.addEventListener('mouseenter',()=>{timer=setTimeout(()=>{if(img.isConnected&&!browserPosters.has(state.project.id+':'+item.id))img.src=thumbnail(item,true);},480)});tile.addEventListener('mouseleave',()=>{clearTimeout(timer);if(img.isConnected)img.src=browserPosters.get(state.project.id+':'+item.id)||thumbnail(item);});}
const cam=document.createElement('span');cam.className='mw-camera';cam.textContent=item.camera||'Cam 1';frame.append(cam);
const bar=document.createElement('div');bar.className='mw-stripe '+(item.confirmed?item.color:item.suggestedColor);
const description=document.createElement('div');description.className='mw-meta';description.textContent=item.fileName;
const more=document.createElement('div');more.className='mw-meta mw-muted';more.textContent=(item.relativeFolder||'.')+' · '+(item.scene||item.suggestedScene||'—')+(item.confirmed?' ✓':' ?');
tile.append(frame,bar,description,more);tile.addEventListener('click',ev=>choose(kind,item,idx,ev));tile.addEventListener('dblclick',()=>openPreview(kind,item.id));grid.append(tile);}
el('mw-count-'+kind).textContent=list.length+' / избрани '+state.selected[kind].size;
el('mw-tabcount-'+kind).textContent='('+list.length+')';
el('mw-page-'+kind).textContent=(state.page[kind]+1)+' / '+(max+1);el('mw-prev-'+kind).disabled=state.page[kind]===0;el('mw-next-'+kind).disabled=state.page[kind]===max;
grid.scrollTop=scrollTop;fitGrid(kind);}
function renderAll(){render('video');render('photo');}
async function setLabels(kind,dimension,label,color){try{const scope=new Set(state.view[kind].map(x=>x.id));const ids=[...state.selected[kind]].filter(id=>scope.has(id));if(!ids.length)throw Error('Избери ставки во '+(kind==='video'?'ВИДЕО':'ФОТО')+' (Shift+клик за опсег)');
const chunks=[];for(let i=0;i<ids.length;i+=250)chunks.push(ids.slice(i,i+250));for(const c of chunks)await api('/project/'+state.project.id+'/label',{ids:c,dimension,label,color});
msg('Зачувани '+ids.length+' ставки: '+label);const r=await api('/project/'+state.project.id);state.project=r.project;state.items=r.project.items;state.selected[kind].clear();updateMemory();render(kind);
}catch(e){msg('Грешка при селекција: '+e.message,true);}}
const PALETTE=['сина','виолетова','зелена','жолта','црвена','без боја'];
function entries(){return state.project?.legend||SCENES.map(name=>({name,color:COLORS[name],active:true}));}
function colorOptions(selected){return PALETTE.map(c=>`<option value="${c}" ${c===selected?'selected':''}>${c}</option>`).join('');}
async function changeLegend(action,oldName,name,color){
 try{if(action==='remove'&&!window.confirm('Тргни „'+oldName+'“ од легендата? Постојните доделени ознаки остануваат зачувани.'))return;
 const r=await api('/project/'+state.project.id+'/legend',{action,oldName,name,color});
 state.project.legend=r.legend;if(action==='update'){
  const latest=await api('/project/'+state.project.id);state.project=latest.project;state.items=latest.project.items;
 }
 renderLegends();renderAll();msg(action==='remove'?'Категоријата е скриена од легендата; старите избори остануваат.':'Зачувана легенда за оваа свадба.');
 }catch(e){msg('Легенда: '+e.message,true);}
}
function renderLegends(){
 for(const kind of ['video','photo']){
  const list=el('mw-legend-list-'+kind),editor=el('mw-legend-editor-'+kind);list.replaceChildren();editor.replaceChildren();
  for(const entry of entries().filter(x=>x.active!==false)){
   const b=document.createElement('button');b.className='mw-legend-btn';b.type='button';b.title='Додели '+entry.name+' на избраните';
   b.innerHTML=`<span class="mw-dot ${esc(entry.color)}"></span><span>${esc(entry.name)}</span>`;
   b.onclick=()=>setLabels(kind,'scene',entry.name,entry.color);list.append(b);
   const row=document.createElement('div');row.className='mw-edit-row';row.innerHTML=`<input type="text" maxlength="48" aria-label="Име на категорија" value="${esc(entry.name)}"><select aria-label="Боја">${colorOptions(entry.color)}</select><div class="mw-edit-actions"><button type="button">Зачувај</button><button type="button">Тргни</button></div>`;
   const nameEl=row.querySelector('input'),colorEl=row.querySelector('select'),buttons=row.querySelectorAll('button');
   buttons[0].onclick=()=>changeLegend('update',entry.name,nameEl.value.trim(),colorEl.value);
   buttons[1].onclick=()=>changeLegend('remove',entry.name,'','');editor.append(row);
  }
  const add=document.createElement('div');add.className='mw-edit-row';add.innerHTML=`<input type="text" maxlength="48" placeholder="Нова категорија"><select aria-label="Боја">${colorOptions('сина')}</select><div class="mw-edit-actions"><button type="button">＋ Додај категорија</button></div>`;
  add.querySelector('button').onclick=()=>changeLegend('add','',add.querySelector('input').value.trim(),add.querySelector('select').value);editor.append(add);
 }
}
for(const kind of ['video','photo']){
 el('mw-hide-'+kind).onchange=e=>{state.hideConfirmed[kind]=e.target.checked;state.page[kind]=0;state.anchor[kind]=null;render(kind,false);};
 el('mw-search-'+kind).oninput=()=>{state.page[kind]=0;state.anchor[kind]=null;render(kind,false);};
 el('mw-sort-'+kind).onchange=()=>{state.page[kind]=0;state.anchor[kind]=null;render(kind,false);};
 el('mw-prev-'+kind).onclick=()=>{state.page[kind]--;render(kind,false);};el('mw-next-'+kind).onclick=()=>{state.page[kind]++;render(kind,false);};
 el('mw-selectall-'+kind).onclick=()=>{const l=state.view[kind].slice(state.page[kind]*PAGE,(state.page[kind]+1)*PAGE);for(const it of l)state.selected[kind].add(it.id);render(kind);};
 el('mw-clear-'+kind).onclick=()=>{state.selected[kind].clear();state.anchor[kind]=null;render(kind);};
}
el('mw-set-quality').onclick=()=>setLabels('photo','quality',el('mw-quality').value,'без боја');
el('mwCreate').onclick=async()=>{try{const name=el('mwName').value.trim();if(!name)throw Error('Внеси име на свадбата');const output=el('mwOutputNew').value.trim();if(!output)throw Error('Избери основна папка каде ќе го создадеме проектот');
const r=await api('/projects',{name,date:el('mwDate').value,output});el('mwName').value='';msg('Креиран проект: '+name+' · '+r.project.output);await refreshProjects(r.project.id);
}catch(e){msg('Не успеа креирањето: '+e.message,true);}};
// v2.7 workflow buttons show the requested stage without changing the existing gallery state.
const workflowTargets={1:'mwCreateDetails',2:'mwImportDetails',3:'mwAnalyze',4:'mwShortDetails',5:'mwLongDetails',6:'mwLoveDetails'};
const workflowMessages={1:'1/6 · Создади или избери проект.',2:'2/6 · ПРВО сортирај папки, камери и сцени.',3:'3/6 · Анализирај само откако ќе сортираш.',4:'4/6 · Кратките спотови се посебен AI чекор (во развој).',5:'5/6 · Проверка на СИТЕ долги клипови и XML извоз.',6:'6/6 · Креативна Love Story монтажа (во развој).'};
page.querySelectorAll('.mw-jump').forEach(button=>button.onclick=()=>{const n=Number(button.dataset.step),target=el(workflowTargets[n]);
 if(target){const details=target.tagName==='DETAILS'?target:target.closest('details');if(details)details.open=true;target.scrollIntoView({behavior:'smooth',block:'start'});}
 el('mwWorkflowHint').textContent=workflowMessages[n];page.querySelectorAll('.mw-jump').forEach(x=>x.setAttribute('aria-pressed',String(x===button)));
});
el('mwDirectorPreview').onclick=async()=>{try{if(!state.project)throw Error('Избери проект');const r=await api('/project/'+state.project.id+'/director-plan');
 const c=r.chapters;el('mwDirectorSummary').textContent='Видеа: '+r.count+' · Недостасуваат: '+r.missing+'\n'+Object.entries(c).map(([a,b])=>a+': '+b).join(' · ')+'\n\n'+r.rows.map((x,i)=>(i+1)+'. ['+x.chapter+'] '+x.name+' ('+x.camera+')'+(x.fileExists?'':' !! НЕДОСТАСУВА')).join('\n');
 msg('Вклучени '+r.count+' од '+r.count+' видеа. Не се исфрла ништо.');
 }catch(e){msg('Редослед: '+e.message,true);}};
el('mwDirectorXml').onclick=async()=>{try{if(!state.project)throw Error('Избери проект');const url=BASE+'/project/'+encodeURIComponent(state.project.id)+'/director-long-xml';
 const res=await fetch(url,{credentials:'same-origin',cache:'no-store'});if(!res.ok){let text;try{text=(await res.json()).error}catch(_){text='HTTP '+res.status}throw Error(text)}
 const blob=await res.blob(),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='MarK_Long_Wedding.xml';document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(a.href),30000);
 msg('Premiere XML извоз завршен. Увези го во Premiere и провери ги секвенците.');
 }catch(e){msg('XML не е извезен: '+e.message,true);}};
el('mwProject').onchange=()=>openProject(el('mwProject').value).catch(e=>msg(e.message,true));el('mwReload').onclick=()=>refreshProjects(state.project?.id).catch(e=>msg(e.message,true));
async function followJob(job){if(!job)return;const currentId=state.project?.id;state.job=job.id;let count=0;
while(state.job===job.id&&count++<7200){await new Promise(resolve=>setTimeout(resolve,850));let r=await api('/job/'+job.id),j=r.job;scanMsg('Автоматска анализа: '+j.done+' / '+j.total+' ставки. Видеата и фотографиите веќе можеш да ги гледаш во галеријата.',false,true);if(j.state==='ERROR')throw Error(j.error||'Анализата не успеа');if(j.state==='DONE'){if(currentId===state.project?.id){const latest=await api('/project/'+currentId);state.project=latest.project;state.items=latest.project.items;renderAll();updateMemory();}scanMsg('Анализата заврши: '+j.done+' ставки. Прегледај ги VIDEO/PHOTO табовите и потврди ги сцените со бои.');break;}}
state.job=null;}
async function importFolder(){const btn=el('mwAddFolder');if(btn.disabled)return;btn.disabled=true;const oldText=btn.textContent;btn.textContent='⌛ Се пребарува…';
try{if(!state.project)throw Error('Избери или креирај свадбен проект');const path=el('mwSource').value.trim();if(!path)throw Error('ПРВО избери папка со видеа и фотографии, па притисни „Додај и сортирај“');
scanMsg('Скенирам: '+path+'\nСе пребаруваат сите вложени папки; ова може да потрае за голем HDD. Оригиналите не се менуваат.',false,true);
const r=await api('/project/'+encodeURIComponent(state.project.id)+'/folder',{path,kind:el('mwSourceKind').value,camera:el('mwCamera').value});
const id=state.project.id;await openProject(id);el('mwImportDetails').open=true;el('mwCamera').value='Cam 1';
const added=Number(r.added||0),foundV=Number(r.videos||0),foundP=Number(r.photos||0),dirs=Number(r.nestedMediaFolders||0);
const label=(r.rescan?'Повторно пребарување':'Скенирање')+': '+foundV+' видеа · '+foundP+' фотографии во '+dirs+' папки со материјали. '+(r.rescan?'Нови додадени: '+added+'. ':'' );
if(!foundV&&!foundP){scanMsg('Нема пронајдено поддржани видео или фото датотеки во „'+path+'“. Провери дали папката е вистинската и дали постојат MP4/MOV/MTS/JPG/NEF и слични формати.',true);showImport();}
else{scanMsg(label+'Материјалите се внесени. Префрли се на VIDEO/PHOTO табот за thumbnails. Сега додели сцени, а потоа отвори чекор „Анализа“.');}
if(r.job)followJob(r.job).catch(e=>scanMsg('Материјалите се внесени, но дополнителната анализа пријави: '+e.message,true));
}catch(e){scanMsg('СКЕНИРАЊЕ НЕ УСПЕА: '+e.message+'\nПровери ја избраната папка и дали имаш дозвола да ја читаш. Ниту еден оригинал не е менуван.',true);showImport();}
finally{btn.disabled=false;btn.textContent=oldText;}}
el('mwAddFolder').onclick=importFolder;
el('mwAnalyze').onclick=async()=>{try{if(!state.project)throw Error('Избери свадбен проект');if(!state.items.length){scanMsg('Во проектот нема внесени материјали. Прво избери папка и притисни „Додај и сортирај“. „Повтори анализа“ работи само со веќе внесени фајлови.',true);showImport();return;}
scanMsg('Повторно ги анализирам веќе внесените '+state.items.length+' ставки. Нови папки се додаваат од горниот дел.',false,true);
const r=await api('/project/'+encodeURIComponent(state.project.id)+'/analyze',{});await followJob(r.job);scanMsg('Анализата е завршена. Во VIDEO/PHOTO можеш да селектираш материјали.');}
catch(e){scanMsg('Анализа: '+e.message,true);}};
el('mwSaveOutput').onclick=async()=>{try{const r=await api('/project/'+state.project.id+'/output',{path:el('mwOutput').value.trim()});state.project.output=r.output;msg('Зачувана излезна папка: '+r.output);}catch(e){msg(e.message,true);}};
el('mwPreviewSort').onclick=async()=>{try{const candidates=state.items.filter(x=>x.confirmed&&!x.sorted&&!x.sortedPartial&&(state.folderFilter[x.kind]===null||state.folderFilter[x.kind].has(folderKey(x)))),skipped=state.items.length-candidates.length;if(!candidates.length)throw Error('Нема потврдени несортирани ставки. Избери боја/локација со легендата.');
const ids=candidates.map(x=>x.id),mode=el('mwMode').value;const r=await api('/project/'+state.project.id+'/sort-preview',{mode,ids});state.plan=ids;state.mode=mode;
el('mwPlan').hidden=false;el('mwDoSort').hidden=false;el('mwPlan').textContent=mode+': '+r.count+' фајлови · '+r.output+'\n'+r.files.map(x=>x.from+' → '+x.toFolder).join('\n')+(r.count>20?'\n… прикажани првите 20':'')+'\nНе се вклучени непотврдени/веќе обработени ставки: '+skipped;
el('mwDoSort').textContent=mode==='MOVE'?'⚠ ПОТВРДИ MOVE на '+r.count+' фајлови':'✓ ПОТВРДИ COPY на '+r.count+' фајлови';msg('Планот е подготвен. Фајловите НЕ се поместени.');
}catch(e){el('mwDoSort').hidden=true;msg('План: '+e.message,true);}};
el('mwDoSort').onclick=async()=>{const btn=el('mwDoSort');try{const ids=state.plan,mode=state.mode;if(!ids.length)throw Error('Нема план');
const text=mode==='MOVE'?'Оригиналните фајлови ќе се ПРЕМЕСТАТ во проектот. Дали си сигурен?':'Материјалите ќе се КОПИРААТ во проектот. Оригиналите остануваат. Продолжи?';
if(!window.confirm(text))return;btn.disabled=true;
const r=await api('/project/'+state.project.id+'/sort',{mode,ids,confirm:mode});await followJob(r.job);el('mwPlan').hidden=true;btn.hidden=true;state.plan=[];
}catch(e){msg('Сортирање: '+e.message+'; провери го статусот пред повторен обид',true);}finally{btn.disabled=false;}};
(async()=>{try{const r=await api('/status');el('mwCapabilities').textContent='FFmpeg: '+(r.ffmpeg?'ДА':'НЕ (browser fallback)')+' · FFprobe: '+(r.ffprobe?'ДА':'НЕ')+' · Фото: '+(r.photoAnalysis?'ДА':'НЕ');if(!r.ffmpeg)notifyPosterIssue('FFmpeg не е пронајден. MP4 може да добие кадар преку прелистувачот, но MOV/H.265 и други формати бараат локален FFmpeg.');await refreshProjects();}catch(e){msg('MarK проектниот сервер не е поврзан: '+e.message,true);}})();
})();
