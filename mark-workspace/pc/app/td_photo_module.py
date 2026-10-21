"""TD PHOTO v0.6: one analysis per image stem, paired JPG/RAW output. Video untouched."""
from pathlib import Path
from collections import defaultdict
import csv, shutil, threading, time, os, math, xml.sax.saxutils as xmlutil, tempfile, struct, xml.etree.ElementTree as ET
from PIL import Image, ImageOps, ImageFilter, ImageStat

STATE={'running':False,'done':0,'total':0,'status':'Подготвено','output':'','error':'','counts':{}}
LOCK=threading.Lock()
VIEW={'.jpg','.jpeg','.png','.tif','.tiff','.bmp','.webp'}
RAW={'.nef','.nrw','.cr2','.cr3','.arw','.dng','.raf','.rw2','.orf','.pef','.srw'}
EXT=VIEW|RAW

def pick_directory():
    import tkinter as tk
    from tkinter import filedialog
    root=tk.Tk();root.withdraw();root.attributes('-topmost',True)
    try:return filedialog.askdirectory(parent=root) or ''
    finally:root.destroy()

def dhash(im):
    g=ImageOps.grayscale(im).resize((9,8));p=list(g.getdata())
    return sum((1<<(y*8+x)) for y in range(8) for x in range(8) if p[y*9+x]>p[y*9+x+1])

def metrics_image(im):
    im=ImageOps.exif_transpose(im)
    im.thumbnail((1200,1200))
    g=ImageOps.grayscale(im)
    small=g.resize((max(1,min(360,g.width)),max(1,min(360,g.height))))
    edges=small.filter(ImageFilter.FIND_EDGES)
    return ImageStat.Stat(edges).var[0],ImageStat.Stat(small).mean[0],dhash(im)

def metrics(path):
    if path.suffix.lower() in VIEW:
        with Image.open(path) as im:return metrics_image(im)
    try:
        import rawpy
    except ImportError:
        raise RuntimeError('RAW без JPG: инсталирај rawpy (py -3.13 -m pip install rawpy)')
    with rawpy.imread(str(path)) as raw:
        try:
            preview=raw.extract_thumb()
            if preview.format==rawpy.ThumbFormat.JPEG:
                from io import BytesIO
                with Image.open(BytesIO(preview.data)) as im:return metrics_image(im)
            if preview.format==rawpy.ThumbFormat.BITMAP:
                return metrics_image(Image.fromarray(preview.data))
        except Exception:pass
        return metrics_image(Image.fromarray(raw.postprocess(half_size=True,use_camera_wb=True,no_auto_bright=True)))

def unique_target(folder,relative):
    target=folder/relative;target.parent.mkdir(parents=True,exist_ok=True)
    if not target.exists():return target
    for i in range(2,100000):
        alt=target.with_name(f'{target.stem}__{i}{target.suffix}')
        if not alt.exists():return alt
    raise RuntimeError('Too many duplicate output names')

# Metadata-only labels: never set ratings or Pick/Reject.
XMP_NS='http://ns.adobe.com/xap/1.0/'
RDF_NS='http://www.w3.org/1999/02/22-rdf-syntax-ns#'
XMP_HEADER=b'http://ns.adobe.com/xap/1.0/\x00'
ET.register_namespace('x','adobe:ns:meta/')
ET.register_namespace('rdf',RDF_NS)
ET.register_namespace('xmp',XMP_NS)

def merge_label(existing, label):
    if existing:
        root=ET.fromstring(existing)
    else:
        root=ET.Element('{adobe:ns:meta/}xmpmeta')
    rdf=root.find('.//{%s}RDF'%RDF_NS)
    if rdf is None:rdf=ET.SubElement(root,'{%s}RDF'%RDF_NS)
    desc=rdf.find('{%s}Description'%RDF_NS)
    if desc is None:desc=ET.SubElement(rdf,'{%s}Description'%RDF_NS)
    desc.set('{%s}Label'%XMP_NS,label)
    return ET.tostring(root,encoding='utf-8',xml_declaration=True)

def atomic_bytes(path,data,backup_dir=None):
    path=Path(path)
    if backup_dir is not None and path.exists():
        relative=path.resolve().anchor.replace(':','').replace('\\','')
        backup=Path(backup_dir)/(relative+'_'+str(abs(hash(str(path.resolve()))))+'_'+path.name)
        # use collision-resistant path name across different directories
        import hashlib
        backup=Path(backup_dir)/(hashlib.sha256(str(path.resolve()).encode()).hexdigest()[:16]+'_'+path.name)
        backup.parent.mkdir(parents=True,exist_ok=True)
        if not backup.exists():shutil.copy2(path,backup)
    fd,tmp=tempfile.mkstemp(prefix='.tdphoto_',dir=str(path.parent))
    try:
        with os.fdopen(fd,'wb') as f:f.write(data)
        os.replace(tmp,path)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)

def write_raw_label(path,label,backup_dir=None):
    sidecar=Path(path).with_suffix('.xmp')
    existing=sidecar.read_bytes() if sidecar.exists() else None
    updated=merge_label(existing,label)
    atomic_bytes(sidecar,updated,backup_dir)

def write_jpeg_label(path,label,backup_dir=None):
    # Standard JPEG APP1 XMP packet, preserving Exif, ICC and other JPEG segments.
    path=Path(path);data=path.read_bytes()
    if not data.startswith(b'\xff\xd8'):raise ValueError('Not a JPEG: '+str(path))
    pos=2;segments=[];existing=None;found=False
    while pos<len(data):
        if data[pos]!=255:raise ValueError('Invalid JPEG segment')
        begin=pos
        while pos<len(data) and data[pos]==255:pos+=1
        if pos>=len(data):raise ValueError('Truncated JPEG')
        marker=data[pos];pos+=1
        if marker in (0xD9,0xDA):
            tail=data[begin:];break
        if marker in (0x01,) or 0xD0<=marker<=0xD7:
            segments.append(data[begin:pos]);continue
        if pos+2>len(data):raise ValueError('Truncated JPEG length')
        length=struct.unpack('>H',data[pos:pos+2])[0]
        if length<2 or pos+length>len(data):raise ValueError('Invalid JPEG length')
        segment=data[begin:pos+length]
        payload=data[pos+2:pos+length]
        if marker==0xE1 and payload.startswith(XMP_HEADER):
            if found:raise ValueError('Multiple XMP packets; manual review needed')
            found=True;existing=payload[len(XMP_HEADER):]
        elif marker==0xE1 and payload.startswith(b'http://ns.adobe.com/xmp/extension/'):
            raise ValueError('Extended XMP: cannot safely update')
        else:segments.append(segment)
        pos+=length
    else:raise ValueError('Missing JPEG SOS/EOI')
    packet=XMP_HEADER+merge_label(existing,label)
    if len(packet)+2>65535:raise ValueError('XMP too large for JPEG APP1')
    app1=b'\xff\xe1'+struct.pack('>H',len(packet)+2)+packet
    atomic_bytes(path,data[:2]+app1+b''.join(segments)+tail,backup_dir)

def write_color(path,label,backup_dir=None):
    suffix=Path(path).suffix.lower()
    if suffix in RAW:write_raw_label(path,label,backup_dir)
    elif suffix in ('.jpg','.jpeg'):write_jpeg_label(path,label,backup_dir)
    else:raise ValueError('Embedded Lightroom color not supported for '+suffix)

def label_for(category):
    return {'01_NAJDOBRI_KANDIDATI':('Green',5),
      '02_MATNI_PROVERI':('Red',1),
      '03_TEMNI_PROVERI':('Yellow',2),
      '04_SLICNI_DUPLIKATI_PROVERI':('Purple',2),
      '05_PRESVETLI_PROVERI':('Yellow',2),
      '06_ZA_PROVERKA_NEPROCITANI':('Yellow',0)}[category]

def copy_group(item, category, output, source, rows, warnings):
    label,rating=label_for(category)
    for p in item['members']:
        dest=unique_target(output/category,p.relative_to(source))
        shutil.copy2(p,dest)
        try:
            if p.suffix.lower() in RAW or p.suffix.lower() in ('.jpg','.jpeg'):
                write_color(dest,label)
        except Exception as exc:warnings.append(f"Излез {dest}: {exc}")
        rows.append([str(p.relative_to(source)),category,str(item['rep'].relative_to(source)),
          round(item['sharp'],2),round(item['bright'],2),label,rating,str(dest)])

def run(src, sharpness=140, similarity=5, mode="both"):
    try:
        source=Path(src).resolve()
        if not source.is_dir():raise ValueError('Невалидна изворна папка')
        output=source.parent/(source.name+'_TD_AI_SELECTION_'+time.strftime('%Y%m%d_%H%M%S'))
        n=2
        while output.exists():
            output=source.parent/(source.name+'_TD_AI_SELECTION_'+time.strftime('%Y%m%d_%H%M%S')+f'_{n}');n+=1
        files=sorted((p for p in source.rglob('*') if p.is_file() and p.suffix.lower() in EXT),key=lambda p:str(p).lower())
        # Parent folder is part of key: same basename from two cameras/folders must not merge.
        groups=defaultdict(list)
        for p in files:
            rel=p.relative_to(source)
            groups[(str(rel.parent).casefold(),p.stem.casefold())].append(p)
        pairs=list(groups.values())
        with LOCK:STATE.update(running=True,done=0,total=len(pairs),status='Анализа на кадри...',output=str(output),error='',counts={})
        output.mkdir(parents=True)
        scored=[];rows=[];manifest=[];warnings=[]
        backup=output/"ORIGINAL_METADATA_BACKUP"
        for i,members in enumerate(pairs,1):
            # Prefer JPG, then another viewable file, then RAW; evaluate one file only.
            representative=min(members,key=lambda p:(0 if p.suffix.lower() in {'.jpg','.jpeg'} else 1 if p.suffix.lower() in VIEW else 2,str(p).lower()))
            try:
                sharp,bright,h=metrics(representative)
                scored.append({'members':members,'rep':representative,'sharp':sharp,'bright':bright,'hash':h})
            except Exception as exc:
                for p in members:
                    dest=unique_target(output/'06_ZA_PROVERKA_NEPROCITANI',p.relative_to(source)) if mode in ('folders','both') else p
                    if mode in ('folders','both'): shutil.copy2(p,dest)
                    rows.append([str(p.relative_to(source)),'06_ZA_PROVERKA_NEPROCITANI',str(representative.relative_to(source)),'',str(exc),'Yellow',0,str(dest)])
                    manifest.append([str(p.resolve()),'06_ZA_PROVERKA_NEPROCITANI','Yellow',0,0])
            with LOCK:STATE.update(done=i,status='Анализа на кадри...')
        # Candidate comparisons are based on one representative per camera capture.
        clusters=[]
        for item in sorted(scored,key=lambda v:v['sharp'],reverse=True):
            cluster=next((c for c in clusters if (item['hash']^c[0]['hash']).bit_count()<=similarity),None)
            if cluster is None:clusters.append([item])
            else:cluster.append(item)
        for cluster in clusters:
            for rank,item in enumerate(cluster):
                sharp,bright=item['sharp'],item['bright']
                if sharp<sharpness:category='02_MATNI_PROVERI'
                elif rank:category='04_SLICNI_DUPLIKATI_PROVERI'
                elif bright<48:category='03_TEMNI_PROVERI'
                elif bright>215:category='05_PRESVETLI_PROVERI'
                elif sharp<sharpness:category='02_MATNI_PROVERI'
                else:category='01_NAJDOBRI_KANDIDATI'
                if mode in ('folders','both'):
                    copy_group(item,category,output,source,rows,warnings)
                label,rating=label_for(category)
                flag=(-1 if category=='02_MATNI_PROVERI' else 1 if category=='01_NAJDOBRI_KANDIDATI' else 0)
                for original in item['members']:
                    if mode in ('lightroom','both'):
                        try:write_color(original,label,backup)
                        except Exception as exc:warnings.append(f'Оригинал {original}: {exc}')
                    manifest.append([str(original.resolve()),category,label,rating,flag])
                with LOCK:
                    STATE['counts'][category]=STATE['counts'].get(category,0)+1
        (output/'METADATA_WARNINGS.txt').write_text('\n'.join(warnings) if warnings else 'Нема грешки при запишување бои.',encoding='utf-8')
        with (output/'TD_LIGHTROOM_LABELS.csv').open('w',encoding='utf-8-sig',newline='') as f:
            w=csv.writer(f);w.writerow(['original_path','category','color','rating','pick_status']);w.writerows(manifest)
        with (output/'IZVESTAJ.csv').open('w',encoding='utf-8-sig',newline='') as f:
            w=csv.writer(f);w.writerow(['Фајл','Категорија','Анализиран претставник','Острина/грешка','Осветленост','Lightroom боја','Ѕвезди','Излезна патека']);w.writerows(rows)
        (output/'LIGHTROOM_UPATSTVO.txt').write_text("TD PHOTO AI v0.9 - SAMO BOI, BEZ PLUGIN\n\nNovi fotografii: Lightroom Classic > Import > Add, so Include Subfolders. NEF/RAW cita .xmp sidecar, JPG cita vgradeni XMP boi.\nVeke vo katalog: Library > selektiraj fotografii > Metadata > Read Metadata from File. Vnimavaj: ova moze da zameni drugi katalog metapodatoci; napravi backup od katalogot.\nRezim Lightroom ili Both: originalni JPG dobivaat XMP label vo samiot JPG; RAW dobivaat .xmp sidecar vo originalniot folder. Postoecki XMP se zacuva i mu se menuva samo Label, a backup od prethodnite metapodatoci e vo ORIGINAL_METADATA_BACKUP.\nRezim Folders: originalite ne se menuvaat; izlezni kopii dobivaat oznaki.\nAko ima greski proveri METADATA_WARNINGS.txt. Green=dobri, Red=matni, Yellow=za proverka, Purple=duplikati. Lightroom Color Label Set mora da gi prepoznava ovie zborovi.\nNe brisi avtomatski nisto.\n",encoding='utf-8')
        with LOCK:STATE.update(running=False,status=('Завршено, но има '+str(len(warnings))+' предупредувања. Провери METADATA_WARNINGS.txt.' if warnings else 'Завршено. Боите се запишани.'))
    except Exception as exc:
        with LOCK:STATE.update(running=False,error=str(exc),status='Грешка')

def register(app,authed,local_request):
    from flask import jsonify,request
    @app.route('/api/td-photo-v05/pick',methods=['POST'])
    def td_photo_v05_pick():
        if not authed() or not local_request():return jsonify(error='Само локално со PIN'),403
        if request.headers.get('X-TD-Client')!='TD-v1.4':return jsonify(error='Unauthorized'),403
        try:return jsonify(path=pick_directory())
        except Exception as e:return jsonify(error=str(e)),500
    @app.route('/api/td-photo-v05/start',methods=['POST'])
    def td_photo_v05_start():
        if not authed() or not local_request():return jsonify(error='Само локално со PIN'),403
        if request.headers.get('X-TD-Client')!='TD-v1.4':return jsonify(error='Unauthorized'),403
        data=request.get_json(silent=True) or {}
        path=data.get('path','')
        try:
            sharpness=max(20,min(400,int(data.get('sharpness',140))))
            similarity=max(0,min(12,int(data.get('similarity',5))))
            mode=data.get('mode','both')
            if mode not in ('lightroom','folders','both'):return jsonify(error='Невалиден режим'),400
        except (TypeError,ValueError):return jsonify(error='Невалидни поставки'),400
        if not path or not Path(path).is_dir():return jsonify(error='Избери постоечка папка'),400
        with LOCK:
            if STATE['running']:return jsonify(error='Веќе работи анализа'),409
            STATE['running']=True
        threading.Thread(target=run,args=(path,sharpness,similarity,mode),daemon=True).start()
        return jsonify(ok=True)
    @app.route('/api/td-photo-v05/status')
    def td_photo_v05_status():
        if not authed() or not local_request():return jsonify(error='Само локално со PIN'),403
        with LOCK:return jsonify(dict(STATE))
