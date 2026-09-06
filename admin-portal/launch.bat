@echo off
title GabAI Division Admin Portal Launcher
echo ========================================================
echo   GabAI SDO Valenzuela City Admin Portal Launcher
echo ========================================================
echo Starting multi-threaded server on http://127.0.0.1:3000 ...
start "" "http://127.0.0.1:3000/index.html"
cd /d "%~dp0"
python serve.py 3000
pause
