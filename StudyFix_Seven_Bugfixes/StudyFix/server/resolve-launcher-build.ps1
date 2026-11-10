# Source-specific build directory shared by both launchers.
$sourceFiles = @(Get-ChildItem -LiteralPath (Join-Path $project 'src') -Recurse -File | Sort-Object FullName)
$buildInputs = @((Join-Path $project 'build.xml'), (Join-Path $project 'nbproject/project.properties'))
$fingerprintText = (($sourceFiles.FullName + $buildInputs | ForEach-Object { $_.Substring($project.Length) + '=' + (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash }) -join "`n")
$digest = [System.Security.Cryptography.SHA256]::Create()
try { $buildKey = ([BitConverter]::ToString($digest.ComputeHash([Text.Encoding]::UTF8.GetBytes($fingerprintText)))).Replace('-', '').Substring(0, 16) }
finally { $digest.Dispose() }
$launcherBuild = Join-Path $project ('build-launcher/' + $buildKey)
