"""Compact workspace presentation. No booking/media/schema migrations."""
from pathlib import Path
import json
from flask import Blueprint, request, jsonify, send_from_directory

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

def register(app, auth, local):
    if app.extensions.get('mark_workspace_ui'):
        return
    app.extensions['mark_workspace_ui'] = True
    bp = Blueprint('mark_workspace_ui', __name__)

    def guard(write=False):
        if not auth():
            return jsonify(error='Потребна е MarK најава'), 401
        if not local():
            return jsonify(error='Само на локалниот MarK PC'), 403
        if write and (request.headers.get('X-TD-Client') != 'TD-v1.4' or
                      request.headers.get('Origin', '') not in ('', 'http://127.0.0.1:8765', 'http://localhost:8765')):
            return jsonify(error='Невалидно барање'), 403

    @bp.get('/api/mark/workspace/assets/<name>')
    def asset(name):
        denied = guard()
        if denied:
            return denied
        if name not in {'workspace_ui.css', 'workspace_ui.js', 'workspace_booking.css', 'workspace_booking.js'}:
            return jsonify(error='Not found'), 404
        return send_from_directory(HERE, name, max_age=3600)

    @bp.route('/api/mark/workspace/renderer', methods=['GET', 'POST'])
    def renderer():
        denied = guard(request.method == 'POST')
        if denied:
            return denied
        path = ROOT / 'data' / 'ui_renderer.json'
        mode = 'stable'
        if path.is_file():
            try:
                mode = json.loads(path.read_text('utf-8')).get('mode', 'stable')
            except (OSError, ValueError):
                pass
        if request.method == 'POST':
            mode = (request.get_json(silent=True) or {}).get('mode')
            if mode not in {'stable', 'automatic'}:
                return jsonify(error='Невалиден режим'), 400
            path.parent.mkdir(parents=True, exist_ok=True)
            temporary = path.with_suffix('.tmp')
            temporary.write_text(json.dumps({'mode': mode}), encoding='utf-8')
            temporary.replace(path)
        return jsonify(mode=mode, restart_required=request.method == 'POST', ai_gpu_unchanged=True)

    @app.after_request
    def add_workspace(response):
        if response.status_code != 200 or response.mimetype != 'text/html' or response.direct_passthrough:
            return response
        text = response.get_data(as_text=True)
        if 'data-mark-workspace-assets' in text:
            return response
        if 'id="page-home"' in text and 'id="markSharedBar"' in text:
            kind = 'ui'
        elif 'id="bottomNav"' in text and 'id="editor"' in text:
            kind = 'booking'
        elif request.path == '/' and ('id="tdLegacy"' in text or 'tdSimplePanel' in text):
            kind = 'ui'
        else:
            return response
        prefix = '/api/mark/workspace/assets/workspace_' + kind
        style = '<link data-mark-workspace-assets rel="stylesheet" href="'+prefix+'.css?v=20261021-1">'
        booking_available=any(rule.rule=='/api/mark/booking/app' for rule in app.url_map.iter_rules())
        script = '<script>window.MarKWorkspaceCapabilities={booking:'+str(booking_available).lower()+'};</script><script src="'+prefix+'.js?v=20261021-1"></script>'
        text = text.replace('</head>', style+'</head>', 1).replace('</body>', script+'</body>', 1)
        response.set_data(text)
        response.headers['Cache-Control'] = 'no-store'
        return response

    app.register_blueprint(bp)
