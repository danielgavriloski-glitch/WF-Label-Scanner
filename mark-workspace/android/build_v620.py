"""Build TD Booking 6.20 by updating only assets and version of verified 6.19."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import zipfile
from cryptography.hazmat.primitives.serialization import pkcs12, Encoding, PublicFormat
import apk_helpers as helpers

ROOT=Path(__file__).resolve().parent
BASE_SHA='0020830d0bb9da555011c908505eb1230bd1524c85604b391a9e545be635a8f6'
CERT_SHA='76651c8cf1522f1fab74d211162eeb8122695e07490ad57ecbdd578b09a58212'

def attributes(raw):
    off=struct.unpack_from('<H',raw,2)[0]
    kind,header,size=struct.unpack_from('<HHI',raw,off)
    assert kind==1
    count,styles,flags,start,style=struct.unpack_from('<IIIII',raw,off+8)
    strings=[]
    for i in range(count):
        p=off+start+struct.unpack_from('<I',raw,off+header+4*i)[0]
        if flags&0x100:
            _,p=helpers.decode_len8(raw,p);length,p=helpers.decode_len8(raw,p)
            text=raw[p:p+length].decode('utf-8')
        else:
            length,p=helpers.decode_len16(raw,p);text=raw[p:p+2*length].decode('utf-16le')
        strings.append(text)
    off+=size
    while off<len(raw):
        kind,header,size=struct.unpack_from('<HHI',raw,off)
        if kind==0x0102 and strings[struct.unpack_from('<I',raw,off+20)[0]]=='manifest':
            start,step,count=struct.unpack_from('<HHH',raw,off+24);result={};p=off+16+start
            for _ in range(count):
                ns,name,raw_value=struct.unpack_from('<III',raw,p)
                dtype=raw[p+15];value=struct.unpack_from('<I',raw,p+16)[0]
                result[strings[name]]=strings[value] if dtype==3 else value;p+=step
            return result
        off+=size
    raise ValueError('Manifest root missing')

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--baseline',type=Path,default=ROOT/'baseline/TD_Booking_v6_19.apk')
    parser.add_argument('--assets',type=Path,default=ROOT/'assets')
    parser.add_argument('--keystore',type=Path,required=True)
    parser.add_argument('--out',type=Path,default=ROOT/'TD_Booking_v6_20_CALENDAR_PAGES.apk')
    args=parser.parse_args()
    assert hashlib.sha256(args.baseline.read_bytes()).hexdigest()==BASE_SHA,'Unexpected baseline'
    password=os.environ.get('TD_BOOKING_KEY_PASSWORD')
    if not password:raise SystemExit('Set TD_BOOKING_KEY_PASSWORD privately.')
    key,cert,_=pkcs12.load_key_and_certificates(args.keystore.read_bytes(),password.encode())
    helpers.key,helpers.cert=key,cert
    helpers.cert_der=cert.public_bytes(Encoding.DER)
    helpers.pub_der=cert.public_key().public_bytes(Encoding.DER,PublicFormat.SubjectPublicKeyInfo)
    helpers.ALG=0x0103;helpers.V2_ID=0x7109871a;helpers.MAGIC=b'APK Sig Block 42'
    assert hashlib.sha256(helpers.cert_der).hexdigest()==CERT_SHA,'Wrong signing certificate'
    assert helpers.verify_v2(args.baseline.read_bytes())['cert_sha256']==CERT_SHA
    with zipfile.ZipFile(args.baseline) as z:original={n:z.read(n) for n in z.namelist()}
    payload=dict(original)
    before=attributes(payload['AndroidManifest.xml'])
    assert before['package']=='mk.td.booking6' and before['versionCode']==79 and before['versionName']=='6.19'
    payload['AndroidManifest.xml']=helpers.patch_axml_strings(payload['AndroidManifest.xml'],{'6.19':'6.20'},{'versionCode':80})
    assert attributes(payload['AndroidManifest.xml'])=={**before,'versionName':'6.20','versionCode':80}
    for f in args.assets.rglob('*'):
        if f.is_file():payload['assets/'+f.relative_to(args.assets).as_posix()]=f.read_bytes()
    changed=[n for n in original if original[n]!=payload[n]]
    assert set(changed)=={'AndroidManifest.xml','assets/index.html'},changed
    reverted=payload['assets/index.html'].decode()
    reverted=reverted.replace('<title>TD Booking 6.20 MarK</title>','<title>TD Booking 6.19 MarK</title>')
    reverted=reverted.replace('TD Production · верзија 6.20 · Календар и свадби','TD Production · верзија 6.12 · Екипа + известувања')
    reverted=reverted.replace('<link rel="stylesheet" href="booking_pages_v620.css">','').replace('<script src="booking_pages_v620.js"></script>','')
    assert reverted.encode()==original['assets/index.html'],'Existing business HTML or scripts changed'
    assert payload['classes.dex']==original['classes.dex']
    assert payload['resources.arsc']==original['resources.arsc']
    added=sorted(set(payload)-set(original))
    assert added==['assets/booking_pages_v620.css','assets/booking_pages_v620.js'],added
    args.out.parent.mkdir(parents=True,exist_ok=True)
    unsigned=args.out.with_suffix('.unsigned.apk')
    with zipfile.ZipFile(unsigned,'w',allowZip64=False) as z:
        for name in ['resources.arsc']+[n for n in payload if n!='resources.arsc']:
            if re.match(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)$',name,re.I) or name=='META-INF/MANIFEST.MF':continue
            info=zipfile.ZipInfo(name,date_time=(1980,1,1,0,0,0));info.create_system=0
            info.compress_type=zipfile.ZIP_STORED if name=='resources.arsc' else zipfile.ZIP_DEFLATED
            z.writestr(info,payload[name])
    args.out.write_bytes(helpers.sign_v2(unsigned.read_bytes()));unsigned.unlink()
    sig=helpers.verify_v2(args.out.read_bytes());assert sig['cert_sha256']==CERT_SHA
    with zipfile.ZipFile(args.out) as z:
        assert z.testzip() is None
        resource=z.getinfo('resources.arsc')
        assert resource.compress_type==zipfile.ZIP_STORED and (resource.header_offset+30+len(resource.filename.encode())+len(resource.extra))%4==0
    checked=subprocess.run(['java','-jar',str(ROOT/'tools/apksigner.jar'),'verify','--verbose','--print-certs',str(args.out)],check=True,capture_output=True,text=True)
    report={'apk':args.out.name,'package':'mk.td.booking6','versionName':'6.20','versionCode':80,'sizeBytes':args.out.stat().st_size,'sha256':hashlib.sha256(args.out.read_bytes()).hexdigest(),'signature':sig,'officialApksignerVerified':True,'nativeCodeByteIdenticalTo619':True,'existingBusinessScriptsByteIdentical':True,'existingSyncBackupBrandingAssetsByteIdentical':True,'resourcesUnchangedAndAligned':True,'changedFiles':changed,'addedFiles':added,'physicalAndroidTested':False}
    args.out.with_suffix('.validation.json').write_text(json.dumps(report,indent=2))
    (args.out.parent/'TD_Booking_v6_20_APKSIGNER.txt').write_text(checked.stdout+checked.stderr)
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
