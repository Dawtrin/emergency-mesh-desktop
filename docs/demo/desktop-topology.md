# Desktop topology for the midterm demonstration

## Purpose and boundary

This is a **desktop TCP demonstration**, not a phone mesh or a replacement for
Wi-Fi Direct/radio hardware. Three Java processes exchange real canonical
`MeshPacket` v1 frames over a local IPv4 network. Internet access is not used.

The application intentionally routes the demo path through the Relay even when
all three computers share one Wi-Fi hotspot and could otherwise reach each
other directly.

## Packet path

```text
Victim PC                 Relay PC                  Base Station PC
VICTIM-01                 RELAY-01                 BASE-01
SOS ────────────────>     validate + forward ───>   persist, alert, map
ACK for SOS <────────     validate + forward <───   optional acknowledgement

Dispatch <────────────    validate + forward <───   operator sends command
ACK for dispatch ─────>   validate + forward ───>   SQLite status becomes ACKED
```

At each forwarding hop the Relay makes a forwarding copy, decrements TTL,
increments hop count, adds its node ID to route history, and recomputes the
checksum. Invalid, duplicate, or expired packets are dropped.

## Local one-laptop mode

Use three terminals and loopback endpoints. These defaults are suitable only
for a local demonstration:

| Process | Node ID | Bind endpoint | Upstream / downstream endpoint |
|---|---|---|---|
| Base Station | `BASE-01` | `127.0.0.1:18888` | Relay `127.0.0.1:18002` |
| Relay | `RELAY-01` | `127.0.0.1:18002` | upstream Base `127.0.0.1:18888`; downstream Victim `127.0.0.1:18001` |
| Victim | `VICTIM-01` | `127.0.0.1:18001` | Relay `127.0.0.1:18002` |

Start Base, then Relay, then Victim. All endpoint values are command-line
configuration; none are inferred from a node-ID naming convention.

## Three-computer Wi-Fi/hotspot mode

Connect all laptops to the same private Wi-Fi router or phone hotspot. This is
a local network; mobile Internet is unnecessary. Find each laptop's IPv4 address
on the hotspot subnet and replace the example addresses below with the actual
addresses. Do **not** use `0.0.0.0` as a next-hop address: it is bind-only.

| Computer | Example LAN IP | Process | Listening port |
|---|---|---|---|
| Victim PC | `192.168.43.101` | Victim | `18001` |
| Relay PC | `192.168.43.102` | Relay | `18002` |
| Base PC | `192.168.43.103` | Base Station | `18888` |

Open Windows Defender Firewall inbound TCP rules for the one listening port on
each corresponding computer, only on the **Private** network profile. Do not
open these demo ports on a public network.

### LAN arguments

```powershell
# Base PC
java -jar target\BaseStationServer-jar-with-dependencies.jar --id BASE-01 --bind-host 0.0.0.0 --bind-port 18888 --relay-host 192.168.43.102 --relay-port 18002 --map-file C:\Maps\demo.mbtiles

# Relay PC
java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode RELAY --id RELAY-01 --bind-host 0.0.0.0 --bind-port 18002 --next-hop-host 192.168.43.103 --next-hop-port 18888 --victim-id VICTIM-01 --victim-host 192.168.43.101 --victim-port 18001

# Victim PC
java -jar target\MeshNodeClient-jar-with-dependencies.jar --mode VICTIM --id VICTIM-01 --bind-host 0.0.0.0 --bind-port 18001 --next-hop-host 192.168.43.102 --next-hop-port 18002
```

`--bind-host 0.0.0.0` accepts packets on all local network interfaces. The
`--next-hop-host`, `--relay-host`, and `--victim-host` arguments must always be
the real IP address or hostname of the destination computer.

## Demonstration checks

## Demo profile and preflight

The version-controlled LAN template is
[`config/demo-profile.properties`](../../config/demo-profile.properties). Replace
the example hotspot IPs and the `map.file` placeholder on each demo machine; do
not place passwords, API keys, or personal paths in the profile.

Before starting the applications, run from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\preflight-demo.ps1
```

It checks JDK 21+, built JARs, profile hosts/ports, local port availability, and
the MBTiles SQLite header without starting the full topology. It also rejects
`127.0.0.1`/`localhost` in a three-computer LAN profile. The script does not alter
Windows firewall or network settings; configure inbound TCP rules manually for
the Private profile only. See [troubleshooting](troubleshooting.md) for recovery.

1. Confirm each application log shows its configured bind endpoint.
2. Send SOS from Victim. The Relay log must report forwarding; the Base must
   show the same packet ID, `RELAY-01` in route history, and the SOS row/marker.
3. Send a dispatch from Base to `VICTIM-01`. The Victim card must appear, then
   Base must report `[ACKED]` only after the ACK returns through Relay.
4. If a step fails, record the packet ID and inspect the Relay log first. Common
   causes are incorrect subnet IP, Windows Firewall, an occupied port, or an
   incorrect map-file path.
