const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');
const ASSETS = path.resolve(__dirname,'../assets');
const booking = (id,bride,groom,date,extra={}) => ({id,bride,groom,date,phone:'070000000',venue:'Охрид',start:'14:00',package:'custom',price:'1000',basePrice:'1000',discountType:'percent',discountValue:'0',deposit:'200',services:'Фото и видео',status:'Резервирана',provider:'TD Production',providerSigner:'Администратор',terms:'Услови од договорот',due:'2026-10-30',delivery:'30 дена',notes:'Оригинална забелешка',locked:false,...extra});
const BOOKINGS=[booking('w-alpha','Ана','Петар','2026-10-20'),booking('w-beta','Елена','Мартин','2026-10-20',{start:'17:00'}),booking('w-signed','Марија','Кирил','2026-10-11',{locked:true,signedAt:'2026-10-11T11:00:00Z',sigProvider:'data:image/png;base64,iVBORw0KGgo=',sigClient1:'data:image/png;base64,iVBORw0KGgo=',sigClient2:'data:image/png;base64,iVBORw0KGgo='}),booking('w-cancelled','Откажана','Свадба','2026-10-23',{status:'Откажана'})];
const FINANCE=Object.fromEntries(BOOKINGS.map(b=>[b.id,{clientPayments:[],crew:[],extras:[],express:[],expenses:[]}]))
FINANCE['w-alpha']={clientPayments:[{id:'pay-old',amount:100,date:'2026-10-10',method:'Готовина',note:'Оригинална уплата'}],crew:[{id:'crew-a',name:'Иван',phone:'070000001',role:'Фотограф',fee:500,tasks:'Фотографирање',payments:[{id:'paid-old',amount:100,date:'2026-10-10',method:'Готовина'}]}],extras:[],express:[{id:'express-old',amount:150,date:'2026-10-10',note:'Експрес'}],expenses:[]};
const INITIAL={'tdBookingsV1':JSON.stringify(BOOKINGS),'tdBookingFinanceV1':JSON.stringify(FINANCE),'tdMarKBrandSettings_v1':JSON.stringify({name:'Мое студио',accent:'#167a74',gold:'#be9858',logo:''})};
async function start() { const local=process.env.TD_TEST_BROWSER||path.resolve(__dirname,'../qa/browser/chrome-headless-shell-linux64/chrome-headless-shell');return chromium.launch({headless:true,executablePath:fs.existsSync(local)?local:undefined,args:['--no-sandbox']}); }
async function fixture(browser, options={}) {
 const context=await browser.newContext({viewport:{width:412,height:915},deviceScaleFactor:1,timezoneId:'Europe/Skopje'});
 const page=await context.newPage(), errors=[], external=[];
 page.on('pageerror',e=>errors.push(e.message));
 await context.route('**/*',async r=>{
   const url=new URL(r.request().url());
   if(url.protocol==='file:')return r.continue();
   if(url.hostname!=='td-booking.local'){external.push(url.hostname);await r.abort();return;}
   const name=url.pathname==='/'?'index.html':decodeURIComponent(url.pathname.slice(1));
   if(name.includes('..'))return r.abort();
   const f=path.join(ASSETS,name);
   if(!fs.existsSync(f))return r.fulfill({status:404,body:'Not found'});
   const ext=path.extname(f),type={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.png':'image/png'}[ext]||'application/octet-stream';
   return r.fulfill({status:200,contentType:type,body:fs.readFileSync(f)});
 });
 await context.addInitScript(({initial,options})=>{
   const RealDate=Date; const fixed=RealDate.parse('2026-10-19T08:30:00+02:00');
   class FixedDate extends RealDate {constructor(...args){super(...(args.length?args:[fixed]));}static now(){return fixed;}}
   window.Date=FixedDate;
   if(!sessionStorage.getItem('fixture-seeded')){for(const[k,v]of Object.entries(initial))localStorage.setItem(k,v);sessionStorage.setItem('fixture-seeded','yes');}
   window.__calls=[];window.__alerts=[];window.__confirm=false;
   window.alert=m=>window.__alerts.push(m);window.confirm=()=>window.__confirm;
   window.TDNative={
     marKInfo:()=>JSON.stringify({paired:false,endpoint:'https://fixture.invalid:8766'}),
     marKPair:(...args)=>window.__calls.push({type:'pair',args}),
     marKConfirm:()=>window.__calls.push({type:'pair-confirm'}),
     marKDisconnect:()=>window.__calls.push({type:'disconnect'}),
     marKSync:s=>window.__calls.push({type:'old-sync',s}),
     marKReplica:s=>{window.__calls.push({type:'full-sync',s});setTimeout(()=>window.tdMarKReplicaResult({ok:true,protocol:2,snapshot:{bookings:{},finance:{},settings:{},roster:{}},applied:[],conflicts:[]}),1);},
     saveBackupJson:s=>{window.__calls.push({type:'backup',s});},
     printContract:()=>window.__calls.push({type:'print'}),
     syncWedding:s=>window.__calls.push({type:'calendar-sync',s}),notify:()=>{}
   };
   window.TDShortcut={isSupported:()=>true,pinShortcut:(...args)=>{window.__calls.push({type:'pin',args});window.TDBrandShortcutResult?.('requested','Потврди Додај');},updateShortcut:(...args)=>window.__calls.push({type:'update-icon',args})};
 },{initial:{...INITIAL,...options.initial},options});
 await page.goto(options.file?'file://'+path.join(ASSETS,'index.html'):'http://td-booking.local/index.html',{waitUntil:'load'});
 await page.waitForFunction(()=>window.TDPages?.route().page==='calendar',{timeout:12000});
 await page.waitForTimeout(30);
 return {page,context,errors,external,close:()=>context.close(),storage:()=>page.evaluate(()=>({...localStorage}))};
}
module.exports={start,fixture,INITIAL,BOOKINGS,FINANCE};
