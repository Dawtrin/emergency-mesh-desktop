param([string]$UbuntuIp)

$ErrorActionPreference = 'Stop'
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw 'Hay mo PowerShell bang Run as Administrator de chay script nay.'
}
if ([string]::IsNullOrWhiteSpace($UbuntuIp)) {
    $UbuntuIp = Read-Host 'Nhap IPv4 Host-only cua Ubuntu VM'
}
$parsedIp = $null
if (-not [System.Net.IPAddress]::TryParse($UbuntuIp, [ref]$parsedIp) -or
    $parsedIp.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork -or
    [System.Net.IPAddress]::IsLoopback($parsedIp) -or
    $parsedIp.GetAddressBytes()[0] -eq 0 -or $parsedIp.GetAddressBytes()[0] -ge 224) {
    throw "UbuntuIp khong hop le: $UbuntuIp"
}

$ruleName = 'EmergencyMesh-Desktop-Base-18888'
$existing = Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue
if ($existing) {
    $existing | Remove-NetFirewallRule
}
New-NetFirewallRule `
    -Name $ruleName `
    -DisplayName 'Emergency Mesh Desktop Base 18888' `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort 18888 `
    -RemoteAddress $UbuntuIp `
    -Action Allow `
    -Profile Private | Out-Null

Write-Host "Da mo TCP 18888 chi cho Ubuntu $UbuntuIp tren profile Private." -ForegroundColor Green
Write-Host 'Kiem tra adapter Host-only bang Get-NetConnectionProfile; khong tat firewall.'
