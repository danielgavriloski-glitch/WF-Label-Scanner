"""MarK setup worker: standard Python, no PowerShell, no Windows security overrides.

Used only after an official signed Python installer was verified by WinVerifyTrust
and executed from the native Windows setup program. All downloaded artifacts
originate from a clearly specified upstream; llama archive digest is verified.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
LOG_DIR = ROOT / 'data'
LOG_DIR.mkdir(exist_ok=True, parents=True)
LOG = LOG_DIR / 'install_log.txt'
HEADERS = {"User-Agent": "MarK-Native-Setup/0.5 (+https://github.com/ggml-org/llama.cpp)", "Accept": "application/json"}


def log(message):
    print(message, flush=True)
    with LOG.open('a', encoding='utf-8') as f:
        f.write(time.strftime('%Y-%m-%d %H:%M:%S') + ' ' + message + '\n')


def run(*args):
    command = [str(a) for a in args]
    log('Running: ' + ' '.join(command[:4]))
    with LOG.open('a', encoding='utf-8') as f:
        p = subprocess.run(command, cwd=str(ROOT), stdout=f, stderr=subprocess.STDOUT, check=False)
    if p.returncode != 0:
        raise RuntimeError('Exit code ' + str(p.returncode) + ': ' + command[0] + '. Check data\\install_log.txt')


def json_get(url):
    req = Request(url, headers=HEADERS)
    with urlopen(req, timeout=65) as r:
        return json.load(r)


def download(url, dest, minimum, expected_digest=None, validate_gguf=False):
    dest = Path(dest)
    dest.parent.mkdir(parents=True, exist_ok=True)
    digest = expected_digest.lower() if expected_digest else None
    if digest and not re.fullmatch(r'[0-9a-f]{64}', digest):
        raise ValueError('Unrecognized expected SHA-256 digest')

    def verify(path):
        if not path.exists() or path.stat().st_size < minimum:
            return False
        if validate_gguf:
            with path.open('rb') as f:
                if f.read(4) != b'GGUF':
                    return False
        if digest:
            sha = hashlib.sha256()
            with path.open('rb') as f:
                for block in iter(lambda: f.read(2 * 1024 * 1024), b''):
                    sha.update(block)
            if sha.hexdigest() != digest:
                return False
        return True

    if verify(dest):
        log('Verified existing: ' + dest.name)
        return
    part = dest.with_name(dest.name + '.partial')
    for retry in range(4):
        start = part.stat().st_size if part.exists() else 0
        hdr = {**HEADERS}
        if start:
            hdr['Range'] = f'bytes={start}-'
        try:
            req = Request(url, headers=hdr)
            with urlopen(req, timeout=75) as response:
                resumed = (response.status == 206)
                if start and not resumed:
                    start = 0
                mode = 'ab' if resumed and start else 'wb'
                count = start
                last = 0
                with part.open(mode) as file:
                    while chunk := response.read(1024 * 1024):
                        file.write(chunk)
                        count += len(chunk)
                        mbs = count // (100 * 1024 * 1024)
                        if mbs > last:
                            last = mbs
                            log(f'{dest.name}: {count / 1024 / 1024:.0f} MiB downloaded')
            if verify(part):
                part.replace(dest)
                log('Downloaded and checked: ' + dest.name)
                return
            raise RuntimeError('Download incomplete or SHA-256/GGUF validation failed')
        except Exception as exc:
            log(f'Download attempt {retry+1}/4 failed: {exc}')
            if retry == 3:
                raise
            time.sleep(2 + retry * 3)
    raise RuntimeError('Unable to download ' + dest.name)


def safe_extract(zip_path, to_dir):
    to_dir = Path(to_dir).resolve()
    to_dir.mkdir(parents=True, exist_ok=True)
    with ZipFile(zip_path) as z:
        for item in z.infolist():
            if item.is_dir():
                continue
            candidate = (to_dir / item.filename.replace('\\', '/')).resolve()
            if not candidate.is_relative_to(to_dir):
                raise ValueError('Invalid archive path: ' + item.filename)
            if item.file_size > 1024 * 1024 * 1024:
                raise ValueError('Unexpectedly large binary in runtime archive')
        z.extractall(to_dir)


def setup_llama():
    dest = ROOT / 'runtime' / 'llama'
    server = dest / 'llama-server.exe'
    if server.is_file():
        log('Existing llama-server.exe found')
        return
    log('Looking for official llama.cpp Windows x64 release ...')
    releases = json_get('https://api.github.com/repos/ggml-org/llama.cpp/releases?per_page=20')
    if not isinstance(releases, list):
        raise RuntimeError('Unrecognized GitHub release response')
    candidates = []
    for kind in ('vulkan', 'cpu'):
        for release in releases:
            for asset in release.get('assets', []):
                name = asset.get('name', '')
                pat = r'-bin-win-(?:' + ('vulkan' if kind == 'vulkan' else 'cpu|avx2|avx') + r')-x64\.zip$'
                if re.search(pat, name, re.I):
                    candidates.append((kind, asset))
        if candidates:
            break
    if not candidates:
        raise RuntimeError('No supported official llama.cpp Windows x64 archive in recent GitHub releases')
    errors = []
    for kind, asset in candidates:
        digest = asset.get('digest', '')
        if not isinstance(digest, str) or not re.fullmatch(r'sha256:[0-9a-fA-F]{64}', digest):
            errors.append(asset['name'] + ' lacks SHA-256 metadata')
            continue
        url = asset.get('browser_download_url', '')
        if not url.startswith('https://github.com/ggml-org/llama.cpp/releases/download/'):
            continue
        archive = ROOT / 'cache' / asset['name']
        try:
            log('Downloading signed-source release archive: ' + asset['name'])
            download(url, archive, 1_000_000, digest.split(':')[1])
            stage = ROOT / 'cache' / 'llama_unpacked'
            stage.mkdir(parents=True, exist_ok=True)
            safe_extract(archive, stage)
            found = next(stage.rglob('llama-server.exe'), None)
            if found is None:
                raise RuntimeError('llama-server.exe missing from official archive')
            dest.mkdir(parents=True, exist_ok=True)
            for item in found.parent.iterdir():
                if item.is_file():
                    shutil.copy2(item, dest / item.name)
            if not server.exists():
                raise RuntimeError('Unable to stage llama-server.exe')
            shutil.rmtree(stage)
            log('Installed llama runtime: ' + kind)
            return
        except Exception as e:
            errors.append(str(e))
            log('Runtime candidate failed: ' + str(e))
    raise RuntimeError('llama.cpp runtime could not be verified/installed: ' + '; '.join(errors[:3]))


def get_model_sha(filename):
    # Hugging Face metadata provides LFS SHA-256 when available.
    manifest = json_get('https://huggingface.co/api/models/Qwen/Qwen3-VL-4B-Instruct-GGUF/tree/main?recursive=false&expand=true')
    for item in manifest:
        if item.get('path') == filename:
            lfs = item.get('lfs') or {}
            sha = lfs.get('oid')
            if isinstance(sha, str) and re.fullmatch('[0-9a-fA-F]{64}', sha):
                return sha.lower()
    raise RuntimeError('No trusted SHA-256 from upstream for ' + filename)


def models():
    base = 'https://huggingface.co/Qwen/Qwen3-VL-4B-Instruct-GGUF/resolve/main/'
    for name, size in [
        ('Qwen3VL-4B-Instruct-Q4_K_M.gguf', 2_300_000_000),
        ('mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf', 400_000_000),
    ]:
        log('Checking SHA-256 from Hugging Face: ' + name)
        sha = get_model_sha(name)
        download(base + name, ROOT / 'models' / name, size, sha, validate_gguf=True)


def main():
    if sys.platform != 'win32':
        raise RuntimeError('Windows is required')
    log('MarK Native Setup v0.5 – Python runtime worker. Security settings remain unchanged.')
    python = ROOT / 'runtime' / 'python' / 'python.exe'
    if not python.exists():
        raise RuntimeError('Python runtime not installed or missing')
    log('Installing Python packages to private MarK runtime ...')
    run(python, '-m', 'pip', 'install', '--disable-pip-version-check', '-r', ROOT / 'app' / 'requirements.txt')
    try:
        run(python, '-m', 'pip', 'install', '--disable-pip-version-check', 'rawpy')
    except Exception as exc:
        log('Optional RAW support install failed: ' + str(exc))
    from repair_ffmpeg import verify_ffmpeg
    log('Verifying bundled FFmpeg before video analysis ...')
    verify_ffmpeg()
    setup_llama()
    models()
    for name in ('workspace/01_INPUT_VIDEO', 'workspace/02_MUSIC', 'workspace/06_OUTPUT', 'data'):
        (ROOT / name).mkdir(parents=True, exist_ok=True)
    (ROOT / 'installation.json').write_text(json.dumps({
        'product': 'MarK TD AI Studio PRO',
        'version': '0.5-native-ffmpeg-test',
        'root': str(ROOT),
        'installed_at': time.strftime('%Y-%m-%dT%H:%M:%S'),
        'signed': False,
    }, indent=2), encoding='utf-8')
    try:
        import win32com.client
        shell=win32com.client.Dispatch('WScript.Shell')
        folders=[Path(shell.SpecialFolders('Desktop')),Path(os.environ['APPDATA'])/'Microsoft/Windows/Start Menu/Programs/MarK']
        for folder in folders:
            folder.mkdir(parents=True,exist_ok=True)
            shortcut=shell.CreateShortcut(str(folder/'MarK.lnk'))
            shortcut.TargetPath=str(ROOT/'MarK.exe')
            shortcut.WorkingDirectory=str(ROOT)
            shortcut.IconLocation=str(ROOT/'brand/MarK.ico')
            shortcut.Save()
    except Exception as exc:
        log('Shortcut creation failed: '+str(exc))
    log('SUCCESS. MarK.exe in the installation folder starts the application.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as exc:
        log('INSTALLATION FAILED: ' + str(exc))
        sys.exit(1)
