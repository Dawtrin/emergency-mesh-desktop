param(
    [Alias('RelayIp')]
    [string]$UbuntuIp,
    [switch]$Rebuild,
    [ValidateRange(1, 65535)]
    [int]$ListenPort = 18888,
    [ValidateRange(1, 65535)]
    [int]$RelayPort = 18002
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $projectRoot 'target\BaseStationServer-jar-with-dependencies.jar'
$map = Join-Path $projectRoot 'src\test\resources\fixtures\test-tiles.mbtiles'
$config = Join-Path $projectRoot 'config\basestation.properties'

if ([string]::IsNullOrWhiteSpace($UbuntuIp)) {
    $UbuntuIp = Read-Host 'Nhap IPv4 cua may chay RELAY (Wi-Fi/LAN hoac Ubuntu Host-only)'
}

$parsedIp = $null
if (-not [System.Net.IPAddress]::TryParse($UbuntuIp, [ref]$parsedIp) -or
    $parsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    [System.Net.IPAddress]::IsLoopback($parsedIp) -or
    $parsedIp.GetAddressBytes()[0] -eq 0 -or $parsedIp.GetAddressBytes()[0] -ge 224) {
    throw "IP Relay khong phai IPv4 peer hop le: $UbuntuIp"
}

$currentJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { '' }
$currentVersion = if ($currentJava -and (Test-Path $currentJava)) { (& $currentJava --version | Select-Object -First 1) -join '' } else { '' }
if ($currentVersion -notmatch '^(?:openjdk|java) (?<major>\d+)' -or [int]$Matches.major -lt 21) {
    $jdkRoots = @(
        (Join-Path $HOME '.jdks'),
        'C:\Program Files\Microsoft',
        'C:\Program Files\Eclipse Adoptium',
        'C:\Program Files\Java'
    )
    $candidates = foreach ($jdkRoot in $jdkRoots) {
        if (Test-Path $jdkRoot) {
            Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $java = Join-Path $_.FullName 'bin\java.exe'
                $line = if (Test-Path $java) { (& $java --version | Select-Object -First 1) -join '' } else { '' }
                if ($line -match '^(?:openjdk|java) (?<major>\d+)' -and [int]$Matches.major -ge 21) {
                    [PSCustomObject]@{ Home = $_.FullName; Major = [int]$Matches.major }
                }
            }
        }
    }
    $candidate = $candidates | Sort-Object Major | Select-Object -First 1
    if ($candidate) {
        $env:JAVA_HOME = $candidate.Home
    }
}
if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

$javaVersion = (& java --version | Select-Object -First 1) -join ''
if ($javaVersion -notmatch '^(?:openjdk|java) (?<major>\d+)') {
    throw 'Khong tim thay Java. Hay cai JDK 21 va dat JAVA_HOME.'
}
if ([int]$Matches.major -lt 21) {
    throw "Can JDK 21 tro len, hien tai: $javaVersion"
}

Set-Location $projectRoot
if ($Rebuild -or -not (Test-Path $jar)) {
    Write-Host 'Dang build project bang JDK 21...' -ForegroundColor Cyan
    & .\mvnw.cmd -q package
    if ($LASTEXITCODE -ne 0) {
        throw "Build that bai, exit code $LASTEXITCODE"
    }
}

Write-Host "Java: $javaVersion" -ForegroundColor Green
Write-Host "Base Station: 0.0.0.0:$ListenPort" -ForegroundColor Green
Write-Host "Relay: $UbuntuIp`:$RelayPort" -ForegroundColor Green
Write-Host "Firewall: chi cho phep TCP $ListenPort tu IP Relay; khong tat firewall." -ForegroundColor Yellow
Write-Host 'Dong cua so JavaFX hoac nhan Ctrl+C de dung.' -ForegroundColor Yellow

& java -jar $jar `
    --config $config `
    --bind-host 0.0.0.0 `
    --bind-port $ListenPort `
    --relay-host $UbuntuIp `
    --relay-port $RelayPort `
    --map-file $map

if ($LASTEXITCODE -ne 0) {
    throw "Base Station dung voi exit code $LASTEXITCODE"
}
