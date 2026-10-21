@echo off
setlocal
cd /d "%~dp0"
if exist "..\.venv\Scripts\python.exe" (
 "..\.venv\Scripts\python.exe" "TD_Photo_Selector_v0_2.py"
) else if exist "..\venv\Scripts\python.exe" (
 "..\venv\Scripts\python.exe" "TD_Photo_Selector_v0_2.py"
) else (
 python "TD_Photo_Selector_v0_2.py"
)
if errorlevel 1 (
 echo Missing libraries? Run: python -m pip install opencv-python numpy
 pause
)
