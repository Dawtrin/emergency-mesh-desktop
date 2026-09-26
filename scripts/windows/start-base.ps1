param(
    [string]$UbuntuIp,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $projectRoot 'target\BaseStationServer-jar-with-dependencies.jar'
$map = Join-Path $projectRoot 'src\test\resources\fixtures\test-tiles.mbtiles'
$config = Join-Path $projectRoot 'config\basestation.properties'

if ([string]::IsNullOrWhiteSpace($UbuntuIp)) {
    $UbuntuIp = Read-Host 'Nhap IPv4 Host-only cua Ubuntu VM (vi du 192.168.56.101)'
}

$parsedIp = $null
if (-not [System.Net.IPAddress]::TryParse($UbuntuIp, [ref]$parsedIp) -or
    $parsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    [System.Net.IPAddress]::IsLoopback($parsedIp) -or
    $parsedIp.GetAddressBytes()[0] -eq 0 -or $parsedIp.GetAddressBytes()[0] -ge 224) {
    throw "UbuntuIp khong phai IPv4 hop le: $UbuntuIp"
}

$currentJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { '' }
$currentVersion = if ($currentJava -and (Test-Path $currentJava)) { (& $currentJava --version | Select-Object -First 1) -join '' } else { '' }
if ($currentVersion -notmatch '^(?:openjdk|java) (?<major>\d+)' -or [int]$Matches.major -lt 21) {
    $candidate = Get-ChildItem (Join-Path $HOME '.jdks') -Directory -ErrorAction SilentlyContinue |
        ForEach-Object {
            $java = Join-Path $_.FullName 'bin\java.exe'
            $line = if (Test-Path $java) { (& $java --version | Select-Object -First 1) -join '' } else { '' }
            if ($line -match '^(?:openjdk|java) (?<major>\d+)' -and [int]$Matches.major -ge 21) {
                [PSCustomObject]@{ Home = $_.FullName; Major = [int]$Matches.major }
            }
        } | Sort-Object Major | Select-Object -First 1
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

Write-Host "Base Station: 0.0.0.0:18888" -ForegroundColor Green
Write-Host "Relay Ubuntu: $UbuntuIp`:18002" -ForegroundColor Green
Write-Host 'Dong cua so JavaFX hoac nhan Ctrl+C de dung.' -ForegroundColor Yellow

& java -jar $jar `
    --config $config `
    --relay-host $UbuntuIp `
    --map-file $map

if ($LASTEXITCODE -ne 0) {
    throw "Base Station dung voi exit code $LASTEXITCODE"
}
