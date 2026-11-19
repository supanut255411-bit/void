package dev.voidreaver.client;

import dev.voidreaver.config.VoidreaverConfig;

/**
 * Camera shake. Short impulses (hits, impacts) decay over their duration; a separate ground rumble is set every tick by
 * shockwaves passing nearby and stops as soon as they do. {@code screenShakeIntensity} in the config scales all of it; 0 turns it off.
 */
public final class ScreenShake {
	private static final float DEGREES_PER_UNIT = 1.8f;
	private static final float MAX_DEGREES = 11.0f;

	private static float amplitude;
	private static int remaining;
	private static int total = 1;
	private static float rumblePending;
	private static float rumbleNow;

	private ScreenShake() { }

	public static void add(float intensity, int ticks) {
		float scaled = (float) (intensity * VoidreaverConfig.get().screenShakeIntensity);

		if (scaled <= 0.0f || ticks <= 0) {
			return;
		}

		// A stronger shake replaces a weaker one in progress; a weaker one never cuts a stronger one short.
		if (remaining <= 0 || scaled >= currentAmplitude()) {
			amplitude = scaled;
			total = ticks;
			remaining = ticks;
		}
	}

	/** Ground rumble for this tick only. Call every tick while a wave is nearby. */
	static void rumble(float intensity) {
		float scaled = (float) (intensity * VoidreaverConfig.get().screenShakeIntensity);
		rumblePending = Math.max(rumblePending, scaled);
	}

	static void tick() {
		if (remaining > 0) {
			remaining--;
		}

		rumbleNow = rumblePending;
		rumblePending = 0.0f;
	}

	static void clear() {
		remaining = 0;
		rumblePending = 0.0f;
		rumbleNow = 0.0f;
	}

	private static float currentAmplitude() {
		return remaining <= 0 ? 0.0f : amplitude * remaining / total;
	}

	/** Camera rotation offset in degrees as {yaw, pitch}, or null when not shaking. Called every frame. */
	public static float[] offset() {
		float strength = currentAmplitude() + rumbleNow;

		if (strength <= 0.001f) {
			return null;
		}

		float degrees = Math.min(MAX_DEGREES, strength * DEGREES_PER_UNIT);
		double t = System.nanoTime() * 1.0E-9;
		float yaw = (float) (Math.sin(t * 61.0) + 0.6 * Math.sin(t * 37.3)) * degrees * 0.6f;
		float pitch = (float) (Math.sin(t * 53.0 + 1.7) + 0.6 * Math.sin(t * 29.1)) * degrees * 0.6f;
		return new float[] {yaw, pitch};
	}
}
