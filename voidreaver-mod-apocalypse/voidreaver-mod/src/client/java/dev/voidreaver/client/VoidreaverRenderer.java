package dev.voidreaver.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.voidreaver.chain.ChainGeometry;
import dev.voidreaver.client.fx.Fx;

/**
 * Draws the physical chain (real 3D links, alternating orientation, end to end), the terrain-following shockwave rings and
 * every particle of the {@link Fx} engine. Everything goes through one vertex buffer per frame.
 */
final class VoidreaverRenderer {
	private static final double LINK_PITCH = 0.34;
	private static final int MAX_LINKS = 420;

	private VoidreaverRenderer() { }

	static void render(WorldRenderContext context) {
		if (ClientChains.isEmpty() && ShockwaveFx.all().isEmpty() && Fx.isEmpty()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;

		if (level == null) {
			return;
		}

		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.worldState().cameraRenderState.pos;
		PoseStack pose = context.matrices();

		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);

		VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugFilledBox());
		Matrix4f matrix = pose.last().pose();

		for (ClientChains.Chain chain : ClientChains.all()) {
			Entity owner = level.getEntity(chain.ownerId);

			if (!(owner instanceof Player player)) {
				continue;
			}

			Vec3 head = ChainGeometry.headPosition(player, chain.mainHand, partial);
			Vec3 tip = chain.prevTip.lerp(chain.tip, partial);

			if (chain.phase == ClientChains.Phase.HOOKED && chain.reached) {
				Entity target = level.getEntity(chain.targetId);

				if (target != null) {
					tip = target.getPosition(partial).add(0.0, target.getBbHeight() * 0.5, 0.0);
				}
			}

			drawChain(buffer, matrix, head, tip);
		}

		for (ShockwaveFx.Wave wave : ShockwaveFx.all()) {
			double radius = Math.min(wave.maxRadius, wave.prevRadius + (wave.radius - wave.prevRadius) * partial);
			drawWave(buffer, matrix, wave, radius);
		}

		Fx.render(buffer, matrix, camera.x, camera.y, camera.z, partial);

		pose.popPose();
	}

	// ------------------------------------------------------------------ chain

	private static void drawChain(VertexConsumer vc, Matrix4f m, Vec3 head, Vec3 tip) {
		Vec3 delta = tip.subtract(head);
		double length = delta.length();

		if (length < 0.15) {
			return;
		}

		Vec3 axis = delta.scale(1.0 / length);
		Vec3 reference = Math.abs(axis.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 u = axis.cross(reference).normalize();
		Vec3 v = axis.cross(u).normalize();

		int links = Math.max(1, Math.min(MAX_LINKS, (int) Math.round(length / LINK_PITCH)));
		double step = length / links; // spread evenly so the links always fill the gap exactly, with no holes
		Vec3 halfLength = axis.scale(step * 0.46);

		for (int i = 0; i < links; i++) {
			Vec3 center = head.add(axis.scale(step * (i + 0.5)));
			boolean even = (i & 1) == 0;
			Vec3 wide = (even ? u : v).scale(0.075);
			Vec3 thin = (even ? v : u).scale(0.028);
			int color = (i % 4 == 0) ? argb(255, 150, 70, 255) : (even ? argb(255, 66, 66, 86) : argb(255, 50, 50, 68));
			box(vc, m, center, halfLength, wide, thin, color);
		}

		// Spiked hook head at the tip.
		Vec3 core = axis.scale(0.13);
		box(vc, m, tip, core, u.scale(0.13), v.scale(0.13), argb(255, 34, 32, 46));
		box(vc, m, tip, axis.scale(0.16), u.scale(0.07), v.scale(0.07), argb(255, 170, 90, 255));
		box(vc, m, tip.add(u.scale(0.2)), u.scale(0.12), axis.scale(0.04), v.scale(0.04), argb(255, 80, 78, 100));
		box(vc, m, tip.subtract(u.scale(0.2)), u.scale(0.12), axis.scale(0.04), v.scale(0.04), argb(255, 80, 78, 100));
		box(vc, m, tip.add(v.scale(0.2)), v.scale(0.12), axis.scale(0.04), u.scale(0.04), argb(255, 80, 78, 100));
		box(vc, m, tip.subtract(v.scale(0.2)), v.scale(0.12), axis.scale(0.04), u.scale(0.04), argb(255, 80, 78, 100));
	}

	/** An oriented box with half-extent vectors hx, hy, hz. Faces are shaded by axis so links read as solid objects. */
	private static void box(VertexConsumer vc, Matrix4f m, Vec3 center, Vec3 hx, Vec3 hy, Vec3 hz, int color) {
		Vec3[] p = new Vec3[8];

		for (int i = 0; i < 8; i++) {
			double sx = (i & 1) == 0 ? -1.0 : 1.0;
			double sy = (i & 2) == 0 ? -1.0 : 1.0;
			double sz = (i & 4) == 0 ? -1.0 : 1.0;
			p[i] = center.add(hx.scale(sx)).add(hy.scale(sy)).add(hz.scale(sz));
		}

		int ends = shade(color, 0.6f);
		int broad = shade(color, 1.0f);
		int edge = shade(color, 0.8f);

		quad(vc, m, p[0], p[2], p[6], p[4], ends);
		quad(vc, m, p[1], p[3], p[7], p[5], ends);
		quad(vc, m, p[0], p[1], p[5], p[4], broad);
		quad(vc, m, p[2], p[3], p[7], p[6], broad);
		quad(vc, m, p[0], p[1], p[3], p[2], edge);
		quad(vc, m, p[4], p[5], p[7], p[6], edge);
	}

	// ------------------------------------------------------------------ shockwave

	/** Rings that follow the real ground height segment by segment, so they sweep over hills and dips instead of cutting through them. */
	private static void drawWave(VertexConsumer vc, Matrix4f m, ShockwaveFx.Wave wave, double radius) {
		double progress = Math.min(1.0, radius / wave.maxRadius);
		double fade = Math.pow(1.0 - progress, 0.5);

		if (fade <= 0.02 || radius < 0.4) {
			return;
		}

		double widthScale = 1.0 + radius * 0.012;
		boolean stomp = wave.kind == dev.voidreaver.net.ShockwavePayload.ULTIMATE;

		if (stomp) {
			// Layer 6: a tall, soft energy halo riding the front. Bright at the base, fading to nothing at the top, so it is
			// visible from far away and gets taller with distance; the crown pattern gives the ring a recognisable shape.
			drawHalo(vc, m, wave, wave.ground, radius, 3.0 + radius * 0.16, fade);
			// Layer 2: a low, wide, dark pressure band trailing behind the bright edge.
			drawRing(vc, m, wave, wave.groundTrail, radius * 0.78, 1.6 * widthScale, 0.1 + 0.2 * fade, argb((int) (110 * fade), 60, 24, 130));
			// A thin secondary ring just inside the main one.
			drawRing(vc, m, wave, wave.groundTrail, radius * 0.93, 0.22 * widthScale, 0.3 + 0.4 * fade, argb((int) (200 * fade), 235, 215, 255));
		}

		drawRing(vc, m, wave, wave.ground, radius, 0.7 * widthScale, 0.25 + 0.7 * fade, argb((int) (220 * fade), 175, 80, 255));
		drawRing(vc, m, wave, wave.groundTrail, radius * 0.85, 0.45 * widthScale, 0.12 + 0.3 * fade, argb((int) (130 * fade), 100, 40, 180));
	}

	/** A translucent wall that follows the terrain, with a bright base fading to transparent and a repeating angular crown. */
	private static void drawHalo(VertexConsumer vc, Matrix4f m, ShockwaveFx.Wave wave, float[] ground, double radius, double height, double fade) {
		int base = (int) (170 * fade);
		int baseColor = argb(base, 225, 190, 255);
		int topColor = argb(0, 140, 70, 230);

		for (int i = 0; i < wave.segments; i++) {
			double a0 = Math.PI * 2.0 * i / wave.segments;
			double a1 = Math.PI * 2.0 * (i + 1) / wave.segments;
			double c0 = Math.cos(a0);
			double s0 = Math.sin(a0);
			double c1 = Math.cos(a1);
			double s1 = Math.sin(a1);
			double cx = wave.center.x;
			double cz = wave.center.z;
			double y0 = ground[i] + 0.1;
			double y1 = ground[i + 1] + 0.1;
			// every sixth segment group is taller: a simple geometric crown
			double crown = (i % 8) < 3 ? 1.0 : 0.55;
			double h = height * crown;

			quadGradient(vc, m,
					new Vec3(cx + c0 * radius, y0, cz + s0 * radius), baseColor,
					new Vec3(cx + c1 * radius, y1, cz + s1 * radius), baseColor,
					new Vec3(cx + c1 * radius, y1 + h, cz + s1 * radius), topColor,
					new Vec3(cx + c0 * radius, y0 + h, cz + s0 * radius), topColor);
		}
	}

	private static void quadGradient(VertexConsumer vc, Matrix4f m, Vec3 a, int ca, Vec3 b, int cb, Vec3 c, int cc, Vec3 d, int cd) {
		vertex(vc, m, a, ca);
		vertex(vc, m, b, cb);
		vertex(vc, m, c, cc);
		vertex(vc, m, d, cd);
		vertex(vc, m, d, cd);
		vertex(vc, m, c, cc);
		vertex(vc, m, b, cb);
		vertex(vc, m, a, ca);
	}

	private static void drawRing(VertexConsumer vc, Matrix4f m, ShockwaveFx.Wave wave, float[] ground, double radius, double halfWidth, double height, int color) {
		double inner = Math.max(0.0, radius - halfWidth);
		double outer = radius + halfWidth;
		int wallColor = withAlpha(color, (int) (((color >>> 24) & 0xFF) * 0.55));

		for (int i = 0; i < wave.segments; i++) {
			double a0 = Math.PI * 2.0 * i / wave.segments;
			double a1 = Math.PI * 2.0 * (i + 1) / wave.segments;
			double c0 = Math.cos(a0);
			double s0 = Math.sin(a0);
			double c1 = Math.cos(a1);
			double s1 = Math.sin(a1);
			double y0 = ground[i] + 0.12;
			double y1 = ground[i + 1] + 0.12;
			double cx = wave.center.x;
			double cz = wave.center.z;

			quad(vc, m,
					new Vec3(cx + c0 * inner, y0, cz + s0 * inner),
					new Vec3(cx + c0 * outer, y0, cz + s0 * outer),
					new Vec3(cx + c1 * outer, y1, cz + s1 * outer),
					new Vec3(cx + c1 * inner, y1, cz + s1 * inner),
					color);

			quad(vc, m,
					new Vec3(cx + c0 * radius, y0, cz + s0 * radius),
					new Vec3(cx + c1 * radius, y1, cz + s1 * radius),
					new Vec3(cx + c1 * radius, y1 + height, cz + s1 * radius),
					new Vec3(cx + c0 * radius, y0 + height, cz + s0 * radius),
					wallColor);
		}
	}

	// ------------------------------------------------------------------ primitives

	/** Emits the quad twice, once per winding, so it is visible from both sides regardless of the pipeline's culling. */
	private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		vertex(vc, m, a, color);
		vertex(vc, m, b, color);
		vertex(vc, m, c, color);
		vertex(vc, m, d, color);
		vertex(vc, m, d, color);
		vertex(vc, m, c, color);
		vertex(vc, m, b, color);
		vertex(vc, m, a, color);
	}

	private static void vertex(VertexConsumer vc, Matrix4f m, Vec3 p, int color) {
		vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(color);
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
	}

	private static int withAlpha(int color, int alpha) {
		return (alpha & 0xFF) << 24 | (color & 0x00FFFFFF);
	}

	private static int shade(int color, float factor) {
		int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
		int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
		int b = Math.min(255, (int) ((color & 0xFF) * factor));
		return (color & 0xFF000000) | r << 16 | g << 8 | b;
	}
}
