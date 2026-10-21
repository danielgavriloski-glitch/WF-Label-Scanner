import os, csv, shutil, threading, queue, hashlib
from pathlib import Path
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from PIL import Image, ImageFilter, ImageStat, ImageOps

EXT={'.jpg','.jpeg','.png','.tif','.tiff','.bmp'}
def features(path):
    with Image.open(path) as im:
        im=ImageOps.exif_transpose(im).convert('RGB'); im.thumbnail((640,640))
        gray=im.convert('L'); stat=ImageStat.Stat(gray); brightness=stat.mean[0]
        edges=gray.filter(ImageFilter.FIND_EDGES); sharp=ImageStat.Stat(edges).var[0]
        thumb=gray.resize((9,8)); px=list(thumb.getdata()); bits=[]
        for y in range(8):
            for x in range(8): bits.append(px[y*9+x]>px[y*9+x+1])
        dh=sum(int(v)<<i for i,v in enumerate(bits))
        return brightness,sharp,dh

def hamming(a,b):return (a^b).bit_count()

def analyze(src,dst,log):
    files=sorted(p for p in Path(src).rglob('*') if p.is_file() and p.suffix.lower() in EXT)
    if not files:raise ValueError('Не се пронајдени JPG/PNG/TIFF фотографии.')
    result=[]; groups=[]
    for n,p in enumerate(files,1):
        try:
            bright,sharp,dhash=features(p)
            match=next((g for g in groups if hamming(g['hash'],dhash)<=4),None)
            if match:
                if sharp>match['sharp']:
                    result[match['idx']]['category']='03_DUPLIKATI_PROVERKA'
                    match.update(hash=dhash,sharp=sharp,idx=len(result))
                    cat='01_NAJDOBRI_KANDIDATI'
                else:cat='03_DUPLIKATI_PROVERKA'
            else:
                cat='01_NAJDOBRI_KANDIDATI'
                groups.append({'hash':dhash,'sharp':sharp,'idx':len(result)})
            if cat!='03_DUPLIKATI_PROVERKA':
                if sharp<100:cat='02_MATNI_PROVERKA'
                elif bright<55:cat='04_TEMNI_PROVERKA'
                elif bright>215:cat='05_PRESVETLI_PROVERKA'
            result.append(dict(source=str(p),category=cat,brightness=round(bright,1),sharpness=round(sharp,1),error=''))
        except Exception as e:result.append(dict(source=str(p),category='06_GRESKI',brightness='',sharpness='',error=str(e)))
        if n%10==0 or n==len(files):log(f'Анализирани {n}/{len(files)}')
    dst=Path(dst);dst.mkdir(parents=True,exist_ok=True)
    for n,item in enumerate(result,1):
        original=Path(item['source']); folder=dst/item['category'];folder.mkdir(exist_ok=True)
        name=f'{n:05d}_{original.name}'; target=folder/name
        if target.exists():target=folder/f'{n:05d}_{hashlib.md5(str(original).encode()).hexdigest()[:6]}_{original.name}'
        shutil.copy2(original,target)
        item['copy']=str(target)
    with (dst/'TD_PHOTO_REPORT.csv').open('w',newline='',encoding='utf-8-sig') as f:
        w=csv.DictWriter(f,fieldnames=['source','copy','category','brightness','sharpness','error']);w.writeheader();w.writerows(result)
    return len(result)

root=tk.Tk();root.title('TD AI PHOTO — Safe Selector Test');root.geometry('750x450');root.configure(bg='#101827')
style=ttk.Style();style.theme_use('clam')
source=tk.StringVar();out=tk.StringVar();q=queue.Queue()
tk.Label(root,text='TD AI PHOTO SELECTOR',font=('Segoe UI',19,'bold'),bg='#101827',fg='#25d6bc').pack(pady=(20,4))
tk.Label(root,text='Самостоен фото модул • не менува видео, Lightroom или оригинали',bg='#101827',fg='white').pack(pady=(0,22))
for label,var in [('Папка со фотографии',source),('Излезна папка (различна од изворната)',out)]:
    frame=tk.Frame(root,bg='#101827');frame.pack(fill='x',padx=25,pady=8)
    tk.Label(frame,text=label,bg='#101827',fg='white',width=34,anchor='w').pack(side='left')
    ttk.Entry(frame,textvariable=var).pack(side='left',fill='x',expand=True,padx=6)
    ttk.Button(frame,text='Избери',command=lambda v=var:v.set(filedialog.askdirectory() or v.get())).pack(side='left')
status=tk.StringVar(value='Подготвено за тест со 30–50 фотографии.')
tk.Label(root,textvariable=status,bg='#101827',fg='#25d6bc',wraplength=700).pack(pady=18)
def poll():
    try:
        while True:
            kind,msg=q.get_nowait()
            if kind=='progress':status.set(msg)
            else:
                btn.config(state='normal');status.set(msg)
                if kind=='error':messagebox.showerror('TD PHOTO',msg)
                else:messagebox.showinfo('TD PHOTO',msg)
    except queue.Empty:pass
    root.after(200,poll)
def run():
    a=Path(source.get());b=Path(out.get())
    if not a.is_dir() or not str(b):messagebox.showwarning('Папки','Избери две валидни папки.');return
    if a.resolve()==b.resolve() or a.resolve() in b.resolve().parents or b.resolve() in a.resolve().parents:
        messagebox.showwarning('Безбедност','Излезната папка мора да биде надвор од изворната папка.');return
    btn.config(state='disabled');status.set('Анализа во тек...')
    def worker():
        try:n=analyze(a,b,lambda s:q.put(('progress',s)));q.put(('done',f'Готово: {n} фотографии. Провери ги папките и TD_PHOTO_REPORT.csv.'))
        except Exception as e:q.put(('error',str(e)))
    threading.Thread(target=worker,daemon=True).start()
btn=ttk.Button(root,text='СТАРТ — АНАЛИЗА И КОПИРАЊЕ',command=run);btn.pack(pady=8)
tk.Label(root,text='ПРОБНА селекција: дупликатите се проценуваат според сличност; сите категории бараат човечка проверка.\nНема RAW/NEF, лица/очи, свадбени сцени или автоматско запишување во Lightroom.',bg='#101827',fg='#aab5c8',justify='center',wraplength=700).pack(pady=20)
root.after(200,poll);root.mainloop()
