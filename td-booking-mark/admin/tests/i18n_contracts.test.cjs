'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const vm=require('node:vm');
const assets=path.join(__dirname,'../src/main/assets');
const source=fs.readFileSync(path.join(assets,'booking_i18n_v620.js'),'utf8');
const html=fs.readFileSync(path.join(assets,'index.html'),'utf8');
new vm.Script(source,{filename:'booking_i18n_v620.js'});
let selection='en';
const fakeWindow={addEventListener(){}};
const fakeDocument={readyState:'loading',addEventListener(){},getElementById(id){return {value:''}}};
const defaultWedding=html.match(/<textarea[^>]*id="terms"[^>]*>([\s\S]*?)<\/textarea>/)[1].replace(/\\n/g,'\n');
const sample={id:'ABCD1234-XYZ',provider:'TD Production',providerSigner:'John Smith',bride:'Jane',groom:'Jack',
 phone:'070000000',date:'2026-11-01',venue:'Ohrid',start:'12:30',eventType:'wedding',
 package:'0',services:'One camera',price:'800',basePrice:'800',deposit:'100',discountValue:'0',due:'2026-12-01',
 delivery:'2027-01-01',terms:defaultWedding,notes:'',locked:true,signedAt:'2026-11-02T12:00:00Z',
 sigProvider:'data:image/png;base64,AA',sigClient1:'data:image/png;base64,BB',sigClient2:'data:image/png;base64,CC'};
const payments={crew:[{name:'James',fee:200,payments:[{amount:50}]}],express:[{amount:30}],clientPayments:[{amount:100}],expenses:[{amount:20}],extras:[]};
const context={
 window:fakeWindow,document:fakeDocument,
 localStorage:{getItem(){return selection}},
 navigator:{language:'mk-MK'},
 PACKAGES:[['Essential',800,'One camera']],
 packageName:()=>'Essential',
 data:[sample],
 finRecord:()=>payments,
 totalContracted:(b)=>Number(b.price),
 totalClient:(b,f)=>Number(b.deposit)+f.clientPayments.reduce((s,p)=>s+p.amount,0),
 expressReceived:f=>f.express.reduce((s,e)=>s+e.amount,0),
 crewPaid:c=>c.payments.reduce((s,p)=>s+p.amount,0),
 expenseTotal:f=>f.expenses.reduce((s,p)=>s+p.amount,0),
 totalDue:(b,f)=>Number(b.price)-(Number(b.deposit)+f.clientPayments.reduce((s,p)=>s+p.amount,0)),
 staffGroups:()=>[{name:'James',events:[sample],fee:200,paid:50}],
 console
};
vm.runInNewContext(source,context,{filename:'booking_i18n_v620.js'});
const app=fakeWindow.MarKBookingLanguages;
assert(app&&typeof app.contract==='function'&&typeof app.financialReport==='function');
assert.deepEqual(Object.keys(app.languages),['mk','en','sq','tr']);
for(const [key,value] of Object.entries(app.ui)){
 assert.equal(value.length,3,'Missing language variants for '+key);
 for(const [index,phrase] of value.entries())assert(typeof phrase==='string'&&phrase.trim().length,'Blank translation of '+key+' index '+index);
}
// Static HTML audit: every displayed Cyrillic label must have a dictionary entry.
const staticHtml=html.split(/<script\b[^>]*>/i)[0]
 .replace(/<style[\s\S]*?<\/style>/gi,'').replace(/<textarea[\s\S]*?<\/textarea>/gi,'');
const labels=[...new Set([...staticHtml.matchAll(/>([^<>]{1,400})</g)]
 .map(x=>x[1].replace(/&nbsp;/g,' ').replace(/\s+/g,' ').trim())
 .filter(x=>/[\u0400-\u052f]/.test(x)))];
const missing=labels.filter(v=>!app.ui[v]&&!app.ui[v.replace(/^[^\p{L}]*/u,'').trim()]);
assert.deepEqual(missing,[],'Untranslated static page labels: '+missing.join(' / '));
// Every language must generate a full signed wedding contract with the same booking data.
for(const lang of ['mk','en','sq','tr']){
 selection=lang;
 assert.equal(app.language(),lang);
 const contract=app.contract(sample,lang);
 assert(contract.includes('Jane')&&contract.includes('Jack')&&contract.includes('800'),'Data missing in '+lang);
 assert(contract.includes(sample.sigProvider)&&contract.includes(sample.sigClient2),'Signed image missing in '+lang);
 if(lang!=='mk'){
  assert(!contract.includes('Изведувачот ги обезбедува'),'Untranslated standard terms '+lang);
  assert(!contract.includes('Услови на договорот'),'Untranslated heading '+lang);
 }
 const report=app.financialReport(lang);
 for(const amount of ['800.00','200.00','50.00','30.00','20.00']){
  assert(report.includes(amount),'Financial amount '+amount+' missing in '+lang);
 }
 assert(report.includes('James'),'Team member missing in report '+lang);
 assert(app.translateMessage('Пополнете ги полињата означени со *.').length>0);
 if(lang!=='mk')assert(!app.translateMessage('Пополнете ги полињата означени со *.').includes('Пополнете'),'Dialog not localized '+lang);
}
console.log('PASS: all '+labels.length+' static labels have entries; 4 languages, signatures, full financial report and dialog text verified.');