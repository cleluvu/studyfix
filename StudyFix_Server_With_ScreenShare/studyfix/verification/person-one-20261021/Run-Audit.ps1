$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if (-not (Test-Path -LiteralPath (Join-Path $projectRoot 'src/studyfix/Protocol.java'))) { throw 'Run this script from its preserved StudyFix audit directory.' }
Set-Location -LiteralPath $projectRoot
$antCommand = Get-Command ant.bat -ErrorAction SilentlyContinue | Select-Object -First 1
$ant = if ($antCommand) { $antCommand.Source } else { 'C:\Program Files\Apache NetBeans\extide\ant\bin\ant.bat' }
if (-not (Test-Path -LiteralPath $ant)) { throw 'BLOCKED: Ant not found. Set the local $ant path to your installed NetBeans Ant.' }
$audit = 'verification/person-one-20261021'
$taskBuild = "$audit/build"
$taskDist = "$audit/dist"
$taskClasses = "$audit/test-classes"
$resolvedBuild = [IO.Path]::GetFullPath((Join-Path $projectRoot $taskBuild))
if (-not $resolvedBuild.StartsWith($PSScriptRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Refusing clean outside the owned audit directory.' }
function Invoke-NativeLog([string]$program, [string[]]$nativeArguments, [string]$logPath) {
    $savedPreference = $ErrorActionPreference
    try {
        # Windows PowerShell reports native stderr as ErrorRecord even for java -version.
        # Exit codes, not the mere presence of stderr, decide whether a test failed.
        $ErrorActionPreference = 'Continue'
        & $program @nativeArguments 2>&1 | Out-File -Encoding UTF8 $logPath
        return $LASTEXITCODE
    } finally { $ErrorActionPreference = $savedPreference }
}
Get-FileHash src/studyfix/*.java,nbproject/project.properties,build.xml,README.md -Algorithm SHA256 | Export-Csv -Encoding UTF8 -NoTypeInformation "$audit/source-before.csv"
Get-FileHash lib/*.jar -Algorithm SHA256 | Export-Csv -Encoding UTF8 -NoTypeInformation "$audit/dependency.csv"
$versionExit = Invoke-NativeLog -program 'java' -nativeArguments @('-version') -logPath "$audit/jdk.txt"
$exits = [ordered]@{}
$buildExit = Invoke-NativeLog -program $ant -nativeArguments @("-Dbuild.dir=$taskBuild","-Ddist.dir=$taskDist",'clean','jar') -logPath "$audit/build.log"
if ($buildExit -ne 0) { throw 'BLOCKED: clean build failed. See build.log; dependent runs were not attempted.' }
New-Item -ItemType Directory -Force $taskClasses | Out-Null
$javaFiles = @((Get-ChildItem test/studyfix -Filter '*.java' -File).FullName) + @((Get-ChildItem $PSScriptRoot -Filter '*.java' -File).FullName)
$compileExit = Invoke-NativeLog -program 'javac' -nativeArguments (@('--release','26','-encoding','UTF-8','-cp',"$taskBuild/classes;lib/*",'-d',$taskClasses) + $javaFiles) -logPath "$audit/compile-audit.log"
if ($compileExit -ne 0) { throw 'BLOCKED: audit test compilation failed; see compile-audit.log.' }
$exits['AntIntegration'] = Invoke-NativeLog -program $ant -nativeArguments @("-Dbuild.dir=$taskBuild","-Ddist.dir=$taskDist",'integration-test') -logPath "$audit/integration.log"
$classpath = "$taskClasses;$taskBuild/classes;lib/*"
function Run-Case([string]$className, [string]$log, [string[]]$extraArguments=@()) {
    $caseArguments = @('-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8','--enable-native-access=ALL-UNNAMED','-cp',$classpath,"studyfix.$className") + $extraArguments
    $caseExit = Invoke-NativeLog -program 'java' -nativeArguments $caseArguments -logPath "$audit/$log"
    $exits[$className] = $caseExit
    Write-Output "$className exit=$caseExit (see $audit/$log)"
}
Run-Case 'PersonOneTest' 'person-one.log'
Run-Case 'ConsoleHistoryInterleaveTest' 'console-interleave.log'
Run-Case 'FullAudit' 'full-audit.log'
Run-Case 'FullAuditExtras' 'full-audit-extras.log'
Run-Case 'ServerAdminLiveTest' 'admin-live.log'
Run-Case 'PackagedSmoke' 'packaged.log'
$historyLine=Get-Content -Encoding UTF8 "$audit/integration.log" | Where-Object {$_ -match 'TEST_DATABASE_DIR=(.+)$'} | Select-Object -First 1
if ($historyLine -match 'TEST_DATABASE_DIR=(.+)$') { Run-Case 'LegacyHistorySmoke' 'legacy-read.log' @((Join-Path $Matches[1].Trim() 'legacy.db')) }
$evidenceLine=Get-Content -Encoding UTF8 "$audit/console-interleave.log" | Where-Object {$_ -match '^CONSOLE_EVIDENCE=(.+)$'} | Select-Object -First 1
if ($evidenceLine -match '^CONSOLE_EVIDENCE=(.+)$') {
    Copy-Item -LiteralPath $Matches[1].Trim() -Destination "$audit/console-interleave-evidence.log"
    $raw=[IO.File]::ReadAllText((Join-Path $projectRoot "$audit/console-interleave-evidence.log"))
    $begin=$raw.IndexOf('BEGIN_HISTORY');$notice=$raw.IndexOf('Đã tạm dừng heartbeat');$end=$raw.IndexOf('END_HISTORY')
    "begin_char=$begin`nkeyboard_notice_char=$notice`nend_char=$end`nnotice_inside_history=$($begin -lt $notice -and $notice -lt $end)" | Set-Content -Encoding UTF8 "$audit/console-interleave-proof.txt"
}
$exits | ConvertTo-Json | Set-Content -Encoding UTF8 "$audit/run-exits.json"
& (Join-Path $PSScriptRoot 'BuildReport.ps1')
Write-Output "Audit finished. Expected known failure, if still present: ConsoleHistoryInterleaveTest. Report: $audit/REPORT.md"
