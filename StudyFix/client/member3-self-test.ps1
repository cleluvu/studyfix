$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
& (Join-Path $root 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Client build failed.' }
$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { $null }
$javac = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'javac.exe'))) { Join-Path $jdkBin 'javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$java = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'java.exe'))) { Join-Path $jdkBin 'java.exe' } else { (Get-Command java -ErrorAction Stop).Source }
$testRoot = 'src/test/java'
$out = 'target/test-classes'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$tests = @(Get-ChildItem $testRoot -Filter '*.java' -Recurse | ForEach-Object { $_.FullName.Substring($root.Length + 1) })
$args = @('--release','17','-encoding','UTF-8','-cp','target/classes','-d',$out) + $tests
& $javac @args
if ($LASTEXITCODE -ne 0) { throw "Client test compile failed ($LASTEXITCODE)" }
$cp = 'target/classes;target/test-classes'
foreach ($class in @('vn.studyfix.client.ClientSelfTest','vn.studyfix.client.exercise.ExerciseModuleSelfTest')) {
    & $java '-Dfile.encoding=UTF-8' '-cp' $cp $class
    if ($LASTEXITCODE -ne 0) { throw "$class FAIL ($LASTEXITCODE)" }
}
Write-Host 'PASS: client core + Member-3 protocol tests.' -ForegroundColor Green
