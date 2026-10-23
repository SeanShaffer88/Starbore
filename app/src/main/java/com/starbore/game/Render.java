package com.starbore.game;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.ArrayList;
import java.util.Random;

/** Draws the world in one of four themes: Neon, Blueprint, Pixel and Paper-cut. */
final class Render {
    /** crust, mantle, core colours per sector palette. */
    static final int[][] PALETTES = {
            {0xFF19E3C9, 0xFF8A5CFF, 0xFFFF3EA5},
            {0xFFFFB547, 0xFFFF5E3A, 0xFFD3134F},
            {0xFFA6FF4D, 0xFF2EC7FF, 0xFF4A5BFF},
            {0xFFFF7AD9, 0xFFB04DFF, 0xFF5B2DFF},
            {0xFF7DF9FF, 0xFF3D8BFF, 0xFFFFE066},
    };
    static final int GOLD = 0xFFFFD84A, ENERGY = 0xFF9BF6FF, EXPLOSIVE = 0xFFFF2D55, ARMOR = 0xFF8A93A6,
            HEART = 0xFFFF3EA5, NODE = 0xFF00E0A0, TURRET = 0xFFF0E442;
    /** Okabe-Ito colours for the colour-blind palette. */
    static final int CB_GOLD = 0xFFE69F00, CB_ENERGY = 0xFF56B4E9, CB_EXPLOSIVE = 0xFFD55E00, CB_VEIN = 0xFFCC79A7,
            CB_NODE = 0xFF009E73, CB_TURRET = 0xFFF0E442;
    static volatile boolean colorBlind;

    static int blockColor(int pal, Block b) {
        boolean cb = colorBlind;
        switch (b.type) {
            case Block.GOLD: return cb ? CB_GOLD : GOLD;
            case Block.ENERGY: return cb ? CB_ENERGY : ENERGY;
            case Block.EXPLOSIVE: return cb ? CB_EXPLOSIVE : EXPLOSIVE;
            case Block.ARMOR: return ARMOR;
            case Block.VEIN: return cb ? CB_VEIN : 0xFFFFFFFF;
            case Block.HEART: return HEART;
            case Block.NODE: return cb ? CB_NODE : NODE;
            case Block.TURRET: return cb ? CB_TURRET : TURRET;
        }
        return PALETTES[pal][b.layer];
    }

    static int alpha(int c, int a) { return (c & 0xFFFFFF) | (Math.max(0, Math.min(255, a)) << 24); }

    static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint add = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint addStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hex = new Path(), sq = new Path(), bolt = new Path(), flame = new Path(), shard = new Path(),
            diamond = new Path(), tri = new Path(), crack1 = new Path(), crack2 = new Path();
    private final Path[] hullPaths = new Path[Hulls.COUNT];
    private final float[] starX = new float[160], starY = new float[160], starZ = new float[160];
    private final float[] hsv = new float[3];
    private final RadialGradient nebula1, nebula2;
    private RadialGradient planetGlow, vignette;
    private BitmapShader scanlines;
    private float vigW, vigH;
    private int vigTheme = -1;
    private Planet glowFor, lastPlanet;
    float camX, camY, zoom = 1;
    // engine ribbon
    private final float[] trailX = new float[24], trailY = new float[24];
    private int trailN;
    private float lastAng, bank;
    // coin flyers: sx, sy, vx, vy, age, type
    private final ArrayList<float[]> flyers = new ArrayList<>();

    Render() {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        addStroke.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        addStroke.setStyle(Paint.Style.STROKE);
        addStroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Ui.FONT);
        text.setTextAlign(Paint.Align.CENTER);
        textEdge.setTypeface(Ui.FONT);
        textEdge.setTextAlign(Paint.Align.CENTER);
        textEdge.setStyle(Paint.Style.STROKE);
        textEdge.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            float x = (float) Math.cos(a), y = (float) Math.sin(a);
            if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
        }
        hex.close();
        sq.addRect(-0.8f, -0.8f, 0.8f, 0.8f, Path.Direction.CW);
        bolt.moveTo(0.15f, -0.6f); bolt.lineTo(-0.3f, 0.08f); bolt.lineTo(0.02f, 0.08f);
        bolt.lineTo(-0.15f, 0.6f); bolt.lineTo(0.3f, -0.08f); bolt.lineTo(-0.02f, -0.08f); bolt.close();
        flame.moveTo(-6, -4); flame.lineTo(-18, 0); flame.lineTo(-6, 4); flame.close();
        shard.moveTo(1, 0); shard.lineTo(-0.6f, 0.7f); shard.lineTo(-0.4f, -0.8f); shard.close();
        diamond.moveTo(7, 0); diamond.lineTo(0, -5); diamond.lineTo(-5, 0); diamond.lineTo(0, 5); diamond.close();
        tri.moveTo(0, -0.5f); tri.lineTo(0.45f, 0.32f); tri.lineTo(-0.45f, 0.32f); tri.close();
        crack1.moveTo(-0.1f, -0.75f); crack1.lineTo(0.06f, -0.25f); crack1.lineTo(-0.16f, 0.1f); crack1.lineTo(0.12f, 0.6f);
        crack1.moveTo(0.06f, -0.25f); crack1.lineTo(0.5f, -0.05f);
        crack2.moveTo(-0.7f, 0.2f); crack2.lineTo(-0.25f, 0.05f); crack2.lineTo(0.05f, 0.35f); crack2.lineTo(0.55f, 0.3f);
        crack2.moveTo(-0.25f, 0.05f); crack2.lineTo(-0.35f, -0.45f);
        for (int h = 0; h < Hulls.COUNT; h++) {
            float[] s = Hulls.SHAPE[h];
            Path p = new Path();
            p.moveTo(s[0], s[1]);
            for (int i = 2; i < s.length; i += 2) p.lineTo(s[i], s[i + 1]);
            p.close();
            hullPaths[h] = p;
        }
        Random r = new Random(7);
        for (int i = 0; i < starX.length; i++) {
            starX[i] = r.nextFloat();
            starY[i] = r.nextFloat();
            starZ[i] = 0.2f + r.nextFloat() * 0.8f;
        }
        nebula1 = new RadialGradient(0, 0, 1, 0x553D1A8C, 0x003D1A8C, Shader.TileMode.CLAMP);
        nebula2 = new RadialGradient(0, 0, 1, 0x40FF3EA5, 0x00FF3EA5, Shader.TileMode.CLAMP);
        Bitmap sl = Bitmap.createBitmap(1, 4, Bitmap.Config.ARGB_8888);
        sl.setPixel(0, 0, 0x00000000);
        sl.setPixel(0, 1, 0x00000000);
        sl.setPixel(0, 2, 0x00000000);
        sl.setPixel(0, 3, 0x50000000);
        scanlines = new BitmapShader(sl, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }

    void draw(Canvas c, Game g, float W, float H, float dt) {
        Planet p = g.planet;
        int th = g.theme;
        boolean lo = g.lowFx;
        colorBlind = g.colorBlind;
        boolean aa = th != Themes.PIXEL;
        fill.setAntiAlias(aa);
        stroke.setAntiAlias(aa);
        boolean additive = !lo && (th == Themes.NEON || th == Themes.BLUEPRINT);

        // ---- camera ----
        if (p != lastPlanet) {
            if (lastPlanet != null) zoom *= 0.25f; // warp in from far away
            lastPlanet = p;
            trailN = 0;
        }
        float baseZoom = Math.min(W, H * 0.75f) / 470f; // same framing in portrait and landscape
        float tz = g.mapMode ? Math.min(W, H * 0.8f) / (p.worldR * 2.3f) : baseZoom;
        zoom += (tz - zoom) * Math.min(1, dt * 7);
        float tx = g.mapMode ? 0 : g.shipX, ty = g.mapMode ? 0 : g.shipY;
        camX += (tx - camX) * Math.min(1, dt * 9);
        camY += (ty - camY) * Math.min(1, dt * 9);
        float shx = 0, shy = 0;
        if (g.shake > 0) {
            // smooth multi-frequency shake instead of white noise
            float s = g.shake;
            shx = (float) (Math.sin(g.time * 71) + Math.sin(g.time * 113) * 0.6) * s;
            shy = (float) (Math.cos(g.time * 83) + Math.sin(g.time * 127) * 0.6) * s;
        }
        float ox = W / 2 - camX * zoom + shx, oy = H * 0.47f - camY * zoom + shy;
        g.viewOx = ox;
        g.viewOy = oy;
        g.viewZoom = zoom;

        background(c, g, W, H, ox, oy, th, lo);

        // warp streaks
        if (g.warpT > 0 && !lo) {
            float f = g.warpT / 1.2f;
            for (int i = 0; i < 70; i++) {
                double a = starX[i] * Math.PI * 2;
                float r0 = (0.1f + starY[i]) * W * 0.7f * (1.6f - f);
                float len = W * 0.35f * f * starZ[i];
                float cx = W / 2, cy = H * 0.47f;
                float x0 = cx + (float) Math.cos(a) * r0, y0 = cy + (float) Math.sin(a) * r0;
                float x1 = cx + (float) Math.cos(a) * (r0 + len), y1 = cy + (float) Math.sin(a) * (r0 + len);
                Paint sp = additive ? addStroke : stroke;
                sp.setStrokeWidth(1 + 2 * starZ[i]);
                sp.setColor(alpha(th == Themes.PAPER ? 0xFF3A2E2A : 0xFFCFE8FF, (int) (200 * f)));
                c.drawLine(x0, y0, x1, y1, sp);
            }
        }

        if (th == Themes.NEON && !lo) {
            if (glowFor != p) {
                int[] pc = PALETTES[p.pal];
                planetGlow = new RadialGradient(0, 0, 1, new int[]{alpha(pc[2], 0x50), alpha(pc[1], 0x28), 0},
                        new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
                glowFor = p;
            }
            drawGradient(c, planetGlow, ox, oy, p.worldR * 1.45f * zoom);
        }

        // ---- burning ground ----
        for (int i = 0; i < g.zones.size(); i++) {
            Game.Zone z = g.zones.get(i);
            float a = Math.min(1, z.life / 0.5f);
            Paint fp = additive ? add : fill;
            fp.setColor(alpha(0xFFFF7A2E, (int) ((50 + 30 * Math.sin(g.time * 20 + i)) * a)));
            c.drawCircle(ox + z.x * zoom, oy + z.y * zoom, z.r * zoom, fp);
        }

        // ---- hexes ----
        float rad = Game.HS * 0.93f * zoom;
        float margin = rad * 2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(g.time * 8);
        for (int i = 0, n = p.all.size(); i < n; i++) {
            Block b = p.all.get(i);
            if (!b.alive && lo && (b.deadT < 0 || g.time - b.deadT > 1f)) continue;
            float sx = ox + b.x * zoom, sy = oy + b.y * zoom;
            if (sx < -margin || sy < -margin || sx > W + margin || sy > H + margin) continue;
            c.save();
            c.translate(sx, sy);
            c.scale(rad, rad);
            drawBlock(c, g, p, b, rad, th, pulse, lo, additive);
            c.restore();
        }

        // ---- shield dome & node tethers ----
        if (p.nodesLeft > 0) {
            float a = 0.5f + 0.5f * (float) Math.sin(g.time * 3);
            int nc = colorBlind ? CB_NODE : NODE;
            for (Block b : p.alive) {
                if (b.type != Block.NODE) continue;
                stroke.setStrokeWidth(2 * zoom);
                stroke.setColor(alpha(nc, (int) (90 + 80 * a)));
                c.drawLine(ox + b.x * zoom, oy + b.y * zoom, ox, oy, stroke);
            }
            fill.setColor(alpha(nc, (int) (18 + 14 * a)));
            c.drawCircle(ox, oy, p.worldR * zoom * 1.02f, fill);
            stroke.setStrokeWidth(3 * zoom);
            stroke.setColor(alpha(nc, (int) (110 + 90 * a)));
            c.drawCircle(ox, oy, p.worldR * zoom * 1.02f, stroke);
        }

        // ---- singularities ----
        for (int i = 0; i < g.sings.size(); i++) {
            Game.Sing s = g.sings.get(i);
            float sx = ox + s.x * zoom, sy = oy + s.y * zoom;
            float a = Math.min(1, s.life);
            fill.setColor(alpha(0xFF000000, (int) (230 * a)));
            c.drawCircle(sx, sy, 16 * zoom, fill);
            Paint sp = additive ? addStroke : stroke;
            for (int k = 0; k < 3; k++) {
                sp.setStrokeWidth(2.5f * zoom);
                sp.setColor(alpha(0xFFB04DFF, (int) (200 * a)));
                float rr = (22 + k * 14 + (g.time * 40) % 14) * zoom;
                c.drawArc(sx - rr, sy - rr, sx + rr, sy + rr, (g.time * 300 + k * 120) % 360, 200, false, sp);
            }
        }

        // ---- rings / explosion cores ----
        for (int i = 0; i < g.rings.size(); i++) {
            Game.Ring r = g.rings.get(i);
            float f = 1 - r.life / r.max;
            float rr = (r.r0 + (r.r1 - r.r0) * (1 - (1 - f) * (1 - f))) * zoom;
            if (r.filled) {
                Paint fp = additive ? add : fill;
                fp.setColor(alpha(fxColor(r.color, th), (int) (230 * (1 - f) * (1 - f))));
                c.drawCircle(ox + r.x * zoom, oy + r.y * zoom, rr, fp);
                continue;
            }
            Paint sp = additive ? addStroke : stroke;
            sp.setStrokeWidth(5 * zoom * (1 - f) + 1);
            sp.setColor(alpha(fxColor(r.color, th), (int) (255 * (1 - f))));
            c.drawCircle(ox + r.x * zoom, oy + r.y * zoom, rr, sp);
        }

        // ---- particles ----
        int smokeCol = th == Themes.PAPER ? 0xFF8A7A6A : th == Themes.BLUEPRINT ? 0xFF6A9AC8 : 0xFF3A2A5A;
        for (int i = 0; i < g.parts.size(); i++) {
            Game.Particle q = g.parts.get(i);
            float f = q.life / q.max;
            float px = ox + q.x * zoom, py = oy + q.y * zoom;
            if (px < -40 || py < -40 || px > W + 40 || py > H + 40) continue;
            if (q.kind == Game.P_SMOKE) {
                boolean tint = q.color != 0xFF2A1F45;
                fill.setColor(alpha(tint ? fxColor(q.color, th) : smokeCol, (int) ((tint ? 140 : 110) * f)));
                float s = q.size * zoom * (tint ? (0.4f + 0.6f * f) : (1.6f - 0.8f * f));
                if (th == Themes.PIXEL) c.drawRect(px - s, py - s, px + s, py + s, fill);
                else c.drawCircle(px, py, s, fill);
                continue;
            }
            if (q.kind == Game.P_SPARK) {
                Paint sp = additive ? addStroke : stroke;
                sp.setStrokeWidth(q.size * zoom);
                sp.setColor(alpha(fxColor(q.color, th), (int) (255 * f)));
                c.drawLine(px, py, px - q.vx * 0.035f * zoom, py - q.vy * 0.035f * zoom, sp);
                continue;
            }
            fill.setColor(alpha(fxColor(q.color, th), (int) (255 * Math.min(1, f * 1.5f))));
            float s = q.size * zoom * (0.5f + 0.5f * f);
            if (th == Themes.PIXEL) {
                c.drawRect(px - s, py - s, px + s, py + s, fill);
                continue;
            }
            c.save();
            c.translate(px, py);
            c.rotate(q.rot * 57.3f);
            c.scale(s, s);
            c.drawPath(shard, fill);
            c.restore();
        }

        // ---- comets ----
        for (int i = 0; i < g.comets.size(); i++) {
            Game.Comet cm = g.comets.get(i);
            float sx = ox + cm.x * zoom, sy = oy + cm.y * zoom;
            int col = cm.meteor ? 0xFFFFB547 : Boosts.COLOR[cm.kind];
            float r = (cm.meteor ? 7 : 11) * zoom;
            float sp = (float) Math.hypot(cm.vx, cm.vy) + 0.01f;
            Paint gp = additive ? addStroke : glow;
            gp.setStrokeWidth(r * 1.6f);
            gp.setColor(alpha(col, 70));
            c.drawLine(sx, sy, sx - cm.vx / sp * r * 5, sy - cm.vy / sp * r * 5, gp);
            fill.setColor(fxColor(col, th));
            c.drawCircle(sx, sy, r, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(sx, sy, r * 0.5f, fill);
            if (!cm.meteor) {
                stroke.setStrokeWidth(2 * zoom);
                stroke.setColor(alpha(col, (int) (120 + 120 * pulse)));
                c.drawCircle(sx, sy, r * (1.8f + 0.3f * pulse), stroke);
            }
        }

        // ---- projectiles ----
        for (int i = 0; i < g.projs.size(); i++) {
            Game.Proj q = g.projs.get(i);
            float sx = ox + q.x * zoom, sy = oy + q.y * zoom;
            int qc = fxColor(q.color, th);
            if (q.kind == Game.Proj.LOB) {
                float f = 1 - q.life / q.total;
                float h = (float) Math.sin(f * Math.PI) * 40 * zoom;
                fill.setColor(0x55000000);
                c.drawCircle(sx, sy, 4 * zoom, fill);
                fill.setColor(qc);
                c.drawCircle(sx, sy - h, (4 + 2 * (float) Math.sin(f * Math.PI)) * zoom, fill);
            } else {
                float sp = (float) Math.hypot(q.vx, q.vy) + 0.01f;
                float len = (q.kind == Game.Proj.MISSILE ? 7 : 14) * zoom;
                float ex = sx - q.vx / sp * len, ey = sy - q.vy / sp * len;
                if (additive) {
                    addStroke.setStrokeWidth(7 * zoom);
                    addStroke.setColor(alpha(qc, 70));
                    c.drawLine(sx, sy, ex, ey, addStroke);
                }
                stroke.setStrokeWidth((th == Themes.PIXEL ? 3.5f : 2.4f) * zoom);
                stroke.setColor(qc);
                c.drawLine(sx, sy, ex, ey, stroke);
                fill.setColor(th == Themes.PAPER ? qc : 0xFFFFFFFF);
                c.drawCircle(sx, sy, 1.6f * zoom, fill);
            }
        }
        // enemy stun shots
        for (int i = 0; i < g.eshots.size(); i++) {
            Game.Proj q = g.eshots.get(i);
            float sx = ox + q.x * zoom, sy = oy + q.y * zoom;
            float r = (6 + 1.5f * pulse) * zoom;
            Paint fp = additive ? add : fill;
            fp.setColor(alpha(EXPLOSIVE, 80));
            c.drawCircle(sx, sy, r * 2, fp);
            fill.setColor(EXPLOSIVE);
            c.drawCircle(sx, sy, r, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(sx, sy, r * 0.45f, fill);
        }

        // ---- beams ----
        float shipSX = ox + g.shipX * zoom, shipSY = oy + g.shipY * zoom;
        if (g.lanceOn) {
            float ex = ox + g.lanceX * zoom, ey = oy + g.lanceY * zoom;
            int lc = fxColor(Weapons.COLOR[Weapons.LANCE], th);
            Paint gp = additive ? addStroke : glow;
            gp.setStrokeWidth((11 + 4 * (float) Math.sin(g.time * 47)) * zoom);
            gp.setColor(alpha(lc, 80));
            c.drawLine(shipSX, shipSY, ex, ey, gp);
            stroke.setStrokeWidth((2.5f + (float) Math.sin(g.time * 90)) * zoom);
            stroke.setColor(th == Themes.PAPER ? lc : 0xFFFFFFE0);
            c.drawLine(shipSX, shipSY, ex, ey, stroke);
            Paint fp = additive ? add : fill;
            fp.setColor(alpha(lc, 150));
            c.drawCircle(ex, ey, (7 + 3 * pulse) * zoom, fp);
        }
        for (int i = 0; i < g.beams.size(); i++) {
            Game.Beam bm = g.beams.get(i);
            float f = bm.life / bm.max;
            int bc = fxColor(bm.color, th);
            for (int k = 0; k + 3 < bm.pts.length; k += 2) {
                float x0 = ox + bm.pts[k] * zoom, y0 = oy + bm.pts[k + 1] * zoom;
                float x1 = ox + bm.pts[k + 2] * zoom, y1 = oy + bm.pts[k + 3] * zoom;
                Paint gp = additive ? addStroke : glow;
                gp.setStrokeWidth(bm.width * 3 * zoom * f);
                gp.setColor(alpha(bc, (int) (90 * f)));
                c.drawLine(x0, y0, x1, y1, gp);
                stroke.setStrokeWidth(bm.width * 0.5f * zoom * f + 0.5f);
                stroke.setColor(alpha(th == Themes.PAPER ? bc : 0xFFFFFFFF, (int) (255 * f)));
                c.drawLine(x0, y0, x1, y1, stroke);
            }
        }

        // ---- drones ----
        for (int i = 0; i < g.drones.length; i++) {
            Game.Drone d = g.drones[i];
            if (d == null) continue;
            c.save();
            c.translate(ox + d.x * zoom, oy + d.y * zoom);
            c.rotate(g.time * 90 + i * 45);
            c.scale(zoom, zoom);
            fill.setColor(shipFill(th));
            c.drawPath(diamond, fill);
            stroke.setStrokeWidth(1.8f);
            stroke.setColor(fxColor(Drones.COLOR[i], th));
            c.drawPath(diamond, stroke);
            c.restore();
        }

        // ---- engine ribbon ----
        if (!lo) {
            if (trailN == 0 || Math.hypot(g.shipX - trailX[0], g.shipY - trailY[0]) > 4) {
                System.arraycopy(trailX, 0, trailX, 1, trailX.length - 1);
                System.arraycopy(trailY, 0, trailY, 1, trailY.length - 1);
                trailX[0] = g.shipX;
                trailY[0] = g.shipY;
                trailN = Math.min(trailN + 1, trailX.length);
            }
            Paint sp = additive ? addStroke : stroke;
            for (int i = 0; i + 1 < trailN; i++) {
                float f = 1 - i / (float) trailN;
                sp.setStrokeWidth(7 * zoom * f);
                int tc;
                if (g.cosTrail == 3) {
                    hsv[0] = (g.time * 200 + i * 18) % 360;
                    hsv[1] = 0.8f;
                    hsv[2] = 1f;
                    tc = Color.HSVToColor(hsv);
                } else tc = Cosmetics.COLOR[g.cosTrail];
                sp.setColor(alpha(th == Themes.PAPER ? mix(tc, 0xFF3A2E2A, 0.4f) : tc, (int) (120 * f)));
                c.drawLine(ox + trailX[i] * zoom, oy + trailY[i] * zoom, ox + trailX[i + 1] * zoom, oy + trailY[i + 1] * zoom, sp);
            }
        }

        // ---- saws ----
        if (g.wLvl[Weapons.SAWS] >= 0) {
            float sr = g.sawRadius();
            for (int s = 0; s < 2; s++) {
                float a = g.sawAng + s * 3.14159f;
                float bx = shipSX + (float) Math.cos(a) * sr * zoom, by = shipSY + (float) Math.sin(a) * sr * zoom;
                c.save();
                c.translate(bx, by);
                c.rotate(g.time * 900);
                float r = 9 * zoom;
                fill.setColor(shipFill(th));
                c.drawCircle(0, 0, r, fill);
                stroke.setStrokeWidth(2 * zoom);
                stroke.setColor(fxColor(Weapons.COLOR[Weapons.SAWS], th));
                for (int t = 0; t < 6; t++) {
                    c.drawLine(0, -r * 0.3f, r * 0.5f, -r * 1.25f, stroke);
                    c.rotate(60);
                }
                c.drawCircle(0, 0, r, stroke);
                c.restore();
            }
        }

        // ---- ship ----
        float turn = g.shipAng - lastAng;
        while (turn > Math.PI) turn -= 6.2831853f;
        while (turn < -Math.PI) turn += 6.2831853f;
        lastAng = g.shipAng;
        float targetBank = dt > 0 ? Math.max(-1, Math.min(1, turn / dt / 6f)) : 0;
        bank += (targetBank - bank) * Math.min(1, dt * 8);
        Path shipPath = hullPaths[g.hull];
        boolean stunned = g.stunT > 0;
        c.save();
        c.translate(shipSX, shipSY);
        c.rotate(g.shipAng * 57.29578f);
        c.scale(zoom, zoom * (1 - 0.3f * Math.abs(bank)));
        float fl = 0.7f + 0.3f * (float) Math.sin(g.time * 50);
        c.save();
        c.scale(fl * (1 + Math.min(1, (float) Math.hypot(g.shipVX, g.shipVY) / 200f) * 0.6f), 1);
        fill.setColor(0xFFFF9F43);
        c.drawPath(flame, fill);
        c.restore();
        int paint = g.cosPaint == 6 ? Hulls.COLOR[g.hull] : Cosmetics.COLOR[g.cosPaint];
        if (th == Themes.NEON && !lo) {
            glow.setStrokeWidth(8);
            glow.setColor(alpha(paint, 0x44));
            c.drawPath(shipPath, glow);
        } else if (th == Themes.PAPER) {
            c.save();
            c.translate(2.5f, 3.5f);
            fill.setColor(0x33000000);
            c.drawPath(shipPath, fill);
            c.restore();
        }
        fill.setColor(stunned && ((int) (g.time * 20) & 1) == 0 ? 0xFFFF2D55 : th == Themes.PIXEL ? paint : shipFill(th));
        c.drawPath(shipPath, fill);
        if (th != Themes.PIXEL) {
            stroke.setStrokeWidth(2.2f);
            stroke.setColor(th == Themes.NEON ? paint : g.cosPaint != 6 ? mix(paint, shipEdge(th), 0.4f) : shipEdge(th));
            c.drawPath(shipPath, stroke);
        }
        fill.setColor(0xFFFF3EA5);
        c.drawCircle(4, 0, 2.6f, fill);
        if (g.muzzle > 0) {
            float m = g.muzzle / 0.07f;
            Paint fp = additive ? add : fill;
            fp.setColor(alpha(fxColor(g.muzzleColor, th), (int) (230 * m)));
            c.drawCircle(19, 0, 7 * m + 2, fp);
            fill.setColor(alpha(0xFFFFFFFF, (int) (255 * m)));
            c.drawCircle(19, 0, 3 * m, fill);
        }
        c.restore();
        if (stunned) {
            stroke.setStrokeWidth(1.5f * zoom);
            stroke.setColor(alpha(0xFFFFE066, 200));
            for (int k = 0; k < 3; k++) {
                float a = g.time * 12 + k * 2.1f;
                float r = 18 * zoom;
                c.drawLine(shipSX + (float) Math.cos(a) * r, shipSY + (float) Math.sin(a) * r,
                        shipSX + (float) Math.cos(a + 0.6f) * r * 1.3f, shipSY + (float) Math.sin(a + 0.6f) * r * 1.3f, stroke);
            }
        }

        // ---- floating texts ----
        for (int i = 0; i < g.texts.size(); i++) {
            Game.FText t = g.texts.get(i);
            float max = t.big ? 1.2f : 1f, age = max - t.life;
            float s = t.big ? (age < 0.12f ? 0.4f + age / 0.12f * 1.1f : Math.max(1f, 1.5f - (age - 0.12f) * 3)) : 1f;
            float ts = (t.big ? 17 : 12) * zoom * s;
            int a = (int) (255 * Math.min(1, t.life * 2.5f));
            float x = ox + t.x * zoom, y = oy + t.y * zoom;
            textEdge.setTextSize(ts);
            textEdge.setStrokeWidth(ts * 0.18f);
            textEdge.setColor(alpha(th == Themes.PAPER ? 0xFFFFFFFF : 0xFF000000, (int) (a * 0.8f)));
            c.drawText(t.s, x, y, textEdge);
            text.setTextSize(ts);
            text.setColor(alpha(fxColor(t.color, th), a));
            c.drawText(t.s, x, y, text);
        }

        // ---- off-screen pointer to the nearest hex ----
        if (!g.mapMode && g.nearest != null) {
            float nx = ox + g.nearest.x * zoom, ny = oy + g.nearest.y * zoom;
            if (nx < 0 || ny < 0 || nx > W || ny > H) {
                float a = (float) Math.atan2(ny - shipSY, nx - shipSX);
                float px = W / 2 + (float) Math.cos(a) * W * 0.4f, py = H * 0.47f + (float) Math.sin(a) * W * 0.4f;
                c.save();
                c.translate(px, py);
                c.rotate(a * 57.3f);
                fill.setColor(alpha(fxColor(PALETTES[p.pal][0], th), (int) (150 + 100 * pulse)));
                c.scale(zoom, zoom);
                c.drawPath(hullPaths[0], fill);
                c.restore();
            }
        }

        // ---- screen-space post: vignette, scanlines, flash ----
        if (!lo) {
            if (vigW != W || vigH != H || vigTheme != th) {
                int edge = th == Themes.PAPER ? 0x70503A20 : th == Themes.BLUEPRINT ? 0x90041830 : 0xB0000000;
                vignette = new RadialGradient(W / 2, H / 2, (float) Math.hypot(W, H) * 0.55f,
                        new int[]{0, 0, edge}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
                vigW = W;
                vigH = H;
                vigTheme = th;
            }
            fill.setShader(vignette);
            fill.setColor(Color.WHITE);
            c.drawRect(0, 0, W, H, fill);
            fill.setShader(null);
            if (th == Themes.PIXEL) {
                fill.setShader(scanlines);
                c.drawRect(0, 0, W, H, fill);
                fill.setShader(null);
            }
        }
        if (g.flash > 0) {
            fill.setColor(alpha(g.flashColor, (int) (Math.min(1, g.flash) * 110)));
            c.drawRect(0, 0, W, H, fill);
        }
        if (g.stunT > 0) {
            stroke.setStrokeWidth(W * 0.04f);
            stroke.setColor(alpha(0xFFFF2D55, (int) (70 + 50 * pulse)));
            c.drawRect(0, 0, W, H, stroke);
        }

        lastH = H;
        drawFlyers(c, g, W, dt, ox, oy);
    }

    /** Coins/energy that burst out of broken hexes and zip into the HUD counters. */
    private float lastH = 1;

    private float H() { return lastH; }

    private void drawFlyers(Canvas c, Game g, float W, float dt, float ox, float oy) {
        for (float[] e : g.coinEvents) {
            if (flyers.size() > 60) break;
            float a = g.rnd.nextFloat() * 6.283f, s = 120 + g.rnd.nextFloat() * 260;
            flyers.add(new float[]{ox + e[0] * zoom, oy + e[1] * zoom, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0, e[2]});
        }
        g.coinEvents.clear();
        float u = Math.min(W, H() * 0.62f) / 100f;
        for (int i = flyers.size() - 1; i >= 0; i--) {
            float[] f = flyers.get(i);
            f[4] += dt;
            float txp = f[5] == 0 ? 5 * u : 46 * u, typ = 5.5f * u;
            if (f[4] > 0.28f) {
                float dx = txp - f[0], dy = typ - f[1], d = (float) Math.sqrt(dx * dx + dy * dy) + 0.01f;
                float acc = 4200 * Math.min(1, (f[4] - 0.28f) * 3);
                f[2] += dx / d * acc * dt;
                f[3] += dy / d * acc * dt;
                float sp = (float) Math.hypot(f[2], f[3]);
                float max = 2600;
                if (sp > max) { f[2] *= max / sp; f[3] *= max / sp; }
                if (d < 3 * u) {
                    flyers.remove(i);
                    if (f[5] == 0) g.coinPunch = 1; else g.energyPunch = 1;
                    continue;
                }
            } else {
                f[2] *= 1 - dt * 4;
                f[3] *= 1 - dt * 4;
            }
            f[0] += f[2] * dt;
            f[1] += f[3] * dt;
            int col = f[5] == 0 ? (colorBlind ? CB_GOLD : GOLD) : (colorBlind ? CB_ENERGY : ENERGY);
            float r = 1.3f * u;
            c.save();
            c.translate(f[0], f[1]);
            c.rotate(f[4] * 400);
            c.scale(r, r);
            fill.setColor(col);
            c.drawPath(hex, fill);
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(-0.25f, -0.25f, 0.25f, fill);
            c.restore();
        }
    }

    private void background(Canvas c, Game g, float W, float H, float ox, float oy, int th, boolean lo) {
        switch (th) {
            case Themes.BLUEPRINT: {
                c.drawColor(0xFF0E3A63);
                if (lo) return;
                float step = 48 * zoom;
                int k0x = (int) Math.floor(-ox / step), k0y = (int) Math.floor(-oy / step);
                for (int k = k0x; k * step + ox < W; k++) {
                    stroke.setStrokeWidth(k % 4 == 0 ? 1.4f : 0.8f);
                    stroke.setColor(k % 4 == 0 ? 0x40FFFFFF : 0x1CFFFFFF);
                    c.drawLine(k * step + ox, 0, k * step + ox, H, stroke);
                }
                for (int k = k0y; k * step + oy < H; k++) {
                    stroke.setStrokeWidth(k % 4 == 0 ? 1.4f : 0.8f);
                    stroke.setColor(k % 4 == 0 ? 0x40FFFFFF : 0x1CFFFFFF);
                    c.drawLine(0, k * step + oy, W, k * step + oy, stroke);
                }
                stroke.setStrokeWidth(1.2f);
                stroke.setColor(0x30FFFFFF);
                c.drawCircle(ox, oy, g.planet.worldR * zoom * 1.1f, stroke);
                return;
            }
            case Themes.PIXEL:
                c.drawColor(0xFF0D0D1A);
                for (int i = 0; i < starX.length; i += lo ? 4 : 2) {
                    float z = starZ[i];
                    float sx = mod(starX[i] * W - camX * z * 0.15f, W), sy = mod(starY[i] * H - camY * z * 0.15f, H);
                    float s = z > 0.7f ? 3 : 2;
                    fill.setColor(((int) (g.time * 2 + i) % 7 == 0) ? 0xFFFFE066 : 0xFFB8C0D8);
                    c.drawRect(sx, sy, sx + s, sy + s, fill);
                }
                return;
            case Themes.PAPER:
                c.drawColor(0xFFEDE3CC);
                if (lo) return;
                for (int i = 0; i < starX.length; i++) {
                    float z = starZ[i];
                    float sx = mod(starX[i] * W - camX * z * 0.08f, W), sy = mod(starY[i] * H - camY * z * 0.08f, H);
                    fill.setColor(0x14000000);
                    c.drawCircle(sx, sy, 1 + z * 2, fill);
                }
                return;
        }
        c.drawColor(0xFF07041A);
        if (!lo) {
            drawGradient(c, nebula1, W * 0.2f - camX * 0.02f, H * 0.3f - camY * 0.02f, W * 0.9f);
            drawGradient(c, nebula2, W * 0.85f - camX * 0.03f, H * 0.75f - camY * 0.03f, W * 0.7f);
        }
        for (int i = 0; i < starX.length; i += lo ? 3 : 1) {
            float z = starZ[i];
            float sx = mod(starX[i] * W - camX * z * 0.15f, W);
            float sy = mod(starY[i] * H - camY * z * 0.15f, H);
            fill.setColor(alpha(0xFFFFFFFF, (int) (60 + 150 * z * (0.7f + 0.3f * (float) Math.sin(g.time * 2 + i)))));
            c.drawCircle(sx, sy, 0.6f + z * 1.4f, fill);
        }
    }

    /** Canvas is already translated to the hex centre and scaled so the hex radius is 1. */
    private void drawBlock(Canvas c, Game g, Planet p, Block b, float rad, int th, float pulse, boolean lo, boolean additive) {
        Path shape = th == Themes.PIXEL ? sq : hex;
        int col = blockColor(p.pal, b);
        if (b.type == Block.VEIN && !colorBlind) {
            hsv[0] = (g.time * 120 + b.q * 25 + b.r * 15) % 360;
            hsv[1] = 0.55f;
            hsv[2] = 1f;
            col = Color.HSVToColor(hsv);
        }
        if (!b.alive) {
            float age = b.deadT < 0 ? 99 : g.time - b.deadT;
            if (age < 1.3f) {
                // freshly mined: white-hot ember cooling to a scorch mark
                float f = 1 - age / 1.3f;
                int ember = age < 0.15f ? 0xFFFFF2C0 : mix(0xFFFF3A1A, 0xFFFFB547, f);
                fill.setColor(alpha(th == Themes.PAPER ? mix(ember, 0xFF3A2E2A, 0.3f) : ember, (int) (210 * f * f)));
                c.save();
                float k = 0.6f + 0.4f * f;
                c.scale(k, k);
                c.drawPath(shape, fill);
                c.restore();
            }
            if (lo) return;
            switch (th) {
                case Themes.PAPER:
                    fill.setColor(0x16000000);
                    c.drawPath(shape, fill);
                    break;
                case Themes.NEON:
                    fill.setColor(alpha(PALETTES[p.pal][b.layer], 10));
                    c.drawPath(shape, fill);
                    break;
            }
            stroke.setStrokeWidth(1.2f / rad);
            stroke.setColor(th == Themes.BLUEPRINT ? 0x22FFFFFF : th == Themes.PAPER ? 0x1A000000
                    : alpha(PALETTES[p.pal][b.layer], 26));
            c.drawPath(shape, stroke);
            return;
        }
        float hr = (float) Math.max(0, b.hp / b.maxHp);
        float k = 0.28f + 0.6f * hr;
        float punch = 1 - 0.16f * b.punch;
        if (b.type == Block.HEART || b.type == Block.NODE) punch *= 1 + 0.08f * pulse;
        if (punch != 1) c.scale(punch, punch);
        if (lo) {
            fill.setColor(b.flash > 0 ? 0xFFFFFFFF : alpha(col, 0x90 + (int) (0x60 * hr)));
            c.drawPath(shape, fill);
        } else switch (th) {
            case Themes.BLUEPRINT:
                fill.setColor(alpha(col, 0x30));
                c.drawPath(shape, fill);
                stroke.setStrokeWidth(1.5f / rad);
                stroke.setColor(b.flash > 0 ? 0xFFFFFFFF : alpha(col, 0xE0));
                c.save();
                c.scale(k, k);
                c.drawPath(shape, stroke);
                c.restore();
                stroke.setColor(0xD0FFFFFF);
                stroke.setStrokeWidth(1.2f / rad);
                c.drawPath(shape, stroke);
                break;
            case Themes.PIXEL:
                fill.setColor(mix(col, 0xFF000000, 0.55f));
                c.drawPath(shape, fill);
                c.save();
                c.scale(k, k);
                fill.setColor(b.flash > 0 ? 0xFFFFFFFF : col);
                c.drawPath(shape, fill);
                c.restore();
                fill.setColor(alpha(0xFFFFFFFF, 0x50));
                c.drawRect(-0.8f, -0.8f, 0.8f, -0.62f, fill);
                break;
            case Themes.PAPER:
                c.save();
                c.translate(0.1f, 0.16f);
                fill.setColor(0x30000000);
                c.drawPath(shape, fill);
                c.restore();
                fill.setColor(mix(col, 0xFFFFFFFF, 0.55f));
                c.drawPath(shape, fill);
                c.save();
                c.scale(k, k);
                fill.setColor(b.flash > 0 ? 0xFFFFFFFF : mix(col, 0xFF000000, 0.1f));
                c.drawPath(shape, fill);
                c.restore();
                stroke.setStrokeWidth(1.4f / rad);
                stroke.setColor(0xFF3A2E2A);
                c.drawPath(shape, stroke);
                break;
            default:
                fill.setColor(alpha(col, 0x38));
                c.drawPath(shape, fill);
                c.save();
                c.scale(k, k);
                fill.setColor(b.flash > 0 ? 0xFFFFFFFF : alpha(col, 0xD8));
                c.drawPath(shape, fill);
                c.restore();
                stroke.setStrokeWidth(2f / rad);
                stroke.setColor(b.type == Block.EXPLOSIVE ? alpha(col, (int) (120 + 135 * pulse)) : col);
                c.drawPath(shape, stroke);
                if (additive && b.type != Block.NORMAL && b.type != Block.ARMOR) {
                    addStroke.setStrokeWidth(0.45f);
                    addStroke.setColor(alpha(col, (int) (40 + 30 * pulse)));
                    c.drawPath(shape, addStroke);
                }
        }
        // damage cracks
        if (!lo && hr < 0.7f && b.type != Block.HEART) {
            stroke.setStrokeWidth(1.6f / rad);
            stroke.setColor(th == Themes.BLUEPRINT ? 0xC0FFFFFF : 0xC0000000);
            boolean flip = ((b.q * 7 + b.r * 3) & 1) == 0;
            c.drawPath(flip ? crack1 : crack2, stroke);
            if (hr < 0.4f) c.drawPath(flip ? crack2 : crack1, stroke);
        }
        // type markers (shared by all themes; stronger shapes in colour-blind mode)
        boolean cb = colorBlind;
        switch (b.type) {
            case Block.ENERGY:
                fill.setColor(0xFF0B0620);
                if (cb) { c.save(); c.scale(1.35f, 1.35f); c.drawPath(bolt, fill); c.restore(); }
                else c.drawPath(bolt, fill);
                break;
            case Block.GOLD:
                if (cb) {
                    stroke.setStrokeWidth(2.2f / rad);
                    stroke.setColor(0xFF0B0620);
                    c.drawCircle(0, 0, 0.38f, stroke);
                }
                fill.setColor(cb ? 0xFF0B0620 : 0xFFFFF6C8);
                c.drawCircle(0, 0, 0.16f, fill);
                break;
            case Block.EXPLOSIVE:
                if (cb) {
                    fill.setColor(0xFF0B0620);
                    c.drawPath(tri, fill);
                    fill.setColor(alpha(0xFFFFFFFF, (int) (255 * pulse)));
                    c.drawRect(-0.04f, -0.25f, 0.04f, 0.08f, fill);
                    c.drawRect(-0.04f, 0.14f, 0.04f, 0.22f, fill);
                    break;
                }
                fill.setColor(0xFF1A0410);
                c.drawCircle(0, 0, 0.22f, fill);
                fill.setColor(alpha(b.overload ? 0xFFFFFFFF : 0xFFFFE066, (int) (255 * pulse)));
                c.drawCircle(0, 0, 0.11f, fill);
                break;
            case Block.ARMOR:
                stroke.setStrokeWidth(1.5f / rad);
                stroke.setColor(0xFF2B2F3A);
                c.drawLine(-0.45f, -0.25f, 0.45f, -0.25f, stroke);
                c.drawLine(-0.45f, 0.25f, 0.45f, 0.25f, stroke);
                break;
            case Block.HEART:
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(0, 0, 0.25f + 0.1f * pulse, fill);
                break;
            case Block.NODE:
                fill.setColor(0xFF04140E);
                c.drawCircle(0, 0, 0.34f, fill);
                fill.setColor(alpha(0xFFFFFFFF, (int) (150 + 100 * pulse)));
                c.drawCircle(0, 0, 0.18f, fill);
                break;
            case Block.TURRET: {
                fill.setColor(0xFF1A1A08);
                c.drawCircle(0, 0, 0.32f, fill);
                float a = (float) Math.atan2(g.shipY - b.y, g.shipX - b.x);
                stroke.setStrokeWidth(3.5f / rad);
                stroke.setColor(0xFF1A1A08);
                c.drawLine(0, 0, (float) Math.cos(a) * 0.85f, (float) Math.sin(a) * 0.85f, stroke);
                if (b.fireT < 0.5f) {
                    fill.setColor(alpha(EXPLOSIVE, (int) (255 * (1 - b.fireT / 0.5f))));
                    c.drawCircle(0, 0, 0.2f, fill);
                }
                break;
            }
            case Block.VEIN:
                if (g.mapMode) {
                    stroke.setStrokeWidth(3f / rad);
                    stroke.setColor(alpha(0xFFFFFFFF, (int) (120 + 135 * pulse)));
                    c.drawCircle(0, 0, 1.25f, stroke);
                }
                if (cb) {
                    stroke.setStrokeWidth(1.8f / rad);
                    stroke.setColor(0xFF0B0620);
                    c.drawLine(-0.4f, -0.2f, 0.4f, -0.2f, stroke);
                    c.drawLine(-0.4f, 0.2f, 0.4f, 0.2f, stroke);
                }
                fill.setColor(0xFFFFFFFF);
                c.drawCircle(0, 0, 0.12f, fill);
                break;
        }
        if (b.frost > 0) {
            float f = (float) (b.frost / Math.max(1e-9, b.maxFrost));
            fill.setColor(alpha(0xFFE0F7FF, (int) (40 + 110 * f)));
            c.drawPath(shape, fill);
            stroke.setStrokeWidth(1.2f / rad);
            stroke.setColor(alpha(0xFFFFFFFF, (int) (200 * f)));
            c.drawLine(-0.5f, -0.3f, 0.1f, 0.2f, stroke);
            c.drawLine(0.1f, 0.2f, 0.45f, -0.1f, stroke);
        }
    }

    private static int fxColor(int c, int th) {
        return th == Themes.PAPER ? mix(c, 0xFF000000, 0.35f) : c;
    }

    private static int shipFill(int th) {
        switch (th) {
            case Themes.BLUEPRINT: return 0xFF0E3A63;
            case Themes.PIXEL: return 0xFF9BF6FF;
            case Themes.PAPER: return 0xFFFFFFFF;
        }
        return 0xFF140B33;
    }

    private static int shipEdge(int th) {
        switch (th) {
            case Themes.BLUEPRINT: return 0xFFFFFFFF;
            case Themes.PAPER: return 0xFF3A2E2A;
        }
        return 0xFF9BF6FF;
    }

    private void drawGradient(Canvas c, Shader s, float x, float y, float r) {
        c.save();
        c.translate(x, y);
        c.scale(r, r);
        fill.setShader(s);
        fill.setColor(Color.WHITE);
        c.drawCircle(0, 0, 1, fill);
        fill.setShader(null);
        c.restore();
    }

    private static float mod(float a, float m) {
        a %= m;
        return a < 0 ? a + m : a;
    }
}
