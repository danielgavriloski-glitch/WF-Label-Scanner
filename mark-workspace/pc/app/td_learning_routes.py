"""Additive authenticated local learning endpoints for TD AI Studio."""
from __future__ import annotations
from pathlib import Path
import threading, time, subprocess, os
from flask import jsonify, request
from td_style_learning import learn, save_profile, list_profiles, choose_profile

def install_learning_routes(g):
    app=g['app']; BASE=g['BASE']
    profiles=BASE/'TD_LEARNED_STYLES'
    state={'state':'idle','messages':[],'style':None,'error':'','started':0}
    lock=threading.Lock()
    def access():return g['api_guard']()
    def log(line):
        with lock:
            state['messages'].append(str(line)[:260])
            state['messages']=state['messages'][-100:]
    @app.route('/api/learn/pick',methods=['POST'])
    def td_learn_picker():
        guard=access()
        if guard:return guard
        if not g['local_request']():return jsonify(error='Choose reference files on Windows PC'),403
        if os.name!='nt':return jsonify(error='Windows picker unavailable'),501
        kind=str((request.get_json(silent=True) or {}).get('kind',''))
        if kind not in ('video','xml','raw'):return jsonify(error='Unsupported picker'),400
        filters={
          'video':'Finished videos|*.mp4;*.mov;*.m4v;*.mkv|All files|*.*',
          'xml':'Premiere Final Cut Pro XML|*.xml|All files|*.*'}
        script=("$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.Windows.Forms; "
          + ("$p=New-Object System.Windows.Forms.FolderBrowserDialog; $p.Description='Choose original wedding source folder'; "
             if kind=='raw' else
             "$p=New-Object System.Windows.Forms.OpenFileDialog; "
             + "$p.Filter='"+filters[kind]+"'; ")
          + "if ($p.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { "
          + ("Write-Output $p.SelectedPath" if kind=='raw' else "Write-Output $p.FileName") + " }")
        try:
            r=subprocess.run(['powershell.exe','-NoProfile','-STA','-Command',script],
                  capture_output=True,text=True,timeout=240)
            if r.returncode:return jsonify(error='File picker failed: '+r.stderr[-350:]),503
            lines=[x.strip() for x in r.stdout.splitlines() if x.strip()]
            return jsonify(ok=True,path=lines[-1] if lines else '')
        except subprocess.TimeoutExpired:return jsonify(error='Selection timed out'),408
        except OSError as e:return jsonify(error=str(e)),503
    @app.route('/api/learn/styles')
    def td_learn_styles():
        guard=access()
        if guard:return guard
        return jsonify(styles=list_profiles(profiles))
    @app.route('/api/learn/status')
    def td_learn_status():
        guard=access()
        if guard:return guard
        with lock:return jsonify(**state)
    @app.route('/api/learn/style/<style_id>')
    def td_learn_style_info(style_id):
        guard=access()
        if guard:return guard
        try:
            obj=choose_profile(profiles,style_id)
            return jsonify(style=obj)
        except ValueError as e:return jsonify(error=str(e)),404
    @app.route('/api/learn/start',methods=['POST'])
    def td_learn_start():
        guard=access()
        if guard:return guard
        if not g['local_request']():return jsonify(error='Learning can only be started on this Windows PC'),403
        data=request.get_json(silent=True) or {}
        mp4=str(data.get('video','')).strip()
        xml=str(data.get('xml','')).strip()
        raw=str(data.get('raw_folder','')).strip()
        name=str(data.get('name','')).strip()[:70]
        if not mp4 and not xml:return jsonify(error='Choose a finished film (MP4) and/or Premiere XML'),400
        for value,suffixes,label in ((xml,{'.xml'},'XML'),(mp4,{'.mp4','.mov','.mkv','.m4v'},'Video')):
            if value and (not Path(value).is_absolute() or not Path(value).is_file() or Path(value).suffix.lower() not in suffixes):
                return jsonify(error=label+' path is missing or unsupported'),400
        if raw and (not Path(raw).is_absolute() or not Path(raw).is_dir()):
            return jsonify(error='Original footage folder is missing'),400
        with g['lock']:
            if g['job']['state']=='running':return jsonify(error='Finish current AI Director edit before learning'),409
        with lock:
            if state['state']=='running':return jsonify(error='Style learning is already running'),409
            state.update(state='running',messages=['Starting local reference analysis'],style=None,error='',started=time.time())
        def worker():
            try:
                profile=learn(reference_xml=xml or None,reference_mp4=mp4 or None,
                        raw_folder=raw or None,name=name,use_vision=bool(data.get('vision',True)),log=log)
                saved=save_profile(profile,profiles)
                with lock:state.update(state='finished',style={'id':saved['id'],'name':saved['name'],
                     'basis':saved['basis'],'cuts':saved['reference_cut_count']})
                log('SUCCESS — style saved locally; select it in AI Director')
            except Exception as e:
                with lock:state.update(state='failed',error=str(e)[:400]);
                log('ERROR — '+str(e))
        threading.Thread(target=worker,daemon=True,name='td-style-learning').start()
        return jsonify(ok=True,reply='Learning started; check Learn Status')
