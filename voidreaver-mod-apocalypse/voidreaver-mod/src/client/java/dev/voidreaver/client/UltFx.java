package dev.voidreaver.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.voidreaver.chain.ChainGeometry;
import dev.voidreaver.client.fx.Emitters;
import dev.voidreaver.client.fx.Fx;
import dev.voidreaver.config.VoidreaverConfig;
import dev.voidreaver.net.UltPhasePayload;

/**
 * Apocalypse Stomp visuals around the player, one distinct, layered look per phase. Everything is driven by the server's
 * phase events and by the player's real movement (positions and speeds are measured every tick), never by a fixed script:
 * <ul>
 * <li>CHARGE: violet aura, orbiting fragments, energy streaks into the mace, the ground lifting and drifting towards the player.</li>
 * <li>ASCENT: a launch burst at the origin (flash, rings, debris, dust) and a wide multi-stream column that follows the player.</li>
 * <li>SUSPENSION: rotating rings and orbiting fragments, streams into the weapon, a pattern and pulses on the ground far below.</li>
 * <li>DESCENT: the void meteor. Streak length, density and fragment speed all scale with the measured speed, and the
 * energy tightens around the mace just before the ground (detected from the real terrain).</li>
 * </ul>
 * For the local player it also publishes the FOV boost and the screen-edge strength used by the camera mixin and the HUD.
 */
public final class UltFx {
	private static final double TAU = Math.PI * 2.0;
	private static final int DARK_VAPOR = Fx.argb(150, 42, 15, 92);
	private static final int VAPOR_FADE = Fx.argb(0, 20, 6, 48);
	private static final int GLOW_SOFT = Fx.argb(120, 200, 140, 255);

	private static final class Entry {
		int phase;
		int age;
		Vec3 last;
		Vec3 origin;
		double groundY = Double.NaN;
		boolean anticipated;
	}

	private static final Map<Integer, Entry> ACTIVE = new HashMap<>();

	// Local-player camera state, smoothed every tick. Read by the camera mixin and the HUD.
	private static float fovBoost;
	private static float edge;
	private static float streaks;

	private UltFx() { }

	static void clear() {
		ACTIVE.clear();
		fovBoost = 0.0f;
		edge = 0.0f;
		streaks = 0.0f;
	}

	/** Extra field of view in degrees (0 when the stomp is not running for the local player). */
	public static float fovBoost() {
		return fovBoost;
	}

	/** 0..1 strength of the purple-white screen-edge glow. */
	static float edge() {
		return edge;
	}

	/** 0..1 strength of the screen-space speed streaks. */
	static float streaks() {
		return streaks;
	}

	static void handle(UltPhasePayload payload) {
		if (payload.phase() == UltPhasePayload.END) {
			ACTIVE.remove(payload.ownerId());
			return;
		}

		Entry entry = ACTIVE.computeIfAbsent(payload.ownerId(), id -> new Entry());
		entry.phase = payload.phase();
		entry.age = 0;
		entry.anticipated = false;

		Minecraft client = Minecraft.getInstance();

		if (client.level != null && client.level.getEntity(payload.ownerId()) instanceof Player owner) {
			entry.last = owner.position();

			if (payload.phase() == UltPhasePayload.CHARGE) {
				entry.origin = owner.position();
			}

			if (payload.phase() == UltPhasePayload.ASCENT) {
				if (entry.origin == null) {
					entry.origin = owner.position();
				}

				launchBurst(client.level, entry.origin, owner);
			}

			if (payload.phase() == UltPhasePayload.SUSPENSION) {
				suspensionEntry(owner);
			}

			if (client.player != null && owner.getId() == client.player.getId()) {
				ScreenShake.add(phaseShake(payload.phase()), payload.phase() == UltPhasePayload.ASCENT ? 16 : 10);
			}
		}
	}

	private static float phaseShake(int phase) {
		return switch (phase) {
			case UltPhasePayload.ASCENT -> 1.4f;
			case UltPhasePayload.DESCENT -> 0.8f;
			case UltPhasePayload.SUSPENSION -> 0.5f;
			default -> 0.3f;
		};
	}

	static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			ACTIVE.clear();
			fovBoost = 0.0f;
			edge = 0.0f;
			streaks = 0.0f;
			return;
		}

		float targetFov = 0.0f;
		float targetEdge = 0.0f;
		float targetStreaks = 0.0f;
		Iterator<Map.Entry<Integer, Entry>> iterator = ACTIVE.entrySet().iterator();

		while (iterator.hasNext()) {
			Map.Entry<Integer, Entry> mapEntry = iterator.next();
			Entity entity = level.getEntity(mapEntry.getKey());

			if (!(entity instanceof Player owner) || owner.isRemoved()) {
				iterator.remove();
				continue;
			}

			Entry entry = mapEntry.getValue();
			entry.age++;
			Vec3 now = owner.position();
			Vec3 moved = entry.last == null ? Vec3.ZERO : now.subtract(entry.last);
			entry.last = now;
			double speed = moved.length();
			Vec3 head = ChainGeometry.headPosition(owner, true, 1.0f);

			switch (entry.phase) {
				case UltPhasePayload.CHARGE -> charge(level, owner, entry, now, head);
				case UltPhasePayload.ASCENT -> ascent(level, owner, entry, now, moved, speed);
				case UltPhasePayload.SUSPENSION -> suspension(level, owner, entry, now, head);
				case UltPhasePayload.DESCENT -> descent(level, owner, entry, now, moved, speed, head);
				default -> { }
			}

			if (client.player != null && owner.getId() == client.player.getId()) {
				double fraction = Math.min(1.0, speed / 4.5);

				switch (entry.phase) {
					case UltPhasePayload.CHARGE -> targetEdge = Math.max(targetEdge, Math.min(1.0f, entry.age / 20.0f) * 0.22f);
					case UltPhasePayload.ASCENT -> {
						targetFov = Math.max(targetFov, (float) (fraction * 16.0));
						targetEdge = Math.max(targetEdge, 0.4f);
						targetStreaks = Math.max(targetStreaks, (float) fraction * 0.6f);
					}
					case UltPhasePayload.SUSPENSION -> targetEdge = Math.max(targetEdge, 0.3f + 0.3f * Math.min(1.0f, entry.age / 30.0f));
					case UltPhasePayload.DESCENT -> {
						targetFov = Math.max(targetFov, (float) (fraction * 30.0) + (entry.anticipated ? 6.0f : 0.0f));
						targetEdge = Math.max(targetEdge, (float) (0.35 + 0.65 * fraction));
						targetStreaks = Math.max(targetStreaks, (float) fraction);
					}
					default -> { }
				}

				// Let the local player see the battlefield below, and keep the weapon pointing at the ground during the dive.
				if (VoidreaverConfig.get().ultimateLookDown
						&& (entry.phase == UltPhasePayload.SUSPENSION || entry.phase == UltPhasePayload.DESCENT)) {
					float pitch = client.player.getXRot();
					float target = entry.phase == UltPhasePayload.DESCENT ? 82.0f : 62.0f;
					client.player.setXRot(pitch + (target - pitch) * 0.18f);
					client.player.xRotO = client.player.getXRot();
				}
			}
		}

		// Smooth in, release out: nothing here can stay on once the stomp is over (targets fall back to 0).
		fovBoost += (targetFov - fovBoost) * (targetFov > fovBoost ? 0.35f : 0.12f);
		edge += (targetEdge - edge) * (targetEdge > edge ? 0.3f : 0.1f);
		streaks += (targetStreaks - streaks) * (targetStreaks > streaks ? 0.35f : 0.15f);

		if (fovBoost < 0.02f) {
			fovBoost = 0.0f;
		}

		if (edge < 0.01f) {
			edge = 0.0f;
		}

		if (streaks < 0.01f) {
			streaks = 0.0f;
		}
	}

	// ------------------------------------------------------------------ helpers

	/** Y of the first solid surface below a point (top face), or NaN if there is none within {@code depth} blocks. */
	private static double groundBelow(ClientLevel level, double x, double startY, double z, int depth) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int by = (int) Math.floor(startY);

		for (int d = 0; d <= depth; d++) {
			pos.set(bx, by - d, bz);

			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return by - d + 1.0;
			}
		}

		return Double.NaN;
	}

	/** Orbiting angular fragments: respawned every tick with a short life, so they travel on a circle around a centre. */
	private static void orbitFragments(Vec3 c, int fragments, double radius, double spin, double tilt, double size, int color, int fadeColor, int life) {
		for (int i = 0; i < fragments; i++) {
			double a = spin + i * TAU / fragments;
			double ca = Math.cos(a);
			double sa = Math.sin(a);
			double x = c.x + ca * radius;
			double z = c.z + sa * radius;
			double y = c.y + sa * radius * tilt;
			double step = 0.45 * radius * 0.18;
			Fx.spawn(Fx.DEBRIS, x, y, z, -sa * step, ca * step * tilt, ca * step, life, size, size * 0.8, color, fadeColor, 0.0, 1.0, (byte) 0);
		}
	}

	// ------------------------------------------------------------------ CHARGE

	private static void charge(ClientLevel level, Player owner, Entry entry, Vec3 now, Vec3 head) {
		double progress = Math.min(1.0, entry.age / 20.0);
		double spin = entry.age * (0.35 + progress * 0.4);

		// Weapon: energy gathers into the head from every direction, faster and denser towards launch.
		for (int i = 0, count = Fx.nu(8 + 14 * progress); i < count; i++) {
			Vec3 offset = new Vec3(Fx.rnds(), Fx.rnds(), Fx.rnds()).normalize().scale(2.0 + Fx.rnd() * 2.6);
			Vec3 v = offset.scale(-1.0 / 10.0);
			Fx.spawn(Fx.ENERGY, head.x + offset.x, head.y + offset.y, head.z + offset.z, v.x, v.y, v.z, 10, 0.34, 0.05, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 1.0, (byte) 0);
		}

		// The glow at the mace head grows brighter, and small violet fragments orbit it.
		Fx.spawn(Fx.FLASH, head.x, head.y, head.z, 0, 0, 0, 2, 0.6 + progress * 2.2, 1.0 + progress * 3.0, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		orbitFragments(head, 6, 0.5 + 0.3 * progress, spin, 0.45, 0.1 + 0.05 * progress, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET), 3);
		orbitFragments(head, 4, 0.9 + 0.3 * progress, -spin * 0.8, 0.2, 0.08, Fx.WHITE_V, Fx.fade(Fx.VIOLET_LO), 3);

		if (entry.age % 3 == 0) {
			Fx.ring(head.x, head.y, head.z, Math.sin(entry.age * 0.3), 1.0, Math.cos(entry.age * 0.3), 0.3, 1.3 + progress * 0.8, 0.07, 8, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
		}

		// Player: a violet aura over the whole body, rotating motes, streaks running into the mace.
		for (int i = 0, count = Fx.nu(6 + 8 * progress); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = 0.65 + Fx.rnd() * 0.25;
			Fx.spawn(Fx.ENERGY, now.x + Math.cos(a) * r, now.y + Fx.rnd() * 1.9, now.z + Math.sin(a) * r, -Math.sin(a) * 0.09, 0.03 + Fx.rnd() * 0.05, Math.cos(a) * 0.09,
					10, 0.34, 0.1, GLOW_SOFT, Fx.fade(Fx.VIOLET_LO), 0.0, 0.97, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(2 + 4 * progress); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Vec3 from = new Vec3(now.x + Math.cos(a) * 0.8, now.y + 0.2 + Fx.rnd() * 1.6, now.z + Math.sin(a) * 0.8);
			Vec3 v = head.subtract(from).scale(1.0 / 6.0);
			Fx.spawn(Fx.STREAK, from.x, from.y, from.z, v.x, v.y, v.z, 6, 0.12, 0.02, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		}

		// Visible "distortion" around the silhouette: thin tilted rings that hug the body and wobble.
		if (entry.age % 4 == 0) {
			Fx.ring(now.x, now.y, now.z, Math.sin(entry.age * 0.5) * 0.5, 1.0, Math.cos(entry.age * 0.5) * 0.5, 0.9, 1.25, 0.06, 10, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET), owner.getId(), 0.3 + (entry.age % 8) * 0.2);
		}

		// Environment: the ground responds. Specks lift, dust slides towards the player, flat ripples run across, the light pulses.
		for (int i = 0, count = Fx.nu(5 + 10 * progress); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = 2.0 + Fx.rnd() * (6.0 + 5.0 * progress);
			double x = now.x + Math.cos(a) * r;
			double z = now.z + Math.sin(a) * r;
			double y = ShockwaveFx.groundY(level, x, z, now.y);
			int color = Emitters.groundColor(level, x, y, z);

			if (Fx.rnd() < 0.55f) {
				Fx.spawn(Fx.DEBRIS, x, y + 0.1, z, 0.0, 0.04 + Fx.rnd() * 0.08, 0.0, 26 + (int) (Fx.rnd() * 14), 0.07 + Fx.rnd() * 0.07, 0.05, color, (color & 0x00FFFFFF) | 0x80000000, -0.002, 0.97, (byte) 0);
			} else {
				Fx.spawn(Fx.DUST, x, y + 0.2, z, -Math.cos(a) * 0.06, 0.01, -Math.sin(a) * 0.06, 22, 0.6, 1.6, (color & 0x00FFFFFF) | 0x80000000, color & 0x00FFFFFF, 0.0, 0.95, (byte) 0);
			}
		}

		if (entry.age % 5 == 0) {
			double ground = ShockwaveFx.groundY(level, now.x, now.z, now.y);
			Fx.ring(now.x, ground + 0.12, now.z, 0, 1, 0, 1.0, 6.0 + progress * 6.0, 0.18, 16, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
			Fx.spawn(Fx.FLASH, now.x, ground + 0.3, now.z, 0, 0, 0, 8, 3.0 + progress * 5.0, 1.5, Fx.argb(70 + (int) (80 * progress), 154, 77, 255), Fx.fade(Fx.VIOLET_LO), 0.0, 1.0, (byte) 0);
		}
	}

	// ------------------------------------------------------------------ ASCENT

	/** The moment of launch: a flash and ground pulse at the origin, vertical rings, flung surface, dust and outward streaks. */
	private static void launchBurst(ClientLevel level, Vec3 origin, Player owner) {
		double ground = ShockwaveFx.groundY(level, origin.x, origin.z, origin.y);
		int color = Emitters.groundColor(level, origin.x, ground, origin.z);
		int dust = (color & 0x00FFFFFF) | 0xC0000000;

		Fx.spawn(Fx.FLASH, origin.x, ground + 0.4, origin.z, 0, 0, 0, 6, 5.0, 16.0, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);

		// Circular surface pulse, then taller rings that climb the column.
		Fx.ring(origin.x, ground + 0.15, origin.z, 0, 1, 0, 1.0, 22.0, 1.6, 12, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
		Fx.ring(origin.x, ground + 0.15, origin.z, 0, 1, 0, 1.0, 36.0, 0.9, 18, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);

		for (int i = 0; i < 5; i++) {
			Fx.ring(origin.x, ground + 0.5 + i * 1.8, origin.z, 0, 1, 0, 1.0, 7.0 + i * 2.5, 0.5, 10 + i * 3, i % 2 == 0 ? Fx.WHITE_V : Fx.VIOLET_HI, Fx.fade(Fx.VIOLET), -1, 0.0);
		}

		// Upward burst: a fan of long streaks and a bright core.
		for (int i = 0, count = Fx.nu(46); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = Fx.rnd() * 1.6;
			Fx.spawn(Fx.STREAK, origin.x + Math.cos(a) * r, ground + 0.3, origin.z + Math.sin(a) * r, Math.cos(a) * 0.15, 1.4 + Fx.rnd() * 2.4, Math.sin(a) * 0.15,
					14 + (int) (Fx.rnd() * 8), 0.22, 0.03, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.97, (byte) 0);
		}

		// Outward energy streaks along the surface.
		for (int i = 0, count = Fx.nu(40); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double v = 1.2 + Fx.rnd() * 2.4;
			Fx.spawn(Fx.STREAK, origin.x, ground + 0.25, origin.z, Math.cos(a) * v, 0.02, Math.sin(a) * v, 10 + (int) (Fx.rnd() * 6), 0.18, 0.02, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.95, (byte) 0);
		}

		// Surface flung upward, and a wide dust cloud.
		for (int i = 0, count = Fx.nu(80); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double v = 0.15 + Fx.rnd() * 0.8;
			Fx.spawn(Fx.DEBRIS, origin.x + Math.cos(a) * 0.8, ground + 0.4, origin.z + Math.sin(a) * 0.8, Math.cos(a) * v, 0.6 + Fx.rnd() * 1.3, Math.sin(a) * v,
					40 + (int) (Fx.rnd() * 30), 0.16 + Fx.rnd() * 0.16, 0.14, color, (color & 0x00FFFFFF) | 0xE0000000, 0.06, 0.98, Fx.COLLIDE);
		}

		for (int i = 0, count = Fx.nu(70); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double v = 0.2 + Fx.rnd() * 0.8;
			Fx.spawn(Fx.DUST, origin.x, ground + 0.3, origin.z, Math.cos(a) * v, 0.04 + Fx.rnd() * 0.25, Math.sin(a) * v, 30 + (int) (Fx.rnd() * 30), 1.0, 5.0, dust, color & 0x00FFFFFF, -0.001, 0.92, (byte) 0);
		}

		// Violet vapour left hanging over the launch point.
		for (int i = 0, count = Fx.nu(24); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.DUST, origin.x + Math.cos(a) * 1.5, ground + 0.5, origin.z + Math.sin(a) * 1.5, Math.cos(a) * 0.1, 0.08 + Fx.rnd() * 0.15, Math.sin(a) * 0.1, 40, 1.5, 6.0, DARK_VAPOR, VAPOR_FADE, 0.0, 0.93, (byte) 0);
		}
	}

	private static void ascent(ClientLevel level, Player owner, Entry entry, Vec3 now, Vec3 moved, double speed) {
		double strength = Math.min(1.0, entry.age / 10.0);
		double spin = entry.age * 0.55;

		// Stream 1: three overlapping spirals around the body, widening with speed. They stay where the player was.
		double width = 0.8 + Math.min(1.0, speed / 2.6) * 0.9;

		for (int s = 0; s < 3; s++) {
			double a = spin + s * TAU / 3.0;
			for (int i = 0, count = Fx.nu(2 + 2 * strength); i < count; i++) {
				double jitter = Fx.rnds() * 0.5;
				Fx.spawn(Fx.ENERGY, now.x + Math.cos(a + jitter) * width, now.y + Fx.rnd() * 0.9, now.z + Math.sin(a + jitter) * width,
						-Math.sin(a) * 0.16, -0.12 - Fx.rnd() * 0.2, Math.cos(a) * 0.16, 16, 0.34, 0.06, s == 1 ? Fx.WHITE_V : Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.96, (byte) 0);
			}
		}

		// Stream 2: long vertical light streaks, longer the faster the player really moves.
		for (int i = 0, count = Fx.nu(8 + 12 * strength + speed * 3.0); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = Fx.rnd() * 0.9;
			Fx.spawn(Fx.STREAK, now.x + Math.cos(a) * r, now.y + Fx.rnd() * 0.8, now.z + Math.sin(a) * r, 0.0, -(0.4 + Fx.rnd() * 0.9 + speed * 0.14), 0.0,
					14, 0.2 + Fx.rnd() * 0.12, 0.02, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.97, (byte) 0);
		}

		// Stream 3: bright white-violet sparks thrown out sideways.
		for (int i = 0, count = Fx.nu(8); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.SPARK, now.x + Math.cos(a) * 0.5, now.y + 0.4 + Fx.rnd(), now.z + Math.sin(a) * 0.5, Math.cos(a) * (0.1 + Fx.rnd() * 0.3), -0.2 - Fx.rnd() * 0.5, Math.sin(a) * (0.1 + Fx.rnd() * 0.3),
					10, 0.09, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.94, (byte) 0);
		}

		// Large geometric fragments shed from the column, and smaller glowing ones.
		for (int i = 0, count = Fx.nu(3); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = 0.8 + Fx.rnd() * 1.4;
			Fx.spawn(Fx.DEBRIS, now.x + Math.cos(a) * r, now.y + Fx.rnd() * 1.5, now.z + Math.sin(a) * r, Math.cos(a) * 0.05, -0.1, Math.sin(a) * 0.05,
					18 + (int) (Fx.rnd() * 10), 0.26 + Fx.rnd() * 0.18, 0.1, Fx.VIOLET_LO, Fx.fade(Fx.VIOLET_DARK), 0.015, 0.96, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(6); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.ENERGY, now.x + Math.cos(a) * 1.4, now.y + Fx.rnd() * 1.6, now.z + Math.sin(a) * 1.4, 0.0, -0.1, 0.0, 12, 0.18, 0.04, Fx.VIOLET, Fx.fade(Fx.VIOLET_LO), 0.0, 0.97, (byte) 0);
		}

		// Dark purple vapour clouds left behind in the player's wake.
		for (int i = 0, count = Fx.nu(3 + 3 * strength); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.DUST, now.x + Math.cos(a) * 0.6, now.y + 0.2, now.z + Math.sin(a) * 0.6, Math.cos(a) * 0.04, -0.03, Math.sin(a) * 0.04, 34, 1.2, 4.2, DARK_VAPOR, VAPOR_FADE, 0.0, 0.94, (byte) 0);
		}

		// Rotating rings around the climbing player.
		if (entry.age % 2 == 0) {
			double tilt = entry.age * 0.35;
			Fx.ring(now.x, now.y + 0.9, now.z, Math.sin(tilt) * 0.5, 1.0, Math.cos(tilt) * 0.5, 1.4, 5.5 + 5.0 * strength, 0.3, 11, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
		}

		if (entry.age % 7 == 0) {
			Fx.spawn(Fx.FLASH, now.x, now.y, now.z, 0, 0, 0, 3, 1.8, 4.2, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		}

		// The launch point keeps reacting for a moment: ground-level energy pulses and a little rising dust.
		if (entry.origin != null && entry.age % 4 == 0 && entry.age <= 16) {
			double ground = ShockwaveFx.groundY(level, entry.origin.x, entry.origin.z, entry.origin.y);
			Fx.ring(entry.origin.x, ground + 0.15, entry.origin.z, 0, 1, 0, 2.0, 12.0 + entry.age, 0.5, 12, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);

			for (int i = 0, count = Fx.nu(8); i < count; i++) {
				double a = Fx.rnd() * TAU;
				Fx.spawn(Fx.DUST, entry.origin.x + Math.cos(a) * 2.0, ground + 0.3, entry.origin.z + Math.sin(a) * 2.0, Math.cos(a) * 0.3, 0.1 + Fx.rnd() * 0.2, Math.sin(a) * 0.3, 30, 1.0, 3.5, GLOW_SOFT, Fx.fade(Fx.VIOLET_DARK), 0.0, 0.93, (byte) 0);
			}
		}
	}

	// ------------------------------------------------------------------ SUSPENSION

	private static void suspensionEntry(Player owner) {
		double r = 3.0;
		Fx.ring(owner.getX(), owner.getY(), owner.getZ(), 0.35, 1.0, 0.1, 2.6, 2.6, 0.2, 60, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET), owner.getId(), 1.0);
		Fx.ring(owner.getX(), owner.getY(), owner.getZ(), -0.3, 1.0, -0.2, 3.4, 3.4, 0.16, 60, Fx.WHITE_V, Fx.fade(Fx.VIOLET_LO), owner.getId(), 1.0);
		Fx.ring(owner.getX(), owner.getY(), owner.getZ(), 0.1, 0.4, 1.0, r, r, 0.14, 60, Fx.VIOLET, Fx.fade(Fx.VIOLET_LO), owner.getId(), 1.0);
		Fx.ring(owner.getX(), owner.getY(), owner.getZ(), 1.0, 0.3, -0.2, r + 1.2, r + 1.2, 0.1, 60, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_DARK), owner.getId(), 1.0);
	}

	private static void suspension(ClientLevel level, Player owner, Entry entry, Vec3 now, Vec3 head) {
		double progress = Math.min(1.0, entry.age / 30.0);
		double spin = entry.age * 0.4;

		if (entry.age % 5 == 1 || Double.isNaN(entry.groundY)) {
			entry.groundY = groundBelow(level, now.x, now.y, now.z, 130);
		}

		// Around the player: large aura, large geometric fragments on two orbits, spiral sparks.
		for (int i = 0, count = Fx.nu(6 + 6 * progress); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = 0.8 + Fx.rnd() * 0.5;
			Fx.spawn(Fx.ENERGY, now.x + Math.cos(a) * r, now.y + Fx.rnd() * 2.0, now.z + Math.sin(a) * r, -Math.sin(a) * 0.08, 0.02, Math.cos(a) * 0.08, 10, 0.4, 0.12, GLOW_SOFT, Fx.fade(Fx.VIOLET_LO), 0.0, 0.97, (byte) 0);
		}

		Vec3 mid = new Vec3(now.x, now.y + 1.0, now.z);
		orbitFragments(mid, 6, 2.2 + progress * 0.6, spin, 0.35, 0.3, Fx.VIOLET_LO, Fx.fade(Fx.VIOLET_DARK), 3);
		orbitFragments(mid, 5, 3.0 + progress * 0.8, -spin * 0.7, 0.2, 0.22, Fx.VIOLET, Fx.fade(Fx.VIOLET_LO), 3);
		orbitFragments(mid, 8, 1.7, spin * 1.4, 0.5, 0.09, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 3);

		for (int i = 0, count = Fx.nu(5); i < count; i++) {
			double a = spin * 1.6 + i * 1.25 + Fx.rnd() * 0.3;
			double r = 1.4 + progress * 1.4;
			double y = now.y + 0.2 + ((entry.age + i * 7) % 24) * 0.1;
			Fx.spawn(Fx.SPARK, now.x + Math.cos(a) * r, y, now.z + Math.sin(a) * r, -Math.sin(a) * 0.3, 0.06, Math.cos(a) * 0.3, 7, 0.08, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.97, (byte) 0);
		}

		// Energy streams flowing into the weapon, denser every tick.
		for (int i = 0, count = Fx.nu(10 + 18 * progress); i < count; i++) {
			Vec3 offset = new Vec3(Fx.rnds(), Fx.rnds(), Fx.rnds()).normalize().scale(4.0 + Fx.rnd() * 6.0);
			Vec3 v = offset.scale(-1.0 / 10.0);
			Fx.spawn(Fx.ENERGY, head.x + offset.x, head.y + offset.y, head.z + offset.z, v.x, v.y, v.z, 10, 0.34, 0.05, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 1.0, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(4 + 4 * progress); i < count; i++) {
			Vec3 offset = new Vec3(Fx.rnds(), Fx.rnds(), Fx.rnds()).normalize().scale(3.0 + Fx.rnd() * 3.0);
			Vec3 v = offset.scale(-1.0 / 6.0);
			Fx.spawn(Fx.STREAK, head.x + offset.x, head.y + offset.y, head.z + offset.z, v.x, v.y, v.z, 6, 0.14, 0.02, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		}

		// Weapon: growing core glow, fragments and several rings turning around the head.
		Fx.spawn(Fx.FLASH, head.x, head.y, head.z, 0, 0, 0, 2, 1.0 + progress * 3.2, 1.6 + progress * 4.0, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		orbitFragments(head, 6, 0.6 + 0.4 * progress, -spin * 1.5, 0.5, 0.12, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET), 3);

		if (entry.age % 3 == 0) {
			double t = entry.age * 0.5;
			Fx.ring(head.x, head.y, head.z, Math.sin(t), 0.6, Math.cos(t), 0.4, 1.8 + progress, 0.08, 9, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
			Fx.ring(head.x, head.y, head.z, Math.cos(t), -0.5, Math.sin(t), 0.5, 2.4 + progress, 0.06, 9, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
		}

		// Battlefield below: a faint circular pattern, violet pulses on the far terrain, specks rising, dust drifting outward.
		if (!Double.isNaN(entry.groundY)) {
			double gy = entry.groundY;

			if (entry.age % 8 == 0) {
				double reach = 8.0 + 14.0 * progress;
				Fx.ring(now.x, gy + 0.15, now.z, 0, 1, 0, reach * 0.5, reach, 0.35, 16, Fx.VIOLET, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
				Fx.ring(now.x, gy + 0.15, now.z, 0, 1, 0, reach * 0.3, reach * 0.55, 0.22, 16, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_DARK), -1, 0.0);
			}

			if (entry.age % 10 == 0) {
				Fx.spawn(Fx.FLASH, now.x, gy + 0.5, now.z, 0, 0, 0, 10, 8.0 + 14.0 * progress, 4.0, Fx.argb(60 + (int) (60 * progress), 154, 77, 255), Fx.fade(Fx.VIOLET_LO), 0.0, 1.0, (byte) 0);
			}

			// A geometric pattern: points on a circle that light up one after another around the centre.
			if (entry.age % 2 == 0) {
				int spokes = 8;
				double ring = 6.0 + 10.0 * progress;

				for (int i = 0; i < spokes; i++) {
					double a = TAU * i / spokes + entry.age * 0.03;
					Fx.spawn(Fx.ENERGY, now.x + Math.cos(a) * ring, gy + 0.3, now.z + Math.sin(a) * ring, 0.0, 0.06, 0.0, 10, 0.7, 0.2, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.97, (byte) 0);
				}
			}

			for (int i = 0, count = Fx.nu(6 + 8 * progress); i < count; i++) {
				double a = Fx.rnd() * TAU;
				double r = 3.0 + Fx.rnd() * 24.0;
				double x = now.x + Math.cos(a) * r;
				double z = now.z + Math.sin(a) * r;
				double y = ShockwaveFx.groundY(level, x, z, gy);
				int color = Emitters.groundColor(level, x, y, z);

				if (Fx.rnd() < 0.5f) {
					Fx.spawn(Fx.DEBRIS, x, y + 0.1, z, 0.0, 0.05 + Fx.rnd() * 0.1, 0.0, 34, 0.09 + Fx.rnd() * 0.08, 0.05, color, (color & 0x00FFFFFF) | 0x80000000, -0.002, 0.97, (byte) 0);
				} else {
					Fx.spawn(Fx.DUST, x, y + 0.2, z, Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08, 30, 1.0, 3.2, (color & 0x00FFFFFF) | 0x80000000, color & 0x00FFFFFF, 0.0, 0.95, (byte) 0);
				}
			}
		}
	}

	// ------------------------------------------------------------------ DESCENT

	private static void descent(ClientLevel level, Player owner, Entry entry, Vec3 now, Vec3 moved, double speed, Vec3 head) {
		double strength = Math.min(1.0, speed / 4.5); // everything below scales with the measured fall speed
		double spin = entry.age * (0.8 + strength);

		// Real distance to the ground, refreshed every tick over the range that matters for the anticipation.
		double ground = groundBelow(level, now.x, now.y, now.z, (int) (speed * 5.0 + 12.0));

		if (!Double.isNaN(ground)) {
			entry.groundY = ground;
		}

		boolean close = !Double.isNaN(ground) && now.y - ground < speed * 4.0 + 3.0;

		if (close && !entry.anticipated) {
			entry.anticipated = true;
			ScreenShake.add(0.5f, 6);

			// Short anticipation pulse: a bright bloom and a tight ring at the weapon.
			Fx.spawn(Fx.FLASH, head.x, head.y, head.z, 0, 0, 0, 4, 2.0, 6.0, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
			Fx.ring(head.x, head.y, head.z, 0, 1, 0, 0.5, 5.0, 0.3, 6, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
		}

		// Main column: layered streaks of different lengths and widths trailing above the player.
		for (int i = 0, count = Fx.nu(14 + 26 * strength); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = Fx.rnd() * (0.5 + 0.6 * strength);
			double v = 0.9 + Fx.rnd() * 1.6 + speed * 0.5;
			Fx.spawn(Fx.STREAK, now.x + Math.cos(a) * r, now.y + 0.9 + Fx.rnd() * 1.6, now.z + Math.sin(a) * r, 0.0, v, 0.0,
					10 + (int) (Fx.rnd() * 5), 0.16 + Fx.rnd() * 0.2, 0.02, i % 3 == 0 ? Fx.VIOLET_HI : Fx.WHITE_V, Fx.fade(Fx.VIOLET_LO), 0.0, 0.98, (byte) 0);
		}

		// Two spiral energy streams around the body; the faster the fall, the tighter and faster they run.
		for (int s = 0; s < 2; s++) {
			double a = spin * (s == 0 ? 1.0 : -1.0) + s * Math.PI;
			double r = 1.0 - 0.35 * strength;

			for (int i = 0, count = Fx.nu(3 + 4 * strength); i < count; i++) {
				double aa = a + i * 0.8;
				Fx.spawn(Fx.ENERGY, now.x + Math.cos(aa) * r, now.y + 0.4 + i * 0.45, now.z + Math.sin(aa) * r, -Math.sin(aa) * 0.1, 0.9 + speed * 0.4, Math.cos(aa) * 0.1,
						9, 0.3, 0.06, s == 0 ? Fx.VIOLET_HI : Fx.WHITE_V, Fx.fade(Fx.VIOLET_LO), 0.0, 0.97, (byte) 0);
			}
		}

		// Rotating geometric fragments, faster with speed, and large glowing fragments.
		orbitFragments(new Vec3(now.x, now.y + 1.2, now.z), 5, 1.3, spin * 1.2, 0.0, 0.2 + 0.1 * strength, Fx.VIOLET_LO, Fx.fade(Fx.VIOLET_DARK), 3);

		for (int i = 0, count = Fx.nu(3 + 4 * strength); i < count; i++) {
			double a = Fx.rnd() * TAU;
			double r = 0.8 + Fx.rnd() * 1.2;
			Fx.spawn(Fx.DEBRIS, now.x + Math.cos(a) * r, now.y + 1.0, now.z + Math.sin(a) * r, 0.0, 0.7 + speed * 0.35, 0.0, 14, 0.24 + Fx.rnd() * 0.16, 0.08, Fx.VIOLET, Fx.fade(Fx.VIOLET_LO), 0.0, 0.98, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(2 + 3 * strength); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.ENERGY, now.x + Math.cos(a) * 0.7, now.y + 1.0 + Fx.rnd() * 3.0, now.z + Math.sin(a) * 0.7, 0.0, 0.5 + speed * 0.3, 0.0, 12, 0.9 + strength * 0.6, 0.2, Fx.VIOLET, Fx.fade(Fx.VIOLET_LO), 0.0, 0.97, (byte) 0);
		}

		// Fast sparks and dark atmospheric clouds torn off the column.
		for (int i = 0, count = Fx.nu(8 + 8 * strength); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.SPARK, now.x + Math.cos(a) * 0.9, now.y + 0.3 + Fx.rnd() * 1.8, now.z + Math.sin(a) * 0.9, Math.cos(a) * 0.12, 0.7 + speed * 0.45, Math.sin(a) * 0.12, 8, 0.09, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.97, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(2 + 3 * strength); i < count; i++) {
			double a = Fx.rnd() * TAU;
			Fx.spawn(Fx.DUST, now.x + Math.cos(a) * 1.0, now.y + 1.5 + Fx.rnd() * 3.0, now.z + Math.sin(a) * 1.0, Math.cos(a) * 0.1, 0.4 + speed * 0.2, Math.sin(a) * 0.1, 26, 1.4, 5.0, DARK_VAPOR, VAPOR_FADE, 0.0, 0.94, (byte) 0);
		}

		// The central glow concentrates the faster the dive: small and hot at full speed.
		Fx.spawn(Fx.ENERGY, now.x, now.y + 1.2, now.z, 0, 0.3, 0, 6, 0.7 + strength * 1.2, 0.3, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);

		if (entry.age % 2 == 0) {
			Fx.ring(now.x, now.y + 2.0 + Fx.rnd(), now.z, 0, 1, 0, 1.0, 2.6 + strength * 1.6, 0.14, 7, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
		}

		// Just before impact the energy tightens onto the mace head and the glow turns white.
		if (close) {
			for (int i = 0, count = Fx.nu(18); i < count; i++) {
				Vec3 offset = new Vec3(Fx.rnds(), Fx.rnds(), Fx.rnds()).normalize().scale(1.5 + Fx.rnd() * 2.5);
				Vec3 v = offset.scale(-1.0 / 5.0);
				Fx.spawn(Fx.ENERGY, head.x + offset.x, head.y + offset.y, head.z + offset.z, v.x, v.y, v.z, 5, 0.4, 0.1, Fx.WHITE_V, Fx.fade(Fx.VIOLET_HI), 0.0, 1.0, (byte) 0);
			}

			Fx.spawn(Fx.FLASH, head.x, head.y, head.z, 0, 0, 0, 2, 1.8, 4.0, Fx.WHITE_V, Fx.fade(Fx.VIOLET_HI), 0.0, 1.0, (byte) 0);

			if (!Double.isNaN(ground)) {
				// The ground ahead starts to light up under the incoming energy.
				Fx.spawn(Fx.FLASH, now.x, ground + 0.3, now.z, 0, 0, 0, 3, 6.0, 12.0, Fx.argb(110, 200, 140, 255), Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
			}
		}
	}
}
