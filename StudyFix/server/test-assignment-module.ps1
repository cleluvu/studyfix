$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $project
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1' }
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
$ant = (Get-Command ant.bat -ErrorAction SilentlyContinue).Source
if (-not $ant -and $env:ANT_HOME) { $candidate = Join-Path $env:ANT_HOME 'bin\ant.bat'; if (Test-Path $candidate) { $ant = $candidate } }
if (-not $ant) {
    $downloads = Join-Path $env:USERPROFILE 'Downloads'
    if (Test-Path $downloads) { $found = Get-ChildItem $downloads -Filter 'ant.bat' -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1; if ($found) { $ant = $found.FullName } }
}
if (!(Test-Path $javac) -or !(Test-Path $java)) { throw "Cần JDK 26; kiểm tra JAVA_HOME: $env:JAVA_HOME" }
if (-not $ant -or !(Test-Path $ant)) { throw 'Không tìm thấy Ant.' }
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
$cp = "$classes;$sqlite"
$args = @('--release','26','-encoding','UTF-8','-cp',$cp,'-d',$out) + $tests
& $javac @args
if ($LASTEXITCODE -ne 0) { throw "Biên dịch test Member 3 thất bại ($LASTEXITCODE)" }
$testCp = "$classes;$out;$sqlite"
Write-Host 'Chạy AssignmentModuleSelfTest...' -ForegroundColor Cyan
& $java '-Dfile.encoding=UTF-8' '-cp' $testCp 'studyfix.assignment.AssignmentModuleSelfTest'
if ($LASTEXITCODE -ne 0) { throw "AssignmentModuleSelfTest FAIL ($LASTEXITCODE)" }
Write-Host 'Chạy AssignmentProtocolIntegrationTest (TCP thật + SQLite tạm)...' -ForegroundColor Cyan
& $java '--enable-native-access=ALL-UNNAMED' '-Dfile.encoding=UTF-8' '-cp' $testCp 'studyfix.assignment.AssignmentProtocolIntegrationTest'
if ($LASTEXITCODE -ne 0) { throw "AssignmentProtocolIntegrationTest FAIL ($LASTEXITCODE)" }
Write-Host 'Chạy WebGatewayIntegrationTest (HTTP thật + database tạm)...' -ForegroundColor Cyan
& $java '-Dfile.encoding=UTF-8' '-cp' $testCp 'studyfix.WebGatewayIntegrationTest'
if ($LASTEXITCODE -ne 0) { throw "WebGatewayIntegrationTest FAIL ($LASTEXITCODE)" }
Write-Host 'PASS: assignment module + Web Gateway integration đã hoàn thành.' -ForegroundColor Green
