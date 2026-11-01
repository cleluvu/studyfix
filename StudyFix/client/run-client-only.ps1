$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$jar = Join-Path $root 'target\studyfix-client-1.0.0.jar'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Java\jdk-26.0.2.1' }
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'
if (!(Test-Path $java)) { $java = (Get-Command java -ErrorAction Stop).Source }
if (!(Test-Path $jar)) { throw "Chưa có JAR tại $jar. Chạy .\build.ps1 trước." }
& $java '-Dfile.encoding=UTF-8' '-Dstudyfix.assignment.port=5002' '-jar' $jar
