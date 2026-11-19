package dev.voidreaver.client;

import dev.voidreaver.client.fx.Fx;
import dev.voidreaver.config.VoidreaverConfig;

/**
 * The cinematic impact frame: a short white flash, then a dark high-contrast beat. It is drawn on the HUD layer and also
 * pauses the client's particles for a moment (a visual hit-stop). The server and other players are never paused.
 */
final class ImpactFrame {
	private static final int TOTAL = 6;
	private static int ticks;
	private static float strength;

	private ImpactFrame() { }

	static void trigger(float intensity) {
		if (!VoidreaverConfig.get().impactFrameEnabled || intensity <= 0.05f) {
			return;
		}

		ticks = TOTAL;
		strength = Math.min(1.0f, intensity);
		Fx.freeze(2);
	}

	static void tick() {
		if (ticks > 0) {
			ticks--;
		}
	}

	static void clear() {
		ticks = 0;
	}

	/** 0..1 strength of the white-violet screen-edge flare during the frame (it fades faster than the flash itself). */
	static float edge(float partial) {
		if (ticks <= 0) {
			return 0.0f;
		}

		float t = Math.max(0.0f, (ticks - partial) / TOTAL);
		return Math.min(1.0f, strength * (0.4f + 0.6f * t));
	}

	/** ARGB colour to draw over the whole screen this frame, or 0 for nothing. */
	static int overlay(float partial) {
		if (ticks <= 0) {
			return 0;
		}

		float t = (ticks - partial) / TOTAL; // 1 -> 0 over the effect
		int alpha;
		int rgb;

		if (t > 0.66f) { // sharp white flash
			alpha = (int) (235 * strength);
			rgb = 0xFFFFFF;
		} else if (t > 0.33f) { // dark, high-contrast beat
			alpha = (int) (150 * strength);
			rgb = 0x08020F;
		} else { // violet fade-out
			alpha = (int) (110 * strength * (t / 0.33f));
			rgb = 0x6A2BD0;
		}

		return (Math.max(0, Math.min(255, alpha)) << 24) | rgb;
	}
}
