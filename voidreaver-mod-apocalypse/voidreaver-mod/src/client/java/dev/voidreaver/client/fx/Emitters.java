package dev.voidreaver.client.fx;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Ability-specific particle recipes built on {@link Fx}. Each ability has its own look; all of it is created client-side. */
public final class Emitters {
	private Emitters() { }

	/** A unit vector close to {@code dir}; spread 0 = exactly dir, 1 = very wide. */
	private static Vec3 cone(Vec3 dir, double spread) {
		Vec3 v = new Vec3(dir.x + Fx.rnds() * spread, dir.y + Fx.rnds() * spread, dir.z + Fx.rnds() * spread);
		return v.lengthSqr() < 1.0E-6 ? dir : v.normalize();
	}

	private static Vec3 randomUnit() {
		Vec3 v = new Vec3(Fx.rnds(), Fx.rnds(), Fx.rnds());
		return v.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 1.0, 0.0) : v.normalize();
	}

	/** Block colour of the surface at a position, for dust and debris that looks like the ground it came from. */
	public static int groundColor(ClientLevel level, double x, double y, double z) {
		BlockPos pos = BlockPos.containing(x, y - 0.5, z);
		BlockState state = level.getBlockState(pos);

		if (state.isAir()) {
			return 0xFF7A6A90;
		}

		return 0xFF000000 | state.getMapColor(level, pos).col;
	}

	// ------------------------------------------------------------------ chain

	/** Concentrated burst at the mace head, shooting the way the chain goes. */
	public static void chainLaunch(Vec3 head, Vec3 dir) {
		for (int i = 0, count = Fx.n(28); i < count; i++) {
			Vec3 v = cone(dir, 0.35).scale(0.5 + Fx.rnd() * 1.5);
			Fx.spawn(Fx.SPARK, head.x, head.y, head.z, v.x, v.y, v.z, 8 + (int) (Fx.rnd() * 8), 0.09, 0.02, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.88, (byte) 0);
		}

		for (int i = 0, count = Fx.n(10); i < count; i++) {
			Vec3 v = cone(dir, 0.12).scale(2.0 + Fx.rnd() * 1.6);
			Fx.spawn(Fx.STREAK, head.x, head.y, head.z, v.x, v.y, v.z, 6, 0.14, 0.02, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.95, (byte) 0);
		}

		for (int i = 0, count = Fx.n(14); i < count; i++) {
			Vec3 v = randomUnit().scale(0.08 + Fx.rnd() * 0.14);
			Fx.spawn(Fx.ENERGY, head.x, head.y, head.z, v.x, v.y, v.z, 14, 0.34, 0.05, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.93, (byte) 0);
		}

		Fx.spawn(Fx.FLASH, head.x, head.y, head.z, 0.0, 0.0, 0.0, 3, 1.5, 2.4, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		Fx.ring(head.x, head.y, head.z, dir.x, dir.y, dir.z, 0.2, 2.4, 0.2, 8, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
	}

	/** Every tick while the chain is out: sparks and energy running along its real path, plus a trail behind the tip. */
	public static void chainFlight(Vec3 head, Vec3 tip, boolean hooked) {
		Vec3 delta = tip.subtract(head);
		double length = delta.length();

		if (length < 0.5) {
			return;
		}

		Vec3 dir = delta.scale(1.0 / length);

		for (int i = 0, count = Fx.n(3 + Math.min(4.0, length / 12.0)); i < count; i++) {
			double t = Fx.rnd();
			Vec3 p = head.add(delta.scale(t));

			if (!Fx.near(p.x, p.y, p.z, 70.0)) {
				continue;
			}

			Vec3 v = randomUnit().scale(0.05 + Fx.rnd() * 0.1);
			Fx.spawn(Fx.SPARK, p.x, p.y, p.z, v.x, v.y, v.z, 6 + (int) (Fx.rnd() * 5), 0.07, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.9, (byte) 0);
		}

		for (int i = 0, count = Fx.n(2); i < count; i++) {
			Vec3 p = head.add(delta.scale(Fx.rnd()));
			Vec3 v = dir.scale(0.3 + Fx.rnd() * 0.2);
			Fx.spawn(Fx.ENERGY, p.x, p.y, p.z, v.x, v.y, v.z, 10, 0.18, 0.03, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.96, (byte) 0);
		}

		// Directional trail behind the tip. It moves backwards along the chain, so nothing is left hanging in the air.
		if (!hooked) {
			for (int i = 0, count = Fx.n(3); i < count; i++) {
				Vec3 v = dir.scale(-(0.6 + Fx.rnd() * 0.8));
				Fx.spawn(Fx.STREAK, tip.x, tip.y, tip.z, v.x, v.y, v.z, 8, 0.15, 0.02, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.9, (byte) 0);
			}
		}
	}

	/** The chain bites: bright radial burst and a circular energy pulse at the exact hit location. */
	public static void hookImpact(Vec3 pos, Vec3 dir) {
		for (int i = 0, count = Fx.n(46); i < count; i++) {
			Vec3 v = randomUnit().scale(0.3 + Fx.rnd() * 1.0);
			Fx.spawn(Fx.SPARK, pos.x, pos.y, pos.z, v.x, v.y, v.z, 10 + (int) (Fx.rnd() * 9), 0.1, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.02, 0.9, (byte) 0);
		}

		for (int i = 0, count = Fx.n(16); i < count; i++) {
			Vec3 v = randomUnit().scale(0.1 + Fx.rnd() * 0.22);
			Fx.spawn(Fx.ENERGY, pos.x, pos.y, pos.z, v.x, v.y, v.z, 20, 0.3, 0.04, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.94, (byte) 0);
		}

		Fx.spawn(Fx.FLASH, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, 4, 1.8, 3.2, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		Fx.ring(pos.x, pos.y, pos.z, dir.x, dir.y, dir.z, 0.3, 3.2, 0.25, 10, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
	}

	/** While being pulled: speed lines and a trail along the real movement direction, stronger the faster the player goes. */
	public static void pullTrail(Vec3 pos, Vec3 motion) {
		double speed = motion.length();

		if (speed < 0.2) {
			return;
		}

		Vec3 back = motion.scale(-1.0 / speed);
		double boost = Math.min(2.0, speed / 1.5);

		for (int i = 0, count = Fx.n(5 + 4 * boost); i < count; i++) {
			Vec3 offset = randomUnit().scale(0.2 + Fx.rnd() * 0.7);
			Vec3 v = back.scale(0.4 + speed * 0.25 + Fx.rnd() * 0.3);
			Fx.spawn(Fx.STREAK, pos.x + offset.x, pos.y + 0.9 + offset.y, pos.z + offset.z, v.x, v.y, v.z, 8, 0.12, 0.02, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.94, (byte) 0);
		}

		for (int i = 0, count = Fx.n(3 + 3 * boost); i < count; i++) {
			Vec3 offset = randomUnit().scale(0.5);
			Vec3 v = back.scale(speed * 0.6).add(randomUnit().scale(0.08));
			Fx.spawn(Fx.SPARK, pos.x + offset.x, pos.y + 0.9 + offset.y, pos.z + offset.z, v.x, v.y, v.z, 7, 0.07, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 0.92, (byte) 0);
		}

		// Wind rushing past: thin pale lines in a ring around the player that rip backwards faster than the player moves.
		for (int i = 0, count = Fx.n(3 * boost); i < count; i++) {
			Vec3 offset = randomUnit().scale(1.0 + Fx.rnd() * 1.4);
			Vec3 v = back.scale(speed * 1.2);
			Fx.spawn(Fx.STREAK, pos.x + offset.x, pos.y + 0.9 + offset.y, pos.z + offset.z, v.x, v.y, v.z, 5, 0.05, 0.01, 0xB0E8D8FF, 0x00E8D8FF, 0.0, 1.0, (byte) 0);
		}
	}

	// ------------------------------------------------------------------ melee

	/** Hit at the real impact point, shaped by the swing direction. */
	public static void meleeHit(ClientLevel level, Vec3 pos, Vec3 dir, double strength) {
		double s = Math.max(0.4, strength);

		for (int i = 0, count = Fx.n(20 * s); i < count; i++) {
			Vec3 v = cone(dir, 0.9).scale(0.4 + Fx.rnd() * 1.3);
			Fx.spawn(Fx.SPARK, pos.x, pos.y, pos.z, v.x, v.y, v.z, 8 + (int) (Fx.rnd() * 8), 0.09, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.03, 0.9, (byte) 0);
		}

		for (int i = 0, count = Fx.n(6 * s); i < count; i++) {
			Vec3 v = cone(dir, 0.15).scale(1.4 + Fx.rnd() * 1.2);
			Fx.spawn(Fx.STREAK, pos.x, pos.y, pos.z, v.x, v.y, v.z, 6, 0.14, 0.02, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.93, (byte) 0);
		}

		int ground = groundColor(level, pos.x, pos.y - 0.8, pos.z);

		for (int i = 0, count = Fx.n(7 * s); i < count; i++) {
			Vec3 v = new Vec3(Fx.rnds() * 0.1, 0.03 + Fx.rnd() * 0.08, Fx.rnds() * 0.1).add(dir.scale(0.12));
			Fx.spawn(Fx.DUST, pos.x, pos.y - 0.4, pos.z, v.x, v.y, v.z, 20, 0.3, 1.1, (ground & 0x00FFFFFF) | 0xA0000000, (ground & 0x00FFFFFF), -0.001, 0.94, (byte) 0);
		}

		for (int i = 0, count = Fx.n(8 * s); i < count; i++) {
			Vec3 v = cone(dir, 1.0).scale(0.1 + Fx.rnd() * 0.3);
			Fx.spawn(Fx.ENERGY, pos.x, pos.y, pos.z, v.x, v.y, v.z, 14, 0.2, 0.03, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.93, (byte) 0);
		}

		Fx.spawn(Fx.FLASH, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, 3, 1.0 * s + 0.5, 2.0 * s + 0.8, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
	}

	// ------------------------------------------------------------------ impacts

	/**
	 * The first, fast layer of a big ground impact: a flash, a radial burst of sparks and streaks, flying fragments
	 * of the actual ground and a heavy dust ring. Slower secondary debris follows from the wave itself.
	 */
	public static void impactBurst(ClientLevel level, Vec3 center, double scale) {
		int ground = groundColor(level, center.x, center.y, center.z);
		int dustColor = (ground & 0x00FFFFFF) | 0xC0000000;

		Fx.spawn(Fx.FLASH, center.x, center.y + 0.5, center.z, 0.0, 0.0, 0.0, 6, 4.0 * scale, 12.0 * scale, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);

		for (int i = 0, count = Fx.n(70 * scale); i < count; i++) {
			double angle = Fx.rnd() * Math.PI * 2.0;
			double speed = (0.7 + Fx.rnd() * 2.2) * Math.min(2.0, scale);
			Fx.spawn(Fx.SPARK, center.x, center.y + 0.3, center.z, Math.cos(angle) * speed, 0.1 + Fx.rnd() * 0.7, Math.sin(angle) * speed,
					10 + (int) (Fx.rnd() * 14), 0.12, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.04, 0.92, (byte) 0);
		}

		for (int i = 0, count = Fx.n(34 * scale); i < count; i++) {
			double angle = Fx.rnd() * Math.PI * 2.0;
			double speed = (1.5 + Fx.rnd() * 2.6) * Math.min(2.5, scale);
			Fx.spawn(Fx.STREAK, center.x, center.y + 0.25, center.z, Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed,
					8 + (int) (Fx.rnd() * 6), 0.2, 0.02, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.95, (byte) 0);
		}

		for (int i = 0, count = Fx.n(46 * scale); i < count; i++) {
			double angle = Fx.rnd() * Math.PI * 2.0;
			double speed = 0.2 + Fx.rnd() * 0.9 * Math.min(2.0, scale);
			Fx.spawn(Fx.DEBRIS, center.x, center.y + 0.4, center.z, Math.cos(angle) * speed, 0.45 + Fx.rnd() * 0.9, Math.sin(angle) * speed,
					40 + (int) (Fx.rnd() * 40), 0.2 + Fx.rnd() * 0.16, 0.16, ground, ground & 0x00FFFFFF | 0xE0000000, 0.07, 0.98, Fx.COLLIDE);
		}

		for (int i = 0, count = Fx.n(60 * scale); i < count; i++) {
			double angle = Fx.rnd() * Math.PI * 2.0;
			double speed = 0.15 + Fx.rnd() * 0.7 * Math.min(2.0, scale);
			Fx.spawn(Fx.DUST, center.x, center.y + 0.3, center.z, Math.cos(angle) * speed, 0.02 + Fx.rnd() * 0.08, Math.sin(angle) * speed,
					30 + (int) (Fx.rnd() * 30), 0.9, 3.8 * scale, dustColor, ground & 0x00FFFFFF, -0.001, 0.92, (byte) 0);
		}

		for (int i = 0, count = Fx.n(30 * scale); i < count; i++) {
			Vec3 v = randomUnit().scale(0.15 + Fx.rnd() * 0.5);
			Fx.spawn(Fx.ENERGY, center.x, center.y + 0.6, center.z, v.x, Math.abs(v.y) + 0.05, v.z, 26 + (int) (Fx.rnd() * 14), 0.4, 0.05, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.95, (byte) 0);
		}
	}

	// ------------------------------------------------------------------ Apocalypse Stomp

	/**
	 * The exact impact: one overwhelming flash, a pillar of energy, a radial burst of long bright streaks, angular fragments,
	 * dense dark-violet clouds and a heavy dust ring, plus several fast rings. Layered fast-to-slow so it reads from far away.
	 */
	public static void apocalypseBurst(ClientLevel level, Vec3 center) {
		int ground = groundColor(level, center.x, center.y, center.z);
		int dustColor = (ground & 0x00FFFFFF) | 0xD0000000;
		int vapor = Fx.argb(170, 46, 16, 98);

		// Dominant centre: two stacked flashes, one huge and short, one tighter and a little longer.
		Fx.spawn(Fx.FLASH, center.x, center.y + 0.6, center.z, 0.0, 0.0, 0.0, 5, 14.0, 44.0, Fx.WHITE_V, Fx.fade(Fx.VIOLET_HI), 0.0, 1.0, (byte) 0);
		Fx.spawn(Fx.FLASH, center.x, center.y + 1.2, center.z, 0.0, 0.0, 0.0, 12, 8.0, 22.0, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 1.0, (byte) 0);

		// Several fast rings: the "distortion" travelling away from the impact.
		Fx.ring(center.x, center.y + 0.2, center.z, 0, 1, 0, 1.0, 16.0, 2.4, 6, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
		Fx.ring(center.x, center.y + 0.2, center.z, 0, 1, 0, 1.0, 30.0, 1.8, 9, Fx.WHITE_V, Fx.fade(Fx.VIOLET_HI), -1, 0.0);
		Fx.ring(center.x, center.y + 0.3, center.z, 0, 1, 0, 1.0, 100.0, 1.4, 30, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);
		Fx.ring(center.x, center.y + 0.3, center.z, 0, 1, 0, 1.0, 100.0, 0.7, 36, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);
		Fx.ring(center.x, center.y + 0.3, center.z, 0, 1, 0, 1.0, 76.0, 0.9, 26, Fx.VIOLET, Fx.fade(Fx.VIOLET_DARK), -1, 0.0);
		Fx.ring(center.x, center.y + 0.3, center.z, 0, 1, 0, 1.0, 50.0, 1.0, 18, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), -1, 0.0);

		// Energy pillar shooting straight up.
		for (int i = 0, count = Fx.nu(90); i < count; i++) {
			double a = Fx.rnd() * Math.PI * 2.0;
			double r = Fx.rnd() * 2.4;
			Fx.spawn(Fx.STREAK, center.x + Math.cos(a) * r, center.y + 0.3, center.z + Math.sin(a) * r, Math.cos(a) * 0.1, 2.2 + Fx.rnd() * 3.4, Math.sin(a) * 0.1,
					12 + (int) (Fx.rnd() * 10), 0.3, 0.03, i % 2 == 0 ? Fx.WHITE_V : Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.96, (byte) 0);
		}

		// Radial burst: several layers at different speeds and lengths.
		for (int i = 0, count = Fx.nu(140); i < count; i++) {
			double a = Fx.rnd() * Math.PI * 2.0;
			double v = 1.4 + Fx.rnd() * 5.0;
			Fx.spawn(Fx.STREAK, center.x, center.y + 0.3, center.z, Math.cos(a) * v, 0.02 + Fx.rnd() * 0.1, Math.sin(a) * v, 8 + (int) (Fx.rnd() * 10),
					0.16 + Fx.rnd() * 0.22, 0.02, i % 3 == 0 ? Fx.WHITE_V : Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.95, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(160); i < count; i++) {
			double a = Fx.rnd() * Math.PI * 2.0;
			double v = 0.8 + Fx.rnd() * 3.2;
			Fx.spawn(Fx.SPARK, center.x, center.y + 0.3, center.z, Math.cos(a) * v, 0.1 + Fx.rnd() * 0.9, Math.sin(a) * v, 12 + (int) (Fx.rnd() * 16), 0.12, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.05, 0.93, (byte) 0);
		}

		// Fragments: angular violet shards and chunks of the real ground.
		for (int i = 0, count = Fx.nu(90); i < count; i++) {
			double a = Fx.rnd() * Math.PI * 2.0;
			double v = 0.3 + Fx.rnd() * 1.8;
			boolean shard = i % 3 == 0;
			Fx.spawn(Fx.DEBRIS, center.x, center.y + 0.5, center.z, Math.cos(a) * v, 0.7 + Fx.rnd() * 1.6, Math.sin(a) * v, 50 + (int) (Fx.rnd() * 50),
					shard ? 0.22 + Fx.rnd() * 0.2 : 0.2 + Fx.rnd() * 0.22, 0.14, shard ? Fx.VIOLET : ground, shard ? Fx.fade(Fx.VIOLET_DARK) | 0xE0000000 : (ground & 0x00FFFFFF) | 0xE0000000,
					0.07, 0.98, Fx.COLLIDE);
		}

		// Dense dark-violet atmosphere and the heavy ground dust, kept low and spread wide rather than one opaque ball.
		for (int i = 0, count = Fx.nu(80); i < count; i++) {
			double a = Fx.rnd() * Math.PI * 2.0;
			double v = 0.3 + Fx.rnd() * 1.4;
			Fx.spawn(Fx.DUST, center.x, center.y + 0.4, center.z, Math.cos(a) * v, 0.03 + Fx.rnd() * 0.2, Math.sin(a) * v, 40 + (int) (Fx.rnd() * 40), 1.4, 7.0, vapor, Fx.argb(0, 24, 8, 56), -0.001, 0.92, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(120); i < count; i++) {
			double a = Fx.rnd() * Math.PI * 2.0;
			double v = 0.25 + Fx.rnd() * 1.5;
			Fx.spawn(Fx.DUST, center.x, center.y + 0.3, center.z, Math.cos(a) * v, 0.02 + Fx.rnd() * 0.12, Math.sin(a) * v, 36 + (int) (Fx.rnd() * 34), 1.2, 6.0, dustColor, ground & 0x00FFFFFF, -0.0008, 0.92, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(50); i < count; i++) {
			Vec3 v = randomUnit().scale(0.2 + Fx.rnd() * 0.7);
			Fx.spawn(Fx.ENERGY, center.x, center.y + 0.7, center.z, v.x, Math.abs(v.y) + 0.05, v.z, 30 + (int) (Fx.rnd() * 20), 0.5, 0.06, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.95, (byte) 0);
		}
	}

	/** An entity caught by the stomp: a localised burst and a small shockwave ring, bigger the closer it was to the impact. */
	public static void ultHit(ClientLevel level, Vec3 pos, Vec3 outward, double strength) {
		double s = Math.max(0.25, Math.min(1.0, strength));

		Fx.spawn(Fx.FLASH, pos.x, pos.y, pos.z, 0.0, 0.0, 0.0, 4, 1.4 + 2.6 * s, 2.4 + 4.0 * s, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.0, 1.0, (byte) 0);
		Fx.ring(pos.x, pos.y - 0.6, pos.z, 0, 1, 0, 0.4, 2.8 + 3.2 * s, 0.25, 10, Fx.WHITE_V, Fx.fade(Fx.VIOLET), -1, 0.0);

		if (!Fx.near(pos.x, pos.y, pos.z, 90.0)) {
			return; // far away only the flash and ring are worth drawing
		}

		for (int i = 0, count = Fx.nu(22 * s + 6); i < count; i++) {
			Vec3 v = randomUnit().scale(0.3 + Fx.rnd() * 0.9);
			Fx.spawn(Fx.SPARK, pos.x, pos.y, pos.z, v.x + outward.x * 0.4, v.y + 0.2, v.z + outward.z * 0.4, 9 + (int) (Fx.rnd() * 8), 0.1, 0.01, Fx.WHITE_V, Fx.fade(Fx.VIOLET), 0.03, 0.9, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(10 * s + 3); i < count; i++) {
			Vec3 v = randomUnit().scale(0.1 + Fx.rnd() * 0.25);
			Fx.spawn(Fx.ENERGY, pos.x, pos.y, pos.z, v.x, v.y, v.z, 18, 0.34, 0.05, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.94, (byte) 0);
		}

		for (int i = 0, count = Fx.nu(6 * s + 2); i < count; i++) {
			Vec3 v = new Vec3(outward.x, 0.5 + Fx.rnd(), outward.z).scale(0.6 + Fx.rnd() * 1.2);
			Fx.spawn(Fx.STREAK, pos.x, pos.y, pos.z, v.x, v.y, v.z, 8, 0.16, 0.02, Fx.VIOLET_HI, Fx.fade(Fx.VIOLET_LO), 0.0, 0.94, (byte) 0);
		}
	}
}
