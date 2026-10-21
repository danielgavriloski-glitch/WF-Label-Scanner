"""MarK Assistant integrated as a Flask blueprint into original TD Studio (same server+PIN)."""
from __future__ import annotations
from flask import Blueprint,request,session,jsonify,redirect,send_from_directory
from pathlib import Path
import sqlite3,threading,datetime,json,re
from .runtime import health,completions
BASE=Path(__file__).resolve().parents[2]
DATA=BASE/'data'/'mark.sqlite3'
UI=Path(__file__).parent/'mark_ui.html'
bp=Blueprint('mark_full',__name__)
AUTH=None;LOCAL=None
DLOCK=threading.RLock()

def connect():
    DATA.parent.mkdir(parents=True,exist_ok=True)
    db=sqlite3.connect(str(DATA),timeout=30)
    db.execute('PRAGMA busy_timeout=10000')
    db.executescript("""CREATE TABLE IF NOT EXISTS messages(id INTEGER PRIMARY KEY,project TEXT,role TEXT,text TEXT,ts TEXT DEFAULT CURRENT_TIMESTAMP);
      CREATE TABLE IF NOT EXISTS memories(id INTEGER PRIMARY KEY,project TEXT,scope TEXT,text TEXT,ts TEXT DEFAULT CURRENT_TIMESTAMP);
      CREATE TABLE IF NOT EXISTS bookings(id TEXT PRIMARY KEY,data TEXT NOT NULL,updated TEXT DEFAULT CURRENT_TIMESTAMP);
      CREATE TABLE IF NOT EXISTS links(id TEXT PRIMARY KEY,state TEXT,updated TEXT DEFAULT CURRENT_TIMESTAMP);""")
    return db

def guard(post=False):
    if not AUTH():return jsonify(error='Login with your TD PIN first'),401
    if not LOCAL():return jsonify(error='MarK controls are available on the local computer only'),403
    if post and request.headers.get('X-TD-Client')!='TD-v1.4':return jsonify(error='Invalid request marker'),403
    if post:
        origin=request.headers.get('Origin','')
        # Protection from cross-site browser requests. No Origin is allowed for native callers.
        if origin and origin not in ('http://127.0.0.1:8765','http://localhost:8765'):return jsonify(error='Origin not allowed'),403
    return None

def who(data):return str(data.get('project','Општо')).strip()[:96] or 'Општо'

@bp.route('/mark')
def page():
    if not AUTH():return redirect('/login')
    if not LOCAL():return jsonify(error='MarK desktop-only'),403
    return UI.read_text('utf-8')

@bp.route('/mark/logo')
def logo():
    if not AUTH():return redirect('/login')
    return send_from_directory(str(BASE/'brand'),'MarK_Logo_Transparent_2K.png')

@bp.route('/api/mark/status')
def status():
    g=guard()
    if g:return g
    with DLOCK,connect() as c:
        count=c.execute('SELECT COUNT(*) FROM bookings').fetchone()[0]
    return jsonify(engine=health(),booking_imported=count,booking_live_sync=False,phone_paired=False,model='Qwen3-VL-4B-Instruct Q4_K_M',old_td_preserved=True,adobe_plugin_source_bundled=True)

@bp.route('/api/mark/history')
def history():
    g=guard()
    if g:return g
    project=who(request.args)
    with DLOCK,connect() as c: rows=c.execute('SELECT role,text,ts FROM messages WHERE project=? ORDER BY id DESC LIMIT 100',(project,)).fetchall()
    return jsonify(history=[{'role':r,'text':t,'ts':ts} for r,t,ts in reversed(rows)])

@bp.route('/api/mark/memory',methods=['GET','POST'])
def memory():
    g=guard(request.method=='POST')
    if g:return g
    data=request.get_json(silent=True) if request.method=='POST' else request.args
    data=data or {};project=who(data)
    if request.method=='POST':
        note=str(data.get('text','')).strip()[:1600];scope=data.get('scope','project')
        if not note:return jsonify(error='Empty memory'),400
        if scope not in ('project','global'):return jsonify(error='Invalid scope'),400
        with DLOCK,connect() as c:c.execute('INSERT INTO memories(project,scope,text) VALUES (?,?,?)',(project,scope,note))
    with DLOCK,connect() as c:rows=c.execute("SELECT scope,text,ts FROM memories WHERE scope='global' OR project=? ORDER BY id DESC LIMIT 75",(project,)).fetchall()
    return jsonify(memories=[{'scope':s,'text':t,'ts':ts} for s,t,ts in rows])

@bp.route('/api/mark/bookings',methods=['GET','POST'])
def bookings():
    g=guard(request.method=='POST')
    if g:return g
    if request.method=='POST':
        data=request.get_json(silent=True) or {}
        rows=data.get('bookings')
        if not isinstance(rows,list) or len(rows)>2000:return jsonify(error='Expected bookings JSON array (max 2000)'),400
        with DLOCK,connect() as c:
            for i,booking in enumerate(rows):
                if not isinstance(booking,dict):continue
                ref=str(booking.get('id') or booking.get('bookingId') or '').strip()[:120]
                if not ref:continue
                c.execute('INSERT INTO bookings(id,data) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data,updated=CURRENT_TIMESTAMP',(ref,json.dumps(booking,ensure_ascii=False)))
        return jsonify(imported=len(rows),live_sync=False)
    with DLOCK,connect() as c: rows=c.execute('SELECT id,data FROM bookings ORDER BY updated DESC LIMIT 200').fetchall()
    return jsonify(bookings=[{'id':ref,**json.loads(text)} for ref,text in rows],live_sync=False,reason='TD Booking Android code not supplied; local import works')

@bp.route('/api/mark/chat',methods=['POST'])
def chat():
    g=guard(True)
    if g:return g
    data=request.get_json(silent=True) or {}
    project=who(data);prompt=str(data.get('text','')).strip()[:7000]
    if not prompt:return jsonify(error='Напиши порака'),400
    with DLOCK,connect() as c:
        c.execute('INSERT INTO messages(project,role,text) VALUES (?,?,?)',(project,'user',prompt))
        older=c.execute('SELECT role,text FROM messages WHERE project=? ORDER BY id DESC LIMIT 14',(project,)).fetchall()
        memories=c.execute("SELECT text FROM memories WHERE scope='global' OR project=? ORDER BY id DESC LIMIT 20",(project,)).fetchall()
        bk=c.execute('SELECT COUNT(*) FROM bookings').fetchone()[0]
    m=re.match(r'^(?:запамти|zapamti|remember)\s*[:：-]?\s*(.{3,})$',prompt,re.I|re.S)
    if m:
        note=m.group(1).strip()[:1600]
        with DLOCK,connect() as c:c.execute('INSERT INTO memories(project,scope,text) VALUES (?,?,?)',(project,'project',note))
        answer='Запамтив за проектот „'+project+'“: '+note
    elif any(x in prompt.lower() for x in ('booking','букинг','резервац')) and not health()['ready']:
        answer=f'Во MarK се локално увезени {bk} резервации. Android синхронизацијата не е вклучена; потребен е изворниот TD Booking проект.'
    elif not health()['ready']:
        answer='MarK разговорот е вграден во TD Studio, но локалниот модел уште не е стартуван. Инсталирај го преку INSTALL_ALL_IN_ONE.bat и провери ја статус-линијата. Правилата и разговорите се зачувуваат локално.'
    else:
        system=("Ти си MarK, локален AI асистент интегриран во TD AI Studio PRO. Комуницирај на македонски, разбирливо, без измислени тврдења. "
         "Имаш податоци само за настаните што се потврдени преку вистински APIs. Не тврди дека си исполнил Premiere/Lightroom/Booking задача ако немаш потврда. "
         "Не бриши, префрлај, исплаќај и не менувај фајлови во разговор. По корисничка потврда упати на постојните TD контроли. "
         "Сите читливи видео-оригинали влегуваат во долгото видео; за кратките спотови не се користат клипови над 5 минути. "
         "По секој спот следува долго поглавје, по ред зет/невеста/црква/ресторан. "
         f"Проект: {project}. Увезени Booking записи: {bk}, нема потврдена двонасочна синхронизација. "
         "Правила: "+' | '.join(x[0] for x in reversed(memories)))
        msgs=[{'role':'system','content':system}]+[{'role':r,'content':t[:2200]} for r,t in reversed(older)]
        try:answer=completions(msgs,temperature=.3,max_tokens=800)
        except Exception as exc:answer='Грешка на локалниот MarK модел: '+str(exc)[:240]
    with DLOCK,connect() as c:c.execute('INSERT INTO messages(project,role,text) VALUES (?,?,?)',(project,'assistant',answer))
    return jsonify(reply=answer,project=project,model_ready=health()['ready'])

def install_routes(app,authed,local_request):
    global AUTH,LOCAL
    AUTH=authed;LOCAL=local_request
    with connect():pass
    app.register_blueprint(bp)
    from .desktop_routes_addon import install as install_desktop
    install_desktop(app,authed,local_request)
    from .booking_sync import install as install_sync
    install_sync(app,authed,local_request)
    from .booking_replica import install as install_replica
    install_replica(app,authed,local_request)
    from .unified import install as install_unified
    install_unified(app,authed,local_request)
    from .wedding_media_v1 import install as install_media
    install_media(app,authed,local_request)
    from .premiere_native import register as install_premiere
    install_premiere(app,authed,local_request)
    from .workspace_ui import register as install_workspace
    install_workspace(app,authed,local_request)
