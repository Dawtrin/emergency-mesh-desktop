param(
    [Parameter(Mandatory)]
    [ValidateSet('RELAY', 'VICTIM')]
    [string]$Mode,
    [Parameter(Mandatory)]
    [string]$NodeId,
    [Parameter(Mandatory)]
    [ValidateRange(1, 65535)]
    [int]$ListenPort,
    [string]$NextHopIp,
    [Parameter(Mandatory)]
    [ValidateRange(1, 65535)]
    [int]$NextHopPort,
    [string]$VictimId = 'NODE_A_VICTIM',
    [string]$VictimIp,
    [ValidateRange(1, 65535)]
    [int]$VictimPort = 8001,
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jar = Join-Path $projectRoot 'target\MeshNodeClient-jar-with-dependencies.jar'

function Read-IPv4([string]$prompt, [string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = Read-Host $prompt
    }
    $parsed = $null
    if (-not [System.Net.IPAddress]::TryParse($value, [ref]$parsed) -or
        $parsed.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
        $parsed.GetAddressBytes()[0] -eq 0 -or $parsed.GetAddressBytes()[0] -ge 224) {
        throw "IPv4 khong hop le: $value"
    }
    return $value
}

function Select-Jdk21 {
    $currentJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { '' }
    $currentVersion = if ($currentJava -and (Test-Path $currentJava)) {
        (& $currentJava --version | Select-Object -First 1) -join ''
    } else { '' }
    if ($currentVersion -notmatch '^(?:openjdk|java) (?<major>\d+)' -or [int]$Matches.major -lt 21) {
        $candidate = Get-ChildItem (Join-Path $HOME '.jdks') -Directory -ErrorAction SilentlyContinue |
            ForEach-Object {
                $java = Join-Path $_.FullName 'bin\java.exe'
                $line = if (Test-Path $java) { (& $java --version | Select-Object -First 1) -join '' } else { '' }
                if ($line -match '^(?:openjdk|java) (?<major>\d+)' -and [int]$Matches.major -ge 21) {
                    [PSCustomObject]@{ Home = $_.FullName; Major = [int]$Matches.major }
                }
            } | Sort-Object Major | Select-Object -First 1
        if ($candidate) { $env:JAVA_HOME = $candidate.Home }
    }
    if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }
    $version = (& java --version | Select-Object -First 1) -join ''
    if ($version -notmatch '^(?:openjdk|java) (?<major>\d+)' -or [int]$Matches.major -lt 21) {
        throw "Can JDK 21 tro len. Java hien tai: $version"
    }
    return $version
}

$NextHopIp = Read-IPv4 'Nhap IPv4 cua may next-hop' $NextHopIp
if ($Mode -eq 'RELAY') {
    $VictimIp = Read-IPv4 'Nhap IPv4 cua may Victim' $VictimIp
}

$javaVersion = Select-Jdk21
Set-Location $projectRoot
if ($Rebuild -or -not (Test-Path $jar)) {
    Write-Host 'Dang build project bang JDK 21...' -ForegroundColor Cyan
    & .\mvnw.cmd -q package
    if ($LASTEXITCODE -ne 0) { throw "Build that bai, exit code $LASTEXITCODE" }
}

Write-Host "Java: $javaVersion" -ForegroundColor Green
Write-Host "$Mode $NodeId lang nghe 0.0.0.0:$ListenPort" -ForegroundColor Green
Write-Host "Next hop: $NextHopIp`:$NextHopPort" -ForegroundColor Green
Write-Host "Firewall: mo dung TCP $ListenPort cho peer; khong tat firewall." -ForegroundColor Yellow

$arguments = @('-jar', $jar, '--mode', $Mode, '--id', $NodeId,
    '--bind-host', '0.0.0.0', '--bind-port', "$ListenPort",
    '--next-hop-host', $NextHopIp, '--next-hop-port', "$NextHopPort")
if ($Mode -eq 'RELAY') {
    $arguments += @('--victim-id', $VictimId, '--victim-host', $VictimIp, '--victim-port', "$VictimPort")
}

& java @arguments
if ($LASTEXITCODE -ne 0) { throw "$Mode dung voi exit code $LASTEXITCODE" }
