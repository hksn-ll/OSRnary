Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   GabAI SDO Valenzuela City Admin Portal Launcher" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Starting multi-threaded server on http://127.0.0.1:3000 ..." -ForegroundColor Green
Set-Location -Path $PSScriptRoot
Start-Process "http://127.0.0.1:3000/index.html"
python serve.py 3000
