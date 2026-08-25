package com.rescue.mesh.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Tiện ích tính và kiểm tra SHA-256 Checksum cho MeshPacket.
 *
 * Cơ sở lý thuyết: SHA-256 (RFC 6234) — Cryptographic Hash Function
 * đảm bảo tính toàn vẹn dữ liệu (Data Integrity).
 * Mỗi thay đổi dù nhỏ nhất trong payload sẽ tạo ra checksum hoàn toàn khác.
 *
 * Ứng dụng trong hệ thống:
 *   - Node relay tính checksum của payload JSON trước khi gửi
 *   - Node nhận tính lại checksum và so sánh → phát hiện gói tin bị lỗi/giả mạo
 */
public class ChecksumUtil {

    private static final String ALGORITHM = "SHA-256";

    /**
     * Tính SHA-256 checksum của một chuỗi đầu vào (thường là JSON của payload).
     *
     * @param data Chuỗi dữ liệu cần tính checksum (không được null)
     * @return Chuỗi hex 64 ký tự đại diện cho SHA-256 hash
     * @throws IllegalArgumentException nếu data là null
     * @throws RuntimeException         nếu môi trường Java không hỗ trợ SHA-256 (rất hiếm)
     */
    public static String compute(String data) {
        if (data == null) {
            throw new IllegalArgumentException("[ChecksumUtil] Dữ liệu đầu vào không được null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 có mặt trong mọi JVM chuẩn (Java 7+), nhánh này hầu như không bao giờ xảy ra
            throw new RuntimeException("[ChecksumUtil] Môi trường JVM không hỗ trợ " + ALGORITHM, e);
        }
    }

    /**
     * Kiểm tra xem một chuỗi data có khớp với checksum đã cho không.
     *
     * @param data             Chuỗi dữ liệu gốc cần kiểm tra
     * @param expectedChecksum Checksum mong đợi (64 ký tự hex)
     * @return true nếu checksum khớp, false nếu dữ liệu bị thay đổi hoặc checksum sai
     */
    public static boolean verify(String data, String expectedChecksum) {
        if (data == null || expectedChecksum == null) {
            return false;
        }
        String actualChecksum = compute(data);
        return actualChecksum.equalsIgnoreCase(expectedChecksum);
    }

    /**
     * Chuyển mảng byte sang chuỗi hex lowercase.
     * Ví dụ: [0xA3, 0xF8] → "a3f8"
     *
     * @param bytes Mảng byte đầu vào
     * @return Chuỗi hex
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexBuilder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexBuilder.append(String.format("%02x", b));
        }
        return hexBuilder.toString();
    }
}