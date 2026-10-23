package com.starbore.game;

import java.util.ArrayList;
import java.util.Random;

final class Block {
    static final int NORMAL = 0, GOLD = 1, ENERGY = 2, EXPLOSIVE = 3, ARMOR = 4, VEIN = 5, HEART = 6, NODE = 7, TURRET = 8;
    final int q, r, layer;
    final float x, y;
    int type, idx, vein = -1, lobe;
    double hp, maxHp, frost, maxFrost;
    boolean alive = true, overload;
    float flash, punch, deadT = -1, fireT;

    Block(int q, int r, int layer) {
        this.q = q;
        this.r = r;
        this.layer = layer;
        x = Game.HS * Game.SQ3 * (q + r * 0.5f);
        y = Game.HS * 1.5f * r;
    }
}

/** A planet made of a hex grid in axial coordinates (pointy-top). */
final class Planet {
    static final int MOD_NONE = 0, MOD_LOWG = 1, MOD_MAGNET = 2, MOD_FROZEN = 3, MOD_UNSTABLE = 4, MOD_RICH = 5;
    static final String[] MOD_NAME = {"", "LOW GRAVITY", "MAGNETIC", "FROZEN", "UNSTABLE", "MOTHERLODE"};
    static final String[] MOD_DESC = {"", "shots fly 50% farther", "bolts curve toward hexes",
            "frost coats every hex (+40% coins)", "red hexes everywhere", "gold hexes x2.5"};
    static final int BOSS_HEART = 0, BOSS_SHIELD = 1, BOSS_GUNSHIP = 2, BOSS_SPLITTER = 3;
    static final String[] BOSS_NAME = {"CORE TITAN", "AEGIS WORLD", "GUNSHIP PLANET", "SPLITTER"};
    static final String[] BOSS_DESC = {"break the heart before time runs out",
            "destroy the green shield nodes - the planet shrugs off 90% damage until they fall",
            "turrets fire stun shots - drag to dodge the red orbs",
            "crack the heart and it splits into two planets"};
    static final int[][] DIRS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

    final int R;
    final Block[][] grid;
    final ArrayList<Block> all = new ArrayList<>();
    final ArrayList<Block> alive = new ArrayList<>();
    final float worldR;
    final String name;
    final int pal, mod, bossType;
    final boolean boss, twin;
    final double baseHp;
    final int[] veinLeft;
    int nodesLeft;

    private static final String[] SYL = {"kor", "va", "zen", "tha", "rix", "ul", "mor", "qua", "dra", "le",
            "syn", "oth", "pyr", "ex", "nai", "vol", "ka", "teh"};

    static int hexDist(int q, int r) {
        return (Math.abs(q) + Math.abs(r) + Math.abs(q + r)) / 2;
    }

    static int bossTypeFor(int sector) { return ((sector / 10) - 1) % 4; }

    /**
     * @param twin second phase of a Splitter boss: two smaller planets side by side.
     */
    Planet(int sector, long seed, boolean[] aliveMask, boolean daily, boolean twin) {
        this(sector, seed, aliveMask, daily, twin, -1, 1);
    }

    /**
     * @param forceMod modifier to apply regardless of the roll (-1 = roll normally).
     * @param hpMul    extra hit-point multiplier (weekly Titan World, Deep Core).
     */
    Planet(int sector, long seed, boolean[] aliveMask, boolean daily, boolean twin, int forceMod, double hpMul) {
        Random rnd = new Random(seed);
        boss = !daily && sector % 10 == 0;
        bossType = boss ? bossTypeFor(sector) : -1;
        this.twin = twin;
        int baseR = Math.min(6 + sector / 3, 22);
        int lobeR = twin ? Math.max(4, (int) (baseR * 0.62f)) : baseR;
        int off = twin ? lobeR + 2 : 0; // lobe centre offset in q
        R = twin ? off + lobeR + 1 : baseR;
        pal = (sector - 1) % Render.PALETTES.length;
        baseHp = 8 * Math.pow(1.32, sector - 1) * (boss ? 1.5 : 1) * hpMul;
        grid = new Block[2 * R + 1][2 * R + 1];

        String nm = SYL[rnd.nextInt(SYL.length)] + SYL[rnd.nextInt(SYL.length)];
        name = Character.toUpperCase(nm.charAt(0)) + nm.substring(1) + "-" + (sector * 7 + rnd.nextInt(7));
        int m = 1 + rnd.nextInt(5);
        boolean rollMod = !boss && (sector >= 5 || daily) && rnd.nextFloat() < 0.45f;
        mod = forceMod >= 0 ? forceMod : rollMod ? m : MOD_NONE;

        // Organic outline: the outer rings are eaten away by smooth angular noise.
        float[] edge = new float[16];
        for (int i = 0; i < edge.length; i++) edge[i] = rnd.nextFloat() * Math.min(2.6f, lobeR * 0.25f);

        float pExp = mod == MOD_UNSTABLE ? 0.12f : 0.03f;
        float pEn = 0.025f;
        float pGold = mod == MOD_RICH ? 0.1f : 0.04f;
        int lobes = twin ? 2 : 1;
        for (int lobe = 0; lobe < lobes; lobe++) {
            int cq = twin ? (lobe == 0 ? -off : off) : 0;
            for (int r = -lobeR; r <= lobeR; r++) {
                for (int q = -lobeR; q <= lobeR; q++) {
                    int d = hexDist(q, r);
                    if (d > lobeR) continue;
                    int gq = q + cq;
                    if (get(gq, r) != null || Math.abs(gq) > R) continue;
                    float x = Game.SQ3 * (q + r * 0.5f), y = 1.5f * r;
                    double a = (Math.atan2(y, x) / (Math.PI * 2) + 1) % 1 * edge.length;
                    int i0 = (int) a % edge.length, i1 = (i0 + 1) % edge.length;
                    float f = (float) (a - Math.floor(a));
                    float cut = edge[i0] * (1 - f) + edge[i1] * f;
                    if (d > lobeR - cut) continue;

                    float t = d / (float) lobeR;
                    int layer = t > 0.66f ? 0 : t > 0.33f ? 1 : 2;
                    Block b = new Block(gq, r, layer);
                    b.lobe = lobe;
                    float roll = rnd.nextFloat();
                    double mul = 1;
                    if (d == 0 && boss && (bossType == BOSS_HEART || bossType == BOSS_SPLITTER || bossType == BOSS_SHIELD)) {
                        b.type = Block.HEART;
                        mul = twin ? 60 : 120;
                    } else if (d > 0) {
                        if (roll < pExp) { b.type = Block.EXPLOSIVE; mul = 0.6; }
                        else if (roll < pExp + pEn) { b.type = Block.ENERGY; mul = 1.2; }
                        else if (roll < pExp + pEn + pGold) { b.type = Block.GOLD; mul = 1.5; }
                        else if (boss && layer == 0) { b.type = Block.ARMOR; mul = 1.5; }
                    }
                    b.maxHp = b.hp = baseHp * (1 + 1.6 * (1 - t)) * mul * (0.9 + rnd.nextFloat() * 0.2);
                    if (mod == MOD_FROZEN) b.maxFrost = b.frost = b.maxHp * 0.4;
                    grid[gq + R][r + R] = b;
                    all.add(b);
                }
            }
        }

        // Boss furniture: shield nodes in the mantle ring, turrets on the crust.
        if (bossType == BOSS_SHIELD) placeSpecial(rnd, Block.NODE, 1, 5 + baseR / 6, 12);
        if (bossType == BOSS_GUNSHIP) placeSpecial(rnd, Block.TURRET, 0, 6 + baseR / 4, 4);

        // Ore veins: random walks of rare hexes through the inner layers.
        int nv = 1 + lobeR / 7;
        veinLeft = new int[nv];
        for (int v = 0; v < nv; v++) {
            Block cur = null;
            for (int k = 0; k < 30 && cur == null; k++) {
                Block c = all.get(rnd.nextInt(all.size()));
                if (c.layer >= 1 && c.type == Block.NORMAL) cur = c;
            }
            int len = 5 + rnd.nextInt(5);
            while (cur != null && len-- > 0) {
                cur.type = Block.VEIN;
                cur.vein = v;
                cur.maxHp = cur.hp = cur.maxHp * 2;
                if (cur.maxFrost > 0) cur.maxFrost = cur.frost = cur.maxHp * 0.4;
                veinLeft[v]++;
                Block next = null;
                int start = rnd.nextInt(6);
                for (int k = 0; k < 6 && next == null; k++) {
                    int[] dd = DIRS[(start + k) % 6];
                    Block n = get(cur.q + dd[0], cur.r + dd[1]);
                    if (n != null && n.type == Block.NORMAL) next = n;
                }
                cur = next;
            }
        }

        float wr = 0;
        for (int i = 0; i < all.size(); i++) {
            Block b = all.get(i);
            wr = Math.max(wr, (float) Math.hypot(b.x, b.y));
            if (aliveMask != null && i < aliveMask.length && !aliveMask[i]) {
                b.alive = false;
                if (b.vein >= 0) veinLeft[b.vein]--;
                continue;
            }
            if (b.type == Block.NODE) nodesLeft++;
            b.idx = alive.size();
            alive.add(b);
        }
        worldR = Math.max(wr, 1) + Game.HS;
    }

    private void placeSpecial(Random rnd, int type, int layer, int count, double hpMul) {
        int placed = 0;
        for (int k = 0; k < 400 && placed < count; k++) {
            Block b = all.get(rnd.nextInt(all.size()));
            if (b.layer != layer || b.type == Block.HEART) continue;
            boolean crowded = false;
            for (Block o : all) {
                if (o.type == type && Math.hypot(o.x - b.x, o.y - b.y) < Game.HS * 5) { crowded = true; break; }
            }
            if (crowded) continue;
            b.type = type;
            b.maxHp = b.hp = baseHp * hpMul;
            b.maxFrost = b.frost = 0;
            b.fireT = rnd.nextFloat() * 3;
            placed++;
        }
    }

    Block get(int q, int r) {
        int i = q + R, j = r + R;
        if (i < 0 || j < 0 || i > 2 * R || j > 2 * R) return null;
        return grid[i][j];
    }

    /** Block whose hex contains the world point (alive or not), or null. */
    Block at(float x, float y) {
        float fq = (Game.SQ3 / 3f * x - y / 3f) / Game.HS;
        float fr = (2f / 3f * y) / Game.HS;
        float fs = -fq - fr;
        int q = Math.round(fq), r = Math.round(fr), s = Math.round(fs);
        float dq = Math.abs(q - fq), dr = Math.abs(r - fr), ds = Math.abs(s - fs);
        if (dq > dr && dq > ds) q = -r - s;
        else if (dr > ds) r = -q - s;
        return get(q, r);
    }

    void remove(Block b) {
        b.alive = false;
        if (b.type == Block.NODE) nodesLeft--;
        int last = alive.size() - 1;
        Block m = alive.get(last);
        alive.set(b.idx, m);
        m.idx = b.idx;
        alive.remove(last);
    }

    void revive(Block b) {
        b.alive = true;
        b.deadT = -1;
        b.hp = b.maxHp * 0.5;
        b.frost = b.maxFrost * 0.5;
        b.idx = alive.size();
        alive.add(b);
        if (b.vein >= 0) veinLeft[b.vein]++;
        if (b.type == Block.NODE) nodesLeft++;
    }

    boolean hasAliveNeighbor(Block b) {
        for (int[] d : DIRS) {
            Block n = get(b.q + d[0], b.r + d[1]);
            if (n != null && n.alive) return true;
        }
        return false;
    }

    boolean[] mask() {
        boolean[] m = new boolean[all.size()];
        for (int i = 0; i < m.length; i++) m[i] = all.get(i).alive;
        return m;
    }

    float progress() {
        return 1f - alive.size() / (float) all.size();
    }
}
