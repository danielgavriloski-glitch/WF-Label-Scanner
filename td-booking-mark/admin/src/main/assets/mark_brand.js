(()=>{'use strict';
// Optional user-selected logo stays ONLY on this device; the MarK M app identity stays fixed.
const KEY='tdMarKBrandSettings_v1',lookup=id=>document.getElementById(id);
const logoTag=document.createElement('img');logoTag.id='tdMarkBrandBadge';logoTag.src='mark_m_web.png';logoTag.alt='MarK M';
const hdr=document.querySelector('header');if(hdr){hdr.appendChild(logoTag)}
const customer=document.createElement('img');customer.id='markCustomerLogo';customer.alt='Лого на корисник';if(hdr)hdr.appendChild(customer);
const brandLabel=document.createElement('span');brandLabel.id='markBrandLabel';brandLabel.style.cssText='display:block;font-size:12px;opacity:.9;margin-top:3px;letter-spacing:.04em';if(hdr)hdr.appendChild(brandLabel);
const btn=document.createElement('button');btn.id='markSettingsBtn';btn.type='button';btn.title='MarK изглед и лого';btn.innerHTML='<img src="mark_m_web.png" alt="MarK">';document.body.append(btn);
const overlay=document.createElement('div');overlay.id='markBrandSettings';overlay.innerHTML=`<div id="markBrandBody" role="dialog" aria-label="MarK изглед"><h2>MarK · TD Booking</h2>
<p>Се задржува препознатливото <b>M</b> на апликацијата. Основниот изглед е TD Production; по желба додади свое лого и избери бои.</p>
<label for="markBrandName">Назив на бренд</label><input id="markBrandName" maxlength="75" placeholder="TD Production">
<label for="markBrandFile">Лого од телефон (PNG/JPG)</label><input type="file" id="markBrandFile" accept="image/png,image/jpeg,image/webp"><div id="markBrandPreview"></div>
<label for="markBrandAccent">Главна боја</label><input type="color" id="markBrandAccent" value="#167a74">
<label for="markBrandGold">Златен акцент</label><input type="color" id="markBrandGold" value="#be9858">
<div class="brandActions"><button type="button" id="markBrandSave">Зачувај</button><button type="button" class="secondary" id="markBrandReset">TD стандард</button><button type="button" class="secondary" id="markBrandClose">Затвори</button></div><p>Логото и боите се зачувуваат локално на уредот. Резервациите, договорите и уплатите не се менуваат.</p></div>`;
document.body.append(overlay);
const D={name:'TD Production',accent:'#167a74',gold:'#be9858',logo:''};let chosen='';
const safeHex=s=>/^#[0-9a-f]{6}$/i.test(s||'')?s:D.accent;
const hexRgb=s=>[1,3,5].map(n=>parseInt(s.slice(n,n+2),16));
function shade(rgb,k){return '#'+rgb.map(v=>Math.max(0,Math.min(255,Math.round(v*k))).toString(16).padStart(2,'0')).join('')}
function apply(v){const accent=safeHex(v.accent),gold=/^#[0-9a-f]{6}$/i.test(v.gold||'')?v.gold:D.gold;
 document.documentElement.style.setProperty('--mb-accent',accent);document.documentElement.style.setProperty('--mb-deep',shade(hexRgb(accent),.65));document.documentElement.style.setProperty('--mb-gold',gold);
 const im=lookup('markCustomerLogo');if(v.logo&&v.logo.startsWith('data:image/')){im.src=v.logo;im.classList.add('visible')}else{im.removeAttribute('src');im.classList.remove('visible')}
 lookup('markBrandName').value=(v.name||D.name).slice(0,75);if(lookup('markBrandLabel'))lookup('markBrandLabel').textContent=(v.name||D.name).slice(0,75);lookup('markBrandAccent').value=accent;lookup('markBrandGold').value=gold;
 // Existing TD logo remains visible, and custom logo is displayed next to it.
}
function state(){try{return {...D,...JSON.parse(localStorage.getItem(KEY)||'{}')}}catch(_){return {...D}}}
apply(state());if(!window.TDNative||typeof window.TDNative.marKInfo!=='function'){lookup('markBrandFile').disabled=true;const hint=document.createElement('p');hint.textContent='Поставување лого од Галерија бара целосната native v6.14. Боите и името можеш да ги менуваш и сега.';lookup('markBrandFile').after(hint)}btn.onclick=()=>{overlay.classList.add('open')};lookup('markBrandClose').onclick=()=>overlay.classList.remove('open');overlay.onclick=e=>{if(e.target===overlay)overlay.classList.remove('open')};
lookup('markBrandFile').onchange=e=>{const file=e.target.files&&e.target.files[0];if(!file)return;if(file.size>8*1024*1024){alert('Лого до 8 MB.');return}if(!/^image\/(jpeg|png|webp)$/.test(file.type)){alert('Избери PNG или JPG.');return}
 const reader=new FileReader();reader.onload=()=>{const img=new Image();img.onload=()=>{let c=document.createElement('canvas'),scale=Math.min(1,420/Math.max(img.width,img.height));c.width=Math.max(1,Math.round(img.width*scale));c.height=Math.max(1,Math.round(img.height*scale));c.getContext('2d').drawImage(img,0,0,c.width,c.height);chosen=c.toDataURL('image/png');lookup('markBrandPreview').textContent='✓ Логото е подготвено';
 // Suggest a legible theme color drawn from opaque colorful parts of the image.
 const ctx=c.getContext('2d'),pixels=ctx.getImageData(0,0,c.width,c.height).data;let best=null,bestScore=0;
 for(let i=0;i<pixels.length;i+=Math.max(4,Math.floor(pixels.length/3000/4)*4)){let r=pixels[i],g=pixels[i+1],b=pixels[i+2],a=pixels[i+3];let range=Math.max(r,g,b)-Math.min(r,g,b),lum=(r+g+b)/3,score=range*(a/255);if(lum>30&&lum<225&&score>bestScore){best=[r,g,b];bestScore=score}}
 if(best&&bestScore>30)lookup('markBrandAccent').value=shade(best,.77);
 };img.src=reader.result};reader.readAsDataURL(file)};
lookup('markBrandSave').onclick=()=>{const old=state();let next={name:lookup('markBrandName').value.trim()||D.name,accent:lookup('markBrandAccent').value,gold:lookup('markBrandGold').value,logo:chosen||old.logo};try{localStorage.setItem(KEY,JSON.stringify(next));apply(next);overlay.classList.remove('open')}catch(_){alert('Не можам да го зачувам логото; пробај со помала слика.')}};
lookup('markBrandReset').onclick=()=>{chosen='';localStorage.removeItem(KEY);lookup('markBrandPreview').textContent='';apply(D)};
})();
