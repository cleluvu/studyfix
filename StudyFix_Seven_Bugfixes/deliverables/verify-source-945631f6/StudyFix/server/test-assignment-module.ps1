$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $project
. (Join-Path $project 'resolve-tools.ps1')
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
Write-Host 'Build baseline server trước...' -ForegroundColor Cyan
& $ant jar
if ($LASTEXITCODE -ne 0) { throw "ant jar thất bại ($LASTEXITCODE)" }
$classes = Join-Path $project 'build\classes'
$sqlite = Join-Path $project 'lib\sqlite-jdbc-3.53.0.0.jar'
$out = Join-Path $project 'build\p3-test-classes'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$tests = @(
    (Join-Path $project 'test\studyfix\assignment\AssignmentModuleSelfTest.java'),
    (Join-Path $project 'test\studyfix\assignment\AssignmentProtocolIntegrationTest.java'),
    (Join-Path $project 'test\studyfix\WebGatewayIntegrationTest.java')
)
foreach ($test in $tests) { if (!(Test-Path $test)) { throw "Thiếu test source: $test" } }
$cp = "$classes;$(Join-Path $project 'lib\*')"
$args = @('--release','26','-encoding','UTF-8','-cp',$cp,'-d',$out) + $tests
& $javac @args
if ($LASTEXITCODE -ne 0) { throw "Biên dịch test Member 3 thất bại ($LASTEXITCODE)" }
$testCp = "$classes;$out;$(Join-Path $project 'lib\*')"
Write-Host 'Chạy AssignmentModuleSelfTest...' -ForegroundColor Cyan
& $java "-Dstudyfix.test.accounts=$(Join-Path $project 'test\fixtures\accounts.tsv')" '-Dstudyfix.management.db=sqlite' '-Dfile.encoding=UTF-8' '-cp' $testCp 'studyfix.assignment.AssignmentModuleSelfTest'
if ($LASTEXITCODE -ne 0) { throw "AssignmentModuleSelfTest FAIL ($LASTEXITCODE)" }
Write-Host 'Chạy AssignmentProtocolIntegrationTest (TCP thật + SQLite tạm)...' -ForegroundColor Cyan
& $java '--enable-native-access=ALL-UNNAMED' "-Dstudyfix.test.accounts=$(Join-Path $project 'test\fixtures\accounts.tsv')" '-Dstudyfix.management.db=sqlite' '-Dfile.encoding=UTF-8' '-cp' $testCp 'studyfix.assignment.AssignmentProtocolIntegrationTest'
if ($LASTEXITCODE -ne 0) { throw "AssignmentProtocolIntegrationTest FAIL ($LASTEXITCODE)" }
Write-Host 'Chạy WebGatewayIntegrationTest (HTTP thật + database tạm)...' -ForegroundColor Cyan
& $java "-Dstudyfix.test.accounts=$(Join-Path $project 'test\fixtures\accounts.tsv')" '-Dstudyfix.management.db=sqlite' '-Dfile.encoding=UTF-8' '-cp' $testCp 'studyfix.WebGatewayIntegrationTest'
if ($LASTEXITCODE -ne 0) { throw "WebGatewayIntegrationTest FAIL ($LASTEXITCODE)" }
Write-Host 'PASS: assignment module + Web Gateway integration đã hoàn thành.' -ForegroundColor Green
