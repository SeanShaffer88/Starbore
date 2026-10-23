package com.starbore.game;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Random;

/** All game state and simulation. Accessed only from the game thread (or while holding its lock). */
final class Game {
    static final float SQ3 = 1.7320508f;
    static final float HS = 24f;
    static final float SHIP_R = 11f;
    static final float BOSS_TIME = 150f;
    static final float COMBO_WINDOW = 1.5f;

    /** Sound/haptics sink, implemented by the Android layer (null in headless tests). */
    interface Fx {
        /** rate <= 0 means "slightly randomised default pitch". */
        void play(int id, float vol, float rate);

        void buzz(int ms);
    }

    static final int S_SHOOT = 0, S_ZAP = 1, S_BREAK = 2, S_BOOM = 3, S_COIN = 4, S_CLEAR = 5, S_CLICK = 6, S_BOSS = 7;
    static final int SRC_HIT = 0, SRC_LANCE = 1, SRC_CHAIN = 2, SRC_NOVA = 3;
    static final int P_SHARD = 0, P_SMOKE = 1, P_SPARK = 2;

    final Random rnd = new Random();
    Fx fx;

    // ---------------- persistent ----------------
    double coins, energy, scrap, cores, lifetimeCores;
    int sector = 1, bestSector = 1, runBest = 1;
    long seed = 1;
    final int[] wLvl = new int[Weapons.COUNT];
    final int[] shipLvl = new int[Ship.COUNT];
    final int[] perkLvl = new int[Perks.COUNT];
    final int[] dLvl = new int[Drones.COUNT];
    final int[] resLvl = new int[Research.COUNT];
    final int[][] skillInv = new int[Skills.COUNT][4];
    final ArrayList<int[]> eqSkills = new ArrayList<>();
    final ArrayList<Gear.Item> gearInv = new ArrayList<>();
    final Gear.Item[] eqGear = new Gear.Item[3];
    double cpsAvg;
    int buyMode;
    boolean auto = true, soundOn = true, hapticsOn = true, bossBlocked, twinPhase;
    boolean autoBuy, colorBlind, bigText, lowFx;
    int theme, hull, hullsOwned = 1, hintsDone;
    int researching = -1;
    long researchEnd;
    // stats
    long totalBlocks, goldMined, planetsCleared, bossesKilled, collapses, veinsDone, dailyClears, cometsCaught;
    int bestChain, bestCombo;
    float fastestClear;
    double totalEarned;
    final boolean[] achClaimed = new boolean[Achievements.COUNT];
    // daily
    int dailyBestDate, dailyLastClear;
    float dailyBest;
    // deep core & weekly
    int deepBest, weeklyDoneWeek = -1, weekliesCleared;
    // run cards
    final int[] runCard = new int[Cards.COUNT];
    int pendingPicks;
    int[] offer;
    // cosmetics & codex
    int cosTrail = 0, cosPaint = 6, cosBoom = 11, cosWeekly;
    int seenBlocks, seenBoss, seenMods;
    boolean musicOn = true;

    /** Platform hooks implemented by the activity (save export/import). */
    interface Platform {
        void exportSave();

        void importSave();
    }

    Platform platform;
    static final int SIDE_DAILY = 1, SIDE_DEEP = 2, SIDE_WEEKLY = 3;
    static final float NOVA_CD = 40f;

    // ---------------- derived ----------------
    double dmgMul, frMul, coinMul, critChance, critDmg, speedMul, gearMul;
    final double[] sk = new double[Skills.COUNT];
    final boolean[] syn = new boolean[Synergies.NAME.length];

    // ---------------- runtime ----------------
    Planet planet, mainPlanet;
    /** True while any side mode (daily, deep core, weekly) is active. */
    boolean daily;
    int sideMode, mutator = -1, deepDepth;
    float dailyTime, weeklyLimit;
    float novaCd;
    float shipX, shipY, shipVX, shipVY, shipAng;
    float joyX, joyY;
    boolean joyActive, mapMode;
    float time, planetTime, bossTime, regenT;
    Block nearest;
    float nearestD;
    final float[] wTimer = new float[Weapons.COUNT];
    float sawAng;
    boolean lanceOn;
    float lanceX, lanceY;
    float clearT = -1;
    boolean pendingSplit;
    // juice
    float shake, hitStopT, slowT, flash, warpT, muzzle, comboPunch, coinPunch, energyPunch, stunT;
    int flashColor, muzzleColor, combo;
    float comboT;
    final ArrayList<float[]> coinEvents = new ArrayList<>();
    // view (written by Render, read by Ui for tap->world)
    float viewOx, viewOy, viewZoom = 1;
    // boosts & comets
    final float[] boostT = new float[3];
    float cometT = 40;
    float autoBuyT;
    // measured dps
    double dmgThisSec, dpsAvg;
    int curChain;
    double secCoins;
    float secT;
    String banner;
    float bannerT, bannerMax;
    int bannerColor;
    String popTitle;
    String[] popLines;

    final ArrayList<Proj> projs = new ArrayList<>();
    final ArrayList<Proj> eshots = new ArrayList<>();
    final ArrayList<Particle> parts = new ArrayList<>();
    final ArrayList<Beam> beams = new ArrayList<>();
    final ArrayList<Ring> rings = new ArrayList<>();
    final ArrayList<FText> texts = new ArrayList<>();
    final ArrayList<Expl> expls = new ArrayList<>();
    final ArrayList<Sing> sings = new ArrayList<>();
    final ArrayList<Zone> zones = new ArrayList<>();
    final ArrayList<Comet> comets = new ArrayList<>();
    final Drone[] drones = new Drone[Drones.COUNT];
    private final ArrayList<Block> tmp = new ArrayList<>();
    private final ArrayList<Block> tmpT = new ArrayList<>();
    private final ArrayList<Block> chainHit = new ArrayList<>();

    static final class Proj {
        static final int BOLT = 0, LOB = 1, MISSILE = 2;
        int kind, color;
        float x, y, vx, vy, life, aoe, sx, sy, tx, ty, total;
        double dmg;
        Block target;
    }

    static final class Particle {
        float x, y, vx, vy, life, max, size, rot;
        int color, kind;
    }

    static final class Beam {
        float[] pts;
        float life, max, width;
        int color;
    }

    static final class Ring {
        float x, y, r0, r1, life, max;
        int color;
        boolean filled;
    }

    static final class FText {
        float x, y, life;
        String s;
        int color;
        boolean big;
    }

    static final class Expl {
        float x, y, delay;
        double dmg;
        boolean big, cosmetic;
    }

    static final class Sing {
        float x, y, life;
        double dps;
    }

    static final class Zone {
        float x, y, r, life, max;
        double dps;
    }

    static final class Drone {
        float x, y, cd, retarget;
        Block target;
    }

    static final class Comet {
        float x, y, vx, vy, life;
        int kind;
        boolean meteor;
    }

    void newGame() {
        for (int i = 0; i < Weapons.COUNT; i++) wLvl[i] = -1;
        wLvl[0] = 0;
        for (int i = 0; i < Drones.COUNT; i++) dLvl[i] = -1;
        newPlanet();
    }

    void placeShip() {
        float a = rnd.nextFloat() * 6.283f;
        shipX = (float) Math.cos(a) * (planet.worldR + 70);
        shipY = (float) Math.sin(a) * (planet.worldR + 70);
        shipVX = shipVY = 0;
        for (int i = 0; i < drones.length; i++) drones[i] = null;
    }

    private void clearTransient() {
        projs.clear();
        eshots.clear();
        sings.clear();
        expls.clear();
        zones.clear();
        beams.clear();
        clearT = -1;
        planetTime = 0;
        stunT = 0;
    }

    void newPlanet() {
        seed = rnd.nextLong();
        twinPhase = false;
        planet = new Planet(sector, seed, null, false, false);
        onPlanetStart();
    }

    /** Called after a fresh or loaded main planet becomes active. */
    void onPlanetStart() {
        clearTransient();
        placeShip();
        warpT = 1.2f;
        noteSeen();
        if (planet.boss) {
            bossTime = bossTimeMax();
            regenT = 2.5f;
        }
    }

    void noteSeen() {
        if (planet.mod != Planet.MOD_NONE) seenMods |= 1 << planet.mod;
        if (planet.boss) seenBoss |= 1 << planet.bossType;
    }

    float bossTimeMax() { return BOSS_TIME + 15 * resLvl[Research.INTEL] + 15 * runCard[Cards.BOSST]; }

    float novaMax() { return NOVA_CD * (float) Math.pow(0.85, runCard[Cards.NOVA]); }

    int skillSlots() { return 3 + Math.min(3, bestSector / 10); }

    int droneSlots() { return 1 + perkLvl[Perks.DRONEBAY] + (hull == Hulls.CARRIER ? 1 : 0); }

    int dronesOwned() {
        int n = 0;
        for (int l : dLvl) if (l >= 0) n++;
        return n;
    }

    int unlockedWeapons() {
        int n = 0;
        for (int l : wLvl) if (l >= 0) n++;
        return n;
    }

    float sawRadius() { return hull == Hulls.REAPER ? 74 : 46; }

    boolean hasHull(int h) { return (hullsOwned & (1 << h)) != 0; }

    void recalc() {
        for (int i = 0; i < sk.length; i++) sk[i] = 0;
        for (int[] e : eqSkills) sk[e[0]] += Skills.value(e[0], e[1]);
        gearMul = 1 + 0.15 * resLvl[Research.FORGE];
        double hull0 = eqGear[0] == null ? 0 : Gear.value(eqGear[0]) * gearMul;
        double core = eqGear[1] == null ? 0 : Gear.value(eqGear[1]) * gearMul;
        double eng = eqGear[2] == null ? 0 : Gear.value(eqGear[2]) * gearMul;
        dmgMul = Math.pow(Weapons.UNLOCK_MULT, unlockedWeapons() - 1) * (1 + core) * (1 + sk[Skills.DMG])
                * (1 + 0.25 * perkLvl[Perks.DAMAGE]) * (1 + 0.02 * lifetimeCores) * (1 + 0.1 * resLvl[Research.DRILL])
                * (hull == Hulls.BASTION ? 1.15 : 1) * (boostT[Boosts.OVERDRIVE] > 0 ? 2.5 : 1)
                * (1 + 0.12 * runCard[Cards.DMG]) * (mutator == Mutators.GLASS ? 3 : 1);
        frMul = (1 + 0.05 * shipLvl[0]) * (1 + 0.6 * eng) * (1 + sk[Skills.RATE]) * (1 + 0.05 * resLvl[Research.OVERCLOCK])
                * (boostT[Boosts.FRENZY] > 0 ? 2 : 1) * (1 + 0.08 * runCard[Cards.RATE]);
        coinMul = (1 + 0.12 * shipLvl[1]) * (1 + hull0) * (1 + sk[Skills.COIN]) * (1 + 0.25 * perkLvl[Perks.YIELD])
                * (1 + 0.1 * resLvl[Research.MARKET]) * (hull == Hulls.PROSPECTOR ? 1.2 : 1)
                * (boostT[Boosts.GOLD] > 0 ? 3 : 1) * (1 + 0.15 * runCard[Cards.COIN]) * (mutator == Mutators.TITAN ? 3 : 1);
        critChance = Math.min(0.75, 0.05 + sk[Skills.CRIT] + 0.04 * runCard[Cards.CRIT]);
        critDmg = 2.5 + 0.2 * shipLvl[2] + 0.5 * runCard[Cards.CRITDMG];
        speedMul = Math.min(3, (1 + 0.06 * shipLvl[3]) * (1 + eng) * (1 + sk[Skills.SPEED]) * (hull == Hulls.HORNET ? 1.25 : 1));
        for (int s = 0; s < syn.length; s++) {
            boolean on = true;
            for (int w : Synergies.NEEDS[s]) if (wLvl[w] < 0) on = false;
            syn[s] = on;
        }
    }

    double weaponDmg(int w) {
        double d = Weapons.DMG[w] * Weapons.lvlFactor(Math.max(0, wLvl[w])) * dmgMul;
        if (w == Weapons.SAWS && hull == Hulls.REAPER) d *= 1.5;
        if (mutator == Mutators.DRONES) d *= 0.5;
        return d;
    }

    double droneDmg(int d) {
        return weaponDmg(Weapons.BLASTER) * 2.5 * Weapons.lvlFactor(Math.max(0, dLvl[d])) / Weapons.lvlFactor(0)
                * (1 + 0.2 * resLvl[Research.DRONEAI]) * (1 + 0.25 * runCard[Cards.DRONE])
                * (mutator == Mutators.DRONES ? 3 : 1);
    }

    /** Peak damage per second with every weapon firing (used to compare upgrades; real play is lower). */
    double estDps() {
        double crit = 1 + critChance * (critDmg - 1);
        double s = 0;
        for (int w = 0; w < Weapons.COUNT; w++) {
            if (wLvl[w] < 0) continue;
            double d = weaponDmg(w), cd = Weapons.CD[w];
            switch (w) {
                case Weapons.BLASTER: s += d / cd * frMul * crit; break;
                case Weapons.LANCE: s += d * frMul; break;
                case Weapons.SCATTER: s += d * 2 / cd * frMul * crit; break;
                case Weapons.MORTAR: s += d * (1 + runCard[Cards.MORTAR]) * (syn[Synergies.NAPALM] ? 3.5 : 2.5) / cd * frMul * crit; break;
                case Weapons.CHAIN: s += d * (3 + runCard[Cards.CHAIN] * 0.5) / cd * frMul * crit; break;
                case Weapons.SAWS: s += d * 1.2 * frMul; break;
                case Weapons.SWARM: s += d * 4 / cd * frMul * crit; break;
                case Weapons.RAIL: s += d * 2.5 / cd * frMul * crit; break;
                case Weapons.NOVA: s += d * 4 / cd * frMul * crit; break;
                case Weapons.SINGULARITY: s += d * 6 / cd * frMul; break;
            }
        }
        for (int i = 0; i < Drones.COUNT; i++) if (dLvl[i] >= 0) s += droneDmg(i) / 0.45 * 0.5 * frMul * crit;
        return s;
    }

    /** Relative DPS gain (0.12 = +12%) of a hypothetical change: kind 0 weapon, 1 ship, 2 drone. */
    double dpsGain(int kind, int idx, int n) {
        recalc();
        double before = estDps();
        int[] arr = kind == 0 ? wLvl : kind == 1 ? shipLvl : dLvl;
        int old = arr[idx];
        arr[idx] = old < 0 ? 0 : old + n;
        recalc();
        double after = estDps();
        arr[idx] = old;
        recalc();
        return before <= 0 ? 0 : after / before - 1;
    }

    double gearDpsGain(int slot) {
        Gear.Item it = eqGear[slot];
        if (it == null) return 0;
        recalc();
        double before = estDps();
        it.lvl++;
        recalc();
        double after = estDps();
        it.lvl--;
        recalc();
        return before <= 0 ? 0 : after / before - 1;
    }

    float rangeMul() { return planet.mod == Planet.MOD_LOWG ? 1.5f : 1f; }

    /** Sound-effect volume steps; the default (60%) sits under the music. */
    static final float[] SFX_LEVELS = {0.25f, 0.4f, 0.6f, 0.8f, 1f};
    int sfxLevel = 2;

    void sfx(int id, float vol) { if (fx != null && soundOn) fx.play(id, vol * SFX_LEVELS[sfxLevel], 0); }

    void sfxR(int id, float vol, float rate) { if (fx != null && soundOn) fx.play(id, vol * SFX_LEVELS[sfxLevel], rate); }

    void buzz(int ms) { if (fx != null && hapticsOn) fx.buzz(ms); }

    void hitStop(float s) { if (hitStopT <= 0) hitStopT = s; }

    /** Size of the campaign planet (the one that gets saved). */
    int mainOrPlanetSize() { return (daily && mainPlanet != null ? mainPlanet : planet).all.size(); }

    void flash(float a, int col) {
        if (a >= flash) { flash = a; flashColor = col; }
    }

    // =====================================================================
    void update(float dt) {
        float real = dt;
        if (hitStopT > 0) { hitStopT -= real; dt *= 0.05f; }
        else if (slowT > 0) { slowT -= real; dt *= 0.35f; }
        time += dt;
        if (pendingSplit) doSplit();
        for (int i = 0; i < boostT.length; i++) if (boostT[i] > 0) boostT[i] -= dt;
        recalc();
        if (clearT >= 0) {
            clearT -= dt;
            if (clearT < 0) {
                if (daily && sideMode == SIDE_DEEP) nextDeep();
                else if (daily) endDaily();
                else nextSector();
            }
        } else {
            planetTime += dt;
            if (daily) dailyTime += dt;
            if (daily && sideMode == SIDE_WEEKLY && dailyTime > weeklyLimit) weeklyFailed();
            if (planet.boss && !daily) updateBoss(dt);
        }
        if (stunT > 0) stunT -= dt;
        if (novaCd > 0) novaCd -= dt;
        updateShip(dt);
        findNearest();
        if (clearT < 0) {
            if (stunT <= 0) updateWeapons(dt);
            else lanceOn = false;
            updateDrones(dt);
            if (planet.bossType == Planet.BOSS_GUNSHIP && !daily) updateTurrets(dt);
        }
        updateProjectiles(dt);
        updateExplosions(dt);
        updateComets(dt);
        updateFx(dt);
        if (autoBuy && perkLvl[Perks.AUTOBUY] > 0) {
            autoBuyT -= real;
            if (autoBuyT <= 0) { autoBuyT = 0.3f; autoBuyStep(); }
        }

        if (clearT < 0 && !pendingSplit && planet.alive.isEmpty()) planetCleared();

        secT += real;
        if (secT >= 1f) {
            if (!daily) cpsAvg = cpsAvg == 0 ? secCoins : cpsAvg * 0.95 + secCoins * 0.05;
            dpsAvg = dpsAvg * 0.7 + dmgThisSec / secT * 0.3;
            dmgThisSec = 0;
            secCoins = 0;
            secT = 0;
            checkResearch();
        }
        if (bannerT > 0) bannerT -= real;
        comboT -= dt;
        if (comboT <= 0) combo = 0;
        shake = Math.max(0, shake - real * 30);
        flash = Math.max(0, flash - real * 2.5f);
        warpT = Math.max(0, warpT - real);
        muzzle = Math.max(0, muzzle - real);
        comboPunch = Math.max(0, comboPunch - real * 5);
        coinPunch = Math.max(0, coinPunch - real * 4);
        energyPunch = Math.max(0, energyPunch - real * 4);
    }

    private void updateBoss(float dt) {
        bossTime -= dt;
        regenT -= dt;
        if (regenT <= 0) {
            regenT = 2.5f;
            ArrayList<Block> cand = new ArrayList<>();
            for (Block b : planet.all) {
                if (b.alive || b.type == Block.HEART) continue;
                float dx = b.x - shipX, dy = b.y - shipY;
                if (dx * dx + dy * dy < 60 * 60) continue;
                if (planet.hasAliveNeighbor(b)) cand.add(b);
            }
            for (int i = 0; i < 3 && !cand.isEmpty(); i++) {
                Block b = cand.remove(rnd.nextInt(cand.size()));
                planet.revive(b);
                b.flash = 0.25f;
                ring(b.x, b.y, 2, HS, 0.35f, 0xFFFF2D55);
            }
        }
        if (bossTime <= 0) {
            bossBlocked = true;
            sector = Math.max(1, sector - 1);
            newPlanet();
            showBanner("BOSS ESCAPED - farming sector " + sector + ". Tap FIGHT BOSS when stronger", 0xFFFF2D55, 4f);
            sfx(S_BOSS, 0.9f);
            buzz(200);
            flash(0.5f, 0xFFFF2D55);
        }
    }

    private void updateTurrets(float dt) {
        for (Block b : planet.alive) {
            if (b.type != Block.TURRET) continue;
            b.fireT -= dt;
            if (b.fireT > 0) continue;
            b.fireT = 4f + rnd.nextFloat() * 2;
            float dx = shipX - b.x, dy = shipY - b.y, d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 480 || d < 1) continue;
            Proj p = new Proj();
            p.x = b.x; p.y = b.y;
            p.vx = dx / d * 130;
            p.vy = dy / d * 130;
            p.life = 5f;
            p.color = 0xFFFF2D55;
            eshots.add(p);
            ring(b.x, b.y, 2, HS * 0.9f, 0.25f, 0xFFFF2D55);
        }
    }

    void fightBoss() {
        if (!bossBlocked || daily) return;
        bossBlocked = false;
        sector = (sector / 10 + 1) * 10;
        newPlanet();
        bossIntro();
    }

    private void bossIntro() {
        int bt = planet.bossType;
        showBanner(Planet.BOSS_NAME[bt] + " - " + Planet.BOSS_DESC[bt], 0xFFFF2D55, 4f);
        sfx(S_BOSS, 1f);
        buzz(120);
        flash(0.35f, 0xFFFF2D55);
    }

    private void updateShip(float dt) {
        float speed = 170f * (float) speedMul;
        float tvx = 0, tvy = 0;
        if (stunT > 0) {
            // drift helplessly
        } else if (joyActive) {
            tvx = joyX * speed;
            tvy = joyY * speed;
        } else if (auto && nearest != null) {
            float dx = shipX - nearest.x, dy = shipY - nearest.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy) + 0.001f;
            float stand = 58f;
            // Stand off from the target and drift sideways so the ship circles it.
            float px = -dy / d, py = dx / d;
            float wob = (float) Math.sin(time * 0.7f) * 0.9f;
            float gx = nearest.x + dx / d * stand + px * 30 * wob;
            float gy = nearest.y + dy / d * stand + py * 30 * wob;
            float ex = gx - shipX, ey = gy - shipY;
            float el = (float) Math.sqrt(ex * ex + ey * ey) + 0.001f;
            float s = Math.min(speed, el * 3f);
            tvx = ex / el * s;
            tvy = ey / el * s;
        }
        float k = Math.min(1, dt * (stunT > 0 ? 1 : 6));
        shipVX += (tvx - shipVX) * k;
        shipVY += (tvy - shipVY) * k;
        shipX += shipVX * dt;
        shipY += shipVY * dt;

        // The ship can only fly through mined-out space.
        getNear(shipX, shipY, HS * 2.2f, tmp);
        float minD = HS * 0.9f + SHIP_R;
        for (int i = 0; i < tmp.size(); i++) {
            Block b = tmp.get(i);
            float dx = shipX - b.x, dy = shipY - b.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < minD) {
                if (d < 0.01f) { dx = 1; dy = 0; d = 1; }
                float push = minD - d;
                shipX += dx / d * push;
                shipY += dy / d * push;
                float vn = (shipVX * dx + shipVY * dy) / d;
                if (vn < 0) { shipVX -= vn * dx / d; shipVY -= vn * dy / d; }
            }
        }
        float lim = planet.worldR + 400;
        float cd = (float) Math.hypot(shipX, shipY);
        if (cd > lim) { shipX *= lim / cd; shipY *= lim / cd; }

        float aimA;
        if (nearest != null && nearestD < 420) aimA = (float) Math.atan2(nearest.y - shipY, nearest.x - shipX);
        else if (Math.abs(shipVX) + Math.abs(shipVY) > 5) aimA = (float) Math.atan2(shipVY, shipVX);
        else aimA = shipAng;
        if (stunT > 0) aimA = shipAng + dt * 9;
        float da = aimA - shipAng;
        while (da > Math.PI) da -= 6.2831853f;
        while (da < -Math.PI) da += 6.2831853f;
        shipAng += da * Math.min(1, dt * 10);

        float sp = (float) Math.hypot(shipVX, shipVY);
        if (sp > 30 && parts.size() < partCap()) {
            float bx = shipX - (float) Math.cos(shipAng) * 12, by = shipY - (float) Math.sin(shipAng) * 12;
            spawnPart(bx, by, -shipVX * 0.2f + rnd.nextFloat() * 20 - 10, -shipVY * 0.2f + rnd.nextFloat() * 20 - 10,
                    0.35f, 2.5f, 0xFFFF9F43, P_SHARD);
        }
    }

    private void findNearest() {
        nearest = null;
        Block node = null;
        float best = Float.MAX_VALUE, bestNode = Float.MAX_VALUE;
        ArrayList<Block> a = planet.alive;
        boolean nodes = planet.nodesLeft > 0;
        for (int i = 0, n = a.size(); i < n; i++) {
            Block b = a.get(i);
            float dx = b.x - shipX, dy = b.y - shipY;
            float d = dx * dx + dy * dy;
            if (d < best) { best = d; nearest = b; }
            if (nodes && b.type == Block.NODE && d < bestNode) { bestNode = d; node = b; }
        }
        // While shield nodes stand, the autopilot hunts them first.
        if (node != null && auto && !joyActive) { nearest = node; best = bestNode; }
        nearestD = nearest == null ? Float.MAX_VALUE : (float) Math.sqrt(best);
    }

    /** Nearest alive block to a point; type < 0 means any type. */
    Block nearestTo(float x, float y, int type) {
        Block best = null;
        float bd = Float.MAX_VALUE;
        ArrayList<Block> a = planet.alive;
        for (int i = 0, n = a.size(); i < n; i++) {
            Block b = a.get(i);
            if (type >= 0 && b.type != type) continue;
            float dx = b.x - x, dy = b.y - y;
            float d = dx * dx + dy * dy;
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    /** Alive blocks whose centre is within rad of (x,y). */
    void getNear(float x, float y, float rad, ArrayList<Block> out) {
        out.clear();
        float fq = (SQ3 / 3f * x - y / 3f) / HS, fr = (2f / 3f * y) / HS;
        int cq = Math.round(fq), cr = Math.round(fr);
        int hr = (int) Math.ceil(rad / (HS * 1.5f)) + 1;
        float r2 = (rad + HS * 0.5f) * (rad + HS * 0.5f);
        for (int dr = -hr; dr <= hr; dr++) {
            for (int dq = -hr; dq <= hr; dq++) {
                Block b = planet.get(cq + dq, cr + dr);
                if (b == null || !b.alive) continue;
                float dx = b.x - x, dy = b.y - y;
                if (dx * dx + dy * dy <= r2) out.add(b);
            }
        }
    }

    // =====================================================================
    private void updateWeapons(float dt) {
        lanceOn = false;
        Block t = nearest;
        for (int w = 0; w < Weapons.COUNT; w++) {
            if (wLvl[w] < 0) continue;
            if (mutator == Mutators.BLASTER && w > Weapons.LANCE) continue;
            switch (w) {
                case Weapons.LANCE:
                    if (t != null && nearestD < Weapons.RANGE[w] * rangeMul()) {
                        lanceOn = true;
                        lanceX = t.x;
                        lanceY = t.y;
                        damage(t, weaponDmg(w) * dt * frMul, false, SRC_LANCE);
                        if (rnd.nextFloat() < dt * 30) spark(t.x, t.y, Weapons.COLOR[w], 2);
                    }
                    continue;
                case Weapons.SAWS: {
                    boolean grind = syn[Synergies.GRIND] && !sings.isEmpty();
                    sawAng += dt * (grind ? 10f : 5f);
                    double sd = weaponDmg(w) * dt * frMul * (grind ? 3 : 1);
                    float sr = sawRadius();
                    for (int s = 0; s < 2; s++) {
                        float a = sawAng + s * 3.14159f;
                        float sx = shipX + (float) Math.cos(a) * sr, sy = shipY + (float) Math.sin(a) * sr;
                        getNear(sx, sy, HS * 0.8f, tmp);
                        for (int i = 0; i < tmp.size(); i++) {
                            damage(tmp.get(i), sd, false, SRC_HIT);
                            if (rnd.nextFloat() < dt * 14) spark(sx, sy, Weapons.COLOR[w], 2);
                        }
                    }
                    continue;
                }
            }
            wTimer[w] -= dt * (float) frMul;
            if (wTimer[w] > 0) continue;
            if (t == null) { wTimer[w] = 0; continue; }
            boolean fired = fire(w, t);
            if (fired) wTimer[w] += Weapons.CD[w];
            else wTimer[w] = 0;
        }
    }

    private void recoil(float ang, float amt) {
        shipVX -= (float) Math.cos(ang) * amt;
        shipVY -= (float) Math.sin(ang) * amt;
    }

    private boolean fire(int w, Block t) {
        float range = Weapons.RANGE[w] * rangeMul();
        double dmg = weaponDmg(w);
        int col = Weapons.COLOR[w];
        float ang = (float) Math.atan2(t.y - shipY, t.x - shipX);
        float mx = shipX + (float) Math.cos(shipAng) * 14, my = shipY + (float) Math.sin(shipAng) * 14;
        switch (w) {
            case Weapons.BLASTER:
                if (nearestD > range) return false;
                bolt(mx, my, ang + (rnd.nextFloat() - 0.5f) * 0.08f, 760, range + 40, dmg, col, t);
                sfx(S_SHOOT, 0.25f);
                muzzle(col);
                return true;
            case Weapons.SCATTER:
                if (nearestD > range) return false;
                for (int i = -2; i <= 2; i++) bolt(mx, my, ang + i * 0.16f, 620, range, dmg, col, t);
                sfx(S_SHOOT, 0.35f);
                muzzle(col);
                recoil(ang, 40);
                return true;
            case Weapons.MORTAR: {
                Block tgt = randomAliveWithin(range);
                if (tgt == null) return false;
                for (int k = 0; k <= runCard[Cards.MORTAR]; k++) {
                    if (k > 0) {
                        Block extra = randomAliveWithin(range);
                        if (extra != null) tgt = extra;
                    }
                    Proj p = new Proj();
                    p.kind = Proj.LOB;
                    p.sx = p.x = mx; p.sy = p.y = my;
                    p.tx = tgt.x; p.ty = tgt.y;
                    p.total = p.life = 0.8f + k * 0.12f;
                    p.dmg = dmg;
                    p.aoe = HS * 1.9f;
                    p.color = col;
                    projs.add(p);
                }
                muzzle(col);
                recoil(ang, 30);
                return true;
            }
            case Weapons.CHAIN:
                if (nearestD > range) return false;
                chainArc(mx, my, t, dmg, col);
                return true;
            case Weapons.SWARM:
                if (nearestD > range) return false;
                for (int i = 0; i < 4; i++) {
                    Proj p = new Proj();
                    p.kind = Proj.MISSILE;
                    float a = shipAng + (i - 1.5f) * 0.9f;
                    p.x = shipX; p.y = shipY;
                    p.vx = (float) Math.cos(a) * 260;
                    p.vy = (float) Math.sin(a) * 260;
                    p.life = 3f;
                    p.dmg = dmg;
                    p.aoe = HS * 1.2f;
                    p.color = col;
                    p.target = t;
                    projs.add(p);
                }
                return true;
            case Weapons.RAIL: {
                if (nearestD > range) return false;
                float cx = (float) Math.cos(ang), cy = (float) Math.sin(ang);
                Block last = null, lastHit = null;
                int hits = 0;
                for (float d = 0; d < range; d += HS * 0.4f) {
                    Block b = planet.at(mx + cx * d, my + cy * d);
                    if (b != null && b.alive && b != last) {
                        last = b;
                        lastHit = b;
                        damage(b, dmg, true, SRC_HIT);
                        hits++;
                        if (!lowFx) spark(b.x, b.y, col, 3);
                    }
                }
                Beam bm = new Beam();
                bm.pts = new float[]{mx, my, mx + cx * range, my + cy * range};
                bm.life = bm.max = 0.35f;
                bm.width = 7f;
                bm.color = col;
                beams.add(bm);
                shake = Math.max(shake, 6);
                sfx(S_ZAP, 0.7f);
                muzzle(col);
                recoil(ang, 120);
                buzz(12);
                if (syn[Synergies.CONDUCTOR] && lastHit != null) {
                    Block start = lastHit.alive ? lastHit : nearestTo(lastHit.x, lastHit.y, -1);
                    if (start != null) chainArc(lastHit.x, lastHit.y, start, weaponDmg(Weapons.CHAIN), Weapons.COLOR[Weapons.CHAIN]);
                }
                return hits > 0;
            }
            case Weapons.NOVA: {
                float rad = HS * 5f;
                if (nearestD > rad) return false;
                getNear(shipX, shipY, rad, tmp);
                ArrayList<Block> hit = new ArrayList<>(tmp);
                for (int i = 0; i < hit.size(); i++) damage(hit.get(i), dmg, true, SRC_NOVA);
                ring(shipX, shipY, 10, rad, 0.45f, col);
                ringFill(shipX, shipY, rad * 0.6f, 0.2f, col);
                shake = Math.max(shake, 6);
                flash(0.12f, col);
                sfx(S_BOOM, 0.4f);
                return true;
            }
            case Weapons.SINGULARITY: {
                if (nearestD > range) return false;
                Sing s = new Sing();
                s.x = t.x; s.y = t.y;
                s.life = 3f;
                s.dps = dmg;
                sings.add(s);
                return true;
            }
        }
        return false;
    }

    private void muzzle(int col) {
        muzzle = 0.07f;
        muzzleColor = col;
    }

    private void chainArc(float mx, float my, Block t, double dmg, int col) {
        chainHit.clear();
        int jumps = 6 + runCard[Cards.CHAIN];
        float[] pts = new float[2 * (jumps + 2)];
        int np = 0;
        pts[np++] = mx; pts[np++] = my;
        Block cur = t;
        for (int j = 0; j < jumps && cur != null; j++) {
            chainHit.add(cur);
            pts[np++] = cur.x; pts[np++] = cur.y;
            float cx = cur.x, cy = cur.y;
            damage(cur, dmg, true, SRC_CHAIN);
            getNear(cx, cy, HS * 3.2f, tmp);
            Block nx = null;
            float bd = Float.MAX_VALUE;
            for (int i = 0; i < tmp.size(); i++) {
                Block b = tmp.get(i);
                if (chainHit.contains(b)) continue;
                float d = (b.x - cx) * (b.x - cx) + (b.y - cy) * (b.y - cy);
                if (d < bd) { bd = d; nx = b; }
            }
            cur = nx;
        }
        Beam bm = new Beam();
        float[] p2 = new float[np];
        System.arraycopy(pts, 0, p2, 0, np);
        bm.pts = jag(p2);
        bm.life = bm.max = 0.22f;
        bm.width = 3f;
        bm.color = col;
        beams.add(bm);
        sfx(S_ZAP, 0.4f);
    }

    private void bolt(float x, float y, float a, float speed, float range, double dmg, int col, Block target) {
        Proj p = new Proj();
        p.kind = Proj.BOLT;
        p.x = x; p.y = y;
        p.vx = (float) Math.cos(a) * speed;
        p.vy = (float) Math.sin(a) * speed;
        p.life = range / speed;
        p.dmg = dmg;
        p.color = col;
        p.target = target;
        projs.add(p);
    }

    private Block randomAliveWithin(float range) {
        ArrayList<Block> a = planet.alive;
        if (a.isEmpty()) return null;
        float r2 = range * range;
        for (int i = 0; i < 12; i++) {
            Block b = a.get(rnd.nextInt(a.size()));
            float dx = b.x - shipX, dy = b.y - shipY;
            if (dx * dx + dy * dy < r2) return b;
        }
        return nearestD < range ? nearest : null;
    }

    private float[] jag(float[] p) {
        int segs = p.length / 2 - 1;
        float[] o = new float[(segs * 3 + 1) * 2];
        int k = 0;
        for (int i = 0; i < segs; i++) {
            float x0 = p[i * 2], y0 = p[i * 2 + 1], x1 = p[i * 2 + 2], y1 = p[i * 2 + 3];
            float nx = -(y1 - y0) * 0.18f, ny = (x1 - x0) * 0.18f;
            o[k++] = x0; o[k++] = y0;
            for (int j = 1; j <= 2; j++) {
                float f = j / 3f, jit = rnd.nextFloat() - 0.5f;
                o[k++] = x0 + (x1 - x0) * f + nx * jit;
                o[k++] = y0 + (y1 - y0) * f + ny * jit;
            }
        }
        o[k++] = p[p.length - 2];
        o[k] = p[p.length - 1];
        return o;
    }

    // =====================================================================
    private void updateDrones(float dt) {
        int slot = 0;
        for (int i = 0; i < Drones.COUNT; i++) {
            if (dLvl[i] < 0) { drones[i] = null; continue; }
            Drone d = drones[i];
            if (d == null) {
                d = drones[i] = new Drone();
                d.x = shipX;
                d.y = shipY;
            }
            slot++;
            d.retarget -= dt;
            if (d.target == null || !d.target.alive || d.retarget <= 0) {
                d.target = nearestTo(d.x, d.y, Drones.TARGET[i]);
                if (d.target == null) d.target = nearest;
                d.retarget = 1f;
            }
            float tx, ty;
            float oa = time * 1.3f + slot * 1.57f;
            if (d.target != null) {
                tx = d.target.x + (float) Math.cos(oa) * 26;
                ty = d.target.y + (float) Math.sin(oa) * 26;
            } else {
                tx = shipX + (float) Math.cos(oa) * 40;
                ty = shipY + (float) Math.sin(oa) * 40;
            }
            float ex = tx - d.x, ey = ty - d.y, el = (float) Math.sqrt(ex * ex + ey * ey) + 0.001f;
            float sp = Math.min(260f * (float) speedMul, el * 4);
            d.x += ex / el * sp * dt;
            d.y += ey / el * sp * dt;
            d.cd -= dt * (float) frMul;
            if (d.target != null && d.cd <= 0) {
                float dx = d.target.x - d.x, dy = d.target.y - d.y;
                if (dx * dx + dy * dy < 70 * 70) {
                    Block tg = d.target;
                    Beam bm = new Beam();
                    bm.pts = new float[]{d.x, d.y, tg.x, tg.y};
                    bm.life = bm.max = 0.12f;
                    bm.width = 2f;
                    bm.color = Drones.COLOR[i];
                    beams.add(bm);
                    damage(tg, droneDmg(i), true, SRC_HIT);
                    d.cd = 0.45f;
                    sfx(S_ZAP, 0.15f);
                }
            }
        }
    }

    private void updateProjectiles(float dt) {
        for (int i = projs.size() - 1; i >= 0; i--) {
            if (stepProj(projs.get(i), dt)) {
                int last = projs.size() - 1;
                projs.set(i, projs.get(last));
                projs.remove(last);
            }
        }
        for (int i = eshots.size() - 1; i >= 0; i--) {
            Proj p = eshots.get(i);
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float dx = p.x - shipX, dy = p.y - shipY;
            if (dx * dx + dy * dy < (SHIP_R + 7) * (SHIP_R + 7)) {
                eshots.remove(i);
                burst(p.x, p.y, 0xFFFF2D55, 10);
                if (hull != Hulls.BASTION) {
                    stunT = 1.1f;
                    shake = Math.max(shake, 9);
                    flash(0.35f, 0xFFFF2D55);
                    text(shipX, shipY - 20, "STUNNED", 0xFFFF2D55, true);
                    buzz(90);
                    sfx(S_BOOM, 0.6f);
                } else text(shipX, shipY - 20, "DEFLECTED", 0xFFFF5E7E, false);
                continue;
            }
            if (p.life <= 0) eshots.remove(i);
        }
        for (int i = sings.size() - 1; i >= 0; i--) {
            Sing s = sings.get(i);
            s.life -= dt;
            getNear(s.x, s.y, HS * 2.6f, tmp);
            for (int j = 0; j < tmp.size(); j++) damage(tmp.get(j), s.dps * dt, false, SRC_HIT);
            if (rnd.nextFloat() < dt * 30 && parts.size() < partCap()) {
                float a = rnd.nextFloat() * 6.283f, r = 50 + rnd.nextFloat() * 20;
                spawnPart(s.x + (float) Math.cos(a) * r, s.y + (float) Math.sin(a) * r,
                        -(float) Math.cos(a) * r * 2.5f, -(float) Math.sin(a) * r * 2.5f, 0.4f, 2.5f, 0xFFB04DFF, P_SPARK);
            }
            if (s.life <= 0) sings.remove(i);
        }
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            z.life -= dt;
            getNear(z.x, z.y, z.r, tmp);
            for (int j = 0; j < tmp.size(); j++) damage(tmp.get(j), z.dps * dt, false, SRC_HIT);
            if (rnd.nextFloat() < dt * 20 && parts.size() < partCap()) {
                float a = rnd.nextFloat() * 6.283f, r = rnd.nextFloat() * z.r;
                spawnPart(z.x + (float) Math.cos(a) * r, z.y + (float) Math.sin(a) * r, 0, -40, 0.5f, 3f,
                        rnd.nextBoolean() ? 0xFFFF9F43 : 0xFFFF5E3A, P_SHARD);
            }
            if (z.life <= 0) zones.remove(i);
        }
    }

    private boolean stepProj(Proj p, float dt) {
        p.life -= dt;
        if (p.kind == Proj.LOB) {
            float f = 1 - Math.max(0, p.life) / p.total;
            p.x = p.sx + (p.tx - p.sx) * f;
            p.y = p.sy + (p.ty - p.sy) * f;
            if (p.life <= 0) {
                aoe(p.tx, p.ty, p.aoe, p.dmg);
                ring(p.tx, p.ty, 4, p.aoe, 0.3f, p.color);
                ringFill(p.tx, p.ty, p.aoe * 0.8f, 0.15f, p.color);
                burst(p.tx, p.ty, p.color, 10);
                smoke(p.tx, p.ty, 3);
                shake = Math.max(shake, 4);
                sfx(S_BOOM, 0.3f);
                if (syn[Synergies.NAPALM]) {
                    Zone z = new Zone();
                    z.x = p.tx; z.y = p.ty; z.r = p.aoe;
                    z.life = z.max = 2.5f;
                    z.dps = p.dmg * 0.3;
                    zones.add(z);
                }
                return true;
            }
            return false;
        }
        if (p.life <= 0) return true;
        if (p.kind == Proj.MISSILE || (planet.mod == Planet.MOD_MAGNET && p.target != null)) {
            if (p.kind == Proj.MISSILE && (p.target == null || !p.target.alive)) p.target = nearestTo(p.x, p.y, -1);
            if (p.target != null && p.target.alive) {
                float sp = (float) Math.hypot(p.vx, p.vy);
                if (p.kind == Proj.MISSILE) sp = Math.min(520, sp + dt * 500);
                float cur = (float) Math.atan2(p.vy, p.vx);
                float want = (float) Math.atan2(p.target.y - p.y, p.target.x - p.x);
                float da = want - cur;
                while (da > Math.PI) da -= 6.2831853f;
                while (da < -Math.PI) da += 6.2831853f;
                float turn = p.kind == Proj.MISSILE ? 7 : 5;
                cur += Math.max(-dt * turn, Math.min(dt * turn, da));
                p.vx = (float) Math.cos(cur) * sp;
                p.vy = (float) Math.sin(cur) * sp;
            }
            if (p.kind == Proj.MISSILE && rnd.nextFloat() < 0.6f && parts.size() < partCap())
                spawnPart(p.x, p.y, 0, 0, 0.3f, 2.5f, p.color, P_SMOKE);
        }
        float step = (float) Math.hypot(p.vx, p.vy) * dt;
        int n = Math.max(1, (int) Math.ceil(step / (HS * 0.5f)));
        for (int k = 0; k < n; k++) {
            p.x += p.vx * dt / n;
            p.y += p.vy * dt / n;
            Block b = planet.at(p.x, p.y);
            if (b != null && b.alive) {
                if (p.aoe > 0) {
                    aoe(p.x, p.y, p.aoe, p.dmg);
                    ring(p.x, p.y, 4, p.aoe, 0.25f, p.color);
                    ringFill(p.x, p.y, p.aoe * 0.6f, 0.12f, p.color);
                } else damage(b, p.dmg, true, SRC_HIT);
                spark(p.x, p.y, p.color, 3);
                return true;
            }
        }
        return false;
    }

    private void aoe(float x, float y, float rad, double dmg) {
        getNear(x, y, rad, tmp);
        ArrayList<Block> hit = new ArrayList<>(tmp);
        for (int i = 0; i < hit.size(); i++) damage(hit.get(i), dmg, true, SRC_HIT);
    }

    private void updateExplosions(float dt) {
        for (int i = expls.size() - 1; i >= 0; i--) {
            Expl e = expls.get(i);
            e.delay -= dt;
            if (e.delay > 0) continue;
            expls.remove(i);
            if (e.cosmetic) {
                burst(e.x, e.y, 0xFFFF3EA5, 8);
                ring(e.x, e.y, 4, HS * 2, 0.35f, 0xFFFF3EA5);
                ringFill(e.x, e.y, HS * 1.5f, 0.15f, 0xFFFFFFFF);
                continue;
            }
            curChain++;
            if (curChain > bestChain) bestChain = curChain;
            if (curChain >= 5 && curChain % 5 == 0) text(e.x, e.y - 20, "CHAIN x" + curChain, 0xFFFF6A3D, true);
            float rad = HS * SQ3 * (1.25f + (float) sk[Skills.VOLATILE] * 0.5f + (e.big ? 1f : 0f));
            getNear(e.x, e.y, rad, tmp);
            ArrayList<Block> hit = new ArrayList<>(tmp);
            for (int j = 0; j < hit.size(); j++) damage(hit.get(j), e.dmg, false, SRC_HIT);
            int ringCol = cosBoom == 12 ? 0xFF7DF9FF : e.big ? 0xFFFFE066 : 0xFFFF2D55;
            ring(e.x, e.y, 6, rad * 1.2f, 0.4f, ringCol);
            ringFill(e.x, e.y, rad * 0.9f, 0.18f, cosBoom == 12 ? 0xFFFFFFFF : 0xFFFFF2C0);
            if (cosBoom == 13) {
                for (int k = 0; k < 4; k++) burst(e.x, e.y, CONFETTI[rnd.nextInt(CONFETTI.length)], 5);
            } else burst(e.x, e.y, cosBoom == 12 ? 0xFF9BF6FF : 0xFFFF6A3D, 18);
            smoke(e.x, e.y, 5);
            shake = Math.max(shake, e.big ? 12 : 8);
            if (e.big) { hitStop(0.06f); flash(0.25f, 0xFFFFE066); }
            else hitStop(0.025f);
            sfx(S_BOOM, e.big ? 1f : 0.7f);
            buzz(25);
        }
        if (expls.isEmpty()) curChain = 0;
    }

    void damage(Block b, double amt, boolean canCrit, int src) {
        if (!b.alive) return;
        boolean crit = false;
        if (canCrit && rnd.nextFloat() < critChance) {
            amt *= critDmg;
            crit = true;
        }
        boolean shatter = src == SRC_LANCE && syn[Synergies.SHATTER];
        if (b.type == Block.ARMOR && !shatter) amt *= 0.35;
        if (planet.nodesLeft > 0 && b.type != Block.NODE) amt *= 0.1;
        if (b.frost > 0) {
            if (shatter) b.frost = 0;
            else {
                double use = Math.min(b.frost, amt * 0.5);
                b.frost -= use;
                amt -= use * 2;
            }
            if (b.frost <= 0) spark(b.x, b.y, 0xFFE0F7FF, 6);
            if (amt <= 0) { b.flash = 0.04f; b.punch = 0.6f; return; }
        }
        if (b.type == Block.EXPLOSIVE) {
            if (src == SRC_CHAIN && syn[Synergies.OVERLOAD]) b.overload = true;
            if (src == SRC_NOVA && syn[Synergies.DETONATOR]) amt = b.hp + 1;
        }
        if (crit && rnd.nextFloat() < 0.35f) text(b.x, b.y - 10, Fmt.n(amt), 0xFFFFE066, true);
        dmgThisSec += amt;
        b.hp -= amt;
        b.flash = 0.07f;
        b.punch = 1;
        if (b.hp <= 0) kill(b);
    }

    private void kill(Block b) {
        planet.remove(b);
        b.deadT = time;
        totalBlocks++;
        seenBlocks |= 1 << b.type;
        combo++;
        comboT = COMBO_WINDOW;
        comboPunch = 1;
        if (combo > bestCombo) bestCombo = combo;
        if (combo % 50 == 0) {
            text(shipX, shipY - 30, combo + " COMBO!", 0xFFFF7AD9, true);
            buzz(20);
        }
        double mult = b.type == Block.GOLD ? 12 * (1 + 0.5 * runCard[Cards.GOLDV]) : b.type == Block.VEIN ? 25 : b.type == Block.HEART ? 5
                : b.type == Block.NODE || b.type == Block.TURRET ? 8 : 1;
        if (planet.mod == Planet.MOD_FROZEN) mult *= 1.4;
        double c = b.maxHp * 0.3 * coinMul * mult * comboMul();
        coins += c;
        secCoins += c;
        totalEarned += c;
        int col = Render.blockColor(planet.pal, b);
        burst(b.x, b.y, col, b.type == Block.NORMAL ? 6 : 12);
        sfxR(S_BREAK, 0.2f, 0.85f + Math.min(combo, 80) / 80f * 0.9f);
        if (b.type != Block.NORMAL || rnd.nextFloat() < 0.22f) coinEvent(b.x, b.y, 0);
        if (b.type == Block.GOLD) {
            goldMined++;
            text(b.x, b.y, "+" + Fmt.n(c), 0xFFFFD84A, false);
            sfx(S_COIN, 0.35f);
            for (int i = 0; i < 3; i++) coinEvent(b.x, b.y, 0);
        }
        if (b.type == Block.ENERGY) {
            double e = 1 + sk[Skills.SIPHON] + 0.5 * resLvl[Research.CELLS];
            energy += e;
            text(b.x, b.y, "+" + Fmt.n(e) + " EN", 0xFF9BF6FF, false);
            sfx(S_COIN, 0.25f);
            coinEvent(b.x, b.y, 1);
        }
        if (b.type == Block.VEIN && b.vein >= 0) {
            text(b.x, b.y, "+" + Fmt.n(c), 0xFFFFFFFF, false);
            if (--planet.veinLeft[b.vein] == 0) {
                veinsDone++;
                energy += 3;
                scrap += 5;
                showBanner("ORE VEIN COMPLETE  +3 EN  +5 SCRAP", 0xFFFFFFFF, 2.2f);
                sfx(S_COIN, 0.8f);
                for (int i = 0; i < 6; i++) coinEvent(b.x, b.y, i % 2);
            }
        }
        if (b.type == Block.NODE) {
            ring(b.x, b.y, 6, HS * 4, 0.5f, 0xFF00E0A0);
            shake = Math.max(shake, 8);
            hitStop(0.05f);
            if (planet.nodesLeft == 0) {
                showBanner("SHIELD DOWN - full damage!", 0xFF00E0A0, 2.5f);
                flash(0.4f, 0xFF00E0A0);
                sfx(S_CLEAR, 0.7f);
            } else text(b.x, b.y - 20, planet.nodesLeft + " NODES LEFT", 0xFF00E0A0, true);
        }
        if (b.type == Block.TURRET) {
            ring(b.x, b.y, 6, HS * 3, 0.4f, 0xFFF0E442);
            smoke(b.x, b.y, 4);
            shake = Math.max(shake, 6);
        }
        if (b.type == Block.HEART) heartKilled(b);
        if (b.type == Block.EXPLOSIVE) {
            if (runCard[Cards.REDEN] > 0) energy += 0.5 * runCard[Cards.REDEN];
            Expl e = new Expl();
            e.x = b.x; e.y = b.y;
            e.delay = 0.12f;
            e.big = b.overload;
            e.dmg = (b.maxHp * 6 * (1 + sk[Skills.VOLATILE]) + planet.baseHp * 3) * (b.overload ? 2 : 1);
            expls.add(e);
        }
        double tr = sk[Skills.ALCHEMY];
        if (tr > 0 && rnd.nextFloat() < tr) {
            getNear(b.x, b.y, HS * 2f, tmpT);
            for (int i = 0; i < tmpT.size(); i++) {
                Block n = tmpT.get(i);
                if (n.type == Block.NORMAL) {
                    n.type = Block.GOLD;
                    n.flash = 0.3f;
                    ring(n.x, n.y, 2, HS, 0.3f, 0xFFFFD84A);
                    break;
                }
            }
        }
    }

    private void heartKilled(Block b) {
        ring(b.x, b.y, 10, HS * 9, 0.9f, 0xFFFF3EA5);
        ringFill(b.x, b.y, HS * 5, 0.3f, 0xFFFFFFFF);
        burst(b.x, b.y, 0xFFFF3EA5, 40);
        smoke(b.x, b.y, 10);
        shake = 16;
        hitStop(0.22f);
        slowT = 1.0f;
        flash(0.8f, 0xFFFF3EA5);
        buzz(160);
        sfx(S_BOOM, 1f);
        // Splitter: the first heart cracks the world in two instead of ending the fight.
        if (planet.bossType == Planet.BOSS_SPLITTER && !planet.twin && !daily) {
            // Swapping planets mid-update would invalidate lists being iterated; do it next frame.
            pendingSplit = true;
            return;
        }
        // The heart takes its own lobe with it.
        ArrayList<Block> rest = new ArrayList<>(planet.alive);
        for (Block r : rest) {
            if (!r.alive || r.lobe != b.lobe) continue;
            if (rnd.nextFloat() < 0.15f) {
                Expl e = new Expl();
                e.x = r.x; e.y = r.y;
                e.delay = 0.05f + rnd.nextFloat() * 0.6f;
                e.cosmetic = true;
                expls.add(e);
            }
            r.frost = 0;
            r.hp = 0;
            if (r.type == Block.HEART) continue;
            kill(r);
        }
    }

    private void doSplit() {
        pendingSplit = false;
        twinPhase = true;
        planet = new Planet(sector, seed + 1, null, false, true);
        clearTransient();
        placeShip();
        bossTime += 45;
        regenT = 2.5f;
        warpT = 0.6f;
        showBanner("THE PLANET SPLITS!  +45s", 0xFFFF3EA5, 3f);
        sfx(S_BOSS, 1f);
    }

    private static final int[] CONFETTI = {0xFFFF3EA5, 0xFF19E3C9, 0xFFFFD84A, 0xFF8A5CFF, 0xFFA6FF4D};

    private double comboMul() { return 1 + Math.min(combo, 200) * 0.0025; }

    private void coinEvent(float x, float y, int type) {
        if (coinEvents.size() < 40 && !lowFx) coinEvents.add(new float[]{x, y, type});
    }

    private void planetCleared() {
        clearT = 2.8f;
        sfx(S_CLEAR, 1f);
        buzz(60);
        slowT = 0.7f;
        flash(0.45f, 0xFF5CFFB0);
        if (daily) {
            if (sideMode == SIDE_DEEP) deepCleared();
            else if (sideMode == SIDE_WEEKLY) weeklyCleared();
            else dailyCleared();
            return;
        }
        planetsCleared++;
        pendingPicks += planet.boss ? 2 : 1;
        if (planet.all.size() >= 500 && (fastestClear == 0 || planetTime < fastestClear)) fastestClear = planetTime;
        double bonus = planet.baseHp * 40 * coinMul * (planet.boss ? 5 : 1);
        double en = 3 + sector / 3 + (planet.boss ? 20 : 0);
        coins += bonus;
        totalEarned += bonus;
        energy += en;
        String drop = "";
        if (gearInv.size() < Gear.INV_CAP) {
            Gear.Item it = Gear.roll(rnd, sector);
            if (planet.boss) it.rar = Math.max(it.rar, 2);
            gearInv.add(it);
            drop = " + " + Gear.name(it);
        }
        if (planet.boss) {
            bossesKilled++;
            int h = rollHull();
            if (h > 0) drop += " + HULL: " + Hulls.NAME[h];
        }
        showBanner((planet.boss ? "BOSS DEFEATED" : "PLANET CLEARED") + "  +" + Fmt.n(bonus) + "  +" + Fmt.n(en) + " EN" + drop,
                0xFF5CFFB0, 3.5f);
        ring(0, 0, 10, planet.worldR * 1.4f, 1.2f, 0xFF5CFFB0);
        for (int i = 0; i < 10; i++) coinEvent(shipX, shipY, 0);
    }

    /** Boss reward: the first boss always gives a hull, later ones 50% while any are missing. */
    private int rollHull() {
        ArrayList<Integer> missing = new ArrayList<>();
        for (int h = 1; h < Hulls.COUNT; h++) if (!hasHull(h)) missing.add(h);
        if (missing.isEmpty() || (bossesKilled > 1 && rnd.nextFloat() > 0.5f)) return 0;
        int h = missing.get(rnd.nextInt(missing.size()));
        hullsOwned |= 1 << h;
        return h;
    }

    private void nextSector() {
        if (!(bossBlocked && (sector + 1) % 10 == 0)) sector++;
        runBest = Math.max(runBest, sector);
        bestSector = Math.max(bestSector, sector);
        newPlanet();
        if (planet.boss) bossIntro();
        else {
            String m = planet.mod != Planet.MOD_NONE ? "  [" + Planet.MOD_NAME[planet.mod] + "]" : "";
            showBanner("SECTOR " + sector + " - " + planet.name + m, Render.PALETTES[planet.pal][0], 2.5f);
        }
    }

    // ---------------- active ability ----------------
    void useNova() {
        if (novaCd > 0 || clearT >= 0 || stunT > 0) return;
        novaCd = novaMax();
        float rad = HS * 7;
        double dmg = Math.max(estDps() * 4, weaponDmg(Weapons.BLASTER) * 60);
        getNear(shipX, shipY, rad, tmp);
        ArrayList<Block> hit = new ArrayList<>(tmp);
        for (int i = 0; i < hit.size(); i++) damage(hit.get(i), dmg, true, SRC_NOVA);
        ringFill(shipX, shipY, rad, 0.35f, 0xFFFFFFFF);
        ring(shipX, shipY, 10, rad * 1.3f, 0.7f, 0xFFC58BFF);
        ring(shipX, shipY, 10, rad * 0.8f, 0.5f, 0xFF9BF6FF);
        burst(shipX, shipY, 0xFFC58BFF, 30);
        smoke(shipX, shipY, 8);
        shake = 15;
        hitStop(0.1f);
        flash(0.5f, 0xFFC58BFF);
        sfx(S_BOOM, 1f);
        buzz(120);
    }

    // ---------------- run cards ----------------
    void openPicks() {
        if (pendingPicks <= 0 || offer != null) return;
        ArrayList<Integer> pool = new ArrayList<>();
        for (int i = 0; i < Cards.COUNT; i++) if (runCard[i] < Cards.MAX[i]) pool.add(i);
        if (pool.isEmpty()) { pendingPicks = 0; return; }
        Collections.shuffle(pool, rnd);
        int n = Math.min(3, pool.size());
        offer = new int[n];
        for (int i = 0; i < n; i++) offer[i] = pool.get(i);
    }

    void pickCard(int i) {
        if (offer == null || i < 0 || i >= offer.length) return;
        int c = offer[i];
        runCard[c]++;
        pendingPicks--;
        offer = null;
        showBanner(Cards.NAME[c] + "  " + Cards.DESC[c], Cards.COLOR[c], 2f);
        sfx(S_COIN, 0.6f);
    }

    // ---------------- cosmetics ----------------
    boolean cosUnlocked(int i) {
        int a = Cosmetics.ACH[i];
        return a == -1 || (a == -2 ? (cosWeekly & (1 << i)) != 0 : achDone(a));
    }

    void setCos(int i) {
        if (!cosUnlocked(i)) return;
        switch (Cosmetics.TYPE[i]) {
            case Cosmetics.TRAIL: cosTrail = i; break;
            case Cosmetics.PAINT: cosPaint = i; break;
            default: cosBoom = i;
        }
    }

    // ---------------- comets & boosts ----------------
    private void updateComets(float dt) {
        if (clearT < 0 && !daily) cometT -= dt;
        if (cometT <= 0) {
            cometT = 45 + rnd.nextFloat() * 50;
            boolean shower = rnd.nextFloat() < 0.3f;
            float a = rnd.nextFloat() * 6.283f;
            float dirX = (float) Math.cos(a), dirY = (float) Math.sin(a);
            int n = shower ? 6 : 1;
            for (int i = 0; i < n; i++) {
                Comet c = new Comet();
                c.meteor = shower;
                float spd = shower ? 150 + rnd.nextFloat() * 40 : 85;
                float side = (rnd.nextFloat() - 0.5f) * (shower ? 260 : 120);
                float back = 330 + i * 70;
                c.x = shipX - dirX * back - dirY * side;
                c.y = shipY - dirY * back + dirX * side;
                c.vx = dirX * spd;
                c.vy = dirY * spd;
                c.life = (back * 2) / spd;
                c.kind = rnd.nextInt(4);
                comets.add(c);
            }
            if (!shower) sfx(S_COIN, 0.5f);
        }
        for (int i = comets.size() - 1; i >= 0; i--) {
            Comet c = comets.get(i);
            c.x += c.vx * dt;
            c.y += c.vy * dt;
            c.life -= dt;
            if (!lowFx && parts.size() < partCap() && rnd.nextFloat() < (c.meteor ? 0.5f : 0.9f))
                spawnPart(c.x, c.y, -c.vx * 0.2f + rnd.nextFloat() * 16 - 8, -c.vy * 0.2f + rnd.nextFloat() * 16 - 8,
                        0.6f, c.meteor ? 2.5f : 4f, c.meteor ? 0xFFFFB547 : Boosts.COLOR[c.kind], P_SMOKE);
            if (c.life <= 0) comets.remove(i);
        }
    }

    /** Called by the UI with a tap converted to world coordinates. Returns true if something was caught. */
    boolean tapWorld(float wx, float wy) {
        for (int i = comets.size() - 1; i >= 0; i--) {
            Comet c = comets.get(i);
            float r = c.meteor ? 40 : 55;
            float dx = c.x - wx, dy = c.y - wy;
            if (dx * dx + dy * dy > r * r) continue;
            comets.remove(i);
            cometsCaught++;
            burst(c.x, c.y, c.meteor ? 0xFFFFB547 : Boosts.COLOR[c.kind], 20);
            ring(c.x, c.y, 4, 70, 0.4f, c.meteor ? 0xFFFFB547 : Boosts.COLOR[c.kind]);
            sfx(S_COIN, 0.9f);
            buzz(30);
            if (c.meteor) {
                double v = Math.max(cpsAvg * 8, planet.baseHp * 20 * coinMul);
                coins += v;
                totalEarned += v;
                text(c.x, c.y, "+" + Fmt.n(v), 0xFFFFD84A, true);
                for (int k = 0; k < 4; k++) coinEvent(c.x, c.y, 0);
            } else if (c.kind == Boosts.ENERGY) {
                double e = 5 + sector / 5;
                energy += e;
                text(c.x, c.y, "+" + Fmt.n(e) + " EN", 0xFF9BF6FF, true);
                for (int k = 0; k < 4; k++) coinEvent(c.x, c.y, 1);
            } else {
                boostT[c.kind] = Boosts.TIME[c.kind];
                showBanner(Boosts.NAME[c.kind] + "!  " + Boosts.DESC[c.kind] + " for " + (int) Boosts.TIME[c.kind] + "s",
                        Boosts.COLOR[c.kind], 2.5f);
                flash(0.25f, Boosts.COLOR[c.kind]);
            }
            return true;
        }
        return false;
    }

    // ---------------- research ----------------
    boolean researchUnlocked() { return bestSector >= Research.UNLOCK_SECTOR; }

    boolean researchAvailable(int i) {
        return Research.REQ[i] < 0 || resLvl[Research.REQ[i]] >= Research.REQ_LVL[i];
    }

    void startResearch(int i) {
        if (researching >= 0 || resLvl[i] >= Research.MAX[i] || !researchAvailable(i)) return;
        int c = Research.cost(i, resLvl[i]);
        if (energy < c) return;
        energy -= c;
        researching = i;
        researchEnd = System.currentTimeMillis() + Research.durationMs(i, resLvl[i]);
    }

    long researchLeftMs() { return researching < 0 ? 0 : Math.max(0, researchEnd - System.currentTimeMillis()); }

    void checkResearch() {
        if (researching >= 0 && System.currentTimeMillis() >= researchEnd) {
            int i = researching;
            resLvl[i]++;
            researching = -1;
            showBanner("RESEARCH COMPLETE: " + Research.NAME[i] + " Lv " + resLvl[i], 0xFF7DF9FF, 3f);
            sfx(S_CLEAR, 0.6f);
        }
    }

    // ---------------- auto-buy ----------------
    private void autoBuyStep() {
        if (pendingPicks > 0) {
            openPicks();
            if (offer != null) pickCard(rnd.nextInt(offer.length));
        }
        int saved = buyMode;
        buyMode = 0;
        for (int k = 0; k < 20; k++) {
            // next weapon unlock always comes first when affordable
            for (int w = 0; w < Weapons.COUNT; w++) {
                if (wLvl[w] < 0) {
                    if (coins >= Weapons.COST[w]) buyWeapon(w);
                    break;
                }
            }
            double best = Double.MAX_VALUE;
            int kind = -1, idx = -1;
            for (int w = 0; w < Weapons.COUNT; w++) {
                if (wLvl[w] < 0) continue;
                double c = Fmt.costN(Weapons.upBase(w), Weapons.UP_GROWTH, wLvl[w], 1);
                if (c < best) { best = c; kind = 0; idx = w; }
            }
            for (int i = 0; i < Ship.COUNT; i++) {
                if (shipLvl[i] >= Ship.MAX[i]) continue;
                double c = Fmt.costN(Ship.BASE[i], Ship.GROWTH[i], shipLvl[i], 1);
                if (c < best) { best = c; kind = 1; idx = i; }
            }
            for (int i = 0; i < Drones.COUNT; i++) {
                double c = dLvl[i] < 0 ? (dronesOwned() < droneSlots() ? Drones.COST[i] : Double.MAX_VALUE)
                        : Fmt.costN(Drones.upBase(i), Drones.GROWTH, dLvl[i], 1);
                if (c < best) { best = c; kind = 2; idx = i; }
            }
            if (kind < 0 || coins < best) break;
            if (kind == 0) buyWeapon(idx);
            else if (kind == 1) buyShip(idx);
            else buyDrone(idx);
        }
        buyMode = saved;
    }

    // ---------------- daily challenge ----------------
    static int today() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    void startDaily() { startSide(SIDE_DAILY); }

    boolean deepUnlocked() { return bestSector >= 50; }

    void startSide(int mode) {
        if (daily) return;
        if (mode == SIDE_DEEP && !deepUnlocked()) return;
        mainPlanet = planet;
        daily = true;
        sideMode = mode;
        dailyTime = 0;
        mutator = -1;
        if (mode == SIDE_DAILY) {
            int date = today();
            planet = new Planet(Math.max(5, runBest + 3), date * 7919L + 13, null, true, false);
        } else if (mode == SIDE_DEEP) {
            deepDepth = 1;
            planet = deepPlanet();
        } else {
            int week = Mutators.week();
            mutator = week % Mutators.COUNT;
            weeklyLimit = mutator == Mutators.GLASS ? 90 : 180;
            int fm = mutator == Mutators.FREEZE ? Planet.MOD_FROZEN : mutator == Mutators.POWDER ? Planet.MOD_UNSTABLE : -1;
            planet = new Planet(Math.max(5, runBest + 2), week * 31337L + 7, null, true, false, fm,
                    mutator == Mutators.TITAN ? 3 : 1);
        }
        clearTransient();
        placeShip();
        warpT = 1.2f;
        noteSeen();
        String m = planet.mod != Planet.MOD_NONE ? "  [" + Planet.MOD_NAME[planet.mod] + "]" : "";
        if (mode == SIDE_DAILY) showBanner("DAILY PLANET " + planet.name + m + " - clear it fast!", 0xFF7DF9FF, 3f);
        else if (mode == SIDE_DEEP) showBanner("DEEP CORE - how far down can you drill?", 0xFFFF9F43, 3f);
        else showBanner("WEEKLY: " + Mutators.NAME[mutator] + " - " + Mutators.DESC[mutator], 0xFFC165FF, 3.5f);
    }

    private Planet deepPlanet() {
        return new Planet(Math.max(10, runBest) + deepDepth * 2, rnd.nextLong(), null, true, false, -1,
                Math.pow(1.25, deepDepth));
    }

    private void nextDeep() {
        deepDepth++;
        planet = deepPlanet();
        clearTransient();
        placeShip();
        warpT = 1.2f;
        noteSeen();
        showBanner("DEPTH " + deepDepth + (deepDepth > deepBest ? "  - new record ahead!" : ""), 0xFFFF9F43, 2f);
    }

    private void deepCleared() {
        clearT = 1.6f;
        double en = 2 + deepDepth / 3, sc = 3 + deepDepth;
        energy += en;
        scrap += sc;
        if (deepDepth > deepBest) deepBest = deepDepth;
        showBanner("DEPTH " + deepDepth + " CLEARED  +" + Fmt.n(en) + " EN  +" + Fmt.n(sc) + " SCRAP", 0xFFFF9F43, 2f);
    }

    private void weeklyCleared() {
        int week = Mutators.week();
        String reward = "Already rewarded this week.";
        weekliesCleared++;
        if (weeklyDoneWeek != week) {
            weeklyDoneWeek = week;
            energy += 50;
            reward = "+50 energy";
            for (int i = 0; i < Cosmetics.COUNT; i++) {
                if (Cosmetics.ACH[i] == -2 && (cosWeekly & (1 << i)) == 0) {
                    cosWeekly |= 1 << i;
                    reward += ", cosmetic: " + Cosmetics.NAME[i];
                    break;
                }
            }
        }
        popTitle = "WEEKLY CLEARED";
        popLines = new String[]{Mutators.NAME[mutator] + " in " + fmtTime(dailyTime), reward};
    }

    private void weeklyFailed() {
        popTitle = "WEEKLY FAILED";
        popLines = new String[]{"Time ran out (" + (int) weeklyLimit + "s).", "Get stronger and try again this week."};
        endDaily();
    }

    void endDaily() {
        if (!daily) return;
        daily = false;
        mutator = -1;
        sideMode = 0;
        planet = mainPlanet;
        mainPlanet = null;
        clearTransient();
        placeShip();
        warpT = 1.2f;
        if (planet.boss) { bossTime = bossTimeMax(); regenT = 2.5f; }
    }

    private void dailyCleared() {
        int date = today();
        float t = dailyTime;
        boolean record = dailyBestDate != date || t < dailyBest;
        if (record) { dailyBest = t; dailyBestDate = date; }
        boolean first = dailyLastClear != date;
        String reward = "Already rewarded today - beat your time!";
        if (first) {
            dailyLastClear = date;
            dailyClears++;
            energy += 15;
            scrap += 20;
            reward = "Reward: +15 energy, +20 scrap";
        }
        popTitle = "DAILY CLEARED";
        popLines = new String[]{"Time: " + fmtTime(t) + (record ? "  NEW BEST" : ""), "Today's best: " + fmtTime(dailyBest), reward};
    }

    static String fmtTime(float s) {
        int m = (int) (s / 60);
        return m + ":" + String.format(java.util.Locale.US, "%04.1f", s - m * 60);
    }

    // ---------------- prestige ----------------
    void collapse() {
        int c = Perks.coresFor(runBest);
        if (c <= 0) return;
        if (daily) endDaily();
        cores += c;
        lifetimeCores += c;
        collapses++;
        int pl = perkLvl[Perks.HEADSTART];
        coins = pl == 0 ? 0 : 200 * Math.pow(10, pl);
        sector = 1;
        runBest = 1;
        bossBlocked = false;
        int keep = 1 + perkLvl[Perks.MEMORY];
        for (int w = 0; w < Weapons.COUNT; w++) wLvl[w] = w < keep ? 0 : -1;
        for (int i = 0; i < Ship.COUNT; i++) shipLvl[i] = 0;
        for (int i = 0; i < Drones.COUNT; i++) dLvl[i] = -1;
        for (int i = 0; i < Cards.COUNT; i++) runCard[i] = 0;
        pendingPicks = 0;
        offer = null;
        cpsAvg = 0;
        newPlanet();
        popTitle = "CORE COLLAPSE";
        popLines = new String[]{"The galaxy folds back to sector 1.", "+" + c + " Cores (" + Fmt.n(cores) + " total)",
                "Every lifetime core: +2% damage.", "Spend cores on perks in the LAB tab."};
        sfx(S_BOSS, 1f);
        buzz(300);
        flash(0.9f, 0xFFFF3EA5);
    }

    void buyPerk(int p) {
        if (perkLvl[p] >= Perks.MAX[p]) return;
        double c = Perks.cost(p, perkLvl[p]);
        if (cores >= c) {
            cores -= c;
            perkLvl[p]++;
            if (p == Perks.AUTOBUY) autoBuy = true;
        }
    }

    // ---------------- achievements ----------------
    double achValue(int i) {
        switch (Achievements.STAT[i]) {
            case Achievements.ST_BLOCKS: return totalBlocks;
            case Achievements.ST_GOLD: return goldMined;
            case Achievements.ST_PLANETS: return planetsCleared;
            case Achievements.ST_BOSSES: return bossesKilled;
            case Achievements.ST_CHAIN: return bestChain;
            case Achievements.ST_VEINS: return veinsDone;
            case Achievements.ST_SECTOR: return bestSector;
            case Achievements.ST_COLLAPSE: return collapses;
            case Achievements.ST_DAILY: return dailyClears;
            case Achievements.ST_WEAPONS: return unlockedWeapons();
            case Achievements.ST_SPEED: return fastestClear > 0 && fastestClear <= 30 ? 1 : 0;
            case Achievements.ST_DEEP: return deepBest;
            case Achievements.ST_WEEKLY: return weekliesCleared;
            case Achievements.ST_COMETS: return cometsCaught;
        }
        return 0;
    }

    boolean achDone(int i) { return achValue(i) >= Achievements.GOAL[i]; }

    int achClaimable() {
        int n = 0;
        for (int i = 0; i < Achievements.COUNT; i++) if (!achClaimed[i] && achDone(i)) n++;
        return n;
    }

    void claimAch(int i) {
        if (achClaimed[i] || !achDone(i)) return;
        achClaimed[i] = true;
        energy += Achievements.REWARD[i];
        String th = "";
        for (int t = 0; t < Themes.ACH.length; t++) if (Themes.ACH[t] == i) th = "  +theme " + Themes.NAME[t];
        showBanner(Achievements.NAME[i] + "  +" + Achievements.REWARD[i] + " EN" + th, 0xFFFFB547, 2.5f);
    }

    boolean themeUnlocked(int t) { return Themes.ACH[t] < 0 || achDone(Themes.ACH[t]); }

    void grantOffline(double seconds) {
        checkResearch();
        if (seconds < 60 || cpsAvg <= 0) return;
        int ds = perkLvl[Perks.DEEPSCAN], rs = resLvl[Research.SENSORS];
        double capH = 8 + 2 * ds + rs, rate = 0.5 + 0.1 * ds + 0.1 * rs;
        double s = Math.min(seconds, capH * 3600);
        double earn = cpsAvg * s * rate;
        coins += earn;
        totalEarned += earn;
        int h = (int) (s / 3600), m = (int) (s % 3600 / 60);
        popTitle = "WELCOME BACK";
        popLines = new String[]{"Your drones kept mining for " + (h > 0 ? h + "h " : "") + m + "m.",
                "Collected " + Fmt.n(earn) + " coins.",
                "(" + Math.round(rate * 100) + "% of your mining rate, up to " + (int) capH + "h)"};
    }

    // ---------------- shop actions ----------------
    int buyCount(double base, double g, int lvl, double money, int cap) {
        if (buyMode == 0) return 1;
        if (buyMode == 1) return Math.max(1, Math.min(10, cap));
        return Fmt.maxAffordable(base, g, lvl, money, cap);
    }

    void buyWeapon(int w) {
        if (wLvl[w] < 0) {
            if (coins >= Weapons.COST[w]) {
                coins -= Weapons.COST[w];
                wLvl[w] = 0;
                showBanner(Weapons.NAME[w] + " ONLINE  -  all damage x1.5", Weapons.COLOR[w], 2.5f);
                flash(0.2f, Weapons.COLOR[w]);
            }
            return;
        }
        int n = buyCount(Weapons.upBase(w), Weapons.UP_GROWTH, wLvl[w], coins, 100000);
        double c = Fmt.costN(Weapons.upBase(w), Weapons.UP_GROWTH, wLvl[w], n);
        if (coins >= c) { coins -= c; wLvl[w] += n; }
    }

    void buyShip(int i) {
        int cap = Ship.MAX[i] - shipLvl[i];
        if (cap <= 0) return;
        int n = buyCount(Ship.BASE[i], Ship.GROWTH[i], shipLvl[i], coins, cap);
        double c = Fmt.costN(Ship.BASE[i], Ship.GROWTH[i], shipLvl[i], n);
        if (coins >= c) { coins -= c; shipLvl[i] += n; }
    }

    void buyDrone(int i) {
        if (dLvl[i] < 0) {
            if (dronesOwned() < droneSlots() && coins >= Drones.COST[i]) {
                coins -= Drones.COST[i];
                dLvl[i] = 0;
                showBanner(Drones.NAME[i] + " DEPLOYED", Drones.COLOR[i], 2f);
            }
            return;
        }
        int n = buyCount(Drones.upBase(i), Drones.GROWTH, dLvl[i], coins, 100000);
        double c = Fmt.costN(Drones.upBase(i), Drones.GROWTH, dLvl[i], n);
        if (coins >= c) { coins -= c; dLvl[i] += n; }
    }

    void setHull(int h) {
        if (!hasHull(h)) return;
        hull = h;
        // Losing the Carrier's extra slot parks the most recent drone.
        while (dronesOwned() > droneSlots()) {
            for (int i = Drones.COUNT - 1; i >= 0; i--) if (dLvl[i] >= 0) { dLvl[i] = -1; break; }
        }
    }

    void rollSkills(int count) {
        int cost = count == 1 ? Skills.ROLL_COST : Skills.ROLL10_COST;
        if (energy < cost) return;
        energy -= cost;
        String[] lines = new String[count];
        for (int i = 0; i < count; i++) {
            int s = rnd.nextInt(Skills.COUNT), r = Skills.rollRarity(rnd);
            skillInv[s][r]++;
            lines[i] = Skills.RARITY[r] + " " + Skills.NAME[s];
        }
        popTitle = count == 1 ? "SKILL ACQUIRED" : "10 SKILLS ACQUIRED";
        popLines = lines;
    }

    boolean skillEquipped(int s) {
        for (int[] e : eqSkills) if (e[0] == s) return true;
        return false;
    }

    void equipSkill(int s, int r) {
        if (skillInv[s][r] <= 0) return;
        if (skillEquipped(s)) {
            // swap in place: replace the equipped copy of this skill
            for (int i = 0; i < eqSkills.size(); i++) {
                int[] e = eqSkills.get(i);
                if (e[0] == s) { skillInv[s][e[1]]++; eqSkills.remove(i); break; }
            }
        } else if (eqSkills.size() >= skillSlots()) return;
        skillInv[s][r]--;
        eqSkills.add(new int[]{s, r});
    }

    void unequipSkill(int i) {
        if (i < 0 || i >= eqSkills.size()) return;
        int[] e = eqSkills.remove(i);
        skillInv[e[0]][e[1]]++;
    }

    void fuseSkill(int s, int r) {
        if (r >= 3 || skillInv[s][r] < 3) return;
        skillInv[s][r] -= 3;
        skillInv[s][r + 1]++;
        showBanner("FUSED: " + Skills.RARITY[r + 1] + " " + Skills.NAME[s], Skills.RAR_COLOR[r + 1], 2f);
    }

    /** Skill priority used to break ties between equal-rarity skills (damage first). */
    private static final int[] SKILL_PRIO = {Skills.DMG, Skills.COIN, Skills.RATE, Skills.CRIT, Skills.ALCHEMY,
            Skills.VOLATILE, Skills.SIPHON, Skills.SPEED};

    /** Best loadout: one copy per skill type at its highest owned rarity, rarest first. */
    private ArrayList<int[]> bestSkills() {
        int[] best = new int[Skills.COUNT];
        for (int s = 0; s < Skills.COUNT; s++) {
            best[s] = -1;
            for (int r = 3; r >= 0 && best[s] < 0; r--) if (skillInv[s][r] > 0) best[s] = r;
        }
        for (int[] e : eqSkills) if (e[1] > best[e[0]]) best[e[0]] = e[1];
        ArrayList<int[]> out = new ArrayList<>();
        for (int r = 3; r >= 0; r--)
            for (int s : SKILL_PRIO)
                if (best[s] == r && out.size() < skillSlots()) out.add(new int[]{s, r});
        return out;
    }

    boolean canEquipBetterSkills() {
        ArrayList<int[]> b = bestSkills();
        if (b.size() != eqSkills.size()) return true;
        for (int[] want : b) {
            boolean has = false;
            for (int[] e : eqSkills) if (e[0] == want[0] && e[1] == want[1]) has = true;
            if (!has) return true;
        }
        return false;
    }

    void equipBestSkills() {
        ArrayList<int[]> b = bestSkills();
        while (!eqSkills.isEmpty()) unequipSkill(0);
        for (int[] e : b) equipSkill(e[0], e[1]);
    }

    private int bestGearIndex(int slot) {
        double cur = eqGear[slot] == null ? -1 : Gear.value(eqGear[slot]);
        int bi = -1;
        for (int i = 0; i < gearInv.size(); i++) {
            Gear.Item it = gearInv.get(i);
            if (it.slot == slot && Gear.value(it) > cur) { cur = Gear.value(it); bi = i; }
        }
        return bi;
    }

    boolean canEquipBetterGear() {
        for (int s = 0; s < 3; s++) if (bestGearIndex(s) >= 0) return true;
        return false;
    }

    void equipBestGear() {
        for (int s = 0; s < 3; s++) {
            int i = bestGearIndex(s);
            if (i >= 0) equipGear(i);
        }
    }

    void equipGear(int i) {
        if (i < 0 || i >= gearInv.size()) return;
        Gear.Item it = gearInv.remove(i);
        Gear.Item old = eqGear[it.slot];
        eqGear[it.slot] = it;
        if (old != null) gearInv.add(old);
    }

    void salvageGear(int i) {
        if (i < 0 || i >= gearInv.size()) return;
        scrap += Gear.salvage(gearInv.remove(i));
    }

    void salvageCommons() {
        for (int i = gearInv.size() - 1; i >= 0; i--) if (gearInv.get(i).rar == 0) salvageGear(i);
    }

    void upgradeGear(int slot) {
        Gear.Item it = eqGear[slot];
        if (it == null || it.lvl >= Gear.MAX_LVL) return;
        double c = Gear.upCost(it);
        if (scrap >= c) { scrap -= c; it.lvl++; }
    }

    void forge() {
        if (energy < Gear.FORGE_COST || gearInv.size() >= Gear.INV_CAP) return;
        energy -= Gear.FORGE_COST;
        Gear.Item it = Gear.roll(rnd, sector + 20);
        gearInv.add(it);
        showBanner("FORGED: " + Gear.name(it), Skills.RAR_COLOR[it.rar], 2f);
    }

    // ---------------- fx ----------------
    void showBanner(String s, int color, float t) {
        banner = s;
        bannerColor = color;
        bannerT = bannerMax = t;
    }

    int partCap() { return lowFx ? 150 : 700; }

    private void spawnPart(float x, float y, float vx, float vy, float life, float size, int color, int kind) {
        Particle p = new Particle();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy;
        p.life = p.max = life;
        p.size = size;
        p.color = color;
        p.kind = kind;
        p.rot = rnd.nextFloat() * 6.28f;
        parts.add(p);
    }

    private void burst(float x, float y, int col, int n) {
        if (lowFx) n = Math.max(1, n / 3);
        if (parts.size() > partCap()) n = Math.min(n, 2);
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * 6.283f, s = 50 + rnd.nextFloat() * 170;
            boolean spark = (i & 1) == 1;
            spawnPart(x, y, (float) Math.cos(a) * s * (spark ? 1.8f : 1), (float) Math.sin(a) * s * (spark ? 1.8f : 1),
                    spark ? 0.25f + rnd.nextFloat() * 0.2f : 0.45f + rnd.nextFloat() * 0.5f,
                    spark ? 1.6f : 3 + rnd.nextFloat() * 4.5f, spark ? 0xFFFFF2C0 : col, spark ? P_SPARK : P_SHARD);
        }
    }

    private void smoke(float x, float y, int n) {
        if (lowFx || parts.size() > partCap()) return;
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * 6.283f, s = 15 + rnd.nextFloat() * 40;
            spawnPart(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.9f + rnd.nextFloat() * 0.7f,
                    8 + rnd.nextFloat() * 8, 0xFF2A1F45, P_SMOKE);
        }
    }

    private void spark(float x, float y, int col, int n) {
        if (parts.size() > partCap()) return;
        if (lowFx) n = 1;
        for (int i = 0; i < n; i++) {
            float a = rnd.nextFloat() * 6.283f, s = 90 + rnd.nextFloat() * 160;
            spawnPart(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s, 0.2f, 1.5f, col, P_SPARK);
        }
    }

    private void ring(float x, float y, float r0, float r1, float life, int col) {
        if (rings.size() > 60) return;
        Ring r = new Ring();
        r.x = x; r.y = y; r.r0 = r0; r.r1 = r1;
        r.life = r.max = life;
        r.color = col;
        rings.add(r);
    }

    private void ringFill(float x, float y, float rad, float life, int col) {
        if (rings.size() > 60 || lowFx) return;
        Ring r = new Ring();
        r.x = x; r.y = y; r.r0 = rad * 0.5f; r.r1 = rad;
        r.life = r.max = life;
        r.color = col;
        r.filled = true;
        rings.add(r);
    }

    private void text(float x, float y, String s, int col, boolean big) {
        if (texts.size() > 40) return;
        FText t = new FText();
        t.x = x; t.y = y; t.s = s; t.color = col;
        t.life = big ? 1.2f : 1f;
        t.big = big;
        texts.add(t);
    }

    private void updateFx(float dt) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            Particle p = parts.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                int last = parts.size() - 1;
                parts.set(i, parts.get(last));
                parts.remove(last);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float drag = p.kind == P_SMOKE ? 1.2f : p.kind == P_SPARK ? 3.5f : 2.5f;
            p.vx *= 1 - dt * drag;
            p.vy *= 1 - dt * drag;
            p.rot += dt * 6;
        }
        for (int i = beams.size() - 1; i >= 0; i--) if ((beams.get(i).life -= dt) <= 0) beams.remove(i);
        for (int i = rings.size() - 1; i >= 0; i--) if ((rings.get(i).life -= dt) <= 0) rings.remove(i);
        for (int i = texts.size() - 1; i >= 0; i--) {
            FText t = texts.get(i);
            t.y -= dt * (t.big ? 22 : 30);
            if ((t.life -= dt) <= 0) texts.remove(i);
        }
        ArrayList<Block> a = planet.alive;
        for (int i = 0, n = a.size(); i < n; i++) {
            Block b = a.get(i);
            if (b.flash > 0) b.flash -= dt;
            if (b.punch > 0) b.punch = Math.max(0, b.punch - dt * 7);
        }
    }
}
