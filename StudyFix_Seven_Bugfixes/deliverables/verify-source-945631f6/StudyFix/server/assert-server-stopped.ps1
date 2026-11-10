# Check before Ant rebuilds the application JAR. Do not stop a running server automatically.
$taskJar = Join-Path $PSScriptRoot 'dist\studyfix.jar'
if (Test-Path -LiteralPath $taskJar) {
    $taskJarHandle = $null
    try {
        $taskJarHandle = [IO.File]::Open($taskJar, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    } catch [IO.IOException] {
        throw "StudyFix cu van dang giu dist\studyfix.jar. Dong cua so Server Manager cu hoac nhan Ctrl+C o terminal dang chay server, doi server dung han, roi chay lai lenh. Chua chay Ant clean; khong can xoa JAR hay database."
    } catch [UnauthorizedAccessException] {
        throw "Khong co quyen ghi dist\studyfix.jar. Kiem tra quyen truy cap hoac thuoc tinh Read-only cua file."
    } finally {
        if ($taskJarHandle) { $taskJarHandle.Dispose() }
    }
}
$taskWebPort = 8080
if ($env:STUDYFIX_WEB_PORT) {
    if (-not [int]::TryParse($env:STUDYFIX_WEB_PORT, [ref]$taskWebPort) -or $taskWebPort -lt 1 -or $taskWebPort -gt 65535) {
        throw 'STUDYFIX_WEB_PORT phai la so tu 1 den 65535.'
    }
}
$taskPorts = @(5000, 5001, 5002, $taskWebPort)
if ($env:STUDYFIX_WS_ENABLED -ne 'false') {
    $taskWsPort = $taskWebPort + 1
    if ($env:STUDYFIX_WS_PORT -and (-not [int]::TryParse($env:STUDYFIX_WS_PORT,[ref]$taskWsPort))) { throw 'STUDYFIX_WS_PORT phai la so.' }
    if ($taskWsPort -lt 1 -or $taskWsPort -gt 65535) { throw 'STUDYFIX_WS_PORT phai tu 1 den 65535.' }
    $taskPorts += $taskWsPort
}
$taskListeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
$taskBusyPorts = @($taskListeners | Where-Object { $_.Port -in $taskPorts } | ForEach-Object { $_.Port } | Sort-Object -Unique)
if ($taskBusyPorts.Count -gt 0) {
    throw "Cong $($taskBusyPorts -join ', ') dang duoc su dung. Dung server cu truoc khi mo StudyFix. Chua chay Ant clean."
}
