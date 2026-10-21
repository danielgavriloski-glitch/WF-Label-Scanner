"""MarK integrated local workspace v1.7.0.

One active project shared by MarK modules, backed by a separate metadata database.
Status/inventory only: media AI processing remains owned by installed TD Studio.
The original TD Studio and TD Booking data are never rewritten here.
"""
from __future__ import annotations
import json
import sqlite3
import threading
from pathlib import Path
from flask import Blueprint, request, jsonify, Response, current_app

ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / 'data' / 'unified_workspace_v1.sqlite3'
UI_JS = Path(__file__).with_name('unified_ui.js')
LOCK = threading.RLock()
bp = Blueprint('mark_unified_v160', __name__)
_AUTH = None
_LOCAL = None


def _guard(write=False):
    if _AUTH is None or not _AUTH(): return jsonify(error='MarK PIN најава е потребна'), 401
    if _LOCAL is None or not _LOCAL(): return jsonify(error='Дозволено само на MarK компјутерот'), 403
    if write:
        if request.headers.get('X-TD-Client')!='TD-v1.4':
            return jsonify(error='Невалиден локален клиент'), 403
        origin=request.headers.get('Origin','')
        if origin and origin not in ('http://127.0.0.1:8765','http://localhost:8765'):
            return jsonify(error='Невалиден извор'), 403
    return None


def _db():
    DB.parent.mkdir(parents=True, exist_ok=True)
    c=sqlite3.connect(str(DB),timeout=10)
    c.execute('PRAGMA busy_timeout=10000')
    c.execute('CREATE TABLE IF NOT EXISTS workspace (key TEXT PRIMARY KEY, value TEXT NOT NULL)')
    return c


def _projects():
    from .routes import connect, DLOCK
    with DLOCK, connect() as c:
        c.execute('CREATE TABLE IF NOT EXISTS desktop_projects(name TEXT PRIMARY KEY, created TEXT DEFAULT CURRENT_TIMESTAMP)')
        names=c.execute("SELECT project FROM messages UNION SELECT project FROM memories UNION SELECT name FROM desktop_projects").fetchall()
    all_names=['Општо']+[x[0] for x in names if x[0] and x[0]!='Општо']
    try:
        from .booking_sync import _db as sync_db
        with sync_db() as c:
            entries=c.execute('SELECT project_name FROM projects').fetchall()
        for (name,) in entries:
            if name not in all_names:all_names.append(name)
    except (ImportError,sqlite3.Error):pass
    return all_names[:500]


def _selected():
    with LOCK,_db() as c:
        r=c.execute("SELECT value FROM workspace WHERE key='active_project'").fetchone()
    name=r[0] if r else 'Општо'
    return name if name in _projects() else 'Општо'


def _project_data(name):
    output={'name':name,'found':False,'booking':None,'path':None,'status':'NO_HDD_PROJECT',
            'media':{'videos':0,'photos':0,'sources':[]},'folderLabels':[]}
    if name=='Општо':return output
    try:
        from .booking_sync import _db as sync_db
        with sync_db() as c:
            p=c.execute('SELECT booking_id,path,status FROM projects WHERE project_name=?',(name,)).fetchone()
            if not p:return output
            b=c.execute('SELECT json FROM bookings WHERE id=?',(p[0],)).fetchone()
        output.update(found=True,booking=json.loads(b[0]) if b else None,path=p[1],status=p[2])
        manifest=Path(p[1])/'00_ADMIN'/'BOOKING'/'project_manifest.json'
        if manifest.is_file():
            data=json.loads(manifest.read_text(encoding='utf8'))
            if data.get('bookingId')==p[0]:
                sources=data.get('sources') or {}
                output['media']={k:sources.get(k) for k in ('videos','photos','sources')}
                output['folderLabels']=data.get('folders') or []
                output['mediaAnalysis']=data.get('mediaAnalysis','FILE_INVENTORY_ONLY')
    except (OSError,ValueError,sqlite3.Error):
        output['status']='PROJECT_METADATA_UNAVAILABLE'
    return output


@bp.get('/api/mark/unified/status')
def status():
    g=_guard()
    if g:return g
    selected=_selected()
    rules=current_app.url_map
    routes={k:any(r.rule==k for r in rules.iter_rules()) for k in
       ('/api/mark/sync/status','/api/info','/api/start','/api/lightroom/open','/api/td-photo-open-v04')}
    return jsonify(version='1.7.0',selected=selected,projects=_projects(),workspace=_project_data(selected),
         nativeTD='EMBEDDED_SAME_LOCAL_APPLICATION',endpoints=routes,
         limitations=['Фото AI и Premiere Director се извршуваат преку веќе инсталираниот TD Studio.',
                      'Медија прегледот проверува фајлови, не потврдува визуелна AI анализа.',
                      'APK поврзувањето бара компајлирана и потпишана TD Booking верзија.'])


@bp.post('/api/mark/unified/select')
def select():
    g=_guard(write=True)
    if g:return g
    obj=request.get_json(silent=True) or {}
    name=str(obj.get('project','')).strip()
    if not name or name not in _projects(): return jsonify(error='Избери постоечки MarK проект'),400
    with LOCK,_db() as c:
        c.execute("INSERT INTO workspace(key,value) VALUES('active_project',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",(name,))
        c.commit()
    return jsonify(selected=name,workspace=_project_data(name))


@bp.get('/api/mark/unified/ui.js')
def serve_js():
    g=_guard()
    if g:return g
    return Response(UI_JS.read_text('utf8'),mimetype='application/javascript',headers={'Cache-Control':'no-store'})


def install(app, auth, local):
    global _AUTH,_LOCAL
    _AUTH,_LOCAL=auth,local
    app.register_blueprint(bp)
