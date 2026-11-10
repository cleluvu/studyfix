# Read literal KEY=value only. No Invoke-Expression, command expansion, or secret logging.
$envFile = if ($env:STUDYFIX_ENV_FILE) { $env:STUDYFIX_ENV_FILE } else { Join-Path $PSScriptRoot '.env' }
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in [IO.File]::ReadAllLines($envFile,[Text.Encoding]::UTF8)) {
        $valueLine = $line.Trim()
        if (-not $valueLine -or $valueLine.StartsWith('#')) { continue }
        if ($valueLine -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') { throw 'Invalid environment configuration line.' }
        $key = $Matches[1]; $value = $Matches[2].Trim()
        if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) { $value = $value.Substring(1,$value.Length-2) }
        if ($value -and -not [Environment]::GetEnvironmentVariable($key,'Process')) { [Environment]::SetEnvironmentVariable($key,$value,'Process') }
    }
}
