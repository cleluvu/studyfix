$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-26'
$env:ANT_HOME = 'C:\Apache\ant\apache-ant-1.10.18'
$env:Path = "$env:ANT_HOME\bin;$env:JAVA_HOME\bin;$env:Path"
Set-Location (Split-Path -Parent $MyInvocation.MyCommand.Path)
# Run Server Manager
.\server\run-server-manager.ps1

