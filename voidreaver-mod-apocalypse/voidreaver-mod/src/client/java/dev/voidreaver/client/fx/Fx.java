package dev.voidreaver.client.fx;

import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

import dev.voidreaver.config.VoidreaverConfig;

/**
 * The Voidreaver particle engine. Every particle has its own position, velocity, gravity, air resistance, lifetime, size curve,
 * rotation, colour transition and fade; nothing just pops in or out. Particles live in pooled primitive arrays (no per-particle
 * objects), are drawn as camera-facing quads or velocity-stretched streaks, and are created client-side from small server events.
 *
 * Kinds: ENERGY (glowing mote with halo), SPARK (short bright streak), STREAK (long stretched trail), DUST (soft puff),
 * DEBRIS (tumbling fragment that collides with blocks), FLASH (very short bright pulse).
 * Rings (flat or tilted expanding circles) are a separate small pool.
 */
public final class Fx {
	public static final byte ENERGY = 0;
	public static final byte SPARK = 1;
	public static final byte STREAK = 2;
	public static final byte DUST = 3;
	public static final byte DEBRIS = 4;
	public static final byte FLASH = 5;
	public static final byte COLLIDE = 1;

	public static final int VIOLET_HI = 0xFFC88CFF;
	public static final int VIOLET = 0xFF9A4DFF;
	public static final int VIOLET_LO = 0xFF5A22B8;
	public static final int VIOLET_DARK = 0xFF2A0F5C;
	public static final int WHITE_V = 0xFFF1E4FF;

	private static final int HARD_MAX = 24000;
	private static final double[] PX = new double[HARD_MAX];
	private static final double[] PY = new double[HARD_MAX];
	private static final double[] PZ = new double[HARD_MAX];
	private static final float[] VX = new float[HARD_MAX];
	private static final float[] VY = new float[HARD_MAX];
	private static final float[] VZ = new float[HARD_MAX];
	private static final float[] AGE = new float[HARD_MAX];
	private static final float[] LIFE = new float[HARD_MAX];
	private static final float[] SIZE0 = new float[HARD_MAX];
	private static final float[] SIZE1 = new float[HARD_MAX];
	private static final float[] ROT = new float[HARD_MAX];
	private static final float[] SPIN = new float[HARD_MAX];
	private static final float[] GRAV = new float[HARD_MAX];
	private static final float[] DRAG = new float[HARD_MAX];
	private static final int[] COLOR0 = new int[HARD_MAX];
	private static final int[] COLOR1 = new int[HARD_MAX];
	private static final byte[] KIND = new byte[HARD_MAX];
	private static final byte[] FLAGS = new byte[HARD_MAX];
	private static int count;

	private static final int RING_MAX = 128;
	private static final double[] RX = new double[RING_MAX];
	private static final double[] RY = new double[RING_MAX];
	private static final double[] RZ = new double[RING_MAX];
	private static final float[] RNX = new float[RING_MAX];
	private static final float[] RNY = new float[RING_MAX];
	private static final float[] RNZ = new float[RING_MAX];
	private static final float[] RR0 = new float[RING_MAX];
	private static final float[] RR1 = new float[RING_MAX];
	private static final float[] RTHICK = new float[RING_MAX];
	private static final float[] RAGE = new float[RING_MAX];
	private static final float[] RLIFE = new float[RING_MAX];
	private static final int[] RC0 = new int[RING_MAX];
	private static final int[] RC1 = new int[RING_MAX];
	private static final int[] RFOLLOW = new int[RING_MAX];
	private static final float[] ROFFY = new float[RING_MAX];
	private static int ringCount;

	private static int freezeTicks;
	private static double density = 1.0;
	private static int cap = 12000;
	private static double refX;
	private static double refY;
	private static double refZ;

	private Fx() { }

	// ------------------------------------------------------------------ quality and helpers

	private static void refreshQuality() {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		String quality = cfg.particleQuality == null ? "HIGH" : cfg.particleQuality.trim().toUpperCase();
		double base;

		switch (quality) {
			case "LOW" -> { base = 0.35; cap = 2500; }
			case "MEDIUM" -> { base = 0.65; cap = 6000; }
			case "ULTRA" -> { base = 1.6; cap = 22000; }
			default -> { base = 1.0; cap = 12000; }
		}

		density = base * cfg.particleIntensity;
	}

	/** Scales an emitter's particle count by quality and the particle-intensity setting, with probabilistic rounding. */
	public static int n(double base) {
		double value = base * density;
		int whole = (int) value;

		if (ThreadLocalRandom.current().nextDouble() < value - whole) {
			whole++;
		}

		return Math.max(0, whole);
	}

	/** Like {@link #n} but at one fixed density, so the Apocalypse Stomp looks the same for every player. */
	public static int nu(double base) {
		double value = base * 1.15;
		int whole = (int) value;

		if (ThreadLocalRandom.current().nextDouble() < value - whole) {
			whole++;
		}

		return Math.max(0, whole);
	}

	public static float rnd() {
		return ThreadLocalRandom.current().nextFloat();
	}

	/** Random value in -1..1. */
	public static float rnds() {
		return ThreadLocalRandom.current().nextFloat() * 2.0f - 1.0f;
	}

	/** True when a position is within {@code range} blocks of the local player: small details are skipped far away. */
	public static boolean near(double x, double y, double z, double range) {
		double dx = x - refX;
		double dy = y - refY;
		double dz = z - refZ;
		return dx * dx + dy * dy + dz * dz <= range * range;
	}

	public static int fade(int color) {
		return color & 0x00FFFFFF;
	}

	public static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	/** Pauses particle updates for a few ticks: the visual "hit stop" of the impact frame. Nothing server-side is touched. */
	public static void freeze(int ticks) {
		freezeTicks = Math.max(freezeTicks, ticks);
	}

	public static void clear() {
		count = 0;
		ringCount = 0;
		freezeTicks = 0;
	}

	// ------------------------------------------------------------------ spawning

	public static void spawn(byte kind, double x, double y, double z, double vx, double vy, double vz, int life,
			double size0, double size1, int color0, int color1, double gravity, double drag, byte flags) {
		if (count >= cap || count >= HARD_MAX) {
			return;
		}

		int i = count++;
		PX[i] = x;
		PY[i] = y;
		PZ[i] = z;
		VX[i] = (float) vx;
		VY[i] = (float) vy;
		VZ[i] = (float) vz;
		AGE[i] = 0.0f;
		LIFE[i] = Math.max(1, life);
		SIZE0[i] = (float) size0;
		SIZE1[i] = (float) size1;
		ROT[i] = rnd() * 6.2831855f;
		SPIN[i] = rnds() * 0.35f;
		GRAV[i] = (float) gravity;
		DRAG[i] = (float) drag;
		COLOR0[i] = color0;
		COLOR1[i] = color1;
		KIND[i] = kind;
		FLAGS[i] = flags;
	}

	/** An expanding ring in the plane perpendicular to (nx, ny, nz). Pass followEntityId >= 0 to keep it on an entity. */
	public static void ring(double x, double y, double z, double nx, double ny, double nz, double r0, double r1,
			double thickness, int life, int color0, int color1, int followEntityId, double offsetY) {
		if (ringCount >= RING_MAX) {
			return;
		}

		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);

		if (length < 1.0E-6) {
			nx = 0.0;
			ny = 1.0;
			nz = 0.0;
			length = 1.0;
		}

		int i = ringCount++;
		RX[i] = x;
		RY[i] = y;
		RZ[i] = z;
		RNX[i] = (float) (nx / length);
		RNY[i] = (float) (ny / length);
		RNZ[i] = (float) (nz / length);
		RR0[i] = (float) r0;
		RR1[i] = (float) r1;
		RTHICK[i] = (float) thickness;
		RAGE[i] = 0.0f;
		RLIFE[i] = Math.max(1, life);
		RC0[i] = color0;
		RC1[i] = color1;
		RFOLLOW[i] = followEntityId;
		ROFFY[i] = (float) offsetY;
	}

	// ------------------------------------------------------------------ simulation

	public static void tick(ClientLevel level) {
		refreshQuality();
		Minecraft client = Minecraft.getInstance();

		if (client.player != null) {
			refX = client.player.getX();
			refY = client.player.getY();
			refZ = client.player.getZ();
		}

		if (level == null) {
			count = 0;
			ringCount = 0;
			return;
		}

		if (freezeTicks > 0) {
			freezeTicks--;
			return;
		}

		for (int i = count - 1; i >= 0; i--) {
			AGE[i] += 1.0f;

			if (AGE[i] >= LIFE[i]) {
				removeParticle(i);
				continue;
			}

			VY[i] -= GRAV[i];
			VX[i] *= DRAG[i];
			VY[i] *= DRAG[i];
			VZ[i] *= DRAG[i];

			double nx = PX[i] + VX[i];
			double ny = PY[i] + VY[i];
			double nz = PZ[i] + VZ[i];

			if ((FLAGS[i] & COLLIDE) != 0) {
				BlockPos pos = BlockPos.containing(nx, ny, nz);
				BlockState state = level.getBlockState(pos);

				if (!state.getCollisionShape(level, pos).isEmpty()) {
					VY[i] = VY[i] < 0.0f ? -VY[i] * 0.22f : -VY[i] * 0.3f;
					VX[i] *= 0.45f;
					VZ[i] *= 0.45f;
					SPIN[i] *= 0.5f;
					nx = PX[i];
					ny = PY[i];
					nz = PZ[i];
				}
			}

			PX[i] = nx;
			PY[i] = ny;
			PZ[i] = nz;
			ROT[i] += SPIN[i];
		}

		for (int i = ringCount - 1; i >= 0; i--) {
			RAGE[i] += 1.0f;

			if (RAGE[i] >= RLIFE[i]) {
				removeRing(i);
				continue;
			}

			if (RFOLLOW[i] >= 0) {
				Entity entity = level.getEntity(RFOLLOW[i]);

				if (entity == null) {
					removeRing(i);
					continue;
				}

				RX[i] = entity.getX();
				RY[i] = entity.getY() + ROFFY[i];
				RZ[i] = entity.getZ();
			}
		}
	}

	private static void removeParticle(int i) {
		int last = --count;

		if (i != last) {
			PX[i] = PX[last]; PY[i] = PY[last]; PZ[i] = PZ[last];
			VX[i] = VX[last]; VY[i] = VY[last]; VZ[i] = VZ[last];
			AGE[i] = AGE[last]; LIFE[i] = LIFE[last];
			SIZE0[i] = SIZE0[last]; SIZE1[i] = SIZE1[last];
			ROT[i] = ROT[last]; SPIN[i] = SPIN[last];
			GRAV[i] = GRAV[last]; DRAG[i] = DRAG[last];
			COLOR0[i] = COLOR0[last]; COLOR1[i] = COLOR1[last];
			KIND[i] = KIND[last]; FLAGS[i] = FLAGS[last];
		}
	}

	private static void removeRing(int i) {
		int last = --ringCount;

		if (i != last) {
			RX[i] = RX[last]; RY[i] = RY[last]; RZ[i] = RZ[last];
			RNX[i] = RNX[last]; RNY[i] = RNY[last]; RNZ[i] = RNZ[last];
			RR0[i] = RR0[last]; RR1[i] = RR1[last]; RTHICK[i] = RTHICK[last];
			RAGE[i] = RAGE[last]; RLIFE[i] = RLIFE[last];
			RC0[i] = RC0[last]; RC1[i] = RC1[last];
			RFOLLOW[i] = RFOLLOW[last]; ROFFY[i] = ROFFY[last];
		}
	}

	public static boolean isEmpty() {
		return count == 0 && ringCount == 0;
	}

	// ------------------------------------------------------------------ rendering

	private static float smooth(float x) {
		x = Math.max(0.0f, Math.min(1.0f, x));
		return x * x * (3.0f - 2.0f * x);
	}

	private static int lerpColor(int a, int b, float t) {
		int aa = (int) (((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		int ar = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return argb(aa, ar, ag, ab);
	}

	private static int withAlpha(int color, float factor) {
		int a = (int) (((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, factor)));
		return (a << 24) | (color & 0x00FFFFFF);
	}

	private static void vert(VertexConsumer vc, Matrix4f m, double x, double y, double z, int color) {
		vc.addVertex(m, (float) x, (float) y, (float) z).setColor(color);
	}

	/** Four corners with their own colours, emitted twice (both windings) so culling can never hide it. */
	private static void quad(VertexConsumer vc, Matrix4f m,
			double x0, double y0, double z0, int c0, double x1, double y1, double z1, int c1,
			double x2, double y2, double z2, int c2, double x3, double y3, double z3, int c3) {
		vert(vc, m, x0, y0, z0, c0);
		vert(vc, m, x1, y1, z1, c1);
		vert(vc, m, x2, y2, z2, c2);
		vert(vc, m, x3, y3, z3, c3);
		vert(vc, m, x3, y3, z3, c3);
		vert(vc, m, x2, y2, z2, c2);
		vert(vc, m, x1, y1, z1, c1);
		vert(vc, m, x0, y0, z0, c0);
	}

	public static void render(VertexConsumer vc, Matrix4f m, double camX, double camY, double camZ, float partial) {
		for (int i = 0; i < count; i++) {
			float t = (AGE[i] + partial) / LIFE[i];
			byte kind = KIND[i];
			double x = PX[i] + VX[i] * partial;
			double y = PY[i] + VY[i] * partial;
			double z = PZ[i] + VZ[i] * partial;
			double dx = x - camX;
			double dy = y - camY;
			double dz = z - camZ;
			double distSqr = dx * dx + dy * dy + dz * dz;
			double maxDist = (kind == SPARK || kind == DEBRIS) ? 80.0 : 260.0;

			if (distSqr > maxDist * maxDist || distSqr < 0.01) {
				continue;
			}

			double dist = Math.sqrt(distSqr);
			float size = SIZE0[i] + (SIZE1[i] - SIZE0[i]) * t;
			int color = lerpColor(COLOR0[i], COLOR1[i], t);
			float alpha = kind == FLASH ? (1.0f - t) : smooth(t / 0.08f) * smooth((1.0f - t) / 0.45f);
			color = withAlpha(color, alpha);

			// Billboard basis from the direction camera -> particle.
			double fx = dx / dist;
			double fy = dy / dist;
			double fz = dz / dist;
			double rx = -fz;
			double rz = fx;
			double rl = Math.sqrt(rx * rx + rz * rz);

			if (rl < 1.0E-4) {
				rx = 1.0;
				rz = 0.0;
			} else {
				rx /= rl;
				rz /= rl;
			}

			double ux = -rz * fy;
			double uy = rz * fx - rx * fz;
			double uz = rx * fy;

			if (kind == SPARK || kind == STREAK) {
				double speed = Math.sqrt(VX[i] * VX[i] + VY[i] * VY[i] + VZ[i] * VZ[i]);
				double ax = speed > 1.0E-4 ? VX[i] / speed : 0.0;
				double ay = speed > 1.0E-4 ? VY[i] / speed : 1.0;
				double az = speed > 1.0E-4 ? VZ[i] / speed : 0.0;
				double length = size * 2.0 + speed * (kind == STREAK ? 3.4 : 1.3);
				// side = axis x view direction, so the streak always shows its width to the camera
				double sx = ay * fz - az * fy;
				double sy = az * fx - ax * fz;
				double sz = ax * fy - ay * fx;
				double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);

				if (sl < 1.0E-4) {
					continue;
				}

				double w = size * 0.5 / sl;
				sx *= w;
				sy *= w;
				sz *= w;
				double hx = x + ax * length * 0.5;
				double hy = y + ay * length * 0.5;
				double hz = z + az * length * 0.5;
				double tx = x - ax * length * 0.5;
				double ty = y - ay * length * 0.5;
				double tz = z - az * length * 0.5;
				int tail = withAlpha(color, 0.0f);
				quad(vc, m, hx - sx, hy - sy, hz - sz, color, hx + sx, hy + sy, hz + sz, color,
						tx + sx, ty + sy, tz + sz, tail, tx - sx, ty - sy, tz - sz, tail);
				continue;
			}

			float cos = (float) Math.cos(ROT[i]);
			float sin = (float) Math.sin(ROT[i]);
			double r1x = rx * cos + ux * sin;
			double r1y = uy * sin;
			double r1z = rz * cos + uz * sin;
			double u1x = -rx * sin + ux * cos;
			double u1y = uy * cos;
			double u1z = -rz * sin + uz * cos;
			double half = size * 0.5;

			switch (kind) {
				case ENERGY -> {
					double halo = half * 2.4;
					int haloColor = withAlpha(color, 0.26f);
					quad(vc, m, x - r1x * halo - u1x * halo, y - r1y * halo - u1y * halo, z - r1z * halo - u1z * halo, haloColor,
							x + r1x * halo - u1x * halo, y + r1y * halo - u1y * halo, z + r1z * halo - u1z * halo, haloColor,
							x + r1x * halo + u1x * halo, y + r1y * halo + u1y * halo, z + r1z * halo + u1z * halo, haloColor,
							x - r1x * halo + u1x * halo, y - r1y * halo + u1y * halo, z - r1z * halo + u1z * halo, haloColor);
					quad(vc, m, x - r1x * half - u1x * half, y - r1y * half - u1y * half, z - r1z * half - u1z * half, color,
							x + r1x * half - u1x * half, y + r1y * half - u1y * half, z + r1z * half - u1z * half, color,
							x + r1x * half + u1x * half, y + r1y * half + u1y * half, z + r1z * half + u1z * half, color,
							x - r1x * half + u1x * half, y - r1y * half + u1y * half, z - r1z * half + u1z * half, color);
				}
				case DEBRIS -> {
					float shade = 0.72f + 0.28f * sin;
					int dark = argb((color >>> 24) & 0xFF, (int) (((color >> 16) & 0xFF) * shade), (int) (((color >> 8) & 0xFF) * shade), (int) ((color & 0xFF) * shade));
					quad(vc, m, x - r1x * half - u1x * half, y - r1y * half - u1y * half, z - r1z * half - u1z * half, dark,
							x + r1x * half - u1x * half, y + r1y * half - u1y * half, z + r1z * half - u1z * half, dark,
							x + r1x * half + u1x * half, y + r1y * half + u1y * half, z + r1z * half + u1z * half, dark,
							x - r1x * half + u1x * half, y - r1y * half + u1y * half, z - r1z * half + u1z * half, dark);
				}
				default -> { // DUST, FLASH
					int soft = kind == DUST ? withAlpha(color, 0.6f) : color;
					quad(vc, m, x - r1x * half - u1x * half, y - r1y * half - u1y * half, z - r1z * half - u1z * half, soft,
							x + r1x * half - u1x * half, y + r1y * half - u1y * half, z + r1z * half - u1z * half, soft,
							x + r1x * half + u1x * half, y + r1y * half + u1y * half, z + r1z * half + u1z * half, soft,
							x - r1x * half + u1x * half, y - r1y * half + u1y * half, z - r1z * half + u1z * half, soft);
				}
			}
		}

		for (int i = 0; i < ringCount; i++) {
			float t = (RAGE[i] + partial) / RLIFE[i];
			float ease = 1.0f - (1.0f - t) * (1.0f - t);
			double radius = RR0[i] + (RR1[i] - RR0[i]) * ease;
			float alpha = smooth(t / 0.1f) * smooth((1.0f - t) / 0.6f);
			int color = withAlpha(lerpColor(RC0[i], RC1[i], t), alpha);
			double nx = RNX[i];
			double ny = RNY[i];
			double nz = RNZ[i];
			// two vectors spanning the ring plane
			double ux;
			double uy;
			double uz;

			if (Math.abs(ny) > 0.95) {
				ux = 1.0;
				uy = 0.0;
				uz = 0.0;
			} else {
				ux = -nz;
				uy = 0.0;
				uz = nx;
				double ul = Math.sqrt(ux * ux + uz * uz);
				ux /= ul;
				uz /= ul;
			}

			double vx = ny * uz - nz * uy;
			double vy = nz * ux - nx * uz;
			double vz = nx * uy - ny * ux;
			double inner = Math.max(0.0, radius - RTHICK[i] * 0.5);
			double outer = radius + RTHICK[i] * 0.5;
			int segments = (int) Math.max(16, Math.min(72, radius * 8.0));

			for (int s = 0; s < segments; s++) {
				double a0 = Math.PI * 2.0 * s / segments;
				double a1 = Math.PI * 2.0 * (s + 1) / segments;
				double c0 = Math.cos(a0);
				double s0 = Math.sin(a0);
				double c1 = Math.cos(a1);
				double s1 = Math.sin(a1);
				double cx = RX[i];
				double cy = RY[i];
				double cz = RZ[i];

				quad(vc, m,
						cx + (ux * c0 + vx * s0) * inner, cy + (uy * c0 + vy * s0) * inner, cz + (uz * c0 + vz * s0) * inner, color,
						cx + (ux * c0 + vx * s0) * outer, cy + (uy * c0 + vy * s0) * outer, cz + (uz * c0 + vz * s0) * outer, color,
						cx + (ux * c1 + vx * s1) * outer, cy + (uy * c1 + vy * s1) * outer, cz + (uz * c1 + vz * s1) * outer, color,
						cx + (ux * c1 + vx * s1) * inner, cy + (uy * c1 + vy * s1) * inner, cz + (uz * c1 + vz * s1) * inner, color);
			}
		}
	}
}
