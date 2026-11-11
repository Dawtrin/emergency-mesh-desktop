/**
 * C2 Audio Engine — Web Audio API Synthesizer Siren & Visual Strobe Alarm
 * Sweeping frequency 800Hz–1500Hz, 100% Offline (no external audio files)
 */
class C2AudioEngine {
  constructor() {
    this.audioCtx = null;
    this.isMuted = false;
    this.isPlaying = false;
    this.oscillator = null;
    this.gainNode = null;
    this.alarmTimer = null;

    // Unlock audio on first user gesture (browser autoplay policy)
    const unlock = () => {
      this.initContext();
      window.removeEventListener('click', unlock);
      window.removeEventListener('keydown', unlock);
    };
    window.addEventListener('click', unlock);
    window.addEventListener('keydown', unlock);
  }

  initContext() {
    if (!this.audioCtx) {
      const AC = window.AudioContext || window.webkitAudioContext;
      this.audioCtx = new AC();
    }
    if (this.audioCtx.state === 'suspended') this.audioCtx.resume();
  }

  toggleMute() {
    this.isMuted = !this.isMuted;
    if (this.isMuted && this.isPlaying) this.stopAlarm();

    const btn = document.getElementById('btn-mute-toggle');
    if (btn) {
      btn.textContent = this.isMuted ? '🔇 ĐÃ TẮT CÒI' : '🔊 CÒI BÁO ĐỘNG';
      btn.classList.toggle('danger', !this.isMuted);
    }
    c2App.showToast(this.isMuted ? '🔇 Đã tắt còi hú' : '🔊 Còi hú đã bật', 'info');
    return this.isMuted;
  }

  /**
   * Kích hoạt còi hú dã chiến quét tần số (800Hz–1500Hz)
   * @param {number} dur — Thời lượng hú (giây)
   */
  triggerSosAlarm(dur = 6) {
    if (this.isMuted) return;
    this.initContext();

    // Visual strobe
    const strobe = document.getElementById('strobe-alarm');
    if (strobe) strobe.classList.add('active');

    if (this.isPlaying) return;
    this.isPlaying = true;

    try {
      const now = this.audioCtx.currentTime;
      this.oscillator = this.audioCtx.createOscillator();
      this.gainNode = this.audioCtx.createGain();

      this.oscillator.type = 'sawtooth';

      // Sweep 800↔1500 Hz
      const sweep = 0.4;
      const cycles = Math.floor(dur / (sweep * 2));
      for (let i = 0; i < cycles * 2; i++) {
        const t = now + i * sweep;
        this.oscillator.frequency.linearRampToValueAtTime(i % 2 === 0 ? 1500 : 800, t);
      }

      // Volume control
      const vol = parseInt(document.getElementById('cfg-volume')?.value || '80') / 100;
      this.gainNode.gain.setValueAtTime(0.15 * vol, now);
      this.gainNode.gain.exponentialRampToValueAtTime(0.001, now + dur);

      this.oscillator.connect(this.gainNode);
      this.gainNode.connect(this.audioCtx.destination);
      this.oscillator.start(now);
      this.oscillator.stop(now + dur);

      this.oscillator.onended = () => this.stopAlarm();
      if (this.alarmTimer) clearTimeout(this.alarmTimer);
      this.alarmTimer = setTimeout(() => this.stopAlarm(), dur * 1000);
    } catch (e) {
      console.warn('[Audio] Error:', e);
      this.stopAlarm();
    }
  }

  stopAlarm() {
    this.isPlaying = false;
    const strobe = document.getElementById('strobe-alarm');
    if (strobe) strobe.classList.remove('active');
    if (this.oscillator) {
      try { this.oscillator.stop(); this.oscillator.disconnect(); } catch (e) {}
      this.oscillator = null;
    }
    if (this.alarmTimer) { clearTimeout(this.alarmTimer); this.alarmTimer = null; }
  }
}

window.c2Audio = new C2AudioEngine();
