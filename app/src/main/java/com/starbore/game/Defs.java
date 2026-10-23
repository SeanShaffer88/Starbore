package com.starbore.game;

import java.util.Locale;
import java.util.Random;

/** Static game data: number formatting, weapons, skills, gear and ship upgrades. */
final class Fmt {
    private static final String[] SUFFIX = {"", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"};

    static String n(double v) {
        if (v < 0) return "-" + n(-v);
        if (v < 1000) {
            if (v < 10 && v != Math.floor(v)) return String.format(Locale.US, "%.1f", v);
            return String.valueOf((long) Math.floor(v));
        }
        int e = 0;
        while (v >= 1000) { v /= 1000; e++; }
        String s;
        if (e < SUFFIX.length) s = SUFFIX[e];
        else {
            int k = e - SUFFIX.length;
            s = "" + (char) ('a' + (k / 26) % 26) + (char) ('a' + k % 26);
        }
        String num = v >= 100 ? String.format(Locale.US, "%.0f", v)
                : v >= 10 ? String.format(Locale.US, "%.1f", v)
                : String.format(Locale.US, "%.2f", v);
        return num + s;
    }

    static String pct(double v) {
        return "+" + (v * 100 >= 100 ? String.valueOf(Math.round(v * 100)) : String.format(Locale.US, "%.1f", v * 100)) + "%";
    }

    /** Sum of a geometric cost series: base*g^lvl + ... + base*g^(lvl+n-1). */
    static double costN(double base, double g, int lvl, int n) {
        return base * Math.pow(g, lvl) * (Math.pow(g, n) - 1) / (g - 1);
    }

    /** How many levels can be bought with the money (at least 1 for display). */
    static int maxAffordable(double base, double g, int lvl, double money, int cap) {
        double first = base * Math.pow(g, lvl);
        if (money < first) return 1;
        int n = (int) Math.floor(Math.log(money * (g - 1) / first + 1) / Math.log(g));
        return Math.max(1, Math.min(n, cap));
    }
}

final class Weapons {
    static final int COUNT = 10;
    static final int BLASTER = 0, LANCE = 1, SCATTER = 2, MORTAR = 3, CHAIN = 4,
            SAWS = 5, SWARM = 6, RAIL = 7, NOVA = 8, SINGULARITY = 9;

    static final String[] NAME = {"Pulse Blaster", "Drill Lance", "Scatter Cannon", "Arc Mortar", "Chain Arc",
            "Orbit Saws", "Seeker Swarm", "Rail Driver", "Nova Pulse", "Singularity"};
    static final String[] DESC = {"Rapid bolts at the nearest hex", "Continuous short-range cutting beam",
            "Five-pellet spread", "Lobs shells that blast an area", "Lightning that leaps between hexes",
            "Two blades circle your ship", "Homing micro-missiles", "Pierces every hex in a line",
            "Shockwave around your ship", "A tiny black hole that grinds an area"};
    /** Unlock cost in coins. */
    static final double[] COST = {0, 600, 8e3, 1e5, 1.5e6, 2.5e7, 4e8, 7e9, 1.2e11, 2e12};
    /** Damage per hit (or per second for beam/saws/singularity). */
    static final double[] DMG = {4, 20, 5, 40, 26, 36, 60, 340, 220, 800};
    static final float[] CD = {0.32f, 0f, 0.85f, 1.6f, 1.1f, 0f, 1.3f, 2.2f, 3.0f, 7f};
    static final float[] RANGE = {250, 125, 210, 380, 240, 0, 330, 540, 0, 320};
    static final int[] COLOR = {0xFF9BF6FF, 0xFFFFD84A, 0xFFFF7AD9, 0xFFFF9F43, 0xFF8AB4FF,
            0xFFE8E8F0, 0xFFFF5E7E, 0xFF5CFFB0, 0xFFC58BFF, 0xFFB04DFF};
    /** Every unlocked weapon permanently multiplies all damage by this. */
    static final double UNLOCK_MULT = 1.5;

    /** Linear growth, x2 every 25 levels up to level 100, then x3 every 25 levels (late-game push). */
    static double lvlFactor(int lvl) {
        int m = lvl / 25;
        return (1 + 0.2 * lvl) * Math.pow(2, Math.min(m, 4)) * Math.pow(3, Math.max(0, m - 4));
    }

    static double upBase(int w) { return w == 0 ? 6 : COST[w] * 0.08; }

    static final double UP_GROWTH = 1.16;
}

final class Ship {
    static final int COUNT = 4;
    static final String[] NAME = {"Targeting CPU", "Cargo Refinery", "Crit Lens", "Thrusters"};
    static final String[] DESC = {"+5% fire rate", "+12% coins", "+0.2x crit damage", "+6% ship speed"};
    static final double[] BASE = {40, 60, 150, 30};
    static final double[] GROWTH = {1.22, 1.2, 1.28, 1.35};
    static final int[] MAX = {60, 999, 40, 20};
}

final class Skills {
    static final int COUNT = 8;
    static final int DMG = 0, COIN = 1, RATE = 2, ALCHEMY = 3, CRIT = 4, VOLATILE = 5, SPEED = 6, SIPHON = 7;
    static final String[] NAME = {"Overcharge", "Prospector", "Rapid Cycle", "Alchemy", "Crit Matrix",
            "Volatility", "Afterburner", "Siphon"};
    static final String[] EFFECT = {"damage", "coins", "fire rate", "transmute to gold", "crit chance",
            "explosion power", "ship speed", "energy gain"};
    static final double[] BASE = {0.10, 0.12, 0.06, 0.03, 0.03, 0.20, 0.10, 0.15};
    static final String[] RARITY = {"Common", "Rare", "Epic", "Legendary"};
    static final double[] RAR_MUL = {1, 2.5, 6, 15};
    static final int[] RAR_COLOR = {0xFFB8C0D8, 0xFF4FA8FF, 0xFFC165FF, 0xFFFFB547};
    static final int ROLL_COST = 15, ROLL10_COST = 135;

    static double value(int s, int r) { return BASE[s] * RAR_MUL[r]; }

    static String desc(int s, int r) { return Fmt.pct(value(s, r)) + " " + EFFECT[s]; }

    static int rollRarity(Random rnd) {
        float f = rnd.nextFloat() * 100;
        if (f < 0.6f) return 3;
        if (f < 5f) return 2;
        if (f < 24f) return 1;
        return 0;
    }
}

final class Gear {
    static final String[] SLOT = {"Hull", "Reactor Core", "Engine"};
    static final String[] EFFECT = {"coins", "damage", "fire rate & speed"};
    static final double[] BASE = {0.08, 0.2, 0.45, 1.0};
    static final int[] SALVAGE = {4, 10, 25, 60};
    static final int MAX_LVL = 20, INV_CAP = 30, FORGE_COST = 25;

    static final class Item {
        int slot, rar, lvl;
        Item(int slot, int rar, int lvl) { this.slot = slot; this.rar = rar; this.lvl = lvl; }
    }

    static double value(Item i) { return BASE[i.rar] * (1 + 0.25 * i.lvl); }

    static double upCost(Item i) { return 6 * (i.lvl + 1) * (i.rar + 1); }

    static double salvage(Item i) { return SALVAGE[i.rar] + 3 * i.lvl * (i.rar + 1); }

    static String name(Item i) { return Skills.RARITY[i.rar] + " " + SLOT[i.slot]; }

    static String desc(Item i) { return Fmt.pct(value(i)) + " " + EFFECT[i.slot]; }

    static Item roll(Random rnd, int sector) {
        float f = rnd.nextFloat() * 100 - Math.min(sector, 60) * 0.25f;
        int r = f < 2 ? 3 : f < 12 ? 2 : f < 40 ? 1 : 0;
        return new Item(rnd.nextInt(3), r, 0);
    }
}

/** Permanent upgrades bought with Cores from Core Collapse (prestige). */
final class Perks {
    static final int COUNT = 7;
    static final int DAMAGE = 0, YIELD = 1, HEADSTART = 2, DRONEBAY = 3, DEEPSCAN = 4, MEMORY = 5, AUTOBUY = 6;
    static final String[] NAME = {"Core Resonance", "Core Yield", "Head Start", "Drone Bay", "Deep Scan", "Weapon Memory",
            "Quartermaster"};
    static final String[] DESC = {"+25% damage", "+25% coins", "start each run with coins", "+1 drone slot",
            "+2h offline cap, +10% offline rate", "keep +1 weapon unlocked after a collapse",
            "unlocks AUTO-BUY: spends coins on the cheapest upgrade"};
    static final double[] BASE = {1, 1, 2, 4, 2, 3, 3};
    static final double[] GROWTH = {1.5, 1.5, 1.8, 3, 1.8, 2.2, 1};
    static final int[] MAX = {999, 999, 15, 3, 6, 9, 1};
    static final int MIN_SECTOR = 25;

    static double cost(int p, int lvl) { return Math.ceil(BASE[p] * Math.pow(GROWTH[p], lvl)); }

    static int coresFor(int runBest) {
        if (runBest < MIN_SECTOR) return 0;
        return Math.max(1, (int) Math.floor(Math.pow((runBest - 20) / 5.0, 1.6)));
    }
}

/** Escort drones: each hunts its own kind of hex. */
final class Drones {
    static final int COUNT = 4;
    static final String[] NAME = {"Miner Drone", "Gold Hound", "Spark Hound", "Sapper"};
    static final String[] DESC = {"zaps the nearest hex", "hunts gold hexes", "hunts energy hexes", "detonates red hexes"};
    static final int[] TARGET = {-1, Block.GOLD, Block.ENERGY, Block.EXPLOSIVE};
    static final int[] COLOR = {0xFF9BF6FF, 0xFFFFD84A, 0xFF7DF9FF, 0xFFFF5E7E};
    static final double[] COST = {4e3, 4e4, 3e5, 2e6};
    static final double GROWTH = 1.18;

    static double upBase(int d) { return COST[d] * 0.1; }
}

/** Two-weapon (or weapon + hex) interactions. */
final class Synergies {
    static final String[] NAME = {"OVERLOAD", "NAPALM", "SHATTER", "DETONATOR", "GRAVITY GRIND", "CONDUCTOR"};
    static final String[] DESC = {"Chain Arc makes red hexes blow up x2 and wider",
            "Arc Mortar + Scatter Cannon: shells leave burning ground",
            "Drill Lance ignores armor and shatters frost instantly",
            "Nova Pulse detonates red hexes instantly",
            "Singularity + Orbit Saws: saws deal x3 while a singularity lives",
            "Rail Driver + Chain Arc: every rail shot releases a free chain arc"};
    static final int[][] NEEDS = {{Weapons.CHAIN}, {Weapons.MORTAR, Weapons.SCATTER}, {Weapons.LANCE},
            {Weapons.NOVA}, {Weapons.SINGULARITY, Weapons.SAWS}, {Weapons.RAIL, Weapons.CHAIN}};
    static final int OVERLOAD = 0, NAPALM = 1, SHATTER = 2, DETONATOR = 3, GRIND = 4, CONDUCTOR = 5;
}

final class Achievements {
    static final int COUNT = 19;
    static final int ST_BLOCKS = 0, ST_GOLD = 1, ST_PLANETS = 2, ST_BOSSES = 3, ST_CHAIN = 4, ST_VEINS = 5,
            ST_SECTOR = 6, ST_COLLAPSE = 7, ST_DAILY = 8, ST_WEAPONS = 9, ST_SPEED = 10, ST_DEEP = 11, ST_WEEKLY = 12,
            ST_COMETS = 13;
    static final String[] NAME = {"Prospector", "Strip Miner", "Gold Rush", "Planet Eater", "World Ender",
            "Giant Slayer", "Titan Breaker", "Chain Reaction", "Cascade", "Vein Hunter", "Deep Space", "Far Reach",
            "Collapse", "Daily Driller", "Arsenal", "Speedrun", "Deep Diver", "Rule Breaker", "Comet Chaser"};
    static final String[] DESC = {"Mine 1,000 hexes", "Mine 100,000 hexes", "Mine 500 gold hexes", "Clear 25 planets",
            "Clear 250 planets", "Defeat a boss planet", "Defeat 10 boss planets", "Chain 10 explosions",
            "Chain 30 explosions", "Complete 10 ore veins", "Reach sector 15", "Reach sector 30",
            "Perform a Core Collapse", "Clear 3 daily planets", "Unlock all 10 weapons", "Clear a 500+ hex planet in 30s",
            "Reach Deep Core depth 10", "Clear a weekly challenge", "Catch 25 comets"};
    static final int[] STAT = {ST_BLOCKS, ST_BLOCKS, ST_GOLD, ST_PLANETS, ST_PLANETS, ST_BOSSES, ST_BOSSES, ST_CHAIN,
            ST_CHAIN, ST_VEINS, ST_SECTOR, ST_SECTOR, ST_COLLAPSE, ST_DAILY, ST_WEAPONS, ST_SPEED, ST_DEEP, ST_WEEKLY,
            ST_COMETS};
    static final double[] GOAL = {1e3, 1e5, 500, 25, 250, 1, 10, 10, 30, 10, 15, 30, 1, 3, 10, 1, 10, 1, 25};
    static final int[] REWARD = {10, 50, 20, 20, 80, 25, 100, 15, 40, 20, 20, 40, 50, 30, 100, 20, 60, 40, 30};
}

final class Themes {
    static final int NEON = 0, BLUEPRINT = 1, PIXEL = 2, PAPER = 3;
    static final String[] NAME = {"Neon", "Blueprint", "Pixel", "Paper-cut"};
    /** Achievement that unlocks each theme (-1 = always). */
    static final int[] ACH = {-1, 10, 11, 12};
}

/** Collectible ship hulls (dropped by boss planets). */
final class Hulls {
    static final int COUNT = 6;
    static final int WASP = 0, HORNET = 1, CARRIER = 2, REAPER = 3, PROSPECTOR = 4, BASTION = 5;
    static final String[] NAME = {"Wasp", "Hornet", "Carrier", "Reaper", "Prospector", "Bastion"};
    static final String[] DESC = {"balanced starter hull", "+25% ship speed", "+1 drone slot",
            "saws: +60% reach, +50% damage", "+20% coins", "+15% damage, immune to stuns"};
    static final int[] COLOR = {0xFF9BF6FF, 0xFF5CFFB0, 0xFFFFB547, 0xFFE8E8F0, 0xFFFFD84A, 0xFFFF5E7E};
    /** Hull outlines, pointing along +x. */
    static final float[][] SHAPE = {
            {17, 0, -10, -11, -5, 0, -10, 11},
            {21, 0, -12, -6, -6, 0, -12, 6},
            {14, 0, 6, -12, -12, -12, -8, 0, -12, 12, 6, 12},
            {18, -3, 8, -12, -10, -9, -4, 0, -10, 9, 8, 12, 18, 3, 6, 0},
            {15, 0, 7, -10, -9, -10, -13, 0, -9, 10, 7, 10},
            {16, 0, 4, -8, 4, -13, -12, -13, -8, 0, -12, 13, 4, 13, 4, 8},
    };
}

/** Real-time research: each level takes real minutes/hours and keeps running while the app is closed. */
final class Research {
    static final int COUNT = 8;
    static final int DRILL = 0, MARKET = 1, CELLS = 2, DRONEAI = 3, INTEL = 4, SENSORS = 5, OVERCLOCK = 6, FORGE = 7;
    static final String[] NAME = {"Deep Drilling", "Market Analysis", "Energy Cells", "Drone AI", "Boss Intel",
            "Long-Range Sensors", "Overclock", "Quantum Forge"};
    static final String[] DESC = {"+10% damage", "+10% coins", "+0.5 energy per energy hex", "+20% drone damage",
            "+15s boss timer", "+10% offline rate, +1h offline cap", "+5% fire rate", "+15% gear stats"};
    static final int[] MAX = {10, 10, 5, 8, 4, 5, 10, 5};
    static final int[] REQ = {-1, -1, -1, DRILL, DRILL, MARKET, DRONEAI, CELLS};
    static final int[] REQ_LVL = {0, 0, 0, 2, 3, 2, 3, 2};
    static final double[] MINUTES = {5, 5, 10, 15, 20, 15, 30, 30};
    static final int UNLOCK_SECTOR = 4;

    static long durationMs(int i, int lvl) { return (long) (MINUTES[i] * 60000 * Math.pow(1.6, lvl)); }

    static int cost(int i, int lvl) { return (int) Math.ceil(8 * Math.pow(1.4, lvl) * (REQ[i] < 0 ? 1 : 2)); }

    static String fmtDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return (s / 3600) + "h " + (s % 3600 / 60) + "m";
        if (s >= 60) return (s / 60) + "m " + (s % 60) + "s";
        return s + "s";
    }
}

/** Tap-to-collect boosts carried by comets. */
final class Boosts {
    static final int FRENZY = 0, GOLD = 1, OVERDRIVE = 2, ENERGY = 3;
    static final String[] NAME = {"FRENZY", "GOLD RUSH", "OVERDRIVE", "ENERGY CACHE"};
    static final String[] DESC = {"fire rate x2", "coins x3", "damage x2.5", "instant energy"};
    static final float[] TIME = {20, 20, 15, 0};
    static final int[] COLOR = {0xFFFF7AD9, 0xFFFFD84A, 0xFFFF5E3A, 0xFF9BF6FF};
}

final class Version {
    static final String NAME = "1.4.1";
}

/** Run upgrades: after each cleared planet pick 1 of 3. They stack, and reset on Core Collapse. */
final class Cards {
    static final int COUNT = 12;
    static final int DMG = 0, COIN = 1, RATE = 2, CHAIN = 3, REDEN = 4, CRIT = 5, CRITDMG = 6, GOLDV = 7, MORTAR = 8,
            DRONE = 9, BOSST = 10, NOVA = 11;
    static final String[] NAME = {"Hot Barrels", "Tax Haven", "Hair Trigger", "Arc Relay", "Volatile Cells", "Keen Optics",
            "Glass Edge", "Midas Touch", "Cluster Shells", "Drone Overclock", "Stopwatch", "Star Heart"};
    static final String[] DESC = {"+12% damage", "+15% coins", "+8% fire rate", "Chain Arc +1 jump",
            "red hexes give +0.5 energy", "+4% crit chance", "+0.5x crit damage", "gold hexes +50% value",
            "Arc Mortar fires +1 shell", "drones +25% damage", "+15s on boss timers", "Supernova recharges 15% faster"};
    static final int[] MAX = {99, 99, 99, 4, 6, 10, 10, 10, 3, 99, 6, 5};
    static final int[] COLOR = {0xFFFF5E3A, 0xFFFFD84A, 0xFFFF7AD9, 0xFF8AB4FF, 0xFFFF2D55, 0xFFFFE066, 0xFFE8E8F0,
            0xFFFFB547, 0xFFFF9F43, 0xFF9BF6FF, 0xFF7DF9FF, 0xFFC58BFF};
}

/** Weekly challenge rules. */
final class Mutators {
    static final int COUNT = 6;
    static final int FREEZE = 0, POWDER = 1, BLASTER = 2, GLASS = 3, TITAN = 4, DRONES = 5;
    static final String[] NAME = {"DEEP FREEZE", "POWDER KEG", "BLASTER ONLY", "GLASS CANNON", "TITAN WORLD", "DRONE WARS"};
    static final String[] DESC = {"every hex is frozen", "red hexes everywhere",
            "only the Pulse Blaster and Drill Lance fire", "x3 damage, but only 90 seconds",
            "hexes x3 tougher, coins x3", "drones x3 damage, ship weapons x0.5"};

    static int week() { return (int) (System.currentTimeMillis() / (7L * 24 * 3600 * 1000)); }
}

/** Trails, ship paint and explosion styles. Unlocked by achievements (index) or weekly challenges (-2). */
final class Cosmetics {
    static final int TRAIL = 0, PAINT = 1, BOOM = 2;
    static final String[] TYPE_NAME = {"ENGINE TRAIL", "SHIP PAINT", "EXPLOSIONS"};
    static final int[] TYPE = {TRAIL, TRAIL, TRAIL, TRAIL, TRAIL, TRAIL, PAINT, PAINT, PAINT, PAINT, PAINT, BOOM, BOOM, BOOM};
    static final String[] NAME = {"Ember", "Ice", "Toxic", "Rainbow", "Royal", "Void", "Hull colour", "Crimson", "Gold",
            "Mint", "Ghost", "Classic", "Plasma", "Confetti"};
    static final int[] COLOR = {0xFFFF9F43, 0xFF9BF6FF, 0xFFA6FF4D, 0, 0xFFC165FF, 0xFF5B2DFF, 0, 0xFFFF2D55, 0xFFFFD84A,
            0xFF5CFFB0, 0xFFE8E8F0, 0xFFFF6A3D, 0xFF7DF9FF, 0};
    /** -1 = default, -2 = weekly reward, else achievement index. */
    static final int[] ACH = {-1, 0, 2, 8, -2, -2, -1, 5, 3, 9, -2, -1, 7, -2};
    static final int COUNT = NAME.length;
}

/** Codex entries with a line of flavour each. */
final class Codex {
    static final String[] BLOCK = {"Crust Hex", "Gold Hex", "Energy Hex", "Red Hex", "Armor Plate", "Ore Vein",
            "Planet Heart", "Shield Node", "Turret"};
    static final String[] BLOCK_LORE = {
            "Ordinary planet matter. Tougher toward the core.",
            "Crystallised gold. Pays 12x - drones love it.",
            "Charged cells. Each one gives energy for skills and research.",
            "Unstable fuel. Blows up and takes its neighbours with it.",
            "Boss shell plating. Shrugs off 65% of damage, except from the Lance.",
            "Seams of rainbow ore. Pays 25x; finish a vein for a bonus.",
            "A boss planet's core. Break it and the world breaks too.",
            "Projects the Aegis shield. Destroy every node to break it.",
            "Gunship defences. Fires stun orbs - steer to dodge them."};
    static final String[] BOSS_LORE = {
            "An armoured giant. Crack the shell, then the heart.",
            "Hides behind a green shield dome powered by nodes in its mantle.",
            "A fortress world bristling with turrets that stun your ship.",
            "Two worlds pretending to be one. The heart holds them together."};
    static final String[] MOD_LORE = {"",
            "Thin gravity lets every shot travel half again as far.",
            "A magnetic field bends bolts toward the nearest hex.",
            "Ice coats each hex; frost absorbs damage but pays extra.",
            "The crust is riddled with volatile red cells.",
            "A rich world - gold hexes everywhere."};
}
