const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {start,fixture,INITIAL,FINANCE}=require('./browser_harness.cjs');
const passed=[];
async function test(name,fn){await fn();passed.push(name);console.log('PASS',name);}
(async()=>{
 const browser=await start();let f;
 try {
  f=await fixture(browser);const p=f.page;
  const route=()=>p.evaluate(()=>TDPages.route());
  const clickTab=tab=>p.locator('#tdWeddingContext [data-wedding-tab="'+tab+'"]').click();
  await test('Starts on the wedding calendar; sync is contained only in Settings',async()=>{
   assert.equal((await route()).page,'calendar');assert.ok(await p.locator('#overview').isVisible());
   assert.equal(await p.locator('#marKFullSyncPanel').isVisible(),false);
   assert.equal(await p.locator('#marKFullSyncPanel').evaluate(e=>e.parentElement.id),'tdSyncPage');
   assert.equal(await p.locator('#bottomNav button').count(),5);
   for(const[k,v]of Object.entries(INITIAL))assert.equal((await f.storage())[k],v);
  });
  await test('Calendar shows every wedding on a shared date',async()=>{
   await p.locator('[data-day="2026-10-20"]').click();
   assert.equal(await p.locator('#tdDayBookings [data-wedding]').count(),2);
   assert.equal(await p.locator('[data-day="2026-10-20"] .td-calendar-dot').innerText(),'2');
  });
  await test('Wedding overview separates client payments, Express and staff fees',async()=>{
   await p.locator('#tdDayBookings [data-wedding="w-alpha"]').click();
   assert.equal((await route()).id,'w-alpha');assert.ok(await p.locator('#tdWeddingOverview').isVisible());
   const amounts=await p.locator('#tdWeddingOverview .td-metric strong').allTextContents();
   assert.deepEqual(amounts,['1000.00 €','300.00 €','700.00 €','500.00 €','100.00 €','400.00 €']);
   assert.match(await p.locator('.td-express-note').innerText(),/150.00/);
  });
  await test('Wedding payment page keeps the exact selected booking and hides global totals',async()=>{
   await clickTab('money');assert.equal(await p.locator('#finWedding').inputValue(),'w-alpha');
   assert.equal(await p.locator('#globalSummary').isVisible(),false);assert.ok(await p.locator('#clientAmount').isVisible());
   assert.equal(await p.locator('#expressAmount').isVisible(),false);
   await p.locator('[data-finance-tab="express"]').click();assert.ok(await p.locator('#expressAmount').isVisible());assert.equal(await p.locator('#clientAmount').isVisible(),false);
   await p.locator('[data-finance-tab="client"]').click();
  });
  await test('Payment goes only to the chosen wedding and overview updates',async()=>{
   await p.locator('#clientAmount').fill('250');await p.locator('#clientDate').fill('2026-10-19');
   await p.getByRole('button',{name:'+ Забележи уплата',exact:true}).click();
   const fin=JSON.parse((await f.storage()).tdBookingFinanceV1);
   assert.equal(fin['w-alpha'].clientPayments.length,2);assert.equal(fin['w-alpha'].clientPayments[1].amount,250);
   assert.deepEqual(fin['w-beta'],FINANCE['w-beta']);assert.equal(fin['w-alpha'].express[0].amount,150);
   await clickTab('overview');assert.equal(await p.locator('#tdWeddingOverview .td-metric strong').nth(2).innerText(),'450.00 €');
  });
  await test('Staff tab retains existing crew and creates payment on the same wedding',async()=>{
   await clickTab('crew');assert.ok(await p.locator('#crewList').isVisible());assert.equal(await p.locator('#clientAmount').isVisible(),false);
   await p.evaluate(()=>showPayout('crew-a'));await p.locator('#pAmount').fill('80');
   await p.getByRole('button',{name:'Зачувај исплата',exact:true}).click();
   const fin=JSON.parse((await f.storage()).tdBookingFinanceV1);
   assert.equal(fin['w-alpha'].crew[0].payments.length,2);assert.equal(fin['w-alpha'].crew[0].payments[1].amount,80);
   assert.deepEqual(fin['w-beta'],FINANCE['w-beta']);
  });
  await test('Receipts open as their own page and return to the same wedding tab',async()=>{
   await clickTab('money');await p.evaluate(()=>clientReceipt('deposit'));
   assert.equal((await route()).page,'document');assert.ok(await p.locator('#financePrint').isVisible());assert.equal(await p.locator('#finance').isVisible(),false);
   await p.locator('[data-action="close-document"]').click();assert.equal((await route()).id,'w-alpha');assert.equal((await route()).tab,'money');
  });
  await test('Signed contract retains original signatures and has no edit action',async()=>{
   await p.evaluate(()=>TDPages.openWedding('w-signed'));assert.equal(await p.locator('[data-action="edit-wedding"]').count(),0);
   await clickTab('contract');assert.ok(await p.locator('#printCard').isVisible());assert.equal(await p.locator('#editor').isVisible(),false);
   const b=JSON.parse((await f.storage()).tdBookingsV1).find(x=>x.id==='w-signed');const old=JSON.parse(INITIAL.tdBookingsV1).find(x=>x.id==='w-signed');assert.deepEqual(b,old);
  });
  await test('Settings exposes sync, backups, package prices, branding, Cloud and reports',async()=>{
   await p.locator('#bottomNav [data-route="settings"]').click();assert.ok(await p.locator('#tdPageSettings').isVisible());
   assert.equal(await p.locator('#tdPageSettings .td-settings-card').count(),6);assert.equal(await p.locator('#marKFullSyncPanel').isVisible(),false);
   await p.locator('[data-action="brand"]').click();assert.ok(await p.locator('#markBrandSettings').isVisible());
   assert.equal(await p.locator('#markBrandName').inputValue(),'Мое студио');await p.locator('#markBrandClose').click();
  });
  await test('Moved synchronization and pairing controls retain their original handlers',async()=>{
   await p.locator('#tdPageSettings [data-route="sync"]').click();assert.ok(await p.locator('#marKFullSyncPanel').isVisible());
   await p.locator('#marKFullSyncNow').click();await p.waitForFunction(()=>window.__calls.some(x=>x.type==='full-sync'));
   await p.locator('#tdMarkURL').fill('https://192.168.1.10:8766');await p.locator('#tdMarkFP').fill('a'.repeat(64));await p.locator('#tdMarkPair').click();
   assert.ok(await p.evaluate(()=>window.__calls.some(x=>x.type==='pair'&&x.args[0]==='https://192.168.1.10:8766')));
  });
  await test('Backup Save As still exports current weddings, finances and signatures',async()=>{
   await p.evaluate(()=>navigate('backup'));await p.locator('#tdBackupSaveFile').click();
   const text=await p.evaluate(()=>window.__calls.findLast(x=>x.type==='backup').s),backup=JSON.parse(text);
   assert.equal(backup.bookings.length,4);assert.equal(backup.finance['w-alpha'].clientPayments.length,2);assert.ok(backup.bookings.find(b=>b.id==='w-signed').sigProvider);
  });
  await test('Package prices and local/Cloud staff pages remain reachable',async()=>{
   await p.evaluate(()=>navigate('packages'));assert.ok(await p.locator('#settingsBody').isVisible());assert.ok(await p.locator('#settingPrice0').isVisible());
   await p.locator('#bottomNav [data-route="staff"]').click();assert.ok(await p.locator('#tdManualCrew').isVisible());assert.equal(await p.locator('#tdTeamPanel').isVisible(),false);
   await p.locator('[data-staff-tab="cloud"]').click();assert.ok(await p.locator('#tdTeamPanel').isVisible());assert.equal(await p.locator('#tdManualCrew').isVisible(),false);
  });
  await test('Wedding search includes venue/phone and status filtering',async()=>{
   await p.locator('#bottomNav [data-route="weddings"]').click();await p.locator('#searchWedding').fill('Елена');assert.equal(await p.locator('#bookings [data-wedding]').count(),1);
   await p.locator('#searchWedding').fill('');await p.locator('#tdWeddingStatus').selectOption('Откажана');assert.equal(await p.locator('#bookings [data-wedding]').count(),1);
   await p.locator('#tdWeddingStatus').selectOption('');await p.locator('#searchWedding').fill('070000000');assert.equal(await p.locator('#bookings [data-wedding]').count(),4);await p.locator('#searchWedding').fill('');
  });
  await test('Unsaved edits block leaving until the user chooses to discard',async()=>{
   await p.locator('#bookings [data-wedding="w-alpha"]').click();await p.locator('[data-action="edit-wedding"]').click();
   await p.locator('#bride').fill('Незачувано');await p.locator('#bottomNav [data-route="settings"]').click();assert.equal((await route()).page,'editor');assert.equal(await p.locator('#bride').inputValue(),'Незачувано');
   await p.evaluate(()=>window.__confirm=true);await p.locator('#bottomNav [data-route="settings"]').click();assert.equal((await route()).page,'settings');
   assert.equal(JSON.parse((await f.storage()).tdBookingsV1).find(b=>b.id==='w-alpha').bride,'Ана');await p.evaluate(()=>window.__confirm=false);
  });
  await test('Editing and saving a booking preserves its ID, finances and other weddings',async()=>{
   await p.evaluate(()=>TDPages.openWedding('w-alpha'));await p.locator('[data-action="edit-wedding"]').click();await p.locator('#bride').fill('Ана изменета');
   await p.evaluate(()=>window.__confirm=true); // The original app asks about the second wedding on this date.
   await p.locator('#editor').getByRole('button',{name:'Зачувај нацрт',exact:true}).click();assert.equal(JSON.parse((await f.storage()).tdBookingsV1).find(b=>b.id==='w-alpha').bride,'Ана изменета');
   await p.locator('[data-action="close-editor"]').click();assert.equal((await route()).id,'w-alpha');assert.match(await p.locator('#tdWeddingContext h2').innerText(),/изменета/);
  });
  await test('New wedding on a selected day uses that date and preserves all previous records',async()=>{
   await p.locator('#bottomNav [data-route="calendar"]').click();await p.locator('[data-day="2026-10-20"]').click();await p.locator('[data-action="new-on-day"]').click();assert.equal(await p.locator('#date').inputValue(),'2026-10-20');
   await p.locator('#bride').fill('Нова');await p.locator('#groom').fill('Свадба');await p.locator('#phone').fill('070000002');await p.locator('[data-tab="price"]').click();await p.locator('#package').selectOption('custom');await p.locator('#services').fill('Фото');await p.locator('#basePrice').fill('800');await p.locator('[data-tab="contract"]').click();await p.locator('#providerSigner').fill('Админ');
   await p.evaluate(()=>window.__confirm=true);await p.locator('#editor').getByRole('button',{name:'Зачувај нацрт',exact:true}).click();
   const saved=JSON.parse((await f.storage()).tdBookingsV1);assert.equal(saved.length,5);assert.equal(saved.find(b=>b.bride==='Нова').date,'2026-10-20');
   assert.equal(saved.find(b=>b.id==='w-alpha').bride,'Ана изменета');assert.equal(saved.find(b=>b.id==='w-signed').locked,true);await p.locator('[data-action="close-editor"]').click();
  });
  await test('Draft contract signing and locking still save all three signatures',async()=>{
   await clickTab('contract');await p.waitForTimeout(70);
   await p.evaluate(()=>{for(const id of ['sigProvider','sigClient1','sigClient2'])document.getElementById(id).getContext('2d').fillRect(5,5,25,10);finalize();});
   const b=JSON.parse((await f.storage()).tdBookingsV1).find(b=>b.bride==='Нова');assert.equal(b.locked,true);assert.ok(b.sigProvider.startsWith('data:image/png'));assert.ok(b.sigClient1);assert.ok(b.sigClient2);
   await clickTab('overview');assert.equal((await route()).tab,'overview');assert.equal(await p.locator('[data-action="edit-wedding"]').count(),0);
  });
  await test('Month navigation handles leap years and January rollover',async()=>{
   await p.locator('#bottomNav [data-route="calendar"]').click();await p.evaluate(()=>shiftMonth(16));assert.equal(await p.locator('#calendar [data-day]').count(),29);assert.equal(await p.locator('[data-day="2028-02-29"]').count(),1);
   await p.evaluate(()=>shiftMonth(-13));assert.equal(await p.locator('[data-day="2027-01-31"]').count(),1);await p.evaluate(()=>shiftMonth(-1));assert.equal(await p.locator('[data-day="2026-12-31"]').count(),1);
   await p.locator('[data-action="today"]').click();
  });
  await test('Calendar, wedding overview and settings fit small and large screens',async()=>{
   for(const width of [320,360,412,800]){
    await p.setViewportSize({width,height:915});
    for(const page of ['calendar','settings']){await p.evaluate(page=>navigate(page),page);assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth),page+' at '+width);}
    await p.evaluate(()=>TDPages.openWedding('w-alpha'));assert.ok(await p.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth),'profile at '+width);
   }
   await p.setViewportSize({width:412,height:915});await p.screenshot({path:path.resolve(__dirname,'../qa/wedding.png'),fullPage:true});await p.evaluate(()=>navigate('settings'));await p.screenshot({path:path.resolve(__dirname,'../qa/settings.png'),fullPage:true});await p.evaluate(()=>navigate('calendar'));await p.screenshot({path:path.resolve(__dirname,'../qa/calendar.png'),fullPage:true});
  });
  await test('Reports keep period controls and original PDF printing',async()=>{
   await p.evaluate(()=>navigate('reports'));assert.ok(await p.locator('#rangeFrom').isVisible());await p.evaluate(()=>printReport());await p.waitForTimeout(150);
   assert.ok(await p.evaluate(()=>window.__calls.some(c=>c.type==='print')));assert.equal((await route()).page,'report-document');
   await p.evaluate(()=>closeReportPrint());assert.equal((await route()).page,'reports');
  });
  await test('All rendered IDs remain unique and no script errors occurred',async()=>{
   const duplicates=await p.evaluate(()=>{const seen=new Set(),dupes=[];for(const e of document.querySelectorAll('[id]')){if(seen.has(e.id))dupes.push(e.id);seen.add(e.id);}return dupes;});assert.deepEqual(duplicates,[]);assert.deepEqual(f.errors,[]);assert.deepEqual(f.external,[]);
  });
  await f.close(); f=await fixture(browser);
  await test('Back navigation restores the selected calendar day',async()=>{
   await f.page.locator('[data-day="2026-10-20"]').click();await f.page.locator('#tdDayBookings [data-wedding="w-alpha"]').click();await f.page.goBack();
   await f.page.waitForFunction(()=>TDPages.route().page==='calendar');assert.equal(await f.page.locator('[data-day="2026-10-20"]').getAttribute('aria-pressed'),'true');
  });
  await f.close(); f=await fixture(browser,{initial:{tdBookingsV1:'[]',tdBookingFinanceV1:'{}'}});
  await test('An empty installation starts cleanly and can open a new booking',async()=>{
   assert.equal(await f.page.locator('#tdDayBookings [data-wedding]').count(),0);await f.page.locator('#overview [data-action="new"]').click();assert.equal(await f.page.evaluate(()=>TDPages.route().page),'editor');assert.ok(await f.page.locator('#bride').isVisible());assert.deepEqual(f.errors,[]);
  });
  await f.close();f=await fixture(browser,{file:true});
  await test('Local file mode used by Android supports page history and return to the calendar',async()=>{
   assert.ok(await f.page.evaluate(()=>history.state?.tdBookingPagesV620));
   await f.page.locator('[data-day="2026-10-20"]').click();await f.page.locator('#tdDayBookings [data-wedding="w-beta"]').click();await f.page.goBack();
   await f.page.waitForFunction(()=>TDPages.route().page==='calendar');assert.equal(await f.page.locator('[data-day="2026-10-20"]').getAttribute('aria-pressed'),'true');assert.deepEqual(f.errors,[]);
  });
  const report={passed:passed.length,tests:passed,engine:'Headless Chromium '+browser.version()+'; desktop browser simulating Android WebView with native bridge mocks',physicalAndroidDeviceTested:false};
  fs.writeFileSync(path.resolve(__dirname,'../qa/PAGES_TEST_RESULTS.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
 }catch(e){console.error(e.stack);if(f){console.error('SCRIPT_ERRORS',f.errors);await f.page.screenshot({path:path.resolve(__dirname,'../qa/failure.png'),fullPage:true}).catch(()=>{});}process.exitCode=1;}
 finally{await f?.close();await browser.close();}
})();
