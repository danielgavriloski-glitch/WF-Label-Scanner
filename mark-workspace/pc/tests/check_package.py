"""Static package and local runtime smoke tests, no external network."""
from pathlib import Path
import json,sys,urllib.request,threading
ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/'app'
for required in ['server.py','static/index.html','td_pro_ui.html','td_wedding_chapters.py','td_photo_module.py','TDPhotoPRO.lrplugin/Info.lua','TD_Premiere_Bridge_uxp/manifest.json','mark_core/runtime.py','mark_core/routes.py','mark_core/mark_ui.html']:
    assert (APP/required).is_file(),required
assert 'MarK ENGINE' in (APP/'static/index.html').read_text('utf-8')
assert 'MarK' in (APP/'mark_core/mark_ui.html').read_text('utf-8')
assert 'mark_install_routes(app, authed, local_request)' in (APP/'server.py').read_text('utf-8')
assert 'TD_AI_ROOT' in (ROOT/'START_MarK_TD.bat').read_text('utf-8')
assert 'Qwen3VL-4B-Instruct-Q4_K_M.gguf' in (ROOT/'installer/Install-MarK.ps1').read_text('utf-8')
sys.path.insert(0,str(APP))
from mark_core import runtime
assert not runtime.health()['ready'] or runtime.health()['binary']
print('PASS: core files, old TD interface, plugin sources, installer, safe runtime status')
