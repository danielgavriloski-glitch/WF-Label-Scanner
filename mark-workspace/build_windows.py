"""Cross-build the Win32 bootstrapper and append the complete source payload."""
from pathlib import Path
import argparse
import hashlib
import json
import struct
import subprocess
from zipfile import ZipFile, ZIP_DEFLATED

ROOT=Path(__file__).resolve().parent

def payload(destination):
    with ZipFile(destination,'w',ZIP_DEFLATED,compresslevel=9) as z:
        for top in ['app','brand','installer_native']:
            for item in sorted((ROOT/'pc'/top).rglob('*')):
                if not item.is_file() or '__pycache__' in item.parts or item.suffix=='.pyc':continue
                if item.name=='td_local_config.json':continue
                z.write(item,item.relative_to(ROOT/'pc'))
        for name in ['model_sources.json','third_party_notice.txt']:
            if (ROOT/'pc'/name).is_file():z.write(ROOT/'pc'/name,name)
    return destination

def main():
    p=argparse.ArgumentParser();p.add_argument('--zig',required=True);p.add_argument('--out',type=Path,default=ROOT/'dist');args=p.parse_args()
    args.out.mkdir(parents=True,exist_ok=True)
    src=ROOT/'native_setup';res=args.out/'setup.res'
    subprocess.run([args.zig,'rc','/fo'+str(res),str(src/'setup.rc')],cwd=src,check=True)
    exe=args.out/'MarK_Workspace_PC_Setup_TEST.exe'
    subprocess.run([args.zig,'cc','-target','x86_64-windows-gnu','-ffreestanding','-fno-stack-protector','-fno-builtin','-Os','-nostdlib','-Wl,--subsystem,windows','-Wl,--entry,WinMainCRTStartup',str(src/'setup.c'),str(src/'stack_probe.s'),str(res),'-lkernel32','-luser32','-lshell32','-lole32','-lwintrust','-o',str(exe)],cwd=src,check=True)
    archive=payload(args.out/'workspace_payload.zip')
    with exe.open('ab') as f:f.write(archive.read_bytes());f.write(b'MARKZP05'+struct.pack('<Q',archive.stat().st_size))
    report={'file':exe.name,'bytes':exe.stat().st_size,'sha256':hashlib.sha256(exe.read_bytes()).hexdigest(),'windowsArchitecture':'x64','modes':['full-installation','update-existing'],'fullInstallationDownloadsRuntimeAndModels':True,'physicalWindowsTested':False}
    exe.with_suffix('.validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))

if __name__=='__main__':main()
