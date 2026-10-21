const fs = require('fs');
const vm = require('vm');
const assert = require('node:assert/strict');
const root = require('node:path').resolve(__dirname, '..');
const html = fs.readFileSync(root + '/admin/src/main/assets/index.html','utf8');
const scripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map(m=>m[1]);
for (const [i,s] of scripts.entries()) new vm.Script(s,{filename:`app-script-${i}.js`});
const clone = x => x === undefined ? undefined : JSON.parse(JSON.stringify(x));
const example = () => ({request:{id:'inv_test', weddingId:'w_test', weddingTitle:'Марија и Кирил', weddingDate:'2026-10-18', employeeName:'Фотограф', allowedRoles:['Фотограф']}, response:{status:'accepted', proposedFee:150, roles:['Фотограф'], counterBy:'td', awaitingEmployee:true}, custom:{keep:'unchanged'}});
function setup(inv = example(), crew = []) {
  const s = {remote:clone(inv), finance:{clientPayments:[],crew:clone(crew)}, requests:[], confirms:0, alerts:[], failGet:false, failPut:false, lostResponse:false, race:false, failLocal:false, revision:1, delay:0};
  const nodes = new Map(); let ctx;
  class El {
    constructor(tag){this.tagName=tag;this.style={};this.id='';this.disabled=false;this.textContent='';this.value='';this.listeners=[];this.classList={add(){},remove(){}};}
    set innerHTML(value){this.html=value;this.parts=new Map();for(const m of value.matchAll(/<([\w-]+)\b([^>]*\bid="([^"]+)"[^>]*)>/g)){const e=new El(m[1]);e.id=m[3];e.value=m[2].match(/\bvalue="([^"]*)"/)?.[1]||'';e.readOnly=/\breadonly\b/.test(m[2]);e.onclick=m[2].match(/onclick="([^"]*)"/)?.[1];if(e.tagName==='select')e.value='Готовина';this.parts.set(e.id,e);}}
    get innerHTML(){return this.html||'';}
    addEventListener(type,callback){this.listeners.push({type,callback});}
    appendChild(el){el.parent=this;nodes.set(el.id,el);for(const [id,p] of el.parts||[])nodes.set(id,p);}
    insertAdjacentHTML(_pos,value){this.html=(this.html||'')+value;}
    remove(){nodes.delete(this.id);for(const [id,p] of this.parts||[])if(nodes.get(id)===p)nodes.delete(id);}
    querySelector(){return null;}
    async click(force=false){if(this.disabled&&!force)return;for(const x of this.listeners.filter(x=>x.type==='click'))await x.callback({target:this});if(this.onclick)await vm.runInContext(this.onclick,ctx);}
  }
  for(const id of ['tdCloudInvites','tdCloudSummary','tdCloudEmployees','tdCloudStatus','tdCloudMessages'])nodes.set(id,new El('div'));
  const store=new Map([
    ['tdCloudConfigV1',JSON.stringify({apiKey:'fixture-key',databaseURL:'https://td-test.invalid'})],
    ['tdCloudStudioV1',JSON.stringify({studioId:'studio_test'})],
    ['tdCloudAuthV1',JSON.stringify({localId:'admin_test',idToken:'fixture-token',expiresAt:Date.now()+3600000})]
  ]);
  const getHeader=(headers,name)=>Object.entries(headers||{}).find(([k])=>k.toLowerCase()===name.toLowerCase())?.[1];
  const fetch=async(url,opt={})=>{
    const u=new URL(url);assert.equal(u.hostname,'td-test.invalid','No test request may reach a real service.');
    const path=u.pathname.replace(/^\//,'').replace(/\.json$/,'');
    assert.equal(path,'tdStudios/studio_test/invites/user_test/inv_test');
    const method=opt.method||'GET',body=opt.body?JSON.parse(opt.body):undefined;
    s.requests.push({path,method,body,headers:opt.headers,cache:opt.cache});
    if(s.delay)await new Promise(r=>setTimeout(r,s.delay));
    if(method==='GET'&&s.failGet)throw new Error('Нема интернет');
    const response=(status,value)=>({ok:status===200,status,text:async()=>JSON.stringify(value),headers:{get:name=>name.toLowerCase()==='etag'?`"v${s.revision}"`:null}});
    if(method==='GET')return response(200,clone(s.remote));
    assert.equal(method,'PUT');assert.ok(getHeader(opt.headers,'if-match'),'Payment writes must have a conditional version.');
    if(s.failPut)throw new Error('Нема интернет');
    if(s.race){s.remote.concurrentField='keep latest';s.revision++;s.race=false;}
    if(getHeader(opt.headers,'if-match')!==`"v${s.revision}"`)return response(412,clone(s.remote));
    s.remote=clone(body);s.revision++;
    if(s.lostResponse){s.lostResponse=false;throw new Error('Изгубен одговор');}
    return response(200,clone(s.remote));
  };
  const globals={console,URL,URLSearchParams,Uint8Array,AbortController,crypto:require('node:crypto').webcrypto,setTimeout,clearTimeout,setInterval:()=>0,clearInterval(){},
    localStorage:{getItem:k=>store.get(k)??null,setItem:(k,v)=>store.set(k,String(v)),removeItem:k=>store.delete(k)},fetch,
    document:{getElementById:id=>nodes.get(id)||null,createElement:tag=>new El(tag),body:new El('body'),head:new El('head'),querySelector:()=>null,querySelectorAll:sel=>sel.includes('tdSettlementRole')?s.selectedRoles.map(value=>({value})):sel==='#crewList .finance-row'?s.crewRows:[]},
    E:x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),data:[{id:'w_test',date:'2026-10-18',bride:'Марија',groom:'Кирил'}],
    finRecord:()=>s.finance,finId:()=>`crew_${s.finance.crew.length}`,finSave:()=>{if(s.failLocal)throw new Error('Storage full');store.set('tdBookingFinanceV1',JSON.stringify({w_test:s.finance}));},
    selectedWedding:()=>globals.data[0],crewPaid:c=>(c.payments||[]).reduce((v,p)=>v+Number(p.amount),0),drawFinance:()=>{},refreshViews:()=>{},financeRefresh:()=>{},
    addPayout:id=>{const c=s.finance.crew.find(x=>x.id===id);c.payments.push({id:'additional_payment',amount:10,date:'2026-10-11',method:'Готовина',note:'Доплата'});},
    deletePayment:(_kind,pid,cid)=>{const c=s.finance.crew.find(x=>x.id===cid);c.payments=c.payments.filter(x=>x.id!==pid);},
    confirm:()=>{s.confirms++;return false;},alert:m=>s.alerts.push(m)};
  ctx=vm.createContext(globals);ctx.window=ctx;ctx.addEventListener=()=>{};
  let cloud=scripts.find(x=>x.includes('online employee/team module'));
  const end=cloud.lastIndexOf('addCss();installPanel();');assert.ok(end>0);
  cloud=cloud.slice(0,end)+`window.__seed=inv=>{cloudEmployees={user_test:{name:'Фотограф',active:true}};cloudInvites={user_test:{inv_test:inv}};};window.__integrate=integrateInvites;window.__wrap=wrapFinance;window.__mirror=mirrorCrew;window.__preview=tdSettlementPreview;})();`;
  vm.runInContext(cloud,ctx);ctx.__seed(clone(inv));ctx.tdCloudSync=async()=>{};
  s.ctx=ctx;s.nodes=nodes;s.store=store;s.selectedRoles=['Фотограф'];s.crewRows=[new El('div')];
  s.open=()=>ctx.tdDirectSettlement('user_test','inv_test');
  s.openLocal=cid=>ctx.tdDirectCrewSettlement('w_test',cid);
  s.set=(total,fee,date='2026-10-11')=>{nodes.get('tdSettlementPaid').value=String(total);if(fee!==undefined)nodes.get('tdSettlementFee').value=String(fee);nodes.get('tdSettlementDate').value=date;};
  s.save=()=>nodes.get('tdSettlementSave').click();
  s.writes=()=>s.requests.filter(r=>r.method!=='GET');
  s.dialog=()=>nodes.get('tdDialog')?.innerHTML||'';
  s.status=()=>nodes.get('tdSettlementStatus')?.textContent||'';
  s.sync=()=>{ctx.__seed(clone(s.remote));ctx.__integrate();};
  return s;
}
const sum = payments=>Object.values(payments||{}).reduce((v,p)=>v+Number(p.amount),0);
const linked = (payments=[])=>({id:'crew_existing',name:'Фотограф',fee:'150',role:'Фотограф',payments,cloudEmployeeId:'user_test',cloudInviteId:'inv_test'});
(async()=>{
 const results=[];
 let s=setup();await s.open();assert.equal(s.writes().length,0);s.set(150);await s.save();assert.equal(s.remote.completion.status,'closed');assert.equal(s.remote.decision.confirmationMode,'direct_settlement');assert.equal(s.remote.decision.confirmedFee,150);assert.equal(s.remote.contract,undefined);assert.equal(s.remote.response.awaitingEmployee,true);assert.equal(s.remote.custom.keep,'unchanged');assert.equal(sum(s.remote.payments),150);assert.equal(s.finance.crew.length,1);assert.equal(s.finance.crew[0].completion.status,'closed');assert.equal(sum(s.finance.crew[0].payments),150);assert.equal(s.confirms,0);assert.equal(s.alerts.length,0);assert.match(s.nodes.get('tdCloudInvites').innerHTML,/Затворен од TD/);assert.doesNotMatch(s.nodes.get('tdCloudInvites').innerHTML,/Чека одговор од колегата/);assert.equal(JSON.parse(s.store.get('tdBookingFinanceV1')).w_test.crew[0].completion.closedBy,'admin');results.push('Pending counteroffer closes directly, saves both ledgers, and never fabricates a response or signature.');
 await s.open();s.set(150);await s.save();assert.equal(Object.keys(s.remote.payments).length,1);assert.equal(s.finance.crew.length,1);results.push('Reopening and saving the same total does not duplicate a payment or engagement.');
 let inv=example();inv.decision={status:'confirmed',confirmedFee:150,roles:['Фотограф']};inv.contract={base:{body:'Original agreement',status:'waiting_signature'},signature:{data:'real-ink',name:'Фотограф',signedAt:'2026-10-11'}};inv.payments={deposit:{amount:50,date:'2026-10-01',method:'Трансфер',note:'Капар',extra:'preserve'}};s=setup(inv,[linked([{id:'deposit',...inv.payments.deposit}])]);await s.open();s.set(120);await s.save();assert.deepEqual(s.remote.contract,inv.contract);assert.deepEqual(s.remote.decision,inv.decision);assert.deepEqual(s.remote.payments.deposit,inv.payments.deposit);assert.equal(Object.keys(s.remote.payments).length,2);assert.equal(sum(s.remote.payments),120);assert.equal(s.remote.completion.remainingDue,30);assert.match(s.dialog(),/30\.00/);results.push('A 50 deposit plus a total of 120 adds only 70, keeps real signatures, and retains 30 due.');
 s=setup(inv,[linked([{id:'deposit',...inv.payments.deposit}])]);await s.open();s.set('40,25');s.ctx.__preview();assert.match(s.nodes.get('tdSettlementPreview').textContent,/Корекција/);await s.save();assert.deepEqual(s.remote.payments.deposit,inv.payments.deposit);const correction=Object.values(s.remote.payments).find(p=>p.entryType==='admin_adjustment');assert.equal(correction.amount,-9.75);assert.match(correction.note,/50\.00.*40\.25/);assert.equal(sum(s.remote.payments),40.25);assert.equal(s.remote.completion.remainingDue,109.75);results.push('Reducing a total records an explicit negative adjustment without deleting payment history.');
 for(const [fee,total,due] of [[150,0,150],[150,175,0],[0,0,0],[100.25,80.15,20.1]]){s=setup();await s.open();s.set(total,fee);await s.save();assert.equal(s.remote.completion.paidTotal,total);assert.equal(s.remote.completion.remainingDue,due);assert.equal(s.remote.decision.confirmedFee,fee);}results.push('Zero, partial, excess, and decimal payments keep the actual fee and exact balance.');
 for(const value of ['',-1,'abc','NaN','Infinity','12.345','1e2']){s=setup();await s.open();s.set(value);await s.save();assert.equal(s.writes().length,0);assert.equal(s.remote.completion,undefined);assert.match(s.status(),/Внеси/);}results.push('Blank, negative, malformed, and overprecise amounts are blocked visibly.');
 s=setup();await s.open();s.set(100,150,'2026-02-30');await s.save();assert.equal(s.writes().length,0);assert.match(s.status(),/датум/);s.set(100);s.selectedRoles=[];await s.save();assert.equal(s.writes().length,0);assert.match(s.status(),/улога/);results.push('Invalid dates and missing roles cannot create a settlement.');
 s=setup(inv);await s.open();s.set(150,170);await s.save();assert.equal(s.writes().length,0);assert.match(s.status(),/веќе е потврден/);results.push('The direct payment flow cannot rewrite an agreed fee or signed contract.');
 s=setup();await s.open();s.delay=10;s.set(150);const first=s.save();await s.nodes.get('tdSettlementSave').click(true);await first;assert.equal(s.writes().length,1);assert.equal(sum(s.remote.payments),150);results.push('Rapid repeated taps produce one conditional write and one payment.');
 s=setup();await s.open();s.set(150);s.failPut=true;await s.save();assert.equal(s.remote.completion,undefined);assert.equal(s.finance.crew.length,0);assert.match(s.status(),/Нема интернет/);assert.equal(s.nodes.get('tdSettlementSave').disabled,false);s.failPut=false;await s.save();assert.equal(sum(s.remote.payments),150);results.push('Network failure leaves the cloud unchanged and enables a safe retry.');
 s=setup();await s.open();s.set(150);s.lostResponse=true;await s.save();assert.equal(sum(s.remote.payments),150);assert.match(s.status(),/Изгубен одговор/);await s.save();assert.equal(s.writes().length,1);assert.equal(Object.keys(s.remote.payments).length,1);assert.equal(s.finance.crew.length,1);results.push('A lost success response is recovered without charging or recording twice.');
 s=setup();await s.open();s.remote.payments={other:{amount:20,date:'2026-10-11',method:'Трансфер'}};s.revision++;s.set(150);await s.save();assert.equal(s.writes().length,0);assert.match(s.status(),/изменети/);assert.equal(sum(s.remote.payments),20);results.push('A payment added after opening the form must be reviewed before closure.');
 s=setup();await s.open();s.set(150);s.race=true;await s.save();assert.equal(s.remote.completion,undefined);assert.equal(s.remote.concurrentField,'keep latest');assert.match(s.status(),/меѓувреме/);results.push('Conditional writes preserve an update made between the final read and save.');
 s=setup();await s.open();s.set(150);s.failLocal=true;await s.save();assert.equal(s.remote.completion.status,'closed');assert.match(s.dialog(),/Ангажманот е затворен/);s.failLocal=false;s.sync();assert.equal(s.finance.crew.length,1);assert.equal(sum(s.finance.crew[0].payments),150);results.push('A local refresh failure cannot undo cloud success; the next sync restores the ledger.');
 inv=example();inv.decision={status:'confirmed',confirmedFee:150,roles:['Фотограф']};inv.payments={remote:{amount:90,date:'2026-10-10',method:'Трансфер'}};inv.completion={status:'closed',closedBy:'admin',paidTotal:90};s=setup(inv);s.sync();s.sync();assert.equal(s.writes().length,0);assert.equal(s.finance.crew.length,1);assert.equal(sum(s.finance.crew[0].payments),90);assert.equal(s.finance.crew[0].completion.status,'closed');results.push('A second device imports paid history and closure without overwriting cloud payments.');
 const c={id:'local',name:'Локален колега',role:'Фотограф',fee:'150',payments:[{id:'old',amount:50,date:'2026-10-01',method:'Готовина'}],signedContract:{body:'signed-local',provider:'ink-1',crew:'ink-2'}};s=setup(example(),[c]);s.ctx.data.push({id:'another',date:'2026-10-20',bride:'Друга',groom:'Свадба'});s.openLocal('local');s.set(120);await s.save();assert.equal(s.requests.length,0);assert.equal(s.finance.crew[0].completion.closedBy,'admin');assert.equal(sum(s.finance.crew[0].payments),120);assert.deepEqual(s.finance.crew[0].signedContract,c.signedContract);results.push('Unlinked local crew can be closed offline, retaining signatures and previous payments.');
 s=setup(example(),[c]);s.openLocal('local');s.set(150);s.failLocal=true;await s.save();assert.equal(s.finance.crew[0].completion,undefined);assert.equal(sum(s.finance.crew[0].payments),50);assert.match(s.status(),/Storage full/);results.push('A failed local save rolls back the payment and closure together.');
 inv=example();inv.decision={status:'confirmed',confirmedFee:150,roles:['Фотограф']};inv.payments={unseen:{amount:30,date:'2026-10-11',method:'Трансфер'}};inv.completion={status:'closed',closedBy:'admin',paidTotal:30};s=setup(inv,[linked([])]);s.ctx.__wrap();s.ctx.addPayout('crew_existing');for(let i=0;i<20&&s.writes().length===0;i++)await new Promise(r=>setTimeout(r,1));await new Promise(r=>setTimeout(r,2));assert.equal(s.remote.payments.unseen.amount,30);assert.equal(s.remote.payments.additional_payment.amount,10);assert.equal(s.remote.completion.paidTotal,40);results.push('Normal payout mirroring preserves unseen remote payments and updates a closed balance.');
 s=setup(inv,[linked([{id:'unseen',...inv.payments.unseen},{id:'to_delete',amount:20,date:'2026-10-11',method:'Готовина'}])]);s.remote.payments.to_delete={amount:20,date:'2026-10-11',method:'Готовина'};s.ctx.__wrap();s.ctx.deletePayment('crew','to_delete','crew_existing');for(let i=0;i<20&&s.writes().length===0;i++)await new Promise(r=>setTimeout(r,1));await new Promise(r=>setTimeout(r,2));assert.equal(s.remote.payments.to_delete,undefined);assert.equal(s.remote.payments.unseen.amount,30);assert.equal(s.remote.completion.paidTotal,30);results.push('Explicit payment removal deletes only that entry and retains other cloud history.');
 s=setup();s.ctx.__wrap();s.finance.crew.push({...c,completion:{status:'closed',closedBy:'admin'}});s.ctx.drawFinance();assert.match(s.crewRows[0].innerHTML,/Затворен од TD/);assert.match(s.crewRows[0].innerHTML,/Уреди исплатено/);results.push('The finance card exposes the new action and clearly labels admin closure.');
 console.log(JSON.stringify({syntaxScripts:scripts.length,passed:results.length,results},null,2));
})().catch(e=>{console.error(e);process.exitCode=1;});
