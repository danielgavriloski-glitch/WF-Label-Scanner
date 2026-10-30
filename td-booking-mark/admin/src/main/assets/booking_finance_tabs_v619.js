/* v6.19 Finance navigation: reorganize existing DOM without changing any finance data. */
(()=>{'use strict';
const fin=document.getElementById('finance');
if(!fin||document.getElementById('bookingFinanceTabs'))return;
const definitions=[
 ['overview','Преглед'],['client','Уплати'],['extras','Доплати / Експрес'],
 ['expenses','Трошоци'],['crew','Екипа']
];
const tabs=document.createElement('div');tabs.id='bookingFinanceTabs';tabs.setAttribute('role','tablist');
const style=document.createElement('style');
style.textContent=`
#bookingFinanceTabs{display:flex;gap:5px;overflow-x:auto;padding:7px 0 12px;scrollbar-width:thin}
#bookingFinanceTabs button{flex:0 0 auto;font-size:13px;min-height:40px;padding:8px 12px;white-space:nowrap;background:#e2ede5;color:#25463e}
#bookingFinanceTabs button.active{background:#184d42;color:white}
.finance-pane[hidden]{display:none!important}
.finance-pane h3{margin-top:9px}
`;
document.head.appendChild(style);
const panes={};
for(const [key,label] of definitions){
 const panel=document.createElement('div');panel.className='finance-pane';panel.id='financePane_'+key;
 panel.setAttribute('role','tabpanel');panes[key]=panel;
 const button=document.createElement('button');button.type='button';button.className='secondary';button.dataset.financeTab=key;button.textContent=label;
 button.setAttribute('role','tab');button.onclick=()=>show(key);
 tabs.appendChild(button);
}
const selector=document.getElementById('finWedding');
if(!selector)return;
selector.insertAdjacentElement('afterend',tabs);
// Keep the date/event selector visible and move existing functional controls unchanged.
let pane='overview';
for(const element of [...fin.children]){
 if(element===tabs||Object.values(panes).includes(element)||element===fin.querySelector('h2')||element===selector)continue;
 if(element.tagName==='P'&&element.classList.contains('finance-note')&&pane==='overview')continue;
 if(element.tagName==='LABEL'&&element.nextElementSibling===selector)continue;
 if(element.tagName==='H3'){
   const heading=element.textContent.trim();
   if(heading.startsWith('Уплата од клиент'))pane='client';
   else if(heading.startsWith('Експрес')||heading.startsWith('Дополнителни услуги'))pane='extras';
   else if(heading.startsWith('Други трошоци'))pane='expenses';
   else if(heading.startsWith('Ангажмани'))pane='crew';
 }
 panes[pane].appendChild(element);
}
for(const [key] of definitions)fin.appendChild(panes[key]);
function show(key){
 for(const [id] of definitions){
   panes[id].hidden=id!==key;
   const button=tabs.querySelector('[data-finance-tab="'+id+'"]');
   button.classList.toggle('active',id===key);button.setAttribute('aria-selected',String(id===key));
 }
}
window.tdFinanceTab=show;
const oldNav=window.navigate;
if(typeof oldNav==='function')window.navigate=function(view){oldNav(view);if(view==='finance')show('overview')};
const oldGo=window.goFinance;
if(typeof oldGo==='function')window.goFinance=function(id){oldGo(id);show('overview')};
show('overview');
})();