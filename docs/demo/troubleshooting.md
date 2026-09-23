# Desktop demo troubleshooting

Run `powershell -ExecutionPolicy Bypass -File scripts/preflight-demo.ps1` before
the demonstration. It is read-only: it never changes Windows Firewall or network
security settings.

Start the Base Station first, then Relay, then Victim. This ensures a Victim SOS
has an available path and a Base dispatch can return through the Relay.

| Symptom | Check and recovery |
| --- | --- |
| Wrong Java version | Install/select JDK 21 or newer; `java -version` must report 21+. |
| Port occupied | Stop the process using the listed port or choose a free port in all matching endpoints. |
| Incorrect hotspot IP | Use each computer's current IPv4 address on the same hotspot subnet; never use `0.0.0.0` as a peer address. |
| Windows Public network | Set the hotspot/network to **Private** before allowing the demo ports. |
| Firewall blocking | Manually allow inbound TCP 18001, 18002, and 18888 only for the Private profile. Do not open them on Public. |
| Relay unavailable | The UI shows `RETRY_WAIT` then `FAILED`; start/fix Relay and send a new SOS or let the persisted dispatch outbox retry. |
| Missing/corrupt map | Replace `map.file` with a readable MBTiles (SQLite) database. The command UI still works without a map. |
| Database location | Default Base Station data is `data/mesh_desktop.db`; do not delete it when pending dispatches need to survive restart. |

`CONNECTED` means the application completed an outbound TCP write to its peer.
It does not mean an end-to-end dispatch is delivered: only a correlated `ACKED`
dispatch is confirmed by the victim. The persisted outbox states are `PENDING`,
`SENT` (shown as waiting for ACK), `ACKED`, and `FAILED`; socket connection state
is session-only and is not persisted.
