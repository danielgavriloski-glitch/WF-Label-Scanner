/* TD Booking 6.18: independent offline JSON export; no Firebase required.
   Native Storage Access Framework handles Android Save As; never overwrites app data.
*/
(()=>{'use strict';
 const root=document.getElementById('backupView');
 if(!root)return;
 const target=document.getElementById('backupText');
 const btn=document.createElement('button');btn.type='button';btn.id='tdBackupSaveFile';
 btn.textContent='💾 Зачувај Backup.json во телефон';
 btn.style.cssText='display:block;width:100%;margin:12px 0;background:#09685e;color:#fff';
 const info=document.createElement('p');info.id='tdBackupExportStatus';
 info.style.cssText='white-space:pre-wrap;font-size:13px;color:#205a50';
 const before=target&&target.parentNode===root?target:root.querySelector('textarea');
 if(before)root.insertBefore(btn,before);else root.append(btn);
 btn.insertAdjacentElement('afterend',info);
 const text=(msg,bad=false)=>{info.textContent=msg;info.style.color=bad?'#9a342d':'#205a50'};
 const read=(key,fallback)=>{const raw=localStorage.getItem(key);return raw===null?fallback:JSON.parse(raw)};
 const pack=()=>{
   const bookings=typeof data!=='undefined'&&Array.isArray(data)?data:read('tdBookingsV1',[]);
   const accounts=typeof finance!=='undefined'&&finance&&typeof finance==='object'?finance:read('tdBookingFinanceV1',{});
   if(!Array.isArray(bookings)||!accounts||Array.isArray(accounts)||typeof accounts!=='object')
     throw Error('Неправилни настани или финансиски податоци — ништо не е извезено');
   const packages=read('tdPackageSettings',[]);
   const obj={format:'TD-Booking-backup',version:3,exported:new Date().toISOString(),
      bookings,finance:accounts,packageSettings:packages};
   const output=JSON.stringify(obj,null,2);
   if(output.length>16*1024*1024)throw Error('Копијата е преголема за овој извоз');
   if(!bookings.length)throw Error('Не се пронајдени настани. Нема да извезам празна копија без твоја потврда.');
   return {output,count:bookings.length,finCount:Object.keys(accounts).length};
 };
 btn.addEventListener('click',()=>{
   try{
     const data=pack();if(target)target.value=data.output;
     text('Подготвени: '+data.count+' настани и '+data.finCount+' финансиски записи. Избери место за зачувување…');
     if(window.TDNative&&typeof window.TDNative.saveBackupJson==='function'){
       window.TDNative.saveBackupJson(data.output);
     }else{
       text('Android Save As не е достапен во оваа APK. Текстот е подготвен под копчето; копирај го во приватна датотека. Ова не е зачуван фајл.',true);
     }
   }catch(e){text('ГРЕШКА: '+e.message,true)}
 });
 window.tdNativeBackupResult=(result)=>{
    text(result&&result.message?result.message:'Не е добиена потврда за зачувување.',!(result&&result.ok));
 };
 window.tdVerifyBackup=()=>{const o=pack();return {weddings:o.count,financeRecords:o.finCount}};
})();
