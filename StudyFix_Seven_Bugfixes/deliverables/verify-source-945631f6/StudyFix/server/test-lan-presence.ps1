$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $project
try {
    . (Join-Path $project 'resolve-tools.ps1')
    $javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
    $testClasses = Join-Path $project 'build\lan-presence-test'
    New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $project 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)
    $sources += @((Join-Path $project 'test\studyfix\ClassroomIntegrationTest.java'),(Join-Path $project 'test\studyfix\LanPresenceIntegrationTest.java'))
    & $javac -encoding UTF-8 -cp (Join-Path $project 'lib\*') -d $testClasses @sources
    if ($LASTEXITCODE -ne 0) { throw 'LAN presence compilation failed.' }
    & $java '--enable-native-access=ALL-UNNAMED' "-Dstudyfix.test.accounts=$(Join-Path $project 'test\fixtures\accounts.tsv')" '-Dstudyfix.management.db=sqlite' '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "$testClasses;$(Join-Path $project 'lib\*')" 'studyfix.LanPresenceIntegrationTest'
    if ($LASTEXITCODE -ne 0) { throw 'LAN presence integration test failed.' }
} finally { Pop-Location }
