param(
    [string]$Profile = "config/demo-profile.properties",
    [string]$JarDirectory = "target"
)

$ErrorActionPreference = "Stop"
$failed = $false
function Report([bool]$ok, [string]$message) { if ($ok) { Write-Host "PASS $message" } else { Write-Host "FAIL $message" -ForegroundColor Red; $script:failed = $true } }
function RequireValue($properties, [string]$name) { if (-not $properties.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($properties[$name])) { Report $false "missing profile value: $name"; return "" }; return $properties[$name].Trim() }

if (-not (Test-Path -LiteralPath $Profile -PathType Leaf)) { throw "Profile not found: $Profile" }
$properties = @{}
Get-Content -LiteralPath $Profile | ForEach-Object { $line = $_.Trim(); if ($line -and -not $line.StartsWith("#")) { $parts = $line.Split("=", 2); if ($parts.Length -eq 2) { $properties[$parts[0].Trim()] = $parts[1].Trim() } } }

try { $major = [int]((& java -version 2>&1 | Select-Object -First 1) -replace '.*?(\d+)(?:\..*)?"?$', '$1'); Report ($major -ge 21) "Java feature version $major (JDK 21+ required)" } catch { Report $false "Java JDK 21+ is unavailable" }
Report (Test-Path -LiteralPath (Join-Path $JarDirectory "BaseStationServer-jar-with-dependencies.jar") -PathType Leaf) "Base Station fat JAR exists"
Report (Test-Path -LiteralPath (Join-Path $JarDirectory "MeshNodeClient-jar-with-dependencies.jar") -PathType Leaf) "Node Client fat JAR exists"

foreach ($role in "base", "relay", "victim") {
    $bind = RequireValue $properties "$role.bindHost"; $peer = RequireValue $properties "$role.peerHost"; $portText = RequireValue $properties "$role.port"
    $port = 0; [void][int]::TryParse($portText, [ref]$port); Report ($port -ge 1 -and $port -le 65535) "$role port is valid"
    if ($bind -and $port -ge 1 -and $port -le 65535) { try { $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Parse($bind), $port); $listener.Start(); $listener.Stop(); Report $true "$role bind port $bind`:$port is available" } catch { Report $false "$role bind port $bind`:$port is occupied or unavailable" } }
}
$map = RequireValue $properties "map.file"; if ($map -and -not [IO.Path]::IsPathRooted($map)) { $map = Join-Path (Split-Path -Parent (Resolve-Path $Profile)) $map }
if (Test-Path -LiteralPath $map -PathType Leaf) { $bytes = [IO.File]::ReadAllBytes($map); $magic = [Text.Encoding]::ASCII.GetString($bytes, 0, [Math]::Min(16, $bytes.Length)); Report ($magic.StartsWith("SQLite format 3")) "map file appears to be valid MBTiles" } else { Report $false "map file is missing: $map" }
if ($properties["profile.threeComputerLan"] -eq "true" -and (($properties.Values | Where-Object { $_ -match '^(localhost|127\.0\.0\.1|::1)$' }).Count -gt 0)) { Report $false "LAN profile must not use 127.0.0.1/localhost" }
Write-Host "Firewall guidance: manually allow inbound TCP ports 18001, 18002, and 18888 only on the Windows Private network profile. Do not create rules automatically or expose them on Public."
if ($failed) { exit 1 }
