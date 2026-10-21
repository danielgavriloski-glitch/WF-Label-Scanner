"""Local one-click TD Director workflow for the existing v1.5/v1.6 Flask studio.
Registration is additive; preserves PIN, hotkeys, Lightroom and screen UI.
"""
from __future__ import annotations
import base64, collections, json, os, subprocess, sys, threading, time, urllib.request
from pathlib import Path
from flask import jsonify, request
from camera_sources import discover_sources


def install_director_routes(g):
    app=g['app'];BASE=g['BASE'];CFG=g['CFG'];lock=g['lock'];job=g['job']
    def log(s):
        with lock:job['log'].append('[DIRECTOR] '+str(s))
    def access():return g['api_guard']()
    def find_premiere():
        if os.name!='nt':return None
        roots=[Path(os.environ[key])/'Adobe' for key in ('ProgramFiles','ProgramW6432') if os.environ.get(key)]
        for root in roots:
            if root.is_dir():
                for d in sorted(root.glob('Adobe Premiere Pro *'),reverse=True):
                    exe=d/'Adobe Premiere Pro.exe'
                    if exe.is_file():return exe
        return None
    def observe_screen(description):
        """Read-only local vision to detect known dialogs. Never trust vision to generate arbitrary clicks."""
        try:
            grab=g.get('grab')
            if not grab:return None
            jpeg=grab()
            payload={'model':'qwen2.5vl:3b','stream':False,'format':'json',
                'messages':[{'role':'user','content':'Analyze Windows desktop screenshot for Premiere automation. Return JSON with keys premiere_project_open(boolean), blocking_dialog(boolean), notes(string short). Only if visually certain; uncertain means project_open=false. Do not give instructions or click coordinates.',
                             'images':[base64.b64encode(jpeg).decode('ascii')]}],
                'options':{'temperature':0,'num_ctx':1000}}
            req=urllib.request.Request('http://127.0.0.1:8798/api/chat',data=json.dumps(payload).encode(),headers={'Content-Type':'application/json'})
            with urllib.request.urlopen(req,timeout=95) as resp:reply=json.load(resp)
            return json.loads(reply['message']['content'])
        except Exception as e:
            log('Screen vision unavailable: '+str(e)[:130]);return None
    def import_to_premiere(xml):
        # A click on AUTO EDIT / AI DIRECTOR is permission for this single
        # safe, scoped Premiere import. It does NOT enable arbitrary phone
        # mouse control or persist the remote-control permission.
        if g['get_state']()['mode'] != 'running':
            log('Auto-import cancelled: F9 paused or F10 stopped control.');return
        try:
            import pyautogui as pg
            import pygetwindow as gw
            wins=[w for w in gw.getAllWindows() if 'premiere pro' in (w.title or '').lower()]
            if not wins:
                log('Premiere window not ready. Keep Premiere open with an empty project and use File > Import.');return
            w=wins[0]
            if w.isMinimized:w.restore()
            w.activate();time.sleep(1)
            if g['get_state']()['mode']!='running':
                log('Import paused or stopped before UI action.');return
            insight=observe_screen('before import')
            if insight is not None:
                log('Premiere screen AI: '+str(insight.get('notes',''))[:140])
                if not insight.get('premiere_project_open',False) or insight.get('blocking_dialog',False):
                    log('PREMIERE NOT READY: Open a project/close dialogs. AUTO IMPORT CANCELLED for safety.');return
            else:
                log('Screen AI could not confirm Premiere state. Auto-import skipped for safety; XML saved.')
                return
            # A known safe macro: File > Import is Ctrl+I on Windows Premiere.
            pg.hotkey('ctrl','i');time.sleep(1.1)
            if g['get_state']()['mode']!='running':log('Cancelled by F9/F10');return
            pg.write(str(xml),interval=.001);pg.press('enter')
            log('PREMIERE IMPORT REQUEST SENT: '+str(xml.name))
            time.sleep(2)
            insight=observe_screen('after import')
            if insight:
                log('After-import screen AI: '+str(insight.get('notes',''))[:180])
                if insight.get('blocking_dialog',False):log('Blocking dialog detected: inspect Premiere manually; stopped automatic UI actions.')
            log('Import requested. Verify XML appeared and inspect cuts/links; no further blind clicks.')
        except Exception as e:
            log('Premiere auto-import could not finish: '+repr(e)[:200]+' — XML is still saved.')

    def run_stage(folder, music=None, seconds=0, quality='standard', romantic=True,
                  render_slow=False, auto_import=True, style_profile=None):
        """Run one Director stage; may follow Auto Edit without another click.

        Runs outside request context; only a previously authenticated START
        creates this worker. F9/F10 remain the shared gate for the entire task.
        """
        with lock:
            job['state']='running'
            job['stage']='director'
            job['project']=folder.name
            job['source']=str(folder)
            job['music']=str(music or '')
            job['export_xml']=''
            job['project_file']=''
            job['project_status']='analyzing'
            job['log'].append('[DIRECTOR] Stage 2 — romantic story, variable cuts and music timing')
        command=[sys.executable,str(BASE/'director_quality_v19.py'),'--base',str(g['ASSET_BASE']),
                 '--folder',str(folder),'--quality',quality,'--seconds',str(seconds)]
        if style_profile:
            from td_style_learning import choose_profile
            choose_profile(BASE/'TD_LEARNED_STYLES',style_profile)
            command.extend(['--style-profile',str(style_profile)])
        if music:command.extend(['--music',str(music)])
        if romantic:command.append('--romantic-ai')
        if render_slow:command.append('--render-slow')
        try:
            from guard import wait_for_job
            wait_for_job()  # F9 pauses the transition between Auto Edit and Director
            if auto_import:
                exe=find_premiere()
                if exe:
                    try:
                        import pygetwindow as gw
                        if not any('premiere pro' in (w.title or '').lower() for w in gw.getAllWindows()):
                            subprocess.Popen([str(exe)],cwd=str(exe.parent))
                            log('Opening Premiere; a project must be open before XML import.')
                    except Exception as e:log('Premiere launch warning: '+str(e)[:180])
                else:log('Premiere executable not found. XML will still be saved.')
            child_env=os.environ.copy()
            child_env['PYTHONIOENCODING']='utf-8:backslashreplace'
            child_env['PYTHONUTF8']='1'
            p=subprocess.Popen(command,cwd=str(BASE),stdout=subprocess.PIPE,
                 stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',bufsize=1,
                 env=child_env)
            with lock:job['proc']=p
            for line in p.stdout:
                with lock:
                    job['log'].append(line.rstrip())
                    if line.startswith('EXPORT XML:'):job['export_xml']=line.partition(':')[2].strip()
            code=p.wait()
            with lock:job.pop('proc',None)
            if code==0:
                wait_for_job()
                xml=Path(job.get('export_xml',''))
                if xml.is_file():
                    # Always keep a readable XML draft near source footage.
                    # Native .prproj is created only by the paired Premiere UXP plugin.
                    from project_bridge import prepare_project_workspace
                    package=prepare_project_workspace(folder, xml, log)
                    with lock:
                        job['project_file']=str(package['project_path'])
                        job['project_status']='pending_uxp'
                    if auto_import:
                        bridge=g['premiere_bridge']
                        result=bridge.submit_project(package['project_path'], package['xml_path'], g['get_state'])
                        if result.get('created'):
                            with lock: job['project_status']='created'
                            log('Native Premiere project created: '+str(package['project_path']))
                            if not result.get('imported') and g['get_state']()['mode']=='running':
                                log('UXP project created but XML not imported via API; attempting safe Premiere File > Import.')
                                import_to_premiere(package['xml_path'])
                            elif result.get('imported'):
                                log('UXP confirmed imported sequence in the Premiere project.')
                        else:
                            with lock: job['project_status']='pending_manual'
                            log('Premiere project creation not confirmed: '+str(result.get('error','Plugin not paired/active')))
                            log('XML draft saved beside source videos. Do not rename it .prproj.')
                            # Existing workflow can still import if a project is already open.
                            if g['get_state']()['mode']=='running': import_to_premiere(package['xml_path'])
                else:
                    log('Director XML missing: project creation skipped.')
            mode=g['get_state']()['mode']
            with lock:
                job.update(exit_code=code,done=time.time(),
                    state='stopped' if mode=='stopped' else ('finished' if code==0 else 'failed'))
                job['stage']='complete' if code==0 and mode!='stopped' else 'stopped/failed'
            if mode=='running':g['set_state']('stopped','Finished Director task')
            return code
        except InterruptedError:
            with lock:job.update(state='stopped',stage='stopped',done=time.time(),exit_code=130);job.pop('proc',None)
            log('Task stopped via F10 or Stop; no further Premiere actions.')
            return 130
        except Exception as e:
            log('Director ERROR: '+repr(e)[:230])
            with lock:job.update(state='failed',stage='failed',done=time.time(),exit_code=1);job.pop('proc',None)
            if g['get_state']()['mode']=='running':g['set_state']('stopped','Director failed')
            return 1

    # Server's AUTO EDIT worker uses the very same Director code automatically.
    g['director_stage_runner']=run_stage

    @app.route('/api/director/sources',methods=['GET'])
    def director_sources():
        gate=access()
        if gate:return gate
        raw=str(request.args.get('folder','')).strip()
        project=str(request.args.get('project','')).strip()
        if raw:
            folder=Path(raw).expanduser()
            if not folder.is_absolute():return jsonify(error='Folder must be an absolute path'),400
        else:
            if not project or project in ('.','..') or '/' in project or '\\' in project:
                return jsonify(error='Select project folder'),400
            folder=g['PROJECT_ROOT']/project
        try:
            d=discover_sources(folder)
            return jsonify(folder=str(d['root']),video_count=d['video_count'],
                    source_folders=d['folders'],source_folder_count=d['folder_count'])
        except (FileNotFoundError,PermissionError,OSError) as e:
            return jsonify(error='Cannot scan chosen folder: '+str(e)),400

    @app.route('/api/director/start',methods=['POST'])
    def director_start():
        gate=access()
        if gate:return gate
        data=request.get_json(silent=True) or {}
        raw=str(data.get('folder','')).strip(); project=str(data.get('project','')).strip()
        if raw:folder=Path(raw).expanduser()
        else:
            if not project or project in ('.','..') or '/' in project or '\\' in project:
                return jsonify(error='Choose a project folder'),400
            folder=g['PROJECT_ROOT']/project
        if not folder.is_absolute() or not folder.is_dir():return jsonify(error='Source folder does not exist'),400
        folder=folder.resolve()
        sources=discover_sources(folder)
        if not sources['files']:return jsonify(error='No video files found inside this wedding folder'),400
        music_txt=str(data.get('music','')).strip()
        music=Path(music_txt).expanduser().resolve() if music_txt else None
        if music and (not music.is_file() or music.suffix.lower() not in ('.mp3','.wav','.m4a','.aif','.aiff')):
            return jsonify(error='Music path is invalid'),400
        try:seconds=int(data.get('seconds',0))
        except Exception:return jsonify(error='Invalid duration'),400
        if seconds not in (0,120,180,240,300):return jsonify(error='Select a supported duration'),400
        quality=str(data.get('quality','standard'))
        if quality not in ('fast','standard','deep'):return jsonify(error='Invalid analysis quality'),400
        with lock:
            if job['state']=='running':return jsonify(error='An edit is already running'),409
            job.update(state='running',stage='director',project=folder.name,source=str(folder),project_file='',project_status='analyzing',
                       music=str(music or ''),export_xml='',started=time.time(),done=0,exit_code=None)
            job['log'].clear();job['log'].append(f"START AI DIRECTOR — {sources['video_count']} videos / {sources['folder_count']} source folders; AI control automatically enabled.")
        CFG['last_source']=str(folder);CFG['last_music']=str(music or '')
        g['CONFIG'].write_text(json.dumps(CFG,indent=2),encoding='utf-8')
        g['set_state']('running','AI Director started by authenticated user')
        threading.Thread(target=run_stage,kwargs=dict(folder=folder,music=music,seconds=seconds,
                         quality=quality,romantic=bool(data.get('romantic',True)),
                         render_slow=bool(data.get('render_slow',False)),auto_import=True,
                         style_profile=str(data.get('style_profile','')).strip() or None),
                         daemon=True,name='director171-worker').start()
        return jsonify(ok=True,reply='AI Director running. Premiere import will be attempted after editing; F9 pause, F10 stop.')

# TD_STYLE_LEARNING_V2_0
