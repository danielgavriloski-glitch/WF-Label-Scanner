from pathlib import Path
import ast, zipfile
r=Path(__file__).resolve().parents[1]
assert (r/'Setup_MarK.vbs').exists() and (r/'START_MarK.vbs').exists()
for py in (r/'app').rglob('*.py'):
 ast.parse(py.read_text('utf-8'),filename=str(py))
ps=(r/'installer'/'Install-MarK-Worker.ps1').read_text('utf-8-sig')
assert 'per_page=20' in ps and 'bin-win-' in ps and 'Get-FileHash' in ps
assert 'TargetDir=' in ps and 'Include_tcltk=1' in ps
assert 'Qwen3VL-4B-Instruct-Q4_K_M.gguf' in ps
ui=(r/'installer'/'Setup-MarK-GUI.ps1').read_text('utf-8-sig')
assert 'FolderBrowserDialog' in ui and 'GetDrives' in ui
assert 'ИНСТАЛИРАЈ MarK' in ui
print('PASS: source code, GUI files, engine download fallback, model config, disk-picker')
