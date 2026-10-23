package com.starbore.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

import java.util.ArrayList;
import java.util.Locale;

/** Immediate-mode HUD and menus, drawn straight onto the game canvas. */
final class Ui {
    static final Typeface FONT = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD);

    private static final int A_AUTO = 1, A_MAP = 2, A_BUYMODE = 3, A_MORE = 4, A_FIGHTBOSS = 5, A_DAILY = 6,
            A_SOUND = 7, A_HAPTIC = 8, A_COLLAPSE = 9, A_ROLL1 = 10, A_ROLL10 = 11, A_FORGE = 12, A_SALVC = 13,
            A_POPOK = 14, A_BESTSK = 15, A_BESTGEAR = 16, A_AUTOBUY = 17, A_CB = 18, A_BIGTEXT = 19, A_LOWFX = 20,
            A_HINT = 21, A_TAB = 100, A_WEAP = 200, A_SHIP = 300, A_SKUNEQ = 400, A_SKEQ = 500, A_SKFUSE = 600,
            A_GEARUP = 700, A_GEQ = 800, A_GSALV = 900, A_THEME = 1000, A_ACH = 1100, A_PERK = 1200, A_DRONE = 1300,
            A_HULL = 1400, A_RES = 1500, A_CARD = 1600, A_COS = 1700,
            A_SFXVOL = 29, A_PICK = 22, A_NOVA = 23, A_MUSIC = 24, A_EXPORT = 25, A_IMPORT = 26, A_DEEP = 27, A_WEEKLY = 28;

    private static final String[] TABS = {"WEAPONS", "SKILLS", "GEAR", "SHIP", "LAB", "MORE"};
    private static final int TAB_COUNT = 5; // MORE (index 5) opens from the side button
    private static final int[] TAB_COL = {0xFF9BF6FF, 0xFFC165FF, 0xFFFFB547, 0xFF5CFFB0, 0xFFFF3EA5, 0xFF7DF9FF};
    private static final int BG = 0xE6100A26, CARD = 0xFF1A1236, DIM = 0xFF6E6890, TXT = 0xFFE8E6F5, GOOD = 0xFF5CFFB0;

    // ---- onboarding hints: text, tab to point at (-1 = none) ----
    private static final String[] HINT = {
            "Coins! Upgrade your blaster in WEAPONS",
            "SHIP upgrades boost fire rate and coins",
            "Energy! Roll a skill in SKILLS",
            "New gear dropped - equip it in GEAR",
            "The LAB can research upgrades over time",
            "Tap the comet for a boost!",
            "Core Collapse is ready in the LAB",
            "Boss planet! Watch the timer",
    };
    private static final int[] HINT_TAB = {0, 3, 1, 2, 4, -1, 4, -1};

    private final Game g;
    private float W, H, u, ts = 1;
    int tab = -1;
    private int shownTab = -1;
    private float panelAnim;
    private float scroll, contentH, panelTop, panelBot, contentTop;
    private float collapseArm;
    private double dispCoins, dispEnergy;
    private int hint = -1;
    private float hintT;
    /** Title screen shown at launch; any tap dismisses it. */
    private boolean title = true;
    private float titleT;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), stroke = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path coinIcon = new Path(), boltIcon = new Path(), arrow = new Path();
    private final Path[] hullIcons = new Path[Hulls.COUNT];

    private static final class Btn {
        final RectF r = new RectF();
        int act;
    }

    private final ArrayList<Btn> btns = new ArrayList<>();
    private int nb;

    // touch state
    private float downX, downY, lastY, joyBX, joyBY, joyKX, joyKY;
    private boolean moved, dragging, joyOn;
    private int downBtn = -1;
    private float holdT, repeatT;
    private boolean repeated;

    Ui(Game g) {
        this.g = g;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(FONT);
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3;
            float x = (float) Math.cos(a), y = (float) Math.sin(a);
            if (i == 0) coinIcon.moveTo(x, y); else coinIcon.lineTo(x, y);
        }
        coinIcon.close();
        boltIcon.moveTo(0.25f, -1f); boltIcon.lineTo(-0.5f, 0.12f); boltIcon.lineTo(0.03f, 0.12f);
        boltIcon.lineTo(-0.25f, 1f); boltIcon.lineTo(0.5f, -0.12f); boltIcon.lineTo(-0.03f, -0.12f); boltIcon.close();
        arrow.moveTo(0, 1); arrow.lineTo(-0.8f, -0.2f); arrow.lineTo(0.8f, -0.2f); arrow.close();
        for (int h = 0; h < Hulls.COUNT; h++) {
            float[] s = Hulls.SHAPE[h];
            Path p = new Path();
            p.moveTo(s[0], s[1]);
            for (int i = 2; i < s.length; i += 2) p.lineTo(s[i], s[i + 1]);
            p.close();
            hullIcons[h] = p;
        }
        dispCoins = g.coins;
        dispEnergy = g.energy;
    }

    private float sz(float s) { return s * ts; }

    // =====================================================================
    void draw(Canvas c, float W, float H, float dt) {
        this.W = W;
        this.H = H;
        u = Math.min(W, H * 0.62f) / 100f; // phone-like scale in portrait, sane sizes in landscape/tablets
        ts = g.bigText ? 1.15f : 1f;
        nb = 0;
        if (collapseArm > 0) collapseArm -= dt;
        updateHold(dt);
        float k = Math.min(1, dt * 9);
        dispCoins += (g.coins - dispCoins) * k;
        if (Math.abs(g.coins - dispCoins) < Math.max(1, g.coins * 0.0005)) dispCoins = g.coins;
        dispEnergy += (g.energy - dispEnergy) * k;
        if (Math.abs(g.energy - dispEnergy) < 0.5) dispEnergy = g.energy;

        float tabTop = H - 14 * u;
        float target = tab >= 0 ? 1 : 0;
        panelAnim += (target - panelAnim) * Math.min(1, dt * 14);
        if (Math.abs(target - panelAnim) < 0.01f) panelAnim = target;
        if (tab >= 0) shownTab = tab;
        float ph = Math.min(H * 0.32f, 62 * u);
        float ease = 1 - (1 - panelAnim) * (1 - panelAnim);
        panelBot = tabTop + (1 - ease) * ph;
        panelTop = panelBot - ph;
        contentTop = panelTop + 9 * u;

        drawTop(c);
        // side buttons
        float bx = W - 17 * u;
        button(c, bx, 18 * u, W - 3 * u, 25 * u, g.auto ? "AUTO" : "MANUAL", A_AUTO, true, g.auto ? GOOD : DIM, g.auto);
        button(c, bx, 27 * u, W - 3 * u, 34 * u, g.mapMode ? "SHIP" : "MAP", A_MAP, true, 0xFF9BF6FF, g.mapMode);
        button(c, bx, 36 * u, W - 3 * u, 43 * u, "MORE", A_MORE, true, TAB_COL[5], tab == 5);
        if (g.achClaimable() > 0 && tab != 5) {
            fill.setColor(0xFFFF3EA5);
            c.drawCircle(W - 4 * u, 36.5f * u, 1.3f * u, fill);
        }

        drawStatus(c);
        drawCombo(c);
        drawBoosts(c);

        if (joyOn) {
            stroke.setStrokeWidth(0.5f * u);
            stroke.setColor(0x669BF6FF);
            c.drawCircle(joyBX, joyBY, 12 * u, stroke);
            fill.setColor(0x889BF6FF);
            c.drawCircle(joyKX, joyKY, 5 * u, fill);
        }

        drawAbility(c, H - 14 * u);
        drawStamp(c);
        if (g.bannerT > 0 && g.banner != null) drawBanner(c);

        if (panelAnim > 0.01f && shownTab >= 0) drawPanel(c);

        // tab bar
        fill.setColor(0xF20B0620);
        c.drawRect(0, tabTop, W, H, fill);
        float tw = W / TAB_COUNT;
        updateHint(dt);
        for (int i = 0; i < TAB_COUNT; i++) {
            boolean sel = tab == i;
            boolean pointed = hint >= 0 && HINT_TAB[hint] == i;
            int col = TAB_COL[i];
            if (pointed) col = Render.mix(col, 0xFFFFFFFF, 0.5f + 0.5f * (float) Math.sin(g.time * 8));
            button(c, i * tw + 1.2f * u, tabTop + 2.5f * u, (i + 1) * tw - 1.2f * u, H - 2.5f * u, TABS[i], A_TAB + i,
                    true, col, sel || pointed);
            if (tabAlert(i) && !sel) {
                fill.setColor(0xFFFF3EA5);
                c.drawCircle((i + 1) * tw - 3 * u, tabTop + 4 * u, 1.3f * u, fill);
            }
        }
        drawHint(c, tabTop, tw);

        if (g.offer != null && g.popTitle == null) drawPicker(c);
        if (g.popTitle != null) drawPopup(c);
        if (title) drawTitle(c, dt);
    }

    // ---------------- title screen ----------------
    private void drawTitle(Canvas c, float dt) {
        titleT += dt;
        nb = 0;
        fill.setColor(Render.alpha(0xFF07041A, 0xC8));
        c.drawRect(0, 0, W, H, fill);
        float cy0 = H * 0.38f;
        float bob = (float) Math.sin(titleT * 2) * u;
        // hex emblem
        c.save();
        c.translate(W / 2, cy0 - 22 * u + bob);
        c.rotate(titleT * 20);
        c.scale(12 * u, 12 * u);
        stroke.setStrokeWidth(0.06f);
        for (int k = 0; k < 3; k++) {
            stroke.setColor(Render.alpha(new int[]{0xFF19E3C9, 0xFF8A5CFF, 0xFFFF3EA5}[k], 220));
            c.drawPath(coinIcon, stroke);
            c.scale(0.68f, 0.68f);
            c.rotate(-titleT * 40);
        }
        c.restore();
        text.setTextAlign(Paint.Align.CENTER);
        String name = "STARBORE";
        float ts0 = 13 * u;
        for (int i = 0; i < 3; i++) {
            text.setTextSize(ts0);
            int col = i == 0 ? 0x66FF3EA5 : i == 1 ? 0x6619E3C9 : 0xFFFFFFFF;
            float off = i == 2 ? 0 : (i == 0 ? -0.6f : 0.6f) * u * (1 + (float) Math.sin(titleT * 7));
            text.setColor(col);
            c.drawText(name, W / 2 + off, cy0 + bob, text);
        }
        text.setTextSize(sz(3.4f * u));
        text.setColor(0xFF9BF6FF);
        c.drawText("H E X   D R I L L", W / 2, cy0 + 7 * u + bob, text);
        float a = 0.5f + 0.5f * (float) Math.sin(titleT * 4);
        text.setTextSize(sz(4 * u));
        text.setColor(Render.alpha(0xFFFFE066, (int) (120 + 135 * a)));
        c.drawText("TAP TO START", W / 2, H * 0.68f, text);
        text.setTextSize(sz(2.6f * u));
        text.setColor(DIM);
        c.drawText("Sector " + g.sector + "  |  best " + g.bestSector + (g.collapses > 0 ? "  |  " + g.collapses + " collapses" : ""),
                W / 2, H * 0.68f + 6 * u, text);
        c.drawText("v" + Version.NAME + "  -  offline, no ads, no tracking", W / 2, H - 6 * u, text);
    }

    // ---------------- active ability & picks ----------------
    private void drawAbility(Canvas c, float tabTop) {
        if (tab >= 0 || title) return;
        float r = 7 * u, x = 3 * u + r, y = tabTop - 4 * u - r;
        float max = g.novaMax();
        float f = g.novaCd > 0 ? 1 - g.novaCd / max : 1;
        boolean ready = g.novaCd <= 0 && g.clearT < 0;
        fill.setColor(ready ? Render.alpha(0xFFC58BFF, 0x50 + (int) (40 * Math.sin(g.time * 5))) : 0xC0120C2A);
        c.drawCircle(x, y, r, fill);
        stroke.setStrokeWidth(0.7f * u);
        stroke.setColor(0xFF231A45);
        c.drawCircle(x, y, r, stroke);
        stroke.setColor(0xFFC58BFF);
        rf.set(x - r, y - r, x + r, y + r);
        c.drawArc(rf, -90, 360 * f, false, stroke);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sz(2.4f * u));
        text.setColor(ready ? 0xFFFFFFFF : DIM);
        c.drawText(ready ? "NOVA" : String.valueOf((int) Math.ceil(g.novaCd)), x, y + 0.9f * u, text);
        if (ready) {
            rf.set(x - r, y - r, x + r, y + r);
            registerBtn(rf, A_NOVA);
        }
        if (g.pendingPicks > 0) {
            float bx = x + r + 3 * u, by = y - 4 * u;
            int col = Render.mix(0xFFFFE066, 0xFFFFFFFF, 0.5f + 0.5f * (float) Math.sin(g.time * 6));
            button(c, bx, by, bx + 30 * u, by + 8 * u, "PICK UPGRADE (" + g.pendingPicks + ")", A_PICK, true, col, true);
        }
    }

    private void drawPicker(Canvas c) {
        nb = 0;
        fill.setColor(0xC0000000);
        c.drawRect(0, 0, W, H, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sz(5 * u));
        text.setColor(0xFFFFE066);
        c.drawText("CHOOSE AN UPGRADE", W / 2, H * 0.24f, text);
        text.setTextSize(sz(2.6f * u));
        text.setColor(DIM);
        c.drawText("Lasts for this run - stacks - resets on Core Collapse", W / 2, H * 0.24f + 5 * u, text);
        int n = g.offer.length;
        float ch = 20 * u, gap = 3 * u, top = H * 0.32f;
        for (int i = 0; i < n; i++) {
            int k = g.offer[i];
            float y = top + i * (ch + gap);
            rf.set(8 * u, y, W - 8 * u, y + ch);
            boolean pressed = downBtn == A_CARD + i;
            if (pressed) rf.inset(u, u);
            fill.setColor(pressed ? Render.alpha(Cards.COLOR[k], 0x50) : 0xFF140D30);
            c.drawRoundRect(rf, 3 * u, 3 * u, fill);
            stroke.setStrokeWidth(0.5f * u);
            stroke.setColor(Cards.COLOR[k]);
            c.drawRoundRect(rf, 3 * u, 3 * u, stroke);
            line(c, Cards.NAME[k], 13 * u, y + 8 * u, 4.4f * u, Cards.COLOR[k]);
            line(c, Cards.DESC[k], 13 * u, y + 13.5f * u, 3 * u, TXT);
            line(c, "owned " + g.runCard[k] + (Cards.MAX[k] < 99 ? "/" + Cards.MAX[k] : ""), 13 * u, y + 17.5f * u, 2.4f * u, DIM);
            registerBtn(rf, A_CARD + i);
        }
    }

    private boolean tabAlert(int i) {
        switch (i) {
            case 0:
                for (int w = 0; w < Weapons.COUNT; w++) {
                    if (g.wLvl[w] < 0) { if (g.coins >= Weapons.COST[w]) return true; break; }
                }
                return false;
            case 1: return g.energy >= Skills.ROLL_COST;
            case 2: return !g.gearInv.isEmpty() && g.canEquipBetterGear();
            case 4:
                if (g.researchUnlocked() && g.researching < 0) return true;
                for (int p = 0; p < Perks.COUNT; p++)
                    if (g.perkLvl[p] < Perks.MAX[p] && g.cores >= Perks.cost(p, g.perkLvl[p])) return true;
                return false;
        }
        return false;
    }

    // ---------------- hints ----------------
    private boolean hintCond(int i) {
        switch (i) {
            case 0: return g.coins >= Weapons.upBase(0) && g.wLvl[0] == 0;
            case 1: return (g.hintsDone & 1) != 0 && g.coins >= Ship.BASE[0];
            case 2: return g.energy >= Skills.ROLL_COST;
            case 3: return !g.gearInv.isEmpty();
            case 4: return g.researchUnlocked();
            case 5: return !g.comets.isEmpty();
            case 6: return Perks.coresFor(g.runBest) > 0;
            case 7: return g.planet.boss && !g.daily;
        }
        return false;
    }

    private void updateHint(float dt) {
        if (hint >= 0) {
            hintT += dt;
            boolean done = (HINT_TAB[hint] >= 0 && tab == HINT_TAB[hint]) || (HINT_TAB[hint] < 0 && hintT > 6)
                    || (hint == 5 && g.comets.isEmpty());
            if (done) {
                g.hintsDone |= 1 << hint;
                hint = -1;
            }
            return;
        }
        if (g.popTitle != null) return;
        for (int i = 0; i < HINT.length; i++) {
            if ((g.hintsDone & (1 << i)) == 0 && hintCond(i)) {
                hint = i;
                hintT = 0;
                return;
            }
        }
    }

    private void drawHint(Canvas c, float tabTop, float tw) {
        if (hint < 0 || g.popTitle != null) return;
        String s = HINT[hint];
        text.setTextSize(sz(3.2f * u));
        float w = Math.min(W - 6 * u, text.measureText(s) + 6 * u);
        float bob = (float) Math.sin(g.time * 6) * u;
        float y = (tab >= 0 ? panelTop : tabTop) - 11 * u + bob;
        int t = HINT_TAB[hint];
        float ax = t >= 0 ? (t + 0.5f) * tw : W / 2;
        float l = Math.max(3 * u, Math.min(W - 3 * u - w, ax - w / 2));
        rf.set(l, y, l + w, y + 7.5f * u);
        fill.setColor(0xF2FFE066);
        c.drawRoundRect(rf, 2 * u, 2 * u, fill);
        if (t >= 0 && tab < 0) {
            c.save();
            c.translate(ax, y + 9.5f * u);
            c.scale(1.8f * u, 1.8f * u);
            c.drawPath(arrow, fill);
            c.restore();
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFF1A0E00);
        if (text.measureText(s) > w - 3 * u) text.setTextSize(sz(3.2f * u) * (w - 3 * u) / text.measureText(s));
        c.drawText(s, l + w / 2, y + 5 * u, text);
        registerBtn(rf, A_HINT);
    }

    // ---------------- HUD ----------------
    private void drawTop(Canvas c) {
        fill.setColor(BG);
        c.drawRect(0, 0, W, 15 * u, fill);
        stroke.setStrokeWidth(0.3f * u);
        stroke.setColor(0x5519E3C9);
        c.drawLine(0, 15 * u, W, 15 * u, stroke);

        int gold = g.colorBlind ? Render.CB_GOLD : Render.GOLD, en = g.colorBlind ? Render.CB_ENERGY : Render.ENERGY;
        float cp = 1 + 0.35f * g.coinPunch;
        icon(c, coinIcon, 5 * u, 5.5f * u, 2.2f * u * cp, gold);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(sz(5 * u) * (1 + 0.08f * g.coinPunch));
        text.setColor(g.coinPunch > 0.5f ? 0xFFFFFFFF : gold);
        c.drawText(Fmt.n(dispCoins), 9 * u, 7.3f * u, text);

        float ep = 1 + 0.35f * g.energyPunch;
        icon(c, boltIcon, 46 * u, 5.5f * u, 2.4f * u * ep, en);
        text.setTextSize(sz(5 * u) * (1 + 0.08f * g.energyPunch));
        text.setColor(g.energyPunch > 0.5f ? 0xFFFFFFFF : en);
        c.drawText(Fmt.n(Math.floor(dispEnergy)), 49.5f * u, 7.3f * u, text);

        text.setTextSize(sz(2.7f * u));
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(DIM);
        c.drawText(Fmt.n(g.cpsAvg) + "/s", W - 3 * u, 4.6f * u, text);
        text.setColor(0xFFFF9F43);
        c.drawText(Fmt.n(g.dpsAvg) + " DPS", W - 3 * u, 8.4f * u, text);

        Planet p = g.planet;
        boolean boss = p.boss && !g.daily;
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(sz(3.2f * u));
        text.setColor(boss ? 0xFFFF2D55 : Render.PALETTES[p.pal][0]);
        String head = g.daily ? (g.sideMode == Game.SIDE_DEEP ? "DEEP CORE  " : g.sideMode == Game.SIDE_WEEKLY ? "WEEKLY  " : "DAILY  ") + p.name : boss ? Planet.BOSS_NAME[p.bossType] + " " + g.sector
                : "SECTOR " + g.sector + "  " + p.name;
        c.drawText(head, 4 * u, 11.3f * u, text);
        text.setTextAlign(Paint.Align.RIGHT);
        float prog = p.progress();
        c.drawText((int) (prog * 100) + "%", W - 3 * u, 11.3f * u, text);
        rf.set(4 * u, 12.4f * u, W - 3 * u, 13.4f * u);
        fill.setColor(0xFF231A45);
        c.drawRoundRect(rf, u, u, fill);
        rf.right = rf.left + rf.width() * prog;
        fill.setColor(boss ? 0xFFFF2D55 : Render.PALETTES[p.pal][0]);
        c.drawRoundRect(rf, u, u, fill);
    }

    private void drawStatus(Canvas c) {
        Planet p = g.planet;
        float y = 19.5f * u;
        if (g.daily && g.sideMode == Game.SIDE_DEEP) {
            line(c, "DEPTH " + g.deepDepth, 4 * u, y, 3.6f * u, 0xFFFF9F43);
            line(c, "RECORD " + g.deepBest, 4 * u, y + 4.5f * u, 2.8f * u, DIM);
            button(c, 3 * u, 26 * u, 28 * u, 33 * u, "SURFACE", A_DAILY, true, 0xFFFF9F43, false);
            y = 38 * u;
        } else if (g.daily && g.sideMode == Game.SIDE_WEEKLY) {
            float left = Math.max(0, g.weeklyLimit - g.dailyTime);
            line(c, Mutators.NAME[g.mutator], 4 * u, y, 3.4f * u, 0xFFC165FF);
            line(c, (int) Math.ceil(left) + "s LEFT", 4 * u, y + 4.5f * u, 3 * u, left < 20 ? 0xFFFF2D55 : TXT);
            button(c, 3 * u, 27 * u, 28 * u, 34 * u, "GIVE UP", A_DAILY, true, 0xFFC165FF, false);
            y = 39 * u;
        } else if (g.daily) {
            line(c, "TIME " + Game.fmtTime(g.dailyTime), 4 * u, y, 3.6f * u, 0xFF7DF9FF);
            y += 4.5f * u;
            if (g.dailyBestDate == Game.today())
                line(c, "BEST " + Game.fmtTime(g.dailyBest), 4 * u, y, 2.8f * u, DIM);
            button(c, 3 * u, 26 * u, 28 * u, 33 * u, "EXIT DAILY", A_DAILY, true, 0xFF7DF9FF, false);
            y = 38 * u;
        } else if (p.boss && g.clearT < 0) {
            float f = Math.max(0, g.bossTime / g.bossTimeMax());
            boolean urgent = g.bossTime < 20;
            int col = urgent && ((int) (g.time * 4) & 1) == 0 ? 0xFFFFFFFF : 0xFFFF2D55;
            line(c, "BOSS ESCAPES IN " + (int) Math.ceil(g.bossTime) + "s", 4 * u, y, 3.2f * u, col);
            rf.set(4 * u, y + 1.2f * u, 60 * u, y + 2.4f * u);
            fill.setColor(0xFF3A0E1C);
            c.drawRoundRect(rf, u, u, fill);
            rf.right = rf.left + rf.width() * f;
            fill.setColor(0xFFFF2D55);
            c.drawRoundRect(rf, u, u, fill);
            y += 6.5f * u;
            if (p.nodesLeft > 0) {
                line(c, p.nodesLeft + " SHIELD NODES", 4 * u, y - 1.5f * u, 2.8f * u,
                        g.colorBlind ? Render.CB_NODE : Render.NODE);
                y += 3.5f * u;
            }
        } else if (g.bossBlocked) {
            button(c, 3 * u, 17.5f * u, 30 * u, 24.5f * u, "FIGHT BOSS", A_FIGHTBOSS, true, 0xFFFF2D55, false);
            y = 29 * u;
        }
        if (p.mod != Planet.MOD_NONE) {
            line(c, Planet.MOD_NAME[p.mod], 4 * u, y, 3f * u, 0xFFFFE066);
            line(c, Planet.MOD_DESC[p.mod], 4 * u, y + 3.6f * u, 2.5f * u, DIM);
        }
    }

    private void drawCombo(Canvas c) {
        if (g.combo < 5) return;
        float x = W - 10 * u, y = 52 * u;
        float heat = Math.min(1, g.combo / 150f);
        int col = heat < 0.5f ? Render.mix(0xFF9BF6FF, 0xFFFFE066, heat * 2)
                : Render.mix(0xFFFFE066, 0xFFFF2D55, (heat - 0.5f) * 2);
        float s = 1 + 0.35f * g.comboPunch;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sz(6.5f * u) * s);
        text.setColor(col);
        c.drawText("x" + g.combo, x, y, text);
        text.setTextSize(sz(2.4f * u));
        text.setColor(DIM);
        c.drawText("COMBO +" + Math.round(Math.min(g.combo, 200) * 0.25) + "%", x, y + 3.5f * u, text);
        rf.set(x - 7 * u, y + 4.5f * u, x + 7 * u, y + 5.4f * u);
        fill.setColor(0xFF231A45);
        c.drawRect(rf, fill);
        rf.right = rf.left + rf.width() * Math.max(0, g.comboT / Game.COMBO_WINDOW);
        fill.setColor(col);
        c.drawRect(rf, fill);
    }

    private void drawBoosts(Canvas c) {
        float y = 62 * u;
        for (int i = 0; i < g.boostT.length; i++) {
            if (g.boostT[i] <= 0) continue;
            int col = Boosts.COLOR[i];
            rf.set(W - 26 * u, y, W - 3 * u, y + 6 * u);
            fill.setColor(0xD0100A26);
            c.drawRoundRect(rf, 1.5f * u, 1.5f * u, fill);
            rf.right = rf.left + rf.width() * (g.boostT[i] / Boosts.TIME[i]);
            fill.setColor(Render.alpha(col, 0x60));
            c.drawRoundRect(rf, 1.5f * u, 1.5f * u, fill);
            line(c, Boosts.NAME[i] + " " + (int) Math.ceil(g.boostT[i]), W - 25 * u, y + 4.2f * u, 2.6f * u, col);
            y += 7 * u;
        }
    }

    /** Big rotated stamp while a planet-cleared sequence plays. */
    private void drawStamp(Canvas c) {
        if (g.clearT <= 0 || g.daily) return;
        float age = 2.8f - g.clearT;
        float s = age < 0.18f ? 2.2f - age / 0.18f * 1.2f : 1f;
        float a = Math.min(1, g.clearT / 0.4f);
        String t = g.planet.boss ? "BOSS DOWN" : "CLEARED";
        c.save();
        c.translate(W / 2, H * 0.36f);
        c.rotate(-6);
        c.scale(s, s);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sz(11 * u));
        float tw = text.measureText(t);
        rf.set(-tw / 2 - 3 * u, -10 * u, tw / 2 + 3 * u, 3.5f * u);
        stroke.setStrokeWidth(1.1f * u);
        stroke.setColor(Render.alpha(GOOD, (int) (255 * a)));
        c.drawRoundRect(rf, 2 * u, 2 * u, stroke);
        text.setColor(Render.alpha(GOOD, (int) (255 * a)));
        c.drawText(t, 0, 0, text);
        c.restore();
    }

    private void drawBanner(Canvas c) {
        float a = Math.min(1, g.bannerT * 2);
        float age = g.bannerMax - g.bannerT;
        float pop = age < 0.15f ? 0.7f + age / 0.15f * 0.4f : age < 0.25f ? 1.1f - (age - 0.15f) : 1f;
        text.setTextAlign(Paint.Align.CENTER);
        float ts0 = sz(3.6f * u);
        text.setTextSize(ts0);
        float tw = text.measureText(g.banner) + 6 * u;
        if (tw > W - 4 * u) {
            text.setTextSize(ts0 * (W - 10 * u) / text.measureText(g.banner));
            tw = W - 4 * u;
        }
        float y = 47 * u;
        c.save();
        c.translate(W / 2, y);
        c.scale(pop, pop);
        rf.set(-tw / 2, -5 * u, tw / 2, 3 * u);
        fill.setColor(Render.alpha(0xFF0B0620, (int) (225 * a)));
        c.drawRoundRect(rf, 2 * u, 2 * u, fill);
        stroke.setStrokeWidth(0.45f * u);
        stroke.setColor(Render.alpha(g.bannerColor, (int) (255 * a)));
        c.drawRoundRect(rf, 2 * u, 2 * u, stroke);
        text.setColor(Render.alpha(g.bannerColor, (int) (255 * a)));
        c.drawText(g.banner, 0, 0, text);
        c.restore();
    }

    private void icon(Canvas c, Path p, float x, float y, float s, int col) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        fill.setColor(col);
        c.drawPath(p, fill);
        c.restore();
    }

    // =====================================================================
    private float cy; // content cursor (unscrolled)
    private boolean clipping;

    private void drawPanel(Canvas c) {
        int t = shownTab;
        int col = TAB_COL[t];
        c.save();
        c.clipRect(0, panelTop, W, H - 14 * u);
        fill.setColor(0xF50D0824);
        c.drawRect(0, panelTop, W, panelBot, fill);
        stroke.setStrokeWidth(0.4f * u);
        stroke.setColor(col);
        c.drawLine(0, panelTop, W, panelTop, stroke);

        // header
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(sz(3.8f * u));
        text.setColor(col);
        c.drawText(TABS[t], 4 * u, panelTop + 6 * u, text);
        if (t == 0 || t == 3) {
            String[] bm = {"BUY x1", "BUY x10", "BUY MAX"};
            button(c, W - 25 * u, panelTop + 1.5f * u, W - 3 * u, panelTop + 7.5f * u, bm[g.buyMode], A_BUYMODE,
                    tab >= 0, col, false);
            if (g.perkLvl[Perks.AUTOBUY] > 0)
                button(c, W - 52 * u, panelTop + 1.5f * u, W - 27 * u, panelTop + 7.5f * u,
                        "AUTO-BUY " + (g.autoBuy ? "ON" : "OFF"), A_AUTOBUY, tab >= 0, g.autoBuy ? GOOD : DIM, g.autoBuy);
        } else if (t == 1) {
            rightInfo(c, Fmt.n(Math.floor(g.energy)) + " EN", Render.ENERGY);
        } else if (t == 2) {
            rightInfo(c, Fmt.n(g.scrap) + " SCRAP", 0xFFB8C0D8);
        } else if (t == 4) {
            rightInfo(c, Fmt.n(Math.floor(g.energy)) + " EN   " + Fmt.n(g.cores) + " CORES", 0xFFFF3EA5);
        }

        float top = contentTop;
        float maxScroll = Math.max(0, contentH - (panelBot - top) + 3 * u);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        c.clipRect(0, top, W, panelBot);
        cy = top - scroll;
        float start = cy;
        clipping = true;
        switch (t) {
            case 0: weaponsPanel(c); break;
            case 1: skillsPanel(c); break;
            case 2: gearPanel(c); break;
            case 3: shipPanel(c); break;
            case 4: labPanel(c); break;
            case 5: morePanel(c); break;
        }
        clipping = false;
        contentH = cy - start;
        c.restore();
        // scroll indicator
        if (contentH > panelBot - top && tab >= 0) {
            float vis = (panelBot - top) / contentH;
            float bh = (panelBot - top) * vis;
            float by = top + (panelBot - top - bh) * (scroll / Math.max(1, maxScroll));
            fill.setColor(Render.alpha(col, 0x70));
            c.drawRoundRect(W - 1.6f * u, by, W - 0.6f * u, by + bh, u, u, fill);
        }
    }

    private void rightInfo(Canvas c, String s, int col) {
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(sz(3.4f * u));
        text.setColor(col);
        c.drawText(s, W - 4 * u, panelTop + 6 * u, text);
    }

    private void card(Canvas c, float h, int edge) {
        rf.set(3 * u, cy, W - 3 * u, cy + h);
        fill.setColor(CARD);
        c.drawRoundRect(rf, 2 * u, 2 * u, fill);
        stroke.setStrokeWidth(0.3f * u);
        stroke.setColor(Render.alpha(edge, 0x90));
        c.drawRoundRect(rf, 2 * u, 2 * u, stroke);
        fill.setColor(Render.alpha(edge, 0xB0));
        c.drawRect(3 * u, cy + 2 * u, 3.8f * u, cy + h - 2 * u, fill);
    }

    private void line(Canvas c, String s, float x, float y, float size, int col) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(sz(size));
        text.setColor(col);
        float max = W - x - 3 * u;
        float w = text.measureText(s);
        if (w > max && max > 0) text.setTextSize(sz(size) * max / w);
        c.drawText(s, x, y, text);
    }

    private void label(Canvas c, String s, int col) {
        line(c, s, 4 * u, cy + 4 * u, 3.2f * u, col);
        cy += 6 * u;
    }

    private static int sum(int[] a) {
        int s = 0;
        for (int v : a) s += v;
        return s;
    }

    private static String gain(double g) {
        if (g < 0.0005) return "";
        return "+" + (g >= 0.1 ? String.valueOf(Math.round(g * 100)) : String.format(Locale.US, "%.1f", g * 100)) + "% DPS";
    }

    private void weaponsPanel(Canvas c) {
        line(c, "Peak " + Fmt.n(g.estDps()) + " DPS (all weapons firing)  green = gain per buy", 4 * u, cy + 3 * u, 2.6f * u, 0xFFFF9F43);
        cy += 5 * u;
        for (int w = 0; w < Weapons.COUNT; w++) {
            int lvl = g.wLvl[w];
            boolean locked = lvl < 0;
            boolean prevLocked = w > 0 && g.wLvl[w - 1] < 0;
            float h = 16 * u;
            card(c, h, locked ? DIM : Weapons.COLOR[w]);
            line(c, Weapons.NAME[w], 6 * u, cy + 5.5f * u, 4 * u, locked ? DIM : Weapons.COLOR[w]);
            if (locked) {
                line(c, Weapons.DESC[w], 6 * u, cy + 10 * u, 2.8f * u, DIM);
                if (!prevLocked) {
                    line(c, "Unlock: all damage x1.5  " + gain(g.dpsGain(0, w, 1)), 6 * u, cy + 13.5f * u, 2.6f * u, GOOD);
                    button(c, W - 30 * u, cy + 4 * u, W - 6 * u, cy + 12 * u, Fmt.n(Weapons.COST[w]), A_WEAP + w,
                            g.coins >= Weapons.COST[w], Render.GOLD, false);
                } else {
                    line(c, "Unlock: all damage x1.5", 6 * u, cy + 13.5f * u, 2.6f * u, 0xFF8F87B8);
                    line(c, "LOCKED", W - 22 * u, cy + 9 * u, 3 * u, DIM);
                }
            } else {
                int n = g.buyCount(Weapons.upBase(w), Weapons.UP_GROWTH, lvl, g.coins, 100000);
                double cost = Fmt.costN(Weapons.upBase(w), Weapons.UP_GROWTH, lvl, n);
                line(c, "Lv " + lvl + "   " + Fmt.n(g.weaponDmg(w)) + (Weapons.CD[w] == 0 ? " dps" : " dmg"),
                        6 * u, cy + 10 * u, 3 * u, TXT);
                String gs = gain(g.dpsGain(0, w, n));
                if (gs.length() > 0) line(c, gs, 6 * u, cy + 13.8f * u, 2.6f * u, GOOD);
                else line(c, Weapons.DESC[w], 6 * u, cy + 13.5f * u, 2.6f * u, DIM);
                button(c, W - 30 * u, cy + 4 * u, W - 6 * u, cy + 12 * u, "+" + n + " " + Fmt.n(cost), A_WEAP + w,
                        g.coins >= cost, Render.GOLD, false);
            }
            cy += h + 2 * u;
        }
        label(c, "SYNERGIES", 0xFF9BF6FF);
        for (int s = 0; s < Synergies.NAME.length; s++) {
            boolean on = g.syn[s];
            float h = 11 * u;
            card(c, h, on ? GOOD : DIM);
            line(c, Synergies.NAME[s] + (on ? "  ACTIVE" : ""), 6 * u, cy + 4.8f * u, 3.3f * u, on ? GOOD : DIM);
            line(c, Synergies.DESC[s], 6 * u, cy + 8.8f * u, 2.4f * u, on ? TXT : DIM);
            cy += h + 1.5f * u;
        }
    }

    private void shipPanel(Canvas c) {
        String[] effect = {"", "+12% coins each", "", "+6% speed each"};
        for (int i = 0; i < Ship.COUNT; i++) {
            int lvl = g.shipLvl[i];
            float h = 14 * u;
            card(c, h, GOOD);
            line(c, Ship.NAME[i], 6 * u, cy + 5.5f * u, 4 * u, GOOD);
            if (lvl >= Ship.MAX[i]) {
                line(c, "Lv " + lvl + "/" + Ship.MAX[i] + "   " + Ship.DESC[i], 6 * u, cy + 10.5f * u, 2.8f * u, TXT);
                line(c, "MAX", W - 18 * u, cy + 9 * u, 3.5f * u, DIM);
            } else {
                int n = g.buyCount(Ship.BASE[i], Ship.GROWTH[i], lvl, g.coins, Ship.MAX[i] - lvl);
                double cost = Fmt.costN(Ship.BASE[i], Ship.GROWTH[i], lvl, n);
                String gs = (i == 0 || i == 2) ? gain(g.dpsGain(1, i, n)) : effect[i];
                line(c, "Lv " + lvl + (Ship.MAX[i] < 999 ? "/" + Ship.MAX[i] : "") + "  " + Ship.DESC[i],
                        6 * u, cy + 9.5f * u, 2.7f * u, TXT);
                line(c, gs, 6 * u, cy + 12.8f * u, 2.5f * u, GOOD);
                button(c, W - 30 * u, cy + 3 * u, W - 6 * u, cy + 11 * u, "+" + n + " " + Fmt.n(cost), A_SHIP + i,
                        g.coins >= cost, Render.GOLD, false);
            }
            cy += h + 2 * u;
        }
        label(c, "ESCORT DRONES  " + g.dronesOwned() + "/" + g.droneSlots() + " slots", GOOD);
        for (int i = 0; i < Drones.COUNT; i++) {
            int lvl = g.dLvl[i];
            float h = 14 * u;
            int dc = Drones.COLOR[i];
            card(c, h, lvl < 0 ? DIM : dc);
            line(c, Drones.NAME[i], 6 * u, cy + 5.5f * u, 4 * u, lvl < 0 ? DIM : dc);
            if (lvl < 0) {
                line(c, Drones.DESC[i], 6 * u, cy + 10.5f * u, 2.8f * u, DIM);
                boolean slot = g.dronesOwned() < g.droneSlots();
                if (slot) {
                    line(c, gain(g.dpsGain(2, i, 1)), 6 * u, cy + 13.3f * u, 2.4f * u, GOOD);
                    button(c, W - 30 * u, cy + 3 * u, W - 6 * u, cy + 11 * u, Fmt.n(Drones.COST[i]), A_DRONE + i,
                            g.coins >= Drones.COST[i], Render.GOLD, false);
                } else line(c, "NO SLOT", W - 24 * u, cy + 8.5f * u, 3 * u, DIM);
            } else {
                int n = g.buyCount(Drones.upBase(i), Drones.GROWTH, lvl, g.coins, 100000);
                double cost = Fmt.costN(Drones.upBase(i), Drones.GROWTH, lvl, n);
                line(c, "Lv " + lvl + "  " + Fmt.n(g.droneDmg(i)) + " dmg  " + Drones.DESC[i], 6 * u, cy + 9.5f * u,
                        2.5f * u, TXT);
                line(c, gain(g.dpsGain(2, i, n)), 6 * u, cy + 12.8f * u, 2.5f * u, GOOD);
                button(c, W - 30 * u, cy + 3 * u, W - 6 * u, cy + 11 * u, "+" + n + " " + Fmt.n(cost), A_DRONE + i,
                        g.coins >= cost, Render.GOLD, false);
            }
            cy += h + 2 * u;
        }
        label(c, "HULLS  (bosses drop new ones)", GOOD);
        for (int h = 0; h < Hulls.COUNT; h++) {
            boolean own = g.hasHull(h), on = g.hull == h;
            float ch = 12 * u;
            card(c, ch, on ? Hulls.COLOR[h] : own ? 0xFF3A3360 : DIM);
            c.save();
            c.translate(10 * u, cy + ch / 2);
            c.scale(0.22f * u, 0.22f * u);
            fill.setColor(own ? Hulls.COLOR[h] : DIM);
            c.drawPath(hullIcons[h], fill);
            c.restore();
            line(c, own ? Hulls.NAME[h] : "???", 16 * u, cy + 5 * u, 3.4f * u, own ? Hulls.COLOR[h] : DIM);
            line(c, own ? Hulls.DESC[h] : "defeat boss planets to find it", 16 * u, cy + 9.3f * u, 2.5f * u, own ? TXT : DIM);
            if (own) button(c, W - 24 * u, cy + 2.5f * u, W - 6 * u, cy + 9.5f * u, on ? "ACTIVE" : "USE", A_HULL + h,
                    !on, Hulls.COLOR[h], on);
            cy += ch + 1.5f * u;
        }
    }

    private void labPanel(Canvas c) {
        label(c, "RESEARCH  (runs in real time, even when closed)", 0xFF7DF9FF);
        if (!g.researchUnlocked()) {
            line(c, "Reach sector " + Research.UNLOCK_SECTOR + " to open the lab.", 5 * u, cy + 3 * u, 2.8f * u, DIM);
            cy += 7 * u;
        } else {
            if (g.researching >= 0) {
                int r = g.researching;
                float h = 12 * u;
                card(c, h, 0xFF7DF9FF);
                long left = g.researchLeftMs(), total = Research.durationMs(r, g.resLvl[r]);
                line(c, Research.NAME[r] + " -> Lv " + (g.resLvl[r] + 1), 6 * u, cy + 5 * u, 3.4f * u, 0xFF7DF9FF);
                text.setTextSize(sz(3 * u));
                text.setTextAlign(Paint.Align.RIGHT);
                text.setColor(TXT);
                c.drawText(Research.fmtDuration(left) + " left", W - 6 * u, cy + 5 * u, text);
                rf.set(6 * u, cy + 7.5f * u, W - 6 * u, cy + 9.5f * u);
                fill.setColor(0xFF231A45);
                c.drawRoundRect(rf, u, u, fill);
                rf.right = rf.left + rf.width() * (1 - left / (float) Math.max(1, total));
                fill.setColor(0xFF7DF9FF);
                c.drawRoundRect(rf, u, u, fill);
                cy += h + 2 * u;
            }
            for (int i = 0; i < Research.COUNT; i++) {
                int lvl = g.resLvl[i];
                boolean avail = g.researchAvailable(i), maxed = lvl >= Research.MAX[i];
                float h = 13 * u;
                card(c, h, avail ? 0xFF7DF9FF : DIM);
                line(c, (Research.REQ[i] >= 0 ? "- " : "") + Research.NAME[i] + "  " + lvl + "/" + Research.MAX[i],
                        6 * u, cy + 5 * u, 3.3f * u, avail ? 0xFF7DF9FF : DIM);
                String sub = !avail ? "needs " + Research.NAME[Research.REQ[i]] + " Lv " + Research.REQ_LVL[i]
                        : maxed ? Research.DESC[i] + " (maxed)"
                        : Research.DESC[i] + "  |  " + Research.fmtDuration(Research.durationMs(i, lvl));
                line(c, sub, 6 * u, cy + 9.6f * u, 2.4f * u, avail ? TXT : DIM);
                if (avail && !maxed) {
                    int cost = Research.cost(i, lvl);
                    button(c, W - 26 * u, cy + 2.5f * u, W - 6 * u, cy + 9.5f * u, cost + " EN", A_RES + i,
                            g.researching < 0 && g.energy >= cost, Render.ENERGY, false);
                }
                cy += h + 1.5f * u;
            }
        }

        int gainCores = Perks.coresFor(g.runBest);
        cy += 2 * u;
        float h = 24 * u;
        card(c, h, 0xFFFF3EA5);
        line(c, "CORE COLLAPSE", 6 * u, cy + 5.5f * u, 4 * u, 0xFFFF3EA5);
        line(c, "Reset sectors, coins, weapons, ship & drones.", 6 * u, cy + 10 * u, 2.6f * u, TXT);
        line(c, "Keep skills, gear, hulls, research, cores, perks.", 6 * u, cy + 13.5f * u, 2.6f * u, TXT);
        line(c, gainCores > 0 ? "Collapse now for +" + gainCores + " cores (run best: sector " + g.runBest + ")"
                        : "Reach sector " + Perks.MIN_SECTOR + " to collapse (run best: " + g.runBest + ")",
                6 * u, cy + 17.5f * u, 2.6f * u, gainCores > 0 ? GOOD : DIM);
        button(c, W - 36 * u, cy + 18.5f * u, W - 6 * u, cy + 23 * u, collapseArm > 0 ? "TAP TO CONFIRM" : "COLLAPSE",
                A_COLLAPSE, gainCores > 0, 0xFFFF3EA5, collapseArm > 0);
        cy += h + 2 * u;
        line(c, "Lifetime cores " + Fmt.n(g.lifetimeCores) + " = +" + Math.round(g.lifetimeCores * 2) + "% damage",
                4 * u, cy + 3 * u, 2.8f * u, DIM);
        cy += 6 * u;
        label(c, "PERKS", 0xFFFF3EA5);
        for (int p = 0; p < Perks.COUNT; p++) {
            int lvl = g.perkLvl[p];
            float ph = 13 * u;
            card(c, ph, 0xFFFF3EA5);
            line(c, Perks.NAME[p] + "  Lv " + lvl, 6 * u, cy + 5.3f * u, 3.6f * u, 0xFFFF7AD9);
            line(c, Perks.DESC[p], 6 * u, cy + 10 * u, 2.5f * u, TXT);
            if (lvl >= Perks.MAX[p]) line(c, "MAX", W - 18 * u, cy + 8 * u, 3.5f * u, DIM);
            else {
                double cost = Perks.cost(p, lvl);
                button(c, W - 28 * u, cy + 2.5f * u, W - 6 * u, cy + 10.5f * u, Fmt.n(cost) + " CORE", A_PERK + p,
                        g.cores >= cost, 0xFFFF3EA5, false);
            }
            cy += ph + 1.5f * u;
        }
    }

    private void morePanel(Canvas c) {
        int today = Game.today();
        float h = 17 * u;
        card(c, h, 0xFF7DF9FF);
        line(c, "DAILY CHALLENGE", 6 * u, cy + 5.5f * u, 4 * u, 0xFF7DF9FF);
        line(c, "A new seeded planet every day. Clear it fast.", 6 * u, cy + 10 * u, 2.6f * u, TXT);
        String best = g.dailyBestDate == today ? "Today's best: " + Game.fmtTime(g.dailyBest) : "Not cleared today";
        line(c, best + (g.dailyLastClear == today ? "" : "  (+15 EN, +20 scrap)"), 6 * u, cy + 14 * u, 2.5f * u, DIM);
        boolean inDaily = g.daily && g.sideMode == Game.SIDE_DAILY;
        button(c, W - 26 * u, cy + 2.5f * u, W - 6 * u, cy + 9.5f * u, inDaily ? "EXIT" : "START", A_DAILY,
                !g.daily || inDaily, 0xFF7DF9FF, inDaily);
        cy += h + 2 * u;

        // weekly mutator
        int mut = Mutators.week() % Mutators.COUNT;
        boolean weeklyDone = g.weeklyDoneWeek == Mutators.week();
        card(c, h, 0xFFC165FF);
        line(c, "WEEKLY: " + Mutators.NAME[mut], 6 * u, cy + 5.5f * u, 4 * u, 0xFFC165FF);
        line(c, Mutators.DESC[mut], 6 * u, cy + 10 * u, 2.6f * u, TXT);
        line(c, weeklyDone ? "Cleared this week" : "Clear in time: +50 EN and a cosmetic", 6 * u, cy + 14 * u, 2.5f * u,
                weeklyDone ? GOOD : DIM);
        boolean inWeekly = g.daily && g.sideMode == Game.SIDE_WEEKLY;
        button(c, W - 26 * u, cy + 2.5f * u, W - 6 * u, cy + 9.5f * u, inWeekly ? "EXIT" : "START", A_WEEKLY,
                !g.daily || inWeekly, 0xFFC165FF, inWeekly);
        cy += h + 2 * u;

        // deep core
        boolean deepOk = g.deepUnlocked();
        card(c, h, deepOk ? 0xFFFF9F43 : DIM);
        line(c, "DEEP CORE", 6 * u, cy + 5.5f * u, 4 * u, deepOk ? 0xFFFF9F43 : DIM);
        line(c, deepOk ? "Endless layers, each 25% tougher. Energy + scrap per depth." : "Unlocks at sector 50.",
                6 * u, cy + 10 * u, 2.6f * u, deepOk ? TXT : DIM);
        line(c, "Record depth: " + g.deepBest, 6 * u, cy + 14 * u, 2.5f * u, DIM);
        boolean inDeep = g.daily && g.sideMode == Game.SIDE_DEEP;
        button(c, W - 26 * u, cy + 2.5f * u, W - 6 * u, cy + 9.5f * u, inDeep ? "SURFACE" : "DIVE", A_DEEP,
                deepOk && (!g.daily || inDeep), 0xFFFF9F43, inDeep);
        cy += h + 2 * u;

        label(c, "SETTINGS", 0xFF7DF9FF);
        float bw = (W - 9 * u) / 2;
        toggleRow(c, bw, "SOUND", g.soundOn, A_SOUND, "VIBRATION", g.hapticsOn, A_HAPTIC);
        toggleRow(c, bw, "COLOUR-BLIND", g.colorBlind, A_CB, "LARGE TEXT", g.bigText, A_BIGTEXT);
        toggleRow(c, bw, "PERFORMANCE", g.lowFx, A_LOWFX, "AUTO-BUY", g.autoBuy && g.perkLvl[Perks.AUTOBUY] > 0,
                g.perkLvl[Perks.AUTOBUY] > 0 ? A_AUTOBUY : -1);
        toggleRow(c, bw, "MUSIC", g.musicOn, A_MUSIC, "", false, -2);
        cy -= 10 * u;
        button(c, 6 * u + bw, cy, W - 3 * u, cy + 8 * u,
                "SFX VOLUME " + Math.round(Game.SFX_LEVELS[g.sfxLevel] * 100) + "%", A_SFXVOL, g.soundOn, 0xFF7DF9FF, false);
        cy += 10 * u;
        line(c, "Performance mode: fewer particles, simpler planet drawing.", 4 * u, cy + 2 * u, 2.3f * u, DIM);
        cy += 5 * u;
        button(c, 3 * u, cy, 3 * u + bw, cy + 8 * u, "EXPORT SAVE", A_EXPORT, g.platform != null, 0xFF7DF9FF, false);
        button(c, 6 * u + bw, cy, W - 3 * u, cy + 8 * u, "IMPORT SAVE", A_IMPORT, g.platform != null, 0xFF7DF9FF, false);
        cy += 10 * u;
        line(c, "Back up your progress to a file, or restore it on a new phone.", 4 * u, cy + 2 * u, 2.3f * u, DIM);
        cy += 5 * u;

        label(c, "THEMES", 0xFF7DF9FF);
        float tw = (W - 6 * u - 3 * 2 * u) / 4;
        for (int t = 0; t < 4; t++) {
            boolean un = g.themeUnlocked(t);
            float l = 3 * u + t * (tw + 2 * u);
            button(c, l, cy, l + tw, cy + 8 * u, un ? Themes.NAME[t] : "LOCKED", A_THEME + t, un,
                    g.theme == t ? 0xFFFFE066 : 0xFF7DF9FF, g.theme == t);
        }
        cy += 9.5f * u;
        line(c, "Unlock: Blueprint @ sector 15, Pixel @ 30, Paper-cut @ first collapse", 4 * u, cy + 2 * u, 2.3f * u, DIM);
        cy += 5 * u;

        label(c, "COSMETICS", 0xFF7DF9FF);
        for (int type = 0; type < 3; type++) {
            line(c, Cosmetics.TYPE_NAME[type], 4 * u, cy + 3 * u, 2.6f * u, DIM);
            cy += 4.5f * u;
            float x = 3 * u;
            for (int i = 0; i < Cosmetics.COUNT; i++) {
                if (Cosmetics.TYPE[i] != type) continue;
                boolean un = g.cosUnlocked(i);
                boolean on = (type == 0 ? g.cosTrail : type == 1 ? g.cosPaint : g.cosBoom) == i;
                float bwid = 18 * u;
                if (x + bwid > W - 3 * u) { x = 3 * u; cy += 9 * u; }
                int col = Cosmetics.COLOR[i] != 0 ? Cosmetics.COLOR[i] : 0xFFE8E6F5;
                button(c, x, cy, x + bwid, cy + 7.5f * u, un ? Cosmetics.NAME[i] : "LOCKED", A_COS + i, un && !on,
                        on ? 0xFFFFE066 : col, on);
                x += bwid + 1.5f * u;
            }
            cy += 9.5f * u;
        }
        line(c, "Locked ones come from achievements and weekly challenges.", 4 * u, cy + 1 * u, 2.3f * u, DIM);
        cy += 5 * u;

        label(c, "CODEX", 0xFF7DF9FF);
        for (int i = 0; i < Codex.BLOCK.length; i++) codexRow(c, (g.seenBlocks & (1 << i)) != 0, Codex.BLOCK[i], Codex.BLOCK_LORE[i]);
        for (int i = 0; i < 4; i++) codexRow(c, (g.seenBoss & (1 << i)) != 0, Planet.BOSS_NAME[i], Codex.BOSS_LORE[i]);
        for (int i = 1; i < Planet.MOD_NAME.length; i++) codexRow(c, (g.seenMods & (1 << i)) != 0, Planet.MOD_NAME[i], Codex.MOD_LORE[i]);
        for (int i = 0; i < Synergies.NAME.length; i++) codexRow(c, g.syn[i], Synergies.NAME[i], Synergies.DESC[i]);

        label(c, "ACHIEVEMENTS", 0xFF7DF9FF);
        for (int i = 0; i < Achievements.COUNT; i++) {
            boolean done = g.achDone(i), claimed = g.achClaimed[i];
            float ah = 11 * u;
            card(c, ah, claimed ? DIM : done ? 0xFFFFB547 : 0xFF3A3360);
            line(c, Achievements.NAME[i], 6 * u, cy + 4.8f * u, 3.3f * u, claimed ? DIM : done ? 0xFFFFB547 : TXT);
            double v = Math.min(g.achValue(i), Achievements.GOAL[i]);
            String prog = Achievements.STAT[i] == Achievements.ST_SPEED ? (done ? "done" : "best " +
                    (g.fastestClear > 0 ? Game.fmtTime(g.fastestClear) : "-")) : Fmt.n(v) + "/" + Fmt.n(Achievements.GOAL[i]);
            line(c, Achievements.DESC[i] + "  " + prog, 6 * u, cy + 8.8f * u, 2.4f * u, DIM);
            if (claimed) line(c, "DONE", W - 16 * u, cy + 7 * u, 3 * u, DIM);
            else button(c, W - 26 * u, cy + 2 * u, W - 6 * u, cy + 9 * u, "+" + Achievements.REWARD[i] + " EN", A_ACH + i,
                    done, 0xFFFFB547, false);
            cy += ah + 1.5f * u;
        }

        label(c, "STATS", 0xFF7DF9FF);
        String[] stats = {
                "Peak DPS            " + Fmt.n(g.estDps()),
                "Damage multiplier   x" + Fmt.n(g.dmgMul),
                "Fire rate           x" + String.format(Locale.US, "%.2f", g.frMul),
                "Coin multiplier     x" + Fmt.n(g.coinMul),
                "Crit                " + Math.round(g.critChance * 100) + "% for x" + String.format(Locale.US, "%.1f", g.critDmg),
                "Ship speed          x" + String.format(Locale.US, "%.2f", g.speedMul),
                "Hexes mined         " + Fmt.n(g.totalBlocks),
                "Gold hexes          " + Fmt.n(g.goldMined),
                "Coins earned        " + Fmt.n(g.totalEarned),
                "Planets cleared     " + Fmt.n(g.planetsCleared),
                "Bosses defeated     " + Fmt.n(g.bossesKilled),
                "Ore veins           " + Fmt.n(g.veinsDone),
                "Comets caught       " + Fmt.n(g.cometsCaught),
                "Best chain          " + g.bestChain + " explosions",
                "Best combo          " + g.bestCombo,
                "Fastest 500+ clear  " + (g.fastestClear > 0 ? Game.fmtTime(g.fastestClear) : "-"),
                "Best sector         " + g.bestSector + " (this run " + g.runBest + ")",
                "Core collapses      " + g.collapses,
                "Daily clears        " + g.dailyClears,
                "Deep Core record    depth " + g.deepBest,
                "Run upgrades held   " + sum(g.runCard),
        };
        for (String s : stats) {
            line(c, s, 5 * u, cy + 3.5f * u, 2.8f * u, TXT);
            cy += 4.6f * u;
        }
        cy += 2 * u;
        line(c, "Starbore v" + Version.NAME + " - offline, no ads, no tracking.", 5 * u, cy + 3 * u, 2.6f * u, DIM);
        cy += 6 * u;
    }

    private void codexRow(Canvas c, boolean known, String name, String lore) {
        float h = 10 * u;
        card(c, h, known ? 0xFF7DF9FF : 0xFF3A3360);
        line(c, known ? name : "???", 6 * u, cy + 4.5f * u, 3 * u, known ? 0xFF7DF9FF : DIM);
        line(c, known ? lore : "Not discovered yet.", 6 * u, cy + 8.2f * u, 2.3f * u, known ? TXT : DIM);
        cy += h + 1.2f * u;
    }

    private void toggleRow(Canvas c, float bw, String a, boolean av, int aa, String b, boolean bv, int ba) {
        button(c, 3 * u, cy, 3 * u + bw, cy + 8 * u, a + " " + (av ? "ON" : "OFF"), aa, aa >= 0, av ? GOOD : DIM, av);
        if (ba != -2)
            button(c, 6 * u + bw, cy, W - 3 * u, cy + 8 * u, ba < 0 ? b + " (PERK)" : b + " " + (bv ? "ON" : "OFF"), ba,
                    ba >= 0, bv ? GOOD : DIM, bv);
        cy += 10 * u;
    }

    private void skillsPanel(Canvas c) {
        float bw = (W - 9 * u) / 2;
        button(c, 3 * u, cy, 3 * u + bw, cy + 9 * u, "ROLL x1  " + Skills.ROLL_COST + " EN", A_ROLL1,
                g.energy >= Skills.ROLL_COST, Render.ENERGY, false);
        button(c, 6 * u + bw, cy, W - 3 * u, cy + 9 * u, "ROLL x10  " + Skills.ROLL10_COST + " EN", A_ROLL10,
                g.energy >= Skills.ROLL10_COST, Render.ENERGY, false);
        cy += 11 * u;
        line(c, "Energy: cyan bolt hexes, planets, veins, comets, achievements.", 4 * u, cy + 2 * u, 2.4f * u, DIM);
        cy += 5 * u;

        button(c, W - 30 * u, cy + 0.5f * u, W - 3 * u, cy + 6 * u, "EQUIP BEST", A_BESTSK, g.canEquipBetterSkills(),
                0xFFC165FF, false);
        label(c, "EQUIPPED " + g.eqSkills.size() + "/" + g.skillSlots(), 0xFFC165FF);
        if (g.eqSkills.isEmpty()) {
            line(c, "Nothing equipped - roll and equip skills below.", 5 * u, cy + 3 * u, 2.7f * u, DIM);
            cy += 7 * u;
        }
        for (int i = 0; i < g.eqSkills.size(); i++) {
            int[] e = g.eqSkills.get(i);
            skillRow(c, e[0], e[1], 0, true, i);
        }
        label(c, "INVENTORY  (fuse 3 of a kind into the next rarity)", 0xFFC165FF);
        boolean any = false;
        for (int r = 3; r >= 0; r--) {
            for (int s = 0; s < Skills.COUNT; s++) {
                if (g.skillInv[s][r] > 0) { skillRow(c, s, r, g.skillInv[s][r], false, 0); any = true; }
            }
        }
        if (!any) {
            line(c, "Empty.", 5 * u, cy + 3 * u, 2.7f * u, DIM);
            cy += 7 * u;
        }
    }

    private void skillRow(Canvas c, int s, int r, int count, boolean equipped, int idx) {
        float h = 13 * u;
        int rc = Skills.RAR_COLOR[r];
        card(c, h, rc);
        line(c, Skills.NAME[s] + (equipped ? "" : "  x" + count), 6 * u, cy + 5.3f * u, 3.6f * u, rc);
        line(c, Skills.RARITY[r] + " - " + Skills.desc(s, r), 6 * u, cy + 10 * u, 2.7f * u, TXT);
        if (equipped) {
            button(c, W - 24 * u, cy + 2.5f * u, W - 6 * u, cy + 10.5f * u, "REMOVE", A_SKUNEQ + idx, true, DIM, false);
        } else {
            int id = s * 4 + r;
            boolean canEq = g.skillEquipped(s) || g.eqSkills.size() < g.skillSlots();
            button(c, W - 42 * u, cy + 2.5f * u, W - 25 * u, cy + 10.5f * u, g.skillEquipped(s) ? "SWAP" : "EQUIP",
                    A_SKEQ + id, canEq, rc, false);
            if (r < 3)
                button(c, W - 23 * u, cy + 2.5f * u, W - 6 * u, cy + 10.5f * u, "FUSE", A_SKFUSE + id, count >= 3,
                        Skills.RAR_COLOR[r + 1], false);
        }
        cy += h + 1.5f * u;
    }

    private void gearPanel(Canvas c) {
        for (int slot = 0; slot < 3; slot++) {
            Gear.Item it = g.eqGear[slot];
            float h = 13 * u;
            card(c, h, it == null ? DIM : Skills.RAR_COLOR[it.rar]);
            if (it == null) {
                line(c, Gear.SLOT[slot] + " - empty", 6 * u, cy + 5.3f * u, 3.6f * u, DIM);
                line(c, "Boosts " + Gear.EFFECT[slot], 6 * u, cy + 10 * u, 2.7f * u, DIM);
            } else {
                line(c, Gear.name(it) + "  Lv" + it.lvl, 6 * u, cy + 5.3f * u, 3.6f * u, Skills.RAR_COLOR[it.rar]);
                String gs = slot == 0 || it.lvl >= Gear.MAX_LVL ? "" : gain(g.gearDpsGain(slot));
                line(c, Fmt.pct(Gear.value(it) * g.gearMul) + " " + Gear.EFFECT[slot] + (gs.length() > 0 ? "  next: " + gs : ""),
                        6 * u, cy + 10 * u, 2.6f * u, gs.length() > 0 ? GOOD : TXT);
                if (it.lvl < Gear.MAX_LVL) {
                    double cost = Gear.upCost(it);
                    button(c, W - 30 * u, cy + 2.5f * u, W - 6 * u, cy + 10.5f * u, "UP " + Fmt.n(cost), A_GEARUP + slot,
                            g.scrap >= cost, 0xFFB8C0D8, false);
                } else line(c, "MAX", W - 18 * u, cy + 8 * u, 3.5f * u, DIM);
            }
            cy += h + 1.5f * u;
        }
        cy += u;
        float bw = (W - 9 * u) / 2;
        button(c, 3 * u, cy, 3 * u + bw, cy + 9 * u, "FORGE  " + Gear.FORGE_COST + " EN", A_FORGE,
                g.energy >= Gear.FORGE_COST && g.gearInv.size() < Gear.INV_CAP, Render.ENERGY, false);
        button(c, 6 * u + bw, cy, W - 3 * u, cy + 9 * u, "SALVAGE COMMONS", A_SALVC, !g.gearInv.isEmpty(), 0xFFB8C0D8, false);
        cy += 11 * u;
        button(c, W - 30 * u, cy + 0.5f * u, W - 3 * u, cy + 6 * u, "EQUIP BEST", A_BESTGEAR, g.canEquipBetterGear(),
                0xFFFFB547, false);
        label(c, "STORAGE " + g.gearInv.size() + "/" + Gear.INV_CAP, 0xFFFFB547);
        for (int i = 0; i < g.gearInv.size(); i++) {
            Gear.Item it = g.gearInv.get(i);
            float h = 12 * u;
            int rc = Skills.RAR_COLOR[it.rar];
            card(c, h, rc);
            line(c, Gear.name(it) + (it.lvl > 0 ? "  Lv" + it.lvl : ""), 6 * u, cy + 5 * u, 3.3f * u, rc);
            Gear.Item cur = g.eqGear[it.slot];
            boolean better = cur == null || Gear.value(it) > Gear.value(cur);
            line(c, Gear.desc(it) + (better ? "  ^" : ""), 6 * u, cy + 9.5f * u, 2.7f * u, better ? GOOD : TXT);
            button(c, W - 42 * u, cy + 2 * u, W - 25 * u, cy + 10 * u, "EQUIP", A_GEQ + i, true, rc, false);
            button(c, W - 23 * u, cy + 2 * u, W - 6 * u, cy + 10 * u, "+" + Fmt.n(Gear.salvage(it)), A_GSALV + i, true,
                    0xFFB8C0D8, false);
            cy += h + 1.5f * u;
        }
    }

    private void drawPopup(Canvas c) {
        fill.setColor(0xB0000000);
        c.drawRect(0, 0, W, H, fill);
        int n = g.popLines.length;
        float h = 22 * u + n * 5 * u;
        float top = H / 2 - h / 2;
        rf.set(6 * u, top, W - 6 * u, top + h);
        fill.setColor(0xFF140D30);
        c.drawRoundRect(rf, 3 * u, 3 * u, fill);
        stroke.setStrokeWidth(0.5f * u);
        stroke.setColor(0xFF9BF6FF);
        c.drawRoundRect(rf, 3 * u, 3 * u, stroke);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(sz(5 * u));
        text.setColor(0xFF9BF6FF);
        c.drawText(g.popTitle, W / 2, top + 8 * u, text);
        for (int i = 0; i < n; i++) {
            String s = g.popLines[i];
            int col = TXT;
            for (int r = 0; r < 4; r++) if (s.startsWith(Skills.RARITY[r])) col = Skills.RAR_COLOR[r];
            text.setColor(col);
            text.setTextSize(sz(3.2f * u));
            float tw = text.measureText(s);
            if (tw > W - 16 * u) text.setTextSize(sz(3.2f * u) * (W - 16 * u) / tw);
            c.drawText(s, W / 2, top + 14 * u + i * 5 * u, text);
        }
        nb = 0; // the popup is modal: only its button is live
        button(c, W / 2 - 15 * u, top + h - 10 * u, W / 2 + 15 * u, top + h - 3 * u, "OK", A_POPOK, true, 0xFF9BF6FF, false);
    }

    private void button(Canvas c, float l, float t, float r, float b, String label, int act, boolean enabled, int col,
                        boolean selected) {
        boolean pressed = enabled && act >= 0 && act == downBtn && !(dragging && moved);
        rf.set(l, t, r, b);
        if (pressed) rf.inset(0.5f * u, 0.4f * u);
        fill.setColor(pressed ? Render.alpha(col, 0x70) : selected ? Render.alpha(col, 0x40) : 0xFF120C2A);
        c.drawRoundRect(rf, 1.6f * u, 1.6f * u, fill);
        stroke.setStrokeWidth((selected || pressed ? 0.55f : 0.35f) * u);
        stroke.setColor(enabled ? col : Render.alpha(DIM, 0x90));
        c.drawRoundRect(rf, 1.6f * u, 1.6f * u, stroke);
        text.setTextAlign(Paint.Align.CENTER);
        float tsz = Math.min((b - t) * 0.42f, sz(3.4f * u));
        text.setTextSize(tsz);
        float tw = text.measureText(label);
        if (tw > (r - l) - 2 * u) text.setTextSize(tsz * ((r - l) - 2 * u) / tw);
        text.setColor(pressed ? 0xFFFFFFFF : enabled ? col : DIM);
        c.drawText(label, (l + r) / 2, (t + b) / 2 + text.getTextSize() * 0.36f, text);
        if (!enabled || act < 0) return;
        if (clipping && (tab < 0 || b < contentTop || t > panelBot)) return; // scrolled out of view / closing
        rf.set(l, t, r, b);
        if (clipping) rf.intersect(0, contentTop, W, panelBot);
        registerBtn(rf, act);
    }

    private void registerBtn(RectF r, int act) {
        Btn bt;
        if (nb < btns.size()) bt = btns.get(nb); else { bt = new Btn(); btns.add(bt); }
        bt.r.set(r);
        bt.act = act;
        nb++;
    }

    // =====================================================================
    private int hit(float x, float y) {
        for (int i = nb - 1; i >= 0; i--) if (btns.get(i).r.contains(x, y)) return btns.get(i).act;
        return -1;
    }

    private boolean inPanel(float x, float y) {
        return tab >= 0 && y >= panelTop && y < panelBot;
    }

    void touch(int action, float x, float y) {
        if (u == 0) return;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (title) {
                    title = false;
                    g.sfx(Game.S_CLEAR, 0.5f);
                    downBtn = -1;
                    return;
                }
                downX = x; downY = y; lastY = y;
                moved = false;
                downBtn = hit(x, y);
                holdT = 0;
                repeatT = 0;
                repeated = false;
                dragging = g.popTitle == null && g.offer == null && inPanel(x, y);
                if (downBtn < 0 && !dragging && g.popTitle == null && g.offer == null && y > 15 * u && y < H - 14 * u) {
                    // comets first, then the joystick
                    if (g.tapWorld((x - g.viewOx) / g.viewZoom, (y - g.viewOy) / g.viewZoom)) return;
                    joyOn = true;
                    joyBX = joyKX = x;
                    joyBY = joyKY = y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(x - downX) + Math.abs(y - downY) > 2.5f * u) moved = true;
                if (dragging) {
                    scroll -= y - lastY;
                    lastY = y;
                }
                if (joyOn) {
                    float dx = x - joyBX, dy = y - joyBY, d = (float) Math.hypot(dx, dy), max = 12 * u;
                    if (d > max) { dx *= max / d; dy *= max / d; }
                    joyKX = joyBX + dx;
                    joyKY = joyBY + dy;
                    g.joyActive = d > 2 * u;
                    g.joyX = dx / max;
                    g.joyY = dy / max;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (action == MotionEvent.ACTION_UP && downBtn >= 0 && !repeated && !(dragging && moved)
                        && hit(x, y) == downBtn)
                    act(downBtn);
                dragging = false;
                joyOn = false;
                g.joyActive = false;
                downBtn = -1;
                break;
        }
    }

    boolean back() {
        if (title) { title = false; return true; }
        if (g.popTitle != null) { g.popTitle = null; return true; }
        if (g.offer != null) { g.offer = null; return true; }
        if (tab >= 0) { tab = -1; return true; }
        if (g.mapMode) { g.mapMode = false; return true; }
        return false;
    }

    /** Upgrade buttons that keep buying while held. */
    private static boolean repeatable(int a) {
        return (a >= A_WEAP && a < A_WEAP + 100) || (a >= A_SHIP && a < A_SHIP + 100) || (a >= A_GEARUP && a < A_GEARUP + 100)
                || (a >= A_PERK && a < A_PERK + 100) || (a >= A_DRONE && a < A_DRONE + 100);
    }

    private void updateHold(float dt) {
        if (downBtn < 0 || !repeatable(downBtn) || moved || g.popTitle != null) return;
        holdT += dt;
        if (holdT < 0.35f) return;
        repeatT -= dt;
        if (repeatT > 0) return;
        // accelerate from ~7 to ~30 purchases per second
        repeatT = Math.max(0.033f, 0.14f - (holdT - 0.35f) * 0.06f);
        repeated = true;
        act(downBtn);
    }

    private void act(int a) {
        g.sfx(Game.S_CLICK, 0.4f);
        if (a == A_POPOK) { g.popTitle = null; return; }
        if (a == A_HINT) { if (hint >= 0) { g.hintsDone |= 1 << hint; hint = -1; } return; }
        if (a == A_AUTO) { g.auto = !g.auto; return; }
        if (a == A_MAP) { g.mapMode = !g.mapMode; return; }
        if (a == A_BUYMODE) { g.buyMode = (g.buyMode + 1) % 3; return; }
        if (a == A_MORE) { toggleTab(5); return; }
        if (a == A_FIGHTBOSS) { g.fightBoss(); return; }
        if (a == A_DAILY) {
            if (g.daily) g.endDaily(); else { g.startSide(Game.SIDE_DAILY); tab = -1; }
            return;
        }
        if (a == A_SOUND) { g.soundOn = !g.soundOn; return; }
        if (a == A_HAPTIC) { g.hapticsOn = !g.hapticsOn; g.buzz(40); return; }
        if (a == A_CB) { g.colorBlind = !g.colorBlind; return; }
        if (a == A_BIGTEXT) { g.bigText = !g.bigText; return; }
        if (a == A_LOWFX) { g.lowFx = !g.lowFx; return; }
        if (a == A_AUTOBUY) { if (g.perkLvl[Perks.AUTOBUY] > 0) g.autoBuy = !g.autoBuy; return; }
        if (a == A_COLLAPSE) {
            if (collapseArm > 0) { collapseArm = 0; g.collapse(); tab = -1; } else collapseArm = 3f;
            return;
        }
        if (a == A_PICK) { g.openPicks(); return; }
        if (a == A_NOVA) { g.useNova(); return; }
        if (a == A_MUSIC) { g.musicOn = !g.musicOn; return; }
        if (a == A_SFXVOL) {
            g.sfxLevel = (g.sfxLevel + 1) % Game.SFX_LEVELS.length;
            g.sfx(Game.S_COIN, 0.6f); // preview the new level
            return;
        }
        if (a == A_EXPORT) { if (g.platform != null) g.platform.exportSave(); return; }
        if (a == A_IMPORT) { if (g.platform != null) g.platform.importSave(); return; }
        if (a == A_DEEP) {
            if (g.daily) g.endDaily(); else { g.startSide(Game.SIDE_DEEP); tab = -1; }
            return;
        }
        if (a == A_WEEKLY) {
            if (g.daily) g.endDaily(); else { g.startSide(Game.SIDE_WEEKLY); tab = -1; }
            return;
        }
        if (a == A_BESTSK) { g.equipBestSkills(); return; }
        if (a == A_BESTGEAR) { g.equipBestGear(); return; }
        if (a == A_ROLL1) { g.rollSkills(1); return; }
        if (a == A_ROLL10) { g.rollSkills(10); return; }
        if (a == A_FORGE) { g.forge(); return; }
        if (a == A_SALVC) { g.salvageCommons(); return; }
        if (a >= A_COS) { g.setCos(a - A_COS); return; }
        if (a >= A_CARD) { g.pickCard(a - A_CARD); return; }
        if (a >= A_RES) { g.startResearch(a - A_RES); return; }
        if (a >= A_HULL) { g.setHull(a - A_HULL); return; }
        if (a >= A_DRONE) { g.buyDrone(a - A_DRONE); return; }
        if (a >= A_PERK) { g.buyPerk(a - A_PERK); return; }
        if (a >= A_ACH) { g.claimAch(a - A_ACH); return; }
        if (a >= A_THEME) { int t = a - A_THEME; if (g.themeUnlocked(t)) g.theme = t; return; }
        if (a >= A_GSALV) { g.salvageGear(a - A_GSALV); return; }
        if (a >= A_GEQ) { g.equipGear(a - A_GEQ); return; }
        if (a >= A_GEARUP) { g.upgradeGear(a - A_GEARUP); return; }
        if (a >= A_SKFUSE) { int id = a - A_SKFUSE; g.fuseSkill(id / 4, id % 4); return; }
        if (a >= A_SKEQ) { int id = a - A_SKEQ; g.equipSkill(id / 4, id % 4); return; }
        if (a >= A_SKUNEQ) { g.unequipSkill(a - A_SKUNEQ); return; }
        if (a >= A_SHIP) { g.buyShip(a - A_SHIP); return; }
        if (a >= A_WEAP) { g.buyWeapon(a - A_WEAP); return; }
        if (a >= A_TAB) toggleTab(a - A_TAB);
    }

    private void toggleTab(int t) {
        if (tab == t) tab = -1;
        else {
            tab = t;
            scroll = 0;
        }
    }
}
