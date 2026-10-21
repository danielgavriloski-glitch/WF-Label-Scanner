"""TD Production AI Studio 1.5 — offline Wedding Rough Cut & local AI visual review.
Originals are READ-ONLY. Never modifies source media. Exports FCP7 XML + review files.
Ollama model is optional; without it, labels are TECHNICAL ONLY.
"""
from __future__ import annotations
import argparse, base64, collections, csv, hashlib, io, json, os, re, sys, time, urllib.request, urllib.error
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

# FORCE UTF-8 LOGGING on Windows, including when CMD uses cp1252 and stdout is piped.
# Without this, Cyrillic camera folder names raise UnicodeEncodeError before analysis.
def _td_force_utf8_console():
    for s in (sys.stdout, sys.stderr):
        try:
            s.reconfigure(encoding='utf-8', errors='backslashreplace')
        except (AttributeError, ValueError, OSError):
            pass
_td_force_utf8_console()
import xml.etree.ElementTree as ET
import cv2
from camera_sources import discover_sources
import numpy as np
from guard import wait_for_job
from mutagen import File as MutagenFile

FPS=25
VIDEO_EXT={'.mp4','.mov','.avi','.m4v','.mkv','.mts','.m2ts'}
AUDIO_EXT={'.mp3','.wav','.aif','.aiff','.m4a'}
VISUAL_MODEL='qwen2.5vl:3b'

def tag(p,n,v=None,**attrs):
    e=ET.SubElement(p,n,**attrs)
    if v is not None:e.text=str(v)
    return e

def rate(p):
    r=tag(p,'rate');tag(r,'timebase',FPS);tag(r,'ntsc','FALSE')

def song_seconds(path):
    if not path:return None
    try:
        f=MutagenFile(str(path));return float(f.info.length)
    except Exception:return None

def analyze(path, quality='standard'):
    cap=cv2.VideoCapture(str(path))
    if not cap.isOpened():return []
    sr=cap.get(cv2.CAP_PROP_FPS)
    count=cap.get(cv2.CAP_PROP_FRAME_COUNT)
    duration=count/sr if sr and sr>0 else 0
    if duration<2:cap.release();return []
    interval={'fast':15,'standard':7,'deep':3.5}.get(quality,7)
    ts=np.arange(min(1,duration/3),max(.1,duration-.75),interval)
    cascade=Path(cv2.data.haarcascades)/'haarcascade_frontalface_default.xml'
    faces_cv=cv2.CascadeClassifier(str(cascade))
    entries=[]
    for t in ts:
        wait_for_job()
        cap.set(cv2.CAP_PROP_POS_MSEC,float(t*1000));ok,frame=cap.read()
        if not ok:continue
        h,w=frame.shape[:2];scale=min(1,480/max(w,1))
        frame=cv2.resize(frame,(max(1,int(w*scale)),max(1,int(h*scale))))
        gray=cv2.cvtColor(frame,cv2.COLOR_BGR2GRAY)
        bright=float(gray.mean());sharp=float(cv2.Laplacian(gray,cv2.CV_64F).var())
        if not faces_cv.empty():
            found=faces_cv.detectMultiScale(gray,1.15,4,minSize=(24,24))
            faces=len(found)
        else:faces=0
        tech=.55*min(1,sharp/155)+.45*max(0,1-abs(bright-122)/103)
        couple=1 if faces>=2 else 0
        score=.70*tech+.22*couple+.08*min(faces,1)
        cliplen=4.0 if score>.58 else 3.2
        start=max(0,min(duration-cliplen-.1,float(t)-cliplen/2))
        end=min(duration,start+cliplen)
        if end-start>1.7:
            entries.append(dict(score=round(float(score),4),tech=round(float(tech),4),path=path,
                start=float(start),end=float(end),timestamp=float(t),faces=faces,
                couple=couple,source_duration=float(duration),scene='unknown',romance=0.0,ai_checked=False))
    cap.release();return entries

def ollama_ready():
    try:
        with urllib.request.urlopen('http://127.0.0.1:8798/api/tags',timeout=3) as r:
            v=json.load(r)
        return any(m.get('name','').split(':')[0]=='qwen2.5vl' and m.get('name','').endswith(':3b') for m in v.get('models',[]))
    except Exception:return False

def capture_jpg(path,t):
    cap=cv2.VideoCapture(str(path));cap.set(cv2.CAP_PROP_POS_MSEC,t*1000)
    ok,f=cap.read();cap.release()
    if not ok:return None
    h,w=f.shape[:2];s=min(1,640/max(w,1))
    f=cv2.resize(f,(int(w*s),int(h*s)))
    ok,buf=cv2.imencode('.jpg',f,[int(cv2.IMWRITE_JPEG_QUALITY),72]);return buf.tobytes() if ok else None

def predict_scene(jpg):
    system=('You classify one wedding video FRAME, not a whole clip. '
       'Do not infer identity. Return ONLY a JSON object with keys "scene" and "romance" and "note". '
       'scene one of "couple", "ceremony", "party", "details", "other". '
       'romance 0..1 indicates VISUAL evidence that two people are engaged in a tender couple moment '
       '(holding hands, hugging, kissing, intimate couple posing). Do not claim actions not shown. '
       'note short English description of actual pixels. Use romance 0 if uncertain.')
    req={'model':VISUAL_MODEL,'stream':False,'format':'json',
         'messages':[{'role':'system','content':system},
         {'role':'user','content':'Classify this single frame.',
          'images':[base64.b64encode(jpg).decode('ascii')]}],
         'options':{'temperature':0,'num_ctx':1500}}
    payload=json.dumps(req).encode('utf-8')
    r=urllib.request.Request('http://127.0.0.1:8798/api/chat',data=payload,headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(r,timeout=120) as res:result=json.load(res)
    content=result.get('message',{}).get('content','{}')
    parsed=json.loads(content)
    scene=str(parsed.get('scene','other')).lower()
    if scene not in ('couple','ceremony','party','details','other'):scene='other'
    try: romance=max(0,min(1,float(parsed.get('romance',0))))
    except Exception:romance=0
    return dict(scene=scene,romance=romance,note=str(parsed.get('note',''))[:110])

def semantic_rerank(candidates,out,per_video=4):
    """All source files represented, if decodable. Bounded model calls; cache on disk."""
    if not ollama_ready():
        print('ROMANTIC AI UNAVAILABLE: install Ollama and run ollama pull qwen2.5vl:3b',flush=True)
        return False
    cache_file=out/'vision_cache_v1_5.json'
    try: cache=json.loads(cache_file.read_text('utf-8'))
    except Exception:cache={}
    grouped=collections.defaultdict(list)
    for c in candidates:grouped[c['path']].append(c)
    to_classify=[]
    for path,cs in grouped.items():
        # First sample from each source + strongest technical samples spread in time
        cs.sort(key=lambda x:x['timestamp'])
        picks=[]
        if cs:picks.append(cs[len(cs)//2])
        for c in sorted(cs,key=lambda c:c['tech'],reverse=True):
            if len(picks)>=per_video:break
            if all(abs(c['timestamp']-p['timestamp'])>=5 for p in picks):picks.append(c)
        to_classify.extend(picks)
    print('AI VISION FRAMES:',len(to_classify),'(cached on future runs)',flush=True)
    valid=0
    for i,c in enumerate(to_classify,1):
        wait_for_job()
        p=c['path']
        try:mt=p.stat().st_mtime_ns
        except OSError:mt=0
        key=hashlib.sha256(f'{p}|{mt}|{round(c["timestamp"],1)}'.encode('utf-8')).hexdigest()
        if key in cache:prediction=cache[key]
        else:
            jpg=capture_jpg(p,c['timestamp'])
            if not jpg:continue
            try:prediction=predict_scene(jpg)
            except Exception as exc:
                print('AI VISION ERROR:',type(exc).__name__,str(exc)[:200],flush=True)
                if valid==0:return False
                continue
            cache[key]=prediction
            if i%8==0:
                cache_file.write_text(json.dumps(cache,ensure_ascii=False),encoding='utf-8')
        c.update(scene=prediction.get('scene','other'),romance=float(prediction.get('romance',0)),
            note=prediction.get('note',''),ai_checked=True)
        c['score']=round(.42*c['tech']+.48*c['romance']+.10*c['couple'],4)
        valid+=1
        print(f'AI VISION {i}/{len(to_classify)} {p.name} {c["scene"]} romantic={c["romance"]:.2f}',flush=True)
    cache_file.write_text(json.dumps(cache,ensure_ascii=False),encoding='utf-8')
    print('AI VISION VERIFIED FRAMES:',valid,flush=True)
    return valid>0

def choose(candidates, seconds, semantic, max_extra=100):
    if semantic:
        usable=[c for c in candidates if c['ai_checked']]
        if sum(c['end']-c['start'] for c in usable)>=seconds*.75:
            candidates=usable
    ranked=sorted(candidates,key=lambda c:(-c['score'],-c['romance'],str(c['path']),c['start']))
    selected=[];per_file=collections.Counter();total=0
    def near(c,pool,delta=8):return any(x['path']==c['path'] and abs(x['start']-c['start'])<delta for x in pool)
    # Give visually recognized romantic moments the first portion of the montage.
    romantic=[c for c in ranked if c['ai_checked'] and c['romance']>=.55]
    if romantic:
        budget=min(seconds*.57,sum(c['end']-c['start'] for c in romantic))
        for c in romantic:
            if total>=budget:break
            if near(c,selected):continue
            selected.append(c);per_file[c['path']]+=1;total+=c['end']-c['start']
    for limit in (1,2,4,999):
        for c in ranked:
            if total>=seconds:break
            if per_file[c['path']]>=limit or near(c,selected):continue
            selected.append(c);per_file[c['path']]+=1;total+=c['end']-c['start']
        if total>=seconds:break
    # Romantic opening + varied second half + romantic closing.
    if romantic:
        love=[c for c in selected if c['ai_checked'] and c['romance']>=.55]
        other=[c for c in selected if c not in love]
        selected=love[:max(1,len(love)//3)]+other[:max(1,len(other)//2)]+love[max(1,len(love)//3):]+other[max(1,len(other)//2):]
    main=[];remain=seconds
    for c in selected:
        duration=min(c['end']-c['start'],remain)
        if duration<.55:break
        main.append({**c,'end':c['start']+duration});remain-=duration
        if remain<=.08:break
    extra=[];counts=collections.Counter()
    for c in ranked:
        if len(extra)>=max_extra:break
        if counts[c['path']]>=3 or near(c,extra+main):continue
        extra.append(c);counts[c['path']]+=1
    return main,extra

def source_file(parent,p,fid,frames,audio=False):
    f=tag(parent,'file',None,id=fid);tag(f,'name',p.name);tag(f,'pathurl',p.resolve().as_uri());tag(f,'duration',frames);rate(f)
    med=tag(f,'media');x=tag(med,'audio' if audio else 'video')
    if audio:
        tag(x,'channelcount',2);sc=tag(x,'samplecharacteristics');tag(sc,'depth',16);tag(sc,'samplerate',48000)
    else:
        sc=tag(x,'samplecharacteristics');tag(sc,'width',1920);tag(sc,'height',1080);tag(sc,'anamorphic','FALSE');tag(sc,'pixelaspectratio','square')

def make_xml(project,main,extra,music,out,include_audio=True):
    root=ET.Element('xmeml',version='4');seq=tag(root,'sequence',None,id='td-seq-15')
    tag(seq,'name',project+' - TD AI v1.5 REVIEW');rate(seq)
    frames=sum(max(1,round((c['end']-c['start'])*FPS)) for c in main)
    e_start=frames+FPS*8
    e_frames=sum(max(1,round((c['end']-c['start'])*FPS)) for c in extra)
    tag(seq,'duration',max(frames,e_start+e_frames));med=tag(seq,'media');vid=tag(med,'video')
    fmt=tag(vid,'format');sc=tag(fmt,'samplecharacteristics')
    tag(sc,'width',1920);tag(sc,'height',1080);tag(sc,'anamorphic','FALSE');tag(sc,'pixelaspectratio','square');tag(sc,'fielddominance','none');rate(sc)
    files={};rows=[]
    for group,clips,where in [('MAIN',main,0),('EXTRA',extra,e_start)]:
        track=tag(vid,'track');tag(track,'name','TD '+group)
        for i,c in enumerate(clips,1):
            frame_in=int(round(c['start']*FPS));frame_out=int(round(c['end']*FPS));n=max(1,frame_out-frame_in)
            ci=tag(track,'clipitem',None,id=f'td-{group.lower()}-{i}')
            tag(ci,'name',group+' - '+c['path'].name)
            for k,v in [('start',where),('end',where+n),('in',frame_in),('out',frame_out),('duration',n)]:tag(ci,k,v)
            if c['path'] not in files:
                fid='td-file-'+str(len(files)+1);files[c['path']]=fid
                source_file(ci,c['path'],fid,round(c['source_duration']*FPS))
            else:tag(ci,'file',None,id=files[c['path']])
            rows.append([group,i,str(c['path']),round(c['start'],2),round(c['end'],2),round(where/FPS,2),
                         round(c['score']*100,1),c['faces'],c.get('scene','unknown'),c.get('romance',0),
                         int(c.get('ai_checked',False)),c.get('note','')]);where+=n
    aud=tag(med,'audio')
    if include_audio and music:
        for ch in (1,2):
            track=tag(aud,'track');ci=tag(track,'clipitem',None,id='td-music-'+str(ch))
            tag(ci,'name',music.name);tag(ci,'start',0);tag(ci,'end',frames);tag(ci,'in',0);tag(ci,'out',frames);tag(ci,'duration',frames)
            st=tag(ci,'sourcetrack');tag(st,'mediatype','audio');tag(st,'trackindex',ch)
            if ch==1:source_file(ci,music,'td-music-source',frames,audio=True)
            else:tag(ci,'file',None,id='td-music-source')
    ET.indent(root,space='  ');ET.ElementTree(root).write(out,encoding='utf-8',xml_declaration=True)
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--base',required=True);ap.add_argument('--folder',required=True)
    ap.add_argument('--music',default='');ap.add_argument('--seconds',type=int,default=0)
    ap.add_argument('--quality',choices=['fast','standard','deep'],default='standard')
    ap.add_argument('--romantic-ai',action='store_true')
    a=ap.parse_args();base=Path(a.base).resolve();folder=Path(a.folder).resolve()
    if not folder.is_dir():raise RuntimeError('Source folder does not exist: '+str(folder))
    sources=discover_sources(folder)
    files=sources['files']
    print(f"SOURCE FOLDERS: {sources['folder_count']} · TOTAL VIDEOS: {len(files)}",flush=True)
    for entry in sources['folders']:
        print(f"  - {entry['name']}: {entry['count']} video(s)",flush=True)
    print('VIDEOS FOUND:',len(files),'SOURCE:',folder,flush=True)
    if not files:raise RuntimeError('No videos found inside this project folder')
    music=Path(a.music).resolve() if a.music else None
    if not music or not music.is_file():
        mdir=base/'02_MUSIC';ms=sorted(p for p in mdir.iterdir() if p.suffix.lower() in AUDIO_EXT) if mdir.is_dir() else []
        music=ms[0] if ms else None
    mlen=song_seconds(music)
    target=min(mlen,a.seconds) if a.seconds and mlen else (a.seconds or mlen or 180.0)
    if not 25<=target<=1200:raise RuntimeError('Song duration or requested length outside 25–1200 sec')
    print('MUSIC:',str(music) if music else 'None','DURATION:',round(mlen,2) if mlen else 'unknown',flush=True)
    print('TARGET:',round(target,2),'seconds',flush=True)
    suffix=hashlib.sha256(str(folder).lower().encode('utf-8')).hexdigest()[:7]
    slug=re.sub(r'[^\w .-]+','_',folder.name).strip()[:45] or 'Wedding'
    out=base/'06_OUTPUT'/'PREMIERE_AUTO_EDIT'/(slug+'_'+suffix)
    out.mkdir(parents=True,exist_ok=True)
    cv2.setNumThreads(1)
    max_workers=min(8,max(2,(os.cpu_count() or 4)//2))
    print('CPU WORKERS:',max_workers,'RTX GPU: Ollama only if model available',flush=True)
    cs=[]
    with ThreadPoolExecutor(max_workers=max_workers) as pool:
        for i,(p,found) in enumerate(zip(files,pool.map(lambda path:analyze(path,a.quality),files)),1):
            wait_for_job();cs.extend(found)
            print(f'ANALYZING {i}/{len(files)} {p.relative_to(folder)} | frames={len(found)}',flush=True)
    if not cs:raise RuntimeError('No decoded frames; unsupported source codecs?')
    print('TECHNICAL CANDIDATES:',len(cs),flush=True)
    ai_done=False
    if a.romantic_ai:
        ai_done=semantic_rerank(cs,out)
        if not ai_done:print('FALLBACK: Technical-only selection. This is NOT romantic AI.',flush=True)
    main_clips,extra=choose(cs,target,semantic=ai_done)
    if not main_clips:raise RuntimeError('No candidate clips for the timeline')
    xml=out/'TD_AI_v1_5_MAIN_AND_EXTRAS.xml'
    rows=make_xml(folder.name,main_clips,extra,music,xml,True)
    fallback=out/'TD_AI_v1_5_VIDEO_ONLY.xml'
    make_xml(folder.name,main_clips,extra,None,fallback,False)
    csv_path=out/'TD_AI_v1_5_SHOT_LIST.csv'
    with csv_path.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['Track','No','Source absolute path','IN sec','OUT sec','Timeline sec','Score','Face count','AI scene','AI romance','AI checked','AI note']);w.writerows(rows)
    info=out/'README_EDIT.txt'
    info.write_text(('TD Production AI Studio v1.5\n'
        f'Source: {folder}\nSong: {music if music else "NONE"}\n'
        f'Visual AI used: {ai_done}\nMain clips: {len(main_clips)}; Extra clips: {len(extra)}\n'
        'Video is a REVIEW rough cut; not an authored final film.\n'
        'Review and adjust every important scene. Extra shots begin 8 seconds after the song.\n'
        'The script does NOT automatically create transitions/speed changes, sync multicam, or control Premiere.\n'
        'If MP3 audio fails linking in Premiere: import the VIDEO_ONLY XML and manually add music.\n'
        'No originals changed. AI labels describe sampled frames only.\n'),encoding='utf-8')
    print('TD V1.5 FINISHED | AI:',ai_done,'MAIN:',len(main_clips),'EXTRAS:',len(extra),flush=True)
    print('EXPORT XML:',xml,flush=True);print('SHOT LIST:',csv_path,flush=True)
    return 0

if __name__=='__main__':
    try:sys.exit(main())
    except Exception as e:
        import traceback;traceback.print_exc();sys.exit(1)
