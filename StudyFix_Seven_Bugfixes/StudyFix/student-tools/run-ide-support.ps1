param(
    [Parameter(Mandatory=$true)][string]$StudyFixOrigin,
    [switch]$CheckOnly
)
$ErrorActionPreference='Stop'
$originUri=$null
if(-not [Uri]::TryCreate($StudyFixOrigin,[UriKind]::Absolute,[ref]$originUri) -or $originUri.Scheme -notin @('http','https') -or $originUri.UserInfo -or $originUri.AbsolutePath -ne '/' -or $originUri.Query -or $originUri.Fragment){throw 'StudyFixOrigin phai la dia chi web StudyFix, vi du http://192.168.1.251:8080'}
$allowedOrigin=$originUri.GetLeftPart([UriPartial]::Authority)
Add-Type -Path (Join-Path $PSScriptRoot 'StudyFixIdeBridge.cs') -ReferencedAssemblies System.Management
if($CheckOnly){Write-Output 'StudyFix IDE bridge compiled successfully.';return}
[StudyFixIdeBridge]::Run($allowedOrigin,18765)