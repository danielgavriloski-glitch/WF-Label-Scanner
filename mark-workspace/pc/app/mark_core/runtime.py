"""MarK local inference runtime and compatibility shim for unmodified legacy AI contracts.
No third-party model hosted remotely. 8798 and 8799 are LOOPBACK-only.
"""
from __future__ import annotations
from pathlib import Path
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
import os, subprocess, json, threading, urllib.request, urllib.error, time, base64, atexit
ROOT=Path(__file__).resolve().parents[2]
BIN=ROOT/'runtime'/'llama'/'llama-server.exe'
MODEL=ROOT/'models'/'Qwen3VL-4B-Instruct-Q4_K_M.gguf'
MMPROJ=ROOT/'models'/'mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf'
API='http://127.0.0.1:8799'
_proc=None
_start_lock=threading.Lock()
_STARTED=False

def health():
    try:
        with urllib.request.urlopen(API+'/health',timeout=1.2) as resp:
            raw=json.load(resp)
        return {'ready':raw.get('status')=='ok','state':raw.get('status'),'model_file':MODEL.exists(),'mmproj_file':MMPROJ.exists(),'binary':BIN.exists(),'backend':'embedded-llama.cpp','offline_after_install':True}
    except Exception:
        return {'ready':False,'state':'not_started_or_loading','model_file':MODEL.exists(),'mmproj_file':MMPROJ.exists(),'binary':BIN.exists(),'backend':'embedded-llama.cpp','offline_after_install':True}

def completions(messages,temperature=.25,max_tokens=700,json_mode=False):
    if not health()['ready']:
        raise RuntimeError('MarK model is not ready. Run MarK_Workspace_PC_Setup_TEST.exe and choose your install disk to install the local engine.')
    payload={'model':'MarK-Vision-4B','messages':messages,'stream':False,'temperature':temperature,'max_tokens':max_tokens}
    if json_mode:
        # Older applications expect machine-parseable JSON; explicitly request it.
        payload['messages']=[{'role':'system','content':'Respond with exactly one valid JSON object, no markdown fences.'}]+messages
        payload['response_format']={'type':'json_object'}
    req=urllib.request.Request(API+'/v1/chat/completions',data=json.dumps(payload,ensure_ascii=False).encode('utf-8'),headers={'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(req,timeout=240) as r:data=json.load(r)
    except urllib.error.HTTPError as err:
        if not json_mode or err.code != 400:raise
        payload.pop('response_format',None)
        req=urllib.request.Request(API+'/v1/chat/completions',data=json.dumps(payload,ensure_ascii=False).encode('utf-8'),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=240) as r:data=json.load(r)
    return str(data['choices'][0]['message']['content'] or '').strip()

class LegacyHandler(BaseHTTPRequestHandler):
    def log_message(self,*a):pass
    def _send(self, code, data):
        raw=json.dumps(data,ensure_ascii=False).encode('utf-8');self.send_response(code);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(raw)));self.end_headers();self.wfile.write(raw)
    def do_GET(self):
        if self.path=='/api/tags':
            names=[{'name':n,'model':n} for n in ('qwen2.5vl:3b','qwen3-vl:8b','qwen3:8b','MarK-Vision-4B')] if health()['ready'] else []
            return self._send(200,{'models':names})
        return self._send(404,{'error':'Unsupported endpoint'})
    def do_POST(self):
        if self.path!='/api/chat':return self._send(404,{'error':'Unsupported endpoint'})
        try:
            n=int(self.headers.get('Content-Length','0'))
            if n<1 or n>18_000_000:raise ValueError('Invalid request size')
            payload=json.loads(self.rfile.read(n));messages=[]
            for m in payload.get('messages',[]):
                content=str(m.get('content',''))
                if m.get('images'):
                    if len(m['images'])>8:raise ValueError('Too many images')
                    blocks=[{'type':'text','text':content}]
                    for encoded in m['images']:
                        if not isinstance(encoded,str):continue
                        blocks.append({'type':'image_url','image_url':{'url':'data:image/jpeg;base64,'+encoded}})
                    content=blocks
                messages.append({'role':m.get('role','user'),'content':content})
            opts=payload.get('options') or {}
            answer=completions(messages,temperature=float(opts.get('temperature',.3)),max_tokens=min(1200,int(opts.get('num_predict',700))),json_mode=payload.get('format')=='json')
            self._send(200,{'model':'MarK-Vision-4B','message':{'role':'assistant','content':answer},'done':True})
        except Exception as e:self._send(503,{'error':str(e)[:400]})

def start_services():
    global _STARTED,_proc
    with _start_lock:
        if _STARTED:return
        _STARTED=True
        try:
            server=ThreadingHTTPServer(('127.0.0.1',8798),LegacyHandler)
            threading.Thread(target=server.serve_forever,daemon=True,name='MarK legacy compatibility').start()
        except OSError as exc:print('MarK compatibility unavailable:',exc,flush=True)
        if os.name!='nt' or not BIN.is_file() or not MODEL.is_file() or not MMPROJ.is_file():
            print('MarK local AI model missing. Run MarK_Workspace_PC_Setup_TEST.exe.',flush=True);return
        if health()['ready']:
            print('MarK local model already running; reusing it.',flush=True);return
        log=ROOT/'data'/'mark_model.log';log.parent.mkdir(exist_ok=True)
        cmd=[str(BIN),'--model',str(MODEL),'--mmproj',str(MMPROJ),'--host','127.0.0.1','--port','8799','--ctx-size','4096','--n-gpu-layers','99','--parallel','1']
        try:
            handle=log.open('ab');_proc=subprocess.Popen(cmd,stdout=handle,stderr=subprocess.STDOUT,cwd=str(BIN.parent),creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
            print('MarK Vision startup requested: local port 8799, pid',_proc.pid,flush=True)
        except OSError as exc:print('MarK local model startup failed:',exc,flush=True)

@atexit.register
def _stop_owned_child():
    if _proc is not None and _proc.poll() is None:
        try:_proc.terminate();_proc.wait(timeout=5)
        except Exception:pass
