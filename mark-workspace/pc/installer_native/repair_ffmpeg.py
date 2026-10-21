"""Safe MarK v0.5 FFmpeg repair in the application's private Python runtime.

Does not touch existing projects, camera media, user data, or Windows security policies.
"""
from __future__ import annotations

from pathlib import Path
import os
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent.parent
LOG = ROOT / 'data' / 'ffmpeg_repair_log.txt'


def log(message):
    LOG.parent.mkdir(parents=True, exist_ok=True)
    message = str(message)
    print(message, flush=True)
    with LOG.open('a', encoding='utf-8') as f:
        f.write(time.strftime('%Y-%m-%d %H:%M:%S') + ' ' + message + '\n')


def verify_ffmpeg():
    import imageio_ffmpeg
    executable = Path(imageio_ffmpeg.get_ffmpeg_exe())
    if not executable.is_file():
        raise RuntimeError('FFmpeg executable not found: ' + str(executable))
    opts = {'capture_output': True, 'text': True, 'timeout': 25}
    if os.name == 'nt':
        opts['creationflags'] = subprocess.CREATE_NO_WINDOW
    result = subprocess.run([str(executable), '-version'], **opts)
    if result.returncode != 0 or 'ffmpeg version' not in result.stdout.lower():
        raise RuntimeError('Bundled FFmpeg failed its startup self-test: ' + result.stderr[-400:])
    log('FFmpeg validated: ' + str(executable))
    log(result.stdout.splitlines()[0])
    return str(executable)


def main():
    python = ROOT / 'runtime' / 'python' / 'python.exe'
    if os.name != 'nt' or not python.is_file() or not (ROOT / 'app' / 'server.py').is_file():
        raise RuntimeError('Select the existing MarK installation folder (contains app and runtime).')
    if Path(sys.executable).resolve() != python.resolve():
        raise RuntimeError('Run with the private MarK Python, not the global system Python.')
    log('MarK v0.5 FFmpeg Repair – preserving all original projects and application settings.')
    cmd = [str(python), '-m', 'pip', 'install', '--disable-pip-version-check',
           '--no-input', '--only-binary=:all:', 'imageio-ffmpeg>=0.5.1,<1']
    log('Installing a prebuilt wheel dependency from configured pip package index...')
    result = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True,
                            encoding='utf-8', errors='replace', timeout=600)
    with LOG.open('a', encoding='utf-8') as f:
        f.write(result.stdout + '\n' + result.stderr + '\n')
    if result.returncode:
        log('Package install failed. Details saved in data\\ffmpeg_repair_log.txt')
        raise RuntimeError('pip failed, exit code: ' + str(result.returncode))
    log('Checking FFmpeg executable...')
    verify_ffmpeg()
    log('SUCCESS. Close and reopen MarK before running Wedding Analysis again.')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception as e:
        log('FAILED: ' + str(e))
        sys.exit(1)
