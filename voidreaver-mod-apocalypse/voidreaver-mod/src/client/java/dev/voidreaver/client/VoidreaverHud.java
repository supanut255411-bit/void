package dev.voidreaver.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import dev.voidreaver.ModItems;

/**
 * HUD layer: the impact-frame overlay, and two small recharge bars (chain and Apocalypse Stomp) next to the hotbar while the
 * weapon is held. The item itself is never put on a vanilla cooldown, so each ability recharges independently.
 */
final class VoidreaverHud {
	private VoidreaverHud() { }

	static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();

		if (client.player == null) {
			return;
		}

		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		float partial = tickCounter.getGameTimeDeltaPartialTick(false);
		int overlay = ImpactFrame.overlay(partial);

		if (overlay != 0) {
			graphics.fill(0, 0, width, height, overlay);
		}

		// Stomp screen effects: purple-white edge glow and downward speed streaks. The centre of the view stays clear.
		float edge = Math.max(UltFx.edge(), ImpactFrame.edge(partial));

		if (edge > 0.01f) {
			drawEdge(graphics, width, height, edge);
		}

		if (UltFx.streaks() > 0.02f) {
			drawStreaks(graphics, width, height, UltFx.streaks());
		}

		boolean holding = client.player.getMainHandItem().is(ModItems.VOIDREAVER) || client.player.getOffhandItem().is(ModItems.VOIDREAVER);

		if (!holding) {
			return;
		}

		int x = width / 2 + 112;
		bar(graphics, client, x, height - 50, "CHAIN", ClientCooldowns.chainLeft, ClientCooldowns.chainTotal);
		bar(graphics, client, x, height - 33, "STOMP", ClientCooldowns.ultLeft, ClientCooldowns.ultTotal);
	}

	/** Soft frame around the screen: bright white-violet at the very border, fading inwards. */
	private static void drawEdge(GuiGraphics graphics, int width, int height, float strength) {
		int depth = (int) (Math.min(width, height) * (0.07 + 0.13 * strength));
		int maxAlpha = (int) (150 * Math.min(1.0f, strength));

		for (int i = 0; i < depth; i += 2) {
			float f = 1.0f - (float) i / depth;
			int alpha = (int) (maxAlpha * f * f);

			if (alpha <= 0) {
				continue;
			}

			// whiter right at the border, more violet further in
			int r = 150 + (int) (90 * f);
			int g = 80 + (int) (120 * f * f);
			int color = (alpha << 24) | (r << 16) | (g << 8) | 255;
			graphics.fill(0, i, width, i + 2, color);
			graphics.fill(0, height - i - 2, width, height - i, color);
			graphics.fill(i, i + 2, i + 2, height - i - 2, color);
			graphics.fill(width - i - 2, i + 2, width - i, height - i - 2, color);
		}
	}

	/** Thin vertical lines rushing past near the screen edges, longer and denser the faster the dive. */
	private static void drawStreaks(GuiGraphics graphics, int width, int height, float strength) {
		long time = System.nanoTime() / 1_000_000L;
		int lines = (int) (10 + 26 * strength);
		int keepClear = (int) (width * 0.27); // nothing drawn in the middle of the view

		for (int i = 0; i < lines; i++) {
			int seed = i * 7919 + 13;
			boolean left = (i & 1) == 0;
			int span = width / 2 - keepClear;
			int x = left ? ((seed * 31) & 0x7FFFFFFF) % Math.max(1, span) : width - 1 - ((seed * 31) & 0x7FFFFFFF) % Math.max(1, span);
			int length = (int) (height * (0.12 + 0.3 * strength) * (0.5 + ((seed >> 3) & 7) / 8.0));
			long speed = 1 + ((seed >> 5) & 3);
			int y = (int) ((time * speed * (0.8 + 1.6 * strength) + seed * 37L) % (height + length)) - length;
			int alpha = (int) (170 * strength * (0.4 + ((seed >> 7) & 3) / 5.0));
			int color = (Math.min(255, alpha) << 24) | 0xE8D4FF;
			// the streak fades towards its tail
			int head = y + length;
			graphics.fill(x, Math.max(0, y), x + 1 + ((seed >> 9) & 1), Math.min(height, head), color);
			graphics.fill(x, Math.max(0, y), x + 1, Math.min(height, y + length / 2), (Math.min(255, alpha / 2) << 24) | 0xA06CFF);
		}
	}

	private static void bar(GuiGraphics graphics, Minecraft client, int x, int y, String label, int left, int total) {
		int barWidth = 62;
		boolean ready = left <= 0;
		double pulse = 0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.008);
		int fillColor;

		if (ready) {
			int shade = (int) (170 + 85 * pulse);
			fillColor = 0xFF000000 | (shade << 16) | (Math.min(255, shade - 60) << 8) | 255;
		} else {
			fillColor = 0xFF6A2BD0;
		}

		double fraction = ready ? 1.0 : 1.0 - (double) left / total;
		int filled = (int) Math.round(barWidth * Math.max(0.0, Math.min(1.0, fraction)));
		String text = ready ? label + " READY" : String.format("%s %.1fs", label, left / 20.0);

		graphics.drawString(client.font, text, x, y, ready ? 0xFFE8D4FF : 0xFFB9A3DD, true);
		graphics.fill(x - 1, y + 10, x + barWidth + 1, y + 16, 0xC0100818);
		graphics.fill(x, y + 11, x + filled, y + 15, fillColor);
	}
}
