"""Detect 1..N local camera/source folders without copying originals.
Only known AI-generated output directories are excluded. Symlink directories
are not followed; no file is modified or deleted.
"""
from __future__ import annotations
import os
import re
from pathlib import Path

VIDEO_EXT={'.mp4','.mov','.avi','.m4v','.mkv','.mts','.m2ts'}
IGNORE_DIRS={'td_ai_edit','06_output','premiere_auto_edit',
             'slow_motion_derived','__pycache__','.git','.venv','venv',
             'td_ai_output','td_ai_cache','td_ai_previews'}

def natural_key(value):
    return [int(s) if s.isdigit() else s.casefold() for s in re.split(r'(\d+)',str(value))]

def discover_sources(wedding_folder):
    root=Path(wedding_folder).expanduser().resolve()
    if not root.is_dir():raise FileNotFoundError(str(root))
    groups={}
    for walkroot, dirs, filenames in os.walk(root,topdown=True,followlinks=False):
        dirs[:]=sorted((d for d in dirs if d.casefold() not in IGNORE_DIRS
                         and not d.startswith('.') and not d.casefold().startswith('td_ai_')),
                       key=natural_key)
        here=Path(walkroot)
        video_files=sorted((here/name for name in filenames
                         if Path(name).suffix.casefold() in VIDEO_EXT and (here/name).is_file()),
                         key=lambda p:natural_key(p.name))
        if not video_files:continue
        label=str(here.relative_to(root)).replace('\\','/')
        if label=='.':label='ROOT (директно во папката)'
        groups[label]=video_files
    files=[p for name in sorted(groups,key=natural_key) for p in groups[name]]
    return {'root':root,'files':files,
            'folders':[{'name':name,'count':len(groups[name])} for name in sorted(groups,key=natural_key)],
            'video_count':len(files),'folder_count':len(groups)}
