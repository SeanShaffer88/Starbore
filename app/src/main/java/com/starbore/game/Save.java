package com.starbore.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

/** Local-only persistence in SharedPreferences. */
final class Save {
    private static final String PREFS = "starbore", KEY = "save_v1";

    /** Set after an import so the old in-memory game cannot overwrite it before the restart. */
    static volatile boolean frozen;

    static void write(Context ctx, Game g) {
        if (frozen) return;
        try {
            prefs(ctx).edit().putString(KEY, toJson(g)).apply();
        } catch (Exception ignored) {
        }
    }

    /** Validates an exported save and installs it as the current one. */
    static boolean importJson(Context ctx, String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (!o.has("coins") || !o.has("sector") || !o.has("w") || !o.has("mask")) return false;
            frozen = true;
            prefs(ctx).edit().putString(KEY, o.toString()).commit();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String toJson(Game g) throws Exception {
        {
            JSONObject o = new JSONObject();
            o.put("coins", g.coins);
            o.put("energy", g.energy);
            o.put("scrap", g.scrap);
            o.put("cores", g.cores);
            o.put("lifeCores", g.lifetimeCores);
            o.put("sector", g.sector);
            o.put("best", g.bestSector);
            o.put("runBest", g.runBest);
            o.put("seed", g.seed);
            o.put("cps", g.cpsAvg);
            o.put("buy", g.buyMode);
            o.put("auto", g.auto);
            o.put("sound", g.soundOn);
            o.put("haptics", g.hapticsOn);
            o.put("theme", g.theme);
            o.put("bossBlocked", g.bossBlocked);
            o.put("bossTime", (double) g.bossTime);
            o.put("twin", g.twinPhase && !g.daily);
            o.put("autoBuy", g.autoBuy);
            o.put("cb", g.colorBlind);
            o.put("bigText", g.bigText);
            o.put("lowFx", g.lowFx);
            o.put("hull", g.hull);
            o.put("hulls", g.hullsOwned);
            o.put("hints", g.hintsDone);
            o.put("research", ints(g.resLvl));
            o.put("researching", g.researching);
            o.put("researchEnd", g.researchEnd);
            o.put("comets", g.cometsCaught);
            o.put("bestCombo", g.bestCombo);
            o.put("w", ints(g.wLvl));
            o.put("ship", ints(g.shipLvl));
            o.put("perks", ints(g.perkLvl));
            o.put("drones", ints(g.dLvl));
            JSONArray inv = new JSONArray();
            for (int[] row : g.skillInv) inv.put(ints(row));
            o.put("skInv", inv);
            JSONArray eq = new JSONArray();
            for (int[] e : g.eqSkills) eq.put(ints(e));
            o.put("skEq", eq);
            JSONArray gi = new JSONArray();
            for (Gear.Item it : g.gearInv) gi.put(item(it));
            o.put("gearInv", gi);
            JSONArray ge = new JSONArray();
            for (Gear.Item it : g.eqGear) ge.put(it == null ? JSONObject.NULL : item(it));
            o.put("gearEq", ge);
            // stats
            o.put("blocks", g.totalBlocks);
            o.put("gold", g.goldMined);
            o.put("planets", g.planetsCleared);
            o.put("bosses", g.bossesKilled);
            o.put("collapses", g.collapses);
            o.put("veins", g.veinsDone);
            o.put("dailies", g.dailyClears);
            o.put("bestChain", g.bestChain);
            o.put("fastest", (double) g.fastestClear);
            o.put("earned", g.totalEarned);
            JSONArray ach = new JSONArray();
            for (boolean b : g.achClaimed) ach.put(b);
            o.put("ach", ach);
            o.put("dailyBestDate", g.dailyBestDate);
            o.put("dailyBest", (double) g.dailyBest);
            o.put("dailyLast", g.dailyLastClear);
            // the main campaign planet, even while a daily is being played
            Planet p = g.daily && g.mainPlanet != null ? g.mainPlanet : g.planet;
            boolean[] m = p.mask();
            byte[] bits = new byte[(m.length + 7) / 8];
            for (int i = 0; i < m.length; i++) if (m[i]) bits[i >> 3] |= (byte) (1 << (i & 7));
            o.put("mask", Base64.encodeToString(bits, Base64.NO_WRAP));
            o.put("maskLen", m.length);
            o.put("time", System.currentTimeMillis());
            o.put("version", Version.NAME);
            o.put("runCards", ints(g.runCard));
            o.put("picks", g.pendingPicks);
            o.put("cos", ints(new int[]{g.cosTrail, g.cosPaint, g.cosBoom, g.cosWeekly}));
            o.put("seen", ints(new int[]{g.seenBlocks, g.seenBoss, g.seenMods}));
            o.put("music", g.musicOn);
            o.put("sfxLevel", g.sfxLevel);
            o.put("deepBest", g.deepBest);
            o.put("weeklies", g.weekliesCleared);
            o.put("weeklyDone", g.weeklyDoneWeek);
            return o.toString();
        }
    }

    /** Loads into g; returns seconds since last save, or -1 for a new game. */
    static double read(Context ctx, Game g) {
        String s = prefs(ctx).getString(KEY, null);
        if (s == null) {
            g.newGame();
            return -1;
        }
        return fromJson(s, g);
    }

    /** Loads a save string into g; returns seconds since it was written, or -1 (new game) if it is unreadable. */
    static double fromJson(String s, Game g) {
        try {
            JSONObject o = new JSONObject(s);
            g.coins = o.getDouble("coins");
            g.energy = o.getDouble("energy");
            g.scrap = o.getDouble("scrap");
            g.cores = o.optDouble("cores", 0);
            g.lifetimeCores = o.optDouble("lifeCores", 0);
            g.sector = o.getInt("sector");
            g.bestSector = o.optInt("best", g.sector);
            g.runBest = o.optInt("runBest", g.sector);
            g.seed = o.getLong("seed");
            g.cpsAvg = o.optDouble("cps", 0);
            g.buyMode = o.optInt("buy");
            g.auto = o.optBoolean("auto", true);
            g.soundOn = o.optBoolean("sound", true);
            g.hapticsOn = o.optBoolean("haptics", true);
            g.theme = o.optInt("theme", 0);
            g.bossBlocked = o.optBoolean("bossBlocked", false);
            g.autoBuy = o.optBoolean("autoBuy", false);
            g.colorBlind = o.optBoolean("cb", false);
            g.bigText = o.optBoolean("bigText", false);
            g.lowFx = o.optBoolean("lowFx", false);
            g.hullsOwned = o.optInt("hulls", 1) | 1;
            g.hull = o.optInt("hull", 0);
            if (!g.hasHull(g.hull)) g.hull = 0;
            // Saves from before hints existed: skip the beginner ones for veterans.
            g.hintsDone = o.optInt("hints", g.bestSector > 3 ? 0x1F : 0);
            if (o.has("research")) readInts(o.getJSONArray("research"), g.resLvl);
            g.researching = o.optInt("researching", -1);
            g.researchEnd = o.optLong("researchEnd", 0);
            g.cometsCaught = o.optLong("comets");
            g.bestCombo = o.optInt("bestCombo");
            if (o.has("runCards")) readInts(o.getJSONArray("runCards"), g.runCard);
            g.pendingPicks = o.optInt("picks");
            if (o.has("cos")) {
                int[] cs = new int[4];
                readInts(o.getJSONArray("cos"), cs);
                g.cosWeekly = cs[3];
                g.cosTrail = cs[0];
                g.cosPaint = cs[1];
                g.cosBoom = cs[2];
            }
            if (o.has("seen")) {
                int[] sn = new int[3];
                readInts(o.getJSONArray("seen"), sn);
                g.seenBlocks = sn[0];
                g.seenBoss = sn[1];
                g.seenMods = sn[2];
            }
            g.musicOn = o.optBoolean("music", true);
            g.sfxLevel = Math.max(0, Math.min(Game.SFX_LEVELS.length - 1, o.optInt("sfxLevel", 2)));
            g.deepBest = o.optInt("deepBest");
            g.weekliesCleared = o.optInt("weeklies");
            g.weeklyDoneWeek = o.optInt("weeklyDone", -1);
            readInts(o.getJSONArray("w"), g.wLvl);
            readInts(o.getJSONArray("ship"), g.shipLvl);
            for (int i = 0; i < Drones.COUNT; i++) g.dLvl[i] = -1;
            if (o.has("perks")) readInts(o.getJSONArray("perks"), g.perkLvl);
            if (o.has("drones")) readInts(o.getJSONArray("drones"), g.dLvl);
            JSONArray inv = o.getJSONArray("skInv");
            for (int i = 0; i < Math.min(inv.length(), g.skillInv.length); i++) readInts(inv.getJSONArray(i), g.skillInv[i]);
            JSONArray eq = o.getJSONArray("skEq");
            g.eqSkills.clear();
            for (int i = 0; i < eq.length(); i++) {
                JSONArray e = eq.getJSONArray(i);
                g.eqSkills.add(new int[]{e.getInt(0), e.getInt(1)});
            }
            JSONArray gi = o.getJSONArray("gearInv");
            g.gearInv.clear();
            for (int i = 0; i < gi.length(); i++) g.gearInv.add(item(gi.getJSONObject(i)));
            JSONArray ge = o.getJSONArray("gearEq");
            for (int i = 0; i < 3 && i < ge.length(); i++) g.eqGear[i] = ge.isNull(i) ? null : item(ge.getJSONObject(i));
            g.totalBlocks = o.optLong("blocks");
            g.goldMined = o.optLong("gold");
            g.planetsCleared = o.optLong("planets");
            g.bossesKilled = o.optLong("bosses");
            g.collapses = o.optLong("collapses");
            g.veinsDone = o.optLong("veins");
            g.dailyClears = o.optLong("dailies");
            g.bestChain = o.optInt("bestChain");
            g.fastestClear = (float) o.optDouble("fastest", 0);
            g.totalEarned = o.optDouble("earned", 0);
            JSONArray ach = o.optJSONArray("ach");
            if (ach != null) for (int i = 0; i < Math.min(ach.length(), g.achClaimed.length); i++) g.achClaimed[i] = ach.getBoolean(i);
            // cosmetics depend on achievement stats, so validate them once everything is loaded
            if (!validCos(g, g.cosTrail, Cosmetics.TRAIL)) g.cosTrail = 0;
            if (!validCos(g, g.cosPaint, Cosmetics.PAINT)) g.cosPaint = 6;
            if (!validCos(g, g.cosBoom, Cosmetics.BOOM)) g.cosBoom = 11;
            g.dailyBestDate = o.optInt("dailyBestDate");
            g.dailyBest = (float) o.optDouble("dailyBest", 0);
            g.dailyLastClear = o.optInt("dailyLast");
            if (!g.themeUnlocked(g.theme)) g.theme = 0;

            byte[] bits = Base64.decode(o.getString("mask"), Base64.NO_WRAP);
            boolean[] m = new boolean[o.getInt("maskLen")];
            for (int i = 0; i < m.length; i++) m[i] = (bits[i >> 3] & (1 << (i & 7))) != 0;
            boolean twin = o.optBoolean("twin", false);
            g.twinPhase = twin;
            g.planet = new Planet(g.sector, twin ? g.seed + 1 : g.seed, m, false, twin);
            g.onPlanetStart();
            if (g.planet.boss) g.bossTime = (float) o.optDouble("bossTime", g.bossTimeMax());
            return (System.currentTimeMillis() - o.getLong("time")) / 1000.0;
        } catch (Exception e) {
            g.newGame();
            return -1;
        }
    }

    private static boolean validCos(Game g, int i, int type) {
        return i >= 0 && i < Cosmetics.COUNT && Cosmetics.TYPE[i] == type && g.cosUnlocked(i);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray ints(int[] a) {
        JSONArray j = new JSONArray();
        for (int v : a) j.put(v);
        return j;
    }

    private static void readInts(JSONArray j, int[] out) throws Exception {
        for (int i = 0; i < Math.min(j.length(), out.length); i++) out[i] = j.getInt(i);
    }

    private static JSONObject item(Gear.Item it) throws Exception {
        JSONObject o = new JSONObject();
        o.put("s", it.slot);
        o.put("r", it.rar);
        o.put("l", it.lvl);
        return o;
    }

    private static Gear.Item item(JSONObject o) throws Exception {
        return new Gear.Item(o.getInt("s"), o.getInt("r"), o.getInt("l"));
    }
}
