const fs = require('fs');
const vm = require('vm');
const assert = require('node:assert/strict');
const root = require('node:path').resolve(__dirname, '..');
const fixed = fs.readFileSync(root + '/admin/src/main/assets/index.html', 'utf8');
const original = fs.readFileSync(root + '/base/index-v6.7.html', 'utf8');
const scripts = html => [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map(x => x[1]);
for (const [i, code] of scripts(fixed).entries()) new vm.Script(code, {filename: `app-script-${i}.js`});
const clone = x => x === undefined ? undefined : JSON.parse(JSON.stringify(x));
const example = () => ({request: {id:'inv_test', weddingId:'w_test', weddingDate:'2026-10-18', weddingTitle:'Кирил и Марија', employeeName:'Тест Фотограф', startTime:'10:00', allowedRoles:['Фотограф']}, response:{status:'accepted', proposedFee:150, roles:['Фотограф'], respondedAt:'2026-10-11T20:00:00Z'}});

function setup(html, inv = example()) {
  const state = {requests:[], alerts:[], confirmCalls:0, failRead:false, failPatch:false, delay:0, syncFail:false, finance:{crew:[]}, renders:0};
  const employees = {user_test:{name:'Тест Фотограф', uid:'user_test', active:true}};
  state.remote = {tdStudios:{studio_test:{employees, invites:{user_test:{inv_test:clone(inv)}}, messages:{user_test:{}}}}};
  const nodes = new Map();
  let ctx;
  class El {
    constructor(tag) {this.tagName=tag; this.id=''; this.style={}; this.dataset={}; this.listeners=[]; this.disabled=false; this.textContent=''; this.owner=null;}
    set innerHTML(value) {this.html=value; this.parts=new Map(); for(const m of value.matchAll(/<([\w-]+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)){const e=new El(m[1]);e.id=m[3];e.owner=this;e.onclick=m[2].match(/onclick="([^"]*)"/)?.[1];this.parts.set(e.id,e);}}
    get innerHTML() {return this.html||'';}
    addEventListener(type, callback, capture=false) {this.listeners.push({type,callback,capture});}
    appendChild(el) {el.parent=this;nodes.set(el.id,el);for(const [id,p] of el.parts||[])nodes.set(id,p);}
    querySelector(selector) {return this.parts?.get(selector.replace(/^#/,''))||null;}
    remove() {nodes.delete(this.id);for(const [id,p] of this.parts||[])if(nodes.get(id)===p)nodes.delete(id);}
    async click(force=false) {if(this.disabled&&!force)return; const event={target:this,stopImmediatePropagation(){this.stopped=true}};for(const l of this.listeners.filter(l=>l.type==='click')){if(event.stopped)break;await l.callback(event);}if(this.onclick)vm.runInContext(this.onclick,ctx);}
  }
  const store = new Map();
  store.set('tdCloudConfigV1',JSON.stringify({apiKey:'test-only',databaseURL:'https://td-test.invalid'}));
  store.set('tdCloudStudioV1',JSON.stringify({studioId:'studio_test'}));
  store.set('tdCloudAuthV1',JSON.stringify({localId:'admin_test',idToken:'fixture-token',expiresAt:Date.now()+3600000}));
  const storage={getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,String(v)),removeItem:k=>store.delete(k)};
  const fetch = async (url, options={}) => {
    assert.equal(new URL(url).hostname, 'td-test.invalid', 'Test must never use a real service');
    const path=new URL(url).pathname.replace(/^\//,'').replace(/\.json$/,'');
    const method=options.method||'GET';
    const body=options.body?JSON.parse(options.body):undefined;
    state.requests.push({path,method,body});
    if(state.delay)await new Promise(resolve=>setTimeout(resolve,state.delay));
    if((method==='GET'&&state.failRead)||(method==='PATCH'&&state.failPatch))throw new Error('Нема интернет');
    const keys=path.split('/');
    let value=state.remote;
    for(const k of keys)value=value?.[k];
    if(method==='PATCH'){
      for(const [field,v] of Object.entries(body)){let obj=value;const parts=field.split('/');for(const key of parts.slice(0,-1))obj=obj[key]||(obj[key]={});obj[parts.at(-1)]=clone(v);}
    } else if(method!=='GET')throw new Error('Unexpected write: '+method+' '+path);
    return {ok:true,status:200,text:async()=>JSON.stringify(value??null)};
  };
  const globals={console,URL,URLSearchParams,Uint8Array,AbortController,setTimeout,clearTimeout,setInterval:()=>0,clearInterval:()=>{},crypto:require('node:crypto').webcrypto,
    localStorage:storage,fetch,document:{getElementById:id=>nodes.get(id)||null,createElement:tag=>new El(tag),body:new El('body'),head:new El('head'),querySelector:()=>null,querySelectorAll:()=>[]},
    E:s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),data:[{id:'w_test',date:'2026-10-18',bride:'Марија',groom:'Кирил'}],
    finRecord:()=>state.finance,finId:()=>`crew_${state.finance.crew.length}`,finSave:()=>store.set('tdBookingFinanceV1',JSON.stringify({w_test:state.finance})),refreshViews:()=>{},
    alert:s=>state.alerts.push(s),confirm:()=>{state.confirmCalls++;return false;}};
  ctx=vm.createContext(globals);ctx.window=ctx;
  let cloud=scripts(html).find(s=>s.includes('online employee/team module'));
  assert.ok(cloud);
  const pos=cloud.lastIndexOf('addCss();installPanel();');assert.ok(pos>0);
  cloud=cloud.slice(0,pos)+`\nwindow.__seed=(employees,invites)=>{cloudEmployees=employees;cloudInvites=invites;};window.__integrate=integrateInvites;})();`;
  vm.runInContext(cloud,ctx,{filename:'cloud-module.js'});
  ctx.__seed(clone(employees),{user_test:{inv_test:clone(inv)}});
  ctx.tdCloudSync=async()=>{if(state.syncFail)throw new Error('background sync unavailable');};
  state.ctx=ctx;state.nodes=nodes;state.store=store;
  state.offer=()=>state.remote.tdStudios.studio_test.invites.user_test.inv_test;
  state.open=()=>ctx.tdConfirmInvite('user_test','inv_test');
  state.accept=()=>nodes.get('tdPriceAccept').click();
  state.writes=()=>state.requests.filter(x=>x.method!=='GET');
  state.html=()=>nodes.get('tdDialog')?.innerHTML||'';
  return state;
}

(async()=>{
  const results=[];
  let s=setup(original);await s.open();assert.equal(s.confirmCalls,1);assert.equal(s.writes().length,0);assert.equal(s.nodes.has('tdDialog'),false);results.push('Reproduced old Android behavior: hidden confirm cancels acceptance.');
  s=setup(fixed);s.open();assert.match(s.html(),/Прифаќање цена/);assert.match(s.html(),/150\.00/);assert.equal(s.writes().length,0);await s.nodes.get('tdPriceCancel').click();assert.equal(s.nodes.has('tdDialog'),false);assert.equal(s.writes().length,0);results.push('Visible confirmation and Cancel perform no write.');
  s=setup(fixed);s.open();s.delay=10;const a=s.accept();await s.nodes.get('tdPriceAccept').click(true);await a;assert.equal(s.writes().length,1);assert.equal(s.writes()[0].method,'PATCH');assert.deepEqual(Object.keys(s.writes()[0].body).sort(),['contract/base','decision']);assert.equal(s.offer().decision.confirmedFee,150);assert.match(s.offer().contract.base.body,/150\.00/);assert.equal(s.offer().contract.base.status,'waiting_signature');assert.match(s.html(),/Цената е потврдена/);assert.equal(s.confirmCalls,0);assert.equal(s.alerts.length,0);assert.equal(s.finance.crew.length,1);s.ctx.__integrate();assert.equal(s.finance.crew.length,1);assert.equal(JSON.parse(s.store.get('tdCloudCacheV1')).invites.user_test.inv_test.decision.confirmedFee,150);results.push('Double click saves one fee+contract update; cache and finance persist without duplicate crew.');
  s=setup(fixed);s.open();s.failPatch=true;await s.accept();assert.match(s.nodes.get('tdPriceSaveStatus').textContent,/Зачувувањето не е потврдено.*Нема интернет/);assert.equal(s.offer().decision,undefined);assert.equal(s.nodes.get('tdPriceAccept').disabled,false);s.failPatch=false;await s.accept();assert.equal(s.offer().decision.confirmedFee,150);results.push('Connection failure shows an error, keeps the offer unchanged, and supports retry.');
  s=setup(fixed);s.open();s.failRead=true;await s.accept();assert.match(s.nodes.get('tdPriceSaveStatus').textContent,/Нема интернет/);assert.equal(s.writes().length,0);results.push('Failed pre-save read cannot accept an unverified offer.');
  s=setup(fixed);s.open();s.offer().response.proposedFee=200;await s.accept();assert.equal(s.writes().length,0);assert.match(s.nodes.get('tdPriceSaveStatus').textContent,/Предлогот е изменет/);assert.equal(s.offer().decision,undefined);results.push('Changed price is displayed as an error and is not accepted silently.');
  s=setup(fixed);s.open();s.offer().request=Object.fromEntries(Object.entries(s.offer().request).reverse());await s.accept();assert.equal(s.offer().decision.confirmedFee,150);results.push('Database object key ordering cannot block an unchanged price.');
  for(const raw of [null,undefined,'',-1,'not-a-price']){const inv=example();inv.response.proposedFee=raw;s=setup(fixed,inv);s.open();assert.equal(s.nodes.has('tdPriceAccept'),false);assert.equal(s.writes().length,0);}results.push('Missing, blank, negative, and malformed fees cannot be accepted.');
  for(const response of [{counterBy:'td',awaitingEmployee:true},{counterBy:'employeeRejectedTd'},{status:'rejected'},{roles:[]}]){const inv=example();Object.assign(inv.response,response);s=setup(fixed,inv);s.open();assert.equal(s.nodes.has('tdPriceAccept'),false);assert.equal(s.writes().length,0);}results.push('Pending, rejected, and role-free responses stay blocked with visible feedback.');
  for(const fee of [0,120.5]){const inv=example();inv.response.proposedFee=fee;inv.response.counterBy='employeeAcceptedTd';s=setup(fixed,inv);s.open();await s.accept();assert.equal(s.offer().decision.confirmedFee,fee);}results.push('Zero-fee and decimal accepted counteroffers retain their exact price.');
  s=setup(fixed);s.open();s.offer().decision={status:'confirmed',confirmedFee:150};s.offer().contract={base:{body:'existing contract'},signature:{data:'existing signature'}};await s.accept();assert.equal(s.writes().length,0);assert.equal(s.offer().contract.signature.data,'existing signature');assert.match(s.html(),/веќе е потврдена/);results.push('Already confirmed contract is preserved without another write.');
  s=setup(fixed);s.open();s.syncFail=true;await s.accept();assert.match(s.html(),/Цената е потврдена/);assert.equal(s.offer().decision.confirmedFee,150);results.push('A later refresh error cannot turn a completed save into a failure.');
  console.log(JSON.stringify({syntaxScripts:scripts(fixed).length,passed:results.length,results},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
