$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $project
$testMember3 = Join-Path $project 'test-assignment-module.ps1'
. (Join-Path $project 'resolve-tools.ps1')
Write-Host '1/2: chạy regression test lõi (cần Java 26)...' -ForegroundColor Cyan
& $ant integration-test
if ($LASTEXITCODE -ne 0) { throw "ant integration-test FAIL ($LASTEXITCODE)" }
Write-Host '2/2: chạy test riêng module bài tập Member 3...' -ForegroundColor Cyan
& $testMember3
if ($LASTEXITCODE -ne 0) { throw 'test-assignment-module.ps1 FAIL' }
Write-Host 'PASS: core regression + Member 3 assignment tests.' -ForegroundColor Green
