"""TD AI Studio 1.8: very limited on-screen Premiere operator smoke test.

Flow: screenshot -> local Ollama identifies File menu -> controlled mouse click
-> screenshot verified by local Ollama -> Esc -> screenshot verified.
No destructive commands, no montage manipulation, no remote cloud upload.
"""
from __future__ import annotations
import base64
import io
import json
import os
import threading
import time
import urllib.request
from pathlib import Path

MODEL = 'qwen2.5vl:3b'
SERVER = 'http://127.0.0.1:8798/api/chat'


def install_operator(globals_dict):
    from flask import request, jsonify
    from guard import get_state, set_state
    app = globals_dict['app']
    api_guard = globals_dict['api_guard']
    local_request = globals_dict['local_request']
    CFG = globals_dict['CFG']
    main_job = globals_dict['job']
    main_lock = globals_dict['lock']
    state = {'phase': 'idle', 'message': 'Ready for first safe screen-control test.',
             'history': [], 'started': None, 'ended': None}
    lock = threading.RLock()

    def log(message):
        with lock:
            state['message'] = str(message)
            state['history'].append(time.strftime('%H:%M:%S') + '  ' + str(message))
            state['history'] = state['history'][-60:]

    def gate():
        # Global F9/F10 are registered by TD AI Studio. Do not emit input while paused/stopped.
        while True:
            mode = get_state().get('mode', 'stopped')
            if mode == 'stopped':
                raise InterruptedError('Stopped with F10 or STOP. No more mouse/keyboard actions.')
            if mode == 'running':
                return
            time.sleep(.15)

    def model(image_bytes, task):
        # One screenshot per request: only local Ollama.
        request_json = {'model': MODEL, 'stream': False, 'format': 'json',
            'options': {'temperature': 0.0, 'num_ctx': 4096},
            'messages': [
                {'role': 'system', 'content': (
                    'You are a careful computer-vision observer for Adobe Premiere Pro. '
                    'The attached image is a screenshot or crop of the Premiere desktop. '
                    'Only describe what is VISIBLE, never invent missing buttons. '
                    'Return VALID JSON ONLY. Do not instruct destructive actions.')},
                {'role': 'user', 'content': task, 'images': [base64.b64encode(image_bytes).decode('ascii')]}]}
        req = urllib.request.Request(SERVER, data=json.dumps(request_json).encode('utf-8'),
                                  headers={'Content-Type': 'application/json'}, method='POST')
        with urllib.request.urlopen(req, timeout=95) as response:
            result = json.load(response)
        text = result.get('message', {}).get('content', '{}')
        try:
            value = json.loads(text)
        except (TypeError, ValueError):
            raise RuntimeError('Vision model did not return valid JSON: '+str(text)[:180])
        if not isinstance(value, dict):
            raise RuntimeError('Vision model returned an unexpected format.')
        return value

    def shot(rect=None):
        import mss
        from PIL import Image
        with mss.mss() as screen:
            if rect:
                image_rect = dict(left=int(rect[0]), top=int(rect[1]), width=int(rect[2]), height=int(rect[3]))
            else:
                image_rect = screen.monitors[1]
            img = screen.grab(image_rect)
            picture = Image.frombytes('RGB', img.size, img.rgb)
            if not rect:
                picture.thumbnail((1280, 800))
            out = io.BytesIO()
            picture.save(out, format='JPEG', quality=78)
            return out.getvalue()

    def bool_field(out, name):
        return out.get(name) is True

    def premiere_window():
        import pygetwindow as gw
        matches = [w for w in gw.getAllWindows() if 'premiere' in (w.title or '').lower()
                   and 'developer tool' not in (w.title or '').lower() and w.width >= 700 and w.height >= 450]
        if not matches:
            raise RuntimeError('Open Premiere Pro 2026 with a blank TEST project first.')
        return sorted(matches, key=lambda w: w.width*w.height, reverse=True)[0]

    def run():
        import pyautogui as pg
        pg.FAILSAFE = True   # mouse to top-left = PyAutoGUI safety stop
        try:
            gate()
            log('1/5: Focusing Premiere — no editing commands yet.')
            win = premiere_window()
            if getattr(win, 'isMinimized', False):
                win.restore()
            win.activate()
            time.sleep(1.0)
            gate()
            # Only a short strip near the Premiere File/Edit menu is sent to vision.
            strip_x = max(0, int(win.left))
            strip_y = max(0, int(win.top))
            strip_w = min(850, max(300, int(win.width)))
            strip_h = min(160, max(80, int(win.height)))
            log('2/5: Local vision locating File menu on Premiere screen.')
            first = model(shot((strip_x, strip_y, strip_w, strip_h)),
                'In this top-left portion of Adobe Premiere window, find only the File menu in its top menu bar. '
                'Return JSON keys: premiere_visible (boolean), file_visible (boolean), '
                'x (number between 0 and 1, fractional image coordinate of center of File label), '
                'y (number between 0 and 1), confidence (number 0..1), reason (short text). '
                'If File is not clearly visible, set file_visible false and coordinates null.')
            if not bool_field(first,'file_visible'):
                raise RuntimeError('Vision cannot clearly see Premiere File menu: '+str(first.get('reason',''))[:160])
            try:
                x, y, conf = float(first['x']), float(first['y']), float(first['confidence'])
            except (TypeError, ValueError, KeyError):
                raise RuntimeError('Vision did not provide usable coordinates/confidence.')
            # Extremely constrained click region: Premiere top menu ONLY.
            if not (0.0 < x < .24 and 0.0 < y < .65 and conf >= .75):
                raise RuntimeError('File menu location uncertain; no click performed. '+repr(first)[:190])
            target_x = int(strip_x + x*strip_w)
            target_y = int(strip_y + y*strip_h)
            gate()
            import pygetwindow as gw
            active_title = (gw.getActiveWindowTitle() or '').lower()
            if 'premiere' not in active_title:
                raise RuntimeError('Premiere is no longer the active window; click cancelled.')
            log('3/5: Clicking File menu in Premiere using mouse.')
            pg.click(target_x, target_y)
            time.sleep(.7)
            gate()
            log('4/5: Checking opened menu using local AI Vision.')
            check = model(shot(),
                'Does this screenshot visibly show Adobe Premiere with its File menu DROPDOWN OPEN? '
                'Return JSON with keys file_menu_open (boolean), confidence (0..1), reason (short). '
                'Say false if unclear. Do not infer from expected clicks.')
            menu_open = bool_field(check,'file_menu_open') and float(check.get('confidence',0) or 0)>=.65
            gate()
            pg.press('esc')
            time.sleep(.25)
            log('5/5: Escape sent. File menu observed open: '+('YES' if menu_open else 'NOT CONFIRMED'))
            with lock:
                state['phase'] = 'finished' if menu_open else 'needs_review'
                state['ended'] = time.time()
            if menu_open:
                log('SUCCESS: AI saw File, clicked it with mouse, verified the menu, and pressed ESC.')
            else:
                log('The mouse/keyboard step ran, but Vision could not confirm the menu. No edit was made.')
        except InterruptedError as err:
            with lock:
                state['phase'] = 'stopped'
                state['ended'] = time.time()
            log(str(err))
        except Exception as err:
            with lock:
                state['phase'] = 'failed'
                state['ended'] = time.time()
            log('STOPPED SAFELY: '+type(err).__name__+': '+str(err)[:260])
        finally:
            # F10 remains authoritative; after a completed test, disarm mouse control.
            with lock:
                is_done = state['phase'] in ('finished','needs_review','failed','stopped')
            if is_done and get_state().get('mode') in ('running','paused'):
                set_state('stopped','TD Operator 1.8 test complete')

    @app.route('/api/operator18/status')
    def td_operator18_status():
        err=api_guard()
        if err:return err
        with lock:
            return jsonify(state.copy())

    @app.route('/api/operator18/start',methods=['POST'])
    def td_operator18_start():
        err=api_guard()
        if err:return err
        if os.name!='nt':return jsonify(error='Only available on Windows.'),501
        if not local_request() and not CFG.get('allow_remote_control',False):
            return jsonify(error='Start on the PC first, or allow remote controls locally.'),403
        with main_lock:
            if main_job.get('state')=='running':
                return jsonify(error='Wait for current auto-edit analysis to finish before operator test.'),409
        with lock:
            if state['phase']=='running':return jsonify(error='Operator already running.'),409
            state.update(phase='running',message='Starting Premiere test...',started=time.time(),ended=None,history=[])
        # Explicit user pressing START authorizes exactly this limited non-destructive test.
        set_state('running','TD Operator 1.8 safe test started')
        threading.Thread(target=run,daemon=True,name='td-premiere-operator-v18').start()
        return jsonify(ok=True,reply='Operator test started: Screen -> File menu click -> AI verification -> Esc.')

    @app.route('/api/operator18/stop',methods=['POST'])
    def td_operator18_stop():
        err=api_guard()
        if err:return err
        set_state('stopped','TD Operator 1.8 stopped by user')
        with lock:
            if state['phase']=='running':state['phase']='stopping'
        return jsonify(ok=True,reply='Stop requested. Current screenshot/AI inference may finish before exiting.')
