"""MarK Smart Wedding Director 2.0 - music-aware planning and safe segment trimming.

Read-only media. Local spectral/onset analysis; only already MarK-Vision-verified
candidates are accepted. The final IN/OUT is re-checked by Native Vision v3.
This is a rough-cut planner, not a guarantee of artistic or identity accuracy.
"""
from __future__ import annotations
import collections, json, math, statistics, subprocess
from pathlib import Path
import numpy as np
import cv2

FPS=25
PHASES=(('INTRO',.13),('LOVE',.49),('CEREMONY',.71),('CELEBRATE',.85),('FINALE',1.01))
PREFERENCES={
 'INTRO': ('details','romantic','couple','ceremony'),
 'LOVE': ('romantic','couple','details'),
 'CEREMONY': ('ceremony','romantic','couple','details'),
 'CELEBRATE': ('party','ceremony','romantic','couple'),
 'FINALE': ('romantic','couple','details','ceremony'),
}
DEFAULT={'style':'romantic','emotion_priority':75,'cut_dynamics':40,'max_sources_uses':2,'max_source_seconds_for_spot':300}

def settings(path):
    """Fail safe for corrupt configs; clamp numeric UI-equivalent controls."""
    config=DEFAULT.copy()
    try:
        source=json.loads(Path(path).read_text(encoding='utf-8-sig'))
        if isinstance(source,dict):
            for key in DEFAULT:
                if key in source:config[key]=source[key]
    except (OSError,ValueError):pass
    if config['style'] not in ('romantic','classic','dynamic'):
        config['style']='romantic'
    for key in ('emotion_priority','cut_dynamics'):
        try:config[key]=max(0,min(100,int(config[key])))
        except (ValueError,TypeError):config[key]=DEFAULT[key]
    try:config['max_sources_uses']=max(1,min(3,int(config['max_sources_uses'])))
    except (ValueError,TypeError):config['max_sources_uses']=2
    # SPOT SAFETY CONTRACT: no clip strictly longer than five minutes as spot source.
    config['max_source_seconds_for_spot']=300
    return config

def phase_at(pos,total):
    frac=pos/max(total,1.0)
    return next(name for name,limit in PHASES if frac<limit)

def _ticks(t):return int(round(float(t)*FPS))

def track_energy(song,seconds,ffmpeg):
    """Decode track RMS every 0.5s; results in [0,1] for relative musical energy.
    Returns no inferred beat/bar facts. Empty map when audio decode fails.
    """
    if not song or not ffmpeg:return []
    try:
        cmd=[ffmpeg,'-nostdin','-v','error','-i',str(song),'-t',str(min(float(seconds),600)),
             '-vn','-ac','1','-ar','4000','-f','f32le','pipe:1']
        out=subprocess.run(cmd,capture_output=True,timeout=120,check=True).stdout
        audio=np.frombuffer(out,dtype='<f4')
        if audio.size<4000*3:return []
        count=audio.size//2000
        chunks=audio[:count*2000].reshape(count,2000)
        rms=np.sqrt(np.mean(chunks.astype(np.float64)**2,axis=1))
        if not np.all(np.isfinite(rms)):return []
        # Convert loudness to relative values; the model sees no song waveform.
        lo,hi=np.percentile(rms,[15,90])
        if hi-lo<1.e-6:return [.5]*len(rms)
        return np.clip((rms-lo)/(hi-lo),0,1).round(3).tolist()
    except (OSError,subprocess.SubprocessError,ValueError) as exc:
        print('MUSIC ENERGY WARNING:',type(exc).__name__,flush=True)
        return []


def music_cut_plan(target,onsets,config,energy_map=None):
    """Deterministic phrase/accent aware cut durations at 25 fps.

    Audio onsets ARE NOT musical downbeats. Do not assert exact BPM/phrases.
    Preference biases cuts toward real spectral peaks while preserving long romance.
    """
    end=max(FPS*2,_ticks(target));valid=sorted(set(_ticks(x) for x in onsets if 0<float(x)<target))
    # Infer only a coarse pulse from repeated onset spacing, not from absolute energy.
    diffs=[b-a for a,b in zip(valid,valid[1:]) if int(.30*FPS)<=b-a<=int(1.1*FPS)]
    pulse=int(round(statistics.median(diffs))) if len(diffs)>=9 else None
    if pulse and not (8<=pulse<=28):pulse=None
    # Accents plausibly at 4/8 pulse intervals, used as weak preferences.
    phrases=set()
    if pulse and len(valid)>12:
        anchor=valid[0]
        for t in range(anchor+8*pulse,end,8*pulse):
            if valid:
                nearest=min(valid,key=lambda x:abs(x-t))
                if abs(nearest-t)<=max(2,int(pulse*.35)):phrases.add(nearest)
    pos=0;cuts=[];steps=0
    energy_factor=config['cut_dynamics']/100
    preset=config['style']
    energy_map=energy_map or []
    while end-pos>=int(.55*FPS) and steps<250:
        steps+=1
        phase=phase_at(pos/FPS,end/FPS)
        min_sec,max_sec,ideal={
          'INTRO':(2.8,6.3,4.9), 'LOVE':(2.6,6.0,4.25),
          'CEREMONY':(2.0,4.7,3.25),'CELEBRATE':(1.3,4.0,2.6),
          'FINALE':(2.8,6.5,4.8)
        }[phase]
        if preset=='dynamic':ideal*=.82
        elif preset=='classic':ideal*=1.12
        local_energy=energy_map[min(len(energy_map)-1,int(pos/FPS*2))] if energy_map else .5
        ideal*=1.13-.40*energy_factor
        # Shift toward shorter cuts in loud/energetic passages, longer cuts in quiet passages.
        ideal*=1.12-.24*float(local_energy)*energy_factor
        min_ticks=max(18,_ticks(min_sec*(1.12-.20*energy_factor)))
        max_ticks=max(min_ticks+4,_ticks(max_sec*(1.08-.16*energy_factor)))
        ideal_ticks=max(min_ticks,min(max_ticks,_ticks(ideal)))
        remaining=end-pos
        if remaining<=max_ticks+min_ticks//2:goal=end
        else:
            lower=pos+min_ticks;upper=min(end-min_ticks,pos+max_ticks)
            candidate=[t for t in valid if lower<=t<=upper]
            if candidate:
                def score(t):
                    # A large timing displacement should not win only due to a transient.
                    return abs(t-(pos+ideal_ticks)) / max(ideal_ticks,1) -(.17 if t in phrases else 0)
                goal=min(candidate,key=score)
            else:goal=pos+ideal_ticks
        goal=min(end,max(pos+min_ticks,goal))
        if end-goal<min_ticks//2:goal=end
        if goal<=pos:break
        cuts.append(goal-pos);pos=goal
    if pos<end:
        if cuts:cuts[-1]+=end-pos
        else:cuts=[end]
    # Exact 25fps sum: no cumulative floating point drift.
    assert sum(cuts)==end
    info={'onsets':len(valid),'energy_windows':len(energy_map),'phrase_candidates':len(phrases),'pulse_estimate_sec':round(pulse/FPS,3) if pulse else None,
          'method':('spectral-onset-biased (not verified musical downbeats)' if valid else 'fallback pacing: no valid music onsets'),'cuts':len(cuts)}
    return [frames/FPS for frames in cuts],info

def _kind(c):
    if not c.get('ai_checked') or not c.get('quality_ok'):return 'reject'
    scene=c.get('scene','other')
    if scene=='couple' and c.get('couple_primary') and c.get('romance',0)>=.55:return 'romantic'
    if scene in ('couple','ceremony','party','details'):return scene
    return 'other'

def choose_story(candidates,durations,config):
    """Select quality-verified shots with story, diversity and source-length guard."""
    usable=[c for c in candidates if _kind(c)!='reject' and 0<float(c.get('source_duration',float('inf')))<=300]
    if not usable:raise RuntimeError('No AI-verified short-source shots (<= 300 seconds) for spot')
    selected=[];last=[];counts=collections.Counter();chosen_centers=collections.defaultdict(list)
    elapsed=0.0;total=sum(durations)
    weight=config['emotion_priority']/100.
    for dur in durations:
        phase=phase_at(elapsed,total);elapsed+=dur
        choices=[]
        for c in usable:
            kind=_kind(c)
            if kind not in PREFERENCES[phase]:continue
            source=c['path'];source_id=str(source).casefold()
            if float(c['source_duration'])<dur+.35:continue
            if counts[source_id]>=config['max_sources_uses']:continue
            at=float(c.get('timestamp',0))
            if any(abs(at-prev)<max(dur*2,8) for prev in chosen_centers[source_id]):continue
            rank=len(PREFERENCES[phase])-PREFERENCES[phase].index(kind)
            romantic=float(c.get('romance',0))*int(bool(c.get('couple_primary')))
            q=float(c.get('visual_quality',0));tech=float(c.get('tech',0))
            recent_cost=sum(.62/(1+i) for i,x in enumerate(reversed(last[-3:])) if x==source_id)
            cat_cost=.25 if selected and _kind(selected[-1])==kind else 0
            source_cost=.47*counts[source_id]
            score=(.65*q+.28*tech+(0.26+.57*weight)*romantic+
                   .15*rank-source_cost-recent_cost-cat_cost)
            # Do not use the notes to identify persons.
            choices.append((score,-counts[source_id],source_id,at,c))
        if not choices:
            # Never fabricate a shot / fill a gap with unverified frames.
            print('DIRECTOR v2 STORY GAP:',phase,'-- no verified short clip for this slot',flush=True)
            continue
        c=max(choices,key=lambda x:(x[0],x[1],x[2],x[3]))[-1]
        src_id=str(c['path']).casefold();center=float(c.get('timestamp',0))
        begin=max(0.,min(float(c['source_duration'])-dur-.08,center-dur/2))
        chosen={**c,'start':begin,'end':begin+dur,'story_phase':phase,'speed_ratio':1.,
                'cut_onset':True,'director_v20':True,'moment_original':center}
        selected.append(chosen);counts[src_id]+=1;chosen_centers[src_id].append(center);last.append(src_id)
    return selected

def _sample_score(path,start,duration):
    """Technical edge screening, never replaces semantic Native Vision confirmation."""
    cap=cv2.VideoCapture(str(path))
    if not cap.isOpened():return -1000.
    try:
        mats=[];sharp=[];brightness=[]
        for sec in (start+.12,start+duration*.5,start+max(.13,duration-.12)):
            cap.set(cv2.CAP_PROP_POS_MSEC,max(sec,0)*1000)
            ok,frame=cap.read()
            if not ok:return -1000.
            h,w=frame.shape[:2]
            if h<2 or w<2:return -1000.
            # Cheap deterministic statistics; no additional AI calls.
            small=cv2.resize(frame,(160,90));gray=cv2.cvtColor(small,cv2.COLOR_BGR2GRAY)
            mats.append(gray);sharp.append(float(cv2.Laplacian(gray,cv2.CV_64F).var()))
            brightness.append(float(gray.mean()))
        jumps=[float(np.mean(cv2.absdiff(mats[i+1],mats[i]))) for i in range(2)]
        # Extremely dark/bright and extremely blurry edges are discouraged.
        severe=sum(b<22 or b>234 for b in brightness)
        lowsharp=sum(s<9 for s in sharp)
        # Huge content change may be normal camera motion, so only a mild penalty.
        jerk=max(jumps)
        return min(statistics.median(sharp),130)/260 - severe*.38-lowsharp*.20-max(0,jerk-68)*.004
    finally:cap.release()

def refine_moments(main,config):
    """Refine cut windows around AI-approved moments without extra model invocations.
    Final actual IN/OUT MUST still pass quality.review_final Native Vision v3.
    """
    refined=[]
    for item in main:
        dur=float(item['end']-item['start']);n=float(item['source_duration'])
        center=float(item.get('moment_original',item.get('timestamp',(item['start']+item['end'])/2)))
        if n<dur+.3:refined.append(item);continue
        original_start=item['start']
        options=[]
        for offset in (0.,-1.0,1.0,-2.0,2.0):
            begin=max(0.,min(n-dur-.1,center+offset-dur/2))
            if any(abs(begin-b)<.25 for _,b in options):continue
            options.append((0,begin))
        scored=[]
        for _,start in options:
            score=_sample_score(item['path'],start,dur)
            # Keep center close to the semantic moment identified by Native Vision.
            score-=abs((start+dur/2)-center)*.10
            scored.append((score,-abs(start-item['start']),start))
        if scored:
            score,_,best=max(scored)
            # If frame decoding fails do NOT override known original window.
            if score>-100:
                item={**item,'start':best,'end':best+dur,'moment_refined':True,
                      'moment_refine_delta_sec':round(best-original_start,2)}
        refined.append(item)
    return refined

def summarize(main):
    return {'cuts':len(main),'sources':len({str(c['path']) for c in main}),
            'seconds':round(sum(c['end']-c['start'] for c in main),2),
            'phases':dict(collections.Counter(c.get('story_phase','unknown') for c in main))}
