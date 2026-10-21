"""TD Studio v2.5: local, cached chapter analysis and editable Premiere XML.

Source video is read-only. Uncertain material is kept inside documentary chapters.
No stock purchases, identity recognition, or remote model calls.
"""
from __future__ import annotations
import base64, collections, copy, csv, datetime as dt, hashlib, io, json, math, os, re, shutil, subprocess, time, urllib.request
from fractions import Fraction
from pathlib import Path
import xml.etree.ElementTree as ET

PHASES=(('groom','Кај зетот'),('bride','Кај невестата'),('church','Црква'),('reception','Ресторан'))
VIDEO_EXT={'.mp4','.mov','.avi','.m4v','.mkv','.mts','.m2ts'}
AUDIO_EXT={'.mp3','.wav','.m4a','.aif','.aiff','.flac'}
SCHEMA=4

def natural(v):
    return [int(x) if x.isdigit() else x.casefold() for x in re.split(r'(\d+)',str(v))]

def safe_name(v):
    s=re.sub(r'[<>:"/\\|?*\x00-\x1f]','_',str(v)).strip(' .')[:80]
    if not s:raise ValueError('Внеси име на проектот.')
    if s.upper().split('.')[0] in {'CON','PRN','AUX','NUL',*(f'COM{i}' for i in range(1,10)),*(f'LPT{i}' for i in range(1,10))}:s='_'+s
    return s

def atomic_json(p,data):
    p=Path(p);p.parent.mkdir(parents=True,exist_ok=True)
    tmp=p.with_name(p.name+'.tmp');tmp.write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8');os.replace(tmp,p)

def ffmpeg():
    candidate=os.environ.get('TD_FFMPEG_PATH','')
    if candidate and Path(candidate).is_file():return candidate
    candidate=shutil.which('ffmpeg')
    if candidate:return candidate
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:raise RuntimeError('Недостига FFmpeg. Инсталирај imageio-ffmpeg во Python околината на Studio.')

def run_process(args,timeout=90):
    opts={'capture_output':True,'timeout':timeout}
    if os.name=='nt':opts['creationflags']=subprocess.CREATE_NO_WINDOW
    return subprocess.run(args,**opts)

def probe(path):
    p=Path(path);exe=ffmpeg();fp=shutil.which('ffprobe')
    if not fp:
        sibling=Path(exe).with_name('ffprobe.exe' if os.name=='nt' else 'ffprobe')
        if sibling.is_file():fp=str(sibling)
    if fp:
        r=run_process([fp,'-v','error','-show_format','-show_streams','-of','json',str(p)],45)
        if r.returncode:raise ValueError('Нечитлив медиум: '+str(p))
        data=json.loads(r.stdout);fmt=data.get('format',{});streams=data.get('streams',[])
        videos=[s for s in streams if s.get('codec_type')=='video' and not s.get('disposition',{}).get('attached_pic')]
        audio=next((s for s in streams if s.get('codec_type')=='audio'),{})
        v=videos[0] if videos else {};tags={**fmt.get('tags',{}),**v.get('tags',{})}
        try:fps=float(Fraction(v.get('avg_frame_rate') or v.get('r_frame_rate') or '25'))
        except (ValueError,ZeroDivisionError):fps=25.
        result={'path':str(p.resolve()),'duration':float(fmt.get('duration') or v.get('duration') or audio.get('duration') or 0),
                'fps':fps if fps>0 else 25.,'width':int(v.get('width',0)),'height':int(v.get('height',0)),
                'audio':bool(audio),'channels':int(audio.get('channels',0)),
                'samplerate':int(audio.get('sample_rate',48000)),'created':tags.get('creation_time'),'video':bool(v)}
    else:
        r=run_process([exe,'-hide_banner','-i',str(p)],45);s=r.stderr.decode('utf-8','replace')
        m=re.search(r'Duration:\s*(\d+):(\d+):([\d.]+)',s);dim=re.search(r'Video:.*?\b(\d{2,5})x(\d{2,5})\b',s);fpsm=re.search(r'([\d.]+) fps',s)
        if not m:raise ValueError('Не можам да го прочитам траењето: '+str(p))
        created=re.search(r'creation_time\s*:\s*(\S+)',s)
        result={'path':str(p.resolve()),'duration':int(m[1])*3600+int(m[2])*60+float(m[3]),'fps':float(fpsm[1]) if fpsm else 25.,
                'width':int(dim[1]) if dim else 0,'height':int(dim[2]) if dim else 0,'audio':'Audio:' in s,
                'channels':2 if 'stereo' in s else 1,'samplerate':48000,'created':created[1] if created else None,'video':bool(dim)}
    if result['duration']<=0:raise ValueError('Невалидно траење: '+str(p))
    try:result['time']=dt.datetime.fromisoformat(str(result['created']).replace('Z','+00:00')).timestamp()
    except (ValueError,TypeError):result['time']=None
    return result

def frame_jpg(path,second):
    r=run_process([ffmpeg(),'-nostdin','-v','error','-ss',str(max(0,second)),'-i',str(path),'-frames:v','1','-vf','scale=640:-2','-f','image2pipe','-vcodec','mjpeg','pipe:1'],45)
    if r.returncode or not r.stdout:raise ValueError('Не можам да прочитам кадар: '+str(path))
    return r.stdout

def visual_metrics(jpeg):
    from PIL import Image, ImageFilter, ImageStat
    with Image.open(io.BytesIO(jpeg)) as im:
        g=im.convert('L');g.thumbnail((320,180));b=ImageStat.Stat(g).mean[0]
        sharp=ImageStat.Stat(g.filter(ImageFilter.FIND_EDGES)).var[0]
        h=list(g.resize((9,8)).getdata());d=sum(1<<(y*8+x) for y in range(8) for x in range(8) if h[y*9+x]>h[y*9+x+1])
    return {'brightness':b,'sharpness':sharp,'hash':str(d),'quality':min(1,sharp/120)*.6+max(0,1-abs(b-125)/120)*.4}

def model_ready(model):
    try:
        with urllib.request.urlopen('http://127.0.0.1:8798/api/tags',timeout=3) as r:data=json.load(r)
        return model in {m.get('name','') for m in data.get('models',[])}
    except (OSError,ValueError):return False

def classify(jpgs,model):
    prompt=('Classify wedding footage from sampled frames. No person identity inference. '
      'Return JSON only: phase (groom, bride, church, reception, unknown), confidence (0..1), '
      'role (establishing, detail, preparation, people, ceremony, celebration, unknown), '
      'drone (boolean), usable (boolean), note (short). '
      'groom/bride require evidence of their home preparation, not merely a man/woman in any location. '
      'Tables or decorations alone cannot identify which home. A church interior or wedding religious ritual is church. '
      'A banquet hall, wedding party, first dance or reception table is reception. '
      'Outdoor portraits away from these settings are unknown. If ambiguous use unknown and low confidence. '
      'Do not invent camera/location connections. usable=false only for obvious accidental footage, strong obstruction or unusable exposure.')
    payload={'model':model,'stream':False,'format':'json','keep_alive':'10m','messages':[{'role':'user','content':prompt,'images':[base64.b64encode(j).decode('ascii') for j in jpgs]}],
             'options':{'temperature':0,'num_ctx':4096,'num_predict':220}}
    req=urllib.request.Request('http://127.0.0.1:8798/api/chat',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=100) as r:data=json.load(r)
    raw=str(data.get('message',{}).get('content',''));raw=re.sub(r'^```(?:json)?\s*|\s*```$','',raw.strip())
    out=json.loads(raw)
    if not isinstance(out,dict):raise ValueError('Vision одговорот не е JSON објект')
    phase=out.get('phase','unknown');phase=phase if phase in dict(PHASES) else 'unknown'
    try:conf=max(0.,min(1.,float(out.get('confidence',0))))
    except (ValueError,TypeError):conf=0.
    roles={'establishing','detail','preparation','people','ceremony','celebration','unknown'}
    return {'phase':phase if conf>=.72 else 'unknown','confidence':conf,'role':out.get('role') if out.get('role') in roles else 'unknown',
            'drone':out.get('drone') is True,'usable':out.get('usable') is not False,'note':str(out.get('note',''))[:240]}

def folder_phase(path,root,overrides=None):
    path=Path(path).resolve();root=Path(root).resolve();hits=set()
    for phase,relative in (overrides or {}).items():
        if not relative or phase not in dict(PHASES):continue
        target=(root/relative).resolve()
        if target!=root and root not in target.parents:raise ValueError('Папките по поглавје треба да бидат во изворната папка.')
        if path==target or target in path.parents:hits.add(phase)
    if len(hits)>1:raise ValueError('Папките по поглавје се преклопуваат.')
    if hits:return hits.pop()
    try:parts=path.parent.relative_to(root).parts
    except ValueError:return 'unknown'
    keys={'groom':{'groom','zet','зет','zetot','зетот'},'bride':{'bride','nevesta','невеста','невестата'},
          'church':{'church','crkva','црква','ceremony','ceremonija','церемонија'},
          'reception':{'reception','restaurant','restoran','ресторан'}}
    for part in parts:
        words=set(re.split(r'[\s_.\-]+',part.casefold()))
        hits.update(k for k,vs in keys.items() if words&vs)
    return hits.pop() if len(hits)==1 else 'unknown'

def analyze_sources(files,root,out,settings,progress,checkpoint):
    cachefile=Path(root)/'TD_AI_CACHE'/'chapters_v21.json'
    try:cache=json.loads(cachefile.read_text('utf-8'))
    except (OSError,ValueError):cache={}
    model=settings.get('model') or 'qwen2.5vl:3b';vision=model_ready(model)
    rows=[];errors=[];metas={};bad_vision=0
    for i,p in enumerate(files,1):
        checkpoint();p=Path(p);st=p.stat();fp=hashlib.sha256(f'{SCHEMA}|{p.resolve()}|{st.st_size}|{st.st_mtime_ns}|{model}|{settings.get("quality","standard")}'.encode()).hexdigest()
        label=settings.get('source_phase_map',{}).get(str(p.resolve())) or folder_phase(p,root,settings.get('overrides'))
        hit=cache.get(fp)
        # Uncertain/error results are retried on a later run when the model is available.
        if hit and (hit.get('vision_ok') or label!='unknown' or not vision):
            meta=hit['meta'];chunks=copy.deepcopy(hit['chunks'])
        else:
            try:
                meta=probe(p)
                if not meta['video']:raise ValueError('Не е видео')
                duration=meta['duration'];step={'fast':300,'standard':120,'deep':60}.get(settings.get('quality'),120)
                chunks=[];all_ok=True
                for a in range(max(1,math.ceil(duration/step))):
                    checkpoint();start=a*step;end=min(duration,(a+1)*step);span=end-start
                    if span<.3:continue
                    moments=[start+span*.28,start+span*.72]
                    pictures=[];scores=[]
                    for moment in moments:
                        try:
                            picture=frame_jpg(p,moment);pictures.append(picture);scores.append({**visual_metrics(picture),'second':moment})
                        except (OSError,ValueError,subprocess.TimeoutExpired) as exc:errors.append(str(exc)[:250])
                    pred={'phase':'unknown','confidence':0.,'role':'unknown','drone':False,'usable':True,'note':'Нема сигурна визуелна класификација'}
                    if label!='unknown':pred.update(phase=label,confidence=1.,note='Поглавје од позната папка')
                    elif vision and pictures and bad_vision<3:
                        try:pred=classify(pictures,model)
                        except (OSError,ValueError,KeyError,TypeError) as exc:
                            all_ok=False;bad_vision+=1;errors.append('AI: '+str(exc)[:250])
                    else:all_ok=False
                    try:relative_name=str(p.relative_to(root))
                    except ValueError:relative_name=str(p)
                    drone_name=bool(set(re.split(r'[\s_.\-/\\]+',relative_name.casefold()))&{'drone','dron','дрон'}) or p.name.upper().startswith('DJI_')
                    pred['drone']=pred['drone'] or drone_name
                    chunks.append({'path':str(p.resolve()),'in':start,'out':end,**pred,'samples':scores,'inferred':False})
                cache[fp]={'meta':meta,'chunks':copy.deepcopy(chunks),'vision_ok':all_ok}
            except (OSError,ValueError,subprocess.TimeoutExpired) as exc:
                errors.append(str(exc));progress(5+int(67*i/len(files)),f'Проверка {i}/{len(files)} · нечитлив {p.name}');continue
        if label!='unknown':
            for c in chunks:c.update(phase=label,confidence=1.,inferred=False,note='Поглавје од позната папка')
        original=settings.get('repair_source_map',{}).get(str(p.resolve()))
        if original and Path(original).is_file():
            try:
                origmeta=probe(original)
                if origmeta.get('time') is not None:
                    meta['time']=origmeta['time'];meta['created']=origmeta.get('created')
                meta['original_path']=str(Path(original).resolve())
            except (OSError,ValueError,subprocess.TimeoutExpired):pass
        metas[str(p.resolve())]=meta;rows.extend(chunks)
        progress(5+int(67*i/len(files)),f'Препознавање {i}/{len(files)} · {p.name}')
        if i%8==0:atomic_json(cachefile,cache)
    atomic_json(cachefile,cache)
    # Infer neutral detail/establishing shots only between agreeing reliable anchors
    # from the same camera directory, close in actual recorded time. Never infer across home boundaries.
    bycam=collections.defaultdict(list)
    for c in rows:bycam[str(Path(c['path']).parent)].append(c)
    for items in bycam.values():
        items.sort(key=lambda c:(metas[c['path']].get('time') is None,metas[c['path']].get('time') or 0,natural(c['path']),c['in']))
        for index,c in enumerate(items):
            if c['phase']!='unknown' or c['role'] not in {'detail','establishing'}:continue
            before=next((x for x in reversed(items[:index]) if x['phase']!='unknown' and not x['inferred']),None)
            after=next((x for x in items[index+1:] if x['phase']!='unknown' and not x['inferred']),None)
            now=metas[c['path']].get('time')
            if before and after and before['phase']==after['phase'] and now is not None:
                bt=metas[before['path']].get('time');at=metas[after['path']].get('time')
                if bt is not None and at is not None and 0<=now-bt<=300 and 0<=at-now<=300:
                    c.update(phase=before['phase'],confidence=.72,inferred=True,note='Детал меѓу две сигурни сцени од истото поглавје')
    atomic_json(Path(out)/'CHAPTER_ANALYSIS.json',{'schema':SCHEMA,'model':model,'vision_ready':vision,'clips':rows,'media':metas,'errors':errors})
    return rows,metas,errors

def pcm(path,start=0,seconds=90,rate=8000):
    import numpy as np
    r=run_process([ffmpeg(),'-nostdin','-v','error','-ss',str(start),'-i',str(path),'-t',str(seconds),'-vn','-ac','1','-ar',str(rate),'-f','f32le','pipe:1'],seconds+45)
    if r.returncode:return np.zeros(0,dtype=np.float32)
    return np.frombuffer(r.stdout,dtype='<f4').copy()

def audio_offset(a,b,rate=8000):
    """Positive lag means B starts later than A. Require distinctive common audio."""
    import numpy as np
    stride=max(1,round(rate*.01))
    def envelope(x):
        n=len(x)//stride
        if n<600:return np.zeros(0)
        y=np.sqrt(np.mean(x[:n*stride].reshape(n,stride)**2,axis=1));return np.log1p(y*40)
    x=envelope(a);y=envelope(b)
    if min(len(x),len(y))<600 or np.std(x)<.06 or np.std(y)<.06:return None
    x=x-x.mean();y=y-y.mean();n=1<<(len(x)+len(y)-2).bit_length()
    z=np.fft.irfft(np.fft.rfft(x,n)*np.fft.rfft(y[::-1],n),n)[:len(x)+len(y)-1]
    lags=np.arange(len(z))-(len(y)-1);valid=(np.abs(lags)<=4500)
    # Normalize independently at each lag, including only the overlapping samples.
    ex=np.r_[0,np.cumsum(x*x)];ey=np.r_[0,np.cumsum(y*y)]
    lo=np.maximum(0,lags);hi=np.minimum(len(x),len(y)+lags);ol=hi-lo
    bl=lo-lags;bh=hi-lags
    denom=np.sqrt(np.maximum(1e-12,(ex[hi]-ex[lo])*(ey[bh]-ey[bl])))
    scores=z/denom;scores[~valid|(ol<1200)]=-1
    pos=int(np.argmax(scores));best=float(scores[pos]);mask=(np.abs(lags-lags[pos])>120)&valid&(ol>=1200)
    second=float(np.max(scores[mask])) if mask.any() else 0
    if best<.68 or best-second<.12:return None
    return {'offset':round(float(lags[pos])*.01,3),'correlation':round(best,4),'separation':round(best-second,4),'overlap_seconds':round(float(ol[pos])*.01,2)}

def synchronize(rows,metas,out,enabled,progress,checkpoint):
    groups={};links=[];seen=set();signals={}
    if not enabled:return groups
    try:import numpy
    except ImportError:return groups
    candidates=[]
    for phase,_ in PHASES:
        paths=list(dict.fromkeys(c['path'] for c in rows if c['phase']==phase and not c['drone'] and metas[c['path']]['audio']))
        paths.sort(key=lambda p:(metas[p].get('time') is None,metas[p].get('time') or 0,natural(p)))
        for i,p in enumerate(paths):
            for other in paths[max(0,i-2):i]:
                if Path(p).parent==Path(other).parent:continue
                a=metas[other].get('time');b=metas[p].get('time')
                if a is None or b is None or abs(a-b)>90:continue
                pair=(other,p)
                if pair not in seen:seen.add(pair);candidates.append(pair)
    for i,(a,b) in enumerate(candidates,1):
        checkpoint()
        try:
            for p in (a,b):
                if p not in signals:signals[p]=pcm(p)
            match=audio_offset(signals[a],signals[b])
            if match:links.append({'a':a,'b':b,**match})
        except (OSError,ValueError,subprocess.TimeoutExpired):pass
        # Bound memory to recent audio comparisons.
        if len(signals)>6:signals.pop(next(iter(signals)))
        progress(72+int(9*i/max(1,len(candidates))),f'Проверка на заеднички звук {i}/{len(candidates)}')
    graph=collections.defaultdict(list)
    for l in links:graph[l['a']].append((l['b'],l['offset']));graph[l['b']].append((l['a'],-l['offset']))
    for root in graph:
        if root in groups:continue
        stack=[(root,0.)];visited={}
        while stack:
            p,off=stack.pop()
            if p in visited:continue
            visited[p]=off;stack.extend((other,off+lag) for other,lag in graph[p] if other not in visited)
        gid=hashlib.sha256(root.encode()).hexdigest()[:10]
        for p,off in visited.items():groups[p]={'group':gid,'offset':off}
    atomic_json(Path(out)/'AUDIO_SYNC.json',{'matches':links,'groups':groups,'note':'Only distinctive shared audio is accepted. Silent drones are grouped by scene, not audio-synchronized.'})
    return groups

def musical_points(path,seconds):
    """Candidate musical attacks; not a claim of perfect musical downbeats."""
    try:
        import numpy as np
        x=pcm(path,seconds=min(seconds,120),rate=11025);stride=551;n=len(x)//stride
        if n<40:return []
        energy=np.mean(x[:n*stride].reshape(n,stride)**2,axis=1);flux=np.maximum(0,np.diff(np.log1p(energy*2000),prepend=0))
        threshold=float(np.mean(flux)+np.std(flux)*.7);peaks=[i*.05 for i in range(2,n-2) if flux[i]>threshold and flux[i]>=max(flux[i-1],flux[i+1])]
        out=[]
        for t in peaks:
            if not out or t-out[-1]>.23:out.append(round(t,2))
        return out
    except (ImportError,OSError,ValueError,subprocess.TimeoutExpired):return []

def choose_music(music,metas,phase,used=None):
    used=used or set();wanted={
        'groom':('warm','acoustic','guitar','family','happy','soft'),
        'bride':('romantic','piano','love','gentle','cinematic','soft'),
        'church':('piano','ambient','peace','calm','soft','strings','sacred'),
        'reception':('party','dance','upbeat','happy','celebration','energetic')}[phase]
    banned=('preview','watermark','sample')
    usable=[p for p in music if str(p) in metas and metas[str(p)]['duration']>=8 and not any(x in Path(p).stem.casefold() for x in banned)]
    if not usable:raise ValueError('Нема читлива песна за спотчето. Додај музика во MUSIC во Premiere или во 02_MUSIC.')
    return max(usable,key=lambda p:(sum(x in Path(p).stem.casefold() for x in wanted)*10-(7 if str(p) in used else 0),min(metas[str(p)]['duration'],60),Path(p).name.casefold()))

def intro_shots(rows,max_seconds,beats,metas=None):
    ranked=[]
    for c in rows:
        # Entire original recordings longer than five minutes are never spot sources.
        if metas is not None and metas[c['path']]['duration']>300:continue
        if not c['usable']:continue
        for sample in c['samples']:
            if sample['brightness']<18 or sample['brightness']>240:continue
            d=min(4.5,c['out']-c['in']);a=max(c['in'],min(c['out']-d,sample['second']-d/2))
            role=c['role'];priority={'establishing':0,'detail':1,'preparation':2,'people':3,'ceremony':3,'celebration':3}.get(role,2)
            ranked.append({'path':c['path'],'in':a,'out':a+d,'role':role,'priority':0 if c['drone'] else priority,'score':sample['quality'],'hash':sample['hash']})
    selected=[];perfile=collections.Counter();hashes=[]
    for s in sorted(ranked,key=lambda s:(-s['score'],s['path'],s['in'])):
        if perfile[s['path']]>=2:continue
        if any(x['path']==s['path'] and abs(x['in']-s['in'])<5 for x in selected):continue
        # Only within this phase. Similar static views are spare shots, not repeated filler.
        if any((int(s['hash'])^int(h)).bit_count()<=3 for h in hashes):continue
        selected.append(s);hashes.append(s['hash']);perfile[s['path']]+=1
    selected.sort(key=lambda s:(s['priority'],-s['score'],s['path'],s['in']))
    out=[];cursor=0.
    for s in selected:
        if cursor>=max_seconds-.2:break
        length=min(s['out']-s['in'],max_seconds-cursor)
        near=[b for b in beats if 2.2<=b-cursor<=length and abs(b-(cursor+length))<.9]
        if near:length=max(near)-cursor
        if length<.6:continue
        out.append({**s,'out':s['in']+length,'timeline':cursor,'track':0,'sound':False});cursor+=length
    return out

def document_shots(rows,metas,sync=None):
    """One complete source clip per file, never clip a long documentary take.

    Audio sync is evidence only: automatic multicam edits would hide source takes.
    """
    sources=collections.defaultdict(list)
    for c in rows:sources[c['path']].append(c)
    paths=sorted(sources,key=lambda p:(metas[p].get('time') is None,
                    metas[p].get('time') or 0,natural(p)))
    out=[];cursor=0.
    for path in paths:
        meta=metas[path]
        span=float(meta['duration'])
        if span<=0:continue
        record=sources[path][0].copy()
        record.update({'in':0.,'out':span,'timeline':cursor,'track':0,'sound':True})
        out.append(record);cursor+=span
    return out

def consolidate_phase_by_file(rows,metas):
    """Each source belongs to exactly one long chapter. Mixed scenes are flagged.

    It is impossible to guarantee inclusion of a full 45-minute clip in one chapter
    if its visual chunks are spread across chapters, unless a chapter is chosen.
    """
    bypath=collections.defaultdict(list)
    for c in rows:bypath[c['path']].append(c)
    order={k:i for i,(k,_) in enumerate(PHASES)}
    mixed=[]
    for path,group in bypath.items():
        weights=collections.Counter()
        for c in group:weights[c['phase']]+=max(0.,c['out']-c['in'])
        winner=sorted(weights,key=lambda p:(-weights[p],order.get(p,99)))[0]
        if len(weights)>1:
            mixed.append({'source':path,'detected_phases':dict(weights),'chosen':winner,
              'reason':'Оригиналот се чува ЦЕЛ во едно долго поглавје; провери го изборот.'})
        for c in group:
            if c['phase']!=winner:
                c.update(phase=winner,uncertain=True,inferred=True,note='ПРОВЕРИ: мешани сцени во еден оригинал')
    return mixed


def assign_unknown(rows,metas):
    """Place uncertain footage in the main wedding, preserving explicit uncertainty.

    Use adjacent chronological anchors on the same camera, then global anchors.
    No claim is made that a fallback is a verified location.
    """
    natural_index={p:i for i,p in enumerate(sorted({x['path'] for x in rows},key=natural))}
    anchors=[c for c in rows if c['phase'] in dict(PHASES)]
    phases={k:i for i,(k,_) in enumerate(PHASES)}
    for c in rows:
        if c['phase']!='unknown':continue
        ct=metas[c['path']].get('time')
        cam=Path(c['path']).parent
        nearby=[a for a in anchors if Path(a['path']).parent==cam] or anchors
        def distance(a):
            at=metas[a['path']].get('time')
            if ct is not None and at is not None:
                return (abs((ct+c['in'])-(at+a['in'])),phases[a['phase']])
            return (abs(natural_index.get(c['path'],0)-natural_index.get(a['path'],0)),phases[a['phase']])
        if nearby:
            chosen=min(nearby,key=distance)['phase']
            reason='Приближно поглавје според соседни снимки; ПРОВЕРИ во Premiere'
        else:
            chosen='groom'
            reason='Нема препознаено поглавје; привремено кај зетот, ПРОВЕРИ'
        c.update(phase=chosen,inferred=True,confidence=0.,note=reason,uncertain=True)
    return rows


def add_dissolves(shots,seconds=.45):
    # Alternating tracks retain every source frame. Only upper clips receive an
    # opacity ramp: this is a true A/B dissolve, not two simultaneous fades to black.
    cursor=0.;out=[]
    for i,shot in enumerate(shots):
        c=shot.copy();length=c['out']-c['in']
        overlap=0 if not i else min(seconds,length/5,(out[-1]['out']-out[-1]['in'])/5)
        # Keep continuous ceremony/common audio joins intact.
        if i and (c.get('audio_source') or out[-1].get('audio_source') or (c['path']==out[-1]['path'] and abs(c['in']-out[-1]['out'])<.1)):overlap=0
        c.update(timeline=max(0,cursor-overlap),track=i%2,fade_in=0.,fade_out=0.)
        if overlap:
            if c['track']==1:c['fade_in']=overlap
            else:out[-1]['fade_out']=overlap
            c['audio_fade_in']=overlap;out[-1]['audio_fade_out']=overlap
        out.append(c);cursor=c['timeline']+length
    return out



def detect_camera_motion(rows, metas, out, progress=lambda *x: None, checkpoint=lambda: None):
    """Conservative, optional optical-flow review hints; NEVER trim or reject footage.

    Sparse frame-pair comparisons are not proof of camera shake: intentional pans,
    subject motion and flash changes can cause false positives. Review manually.
    """
    try:
        import cv2
        import numpy as np
    except ImportError:
        atomic_json(Path(out)/'CAMERA_MOTION.json', {'status':'unavailable', 'reason':'OpenCV/numpy missing', 'flags':[]})
        return []
    flags=[]; paths=list(dict.fromkeys(c['path'] for c in rows))
    for index,path in enumerate(paths,1):
        checkpoint(); duration=float(metas[path]['duration'])
        cap=cv2.VideoCapture(path)
        if not cap.isOpened():continue
        try:
            # At most 180 sparse checks per original source, keeping runtime bounded.
            stride=max(8.,duration/180.)
            for j in range(int(max(0,duration-.5)//stride)+1):
                checkpoint(); t=min(duration-.45,j*stride+.15)
                frames=[]
                for sec in (t,t+.22):
                    cap.set(cv2.CAP_PROP_POS_MSEC,sec*1000)
                    ok,frame=cap.read()
                    if not ok:break
                    h,w=frame.shape[:2]
                    gray=cv2.cvtColor(cv2.resize(frame,(320,max(64,int(320*h/max(1,w))))),cv2.COLOR_BGR2GRAY)
                    frames.append(gray)
                if len(frames)!=2:continue
                points=cv2.goodFeaturesToTrack(frames[0],maxCorners=130,qualityLevel=.025,minDistance=9)
                if points is None or len(points)<18:continue
                nxt,status,_=cv2.calcOpticalFlowPyrLK(frames[0],frames[1],points,None)
                if nxt is None or status is None:continue
                good=status.reshape(-1)>0
                if good.sum()<18:continue
                delta=(nxt[good]-points[good]).reshape(-1,2)
                median=np.median(delta,axis=0)
                magnitude=float(np.linalg.norm(median)/320.)
                if magnitude>=.055:
                    flags.append({'path':path,'second':round(t,2),'movement_ratio':round(magnitude,4),
                                  'note':'ПРОВЕРИ – нагло движење (автоматска проценка, можен намерен пан)'})
        finally:cap.release()
        progress(82+int(4*index/max(1,len(paths))),f'Проверка движење {index}/{len(paths)}')
    atomic_json(Path(out)/'CAMERA_MOTION.json',{'status':'analyzed','sample_method':'sparse optical flow; not definitive shake detection','flags':flags})
    return flags


def attach_review_markers(seq, shots, flags, fps=25):
    """Attach review markers at corresponding source time in a given sequence."""
    bypath=collections.defaultdict(list)
    for f in flags:bypath[f['path']].append(f)
    found=set()
    for shot in shots:
        for f in bypath.get(shot['path'],[]):
            if not shot['in']<=f['second']<shot['out']:continue
            position=round((shot['timeline']+f['second']-shot['in'])*fps)
            key=(position,shot['path'])
            if key in found:continue
            found.add(key)
            marker=tag(seq,'marker');tag(marker,'name','ПРОВЕРИ – нагло движење');tag(marker,'comment',Path(shot['path']).name+' @ '+str(f['second'])+'s; можен намерен пан');tag(marker,'in',position);tag(marker,'out',-1)

def tag(parent,name,value=None,**attrs):
    e=ET.SubElement(parent,name,**attrs)
    if value is not None:e.text=str(value)
    return e

def rate(parent,fps):
    ntsc=any(abs(fps-x)<.03 for x in (23.976,29.97,59.94));r=tag(parent,'rate');tag(r,'timebase',round(fps));tag(r,'ntsc','TRUE' if ntsc else 'FALSE')

def effect_envelope(clip,parameter,points):
    f=tag(clip,'filter');e=tag(f,'effect');is_audio=parameter=='level'
    for n,v in [('name','Audio Levels' if is_audio else 'Opacity'),('effectid','audiolevels' if is_audio else 'opacity'),('effectcategory','audio' if is_audio else 'motion'),('effecttype','audio' if is_audio else 'motion'),('mediatype','audio' if is_audio else 'video')]:tag(e,n,v)
    p=tag(e,'parameter');tag(p,'parameterid',parameter);tag(p,'name','Level' if is_audio else 'opacity');tag(p,'valuemin',0);tag(p,'valuemax',4 if is_audio else 100);tag(p,'value',1 if is_audio else 100)
    for when,value in sorted(set(points)):
        k=tag(p,'keyframe');tag(k,'when',when);tag(k,'value',value);inter=tag(k,'interpolation');tag(inter,'name','linear')

def file_node(parent,meta,ident):
    f=tag(parent,'file',None,id=ident);p=Path(meta['path']);tag(f,'name',p.name);tag(f,'pathurl',p.resolve().as_uri());tag(f,'duration',round(meta['duration']*meta['fps']));rate(f,meta['fps'])
    m=tag(f,'media')
    if meta['video']:
        v=tag(m,'video');sc=tag(v,'samplecharacteristics');rate(sc,meta['fps']);tag(sc,'width',meta['width']);tag(sc,'height',meta['height']);tag(sc,'anamorphic','FALSE');tag(sc,'pixelaspectratio','square');tag(sc,'fielddominance','none')
    if meta['audio']:
        a=tag(m,'audio');tag(a,'channelcount',meta['channels']);s=tag(a,'samplecharacteristics');tag(s,'depth',16);tag(s,'samplerate',meta['samplerate'])
    return f

def sequence_xml(ident,name,shots,metas,music=None,fps=25,width=1920,height=1080,music_cues=None):
    seq=ET.Element('sequence',id=ident);tag(seq,'name',name);rate(seq,fps)
    duration=round(max((s['timeline']+s['out']-s['in'] for s in shots),default=0)*fps);tag(seq,'duration',duration)
    media=tag(seq,'media');video=tag(media,'video');fmt=tag(video,'format');sc=tag(fmt,'samplecharacteristics');rate(sc,fps);tag(sc,'width',width);tag(sc,'height',height);tag(sc,'anamorphic','FALSE');tag(sc,'pixelaspectratio','square');tag(sc,'fielddominance','none')
    vtracks=[tag(video,'track') for _ in range(max((s.get('track',0) for s in shots),default=0)+1)]
    aud=tag(media,'audio');tag(aud,'numOutputChannels',2);af=tag(aud,'format');asc=tag(af,'samplecharacteristics');tag(asc,'depth',16);tag(asc,'samplerate',48000)
    atracks=[tag(aud,'track') for _ in range(len(vtracks)*2+2)]
    defined=set()
    def item(track,s,index,audio=False,channel=1):
        meta=metas[s['path']]
        # FCP XML clipitems in a sequence use the sequence timebase for
        # IN/OUT, START/END and duration. Only the *file* advertises the native
        # source frame rate. Mixing e.g. 50-fps IN/OUT with 25-fps timeline
        # created clips whose media boundaries could be 2x too long in Adobe.
        sr=fps
        a=max(0,round(s['in']*sr));b=min(round(meta['duration']*sr),round(s['out']*sr))
        ci=tag(track,'clipitem',None,id=f'{ident}-{"a" if audio else "v"}-{index}-{channel}');tag(ci,'name',Path(s['path']).name);tag(ci,'enabled','TRUE');tag(ci,'duration',max(1,b-a));rate(ci,sr)
        tag(ci,'start',round(s['timeline']*fps));tag(ci,'end',round((s['timeline']+s['out']-s['in'])*fps));tag(ci,'in',a);tag(ci,'out',b)
        fid='file-'+hashlib.sha256((ident+'|'+s['path']).encode()).hexdigest()[:16]
        if fid not in defined:file_node(ci,meta,fid);defined.add(fid)
        else:tag(ci,'file',None,id=fid)
        if audio:
            source=tag(ci,'sourcetrack');tag(source,'mediatype','audio');tag(source,'trackindex',min(channel,max(1,meta['channels'])))
            fi=float(s.get('audio_fade_in',0));fo=float(s.get('audio_fade_out',0));level=float(s.get('audio_level',1.))
            if fi or fo or level!=1:
                points=[(a,0 if fi else level),(a+round(fi*sr),level),(max(a,b-1-round(fo*sr)),level),(max(a,b-1),0 if fo else level)]
                effect_envelope(ci,'level',points)
        elif s.get('fade_in') or s.get('fade_out'):
            fi=s.get('fade_in',0);fo=s.get('fade_out',0)
            effect_envelope(ci,'opacity',[(a,0 if fi else 100),(a+round(fi*sr),100),(max(a,b-1-round(fo*sr)),100),(max(a,b-1),0 if fo else 100)])
        return ci
    for i,s in enumerate(shots):
        video_index=len(vtracks[s.get('track',0)].findall('clipitem'))+1
        v=item(vtracks[s.get('track',0)],s,i)
        audios=s.get('audio_source') or {'path':s['path'],'in':s['in'],'out':s['out']}
        if s.get('sound') and metas[audios['path']]['audio']:
            ac={**s,**audios};links=[(v,'video',s.get('track',0)+1,video_index)]
            for ch in (1,2):
                trackno=s.get('track',0)*2+ch
                audio_index=len(atracks[trackno-1].findall('clipitem'))+1
                a=item(atracks[trackno-1],ac,i,True,ch);links.append((a,'audio',trackno,audio_index))
            for node,_,_,_ in links:
                for other,kind,trackno,clipindex in links:
                    l=tag(node,'link');tag(l,'linkclipref',other.attrib['id']);tag(l,'mediatype',kind);tag(l,'trackindex',trackno);tag(l,'clipindex',clipindex)
    cues=list(music_cues or [])
    if music and duration:
        p=str(Path(music).resolve());length=min(duration/fps,metas[p]['duration'])
        cues.append({'path':p,'in':0.,'out':length,'timeline':0.,
                     'audio_fade_in':min(.8,length/5),'audio_fade_out':min(1.5,length/5),'audio_level':.55})
    for mi,cue in enumerate(cues):
        for ch in (1,2):item(atracks[-3+ch],cue,f'music{mi}',True,ch)
    tag(seq,'in',0);tag(seq,'out',duration)
    return seq

def write_xml(path,sequences):
    root=ET.Element('xmeml',version='4')
    for seq in sequences:root.append(seq)
    ET.indent(root,space='  ');ET.ElementTree(root).write(path,encoding='utf-8',xml_declaration=True)

def validate_wedding_xml(xml_path,metas):
    """Static checks catch missing paths, broken ref ids and source-time overflow before Premiere.
    Cannot certify Adobe import, codec/GPU compatibility or all frames.
    """
    doc=ET.parse(xml_path).getroot();errors=[];warnings=[];details=[]
    known_media={Path(m['path']).resolve().as_uri():m for m in metas.values()}
    for seq in doc.findall('./sequence'):
        name=seq.findtext('name','')
        definitions={node.attrib['id']:node for node in seq.findall('.//file') if node.findtext('pathurl')}
        for clip in seq.findall('.//clipitem'):
            fn=clip.find('file');fid=fn.get('id') if fn is not None else None
            source=definitions.get(fid)
            if source is None:
                errors.append(f'{name}: unresolved file reference {fid}');continue
            try:
                pathurl=source.findtext('pathurl','')
                a=int(clip.findtext('in','0'));b=int(clip.findtext('out','0'))
                start=int(clip.findtext('start','0'));end=int(clip.findtext('end','0'))
                if a<0 or b<=a:errors.append(f'{name}: invalid source IN/OUT for {fid}')
                if start<0 or end<=start:errors.append(f'{name}: invalid timeline START/END for {fid}')
                if not pathurl.startswith('file:'):errors.append(f'{name}: invalid URL for {fid}')
                if pathurl not in known_media:errors.append(f'{name}: original media path not found in manifest for {fid}')
                else:
                    native=known_media[pathurl]
                    sequence_rate=float(seq.findtext('./rate/timebase','25') or '25')
                    limit=round(native['duration']*sequence_rate)+2
                    if b>limit:errors.append(f'{name}: source OUT exceeds duration for {fid} ({b}>{limit})')
                if abs((b-a)-(end-start))>2:
                    errors.append(f'{name}: source and timeline lengths differ for {fid}')
            except (ValueError,TypeError):errors.append(f'{name}: non-numeric clip fields {fid}')
        details.append({'sequence':name,'clips':len(seq.findall('./media/video/track/clipitem')),
                        'audio_clips':len(seq.findall('./media/audio/track/clipitem')),
                        'sources':len(definitions)})
    # The existence of <file> IDs alone does not prove that footage has frames
    # across its full duration: structural validity != successful Premiere decode.
    return {'status':'error' if errors else 'ok','errors':errors,'warnings':warnings,
            'sequences':details,'note':'XML structure and source timing only; Adobe must verify codecs and import'}


def audit_long_footage(xml_path, rows, metas, fps=25):
    """Prove every readable source is represented in each expected long timeline
    and in ЦЕЛА СВАДБА, full source IN/OUT, without silently dropping clips.
    """
    root=ET.parse(xml_path).getroot()
    expected={x['path']:x['phase'] for x in rows}
    expected={p:phase for p,phase in expected.items() if p in metas and metas[p].get('video')}
    actual_by_seq={}
    for seq in root.findall('./sequence'):
        name=seq.findtext('name','')
        files={n.get('id'):n.findtext('pathurl','') for n in seq.findall('.//file') if n.findtext('pathurl')}
        rows_out=[]
        for ci in seq.findall('./media/video/track/clipitem'):
            file_el=ci.find('file')
            if file_el is None:continue
            rows_out.append((files.get(file_el.get('id')),
                             int(ci.findtext('in','-1')),int(ci.findtext('out','-1'))))
        actual_by_seq[name]=rows_out
    errors=[];checked=0
    for path,phase in expected.items():
        uri=Path(path).resolve().as_uri()
        need=round(metas[path]['duration']*fps)
        label=dict(PHASES)[phase]
        for seq_name,seq_rows in actual_by_seq.items():
            is_main='ЦЕЛА СВАДБА' in seq_name
            is_long=(label in seq_name and 'ДОЛГО' in seq_name)
            if not (is_main or is_long):continue
            matches=[(a,b) for url,a,b in seq_rows if url==uri and a==0 and b>=need-1]
            if matches:checked+=1
            else:errors.append(f'{Path(path).name} is missing/incomplete in {seq_name}: 0..{need} frames')
        if not any('ЦЕЛА СВАДБА' in n for n in actual_by_seq):errors.append('Main wedding sequence missing')
        if not any(label in n and 'ДОЛГО' in n for n in actual_by_seq):errors.append(f'Missing long sequence for {label}')
    return {'status':'error' if errors else 'ok','files_checked':len(expected),
            'full_clip_confirmations':checked,'errors':errors[:150],
            'note':'Full source ranges in generated XML; not a Premiere render/decoder test'}

def build_wedding(files,root,out,title,music,settings,progress=lambda *x:None,checkpoint=lambda:None):
    root=Path(root).resolve();out=Path(out);out.mkdir(parents=True,exist_ok=True);title=safe_name(title)
    music=list(dict.fromkeys(str(Path(p).resolve()) for p in music if Path(p).is_file() and Path(p).suffix.lower() in AUDIO_EXT));musicmeta={}
    for p in music:
        checkpoint()
        try:musicmeta[p]=probe(p)
        except (OSError,ValueError,subprocess.TimeoutExpired):pass
    if not musicmeta:raise ValueError('Нема достапна музика. Додај песна во MUSIC во Premiere или во 02_MUSIC.')
    progress(4,'Ја проверувам музиката и изворите')
    rows,metas,errors=analyze_sources(files,root,out,settings,progress,checkpoint);metas.update(musicmeta)
    if not rows:raise ValueError('Нема читливи видеа. Провери го CHAPTER_ANALYSIS.json.')
    sync=synchronize(rows,metas,out,settings.get('audio_sync',True),progress,checkpoint)
    unknown_count=sum(c['phase']=='unknown' for c in rows)
    motion_flags=detect_camera_motion(rows,metas,out,progress,checkpoint) if settings.get('motion_review',True) else []
    assign_unknown(rows,metas)
    mixed_phases=consolidate_phase_by_file(rows,metas)
    dims=collections.Counter((m['width'],m['height']) for m in metas.values() if m['video'])
    width,height=max(dims,key=lambda d:(dims[d],d[0]*d[1])) if dims else (1920,1080)
    fps=25;maximum=min(60,max(8,int(settings.get('seconds',60))))
    sequences=[];combined=[];combined_music=[];cursor=0.;chapters=[];used=set();markers=[]
    spot_choices=[];transition_rows=[]
    for index,(phase,label) in enumerate(PHASES,1):
        checkpoint();parts=[c for c in rows if c['phase']==phase];progress(81+index*3,'Секвенци · '+label)
        if not parts:chapters.append({'phase':phase,'clips':0,'intro_seconds':0,'long_seconds':0});continue
        manual=settings.get('phase_music',{}).get(phase)
        song=str(Path(manual).resolve()) if manual else str(choose_music(music,musicmeta,phase,used))
        if song not in musicmeta:raise ValueError('Невалидна песна за '+label+': '+song)
        used.add(song)
        target=min(maximum,musicmeta[song]['duration']);beats=musical_points(song,target)
        intro=intro_shots(parts,target,beats,metas);long=document_shots(parts,metas,sync)
        if settings.get('dissolve',True):
            per_phase=settings.get('phase_dissolves',{})
            sec=per_phase.get(phase,settings.get('documentary_dissolve_seconds',.45))
            long=add_dissolves(long,seconds=max(.15,min(1.5,float(sec))))
            for si,ss in enumerate(long):
                if si and (ss.get('fade_in') or long[si-1].get('fade_out')):
                    transition_rows.append([label,Path(long[si-1]['path']).name,Path(ss['path']).name,
                                            round(ss.get('fade_in') or long[si-1].get('fade_out'),3),
                                            'A/B opacity keyframes; must verify Premiere import'])
        intro_len=max((c['timeline']+c['out']-c['in'] for c in intro),default=0);long_len=max((c['timeline']+c['out']-c['in'] for c in long),default=0)
        if intro:
            markers.append((cursor,label+' · СПОТ'))
            sequences.append(sequence_xml(f'td-{phase}-spot',f'{title} · {index:02d} {label} · СПОТ',intro,metas,song,fps,width,height))
            combined.extend({**c,'timeline':c['timeline']+cursor} for c in intro);combined_music.append({'path':song,'in':0.,'out':intro_len,'timeline':cursor,'audio_fade_in':min(.7,intro_len/5),'audio_fade_out':min(1.3,intro_len/5),'audio_level':.55});cursor+=intro_len
        # An additional ONE-CHAPTER sequence for practical editing:
        # spot followed by full documentary (the existing separate spot/long stay).
        chapter_timeline=[c.copy() for c in intro]
        chapter_timeline.extend({**c,'timeline':c['timeline']+intro_len} for c in long)
        if chapter_timeline:
            cues=[{'path':song,'in':0.,'out':intro_len,'timeline':0.,
                   'audio_fade_in':min(.7,intro_len/5),'audio_fade_out':min(1.3,intro_len/5),'audio_level':.55}] if intro_len else []
            chapter_seq=sequence_xml(f'td-{phase}-chapter',
                f'{title} · {index:02d} {label} · ЦЕЛО ПОГЛАВЈЕ',chapter_timeline,
                metas,None,fps,width,height,music_cues=cues)
            attach_review_markers(chapter_seq,chapter_timeline,motion_flags,fps)
            sequences.append(chapter_seq)
        for shot in intro:
            spot_choices.append([label,Path(shot['path']).name,round(shot['in'],3),
                                 round(shot['out'],3),shot.get('role',''),
                                 round(float(shot.get('score',0)),3),Path(song).name])
        if long:
            markers.append((cursor,label+' · ДОЛГО'))
            long_sequence=sequence_xml(f'td-{phase}-long',f'{title} · {index:02d} {label} · ДОЛГО',long,metas,None,fps,width,height)
            attach_review_markers(long_sequence,long,motion_flags,fps)
            sequences.append(long_sequence)
            combined.extend({**c,'timeline':c['timeline']+cursor} for c in long);cursor+=long_len
        chapters.append({'phase':phase,'clips':len(parts),'intro_seconds':round(intro_len,2),'long_seconds':round(long_len,2),'music':song})
    if combined:
        main=sequence_xml('td-main-wedding',f'{title} · ЦЕЛА СВАДБА',combined,metas,None,fps,width,height,music_cues=combined_music)
        attach_review_markers(main,combined,motion_flags,fps)
        for second,name in markers:
            marker=tag(main,'marker');tag(marker,'name',name);tag(marker,'in',round(second*fps));tag(marker,'out',-1)
        sequences.insert(0,main)
    # No separate review sequence: uncertain clips are already in the main wedding.
    unknown=[c for c in rows if c.get('uncertain')]
    path=out/(title+'_Цела_свадба.xml');write_xml(path,sequences)
    audit=validate_wedding_xml(path,metas)
    preservation=audit_long_footage(path,rows,metas,fps)
    audit['long_footage']=preservation
    atomic_json(out/'XML_AUDIT.json',audit)
    if audit['errors'] or preservation['errors']:
        raise RuntimeError('XML или целосните оригинали НЕ ја поминаа проверката; види XML_AUDIT.json: '+str((audit['errors']+preservation['errors'])[:2]))
    counts=collections.Counter(c['phase'] for c in rows)
    result={'xml':str(path),'chapters':chapters,'review_count':unknown_count,'assigned_count':len(rows),'total_seconds':round(cursor,2),'errors':errors,'synced_sources':len(sync),'motion_flags':len(motion_flags),'mixed_phase_sources':mixed_phases,'xml_audit':audit,'width':width,'height':height}
    atomic_json(out/'RESULT.json',result)
    with (out/'CHAPTERS.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f);writer.writerow(['Source','IN seconds','OUT seconds','Phase','Confidence','Inference','Uncertain','Drone','Note'])
        for c in rows:writer.writerow([c['path'],c['in'],c['out'],c['phase'],c['confidence'],c['inferred'],c.get('uncertain',False),c['drone'],c['note']])
    with (out/'CAMERA_MOTION.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f);writer.writerow(['Source','Second','Movement ratio','Review note'])
        for flag in motion_flags:writer.writerow([flag['path'],flag['second'],flag['movement_ratio'],flag['note']])
    with (out/'PHASE_MUSIC.csv').open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['Chapter','Song','Selection'])
        for chapter in chapters:
            if chapter.get('music'):
                phase=chapter['phase']
                w.writerow([dict(PHASES)[phase],Path(chapter['music']).name,
                           'manual' if settings.get('phase_music',{}).get(phase) else 'auto by filename'])
    with (out/'SPOT_SELECTION.csv').open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['Chapter','Filename','Source IN seconds','Source OUT seconds','Role','Quality score','Music']);w.writerows(spot_choices)
    with (out/'TRANSITIONS.csv').open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['Chapter','From','To','Overlap seconds','Import note']);w.writerows(transition_rows)
    (out/'MEDIA_LINKS.csv').write_text('Source path\n'+''.join('\"'+p.replace('\"','\"\"')+'\"\n' for p in sorted({x['path'] for x in rows})),encoding='utf-8-sig')
    (out/'READ_ME_MK.txt').write_text('TD AI Studio v2.8 — монтажа за преглед\n\n'+
        'Главната секвенца е: СПОТ кај зетот, ДОЛГО кај зетот, СПОТ кај невестата, ДОЛГО кај невестата, СПОТ црква, ДОЛГО црква, СПОТ ресторан, ДОЛГО ресторан.\n'+
        'Празните поглавја се прескокнати. Нејасните сцени се во ЦЕЛА СВАДБА, со белешка во CHAPTERS.csv.\n'+
        'Долгите секвенци ги содржат СИТЕ читливи извори, вклучувајќи дрон и паралелни камери, без отфрлање на преклопени снимки. Извори над 300 секунди не се користат во спотови.\n'+
        'Синхронизацијата е информативна во AUDIO_SYNC.json; не ги сече долгите извори. Овој пакет НЕ прави автоматско префрлување на агли.\n'+
        'Нагли движења: CAMERA_MOTION.csv и маркери во XML, доколку OpenCV е инсталиран. Можно е лажно означување при намерен пан; ништо не се отсекува.\n'+
        'Dissolve е преклопување на две видео патеки со opacity keyframes. Секој изворен кадар останува во XML; краткиот преклоп може да измеша два соседни звука. Провери го увозот и премините во твојот Premiere.\n'+
        'Музиката е од достапните локални песни; називите помагаат да се избере атмосфера. Нема купување или преземање Adobe Stock. Користи песни за кои имаш право на употреба.\n'+
        f'Нејасни делови: {unknown_count}. Погрешни визуелни класификации се можни; прегледај ги поглавјата пред испорака.\n',encoding='utf-8')
    progress(96,'XML и секвенците се подготвени за Premiere')
    return result
