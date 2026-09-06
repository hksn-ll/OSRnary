@echo off
title GabAI Admin Portal - Public Internet Tunnel
echo ========================================================
echo   GabAI Admin Portal - Launching to Public Internet
echo ========================================================

start "" /B python -u "%~dp0serve.py" 3000
timeout /t 2 /nobreak >nul

echo Starting secure Cloudflare HTTPS tunnel...
"C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://127.0.0.1:3000
pause
