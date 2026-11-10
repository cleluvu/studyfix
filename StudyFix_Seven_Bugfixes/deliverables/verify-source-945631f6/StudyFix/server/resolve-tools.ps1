# Shared by the two launchers. Changes environment only for this PowerShell process.
$ErrorActionPreference = 'Stop'
if ($env:JAVA_HOME) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { throw "JAVA_HOME khong hop le: $env:JAVA_HOME" }
} else {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    $java = if ($javaCommand) { $javaCommand.Source } else { $null }
    if (-not $java) { throw 'Khong tim thay JDK. Dat JAVA_HOME toi JDK 26.' }
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java)
}
if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    throw 'JAVA_HOME phai tro toi JDK co javac.exe, khong phai JRE hoac java shim.'
}
$env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
$antCommand = Get-Command ant.bat -ErrorAction SilentlyContinue
$ant = if ($antCommand) { $antCommand.Source } else { $null }
if (-not $ant -and $env:ANT_HOME) {
    $candidate = Join-Path $env:ANT_HOME 'bin\ant.bat'
    if (Test-Path -LiteralPath $candidate) { $ant = $candidate }
}
if (-not $ant) {
    $candidate = Join-Path (Split-Path -Parent $env:JAVA_HOME) 'extide\ant\bin\ant.bat'
    if (Test-Path -LiteralPath $candidate) { $ant = $candidate }
}
if (-not $ant) {
    $downloads = Join-Path $env:USERPROFILE 'Downloads'
    if (Test-Path -LiteralPath $downloads) {
        $found = Get-ChildItem -LiteralPath $downloads -Filter ant.bat -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $ant = $found.FullName }
    }
}
if (-not $ant) { throw 'Khong tim thay Ant. Dat ANT_HOME hoac them ant.bat vao PATH.' }
