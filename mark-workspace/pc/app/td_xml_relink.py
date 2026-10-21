"""Relink *copies* of generated Final Cut Pro XML using unambiguous same-filename media.

Does not modify native .prproj or any originals, and refuses ambiguous matches.
"""
from pathlib import Path, PureWindowsPath
from urllib.parse import urlparse, unquote
from collections import defaultdict
from datetime import datetime
import json, os, xml.etree.ElementTree as ET

VIDEO_EXT={'.mp4','.mov','.avi','.m4v','.mkv','.mts','.m2ts','.mp3','.wav','.m4a','.flac','.aif','.aiff'}

def decode_url(url):
    if not url.lower().startswith('file:'):return None
    u=urlparse(url);path=unquote(u.path)
    if os.name=='nt':
        if u.netloc and u.netloc.lower()!='localhost':return Path('\\\\'+u.netloc+path.replace('/','\\'))
        if len(path)>=3 and path[0]=='/' and path[2]==':':path=path[1:]
        return Path(path.replace('/','\\'))
    return Path(path)

def relink_xml(original_xml, folder, destination):
    xml=Path(original_xml).expanduser().resolve();root=Path(folder).expanduser().resolve()
    dest=Path(destination).expanduser().resolve()
    if not xml.is_file() or xml.suffix.lower()!='.xml':raise ValueError('Избери постоечки Final Cut Pro XML фајл.')
    if xml.stat().st_size>150*1024*1024:raise ValueError('XML е поголем од 150 MB.')
    if not root.is_dir():raise ValueError('Избери папка со оригиналните видео/аудио снимки.')
    tree=ET.parse(xml);resources=[f for f in tree.findall('.//file') if f.find('pathurl') is not None and f.findtext('pathurl')]
    # All originals stay read-only; only indexes media inside the user's selected folder.
    index=defaultdict(list)
    for p in root.rglob('*'):
        if p.is_file() and p.suffix.lower() in VIDEO_EXT and not any(c.upper() in ('TD_AI_EDIT','TD_AI_CACHE') for c in p.relative_to(root).parts):
            index[p.name.casefold()].append(p)
    report={'original_xml':str(xml),'search_folder':str(root),'total_refs':len(resources),
            'already_online':0,'relinked':[], 'ambiguous':[], 'unresolved':[],
            'warning':'Создава копија само од XML; постоечки PRPROJ не се менува.'}
    seen=set()
    for file_node in resources:
        url_el=file_node.find('pathurl');raw=url_el.text or ''
        if raw in seen:continue
        seen.add(raw)
        current=decode_url(raw)
        if current and current.is_file():
            report['already_online']+=1;continue
        name=(current.name if current is not None else '') or file_node.findtext('name','')
        alternatives=index.get(name.casefold(),[])
        if len(alternatives)==1:
            new=alternatives[0].resolve().as_uri()
            # Each duplicate definition holding same original URL must follow the same mapping.
            for node in resources:
                if node.findtext('pathurl')==raw:node.find('pathurl').text=new
            report['relinked'].append({'filename':name,'old_url':raw,'new_path':str(alternatives[0])})
        elif len(alternatives)>1:
            report['ambiguous'].append({'filename':name,'matches':[str(x) for x in alternatives[:20]],
                                        'note':'Повеќе фајлови со исто име; НЕ избрав наслепо.'})
        else:report['unresolved'].append({'filename':name,'old_url':raw})
    if not report['relinked']:
        report['new_xml']=None
        report['status']='no_changes'
        return report
    dest.mkdir(parents=True,exist_ok=True)
    candidate=dest/(xml.stem+'_RELINKED_'+datetime.now().strftime('%Y%m%d_%H%M%S')+'.xml')
    n=2
    while candidate.exists():candidate=dest/(xml.stem+'_RELINKED_'+str(n)+'.xml');n+=1
    ET.indent(tree.getroot(),space='  ')
    tree.write(candidate,encoding='utf-8',xml_declaration=True)
    report['new_xml']=str(candidate)
    report['status']='partial' if report['ambiguous'] or report['unresolved'] else 'relinked'
    return report
