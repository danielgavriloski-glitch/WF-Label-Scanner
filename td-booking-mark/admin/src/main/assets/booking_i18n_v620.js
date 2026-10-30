/* MarK Booking 6.20 — independent localization and multilingual printable copies.
 * No changes to booking, finance, sync or signature storage.
 * User supplied free-text is NEVER silently machine-translated.
 */
(function () {
'use strict';
const LANGS={mk:'Македонски',en:'English',sq:'Shqip',tr:'Türkçe'};
const K='markBookingLocale_v620';
const UI={
"Со потписите, страните го прифаќаат текстот прикажан погоре.":["By signing, the parties accept the text above.","Me nënshkrim, palët pranojnë tekstin e mësipërm.","Taraflar imzalayarak yukarıdaki metni kabul ederler."],
"Охрид":["Ohrid","Ohër","Ohri"],
"Исплатено екипа":["Team payments","Pagesat e ekipit","Ekip ödemeleri"],
"Име":["Name","Emri","Ad"],
"Ангажмани":["Engagements","Angazhime","Görevler"],
"По договорените хонорари и внесените трошоци.":["After agreed fees and recorded expenses.","Pas honorareve të dakorduara dhe shpenzimeve të regjistruara.","Anlaşılan ücretler ve kaydedilen giderlerden sonra."],
"Вклучи Anonymous Authentication и Realtime Database. Во изворниот пакет има":["Enable Anonymous Authentication and Realtime Database. The source package includes","Aktivizoni Anonymous Authentication dhe Realtime Database. Paketa burimore përmban","Anonymous Authentication ve Realtime Database'i etkinleştirin. Kaynak paketinde"],
"со правилата што треба да се постават во Database → Rules.":["with rules to add under Database → Rules.","me rregullat që duhen vendosur te Database → Rules.","Database → Rules bölümüne eklenecek kurallar vardır."],
"Вработениот ја отвора Екипа → внесува код → име → свој PIN.":["Employee opens Team → enters code → name → own PIN.","Punonjësi hap Ekipi → shkruan kodin → emrin → PIN-in e vet.","Çalışan Ekip'i açar → kodu girer → adını girer → kendi PIN'ini belirler."],
"не менува UID, договори, исплати или историја. На следното отворање вработениот ќе постави нов PIN.":["does not change UID, agreements, payouts or history. The employee sets a new PIN when reopening.","nuk ndryshon UID, kontrata, pagesa apo historikun. Punonjësi vendos PIN të ri kur hap aplikacionin.","UID, sözleşme, ödeme veya geçmişi değiştirmez. Çalışan sonraki açılışta yeni PIN belirler."],
"кодот е приватен како лозинка. Испрати го само на тој член од екипата.":["this code is private like a password. Send it only to that team member.","kodi është privat si fjalëkalimi. Dërgojani vetëm anëtarit përkatës.","kod bir parola gibi özeldir. Yalnızca ilgili ekip üyesine gönderin."],
"Со потврдата се прифаќа оваа цена и договорот се испраќа во Екипа за потпис.":["Confirming accepts this price and sends the agreement to Team for signing.","Konfirmimi pranon këtë çmim dhe dërgon kontratën te Ekipi për nënshkrim.","Onay ile bu fiyat kabul edilir ve sözleşme imza için Ekip'e gönderilir."],
"Колегата ќе ја добие твојата цена во Екипа и ќе може да ја прифати или да ја одбие.":["Your team member will receive your offer in Team and can accept or reject it.","Kolegu do ta marrë ofertën tuaj te Ekipi dhe mund ta pranojë ose refuzojë.","Ekip üyesi teklifinizi Ekip'te alır ve kabul ya da reddedebilir."],
"Вкупно профили":["Total profiles","Gjithsej profile","Toplam profil"],
"Покани":["Invitations","Ftesat","Davetler"],
"Чекаат твоја потврда":["Awaiting your confirmation","Presin konfirmimin tuaj","Onayınızı bekleyenler"],
"Затворени од TD":["Closed by TD","Mbyllur nga TD","TD tarafından kapatıldı"],
"Хонорар (€)":["Fee (€)","Honorari (€)","Ücret (€)"],
"Улоги за ангажманот":["Engagement roles","Rolet e angazhimit","Görev rolleri"],
"Вкупно исплатено досега (€)":["Total paid to date (€)","Gjithsej paguar deri tani (€)","Bugüne kadar toplam ödenen (€)"],
"Датум на евиденција":["Record date","Data e regjistrimit","Kayıt tarihi"],
"Забелешка (по желба)":["Note (optional)","Shënim (opsionale)","Not (isteğe bağlı)"],
"Со зачувување го затвораш ангажманот веднаш, без потврда од колегата. Остатокот за исплата останува во евиденцијата.":["Saving closes this engagement immediately without team member confirmation. Remaining payment stays on record.","Ruajtja e mbyll menjëherë angazhimin pa konfirmim të kolegut. Pagesa e mbetur mbetet në regjistër.","Kaydetmek, ekip üyesinin onayı olmadan görevi hemen kapatır. Kalan ödeme kayıtlarda kalır."],
"Освежи форма":["Refresh form","Rifresko formularin","Formu yenile"],
"Затворен од TD":["Closed by TD","Mbyllur nga TD","TD tarafından kapatıldı"],
"TD договор потпишан":["TD agreement signed","Kontrata TD e nënshkruar","TD sözleşmesi imzalandı"],
"TD договор во тек":["TD agreement in progress","Kontrata TD në proces","TD sözleşmesi işlemde"],
"Експресот е приход плус. Наплатата и долгот од клиентите се водат одделно.":["Express sales are additional income. Client payments and outstanding balances are tracked separately.","Shitjet Express janë të ardhura shtesë. Pagesat dhe detyrimet e klientëve mbahen veçmas.","Ekspres satışlar ek gelirdir. Müşteri ödemeleri ve borçları ayrı izlenir."],
"Избери свадба":["Select wedding","Zgjidh dasmën","Düğün seç"],
"Сликање со гости":["Guest photo sales","Fotografi me mysafirë","Misafir fotoğrafı satışı"],
"Наплати доплата":["Collect extra payment","Arkëto pagesë shtesë","Ek ücreti tahsil et"],
"Доплатата е наплатена":["Extra payment received","Pagesa shtesë u pranua","Ek ödeme alındı"],
"Отстрани доплата":["Remove extra payment","Hiq pagesën shtesë","Ek ödemeyi kaldır"],
"Ако младенците одбијат сликање со гости, означи го тука. Се додаваат 300 € за наплата од клиентите.":["If the couple declines guest photos, mark it here. An additional €300 is billed to the clients.","Nëse çifti refuzon fotografitë me mysafirët, shënojeni këtu. Klientëve u shtohen 300 €.","Çift misafir fotoğraflarını reddederse burada işaretleyin. Müşterilere 300 € ek ücret yansıtılır."],
"Останува за наплата":["Remaining to collect","Mbetet për arkëtim","Tahsil edilecek bakiye"],
"Наплатен износ (€)":["Amount received (€)","Shuma e arkëtuar (€)","Tahsil edilen tutar (€)"],
"Датум на наплата":["Receipt date","Data e arkëtimit","Tahsilat tarihi"],
"Зачувај наплата":["Save receipt","Ruaj arkëtimin","Tahsilatı kaydet"],
"Тековна заработка со Експрес":["Current earnings including Express","Fitimi aktual me Express","Ekspres dahil güncel kazanç"],
"Продажбата на Експрес фотографии се додава плус. Не се смета како уплата за пакетот или доплатите.":["Express photo sales count as extra revenue, not as a payment toward the package or other extras.","Shitjet e fotove Express llogariten si të ardhura shtesë, jo si pagesë për paketën apo shtesat.","Ekspres fotoğraf satışları ek gelirdir; paket veya diğer ek hizmet ödemesi sayılmaz."],
"Календарот е достапен во APK апликацијата.":["The calendar is available in the Android app.","Kalendari është i disponueshëm në aplikacionin Android.","Takvim Android uygulamasında kullanılabilir."],
"Настанот е синхронизиран со Google Calendar.":["The event has been synced with Google Calendar.","Ngjarja u sinkronizua me Google Calendar.","Etkinlik Google Takvim ile eşitlendi."],
"Пополнете ги полињата означени со *.":["Fill in the fields marked *.","Plotësoni fushat e shënuara me *.","* ile işaretli alanları doldurun."],
"Капарот не може да е поголем од цената.":["The deposit cannot exceed the total price.","Parapagimi nuk mund të tejkalojë çmimin.","Kapora toplam fiyatı aşamaz."],
"Потпишан договор е заклучен. Направете нова резервација за измените.":["This signed agreement is locked. Create a new booking for changes.","Kontrata e nënshkruar është bllokuar. Krijoni rezervim të ri për ndryshimet.","İmzalı sözleşme kilitlidir. Değişiklikler için yeni rezervasyon oluşturun."],
"Нацртот е зачуван на овој уред.":["Draft saved on this device.","Drafti u ruajt në këtë pajisje.","Taslak bu cihaza kaydedildi."],
"Проверете го целиот договор. По потврда се заклучува и повеќе не може да се менува.":["Review the entire agreement. Once confirmed, it will be locked and cannot be changed.","Kontrolloni të gjithë kontratën. Pas konfirmimit ajo bllokohet dhe nuk ndryshohet më.","Sözleşmenin tamamını kontrol edin. Onaylandıktan sonra kilitlenir ve değiştirilemez."],
"Договорот е зачуван. Користете „Печати / зачувај PDF“ за копија надвор од апликацијата.":["Agreement saved. Use Print / Save PDF for an external copy.","Kontrata u ruajt. Përdorni Printo / Ruaj PDF për një kopje jashtë aplikacionit.","Sözleşme kaydedildi. Uygulama dışında kopya için Yazdır / PDF kaydet kullanın."],
"Внеси износ и датум.":["Enter the amount and date.","Shkruani shumën dhe datën.","Tutarı ve tarihi girin."],
"Провери ја цената и попустот.":["Check the price and discount.","Kontrolloni çmimin dhe zbritjen.","Fiyatı ve indirimi kontrol edin."],
"Внеси назив, цена, датум и наплатен износ од 0 до цената.":["Enter name, price, date and received amount between zero and the price.","Shkruani emrin, çmimin, datën dhe shumën e marrë nga zero deri te çmimi.","Ad, fiyat, tarih ve sıfır ile fiyat arasında alınan tutarı girin."],
"Внеси износ поголем од нула и не поголем од остатокот.":["Enter an amount above zero and no more than the remaining balance.","Shkruani shumë më të madhe se zero dhe jo më të madhe se mbetja.","Sıfırdan büyük ve kalan bakiyeyi aşmayan bir tutar girin."],
"Да ја избришам оваа ставка?":["Delete this entry?","Të fshihet ky regjistrim?","Bu kayıt silinsin mi?"],
"Избери свадба, износ и датум.":["Select a wedding, amount and date.","Zgjidhni dasmën, shumën dhe datën.","Düğün, tutar ve tarih seçin."],
"Прво избери свадба.":["Select a wedding first.","Së pari zgjidhni dasmën.","Önce bir düğün seçin."],
"Пополнете име, улога и хонорар.":["Enter name, role and fee.","Shkruani emrin, rolin dhe honorarin.","Ad, görev ve ücret girin."],
"Исплатата е поголема од договорениот хонорар. Продолжи?":["The payout exceeds the agreed fee. Continue?","Pagesa tejkalon honorarin e dakorduar. Të vazhdohet?","Ödeme anlaşılan ücreti aşıyor. Devam edilsin mi?"],
"Да ја избришам оваа евиденција за уплата?":["Delete this payment record?","Të fshihet ky regjistrim pagese?","Bu ödeme kaydı silinsin mi?"],
"Да го избришам ангажманот и сите негови исплати?":["Delete the engagement and all its payouts?","Të fshihet angazhimi dhe të gjitha pagesat e tij?","Görev ve tüm ödemeleri silinsin mi?"],
"Потребни се двата потписа: TD Production и соработникот.":["Both signatures are required: TD Production and team member.","Kërkohen të dy nënshkrimet: TD Production dhe bashkëpunëtori.","İki imza gereklidir: TD Production ve ekip üyesi."],
"Зачувај го потпишаниот договор? Оваа верзија ќе остане сочувана.":["Save this signed agreement? This version will be preserved.","Të ruhet kontrata e nënshkruar? Ky version do të ruhet.","İmzalı sözleşme kaydedilsin mi? Bu sürüm korunacak."],
"Потпишаниот договор е зачуван.":["Signed agreement saved.","Kontrata e nënshkruar u ruajt.","İmzalı sözleşme kaydedildi."],
"Резервната копија е копирана.":["Backup copied.","Kopja rezervë u kopjua.","Yedek kopyalandı."],
"Текстот е означен. Провери дали е копиран.":["Text selected. Check that it was copied.","Teksti u përzgjodh. Kontrolloni nëse u kopjua.","Metin seçildi. Kopyalandığını kontrol edin."],
"Текстот не е исправна резервна копија.":["This text is not a valid backup.","Teksti nuk është kopje rezervë e vlefshme.","Bu metin geçerli bir yedek değil."],
"Ова не е TD Booking резервна копија.":["This is not a TD Booking backup.","Kjo nuk është kopje rezervë TD Booking.","Bu TD Booking yedeği değil."],
"Ова ќе ги замени сите тековни свадби и финансиски податоци на овој уред. Продолжи?":["This replaces all current weddings and financial records on this device. Continue?","Kjo zëvendëson të gjitha dasmat dhe të dhënat financiare në pajisje. Të vazhdohet?","Bu cihazdaki tüm düğün ve finans kayıtları değiştirilecek. Devam edilsin mi?"],
"Податоците се вратени.":["Data restored.","Të dhënat u rikthyen.","Veriler geri yüklendi."],
"Нема доволно простор или внесот не успеа. Тековните податоци се задржани.":["Insufficient storage or import failed. Existing data was retained.","Hapësirë e pamjaftueshme ose importi dështoi. Të dhënat ekzistuese u ruajtën.","Yeterli alan yok veya içe aktarma başarısız. Mevcut veriler korundu."],
"Внеси исправни цени.":["Enter valid prices.","Shkruani çmime të vlefshme.","Geçerli fiyatlar girin."],
"Ценовникот е зачуван. Старите свадби ја задржуваат договорената цена.":["Price list saved. Previous weddings keep their agreed price.","Lista e çmimeve u ruajt. Dasmat e mëparshme ruajnë çmimin e dakorduar.","Fiyat listesi kaydedildi. Eski düğünler anlaşılan fiyatları korur."],
"Внеси опис, износ и датум.":["Enter a description, amount and date.","Shkruani përshkrimin, shumën dhe datën.","Açıklama, tutar ve tarih girin."],
"Избриши трошок?":["Delete this expense?","Të fshihet ky shpenzim?","Bu gider silinsin mi?"],
"Внеси API key и исправен HTTPS Database URL.":["Enter an API key and a valid HTTPS Database URL.","Vendosni çelësin API dhe një URL të vlefshme HTTPS të bazës së të dhënave.","API anahtarı ve geçerli bir HTTPS veritabanı adresi girin."],
"TD Cloud е поврзан.":["TD Cloud connected.","TD Cloud u lidh.","TD Cloud bağlandı."],
"Кодот е копиран.":["Code copied.","Kodi u kopjua.","Kod kopyalandı."],
"Кодот е означен. Избери Copy.":["Code selected. Choose Copy.","Kodi u përzgjodh. Zgjidhni Copy.","Kod seçildi. Kopyala'yı seçin."],
"Вработениот не е пронајден.":["Employee not found.","Punonjësi nuk u gjet.","Çalışan bulunamadı."],
"Внеси име и презиме.":["Enter a full name.","Shkruani emrin dhe mbiemrin.","Ad ve soyad girin."],
"Измените се зачувани. Сите претходни податоци остануваат.":["Changes saved. All previous records remain intact.","Ndryshimet u ruajtën. Të gjitha të dhënat e mëparshme mbeten.","Değişiklikler kaydedildi. Önceki tüm kayıtlar korundu."],
"Reset PIN е испратен. На следното отворање Екипа ќе побара нов PIN.":["PIN reset sent. The Team app will request a new PIN when next opened.","Rivendosja e PIN u dërgua. Aplikacioni Ekipi do të kërkojë PIN të ri në hapjen tjetër.","PIN sıfırlama gönderildi. Ekip uygulaması sonraki açılışta yeni PIN isteyecek."],
"Cloud профилот не е подготвен.":["Cloud profile is not ready.","Profili Cloud nuk është gati.","Cloud profili hazır değil."],
"Кодот уште не е подготвен. Нека вработениот ја ажурира и отвори апликацијата „Екипа“ еднаш, па притисни Освежи.":["The code is not ready yet. Ask the employee to update and open the Team app once, then tap Refresh.","Kodi nuk është ende gati. Punonjësi duhet të përditësojë dhe hapë aplikacionin Ekipi një herë, pastaj shtypni Rifresko.","Kod henüz hazır değil. Çalışanın Ekip uygulamasını güncelleyip bir kez açmasını sağlayın, sonra Yenile'ye dokunun."],
"Кодот за враќање е копиран.":["Recovery code copied.","Kodi i rikuperimit u kopjua.","Kurtarma kodu kopyalandı."],
"Прво активирај барем еден вработен.":["Activate at least one employee first.","Së pari aktivizoni të paktën një punonjës.","Önce en az bir çalışanı etkinleştirin."],
"Избери свадба, најмалку еден вработен и барем една улога.":["Choose a wedding, at least one employee and one role.","Zgjidhni një dasmë, të paktën një punonjës dhe një rol.","Bir düğün, en az bir çalışan ve bir görev seçin."],
"Поканата е испратена.":["Invitation sent.","Ftesa u dërgua.","Davet gönderildi."],
"Овој ангажман веќе има конечна одлука.":["This engagement already has a final decision.","Ky angazhim tashmë ka vendim përfundimtar.","Bu görev için nihai karar zaten verildi."],
"Внеси исправна цена.":["Enter a valid price.","Shkruani çmim të vlefshëm.","Geçerli bir fiyat girin."],
"Нема избрани улоги за ангажманот.":["No roles selected for this engagement.","Nuk janë zgjedhur role për këtë angazhim.","Bu görev için rol seçilmedi."],
"Целосно да го одбиеш ангажманот без нова цена?":["Decline the engagement without a counter-offer?","Ta refuzoni plotësisht angazhimin pa çmim të ri?","Yeni fiyat önermeden görevi tamamen reddetmek istiyor musunuz?"],
"Договорот сѐ уште не е подготвен.":["The agreement is not ready yet.","Kontrata nuk është ende gati.","Sözleşme henüz hazır değil."],
"Нема активни вработени.":["No active employees.","Nuk ka punonjës aktivë.","Aktif çalışan yok."],
"Избери примател и внеси наслов и порака.":["Select a recipient and enter a title and message.","Zgjidhni marrësin dhe shkruani titullin dhe mesazhin.","Alıcı seçin, başlık ve mesaj girin."],
"Информацијата е испратена.":["Notice sent.","Njoftimi u dërgua.","Duyuru gönderildi."],
"Овој телефон нема Web Crypto поддршка. Користи ја обичната копија.":["This phone does not support Web Crypto. Use the standard backup.","Ky telefon nuk mbështet Web Crypto. Përdorni kopjen e zakonshme.","Bu telefon Web Crypto desteklemiyor. Standart yedeklemeyi kullanın."],
"Лозинката треба да има најмалку 6 знаци.":["The password must have at least 6 characters.","Fjalëkalimi duhet të ketë të paktën 6 karaktere.","Parola en az 6 karakter olmalıdır."],
"Повтори ја лозинката:":["Repeat password:","Përsëritni fjalëkalimin:","Parolayı tekrar girin:"],
"Лозинките не се исти. Обиди се повторно.":["Passwords do not match. Try again.","Fjalëkalimet nuk përputhen. Provoni sërish.","Parolalar eşleşmiyor. Tekrar deneyin."],
"Залепи резервна копија.":["Paste a backup.","Ngjitni kopjen rezervë.","Yedeği yapıştırın."],
"Текстот не е исправна JSON резервна копија.":["This is not a valid JSON backup.","Teksti nuk është kopje rezervë JSON e vlefshme.","Bu metin geçerli bir JSON yedeği değil."],
"Целосната копија е оштетена или нецелосна.":["Full backup is corrupted or incomplete.","Kopja e plotë është e dëmtuar ose e paplotë.","Tam yedek bozuk veya eksik."],
"Ова ќе ги замени тековните TD Booking податоци на овој уред со податоците од целосната копија. Продолжи?":["This will replace the current TD Booking data on this device with the full backup. Continue?","Kjo do të zëvendësojë të dhënat TD Booking me kopjen e plotë. Të vazhdohet?","Bu işlem mevcut TD Booking verilerini tam yedekle değiştirecek. Devam edilsin mi?"],
"Внеси ја лозинката од ЦЕЛОСНАТА копија:":["Enter the password for the FULL backup:","Shkruani fjalëkalimin e kopjes së PLOTË:","TAM yedeğin parolasını girin:"],
"Погрешна лозинка или оштетена шифрирана Cloud копија. Ништо не е сменето.":["Wrong password or damaged encrypted Cloud backup. Nothing was changed.","Fjalëkalim i gabuar ose kopje e koduar Cloud e dëmtuar. Asgjë nuk u ndryshua.","Yanlış parola veya bozuk şifreli Cloud yedeği. Hiçbir şey değiştirilmedi."],
"Целосната копија е вратена. TD Booking ќе се рестартира.":["Full backup restored. TD Booking will restart.","Kopja e plotë u rikthye. TD Booking do të riniset.","Tam yedek geri yüklendi. TD Booking yeniden başlayacak."],
"Детали за настан":["Event details","Detajet e ngjarjes","Etkinlik ayrıntıları"],
"Притисни датум на календарот.":["Tap a date on the calendar.","Prek një datë në kalendar.","Takvimden bir tarihe dokunun."],
"Нема закажани настани на овој датум.":["No events scheduled on this date.","Nuk ka ngjarje të planifikuara për këtë datë.","Bu tarihte planlanmış etkinlik yok."],
"Нема внесени настани.":["No events entered.","Nuk ka ngjarje të regjistruara.","Kayıtlı etkinlik yok."],
"Сите настани вкупно":["All events total","Të gjitha ngjarjet gjithsej","Tüm etkinliklerin toplamı"],
"Сите свадби вкупно":["All weddings total","Të gjitha dasmat gjithsej","Tüm düğünlerin toplamı"],
"Договорено":["Agreed","Dakorduar","Anlaşılan"],
"Примено":["Received","Pranuar","Alınan"],
"Исплатено":["Paid out","Paguar","Ödenen"],
"Исплатено на екипа":["Paid to team","Paguar ekipit","Ekibe ödenen"],
"Избери настан":["Select event","Zgjidh ngjarjen","Etkinlik seç"],
"Наплатено од клиенти":["Received from clients","Pranuar nga klientët","Müşterilerden alınan"],
"За наплата":["Outstanding","Për arkëtim","Tahsil edilecek"],
"Договорено со екипа":["Agreed with team","Dakorduar me ekipin","Ekiple anlaşılan"],
"Останато за екипа":["Remaining for team","Mbetja për ekipin","Ekibe kalan"],
"Состојба":["Status","Gjendja","Durum"],
"Капарот од договорот е вклучен автоматски во примените пари. Не внесувај го вторпат.":["The agreement deposit is already included in received payments. Do not enter it again.","Parapagimi i kontratës përfshihet në pagesat e pranuara. Mos e regjistroni sërish.","Sözleşme kaporası alınan ödemelere dahildir. Tekrar kaydetmeyin."],
"Зачувај исплата":["Save payout","Ruaj pagesën","Ödemeyi kaydet"],
"Охрид · Договори и финансии":["Ohrid · Agreements and finances","Ohër · Kontrata dhe financa","Ohri · Sözleşmeler ve finans"],
"Документот се составува според внесените податоци. Проверете ги износите и условите пред потпишување.":["The document uses the entered information. Verify amounts and conditions before signing.","Dokumenti krijohet sipas të dhënave të futura. Verifikoni shumat dhe kushtet para nënshkrimit.","Belge girilen bilgilere göre hazırlanır. İmzalamadan önce tutarları ve koşulları kontrol edin."],
"Студио":["Studio","Studio","Stüdyo"],
"Соработник":["Team member","Bashkëpunëtori","Ekip üyesi"],
"Улога":["Role","Roli","Görev"],
"Време":["Time","Koha","Zaman"],
"Предавање материјал":["Material handover","Dorëzimi i materialit","Materyal teslimi"],
"Хонорар":["Fee","Honorari","Ücret"],
"Вкупно исплатено":["Total paid","Gjithsej paguar","Toplam ödenen"],
"Остаток":["Balance","Mbetja","Bakiye"],
"Потврда":["Receipt","Vërtetim","Makbuz"],
"ДОГОВОР ЗА ФОТО И ВИДЕО УСЛУГИ":["PHOTO AND VIDEO SERVICES AGREEMENT","KONTRATË PËR SHËRBIME FOTO DHE VIDEO","FOTOĞRAF VE VİDEO HİZMET SÖZLEŞMESİ"],
"ДОГОВОР ЗА КОНКРЕТЕН АНГАЖМАН":["AGREEMENT FOR SPECIFIC ENGAGEMENT","KONTRATË PËR ANGAZHIM TË CAKTUAR","BELİRLİ GÖREV SÖZLEŞMESİ"],
"ПОТПИШАН ДОГОВОР ЗА АНГАЖМАН":["SIGNED ENGAGEMENT AGREEMENT","KONTRATË ANGAZHIMI E NËNSHKRUAR","İMZALI GÖREV SÖZLEŞMESİ"],
"ПОТВРДА ЗА ИСПЛАТА":["PAYOUT RECEIPT","VËRTETIM PAGESE","ÖDEME MAKBUZU"],
"ПОТВРДА ЗА ЕКСПРЕС ФОТОГРАФИИ":["EXPRESS PHOTO RECEIPT","VËRTETIM PËR FOTO EXPRESS","EKSPRES FOTOĞRAF MAKBUZU"],
"ПОТВРДА ЗА ПРИМЕНА УПЛАТА":["CLIENT PAYMENT RECEIPT","VËRTETIM PËR PAGESË TË PRANUAR","MÜŞTERİ ÖDEME MAKBUZU"],
"Приход од продадени Експрес фотографии":["Revenue from Express photo sales","Të ardhura nga shitja e fotove Express","Ekspres fotoğraf satış geliri"],
"За исплата на екипа":["Due to team","Për t'iu paguar ekipit","Ekibe ödenecek"],
"Неплатени свадби":["Unpaid weddings","Dasma të papaguara","Ödenmemiş düğünler"],
"Наплатено од клиенти минус исплатено на екипа":["Client receipts minus team payouts","Pranuar nga klientët minus pagesat e ekipit","Müşteri tahsilatları eksi ekip ödemeleri"],
"Следни настани":["Upcoming events","Ngjarjet e ardhshme","Yaklaşan etkinlikler"],
"Платена":["Paid","Paguar","Ödendi"],
"Не е платена":["Unpaid","Papaguar","Ödenmedi"],
"Отвори свадба":["Open wedding","Hap dasmën","Düğünü aç"],
"Датум и свадба":["Date and wedding","Data dhe dasma","Tarih ve düğün"],
"Хонорар / платено":["Fee / paid","Honorar / paguar","Ücret / ödenen"],
"Почетниот датум треба да биде пред крајниот.":["The start date must be before the end date.","Data e fillimit duhet të jetë para datës së mbarimit.","Başlangıç tarihi bitiş tarihinden önce olmalıdır."],
"По свадба":["By wedding","Sipas dasmës","Düğüne göre"],
"Детали":["Details","Detaje","Ayrıntılar"],
"По соработник":["By team member","Sipas bashkëpunëtorit","Ekip üyesine göre"],
"Финансиски извештај":["Financial report","Raporti financiar","Mali rapor"],
"Примено со Експрес":["Received including Express","Pranuar bashkë me Express","Ekspres dahil alınan"],
"Евиденција на дополнителни услуги":["Additional service record","Regjistri i shërbimeve shtesë","Ek hizmet kaydı"],
"Посебна евиденција; не го менува потпишаниот основен договор.":["Separate record; does not change the signed original agreement.","Regjistër i veçantë; nuk e ndryshon kontratën bazë të nënshkruar.","Ayrı kayıttır; imzalı ana sözleşmeyi değiştirmez."],
"Услуга":["Service","Shërbimi","Hizmet"],
"Проверувам поврзување…":["Checking connection…","Po kontrollohet lidhja…","Bağlantı denetleniyor…"],
"Инфо до тим":["Team information","Informata për ekipin","Ekip duyuruları"],
"Освежи":["Refresh","Rifresko","Yenile"],
"Покани и договори":["Invitations and agreements","Ftesat dhe kontratat","Davetler ve sözleşmeler"],
"Последни информации до тимот":["Latest team notices","Njoftimet e fundit për ekipin","Son ekip bildirimleri"],
"TD Cloud поврзување":["TD Cloud connection","Lidhja TD Cloud","TD Cloud bağlantısı"],
"За двете апликации да работат преку интернет, внеси Firebase Web API key и Realtime Database URL. Ова се прави еднаш.":["Enter Firebase Web API key and Realtime Database URL to connect both apps online. Only needed once.","Vendos çelësin Firebase Web API dhe adresën Realtime Database për të lidhur të dy aplikacionet. Bëhet vetëm një herë.","İki uygulamayı internet üzerinden bağlamak için Firebase Web API anahtarını ve Realtime Database adresini girin. Yalnızca bir kez gerekir."],
"Потребни Firebase поставки":["Required Firebase settings","Cilësimet e nevojshme Firebase","Gerekli Firebase ayarları"],
"Поврзи":["Connect","Lidhu","Bağlan"],
"Код за нов вработен":["New employee code","Kodi për punonjës të ri","Yeni çalışan kodu"],
"Испрати му го овој код. Важи 7 дена и може да се активира само еднаш.":["Send this code to the employee. Valid for 7 days and one-time activation.","Dërgoja kodin punonjësit. Vlen 7 ditë dhe aktivizohet vetëm një herë.","Bu kodu çalışana gönderin. 7 gün geçerlidir ve yalnızca bir kez kullanılabilir."],
"Копирај код":["Copy code","Kopjo kodin","Kodu kopyala"],
"Контрола на вработен":["Employee management","Menaxhimi i punonjësit","Çalışan yönetimi"],
"Име и презиме":["Full name","Emri dhe mbiemri","Ad ve soyad"],
"Улога / позиција":["Role / position","Roli / pozita","Görev / pozisyon"],
"Стандардна дневница (€)":["Standard daily rate (€)","Pagesa ditore standarde (€)","Standart günlük ücret (€)"],
"Зачувај измени":["Save changes","Ruaj ndryshimet","Değişiklikleri kaydet"],
"Код за враќање":["Recovery code","Kodi i rikuperimit","Kurtarma kodu"],
"Блокирај":["Block","Blloko","Engelle"],
"Архивирај":["Archive","Arkivo","Arşivle"],
"Активирај повторно":["Reactivate","Riaktivizo","Yeniden etkinleştir"],
"Нема активирани вработени.":["No active employees.","Nuk ka punonjës aktivë.","Aktif çalışan yok."],
"Важно":["Important","E rëndësishme","Önemli"],
"Нова покана за свадба":["New wedding invitation","Ftesë e re për dasmë","Yeni düğün daveti"],
"На кого":["Recipients","Për kë","Alıcılar"],
"Улоги / задачи што се потребни":["Required roles / tasks","Rolet / detyrat e nevojshme","Gerekli görevler"],
"Информација / задачи":["Information / tasks","Informacion / detyra","Bilgi / görevler"],
"Испрати покана":["Send invitation","Dërgo ftesën","Davet gönder"],
"Види договор":["View agreement","Shiko kontratën","Sözleşmeyi görüntüle"],
"Прифаќање цена":["Accept price","Pranimi i çmimit","Fiyatı kabul et"],
"Хонорар за потврда":["Fee to confirm","Honorari për konfirmim","Onaylanacak ücret"],
"Одбиј / Моја цена":["Reject / My price","Refuzo / Çmimi im","Reddet / Fiyatım"],
"Негов предлог":["Their offer","Propozimi i tij/saj","Karşı teklif"],
"Твој предлог за хонорар (€)":["Your proposed fee (€)","Honorari i propozuar (€)","Önerdiğiniz ücret (€)"],
"Порака до колегата (по желба)":["Message to team member (optional)","Mesazh për kolegun (opsionale)","Ekip üyesine mesaj (isteğe bağlı)"],
"Испрати моја цена":["Send my price","Dërgo çmimin tim","Fiyatımı gönder"],
"Целосно одбиј":["Decline entirely","Refuzo plotësisht","Tamamen reddet"],
"Информација до тимот":["Team notice","Njoftim për ekipin","Ekibe duyuru"],
"Приматели":["Recipients","Marrësit","Alıcılar"],
"Наслов":["Title","Titulli","Başlık"],
"Порака":["Message","Mesazhi","Mesaj"],
"Важна промена":["Important change","Ndryshim i rëndësishëm","Önemli değişiklik"],
"Испрати":["Send","Dërgo","Gönder"],
"Потпишани ангажмани":["Signed engagements","Angazhime të nënshkruara","İmzalı görevler"],
"PIN reset испратен":["PIN reset sent","Rivendosja e PIN u dërgua","PIN sıfırlama gönderildi"],
"Уреди / Контрола":["Edit / Manage","Redakto / Menaxho","Düzenle / Yönet"],
"Нема активирани Екипа.":["No active team members.","Nuk ka anëtarë aktivë të ekipit.","Aktif ekip üyesi yok."],
"Чека одговор од колегата":["Awaiting team member reply","Në pritje të përgjigjes së kolegut","Ekip üyesinin yanıtı bekleniyor"],
"Смени моја цена":["Change my price","Ndrysho çmimin tim","Fiyatımı değiştir"],
"Предлог":["Offer","Propozim","Teklif"],
"Потврдено":["Confirmed","Konfirmuar","Onaylandı"],
"Нема испратени покани.":["No invitations sent.","Nuk ka ftesa të dërguara.","Gönderilen davet yok."],
"Нема испратени информации.":["No notices sent.","Nuk ka njoftime të dërguara.","Gönderilen duyuru yok."],
"Број":["Number","Numri","Numara"],
"Датум на договор":["Agreement date","Data e kontratës","Sözleşme tarihi"],
"Изведувач":["Provider","Ofruesi","Hizmet sağlayıcı"],
"Клиенти":["Clients","Klientët","Müşteriler"],
"Контакт":["Contact","Kontakti","İletişim"],
"Настан":["Event","Ngjarja","Etkinlik"],
"Предмет и пакет":["Services and package","Shërbimet dhe paketa","Hizmetler ve paket"],
"Гратис":["Complimentary","Falas","Ücretsiz"],
"Цена и испорака":["Price and delivery","Çmimi dhe dorëzimi","Fiyat ve teslimat"],
"Вкупна цена":["Total price","Çmimi total","Toplam fiyat"],
"Рок за испорака":["Delivery deadline","Afati i dorëzimit","Teslim tarihi"],
"Услови":["Terms","Kushtet","Koşullar"],
"Измени":["Changes","Ndryshime","Değişiklikler"],
"Остаток":["Balance","Mbetja","Bakiye"],
"Зачувај":["Save","Ruaj","Kaydet"],
"Избриши":["Delete","Fshij","Sil"],
"Пребарај":["Search","Kërko","Ara"],
"Нема резултати за ова пребарување.":["No search results.","Nuk ka rezultate për këtë kërkim.","Arama sonucu bulunamadı."],
"Внеси име, датум или пакет за да се појават резултати.":["Enter a name, date or package to see results.","Shkruaj emrin, datën ose paketën për të parë rezultatet.","Sonuçları görmek için ad, tarih veya paket girin."],
"Екипа на настанот":["Event team","Ekipi i ngjarjes","Etkinlik ekibi"],
"Непознат":["Unknown","I panjohur","Bilinmiyor"],
"Без улога":["No role","Pa rol","Görev belirtilmedi"],
"Сè уште нема запишана екипа за овој настан.":["No team recorded for this event yet.","Nuk ka ende ekip për këtë ngjarje.","Bu etkinliğe henüz ekip kaydedilmedi."],
"Телефон ↔ MarK PC":["Phone ↔ MarK PC","Telefon ↔ MarK PC","Telefon ↔ MarK PC"],
"Неповрзано":["Not connected","I palidhur","Bağlı değil"],
"Не е поврзано":["Not connected","I palidhur","Bağlı değil"],
"Започни спарување":["Start pairing","Fillo çiftimin","Eşleştirmeye başla"],
"Заврши по PC потврда":["Finish after PC confirmation","Përfundo pas konfirmimit në PC","PC onayından sonra tamamla"],
"Синхронизирај сè":["Sync everything","Sinkronizo të gjitha","Tümünü eşitle"],
"Подготвено.":["Ready.","Gati.","Hazır."],
"Готовина":["Cash","Para në dorë","Nakit"],
"Трансфер":["Bank transfer","Transfer bankar","Banka havalesi"],
"Друго":["Other","Tjetër","Diğer"],
"Зачувај исплата":["Save payment","Ruaj pagesën","Ödemeyi kaydet"],
"Начин на исплата":["Payment method","Mënyra e pagesës","Ödeme yöntemi"],
"Веќе исплатено (€)":["Already paid (€)","Tashmë paguar (€)","Ödenmiş (€)"],
"Договорен хонорар (€) *":["Agreed fee (€) *","Honorari i dakorduar (€) *","Anlaşılan ücret (€) *"],
"За што / забелешка":["Purpose / note","Qëllimi / shënimi","Amaç / not"],
"Лого на корисник":["User logo","Logo e përdoruesit","Kullanıcı logosu"],
"MarK изглед и лого":["MarK appearance and logo","Pamja dhe logoja e MarK","MarK görünümü ve logosu"],
"Главна боја":["Primary colour","Ngjyra kryesore","Ana renk"],
"Златен акцент":["Gold accent","Theks i artë","Altın vurgu"],
"Лого од телефон (PNG/JPG)":["Logo from phone (PNG/JPG)","Logo nga telefoni (PNG/JPG)","Telefondan logo (PNG/JPG)"],
"Избери PNG или JPG.":["Choose a PNG or JPG.","Zgjidh PNG ose JPG.","PNG veya JPG seçin."],
"Не можам да го зачувам логото; пробај со помала слика.":["Cannot save the logo; try a smaller image.","Nuk mund të ruhet logoja; provo një imazh më të vogël.","Logo kaydedilemiyor; daha küçük bir görsel deneyin."],
"Рачно додавање во екипа":["Add team member manually","Shto anëtar të ekipit manualisht","Ekip üyesini elle ekle"],
"Додај соработник / ангажман":["Add team member / engagement","Shto bashkëpunëtor / angazhim","Ekip üyesi / görev ekle"],
"Нова исплата":["New payout","Pagesë e re","Yeni ödeme"],
"Нема активирани вработени.":["No employees activated.","Nuk ka punonjës të aktivizuar.","Etkinleştirilmiş çalışan yok."],
"Нема испратени покани.":["No invitations sent.","Nuk ka ftesa të dërguara.","Gönderilen davet yok."],
"TD Production · верзија 6.20 · Резервации и екипа":["TD Production · version 6.21 · Bookings and team","TD Production · versioni 6.21 · Rezervimet dhe ekipi","TD Production · sürüm 6.21 · Rezervasyonlar ve ekip"],
"Следен →":["Next →","Tjetër →","Sonraki →"],
"Невеста *":["Bride *","Nusja *","Gelin *"],
"Младоженец *":["Groom *","Dhëndri *","Damat *"],
"Телефон на клиент *":["Client phone *","Telefoni i klientit *","Müşteri telefonu *"],
"Место / хотел":["Venue / hotel","Vendi / hoteli","Mekân / otel"],
"Резервирана":["Booked","Rezervuar","Rezerve edildi"],
"Завршена":["Completed","Përfunduar","Tamamlandı"],
"Предадена":["Delivered","Dorëzuar","Teslim edildi"],
"Откажана":["Cancelled","Anuluar","İptal edildi"],
"Пакет *":["Package *","Paketa *","Paket *"],
"Услуги договорени за оваа свадба *":["Services agreed for this wedding *","Shërbimet e dakorduara për këtë dasmë *","Bu düğün için kararlaştırılan hizmetler *"],
"Договорени услуги за настанот *":["Services agreed for this event *","Shërbimet e dakorduara për këtë ngjarje *","Etkinlik için kararlaştırılan hizmetler *"],
"Цена и подарок":["Price and complimentary items","Çmimi dhe dhuratat","Fiyat ve hediyeler"],
"Договорена цена пред попуст (€)":["Agreed price before discount (€)","Çmimi i dakorduar para zbritjes (€)","İndirimsiz anlaşılan fiyat (€)"],
"Попуст":["Discount","Zbritja","İndirim"],
"Процент (%)":["Percentage (%)","Përqindje (%)","Yüzde (%)"],
"Вредност на попуст":["Discount amount","Vlera e zbritjes","İndirim tutarı"],
"Гратис вклучено во пакетот":["Complimentary package inclusions","Shërbime falas në paketë","Pakete dahil ücretsiz hizmetler"],
"Конечна цена (€)":["Final price (€)","Çmimi përfundimtar (€)","Son fiyat (€)"],
"Капар (€)":["Deposit (€)","Parapagimi (€)","Kapora (€)"],
"Рок за остаток":["Balance due date","Afati për pjesën e mbetur","Kalan ödeme tarihi"],
"Назив на студио / изведувач *":["Studio / provider name *","Emri i studios / ofruesit *","Stüdyo / hizmet sağlayıcı adı *"],
"Твоето целосно име за потпис *":["Your full name for signing *","Emri juaj i plotë për nënshkrim *","İmza için tam adınız *"],
"Дополнителни договори и измени":["Additional agreements and changes","Marrëveshje dhe ndryshime shtesë","Ek anlaşmalar ve değişiklikler"],
"Услови на договорот *":["Agreement terms *","Kushtet e kontratës *","Sözleşme şartları *"],
"Прочитајте и дополнете ги условите за конкретниот настан пред потпишување.":["Read and complete the terms for the event before signing.","Lexoni dhe plotësoni kushtet e ngjarjes para nënshkrimit.","İmzalamadan önce etkinliğin şartlarını okuyup tamamlayın."],
"Зачувај нацрт":["Save draft","Ruaj draftin","Taslağı kaydet"],
"Синхронизирај Google Calendar":["Sync Google Calendar","Sinkronizo Google Calendar","Google Takvim ile eşitle"],
"Потписи на екранот":["On-screen signatures","Nënshkrimet në ekran","Ekrandaki imzalar"],
"Потпишете со S Pen или прст. Секој потпис има свое поле.":["Sign with an S Pen or finger. Each signature has its own field.","Nënshkruani me S Pen ose gisht. Çdo nënshkrim ka fushën e vet.","S Pen veya parmağınızla imzalayın. Her imzanın ayrı bir alanı vardır."],
"TD Production / изведувач":["TD Production / provider","TD Production / ofruesi","TD Production / hizmet sağlayıcı"],
"Избриши потпис":["Clear signature","Fshij nënshkrimin","İmzayı temizle"],
"Назад кон уредување":["Back to editing","Kthehu te redaktimi","Düzenlemeye dön"],
"Избери свадба за да внесеш уплати од клиентите, ангажмани и исплати кон екипата.":["Select a wedding to record client payments, team assignments and payouts.","Zgjidh një dasmë për të regjistruar pagesat e klientëve, angazhimet dhe pagesat e ekipit.","Müşteri ödemelerini, ekip görevlerini ve ödemelerini kaydetmek için bir düğün seçin."],
"Уплата од клиент":["Client payment","Pagesë nga klienti","Müşteri ödemesi"],
"Забележи уплата":["Record payment","Regjistro pagesën","Ödemeyi kaydet"],
"Експрес · посебен приход плус":["Express · separate additional income","Express · të ardhura shtesë të ndara","Ekspres · ayrı ek gelir"],
"Внеси пари од продадени Експрес фотографии. Овој приход е плус, одделно од наплатата за пакетот и доплатите.":["Record proceeds from sold Express photos separately from package and extra-service payments.","Regjistro të ardhurat nga fotot Express veçmas nga pagesat e paketës dhe shërbimeve shtesë.","Satılan Ekspres fotoğrafların gelirini paket ve ek hizmet ödemelerinden ayrı kaydedin."],
"Примено (€)":["Received (€)","Pranuar (€)","Alınan (€)"],
"Датум на продажба":["Sale date","Data e shitjes","Satış tarihi"],
"Опис / број фотографии":["Description / number of photos","Përshkrimi / numri i fotove","Açıklama / fotoğraf sayısı"],
"Додај продажба":["Add sale","Shto shitje","Satış ekle"],
"Услугата се додава на договорениот износ. Ако уште не е платена, ќе се прикаже како долг.":["The service adds to the agreed amount. An unpaid amount appears as a balance owed.","Shërbimi i shtohet shumës së dakorduar. Pjesa e papaguar shfaqet si detyrim.","Bu hizmet anlaşılan tutara eklenir. Ödenmemiş tutar borç olarak görünür."],
"Оваа услуга е гратис":["This service is complimentary","Ky shërbim është falas","Bu hizmet ücretsizdir"],
"Попуст на услугата":["Service discount","Zbritje për shërbimin","Hizmet indirimi"],
"Вредност":["Value","Vlera","Değer"],
"Додај услуга":["Add service","Shto shërbim","Hizmet ekle"],
"Опис":["Description","Përshkrimi","Açıklama"],
"Додај трошок":["Add expense","Shto shpenzim","Gider ekle"],
"Ангажмани за оваа свадба":["Assignments for this wedding","Angazhimet për këtë dasmë","Bu düğünün görevleri"],
"Додај фотограф / видео / кран / дрон / Love Story":["Add photographer / video / crane / drone / Love Story","Shto fotograf / video / kran / dron / Love Story","Fotoğraf / video / vinç / drone / Love Story ekle"],
"Фотограф":["Photographer","Fotograf","Fotoğrafçı"],
"Видеограф":["Videographer","Videograf","Videograf"],
"Кран оператор":["Crane operator","Operator krani","Vinç operatörü"],
"Дрон оператор":["Drone operator","Operator droni","Drone operatörü"],
"Love Story снимање":["Love Story filming","Xhirim Love Story","Love Story çekimi"],
"Монтажа / обработка":["Editing / post-production","Montazh / përpunim","Kurgu / düzenleme"],
"Задачи и опрема":["Tasks and equipment","Detyrat dhe pajisjet","Görevler ve ekipman"],
"Рок и начин на предавање материјал":["Material handover deadline and method","Afati dhe mënyra e dorëzimit të materialit","Materyal teslim tarihi ve yöntemi"],
"Посебни услови":["Special conditions","Kushte të veçanta","Özel koşullar"],
"Ова е договор за конкретен ангажман. Ако лицето е во редовен работен однос, користете соодветен договор за вработување.":["This is a specific engagement agreement. Use an appropriate employment contract for regular employees.","Kjo kontratë është për angazhim të veçantë. Për punonjësit e rregullt përdorni kontratën përkatëse të punës.","Bu sözleşme belirli bir görev içindir. Düzenli çalışanlar için uygun bir iş sözleşmesi kullanın."],
"Нова свадба":["New wedding","Dasmë e re","Yeni düğün"],
"Соработници и нивните ангажмани низ сите свадби.":["Team members and their assignments across all weddings.","Bashkëpunëtorët dhe angazhimet e tyre në të gjitha dasmat.","Tüm düğünlerde ekip üyeleri ve görevleri."],
"Зачувај PDF извештај":["Save PDF report","Ruaj raportin PDF","PDF raporunu kaydet"],
"Целосна безбедносна копија":["Full backup","Kopje rezervë e plotë","Tam yedekleme"],
"Ги зачувува свадбите, парите, екипата, потписите, пакетите и TD Cloud поврзувањето. Cloud најава се шифрира со лозинка што само ти ја знаеш.":["Backs up weddings, finances, team, signatures, packages and TD Cloud connection. Cloud credentials are encrypted with a password only you know.","Ruan dasmat, financat, ekipin, nënshkrimet, paketat dhe TD Cloud. Hyrja në Cloud kodohet me një fjalëkalim që e dini vetëm ju.","Düğünleri, finansı, ekibi, imzaları, paketleri ve TD Cloud bağlantısını yedekler. Cloud girişi yalnızca bildiğiniz parola ile şifrelenir."],
"Направи ЦЕЛОСНА копија":["Create FULL backup","Krijo kopje të PLOTË","TAM yedek oluştur"],
"Копирај текст":["Copy text","Kopjo tekstin","Metni kopyala"],
"Обична копија (само локални податоци)":["Standard backup (local data only)","Kopje e zakonshme (vetëm të dhëna lokale)","Standart yedek (yalnızca yerel veriler)"],
"Свадби, финансии и пакетите.":["Weddings, finances and packages.","Dasmat, financat dhe paketat.","Düğünler, finans ve paketler."],
"Прикажи обична копија":["Show standard backup","Shfaq kopjen e zakonshme","Standart yedeği göster"],
"Чувај ја копијата приватно. Целосната копија содржи шифриран Cloud пристап; без лозинката не може да се прочита.":["Keep your backup private. Full backup includes encrypted Cloud access that cannot be read without the password.","Ruajeni kopjen privatisht. Kopja e plotë përmban hyrje të koduar në Cloud dhe nuk lexohet pa fjalëkalimin.","Yedeği özel tutun. Tam yedek şifreli Cloud erişimi içerir; parola olmadan okunamaz."],
"Врати податоци":["Restore data","Rikthe të dhënat","Verileri geri yükle"],
"Провери и врати":["Validate and restore","Kontrollo dhe rikthe","Doğrula ve geri yükle"],
"Редовни цени за нови резервации. Договорените цени на постојните свадби остануваат исти.":["Standard prices for new bookings. Existing agreed wedding prices remain unchanged.","Çmimet standarde për rezervimet e reja. Çmimet e dakorduara për dasmat ekzistuese nuk ndryshojnë.","Yeni rezervasyonlar için standart fiyatlar. Mevcut düğünlerin anlaşılan fiyatları değişmez."],
"Отвори мени":["Open menu","Hap menynë","Menüyü aç"],
"Главно мени":["Main menu","Menyja kryesore","Ana menü"],
"Барај име, телефон или датум":["Search name, phone or date","Kërko emër, telefon ose datë","Ad, telefon veya tarih ara"],
"Напиши договорен рок":["Enter agreed deadline","Shkruaj afatin e dakorduar","Anlaşılan süreyi girin"],
"Име и презиме на потписникот":["Signer's full name","Emri i plotë i nënshkruesit","İmzalayanın tam adı"],
"Пример: остаток":["Example: remaining balance","Shembull: mbetja","Örnek: kalan bakiye"],
"Пример: 35 слики за гости":["Example: 35 guest photos","Shembull: 35 foto për mysafirët","Örnek: 35 misafir fotoğrafı"],
"Пример: дополнителен фотограф, албум, снимање":["Example: extra photographer, album, filming","Shembull: fotograf shtesë, album, xhirim","Örnek: ek fotoğrafçı, albüm, çekim"],
"Гориво, пат, хотел…":["Fuel, travel, hotel…","Karburant, udhëtim, hotel…","Yakıt, seyahat, otel…"],
"Пример: оригинални датотеки следниот ден":["Example: originals the next day","Shembull: origjinalet ditën tjetër","Örnek: orijinal dosyalar ertesi gün"],
"Овде ќе се појави резервната копија":["Backup text will appear here","Teksti i kopjes rezervë shfaqet këtu","Yedekleme metni burada görünecek"],
"Залепи обична или ЦЕЛОСНА резервна копија тука":["Paste a standard or FULL backup here","Ngjit këtu një kopje të zakonshme ose të PLOTË","Standart veya TAM yedeği buraya yapıştır"],
'Календар':['Calendar','Kalendari','Takvim'],
'Пон':['Mon','Hën','Pzt'],'Вто':['Tue','Mar','Sal'],'Сре':['Wed','Mër','Çar'],'Чет':['Thu','Enj','Per'],'Пет':['Fri','Pre','Cum'],'Саб':['Sat','Sht','Cmt'],'Нед':['Sun','Die','Paz'],
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
sq:{title:'KONTRATË PËR SHËRBIME FOTOGRAFIE DHE VIDEOJE',no:'Numri',date:'Data e kontratës',provider:'Ofruesi i shërbimit',clients:'Klientët',contact:'Kontakti',event:'Ngjarja',start:'Fillimi',venue:'Vendi',subject:'Shërbimet dhe paketa',gift:'Falas',price:'Çmimi dhe dorëzimi',base:'Çmimi para zbritjes',discount:'Zbritja',total:'Çmimi total',deposit:'Parapagimi',balance:'Mbetja për pagesë',due:'Afati i pagesës',delivery:'Afati i dorëzimit',terms:'Kushtet',notes:'Marrëveshje dhe ndryshime shtesë',accept:'Me nënshkrim, palët pranojnë kushtet e paraqitura më sipër.',signed:'Nënshkruar',signature:'nënshkrimi',bride:'Nusja',groom:'Dhëndri',client:'Klienti',agreement:'KONTRATË ANGAZHIMI',receipt:'VËRTETIM PËR PRANIM PAGESE',payout:'VËRTETIM PAGESE',express:'VËRTETIM PËR FOTOT EXPRESS',amount:'Shuma',method:'Mënyra',received:'Gjithsej të pranuara',owed:'Mbetja për pagesë',services:'Shërbime shtesë',fee:'Honorari',paid:'Gjithsej të paguara',role:'Roli',tasks:'Detyrat dhe pajisjet',handoff:'Dorëzimi i materialit',paymentDate:'Data e pagesës',employee:'Bashkëpunëtori',conditions:'Kushte të veçanta',copies:'Kopje në gjuhë të tjera',custom:'Teksti i shkruar nga përdoruesi mbetet në gjuhën origjinale. Kërkohet përkthim dhe verifikim profesional para përdorimit juridik.',report:'Raporti financiar',period:'Periudha',partner:'Ekipi',events:'Ngjarjet',original:'Dokumenti origjinal',copy:'Kopje e përkthyer'},
tr:{title:'FOTOĞRAF VE VİDEO HİZMET SÖZLEŞMESİ',no:'Numara',date:'Sözleşme tarihi',provider:'Hizmet sağlayıcı',clients:'Müşteriler',contact:'İletişim',event:'Etkinlik',start:'Başlangıç',venue:'Mekân',subject:'Hizmetler ve paket',gift:'Ücretsiz',price:'Fiyat ve teslimat',base:'İndirimsiz fiyat',discount:'İndirim',total:'Toplam fiyat',deposit:'Kapora',balance:'Kalan bakiye',due:'Son ödeme tarihi',delivery:'Teslim tarihi',terms:'Şartlar ve koşullar',notes:'Ek anlaşmalar ve değişiklikler',accept:'Taraflar imzalayarak yukarıdaki sözleşmeyi kabul ederler.',signed:'İmzalandı',signature:'imza',bride:'Gelin',groom:'Damat',client:'Müşteri',agreement:'GÖREVLENDİRME SÖZLEŞMESİ',receipt:'ÖDEME MAKBUZU',payout:'ÜCRET ÖDEME MAKBUZU',express:'EKSPRES FOTOĞRAF SATIŞ MAKBUZU',amount:'Tutar',method:'Yöntem',received:'Toplam alınan',owed:'Kalan alacak',services:'Ek hizmetler',fee:'Ücret',paid:'Toplam ödenen',role:'Görev',tasks:'Görevler ve ekipman',handoff:'Materyal teslimi',paymentDate:'Ödeme tarihi',employee:'Ekip üyesi',conditions:'Özel şartlar',copies:'Dil kopyaları',custom:'Kullanıcı tarafından girilen metin orijinal dilinde kalır. Hukuki kullanımdan önce profesyonel çeviri ve kontrol gereklidir.',report:'Mali rapor',period:'Dönem',partner:'Ekip',events:'Etkinlikler',original:'Orijinal belge',copy:'Çevrilmiş kopya'}
};
const $=id=>document.getElementById(id);
const esc=s=>String(s==null?'':s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const nl=s=>esc(s).replace(/\r?\n/g,'<br>');
const safeDate=s=>esc(s||'—');
const effective=()=>{let x=localStorage.getItem(K)||'auto';if(x==='auto'){let n=(navigator.language||'mk').split('-')[0].toLowerCase();return LANGS[n]?n:'mk';}return LANGS[x]?x:'mk'};
let currentDoc=null,uiTimer=null,observer=null;

const PANEL={
mk:['Јазик на апликацијата','Автоматски — јазик на телефонот','Јазикот на документите се избира посебно при создавање PDF.','Јазик на документот','Дополнителни копии','Создади PDF на избраните јазици','Рачно внесените услуги и посебни услови остануваат на оригиналниот јазик; проверете ги пред потпишување.'],
en:['App language','Automatic — phone language','Choose the document language separately when creating a PDF.','Document language','Additional language copies','Create PDF in selected languages','Custom services and special terms stay in their original language; verify translations before signing.'],
sq:['Gjuha e aplikacionit','Automatikisht — gjuha e telefonit','Zgjidh veçmas gjuhën e dokumentit kur krijon PDF.','Gjuha e dokumentit','Kopje shtesë në gjuhë të tjera','Krijo PDF në gjuhët e zgjedhura','Shërbimet dhe kushtet e shkruara nga përdoruesi mbeten në gjuhën origjinale; kontrolloni përkthimin para nënshkrimit.'],
tr:['Uygulama dili','Otomatik — telefon dili','PDF oluştururken belge dilini ayrıca seçin.','Belge dili','Ek dil kopyaları','Seçilen dillerde PDF oluştur','Kullanıcının yazdığı hizmetler ve özel şartlar orijinal dilde kalır; imzadan önce çevirileri kontrol edin.']
};
function setPanelLabels(){
 const t=PANEL[effective()],setting=$('markLanguagePanel');
 if(setting){
 const elements=[setting.querySelector('label'),setting.querySelector('select option[value=auto]'),setting.querySelector('p')];
 elements.forEach((el,i)=>{if(el&&el.textContent!==t[i])el.textContent=t[i]});
 }
 for(const id of ['markContractLanguages','markFinanceLanguages','markReportLanguages']){
  const pane=$(id);if(!pane)continue;
  const elements=[pane.querySelector('.mark-primary-label'),pane.querySelector('.mark-extra-title'),pane.querySelector('.mark-generate'),pane.querySelector('.mark-disclaimer')];
  elements.forEach((el,i)=>{const label=t[i+3];if(el&&el.textContent!==label)el.textContent=label});
 }
}


function localMessage(value) {
 const original=String(value??''),lang=effective(),idx=['en','sq','tr'].indexOf(lang);
 if(idx<0)return original;
 const exact=UI[original]||UI[original.trim()];
 if(exact)return exact[idx];
 // Keep amounts, names, error identifiers and other user data as-is.
 const text=original.replace(/^([^\p{L}]*)([\p{L}\p{M}\s\/.+-]{2,85})(:\s*)/u,(whole,prefix,label,colon)=>{
    return UI[label.trim()] ? prefix+UI[label.trim()][idx]+colon : whole;
 });
 return text;
}
function installMessages() {
 for(const fn of ['alert','confirm','prompt']){
  const native=window[fn];
  if(typeof native!=='function')continue;
  window[fn]=function(message,...rest){return native.call(window,localMessage(message),...rest)};
 }
}

function uiText(node,lang) {
  const previous=node.__markI18n;
  const current=node.nodeValue;
  const original=previous && (current===previous.translated || current===previous.original)
    ?previous.original:current;
  const stripped=original.trim();
  if(!stripped)return;
  const ix=['en','sq','tr'].indexOf(lang);
  const prefix=(stripped.match(/^[^\p{L}]*/u)||[''])[0];
  const core=stripped.slice(prefix.length);
  let out=stripped,match=UI[stripped];
  if(match)out=ix<0?stripped:match[ix];
  else if(UI[core])out=prefix+(ix<0?core:UI[core][ix]);
  else if(ix>=0) {
    // Translate a fixed UI label even when followed by a dynamic amount or name.
    const label=stripped.match(/^([\p{L}\p{M}\s\/.+-]{2,65})(:\s*)([\s\S]*)$/u);
    if(label && UI[label[1].trim()])out=UI[label[1].trim()][ix]+label[2]+label[3];
  }
  const leadSpace=(original.match(/^\s*/)||[''])[0];
  const trailingSpace=(original.match(/\s*$/)||[''])[0];
  const translated=leadSpace+out+trailingSpace;
  node.__markI18n={original,translated};
  if(current!==translated)node.nodeValue=translated;
}
function translateUi() {
  const lang=effective();
  document.documentElement.lang=lang;
  if(observer)observer.disconnect();
  // <option> without explicit value uses its display text as its data value.
  // Fix it once in the original language before translating the display.
  document.querySelectorAll('select option:not([value])').forEach(o=>{
    if(!o.dataset.markOptionValue) {
      const previous=o.firstChild?.__markI18n?.original;
      const original=(previous||o.textContent||'').trim();
      o.dataset.markOptionValue=original;
      o.value=original;
    }
  });
  const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
  let node;
  while((node=walker.nextNode())){
    const parent=node.parentElement;
    if(!parent||parent.closest('script,style,textarea,[contenteditable],#contractText,#financeDocument,#reportDocument,#markMultiPrintArea,#markLanguagePanel,#markContractLanguages,#markFinanceLanguages,#markReportLanguages'))continue;
    if(node.nodeValue.trim())uiText(node,lang);
  }
  document.querySelectorAll('[placeholder],[aria-label],[title]').forEach(el=>{
    for(const attr of ['placeholder','aria-label','title']){
      if(!el.hasAttribute(attr))continue;
      const key='markOriginal'+attr,prev=el.dataset[key],shown=el.dataset['markRendered'+attr],present=el.getAttribute(attr);
      const original=(prev && (present===prev||present===shown))?prev:present;
      el.dataset[key]=original;
      const translated=UI[original]?.[['en','sq','tr'].indexOf(lang)]||original;
      el.dataset['markRendered'+attr]=translated;
      if(present!==translated)el.setAttribute(attr,translated);
    }
  });
  const opt=$('markUiLanguage');
  if(opt)opt.value=localStorage.getItem(K)||'auto';
  const monthLabel=$('monthTitle');
  if(monthLabel&&typeof month!=='undefined'&&month instanceof Date){
    try{monthLabel.textContent=month.toLocaleDateString(
      lang==='mk'?'mk-MK':lang==='sq'?'sq-AL':lang==='tr'?'tr-TR':'en-GB',
      {month:'long',year:'numeric'}
    )}catch(_e){}
  }
  setPanelLabels();
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
 const knownPackage=typeof PACKAGES!=='undefined'&&PACKAGES[Number(b.package)]&&String(b.services||'').trim()===String(PACKAGES[Number(b.package)][2]||'').trim();
 if(lang!=='mk'&&(!knownPackage||(!isGeneral&&!isWeddingDefault)||!!b.notes||!!b.giftNotes))x+='<p class="mark-translation-warning">'+esc(d.custom)+'</p>';
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
 installMessages();
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