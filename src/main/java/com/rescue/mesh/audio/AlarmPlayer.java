package com.rescue.mesh.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;

/**
 * Trình phát âm thanh báo động SOS cho Base Station.
 *
 * Cơ sở lý thuyết:
 *   Cảnh báo âm thanh là thành phần thiết yếu trong hệ thống C2
 *   (Command & Control) cứu nạn. Tiêu chuẩn NFPA 72 quy định
 *   tín hiệu cảnh báo phải có tần số 520-1480 Hz, dạng quét tần số
 *   (frequency sweep) để phân biệt với tín hiệu thông thường.
 *
 * Kỹ thuật:
 *   1. Thử load file alarm.mp3 từ resources/sound/
 *   2. Nếu không tìm thấy → tự sinh âm thanh siren bằng thuật toán
 *      Phase Accumulation Synthesis (tần số quét 500Hz → 1200Hz)
 *   3. Fallback cuối: java.awt.Toolkit.beep()
 *
 *   Thuật toán Phase Accumulation:
 *     - Tránh artifact âm thanh khi thay đổi tần số liên tục
 *     - Tích lũy pha (phase += 2π × freq / sampleRate) thay vì
 *       tính sin(2π × freq × t) trực tiếp
 *     - Kết quả: siren sweep mượt mà, không bị "click" giữa các frame
 *
 * Thread Safety:
 *   - Synchronized methods cho play/stop
 *   - volatile boolean cho trạng thái playing
 *   - Có thể gọi từ bất kỳ thread nào
 */
public class AlarmPlayer {

    // ===== THAM SỐ ÂM THANH =====

    /** Tần số lấy mẫu (samples/giây) — chuẩn CD quality */
    private static final float SAMPLE_RATE = 44100.0f;

    /** Tần số thấp nhất của siren sweep (Hz) */
    private static final double MIN_FREQ = 500.0;

    /** Tần số cao nhất của siren sweep (Hz) */
    private static final double MAX_FREQ = 1200.0;

    /** Thời lượng 1 chu kỳ siren (giây) — lên rồi xuống */
    private static final double SWEEP_DURATION = 1.5;

    /** Tổng thời lượng file âm thanh tạo sẵn (giây) */
    private static final double TOTAL_DURATION = 3.0;

    /** Biên độ tín hiệu (0.0 → 1.0) */
    private static final double AMPLITUDE = 0.7;

    // ===== STATE =====

    /** Clip âm thanh hiện tại — null nếu chưa khởi tạo */
    private Clip currentClip;

    /** Trạng thái đang phát — volatile cho thread visibility */
    private volatile boolean playing = false;

    /** Âm lượng (0.0 → 1.0) */
    private double volume = 0.8;

    /** Singleton-like: byte array siren đã tạo sẵn — tạo 1 lần, dùng nhiều lần */
    private byte[] cachedSirenData;

    /** Audio format cho siren tự tạo: 16-bit PCM, mono, little-endian */
    private final AudioFormat sirenFormat = new AudioFormat(
            SAMPLE_RATE,  // sample rate
            16,           // sample size in bits
            1,            // channels (mono)
            true,         // signed
            false         // little-endian
    );

    // =========================================================
    // KHỞI TẠO
    // =========================================================

    /**
     * Constructor — chuẩn bị sẵn dữ liệu siren.
     * Gọi generateSirenTone() để tạo byte array một lần duy nhất.
     */
    public AlarmPlayer() {
        this.cachedSirenData = generateSirenTone();
    }

    // =========================================================
    // SINH ÂM THANH SIREN
    // =========================================================

    /**
     * Sinh âm thanh siren bằng Phase Accumulation Synthesis.
     *
     * Thuật toán:
     *   1. Quét tần số từ MIN_FREQ lên MAX_FREQ (nửa đầu chu kỳ)
     *   2. Quét ngược từ MAX_FREQ xuống MIN_FREQ (nửa sau chu kỳ)
     *   3. Dùng phase accumulation để tránh click artifact
     *   4. Apply fade-in/fade-out envelope ở đầu/cuối
     *
     * @return Mảng byte PCM 16-bit mono — sẵn sàng nạp vào Clip
     */
    private byte[] generateSirenTone() {
        int totalSamples = (int) (SAMPLE_RATE * TOTAL_DURATION);
        byte[] buffer = new byte[totalSamples * 2]; // 16-bit = 2 bytes/sample

        double phase = 0.0; // Phase accumulator

        for (int i = 0; i < totalSamples; i++) {
            double time = (double) i / SAMPLE_RATE;

            // Tính vị trí trong chu kỳ sweep (0.0 → 1.0)
            double cyclePosition = (time % SWEEP_DURATION) / SWEEP_DURATION;

            // Tạo dạng tam giác: 0→1→0 (lên rồi xuống)
            double triangleWave;
            if (cyclePosition < 0.5) {
                triangleWave = cyclePosition * 2.0; // 0 → 1 (nửa đầu: tần số tăng)
            } else {
                triangleWave = 2.0 - cyclePosition * 2.0; // 1 → 0 (nửa sau: tần số giảm)
            }

            // Tính tần số hiện tại: nội suy giữa MIN_FREQ và MAX_FREQ
            double currentFreq = MIN_FREQ + triangleWave * (MAX_FREQ - MIN_FREQ);

            // Phase Accumulation: tích lũy pha để tránh click
            phase += 2.0 * Math.PI * currentFreq / SAMPLE_RATE;
            if (phase > 2.0 * Math.PI) {
                phase -= 2.0 * Math.PI; // Giữ phase trong [0, 2π] tránh overflow
            }

            double sample = Math.sin(phase) * AMPLITUDE;

            // Envelope: fade-in 50ms đầu, fade-out 50ms cuối
            int fadeSamples = (int) (SAMPLE_RATE * 0.05);
            double envelope = 1.0;
            if (i < fadeSamples) {
                envelope = (double) i / fadeSamples;
            } else if (i > totalSamples - fadeSamples) {
                envelope = (double) (totalSamples - i) / fadeSamples;
            }

            // Chuyển sang 16-bit signed integer
            short pcmValue = (short) (sample * envelope * volume * Short.MAX_VALUE);

            // Little-endian: byte thấp trước, byte cao sau
            buffer[i * 2] = (byte) (pcmValue & 0xFF);
            buffer[i * 2 + 1] = (byte) ((pcmValue >> 8) & 0xFF);
        }

        System.out.println("[INFO] AlarmPlayer: đã tạo siren tone — "
                + totalSamples + " samples, " + TOTAL_DURATION + "s");
        return buffer;
    }

    // =========================================================
    // PHÁT ÂM THANH
    // =========================================================

    /**
     * Phát còi báo động 1 lần (không lặp).
     * Thread-safe: có thể gọi từ bất kỳ thread nào.
     */
    public synchronized void play() {
        playInternal(false);
    }

    /**
     * Phát còi báo động lặp liên tục cho đến khi gọi stop().
     * Dùng khi nhận SOS CRITICAL — kêu cho đến khi chỉ huy xác nhận.
     */
    public synchronized void playLoop() {
        playInternal(true);
    }

    /**
     * Logic phát nội bộ — thử từng phương pháp theo thứ tự ưu tiên.
     *
     * @param loop true nếu lặp liên tục, false nếu phát 1 lần
     */
    private void playInternal(boolean loop) {
        // Dừng clip cũ nếu đang phát
        stopInternal();

        // Thử phương pháp 1: Load alarm.mp3 từ resources
        boolean loaded = tryLoadFromResources();

        // Phương pháp 2: Dùng siren tự tạo
        if (!loaded) {
            loaded = tryPlaySynthesized(loop);
        }

        // Phương pháp 3: Fallback — system beep
        if (!loaded) {
            System.out.println("[WARN] AlarmPlayer: không phát được âm thanh — dùng system beep");
            try {
                java.awt.Toolkit.getDefaultToolkit().beep();
            } catch (Exception e) {
                System.err.println("[ERROR] AlarmPlayer: system beep cũng thất bại: " + e.getMessage());
            }
        }
    }

    /**
     * Thử load file alarm.mp3 từ classpath resources.
     *
     * @return true nếu load và phát thành công
     */
    private boolean tryLoadFromResources() {
        try {
            URL soundUrl = getClass().getResource("/sound/alarm.mp3");
            if (soundUrl == null) {
                return false;
            }

            currentClip = AudioSystem.getClip();
            currentClip.open(AudioSystem.getAudioInputStream(soundUrl));
            currentClip.start();
            playing = true;
            System.out.println("[INFO] AlarmPlayer: phát alarm.mp3 từ resources — OK");
            return true;

        } catch (Exception e) {
            System.out.println("[WARN] AlarmPlayer: không load được alarm.mp3 — " + e.getMessage());
            return false;
        }
    }

    /**
     * Phát siren tự tạo từ dữ liệu PCM đã cache.
     *
     * @param loop true nếu lặp liên tục
     * @return true nếu phát thành công
     */
    private boolean tryPlaySynthesized(boolean loop) {
        try {
            if (cachedSirenData == null || cachedSirenData.length == 0) {
                cachedSirenData = generateSirenTone();
            }

            InputStream byteStream = new ByteArrayInputStream(cachedSirenData);
            javax.sound.sampled.AudioInputStream audioInputStream =
                    new javax.sound.sampled.AudioInputStream(
                            byteStream,
                            sirenFormat,
                            cachedSirenData.length / sirenFormat.getFrameSize()
                    );

            currentClip = AudioSystem.getClip();
            currentClip.open(audioInputStream);

            // Điều chỉnh âm lượng nếu có Master Gain control
            try {
                FloatControl gainControl = (FloatControl) currentClip.getControl(
                        FloatControl.Type.MASTER_GAIN);
                // Chuyển volume (0.0-1.0) sang decibel (-80 → 0 dB)
                float dB = (float) (20.0 * Math.log10(Math.max(volume, 0.0001)));
                dB = Math.max(dB, gainControl.getMinimum());
                dB = Math.min(dB, gainControl.getMaximum());
                gainControl.setValue(dB);
            } catch (IllegalArgumentException e) {
                // Một số hệ thống không hỗ trợ Master Gain — bỏ qua
            }

            if (loop) {
                currentClip.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                currentClip.start();
            }

            playing = true;
            System.out.println("[INFO] AlarmPlayer: phát siren synthesized — "
                    + (loop ? "LOOP" : "ONCE"));
            return true;

        } catch (LineUnavailableException e) {
            System.err.println("[ERROR] AlarmPlayer: audio line không khả dụng: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] AlarmPlayer: lỗi phát siren: " + e.getMessage());
            return false;
        }
    }

    // =========================================================
    // DỪNG ÂM THANH
    // =========================================================

    /**
     * Dừng phát âm thanh đang chạy.
     * Thread-safe: có thể gọi từ bất kỳ thread nào.
     */
    public synchronized void stop() {
        stopInternal();
    }

    /**
     * Logic dừng nội bộ — đóng clip và giải phóng audio line.
     */
    private void stopInternal() {
        playing = false;
        if (currentClip != null) {
            try {
                if (currentClip.isRunning()) {
                    currentClip.stop();
                }
                currentClip.close();
            } catch (Exception e) {
                System.err.println("[ERROR] AlarmPlayer.stop: " + e.getMessage());
            }
            currentClip = null;
        }
    }

    // =========================================================
    // ĐIỀU CHỈNH ÂM LƯỢNG
    // =========================================================

    /**
     * Đặt âm lượng phát.
     *
     * @param volume Giá trị từ 0.0 (im lặng) đến 1.0 (tối đa)
     */
    public void setVolume(double volume) {
        this.volume = Math.max(0.0, Math.min(1.0, volume));
    }

    /**
     * @return Âm lượng hiện tại (0.0 → 1.0)
     */
    public double getVolume() {
        return volume;
    }

    /**
     * @return true nếu đang phát âm thanh
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * Giải phóng toàn bộ tài nguyên.
     * Gọi khi thoát ứng dụng.
     */
    public synchronized void dispose() {
        stopInternal();
        cachedSirenData = null;
        System.out.println("[INFO] AlarmPlayer: đã giải phóng tài nguyên.");
    }
}
