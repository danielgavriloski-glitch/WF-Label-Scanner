/* MarK Booking 6.20 — independent localization and multilingual printable copies.
 * No changes to booking, finance, sync or signature storage.
 * User supplied free-text is NEVER silently machine-translated.
 */
(function () {
'use strict';
const LANGS={mk:'Македонски',en:'English',sq:'Shqip',tr:'Türkçe'};
const K='markBookingLocale_v620';
const UI={
'Календар':['Calendar','Kalendari','Takvim'],
'Календар на настани':['Events calendar','Kalendari i ngjarjeve','Etkinlik takvimi'],
'Пребарај настан':['Search events','Kërko ngjarje','Etkinlik ara'],
'Претходен':['Previous','Mëparshëm','Önceki'],
'Следен':['Next','Tjetër','Sonraki'],
'Избери датум за да ги видиш сите закажани настани.':['Select a date to see scheduled events.','Zgjidh një datë për të parë ngjarjet.','Planlanan etkinlikleri görmek için tarih seçin.'],
'Нова резервација':['New booking','Rezervim i ri','Yeni rezervasyon'],
'Додај настан':['Add event','Shto ngjarje','Etkinlik ekle'],
'Сите настани':['All events','Të gjitha ngjarjet','Tüm etkinlikler'],
'Резервација и договор':['Booking and agreement','Rezervimi dhe kontrata','Rezervasyon ve sözleşme'],
'Почетна':['Home','Kryefaqja','Ana sayfa'],
'Преглед':['Overview','Përmbledhje','Genel bakış'],
'Свадби':['Weddings','Dasmat','Düğünler'],
'Свадби и календар':['Weddings and calendar','Dasmat dhe kalendari','Düğünler ve takvim'],
'Пари':['Finance','Financat','Finans'],
'Финансии':['Finance','Financa','Finans'],
'Финансии и екипа':['Finance and team','Financat dhe ekipi','Finans ve ekip'],
'Финансии по свадба':['Wedding finances','Financat e dasmës','Düğün finansmanı'],
'Екипа':['Team','Ekipi','Ekip'],
'Извештаи':['Reports','Raportet','Raporlar'],
'Мои пакети':['My packages','Paketat e mia','Paketlerim'],
'Резервна копија':['Backup','Kopje rezervë','Yedekleme'],
'Затвори':['Close','Mbyll','Kapat'],
'Назад':['Back','Kthehu','Geri'],
'Назад на календар':['Back to calendar','Kthehu te kalendari','Takvime dön'],
'Зачувај ценовник':['Save prices','Ruaj çmimet','Fiyatları kaydet'],
'Договор':['Agreement','Kontratë','Sözleşme'],
'Договор и уредување':['Agreement and edit','Kontrata dhe redaktimi','Sözleşme ve düzenleme'],
'Избриши':['Delete','Fshij','Sil'],
'Уреди':['Edit','Redakto','Düzenle'],
'Датум':['Date','Data','Tarih'],
'До датум':['To date','Deri më','Bitiş tarihi'],
'Од датум':['From date','Nga data','Başlangıç tarihi'],
'Почеток':['Start','Fillimi','Başlangıç'],
'Крај':['End','Fundi','Bitiş'],
'Телефон':['Phone','Telefoni','Telefon'],
'Невеста':['Bride','Nusja','Gelin'],
'Младоженец':['Groom','Dhëndri','Damat'],
'Клиент':['Client','Klienti','Müşteri'],
'Фотосесија':['Photo session','Fotosesion','Fotoğraf çekimi'],
'Крштевка':['Baptism','Pagëzim','Vaftiz'],
'Сунет':['Circumcision celebration','Syneti','Sünnet'],
'Свадба':['Wedding','Dasmë','Düğün'],
'Друг настан':['Other event','Ngjarje tjetër','Diğer etkinlik'],
'Потпишан':['Signed','Nënshkruar','İmzalandı'],
'Нацрт':['Draft','Draft','Taslak'],
'Вид на настан':['Event type','Lloji i ngjarjes','Etkinlik türü'],
'Износ (€)':['Amount (€)','Shuma (€)','Tutar (€)'],
'Начин':['Method','Mënyra','Yöntem'],
'Готовина':['Cash','Para në dorë','Nakit'],
'Трансфер':['Transfer','Transfer','Havale'],
'Друго':['Other','Tjetër','Diğer'],
'Забелешка':['Note','Shënim','Not'],
'Дополнителни услуги':['Additional services','Shërbime shtesë','Ek hizmetler'],
'Други трошоци':['Other expenses','Shpenzime të tjera','Diğer giderler'],
'Назив на услуга':['Service name','Emri i shërbimit','Hizmet adı'],
'Цена (€)':['Price (€)','Çmimi (€)','Fiyat (€)'],
'Веќе наплатено (€)':['Already received (€)','Tashmë paguar (€)','Alınan ödeme (€)'],
'Сите датуми':['All dates','Të gjitha datat','Tüm tarihler'],
'Зачувај PDF / печати':['Save PDF / print','Ruaj PDF / printo','PDF kaydet / yazdır'],
'Печати / зачувај PDF':['Print / save PDF','Printo / ruaj PDF','Yazdır / PDF kaydet'],
'Потврди и заклучи договор':['Sign and lock agreement','Nënshkruaj dhe blloko kontratën','Sözleşmeyi imzala ve kilitle'],
'Договор за соработник':['Team member agreement','Kontrata e bashkëpunëtorit','Ekip üyesi sözleşmesi'],
'Хонорар (€) *':['Fee (€) *','Honorari (€) *','Ücret (€) *'],
'Улога *':['Role *','Roli *','Görev *'],
'Име и презиме *':['Full name *','Emri dhe mbiemri *','Ad ve soyad *'],
'Зачувај ангажман':['Save engagement','Ruaj angazhimin','Görevi kaydet'],
'Откажи':['Cancel','Anulo','İptal'],
'Зачувај потпишан договор':['Save signed agreement','Ruaj kontratën e nënshkruar','İmzalı sözleşmeyi kaydet'],
'Потврда':['Receipt','Vërtetim','Makbuz'],
'Потврда за исплата':['Payment receipt','Vërtetim pagese','Ödeme makbuzu'],
'Синхронизација со MarK':['Sync with MarK','Sinkronizimi me MarK','MarK ile senkronizasyon'],
'Експрес':['Express','Express','Ekspres'],
'Уплати':['Payments','Pagesat','Ödemeler'],
'Трошоци':['Expenses','Shpenzimet','Giderler'],
'Доплати / Експрес':['Extras / Express','Shtesa / Express','Ek ücretler / Ekspres'],
'Датум на свадба *':['Wedding date *','Data e dasmës *','Düğün tarihi *'],
'Датум на настан *':['Event date *','Data e ngjarjes *','Etkinlik tarihi *'],
'Рок за исплата':['Payment due date','Afati i pagesës','Ödeme vadesi'],
'Рок за испорака':['Delivery deadline','Afati i dorëzimit','Teslim tarihi'],
'Место':['Venue','Vendi','Mekân'],
'Преглед и потписи':['Preview and signatures','Shikimi dhe nënshkrimet','Önizleme ve imzalar'],
'Посебен пакет':['Custom package','Paket i veçantë','Özel paket'],
'Избери пакет':['Choose package','Zgjidh paketën','Paket seç'],
'Статус на свадба':['Wedding status','Statusi i dasmës','Düğün durumu'],
'Статус на настан':['Event status','Statusi i ngjarjes','Etkinlik durumu']
};
const DOC={
mk:{title:'ДОГОВОР ЗА ФОТО И ВИДЕО УСЛУГИ',no:'Број',date:'Датум на договор',provider:'Изведувач',clients:'Клиенти',contact:'Контакт',event:'Настан',start:'Почеток',venue:'Место',subject:'Предмет и пакет',gift:'Гратис',price:'Цена и испорака',base:'Цена пред попуст',discount:'Попуст',total:'Вкупна цена',deposit:'Капар',balance:'Остаток',due:'Рок за остаток',delivery:'Рок за испорака',terms:'Услови',notes:'Дополнителни договори и измени',accept:'Со потписите, страните го прифаќаат текстот прикажан погоре.',signed:'Потпишано',signature:'потпис',bride:'Невеста',groom:'Младоженец',client:'Клиент',agreement:'Договор за ангажман',receipt:'Потврда за примена уплата',payout:'Потврда за исплата',express:'Потврда за Експрес фотографии',amount:'Износ',method:'Начин',received:'Вкупно примено',owed:'За наплата',services:'Дополнителни услуги',fee:'Хонорар',paid:'Вкупно исплатено',role:'Улога',tasks:'Задачи и опрема',handoff:'Предавање материјал',paymentDate:'Датум на исплата',employee:'Соработник',conditions:'Посебни услови',copies:'Јазични копии',custom:'Следниот слободно внесен текст е зачуван во оригиналниот јазик. Проверете го преводот пред правна употреба.',report:'Финансиски извештај',period:'Период',partner:'Екипа',events:'Настани',original:'Оригинален документ',copy:'Јазична копија'},
en:{title:'PHOTOGRAPHY AND VIDEOGRAPHY SERVICES AGREEMENT',no:'Number',date:'Agreement date',provider:'Service provider',clients:'Clients',contact:'Contact',event:'Event',start:'Start time',venue:'Venue',subject:'Services and package',gift:'Complimentary',price:'Price and delivery',base:'Price before discount',discount:'Discount',total:'Total price',deposit:'Deposit',balance:'Balance due',due:'Balance due date',delivery:'Delivery deadline',terms:'Terms and conditions',notes:'Additional agreements and changes',accept:'By signing, the parties accept the agreement as presented above.',signed:'Signed',signature:'signature',bride:'Bride',groom:'Groom',client:'Client',agreement:'ENGAGEMENT AGREEMENT',receipt:'PAYMENT RECEIPT',payout:'PAYOUT RECEIPT',express:'EXPRESS PHOTO SALES RECEIPT',amount:'Amount',method:'Method',received:'Total received',owed:'Outstanding balance',services:'Additional services',fee:'Fee',paid:'Total paid',role:'Role',tasks:'Tasks and equipment',handoff:'Material handover',paymentDate:'Payment date',employee:'Team member',conditions:'Special conditions',copies:'Language copies',custom:'User-supplied text remains in its original language. Have it professionally translated and reviewed before legal use.',report:'Financial report',period:'Period',partner:'Team',events:'Events',original:'Original document',copy:'Translated copy'},
sq:{title:'KONTRATË PËR SHËRBIME FOTOGRAFIE DHE VIDEOJE',no:'Numri',date:'Data e kontratës',provider:'Ofruesi i shërbimit',clients:'Klientët',contact:'Kontakti',event:'Ngjarja',start:'Fillimi',venue:'Vendi',subject:'Shërbimet dhe paketa',gift:'Falas',price:'Çmimi dhe dorëzimi',base:'Çmimi para zbritjes',discount:'Zbritja',total:'Çmimi total',deposit:'Parapagimi',balance:'Mbetja për pagesë',due:'Afati i pagesës',delivery:'Afati i dorëzimit',terms:'Kushtet',notes:'Marrëveshje dhe ndryshime shtesë',accept:'Me nënshkrim, palët pranojnë kushtet e paraqitura më sipër.',signed:'Nënshkruar',signature:'nënshkrimi',bride:'Nusja',groom:'Dhëndri',client:'Klienti',agreement:'KONTRATË ANGAZHIMI',receipt:'VËRTETIM PËR PRANIM PAGese',payout:'VËRTETIM PAGESE',express:'VËRTETIM PËR FOTOT EXPRESS',amount:'Shuma',method:'Mënyra',received:'Gjithsej të pranuara',owed:'Mbetja për pagesë',services:'Shërbime shtesë',fee:'Honorari',paid:'Gjithsej të paguara',role:'Roli',tasks:'Detyrat dhe pajisjet',handoff:'Dorëzimi i materialit',paymentDate:'Data e pagesës',employee:'Bashkëpunëtori',conditions:'Kushte të veçanta',copies:'Kopje në gjuhë të tjera',custom:'Teksti i shkruar nga përdoruesi mbetet në gjuhën origjinale. Kërkohet përkthim dhe verifikim profesional para përdorimit juridik.',report:'Raporti financiar',period:'Periudha',partner:'Ekipi',events:'Ngjarjet',original:'Dokumenti origjinal',copy:'Kopje e përkthyer'},
tr:{title:'FOTOĞRAF VE VİDEO HİZMET SÖZLEŞMESİ',no:'Numara',date:'Sözleşme tarihi',provider:'Hizmet sağlayıcı',clients:'Müşteriler',contact:'İletişim',event:'Etkinlik',start:'Başlangıç',venue:'Mekân',subject:'Hizmetler ve paket',gift:'Ücretsiz',price:'Fiyat ve teslimat',base:'İndirimsiz fiyat',discount:'İndirim',total:'Toplam fiyat',deposit:'Kapora',balance:'Kalan bakiye',due:'Son ödeme tarihi',delivery:'Teslim tarihi',terms:'Şartlar ve koşullar',notes:'Ek anlaşmalar ve değişiklikler',accept:'Taraflar imzalayarak yukarıdaki sözleşmeyi kabul ederler.',signed:'İmzalandı',signature:'imza',bride:'Gelin',groom:'Damat',client:'Müşteri',agreement:'GÖREVLENDİRME SÖZLEŞMESİ',receipt:'ÖDEME MAKBUZU',payout:'ÜCRET ÖDEME MAKBUZU',express:'EKSPRES FOTOĞRAF SATIŞ MAKBUZU',amount:'Tutar',method:'Yöntem',received:'Toplam alınan',owed:'Kalan alacak',services:'Ek hizmetler',fee:'Ücret',paid:'Toplam ödenen',role:'Görev',tasks:'Görevler ve ekipman',handoff:'Materyal teslimi',paymentDate:'Ödeme tarihi',employee:'Ekip üyesi',conditions:'Özel şartlar',copies:'Dil kopyaları',custom:'Kullanıcı tarafından girilen metin orijinal dilinde kalır. Hukuki kullanımdan önce profesyonel çeviri ve kontrol gereklidir.',report:'Mali rapor',period:'Dönem',partner:'Ekip',events:'Etkinlikler',original:'Orijinal belge',copy:'Çevrilmiş kopya'}
};
const $=id=>document.getElementById(id);
const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const nl=s=>esc(s).replace(/\r?\n/g,'<br>');
const safeDate=s=>esc(s||'—');
const effective=()=>{let x=localStorage.getItem(K)||'auto';if(x==='auto'){let n=(navigator.language||'mk').split('-')[0].toLowerCase();return LANGS[n]?n:'mk';}return LANGS[x]?x:'mk'};
let currentDoc=null,uiTimer=null,observer=null;
function uiText(node,lang) {
 let raw=node.__markSource;
 if(raw===undefined){raw=node.nodeValue;node.__markSource=raw;}
 const cleaned=raw.trim(),idx=['en','sq','tr'].indexOf(lang);
 let lead=cleaned.replace(/^[^\p{L}]*/u,'').trim(),prefix=cleaned.slice(0,cleaned.indexOf(lead));
 let phrase=UI[cleaned]||UI[lead];
 if(phrase)node.nodeValue=raw.replace(cleaned,prefix+(idx<0?lead:phrase[idx]));
 else if(node.nodeValue!==raw)node.nodeValue=raw;
}
function translateUi() {
 const lang=effective();document.documentElement.lang=lang;
 if(observer)observer.disconnect();
 const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
 let node;while((node=walker.nextNode())){
  const par=node.parentElement;if(!par||par.closest('script,style,textarea,[contenteditable],#contractText,#financeDocument,#reportDocument,#markMultiPrintArea,#markLanguagePanel,#markContractLanguages,#markFinanceLanguages,#markReportLanguages'))continue;
  if(node.nodeValue.trim())uiText(node,lang);
 }
 document.querySelectorAll('input[placeholder]').forEach(el=>{if(!el.dataset.markOriginalPlaceholder)el.dataset.markOriginalPlaceholder=el.placeholder;let original=el.dataset.markOriginalPlaceholder;el.placeholder=original;});
 const opt=$('markUiLanguage');if(opt)opt.value=localStorage.getItem(K)||'auto';
 const month=$('monthTitle');if(month&&typeof window.month!=='undefined'&&month instanceof HTMLElement){try{month.textContent=window.month.toLocaleDateString(lang+'-'+(lang==='mk'?'MK':lang==='sq'?'AL':lang==='tr'?'TR':'GB'),{month:'long',year:'numeric'})}catch(_e){}}
 if(observer)observer.observe(document.body,{childList:true,subtree:true,characterData:true});
}
function scheduleUi(){if(uiTimer)clearTimeout(uiTimer);uiTimer=setTimeout(translateUi,80)}
function languagesPanel(id,target){
 const pane=document.createElement('div');pane.id=id;pane.className='mark-languages no-print';pane.innerHTML='<label class="mark-primary-label">Јазик на документот / Document language</label><select class="mark-primary"></select><div class="mark-extra-title">Дополнителни копии / Additional copies</div><div class="mark-extra"></div><button type="button" class="mark-generate">Создади PDF на избраните јазици</button><p class="mark-disclaimer muted">Рачно внесените услуги и посебни услови остануваат на оригиналниот јазик; проверете ги пред потпишување.</p>';
 const sel=pane.querySelector('.mark-primary'),extra=pane.querySelector('.mark-extra');
 Object.keys(LANGS).forEach(l=>{let o=document.createElement('option');o.value=l;o.textContent=LANGS[l];sel.appendChild(o);let label=document.createElement('label');label.className='mark-check';let cb=document.createElement('input');cb.type='checkbox';cb.value=l;label.append(cb,document.createTextNode(LANGS[l]));extra.appendChild(label)});
 sel.value=effective();sel.onchange=()=>syncExtra(pane);
 pane.querySelector('.mark-generate').onclick=()=>printCopies(pane.id);
 target.insertAdjacentElement('beforebegin',pane);syncExtra(pane);
 return pane;
}
function syncExtra(pane){const l=pane.querySelector('select').value;pane.querySelectorAll('input[type=checkbox]').forEach(c=>{c.disabled=c.value===l;if(c.disabled)c.checked=false})}
function selectedLanguages(pane){return [...new Set([pane.querySelector('select').value,...[...pane.querySelectorAll('input:checked')].map(x=>x.value)])].filter(l=>LANGS[l])}
function labelRow(label,value){return '<p><b>'+esc(label)+':</b> '+value+'</p>'}
const TERMS={
mk: '1. Изведувачот ги обезбедува договорените фото и видео услуги на наведениот датум и локација.\n2. Клиентот обезбедува навремен пристап и го потврдува распоредот пред настанот.\n3. Цената, капарот, плаќањето и рокот за испорака се наведени во овој договор.\n4. Промените на датумот, услугите или цената се договараат писмено од двете страни.\n5. Дополнителните услови и барања се внесуваат пред потпишување.',
en:'1. The provider will deliver the agreed photo and video services on the stated date and at the stated venue.\n2. The client will ensure timely access and confirm the schedule before the event.\n3. The price, deposit, payment arrangements and delivery date are specified in this agreement.\n4. Changes to date, services or price require written agreement by both parties.\n5. Additional conditions and requests must be recorded before signing.',
sq:'1. Ofruesi siguron shërbimet e dakorduara të fotografimit dhe videos në datën dhe vendin e shënuar.\n2. Klienti siguron qasje në kohë dhe konfirmon orarin para ngjarjes.\n3. Çmimi, parapagimi, pagesat dhe afati i dorëzimit përcaktohen në këtë kontratë.\n4. Ndryshimet në datë, shërbime ose çmim kërkojnë marrëveshje me shkrim nga të dyja palët.\n5. Kushtet dhe kërkesat shtesë regjistrohen para nënshkrimit.',
tr:'1. Hizmet sağlayıcı, belirtilen tarihte ve yerde kararlaştırılan fotoğraf ve video hizmetlerini sunacaktır.\n2. Müşteri etkinlikten önce zamanında erişim sağlayacak ve programı onaylayacaktır.\n3. Fiyat, kapora, ödeme düzeni ve teslim tarihi bu sözleşmede belirtilmiştir.\n4. Tarih, hizmet veya fiyat değişiklikleri için iki tarafın yazılı anlaşması gerekir.\n5. Ek koşullar ve talepler imzalamadan önce kaydedilir.'
};

const WEDDING_TERMS={
mk:'1. Изведувачот ги обезбедува услугите наведени во овој договор за датумот и локациите наведени погоре.\n2. Клиентите обезбедуваат навремен пристап до договорените локации и го потврдуваат распоредот пред свадбата.\n3. Цената, капарот, рокот за остатокот и рокот за испорака се наведени во договорот.\n4. Секоја промена во пакетот, датумот, локацијата, цената или рокот се потврдува писмено од двете страни.\n5. Условите за откажување, користење на фотографиите и видеото и сите посебни барања се внесуваат во „Дополнителни договори и измени“ пред потпис.',
en:'1. The provider delivers the agreed services on the specified date and at the specified locations.\n2. The clients ensure timely access to the agreed locations and confirm the schedule before the wedding.\n3. The price, deposit, balance deadline and delivery deadline are stated in this agreement.\n4. Changes to the package, date, venue, price or deadline must be confirmed in writing by both parties.\n5. Cancellation terms, use of photos and video, and special requests must be recorded under Additional agreements and changes before signing.',
sq:'1. Ofruesi siguron shërbimet e dakorduara në datën dhe vendet e përcaktuara më sipër.\n2. Klientët sigurojnë qasje në kohë në vendet e dakorduara dhe konfirmojnë orarin para dasmës.\n3. Çmimi, parapagimi, afati i pagesës së mbetur dhe afati i dorëzimit janë shënuar në këtë kontratë.\n4. Çdo ndryshim i paketës, datës, vendit, çmimit ose afatit konfirmohet me shkrim nga të dyja palët.\n5. Kushtet e anulimit, përdorimi i fotove dhe videos dhe kërkesat e veçanta shënohen te Marrëveshje dhe ndryshime shtesë para nënshkrimit.',
tr:'1. Hizmet sağlayıcı, yukarıda belirtilen tarih ve yerlerde kararlaştırılan hizmetleri sunar.\n2. Müşteriler kararlaştırılan yerlere zamanında erişim sağlar ve düğün programını önceden onaylar.\n3. Fiyat, kapora, kalan ödeme tarihi ve teslim tarihi bu sözleşmede belirtilir.\n4. Paket, tarih, mekân, fiyat veya süre değişiklikleri her iki tarafça yazılı olarak onaylanır.\n5. İptal şartları, fotoğraf ve videoların kullanımı ile özel talepler imza öncesinde Ek anlaşmalar ve değişiklikler bölümüne yazılır.'
};
const SERVICE_DESCS={
en:[
'One Full HD camera; photography coverage; on-day photo session; 50 printed 13 × 18 cm photos; digital delivery.',
'One Full HD camera; two photographers (the second starts at the bride); photo session; 50 printed 13 × 18 cm photos; digital delivery.',
'Main Full HD camera; second camera with crane at the hotel; drone where conditions permit; two photographers; photo session; 50 printed 13 × 18 cm photos; digital delivery.',
'Main Full HD camera; second camera with crane; drone where conditions permit; two photographers; photo session; 2–3 minute Best Moments video; 50 printed 13 × 18 cm photos; digital delivery.',
'Main Full HD camera; second camera with crane; drone where conditions permit; two photographers; photo session; Love Story on a separate day with at least two hours with the couple; 2–3 minute film; 50 printed 13 × 18 cm photos; digital delivery.',
'Main and crane cameras in 4K UHD; drone where conditions permit; two photographers; photo session; 2–3 minute Best Moments 4K video; 50 printed 13 × 18 cm photos; digital delivery.',
'4K UHD video; crane camera; drone where conditions permit; two photographers; photo session; custom Love Story on a separate day with at least two hours with the couple; 2–3 minute film; 50 printed 13 × 18 cm photos; digital delivery.'],
sq:[
'Një kamerë Full HD; mbulim fotografik; fotosesion ditën e ngjarjes; 50 foto të printuara 13 × 18 cm; dorëzim digjital.',
'Një kamerë Full HD; dy fotografë (i dyti fillon te nusja); fotosesion; 50 foto të printuara 13 × 18 cm; dorëzim digjital.',
'Kamera kryesore Full HD; kamera e dytë me kran në hotel; dron kur kushtet e lejojnë; dy fotografë; fotosesion; 50 foto të printuara 13 × 18 cm; dorëzim digjital.',
'Kamera kryesore Full HD; kamera e dytë me kran; dron kur kushtet e lejojnë; dy fotografë; fotosesion; video Best Moments 2–3 minuta; 50 foto të printuara 13 × 18 cm; dorëzim digjital.',
'Kamera kryesore Full HD; kamera e dytë me kran; dron kur kushtet e lejojnë; dy fotografë; fotosesion; Love Story në ditë të veçantë, së paku dy orë me çiftin; film 2–3 minuta; 50 foto të printuara 13 × 18 cm; dorëzim digjital.',
'Kamera kryesore dhe kamera me kran në 4K UHD; dron kur kushtet e lejojnë; dy fotografë; fotosesion; video Best Moments 4K 2–3 minuta; 50 foto të printuara 13 × 18 cm; dorëzim digjital.',
'Video 4K UHD; kamerë me kran; dron kur kushtet e lejojnë; dy fotografë; fotosesion; Love Story e personalizuar në ditë të veçantë, së paku dy orë me çiftin; film 2–3 minuta; 50 foto të printuara 13 × 18 cm; dorëzim digjital.'],
tr:[
'Bir Full HD kamera; fotoğraf çekimi; etkinlik günü fotoğraf oturumu; 50 adet 13 × 18 cm baskı fotoğraf; dijital teslimat.',
'Bir Full HD kamera; iki fotoğrafçı (ikinci fotoğrafçı gelinin yanında başlar); fotoğraf oturumu; 50 adet 13 × 18 cm baskı; dijital teslimat.',
'Ana Full HD kamera; otelde vinçli ikinci kamera; koşullar uygunsa drone; iki fotoğrafçı; fotoğraf oturumu; 50 adet 13 × 18 cm baskı; dijital teslimat.',
'Ana Full HD kamera; vinçli ikinci kamera; koşullar uygunsa drone; iki fotoğrafçı; fotoğraf oturumu; 2–3 dakikalık Best Moments videosu; 50 adet 13 × 18 cm baskı; dijital teslimat.',
'Ana Full HD kamera; vinçli ikinci kamera; koşullar uygunsa drone; iki fotoğrafçı; fotoğraf oturumu; çiftle ayrı bir günde en az iki saat Love Story çekimi; 2–3 dakikalık film; 50 adet 13 × 18 cm baskı; dijital teslimat.',
'4K UHD ana ve vinçli kameralar; koşullar uygunsa drone; iki fotoğrafçı; fotoğraf oturumu; 2–3 dakikalık Best Moments 4K videosu; 50 adet 13 × 18 cm baskı; dijital teslimat.',
'4K UHD video; vinçli kamera; koşullar uygunsa drone; iki fotoğrafçı; fotoğraf oturumu; çiftle ayrı bir günde en az iki saat kişiselleştirilmiş Love Story; 2–3 dakikalık film; 50 adet 13 × 18 cm baskı; dijital teslimat.']
};
function bookingContract(b,lang){
 const d=DOC[lang],w=!b.eventType||b.eventType==='wedding',rest=(Number(b.price)||0)-(Number(b.deposit)||0);
 const terms=String(b.terms||''),isGeneral=terms.trim()===TERMS.mk.trim();
 const isWeddingDefault=terms.trim()===WEDDING_TERMS.mk.trim();
 const humanTerms=isGeneral?nl(TERMS[lang]):isWeddingDefault?nl(WEDDING_TERMS[lang]):nl(terms);
 let x='<div class="pdf-brand"><strong>'+esc(b.provider||'TD Production')+'</strong><br>'+esc(d.title)+'</div>';
 x+=labelRow(d.no,esc(String(b.id||'').slice(0,8).toUpperCase()))+labelRow(d.date,esc(b.signedAt?new Date(b.signedAt).toLocaleDateString(lang):new Date().toLocaleDateString(lang)));
 x+=labelRow(d.provider,esc(b.provider)+' · '+esc(b.providerSigner));
 x+=labelRow(d.clients,esc(w?(b.bride||'')+' & '+(b.groom||''):b.bride));
 x+=labelRow(d.contact,esc(b.phone));
 x+=labelRow(d.event,safeDate(b.date)) +labelRow(d.start,safeDate(b.start))+labelRow(d.venue,safeDate(b.venue));
 x+='<h3>'+esc(d.subject)+'</h3>'+labelRow(esc(typeof packageName==='function'?packageName(b.package):'Package'),nl((lang!=='mk'&&typeof PACKAGES!=='undefined'&&PACKAGES[Number(b.package)]&&String(b.services||'').trim()===String(PACKAGES[Number(b.package)][2]||'').trim())?SERVICE_DESCS[lang][Number(b.package)]:b.services));
 if(b.giftNotes)x+=labelRow(d.gift,nl(b.giftNotes));
 x+='<h3>'+esc(d.price)+'</h3>';
 if(b.basePrice!==undefined)x+=labelRow(d.base,safeDate(b.basePrice)+' €')+labelRow(d.discount,esc(b.discountValue||0)+(b.discountType==='amount'?' €':' %'));
 x+=labelRow(d.total,safeDate(b.price)+' €')+labelRow(d.deposit,safeDate(b.deposit||0)+' €')+labelRow(d.balance,esc(rest.toFixed(2))+' €');
 x+=labelRow(d.due,safeDate(b.due))+labelRow(d.delivery,safeDate(b.delivery));
 x+='<h3>'+esc(d.terms)+'</h3><p>'+humanTerms+'</p>';
 if(lang!=='mk'&&!isGeneral&&!isWeddingDefault)x+='<p class="mark-translation-warning">'+esc(d.custom)+'</p>';
 x+='<h3>'+esc(d.notes)+'</h3><p>'+nl(b.notes||'—')+'</p><p>'+esc(d.accept)+'</p>';
 if(b.locked){x+='<p><b>'+esc(d.signed)+':</b> '+safeDate(b.signedAt)+'</p><div class="mark-signatures">';
 const sigs=[[d.provider,b.providerSigner,b.sigProvider],[w?d.bride:d.client,b.bride,b.sigClient1]];
 if(w)sigs.push([d.groom,b.groom,b.sigClient2]);
 sigs.forEach(z=>{x+='<div>'+ (z[2]?'<img src="'+esc(z[2])+'" alt="Signature">':'')+'<p><b>'+esc(z[1])+'</b><br>'+esc(z[0])+' · '+esc(d.signature)+'</p></div>'});
 x+='</div>';}
 return x;
}
function money(x){return Number(x||0).toFixed(2)+' €'}
function financeDoc(lang) {
 const d=DOC[lang],c=currentDoc;
 if(!c||!c.w)return null;
 let b=c.w,f=c.f||{},p=c.payment,co=c.co,kind=c.kind;
 let x='<div class="pdf-brand"><strong>'+esc(b.provider||'TD Production')+'</strong><br>'+esc(kind==='client'?d.receipt:kind==='crewReceipt'?d.payout:kind==='express'?d.express:d.agreement)+'</div>';
 x+=labelRow(d.event,esc(b.bride||'')+(b.groom?' & '+esc(b.groom):'')+' · '+safeDate(b.date));
 x+=labelRow(d.provider,esc(b.provider||'TD Production'));
 if(kind==='client'){if(!p)return null;x+=labelRow(d.amount,money(p.amount))+labelRow(d.paymentDate,safeDate(p.date))+labelRow(d.method,esc(p.method||'—'));
  x+=labelRow(d.total,money(b.price))+labelRow(d.services,money(typeof extraBilled==='function'?extraBilled(f):0))+labelRow(d.received,money(typeof totalClient==='function'?totalClient(b,f):0));
 }else if(kind==='crewReceipt'){if(!p||!co)return null;x+=labelRow(d.employee,esc(co.name))+labelRow(d.role,esc(co.role))+labelRow(d.amount,money(p.amount))+labelRow(d.paymentDate,safeDate(p.date))+labelRow(d.method,esc(p.method||'—'))+labelRow(d.fee,money(co.fee));
 }else if(kind==='express'){if(!p)return null;x+=labelRow(d.amount,money(p.amount))+labelRow(d.paymentDate,safeDate(p.date))+labelRow(d.services,esc(p.note||'—'));
 }else if(kind==='crewContract'){if(!co)return null;x+=labelRow(d.employee,esc(co.name))+labelRow(d.role,esc(co.role))+labelRow(d.fee,money(co.fee))+labelRow(d.start,safeDate(co.start))+labelRow(d.due,safeDate(co.due))+labelRow(d.tasks,nl(co.tasks||'—'))+labelRow(d.handoff,nl(co.handoff||'—'))+labelRow(d.conditions,nl(co.notes||'—'));if(lang!=='mk')x+='<p class="mark-translation-warning">'+esc(d.custom)+'</p>';
 }
 if(p&&p.note)x+=labelRow(d.notes,nl(p.note));
 const signs=[b.providerSigner||b.provider||'TD Production',co?co.name:(kind==='client'?b.bride:'')];
 x+='<div class="mark-signatures">'+signs.map(v=>'<div><p><b>'+esc(v)+'</b><br>'+esc(d.signature)+'</p></div>').join('')+'</div>';
 if(co&&co.signedContract){x+='<p>'+esc(d.signed)+': '+safeDate(co.signedContract.signedAt)+'</p>';
 const sig=co.signedContract;if(sig.provider&&sig.crew)x+='<div class="mark-signatures"><img src="'+esc(sig.provider)+'" alt="Signature"><img src="'+esc(sig.crew)+'" alt="Signature"></div>';
 }
 return x;
}
function financialReport(lang){
 const d=DOC[lang],a=$('rangeFrom')?.value||'',z=$('rangeTo')?.value||'';
 const items=(typeof data!=='undefined'?data:[]).filter(b=>(!a||b.date>=a)&&(!z||b.date<=z)).sort((x,y)=>x.date.localeCompare(y.date));
 const th=[d.date,d.event,d.total,d.received,d.owed];
 let out='<div class="pdf-brand"><strong>TD Production</strong></div><h2>'+esc(d.report)+'</h2><p>'+esc(d.period)+': '+safeDate(a)+' – '+safeDate(z)+'</p><table class="mark-report-table"><thead><tr>'+th.map(t=>'<th>'+esc(t)+'</th>').join('')+'</tr></thead><tbody>';
 items.forEach(b=>{const f=(typeof finRecord==='function'?finRecord(b.id):{})||{},received=typeof totalClient==='function'?totalClient(b,f):0,base=typeof totalContracted==='function'?totalContracted(b,f):Number(b.price||0);
 out+='<tr><td>'+esc(b.date)+'</td><td>'+esc(b.bride||'')+' '+esc(b.groom||'')+'</td><td>'+money(base)+'</td><td>'+money(received)+'</td><td>'+money(Math.max(0,base-received))+'</td></tr>'});
 return out+'</tbody></table>';
}
function printCopies(id){
 const pane=$(id);if(!pane)return;
 const selected=selectedLanguages(pane),parts=[];
 for(const lang of selected){
 let body=null;
 if(id==='markContractLanguages'){const b=typeof data!=='undefined'&&data.find(x=>x.id===current);if(!b){alert('Нема избран договор.');return}body=bookingContract(b,lang)}
 if(id==='markFinanceLanguages')body=financeDoc(lang);
 if(id==='markReportLanguages')body=financialReport(lang);
 if(!body){alert('Прво отвори го документот.');return;}
 parts.push('<article class="mark-page" lang="'+lang+'"><div class="mark-page-label">'+esc(LANGS[lang])+' — '+esc(lang==='mk'?DOC[lang].original:DOC[lang].copy)+'</div>'+body+'</article>');
 }
 let area=$('markMultiPrintArea');if(!area){area=document.createElement('div');area.id='markMultiPrintArea';document.body.appendChild(area)}
 area.innerHTML=parts.join('');document.body.classList.add('mark-multiprint');
 if(window.TDNative&&typeof window.TDNative.printContract==='function')window.TDNative.printContract();else window.print();
}
window.addEventListener('afterprint',()=>{document.body.classList.remove('mark-multiprint')});
function install(){
 if(!document.body||!$('drawer'))return;
 const css=document.createElement('style');css.textContent='.mark-languages{background:#eef5f2;border:1px solid #b4d1c7;border-radius:12px;padding:12px;margin:12px 0;color:#23423d}.mark-languages select{max-width:300px}.mark-extra{display:flex;flex-wrap:wrap;gap:8px}.mark-check{display:flex;align-items:center;gap:6px;margin:6px}.mark-check input{width:20px;min-height:20px}.mark-extra-title{font-weight:700;margin-top:8px}.mark-translation-warning{font-size:12px;border-left:3px solid #b78a4e;padding:7px;background:#fff9ec}.mark-signatures{display:flex;gap:20px;justify-content:space-between;margin-top:24px;page-break-inside:avoid}.mark-signatures>div{min-width:26%;border-top:1px solid #789;padding-top:8px}.mark-signatures img{max-width:140px;max-height:70px}.mark-report-table{width:100%;border-collapse:collapse}.mark-report-table th,.mark-report-table td{border:1px solid #aaa;padding:6px}.mark-page{padding:10mm;background:#fff;color:#222;page-break-after:always}.mark-page-label{font-size:11px;color:#444;margin:0 0 16px}@media print{body.mark-multiprint>*:not(#markMultiPrintArea){display:none!important}body.mark-multiprint #markMultiPrintArea{display:block!important;position:static!important}body.mark-multiprint .mark-page{page-break-after:always}body.mark-multiprint{background:white}#markMultiPrintArea{display:none}}';
 document.head.appendChild(css);
 const settings=$('settingsView');if(settings){const panel=document.createElement('div');panel.id='markLanguagePanel';panel.className='pricebox';panel.innerHTML='<h3>Јазик / Language / Gjuha / Dil</h3><label for="markUiLanguage">Јазик на апликацијата</label><select id="markUiLanguage"><option value="auto">Автоматски — јазик на телефонот</option><option value="mk">Македонски</option><option value="en">English</option><option value="sq">Shqip</option><option value="tr">Türkçe</option></select><p class="muted">Јазикот на документите се избира посебно при создавање PDF.</p>';settings.insertBefore(panel,settings.firstChild);$('markUiLanguage').value=localStorage.getItem(K)||'auto';$('markUiLanguage').onchange=e=>{localStorage.setItem(K,e.target.value);translateUi()};}
 const drawer=$('drawer');const menu=document.createElement('button');menu.className='secondary';menu.textContent='🌐 Јазик / Language';menu.onclick=()=>{if(typeof window.navigate==='function')window.navigate('settings');$('markUiLanguage')?.scrollIntoView({block:'center'})};drawer.insertBefore(menu,drawer.lastElementChild);
 if($('contractText'))languagesPanel('markContractLanguages',$('contractText'));
 if($('financeDocument'))languagesPanel('markFinanceLanguages',$('financeDocument'));
 if($('reportBody'))languagesPanel('markReportLanguages',$('reportBody'));
 const selected=()=>{
  try{const b=typeof selectedWedding==='function'?selectedWedding():null;return b?JSON.parse(JSON.stringify(b)):null}catch(_e){return null}
 };
 const finance=(b)=>{try{return b&&typeof finRecord==='function'?JSON.parse(JSON.stringify(finRecord(b.id))):null}catch(_e){return null}};
 function wrap(name,resolve){const prev=window[name];if(typeof prev!=='function')return;window[name]=function(){const args=[...arguments];const b=selected();const f=finance(b);currentDoc={kind:name==='clientReceipt'?'client':name==='crewReceipt'?'crewReceipt':name==='expressReceipt'?'express':'crewContract',w:b,f};
  if(b&&f){if(name==='clientReceipt')currentDoc.payment=args[0]==='deposit'?{amount:b.deposit,date:b.date,method:'Deposit'}:(f.clientPayments||[]).find(p=>p.id===args[0]);if(name==='crewReceipt'){currentDoc.co=(f.crew||[]).find(c=>c.id===args[0]);currentDoc.payment=currentDoc.co?.payments?.find(p=>p.id===args[1]);}if(name==='expressReceipt')currentDoc.payment=(f.express||[]).find(p=>p.id===args[0]);if(name==='crewContract')currentDoc.co=(f.crew||[]).find(c=>c.id===args[0]);}
  const result=prev.apply(this,args);const panel=$('markFinanceLanguages');if(panel)panel.querySelector('select').value=effective();if(panel)syncExtra(panel);return result;
 }}
 ['clientReceipt','crewReceipt','expressReceipt','crewContract'].forEach(w=>wrap(w));
 const originalShow=window.showContract;
 if(typeof originalShow==='function')window.showContract=function(b){const r=originalShow.apply(this,arguments);const pane=$('markContractLanguages');if(pane){pane.querySelector('select').value=effective();syncExtra(pane)}scheduleUi();return r};
 observer=new MutationObserver(scheduleUi);observer.observe(document.body,{childList:true,subtree:true,characterData:true});
 translateUi();
}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});else install();
window.MarKBookingLanguages={languages:LANGS,language:effective,contract:bookingContract,financialReport};
})();