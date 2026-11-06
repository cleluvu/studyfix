$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
& (Join-Path $root 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }

$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { $null }
$javacExe = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'javac.exe'))) { Join-Path $jdkBin 'javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$javaExe = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'java.exe'))) { Join-Path $jdkBin 'java.exe' } else { (Get-Command java -ErrorAction Stop).Source }

$testClasses = 'target/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$tests = @(Get-ChildItem 'src/test/java' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName.Substring($root.Length + 1) })
if ($tests.Count -gt 0) {
  # Use relative paths. This avoids Windows drive-letter ':' being mis-parsed by some PowerShell/native-tool combinations.
  $compileArgs = @('--release','17','-encoding','UTF-8','-cp','target/classes','-d',$testClasses) + $tests
  & $javacExe @compileArgs
  if ($LASTEXITCODE -ne 0) { throw "test compile failed with exit code $LASTEXITCODE" }
  $testClasspath = 'target/classes;target/test-classes'
  & $javaExe '-cp' $testClasspath 'vn.studyfix.client.ClientSelfTest'
  if ($LASTEXITCODE -ne 0) { throw "self-test failed with exit code $LASTEXITCODE" }
} else {
  Write-Host 'No self-test sources found.' -ForegroundColor Yellow
}
