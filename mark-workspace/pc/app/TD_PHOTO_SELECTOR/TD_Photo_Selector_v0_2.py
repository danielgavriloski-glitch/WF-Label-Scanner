import os, csv, shutil, hashlib, threading, tkinter as tk
from tkinter import filedialog, messagebox, ttk
from pathlib import Path
import cv2
import numpy as np

EXT={'.jpg','.jpeg','.png','.tif','.tiff'}
def scan(src,dest,progress):
    photos=[p for p in Path(src).rglob('*') if p.is_file() and p.suffix.lower() in EXT and not Path(dest) in p.parents]
    folders=['01_UBAVI','02_MATNI','03_DUPLI','04_TEMNI','05_PREGORENI','07_ZA_PROVERKA']
    for f in folders:(Path(dest)/f).mkdir(parents=True,exist_ok=True)
    records=[];seen={}
    for i,p in enumerate(photos,1):
        try:
            im=cv2.imdecode(np.fromfile(str(p),dtype=np.uint8),cv2.IMREAD_COLOR)
            if im is None: raise ValueError('Unsupported image')
            h,w=im.shape[:2]; gray=cv2.cvtColor(im,cv2.COLOR_BGR2GRAY)
            small=cv2.resize(gray,(16,16),interpolation=cv2.INTER_AREA)
            bits=(small>np.median(small)).astype(np.uint8).flatten()
            sharp=float(cv2.Laplacian(cv2.resize(gray,(min(w,1200),min(h,1200))),cv2.CV_64F).var())
            lum=float(np.mean(gray)); key=hashlib.sha256(p.read_bytes()).hexdigest()
            if key in seen: category='03_DUPLI';reason='exact file duplicate'
            elif lum<42:category='04_TEMNI';reason='low average brightness'
            elif lum>225:category='05_PREGORENI';reason='high average brightness'
            elif sharp<55:category='02_MATNI';reason='low global sharpness'
            else:
                near=[(np.count_nonzero(bits-b),other) for b,other in seen.values() if isinstance(b,np.ndarray)]
                if near and min(x[0] for x in near)<=5:category='07_ZA_PROVERKA';reason='visually similar; manual review needed'
                else:category='01_UBAVI';reason='basic technical checks passed (not aesthetic judgment)'
            if key not in seen:seen[key]=(bits,str(p))
            target=Path(dest)/category/(p.stem+'_'+hashlib.md5(str(p).encode()).hexdigest()[:8]+p.suffix)
            shutil.copy2(p,target)
            records.append([str(p),str(target),category,round(sharp,1),round(lum,1),reason])
        except Exception as e:records.append([str(p),'','ERROR','','',str(e)])
        progress(i,len(photos))
    with open(Path(dest)/'TD_Photo_Report.csv','w',newline='',encoding='utf-8-sig') as f:
        wr=csv.writer(f);wr.writerow(['source','copy','category','sharpness','brightness','reason']);wr.writerows(records)
    return len(photos)

app=tk.Tk();app.title('TD AI Photo Selector - Safe Test v0.2');app.geometry('670x285')
src=tk.StringVar();dst=tk.StringVar();status=tk.StringVar(value='Choose source and a separate output folder. Originals are never moved.')
for j,(label,var) in enumerate([('Wedding photos',src),('Output folder',dst)]):
    tk.Label(app,text=label).grid(row=j,column=0,padx=8,pady=12,sticky='w')
    tk.Entry(app,textvariable=var,width=62).grid(row=j,column=1)
    tk.Button(app,text='Browse',command=lambda v=var:v.set(filedialog.askdirectory() or v.get())).grid(row=j,column=2,padx=8)
bar=ttk.Progressbar(app,length=500);bar.grid(row=2,column=0,columnspan=3,pady=18)
tk.Label(app,textvariable=status,wraplength=620).grid(row=3,column=0,columnspan=3)
def start():
    s,d=src.get(),dst.get()
    if not os.path.isdir(s) or not d or os.path.abspath(s)==os.path.abspath(d) or os.path.commonpath([os.path.abspath(s),os.path.abspath(d)])==os.path.abspath(s):
        messagebox.showerror('Check folders','Choose a valid source and a separate output folder outside the source.');return
    btn.config(state='disabled')
    def prog(i,n):app.after(0,lambda: (bar.configure(maximum=max(n,1),value=i),status.set(f'Checking {i}/{n}')))
    def job():
        try:
            n=scan(s,d,prog);app.after(0,lambda:messagebox.showinfo('Complete',f'Copied {n} photos. Review all folders and CSV before using results.'))
        except Exception as e:app.after(0,lambda:messagebox.showerror('Error',str(e)))
        finally:app.after(0,lambda:btn.config(state='normal'))
    threading.Thread(target=job,daemon=True).start()
btn=tk.Button(app,text='START SAFE PHOTO SORT',command=start,font=('Segoe UI',12,'bold'));btn.grid(row=4,column=0,columnspan=3,pady=12)
app.mainloop()
