package com.rescue.mesh.controller;

import com.rescue.mesh.db.RelayNodeDao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceRegistryApiController {

    private final RelayNodeDao relayNodeDao;

    @Value("${mesh.tcp.port:8888}")
    private int meshPort;

    public DeviceRegistryApiController(RelayNodeDao relayNodeDao) {
        this.relayNodeDao = relayNodeDao;
    }

    /**
     * Lấy danh sách tất cả thiết bị tham gia mạng Mesh (Node Nạn nhân, Relay Node).
     */
    @GetMapping
    public List<RelayNodeDao.RelayNodeRecord> listDevices() {
        return relayNodeDao.findAll();
    }

    /**
     * Tạo chuỗi dữ liệu cấu hình để vẽ mã QR Code ghép đôi thiết bị Android (Zero-Config).
     * Tự động phát hiện IP LAN thực tế của máy trạm C2 để điện thoại Android kết nối thành công qua Wi-Fi dã chiến.
     */
    @GetMapping("/qr-config")
    public ResponseEntity<Map<String, Object>> getQrConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("meshPort", meshPort);
        config.put("protocol", "TCP_JSON_MESH");
        config.put("version", "v1.0");
        config.put("defaultTtl", 5);
        config.put("networkSsid", "MeshRescue_C2");

        List<String> lanIps = new ArrayList<>();
        String primaryIp = "127.0.0.1";

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        lanIps.add(ip);
                        // Ưu tiên dải mạng Wi-Fi/Ethernet thông dụng dã chiến 192.168.x.x hoặc 10.x.x.x
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            primaryIp = ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        if (!lanIps.isEmpty() && "127.0.0.1".equals(primaryIp)) {
            primaryIp = lanIps.get(0);
        }

        config.put("primaryLanIp", primaryIp);
        config.put("lanIpAddresses", lanIps);

        return ResponseEntity.ok(config);
    }
}
