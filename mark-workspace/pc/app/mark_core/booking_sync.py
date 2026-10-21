"""MarK <-> TD Booking production metadata and project preparation v1.8.0. Full records via booking_replica.

Adds a **separate** HTTPS LAN port; the existing MarK Flask API remains loopback-only.
Does not read Booking finance, payments, personal phone numbers, or signatures.
Optional user-confirmed COPYING of original media into NEW project directories; never moves/deletes sources.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import http.server
import ipaddress
import json
import os
import shutil
import uuid
import re
import secrets
import socket
import sqlite3
import ssl
import threading
import time
from pathlib import Path
from contextlib import contextmanager
from urllib.parse import urlparse

from flask import Blueprint, jsonify, request, Response

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'data' / 'booking_sync'
DATABASE = DATA / 'sync.sqlite3'
HTTPS_PORT = 8766
CLIENT_HEADER = 'TD-v1.4'
CONFIGURED_ROOT = None
_AUTH = None
_LOCAL = None
_LOCK = threading.RLock()
_PENDING: dict[str, dict] = {}
_STATE = {'running':False,'error':'Не е стартувана LAN врската','ip':None,'fingerprint':None}
SYNC_BP = Blueprint('tdbooking_sync_v11', __name__)
_COPY_JOBS: dict[str,dict]={}
_COPY_JOB_LOCK=threading.RLock()
VIDEO_EXTS = {'.mp4','.mov','.mts','.m2ts','.avi','.mxf','.mkv','.mpg','.mpeg','.3gp','.mpg','.wmv'}
PHOTO_EXTS = {'.jpg','.jpeg','.nef','.nrw','.cr2','.cr3','.arw','.raf','.rw2','.dng','.tif','.tiff','.heic'}
SAFE_BOOKING_KEYS = ('id','bride','groom','date','venue','start','package','packageName','services','status','options')


@contextmanager
def _db():
    DATA.mkdir(parents=True,exist_ok=True)
    c = sqlite3.connect(str(DATABASE),timeout=10)
    c.execute('PRAGMA busy_timeout=10000')
    c.executescript('''
    CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, token_hash TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS bookings(id TEXT PRIMARY KEY, json TEXT NOT NULL, checksum TEXT NOT NULL, received_at TEXT DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, booking_id TEXT NOT NULL, label TEXT NOT NULL, at TEXT DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE IF NOT EXISTS projects(booking_id TEXT PRIMARY KEY, project_name TEXT NOT NULL, path TEXT NOT NULL, status TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
    ''')
    try:
        with c:yield c
    finally:c.close()


def _guard(write=False):
    if not _AUTH():return jsonify(error='Потребна е локална PIN најава'),401
    if not _LOCAL():return jsonify(error='Само на локалниот MarK компјутер'),403
    if write:
        if request.headers.get('X-TD-Client') != CLIENT_HEADER:return jsonify(error='Невалиден MarK клиент'),403
        origin=request.headers.get('Origin','')
        if origin and origin not in ('http://127.0.0.1:8765','http://localhost:8765'):
            return jsonify(error='Погрешен извор'),403
    return None


def _my_ip():
    try:
        s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
        s.connect(('192.0.2.1',80))
        v=s.getsockname()[0]
        s.close()
        return v
    except OSError:return '127.0.0.1'


def _create_cert():
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes,serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.x509.oid import NameOID
    cert=DATA/'lan_cert.pem';key=DATA/'lan_key.pem'
    if cert.is_file() and key.is_file():
        b=ssl.PEM_cert_to_DER_cert(cert.read_text('ascii'))
        return cert,key,hashlib.sha256(b).hexdigest()
    DATA.mkdir(parents=True,exist_ok=True)
    pk=ec.generate_private_key(ec.SECP256R1())
    now=dt.datetime.now(dt.timezone.utc)
    subj=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'MarK TD local sync')])
    signed=(x509.CertificateBuilder().subject_name(subj).issuer_name(subj).public_key(pk.public_key())
        .serial_number(x509.random_serial_number()).not_valid_before(now-dt.timedelta(days=1))
        .not_valid_after(now+dt.timedelta(days=3650))
        .add_extension(x509.BasicConstraints(ca=False,path_length=None),critical=True)
        .sign(pk,hashes.SHA256()))
    kb=pk.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption())
    cb=signed.public_bytes(serialization.Encoding.PEM)
    # Both files have restricted permissions wherever the host filesystem honors chmod.
    fd=os.open(str(key),os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
    with os.fdopen(fd,'wb') as f:f.write(kb)
    cert.write_bytes(cb)
    return cert,key,hashlib.sha256(signed.public_bytes(serialization.Encoding.DER)).hexdigest()


def _safe_booking(item):
    if not isinstance(item,dict):return None
    id_=str(item.get('id','')).strip()[:100]
    if not id_ or not re.fullmatch(r'[\w.\-]{1,100}',id_,re.U):return None
    book={}
    for k in SAFE_BOOKING_KEYS:
        v=item.get(k,'')
        if k=='options':
            book[k]={x:bool(v.get(x)) for x in ('video','photo','drone','crane','spot','loveStory','prints') if isinstance(v,dict) and x in v}
        elif isinstance(v,(str,int,float)) and not isinstance(v,bool):book[k]=str(v)[:600 if k=='services' else 160]
    book['id']=id_
    if not re.fullmatch(r'\d{4}-\d{2}-\d{2}',book.get('date','')):return None
    try:dt.date.fromisoformat(book['date'])
    except ValueError:return None
    return book


def _safe_label(b):
    return (b.get('date','')+' · '+b.get('groom','')+' и '+b.get('bride','')).strip(' ·')[:170]


def _import_bookings(device_id, items):
    if not isinstance(items,list) or len(items)>150:return {'error':'Листа до 150 свадби'},400
    changes=[];read=0
    with _LOCK:
        with _db() as c:
            for item in items:
                book=_safe_booking(item)
                if not book:continue
                read+=1
                s=json.dumps(book,ensure_ascii=False,sort_keys=True,separators=(',',':'))
                checksum=hashlib.sha256(s.encode()).hexdigest()
                old=c.execute('SELECT checksum FROM bookings WHERE id=?',(book['id'],)).fetchone()
                if old and old[0]==checksum:continue
                c.execute('INSERT INTO bookings(id,json,checksum) VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET json=excluded.json,checksum=excluded.checksum,received_at=CURRENT_TIMESTAMP',(book['id'],s,checksum))
                kind='new' if old is None else 'changed'
                c.execute('INSERT INTO events(kind,booking_id,label) VALUES (?,?,?)',(kind,book['id'],_safe_label(book)))
                changes.append({'kind':kind,'id':book['id']})
                # Insert only safe production fields into the existing MarK booking list.
                try:
                    from .routes import connect,DLOCK
                    with DLOCK,connect() as md:
                        md.execute('INSERT INTO bookings(id,data) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET data=excluded.data,updated=CURRENT_TIMESTAMP',(book['id'],s))
                except (ImportError,sqlite3.Error):pass
    with _db() as c:
        ids=[row['id'] for row in changes]
        # Project states flow back to the phone even on an unchanged sync.
        ids=[item.get('id') for item in items if isinstance(item,dict) and isinstance(item.get('id'),str)][:150]
        projects=[]
        for booking_id in ids:
            row=c.execute('SELECT project_name,path,status FROM projects WHERE booking_id=?',(booking_id,)).fetchone()
            if row:projects.append({'bookingId':booking_id,'name':row[0],'status':row[2]})
    return {'received':read,'changed':changes,'projects':projects,'source':device_id},200


def _token_hash(s):return hashlib.sha256(s.encode()).hexdigest()


class _LanHandler(http.server.BaseHTTPRequestHandler):
    server_version='MarKLocalSync/1.1'
    def log_message(self, fmt,*args):pass
    def _send(self,code,payload):
        body=json.dumps(payload,ensure_ascii=False).encode('utf8')
        self.send_response(code)
        self.send_header('Content-Type','application/json; charset=utf-8')
        self.send_header('Content-Length',str(len(body)))
        self.send_header('Cache-Control','no-store')
        self.end_headers();self.wfile.write(body)
    def do_POST(self):
        try:
            remote=ipaddress.ip_address(self.client_address[0])
            if not (remote.is_private or remote.is_loopback):
                self._send(403,{'error':'Само приватна LAN мрежа'});return
        except ValueError:
            self._send(403,{'error':'Непозната мрежа'});return
        length=int(self.headers.get('Content-Length','0') or '0')
        if length<1 or length>10*1024*1024:self._send(413,{'error':'Погрешна големина'});return
        try:data=json.loads(self.rfile.read(length))
        except (ValueError,UnicodeError):self._send(400,{'error':'Невалиден JSON'});return
        route=urlparse(self.path).path
        try:
            if route=='/v1/pair/request':
                # Device ID is public (not a credential); the approval is done in MarK Desktop.
                device_id=str(data.get('deviceId',''))[:80]
                if not re.fullmatch(r'[A-Za-z0-9._-]{8,80}',device_id):self._send(400,{'error':'Невалиден уред'});return
                with _LOCK:
                    for p in list(_PENDING):
                        if time.time()-_PENDING[p]['at']>300:del _PENDING[p]
                    if len(_PENDING)>=5:self._send(429,{'error':'Премногу обиди'});return
                    # Existing paired devices require no second pairing.
                    challenge=secrets.token_urlsafe(20)
                    code=f'{secrets.randbelow(1000000):06d}'
                    _PENDING[challenge]={'deviceId':device_id,'code':code,'at':time.time(),'approved':False,'token':None}
                self._send(200,{'challenge':challenge,'code':code,'expiresSeconds':300});return
            if route=='/v1/pair/complete':
                ch=str(data.get('challenge',''))
                with _LOCK:
                    p=_PENDING.get(ch)
                    if not p or time.time()-p['at']>300:self._send(410,{'error':'Кодот истече'});return
                    if not p['approved']:self._send(202,{'pending':True});return
                    if not p['token']:
                        token=secrets.token_urlsafe(40)
                        with _db() as c:c.execute('INSERT INTO devices(id,token_hash) VALUES (?,?) ON CONFLICT(id) DO UPDATE SET token_hash=excluded.token_hash',(p['deviceId'],_token_hash(token)))
                        p['token']=token
                    self._send(200,{'token':p['token'],'deviceId':p['deviceId']})
                    del _PENDING[ch]
                return
            if route=='/v1/bookings':
                dev=str(data.get('deviceId',''))[:80]
                auth=str(self.headers.get('Authorization',''))
                if not auth.startswith('Bearer '):self._send(401,{'error':'Нема дозвола'});return
                with _LOCK, _db() as c:
                    v=c.execute('SELECT token_hash FROM devices WHERE id=?',(dev,)).fetchone()
                if not v or not secrets.compare_digest(v[0],_token_hash(auth[7:])):self._send(403,{'error':'Уредот не е поврзан'});return
                result,status=_import_bookings(dev,data.get('bookings'))
                self._send(status,result);return
            if route=='/v2/replica':
                device_id=str(data.get('deviceId',''))[:80]
                auth=str(self.headers.get('Authorization',''))
                if not auth.startswith('Bearer '):
                    self._send(401,{'error':'Нема дозвола'});return
                with _LOCK, _db() as c:
                    valid=c.execute('SELECT token_hash FROM devices WHERE id=?',(device_id,)).fetchone()
                if not valid or not secrets.compare_digest(valid[0],_token_hash(auth[7:])):
                    self._send(403,{'error':'Уредот не е поврзан'});return
                from .booking_replica import exchange
                result,status=exchange(device_id,data)
                self._send(status,result);return
            self._send(404,{'error':'Непознат пат'})
        except Exception:
            self._send(500,{'error':'Внатрешна грешка – види PC лог'})


def start_https():
    if _STATE['running']:return
    try:
        with _db():pass
        cert,key,fp=_create_cert()
        server=http.server.ThreadingHTTPServer(('0.0.0.0',HTTPS_PORT),_LanHandler)
        ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.minimum_version=ssl.TLSVersion.TLSv1_2
        ctx.load_cert_chain(str(cert),str(key))
        server.socket=ctx.wrap_socket(server.socket,server_side=True)
        _STATE.update({'running':True,'error':'','fingerprint':fp,'ip':_my_ip()})
        threading.Thread(target=server.serve_forever,daemon=True,name='MarKBookingSyncLAN').start()
    except Exception as e:
        _STATE.update({'running':False,'error':type(e).__name__+': '+str(e)[:150]})


@SYNC_BP.get('/api/mark/sync/status')
def sync_status():
    g=_guard()
    if g:return g
    with _LOCK,_db() as c:
        events=c.execute('SELECT id,kind,booking_id,label,at FROM events ORDER BY id DESC LIMIT 30').fetchall()
        devices=c.execute('SELECT id,created_at FROM devices ORDER BY created_at DESC').fetchall()
        bookings=c.execute('SELECT id,json FROM bookings ORDER BY received_at DESC LIMIT 250').fetchall()
        projects=c.execute('SELECT booking_id,project_name,path,status FROM projects').fetchall()
        pending=[{'challenge':ch,'code':p['code'],'deviceId':p['deviceId'],'approved':p['approved']} for ch,p in _PENDING.items() if time.time()-p['at']<300]
    return jsonify(lan=dict(_STATE),address=f'https://{_STATE["ip"]}:{HTTPS_PORT}' if _STATE.get('ip') else '',devices=[{'id':x[0],'created':x[1]} for x in devices],pending=pending,events=[dict(zip(('id','kind','bookingId','label','at'),x)) for x in events],bookings=[json.loads(s) for _,s in bookings],projects=[dict(zip(('bookingId','name','path','status'),x)) for x in projects])


@SYNC_BP.post('/api/mark/sync/approve')
def sync_approve():
    g=_guard(write=True)
    if g:return g
    obj=request.get_json(silent=True) or {};ch=str(obj.get('challenge',''))
    with _LOCK:
        p=_PENDING.get(ch)
        if not p or time.time()-p['at']>300:return jsonify(error='Истечено барање'),410
        if str(obj.get('code',''))!=p['code']:return jsonify(error='Кодот не се совпаѓа'),403
        p['approved']=True
    return jsonify(approved=True)


@SYNC_BP.post('/api/mark/sync/revoke')
def sync_revoke():
    g=_guard(write=True)
    if g:return g
    device_id=str((request.get_json(silent=True) or {}).get('deviceId',''))
    with _LOCK,_db() as c:c.execute('DELETE FROM devices WHERE id=?',(device_id,))
    return jsonify(revoked=True)


def _clean_name(s):
    s=re.sub(r'[<>:"/\\|?*\x00-\x1f]',' ',str(s))
    s=re.sub(r'\s+',' ',s).strip(' .')
    return s[:58] or 'Без_име'


def _root_check(path):
    if not isinstance(path,str) or len(path)>400:raise ValueError('Внеси валидна HDD папка')
    p=Path(path).expanduser()
    if not p.is_absolute() or p.is_symlink():raise ValueError('Избери целна папка на HDD')
    if str(p)==p.anchor:raise ValueError('Избери под-папка, не целиот диск')
    if not p.exists():
        if not p.parent.is_dir() or p.parent.is_symlink():raise ValueError('Родителската HDD папка не постои')
        p.mkdir(exist_ok=False,parents=False)
    if not p.is_dir():raise ValueError('Целната патека не е папка')
    return p.resolve()


def _media_check(folders):
    if not isinstance(folders,list) or len(folders)>12:raise ValueError('Можеш да избереш до 12 изворни папки')
    result=[];total_video=0;total_photo=0
    for row in folders:
        if not isinstance(row,dict):raise ValueError('Невалидна медија папка')
        raw=str(row.get('path','')).strip()
        if not raw:continue
        path=Path(raw).expanduser()
        if not path.is_absolute() or not path.is_dir() or path.is_symlink():raise ValueError('Недостапен извор: '+raw[:130])
        path=path.resolve()
        video=photo=0;others=0
        # No following directory symlinks and cap file enumeration.
        seen=0
        for root,dirs,files in os.walk(path,followlinks=False):
            level=len(Path(root).relative_to(path).parts)
            if level>=8:dirs[:]=[]
            dirs[:]=[d for d in dirs if not Path(root,d).is_symlink()]
            for f in files:
                seen+=1
                if seen>50000:raise ValueError('Премногу датотеки во '+str(path)[:80])
                if Path(root,f).is_symlink():continue
                ext=Path(f).suffix.lower()
                if ext in VIDEO_EXTS and not str(row.get('label','')).startswith('PHOTO'):video+=1
                elif ext in PHOTO_EXTS and not str(row.get('label','')).startswith('VIDEO_'):photo+=1
                else:others+=1
        total_video+=video;total_photo+=photo
        result.append({'path':str(path),'label':str(row.get('label','CAM_1'))[:25],'videos':video,'photos':photo,'other':others})
    return {'sources':result,'videos':total_video,'photos':total_photo,'ready':total_video+total_photo>0,'analysis':'FILE_INVENTORY_ONLY'}


@SYNC_BP.post('/api/mark/sync/media-preview')
def media_preview():
    g=_guard(write=True)
    if g:return g
    try:return jsonify(_media_check((request.get_json(silent=True) or {}).get('folders',[])))
    except (ValueError,OSError) as e:return jsonify(error=str(e)),400


@SYNC_BP.post('/api/mark/sync/create-project')
def create_project():
    g=_guard(write=True)
    if g:return g
    req=request.get_json(silent=True) or {}
    bid=str(req.get('bookingId',''))
    with _LOCK,_db() as c:
        row=c.execute('SELECT json FROM bookings WHERE id=?',(bid,)).fetchone()
        if row is None:return jsonify(error='Свадбата не е синхронизирана'),404
        book=json.loads(row[0])
        if c.execute('SELECT path FROM projects WHERE booking_id=?',(bid,)).fetchone():return jsonify(error='За оваа свадба веќе постои проект; нема да го дуплирам'),409
    try:
        folder=_root_check(req.get('target',''))
        sources=_media_check(req.get('folders',[]))
        name=_clean_name(book.get('date','')+'_'+book.get('groom','')+'_и_'+book.get('bride',''))
        dest=folder/name
        if dest.exists():raise ValueError('Папката веќе постои. Одбери друга главна папка; нема препокривање.')
        # create only new project folder; all original sources stay untouched.
        dest.mkdir(parents=False,exist_ok=False)
        basic=['00_ADMIN/BOOKING','01_СУРОВ_МАТЕРИЈАЛ/ВИДЕО/КАМЕРА_1','01_СУРОВ_МАТЕРИЈАЛ/ВИДЕО/КАМЕРА_2','01_СУРОВ_МАТЕРИЈАЛ/ВИДЕО/ДРОН','01_СУРОВ_МАТЕРИЈАЛ/ВИДЕО/КРАН',
               '01_СУРОВ_МАТЕРИЈАЛ/ФОТОГРАФИИ/КАМЕРА_1','01_СУРОВ_МАТЕРИЈАЛ/ФОТОГРАФИИ/КАМЕРА_2','02_MUSIC','03_PREMIERE/PROJECT','03_PREMIERE/XML',
               '04_LIGHTROOM/CATALOG','04_LIGHTROOM/SELECTED','05_EXPORT/SPOTS','05_EXPORT/FULL_WEDDING','05_EXPORT/PHOTOS',
               '06_DELIVERY','07_REPORTS','08_STYLE_REFERENCES']
        options=book.get('options',{})
        # Every folder is optional to use; it is safe to create empty structure ahead of the event.
        for sub in basic:(dest/sub).mkdir(parents=True,exist_ok=True)
        readiness='READY_FOR_REVIEW' if sources['ready'] else 'WAITING_FOR_MEDIA'
        manifest={'version':1,'bookingId':bid,'name':name,'booking':book,'sources':sources,'status':readiness,
                  'createdAt':dt.datetime.now(dt.timezone.utc).isoformat(),'mediaAnalysis':'EXTENSION_AND_FILE_INVENTORY_ONLY','footagePolicy':{'copy_originals':False,'delete_originals':False,
                  'include_all_readable_in_long_sequences':True,'short_spot_max_source_seconds':300},'folders':basic}
        file=dest/'00_ADMIN'/'BOOKING'/'project_manifest.json'
        file.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
        with _LOCK,_db() as c:
            c.execute('INSERT INTO projects(booking_id,project_name,path,status) VALUES(?,?,?,?)',(bid,name,str(dest),readiness))
        try:
            from .routes import connect,DLOCK
            with DLOCK,connect() as c:
                c.execute('CREATE TABLE IF NOT EXISTS desktop_projects(name TEXT PRIMARY KEY, created TEXT DEFAULT CURRENT_TIMESTAMP)')
                c.execute('INSERT OR IGNORE INTO desktop_projects(name) VALUES (?)',(name,))
        except (ImportError,sqlite3.Error):pass
        return jsonify(path=str(dest),project=name,media=sources,status=readiness,folders=basic,ready_for_review=sources['ready'])
    except (OSError,ValueError) as e:return jsonify(error=str(e)),400


@SYNC_BP.post('/api/mark/sync/update-media')
def update_media():
    g=_guard(write=True)
    if g:return g
    obj=request.get_json(silent=True) or {}
    bid=str(obj.get('bookingId',''))
    with _LOCK,_db() as c:
        row=c.execute('SELECT path FROM projects WHERE booking_id=?',(bid,)).fetchone()
    if row is None:return jsonify(error='Не постои проект за оваа свадба'),404
    dest=Path(row[0])
    try:
        p=dest/'00_ADMIN'/'BOOKING'/'project_manifest.json'
        if not p.is_file():raise ValueError('Манифестот недостасува; не менуваме ништо')
        manifest=json.loads(p.read_text('utf8'))
        if manifest.get('bookingId')!=bid:raise ValueError('Погрешен проектен ID')
        result=_media_check(obj.get('folders',[]))
        status='READY_FOR_REVIEW' if result['ready'] else 'WAITING_FOR_MEDIA'
        manifest['sources']=result;manifest['status']=status
        manifest['mediaCheckedAt']=dt.datetime.now(dt.timezone.utc).isoformat()
        tmp=p.with_name('.manifest_new.json')
        tmp.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
        os.replace(tmp,p)
        with _LOCK,_db() as c:c.execute('UPDATE projects SET status=? WHERE booking_id=?',(status,bid))
        return jsonify(status=status,media=result,path=str(dest),ready_for_review=result['ready'])
    except (OSError,ValueError,json.JSONDecodeError) as e:
        return jsonify(error=str(e)),400




@SYNC_BP.post('/api/mark/sync/manual-booking')
def manual_booking():
    """Local PC booking mirrored into full phone/PC sync. Finance entered separately."""
    g=_guard(write=True)
    if g:return g
    j=request.get_json(silent=True) or {}
    data={'id':'manual-'+uuid.uuid4().hex[:24],'date':str(j.get('date','')).strip(),
          'groom':str(j.get('groom','')).strip(),'bride':str(j.get('bride','')).strip(),
          'venue':'','services':'','status':'Локален проект'}
    b=_safe_booking(data)
    if not b or not b['groom'] or not b['bride'] or len(b['groom'])>80 or len(b['bride'])>80:
        return jsonify(error='Внеси точен датум и имиња за зет и невеста'),400
    # Both databases receive the same immutable booking ID; the phone reads the
    # full-replica DB. No automatically manufactured finance amounts are added.
    try:
        from .booking_replica import seed_manual_booking
        seed_manual_booking(b)
    except (ValueError, OSError, sqlite3.Error) as e:
        return jsonify(error='Full Sync не ја зачува свадбата: '+str(e)),409
    _import_bookings('MarK local', [b])
    return jsonify(created=True,booking=b,fullSyncReady=True)


def _destination_for_label(dest:Path,label:str,ext:str):
    """One canonical Macedonian folder per format and source, no path data supplied by client."""
    label=str(label).upper()
    camera={'VIDEO_CAM_1':'КАМЕРА_1','VIDEO_CAM_2':'КАМЕРА_2',
        'VIDEO_DRONE':'ДРОН','VIDEO_CRANE':'КРАН',
        'PHOTO_CAM_1':'КАМЕРА_1','PHOTO_CAM_2':'КАМЕРА_2',
        'CAM_1':'КАМЕРА_1','CAM_2':'КАМЕРА_2',
        'DRONE':'ДРОН','CRANE':'КРАН','PHOTO':'КАМЕРА_1'}
    if label not in camera:raise ValueError('Непознат извор: '+label[:30])
    is_video=ext in VIDEO_EXTS
    is_photo=ext in PHOTO_EXTS
    if not(is_video or is_photo):return None
    # Do not move photos from a video-only folder or videos from a photo-only folder.
    if label.startswith('PHOTO') and not is_photo:return None
    if label.startswith('VIDEO_') and not is_video:return None
    top='ВИДЕО' if is_video else 'ФОТОГРАФИИ'
    return dest/'01_СУРОВ_МАТЕРИЈАЛ'/top/camera[label]


def _stored_project(booking_id):
    with _LOCK,_db() as c:
        row=c.execute('SELECT path FROM projects WHERE booking_id=?',(booking_id,)).fetchone()
    if row is None:raise ValueError('Нема проект за свадбата')
    base=Path(row[0]).resolve()
    manifest_path=base/'00_ADMIN'/'BOOKING'/'project_manifest.json'
    if not manifest_path.is_file():raise ValueError('Нема манифест на проектот')
    manifest=json.loads(manifest_path.read_text('utf8'))
    if manifest.get('bookingId')!=booking_id:raise ValueError('Проектот не одговара на свадбата')
    return base,manifest_path,manifest


def _copy_originals(job_id:str,booking_id:str):
    with _COPY_JOB_LOCK:job=_COPY_JOBS[job_id]
    def progress(**updates):
        with _COPY_JOB_LOCK:job.update(updates)
    try:
        dest,manifest_path,manifest=_stored_project(booking_id)
        sources=(manifest.get('sources') or {}).get('sources',[])
        inventory=[];space_required=0;seen=set()
        for row in sources:
            origin=Path(str(row.get('path',''))).resolve(strict=True)
            if not origin.is_dir() or origin.is_symlink():raise ValueError('Променет или недостапен извор')
            if dest==origin or dest in origin.parents or origin in dest.parents:
                raise ValueError('Изворната и целната папка не смеат да се преклопуваат')
            label=str(row.get('label',''))
            for walk_dir,dirs,files in os.walk(origin,followlinks=False):
                walk=Path(walk_dir)
                if len(walk.relative_to(origin).parts)>=8:dirs[:]=[]
                dirs[:]=[d for d in dirs if not (walk/d).is_symlink()]
                for name in files:
                    src=walk/name
                    if src.is_symlink() or not src.is_file():continue
                    target_dir=_destination_for_label(dest,label,src.suffix.lower())
                    if target_dir is None:continue
                    relative=src.relative_to(origin)
                    # Preserve nested directory structure within each camera's folder.
                    dst=target_dir/relative
                    k=str(dst).casefold()
                    if k in seen:raise ValueError('Две снимки имаат иста целна патека: '+name)
                    seen.add(k)
                    length=src.stat().st_size
                    inventory.append((src,dst,length))
                    space_required+=length
                    if len(inventory)>50000:raise ValueError('Премногу медија датотеки')
        free=shutil.disk_usage(dest).free
        if free<space_required+max(100*1024*1024,space_required//100):
            raise ValueError('Нема доволно слободен простор за безбедно копирање на HDD')
        progress(filesTotal=len(inventory),bytesTotal=space_required)
        copied=0;bytes_copied=0
        for src,dst,size in inventory:
            if dst.exists():
                if not dst.is_file() or dst.stat().st_size!=size:
                    raise ValueError('Постоечка целна датотека се разликува: '+str(dst))
                def digest(f):
                    h=hashlib.sha256()
                    with f.open('rb') as stream:
                        for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
                    return h.digest()
                if digest(src)!=digest(dst):raise ValueError('Постоечка снимка се разликува: '+str(dst))
            else:
                dst.parent.mkdir(parents=True,exist_ok=True)
                tmp=dst.with_name('.'+dst.name+'.marK-partial-'+job_id[:8])
                try:
                    if tmp.exists():raise ValueError('Постојат незавршени делумни датотеки')
                    shutil.copy2(src,tmp)
                    if tmp.stat().st_size!=size:raise ValueError('Погрешна големина по копирање')
                    h1=hashlib.sha256();h2=hashlib.sha256()
                    for file,h in ((src,h1),(tmp,h2)):
                        with file.open('rb') as stream:
                            for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
                    if h1.digest()!=h2.digest():raise ValueError('SHA256 проверката не помина')
                    os.replace(tmp,dst)
                finally:tmp.unlink(missing_ok=True)
            copied+=1;bytes_copied+=size
            progress(filesCopied=copied,bytesCopied=bytes_copied)
        # Status is not AI semantic analysis or completed Premiere editing.
        manifest['mediaCopy']={'completedAt':dt.datetime.now(dt.timezone.utc).isoformat(),
                              'filesCopied':copied,'bytes':bytes_copied,'mode':'SAFE_COPY_SHA256',
                              'analysis':'FILE_INVENTORY_ONLY'}
        temp=manifest_path.with_name('.manifest_copy_'+job_id[:8]+'.json')
        try:
            temp.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf8')
            os.replace(temp,manifest_path)
        finally:temp.unlink(missing_ok=True)
        progress(state='DONE')
    except Exception as e:
        progress(state='FAILED',error=str(e)[:400])


@SYNC_BP.post('/api/mark/sync/media-copy')
def media_copy():
    g=_guard(write=True)
    if g:return g
    bid=str((request.get_json(silent=True) or {}).get('bookingId',''))
    try:_stored_project(bid)
    except (ValueError,OSError,json.JSONDecodeError) as e:return jsonify(error=str(e)),400
    with _COPY_JOB_LOCK:
        for job_id,job in _COPY_JOBS.items():
            if job.get('bookingId')==bid and job.get('state')=='RUNNING':
                return jsonify(jobId=job_id,state='RUNNING')
        job_id=secrets.token_urlsafe(16)
        _COPY_JOBS[job_id]={'bookingId':bid,'state':'RUNNING','filesTotal':0,
                            'filesCopied':0,'bytesTotal':0,'bytesCopied':0,'error':''}
    threading.Thread(target=_copy_originals,args=(job_id,bid),daemon=True,
                     name='MarK Media Safe Copy').start()
    return jsonify(jobId=job_id,state='RUNNING')


@SYNC_BP.get('/api/mark/sync/media-copy/<job_id>')
def copy_status(job_id):
    g=_guard()
    if g:return g
    with _COPY_JOB_LOCK:
        job=_COPY_JOBS.get(job_id)
        if job is None:return jsonify(error='Непознато копирање'),404
        return jsonify(dict(job))


@SYNC_BP.get('/api/mark/sync/version')
def sync_version():
    g=_guard()
    if g:return g
    return jsonify(component='MarK Desktop / Booking / Media Prep',version='1.8.0',
                   sceneAI='NOT_IMPLEMENTED',androidAPK='FULL_SYNC_v6.15_SOURCE_BUILD_REQUIRED')

@SYNC_BP.get('/api/mark/sync/ui.js')
def ui_js():
    g=_guard()
    if g:return g
    p=Path(__file__).with_name('mark_booking_ui.js')
    return Response(p.read_text('utf8'),mimetype='application/javascript')


def install(app,auth,local):
    global _AUTH,_LOCAL
    _AUTH,_LOCAL=auth,local
    app.register_blueprint(SYNC_BP)
    start_https()
