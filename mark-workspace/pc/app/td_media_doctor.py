"""TD AI Studio 2.6: source-media integrity diagnostics and optional safe transcodes.

Never writes into a source video. Sampling a file cannot prove every frame is good.
Uses locally available ffmpeg/ffprobe and produces an evidence report.
"""
from __future__ import annotations
from pathlib import Path
import json, os, subprocess, shutil, hashlib, time
from fractions import Fraction
import array, math

VIDEO_EXT={'.mp4','.mov','.avi','.m4v','.mkv','.mts','.m2ts'}

def _tools():
    import td_wedding_chapters as chapter
    exe=chapter.ffmpeg()
    ffprobe=shutil.which('ffprobe')
    if not ffprobe:
        sibling=Path(exe).with_name('ffprobe.exe' if os.name=='nt' else 'ffprobe')
        if sibling.is_file():ffprobe=str(sibling)
    if not ffprobe:raise RuntimeError('Недостига ffprobe. Инсталирај целосен FFmpeg пакет за проверка на видеата.')
    return exe,ffprobe

def _run(args, timeout):
    kwargs={'capture_output':True,'timeout':timeout}
    if os.name=='nt':kwargs['creationflags']=subprocess.CREATE_NO_WINDOW
    return subprocess.run(args,**kwargs)

def _fraction(v):
    try:return float(Fraction(v))
    except (ValueError,TypeError,ZeroDivisionError):return 0.

def diagnose(path, level='quick'):
    """quick: metadata + tail-video decode; deep: start/middle/end plus audio decode.
    Severity is advisory; passing the sample is NOT a full-file verification.
    """
    p=Path(path).expanduser().resolve()
    result={'path':str(p),'exists':p.is_file(),'level':level,'status':'error','issues':[],
            'warnings':[], 'samples':[], 'repair_recommended':False}
    if not p.is_file():result['issues'].append('Фајлот не постои – offline извор');return result
    if p.stat().st_size<1024:result['issues'].append('Премногу мал/празен фајл');return result
    if level not in ('quick','deep'):raise ValueError('Невалиден тип на проверка.')
    try:
        exe,ffprobe=_tools()
        r=_run([ffprobe,'-v','error','-show_entries','format=duration,format_name:stream=index,codec_type,codec_name,width,height,r_frame_rate,avg_frame_rate,channels,sample_rate,duration,pix_fmt,profile','-of','json',str(p)],40)
        if r.returncode:raise ValueError(r.stderr.decode('utf-8','replace')[-350:])
        data=json.loads(r.stdout.decode('utf-8','replace'))
        streams=data.get('streams',[])
        vids=[s for s in streams if s.get('codec_type')=='video']
        aud=[s for s in streams if s.get('codec_type')=='audio']
        if not vids:result['issues'].append('Нема видео stream');return result
        v=vids[0];d=float(data.get('format',{}).get('duration') or v.get('duration') or 0)
        if d<=0:result['issues'].append('Непознато/невалидно времетраење');return result
        raw=_fraction(v.get('r_frame_rate'));avg=_fraction(v.get('avg_frame_rate'))
        result.update(duration=round(d,3), size_bytes=p.stat().st_size, video_codec=v.get('codec_name'),
              audio_codec=aud[0].get('codec_name') if aud else None, channels=int(aud[0].get('channels',0)) if aud else 0,
              width=int(v.get('width',0)),height=int(v.get('height',0)), fps_avg=round(avg,4),fps_nominal=round(raw,4),
              audio_streams=[{'index':i,'codec':stream.get('codec_name'),'channels':stream.get('channels'),
                              'sample_rate':stream.get('sample_rate')} for i,stream in enumerate(aud)])
        if raw>0 and avg>0 and abs(raw-avg)/max(raw,avg)>.03:
            result['warnings'].append('Различни FPS податоци: можен VFR — ризик за Premiere XML')
        stream_d=float(v.get('duration') or 0)
        if stream_d and abs(d-stream_d)>max(.8,d*.01):
            result['warnings'].append('Контeјнерот и видео stream имаат различни времетраења – можна црна/празна втора половина')
        if v.get('pix_fmt') in ('yuv420p10le','yuv422p10le','yuv444p10le','gbrp10le'):
            result['warnings'].append('10-bit pixel format: провери Premiere video decoder')
        if v.get('codec_name') not in ('h264','prores','hevc','mpeg2video'):
            result['warnings'].append('Нестандарден видео кодек: '+str(v.get('codec_name')))
        if v.get('codec_name')=='hevc':result['warnings'].append('HEVC: провери поддршка и хардверско декодирање во Premiere')
        if len(aud)>1:result['warnings'].append('Повеќе аудио stream-ови; автоматскиот XML зема само еден')
        if not aud:result['warnings'].append('Нема изворен звук (можеби е нормално за дрон)')
        stamps=([.15, max(.15,d/2), max(.15,d-1.1)] if level=='deep' else [max(.15,d-1.1)])
        for sec in sorted(set(round(min(max(0.,d-.25),x),3) for x in stamps)):
            cmd=[exe,'-nostdin','-v','error','-ss',str(sec),'-i',str(p),'-frames:v','1',
                 '-map','0:v:0','-an','-vf','scale=160:-2','-f','image2pipe','-vcodec','mjpeg','pipe:1']
            rr=_run(cmd,45)
            ok=rr.returncode==0 and rr.stdout.startswith(b'\xff\xd8')
            result['samples'].append({'second':sec,'type':'video','ok':ok})
            if not ok:result['issues'].append(f'Видео не се декодира околу {sec:.2f}s: '+rr.stderr.decode('utf-8','replace')[-180:])
        if level=='deep' and aud:
            for sec in (max(0.,d/2),max(0.,d-1.1)):
                rr=_run([exe,'-nostdin','-v','error','-ss',str(round(sec,3)),'-i',str(p),'-t','0.6',
                         '-map','0:a:0','-vn','-ac','1','-ar','8000','-f','f32le','pipe:1'],45)
                ok=rr.returncode==0 and len(rr.stdout)>128
                result['samples'].append({'second':round(sec,3),'type':'audio','ok':ok})
                if not ok:result['issues'].append(f'Аудио не се декодира околу {sec:.2f}s')
                elif len(rr.stdout)>=512:
                    samples=array.array('f');size=len(rr.stdout)//4*4
                    samples.frombytes(rr.stdout[:size]);rms=math.sqrt(sum(float(x)*float(x) for x in samples)/len(samples))
                    db=round(20*math.log10(max(rms,1e-12)),1)
                    result['samples'][-1]['rms_dbfs']=db
                    if db < -60:
                        result['warnings'].append(f'Многу тивок/празен аудио сигнал кај {sec:.1f}s (stream 0, {db} dBFS)')
        result['repair_recommended']=bool(result['issues'] or any('VFR' in w or 'Нестандарден' in w or 'HEVC' in w for w in result['warnings']))
        result['status']='error' if result['issues'] else ('warning' if result['warnings'] else 'ok')
    except (OSError,ValueError,RuntimeError,subprocess.TimeoutExpired) as exc:
        result['issues'].append(type(exc).__name__+': '+str(exc)[:350])
    return result

def scan_folder(folder, level='quick', checkpoint=lambda:None, notify=lambda *x:None):
    root=Path(folder).expanduser().resolve()
    if not root.is_dir():raise ValueError('Папката не постои.')
    # Never scan TD_AI_EDIT outputs or cache as wedding sources.
    paths=sorted((p for p in root.rglob('*') if p.is_file() and p.suffix.lower() in VIDEO_EXT and
                  not any(x.upper() in ('TD_AI_EDIT','TD_AI_CACHE','PREMIERE_SAFE_MEDIA') for x in p.relative_to(root).parts)),key=lambda p:str(p).casefold())
    report=[]
    for i,p in enumerate(paths,1):
        checkpoint();report.append(diagnose(p,level))
        notify(i,len(paths),p.name,report[-1]['status'])
    return {'root':str(root),'level':level,'files':report,'total':len(paths),
            'ok':sum(x['status']=='ok' for x in report),'warning':sum(x['status']=='warning' for x in report),
            'error':sum(x['status']=='error' for x in report),
            'notice':'Примерочна проверка, не може да гарантира дека секој кадар е исправен.'}

def safe_copy(path, folder, progress=None, crf=18, audio_stream=0):
    """CFR H.264/AAC mezzanine copy next to output, not the original. No silent overwrite."""
    if not 14<=int(crf)<=23:raise ValueError('CRF мора да биде 14..23')
    if not isinstance(audio_stream,int) or not 0<=audio_stream<=7:
        raise ValueError('Невалиден аудио stream (0..7).')
    p=Path(path).expanduser().resolve(); dest=Path(folder).expanduser().resolve()
    if not p.is_file():raise ValueError('Изворот не постои')
    dest.mkdir(parents=True,exist_ok=True)
    # Ensure destination cannot accidentally point to the same file.
    name=p.stem+'__TD_SAFE_'+hashlib.sha256(str(p).encode()).hexdigest()[:10]+'.mp4'
    out=dest/name
    if out==p:raise ValueError('Излезот мора да биде различен од оригиналот.')
    if out.exists():raise FileExistsError('Компатибилната копија веќе постои: '+str(out))
    exe,_=_tools();tmp=dest/(out.stem+'.partial.mp4')
    source=diagnose(p,'quick')
    if 'duration' not in source:raise ValueError('Не можам да прочитам траење на изворниот фајл.')
    source_fps=source.get('fps_avg') or source.get('fps_nominal') or 25
    target_fps=min((24,25,30,50,60),key=lambda fps:abs(fps-source_fps))
    args=[exe,'-nostdin','-hide_banner','-y','-i',str(p),'-map_metadata','0','-map','0:v:0','-map',f'0:a:{audio_stream}?',
          '-c:v','libx264','-preset','medium','-crf',str(crf),'-pix_fmt','yuv420p',
          '-r',str(target_fps),'-fps_mode','cfr','-c:a','aac','-ar','48000','-ac','2',
          '-b:a','256k','-movflags','+faststart',str(tmp)]
    try:
        kwargs={'capture_output':True}
        if os.name=='nt':kwargs['creationflags']=subprocess.CREATE_NO_WINDOW
        r=subprocess.run(args,**kwargs)
        if r.returncode:raise RuntimeError('FFmpeg конверзијата не успеа: '+r.stderr.decode('utf-8','replace')[-450:])
        check=diagnose(tmp,'deep')
        if check['status']=='error':raise RuntimeError('Новата копија не ја помина проверката: '+str(check['issues'][:2]))
        if abs(check['duration']-source['duration'])>max(.8,source['duration']*.002):
            raise RuntimeError('Неочекувана разлика во траење на копијата и оригиналот')
        tmp.replace(out)
        check['path']=str(out)
        return {'original':str(p),'copy':str(out),'check':check}
    finally:
        if tmp.exists():tmp.unlink()
