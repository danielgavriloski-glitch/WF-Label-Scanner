"""TD Wedding Reference v2: measurable learning from 1..100 exported Premiere XMLs.

Extracts *observed* edits and explicit XML transitions, not hallucinated AI motives.
No native .prproj parser; Premiere's FCP XML exporter is required.
"""
from __future__ import annotations
import datetime, hashlib, json, statistics, xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

SCHEMA=2
PHASES={'groom':('зет','groom','zet'),'bride':('невест','bride','nevesta'),
        'church':('црк','church','crkva'), 'reception':('ресторан','reception','restaurant','restoran')}

def phase_for(name):
    s=str(name).casefold()
    return next((k for k,words in PHASES.items() if any(w in s for w in words)), 'unknown')

def _rate(node, fallback=25.):
    try:
        n=float(node.findtext('./rate/timebase','') or fallback)
        return n if 1<=n<=240 else fallback
    except (ValueError,TypeError):return fallback

def inspect_xml(path):
    p=Path(path).expanduser().resolve()
    if p.suffix.lower()!='.xml' or not p.is_file():
        raise ValueError('Извези Final Cut Pro XML од Premiere (.prproj не се чита директно).')
    if p.stat().st_size>100*1024*1024:raise ValueError('XML е поголем од 100 MB.')
    root=ET.parse(p).getroot();sequences=root.findall('.//sequence')
    if not sequences:raise ValueError('Во XML нема секвенци.')
    result=[]
    for seq in sequences:
        name=seq.findtext('name','') or 'Untitled';fps=_rate(seq)
        phase=phase_for(name);clips=[];explicit=[];overlaps=[]
        tracks=seq.findall('./media/video/track')
        for index,track in enumerate(tracks):
            for item in track:
                if item.tag=='transitionitem':
                    try:
                        start=int(item.findtext('start','-1'));end=int(item.findtext('end','-1'))
                        if 0<=start<end:
                            ident=item.findtext('./effect/effectid','') or item.findtext('./effect/name','') or 'transition'
                            explicit.append({'duration':round((end-start)/fps,3),'kind':ident[:90],
                                             'track':index+1,'start':round(start/fps,3)})
                    except (ValueError,TypeError):pass
                if item.tag!='clipitem':continue
                try:
                    start=int(item.findtext('start','-1'));end=int(item.findtext('end','-1'))
                    source_in=int(item.findtext('in','0'));source_out=int(item.findtext('out','0'))
                except (TypeError,ValueError):continue
                if start<0 or end<=start:continue
                file_node=item.find('file');pathurl=file_node.findtext('pathurl','') if file_node is not None else ''
                clips.append({'start':round(start/fps,3),'end':round(end/fps,3),'duration':round((end-start)/fps,3),
                       'track':index+1,'source_in_frames':source_in,'source_out_frames':source_out,
                       'source_name':(file_node.findtext('name','') if file_node is not None else '') or item.findtext('name',''),
                       'source_url':pathurl[:1500], 'has_keyframes':bool(item.findall('.//keyframe'))})
        if not clips:continue
        clips.sort(key=lambda c:(c['start'],c['track']))
        for i,a in enumerate(clips):
            for b in clips[i+1:]:
                if b['start']>=a['end']:break
                if a['track']==b['track']:continue
                overlap=min(a['end'],b['end'])-max(a['start'],b['start'])
                if .08<=overlap<=3:overlaps.append(round(overlap,3))
        duration=round(max(c['end'] for c in clips),3)
        # Track changes are measurable; they do NOT prove manual camera preference.
        represented=len({c['source_url'] or c['source_name'] for c in clips if c['source_url'] or c['source_name']})
        result.append({'name':name,'phase':phase,'clip_count':len(clips),'duration':duration,
             'track_count':len(tracks),'unique_media':represented,
             'observed_explicit_transitions':explicit[:1000],
             'estimated_track_overlaps':overlaps[:1000],
             'keyframed_clip_count':sum(c['has_keyframes'] for c in clips),
             'total_cuts':len(clips)-1,
             'has_audio_tracks':len(seq.findall('./media/audio/track'))>0,
             'cuts_evidence':'timeline edits in exported FCP XML'})
    if not result:raise ValueError('XML нема употребливи video clipitem секвенци.')
    return {'file':str(p),'sha256':hashlib.sha256(p.read_bytes()).hexdigest(), 'sequences':result}

def _bounded_median(values, fallback=.45):
    xs=[v for v in values if .08<=v<=3.]
    return round(max(.15,min(1.5,statistics.median(xs) if xs else fallback)),3)

def learn_many(paths,name='TD Documentary Style'):
    if not isinstance(paths,list) or not 1<=len(paths)<=100:raise ValueError('Дозволени се 1 до 100 XML проекти.')
    unique=list(dict.fromkeys(str(Path(x).expanduser().resolve()) for x in paths))
    refs=[inspect_xml(x) for x in unique]
    seqs=[s for r in refs for s in r['sequences']]
    # Do not treat arbitrary overlaps as true dissolves; learn from explicit transitionitem
    # first, then opacity-keyframed track overlaps as estimated evidence, otherwise fallback.
    per_phase={};all_explicit=[];all_estimated=[]
    for phase in PHASES:
        group=[s for s in seqs if s['phase']==phase]
        explicit=[t['duration'] for s in group for t in s['observed_explicit_transitions']
                  if any(word in t['kind'].casefold() for word in ('dissolv','cross fade','crossfade'))]
        estimates=[x for s in group for x in s['estimated_track_overlaps'] if s['keyframed_clip_count']]
        all_explicit+=explicit;all_estimated+=estimates
        evidence='explicit_xml_transition' if explicit else ('keyframed_overlap_estimate' if estimates else 'default_not_learned')
        per_phase[phase]={'sequences':len(group),'sampled_clips':sum(s['clip_count'] for s in group),
            'explicit_transition_count':len(explicit),'estimated_overlap_count':len(estimates),
            'dissolve_seconds':_bounded_median(explicit or estimates), 'evidence':evidence}
    reliable=all_explicit or all_estimated
    # The single fallback is used when chapter-specific evidence is absent.
    base=_bounded_median(reliable)
    for item in per_phase.values():
        if item['evidence']=='default_not_learned':item['dissolve_seconds']=base
    return {'schema':SCHEMA,'name':str(name).strip()[:80] or 'TD Documentary Style',
        'created':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'references':refs,'reference_count':len(refs),'sequence_count':len(seqs),
        'phase_sequences':{k:per_phase[k]['sequences'] for k in PHASES},
        'phase_parameters':per_phase,'dissolve_seconds':base,
        'evidence_summary':{'explicit_transitions':len(all_explicit),'estimated_keyframed_overlaps':len(all_estimated),
              'cuts':sum(x['total_cuts'] for x in seqs),'unique_sequence_media':sum(x['unique_media'] for x in seqs),
              'all_explicit_transition_items':sum(len(x['observed_explicit_transitions']) for x in seqs),
              'not_learned':['semantic shot quality','why a camera angle was chosen','exact effects not present in XML']},
        'limitations':['Не се чита директно PRPROJ, потребно е XML извезен од Premiere',
             'Премините се научени само ако постојат во XML; инаку се проценуваат или користи default',
             'Изворните долгометражни снимки не смеат да се кратат',
             'Распоредот на камерите е набљудуван, но автоматски избор на агли не е имплементиран']}

def save(profile,folder):
    d=Path(folder);d.mkdir(parents=True,exist_ok=True)
    # The style stays addressable; re-learning same set updates its measurements.
    identity=hashlib.sha256(json.dumps([x['file'] for x in profile['references']],ensure_ascii=False).encode()).hexdigest()[:20]
    dest=d/(identity+'.json');tmp=d/(identity+'.tmp')
    tmp.write_text(json.dumps(profile,ensure_ascii=False,indent=2),encoding='utf-8');tmp.replace(dest)
    return identity

def load(folder,identity):
    if not isinstance(identity,str) or len(identity)!=20 or any(c not in '0123456789abcdef' for c in identity):
        raise ValueError('Невалиден ID за научен стил.')
    obj=json.loads((Path(folder)/(identity+'.json')).read_text(encoding='utf-8'))
    if obj.get('schema') not in (1,2):raise ValueError('Невалиден профил.')
    return obj

def profile_preview(profile):
    return {'name':profile.get('name'),'reference_count':profile.get('reference_count',0),
        'sequence_count':profile.get('sequence_count',0),
        'phase_parameters':profile.get('phase_parameters',{}),
        'evidence_summary':profile.get('evidence_summary',{}),
        'limitations':profile.get('limitations',[])}
