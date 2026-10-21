"""Additive v2.1 routes; keep the existing Studio, PIN and Premiere bridge."""
from __future__ import annotations
import collections, copy, json, secrets, threading, time, hashlib, os
from pathlib import Path
import xml.etree.ElementTree as ET
from flask import jsonify, request
from td_wedding_chapters import AUDIO_EXT, build_wedding, choose_music, probe, safe_name

def install_studio_panel(g):
    app=g['app'];base=g['BASE'];cfg=g['CFG'];job=g['job'];lock=g['lock']
    catalog={'time':0,'files':[],'project':''};progress_peak={'value':0}
    diagnostic={'state':'idle','progress':0,'message':'','report':None,'error':''}
    repair={'state':'idle','progress':0,'message':'','result':None,'error':''}
    teacher_file=Path(base)/'TD_WEDDING_HINTS.json'
    replacements_file=Path(base)/'TD_WEDDING_REPAIRS.json'
    from td_wedding_chapters import folder_phase, atomic_json
    from td_media_doctor import diagnose, scan_folder, safe_copy

    def json_file(p):
        try:
            data=json.loads(Path(p).read_text(encoding='utf-8'))
            return data if isinstance(data,dict) else {}
        except (OSError,ValueError,json.JSONDecodeError):return {}

    def hint_for_path(p,root,hints):
        if folder_phase(p,root)!='unknown':return None
        try:parts={part.casefold() for part in Path(p).resolve().parent.relative_to(root).parts}
        except ValueError:return None
        matches={phase for name,phase in hints.items() if name in parts}
        return next(iter(matches)) if len(matches)==1 else None

    original_progress=g['parse_progress']
    def progress(lines):
        if job.get('stage')=='wedding':return int(job.get('wedding_progress',0))
        return original_progress(lines)
    g['parse_progress']=progress

    def wedding_sources(folder):
        """Never treat Studio's own edit/export/cache/repair copies as new raw footage."""
        from camera_sources import discover_sources
        root=Path(folder).resolve();result=[]
        for candidate in discover_sources(root)['files']:
            p=Path(candidate).resolve()
            if not p.is_file():continue
            try:parts=p.relative_to(root).parts
            except ValueError:continue
            if any(part.upper() in ('TD_AI_EDIT','TD_AI_CACHE','PREMIERE_SAFE_MEDIA',
                                    'TD_BACKUPS','TD_MEDIA_REPAIRS') for part in parts):continue
            result.append(str(p))
        return list(dict.fromkeys(result))

    def available_music(folder='',manual=''):
        if manual:
            p=Path(manual).expanduser()
            if not p.is_absolute() or not p.is_file() or p.suffix.lower() not in AUDIO_EXT:raise ValueError('Избраната песна не постои или не е поддржана.')
            return [str(p.resolve())],'manual'
        # Explicitly chosen folder ALWAYS takes precedence over short-lived Premiere catalog.
        if folder:
            root=Path(folder).expanduser()
            if not root.is_absolute() or not root.is_dir():
                raise ValueError('Избраната музичка папка не постои: '+str(root))
            files=sorted((str(p.resolve()) for p in root.rglob('*')
                          if p.is_file() and p.suffix.lower() in AUDIO_EXT),key=str.casefold)
            return files,'chosen-folder'
        fresh=time.time()-catalog['time']<70
        files=[p for p in catalog['files'] if Path(p).is_file()] if fresh else []
        if files:return files,'premiere'
        root=Path(g['ASSET_BASE'])/'02_MUSIC'
        files=sorted((str(p.resolve()) for p in root.rglob('*')
                      if p.is_file() and p.suffix.lower() in AUDIO_EXT),key=str.casefold) if root.is_dir() else []
        return files,'default-folder'

    # v2.4: additive local reference-learning API, does not touch legacy learner.
    from td_wedding_reference import learn_many as learn_documentary, save as save_documentary, load as load_documentary
    profile_dir=Path(base)/'TD_WEDDING_STYLES'
    # Dedicated documentary learning job; never impersonate the old short-spot learner.
    learn_lock=threading.RLock()
    learn_job={'state':'idle','progress':0,'current':0,'total':0,'message':'Подготвено за Premiere XML',
               'log':[],'error':'','id':'','name':'','started':0,'finished':0,'result':None}
    def learning_status():
        with learn_lock:return copy.deepcopy(learn_job)
    def learning_event(index,total,message):
        with learn_lock:
            learn_job.update(current=index,total=total,message=message,progress=min(93,round(93*index/max(1,total))))
            learn_job['log'].append(f'{index}/{total} · {message}')
            learn_job['log']=learn_job['log'][-60:]

    from td_wedding_reference import profile_preview
    @app.route('/api/wedding/styles',methods=['GET'])
    def wedding_styles():
        gate=g['api_guard']()
        if gate:return gate
        result=[]
        for f in sorted(profile_dir.glob('*.json'),key=lambda x:x.stat().st_mtime,reverse=True):
            try:
                obj=load_documentary(profile_dir,f.stem)
                result.append({'id':f.stem,'name':obj['name'],'references':obj['reference_count'],
                               'dissolve_seconds':obj['dissolve_seconds'],
                               'evidence':obj.get('evidence_summary',{}),
                               'phase_parameters':obj.get('phase_parameters',{})})
            except (OSError,ValueError,KeyError,json.JSONDecodeError):continue
        return jsonify(styles=result)

    @app.route('/api/wedding/style/<identity>',methods=['GET'])
    def wedding_style_detail(identity):
        gate=g['api_guard']()
        if gate:return gate
        try:return jsonify(profile=profile_preview(load_documentary(profile_dir,identity)))
        except (OSError,ValueError,KeyError,json.JSONDecodeError) as exc:return jsonify(error=str(exc)),400

    @app.route('/api/wedding/learn/status',methods=['GET'])
    def wedding_learn_status():
        gate=g['api_guard']()
        if gate:return gate
        return jsonify(learning_status())

    @app.route('/api/wedding/learn',methods=['POST'])
    def wedding_learn():
        gate=g['api_guard']()
        if gate:return gate
        data=request.get_json(silent=True) or {}
        try:
            paths=data.get('files',[])
            if not isinstance(paths,list) or not 1<=len(paths)<=100:
                raise ValueError('Внеси 1–100 Premiere XML патеки, по една во секој ред.')
            cleaned=[]
            for raw in paths:
                if not isinstance(raw,str):raise ValueError('Невалидна XML патека.')
                path=Path(raw.strip().strip('"')).expanduser()
                if path.suffix.casefold()!='.xml':raise ValueError('Потребен е Premiere Final Cut Pro XML, не директен .prproj: '+path.name)
                if not path.is_absolute() or not path.is_file():raise ValueError('XML фајлот не е пронајден: '+str(path))
                cleaned.append(str(path.resolve()))
            append=str(data.get('append_to','')).strip()
            if append:
                old=load_documentary(profile_dir,append)
                cleaned=[r['file'] for r in old['references']]+cleaned
                name=old['name']
            else:name=str(data.get('name','TD Documentary Style'))[:80]
            paths=list(dict.fromkeys(cleaned))
            if len(paths)>100:raise ValueError('Најмногу 100 уникатни Premiere XML свадби.')
        except (ValueError,OSError,KeyError,json.JSONDecodeError) as exc:return jsonify(error=str(exc)),400
        with learn_lock:
            if learn_job['state']=='running':return jsonify(error='Учењето веќе работи. Следи го напредокот под копчето.'),409
            learn_job.update(state='running',progress=0,current=0,total=len(paths),
                 message='Го подготвувам учењето',log=['Старт · '+str(len(paths))+' XML датотеки'],
                 error='',id='',name=name,started=time.time(),finished=0,result=None)
        def learn_worker():
            try:
                obj=learn_documentary(paths,name,progress=learning_event)
                with learn_lock:
                    learn_job.update(progress=96,message='Го зачувувам профилот локално')
                    learn_job['log'].append('Измерени '+str(obj['sequence_count'])+' Premiere секвенци')
                ident=save_documentary(obj,profile_dir)
                result={'id':ident,'name':obj['name'],'references':obj['reference_count'],
                    'sequences':obj['sequence_count'],'phase_parameters':obj.get('phase_parameters',{}),
                    'evidence_summary':obj.get('evidence_summary',{}),'limitations':obj['limitations']}
                with learn_lock:
                    learn_job.update(state='finished',progress=100,id=ident,name=obj['name'],
                        message='Научениот документарен профил е зачуван',finished=time.time(),result=result)
                    learn_job['log'].append('ЗАВРШЕНО · '+str(obj['reference_count'])+' свадби · ID '+ident)
            except Exception as exc:
                with learn_lock:
                    learn_job.update(state='failed',message='Учењето не успеа',error=str(exc)[:500],finished=time.time())
                    learn_job['log'].append('ГРЕШКА · '+str(exc)[:500])
        threading.Thread(target=learn_worker,daemon=True,name='td-wedding-learn-v27').start()
        return jsonify(ok=True,reply='Учењето започна. Следи го напредокот во истиот панел.',total=len(paths)),202

    @app.route('/api/wedding/teach',methods=['GET','POST'])
    def wedding_teach():
        gate=g['api_guard']()
        if gate:return gate
        hints=json_file(teacher_file)
        if request.method=='GET':return jsonify(rules=hints)
        data=request.get_json(silent=True) or {}
        try:
            key=str(data.get('folder_name','')).strip().casefold()
            phase=str(data.get('phase','')).strip()
            if len(key)<2 or len(key)>100 or any(c in key for c in ('/', chr(92), ':', '*', '?', '<', '>', '|')):
                raise ValueError('Внеси само име на папка, без целосна патека.')
            if key in ('cam1','cam2','camera','cam','video','videos','01','02'):
                raise ValueError('Името е премногу општо. Користи конкретна папка: zet, nevesta, crkva, restoran.')
            if phase=='delete':hints.pop(key,None)
            elif phase in ('groom','bride','church','reception'):hints[key]=phase
            else:raise ValueError('Невалидно поглавје.')
            atomic_json(teacher_file,hints)
            return jsonify(ok=True,rules=hints)
        except (ValueError,OSError) as exc:return jsonify(error=str(exc)),400

    @app.route('/api/wedding/status',methods=['GET'])
    def wedding_job_status():
        gate=g['api_guard']()
        if gate:return gate
        with lock:
            return jsonify(state=job.get('state','idle'),stage=job.get('stage',''),
                    progress=job.get('wedding_progress',0),project=job.get('project',''),
                    project_status=job.get('project_status',''),xml=job.get('export_xml',''),
                    project_file=job.get('project_file',''),error=job.get('wedding_error',''),
                    summary=job.get('wedding_summary',{}),log=list(job.get('log',[]))[-45:])

    @app.route('/api/wedding/scan',methods=['POST'])
    def wedding_scan_start():
        gate=g['api_guard']()
        if gate:return gate
        data=request.get_json(silent=True) or {}
        path=Path(str(data.get('folder',''))).expanduser()
        level=data.get('level','quick')
        if not path.is_absolute() or not path.is_dir() or level not in ('quick','deep'):
            return jsonify(error='Избери постоечка папка и Quick или Deep проверка.'),400
        with lock:
            if diagnostic['state']=='running':return jsonify(error='Веќе проверувам видеа.'),409
            diagnostic.update(state='running',progress=0,message='Започнува проверката',report=None,error='')
        def scan_worker():
            try:
                def tick(i,total,name,status):
                    with lock:diagnostic.update(progress=round(100*i/max(1,total)),message=f'{i}/{total} · {name} · {status}')
                report=scan_folder(path,level,notify=tick)
                diagdir=Path(base)/'TD_DIAGNOSTICS';diagdir.mkdir(parents=True,exist_ok=True)
                reportfile=diagdir/(hashlib.sha256(str(path.resolve()).encode()).hexdigest()[:16]+'.json')
                atomic_json(reportfile,report)
                report['report_path']=str(reportfile)
                with lock:diagnostic.update(state='finished',progress=100,message='Проверката заврши',report=report,error='')
            except Exception as exc:
                with lock:diagnostic.update(state='failed',message='',error=str(exc))
        threading.Thread(target=scan_worker,daemon=True,name='td-media-scan').start()
        return jsonify(ok=True,reply='Проверката е започната')

    @app.route('/api/wedding/scan',methods=['GET'])
    def wedding_scan_status():
        gate=g['api_guard']()
        if gate:return gate
        with lock:return jsonify(dict(diagnostic))

    @app.route('/api/wedding/relink',methods=['POST'])
    def wedding_relink():
        gate=g['api_guard']()
        if gate:return gate
        data=request.get_json(silent=True) or {}
        try:
            from td_xml_relink import relink_xml
            dest=Path(base)/'TD_DIAGNOSTICS'/'RELINKED_XML'
            report=relink_xml(str(data.get('xml','')),str(data.get('folder','')),dest)
            if report.get('new_xml'):
                atomic_json(Path(report['new_xml']).with_suffix('.json'),report)
            return jsonify(report)
        except (OSError,ValueError,ET.ParseError) as exc:return jsonify(error=str(exc)),400

    @app.route('/api/wedding/repair',methods=['POST','GET'])
    def wedding_repair():
        gate=g['api_guard']()
        if gate:return gate
        if request.method=='GET':
            with lock:return jsonify(dict(repair))
        data=request.get_json(silent=True) or {}
        original=Path(str(data.get('file',''))).expanduser()
        try:audio_stream=int(data.get('audio_stream',0))
        except (ValueError,TypeError):return jsonify(error='Невалиден аудио stream.'),400
        folder=Path(str(data.get('folder',''))).expanduser()
        if not folder.is_absolute() or not folder.is_dir() or not original.is_absolute() or not original.is_file():
            return jsonify(error='Избери ја оригиналната папка и постоечкиот видео фајл.'),400
        try:
            original=original.resolve();folder=folder.resolve()
            if folder not in original.parents:return jsonify(error='Фајлот не е во избраниот свадбен проект.'),400
        except OSError as exc:return jsonify(error=str(exc)),400
        with lock:
            if repair['state']=='running' or job.get('state')=='running':return jsonify(error='Друга обработка е во тек.'),409
            repair.update(state='running',progress=0,message=original.name,result=None,error='')
        def repair_worker():
            try:
                destination=Path(base)/'TD_MEDIA_REPAIRS'/hashlib.sha256(str(folder).encode()).hexdigest()[:12]
                result=safe_copy(original,destination,audio_stream=audio_stream)
                mappings=json_file(replacements_file);mappings[str(original)]=result['copy']
                atomic_json(replacements_file,mappings)
                with lock:repair.update(state='finished',progress=100,result=result,message='Готова проверена H.264/AAC копија')
            except Exception as exc:
                with lock:repair.update(state='failed',error=str(exc),message='Неуспешна конверзија')
        threading.Thread(target=repair_worker,daemon=True,name='td-media-repair').start()
        return jsonify(ok=True,reply='Создавам нова компатибилна копија без да го менувам оригиналот.')

    @app.route('/wedding-learning')
    def wedding_learning_page():
        gate=g['api_guard']()
        if gate:return gate
        from flask import Response
        html=(Path(__file__).with_name('TD_WEDDING_LEARNING.html')).read_text(encoding='utf-8')
        response=Response(html,mimetype='text/html')
        response.headers['Cache-Control']='no-store, no-cache, must-revalidate'
        return response

    @app.route('/api/bridge/music-catalog',methods=['POST','OPTIONS'])
    def music_catalog():
        if request.remote_addr not in ('127.0.0.1','::1'):return jsonify(error='Loopback only'),403
        if request.method=='OPTIONS':return '',204
        key=request.headers.get('X-TD-Bridge-Key','')
        if not cfg.get('premiere_bridge_secret') or not secrets.compare_digest(key,cfg['premiere_bridge_secret']):return jsonify(error='Bridge not paired'),403
        data=request.get_json(silent=True) or {};items=data.get('files',[])
        if not isinstance(items,list) or len(items)>500:return jsonify(error='Invalid music catalog'),400
        valid=[]
        for raw in items:
            if not isinstance(raw,str) or len(raw)>2048:continue
            p=Path(raw)
            if p.is_absolute() and p.is_file() and p.suffix.lower() in AUDIO_EXT:valid.append(str(p.resolve()))
        catalog.update(time=time.time(),files=list(dict.fromkeys(valid)),project=str(data.get('project',''))[:100])
        return jsonify(ok=True,count=len(catalog['files']))

    @app.route('/api/studio/music')
    def music_status():
        gate=g['api_guard']()
        if gate:return gate
        try:
            folder=request.args.get('folder','').strip() or cfg.get('td_music_folder','')
            files,source=available_music(folder)
            return jsonify(count=len(files),source=source,
                           tracks=[{'path':p,'name':Path(p).stem} for p in files[:500]])
        except (OSError,ValueError) as exc:return jsonify(error=str(exc)),400

    # The existing Director calls this function after generating XML. Retitle a
    # COPY inside TD_AI_EDIT and choose a unique native project path.
    import project_bridge
    original_workspace=project_bridge.prepare_project_workspace
    def named_workspace(folder,xml,log=print):
        package=original_workspace(folder,xml,log)
        with lock:title=job.get('project_title','')
        if not title:return package
        title=safe_name(title);tree=ET.parse(package['xml_path']);seqs=tree.findall('./sequence')
        if len(seqs)==1:seqs[0].find('name').text=title+' · ВИДЕО СПОТ'
        tree.write(package['xml_path'],encoding='utf-8',xml_declaration=True)
        candidate=Path(folder)/(title+' - TD AI.prproj');n=2
        while candidate.exists():candidate=Path(folder)/(title+f' - TD AI {n}.prproj');n+=1
        package['project_path']=candidate
        readme=Path(package['workspace'])/'READ_ME_FIRST.txt'
        with readme.open('a',encoding='utf-8') as f:f.write('\nUser project name: '+title+'\nActual planned Premiere path: '+str(candidate)+'\n')
        with lock:job['project']=title
        return package
    project_bridge.prepare_project_workspace=named_workspace

    @app.before_request
    def remember_project_title():
        if request.method=='POST' and request.path in ('/api/director/start','/api/start'):
            gate=g['api_guard']()
            if gate:return gate
            data=request.get_json(silent=True) or {}
            with lock:
                if job.get('state')=='running':return None
                try:title=safe_name(data['project_name']) if data.get('project_name') else ''
                except ValueError as exc:return jsonify(error=str(exc)),400
                job['project_title']=title

    @app.route('/api/studio/spot',methods=['POST'])
    def studio_spot():
        gate=g['api_guard']()
        if gate:return gate
        data=request.get_json(silent=True) or {}
        with lock:
            if job.get('state')=='running':return jsonify(error='Веќе се обработува проект.'),409
        try:
            title=safe_name(data.get('project_name',''))
            music,source=available_music(str(data.get('music_folder','')).strip(),str(data.get('music','')).strip())
            if not music:raise ValueError('Додај песна во MUSIC во Premiere или во 02_MUSIC.')
            metas={}
            for p in music:
                try:metas[p]=probe(p)
                except (OSError,ValueError):pass
            song=str(choose_music(music,metas,'bride'))
            payload={'folder':data.get('folder',''),'project_name':title,'music':song,
                     'seconds':int(data.get('seconds',180)),'quality':data.get('quality','standard'),
                     'romantic':True,'render_slow':False,'auto_import':True,
                     'style_profile':str(data.get('style_profile','')).strip()}
        except (ValueError,TypeError,OSError) as exc:return jsonify(error=str(exc)),400
        # Forward internally, retaining the real signed-in session and the same
        # authentication marker. Existing Director remains the spot engine.
        with app.test_request_context('/api/director/start',method='POST',json=payload,headers={
                 'X-TD-Client':'TD-v1.4','Cookie':request.headers.get('Cookie','')},environ_base={'REMOTE_ADDR':request.remote_addr or ''}):
            from flask import session
            session['login']=True
            with lock:job.update(project_title=title,review_count=0,wedding_error='')
            return app.view_functions['director_start']()

    def update(percent,message):
        with lock:
            progress_peak['value']=max(progress_peak['value'],min(99,int(percent)))
            job['wedding_progress']=progress_peak['value'];job['log'].append('[ЦЕЛА СВАДБА] '+str(message))

    def worker(folder,title,music,settings):
        from guard import wait_for_job
        try:
            root=folder/'TD_AI_EDIT';root.mkdir(exist_ok=True)
            out=root/(title+'_Цела_свадба_'+time.strftime('%Y%m%d_%H%M%S'));n=2
            while out.exists():out=root/(title+'_Цела_свадба_'+time.strftime('%Y%m%d_%H%M%S')+f'_{n}');n+=1
            source_paths=wedding_sources(folder)
            faulty=[]
            if settings.get('media_check','quick')!='off':
                level=settings.get('media_check','quick')
                if level not in ('quick','deep'):level='quick'
                report={'files':[],'level':level,'note':'Sampling cannot guarantee all frames.'}
                for i,path in enumerate(source_paths,1):
                    wait_for_job()
                    item=diagnose(path,level);report['files'].append(item)
                    if item['status']=='error':update(min(4,i*4/max(1,len(source_paths))),f'ПРОБЛЕМ СО МЕДИУМ: {Path(path).name}')
                atomic_json(out/'MEDIA_DIAGNOSTIC.json',report)
                with lock:
                    faulty=[x for x in report['files'] if x['status']=='error']
                    if faulty:job['log'].append(f'ВНИМАНИЕ: {len(faulty)} видеа не поминаа проверка на крајот; MEDIA_DIAGNOSTIC.json')
            if settings.get('use_repairs',False):
                mappings=json_file(replacements_file)
                converted=[];phases={};repair_sources={}
                for path in source_paths:
                    source=Path(path).resolve();other=Path(mappings.get(str(source),'')) if str(source) in mappings else None
                    if other and other.is_file():
                        hint=settings.get('source_phase_map',{}).get(str(source)) or folder_phase(source,folder,settings.get('overrides'))
                        if hint=='unknown':hint=hint_for_path(source,folder,json_file(teacher_file))
                        if hint:phases[str(other.resolve())]=hint
                        converted.append(str(other.resolve()))
                        repair_sources[str(other.resolve())]=str(source)
                    else:converted.append(path)
                source_paths=converted
                settings['source_phase_map'].update(phases)
                settings['repair_source_map']=repair_sources
            if settings.get('require_preflight'):
                atomic_json(out/'CONFIRMED_CHAPTERS.json',{'source_folder':str(folder),
                    'assignments':settings.get('source_phase_map',{}),'note':'Approved by operator before edit'})
            result=build_wedding(source_paths,folder,out,title,music,settings,update,wait_for_job)
            result['media_replacements_used']=[p for p in source_paths if 'TD_MEDIA_REPAIRS' in str(p).upper()]
            atomic_json(out/'RESULT.json',result)
            with lock:
                job['export_xml']=result['xml'];job['review_count']=result['review_count'];job['assigned_count']=result['assigned_count'];job['wedding_summary']=result
            wait_for_job()
            project=folder/(title+' - Цела свадба.prproj');n=2
            while project.exists():project=folder/(title+f' - Цела свадба {n}.prproj');n+=1
            with lock:job['project_file']=str(project);job['project_status']='pending_uxp'
            update(97,'Подготвувам Premiere проект')
            # Never report a successful automatic Premiere import if preflight
            # has proven an original media source cannot be decoded.
            if faulty and settings.get('strict_media',True):
                answer={'created':False,'imported':False,'error':f'{len(faulty)} проблематични видеа: погледни MEDIA_DIAGNOSTIC.json; XML е зачуван за рачен преглед.'}
                with lock:job['project_status']='blocked_media_errors'
            else:
                answer=g['premiere_bridge'].submit_project(project,Path(result['xml']),g['get_state'])
            wait_for_job()
            with lock:
                if job.get('project_status')!='blocked_media_errors':
                    job['project_status']='created' if answer.get('created') and answer.get('imported') else 'pending_manual'
                if answer.get('error'):job['log'].append('XML е зачуван; Premiere: '+str(answer['error']))
                if result.get('motion_flags'):job['log'].append(f"ПРОВЕРИ ДВИЖЕЊЕ: {result['motion_flags']} маркери; CAMERA_MOTION.csv")
                warnings=[]
                if faulty and settings.get('strict_media',True):warnings.append(f'{len(faulty)} media errors; PREMIERE IMPORT BLOCKED. See MEDIA_DIAGNOSTIC.json.')
                if result['review_count']:warnings.append(f"{result['review_count']} нејасни делови се ВКЛУЧЕНИ во главната секвенца; провери CHAPTERS.csv и редоследот.")
                if warnings:job['wedding_error']=' | '.join(warnings)
                job.update(state='finished',stage='complete',wedding_progress=100,done=time.time(),exit_code=0)
            if g['get_state']().get('mode')=='running':g['set_state']('stopped','Wedding chapters completed')
        except InterruptedError:
            with lock:job.update(state='stopped',stage='stopped',done=time.time(),exit_code=130)
        except Exception as exc:
            with lock:
                job.update(state='failed',stage='failed',done=time.time(),exit_code=1,wedding_error=str(exc));job['log'].append('ГРЕШКА: '+str(exc))
            if g['get_state']().get('mode')=='running':g['set_state']('stopped','Wedding failed')

    # v2.8: Classify FIRST, let the operator correct phases, THEN build sequences.
    # Kept separate from camera synchronization and final video editing.
    from td_wedding_chapters import analyze_sources, consolidate_phase_by_file, PHASES
    prep_lock=threading.RLock()
    prep={'state':'idle','progress':0,'message':'','error':'','token':'',
          'folder':'','files':[],'approved':False,'approved_map':{},'signature':''}

    def prep_signature(paths):
        rows=[]
        for src in paths:
            p=Path(src);st=p.stat()
            rows.append((str(p.resolve()),st.st_size,st.st_mtime_ns))
        return hashlib.sha256(json.dumps(rows,ensure_ascii=False).encode('utf-8')).hexdigest()

    @app.route('/api/wedding/prepare',methods=['GET','POST'])
    def wedding_prepare():
        gate=g['api_guard']()
        if gate:return gate
        if request.method=='GET':
            with prep_lock:
                return jsonify(copy.deepcopy(prep))
        data=request.get_json(silent=True) or {}
        try:
            root=Path(str(data.get('folder',''))).expanduser().resolve()
            if not root.is_dir():raise ValueError('Избери постоечка папка со видеа.')
            source_files=wedding_sources(root)
            if not source_files:raise ValueError('Нема видеа во избраната папка.')
            overrides=data.get('overrides',{})
            if not isinstance(overrides,dict):raise ValueError('Невалидни поглавја.')
            for phase,sub in overrides.items():
                if phase not in dict(PHASES) or not isinstance(sub,str):raise ValueError('Невалидно поглавје.')
                if sub:
                    subdir=(root/sub).resolve()
                    if (subdir!=root and root not in subdir.parents) or not subdir.is_dir():
                        raise ValueError('Погрешна потпапка за '+phase+': '+sub)
            model=str(data.get('model','qwen2.5vl:3b')).strip()
            quality=str(data.get('quality','standard'))
            if quality not in ('fast','standard','deep'):raise ValueError('Невалидна анализа.')
            if not 1<=len(model)<=120:raise ValueError('Невалиден Ollama модел.')
            sig=prep_signature(source_files)
        except (OSError,ValueError) as exc:return jsonify(error=str(exc)),400
        with prep_lock:
            if prep['state']=='running':return jsonify(error='Претходната анализа сè уште работи.'),409
            prep.update(state='running',progress=0,message='Прво ги делам оригиналните снимки по поглавја',
                        error='',token='',folder=str(root),files=[],approved=False,approved_map={},signature=sig)
        def do_prepare():
            try:
                out=Path(root)/'TD_AI_CACHE'/'PHASE_PREVIEW'
                out.mkdir(parents=True,exist_ok=True)
                def report(percent,msg):
                    with prep_lock:
                        prep.update(progress=min(98,max(0,int(percent))),message=msg)
                rows,meta,errors=analyze_sources(source_files,root,out,
                    {'model':model,'quality':quality,'overrides':overrides},report,lambda:None)
                groups=collections.defaultdict(list)
                for c in rows:groups[c['path']].append(c)
                stage=[];counts=collections.Counter()
                for src in source_files:
                    clips=groups.get(src,[])
                    if not clips:continue  # unprobeable originals appear in errors
                    guessed=folder_phase(src,root,overrides)
                    reason='Позната папка' if guessed!='unknown' else 'AI анализа'
                    conf=1.0 if guessed!='unknown' else 0.0
                    if guessed=='unknown':
                        sums=collections.Counter()
                        for c in clips:
                            if c['phase'] in dict(PHASES):
                                sums[c['phase']]+=(c['out']-c['in'])*max(.1,c['confidence'])
                        if sums:
                            ranked=sums.most_common()
                            best,weight=ranked[0]
                            total=sum(sums.values())
                            conf=weight/total if total else 0.
                            # Reject mixed source scenes instead of guessing a home.
                            if conf>=.78 and (len(ranked)<2 or ranked[1][1]<weight*.34):guessed=best
                            else:reason='Мешани/нејасни сцени — рачно провери'
                        else:reason='AI не препозна сигурно поглавје'
                    if guessed=='unknown':counts['unknown']+=1
                    else:counts[guessed]+=1
                    stage.append({'path':src,'name':Path(src).name,'folder':str(Path(src).parent),
                                  'phase':guessed,'confidence':round(conf,3),
                                  'duration':round(meta[src]['duration'],2),
                                  'reason':reason,'mixed':len({c['phase'] for c in clips if c['phase']!='unknown'})>1})
                unreadable=[p for p in source_files if p not in groups]
                token=secrets.token_hex(16)
                with prep_lock:
                    prep.update(state='finished',progress=100,
                        message=f'Поделени {len(stage)} видеа · {counts["unknown"]} за рачна проверка',
                        files=stage,errors=errors[:100],unreadable=unreadable,
                        counts=dict(counts),token=token)
            except Exception as exc:
                with prep_lock:prep.update(state='failed',error=str(exc)[:800],message='Анализата не заврши')
        threading.Thread(target=do_prepare,daemon=True,name='td-wedding-prepare-v28').start()
        return jsonify(ok=True,reply='Започна прво раздвојување на видеата.'),202

    @app.route('/api/wedding/prepare/approve',methods=['POST'])
    def wedding_approve():
        gate=g['api_guard']()
        if gate:return gate
        data=request.get_json(silent=True) or {}
        proposed=data.get('assignments')
        if not isinstance(proposed,dict):return jsonify(error='Недостига рачната распределба.'),400
        with prep_lock:
            if prep['state']!='finished' or data.get('token')!=prep['token']:
                return jsonify(error='Прво анализирај ја свадбата повторно.'),409
            expected={item['path'] for item in prep['files']}
            if set(proposed)!=expected:return jsonify(error='Распоредот мора да ги содржи сите читливи оригинали.'),400
            if any(v not in dict(PHASES) for v in proposed.values()):
                return jsonify(error='Одбери поглавје за сите нејасни снимки.'),400
            prep.update(approved_map=dict(proposed),approved=True,message='Поделбата е потврдена — може монтажа.')
        return jsonify(ok=True,counts=dict(collections.Counter(proposed.values())))

    @app.route('/api/wedding/start',methods=['POST'])
    def wedding_start():
        gate=g['api_guard']()
        if gate:return gate
        with lock:
            if job.get('state')=='running':return jsonify(error='Веќе се обработува проект.'),409
        data=request.get_json(silent=True) or {}
        try:
            folder=Path(str(data.get('folder',''))).expanduser()
            if not folder.is_absolute() or not folder.is_dir():raise ValueError('Избери ја папката со снимките на компјутерот.')
            folder=folder.resolve();title=safe_name(data.get('project_name',''))
            seconds=int(data.get('seconds',60))
            if seconds not in (30,45,60):raise ValueError('Спотчето треба да биде до 30, 45 или 60 секунди.')
            quality=str(data.get('quality','standard'))
            if quality not in ('fast','standard','deep'):raise ValueError('Невалидна анализа.')
            model=str(data.get('model','qwen2.5vl:3b')).strip()
            if not model or len(model)>120:raise ValueError('Внеси го името на локалниот визуелен модел.')
            overrides=data.get('overrides',{})
            if not isinstance(overrides,dict):raise ValueError('Невалидни папки по поглавје.')
            valid_overrides={}
            for phase,relative in overrides.items():
                if phase not in ('groom','bride','church','reception') or not relative:continue
                if not isinstance(relative,str):raise ValueError('Невалидна папка по поглавје.')
                target=(folder/relative).resolve()
                if target!=folder and folder not in target.parents:raise ValueError('Папките по поглавје треба да бидат во изворната папка.')
                if not target.is_dir():raise ValueError('Не постои папка по поглавје: '+relative)
                valid_overrides[phase]=relative
            if len(valid_overrides)>1:
                targets=[(folder/r).resolve() for r in valid_overrides.values()]
                if any(a==b or a in b.parents or b in a.parents for i,a in enumerate(targets) for b in targets[i+1:]):raise ValueError('Папките по поглавје се преклопуваат.')
            if not wedding_sources(folder):raise ValueError('Нема оригинални видеа во избраната папка.')
            if data.get('require_preflight'):
                with prep_lock:
                    if (not prep['approved'] or prep['folder']!=str(folder) or
                        prep['token']!=data.get('plan_token')):
                        raise ValueError('Прво анализирај, поправи и потврди ги четирите поглавја.')
                    if prep['signature']!=prep_signature(wedding_sources(folder)):
                        prep['approved']=False
                        raise ValueError('Изворните снимки се сменети. Повтори ја поделбата.')
            music,provider=available_music(str(data.get('music_folder','')).strip(),str(data.get('music','')).strip())
            if not music:raise ValueError('Нема достапна музика. Додај песни во MUSIC во Premiere или во 02_MUSIC.')
        except (TypeError,ValueError,OSError) as exc:return jsonify(error=str(exc)),400
        cfg['last_source']=str(folder);cfg['td_music_folder']=str(data.get('music_folder','')).strip()
        g['CONFIG'].write_text(json.dumps(cfg,indent=2),encoding='utf-8')
        style_id=str(data.get('documentary_style','')).strip()
        if style_id:
            try: style=load_documentary(profile_dir,style_id)
            except (ValueError,OSError,KeyError,json.JSONDecodeError) as exc:return jsonify(error='Не може да се вчита стилот: '+str(exc)),400
        else: style=None
        chosen_music=data.get('phase_music',{})
        if not isinstance(chosen_music,dict):return jsonify(error='Невалиден избор на песни.'),400
        for phase,p in chosen_music.items():
            if phase not in ('groom','bride','church','reception') or not isinstance(p,str) or str(Path(p).resolve()) not in music:
                return jsonify(error='Песната за поглавје не е во избраната музичка папка: '+phase),400
        hints=json_file(teacher_file)
        sources=wedding_sources(folder)
        phase_map={}
        if data.get('require_preflight'):
            with prep_lock:phase_map.update(copy.deepcopy(prep['approved_map']))
        for p in sources:
            if folder_phase(p,folder,valid_overrides)!='unknown':continue
            rule=hint_for_path(p,folder,hints)
            if rule and str(Path(p).resolve()) not in phase_map:phase_map[str(Path(p).resolve())]=rule
        defaults=style.get('phase_parameters',{}) if style else {}
        phase_dissolves={phase:float(info['dissolve_seconds']) for phase,info in defaults.items()
                   if phase in ('groom','bride','church','reception') and isinstance(info,dict)
                   and isinstance(info.get('dissolve_seconds'),(int,float))}
        user_dissolves=data.get('phase_dissolves',{})
        if not isinstance(user_dissolves,dict):return jsonify(error='Невалидни премини.'),400
        for phase,seconds_user in user_dissolves.items():
            if phase not in ('groom','bride','church','reception'):continue
            try:seconds_user=float(seconds_user)
            except (ValueError,TypeError):return jsonify(error='Преминот мора да биде број.'),400
            if not .15<=seconds_user<=1.5:return jsonify(error='Дозволени се премини 0.15..1.5 секунди.'),400
            phase_dissolves[phase]=seconds_user
        settings={**data,'seconds':seconds,'quality':quality,'model':model,'overrides':valid_overrides,
                  'source_phase_map':phase_map,'phase_dissolves':phase_dissolves,
                  'documentary_dissolve_seconds':style['dissolve_seconds'] if style else .45,
                  'phase_music':chosen_music}

        with lock:
            if job.get('state')=='running':return jsonify(error='Веќе се обработува проект.'),409
            job.update(state='running',stage='wedding',project=title,project_title=title,source=str(folder),project_file='',project_status='analyzing',export_xml='',review_count=0,assigned_count=0,wedding_error='',wedding_summary={},wedding_progress=0,started=time.time(),done=0,exit_code=None)
            job['log'].clear();progress_peak['value']=0

        g['set_state']('running','Wedding chapters started by authenticated user')
        threading.Thread(target=worker,args=(folder,title,music,settings),daemon=True,name='td-wedding-v21').start()
        return jsonify(ok=True,reply='Започнувам со поглавјата на свадбата.',music_source=provider)
