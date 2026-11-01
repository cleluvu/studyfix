$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $project
$testMember3 = Join-Path $project 'test-assignment-module.ps1'
$ant = (Get-Command ant.bat -ErrorAction SilentlyContinue).Source
if (-not $ant -and $env:ANT_HOME) { $candidate = Join-Path $env:ANT_HOME 'bin\ant.bat'; if (Test-Path $candidate) { $ant = $candidate } }
if (-not $ant) {
    $downloads = Join-Path $env:USERPROFILE 'Downloads'
    if (Test-Path $downloads) { $found = Get-ChildItem $downloads -Filter 'ant.bat' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1; if ($found) { $ant = $found.FullName } }
}
if (-not $ant -or !(Test-Path $ant)) { throw 'Không tìm thấy Ant.' }
Write-Host '1/2: chạy regression test lõi (cần Java 26)...' -ForegroundColor Cyan
& $ant integration-test
if ($LASTEXITCODE -ne 0) { throw "ant integration-test FAIL ($LASTEXITCODE)" }
Write-Host '2/2: chạy test riêng module bài tập Member 3...' -ForegroundColor Cyan
& $testMember3
if ($LASTEXITCODE -ne 0) { throw 'test-assignment-module.ps1 FAIL' }
Write-Host 'PASS: core regression + Member 3 assignment tests.' -ForegroundColor Green
