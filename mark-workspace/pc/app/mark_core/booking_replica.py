"""TD Booking <-> MarK desktop full record replica, LAN TLS and PIN guarded.

Independent DB, never modifies original phone/TD/MarK files. Full records are
replicated per booking and per wedding-finance object, plus pricing/roster.
Optimistic revisions reject conflicting writes and preserve signed agreements.
"""
from __future__ import annotations

import datetime as dt
import json
import re
import sqlite3
import threading
from pathlib import Path
from contextlib import contextmanager
from flask import Blueprint, jsonify, request, Response

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'data' / 'booking_sync'
DB = DATA / 'booking_replica_v2.sqlite3'
GUARD_AUTH = None
GUARD_LOCAL = None
LOCK = threading.RLock()
bp = Blueprint('mark_booking_full_replica', __name__)
KINDS = {'bookings', 'finance', 'settings', 'roster', 'employees'}
MAX_BODY = 10 * 1024 * 1024
MAX_TOTAL = 600


@contextmanager
def _conn():
    DATA.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB, timeout=20)
    db.execute('PRAGMA busy_timeout=20000')
    db.execute('PRAGMA journal_mode=WAL')
    db.execute('''CREATE TABLE IF NOT EXISTS documents (
       kind TEXT NOT NULL, ident TEXT NOT NULL, revision INTEGER NOT NULL,
       payload TEXT, updated_at TEXT NOT NULL, updated_by TEXT NOT NULL,
       PRIMARY KEY(kind,ident))''')
    db.execute('''CREATE TABLE IF NOT EXISTS record_history (
       hist_id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, ident TEXT NOT NULL,
       revision INTEGER NOT NULL, payload TEXT, changed_at TEXT NOT NULL, author TEXT NOT NULL)''')
    try:
        with db:
            yield db
    finally:
        db.close()


def _safe_document(kind, value):
    if value is None: return None
    if kind in ('bookings', 'finance', 'roster', 'employees') and not isinstance(value, dict):
        raise ValueError('Невалиден запис за ' + kind)
    if kind == 'employees':
        allowed={'id','name','role','phone','active','notes'}
        if set(value)-allowed or not isinstance(value.get('id'),str) or not isinstance(value.get('name'),str) or not value['name'].strip():
            raise ValueError('Невалиден локален член на екипа')
        if any(len(str(value.get(k,'')))>180 for k in ('name','role','phone','notes')):
            raise ValueError('Премногу долг запис за екипа')
        if 'active' in value and not isinstance(value['active'],bool):
            raise ValueError('Невалиден статус на член')
    if kind == 'bookings':
        if not (isinstance(value.get('id'),str) and value.get('id').strip()):
            raise ValueError('Недостига ID на свадба')
        if value.get('date'):
            dt.date.fromisoformat(value['date'])
    if kind == 'finance':
        for key in ('clientPayments','crew','express','extras'):
            if key in value and not isinstance(value[key],list):
                raise ValueError('Неисправен финансиски список '+key)
    if kind == 'settings' and not isinstance(value,list):
        raise ValueError('Невалидни пакети')
    if kind == 'roster':
        # Roster is display-only: no Firebase tokens, keys or device authentication.
        for person in value.values():
            if not isinstance(person,dict) or set(person)-{'id','name','role','status','active'}:
                raise ValueError('Невалидна листа на екипата')
    encoded=json.dumps(value,ensure_ascii=False,separators=(',',':'),allow_nan=False)
    if len(encoded.encode('utf8'))>4*1024*1024:
        raise ValueError('Записот е преголем за LAN синхронизација')
    return encoded


def _immutable_changed(kind, old, new):
    if old is None:return False
    if new is None:
        return bool((kind=='bookings' and old.get('locked')) or (kind=='finance' and any(isinstance(c,dict) and c.get('signedContract') for c in old.get('crew',[]))))
    if kind=='bookings':
        # Only external calendar linkage can change after a contract has been signed.
        if old.get('locked'):
            protected=lambda o:{k:v for k,v in o.items() if k not in
                                   ('calendarSync','calendarEventId','calendarId')}
            return protected(old)!=protected(new)
    if kind=='finance':
        old_crew={str(c.get('id')):c for c in old.get('crew',[]) if isinstance(c,dict)}
        new_crew={str(c.get('id')):c for c in new.get('crew',[]) if isinstance(c,dict)}
        for ident, before in old_crew.items():
            if before.get('signedContract') and (
                ident not in new_crew or before['signedContract'] != new_crew[ident].get('signedContract')):
                return True
    return False


def exchange(device_id, body):
    """One idempotent transaction. Conflicting records never overwrite originals."""
    if not isinstance(body,dict) or not isinstance(body.get('operations',[]),list):
        return {'error':'Невалиден пакет'},400
    ops=body.get('operations',[])
    if len(ops)>MAX_TOTAL:return {'error':'Најмногу 600 промени одеднаш'},413
    prepared=[]
    try:
        for op in ops:
            if not isinstance(op,dict):raise ValueError('Погрешна промена')
            kind=op.get('kind'); ident=op.get('id'); base=op.get('baseRev')
            if kind not in KINDS or not isinstance(ident,str) or not re.fullmatch(r'[\w.\-]{1,100}',ident,re.U):
                raise ValueError('Недозволен ID')
            if kind in ('settings','roster') and ident!='global':raise ValueError('Погрешен ID')
            if type(base) is not int or base<0:raise ValueError('Погрешна основна ревизија')
            payload=_safe_document(kind,op.get('data'))
            if kind=='employees' and payload and json.loads(payload).get('id')!=ident:
                raise ValueError('ID на член не се совпаѓа')
            if kind=='bookings' and payload and json.loads(payload).get('id')!=ident:
                raise ValueError('ID на свадбата не се совпаѓа')
            prepared.append((kind,ident,base,payload))
    except (ValueError,TypeError,OverflowError) as e:return {'error':str(e)},400
    applied=[];conflicts=[]
    with LOCK:
        with _conn() as c:
            for kind, ident, base, payload in prepared:
                row=c.execute('SELECT revision,payload FROM documents WHERE kind=? AND ident=?',(kind,ident)).fetchone()
                current=row[0] if row else 0
                if base!=current:
                    conflicts.append({'kind':kind,'id':ident,'expected':base,'current':current,'reason':'changed_on_other_device'})
                    continue
                old=json.loads(row[1]) if row and row[1] is not None else None
                new=json.loads(payload) if payload is not None else None
                if _immutable_changed(kind,old,new):
                    conflicts.append({'kind':kind,'id':ident,'expected':base,'current':current,'reason':'signed_document_is_immutable'})
                    continue
                if old==new:continue
                rev=current+1
                if row:
                    c.execute('INSERT INTO record_history(kind,ident,revision,payload,changed_at,author) VALUES (?,?,?,?,?,?)',
                        (kind,ident,current,row[1],dt.datetime.now(dt.timezone.utc).isoformat(),device_id))
                c.execute('''INSERT INTO documents(kind,ident,revision,payload,updated_at,updated_by)
                     VALUES (?,?,?,?,?,?) ON CONFLICT(kind,ident) DO UPDATE SET
                     revision=excluded.revision,payload=excluded.payload,
                     updated_at=excluded.updated_at,updated_by=excluded.updated_by''',
                     (kind,ident,rev,payload,dt.datetime.now(dt.timezone.utc).isoformat(),device_id))
                applied.append({'kind':kind,'id':ident,'revision':rev})
            docs=c.execute('SELECT kind,ident,revision,payload FROM documents ORDER BY kind,ident').fetchall()
    snapshot={k:{} for k in KINDS}
    for kind,ident,rev,payload in docs:
        snapshot[kind][ident]={'rev':rev,'data':json.loads(payload) if payload is not None else None}
    # Legacy project/production index retains safe fields only; full finance NEVER copied there.
    if applied:
        try:
            from .booking_sync import _import_bookings
            items=[]
            for a in applied:
                if a['kind']!='bookings':continue
                x=snapshot['bookings'][a['id']]['data']
                if x is not None:items.append(x)
            if items:_import_bookings('MarK-full-replica',items[:150])
        except (ImportError,sqlite3.Error,ValueError):pass
    return {'version':2,'applied':applied,'conflicts':conflicts,'snapshot':snapshot},200


def _guard(write=False):
    if not GUARD_AUTH or not GUARD_AUTH():return jsonify(error='MarK PIN е потребен'),401
    if not GUARD_LOCAL or not GUARD_LOCAL():return jsonify(error='Само локален MarK PC'),403
    if write:
        if request.headers.get('X-TD-Client')!='TD-v1.4':return jsonify(error='Невалиден MarK клиент'),403
        origin=request.headers.get('Origin','')
        if origin and origin not in ('http://127.0.0.1:8765','http://localhost:8765'):
            return jsonify(error='Неовластен извор'),403
    return None


def seed_manual_booking(booking):
    """Mirror a newly created PC legacy record to full-sync without touching existing data.

    Existing same-ID rows never change; conflicting IDs are refused.
    This method does not import cloud credentials, payments, or fabricated prices.
    """
    ident=booking.get('id')
    if not isinstance(ident,str) or not re.fullmatch(r'[\w.\-]{1,100}',ident,re.U):
        raise ValueError('Невалиден ID за локалната свадба')
    payload=_safe_document('bookings',booking)
    with LOCK, _conn() as c:
        row=c.execute('SELECT payload FROM documents WHERE kind=? AND ident=?',('bookings',ident)).fetchone()
        if row:
            if row[0]!=payload:raise ValueError('Постоечкиот Full Sync запис е различен. Нема пребришување.')
            return False
        now=dt.datetime.now(dt.timezone.utc).isoformat()
        c.execute('INSERT INTO documents(kind,ident,revision,payload,updated_at,updated_by) VALUES (?,?,?,?,?,?)',
                  ('bookings',ident,1,payload,now,'MarK-PC-manual'))
    return True


@bp.post('/api/mark/booking/replica')
def pc_exchange():
    g=_guard(write=True)
    if g:return g
    if request.content_length is None or request.content_length>MAX_BODY:
        return jsonify(error='Премногу податоци'),413
    obj=request.get_json(silent=True)
    output,status=exchange('MarK-PC',obj)
    return jsonify(output),status


@bp.get('/api/mark/booking/replica/status')
def replica_status():
    g=_guard()
    if g:return g
    with LOCK,_conn() as c:
        stat=c.execute('SELECT kind,COUNT(*) FROM documents WHERE payload IS NOT NULL GROUP BY kind').fetchall()
    return jsonify(version=2,counts=dict(stat),mode='BIDIRECTIONAL_OPTIMISTIC_SYNC',maxBytes=MAX_BODY)


@bp.get('/api/mark/booking/app')
def pc_app():
    g=_guard()
    if g:return g
    p=Path(__file__).with_name('booking_full_app.html')
    return Response(p.read_text('utf8'),mimetype='text/html',headers={'Cache-Control':'no-store', 'X-Frame-Options':'SAMEORIGIN'})


@bp.get('/api/mark/booking/asset/<name>')
def pc_asset(name):
    g=_guard()
    if g:return g
    if name not in ('td_mark_ui_v612.js','mark_brand.js','mark_brand.css','booking_full_replica.js','mark_m_web.png','td_default_logo.png','td_manual_crew.js','backup_export_v618.js','booking_pages_v620.css','booking_pages_v620.js','mark_brand_v619.js'):
        return jsonify(error='Недозволена датотека'),404
    p=Path(__file__).parent/name
    if not p.is_file():return jsonify(error='Недостига датотека'),404
    mimetype='image/png' if name.endswith('.png') else 'text/css' if name.endswith('.css') else 'application/javascript'
    return Response(p.read_bytes(),mimetype=mimetype,headers={'Cache-Control':'no-cache'})


def install(app,auth,local):
    global GUARD_AUTH,GUARD_LOCAL
    GUARD_AUTH,GUARD_LOCAL=auth,local
    app.register_blueprint(bp)
