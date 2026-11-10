param([string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin')
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $project
$mysqlProcess = $null
$savedEnvironment = @{}
try {
    . (Join-Path $project 'resolve-tools.ps1')
    $mysqld = Join-Path $MySqlBin 'mysqld.exe'
    if (-not (Test-Path -LiteralPath $mysqld)) { throw 'Khong tim thay mysqld.exe; truyen -MySqlBin hoac chay MySqlAdminIntegrationTest voi database _test rieng.' }
    $testRoot = Join-Path $project ('build\mysql-isolated-' + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
    $testRoot = [IO.Path]::GetFullPath($testRoot)
    if (-not $testRoot.StartsWith([IO.Path]::GetFullPath((Join-Path $project 'build')) + '\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid isolated test directory.' }
    $data = Join-Path $testRoot 'data'
    $initSql = Join-Path $testRoot 'initialize.sql'
    $errorLog = Join-Path $testRoot 'mysql.log'
    $baseDir = Split-Path -Parent $MySqlBin
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $listener.Start(); $testPort = $listener.LocalEndpoint.Port; $listener.Stop()
    $appPassword = 'A1_' + [guid]::NewGuid().ToString('N')
    $rootPassword = 'R1_' + [guid]::NewGuid().ToString('N')
    $sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`nCREATE DATABASE studyfix_integration_test CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;`nCREATE USER 'studyfix_test'@'127.0.0.1' IDENTIFIED BY '$appPassword';`nCREATE USER 'studyfix_test'@'localhost' IDENTIFIED BY '$appPassword';`nGRANT ALL ON studyfix_integration_test.* TO 'studyfix_test'@'127.0.0.1';`nGRANT ALL ON studyfix_integration_test.* TO 'studyfix_test'@'localhost';"
    [IO.File]::WriteAllText($initSql,$sql,[Text.UTF8Encoding]::new($false))
    Write-Output 'Initializing isolated MySQL datadir (existing MySQL80 service is not changed)...'
    & $mysqld '--no-defaults' '--initialize-insecure' "--basedir=$baseDir" "--datadir=$data" "--log-error=$errorLog"
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialize failed. See $errorLog" }
    $mysqlProcess = Start-Process -FilePath $mysqld -WindowStyle Hidden -PassThru -ArgumentList @('--no-defaults',"--basedir=`"$baseDir`"","--datadir=`"$data`"","--port=$testPort",'--bind-address=127.0.0.1','--mysqlx=0','--skip-log-bin',"--init-file=`"$initSql`"","--log-error=`"$errorLog`"")
    $ready = $false
    for ($i=0;$i -lt 100;$i++) {
        if ($mysqlProcess.HasExited) { throw "Isolated MySQL stopped. See $errorLog" }
        # TCP can bind before --init-file executes. Wait for actual MySQL readiness.
        if (Test-Path -LiteralPath $errorLog) {
            $logReader=$null
            try {
                $logStream=[IO.File]::Open($errorLog,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
                $logReader=[IO.StreamReader]::new($logStream)
                if ($logReader.ReadToEnd().Contains('ready for connections')) { $ready=$true; break }
            } catch [IO.IOException] { } finally { if($logReader){$logReader.Dispose()} }
        }
        Start-Sleep -Milliseconds 300
    }
    if (-not $ready) { throw "MySQL startup timed out. See $errorLog" }
    # Only the exact generated credential file is removed; no recursive delete.
    Remove-Item -LiteralPath $initSql
    $values = @{
        STUDYFIX_MANAGEMENT_DB='mysql'; STUDYFIX_MYSQL_URL="jdbc:mysql://127.0.0.1:$testPort/studyfix_integration_test?sslMode=DISABLED&allowPublicKeyRetrieval=true";
        STUDYFIX_MYSQL_USER='studyfix_test'; STUDYFIX_MYSQL_PASSWORD=$appPassword;
        STUDYFIX_MYSQL_IMPORT_SQLITE='true'; STUDYFIX_BOOTSTRAP_TEACHER='integration_admin';
        STUDYFIX_BOOTSTRAP_PASSWORD=('T9_' + [guid]::NewGuid().ToString('N'))
    }
    foreach($key in $values.Keys) { $savedEnvironment[$key]=[Environment]::GetEnvironmentVariable($key,'Process'); [Environment]::SetEnvironmentVariable($key,$values[$key],'Process') }
    $out=Join-Path $testRoot 'classes';New-Item -ItemType Directory -Force -Path $out | Out-Null
    $sources=@(Get-ChildItem -LiteralPath (Join-Path $project 'src') -Recurse -Filter '*.java' | ForEach-Object FullName)+@(Get-ChildItem -LiteralPath (Join-Path $project 'test') -Recurse -Filter '*.java' | ForEach-Object FullName)
    & (Join-Path $env:JAVA_HOME 'bin\javac.exe') -encoding UTF-8 -cp (Join-Path $project 'lib\*') -d $out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation failed.' }
    & $java '--enable-native-access=ALL-UNNAMED' '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' '-Dstderr.encoding=UTF-8' -cp "$out;$(Join-Path $project 'lib\*')" 'studyfix.MySqlAdminIntegrationTest'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL admin integration test failed.' }
    Write-Output 'PASS: isolated real MySQL; existing service and databases untouched.'
} finally {
    foreach($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key],'Process') }
    if ($mysqlProcess -and -not $mysqlProcess.HasExited) {
        # Stop only the process launched above, never the existing MySQL Windows service.
        Stop-Process -Id $mysqlProcess.Id
        $mysqlProcess.WaitForExit()
    }
    Pop-Location
}
