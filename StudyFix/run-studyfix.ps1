$ErrorActionPreference = 'Stop'
# ---------- Set environment ----------
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1'   # <-- thay đổi nếu JDK nằm ở vị trí khác
$env:ANT_HOME  = 'C:\Apache\ant\apache-ant-1.10.18'
$env:Path = "$env:ANT_HOME\bin;$env:JAVA_HOME\bin;$env:Path"

Write-Host "=== ENVIRONMENT ===" -ForegroundColor Cyan
Write-Host "JAVA_HOME = $env:JAVA_HOME"
Write-Host "ANT_HOME  = $env:ANT_HOME"

# ---------- Start Server Manager (new window) ----------
Write-Host "=== Starting Server Manager (new PowerShell window) ===" -ForegroundColor Green
Start-Process -FilePath 'powershell' -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $PSScriptRoot 'server\run-server-manager.ps1') -WindowStyle Normal

# Wait a bit for server to spin up
Start-Sleep -Seconds 5

# ---------- Build & run client ----------
Write-Host "=== Building client ===" -ForegroundColor Green
& (Join-Path $PSScriptRoot 'client\build.ps1')
if ($LASTEXITCODE -ne 0) { throw "Client build failed (exit $LASTEXITCODE)" }

Write-Host "=== Running client ===" -ForegroundColor Green
& (Join-Path $PSScriptRoot 'client\run.ps1')

