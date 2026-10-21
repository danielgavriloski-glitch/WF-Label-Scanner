"""Apply the presentation update without replacing models or business databases."""
from __future__ import annotations
import argparse
import ast
import hashlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
from zipfile import ZipFile

FILES = [
 'app/mark_core/workspace_ui.py', 'app/mark_core/workspace_ui.css',
 'app/mark_core/workspace_ui.js', 'app/mark_core/workspace_booking.css',
 'app/mark_core/workspace_booking.js', 'app/mark_core/unified_ui.js',
 'installer_native/desktop.py',
]
HOOK = '\n    from .workspace_ui import register as install_workspace_ui\n    install_workspace_ui(app,authed,local_request)\n'

def prepare(root, archive):
    root = Path(root).resolve()
    required = ['app/server.py', 'app/mark_core/routes.py', 'app/mark_core/mark_ui.html',
                'app/mark_core/unified_ui.js', 'installer_native/desktop.py']
    for name in required:
        if not (root/name).is_file():
            raise RuntimeError('Не е препознаена MarK Desktop инсталација: '+name)
    routes = (root/'app/mark_core/routes.py').read_text('utf-8')
    if 'def install_routes(' not in routes:
        raise RuntimeError('Непозната верзија на MarK routes.py; нема направени измени.')
    if 'from .workspace_ui import register' not in routes:
        anchor = '    app.register_blueprint(bp)\n'
        if routes.count(anchor) != 1:
            raise RuntimeError('Непозната структура на MarK routes.py; нема направени измени.')
        routes = routes.replace(anchor, anchor+HOOK, 1)
    ui = (root/'app/mark_core/mark_ui.html').read_text('utf-8')
    if 'id="markSharedBar"' not in ui or 'id="markUnifiedProject"' not in ui:
        raise RuntimeError('Прво е потребна основата MarK Desktop v1.7 или понова компатибилна верзија.')
    with ZipFile(archive) as source:
        data = {name: source.read(name) for name in FILES}
    data['app/mark_core/routes.py'] = routes.encode('utf-8')
    for name, raw in data.items():
        if name.endswith('.py'):
            ast.parse(raw.decode('utf-8'), filename=name)
    return root, data

def apply(root, data):
    stamp = time.strftime('%Y%m%d-%H%M%S')+'-'+str(time.time_ns()%1000000)
    backup = root/'data'/'workspace_update_backups'/stamp
    backup.mkdir(parents=True)
    previous = {}
    changed = []
    try:
        for name, raw in data.items():
            destination = root/name
            old = destination.read_bytes() if destination.exists() else None
            previous[name] = old
            if old == raw:
                continue
            if old is not None:
                target = backup/name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(old)
            destination.parent.mkdir(parents=True, exist_ok=True)
            temp = destination.with_name(destination.name+'.workspace-tmp')
            temp.write_bytes(raw)
            os.replace(temp, destination)
            changed.append(name)
    except Exception:
        for name in reversed(changed):
            destination = root/name
            old = previous[name]
            if old is None:
                destination.unlink(missing_ok=True)
            else:
                destination.write_bytes(old)
        raise
    report = {'version':'2026.10.21-test1', 'changed':changed, 'backup':str(backup),
              'sha256':{name:hashlib.sha256(raw).hexdigest() for name,raw in data.items()},
              'modelsAndBusinessDataModified':False}
    (backup/'update-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    return report

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--target',required=True,type=Path)
    parser.add_argument('--archive',required=True,type=Path)
    parser.add_argument('--check',action='store_true')
    args=parser.parse_args()
    root,data=prepare(args.target,args.archive)
    if args.check:
        print(json.dumps({'compatible':True,'programFiles':list(data),'dataMigration':False}));return 0
    with socket.socket() as probe:
        probe.settimeout(.5)
        if probe.connect_ex(('127.0.0.1',8765))==0:
            raise RuntimeError('MarK е сè уште отворен. Избери Излез во tray менито, па повтори ја надградбата.')
    report=apply(root,data)
    print('Надградбата заврши. Стартувај MarK од постојната икона.')
    print('Програмските датотеки за враќање се во: '+report['backup'])
    return 0

if __name__=='__main__':
    try:
        raise SystemExit(main())
    except Exception as exc:
        print('UPDATE STOPPED: '+str(exc),flush=True)
        raise SystemExit(1)
