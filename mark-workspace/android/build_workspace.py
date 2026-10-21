"""Build a clearly separate TEST APK; original-app signing requires its own key."""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import json
import re
import struct
import subprocess
from zipfile import ZipFile,ZipInfo,ZIP_STORED,ZIP_DEFLATED
from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.serialization import load_pem_private_key,Encoding,PublicFormat
from cryptography.x509.oid import NameOID
import apk_helpers as h
from build_v620 import attributes

ROOT=Path(__file__).resolve().parent

def strings(raw):
    off=struct.unpack_from('<H',raw,2)[0];kind,header,size=struct.unpack_from('<HHI',raw,off)
    count,_,flags,start,_=struct.unpack_from('<IIIII',raw,off+8);out=[]
    for i in range(count):
        p=off+start+struct.unpack_from('<I',raw,off+header+4*i)[0]
        if flags&0x100:
            _,p=h.decode_len8(raw,p);n,p=h.decode_len8(raw,p);value=raw[p:p+n].decode('utf-8')
        else:n,p=h.decode_len16(raw,p);value=raw[p:p+2*n].decode('utf-16le')
        out.append(value)
    return out

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--key',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    baseline=ROOT/'baseline/TD_Booking_v6_19.apk'
    if hashlib.sha256(baseline.read_bytes()).hexdigest()!='0020830d0bb9da555011c908505eb1230bd1524c85604b391a9e545be635a8f6':raise ValueError('Unexpected baseline APK')
    key=load_pem_private_key(args.key.read_bytes(),None)
    certificate=ROOT/'workspace_test_certificate.pem'
    if certificate.exists():cert=x509.load_pem_x509_certificate(certificate.read_bytes())
    else:
        subject=x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'MarK Workspace TEST')])
        cert=x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(key.public_key()).serial_number(0x2026102101).not_valid_before(dt.datetime(2020,1,1,tzinfo=dt.timezone.utc)).not_valid_after(dt.datetime(2050,1,1,tzinfo=dt.timezone.utc)).sign(key,hashes.SHA256())
        certificate.write_bytes(cert.public_bytes(Encoding.PEM))
    if cert.public_key().public_bytes(Encoding.DER,PublicFormat.SubjectPublicKeyInfo)!=key.public_key().public_bytes(Encoding.DER,PublicFormat.SubjectPublicKeyInfo):raise ValueError('TEST certificate and key differ')
    h.key,h.cert=key,cert;h.cert_der=cert.public_bytes(Encoding.DER);h.pub_der=key.public_key().public_bytes(Encoding.DER,PublicFormat.SubjectPublicKeyInfo);h.ALG=0x0103;h.V2_ID=0x7109871a;h.MAGIC=b'APK Sig Block 42'
    with ZipFile(baseline) as z:original={n:z.read(n) for n in z.namelist()}
    data=dict(original);manifest=data['AndroidManifest.xml'];old=attributes(manifest);package='mk.td.booking6.workspace.test'
    replacements={v:package+v[len('mk.td.booking6'):] for v in strings(manifest) if v=='mk.td.booking6' or v.startswith('mk.td.booking6.')}
    replacements['6.19']='6.22 TEST'
    for value in strings(manifest):
        if value in ['TD Booking','TD Booking MarK','MarK']:replacements[value]='MarK Workspace TEST'
    data['AndroidManifest.xml']=h.patch_axml_strings(manifest,replacements,{'versionCode':82})
    actual=attributes(data['AndroidManifest.xml']);assert actual['package']==package and actual['versionCode']==82
    for p in (ROOT/'assets').rglob('*'):
        if p.is_file():data['assets/'+p.relative_to(ROOT/'assets').as_posix()]=p.read_bytes()
    assert data['classes.dex']==original['classes.dex'] and data['resources.arsc']==original['resources.arsc']
    args.out.parent.mkdir(parents=True,exist_ok=True);unsigned=args.out.with_suffix('.unsigned.apk')
    with ZipFile(unsigned,'w',allowZip64=False) as z:
        for name in ['resources.arsc']+[n for n in data if n!='resources.arsc']:
            if name=='META-INF/MANIFEST.MF' or re.match(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)$',name,re.I):continue
            info=ZipInfo(name,date_time=(1980,1,1,0,0,0));info.create_system=0;info.compress_type=ZIP_STORED if name=='resources.arsc' else ZIP_DEFLATED;z.writestr(info,data[name])
    args.out.write_bytes(h.sign_v2(unsigned.read_bytes()));unsigned.unlink()
    signature=h.verify_v2(args.out.read_bytes())
    verified=subprocess.run(['java','-jar',str(ROOT/'tools/apksigner.jar'),'verify','--verbose',str(args.out)],check=True,capture_output=True,text=True)
    with ZipFile(args.out) as z:
        assert z.testzip() is None
        r=z.getinfo('resources.arsc');assert (r.header_offset+30+len(r.filename.encode())+len(r.extra))%4==0
    report={'file':args.out.name,'package':package,'versionName':'6.22 TEST','versionCode':82,'sha256':hashlib.sha256(args.out.read_bytes()).hexdigest(),'bytes':args.out.stat().st_size,'signatureVerified':True,'nativeCodeUnchanged':True,'separateInstallationFrom620':True,'updatesOriginal620':False,'physicalAndroidTested':False,'signature':signature}
    args.out.with_suffix('.validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))

if __name__=='__main__':main()
