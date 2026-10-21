"""MarK wedding media preparation v2.7 — guided workflow and safe long timeline and gallery filters, separate from Booking/Sync.

Authenticates with existing MarK PIN session; locally stored manifests only.
Copies/moves require explicit plan and second confirmation. No deletion API.
"""
from __future__ import annotations
import hashlib
import json
import mimetypes
import os
import re
import shutil
import subprocess
import tempfile
import ctypes
import threading
import time
import uuid
from datetime import datetime
from pathlib import Path
from flask import Blueprint, Response, jsonify, request, send_file

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'data' / 'wedding_media_step1'
UI = Path(__file__).with_name('wedding_media_v1.js')
LOCK = threading.RLock()
JOBS = {}
AUTH = LOCAL = None
VIDEO = {'.mp4','.mov','.m4v','.avi','.mts','.m2ts','.mkv','.wmv','.mpg','.mpeg'}
PHOTO = {'.jpg','.jpeg','.png','.tif','.tiff','.webp','.nef','.nrw','.cr2','.cr3','.arw','.dng','.raf','.rw2','.orf'}
RAW = {'.nef','.nrw','.cr2','.cr3','.arw','.dng','.raf','.rw2','.orf'}
CAMERAS = {'Cam 1','Cam 2','Cam 3','Cam 4','Drone','Crane','Other'}
SCENES = {'Зет','Невеста','Црква','Ресторан','Дрон','Друго','За проверка'}
DEFAULT_LEGEND = [
    {'name':'Зет','color':'сина','active':True},
    {'name':'Невеста','color':'виолетова','active':True},
    {'name':'Црква','color':'зелена','active':True},
    {'name':'Ресторан','color':'жолта','active':True},
    {'name':'Дрон','color':'црвена','active':True},
    {'name':'Друго','color':'без боја','active':True},
    {'name':'За проверка','color':'жолта','active':True},
]
def legend_for(p):
    # Historic projects lazily inherit defaults; inactive names remain valid
    # for already tagged materials to prevent data loss on removal.
    return p.get('legend') if isinstance(p.get('legend'), list) else [dict(x) for x in DEFAULT_LEGEND]

def valid_legend_name(value):
    if not isinstance(value, str):return False
    return bool(1 <= len(value.strip()) <= 48 and value == value.strip()
                and not any(c in value for c in ('/', '\\', '\x00', '\r', '\n', '<', '>')))

PHOTO_GROUPS = {'Убави','Заматени','Дупликати','За проверка','За обработка','Албум'}
COLORS = {'зелена','жолта','црвена','сина','виолетова','без боја'}
MODES = {'COPY','MOVE'}
BLUEPRINT = Blueprint('mark_wedding_media_v1',__name__)

def guard(write=False):
    if AUTH is None or not AUTH(): return jsonify(error='Потребна е MarK PIN најава'),401
    if LOCAL is None or not LOCAL(): return jsonify(error='Дозволено само на локалниот PC'),403
    if write:
        if request.headers.get('X-TD-Client') != 'TD-v1.4':
            return jsonify(error='Непознат PC клиент'),403
        origin=request.headers.get('Origin','')
        if origin and origin not in ('http://127.0.0.1:8765','http://localhost:8765'):
            return jsonify(error='Невалиден потекло'),403
    return None

def error(message,code=400):return jsonify(error=message),code

def slug(s):
    s=re.sub(r'[^\w\- ]','',str(s),flags=re.UNICODE).strip().replace(' ','_')
    return s[:70] or 'Wedding'

def metadata_file(pid):
    if not re.fullmatch(r'[0-9a-f]{16}',pid):raise ValueError('Невалиден ID')
    return DATA/'projects'/(pid+'.json')

def save(p):
    dest=metadata_file(p['id']);dest.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix='.wedding-',suffix='.tmp',dir=str(dest.parent))
    try:
        with os.fdopen(fd,'w',encoding='utf8') as f:
            json.dump(p,f,ensure_ascii=False,indent=2);f.flush();os.fsync(f.fileno())
        os.replace(tmp,dest)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)
    # A copy of the manifest lives with the wedding project, so MarK Director
    # can later use camera + scene assignments and the exact media file mapping.
    output=p.get('output')
    if output:
        folder=Path(output)
        if folder.is_dir():
            sidecar=folder/('MarK_Project_'+p['id']+'.json')
            fd,side_tmp=tempfile.mkstemp(prefix='.mark-project-',suffix='.tmp',dir=str(folder))
            try:
                with os.fdopen(fd,'w',encoding='utf8') as sf:
                    json.dump(p,sf,ensure_ascii=False,indent=2)
                    sf.flush();os.fsync(sf.fileno())
                os.replace(side_tmp,sidecar)
            finally:
                if os.path.exists(side_tmp):os.unlink(side_tmp)

def load(pid):
    f=metadata_file(pid)
    if not f.is_file():raise FileNotFoundError('Проектот не постои')
    return json.loads(f.read_text('utf8'))

def get_item(p,item_id):
    for item in p['items']:
        if item['id']==item_id:return item
    raise ValueError('Непознат клип/фотографија')

def _inside(child,parent):
    try:return child==parent or parent in child.parents
    except Exception:return False

def resolve_folder(value):
    if not isinstance(value,str) or len(value)>700:raise ValueError('Невалидна патека')
    p=Path(value.strip()).expanduser().resolve()
    if not p.is_dir():raise ValueError('Папката не постои: '+str(p))
    return p

def item_key(kind,camera,files):
    key='|'.join([kind,camera]+[str(f).casefold() for f in sorted(files,key=str)])
    return hashlib.sha256(key.encode('utf8')).hexdigest()[:20]

def choose_preview(paths):
    return next((str(f) for f in paths if Path(f).suffix.lower() not in RAW),str(paths[0]))

def natural_key(value):
    """Natural case-insensitive ordering: DSC_2 precedes DSC_10."""
    return tuple((1, int(part)) if part.isdigit() else (0, part.casefold())
                 for part in re.split(r'(\d+)', str(value)))

def item_order(item):
    return (natural_key(item['fileName']),
            natural_key(item.get('relativeFolder') or item.get('sourceFolder') or ''),
            natural_key(item.get('camera', '')),
            item['id'])

def _scan(folder,kind,camera,limit=15000):
    """Read every normally accessible nested directory, irrespective of depth.

    No file is altered here. Symlinked directories, hidden cache directories and
    MarK-sorted output directories are skipped to avoid loops/duplicates.
    Unreadable directories are reported rather than silently omitted.
    """
    allowed=VIDEO if kind=='video' else PHOTO
    paths=[]
    errors=[]
    def walk_error(exc):
        errors.append('%s: %s' % (getattr(exc,'filename','?'),exc))
    for walk_root,dirs,files in os.walk(folder,topdown=True,followlinks=False,onerror=walk_error):
        dirs[:]=sorted((d for d in dirs
                        if not d.startswith('.')
                        and not d.startswith('MarK_Sorted_')
                        and not (Path(walk_root)/d).is_symlink()),key=natural_key)
        for name in files:
            path=Path(walk_root)/name
            if path.suffix.lower() in allowed and path.is_file() and not path.is_symlink():
                paths.append(path)
                if len(paths)>limit:
                    raise ValueError('Повеќе од %d %s фајлови; избери помала главна папка' % (limit,kind))
    if errors:
        raise ValueError('Дел од потпапките не може да се прочитаат. Нема делумен увоз: '+'; '.join(errors[:3]))
    # Important: global filename order, NOT traversal/folder order.
    paths.sort(key=lambda f:(natural_key(f.name),natural_key(str(f.relative_to(folder)))))
    groups={}
    for f in paths:
        # RAW+JPG with the same filename stem remain one user selection.
        # Sibling RAW/JPG folders under the same shoot location can be paired.
        base=f.parent
        if base.name.casefold() in ('raw','jpg','jpeg','nef','arw','cr3','dng'):base=base.parent
        key=((str(base).casefold(),f.stem.casefold()) if kind=='photo' else str(f).casefold())
        groups.setdefault(key,[]).append(f)
    result=[]
    for files in groups.values():
        files.sort(key=lambda f:(f.suffix.lower() in RAW,natural_key(str(f))))
        first=files[0]
        source_id=item_key(kind,camera,files)
        relative_folder=str(first.parent.relative_to(folder))
        text=' '.join(str(x) for x in files).casefold()
        suggestion='За проверка';col='жолта'
        for keys,scene,color in [(['groom','zet','зет'],'Зет','сина'),(['bride','nevesta','невеста'],'Невеста','виолетова'),(['church','crkva','црква','ceremony'],'Црква','зелена'),(['restaurant','restoran','ресторан','party','reception'],'Ресторан','зелена'),(['drone','dji','дрон'],'Дрон','сина')]:
            if any(word in text for word in keys): suggestion,col=scene,color;break
        result.append({'id':source_id,'kind':kind,'camera':camera,'fileName':first.name,
                       'files':[str(x) for x in files],'preview':choose_preview(files),
                       'sourceFolder':str(folder),'relativeFolder':relative_folder,
                       'sourceRelativePath':str(first.relative_to(folder)),
                       'scene':None,'category':None,'color':'без боја',
                       'suggestedScene':suggestion,
                       'suggestedCategory':'За проверка' if kind=='photo' else None,
                       'suggestedColor':col,'analysis':{},'confirmed':False,'sorted':None})
    result.sort(key=item_order)
    return result

def ffmpeg_path():
    # Resolve an existing, locally installed Windows FFmpeg without downloads,
    # PATH changes, or arbitrary recursive disk traversal.
    options=[ROOT/'runtime'/'ffmpeg'/'bin'/'ffmpeg.exe',
             ROOT/'runtime'/'ffmpeg'/'ffmpeg.exe',
             ROOT/'runtime'/'bin'/'ffmpeg.exe',
             ROOT/'runtime'/'ffmpeg.exe',
             ROOT/'tools'/'ffmpeg'/'bin'/'ffmpeg.exe',
             ROOT/'tools'/'ffmpeg.exe',
             ROOT/'ffmpeg.exe',
             ROOT/'bin'/'ffmpeg.exe',
             ROOT/'app'/'bin'/'ffmpeg.exe',
             Path(os.environ.get('LOCALAPPDATA',''))/'Programs'/'FFmpeg'/'bin'/'ffmpeg.exe']
    for name in ('ffmpeg.exe','ffmpeg'):
        location=shutil.which(name)
        if location:options.append(Path(location))
    try:
        import imageio_ffmpeg
        options.append(Path(imageio_ffmpeg.get_ffmpeg_exe()))
    except (ImportError,RuntimeError,FileNotFoundError,OSError):
        pass
    return next((str(p) for p in options if p.is_file() and os.access(p,os.R_OK)),None)

def image_tools():
    try:
        from PIL import Image,ImageOps
        return Image,ImageOps
    except ImportError:return None,None

def read_image(path):
    Image,ImageOps=image_tools()
    if not Image:raise RuntimeError('Недостасува Pillow за фото анализа')
    if path.suffix.lower() in RAW:
        try:
            import rawpy
            with rawpy.imread(str(path)) as r:
                return Image.fromarray(r.postprocess(half_size=True,no_auto_bright=True))
        except ImportError:raise RuntimeError('За RAW преглед е потребен rawpy или JPG со исто име')
    im=Image.open(path);im=ImageOps.exif_transpose(im)
    im.thumbnail((2000,2000));return im.convert('RGB')

def _frame_quality(path):
    """Suggest a non-black frame, not semantic AI. Pillow is optional."""
    Image,_=image_tools()
    if Image is None:
        return 100.0
    from PIL import ImageStat
    with Image.open(path) as im:
        im=im.convert('L')
        im.thumbnail((240,150))
        stats=ImageStat.Stat(im)
        mean=float(stats.mean[0]);contrast=float(stats.stddev[0])
        return mean + min(contrast,50)*1.5 if mean>=12 else mean*.3

def _cv2_video_frame(path,out,positions):
    """Optional local backup decoder, no installs or network calls."""
    try:
        import cv2
    except (ImportError, OSError):
        return False
    cap=cv2.VideoCapture(str(path))
    if not cap.isOpened():
        cap.release()
        return False
    best=None;quality=-1
    try:
        for position in positions:
            cap.set(cv2.CAP_PROP_POS_MSEC,position*1000)
            ok,frame=cap.read()
            if not ok or frame is None:continue
            h,w=frame.shape[:2]
            if not w or not h:continue
            if w>640:frame=cv2.resize(frame,(640,max(2,int(h*640/w)//2*2)))
            tmp=out.with_name(out.stem+'.'+uuid.uuid4().hex+'.jpg')
            try:
                if not cv2.imwrite(str(tmp),frame,[cv2.IMWRITE_JPEG_QUALITY,82]):continue
                score=_frame_quality(tmp)
                if score>quality:
                    if best:best.unlink(missing_ok=True)
                    best=tmp;quality=score;tmp=None
                if score>=24:break
            finally:
                if tmp:tmp.unlink(missing_ok=True)
        if not best:return False
        os.replace(best,out)
        best=None
        return True
    finally:
        cap.release()
        if best:best.unlink(missing_ok=True)

def run_media_tool_hidden(args, **kwargs):
    """Keep FFmpeg/FFprobe console windows hidden on Windows.

    Do NOT use shell=True; capture errors and preserve timeouts/exit codes.
    Do not change how subprocesses run on other operating systems.
    """
    if os.name == 'nt':
        kwargs['creationflags'] = subprocess.CREATE_NO_WINDOW
        startup = subprocess.STARTUPINFO()
        startup.dwFlags |= subprocess.STARTF_USESHOWWINDOW
        startup.wShowWindow = subprocess.SW_HIDE
        kwargs['startupinfo'] = startup
    return subprocess.run(args, **kwargs)

def video_frame(path,out,seconds=0.0):
    """Extract a real preview frame with installed FFmpeg, optional OpenCV.

    Atomic, persistent JPEG cache; retries later timestamps for black intros.
    No downloads, originals unchanged. Browser decoder remains a third fallback.
    """
    ff=ffmpeg_path()
    out=Path(out)
    positions=([0.0,0.35,1.0,2.0] if seconds<1 else
               [seconds,seconds+1.0,0.5])
    best=None;quality=-1.0;errs=[]
    try:
        if ff:
            for position in positions:
                tmp=out.with_name(out.stem+'.'+uuid.uuid4().hex+'.jpg')
                try:
                    args=[ff,'-nostdin','-hide_banner','-loglevel','error',
                          '-ss',str(position),'-i',str(path),'-map','0:v:0',
                          '-frames:v','1','-vf','scale=640:-2',
                          '-q:v','4','-y',str(tmp)]
                    proc=run_media_tool_hidden(args,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,
                                        timeout=22,check=False)
                    if proc.returncode!=0 or not tmp.is_file() or tmp.stat().st_size<350:
                        errs.append(proc.stderr.decode('utf8','replace')[:260]);continue
                    score=_frame_quality(tmp)
                    if score>quality:
                        if best:best.unlink(missing_ok=True)
                        best=tmp;quality=score;tmp=None
                    if score>=24:break
                except (OSError,ValueError,subprocess.TimeoutExpired) as exc:
                    errs.append(str(exc)[:260])
                finally:
                    if tmp:tmp.unlink(missing_ok=True)
            if best:
                os.replace(best,out)
                best=None
                return
        if _cv2_video_frame(path,out,positions):return
        raise RuntimeError('FFmpeg/OpenCV не можат да извлечат кадар од '+path.suffix.upper()
                           +'. Прелистувачот ќе проба резервен преглед. '+ '; '.join(errs[:2]))
    finally:
        if best:best.unlink(missing_ok=True)

def photo_score(path):
    im=read_image(path)
    try:
        from PIL import ImageStat,ImageFilter
        gray=im.convert('L');gray.thumbnail((500,500))
        edges=gray.filter(ImageFilter.FIND_EDGES)
        stat=ImageStat.Stat(edges)
        sharp=min(100.0,stat.stddev[0]*3.7)
        mean=ImageStat.Stat(gray).mean[0]
        exposure=max(0,100-abs(mean-124)*0.75)
        imsmall=gray.resize((9,8));pixels=list(imsmall.getdata())
        bits=0
        for y in range(8):
            for x in range(8):bits=(bits<<1)|int(pixels[y*9+x]>pixels[y*9+x+1])
        face=None
        try:
            import cv2,numpy
            gray_array=numpy.array(gray)
            cascade=cv2.CascadeClassifier(str(Path(cv2.data.haarcascades)/'haarcascade_frontalface_default.xml'))
            faces=cascade.detectMultiScale(gray_array,scaleFactor=1.12,minNeighbors=5,minSize=(26,26))
            if len(faces):
                focus=[]
                for x,y,w,h in faces:
                    roi=gray_array[y:y+h,x:x+w]
                    focus.append(float(cv2.Laplacian(roi,cv2.CV_64F).var()))
                face={'detected':int(len(faces)),'focusEstimate':round(max(focus),1)}
        except (ImportError,RuntimeError,ValueError,AttributeError):pass
        value=round(sharp*.75+exposure*.25,1)
        blur=sharp<15
        return {'sharpness':round(sharp,1),'exposure':round(exposure,1),'score':value,
                'blurSuggestion':blur,'perceptualHash':format(bits,'016x'),
                'face':face,'method':'LOCAL_HEURISTIC_NOT_GUARANTEED'}
    finally:im.close()

def ffprobe_path():
    options=[ROOT/'runtime'/'ffmpeg'/'bin'/'ffprobe.exe', ROOT/'runtime'/'ffprobe.exe',
             ROOT/'tools'/'ffprobe.exe', ROOT/'ffprobe.exe']
    options += [Path(x) for x in [shutil.which('ffprobe')] if x]
    return next((str(path) for path in options if path.is_file()), None)

def video_score(path):
    result={'method':'LOCAL_METADATA_NOT_SEMANTIC_AI', 'sceneAnalysis':'Предлог по папка/име; визуелна AI класификација не е активна'}
    ff=ffprobe_path()
    if ff:
        cmd=[ff,'-v','error','-show_format','-show_streams','-of','json',str(path)]
        run=run_media_tool_hidden(cmd,capture_output=True,text=True,timeout=15,check=False)
        if run.returncode==0:
            meta=json.loads(run.stdout)
            duration=float(meta.get('format',{}).get('duration',0) or 0)
            streams=[x for x in meta.get('streams',[]) if x.get('codec_type')=='video']
            st=streams[0] if streams else {}
            result.update(durationSeconds=round(duration,2), width=st.get('width'),height=st.get('height'),
                          codec=st.get('codec_name'),shortSpotEligible=duration<=300 if duration>0 else None)
    else:
        result['warning']='FFprobe не е пронајден; траењето не е потврдено'
    return result

def analyze_worker(pid,job,only_pending=False):
    try:
        with LOCK:
            p=load(pid)
            items=[(x['id'],x['kind'],x['preview']) for x in p['items']
                   if not only_pending or not x.get('analysis')]
            job.update(total=len(items),done=0)
        scored=[];updates=[]
        def flush():
            nonlocal updates
            if not updates:return
            # Every batch uses the latest manifest, to preserve user edits made during analysis.
            with LOCK:
                fresh=load(pid)
                by_id={it['id']:it for it in fresh['items']}
                for item_id,info,cat,color in updates:
                    it=by_id.get(item_id)
                    if not it:continue
                    it['analysis']=info
                    if cat:
                        it['suggestedCategory']=cat
                        it['suggestedQualityColor']=color
                save(fresh)
            updates=[]
        for item_id,kind,preview in items:
            info={};category=None;color=None
            try:
                if kind=='photo':
                    info=photo_score(Path(preview))
                    category='Заматени' if info['blurSuggestion'] else 'Убави'
                    color='црвена' if info['blurSuggestion'] else 'зелена'
                    scored.append((item_id,info))
                else:info=video_score(Path(preview))
            except (OSError,ValueError,RuntimeError,subprocess.TimeoutExpired,json.JSONDecodeError) as exc:
                info={'error':str(exc)}
                if kind=='photo':category,color='За проверка','жолта'
            updates.append((item_id,info,category,color))
            with LOCK:job['done']+=1
            if len(updates)>=24:flush()
        flush()
        # Local perceptual similarity proposal, never move/remove duplicates automatically.
        seen=[];duplicates=[]
        for item_id,info in sorted(scored,key=lambda pair:pair[1]['score'],reverse=True):
            h=int(info['perceptualHash'],16)
            match=next((x for x in seen if (h^x[0]).bit_count()<=5),None)
            if match:duplicates.append((item_id,match[1]))
            else:seen.append((h,item_id))
        if duplicates:
            with LOCK:
                fresh=load(pid);lookup={it['id']:it for it in fresh['items']}
                for item_id,twin_id in duplicates:
                    it=lookup.get(item_id)
                    if it:
                        it.setdefault('analysis',{})['similarTo']=twin_id
                        it['suggestedCategory']='Дупликати'
                        it['suggestedQualityColor']='жолта'
                save(fresh)
        with LOCK:job['state']='DONE'
    except Exception as exc:
        with LOCK:job.update(state='ERROR',error=str(exc))

def safe_destination(p,source,mode,item):
    if not p.get('output'):raise ValueError('Избери и зачувај одредишна папка пред сортирање')
    dest=Path(p['output']).expanduser().resolve()
    if not dest.is_dir():raise ValueError('Излезната папка не постои')
    for entry in p['folders']:
        src=Path(entry['path']).resolve()
        if _inside(dest,src) or _inside(src,dest):raise ValueError('Излезна папка мора да биде надвор од изворните папки')
    kind='VIDEO' if item['kind']=='video' else 'PHOTO'
    label=item.get('scene')
    if not item['confirmed'] or not label:raise ValueError('Непотврдена сцена/локација')
    if label not in SCENES and label not in {row['name'] for row in legend_for(p)}:raise ValueError('Непозната сцена')
    folder=dest/kind/slug(label)/slug(item['camera'])
    if item['kind']=='photo':
        quality=item.get('category') or item.get('suggestedCategory') or 'За проверка'
        if quality not in PHOTO_GROUPS: quality='За проверка'
        folder=folder/slug(quality)
    return folder

def unique_dest(path):
    if not path.exists():return path
    n=2
    while True:
        cand=path.with_name(path.stem+'__'+str(n)+path.suffix)
        if not cand.exists():return cand
        n+=1

def sort_worker(pid,ids,mode,job):
    try:
        with LOCK:p=load(pid);selected=[get_item(p,x) for x in ids]
        for it in selected:
            folder=safe_destination(p,it['preview'],mode,it)
            folder.mkdir(parents=True,exist_ok=True)
            # Journal each file individually so an interruption does not erase progress.
            it.setdefault('sortedPartial',[])
            for f in list(it['files']):
                f=Path(f)
                if not f.is_file():raise ValueError('Недостасува оригинал: '+str(f))
                dst=unique_dest(folder/f.name)
                if mode=='COPY':
                    shutil.copy2(str(f),str(dst))
                else:
                    # Crash-safe-ish cross-volume move: copy to temporary destination
                    # first, publish it, journal destination, then remove source.
                    tmp=dst.with_name('.mark_pending_'+uuid.uuid4().hex+'_'+dst.name)
                    try:
                        shutil.copy2(str(f),str(tmp))
                        if tmp.stat().st_size != f.stat().st_size:raise OSError('Копијата не е целосна')
                        # Verify content before deleting source, not only its size.
                        def file_sha256(path):
                            h=hashlib.sha256()
                            with path.open('rb') as src_stream:
                                for block in iter(lambda:src_stream.read(1024*1024),b''):h.update(block)
                            return h.digest()
                        if file_sha256(tmp)!=file_sha256(f):raise OSError('SHA256 проверката не успеа, оригиналот останува')
                        os.replace(tmp,dst)
                    finally:
                        if tmp.exists():tmp.unlink()
                with LOCK:
                    it['sortedPartial'].append({'source':str(f),'destination':str(dst),'mode':mode,
                                                'sourceDeleted':False})
                    save(p)
                if mode=='MOVE':
                    f.unlink()
                    with LOCK:
                        it['sortedPartial'][-1]['sourceDeleted']=True
                        save(p)
                with LOCK:job['done']+=1
            with LOCK:
                it['sorted']={'mode':mode,'files':it.pop('sortedPartial'),'at':datetime.now().isoformat()}
                if mode=='MOVE':
                    moved={x['source']:x['destination'] for x in it['sorted']['files']}
                    it['files']=[moved.get(f,f) for f in it['files']]
                    it['preview']=moved.get(it['preview'],it['preview'])
                save(p)
        with LOCK:job['state']='DONE'
    except Exception as exc:
        with LOCK:job.update(state='ERROR',error=str(exc))

@BLUEPRINT.get('/api/mark/wedding/v1/ui.js')
def serve_ui():
    g=guard()
    if g:return g
    return Response(UI.read_text('utf8'),mimetype='application/javascript',headers={'Cache-Control':'no-store'})

@BLUEPRINT.get('/api/mark/wedding/v1/status')
def status():
    g=guard()
    if g:return g
    Image,_=image_tools()
    return jsonify(version='2.5-quiet-background-media',ffmpeg=bool(ffmpeg_path()),ffprobe=bool(ffprobe_path()),photoAnalysis=bool(Image),
                   localOnly=True,limitations=['Видео предлозите во оваа верзија се според име и папка, не AI семантичка анализа',
                                                'Оцена на фокус е приближна; корисникот мора да потврди'])

def native_pick_folder():
    if os.name!='nt': raise RuntimeError('Windows folder picker е достапен само на Windows')
    from ctypes import wintypes
    class BROWSEINFOW(ctypes.Structure):
        _fields_=[('hwndOwner',wintypes.HWND),('pidlRoot',ctypes.c_void_p),
                  ('pszDisplayName',wintypes.LPWSTR),('lpszTitle',wintypes.LPCWSTR),
                  ('ulFlags',wintypes.UINT),('lpfn',ctypes.c_void_p),
                  ('lParam',wintypes.LPARAM),('iImage',ctypes.c_int)]
    shell=ctypes.windll.shell32
    ole=ctypes.windll.ole32
    shell.SHBrowseForFolderW.argtypes=[ctypes.POINTER(BROWSEINFOW)]
    shell.SHBrowseForFolderW.restype=ctypes.c_void_p
    shell.SHGetPathFromIDListW.argtypes=[ctypes.c_void_p,ctypes.c_wchar_p]
    shell.SHGetPathFromIDListW.restype=wintypes.BOOL
    ole.CoTaskMemFree.argtypes=[ctypes.c_void_p]
    ctypes.windll.user32.GetForegroundWindow.argtypes=[]
    ctypes.windll.user32.GetForegroundWindow.restype=wintypes.HWND
    # Windows COM dialog invoked on request thread: no extra Python/PowerShell installs.
    initialized=(ole.CoInitialize(None) in (0,1))
    try:
        display=ctypes.create_unicode_buffer(260)
        owner=ctypes.windll.user32.GetForegroundWindow()
        info=BROWSEINFOW(owner,None,ctypes.cast(display,wintypes.LPWSTR),
                        'Избери папка за MarK проект',0x0001|0x0040,None,0,0)
        pidl=shell.SHBrowseForFolderW(ctypes.byref(info))
        if not pidl:return None
        try:
            out=ctypes.create_unicode_buffer(32768)
            if not shell.SHGetPathFromIDListW(pidl,out):raise RuntimeError('Не може да се прочита Windows патеката')
            return str(resolve_folder(out.value))
        finally:ole.CoTaskMemFree(pidl)
    finally:
        if initialized:ole.CoUninitialize()

@BLUEPRINT.post('/api/mark/wedding/v1/pick-folder')
def pick_folder():
    g=guard(True)
    if g:return g
    try:return jsonify(path=native_pick_folder(),cancelled=False)
    except (OSError,ValueError,RuntimeError) as e:return error(str(e))

@BLUEPRINT.route('/api/mark/wedding/v1/projects',methods=['GET','POST'])
def projects():
    g=guard(request.method=='POST')
    if g:return g
    if request.method=='GET':
        DATA.joinpath('projects').mkdir(parents=True,exist_ok=True)
        out=[]
        for f in sorted((DATA/'projects').glob('*.json')):
            try:
                o=json.loads(f.read_text('utf8'));out.append({k:o.get(k) for k in ['id','name','date','output','created']})
            except (OSError,ValueError):pass
        return jsonify(projects=out)
    obj=request.get_json(silent=True) or {}
    name=str(obj.get('name','')).strip()[:100]
    if not name:return error('Внеси име на свадбата')
    date=str(obj.get('date',''))[:20]
    output=str(obj.get('output','')).strip()
    if output and not Path(output).expanduser().is_dir():return error('Излезната папка мора да постои')
    # One independent destination per wedding, beneath selected existing base directory.
    project_id=uuid.uuid4().hex[:16]
    if output:
        target=Path(output).expanduser().resolve()/(slug(name)+'_'+slug(date or 'undated'))
        if target.exists(): target=target.with_name(target.name+'_'+project_id[:6])
        target.mkdir(parents=True,exist_ok=False)
        output=str(target)
    p={'id':project_id,'name':name,'date':date,'created':datetime.now().isoformat(),
       'output':output,
       'folders':[],'items':[],'legend':[dict(x) for x in DEFAULT_LEGEND]}
    with LOCK:save(p)
    return jsonify(project=p),201

@BLUEPRINT.get('/api/mark/wedding/v1/project/<pid>')
def project(pid):
    g=guard()
    if g:return g
    try:
        with LOCK:return jsonify(project=load(pid))
    except (OSError,ValueError) as e:return error(str(e),404)

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/folder')
def add_folder(pid):
    g=guard(True)
    if g:return g
    obj=request.get_json(silent=True) or {}
    camera=obj.get('camera') or 'Cam 1';kind=obj.get('kind')
    if camera not in CAMERAS or kind not in ('video','photo','all'):return error('Избери валидна камера и тип')
    try:
        folder=resolve_folder(obj.get('path'))
        with LOCK:
            p=load(pid)
            # Exact same source can be rescanned safely. An overlapping *different*
            # source is not allowed because it would create duplicate selections.
            existing=next((f for f in p['folders'] if Path(f['path'])==folder and f['kind']==kind and f['camera']==camera),None)
            if any((_inside(folder,Path(e['path'])) or _inside(Path(e['path']),folder)) and e is not existing for e in p['folders']):
                return error('Оваа папка се преклопува со друга веќе додадена папка. Одбери ја истата постоечка папка за повторно скенирање или избери посебна папка.')
            if p['output'] and (_inside(Path(p['output']),folder) or _inside(folder,Path(p['output']))):
                return error('Папката на проектот и изворната папка се преклопуваат. Избери оригинални материјали надвор од проектната папка, за да нема случајно повторно внесување.')
            items=(_scan(folder,'video',camera)+_scan(folder,'photo',camera)) if kind=='all' else _scan(folder,kind,camera)
            if len(items)>25000:raise ValueError('Повеќе од 25000 ставки; избери помала главна папка')
            items.sort(key=item_order)
            dirs_with_media={it.get('relativeFolder','.') for it in items}
            newly_added=[]
            if existing is None:
                p['folders'].append({'path':str(folder),'kind':kind,'camera':camera,'count':len(items),
                                     'mediaFolders':len(dirs_with_media),'recursive':True,'order':'filename-natural'})
                p['items'].extend(items)
                newly_added=items
            else:
                # Incremental rescan: keep stable item IDs and ALL user labels.
                # A new RAW pair should extend the existing JPG group, not
                # create an extra visible selection.
                by_path={str(f).casefold():x for x in p['items'] for f in x['files']}
                by_id={x['id']:x for x in p['items']}
                for fresh in items:
                    old=by_id.get(fresh['id'])
                    if old is None:
                        old=next((by_path.get(str(f).casefold()) for f in fresh['files'] if str(f).casefold() in by_path),None)
                    if old:
                        for f in fresh['files']:
                            if f not in old['files']:
                                old['files'].append(f)
                                by_path[str(f).casefold()]=old
                        if Path(old.get('preview','')).suffix.lower() in RAW and Path(fresh['preview']).suffix.lower() not in RAW:
                            old['preview']=fresh['preview']
                    else:
                        p['items'].append(fresh)
                        newly_added.append(fresh)
                        by_id[fresh['id']]=fresh
                        for f in fresh['files']:by_path[str(f).casefold()]=fresh
                existing.update(count=len(items),mediaFolders=len(dirs_with_media),recursive=True,order='filename-natural')
            p['items'].sort(key=item_order)
            save(p)
        # v2.7: scanning/sorting happens BEFORE optional media analysis.
        # Existing projects and prior scores remain unchanged; explicit Analyze still works.
        job=None
        if newly_added and obj.get('autoAnalyze') is True:
            with LOCK:
                if not any(j.get('pid')==pid and j.get('kind')=='analyze' and j.get('state')=='RUNNING' for j in JOBS.values()):
                    job={'id':uuid.uuid4().hex,'pid':pid,'kind':'analyze','state':'RUNNING','done':0,'total':len(p['items'])}
                    JOBS[job['id']]=job
            if job:threading.Thread(target=analyze_worker,args=(pid,job,True),daemon=True).start()
        return jsonify(added=len(newly_added),rescan=existing is not None,folders=p['folders'],count=len(p['items']),
                       nestedMediaFolders=len(dirs_with_media),videos=sum(x['kind']=='video' for x in items),
                       photos=sum(x['kind']=='photo' for x in items),recursive=True,job=job)
    except (OSError,ValueError) as exc:return error(str(exc))

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/legend')
def update_legend(pid):
    g=guard(True)
    if g:return g
    obj=request.get_json(silent=True) or {}
    action=str(obj.get('action',''))
    name=obj.get('name','')
    old_name=obj.get('oldName','')
    color=obj.get('color','без боја')
    if action not in ('add','update','remove'):return error('Невалидна промена на легенда')
    if action in ('add','update') and (not valid_legend_name(name) or color not in COLORS):
        return error('Името треба да има 1–48 знаци без патеки, а бојата да е од понудените')
    if action in ('remove','update') and not valid_legend_name(old_name):return error('Невалидна постоечка категорија')
    try:
        with LOCK:
            p=load(pid);rows=legend_for(p)
            old=next((e for e in rows if e['name']==old_name),None)
            current=next((e for e in rows if e['name']==name),None)
            if action=='add':
                if current and current.get('active',True):return error('Категоријата веќе постои')
                if current:
                    current['active']=True;current['color']=color
                elif len(rows)>=45:return error('Достигнат е лимитот од 45 категории')
                else:rows.append({'name':name,'color':color,'active':True})
            elif not old:return error('Категоријата не постои')
            elif action=='remove':
                # Archive, do not erase assignments or historical directory names.
                old['active']=False
            else:
                if current and current is not old:return error('Веќе има категорија со ова име')
                old['name']=name;old['color']=color;old['active']=True
                for item in p['items']:
                    if item.get('scene')==old_name:
                        item['scene']=name;item['color']=color
            p['legend']=rows;save(p)
            count=sum(it.get('scene') == (name if action=='update' else old_name) for it in p['items'])
            return jsonify(legend=rows,affected=count)
    except (OSError,ValueError) as exc:return error(str(exc))

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/label')
def label_items(pid):
    g=guard(True)
    if g:return g
    obj=request.get_json(silent=True) or {}
    ids=obj.get('ids') or []
    if not isinstance(ids,list) or not 0<len(ids)<=5500:return error('Избери клипови или фотографии')
    value=str(obj.get('label',''));color=str(obj.get('color','без боја'));dimension=str(obj.get('dimension','scene'))
    if color not in COLORS:return error('Невалидна боја')
    try:
        with LOCK:
            p=load(pid)
            for id in ids:
                it=get_item(p,id)
                allowed=PHOTO_GROUPS if dimension=='quality' and it['kind']=='photo' else {e['name'] for e in legend_for(p) if e.get('active',True)} if dimension=='scene' else set()
                if value not in allowed:return error('Невалидна категорија за типот')
            for id in ids:
                it=get_item(p,id)
                if dimension=='scene':
                    it['scene']=value;it['color']=color;it['confirmed']=True
                else:it['category']=value
            save(p)
            return jsonify(updated=len(ids))
    except (OSError,ValueError) as e:return error(str(e))

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/output')
def set_output(pid):
    g=guard(True)
    if g:return g
    try:
        folder=resolve_folder((request.get_json(silent=True) or {}).get('path'))
        with LOCK:
            p=load(pid)
            if any(_inside(folder,Path(e['path'])) or _inside(Path(e['path']),folder) for e in p['folders']):
                return error('Излезната папка мора да е ОДВОЕНА од сите оригинали')
            p['output']=str(folder);save(p)
        return jsonify(output=str(folder))
    except (OSError,ValueError) as e:return error(str(e))

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/analyze')
def analyze(pid):
    g=guard(True)
    if g:return g
    # Videos may be analyzed without Pillow; photos will be flagged for review if unavailable.
    try:
        with LOCK:
            p=load(pid)
            if p['items']:
                active=next((j for j in JOBS.values() if j.get('pid')==pid and j.get('kind')=='analyze' and j.get('state')=='RUNNING'),None)
                if active:return error('Фото анализата веќе работи')
                job={'id':uuid.uuid4().hex,'pid':pid,'kind':'analyze','state':'RUNNING','done':0,'total':0}
                JOBS[job['id']]=job
            else:return error('Додади изворна папка')
        threading.Thread(target=analyze_worker,args=(pid,job),daemon=True).start()
        return jsonify(job=job)
    except (ValueError,OSError) as e:return error(str(e))

@BLUEPRINT.get('/api/mark/wedding/v1/job/<jobid>')
def job_status(jobid):
    g=guard()
    if g:return g
    with LOCK:
        j=JOBS.get(jobid)
        if not j:return error('Задачата не постои или MarK е рестартиран',404)
        return jsonify(job=j)

@BLUEPRINT.get('/api/mark/wedding/v1/project/<pid>/thumbnail/<item_id>')
def thumbnail(pid,item_id):
    g=guard()
    if g:return g
    try:
        with LOCK:
            it=get_item(load(pid),item_id);src=Path(it['preview'])
        if not src.is_file():return error('Изворот не е достапен',404)
        DATA.joinpath('thumbnails').mkdir(parents=True,exist_ok=True)
        frame_no = 2 if it['kind']=='video' and request.args.get('frame')=='2' else 1
        fingerprint=hashlib.sha256((str(src)+str(src.stat().st_mtime_ns)+'v24frame'+str(frame_no)).encode()).hexdigest()[:28]
        dest=DATA/'thumbnails'/(fingerprint+'.jpg')
        if not dest.is_file() or dest.stat().st_size<350:
            if it['kind']=='video':video_frame(src,dest,seconds=(2.5 if frame_no==2 else 0.0))
            else:
                # Do not leave an incomplete image behind after a failed save.
                temp=dest.with_name(dest.stem+'.'+uuid.uuid4().hex+'.jpg')
                try:
                    img=read_image(src)
                    try:
                        img.thumbnail((800,600));img.save(temp,format='JPEG',quality=78)
                    finally:img.close()
                    os.replace(temp,dest)
                finally:temp.unlink(missing_ok=True)
        return send_file(dest,mimetype='image/jpeg',conditional=True,max_age=604800)
    except (OSError,ValueError,RuntimeError,subprocess.TimeoutExpired) as e:return error(str(e),404)

@BLUEPRINT.get('/api/mark/wedding/v1/project/<pid>/play/<item_id>')
def play(pid,item_id):
    g=guard()
    if g:return g
    try:
        with LOCK:it=get_item(load(pid),item_id)
        if it['kind']!='video':return error('Не е видео',400)
        src=Path(it['preview'])
        if not src.is_file():return error('Оригиналот не е достапен',404)
        return send_file(src,mimetype=mimetypes.guess_type(src.name)[0] or 'application/octet-stream',conditional=True)
    except (OSError,ValueError) as e:return error(str(e),404)

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/sort-preview')
def sort_preview(pid):
    g=guard(True)
    if g:return g
    obj=request.get_json(silent=True) or {};mode=obj.get('mode','COPY');ids=obj.get('ids',[])
    if mode not in MODES or not isinstance(ids,list) or not ids or len(ids)>5500:return error('Неправилен избор')
    try:
        with LOCK:
            p=load(pid);entries=[]
            for id in ids:
                it=get_item(p,id)
                if it.get('sorted') or it.get('sortedPartial'):return error('Оваа ставка е веќе сортирана/делумно обработена; провери го журналот пред нов обид')
                folder=safe_destination(p,it['preview'],mode,it)
                entries.extend([{'from':f,'toFolder':str(folder)} for f in it['files']])
        return jsonify(count=len(entries),mode=mode,files=entries[:20],output=p['output'])
    except (OSError,ValueError) as e:return error(str(e))

@BLUEPRINT.post('/api/mark/wedding/v1/project/<pid>/sort')
def sort_selected(pid):
    g=guard(True)
    if g:return g
    obj=request.get_json(silent=True) or {};mode=obj.get('mode');ids=obj.get('ids',[])
    if mode not in MODES or obj.get('confirm')!=mode or not isinstance(ids,list) or not ids or len(ids)>5500:
        return error('Изрична потврда COPY/MOVE е задолжителна')
    try:
        with LOCK:
            p=load(pid);items=[get_item(p,id) for id in ids]
            if any(it.get('sorted') or it.get('sortedPartial') for it in items):return error('Дел од ставките веќе се обработени или делумно обработени')
            for it in items:safe_destination(p,it['preview'],mode,it)
            if any(j.get('kind')=='sort' and j.get('pid')==pid and j.get('state')=='RUNNING' for j in JOBS.values()):return error('Веќе се сортираат фајлови')
            if any(j.get('kind')=='analyze' and j.get('pid')==pid and j.get('state')=='RUNNING' for j in JOBS.values()):return error('Анализата уште работи. Почекај да заврши пред COPY/MOVE')
            job={'id':uuid.uuid4().hex,'pid':pid,'kind':'sort','mode':mode,'state':'RUNNING',
                 'done':0,'total':sum(len(it['files']) for it in items)}
            JOBS[job['id']]=job
        threading.Thread(target=sort_worker,args=(pid,ids,mode,job),daemon=True).start()
        return jsonify(job=job)
    except (OSError,ValueError) as e:return error(str(e))


# v2.7 — Wedding Director read-only timeline preview + editable Premiere XML export.
# Does not perform semantic shot-quality AI or claim a final creative edit.
from xml.etree import ElementTree as _ET
from urllib.parse import quote as _quote
from flask import make_response as _make_response

_LONG_CHAPTERS = ['Зет', 'Невеста', 'Црква', 'Ресторан', 'Дрон', 'Друго']

def _director_scene(it):
    scene = it.get('scene') if it.get('confirmed') else (it.get('scene') or it.get('suggestedScene'))
    return scene if scene in _LONG_CHAPTERS else 'Друго'

def _director_media(it):
    # Move/COPY may create relocated paths; use existing source if present, then journal output.
    for value in [it.get('preview')] + list(it.get('files') or []):
        if value and Path(value).is_file():return Path(value)
    for entry in (it.get('sorted') or {}).get('files',[]):
        dst=entry.get('destination')
        if dst and Path(dst).is_file():return Path(dst)
    return Path(it.get('preview') or it.get('fileName') or 'missing')

def _director_ordered(p):
    clips=[it for it in p.get('items',[]) if it.get('kind')=='video']
    groups={name:[] for name in _LONG_CHAPTERS}
    for it in clips:groups[_director_scene(it)].append(it)
    for group in groups.values(): group.sort(key=item_order)
    # Every source is counted exactly once. Nothing is rejected or trimmed in the long film.
    ordered=[(chapter,it) for chapter in _LONG_CHAPTERS for it in groups[chapter]]
    assert len(ordered)==len(clips)
    return ordered

def _director_duration(it, path):
    val=(it.get('analysis') or {}).get('durationSeconds')
    try:
        if val is not None and float(val)>0:return float(val)
    except (ValueError,TypeError):pass
    if not path.is_file():return None
    try:
        info=video_score(path)
        val=info.get('durationSeconds')
        return float(val) if val is not None and float(val)>0 else None
    except (ValueError,OSError,RuntimeError,subprocess.TimeoutExpired):return None

def _director_summary(p):
    ordered=_director_ordered(p)
    rows=[]
    for chapter,it in ordered:
        path=_director_media(it)
        dur=(it.get('analysis') or {}).get('durationSeconds')
        rows.append({'id':it['id'],'chapter':chapter,'name':it.get('fileName',''),
                     'camera':it.get('camera',''),'file':str(path),'fileExists':path.is_file(),
                     'durationSeconds':dur,'longIncluded':True,'shortSpotEligible':
                     None if dur is None else 0<float(dur)<=300,
                     'userConfirmed':bool(it.get('confirmed'))})
    return rows

def _director_sequence(name, pairs, frame_rate=25):
    seq=_ET.Element('sequence',{'id':'mark-sequence-'+hashlib.sha256(name.encode('utf-8')).hexdigest()[:10]})
    _ET.SubElement(seq,'name').text=name
    rate=_ET.SubElement(seq,'rate');_ET.SubElement(rate,'timebase').text=str(frame_rate)
    _ET.SubElement(rate,'ntsc').text='FALSE'
    media=_ET.SubElement(seq,'media');video=_ET.SubElement(media,'video');track=_ET.SubElement(video,'track')
    audio=_ET.SubElement(media,'audio');audio_track=_ET.SubElement(audio,'track')
    frame=0
    for i,(it,path,frames) in enumerate(pairs):
        clip=_ET.SubElement(track,'clipitem',{'id':'marK-clip-'+hashlib.sha256((name+it['id']).encode()).hexdigest()[:12]})
        for key,value in [('name',path.name),('start',frame),('end',frame+frames),('in',0),('out',frames),('duration',frames)]:
            _ET.SubElement(clip,key).text=str(value)
        f=_ET.SubElement(clip,'file',{'id':'mark-file-'+hashlib.sha256(str(path).encode()).hexdigest()[:12]})
        _ET.SubElement(f,'name').text=path.name
        # Windows Path.as_uri ensures canonical file:///C:/ paths for Adobe relinking.
        _ET.SubElement(f,'pathurl').text=path.resolve().as_uri()
        r=_ET.SubElement(f,'rate');_ET.SubElement(r,'timebase').text=str(frame_rate)
        _ET.SubElement(r,'ntsc').text='FALSE'
        sr=_ET.SubElement(clip,'sourcetrack')
        _ET.SubElement(sr,'mediatype').text='video';_ET.SubElement(sr,'trackindex').text='1'
        # Preserve access to the original audio track in the documentary cut.
        aud=_ET.SubElement(audio_track,'clipitem',{'id':'mark-audio-'+hashlib.sha256((name+it['id']).encode()).hexdigest()[:12]})
        for key,value in [('name',path.name),('start',frame),('end',frame+frames),('in',0),('out',frames),('duration',frames)]:
            _ET.SubElement(aud,key).text=str(value)
        _ET.SubElement(aud,'file',{'id':f.attrib['id']})
        sr=_ET.SubElement(aud,'sourcetrack')
        _ET.SubElement(sr,'mediatype').text='audio';_ET.SubElement(sr,'trackindex').text='1'
        frame+=frames
    _ET.SubElement(seq,'duration').text=str(frame)
    return seq

@BLUEPRINT.get('/api/mark/wedding/v1/project/<pid>/director-plan')
def director_plan(pid):
    g=guard()
    if g:return g
    try:
        with LOCK:p=load(pid)
        rows=_director_summary(p)
        return jsonify(count=len(rows),chapters={k:sum(r['chapter']==k for r in rows) for k in _LONG_CHAPTERS},
                       missing=sum(not r['fileExists'] for r in rows),rows=rows,
                       note='Долгите видеа: секој клип цел, без кратење, природно сортирање по име внатре во поглавјето.')
    except (OSError,ValueError,AssertionError) as exc:return error(str(exc))

@BLUEPRINT.get('/api/mark/wedding/v1/project/<pid>/director-long-xml')
def director_long_xml(pid):
    g=guard()
    if g:return g
    try:
        with LOCK:p=load(pid)
        ordered=_director_ordered(p)
        if not ordered:return error('Нема видео клипови за извоз')
        pairs=[];bad=[]
        for chapter,it in ordered:
            path=_director_media(it)
            dur=_director_duration(it,path)
            if not path.is_file() or dur is None:bad.append(it.get('fileName','непознато'))
            else:pairs.append((chapter,it,path,max(1,round(dur*25))))
        if bad:
            return error('НЕ Е ИЗВЕЗЕНО: недостасуваат извори или измерено времетраење на '+str(len(bad))+ ' клипови: '+', '.join(bad[:8])+'; инсталирај FFprobe и повтори анализа',422)
        xmeml=_ET.Element('xmeml',{'version':'4'})
        project=_ET.SubElement(xmeml,'project')
        _ET.SubElement(project,'name').text='MarK — '+p.get('name','Свадба')
        children=_ET.SubElement(project,'children')
        allseq=_director_sequence('ЦЕЛА СВАДБА — '+p.get('name','MarK'),[(it,path,frames) for _,it,path,frames in pairs])
        children.append(allseq)
        for chapter in _LONG_CHAPTERS:
            chapterclips=[(it,path,frames) for ch,it,path,frames in pairs if ch==chapter]
            if chapterclips:children.append(_director_sequence('ДОЛГО — '+chapter,chapterclips))
        xml=b'<?xml version="1.0" encoding="UTF-8"?>\n' + _ET.tostring(xmeml,encoding='utf-8')
        response=_make_response(xml)
        response.headers['Content-Type']='application/xml; charset=utf-8'
        response.headers['Content-Disposition']='attachment; filename="MarK_Long_Wedding.xml"'
        response.headers['Cache-Control']='no-store'
        return response
    except (OSError,ValueError,AssertionError) as exc:return error(str(exc))

def install(app,auth,local):
    global AUTH,LOCAL
    AUTH,LOCAL=auth,local
    app.register_blueprint(BLUEPRINT)
