"""TD Photo PRO: conservative, auditable classification and explicitly approved MOVE.

No photo is modified or deleted. RAW+JPEG+XMP companions are moved as a unit.
This is a technical culling assistant, not an identity/eye-open classifier.
"""
from __future__ import annotations
import collections, hashlib, json, os, shutil, threading, time, uuid
from pathlib import Path

SUPPORTED={'.jpg','.jpeg','.png','.tif','.tiff','.webp','.bmp',
 '.nef','.nrw','.cr2','.cr3','.arw','.dng','.raf','.rw2','.orf','.pef','.srw'}
VIEW={'.jpg','.jpeg','.png','.tif','.tiff','.webp','.bmp'}
COLORS={
 '01_NAJDOBRI':'Green', '02_DOBRI':'Yellow', '03_PROVERKA':'Blue',
 '04_MATNI_PROVERKA':'Red','05_SLICNI_PROVERKA':'Purple',
 '06_NEPROCITANI':'Blue'}
LOCK=threading.RLock()
STATUS={'state':'idle','processed':0,'total':0,'message':'Подготвено','job_id':None,'counts':{},'error':''}
DATA=Path(__file__).resolve().parent/'td_chat_data'/'photo_pro'
DATA.mkdir(parents=True,exist_ok=True)

def atomic_json(path,body):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    tmp=path.with_name(path.name+'.tmp')
    with tmp.open('w',encoding='utf-8') as f:
        json.dump(body,f,ensure_ascii=False,indent=2);f.flush();os.fsync(f.fileno())
    os.replace(tmp,path)

def get_job(job_id):
    if not isinstance(job_id,str) or len(job_id)!=32 or any(c not in '0123456789abcdef' for c in job_id):
        raise ValueError('Невалидна задача')
    path=DATA/(job_id+'.json')
    if not path.is_file():raise FileNotFoundError('Не е пронајдена фото-задачата.')
    return json.loads(path.read_text(encoding='utf-8'))

def image_metrics(path):
    """Conservative photo-quality signals, including optional face-region focus.

    Face detection does not identify people or reliably detect closed eyes. If no
    face is detected, no image is penalized solely for that reason.
    """
    from PIL import Image, ImageOps
    from io import BytesIO
    import cv2, numpy as np
    if path.suffix.lower() in VIEW:
        with Image.open(path) as photo:
            im=ImageOps.exif_transpose(photo).convert('RGB').copy()
    else:
        try:import rawpy
        except ImportError:raise ValueError('RAW без JPG бара rawpy; оди во НЕПРОЧИТАНИ')
        with rawpy.imread(str(path)) as raw:
            thumbnail=raw.extract_thumb()
            if thumbnail.format==rawpy.ThumbFormat.JPEG:
                im=Image.open(BytesIO(thumbnail.data)).convert('RGB').copy()
            else:im=Image.fromarray(thumbnail.data).convert('RGB')
    im.thumbnail((1100,1100))
    gray=cv2.cvtColor(np.asarray(im),cv2.COLOR_RGB2GRAY)
    if gray.size==0:raise ValueError('Празна фотографија')
    sharp=float(cv2.Laplacian(gray,cv2.CV_64F).var())
    bright=float(gray.mean())
    clipped_dark=float((gray<=6).mean());clipped_light=float((gray>=249).mean())
    # Multi-scale focus check: image-wide sharpness alone can rate a detailed
    # background above an important soft face. Faces are only positive evidence.
    face_focus=[];face_count=0
    try:
        cascade_path=cv2.data.haarcascades+'haarcascade_frontalface_default.xml'
        cascade=cv2.CascadeClassifier(cascade_path)
        if not cascade.empty():
            for x,y,w,h in cascade.detectMultiScale(gray,scaleFactor=1.14,minNeighbors=5,minSize=(36,36))[:12]:
                face_count+=1
                crop=gray[y:y+h,x:x+w]
                if crop.size:face_focus.append(float(cv2.Laplacian(crop,cv2.CV_64F).var()))
    except (cv2.error,ValueError):pass
    tiny=cv2.resize(gray,(9,8),interpolation=cv2.INTER_AREA)
    dhash=sum(1<<(y*8+x) for y in range(8) for x in range(8) if int(tiny[y,x])>int(tiny[y,x+1]))
    return {'sharp':round(sharp,2),'bright':round(bright,2),
      'dark_fraction':round(clipped_dark,3),'light_fraction':round(clipped_light,3),
      'faces_detected':face_count,'face_focus_best':round(max(face_focus),2) if face_focus else None,
      'face_focus_min':round(min(face_focus),2) if face_focus else None,
      'hash':dhash}

def _groups(source):
    groups=collections.defaultdict(list)
    for p in source.rglob('*'):
        if not p.is_file() or p.is_symlink():continue
        if p.suffix.lower() not in SUPPORTED:continue
        rel=p.relative_to(source)
        groups[(str(rel.parent).casefold(),p.stem.casefold())].append(p)
    return list(groups.values())

def _classify(metrics,strict):
    if metrics is None:return '06_NEPROCITANI'
    s=metrics['sharp'];b=metrics['bright']
    # Thresholds intentionally conservative: uncertain photos NEVER deleted.
    # A blurry detected face is flagged for review, never deleted.
    f=metrics.get('face_focus_best')
    if s<strict*0.55 or (f is not None and f<strict*0.40):return '04_MATNI_PROVERKA'
    if b<38 or b>220 or metrics['dark_fraction']>.65 or metrics['light_fraction']>.55:return '03_PROVERKA'
    if s>=strict*1.7 and 65<=b<=195:return '01_NAJDOBRI'
    if s>=strict*0.85:return '02_DOBRI'
    return '03_PROVERKA'

def scan(source_dir,strict=110, project='Општо'):
    source=Path(source_dir).expanduser().resolve()
    if not source.is_dir():raise ValueError('Не е пронајдена фото-папката')
    if source==Path(source.anchor):raise ValueError('Избери конкретна папка со свадбени фотографии, не цел диск.')
    strict=int(strict)
    if not 30<=strict<=450:raise ValueError('Строгоста треба да биде 30–450')
    with LOCK:
        if STATUS['state'] in ('scanning','moving','undoing'):raise RuntimeError('Веќе има активна фото-задача')
        job_id=uuid.uuid4().hex
        STATUS.update(state='scanning',processed=0,total=0,message='Ги барам фотографиите',job_id=job_id,counts={},error='')
    def worker():
        try:
            grouped=_groups(source)
            with LOCK:STATUS.update(total=len(grouped))
            records=[];counts=collections.Counter();best=[]
            for i,members in enumerate(grouped,1):
                rep=min(members,key=lambda p:(0 if p.suffix.lower() in ('.jpg','.jpeg') else 1 if p.suffix.lower() in VIEW else 2,str(p)))
                error=''
                try:met=image_metrics(rep)
                except Exception as exc:met=None;error=str(exc)
                category=_classify(met,strict)
                if met:
                    # Near-identical composition is REVIEW only; never discard unique moments.
                    duplicate=next((x for x in best if (met['hash']^x['hash']).bit_count()<=3),None)
                    if duplicate and met['sharp']<duplicate['sharp']*0.90 and category!='04_MATNI_PROVERKA':
                        category='05_SLICNI_PROVERKA'
                    else:best.append(met)
                rels=[str(x.relative_to(source)) for x in members]
                # Keep pre-existing RAW XMP companions together, avoid orphaning metadata.
                for p in members:
                    side=p.with_suffix('.xmp')
                    if side.is_file() and str(side.relative_to(source)) not in rels:rels.append(str(side.relative_to(source)))
                record={'files':sorted(set(rels)), 'representative':str(rep.relative_to(source)),
                        'category':category,'color':COLORS[category],'metrics':met,'error':error}
                records.append(record);counts[category]+=1
                with LOCK:STATUS.update(processed=i,message=f'Анализирам {i}/{len(grouped)}',counts=dict(counts))
            job={'id':job_id,'source':str(source),'project':str(project)[:100], 'strict':strict,
                'records':records,'counts':dict(counts),'created':time.strftime('%Y-%m-%dT%H:%M:%S'),
                'status':'ready','output':'','moved':0}
            atomic_json(DATA/(job_id+'.json'),job)
            with LOCK:STATUS.update(state='ready',message='Подготвен преглед. Фотографиите НЕ се преместени.',counts=dict(counts))
        except Exception as exc:
            with LOCK:STATUS.update(state='failed',error=str(exc),message='Неуспешна анализа')
    threading.Thread(target=worker,daemon=True,name='td-photo-pro-scan').start()
    return job_id

def _new_output(source,jobid):
    name=source.name+'_TD_PHOTO_PRO_'+jobid[:8]
    return source.parent/name

def commit(job_id, catalog_confirm=False):
    if not catalog_confirm:raise ValueError('Прво потврди дека фотографиите не се во активен Lightroom каталог или дека имаш backup на каталогот.')
    job=get_job(job_id)
    with LOCK:
        if STATUS['state'] in ('scanning','moving','undoing'):raise RuntimeError('Друга фото-задача е активна')
        if job['status']!='ready':raise ValueError('За оваа задача преместувањето веќе е започнато или завршено.')
        # Reserve BEFORE performing any moves, to make retry safe.
        source=Path(job['source']);output=_new_output(source,job_id)
        if output.exists():raise FileExistsError('Излезната папка веќе постои: '+str(output))
        job.update(status='moving',output=str(output));atomic_json(DATA/(job_id+'.json'),job)
        STATUS.update(state='moving',processed=0,total=sum(len(r['files']) for r in job['records']),message='Преместувам оригинали',error='',job_id=job_id)
    def worker():
        output=Path(job['output']);source=Path(job['source']);moves=[];journal=DATA/(job_id+'.journal.jsonl')
        try:
            for record in job['records']:
                for rel in record['files']:
                    p=source/rel
                    dst=output/record['category']/rel
                    if not p.is_file():raise FileNotFoundError('Изворот недостасува: '+str(p))
                    if dst.exists():raise FileExistsError('Излезот веќе постои: '+str(dst))
                    dst.parent.mkdir(parents=True,exist_ok=True)
                    # Journal BEFORE rename, so interrupted moves can be reconciled.
                    row={'from':str(p),'to':str(dst),'state':'intent'}
                    with journal.open('a',encoding='utf-8') as f:
                        f.write(json.dumps(row,ensure_ascii=False)+'\n');f.flush();os.fsync(f.fileno())
                    os.replace(p,dst)
                    row['state']='done'
                    with journal.open('a',encoding='utf-8') as f:
                        f.write(json.dumps(row,ensure_ascii=False)+'\n');f.flush();os.fsync(f.fileno())
                    moves.append(row)
                    with LOCK:STATUS.update(processed=len(moves),message=f'Преместени {len(moves)} датотеки')
            # No rewrites to camera files. Labels are applied by optional LR plugin.
            items=[{'path':str(output/r['category']/f),'color':r['color']}
                   for r in job['records'] for f in r['files'] if Path(f).suffix.lower() in SUPPORTED]
            import csv
            with (output/'LIGHTROOM_IMPORT.csv').open('w',newline='',encoding='utf-8-sig') as out:
                w=csv.writer(out);w.writerow(['path','color','category']);
                for r in job['records']:
                    for f in r['files']:
                        if Path(f).suffix.lower() in SUPPORTED:w.writerow([str(output/r['category']/f),r['color'],r['category']])
            queue=DATA.parent/'lightroom_queue';queue.mkdir(parents=True,exist_ok=True)
            atomic_json(queue/(job_id+'.json'),{'job_id':job_id,'project':job['project'],'files':items})
            job.update(status='moved',moved=len(moves))
            atomic_json(DATA/(job_id+'.json'),job)
            with LOCK:STATUS.update(state='moved',message='Преместувањето заврши. Lightroom увозот чека активен TD Photo PRO приклучок.')
        except Exception as exc:
            job.update(status='partial_move',moved=len(moves),error=str(exc))
            atomic_json(DATA/(job_id+'.json'),job)
            with LOCK:STATUS.update(state='partial_move',error=str(exc),message='Делумно преместување: провери дневник или врати ги фотографиите')
    threading.Thread(target=worker,daemon=True,name='td-photo-pro-move').start()
    return job['output']

def journal_done(jid):
    p=DATA/(jid+'.journal.jsonl')
    if not p.exists():return []
    done={};order=[]
    for line in p.read_text('utf-8').splitlines():
        row=json.loads(line)
        key=(row['from'],row['to'])
        if key not in done:order.append(key)
        done[key]=row['state']
    # "intent" with completed rename can happen on forced shutdown.
    return [{'from':a,'to':b} for a,b in order if done[(a,b)] in ('intent','done')]

def undo(job_id):
    job=get_job(job_id)
    with LOCK:
        if STATUS['state'] in ('scanning','moving','undoing'):raise RuntimeError('Чекај активната задача да заврши')
        if job['status'] not in ('moved','partial_move','moving','partial_undo'):raise ValueError('Нема доволно податоци во дневникот за враќање')
        STATUS.update(state='undoing',job_id=job_id,processed=0,total=0,message='Ги враќам фотографиите',error='')
    def worker():
        errors=[];recovered=0;entries=journal_done(job_id)
        with LOCK:STATUS['total']=len(entries)
        for row in reversed(entries):
            old,new=Path(row['from']),Path(row['to'])
            try:
                if not new.exists() and old.exists():continue  # already restored
                if not new.exists():raise FileNotFoundError(str(new))
                if old.exists():raise FileExistsError('Оригиналната патека веќе е зафатена: '+str(old))
                old.parent.mkdir(parents=True,exist_ok=True)
                os.replace(new,old);recovered+=1
            except Exception as exc:errors.append(str(exc))
            with LOCK:STATUS['processed']+=1
        job['status']='partial_undo' if errors else 'undone';job['undo_errors']=errors
        atomic_json(DATA/(job_id+'.json'),job)
        # Old queue would point to obsolete paths; remove only our known queue file.
        queue=DATA.parent/'lightroom_queue'/(job_id+'.json')
        if queue.exists():queue.unlink()
        with LOCK:STATUS.update(state=job['status'],message=f'Вратени {recovered} датотеки',error='; '.join(errors[:3]))
    threading.Thread(target=worker,daemon=True,name='td-photo-pro-undo').start()


def recent_jobs(limit=20):
    """On-disk resume list, not tied to the browser tab or server process."""
    rows=[]
    for p in sorted(DATA.glob('*.json'),key=lambda x:x.stat().st_mtime,reverse=True)[:limit]:
        try:
            j=json.loads(p.read_text(encoding='utf-8'))
            if j.get('id') and j.get('status'):
                rows.append({k:j.get(k) for k in ('id','project','source','output','status','created','counts')})
        except (OSError,ValueError):continue
    return rows
