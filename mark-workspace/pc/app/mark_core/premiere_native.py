"""MarK-native Premiere link. No legacy TD Bridge, no Ollama dependency.
Only MarK's embedded llama.cpp runtime is queried for vision status/analysis.
Designed for use with the existing authenticated Flask application and loopback host.
"""
from __future__ import annotations
import base64
import hashlib
import json
import secrets
import sqlite3
import threading
import time
import ipaddress
from pathlib import Path
from flask import Blueprint, request, jsonify, redirect, Response
from .runtime import health, completions

ROOT = Path(__file__).resolve().parents[2]
DB = ROOT / 'data' / 'mark_premiere_link.sqlite3'
lock = threading.RLock()
bp_premiere = Blueprint('mark_premiere_native', __name__)
_PAIR = {'code_hash': '', 'expires': 0, 'attempts': 0}
AUTH = None
LOCAL = None


def dbconnect():
    DB.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(DB, timeout=15)
    con.execute('PRAGMA busy_timeout=10000')
    con.execute('CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, token_hash TEXT NOT NULL, name TEXT NOT NULL, paired_at INTEGER NOT NULL, last_seen INTEGER, state_json TEXT)')
    con.execute('CREATE TABLE IF NOT EXISTS tasks(id TEXT PRIMARY KEY, device_id TEXT, action TEXT NOT NULL, payload TEXT NOT NULL, status TEXT NOT NULL, report TEXT, created_at INTEGER NOT NULL)')
    return con


def loopback():
    try:
        return ipaddress.ip_address(request.remote_addr).is_loopback
    except (ValueError, TypeError):
        return False


def ui_guard():
    if not loopback() or not LOCAL():
        return jsonify(error='Local MarK desktop only'), 403
    if not AUTH():
        return jsonify(error='Login to MarK first'), 401
    if request.method == 'POST':
        if request.headers.get('X-TD-Client') != 'TD-v1.4':
            return jsonify(error='Missing MarK local request marker'), 403
        origin = request.headers.get('Origin', '')
        if origin and origin not in ('http://127.0.0.1:8765', 'http://localhost:8765'):
            return jsonify(error='Cross-origin session action refused'), 403
    return None


def token_device():
    if not loopback():
        return None
    raw = request.headers.get('X-MarK-Premiere-Token', '')
    if not isinstance(raw, str) or len(raw) != 64 or not all(c in '0123456789abcdef' for c in raw):
        return None
    digest = hashlib.sha256(bytes.fromhex(raw)).hexdigest()
    with dbconnect() as con:
        row = con.execute('SELECT id,name FROM devices WHERE token_hash=?', (digest,)).fetchone()
        if row:
            con.execute('UPDATE devices SET last_seen=? WHERE id=?', (int(time.time()), row[0]))
    return row


def authorized_device():
    row = token_device()
    return (row, None) if row else (None, (jsonify(error='MarK pairing required'), 401))


@bp_premiere.after_request
def cors_for_premiere(res):
    # UXP runs from an internal extension origin. CORS is only for this route prefix;
    # all actions still require loopback + a random 256-bit bearer capability.
    origin = request.headers.get('Origin', '')
    if origin and len(origin) < 250 and (origin == 'null' or origin.startswith(('uxp://','file://','http://localhost:', 'http://127.0.0.1:'))):
        res.headers['Access-Control-Allow-Origin'] = origin
        res.headers['Vary'] = 'Origin'
        res.headers['Access-Control-Allow-Headers'] = 'Content-Type, X-MarK-Premiere-Token'
        res.headers['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS'
    res.headers['Cache-Control'] = 'no-store'
    return res


@bp_premiere.route('/api/mark/premiere/<path:rest>', methods=['OPTIONS'])
def preflight(rest):
    if not loopback():
        return jsonify(error='Local only'), 403
    return Response(status=204)


@bp_premiere.route('/api/mark/premiere/capabilities')
def capabilities():
    if not loopback():
        return jsonify(error='Local only'), 403
    h = health()
    return jsonify(ok=True, protocol='mark-native-premiere-v1', identity='MarK Native AI Engine', legacy_td=False,
                   vision_backend=h.get('backend', 'embedded-llama.cpp'), model_ready=bool(h.get('ready')),
                   pairing='local-six-digit-expiring-code')


PAGE = r'''<!doctype html><html lang="mk"><meta charset="utf-8"><title>MarK · Premiere Connection</title><style>
body{max-width:760px;margin:38px auto;padding:20px;background:#101b27;color:#e6eef1;font:15px system-ui}
h1{color:#6ed4d2;font-size:25px}section{padding:18px;border:1px solid #395364;border-radius:14px;background:#1a2b39;margin:16px 0}
button,input{padding:12px;border-radius:9px;border:1px solid #527080;background:#263f50;color:white;font:inherit}
button{cursor:pointer;background:#2c807e}input{width:min(92%,510px)}code{color:#f1c486}pre{white-space:pre-wrap;word-break:break-word;line-height:1.5}
small{color:#b2c3ce}.code{font-size:36px;letter-spacing:6px;font-weight:bold;color:#f5ce8b}
</style><h1>MarK ↔ Adobe Premiere Pro</h1><p>Ова е НОВАТА MarK Native врска, не стариот TD Bridge.</p>
<section><h2>Спарување</h2><button id="generate">Создај еднократен код</button><p class="code" id="pairCode">—</p><small>Кодот важи 2 минути и е за Premiere Director PRO. Не е твојот MarK PIN.</small></section>
<section><h2>Поврзан Premiere</h2><button id="refresh">Освежи статус</button><pre id="devices">Провери уреди...</pre><div><input id="revokeId" placeholder="Device ID (копирај го ID од статусот)"><button id="revoke">Исклучи уред</button></div></section>
<section><h2>Испрати XML во Premiere</h2><p><small>Користи само постоечка XML датотека од MarK извоз. Нов проект нема автоматски да се презапише.</small></p><input id="xml" placeholder="H:\\...\\AI_Montage.xml"><button id="send">Испрати на Premiere</button><pre id="result"></pre></section>
<section><h2>MarK Vision</h2><button id="vision">Провери локален AI модел</button><pre id="visionInfo">Не е проверен.</pre></section>
<script>
async function requestJson(path, data){const opts=data===undefined?{}:{method:'POST',headers:{'Content-Type':'application/json','X-TD-Client':'TD-v1.4'},body:JSON.stringify(data)};const r=await fetch(path,opts);const x=await r.json();if(!r.ok)throw Error(x.error||r.status);return x}
function error(x,id){document.getElementById(id).textContent=String(x.message||x)}
document.getElementById('generate').onclick=async()=>{try{const j=await requestJson('/api/mark/premiere/new-code',{});document.getElementById('pairCode').textContent=j.code}catch(e){error(e,'pairCode')}};
async function refresh(){try{const x=await requestJson('/api/mark/premiere/devices');document.getElementById('devices').textContent=JSON.stringify(x.devices,null,2)}catch(e){error(e,'devices')}}
document.getElementById('refresh').onclick=refresh;
document.getElementById('revoke').onclick=async()=>{if(!confirm('Да го исклучам овој Premiere уред?'))return;try{await requestJson('/api/mark/premiere/revoke',{id:document.getElementById('revokeId').value.trim()});await refresh()}catch(e){error(e,'devices')}};
document.getElementById('send').onclick=async()=>{try{const x=await requestJson('/api/mark/premiere/queue-xml',{path:document.getElementById('xml').value.trim()});document.getElementById('result').textContent='Задача: '+x.id+' · '+x.status}catch(e){error(e,'result')}};
document.getElementById('vision').onclick=async()=>{try{const x=await requestJson('/api/mark/vision/native-status');document.getElementById('visionInfo').textContent=JSON.stringify(x,null,2)}catch(e){error(e,'visionInfo')}};
refresh();
</script></html>'''


@bp_premiere.route('/mark/premiere')
def control_page():
    g = ui_guard()
    if g: return g
    return Response(PAGE, mimetype='text/html')


@bp_premiere.route('/api/mark/premiere/new-code', methods=['POST'])
def create_code():
    g = ui_guard()
    if g: return g
    with lock:
        code = f'{secrets.randbelow(1_000_000):06d}'
        _PAIR.update(code_hash=hashlib.sha256(code.encode()).hexdigest(), expires=time.monotonic()+120, attempts=0)
    return jsonify(code=code, valid_seconds=120)


@bp_premiere.route('/api/mark/premiere/pair', methods=['POST'])
def pair():
    if not loopback(): return jsonify(error='Local only'), 403
    data = request.get_json(silent=True) or {}
    code = data.get('code', '')
    if not isinstance(code, str) or not code.isascii() or len(code)!=6 or not code.isdigit():
        return jsonify(error='Invalid six-digit code'), 400
    name = str(data.get('name', 'Premiere PRO'))[:80].strip() or 'Premiere PRO'
    with lock:
        if not _PAIR['code_hash'] or time.monotonic()>_PAIR['expires'] or _PAIR['attempts']>=5:
            return jsonify(error='Code expired; generate a new one in MarK'), 403
        _PAIR['attempts'] += 1
        if not secrets.compare_digest(_PAIR['code_hash'], hashlib.sha256(code.encode()).hexdigest()):
            return jsonify(error='Code does not match'), 403
        _PAIR.update(code_hash='', expires=0, attempts=0)
        secret = secrets.token_hex(32)
        device_id = secrets.token_hex(8)
        with dbconnect() as con:
            con.execute('INSERT INTO devices(id,token_hash,name,paired_at,last_seen,state_json) VALUES(?,?,?,?,?,?)',
                        (device_id,hashlib.sha256(bytes.fromhex(secret)).hexdigest(),name,int(time.time()),int(time.time()),'{}'))
    return jsonify(ok=True, protocol='mark-native-premiere-v1', token=secret, device_id=device_id)


@bp_premiere.route('/api/mark/premiere/status')
def plugin_status():
    row, error = authorized_device()
    if error: return error
    h=health()
    return jsonify(ok=True,protocol='mark-native-premiere-v1',device_id=row[0],connected=True,
                   model_ready=bool(h.get('ready')),vision_backend=h.get('backend','embedded-llama.cpp'))


@bp_premiere.route('/api/mark/premiere/heartbeat',methods=['POST'])
def heartbeat():
    row,error=authorized_device()
    if error:return error
    data=request.get_json(silent=True) or {}
    # Only status data. Never trust arbitrary paths/commands from the plugin.
    safe={key:str(data.get(key,''))[:180] for key in ('project','sequence','version')}
    safe['active']=bool(data.get('active',True))
    with dbconnect() as con:con.execute('UPDATE devices SET state_json=? WHERE id=?',(json.dumps(safe),row[0]))
    return jsonify(ok=True)


@bp_premiere.route('/api/mark/premiere/devices')
def devices():
    g=ui_guard()
    if g:return g
    with dbconnect() as con:rows=con.execute('SELECT id,name,paired_at,last_seen,state_json FROM devices ORDER BY paired_at DESC').fetchall()
    now=int(time.time())
    return jsonify(devices=[{'id':r[0], 'name':r[1],'paired_at':r[2], 'online':bool(r[3] and now-r[3]<15),
                            'last_seen':r[3], 'state':json.loads(r[4] or '{}')} for r in rows])


@bp_premiere.route('/api/mark/premiere/revoke',methods=['POST'])
def revoke():
    g=ui_guard()
    if g:return g
    data=request.get_json(silent=True) or {}
    dev=str(data.get('id',''))
    if not (len(dev)==16 and all(c in '0123456789abcdef' for c in dev)):return jsonify(error='Invalid device ID'),400
    with dbconnect() as con:
        count=con.execute('DELETE FROM devices WHERE id=?',(dev,)).rowcount
        con.execute("UPDATE tasks SET status='cancelled' WHERE device_id=? AND status='pending'",(dev,))
    return jsonify(ok=bool(count))


@bp_premiere.route('/api/mark/premiere/queue-xml',methods=['POST'])
def queue_xml():
    g=ui_guard()
    if g:return g
    data=request.get_json(silent=True) or {}
    raw=data.get('path','')
    if not isinstance(raw,str) or len(raw)>1000 or '\x00' in raw or '\r' in raw or '\n' in raw:
        return jsonify(error='Invalid file path'),400
    path=Path(raw)
    if not path.is_absolute() or path.suffix.lower()!='.xml' or not path.is_file():
        return jsonify(error='Existing absolute XML file required'),400
    with dbconnect() as con:
        devices=con.execute('SELECT id FROM devices ORDER BY last_seen DESC LIMIT 1').fetchall()
        if not devices:return jsonify(error='Pair Premiere plugin first'),409
        did=devices[0][0]
        task_id=secrets.token_hex(12)
        con.execute('INSERT INTO tasks(id,device_id,action,payload,status,created_at) VALUES(?,?,?,?,?,?)',
                    (task_id,did,'import_xml',json.dumps({'xml_path':str(path)}),'pending',int(time.time())))
    return jsonify(id=task_id,status='pending')


@bp_premiere.route('/api/mark/premiere/poll')
def poll():
    row,error=authorized_device()
    if error:return error
    with dbconnect() as con:
        task=con.execute("SELECT id,action,payload FROM tasks WHERE device_id=? AND status='pending' ORDER BY created_at LIMIT 1",(row[0],)).fetchone()
    return jsonify(ok=True,command={'id':task[0],'action':task[1],**json.loads(task[2])} if task else None)


@bp_premiere.route('/api/mark/premiere/report',methods=['POST'])
def report():
    row,error=authorized_device()
    if error:return error
    data=request.get_json(silent=True) or {}
    tid=str(data.get('id',''))
    if len(tid)!=24 or any(c not in '0123456789abcdef' for c in tid):return jsonify(error='Invalid task ID'),400
    ok=bool(data.get('ok'))
    text=str(data.get('message',''))[:500]
    with dbconnect() as con:
        task=con.execute('SELECT status FROM tasks WHERE id=? AND device_id=?',(tid,row[0])).fetchone()
        if not task:return jsonify(error='Task not found'),404
        if task[0]=='pending':
            con.execute('UPDATE tasks SET status=?,report=? WHERE id=?',('done' if ok else 'failed',json.dumps({'ok':ok,'message':text}),tid))
    return jsonify(ok=True,acknowledged=True)


@bp_premiere.route('/api/mark/vision/native-status')
def vision_status():
    g=ui_guard()
    if g:return g
    h=health()
    return jsonify(backend='MarK Native / '+h.get('backend','embedded-llama.cpp'),model_ready=bool(h.get('ready')),
                   binary_present=bool(h.get('binary')),model_present=bool(h.get('model_file')),
                   projection_present=bool(h.get('mmproj_file')),independent_of_ollama_daemon=True)


@bp_premiere.route('/api/mark/vision/analyze-image',methods=['POST'])
def vision_image():
    g=ui_guard()
    if g:return g
    data=request.get_json(silent=True) or {}
    raw=data.get('image_base64','')
    prompt=str(data.get('prompt','Опиши што гледаш, со неутрална проценка на композиција, фокус и експозиција.'))[:800]
    if not isinstance(raw,str) or len(raw)>8_000_000:return jsonify(error='Image too large'),413
    try:
        pic=base64.b64decode(raw,validate=True)
        if len(pic)>6_000_000 or not (pic.startswith(b'\xff\xd8\xff') or pic.startswith(b'\x89PNG\r\n\x1a\n')):
            return jsonify(error='Only PNG/JPEG image up to 6 MB'),400
        mime='image/jpeg' if pic[:3]==b'\xff\xd8\xff' else 'image/png'
        content=[{'type':'text','text':prompt},{'type':'image_url','image_url':{'url':'data:'+mime+';base64,'+raw}}]
        answer=completions([{'role':'system','content':'You are MarK Vision, offline wedding imagery analysis. Be conservative and do not claim to have edited media.'},
                            {'role':'user','content':content}],max_tokens=700)
    except Exception as exc:
        return jsonify(error='MarK Vision local inference failed: '+str(exc)[:250]),503
    return jsonify(ok=True,answer=answer,backend='embedded-llama.cpp')


def register(app, authed, local_request):
    global AUTH, LOCAL
    AUTH,LOCAL=authed,local_request
    with dbconnect():pass
    app.register_blueprint(bp_premiere)
