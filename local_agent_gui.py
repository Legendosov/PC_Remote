import sys
import os
import ctypes
import threading
import socket
import uvicorn
import pyautogui
import comtypes
from PyQt5.QtWidgets import (QApplication, QMainWindow, QWidget, QVBoxLayout, 
                             QHBoxLayout, QLabel, QPushButton, QTextEdit)
from PyQt5.QtCore import Qt, pyqtSignal, QObject
from PyQt5.QtGui import QColor, QFont, QPixmap
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from pycaw.pycaw import AudioUtilities, IAudioEndpointVolume, ISimpleAudioVolume
from comtypes import CLSCTX_ALL
import qrcode
from io import BytesIO

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.settimeout(0)
        try:
            s.connect(('10.255.255.255', 1))
            IP = s.getsockname()[0]
        except Exception:
            IP = '127.0.0.1'
        finally:
            s.close()
        return IP
    except:
        return 'Не определен'

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

user32 = ctypes.windll.user32
powrprof = ctypes.windll.powrprof

def _get_vol():
    comtypes.CoInitialize()
    devices = AudioUtilities.GetSpeakers()
    return devices.EndpointVolume

class VolumeRequest(BaseModel):
    level: float

class ActionRequest(BaseModel):
    action: str

class AppVolRequest(BaseModel):
    app: str
    level: float

# --- ЭНДПОИНТЫ ---
@app.post("/volume")
def set_vol(req: VolumeRequest):
    print(f"📥 Громкость: {req.level}")
    try:
        vol = _get_vol()
        level = max(0.0, min(1.0, req.level))
        vol.SetMasterVolumeLevelScalar(level, None)
        comtypes.CoUninitialize()
        return {"status": "ok"}
    except Exception as e:
        print(f"❌ Ошибка звука: {e}")
        return {"status": "error"}

@app.post("/action")
def set_action(req: ActionRequest):
    print(f"📥 Действие: {req.action}")
    try:
        a = req.action
        if a == "lock": user32.LockWorkStation()
        elif a == "shutdown": os.system("shutdown /s /t 0 /f")
        elif a == "restart": os.system("shutdown /r /t 0 /f")
        elif a == "sleep": powrprof.SetSuspendState(False, True, False)
        elif a == "playpause": pyautogui.press("playpause")
        elif a == "next": pyautogui.press("nexttrack")
        elif a == "prev": pyautogui.press("prevtrack")
        return {"status": "ok"}
    except Exception as e:
        return {"status": "error"}

@app.get("/sessions")
def get_sessions():
    comtypes.CoInitialize()
    sessions = AudioUtilities.GetAllSessions()
    result = []
    for s in sessions:
        if s.Process and s.Process.name():
            try:
                vol_obj = s._ctl.QueryInterface(ISimpleAudioVolume)
                result.append({
                    "name": s.Process.name().replace('.exe', ''),
                    "volume": round(vol_obj.GetMasterVolume(), 2)
                })
            except: pass
    comtypes.CoUninitialize()
    return {"sessions": result}

@app.post("/app_volume")
def set_app_vol(req: AppVolRequest):
    print(f"📥 Громкость {req.app}: {req.level}")
    try:
        comtypes.CoInitialize()
        level = max(0.0, min(1.0, req.level))
        sessions = AudioUtilities.GetAllSessions()
        for s in sessions:
            if s.Process and req.app.lower() in s.Process.name().lower():
                vol_obj = s._ctl.QueryInterface(ISimpleAudioVolume)
                vol_obj.SetMasterVolume(level, None)
                comtypes.CoUninitialize()
                return {"status": "ok"}
        comtypes.CoUninitialize()
        return {"status": "error", "message": "App not found"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.post("/launch")
def launch_app(req: dict):
    print(f"📥 Запуск: {req}")
    try:
        target = req.get('target')
        type_ = req.get('type')
        if type_ == "url" and target.startswith("http"):
            import webbrowser
            webbrowser.open(target)
        elif type_ == "app" and (target.lower().endswith(".exe") or target.lower().endswith(".lnk")):
            os.startfile(target)
        return {"status": "ok"}
    except Exception as e:
        return {"status": "error", "message": str(e)}

# --- GUI ---
class LogEmitter(QObject):
    log_signal = pyqtSignal(str)

class ServerWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.setWindowTitle("PC Remote Server")
        self.setFixedSize(650, 650)
        self.current_ip = get_local_ip()
        self.current_port = 8001
        
        self.setStyleSheet("""
            QMainWindow { background-color: #1a1a1a; }
            QLabel { color: #ffffff; }
            QPushButton { color: white; border-radius: 10px; border: none; font-weight: bold; }
            QTextEdit { background-color: #111111; color: #cccccc; border: 1px solid #333333; border-radius: 8px; padding: 10px; }
        """)
        
        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)
        
        title = QLabel("PC REMOTE SERVER")
        title.setAlignment(Qt.AlignCenter)
        title.setFont(QFont("Segoe UI", 18, QFont.Bold))
        layout.addWidget(title)

        self.ip_display = QLabel(f"http://{self.current_ip}:{self.current_port}")
        self.ip_display.setAlignment(Qt.AlignCenter)
        self.ip_display.setFont(QFont("Segoe UI", 14, QFont.Bold))
        self.ip_display.setStyleSheet("color: #10B981;")
        layout.addWidget(self.ip_display)

        self.qr_label = QLabel()
        self.qr_label.setAlignment(Qt.AlignCenter)
        self.update_qr()
        layout.addWidget(self.qr_label)

        self.start_btn = QPushButton("▶️ ЗАПУСТИТЬ СЕРВЕР")
        self.start_btn.setFixedHeight(50)
        self.start_btn.setStyleSheet("background-color: #10B981;")
        self.start_btn.clicked.connect(self.toggle_server)
        layout.addWidget(self.start_btn)

        self.log_view = QTextEdit()
        self.log_view.setReadOnly(True)
        layout.addWidget(self.log_view)

        self.is_running = False

    def update_qr(self):
        qr = qrcode.make(f"{self.current_ip}:{self.current_port}")
        buffer = BytesIO()
        qr.save(buffer, format="PNG")
        pixmap = QPixmap()
        pixmap.loadFromData(buffer.getvalue())
        self.qr_label.setPixmap(pixmap)

    def toggle_server(self):
        if not self.is_running:
            self.is_running = True
            self.start_btn.setText("⏹️ ОСТАНОВИТЬ")
            self.start_btn.setStyleSheet("background-color: #EF4444;")
            threading.Thread(target=lambda: uvicorn.run(app, host="0.0.0.0", port=self.current_port), daemon=True).start()
        else:
            self.is_running = False
            self.start_btn.setText("▶️ ЗАПУСТИТЬ СЕРВЕР")
            self.start_btn.setStyleSheet("background-color: #10B981;")

@app.get("/ping")
def ping():
    return {"status": "ok"}           

if __name__ == "__main__":
    app_qt = QApplication(sys.argv)
    window = ServerWindow()
    window.show()
    sys.exit(app_qt.exec_())