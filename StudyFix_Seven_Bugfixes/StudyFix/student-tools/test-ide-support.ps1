$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'run-ide-support.ps1') -StudyFixOrigin http://127.0.0.1:18766 -CheckOnly
$cases=@(
 @('Code','','VSCODE'),@('netbeans64','','NETBEANS'),@('javaw','javaw -Dnetbeans.home="C:\NetBeans\netbeans" org.netbeans.Main','NETBEANS'),
 @('java','java Main','OTHER'),@('chrome','Visual Studio Code - NetBeans','BROWSER'),@('msedge','','BROWSER'),@('firefox','','BROWSER'),@('powershell','NetBeans','OTHER')
)
foreach($case in $cases){$actual=[StudyFixIdeBridge]::Classify($case[0],$case[1]);if($actual -ne $case[2]){throw "Classifier mismatch: $($case[0]) -> $actual"}}
$scriptPath=Join-Path $PSScriptRoot 'run-ide-support.ps1'
$bridgeProcess=Start-Process powershell -WindowStyle Hidden -PassThru -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-File',('"'+$scriptPath+'"'),'-StudyFixOrigin','http://127.0.0.1:18766')
try{
 Add-Type -AssemblyName System.Net.Http
 $client=[Net.Http.HttpClient]::new();$client.Timeout=[TimeSpan]::FromSeconds(3)
 $response=$null
 for($attempt=0;$attempt -lt 30;$attempt++){
  try{$request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,'http://127.0.0.1:18765/status?since=0');$request.Headers.Add('Origin','http://127.0.0.1:18766');$response=$client.SendAsync($request).GetAwaiter().GetResult();break}catch{Start-Sleep -Milliseconds 100}
 }
 if(-not $response -or [int]$response.StatusCode -ne 200){throw 'Bridge did not start or origin rejected'}
 $body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
 if($body.protocol -ne 1 -or -not $body.boot -or $body.kind -notin @('VSCODE','NETBEANS','BROWSER','OTHER','UNKNOWN')){throw 'Invalid bridge status'}
 if($body.PSObject.Properties.Name -contains 'title' -or $body.PSObject.Properties.Name -contains 'process'){throw 'Foreground details leaked'}
 $bad=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get,'http://127.0.0.1:18765/status?since=0');$bad.Headers.Add('Origin','https://unrelated.example');if([int]$client.SendAsync($bad).GetAwaiter().GetResult().StatusCode -ne 403){throw 'Foreign origin was allowed'}
 $options=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Options,'http://127.0.0.1:18765/status?since=0');$options.Headers.Add('Origin','http://127.0.0.1:18766');if([int]$client.SendAsync($options).GetAwaiter().GetResult().StatusCode -ne 204){throw 'Preflight failed'}
 Write-Output 'Native IDE support PASS: classifier, Java/NetBeans identity, live foreground endpoint, CORS origin restrictions, preflight and metadata shape.'
}finally{if($client){$client.Dispose()};if(-not $bridgeProcess.HasExited){Stop-Process -Id $bridgeProcess.Id -Force}}