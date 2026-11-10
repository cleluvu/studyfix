$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Set-Location -LiteralPath $root
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Console]::OutputEncoding
$ant = 'C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat'
if (!(Test-Path -LiteralPath $ant)) { $ant = (Get-Command ant -ErrorAction Stop).Source }
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
$failedSuites = @()
try {
    & $ant integration-test 2>&1 | Tee-Object -FilePath (Join-Path $PSScriptRoot 'ant-final.log')
    if ($LASTEXITCODE -ne 0) { $failedSuites += 'Ant integration-test' }
    if (!(Test-Path 'build/integration-classes/studyfix/PersonOneTest.class')) { throw 'Test compilation unavailable' }
    foreach ($suite in @('PersonOneTest', 'ServerAdminLiveTest', 'ConsoleHistoryInterleaveTest')) {
        $logName = switch ($suite) {
            'PersonOneTest' { 'person-one.log' }
            'ServerAdminLiveTest' { 'admin-live.log' }
            'ConsoleHistoryInterleaveTest' { 'console-interleave.log' }
        }
        & java --enable-native-access=ALL-UNNAMED '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp 'build/integration-classes;lib/*' "studyfix.$suite" 2>&1 |
            Tee-Object -FilePath (Join-Path $PSScriptRoot $logName)
        if ($LASTEXITCODE -ne 0) { $failedSuites += $suite }
    }
} finally { $env:JAVA_TOOL_OPTIONS = $oldJavaOptions }
if ($failedSuites.Count -gt 0) {
    Write-Output ('FAILED SUITES: ' + ($failedSuites -join ', '))
    exit 1
}
exit 0
