package com.starbore.game;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;

/** Hosts the game loop on its own thread. Touches are queued and replayed on that thread. */
final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    final Game game;
    final Ui ui;
    private final Render render = new Render();
    private final ArrayList<float[]> touches = new ArrayList<>();
    private Thread thread;
    private volatile boolean running;
    private float saveT, slowT, perfT;
    private boolean perfHinted;

    private final Music music;
    private int renderErrors;

    GameView(Context ctx, Game game, Music music) {
        super(ctx);
        this.game = game;
        this.music = music;
        ui = new Ui(game);
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        // single-pointer UI: only the first finger counts
        if (a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_POINTER_UP) return true;
        synchronized (touches) {
            touches.add(new float[]{a, e.getX(0), e.getY(0)});
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        running = true;
        thread = new Thread(this, "starbore-loop");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        if (thread != null) {
            try { thread.join(); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    @Override
    public void run() {
        SurfaceHolder holder = getHolder();
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            synchronized (game) {
                synchronized (touches) {
                    for (float[] t : touches) ui.touch((int) t[0], t[1], t[2]);
                    touches.clear();
                }
                game.update(dt);
                music.enabled = game.musicOn;
                music.intensity = game.planet.boss && !game.daily && game.clearT < 0 ? 1f
                        : game.daily && game.sideMode == Game.SIDE_WEEKLY ? 0.6f : 0f;
                saveT += dt;
                if (saveT > 15) {
                    saveT = 0;
                    Save.write(getContext(), game);
                }
                Canvas c = null;
                try {
                    c = Build.VERSION.SDK_INT >= 26 ? holder.lockHardwareCanvas() : holder.lockCanvas();
                    if (c != null) {
                        render.draw(c, game, c.getWidth(), c.getHeight(), dt);
                        ui.draw(c, c.getWidth(), c.getHeight(), dt);
                    }
                    renderErrors = 0;
                } catch (Exception e) {
                    // Never fail silently: log it, and if drawing keeps failing fall back to the simplest renderer.
                    android.util.Log.e("Starbore", "render failed", e);
                    if (++renderErrors == 3) {
                        game.lowFx = true;
                        game.theme = Themes.NEON;
                        game.showBanner("Graphics problem - switched to PERFORMANCE mode", 0xFFFFE066, 5f);
                    }
                } finally {
                    if (c != null) {
                        try { holder.unlockCanvasAndPost(c); } catch (Exception ignored) { }
                    }
                }
            }
            long spent = (System.nanoTime() - now) / 1000000;
            // Suggest performance mode once if the game keeps dropping below ~35 fps.
            if (!perfHinted && !game.lowFx) {
                perfT += dt;
                if (dt > 0.028f) slowT += dt;
                if (perfT > 10) {
                    if (slowT > 6) {
                        perfHinted = true;
                        synchronized (game) {
                            game.showBanner("Running slow? Turn on PERFORMANCE in MORE > SETTINGS", 0xFFFFE066, 5f);
                        }
                    }
                    perfT = slowT = 0;
                }
            }
            if (spent < 15) {
                try { Thread.sleep(15 - spent); } catch (InterruptedException ignored) { }
            }
        }
    }
}
