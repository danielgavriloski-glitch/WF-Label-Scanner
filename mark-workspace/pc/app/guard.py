"""Shared cooperative pause/stop + global Windows F9/F10 hotkeys.

F9 toggles pause/resume ONLY when AI control is already running or paused.
F10 stops AI control and requests termination of an active edit job.
Mouse movements do not affect control state. No low-level mouse hook.
Only one server process may own hotkeys; status is shown in the UI.
"""
from __future__ import annotations

import ctypes
import json
import os
from pathlib import Path
import threading
import time

FILE = Path(__file__).resolve().parent / 'td_control_state.json'
WRITE_LOCK = threading.RLock()
HOTKEY_LOCK = threading.RLock()
HOTKEY_STATUS = {'status': 'starting', 'details': 'Waiting for Windows hotkey registration', 'pause_key': 'F9', 'stop_key': 'F10', 'last_key': None, 'last_time': None}


def get_hotkey_status():
    with HOTKEY_LOCK:
        return HOTKEY_STATUS.copy()


def _set_hotkey_status(status, details, pressed=None):
    with HOTKEY_LOCK:
        HOTKEY_STATUS['status'] = status
        HOTKEY_STATUS['details'] = details
        if pressed:
            HOTKEY_STATUS['last_key'] = pressed
            HOTKEY_STATUS['last_time'] = time.time()


def mark_automated_input():
    """Legacy compatibility for existing automated click endpoints (no-op).

    The hotkeys are handled by Windows and are not tied to mouse movement.
    """
    return None


def get_state():
    try:
        return json.loads(FILE.read_text(encoding='utf-8'))
    except (FileNotFoundError, ValueError, OSError):
        return {'mode': 'stopped', 'reason': 'Not started'}


def set_state(mode, reason=''):
    if mode not in ('running', 'paused', 'stopped'):
        raise ValueError('Invalid state')
    with WRITE_LOCK:
        tmp = FILE.with_suffix('.tmp')
        tmp.write_text(json.dumps({'mode': mode, 'reason': reason, 'time': time.time()}, ensure_ascii=False), encoding='utf-8')
        os.replace(tmp, FILE)


def wait_for_job():
    """Jobs cooperate at checkpoints. Long running decode/model calls finish first."""
    while True:
        mode = get_state()['mode']
        if mode == 'stopped':
            raise InterruptedError('Stopped by user')
        if mode == 'paused':
            time.sleep(.15)
            continue
        return


def attach_global_hotkeys(on_pause_resume, on_stop):
    """Register system-wide F9 and F10 using Windows RegisterHotKey.

    Returns immediately; registration happens on a daemon thread.
    In case of collision, explicitly report error (UI buttons still work).
    """
    if os.name != 'nt':
        _set_hotkey_status('unavailable', 'Global F9/F10 hotkeys require Windows')
        return False

    def listen():
        from ctypes import wintypes
        user = ctypes.windll.user32
        WM_HOTKEY = 0x0312
        MOD_NOREPEAT = 0x4000
        VK_F9 = 0x78
        VK_F10 = 0x79
        ID_PAUSE = 0x75D9
        ID_STOP = 0x75DA
        user.RegisterHotKey.argtypes = [wintypes.HWND, ctypes.c_int, wintypes.UINT, wintypes.UINT]
        user.RegisterHotKey.restype = wintypes.BOOL
        user.UnregisterHotKey.argtypes = [wintypes.HWND, ctypes.c_int]
        user.UnregisterHotKey.restype = wintypes.BOOL
        user.GetMessageW.argtypes = [ctypes.POINTER(wintypes.MSG), wintypes.HWND, wintypes.UINT, wintypes.UINT]
        user.GetMessageW.restype = ctypes.c_int
        user.TranslateMessage.argtypes = [ctypes.POINTER(wintypes.MSG)]
        user.DispatchMessageW.argtypes = [ctypes.POINTER(wintypes.MSG)]

        # RegisterHotKey associates hotkeys with THIS THREAD, so use hWnd=None.
        p_ok = bool(user.RegisterHotKey(None, ID_PAUSE, MOD_NOREPEAT, VK_F9))
        s_ok = bool(user.RegisterHotKey(None, ID_STOP, MOD_NOREPEAT, VK_F10))
        if not (p_ok and s_ok):
            if p_ok:
                user.UnregisterHotKey(None, ID_PAUSE)
            if s_ok:
                user.UnregisterHotKey(None, ID_STOP)
            _set_hotkey_status('error', 'Windows could not register F9/F10. Another application may use them. Use the on-screen controls.')
            print('HOTKEY ERROR: F9/F10 already used or blocked; UI buttons remain available.', flush=True)
            return
        _set_hotkey_status('active', 'F9 = Пауза/Продолжи; F10 = итно Стоп. Глувчето е слободно.')
        print('HOTKEYS READY: F9 pause/resume; F10 emergency stop (global)', flush=True)
        try:
            msg = wintypes.MSG()
            while True:
                value = user.GetMessageW(ctypes.byref(msg), None, 0, 0)
                if value <= 0:
                    break
                if msg.message == WM_HOTKEY:
                    try:
                        if msg.wParam == ID_PAUSE:
                            _set_hotkey_status('active', 'F9 = Пауза/Продолжи; F10 = итно Стоп.', pressed='F9')
                            on_pause_resume()
                        elif msg.wParam == ID_STOP:
                            _set_hotkey_status('active', 'F9 = Пауза/Продолжи; F10 = итно Стоп.', pressed='F10')
                            on_stop()
                    except Exception as exc:
                        print('HOTKEY CALLBACK ERROR:', repr(exc), flush=True)
                user.TranslateMessage(ctypes.byref(msg))
                user.DispatchMessageW(ctypes.byref(msg))
        finally:
            user.UnregisterHotKey(None, ID_PAUSE)
            user.UnregisterHotKey(None, ID_STOP)
            _set_hotkey_status('stopped', 'Global hotkey listener stopped')

    def safe_listen():
        try:
            listen()
        except Exception as exc:
            _set_hotkey_status('error', str(exc))
            print('HOTKEY ERROR:', repr(exc), flush=True)

    threading.Thread(target=safe_listen, name='td-global-hotkeys', daemon=True).start()
    return True
