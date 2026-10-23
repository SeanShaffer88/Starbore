package com.starbore.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Procedurally synthesised sound effects (written once as WAVs to the cache dir) and haptics. */
final class Audio implements Game.Fx {
    private static final int RATE = 22050;
    private static final long[] GAP = {70, 60, 45, 90, 60, 300, 40, 500};

    private final SoundPool pool;
    private final int[] ids = new int[8];
    private final long[] last = new long[8];
    private final Vibrator vib;
    private final Random rnd = new Random();
    private long lastBuzz;

    Audio(Context ctx) {
        AudioAttributes aa = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(aa).build();
        for (int i = 0; i < ids.length; i++) {
            try {
                File f = new File(ctx.getCacheDir(), "sfx_v1_" + i + ".wav");
                if (!f.exists()) writeWav(f, synth(i));
                ids[i] = pool.load(f.getPath(), 1);
            } catch (Exception ignored) {
            }
        }
        vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public void play(int id, float vol, float pitch) {
        long now = SystemClock.uptimeMillis();
        if (ids[id] == 0 || now - last[id] < GAP[id]) return;
        last[id] = now;
        float rate = pitch > 0 ? Math.max(0.5f, Math.min(2f, pitch))
                : id == Game.S_SHOOT || id == Game.S_BREAK || id == Game.S_ZAP ? 0.9f + rnd.nextFloat() * 0.2f : 1f;
        pool.play(ids[id], vol, vol, 1, 0, rate);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void buzz(int ms) {
        long now = SystemClock.uptimeMillis();
        if (vib == null || !vib.hasVibrator() || now - lastBuzz < 80) return;
        lastBuzz = now;
        try {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else vib.vibrate(ms);
        } catch (Exception ignored) {
        }
    }

    void release() { pool.release(); }

    // ---------------------------------------------------------------------
    private static float[] synth(int id) {
        Random r = new Random(id);
        switch (id) {
            case Game.S_SHOOT: return sweep(0.06f, 900, 420, 0.35f, true, r, 0);
            case Game.S_ZAP: return sweep(0.09f, 1500, 700, 0.25f, true, r, 0.3f);
            case Game.S_BREAK: {
                float[] a = sweep(0.1f, 220, 90, 0.35f, false, r, 0.8f);
                return a;
            }
            case Game.S_BOOM: return sweep(0.55f, 110, 35, 0.9f, false, r, 0.9f);
            case Game.S_COIN: return concat(tone(0.05f, 1320, 0.3f), tone(0.12f, 1760, 0.3f));
            case Game.S_CLEAR: return concat(concat(tone(0.09f, 523, 0.3f), tone(0.09f, 659, 0.3f)),
                    concat(tone(0.09f, 784, 0.3f), tone(0.25f, 1047, 0.3f)));
            case Game.S_CLICK: return tone(0.025f, 700, 0.25f);
            case Game.S_BOSS: return sweep(0.9f, 140, 70, 0.5f, true, r, 0.15f);
        }
        return new float[1];
    }

    /** Frequency sweep with exponential decay; square or sine, optionally mixed with noise. */
    private static float[] sweep(float dur, float f0, float f1, float amp, boolean square, Random r, float noise) {
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        double ph = 0;
        float lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 + (f1 - f0) * t;
            ph += 2 * Math.PI * f / RATE;
            float s = (float) Math.sin(ph);
            if (square) s = s > 0 ? 0.6f : -0.6f;
            lp += (r.nextFloat() * 2 - 1 - lp) * 0.25f;
            float env = (float) Math.exp(-t * 5) * Math.min(1, i / 40f);
            o[i] = amp * env * (s * (1 - noise) + lp * noise * 1.6f);
        }
        return o;
    }

    private static float[] tone(float dur, float f, float amp) {
        int n = (int) (dur * RATE);
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            o[i] = amp * (float) Math.sin(2 * Math.PI * f * i / RATE) * (1 - t) * Math.min(1, i / 30f);
        }
        return o;
    }

    private static float[] concat(float[] a, float[] b) {
        float[] o = new float[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }

    private static void writeWav(File f, float[] pcm) throws Exception {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        put(b, 0, "RIFF");
        le32(b, 4, 36 + dataLen);
        put(b, 8, "WAVEfmt ");
        le32(b, 16, 16);
        le16(b, 20, 1);
        le16(b, 22, 1);
        le32(b, 24, RATE);
        le32(b, 28, RATE * 2);
        le16(b, 32, 2);
        le16(b, 34, 16);
        put(b, 36, "data");
        le32(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) {
            int v = (int) (Math.max(-1, Math.min(1, pcm[i])) * 32767);
            le16(b, 44 + i * 2, v);
        }
        FileOutputStream out = new FileOutputStream(f);
        try { out.write(b); } finally { out.close(); }
    }

    private static void put(byte[] b, int o, String s) {
        for (int i = 0; i < s.length(); i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] b, int o, int v) {
        le16(b, o, v);
        le16(b, o + 2, v >> 16);
    }
}
