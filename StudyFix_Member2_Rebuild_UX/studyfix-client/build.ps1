$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$target = 'target'
$classes = 'target/classes'
$jarPath = 'target/studyfix-client-1.0.0.jar'
if (Test-Path $target) { Remove-Item $target -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classes | Out-Null
$javaFiles = @(Get-ChildItem 'src/main/java' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName.Substring($root.Length + 1) })
if (-not $javaFiles -or $javaFiles.Count -eq 0) { throw 'No Java sources found.' }

# Prefer the real JDK named by JAVA_HOME. This avoids Oracle javapath shim paths.
$jdkBin = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin' } else { $null }
$javacExe = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'javac.exe'))) { Join-Path $jdkBin 'javac.exe' } else { (Get-Command javac -ErrorAction Stop).Source }
$jarExe = if ($jdkBin -and (Test-Path (Join-Path $jdkBin 'jar.exe'))) { Join-Path $jdkBin 'jar.exe' } else { $null }
if (-not $jarExe) {
  $candidate = Join-Path (Split-Path -Parent $javacExe) 'jar.exe'
  if (Test-Path $candidate) { $jarExe = $candidate }
  else { throw "Cannot find jar.exe for JDK at '$jdkBin'. Set JAVA_HOME to the full JDK directory." }
}

$compileArgs = @('--release','17','-encoding','UTF-8','-d',$classes) + $javaFiles
& $javacExe @compileArgs
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }
$resources = 'src/main/resources'
if (Test-Path $resources) { Copy-Item (Join-Path $resources '*') $classes -Recurse -Force }
& $jarExe '--create' '--file' $jarPath '--main-class' 'vn.studyfix.client.StudyFixApplication' '-C' $classes '.'
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit code $LASTEXITCODE" }
Write-Host "Build successful: $(Join-Path $root $jarPath)" -ForegroundColor Green
