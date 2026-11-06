$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $project
. (Join-Path $project 'load-env.ps1')

. (Join-Path $project 'resolve-tools.ps1')
. (Join-Path $project 'assert-server-stopped.ps1')

Write-Host "Project server: $project"
Write-Host "Java: $java"
Write-Host "Ant: $ant"
Write-Host 'Đang build server + module Member 3...'
# Keep launcher compilation separate from test databases/output under build/.
& $ant '-Dbuild.dir=build-launcher' jar
if ($LASTEXITCODE -ne 0) { throw "ant jar thất bại ($LASTEXITCODE)" }

$sqlite = Join-Path $project 'lib\sqlite-jdbc-3.53.0.0.jar'
$appJar = Join-Path $project 'dist\studyfix.jar'
if (!(Test-Path $appJar)) { throw "Không tìm thấy JAR server: $appJar" }
if (!(Test-Path $sqlite)) { throw "Không tìm thấy SQLite JDBC: $sqlite" }
New-Item -ItemType Directory -Force -Path (Join-Path $project 'data') | Out-Null

# Keep the existing core DB to preserve accounts/rooms. Learning data is stored separately.
$classpath = "$appJar;$(Join-Path $project 'lib\*')"
$webPort = if ($env:STUDYFIX_WEB_PORT) { $env:STUDYFIX_WEB_PORT } else { '8080' }
Write-Host ('Khởi chạy chế độ đầy đủ: control=5000, screen=5001, assignment=5002, web=' + $webPort) -ForegroundColor Green
$webScheme = if ($env:STUDYFIX_WEB_KEYSTORE) { 'https' } else { 'http' }
Write-Host ('Mở web FE tại ' + $webScheme + '://localhost:' + $webPort) -ForegroundColor Cyan
Write-Host 'Dừng bằng Ctrl+C. Không chạy đồng thời một server khác dùng các cổng 5000/5001/5002.'
& $java '--enable-native-access=ALL-UNNAMED' '-Dfile.encoding=UTF-8' '-Dstudyfix.port=5000' '-Dstudyfix.screen.port=5001' '-Dstudyfix.assignment.port=5002' '-Dstudyfix.db=data/studyfix.db' '-Dstudyfix.learning.db=data/studyfix-learning.db' '-cp' $classpath 'studyfix.assignment.AssignmentEnabledServerMain'
if ($LASTEXITCODE -ne 0) { throw "Server dừng với exit code $LASTEXITCODE" }
