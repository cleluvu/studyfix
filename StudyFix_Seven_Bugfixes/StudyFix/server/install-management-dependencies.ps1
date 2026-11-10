$ErrorActionPreference = 'Stop'
$dependencies = @(
    @{File='Java-WebSocket-1.6.0.jar';Path='org/java-websocket/Java-WebSocket/1.6.0/Java-WebSocket-1.6.0.jar';Hash='EAE29213E4F16515639C28957200F011B3967FFFCADA1962CF0255D24919C22F'},
    @{File='slf4j-api-2.0.16.jar';Path='org/slf4j/slf4j-api/2.0.16/slf4j-api-2.0.16.jar';Hash='A12578DDE1BA00BD9B816D388A0B879928D00BAB3C83C240F7013BF4196C579A'},
    @{File='slf4j-simple-2.0.16.jar';Path='org/slf4j/slf4j-simple/2.0.16/slf4j-simple-2.0.16.jar';Hash='EFFC32018658BEA09D1E08C7D1060CCAD46C086960F583D07DD7FFE9C1172A47'}
)
foreach($dependency in $dependencies){
    $target=Join-Path $PSScriptRoot ('lib\'+$dependency.File)
    if(-not (Test-Path -LiteralPath $target)){Invoke-WebRequest -UseBasicParsing -Uri ('https://repo.maven.apache.org/maven2/'+$dependency.Path) -OutFile $target}
    if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne $dependency.Hash){throw ('Checksum mismatch: '+$dependency.File)}
    Write-Output ('Verified '+$dependency.File)
}
