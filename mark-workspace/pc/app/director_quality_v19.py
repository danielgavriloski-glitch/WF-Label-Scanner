"""TD AI Quality Director v1.9: non-destructive, music-aware, story-guided Premiere rough cut.
Media is read-only; writes distinct sequence, plans, optional slowed proxies.
Samples frames and may misclassify scenes; not full semantic coverage of every moment.
"""
from __future__ import annotations
import argparse, csv, hashlib, json, os, random, re, shutil, subprocess, sys, tempfile
from pathlib import Path
import numpy as np
import cv2
import engine as old
import td_story_quality as quality
from camera_sources import discover_sources
from guard import wait_for_job

FPS=25

def find_ffmpeg():
    cmd=shutil.which('ffmpeg')
    if cmd:return cmd
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:return None

def onset_times(music, seconds):
    """Compute local spectral-flux attack peaks. Fallback to evenly spaced cuts.
    Return detected positions and method, never claim beats if decoding fails.
    """
    exe=find_ffmpeg()
    if not music or not exe:return [],'NO_FFMPEG_OR_SONG'
    try:
        dur=min(float(seconds),600.)
        cmd=[exe,'-nostdin','-v','error','-i',str(music),'-t',str(dur),'-vn','-ac','1','-ar','11025','-f','f32le','pipe:1']
        p=subprocess.run(cmd,capture_output=True,timeout=100,check=True)
        audio=np.frombuffer(p.stdout,dtype='<f4')
        if audio.size<11025*5:return [],'TOO_SHORT'
        hop=256;win=1024
        x=np.asarray(audio[:len(audio)//hop*hop],dtype=np.float32)
        # Spectral flux with low memory: 1 second windows buffered, avoid huge FFT matrix.
        prev=np.zeros(win//2+1,dtype=np.float32);on=[]
        wnd=np.hanning(win).astype(np.float32)
        for i in range(0,len(x)-win,hop):
            spec=np.abs(np.fft.rfft(x[i:i+win]*wnd)).astype(np.float32)
            on.append(float(np.maximum(spec-prev,0).mean()))
            prev=spec
        energy=np.array(on,dtype=np.float32)
        # Robust normalization, adaptive threshold: emphasize strongest transient events.
        smooth=np.convolve(energy,np.ones(21)/21,mode='same')
        novelty=np.maximum(energy-smooth*1.18,0)
        floor=float(np.percentile(novelty,75))
        ids=[];min_gap=int(.29*11025/hop)
        for i in range(min_gap,len(novelty)-min_gap):
            if novelty[i] > max(0.012,floor*1.6) and novelty[i]>=np.max(novelty[i-min_gap:i+min_gap+1]):ids.append(i)
        beats=[round(i*hop/11025,3) for i in ids if .25<i*hop/11025<seconds-.3]
        return beats,'AUDIO_ONSET_FFT'
    except Exception as e:
        print('BEAT ANALYSIS WARNING:',repr(e)[:180],flush=True)
        return [],'AUDIO_DECODING_FAILED'

def duration_plan(target,onsets):
    """Musical phrasing: long romance, shorter accents. At climaxes: 3 rapid cuts."""
    rng=random.Random(1729)
    pos=0.;planned=[]
    while pos+0.5<target:
        sec=pos/max(target,1)
        if sec<.12:base=rng.choice([4.2,5.8,6.2])
        elif sec<.48:base=rng.choice([2.7,3.5,4.6,5.3])
        elif sec<.75:base=rng.choice([1.8,2.5,3.0,4.0])
        elif sec<.86:base=rng.choice([.82,1.05,1.38,2.0])
        else:base=rng.choice([2.6,3.7,4.8,5.6])
        goal=min(target,pos+base)
        # Closest real onset not too close to current playhead, not every cut is on a beat.
        if onsets:
            near=[b for b in onsets if .72 <= b-pos<=6.2 and abs(goal-b)<=.62]
            if near:goal=min(near,key=lambda b:abs(goal-b))
        span=round(goal-pos,3)
        if span<.6:span=.6
        if pos+span>target:span=target-pos
        if span<.50:break
        planned.append(span);pos+=span
    if planned and target-sum(planned)>.35:planned[-1]+=target-sum(planned)
    return planned

def category(c):
    if c.get('ai_checked') and c.get('scene')=='couple' and c.get('romance',0)>=.50:return 'romantic'
    sc=c.get('scene','other')
    if sc in ('ceremony','party','details','couple'):return sc
    if c.get('faces',0)>=2:return 'couple_unsure'
    return 'other'

def story_phase(t,target):
    p=t/max(target,1)
    if p<.13:return 'INTRO'
    if p<.49:return 'LOVE'
    if p<.71:return 'CEREMONY'
    if p<.85:return 'CELEBRATE'
    return 'FINALE'

def pick_for_story(candidates,durations,allow_ai):
    return quality.pick_clean_story(candidates,durations,allow_ai)


def render_slow(main,out,ffmpeg):
    """Render max 3 derived 50% speed video files (sources stay untouched).
    Include only if high frame rate and ffmpeg detected; avoid heavy transcode otherwise.
    """
    if not ffmpeg:return 0
    folder=out/'SLOW_MOTION_DERIVED';folder.mkdir(exist_ok=True)
    made=0
    for i,c in enumerate(main):
        if made>=3:break
        if c.get('story_phase') not in ('LOVE','FINALE'):continue
        if c['end']-c['start']<2:continue
        cap=cv2.VideoCapture(str(c['path']));source_fps=cap.get(cv2.CAP_PROP_FPS);cap.release()
        if source_fps<49:continue
        dur=c['end']-c['start'];source_start=max(0,c['start']);source_duration=dur/2
        name='slow_'+hashlib.sha256((str(c['path'])+str(source_start)).encode()).hexdigest()[:12]+'.mp4'
        path=folder/name
        if not path.is_file() or path.stat().st_size<1000:
            args=[ffmpeg,'-nostdin','-y','-hide_banner','-loglevel','error','-ss',str(round(source_start,3)),'-i',str(c['path']),'-t',str(round(source_duration,3)),'-an',
                  '-vf',f'setpts=2*(PTS-STARTPTS),fps={FPS},scale=1920:-2','-c:v','libx264','-preset','veryfast','-crf','21','-pix_fmt','yuv420p',str(path)]
            try:
                subprocess.run(args,timeout=180,check=True,capture_output=True)
            except Exception as ex:
                path.unlink(missing_ok=True)
                print('SLOW MOTION SKIPPED:',repr(ex)[:120],flush=True)
                continue
        if path.exists() and path.stat().st_size>1000:
            c['original_path']=c['path'];c['original_start']=c['start']
            c['path']=path;c['start']=0.;c['end']=dur;c['source_duration']=dur+.1
            c['speed_ratio']=.5
            c['note']=str(c.get('note',''))+' | 50% speed derived from 50+fps original'
            made+=1
            print('SLOW MOTION RENDERED',made,str(path.name),flush=True)
    return made

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--base',required=True);ap.add_argument('--folder',required=True)
    ap.add_argument('--music',default='');ap.add_argument('--seconds',type=int,default=0)
    ap.add_argument('--quality',choices=['fast','standard','deep'],default='standard')
    ap.add_argument('--romantic-ai',action='store_true')
    ap.add_argument('--render-slow',action='store_true')
    ap.add_argument('--style-profile',default='',help='Validated local learned style ID')
    a=ap.parse_args()
    for stream in (sys.stdout,sys.stderr):
        try: stream.reconfigure(encoding='utf-8',errors='replace')
        except Exception:pass
    folder=Path(a.folder).resolve();base=Path(a.base).resolve()
    if not folder.is_dir():raise RuntimeError('Missing source folder: '+str(folder))
    sources=discover_sources(folder)
    paths=sources['files']
    if not paths:raise RuntimeError('No videos in chosen folder or its camera subfolders')
    print('SOURCE FOLDERS:',sources['folder_count'],flush=True)
    for entry in sources['folders']:
        print(f"  - {entry['name']}: {entry['count']} video(s)",flush=True)
    song=Path(a.music).resolve() if a.music else None
    if a.music and not song.is_file():raise RuntimeError('Selected music is missing: '+str(song))
    if not song or not song.is_file():
        mdir=base/'02_MUSIC';s=sorted(p for p in mdir.iterdir() if p.suffix.lower() in old.AUDIO_EXT) if mdir.is_dir() else []
        song=s[0] if s else None
    songlen=old.song_seconds(song) if song else None
    target=min(songlen,a.seconds) if songlen and a.seconds else (songlen or a.seconds or 120.)
    if not (20<=target<=600):raise RuntimeError('Music length outside supported 20-600 s (or song not readable)')
    from td_style_learning import choose_profile, style_duration_plan
    style=choose_profile(Path(__file__).parent/'TD_LEARNED_STYLES',a.style_profile) if a.style_profile else None
    output_suffix='_STYLE_'+a.style_profile[:8] if style else ''
    out=base/'06_OUTPUT'/'PREMIERE_AUTO_EDIT'/((re.sub(r'[^\w .-]','_',folder.name)[:40] or 'Wedding')+'_QUALITY_v1_9'+output_suffix)
    out.mkdir(parents=True,exist_ok=True)
    print('VIDEO SOURCES:',len(paths),'MUSIC:',str(song),'TARGET:',round(target,2),flush=True)
    print('PHASE 1 — ANALYZE ALL VIDEOS',flush=True)
    # cap CPUs; Ollama uses GPU; resist decoding > available RAM
    from concurrent.futures import ThreadPoolExecutor
    workers=min(8,max(2,(os.cpu_count() or 4)//2))
    cv2.setNumThreads(1)
    candidates=[]
    with ThreadPoolExecutor(max_workers=workers) as pool:
        for i,(p,part) in enumerate(zip(paths,pool.map(lambda x:old.analyze(x,'deep' if a.romantic_ai else a.quality),paths)),1):
            wait_for_job();candidates.extend(part)
            print(f'ANALYZING {i}/{len(paths)} {p.relative_to(folder)} candidates={len(part)}',flush=True)
    if not candidates:raise RuntimeError('No readable frames found')
    ai=False
    if a.romantic_ai:
        print('PHASE 2 — OLLAMA FRAME REVIEW (LOCAL)',flush=True)
        ai=quality.semantic_rerank_v19(candidates,out,per_video=8)
        if not ai:raise RuntimeError('Romantic vision unavailable; not creating an unverified montage')
    if not ai:raise RuntimeError('Enable Romantic AI for quality-first montage; refusal to guess from face count')
    wait_for_job()
    print('PHASE 3 — LISTEN TO SONG',flush=True)
    beats,method=onset_times(song,target)
    print('MUSIC ONSETS:',len(beats),'METHOD:',method,flush=True)
    durations=style_duration_plan(target,beats,style) if style else duration_plan(target,beats)
    if style:print('LEARNED STYLE APPLIED:',style['name'],'|',style['basis'],'|',style['id'],flush=True)
    print('VARIED CUT DURATIONS:',len(durations),[round(x,1) for x in durations[:22]],flush=True)
    if style and style.get('vision',{}).get('status')=='ok':
        mood=style['vision'].get('mood','').lower()
        print('LEARNED REFERENCE MOOD:',mood,flush=True)
        for item in candidates:
            if not item.get('quality_ok'):continue
            if mood=='romantic' and item.get('couple_primary'):
                item['score']+=.12*item.get('romance',0.)
            elif mood=='dynamic' and item.get('scene')=='party':
                item['score']+=.10
    main=pick_for_story(candidates,durations,ai)
    if not main:raise RuntimeError('No AI-approved story segments found. Do not fill with bad material.')
    # Second independent visual check of actual IN/OUT; try clean replacements if needed.
    print('PHASE 3B — FINAL CONTINUITY / CREW CHECK',flush=True)
    main=quality.review_final(main,candidates,out)
    wait_for_job()
    print('PHASE 4 — PREPARE OPTIONAL SLOW MOTION',flush=True)
    nslow=render_slow(main,out,find_ffmpeg()) if a.render_slow else 0
    # Extras: additional good shots, try to include more sources than main.
    used={str(c.get('original_path',c['path'])) for c in main}
    ranked=sorted((c for c in candidates if quality.quality_category(c) != 'reject'),
                  key=lambda c:c['score']+(.27*c.get('romance',0)),reverse=True)
    extra=[];taken=[]
    for c in ranked:
        if len(extra)>=min(120,len(paths)*2):break
        if str(c['path']) in used and len(extra)<len(paths)//2:continue
        if any(str(e['path'])==str(c['path']) and abs(e['start']-c['start'])<10 for e in extra):continue
        extra.append(c)
    xml=out/'TD_AI_QUALITY_v1_9_MAIN_AND_EXTRAS.xml'
    rows=old.make_xml(folder.name+' QUALITY DIRECTOR v1.9',main,extra,song,xml,True)
    old.make_xml(folder.name+' QUALITY DIRECTOR v1.9',main,extra,None,out/'TD_AI_QUALITY_v1_9_VIDEO_ONLY.xml',False)
    with (out/'TD_AI_QUALITY_v1_9_SHOT_LIST.csv').open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['Track','No','Source','IN sec','OUT sec','Timeline sec','Score','Faces','AI scene','Romantic score','AI validated','Notes']);w.writerows(rows)
    with (out/'TD_AI_QUALITY_v1_9_STORY_PLAN.csv').open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.writer(f);w.writerow(['Shot','Story phase','Timeline sec','Duration sec','Original path','Source second','Speed','Detected music onsets'])
        pos=0
        for i,c in enumerate(main,1):
            w.writerow([i,c['story_phase'],round(pos,2),round(c['end']-c['start'],2),str(c.get('original_path',c['path'])),round(c.get('original_start',c['start']),2),c['speed_ratio'],method]);pos+=c['end']-c['start']
    (out/'EDIT_NOTES.txt').write_text(f'TD AI Quality Director v1.9 — experimental wedding rough cut\n'
         f'Source: {folder}\nSong: {song}\nCut plan: {len(main)} clips, up to {target:.1f} seconds; extras={len(extra)}\n'
         f'Music detector: {method} / {len(beats)} transients; may not equal musical downbeats.\n'
         f'Visual AI used: {ai}; AI scans sampled frames, not every frame.\n'
         f'50% slow clips rendered: {nslow} (original media never altered).\n'
         'Transitions: NOT applied automatically yet; needs authenticated UXP integration or validated XML.\n'
         'Premiere XML import can require relinking; verify all video/audio before playback.\n'
         'This is not a finished film. Review your music sync, emotional sequencing and scene correctness.\n',encoding='utf-8')
    print('QUALITY DIRECTOR FINISHED | MAIN:',len(main),'EXTRAS:',len(extra),'SLOW:',nslow,'VISION:',ai,flush=True)
    print('EXPORT XML:',xml,flush=True)
    return 0

if __name__=='__main__':
    try:sys.exit(main())
    except Exception:
        import traceback;traceback.print_exc();sys.exit(1)

# TD_STYLE_LEARNING_V2_0
