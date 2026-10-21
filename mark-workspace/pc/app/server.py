"""LAN-only TD Production controller with Windows Desktop app launcher."""
from __future__ import annotations
import base64, collections, json, os, re, secrets, socket, subprocess, sys, threading, time, urllib.request, urllib.error, webbrowser
from pathlib import Path
from camera_sources import discover_sources
from guard import get_state, set_state, attach_global_hotkeys, mark_automated_input, get_hotkey_status
from flask import Flask, Response, jsonify, redirect, render_template_string, request, session, send_from_directory, url_for, make_response

BASE=Path(__file__).resolve().parent
# The launcher supplies the project root explicitly. This also supports ZIPs
# extracted inside an extra folder (e.g. Downloads/TD_AI.../TD_AI_Local...).
ASSET_BASE=Path(os.environ.get('TD_AI_ROOT', str(BASE.parent))).resolve()
PROJECT_ROOT=ASSET_BASE/'01_INPUT_VIDEO'
STATIC=BASE/'static'
CONFIG=BASE/'td_local_config.json'
PORT=8765

app=Flask(__name__,static_folder=str(STATIC),static_url_path='/static')
app.secret_key=secrets.token_hex(32) # regenerated on restart
app.config.update(SESSION_COOKIE_HTTPONLY=True,SESSION_COOKIE_SAMESITE='Strict')
job={'state':'idle','stage':'idle','project':'','project_file':'','project_status':'not_started','log':collections.deque(maxlen=180),'started':0,'done':0,'exit_code':None}
latest_suggestion={}
lock=threading.Lock()
LOGIN_ATTEMPTS={}

def load_config():
    if CONFIG.exists():
        data=json.loads(CONFIG.read_text('utf-8'))
    else:
        data={'pin':str(secrets.randbelow(900000)+100000),'allow_remote_control':False}
        CONFIG.write_text(json.dumps(data,indent=2),encoding='utf-8')
    return data

CFG=load_config()

def ip():
    try:
        s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);s.connect(('192.0.2.1',80));value=s.getsockname()[0];s.close();return value
    except OSError:return '127.0.0.1'

def authed():return bool(session.get('login'))

def local_request():return request.remote_addr in ('127.0.0.1','::1')

def api_guard():
    if not authed():return jsonify(error='Login required'),401
    if request.method=='POST' and request.headers.get('X-TD-Client')!='TD-v1.4':return jsonify(error='Missing request marker'),403
    return None

@app.route('/api/td-photo-open-v04', methods=['POST'])
def td_photo_open_v04():
    if not authed() or not local_request():
        return jsonify(error='Дозволено само од локалниот компјутер со PIN.'),403
    if request.headers.get('X-TD-Client') != 'TD-v1.4':
        return jsonify(error='Missing request marker'),403
    target = BASE / 'TD_PHOTO_AI' / 'photo_app.py'
    if not target.exists():
        return jsonify(error='Фото модулот не е пронајден.'),404
    try:
        subprocess.Popen([sys.executable,str(target)],cwd=str(target.parent),creationflags=getattr(subprocess,'CREATE_NEW_PROCESS_GROUP',0))
        return jsonify(message='PHOTO AI прозорецот е стартуван. Провери ја Windows taskbar.')
    except Exception as e:
        return jsonify(error=str(e)),500
from td_photo_module import register as td_photo_v05_register
td_photo_v05_register(app, authed, local_request)

@app.route('/login',methods=['GET','POST'])
def login():
    if request.method=='GET':return render_template_string('''<!DOCTYPE html><html lang="mk"><meta name="viewport" content="width=device-width,initial-scale=1"><meta charset="utf-8"><title>TD AI Login</title><style>body{font:17px system-ui;background:#0b111c;color:#f5f7fb;display:grid;place-items:center;min-height:90vh}main{width:min(90vw,340px);padding:25px;background:#162132;border-radius:18px}input,button{width:100%;box-sizing:border-box;padding:14px;border-radius:9px;font-size:18px;margin:12px 0}input{background:#0a1320;color:white;border:1px solid #344557}button{background:#1dd5b0;border:0;font-weight:700}p{color:#91a5b8}</style><main><h2>TD AI Studio</h2><p>Локален пристап со PIN</p><form method="post"><input name="pin" placeholder="6 цифри" type="password" inputmode="numeric" autocomplete="off" required><button>ВЛЕЗИ</button></form></main></html>''')
    address=request.remote_addr or '?'
    fails,ts=LOGIN_ATTEMPTS.get(address,(0,0))
    if fails>=8 and time.time()-ts<300:return 'Too many attempts; wait 5 minutes',429
    if secrets.compare_digest(request.form.get('pin',''),CFG['pin']):
        LOGIN_ATTEMPTS.pop(address,None);session.clear();session['login']=True
        return redirect('/')
    LOGIN_ATTEMPTS[address]=(fails+1,time.time())
    return 'Invalid PIN. <a href="/login">Try again</a>',403

@app.route('/logout')
def logout():session.clear();return redirect('/login')

@app.route('/')
def home():
    if not authed():return redirect('/login')
    response=make_response(send_from_directory(str(STATIC),'index.html'))
    response.headers['Cache-Control']='no-store, no-cache, must-revalidate'
    return response

@app.route('/_td_ai_health')
def td_health():
    return jsonify(app='TD_PRODUCTION_AI_STUDIO', version='1.7.3')

# Separate Lightroom entry: use the user's existing Lightroom Classic plugin,
# NEVER reimplement or modify its photo corrections inside the video engine.
def find_lightroom_classic():
    if os.name != 'nt':return None
    roots=[]
    for key in ('ProgramFiles','ProgramW6432','ProgramFiles(x86)'):
        val=os.environ.get(key)
        if val and Path(val).is_dir():roots.append(Path(val)/'Adobe')
    for root in roots:
        if not root.is_dir():continue
        for parent in sorted(root.glob('*Lightroom Classic*'),reverse=True):
            for candidate in (parent/'Lightroom.exe',parent/'Adobe Lightroom Classic.exe'):
                if candidate.is_file():return candidate.resolve()
    return None

@app.route('/api/lightroom/info')
def lightroom_info():
    guard=api_guard()
    if guard:return guard
    exe=find_lightroom_classic()
    modules=Path(os.environ.get('APPDATA',''))/'Adobe'/'Lightroom'/'Modules' if os.environ.get('APPDATA') else None
    plugin_folders=[]
    if modules and modules.is_dir():
        plugin_folders=[x.name for x in modules.iterdir() if x.is_dir() and x.name.lower().endswith('.lrplugin') and ('td' in x.name.lower() or 'production' in x.name.lower())]
    return jsonify(installed=bool(exe), plugins=plugin_folders, local=local_request())

@app.route('/api/lightroom/open',methods=['POST'])
def open_lightroom():
    guard=api_guard()
    if guard:return guard
    if not local_request():return jsonify(error='Lightroom can be opened only from the PC, not the phone.'),403
    exe=find_lightroom_classic()
    if not exe:return jsonify(error='Adobe Lightroom Classic was not found in the usual Adobe installation folder. Open it manually from Windows.'),404
    try:
        subprocess.Popen([str(exe)],cwd=str(exe.parent))
        return jsonify(ok=True,reply='Lightroom Classic launched. Open your existing TD Production Assistant using Plug-in Extras.')
    except OSError as ex:return jsonify(error='Unable to start Lightroom: '+str(ex)),503

@app.route('/api/info')
def info():
    guard=api_guard()
    if guard:return guard
    projects=[x.name for x in sorted(PROJECT_ROOT.iterdir()) if x.is_dir()] if PROJECT_ROOT.exists() else []
    musdir=ASSET_BASE/'02_MUSIC';music=[x.name for x in musdir.iterdir() if x.suffix.lower() in {'.mp3','.wav','.m4a','.aiff','.aif'}] if musdir.is_dir() else []
    with lock:
        result={k:v for k,v in job.items() if k not in ('log','proc')}
        result['log']=list(job['log'])[-80:]
        result['progress']=parse_progress(list(job['log']))
    result.update(last_source=CFG.get('last_source',''),last_music=CFG.get('last_music',''),premiere_bridge=bridge_status(),projects=projects,music=music,screen_available=screen_ok(),remote_control=CFG.get('allow_remote_control',False),is_local=local_request(),control=get_state(),hotkeys=get_hotkey_status(),url=f'http://{ip()}:{PORT}')
    return jsonify(result)

def parse_progress(lines):
    if job['state']=='finished':return 100
    for line in reversed(lines):
        m=re.search(r'AI VISION\s+(\d+)\s*/\s*(\d+)',line)
        if m:return min(99,round(45+54*int(m.group(1))/max(1,int(m.group(2)))))
        m=re.search(r'ANALYZING\s+(\d+)\s*/\s*(\d+)',line)
        if m:return min(45,round(45*int(m.group(1))/max(1,int(m.group(2)))))
    return 0

def screen_ok():
    try:
        import mss
        with mss.mss() as s:
            return len(s.monitors)>1
    except Exception:return False

def grab():
    import io, mss
    from PIL import Image
    with mss.mss() as m:
        monitor=m.monitors[1]
        pic=m.grab(monitor)
        im=Image.frombytes('RGB',pic.size,pic.rgb)
        im.thumbnail((960,540))
        b=io.BytesIO();im.save(b,format='JPEG',quality=58,optimize=True)
        return b.getvalue()

@app.route('/api/screen.jpg')
def screen():
    guard=api_guard()
    if guard:return guard
    try:
        data=grab()
        return Response(data,mimetype='image/jpeg',headers={'Cache-Control':'no-store'})
    except Exception as e:return jsonify(error='Screen capture unavailable: '+str(e)),503

@app.route('/api/pick-path',methods=['POST'])
def pick_path():
    guard=api_guard()
    if guard:return guard
    if not local_request():return jsonify(error='Choose folders only on the Windows PC. The choice remains available on the phone.'),403
    if os.name!='nt':return jsonify(error='Native Windows folder selection is unavailable on this system'),501
    kind=str((request.get_json(silent=True) or {}).get('kind','folder'))
    if kind not in ('folder','music'):return jsonify(error='Unknown file picker'),400
    script = ("$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Windows.Forms; "
        "if ('KIND' -eq 'music') { "
        "$p=New-Object System.Windows.Forms.OpenFileDialog; "
        "$p.Filter='Audio files|*.mp3;*.wav;*.m4a;*.aif;*.aiff|All files|*.*'; "
        "} else { $p=New-Object System.Windows.Forms.FolderBrowserDialog; "
        "$p.Description='Choose wedding video folder'; } "
        "if ($p.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { "
        "if ('KIND' -eq 'music') { Write-Output $p.FileName } "
        "else { Write-Output $p.SelectedPath } }").replace('KIND',kind)
    try:
        cp=subprocess.run(['powershell.exe','-NoProfile','-STA','-Command',script],capture_output=True,text=True,timeout=240)
        if cp.returncode:return jsonify(error='File selection error: '+cp.stderr[-400:]),503
        names=[line.strip() for line in cp.stdout.splitlines() if line.strip()]
        return jsonify(ok=True,path=names[-1] if names else '')
    except subprocess.TimeoutExpired:return jsonify(error='File picker timed out'),408
    except OSError as e:return jsonify(error=str(e)),503

@app.route('/api/ollama')
def ollama_info():
    guard=api_guard()
    if guard:return guard
    try:
        with urllib.request.urlopen('http://127.0.0.1:8798/api/tags',timeout=3) as response:
            data=json.load(response)
        names=[str(m.get('name','')) for m in data.get('models',[])]
        desired='qwen2.5vl:3b'
        return jsonify(running=True,model=desired,model_ready=desired in names,installed=names,
                       help='MarK Vision is installed locally.')
    except Exception:
        return jsonify(running=False,model='qwen2.5vl:3b',model_ready=False,installed=[],
                       help='Start MarK local inference and refresh. Your footage remains local.')

@app.route('/api/start',methods=['POST'])
def start():
    guard=api_guard()
    if guard:return guard
    data=request.get_json(silent=True) or {}
    name=str(data.get('project',''))
    folder_txt=str(data.get('folder','')).strip()
    if folder_txt:
        selected=Path(folder_txt).expanduser()
        if not selected.is_absolute() or not selected.is_dir():
            return jsonify(error='Selected wedding folder not found on this computer'),400
    else:
        selected=PROJECT_ROOT/name
        if not name or name in ('.','..') or '/' in name or '\\' in name or selected.resolve().parent!=PROJECT_ROOT.resolve() or not selected.is_dir():
            return jsonify(error='Select a wedding project or choose a source folder'),400
    selected=selected.resolve()
    sources=discover_sources(selected)
    if not sources['video_count']:return jsonify(error='No videos found in selected wedding folder or its subfolders'),400
    name=selected.name
    music_txt=str(data.get('music','')).strip()
    if music_txt:
        music_path=Path(music_txt).expanduser()
        if not music_path.is_absolute() or not music_path.is_file() or music_path.suffix.lower() not in ('.mp3','.wav','.m4a','.aif','.aiff'):
            return jsonify(error='Selected music file does not exist or is unsupported'),400
        music_path=music_path.resolve()
    else:
        music_path=None
    quality=str(data.get('quality','standard'))
    if quality not in ('fast','standard','deep'):return jsonify(error='Invalid quality'),400
    try:seconds=int(data.get('seconds',180))
    except (ValueError,TypeError):return jsonify(error='Invalid length'),400
    if not (seconds==0 or 45<=seconds<=360):return jsonify(error='Length must be 0 (song) or 45-360 seconds'),400
    romantic=bool(data.get('romantic',False))
    with lock:
        if job['state']=='running':return jsonify(error='Already working'),409
        job.update(state='running',stage='auto_edit',project=name,source=str(selected),music=str(music_path or ''),export_xml='',started=time.time(),done=0,exit_code=None)
        job['log'].clear();job['log'].append(f"STAGE 1/2: AUTO EDIT — {sources['video_count']} videos in {sources['folder_count']} source folders.")
    CFG['last_source']=str(selected);CFG['last_music']=str(music_path or '');CONFIG.write_text(json.dumps(CFG,indent=2),encoding='utf-8')
    set_state('running','AUTO EDIT / AI DIRECTOR pipeline active')
    cmd=[sys.executable,str(BASE/'engine.py'),'--base',str(ASSET_BASE),'--folder',str(selected),'--quality',quality,'--seconds',str(seconds)]
    if music_path:cmd.extend(['--music',str(music_path)])
    if romantic:cmd.append('--romantic-ai')
    def worker():
        try:
            child_env=os.environ.copy()
            child_env['PYTHONIOENCODING']='utf-8:backslashreplace'
            child_env['PYTHONUTF8']='1'
            p=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,bufsize=1,text=True,encoding='utf-8',errors='replace',cwd=str(BASE),env=child_env)
            with lock:job['proc']=p
            for line in p.stdout:
                with lock:
                    job['log'].append(line.rstrip())
                    if line.startswith('EXPORT XML:'):job['export_xml']=line.partition(':')[2].strip()
            code=p.wait()
            with lock:job.pop('proc',None)
            if code!=0 or get_state()['mode']=='stopped':
                with lock:job.update(state='stopped' if get_state()['mode']=='stopped' else 'failed',
                         stage='stopped/failed',exit_code=code,done=time.time())
                return
            with lock:
                job['log'].append('STAGE 1/2 FINISHED. STARTING AI DIRECTOR AUTOMATICALLY...')
                job['stage']='director_starting'
            try:
                from guard import wait_for_job
                wait_for_job()
                director_stage_runner(folder=selected,music=music_path,seconds=seconds,
                    style_profile=str(data.get('style_profile','')).strip() or None,
                    quality=quality,romantic=romantic,render_slow=bool(data.get('render_slow',False)),
                    auto_import=True)
            except InterruptedError:
                with lock:job.update(state='stopped',stage='stopped',done=time.time(),exit_code=130)
        except Exception as e:
            with lock:job['log'].append('ERROR '+str(e));job.update(state='failed',stage='failed',done=time.time());job.pop('proc',None)
    threading.Thread(target=worker,daemon=True,name='td-oneclick171').start()
    return jsonify(ok=True,reply='AUTO EDIT started. AI Director will start automatically after completion, then attempt Premiere import.')

@app.route('/api/stop',methods=['POST'])
def stop():
    guard=api_guard()
    if guard:return guard
    with lock:
        p=job.get('proc')
        set_state('stopped','Stopped manually')
        if p and p.poll() is None:
            p.terminate();job['log'].append('Stop requested.');return jsonify(ok=True)
    return jsonify(ok=True)

@app.route('/api/control',methods=['POST'])
def control():
    guard=api_guard()
    if guard:return guard
    action=str((request.get_json(silent=True) or {}).get('action',''))
    if action not in ('start','pause','resume','stop'):return jsonify(error='Invalid control action'),400
    if action=='stop':
        set_state('stopped','STOP button');
        with lock:
            p=job.get('proc')
            if p and p.poll() is None:p.terminate()
    elif action=='pause':set_state('paused','Paused by user')
    else:
        set_state('running','Control enabled (waiting for task)' if action=='start' else 'Control resumed')
    return jsonify(ok=True,mode=get_state()['mode'])

@app.route('/api/message',methods=['POST'])
def message():
    guard=api_guard()
    if guard:return guard
    text=str((request.get_json(silent=True) or {}).get('text','')).strip()[:500]
    if not text:return jsonify(error='Empty message'),400
    # Fixed local command catalogue: no shell execution from chat.
    t=text.lower()
    if any(x in t for x in ('status','статус','како оди','progress')):
        with lock:s=job['state'];log=list(job['log'])[-1:] or ['No tasks yet']
        return jsonify(reply=f'Status: {s}. '+log[0])
    if any(x in t for x in ('песна','music','музика')):
        return jsonify(reply='Стави MP3 во 02_MUSIC. Монтажата ќе се ограничи според должината на песната, ако може да се прочита.')
    if any(x in t for x in ('premiere','премиер','xml')):
        return jsonify(reply='По завршување увези TD_AI_v1_4_MAIN_AND_EXTRAS.xml во Premiere преку File > Import. Ако MP3 не се поврзе, користи VIDEO_ONLY XML и стави музика на A1.')
    if any(x in t for x in ('love','романт','бакнеж','преграт')):
        return jsonify(reply='v1.5 има локален визуелен AI каде што е достапен моделот; инаку е само техничка селекција, но сè уште не разбира сигурно бакнежи и романтични сцени. За тоа ќе додадеме вистински локален визуелен модел.')
    return jsonify(reply='Ова е локален контролен панел со ограничени команди, не генеративен AI разговор. За почеток избери проект и AUTO EDIT. Можеш да прашаш „статус“, „музика“ или „Premiere“.')

# Optional Ollama-backed visual assistant. It sees only a screenshot; suggested clicks
# are never executed automatically. All requests go to 127.0.0.1 on this PC.
def local_vision(img_bytes, question, strict_click=False):
    instruction = ("You are TD Production local screen assistant. Describe ONLY what is visibly on the screenshot. "
        "Do not claim to have clicked or to control Premiere. Respond as a short JSON object with keys "
        "description (Macedonian), x, y. If the user requests one click and a clear safe target is visible, "
        "set x and y to 0..1 fractional coordinates in the screenshot; otherwise use null. "
        "Never choose clicks to delete data, publish, install, pay, change security or save-overwrite files. "
        "The user must approve before any click. Do not output markdown. ")
    body={'model':'qwen2.5vl:3b','messages':[{'role':'system','content':instruction},
           {'role':'user','content':question,'images':[base64.b64encode(img_bytes).decode('ascii')]}],
          'stream':False,'format':'json','options':{'temperature':0.1, 'num_ctx':4096}}
    request_obj=urllib.request.Request('http://127.0.0.1:8798/api/chat', data=json.dumps(body).encode('utf-8'),
                                     headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(request_obj,timeout=150) as resp:
        data=json.load(resp)
    raw=data.get('message',{}).get('content','{}')
    try:parsed=json.loads(raw)
    except (ValueError,TypeError):parsed={'description':str(raw),'x':None,'y':None}
    return parsed

@app.route('/api/vision',methods=['POST'])
def vision():
    guard=api_guard()
    if guard:return guard
    data=request.get_json(silent=True) or {}
    question=str(data.get('question','Што гледаш на Premiere екранот?')).strip()[:350]
    if not question:return jsonify(error='Empty question'),400
    try:
        shot=grab()
        output=local_vision(shot,question)
        desc=str(output.get('description','Нема одговор.'))[:1500]
        x,y=output.get('x'),output.get('y')
        suggestion=None
        if isinstance(x,(float,int)) and isinstance(y,(float,int)) and 0<=x<=1 and 0<=y<=1:
            token=secrets.token_urlsafe(18)
            with lock:
                latest_suggestion.clear();latest_suggestion.update(token=token,x=float(x),y=float(y),time=time.time(),question=question,description=desc)
            suggestion={'token':token,'x':x,'y':y}
        return jsonify(reply=desc,suggestion=suggestion)
    except (urllib.error.URLError,ConnectionError) as e:
        return jsonify(error='MarK Vision is not running. Check local model setup: ('+str(e)+')'),503
    except Exception as e:
        return jsonify(error='Local vision error: '+str(e)),503

def record_feedback(did_accept, info):
    row={'timestamp':time.strftime('%Y-%m-%dT%H:%M:%S'),'approved':did_accept,
         'request':info.get('question','')[:350],'description':info.get('description','')[:1000]}
    path=BASE/'feedback_local.jsonl'
    with path.open('a',encoding='utf-8') as f:f.write(json.dumps(row,ensure_ascii=False)+'\n')

@app.route('/api/vision-action',methods=['POST'])
def vision_action():
    guard=api_guard()
    if guard:return guard
    d=request.get_json(silent=True) or {}
    token=str(d.get('token',''))
    accepted=bool(d.get('approved',False))
    with lock:
        if not token or token!=latest_suggestion.get('token') or time.time()-latest_suggestion.get('time',0)>180:
            return jsonify(error='Suggestion expired'),409
        suggestion=latest_suggestion.copy();latest_suggestion.clear()
    if not accepted:
        record_feedback(False,suggestion);return jsonify(ok=True,reply='Предлогот е одбиен. Корекцијата е зачувана локално.')
    if not CFG.get('allow_remote_control') or get_state()['mode']!='running':
        return jsonify(error='AI control is not running. Start AI and enable remote control on the PC.'),403
    try:
        import pyautogui
        width,height=pyautogui.size()
        mark_automated_input()
        pyautogui.click(int(suggestion['x']*width),int(suggestion['y']*height))
    except Exception as e:return jsonify(error='Could not click: '+str(e)),503
    record_feedback(True,suggestion)
    return jsonify(ok=True,reply='Кликот е извршен. Следната AI акција повторно бара потврда.')

# Highly privileged remote desktop control is disabled by default. Only enable locally.
@app.route('/api/set-control',methods=['POST'])
def set_control():
    guard=api_guard()
    if guard:return guard
    if not local_request():return jsonify(error='Allow this only from the computer browser (localhost)'),403
    CFG['allow_remote_control']=bool((request.get_json(silent=True) or {}).get('enabled',False))
    CONFIG.write_text(json.dumps(CFG,indent=2),encoding='utf-8')
    return jsonify(ok=True,enabled=CFG['allow_remote_control'])

@app.route('/api/import-premiere',methods=['POST'])
def import_premiere():
    guard=api_guard()
    if guard:return guard
    if not CFG.get('allow_remote_control') or get_state()['mode']!='running':
        return jsonify(error='Enable remote control locally on PC, then press START AI CONTROL.'),403
    with lock:project=job.get('project','')
    if not project:return jsonify(error='First create a montage with AUTO EDIT WEDDING'),400
    with lock: exported=job.get('export_xml','')
    xml=Path(exported) if exported else None
    if not xml or not xml.is_file():return jsonify(error='No XML export yet'),404
    try:
        import pyautogui as pg
        import pygetwindow as gw
        windows=[w for w in gw.getWindowsWithTitle('Premiere') if w.title and 'premiere' in w.title.lower()]
        if not windows:raise RuntimeError('Open Adobe Premiere Pro and a project first.')
        windows[0].activate();time.sleep(.65)
        if get_state()['mode']!='running':raise RuntimeError('AI paused; import cancelled.')
        mark_automated_input();pg.hotkey('ctrl','i');time.sleep(1.1)
        if get_state()['mode']!='running':raise RuntimeError('AI paused; import cancelled.')
        mark_automated_input();pg.write(str(xml),.001);pg.press('enter')
        return jsonify(ok=True,reply='Premiere import dialog received XML path. Check the Premiere window for import/link prompts.')
    except Exception as e:return jsonify(error='Premiere macro failed: '+str(e)),503

@app.route('/api/remote',methods=['POST'])
def remote():
    guard=api_guard()
    if guard:return guard
    if not CFG.get('allow_remote_control') or get_state()['mode']!='running':return jsonify(error='AI control must be RUNNING and remote control enabled on the computer.'),403
    data=request.get_json(silent=True) or {}
    try:
        import pyautogui
        action=data.get('action')
        if action=='click':
            x=float(data['x']);y=float(data['y'])
            if not (0<=x<=1 and 0<=y<=1):raise ValueError('out of bounds')
            mark_automated_input()
            width,height=pyautogui.size();pyautogui.click(int(x*width),int(y*height))
        elif action=='keypress':
            key=data.get('key');allowed={'space','esc','left','right','up','down'}
            if key not in allowed:raise ValueError('key not allowed')
            mark_automated_input()
            pyautogui.press(key)
        else:raise ValueError('invalid action')
    except Exception as e:return jsonify(error=str(e)),400
    return jsonify(ok=True)


# === TD ADOBE BRIDGE v1.6: explicit, safe Windows actions ===
# One named command per user click; never runs a free-form shell command sent by phone.
def premiere_path():
    if os.name != 'nt':return None
    roots=[Path(x)/'Adobe' for k in ('ProgramFiles','ProgramW6432') if (x:=os.environ.get(k))]
    for root in roots:
        if root.is_dir():
            for candidate in sorted(root.glob('Adobe Premiere Pro *'),reverse=True):
                e=candidate/'Adobe Premiere Pro.exe'
                if e.exists():return e
    return None

def adobe_window(kind):
    import pygetwindow as gw
    names={'premiere':('adobe premiere pro','premiere pro'),'lightroom':('lightroom classic','lightroom')}
    for w in gw.getAllWindows():
        title=(w.title or '').lower()
        if any(k in title for k in names[kind]):
            return w
    return None

def adobe_activate(kind):
    win=adobe_window(kind)
    if not win:raise RuntimeError('Open '+kind+' and its project/catalog first.')
    try:
        if win.isMinimized:win.restore()
        win.activate()
    except Exception as exc:
        raise RuntimeError('Could not activate '+kind+': '+str(exc))
    time.sleep(.45)
    if get_state().get('mode')!='running':raise RuntimeError('AI paused/stopped; action cancelled')
    return win

@app.route('/api/adobe/status')
def adobe_status():
    g=api_guard()
    if g:return g
    if os.name!='nt':return jsonify(premiere=False,lightroom=False,platform='other')
    try:
        import pygetwindow as gw
        names=[(w.title or '').lower() for w in gw.getAllWindows()]
    except Exception:names=[]
    return jsonify(premiere=any('premiere pro' in s for s in names),lightroom=any('lightroom' in s for s in names),
                   control=get_state().get('mode','stopped'),permission=CFG.get('allow_remote_control',False))

@app.route('/api/adobe/action',methods=['POST'])
def adobe_action():
    g=api_guard()
    if g:return g
    d=request.get_json(silent=True) or {}
    kind=str(d.get('app','')).lower()
    action=str(d.get('action','')).lower()
    if kind not in ('premiere','lightroom'):return jsonify(error='Unknown Adobe application'),400
    ALLOWED={'premiere':{'open','play_pause','import_xml','move_left','move_right'},
             'lightroom':{'open','library','develop','next_photo','prev_photo','pick','reject','unflag'}}
    if action not in ALLOWED[kind]:return jsonify(error='Action not supported'),400
    # Remote execution requires explicit opt-in on PC; PIN alone is not enough.
    if not local_request() and not CFG.get('allow_remote_control'):
        return jsonify(error='Allow remote control from the PC first.'),403
    if os.name!='nt':return jsonify(error='Windows only'),501
    if action=='open':
        exe=premiere_path() if kind=='premiere' else find_lightroom_classic()
        if not exe:return jsonify(error=kind+' executable not found; open it manually.'),404
        try:
            subprocess.Popen([str(exe)],cwd=str(exe.parent))
            return jsonify(ok=True,reply='Opening '+kind+'; wait for its project to load.')
        except OSError as e:return jsonify(error=str(e)),503
    if not CFG.get('allow_remote_control') or get_state().get('mode')!='running':
        return jsonify(error='Enable remote control on PC and press START AI CONTROL before keyboard operations.'),403
    try:
        import pyautogui as pg
        adobe_activate(kind)
        keys={'premiere':{'play_pause':'space','move_left':'left','move_right':'right'},
              'lightroom':{'library':'g','develop':'d','next_photo':'right','prev_photo':'left','pick':'p','reject':'x','unflag':'u'}}
        if action=='import_xml':
            with lock: exported=job.get('export_xml','')
            xml=Path(exported) if exported else None
            if not xml or not xml.is_file():return jsonify(error='First finish AUTO EDIT WEDDING so XML exists.'),404
            mark_automated_input();pg.hotkey('ctrl','i');time.sleep(1.2)
            if get_state().get('mode')!='running':return jsonify(error='Cancelled before import'),409
            pg.write(str(xml),interval=.001);pg.press('enter')
            reply='XML import sent to Premiere. Check for link-media dialogs.'
        else:
            mark_automated_input();pg.press(keys[kind][action]);reply=kind+': '+action+' key sent'
        with lock:job['log'].append('ADOBE ACTION: '+kind+' '+action)
        return jsonify(ok=True,reply=reply)
    except Exception as exc:
        return jsonify(error='Adobe control failed: '+str(exc)),503

# TD AI Director v1.7 — additional one-click local workflow
from project_bridge import install_project_bridge
install_project_bridge(globals())
from director_api import install_director_routes
install_director_routes(globals())
# TD_STYLE_LEARNING_V2_0
from td_learning_routes import install_learning_routes
install_learning_routes(globals())

# TD AI Desktop Operator v1.8 safe test
from operator_v18 import install_operator
install_operator(globals())

# TD_SIMPLE_WEDDING_V21
from td_studio_panel import install_studio_panel
install_studio_panel(globals())

from td_chat_addon import register as td_chat_register
td_chat_register(app)
from mark_core.routes import install_routes as mark_install_routes
mark_install_routes(app, authed, local_request)
from mark_core.runtime import start_services as mark_start_services
mark_start_services()

if __name__=='__main__':
    print('\n================ TD AI LOCAL v1.7.3 DESKTOP ================',flush=True)
    print('COMPUTER: http://127.0.0.1:'+str(PORT),flush=True)
    print('PHONE (same Wi-Fi): http://'+ip()+':'+str(PORT),flush=True)
    print('PIN:',CFG['pin'],flush=True)
    print('No cloud; restrict network to trusted LAN only.',flush=True)
    set_state('stopped','Server started; waiting for START')
    def on_hotkey_pause_resume():
        # Do not start a stopped controller from the keyboard accidentally.
        mode = get_state()['mode']
        if mode == 'running':
            set_state('paused', 'F9: paused from keyboard')
            print('AI PAUSED: F9', flush=True)
        elif mode == 'paused':
            set_state('running', 'F9: resumed from keyboard')
            print('AI RESUMED: F9', flush=True)

    def on_hotkey_stop():
        set_state('stopped', 'F10: emergency stop from keyboard')
        with lock:
            proc = job.get('proc')
            if proc is not None and proc.poll() is None:
                proc.terminate()
                job['log'].append('Emergency stop from F10: process termination requested.')
        print('AI EMERGENCY STOP: F10', flush=True)

    attach_global_hotkeys(on_hotkey_pause_resume, on_hotkey_stop)
    print('=================================================\n',flush=True)
    if os.getenv('TD_AI_DESKTOP_MODE') != '1':
        threading.Timer(1.25,lambda:webbrowser.open('http://127.0.0.1:'+str(PORT))).start()
    app.run(host='0.0.0.0',port=PORT,debug=False,threaded=True,use_reloader=False)
