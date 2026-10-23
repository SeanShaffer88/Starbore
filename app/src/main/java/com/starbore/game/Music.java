package com.starbore.game;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/**
 * Procedural synthwave soundtrack streamed through an AudioTrack: pad chords, a bass line, an arpeggio and
 * hi-hats over an Am-F-C-G loop. A boss layer (kick + faster arp) fades in with {@link #intensity}.
 */
final class Music implements Runnable {
    private static final int RATE = 22050;
    private static final float BPM = 104;
    /** Chord roots (MIDI) and triads for Am, F, C, G. */
    private static final int[][] CHORDS = {{57, 60, 64}, {53, 57, 60}, {48, 52, 55}, {55, 59, 62}};
    private static final int[] ARP = {0, 1, 2, 1, 0, 2, 1, 2};

    volatile float intensity;  // 0 = calm, 1 = boss
    volatile boolean enabled = true;
    private volatile boolean running;
    private Thread thread;
    private AudioTrack track;
    private final Random rnd = new Random(3);

    // synth state
    private long sample;
    private double padPh0, padPh1, padPh2, bassPh, arpPh, kickPh;
    private float bassLp, hatEnv, kickEnv, arpEnv, bassEnv, vol, level;

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "starbore-music");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    private static double freq(int midi) { return 440 * Math.pow(2, (midi - 69) / 12.0); }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 4096) * 2, AudioTrack.MODE_STREAM);
            track.play();
        } catch (Exception e) {
            running = false;
            return;
        }
        short[] buf = new short[1024];
        double stepLen = RATE * 60.0 / BPM / 4; // samples per 16th note
        while (running) {
            float targetVol = enabled ? 0.55f : 0f;
            for (int i = 0; i < buf.length; i++) {
                vol += (targetVol - vol) * 0.0005f;
                level += (intensity - level) * 0.00005f;
                long step = (long) (sample / stepLen);
                boolean onStep = sample - (long) (step * stepLen) < 1;
                int bar = (int) (step / 16) % CHORDS.length;
                int s16 = (int) (step % 16);
                int[] ch = CHORDS[bar];
                if (onStep) {
                    if (s16 % 2 == 0) bassEnv = 1;
                    arpEnv = 1;
                    if (s16 % 4 == 2 || (level > 0.5f && s16 % 2 == 1)) hatEnv = 1;
                    if (level > 0.3f && s16 % 4 == 0) { kickEnv = 1; kickPh = 0; }
                }
                // pad
                double t = 1.0 / RATE;
                padPh0 += freq(ch[0]) * t * 1.002;
                padPh1 += freq(ch[1]) * t * 0.998;
                padPh2 += freq(ch[2]) * t;
                float pad = (float) (Math.sin(padPh0 * 6.2832) + Math.sin(padPh1 * 6.2832) + Math.sin(padPh2 * 6.2832)) * 0.06f;
                // bass (saw through a one-pole low-pass)
                bassPh += freq(ch[0] - 24) * t;
                float saw = (float) (2 * (bassPh - Math.floor(bassPh)) - 1);
                bassLp += (saw - bassLp) * 0.08f;
                bassEnv *= 0.99985f;
                float bass = bassLp * (0.3f + 0.7f * bassEnv) * 0.32f;
                // arp (soft square)
                int note = ch[ARP[(s16 * (level > 0.5f ? 2 : 1)) % ARP.length]] + 12;
                arpPh += freq(note) * t;
                float sq = (arpPh - Math.floor(arpPh)) < 0.5 ? 1 : -1;
                arpEnv *= 0.9996f;
                float arp = sq * arpEnv * 0.07f;
                // hats
                hatEnv *= 0.997f;
                float hat = (rnd.nextFloat() * 2 - 1) * hatEnv * 0.06f;
                // kick (boss layer)
                kickEnv *= 0.9993f;
                kickPh += (45 + 120 * kickEnv) * t;
                float kick = (float) Math.sin(kickPh * 6.2832) * kickEnv * 0.45f * level;
                float mix = (pad + bass + arp + hat + kick) * vol;
                buf[i] = (short) (Math.max(-1, Math.min(1, mix)) * 32767);
                sample++;
            }
            try {
                track.write(buf, 0, buf.length);
            } catch (Exception e) {
                break;
            }
        }
        try {
            track.stop();
            track.release();
        } catch (Exception ignored) {
        }
        track = null;
    }
}
