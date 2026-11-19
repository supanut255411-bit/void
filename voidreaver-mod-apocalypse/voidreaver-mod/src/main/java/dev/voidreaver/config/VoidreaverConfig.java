package dev.voidreaver.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

import dev.voidreaver.VoidreaverMod;

/**
 * All tunable values of the Voidreaver. Stored as JSON in {@code config/voidreaver.json}.
 * Server-authoritative values are read on the server; {@code screenShakeIntensity} and
 * {@code particleIntensity} are also read locally by each client for visual effects.
 */
public final class VoidreaverConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int CONFIG_VERSION = 3;
	private static VoidreaverConfig instance = new VoidreaverConfig();

	/** Lets new default values reach existing config files. 0 means the file predates versioning. */
	public int configVersion = 0;

	// ---- Chain (right-click) ----
	/** Maximum chain range in blocks. */
	public double chainMaxRange = 100.0;
	/** Chain tip speed in blocks per tick (10 = 200 blocks/second). */
	public double chainLaunchSpeed = 10.0;
	/** Extra hitbox margin (blocks) used when the chain sweeps for entities. */
	public double chainHitInflate = 0.25;
	/** The chain hooks targets whose hitbox is this many times bigger than normal (2 = double width and height). */
	public double chainHitboxScale = 2.0;
	/** Right-click cooldown in ticks (80 = exactly 4 seconds). */
	public int chainCooldownTicks = 80;
	/** Hard cap on blocks a single chain launch may destroy. */
	public int chainMaxBlocksBroken = 8;
	/** If false, blocks that have a block entity (chests, furnaces...) are never broken even if whitelisted. */
	public boolean chainCanBreakBlockEntities = false;
	/** Blocks the chain can destroy. "namespace:block" or "#namespace:tag". Everything else stops the chain. */
	public List<String> chainBreakableBlocks = new ArrayList<>(List.of(
			"#minecraft:leaves", "minecraft:glass", "minecraft:glass_pane", "minecraft:ice",
			"minecraft:cobweb", "minecraft:dirt", "minecraft:grass_block", "minecraft:sand",
			"minecraft:gravel", "minecraft:netherrack"));
	/** Where the visible chain leaves the mace head, relative to the eyes (forward / to the holding side / up). */
	public double chainOriginForward = 0.8;
	public double chainOriginSide = 0.45;
	public double chainOriginUp = 0.05;

	// ---- Pull ----
	/** Constant pull speed in blocks per tick (3.0 = 60 blocks/second). No acceleration, no slowdown on approach. */
	public double pullSpeed = 3.0;
	/**
	 * The pull ends when the two hitboxes are at most this many blocks apart. Slightly above zero so a target that steps away
	 * in the same tick the player arrives still counts as touched; the player is stopped exactly on its surface regardless.
	 */
	public double pullContactTolerance = 0.3;
	public int pullMaxTicks = 80;
	/** Cancel the pull if the gap shrinks by less than this over {@code pullStuckTicks} ticks (blocked by terrain). */
	public double pullStuckProgress = 0.12;
	public int pullStuckTicks = 8;

	// ---- Melee (left-click) ----
	public double meleeDamage = 50.0;
	public double meleeKnockback = 1.6;
	public double meleeUpwardKnockback = 0.35;
	/** Swings below this attack-strength (0..1) deal no damage. */
	public double meleeMinStrength = 0.25;
	/** Vanilla attack speed attribute modifier: -3.0 means about one swing per second. Needs a restart. */
	public double meleeAttackSpeedModifier = -3.0;

	// ---- Falling smash ----
	public double smashMinFallDistance = 1.5;
	public double smashMinStrength = 0.5;
	public double smashBaseDamage = 60.0;
	public double smashDamagePerBlock = 8.0;
	public double smashMaxCountedFall = 30.0;
	public double smashKnockback = 3.0;
	public double smashUpwardKnockback = 0.9;
	public int smashCooldownTicks = 30;
	/** Upward bounce given to the attacker after a smash. */
	public double smashBounce = 0.35;

	// ---- Shockwave ----
	public double shockwaveRadius = 10.0;
	public double shockwaveDamage = 24.0;
	/** Damage fraction left at the very edge of the shockwave. */
	public double shockwaveMinDamageFraction = 0.4;
	public double shockwaveKnockback = 2.4;
	public double shockwaveUpwardKnockback = 0.8;
	/** Ring expansion speed in blocks per tick. */
	public double shockwaveSpeed = 1.6;
	/** Entities higher than this above the impact point are not hit. */
	public double shockwaveVerticalReach = 4.0;
	public boolean shockwaveHurtsOwner = false;

	// ---- Apocalypse Stomp (ultimate: hold Shift + right-click) ----
	public int ultimateCooldownTicks = 1200;
	public int ultimateChargeTicks = 20;
	/** How far above the launch point the player rises. */
	public double ultimateAscentHeight = 45.0;
	public double ultimateAscentSpeed = 2.6;
	public int ultimateSuspensionTicks = 30;
	public double ultimateDiveSpeed = 4.5;
	/** Ticks of protection after the impact while the wave and debris are still moving. */
	public int ultimateRecoveryTicks = 40;
	public int ultimateImmunityGraceTicks = 20;
	/** Radius of the shockwave in blocks. Never reduced for performance, only the processing is spread over time. */
	public double ultimateRadius = 100.0;
	/** Blocks per tick the wave front travels. */
	public double ultimateWaveSpeed = 3.5;
	/** 200 = 100 hearts. Applied in full at every distance inside the radius. */
	public double ultimateDamage = 200.0;
	/** Damage fraction at the very edge of the radius. 1.0 = the same 100 hearts everywhere. */
	public double ultimateMinDamageFraction = 1.0;
	/** The stomp ignores blocking: shields are knocked down and put on cooldown, then the hit lands in full. */
	public boolean ultimateBreaksShields = true;
	/** Ticks a broken shield stays disabled (players only). */
	public int ultimateShieldDisableTicks = 200;
	public double ultimateKnockback = 3.2;
	public double ultimateUpwardKnockback = 1.5;
	public double ultimateEntityVerticalReach = 64.0;
	/** Fraction of surface columns turned into debris (0.05 = one column in twenty). */
	public double ultimateBlockDensity = 0.07;
	/** How many blocks deep the surface layer goes. 1 = only the top block. Never digs deeper, so no crater. */
	public int ultimateSurfaceDepth = 2;
	public int ultimateMaxBlocksTotal = 3000;
	/** Falling-block entities spawned per tick, at most. */
	public int ultimateMaxBlocksPerTick = 80;
	/** Most falling-block debris entities alive on the server at once. */
	public int ultimateMaxFallingBlocks = 1100;
	/** Debris still in the air after this many ticks is removed. */
	public int ultimateDebrisLifetimeTicks = 600;
	public boolean ultimateBreakBlockEntities = false;
	/** Blocks the ultimate never touches. "namespace:block" or "#namespace:tag". */
	public List<String> ultimateProtectedBlocks = new ArrayList<>(List.of(
			"#minecraft:wither_immune", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:reinforced_deepslate",
			"minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault", "minecraft:beacon", "minecraft:respawn_anchor"));

	// ---- Effects (client side unless noted) ----
	/** 0 disables screen shake and ground rumble. */
	public double screenShakeIntensity = 1.0;
	public double particleIntensity = 1.0;
	/** LOW, MEDIUM, HIGH or ULTRA. Only changes how many particles are drawn, never gameplay. */
	public String particleQuality = "HIGH";
	public boolean impactFrameEnabled = true;
	/** Tilt the camera downward during the stomp's suspension and dive. */
	public boolean ultimateLookDown = true;
	/** Server side: scales all sound volumes. */
	public double soundVolume = 1.0;

	public static VoidreaverConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("voidreaver.json");
		VoidreaverConfig loaded = null;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				loaded = GSON.fromJson(reader, VoidreaverConfig.class);
			} catch (Exception e) {
				VoidreaverMod.LOGGER.error("Could not read {}, using defaults", path, e);
			}
		}

		if (loaded == null) {
			loaded = new VoidreaverConfig();
			loaded.configVersion = CONFIG_VERSION;
		} else if (loaded.configVersion < CONFIG_VERSION) {
			loaded.migrate();
		}

		loaded.sanitize();
		instance = loaded;

		// Always write back so new options appear in old config files.
		try (Writer writer = Files.newBufferedWriter(path)) {
			GSON.toJson(loaded, writer);
		} catch (IOException e) {
			VoidreaverMod.LOGGER.error("Could not write {}", path, e);
		}
	}

	/** Applies the new default values to a config file written by an older version. */
	private void migrate() {
		if (configVersion < 2) {
			chainCooldownTicks = 80; // 4 seconds
			chainMaxRange = 100.0;
			chainHitboxScale = 2.0;
			meleeDamage = 50.0;
		}

		if (configVersion < 3) {
			// Apocalypse Stomp: 100 hearts at every distance, shields broken, a little more surface debris.
			ultimateDamage = 200.0;
			ultimateMinDamageFraction = 1.0;
			ultimateBreaksShields = true;
			ultimateShieldDisableTicks = 200;
			ultimateBlockDensity = 0.07;
			ultimateMaxBlocksTotal = 3000;
			ultimateMaxBlocksPerTick = 80;
			ultimateMaxFallingBlocks = 1100;
		}

		configVersion = CONFIG_VERSION;
	}

	private void sanitize() {
		if (chainBreakableBlocks == null) {
			chainBreakableBlocks = new ArrayList<>();
		}

		chainMaxRange = clamp(chainMaxRange, 1.0, 256.0);
		chainLaunchSpeed = clamp(chainLaunchSpeed, 1.0, 64.0);
		chainHitInflate = clamp(chainHitInflate, 0.0, 2.0);
		chainHitboxScale = clamp(chainHitboxScale, 1.0, 8.0);
		chainCooldownTicks = (int) clamp(chainCooldownTicks, 0, 72000);
		chainMaxBlocksBroken = (int) clamp(chainMaxBlocksBroken, 0, 64);
		pullSpeed = clamp(pullSpeed, 0.2, 8.0);
		pullContactTolerance = clamp(pullContactTolerance, 0.0, 1.0);
		pullMaxTicks = (int) clamp(pullMaxTicks, 5, 600);
		pullStuckTicks = (int) clamp(pullStuckTicks, 2, 100);
		shockwaveRadius = clamp(shockwaveRadius, 1.0, 64.0);
		shockwaveSpeed = clamp(shockwaveSpeed, 0.2, 8.0);
		shockwaveMinDamageFraction = clamp(shockwaveMinDamageFraction, 0.0, 1.0);
		screenShakeIntensity = clamp(screenShakeIntensity, 0.0, 5.0);
		particleIntensity = clamp(particleIntensity, 0.0, 5.0);
		soundVolume = clamp(soundVolume, 0.0, 5.0);
		ultimateCooldownTicks = (int) clamp(ultimateCooldownTicks, 0, 72000);
		ultimateChargeTicks = (int) clamp(ultimateChargeTicks, 1, 200);
		ultimateAscentHeight = clamp(ultimateAscentHeight, 5.0, 200.0);
		ultimateAscentSpeed = clamp(ultimateAscentSpeed, 0.5, 6.0);
		ultimateSuspensionTicks = (int) clamp(ultimateSuspensionTicks, 1, 60);
		ultimateDiveSpeed = clamp(ultimateDiveSpeed, 0.5, 8.0);
		ultimateRecoveryTicks = (int) clamp(ultimateRecoveryTicks, 1, 400);
		ultimateImmunityGraceTicks = (int) clamp(ultimateImmunityGraceTicks, 0, 400);
		ultimateRadius = clamp(ultimateRadius, 5.0, 512.0);
		ultimateWaveSpeed = clamp(ultimateWaveSpeed, 0.5, 16.0);
		ultimateMinDamageFraction = clamp(ultimateMinDamageFraction, 0.0, 1.0);
		ultimateDamage = clamp(ultimateDamage, 0.0, 100000.0);
		ultimateShieldDisableTicks = (int) clamp(ultimateShieldDisableTicks, 0, 72000);
		ultimateEntityVerticalReach = clamp(ultimateEntityVerticalReach, 4.0, 320.0);
		ultimateBlockDensity = clamp(ultimateBlockDensity, 0.0, 1.0);
		ultimateSurfaceDepth = (int) clamp(ultimateSurfaceDepth, 1, 3);
		ultimateMaxBlocksTotal = (int) clamp(ultimateMaxBlocksTotal, 0, 20000);
		ultimateMaxBlocksPerTick = (int) clamp(ultimateMaxBlocksPerTick, 1, 512);
		ultimateMaxFallingBlocks = (int) clamp(ultimateMaxFallingBlocks, 0, 5000);
		ultimateDebrisLifetimeTicks = (int) clamp(ultimateDebrisLifetimeTicks, 40, 6000);
		if (ultimateProtectedBlocks == null) {
			ultimateProtectedBlocks = new ArrayList<>();
		}
		if (particleQuality == null) {
			particleQuality = "HIGH";
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
