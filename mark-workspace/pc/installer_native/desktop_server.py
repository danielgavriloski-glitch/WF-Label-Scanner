"""Serve original TD/MarK routes on loopback only (desktop security default).
Existing old Studio installation is not modified.
"""
import os,sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/'app'))
import server as s
from guard import attach_global_hotkeys

s.set_state('stopped','Desktop server started')
def pause_resume():
    mode=s.get_state()['mode']
    if mode=='running':s.set_state('paused','F9: paused')
    elif mode=='paused':s.set_state('running','F9: resumed')
def stop():
    s.set_state('stopped','F10: stopped')
    with s.lock:
        proc=s.job.get('proc')
        if proc is not None and proc.poll() is None:
            proc.terminate()
            s.job['log'].append('F10: emergency stop')
try: attach_global_hotkeys(pause_resume,stop)
except Exception as exc:print('Hotkey availability:',repr(exc),flush=True)
print('MarK Desktop v1 local server: http://127.0.0.1:8765',flush=True)
s.app.run(host='127.0.0.1',port=8765,debug=False,threaded=True,use_reloader=False)
