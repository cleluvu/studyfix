$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $project

. (Join-Path $project 'resolve-tools.ps1')
. (Join-Path $project 'assert-server-stopped.ps1')

Write-Host "Project server: $project"
Write-Host "Java: $java"
Write-Host "Ant: $ant"
Write-Host 'Dang build server + giao dien Server Manager...'
& $ant clean jar
if ($LASTEXITCODE -ne 0) { throw "ant clean jar that bai ($LASTEXITCODE)" }

$sqlite = Join-Path $project 'lib\sqlite-jdbc-3.53.0.0.jar'
$appJar = Join-Path $project 'dist\studyfix.jar'
if (!(Test-Path $appJar)) { throw "Khong tim thay JAR server: $appJar" }
if (!(Test-Path $sqlite)) { throw "Khong tim thay SQLite JDBC: $sqlite" }
New-Item -ItemType Directory -Force -Path (Join-Path $project 'data') | Out-Null

# Start the Swing Server Manager. It starts control, screen relay, and assignment/grading
# listeners inside one JVM and uses the existing databases without resetting them.
$classpath = "$appJar;$sqlite"
Write-Host 'Mo giao dien StudyFix Server Manager...' -ForegroundColor Green
$webPort = if ($env:STUDYFIX_WEB_PORT) { $env:STUDYFIX_WEB_PORT } else { '8080' }
Write-Host ('Services: control=5000, screen=5001, assignment=5002, web=' + $webPort) -ForegroundColor Green
$webScheme = if ($env:STUDYFIX_WEB_KEYSTORE) { 'https' } else { 'http' }
Write-Host ('Mo web FE tai ' + $webScheme + '://localhost:' + $webPort) -ForegroundColor Cyan
if (-not $env:STUDYFIX_WEB_KEYSTORE) { Write-Host 'Can HTTPS cho browser screen share qua LAN? Cau hinh STUDYFIX_WEB_KEYSTORE + STUDYFIX_WEB_KEYSTORE_PASSWORD hoac TLS reverse proxy.' -ForegroundColor Yellow }
Write-Host 'Giu cua so nay mo; khong chay run-with-assignments.ps1 cung luc.'
& $java '--enable-native-access=ALL-UNNAMED' '-Dfile.encoding=UTF-8' '-Dstudyfix.port=5000' '-Dstudyfix.screen.port=5001' '-Dstudyfix.assignment.port=5002' '-Dstudyfix.db=data/studyfix.db' '-Dstudyfix.learning.db=data/studyfix-learning.db' '-cp' $classpath 'studyfix.Studyfix'
if ($LASTEXITCODE -ne 0) { throw "Server Manager dung voi exit code $LASTEXITCODE" }
