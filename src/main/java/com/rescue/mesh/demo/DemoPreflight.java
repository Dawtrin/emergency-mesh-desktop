package com.rescue.mesh.demo;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Safe, read-only checks used before a desktop demonstration. */
public final class DemoPreflight {
    public interface PortChecker { boolean isAvailable(String host, int port); }
    public record Result(boolean passed, List<String> messages) {}
    private DemoPreflight() {}

    public static Result check(DemoProfile profile, Path baseJar, Path nodeJar, int javaFeature, PortChecker ports) {
        List<String> messages = new ArrayList<>();
        if (javaFeature < 21) messages.add("FAIL Java " + javaFeature + ": cần JDK 21 hoặc mới hơn.");
        else messages.add("PASS JDK " + javaFeature);
        checkJar(baseJar, "Base Station", messages);
        checkJar(nodeJar, "Node Client", messages);
        checkEndpoint(profile.base(), "Base", ports, messages);
        checkEndpoint(profile.relay(), "Relay", ports, messages);
        checkEndpoint(profile.victim(), "Victim", ports, messages);
        checkMap(profile.mapFile(), messages);
        if (profile.threeComputerLan() && usesLoopback(profile)) {
            messages.add("FAIL LAN profile dùng 127.0.0.1/localhost; thay bằng IP hotspot của từng máy.");
        }
        messages.add("Firewall: tạo thủ công inbound TCP cho từng cổng demo chỉ trên mạng Windows Private; không mở trên Public.");
        return new Result(messages.stream().noneMatch(message -> message.startsWith("FAIL")), List.copyOf(messages));
    }

    public static boolean locallyAvailable(String host, int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(host, port));
            return true;
        } catch (IOException | IllegalArgumentException e) { return false; }
    }

    private static void checkJar(Path jar, String name, List<String> messages) {
        if (jar != null && Files.isRegularFile(jar) && size(jar) > 0) messages.add("PASS " + name + " JAR: " + jar);
        else messages.add("FAIL thiếu hoặc rỗng " + name + " JAR: " + jar);
    }
    private static long size(Path file) { try { return Files.size(file); } catch (IOException e) { return 0; } }
    private static void checkEndpoint(DemoProfile.Endpoint endpoint, String label, PortChecker ports, List<String> messages) {
        if (endpoint.bindHost().isBlank() || endpoint.peerHost().isBlank()) {
            messages.add("FAIL " + label + " thiếu host/IP."); return;
        }
        if (!ports.isAvailable(endpoint.bindHost(), endpoint.port())) messages.add("FAIL " + label + " port đang bận: " + endpoint.bindHost() + ':' + endpoint.port());
        else messages.add("PASS " + label + " port khả dụng: " + endpoint.bindHost() + ':' + endpoint.port());
    }
    private static void checkMap(Path map, List<String> messages) {
        try {
            if (!Files.isRegularFile(map) || Files.size(map) < 16) throw new IOException("file missing or too small");
            byte[] header = Files.readAllBytes(map);
            String magic = new String(header, 0, Math.min(16, header.length), java.nio.charset.StandardCharsets.US_ASCII);
            if (!magic.startsWith("SQLite format 3")) throw new IOException("not an SQLite/MBTiles file");
            messages.add("PASS MBTiles map: " + map);
        } catch (IOException e) { messages.add("FAIL map MBTiles thiếu/hỏng: " + map + " (" + e.getMessage() + ')'); }
    }
    private static boolean usesLoopback(DemoProfile profile) {
        return loopback(profile.base().bindHost()) || loopback(profile.base().peerHost())
                || loopback(profile.relay().bindHost()) || loopback(profile.relay().peerHost())
                || loopback(profile.victim().bindHost()) || loopback(profile.victim().peerHost());
    }
    private static boolean loopback(String host) { return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host); }
}
