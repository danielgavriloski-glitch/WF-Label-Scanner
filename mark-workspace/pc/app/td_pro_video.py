"""Video spot preflight. Stages <=300s clips using NTFS hard links, never full copies.
The existing AI Director remains responsible for all editing and Premiere import.
"""
from pathlib import Path
import os, shutil, uuid, json
from td_wedding_chapters import VIDEO_EXT, probe

# Media links are created NEXT TO the source folder, ensuring same-volume NTFS.
# We never modify source media, and keep the staging folder while Premiere needs it.
ROOT=Path(__file__).resolve().parent/'td_chat_data'/'spot_sources'

def prepare_spot(source_path):
    source=Path(source_path).expanduser().resolve()
    if not source.is_dir():raise ValueError('Не постои папката со свадбата.')
    if source==Path(source.anchor) or ROOT==source or ROOT in source.parents or source in ROOT.parents:
        raise ValueError('Избери ја оригиналната папка со снимките, не системската папка на асистентот.')
    videos=sorted((p for p in source.rglob('*') if p.is_file() and p.suffix.lower() in VIDEO_EXT
                  and not p.is_symlink()))
    if not videos:raise ValueError('Нема видеа во папката.')
    eligible=[];excluded=[];errors=[]
    for path in videos:
        try:
            meta=probe(path)
            if not meta.get('video'):raise ValueError('Не содржи видео-стрим')
            if meta['duration']<=300:eligible.append((path,meta['duration']))
            else:excluded.append({'name':str(path.relative_to(source)),'seconds':round(meta['duration'],1)})
        except Exception as exc:errors.append({'name':str(path.relative_to(source)),'error':str(exc)[:180]})
    if errors:
        # Never silently include unknown-duration clips in a max-5-min pipeline.
        raise ValueError(f'{len(errors)} видеа не може да се проверат. Првата грешка: {errors[0]["name"]} — {errors[0]["error"]}')
    if not eligible:raise ValueError('Нема видеа до 5 минути за спотот.')
    job=uuid.uuid4().hex
    # Prefer private Studio storage if the source is on the SAME filesystem;
    # otherwise stage beside the wedding folder so os.link stays same-volume.
    ROOT.parent.mkdir(parents=True,exist_ok=True)
    same_disk=(source.stat().st_dev==ROOT.parent.stat().st_dev)
    destination=(ROOT/job/'CLIPS') if same_disk else (source.parent/('_TD_SPOT_STAGE_'+job[:12])/'CLIPS')
    created=[]
    try:
        for path,duration in eligible:
            target=destination/path.relative_to(source)
            target.parent.mkdir(parents=True,exist_ok=True)
            os.link(path,target)  # no byte copies and camera originals remain in place
            created.append(target)
        manifest={'job':job,'original_source':str(source),'filtered_source':str(destination),
                  'included':len(eligible),'excluded':len(excluded),'long_videos':excluded,
                  'max_seconds':300,'analysis':'deep','media':'NTFS hardlinks; no camera media copied'}
        manifest_path=destination.parent/'TD_SPOT_SOURCE_MANIFEST.json'
        manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
        return manifest
    except OSError as exc:
        # Clean up ONLY newly staged hardlinks. Originals are untouched.
        # Cleanup only the destination created by this attempt. Never source.
        shutil.rmtree(destination.parent,ignore_errors=True)
        raise RuntimeError('Не можам да направам hardlink за клиповите; провери дали изворот е на истиот диск како Studio. '+str(exc))
