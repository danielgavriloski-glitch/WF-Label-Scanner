"""Local Premiere bridge for TD Production AI Studio.

The Flask server never fabricates .prproj files. A native Premiere UXP
plugin creates/saves the .prproj through the official Premiere API.
A private 24-byte pairing secret is stored in existing TD local config.
All bridge calls must originate from local loopback, not the phone/LAN.
"""
from __future__ import annotations
import json
import secrets
import shutil
import threading
import time
from pathlib import Path
from flask import jsonify, request

SUPPORTED = {'.xml'}


def safe_project_name(text):
    # Windows filename characters only; do not rewrite the CAM folders.
    invalid='<>:"/\\|?*'
    s=''.join('_' if c in invalid or ord(c)<32 else c for c in text).strip(' .')
    return s[:80] or 'Wedding'


def prepare_project_workspace(wedding_folder: Path, xml: Path, log=print):
    wedding_folder=wedding_folder.resolve()
    xml=xml.resolve()
    if not wedding_folder.is_dir() or not xml.is_file() or xml.suffix.lower() not in SUPPORTED:
        raise ValueError('Source project folder or AI XML export missing')
    work=wedding_folder/'TD_AI_EDIT'
    work.mkdir(exist_ok=True)
    # Avoid overwriting a previous draft. Keep original media untouched.
    stamp=time.strftime('%Y%m%d_%H%M%S')
    candidate=work/('TD_AI_Director_'+stamp+'.xml')
    n=1
    while candidate.exists():
        candidate=work/f'TD_AI_Director_{stamp}_{n}.xml';n+=1
    shutil.copy2(xml,candidate)
    name=safe_project_name(wedding_folder.name)
    project=wedding_folder/(name+' - TD AI.prproj')
    n=2
    # NEVER overwrite an existing Premiere project, even if it came from us.
    while project.exists():
        project=wedding_folder/(name+f' - TD AI {n}.prproj');n+=1
    readme=work/'READ_ME_FIRST.txt'
    readme.write_text(
        'TD PRODUCTION AI DIRECTOR - REVIEW PROJECT\n'
        f'Wedding folder: {wedding_folder}\n'
        f'Prepared XML: {candidate}\n'
        f'Planned native Premiere project: {project}\n'
        'A valid .prproj is created only inside Adobe Premiere via the paired UXP bridge.\n'
        'If the bridge is offline, import the XML through Premiere > File > Import.\n'
        'Original CAM folders and their contents are not changed.\n'
        'This is an experimental rough cut, not a finished film.\n',encoding='utf-8')
    log('Project workspace next to original videos: '+str(work))
    log('Planned named Premiere project: '+str(project))
    return {'project_path':project, 'xml_path':candidate, 'workspace':work}


class PremiereBridge:
    def __init__(self):
        self.cv=threading.Condition()
        self.last_ping=0.0
        self.last_result=''
        self.command=None
        self.answer=None
        self.counter=0

    def status(self):
        with self.cv:
            return {'connected':(time.monotonic()-self.last_ping)<15,
                    'last_result':self.last_result[:150],
                    'waiting':self.command is not None}

    def poll(self):
        with self.cv:
            self.last_ping=time.monotonic()
            if self.command:
                return dict(self.command)
            return None

    def submit_project(self, path, xml, control, timeout=90):
        with self.cv:
            if time.monotonic()-self.last_ping>15:
                return {'created':False,'error':'Premiere UXP bridge disconnected. Open and pair TD Premiere Bridge panel once.'}
            if self.command is not None:
                return {'created':False,'error':'Premiere is already processing another command.'}
            self.counter+=1
            ident=f'project-{self.counter}-{secrets.token_hex(5)}'
            self.command={'id':ident,'action':'create_project','project_path':str(path),'xml_path':str(xml)}
            self.answer=None
            self.cv.notify_all()
            end=time.monotonic()+timeout
            while True:
                if control().get('mode')=='stopped':
                    self.command=None
                    return {'created':False,'error':'Stopped by F10 or Stop button.'}
                if self.answer and self.answer.get('id')==ident:
                    response=dict(self.answer);self.answer=None;self.command=None
                    self.last_result=str(response.get('message',''))[:240]
                    return response
                left=end-time.monotonic()
                if left<=0:
                    self.command=None
                    return {'created':False,'error':'Premiere plugin did not acknowledge project creation within 90 seconds.'}
                self.cv.wait(timeout=min(0.5,left))

    def report(self,data):
        with self.cv:
            cmd=self.command
            if not cmd or str(data.get('id'))!=cmd['id']:
                return False
            self.answer={'id':cmd['id'],'created':bool(data.get('created',False)),
                         'imported':bool(data.get('imported',False)),
                         'message':str(data.get('message',''))[:400],
                         'error':str(data.get('error',''))[:400]}
            self.last_result=self.answer['message'] or self.answer['error']
            self.cv.notify_all()
            return True


def install_project_bridge(g):
    app=g['app'];CFG=g['CFG'];config=g['CONFIG']
    if not CFG.get('premiere_bridge_secret'):
        CFG['premiere_bridge_secret']=secrets.token_hex(24)
        config.write_text(json.dumps(CFG,indent=2),encoding='utf-8')
    bridge=PremiereBridge()
    g['premiere_bridge']=bridge
    g['bridge_status']=bridge.status

    def local():
        return request.remote_addr in ('127.0.0.1','::1')
    def bridge_auth():
        key=request.headers.get('X-TD-Bridge-Key','')
        return local() and secrets.compare_digest(key,CFG['premiere_bridge_secret'])

    @app.after_request
    def uxp_cors_headers(resp):
        # Adobe UXP fetch can require CORS even on localhost.
        if request.path.startswith('/api/bridge/') and local():
            origin=request.headers.get('Origin','')
            if origin == 'null' or origin.startswith('uxp://'):
                resp.headers['Access-Control-Allow-Origin']=origin
                resp.headers['Access-Control-Allow-Headers']='X-TD-Bridge-Key, Content-Type'
                resp.headers['Access-Control-Allow-Methods']='GET, POST, OPTIONS'
                resp.headers['Vary']='Origin'
        return resp

    @app.route('/api/bridge/poll',methods=['OPTIONS'])
    @app.route('/api/bridge/report',methods=['OPTIONS'])
    def bridge_preflight():
        if not local():return jsonify(error='Loopback only'),403
        return ('',204)

    @app.route('/api/bridge/pairing',methods=['POST'])
    def bridge_pairing():
        gate=g['api_guard']()
        if gate:return gate
        if not local():return jsonify(error='Pairing secret can only be viewed on this PC'),403
        return jsonify(secret=CFG['premiere_bridge_secret'])

    @app.route('/api/bridge/poll',methods=['GET'])
    def bridge_poll():
        if not bridge_auth():return jsonify(error='Bridge not paired'),403
        command=bridge.poll() if g['get_state']().get('mode')=='running' else None
        return jsonify(command=command,status='connected')

    @app.route('/api/bridge/report',methods=['POST'])
    def bridge_report():
        if not bridge_auth():return jsonify(error='Bridge not paired'),403
        if bridge.report(request.get_json(silent=True) or {}):
            return jsonify(ok=True)
        return jsonify(error='No matching request, possibly cancelled'),409
