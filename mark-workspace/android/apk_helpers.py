import zipfile, struct, hashlib
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

def decode_len8(buf,p):
    x=buf[p];p+=1
    if x&0x80: x=((x&0x7f)<<7)|buf[p];p+=1
    return x,p

def enc_len8(n):
    return bytes([(n>>7)|0x80,n&0x7f]) if n>0x7f else bytes([n])

def decode_len16(buf,p):
    x=struct.unpack_from('<H',buf,p)[0];p+=2
    if x&0x8000: x=((x&0x7fff)<<16)|struct.unpack_from('<H',buf,p)[0];p+=2
    return x,p

def enc_len16(n):
    return struct.pack('<HH',0x8000|(n>>16),n&0xffff) if n>0x7fff else struct.pack('<H',n)

def patch_axml_strings(manifest,repls,int_attrs=None):
    b=bytearray(manifest)
    rt,rhs,rsz=struct.unpack_from('<HHI',b,0)
    o=rhs
    # first string pool
    t,h,s=struct.unpack_from('<HHI',b,o)
    if t!=0x0001: raise ValueError('no string pool')
    sc,sty,flags,strStart,styleStart=struct.unpack_from('<IIIII',b,o+8)
    utf8=bool(flags&0x100)
    offsets=[struct.unpack_from('<I',b,o+h+4*i)[0] for i in range(sc)]
    style_off_base=o+h+4*sc
    style_offsets=b[style_off_base:style_off_base+4*sty]
    base=o+strStart
    strings=[]
    for off in offsets:
        p=base+off
        if utf8:
            ulen,p=decode_len8(b,p); blen,p=decode_len8(b,p); st=bytes(b[p:p+blen]).decode('utf8')
        else:
            ulen,p=decode_len16(b,p); st=bytes(b[p:p+2*ulen]).decode('utf-16le')
        strings.append(st)
    strings=[repls.get(x,x) for x in strings]
    data=bytearray(); newoffs=[]
    for st in strings:
        newoffs.append(len(data))
        if utf8:
            raw=st.encode('utf8'); data += enc_len8(len(st.encode('utf-16le'))//2)+enc_len8(len(raw))+raw+b'\x00'
        else:
            raw=st.encode('utf-16le'); data += enc_len16(len(raw)//2)+raw+b'\x00\x00'
    while len(data)%4: data+=b'\x00'
    # preserve styles data if present
    old_styles=b''
    if styleStart:
        old_styles=bytes(b[o+styleStart:o+s])
    header_size=h
    new_str_start=header_size+4*sc+4*sty
    new_style_start=(new_str_start+len(data)) if styleStart else 0
    chunk=bytearray(struct.pack('<HHI',t,h,0)+struct.pack('<IIIII',sc,sty,flags,new_str_start,new_style_start))
    chunk+=b''.join(struct.pack('<I',x) for x in newoffs)+style_offsets+data+old_styles
    struct.pack_into('<I',chunk,4,len(chunk))
    nb=bytearray(b[:o])+chunk+bytearray(b[o+s:])
    struct.pack_into('<I',nb,4,len(nb))
    # patch integer attrs by parsing start elements after new string pool
    if int_attrs:
        o2=o+len(chunk)
        while o2<len(nb):
            ct,ch,cs=struct.unpack_from('<HHI',nb,o2)
            if ct==0x0102:
                attrStart,attrSize,attrCount=struct.unpack_from('<HHH',nb,o2+24)
                p=o2+16+attrStart
                for _ in range(attrCount):
                    ans,aname,raw=struct.unpack_from('<III',nb,p)
                    dtype=nb[p+15]; val=struct.unpack_from('<I',nb,p+16)[0]
                    name=strings[aname] if aname<len(strings) else ''
                    if name in int_attrs and dtype in (0x10,0x11): struct.pack_into('<I',nb,p+16,int_attrs[name])
                    p+=attrSize
            o2+=cs
    return bytes(nb)

def u32(x):return struct.pack('<I',x)

def u64(x):return struct.pack('<Q',x)

def lp32(x):return u32(len(x))+x

def find_eocd(b):
    i=b.rfind(b'PK\x05\x06',max(0,len(b)-65557));
    if i<0:raise ValueError('EOCD')
    return i

def digest_content(apk):
    e=find_eocd(apk); cd=struct.unpack_from('<I',apk,e+16)[0]; sec4=bytearray(apk[e:]);struct.pack_into('<I',sec4,16,cd); chunks=[]
    for sec in (apk[:cd],apk[cd:e],bytes(sec4)):
        for p in range(0,len(sec),1024*1024):
            c=sec[p:p+1024*1024];chunks.append(hashlib.sha256(b'\xa5'+u32(len(c))+c).digest())
    return hashlib.sha256(b'\x5a'+u32(len(chunks))+b''.join(chunks)).digest(),e,cd

def sign_v2(apk):
    dig,e,cd=digest_content(apk); dr=u32(ALG)+lp32(dig); signed=lp32(lp32(dr))+lp32(lp32(cert_der))+lp32(b''); sig=key.sign(signed,padding.PKCS1v15(),hashes.SHA256()); sr=u32(ALG)+lp32(sig); signer=lp32(signed)+lp32(lp32(sr))+lp32(pub_der); val=lp32(lp32(signer)); pair=u64(4+len(val))+u32(V2_ID)+val; size=len(pair)+24; block=u64(size)+pair+u64(size)+MAGIC; sec4=bytearray(apk[e:]);struct.pack_into('<I',sec4,16,cd+len(block));return apk[:cd]+block+apk[cd:e]+bytes(sec4)

def repack(base,out,html,manifest):
    with zipfile.ZipFile(base,'r') as zin:
        names=[n for n in zin.namelist() if not n.upper().startswith('META-INF/')]
        payload={n:zin.read(n) for n in names}
    payload['assets/index.html']=html.encode('utf8');payload['AndroidManifest.xml']=manifest
    order=['resources.arsc']+[n for n in names if n!='resources.arsc']
    tmp=out.with_suffix('.unsigned.apk')
    with zipfile.ZipFile(tmp,'w',allowZip64=False) as z:
        for n in order:
            zi=zipfile.ZipInfo(n);zi.date_time=(1980,1,1,0,0,0);zi.create_system=0;zi.compress_type=zipfile.ZIP_STORED if n=='resources.arsc' else zipfile.ZIP_DEFLATED;z.writestr(zi,payload[n])
    raw=tmp.read_bytes();info=zipfile.ZipFile(tmp).getinfo('resources.arsc');fn=struct.unpack_from('<H',raw,info.header_offset+26)[0];ex=struct.unpack_from('<H',raw,info.header_offset+28)[0];off=info.header_offset+30+fn+ex
    if off%4: raise ValueError(f'resources unaligned {off}')
    out.write_bytes(sign_v2(raw)); tmp.unlink()

def read_lp32(b,off):
    n=struct.unpack_from('<I',b,off)[0]; off+=4; return b[off:off+n],off+n

def verify_v2(apk):
    e=find_eocd(apk); cd_off=struct.unpack_from('<I',apk,e+16)[0]
    if apk[cd_off-16:cd_off] != MAGIC: raise ValueError('magic not found')
    size2=struct.unpack_from('<Q',apk,cd_off-24)[0]
    start=cd_off-(size2+8)
    size1=struct.unpack_from('<Q',apk,start)[0]
    if size1!=size2: raise ValueError('block size mismatch')
    p=start+8
    found=None
    while p<cd_off-24:
        ln=struct.unpack_from('<Q',apk,p)[0]; p+=8
        pid=struct.unpack_from('<I',apk,p)[0]; val=apk[p+4:p+ln]; p+=ln
        if pid==V2_ID: found=val
    if found is None: raise ValueError('no v2 block')
    signers,_=read_lp32(found,0)
    signer,_=read_lp32(signers,0)
    off=0
    signed_data,off=read_lp32(signer,off)
    signatures,off=read_lp32(signer,off)
    pub,off=read_lp32(signer,off)
    sigrec,_=read_lp32(signatures,0)
    alg=struct.unpack_from('<I',sigrec,0)[0]
    sig,_=read_lp32(sigrec,4)
    cert.public_key().verify(sig,signed_data,padding.PKCS1v15(),hashes.SHA256())
    # extract digest
    off=0
    digests,off=read_lp32(signed_data,off)
    certseq,off=read_lp32(signed_data,off)
    attrs,off=read_lp32(signed_data,off)
    drec,_=read_lp32(digests,0)
    dalg=struct.unpack_from('<I',drec,0)[0]
    digest,_=read_lp32(drec,4)
    if alg!=ALG or dalg!=ALG: raise ValueError('algorithm mismatch')
    # Recompute content digest from final APK: section1 before sig block; CD; EOCD with cd offset set to block start
    sec1=apk[:start]; sec3=apk[cd_off:e]; sec4=bytearray(apk[e:]); struct.pack_into('<I',sec4,16,start)
    chunks=[]
    for sec in (sec1,sec3,bytes(sec4)):
        for q in range(0,len(sec),1024*1024):
            c=sec[q:q+1024*1024];chunks.append(hashlib.sha256(b'\xa5'+u32(len(c))+c).digest())
    calc=hashlib.sha256(b'\x5a'+u32(len(chunks))+b''.join(chunks)).digest()
    if calc!=digest: raise ValueError('content digest mismatch')
    if pub!=pub_der: raise ValueError('public key mismatch')
    return {'signing_block_start':start,'central_directory_offset':cd_off,'digest':digest.hex(),'cert_sha256':hashlib.sha256(cert_der).hexdigest()}
