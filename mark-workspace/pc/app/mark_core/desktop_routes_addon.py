"""MarK Desktop v1.0 secure local-only branding and project APIs.

Mounted by the existing mark_core/routes.py inside the existing Flask application.
No changes to user wedding folders or legacy installations.
"""
from __future__ import annotations
from flask import Blueprint, request, jsonify, send_file
from pathlib import Path
from io import BytesIO
from PIL import Image, UnidentifiedImageError
import json, os, tempfile, hashlib, threading

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'data'
BRANDING = DATA / 'branding'
CONFIG = BRANDING / 'settings.json'
LOGO = BRANDING / 'customer_logo.png'
FIXED = ROOT / 'brand' / 'MarK_M_Fixed.png'
_LOCK = threading.RLock()
addon = Blueprint('mark_desktop_v1', __name__)
_AUTH = None
_LOCAL = None
DEFAULT = {'theme':'td','autoPalette':True,'colors':{'primary':'#58aeb9','secondary':'#c6a36c','warm':'#b37f7a'},'softness':70}

def _guard(write=False):
    if not _AUTH(): return jsonify(error='Потребна е PIN најава'), 401
    if not _LOCAL(): return jsonify(error='Дозволено само на овој компјутер'), 403
    if write:
        if request.headers.get('X-TD-Client') != 'TD-v1.4': return jsonify(error='Невалиден клиент'), 403
        origin=request.headers.get('Origin','')
        if origin and origin not in ('http://127.0.0.1:8765','http://localhost:8765'):
            return jsonify(error='Невалиден извор'), 403
    return None

def _json():
    try:
        obj=json.loads(CONFIG.read_text('utf-8')) if CONFIG.exists() else {}
    except (ValueError, OSError):obj={}
    out={**DEFAULT, **{k:v for k,v in obj.items() if k in DEFAULT}}
    out['colors']={**DEFAULT['colors'],**(obj.get('colors') if isinstance(obj.get('colors'),dict) else {})}
    out['hasLogo']=LOGO.is_file()
    return out

def _save(obj):
    BRANDING.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix='.settings_',suffix='.json',dir=str(BRANDING))
    try:
        with os.fdopen(fd,'w',encoding='utf-8') as f:json.dump(obj,f,ensure_ascii=False,indent=2)
        os.replace(tmp,CONFIG)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)

@addon.get('/mark/logo-fixed')
def fixed_logo():
    guard=_guard()
    if guard: return guard
    return send_file(str(FIXED),mimetype='image/png',max_age=3600)

@addon.get('/api/mark/brand/logo')
def customer_logo():
    guard=_guard()
    if guard:return guard
    if not LOGO.is_file(): return jsonify(error='Нема корисничко лого'),404
    return send_file(str(LOGO),mimetype='image/png',max_age=0)

@addon.route('/api/mark/brand',methods=['GET','POST'])
def brand():
    guard=_guard(request.method=='POST')
    if guard:return guard
    with _LOCK:
        if request.method=='POST':
            data=request.get_json(silent=True) or {}
            cur=_json()
            if data.get('removeLogo') is True:
                if LOGO.is_file():LOGO.unlink()
                cur['theme']='td'
                cur['colors']=DEFAULT['colors'].copy()
            if 'theme' in data:
                theme=data['theme']
                if theme not in ('td','custom'):return jsonify(error='Невалидна тема'),400
                cur['theme']=theme
            if 'autoPalette' in data:cur['autoPalette']=bool(data['autoPalette'])
            if 'softness' in data:
                n=data['softness']
                if not isinstance(n,(int,float)):return jsonify(error='Невалидна вредност'),400
                cur['softness']=max(35,min(95,int(n)))
            if 'colors' in data:
                colors=data['colors']
                if not isinstance(colors,dict):return jsonify(error='Невалидни бои'),400
                for key in ('primary','secondary','warm'):
                    if key in colors:
                        val=colors[key]
                        import re
                        if not isinstance(val,str) or not re.fullmatch(r'#[0-9a-fA-F]{6}',val):return jsonify(error='Невалиден код за боја'),400
                        cur['colors'][key]=val.lower()
            if 'logoData' in data:
                import base64
                raw=data['logoData']
                if not isinstance(raw,str) or not raw.startswith('data:image/') or len(raw)>7_000_000:
                    return jsonify(error='Максимум 4 MB PNG/JPG/WEBP лого'),400
                try:
                    head,b64=raw.split(',',1)
                    if head.lower() not in ('data:image/png;base64','data:image/jpeg;base64','data:image/webp;base64'):
                        return jsonify(error='Дозволено PNG, JPG, WEBP'),400
                    binary=base64.b64decode(b64,validate=True)
                    if len(binary)>4*1024*1024: return jsonify(error='Логото е преголемо'),400
                    with Image.open(BytesIO(binary)) as img:
                        if img.width*img.height > 12_000_000: return jsonify(error='Преголема резолуција'),400
                        img.load()
                        img.thumbnail((1024,1024))
                        clean=img.convert('RGBA')
                        BRANDING.mkdir(parents=True,exist_ok=True)
                        tmp=BRANDING / '.new_logo.png'
                        clean.save(tmp,'PNG',optimize=True)
                        os.replace(tmp,LOGO)
                    cur['theme']='custom'
                except (ValueError,UnidentifiedImageError,OSError,base64.binascii.Error):
                    return jsonify(error='Неподдржана или оштетена слика'),400
            _save({k:v for k,v in cur.items() if k!='hasLogo'})
        return jsonify(_json())

@addon.get('/api/mark/desktop-version')
def desktop_version():
    guard=_guard()
    if guard:return guard
    return jsonify(version='1.0',instance=hashlib.sha256(os.path.normcase(str(ROOT)).encode()).hexdigest()[:16])

@addon.route('/api/mark/projects', methods=['GET', 'POST'])
def projects():
    guard=_guard(request.method=='POST')
    if guard:return guard
    from .routes import connect,DLOCK
    with DLOCK,connect() as c:
        c.execute('CREATE TABLE IF NOT EXISTS desktop_projects (name TEXT PRIMARY KEY, created TEXT DEFAULT CURRENT_TIMESTAMP)')
        if request.method=='POST':
            data=request.get_json(silent=True) or {}
            name=str(data.get('name','')).strip()[:96]
            if not name:return jsonify(error='Внеси име на проект'),400
            c.execute('INSERT OR IGNORE INTO desktop_projects(name) VALUES (?)',(name,))
        rows=c.execute('SELECT project FROM messages UNION SELECT project FROM memories UNION SELECT name FROM desktop_projects ORDER BY 1 COLLATE NOCASE').fetchall()
    return jsonify(projects=['Општо']+[p for (p,) in rows if p and p!='Општо'][:250])

def install(app,auth,local):
    global _AUTH,_LOCAL
    _AUTH=auth;_LOCAL=local
    app.register_blueprint(addon)

@addon.get('/_mark_desktop_health')
def unauthed_instance():
    if not _LOCAL(): return jsonify(error='локален пристап'),403
    return jsonify(version='1.0',instance=hashlib.sha256(os.path.normcase(str(ROOT)).encode()).hexdigest()[:16])
