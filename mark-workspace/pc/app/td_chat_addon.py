"""TD AI Studio PRO v0.4 — explicit chat-started wedding jobs with verified status.
Preserves existing td_chat_data/memory.sqlite3 and v0.3 photo/spot workflows.
Never claims that an Ollama response is an executed editing operation.
"""
from __future__ import annotations
import json, os, re, sqlite3, threading, urllib.error, urllib.request
from datetime import datetime
from pathlib import Path
from flask import Blueprint, jsonify, render_template_string, request, session, redirect, current_app, make_response

import td_photo_pro as photo
import td_pro_video as video

bp=Blueprint('td_chat_addon', __name__)
BASE=Path(__file__).resolve().parent
DATA=BASE/'td_chat_data';DATA.mkdir(exist_ok=True)
DB=DATA/'memory.sqlite3'
UI=(BASE/'td_pro_ui.html')
INIT_LOCK=threading.Lock()

def db():
    conn=sqlite3.connect(str(DB),timeout=15)
    conn.execute('PRAGMA busy_timeout=15000')
    with INIT_LOCK:
        conn.executescript('''
          CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY,project TEXT NOT NULL,role TEXT NOT NULL,content TEXT NOT NULL,created TEXT DEFAULT CURRENT_TIMESTAMP);
          CREATE INDEX IF NOT EXISTS ix_messages_project ON messages(project,id);
          CREATE TABLE IF NOT EXISTS memories(id INTEGER PRIMARY KEY,project TEXT NOT NULL,scope TEXT NOT NULL,kind TEXT NOT NULL,content TEXT NOT NULL,created TEXT DEFAULT CURRENT_TIMESTAMP);
          CREATE INDEX IF NOT EXISTS ix_memories_scope ON memories(project,scope);
          CREATE TABLE IF NOT EXISTS feedback(id INTEGER PRIMARY KEY,project TEXT NOT NULL,prompt TEXT NOT NULL,preferred_answer TEXT NOT NULL,created TEXT DEFAULT CURRENT_TIMESTAMP);
          CREATE TABLE IF NOT EXISTS actions(id INTEGER PRIMARY KEY,project TEXT NOT NULL,action TEXT NOT NULL,status TEXT NOT NULL,detail TEXT,created TEXT DEFAULT CURRENT_TIMESTAMP);
          CREATE TABLE IF NOT EXISTS proposals(id INTEGER PRIMARY KEY,project TEXT NOT NULL,description TEXT NOT NULL,status TEXT DEFAULT 'suggested',created TEXT DEFAULT CURRENT_TIMESTAMP);
          CREATE TABLE IF NOT EXISTS project_sources(project TEXT PRIMARY KEY,folder TEXT NOT NULL,updated TEXT DEFAULT CURRENT_TIMESTAMP);
        ''')
    return conn

def gate(post=False):
    if not session.get('login'):return jsonify(error='Најави се со PIN'),401
    if request.remote_addr not in ('127.0.0.1','::1'):return jsonify(error='PRO Assistant дозволен само од овој компјутер'),403
    if post and request.headers.get('X-TD-Client')!='TD-v1.4':return jsonify(error='Недостига TD ознака'),403
    return None

def project_name(data):return str(data.get('project','Општо')).strip()[:100] or 'Општо'

def record_action(project,action,status,detail=''):
    with db() as c:c.execute('INSERT INTO actions(project,action,status,detail) VALUES(?,?,?,?)',(project,action,status,str(detail)[:2000]))

def relevant_older_messages(connection, project, prompt):
    """Simple local recall across earlier messages; no cloud or vector server.
    Only fetch messages from the SAME project and keep the full history on disk.
    """
    terms={t.casefold() for t in re.findall(r"[\w\u0400-\u04ff]{4,}",prompt)
           if not t.isdigit() and len(t)>3}
    if not terms:return []
    candidates=connection.execute(
        'SELECT content FROM messages WHERE project=? ORDER BY id DESC LIMIT 500 OFFSET 30',
        (project,)).fetchall()
    ranked=[]
    for pos,(content,) in enumerate(candidates):
        present=set(re.findall(r"[\w\u0400-\u04ff]{4,}",content.casefold()))
        overlap=terms & present
        score=len(overlap)+sum(1 for word in overlap if len(word)>=8)
        if score>=2:ranked.append((score,-pos,content[:700]))
    return [item[2] for item in sorted(ranked,reverse=True)[:5]]

def model_names():
    try:
        with urllib.request.urlopen('http://127.0.0.1:8798/api/tags',timeout=3) as r:body=json.load(r)
        return [m['name'] for m in body.get('models',[]) if m.get('name')]
    except Exception:return []

def choose_model(requested,installed):
    if requested and requested in installed:return requested
    for wanted in ('llama3.1:8b','qwen2.5:7b','qwen3:8b'):
        if wanted in installed:return wanted
    return next((x for x in installed if 'embed' not in x.lower()),None)

@bp.route('/td-assistant')
def page():
    g=gate()
    if g:
        return redirect('/login') if not session.get('login') else g
    return render_template_string(UI.read_text(encoding='utf-8'))

@bp.route('/api/td-assistant/models')
def models():
    g=gate()
    if g:return g
    installed=model_names()
    return jsonify(models=installed,selected=choose_model('',installed),online=bool(installed))

@bp.route('/api/td-assistant/history')
def history():
    g=gate()
    if g:return g
    project=project_name(request.args)
    with db() as c:
        rows=c.execute('SELECT role,content,created FROM messages WHERE project=? ORDER BY id DESC LIMIT 120',(project,)).fetchall()
    return jsonify(messages=[{'role':r,'content':txt,'created':ts} for r,txt,ts in reversed(rows)])

@bp.route('/api/td-assistant/memories',methods=['GET','POST','DELETE'])
def memories():
    g=gate(request.method!='GET')
    if g:return g
    data=request.get_json(silent=True) or {} if request.method!='GET' else request.args
    project=project_name(data)
    if request.method=='POST':
        content=str(data.get('content','')).strip()[:1600]
        scope=data.get('scope','project')
        if scope not in ('project','global'):return jsonify(error='Невалиден опсег'),400
        if not content:return jsonify(error='Празно правило'),400
        kind='explicit'
        with db() as c:c.execute('INSERT INTO memories(project,scope,kind,content) VALUES(?,?,?,?)',(project,scope,kind,content))
    if request.method=='DELETE':
        try:id_=int(data.get('id'))
        except (ValueError,TypeError):return jsonify(error='Невалидно правило'),400
        with db() as c:c.execute('DELETE FROM memories WHERE id=? AND ((project=? AND scope=\'project\') OR scope=\'global\')',(id_,project))
    with db() as c:
        rows=c.execute('SELECT id,project,scope,content,created FROM memories WHERE scope=\'global\' OR (scope=\'project\' AND project=?) ORDER BY id DESC LIMIT 200',(project,)).fetchall()
    return jsonify(memories=[dict(id=r[0],project=r[1],scope=r[2],content=r[3],created=r[4]) for r in rows])

@bp.route('/api/td-assistant/memory/remove',methods=['POST'])
def remove_memory():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {};project=project_name(data)
    try:idx=int(data.get('id'))
    except (ValueError,TypeError):return jsonify(error='Невалиден ID'),400
    with db() as c:
        c.execute("DELETE FROM memories WHERE id=? AND ((project=? AND scope='project') OR scope='global')",(idx,project))
    return jsonify(ok=True)

@bp.route('/api/td-assistant/backup',methods=['POST'])
def backup():
    g=gate(True)
    if g:return g
    target=DATA/'backups'/('memory_'+datetime.now().strftime('%Y%m%d_%H%M%S_%f')+'.sqlite3')
    target.parent.mkdir(parents=True,exist_ok=True)
    with db() as source, sqlite3.connect(str(target)) as dest:source.backup(dest)
    return jsonify(ok=True,path=str(target))

@bp.route('/api/td-assistant/feedback',methods=['POST'])
def feedback():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {};project=project_name(data)
    prompt=str(data.get('prompt',''))[:2500];better=str(data.get('better','')).strip()[:2500]
    if not better:return jsonify(error='Внеси ја исправката'),400
    with db() as c:
        c.execute('INSERT INTO feedback(project,prompt,preferred_answer) VALUES(?,?,?)',(project,prompt,better))
        c.execute('INSERT INTO memories(project,scope,kind,content) VALUES(?,?,?,?)',(project,'project','correction',better))
    return jsonify(ok=True,reply='Исправката е зачувана како проектно правило. Не е дообучен моделот.')

def source_for_project(project):
    with db() as c:
        row=c.execute('SELECT folder FROM project_sources WHERE project=?',(project,)).fetchone()
    return row[0] if row else ''

@bp.route('/api/td-assistant/project-source',methods=['GET','POST'])
def project_source():
    g=gate(request.method=='POST')
    if g:return g
    data=(request.get_json(silent=True) or {}) if request.method=='POST' else request.args
    project=project_name(data)
    if request.method=='POST':
        raw=str(data.get('folder','')).strip()
        folder=Path(raw).expanduser()
        if not raw or not folder.is_absolute() or not folder.is_dir():
            return jsonify(error='Избери вистинска папка со видеа за овој проект.'),400
        folder=str(folder.resolve())
        if folder==str(Path(folder).anchor):return jsonify(error='Не избирај цел диск, туку папката на свадбата.'),400
        with db() as c:c.execute('INSERT INTO project_sources(project,folder,updated) VALUES(?,?,CURRENT_TIMESTAMP) ON CONFLICT(project) DO UPDATE SET folder=excluded.folder,updated=CURRENT_TIMESTAMP',(project,folder))
    return jsonify(ok=True,project=project,folder=source_for_project(project))

def live_job():
    """Use existing authenticated Studio status, not a fabricated LLM estimate."""
    fn=current_app.view_functions.get('info')
    if fn is None:raise RuntimeError('TD AI Studio status /api/info is unavailable')
    resp=make_response(fn())
    if resp.status_code!=200:raise RuntimeError('Status endpoint returned '+str(resp.status_code))
    data=resp.get_json(silent=True)
    if not isinstance(data,dict):raise RuntimeError('Status endpoint has no JSON')
    return data

def job_summary(job,project):
    state=str(job.get('state','idle'))
    if state=='running':
        phase=str(job.get('stage','?'))
        pct=job.get('wedding_progress') if phase=='wedding' else job.get('progress',0)
        try:pct=max(0,min(99,int(pct or 0)))
        except (TypeError,ValueError):pct=0
        last=(job.get('log') or [''])[-1]
        return f'РЕАЛЕН СТАТУС: работи · {job.get("project") or "неименувано"} · чекор {phase} · {pct}%\n{last}'
    if state=='finished':
        file=job.get('project_file') or job.get('export_xml') or 'Погледни го главниот панел.'
        return f'РЕАЛЕН СТАТУС: обработката завршила · Premiere статус: {job.get("project_status","непознат")}.\nРезултат: {file}\nПровери ја секвенцата пред финален извоз.'
    if state=='failed':return 'РЕАЛЕН СТАТУС: ГРЕШКА. '+str(job.get('wedding_error') or (job.get('log') or [''])[-1] or 'Провери го логот.')
    if state=='stopped':return 'РЕАЛЕН СТАТУС: задачата е запрена.'
    return 'РЕАЛЕН СТАТУС: не е стартувана видео-обработка. Избери ја папката на проектот и кажи „почни“.'

@bp.route('/api/td-assistant/video/status')
def video_status():
    g=gate()
    if g:return g
    try:live=live_job()
    except Exception as exc:return jsonify(error=str(exc)),503
    project=project_name(request.args)
    return jsonify(ok=True,state=live.get('state','idle'),stage=live.get('stage','idle'),
                   project=live.get('project',''),progress=live.get('wedding_progress') if live.get('stage')=='wedding' else live.get('progress',0),
                   project_status=live.get('project_status',''),project_file=live.get('project_file',''),
                   export_xml=live.get('export_xml',''),error=live.get('wedding_error',''),
                   last_log=(live.get('log') or [''])[-1],reply=job_summary(live,project))

def invoke_wedding_start(project,folder):
    """Call trusted v2.1 wedding route with the user's authorized local request.
    Do not duplicate editor implementation or claim Premiere has rendered a video.
    """
    fn=current_app.view_functions.get('wedding_start')
    if fn is None:return False, 'Не е инсталирана функцијата /api/wedding/start од TD Studio v2.1.'
    payload=dict(project_name=project,folder=folder,seconds=60,quality='deep',model='qwen2.5vl:3b',
                 audio_sync=True,dissolve=True,auto_music=True)
    with current_app.test_request_context('/api/wedding/start',method='POST',json=payload,
            headers={'X-TD-Client':'TD-v1.4'},environ_base={'REMOTE_ADDR':'127.0.0.1'}):
        session['login']=True
        response=make_response(fn())
        body=response.get_json(silent=True) or {}
        if response.status_code>=400 or not body.get('ok'):
            return False,str(body.get('error') or 'Почетокот е одбиен: HTTP '+str(response.status_code))
    return True,'Командата за цела свадба е прифатена од вистинскиот видео-мотор. Го следам статусот; ова НЕ значи дека Premiere веќе ја направил монтажата.'

STATUS_REQUEST=re.compile(r'(?iu)\b(?:почна|почна\?|pocna|pocnal|started|raboti|работи|status|статус|напредок|napredok|почнува|pocnuva|заглави)\b|врти во круг|до ка[јд]е')
START_REQUEST=re.compile(r'(?iu)\b(?:почни|започни|стартувај|стартува|pocni|pochni|zapocni|start|startuj|startuvaj|старт)\b')

def handle_explicit_command(project,prompt,folder_input=''):
    """Only deterministic user start/status commands can perform work.
    Never execute from LLM text or an inferred conversational suggestion.
    """
    if STATUS_REQUEST.search(prompt):
        return job_summary(live_job(),project)
    if not START_REQUEST.search(prompt):return None
    live=live_job()
    if live.get('state')=='running':return 'Веќе има активна задача; не стартувам дупло.\n'+job_summary(live,project)
    if project.casefold()=='општо':return 'Прво внеси име на свадбата во „Активен проект“, па кажи „почни“.'
    folder=str(folder_input or source_for_project(project)).strip()
    if not folder:
        return 'Не е избрана папката со видеата за '+project+'. Притисни 📁 кај „Папка со видеа“ и потоа напиши „почни“. '
    src=Path(folder).expanduser()
    if not src.is_absolute() or not src.is_dir():
        return 'НЕ ПОЧНАВ: папката не постои на компјутерот: '+folder
    ok,answer=invoke_wedding_start(project,str(src.resolve()))
    record_action(project,'wedding_start','accepted' if ok else 'failed',answer)
    return ('СТАРТ ПРИФАТЕН. ' if ok else 'НЕ ПОЧНАВ. ')+answer

@bp.route('/api/td-assistant/chat',methods=['POST'])
def chat():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {};project=project_name(data)
    prompt=str(data.get('text','')).strip()[:6000]
    if not prompt:return jsonify(error='Празна порака'),400
    # An explicit natural-language save command is supported even when Ollama
    # is off. Never infer project/global memories from ordinary conversations.
    memory_command=re.match(r'^(?:запамти|zapamti|remember)\s*[:：-]\s*(.{3,})$',prompt,re.IGNORECASE|re.DOTALL)
    if memory_command:
        content=memory_command.group(1).strip()[:1600]
        scope='global' if re.match(r'^(?:за сите проекти|за сите свадби|global)\b',content,re.IGNORECASE) else 'project'
        with db() as c:
            c.execute('INSERT INTO messages(project,role,content) VALUES(?,?,?)',(project,'user',prompt))
            c.execute('INSERT INTO memories(project,scope,kind,content) VALUES(?,?,?,?)',(project,scope,'explicit',content))
            answer='Запомнив како '+('општо правило.' if scope=='global' else 'правило за проектот '+project+'.')
            c.execute('INSERT INTO messages(project,role,content) VALUES(?,?,?)',(project,'assistant',answer))
        return jsonify(reply=answer,model='локална меморија')
    # Persist user message first, even if Ollama is offline.
    with db() as c:
        c.execute('INSERT INTO messages(project,role,content) VALUES(?,?,?)',(project,'user',prompt))
        memories_=c.execute('SELECT scope,content FROM memories WHERE scope=\'global\' OR (scope=\'project\' AND project=?) ORDER BY id DESC LIMIT 30',(project,)).fetchall()
        history_=c.execute('SELECT role,content FROM messages WHERE project=? ORDER BY id DESC LIMIT 30',(project,)).fetchall()
        corrections_=c.execute('SELECT preferred_answer FROM feedback WHERE project=? ORDER BY id DESC LIMIT 8',(project,)).fetchall()
        actions_=c.execute('SELECT action,status,detail FROM actions WHERE project=? ORDER BY id DESC LIMIT 12',(project,)).fetchall()
        recalled_=relevant_older_messages(c,project,prompt)
    # v0.4: user-authorized deterministic commands; unlike conversational Ollama,
    # they call the actual wedding route and return real start/errors/status.
    try:command_answer=handle_explicit_command(project,prompt,data.get('folder',''))
    except Exception as exc:command_answer='НЕ ПОЧНАВ: '+str(exc)[:300]
    if command_answer is not None:
        with db() as c:c.execute('INSERT INTO messages(project,role,content) VALUES(?,?,?)',(project,'assistant',command_answer))
        return jsonify(reply=command_answer,model='TD Studio — вистински статус')
    installed=model_names();model=choose_model(str(data.get('model','')),installed)
    if not model:return jsonify(error='MarK Engine не е подготвен — провери инсталација на моделот. Пораката е зачувана.'),503
    rules='\n'.join(f'[{scope}] {content}' for scope,content in reversed(memories_))
    fixes='\n'.join(f'- {r[0]}' for r in reversed(corrections_))
    activity='\n'.join(f'- {action}: {status} / {detail[:120]}' for action,status,detail in reversed(actions_))
    recalled='\n'.join('- '+m[:700] for m in recalled_)
    system=('Ти си локален MarK Engine / TD Production AI Studio PRO асистент. Одговарај на македонски јазик, практично и кратко. '
      'Не кажувај дека си извршил Premiere, Lightroom, преместување, инсталација или модификација без проверка. '
      'Само ако корисникот експлицитно напише „почни“, системската рута ќе стартува задача кога има валидна папка. '
      'Не тврди дека видео-обработка почнала или завршила врз основа на разговор. За статус користи „статус“. '
      'Не бриши оригинали, не извлекувај PIN, и не прави тајни ажурирања. '
      f'Активен проект: {project}.\nВажечки правила:\n{rules or "Нема."}\nПоследни корекции:\n{fixes or "Нема."}\nРелевантни делови од постари разговори:\n{recalled or "Нема."}\nЗапишани дејства (статусот е последниот забележан, не доказ за завршен процес):\n{activity or "Нема."}')
    messages=[{'role':'system','content':system}]+[{'role':role,'content':content} for role,content in reversed(history_)]
    payload={'model':model,'messages':messages,'stream':False,'options':{'temperature':0.4,'num_ctx':8192}}
    req=urllib.request.Request('http://127.0.0.1:8798/api/chat',data=json.dumps(payload,ensure_ascii=False).encode(),headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=240) as resp:answer=json.load(resp).get('message',{}).get('content','').strip()
    except Exception as e:return jsonify(error='MarK Engine одговорот не успеа. Пораката останува во базата. '+str(e)[:220]),503
    if not answer:return jsonify(error='Празен одговор од моделот'),503
    # Defensive final guard: Ollama is not an executor, even if old chat logs
    # contain confident but false completion messages.
    if re.search(r'(?iu)\b(?:почнав|започнав|стартував|извршив|ја\s+направив|pocnav|started\s+working)\b',answer):
        answer='Нема потврдено стартување во оваа порака. Барањето е зачувано во разговорот. Избери ја папката и напиши „почни“ за да ја активираш вистинската видео-задача.'
    with db() as c:c.execute('INSERT INTO messages(project,role,content) VALUES(?,?,?)',(project,'assistant',answer))
    return jsonify(reply=answer,model=model)

@bp.route('/api/td-pro/capabilities')
def capabilities():
    g=gate()
    if g:return g
    return jsonify(photo='technical_pro_preview_move_undo',video_spot='preflight_max300_deep_existing_director',chat_wedding_start='explicit_local_v21_route',chat_wedding_status='real_studio_state',
       learning='conversation_history_explicit_corrections',premiere='existing_bridge_only',
       lightroom='optional_plugin_requires_activation',auto_upgrade=False)

@bp.route('/api/td-pro/photo/scan',methods=['POST'])
def photo_scan():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {}
    try:jid=photo.scan(data.get('folder',''),data.get('strict',110),project_name(data))
    except Exception as exc:return jsonify(error=str(exc)),400
    record_action(project_name(data),'photo_scan','started',jid)
    return jsonify(ok=True,job_id=jid)

@bp.route('/api/td-pro/photo/status')
def photo_status():
    g=gate()
    if g:return g
    with photo.LOCK:return jsonify(photo.STATUS.copy())

@bp.route('/api/td-pro/photo/preview')
def photo_preview():
    g=gate()
    if g:return g
    try:
        job=photo.get_job(request.args.get('job_id',''))
        return jsonify(id=job['id'],project=job['project'],status=job['status'],source=job['source'],output=job['output'],
           counts=job['counts'],total=len(job['records']),sample=[{'file':r['representative'],'category':r['category'],'metrics':r['metrics']} for r in job['records'][:35]])
    except Exception as exc:return jsonify(error=str(exc)),400

@bp.route('/api/td-pro/photo/move',methods=['POST'])
def photo_move():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {}
    try:
        jid=str(data.get('job_id',''));job=photo.get_job(jid)
        if not data.get('confirm'):raise ValueError('Потврди MOVE за да се преместат оригиналите.')
        target=photo.commit(jid,bool(data.get('catalog_backup')))
    except Exception as exc:return jsonify(error=str(exc)),400
    record_action(job['project'],'photo_move','started',target)
    return jsonify(ok=True,output=target)

@bp.route('/api/td-pro/photo/undo',methods=['POST'])
def photo_undo():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {}
    try:
        jid=str(data.get('job_id',''));job=photo.get_job(jid)
        if data.get('confirm') is not True:raise ValueError('Потврди враќање.')
        photo.undo(jid)
    except Exception as exc:return jsonify(error=str(exc)),400
    record_action(job['project'],'photo_undo','started',jid)
    return jsonify(ok=True)

@bp.route('/api/td-pro/spot/prepare',methods=['POST'])
def spot_prepare():
    g=gate(True)
    if g:return g
    data=request.get_json(silent=True) or {}
    try:result=video.prepare_spot(data.get('folder',''))
    except Exception as exc:return jsonify(error=str(exc)),400
    record_action(project_name(data),'spot_prepare','staged',f"{result['included']} short; {result['excluded']} long")
    return jsonify(ok=True,**result)

@bp.route('/api/td-pro/photo/recent')
def photo_recent():
    g=gate()
    if g:return g
    return jsonify(jobs=photo.recent_jobs())

@bp.route('/api/td-assistant/actions')
def recent_actions():
    g=gate()
    if g:return g
    project=project_name(request.args)
    with db() as c:
        rows=c.execute('SELECT action,status,detail,created FROM actions WHERE project=? ORDER BY id DESC LIMIT 30',(project,)).fetchall()
    return jsonify(actions=[dict(action=r[0],status=r[1],detail=r[2],created=r[3]) for r in rows])

@bp.route('/api/td-assistant/proposals',methods=['GET','POST'])
def improvements():
    g=gate(request.method=='POST')
    if g:return g
    data=request.get_json(silent=True) or {} if request.method=='POST' else request.args
    project=project_name(data)
    if request.method=='POST':
        description=str(data.get('description','')).strip()[:1600]
        if not description:return jsonify(error='Напиши предлог за надградба'),400
        with db() as c:c.execute('INSERT INTO proposals(project,description) VALUES(?,?)',(project,description))
    with db() as c:
        rows=c.execute('SELECT id,description,status,created FROM proposals WHERE project=? ORDER BY id DESC LIMIT 30',(project,)).fetchall()
    return jsonify(proposals=[dict(id=r[0],description=r[1],status=r[2],created=r[3]) for r in rows])

def register(app):app.register_blueprint(bp)
