param([Parameter(Mandatory=$true)][string]$Username,[Parameter(Mandatory=$true)][string]$DisplayName)
$ErrorActionPreference='Stop'
$project=Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $project
try{
    . (Join-Path $project 'load-env.ps1')
    . (Join-Path $project 'resolve-tools.ps1')
    $appJar=Join-Path $project 'dist\studyfix.jar'
    if(-not (Test-Path -LiteralPath $appJar)){throw 'Build server truoc khi cap tai khoan Teacher.'}
    & $java '-Dfile.encoding=UTF-8' -cp "$appJar;$(Join-Path $project 'lib\*')" 'studyfix.ManagementCli' 'create-teacher' $Username $DisplayName
    if($LASTEXITCODE -ne 0){throw 'Khong tao duoc Teacher. Kiem tra cau hinh va mat khau.'}
}finally{Pop-Location}
