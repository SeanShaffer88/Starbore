package com.starbore.game;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class MainActivity extends Activity implements Game.Platform {
    private static final int REQ_EXPORT = 41, REQ_IMPORT = 42;
    private Game game;
    private GameView view;
    private long pausedAt;
    private Audio audio;
    private final Music music = new Music();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Save.frozen = false;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        game = new Game();
        audio = new Audio(this);
        game.fx = audio;
        game.platform = this;
        double away = Save.read(this, game);
        game.recalc();
        if (away > 0) game.grantOffline(away);
        if (away < 0) {
            game.popTitle = "STARBORE";
            game.popLines = new String[]{"Your ship mines on its own.", "Drag anywhere to steer it yourself.",
                    "Gold hexes pay big, cyan ones give energy,", "red ones explode in chains.",
                    "Clear the planet to warp to the next sector."};
        }
        view = new GameView(this, game, music);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        music.start();
        if (pausedAt > 0) {
            double away = (System.currentTimeMillis() - pausedAt) / 1000.0;
            synchronized (game) { game.grantOffline(away); }
            pausedAt = 0;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        music.stop();
        pausedAt = System.currentTimeMillis();
        synchronized (game) { Save.write(this, game); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audio != null) audio.release();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        boolean handled;
        synchronized (game) { handled = view.ui.back(); }
        if (!handled) super.onBackPressed();
    }

    // ---------------- save export / import (Storage Access Framework, no permissions) ----------------
    @Override
    public void exportSave() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "starbore-save.json");
                try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { toast("No file picker available"); }
            }
        });
    }

    @Override
    public void importSave() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { toast("No file picker available"); }
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            try {
                String json;
                synchronized (game) { json = Save.toJson(game); }
                OutputStream out = getContentResolver().openOutputStream(uri, "wt");
                try { out.write(json.getBytes("UTF-8")); } finally { out.close(); }
                toast("Save exported");
            } catch (Exception e) {
                toast("Export failed");
            }
        } else if (req == REQ_IMPORT) {
            try {
                InputStream in = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0 && bo.size() < 4 << 20) bo.write(buf, 0, n);
                } finally { in.close(); }
                if (Save.importJson(this, bo.toString("UTF-8"))) {
                    toast("Save imported - restarting");
                    recreate();
                } else toast("That file is not a Starbore save");
            } catch (Exception e) {
                toast("Import failed");
            }
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 19) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }
}
