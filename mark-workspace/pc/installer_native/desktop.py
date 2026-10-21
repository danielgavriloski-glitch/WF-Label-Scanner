"""MarK Desktop v1.0 — genuine Qt desktop window (no external browser)."""
from __future__ import annotations
from pathlib import Path
from urllib.request import urlopen
from urllib.error import HTTPError, URLError
import hashlib
import json
import os
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parent.parent
PORT = 8765
URL = f'http://127.0.0.1:{PORT}'
LOG = ROOT / 'data' / 'desktop_log.txt'


def append_log(msg):
    LOG.parent.mkdir(parents=True,exist_ok=True)
    with LOG.open('a',encoding='utf-8') as f:f.write(time.strftime('%Y-%m-%d %H:%M:%S')+' '+str(msg)+'\n')


def probe():
    try:
        with urlopen(URL+'/_mark_desktop_health', timeout=2) as r:
            return json.load(r)
    except HTTPError as e:
        return {'error': f'HTTP {e.code}'}
    except (OSError,URLError,ValueError):
        return None


def start_server():
    actual=hashlib.sha256(os.path.normcase(str(ROOT)).encode()).hexdigest()[:16]
    info=probe()
    if info:
        if info.get('instance')!=actual:
            raise RuntimeError('Портата 8765 е зафатена од друга TD/MarK инсталација. Затвори го стариот MarK и обиди се повторно. Ништо нема да менуваме.')
        return
    py = ROOT/'runtime'/'python'/'pythonw.exe'
    worker = ROOT/'installer_native'/'desktop_server.py'
    if not py.is_file() or not worker.is_file():
        raise RuntimeError('Недостига локалниот MarK Python runtime или desktop сервер.')
    env=os.environ.copy()
    env.update({'MARK_HOME':str(ROOT),'TD_AI_ROOT':str(ROOT/'workspace'),'TD_AI_DESKTOP_MODE':'1'})
    data=ROOT/'data';data.mkdir(parents=True,exist_ok=True)
    log=open(data/'server_log.txt','a',encoding='utf-8')
    try:
        proc=subprocess.Popen([str(py),str(worker)],cwd=str(ROOT/'app'),env=env,
            stdin=subprocess.DEVNULL,stdout=log,stderr=log,
            creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    finally:
        log.close()
    for _ in range(80):
        if proc.poll() is not None:
            raise RuntimeError('Локалниот сервер прекина. Провери data\\server_log.txt')
        x=probe()
        if x and x.get('instance') == actual:
            (data/'desktop_server.pid').write_text(str(proc.pid),encoding='ascii')
            return
        if x and x.get('error'):
            raise RuntimeError('Портата 8765 е зафатена. Затвори ја старата MarK инсталација.')
        time.sleep(.6)
    raise RuntimeError('Нема одговор од MarK по 48 секунди. Провери data\\server_log.txt')


def main():
    if sys.platform!='win32':
        print('Windows 10/11 only');return 2
    mode='stable'
    try:
        mode=json.loads((ROOT/'data'/'ui_renderer.json').read_text('utf-8')).get('mode','stable')
    except (OSError,ValueError):
        pass
    if mode=='stable':
        flags=os.environ.get('QTWEBENGINE_CHROMIUM_FLAGS','')
        os.environ['QTWEBENGINE_CHROMIUM_FLAGS']=flags+' --disable-gpu --disable-gpu-compositing'
    append_log('UI renderer: '+mode+'; AI GPU is independent')
    try:
        from PySide6.QtWidgets import QApplication,QMainWindow,QMessageBox,QToolBar,QLabel,QFileDialog
        from PySide6.QtCore import Qt,QUrl,QStandardPaths,QObject,Slot
        from PySide6.QtWebChannel import QWebChannel
        from PySide6.QtGui import QAction,QIcon,QColor
        from PySide6.QtWebEngineWidgets import QWebEngineView
        from PySide6.QtWebEngineCore import QWebEngineProfile,QWebEnginePage
    except Exception as exc:
        append_log('PySide6 dependency error: '+repr(exc))
        try:
            import ctypes
            ctypes.windll.user32.MessageBoxW(0,'Недостасува PySide6 WebEngine. Повторно стартувај го MarK Desktop v1 Setup за инсталирање на компонентите.','MarK Desktop',0x10)
        except Exception: pass
        return 2

    app=QApplication(sys.argv)
    app.setApplicationName('MarK Engine · TD AI Studio PRO')
    app.setStyle('Fusion')
    logo=ROOT/'brand'/'MarK.ico'
    if logo.is_file():app.setWindowIcon(QIcon(str(logo)))
    cfg=ROOT/'app'/'td_local_config.json'
    if not cfg.is_file():
        cfg.parent.mkdir(parents=True,exist_ok=True)
        pin=f'{secrets.randbelow(900000)+100000}'
        cfg.write_text(json.dumps({'pin':pin,'allow_remote_control':False},indent=2),encoding='utf-8')
        QMessageBox.information(None,'MarK – прво стартување','Твојот локален MarK PIN е: '+pin+'\nЗапиши го на сигурно место. Ќе ти треба за најава.')
    try:
        start_server()
    except Exception as exc:
        append_log('Startup failed: '+repr(exc))
        QMessageBox.critical(None,'MarK не можеше да стартува',str(exc))
        return 1

    class LocalPage(QWebEnginePage):
        def acceptNavigationRequest(self, url, nav_type, isMainFrame):
            if url.scheme()=='about':return True
            ok=url.scheme()=='http' and url.host() in ('127.0.0.1','localhost') and url.port(-1)==PORT
            if not ok:append_log('Denied navigation to '+url.toString()[:250])
            return ok

    class Desktop(QMainWindow):
        def __init__(self):
            super().__init__()
            self.setWindowTitle('MarK Engine — TD AI Studio PRO')
            self.resize(1430,930)
            self.setMinimumSize(900,640)
            profile=QWebEngineProfile('MarKDesktop',self)
            store=ROOT/'data'/'qt_profile'
            store.mkdir(parents=True,exist_ok=True)
            profile.setPersistentStoragePath(str(store))
            profile.setCachePath(str(store/'cache'))
            profile.setPersistentCookiesPolicy(QWebEngineProfile.PersistentCookiesPolicy.ForcePersistentCookies)
            profile.downloadRequested.connect(self.save_download)
            self.view=QWebEngineView(self)
            self.view.setPage(LocalPage(profile,self.view))
            self.view.page().setBackgroundColor(QColor("#e7eaed"))
            self.channel=QWebChannel(self.view.page())
            owner=self
            class FolderPicker(QObject):
                @Slot(result=str)
                def selectFolder(picker):
                    return QFileDialog.getExistingDirectory(owner,'Избери папка')
            self.picker=FolderPicker(self.channel)
            self.channel.registerObject('markPicker',self.picker)
            self.view.page().setWebChannel(self.channel)
            self.setCentralWidget(self.view)
            toolbar=QToolBar('MarK контроли')
            toolbar.setMovable(False)
            toolbar.setIconSize(__import__('PySide6.QtCore',fromlist=['QSize']).QSize(16,16))
            toolbar.setStyleSheet('QToolBar { background:#142534;color:#d7e4e9; spacing:8px;padding:4px;border-bottom:1px solid #334956 } QToolButton {border:0; padding:5px 12px; color:#d7e4e9;} QToolButton:hover{background:#294452;border-radius:5px}')
            self.addToolBar(toolbar)
            toolbar.hide()
            controls=QAction("Контроли",self);controls.setShortcut("Ctrl+Alt+T");controls.triggered.connect(lambda:toolbar.setVisible(not toolbar.isVisible()));self.addAction(controls)
            full=QAction("Цел екран",self);full.setShortcut("F11");full.triggered.connect(lambda:self.showNormal() if self.isFullScreen() else self.showFullScreen());self.addAction(full)
            home=QAction('⌂  MarK панел',self);home.triggered.connect(lambda:self.view.load(QUrl(URL+'/mark')));toolbar.addAction(home)
            back=QAction('← Назад',self);back.triggered.connect(self.view.back);toolbar.addAction(back)
            reload=QAction('↻ Освежи',self);reload.triggered.connect(self.view.reload);toolbar.addAction(reload)
            pin=QAction('Мој PIN',self);pin.triggered.connect(self.show_pin);toolbar.addAction(pin)
            self.view.load(QUrl(URL+'/mark'))
        def save_download(self,download):
            path,_=QFileDialog.getSaveFileName(self,'Зачувај датотека',download.suggestedFileName())
            if path:
                download.setDownloadDirectory(str(Path(path).parent))
                download.setDownloadFileName(Path(path).name)
                download.accept()
            else:
                download.cancel()
        def show_pin(self):
            try:
                v=json.loads(cfg.read_text('utf-8'))['pin']
                QMessageBox.information(self,'MarK локален PIN','PIN: '+str(v)+'\nНе го споделувај со други лица.')
            except Exception:
                QMessageBox.warning(self,'MarK PIN','Не може да се прочита локалниот PIN.')
    win=Desktop()
    win.showMaximized()
    append_log('Qt Desktop UI opened')
    return app.exec()

if __name__=='__main__':
    raise SystemExit(main())
