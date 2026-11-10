param(
    [Parameter(Mandatory=$true)][string]$ServerIp,
    [switch]$Configure,
    [string]$OutputDirectory,
    [string]$EnvFile
)
$ErrorActionPreference='Stop'
$project=$PSScriptRoot
$address=$null
if(-not [Net.IPAddress]::TryParse($ServerIp,[ref]$address) -or $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork){throw 'ServerIp must be the IPv4 address of the LAN server.'}
$keytoolCommand=Get-Command keytool.exe -ErrorAction SilentlyContinue
$keytool=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin\keytool.exe'}elseif($keytoolCommand){$keytoolCommand.Source}else{$null}
if(-not $keytool -or -not(Test-Path -LiteralPath $keytool)){throw 'Cannot find JDK keytool.exe. Set JAVA_HOME to the installed JDK.'}
if(-not $OutputDirectory){$OutputDirectory=Join-Path $project ('data\https\'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8))}
if(-not [IO.Path]::IsPathRooted($OutputDirectory)){$OutputDirectory=Join-Path $project $OutputDirectory}
$out=[IO.Path]::GetFullPath($OutputDirectory)
if(-not $out.StartsWith([IO.Path]::GetFullPath($project)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'OutputDirectory must stay inside the server project.'}
if(Test-Path -LiteralPath $out){throw 'OutputDirectory already exists. Existing certificates will not be overwritten.'}
if(-not $EnvFile){$EnvFile=Join-Path $project '.env'}
if(-not [IO.Path]::IsPathRooted($EnvFile)){$EnvFile=Join-Path $project $EnvFile}
$EnvFile=[IO.Path]::GetFullPath($EnvFile)
if(-not $EnvFile.StartsWith([IO.Path]::GetFullPath($project)+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'EnvFile must stay inside the server project.'}
New-Item -ItemType Directory -Path $out -Force | Out-Null
function New-Secret {
    $bytes=New-Object byte[] 32;$rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($bytes);return [Convert]::ToBase64String($bytes)}finally{$rng.Dispose()}
}
function Invoke-Keytool([string[]]$Arguments){
    $previousPreference=$ErrorActionPreference
    try{$ErrorActionPreference='Continue';$result=& $keytool @Arguments 2>&1;$code=$LASTEXITCODE}finally{$ErrorActionPreference=$previousPreference}
    if($code -ne 0){throw ('keytool failed: '+($result | Out-String))}
}
$oldCa=[Environment]::GetEnvironmentVariable('STUDYFIX_TLS_SETUP_CA','Process')
$oldLeaf=[Environment]::GetEnvironmentVariable('STUDYFIX_TLS_SETUP_LEAF','Process')
$ca=Join-Path $out 'temporary-ca.p12';$root=Join-Path $out 'studyfix-lan-root.cer';$leaf=Join-Path $out 'studyfix-server.p12'
try{
    $env:STUDYFIX_TLS_SETUP_CA=New-Secret;$env:STUDYFIX_TLS_SETUP_LEAF=New-Secret
    $caOptions=@('-keystore',$ca,'-storetype','PKCS12','-storepass:env','STUDYFIX_TLS_SETUP_CA')
    $leafOptions=@('-keystore',$leaf,'-storetype','PKCS12','-storepass:env','STUDYFIX_TLS_SETUP_LEAF')
    Invoke-Keytool (@('-genkeypair','-alias','studyfix-ca','-keyalg','RSA','-keysize','3072','-sigalg','SHA256withRSA','-dname','CN=StudyFix LAN Local CA','-validity','1825','-ext','BC:critical=ca:true,pathlen:0','-ext','KU:critical=keyCertSign,cRLSign','-noprompt')+$caOptions)
    Invoke-Keytool (@('-exportcert','-alias','studyfix-ca','-file',$root)+$caOptions)
    Invoke-Keytool (@('-genkeypair','-alias','studyfix-server','-keyalg','RSA','-keysize','2048','-sigalg','SHA256withRSA','-dname',('CN='+$ServerIp),'-validity','365','-noprompt')+$leafOptions)
    $csr=Join-Path $out 'server.csr';$signed=Join-Path $out 'server-signed.cer'
    Invoke-Keytool (@('-certreq','-alias','studyfix-server','-file',$csr)+$leafOptions)
    $san='SAN=ip:'+$ServerIp+',ip:127.0.0.1,ip:::1,dns:localhost'
    Invoke-Keytool (@('-gencert','-alias','studyfix-ca','-infile',$csr,'-outfile',$signed,'-validity','365','-ext',$san,'-ext','BC:critical=ca:false','-ext','KU:critical=digitalSignature,keyEncipherment','-ext','EKU=serverAuth')+$caOptions)
    Invoke-Keytool (@('-importcert','-alias','studyfix-ca','-file',$root,'-noprompt')+$leafOptions)
    Invoke-Keytool (@('-importcert','-alias','studyfix-server','-file',$signed,'-noprompt')+$leafOptions)
    # The CA signing key is not needed after this server certificate has been issued.
    Remove-Item -LiteralPath $ca
    $settings=@{STUDYFIX_WEB_KEYSTORE=$leaf.Replace('\','/');STUDYFIX_WEB_KEYSTORE_PASSWORD=$env:STUDYFIX_TLS_SETUP_LEAF;STUDYFIX_WS_ENABLED='true'}
    $configuration=Join-Path $out 'https.env'
    [IO.File]::WriteAllLines($configuration,@($settings.GetEnumerator() | ForEach-Object {$_.Key+'='+$_.Value}),[Text.UTF8Encoding]::new($false))
    if($Configure){
        if(Test-Path -LiteralPath $EnvFile){Copy-Item -LiteralPath $EnvFile -Destination (Join-Path $out 'env-before-https.backup');$lines=@([IO.File]::ReadAllLines($EnvFile,[Text.Encoding]::UTF8))}
        else{$lines=@([IO.File]::ReadAllLines((Join-Path $project '.env.example'),[Text.Encoding]::UTF8))}
        foreach($key in $settings.Keys){$lines=@($lines | Where-Object {$_ -notmatch ('^\s*'+[regex]::Escape($key)+'\s*=')});$lines+=($key+'='+$settings[$key])}
        [IO.File]::WriteAllLines($EnvFile,$lines,[Text.UTF8Encoding]::new($false))
    }
    $cert=[Security.Cryptography.X509Certificates.X509Certificate2]::new($root)
    try{[PSCustomObject]@{ServerUrl='https://'+$ServerIp+':8080';CertificateFile=$root;CertificateThumbprint=$cert.Thumbprint;EnvUpdated=[bool]$Configure;ConfigurationFile=$configuration}}finally{$cert.Dispose()}
}finally{
    [Environment]::SetEnvironmentVariable('STUDYFIX_TLS_SETUP_CA',$oldCa,'Process')
    [Environment]::SetEnvironmentVariable('STUDYFIX_TLS_SETUP_LEAF',$oldLeaf,'Process')
}
