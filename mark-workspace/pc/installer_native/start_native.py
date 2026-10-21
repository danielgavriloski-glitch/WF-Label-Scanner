"""Native Windows MarK.exe starter; no VBS, CMD, or PowerShell involved."""
from pathlib import Path
import json
import os
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import webbrowser

ROOT = Path(__file__).resolve().parent.parent
PORT = 8765
BASE = f'http://127.0.0.1:{PORT}'


def alert(message, title='MarK / TD AI Studio PRO', error=False):
    try:
        from tkinter import Tk, messagebox
        root = Tk()
        root.withdraw()
        if error: messagebox.showerror(title, message)
        else: messagebox.showinfo(title, message)
        root.destroy()
    except Exception:
        print(title + ': ' + message, file=sys.stderr)


def health(path):
    try:
        with urllib.request.urlopen(BASE + path, timeout=2) as r:
            return r.status
    except urllib.error.HTTPError as e:
        return e.code
    except (OSError, TimeoutError):
        return None


def main():
    if sys.platform != 'win32':
        raise RuntimeError('Windows only')
    exe = ROOT / 'runtime' / 'python' / 'pythonw.exe'
    if not exe.is_file():
        alert('MarK Python runtime is not installed. Run the Setup EXE again.', error=True)
        return 1
    config = ROOT / 'app' / 'td_local_config.json'
    if not config.exists():
        pin = f'{secrets.randbelow(900000)+100000}'
        config.write_text(json.dumps({'pin': pin, 'allow_remote_control': False}), encoding='utf-8')
        alert('Твојот MarK PIN е: ' + pin + '\nЗапиши го на сигурно место.', title='MarK – прво стартување')
    if health('/_td_ai_health') is not None:
        if health('/api/mark/status') not in (200, 401):
            alert('Портата 8765 е зафатена од друга TD апликација. Затвори ја старата верзија.', error=True)
            return 1
    else:
        env = os.environ.copy()
        env.update({
            'MARK_HOME': str(ROOT),
            'TD_AI_ROOT': str(ROOT / 'workspace'),
            'TD_AI_DESKTOP_MODE': '1',
        })
        (ROOT / 'data').mkdir(parents=True, exist_ok=True)
        log = open(ROOT / 'data' / 'server_log.txt', 'a', encoding='utf-8')
        p = subprocess.Popen([str(exe), str(ROOT / 'app' / 'server.py')],
                             cwd=str(ROOT / 'app'), env=env,
                             stdin=subprocess.DEVNULL, stdout=log, stderr=log)
        (ROOT / 'data' / 'mark_server.pid').write_text(str(p.pid), encoding='utf-8')
        log.close()
        for _ in range(60):
            if health('/_td_ai_health') == 200:
                break
            if p.poll() is not None:
                alert('MarK server stopped unexpectedly. Check data\\server_log.txt', error=True)
                return 1
            time.sleep(1)
        else:
            alert('MarK did not respond. Check data\\server_log.txt', error=True)
            return 1
    os.startfile(BASE)
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as e:
        alert(str(e), error=True)
        sys.exit(1)
