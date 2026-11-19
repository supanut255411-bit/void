package dev.voidreaver.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import dev.voidreaver.config.VoidreaverConfig;

/**
 * Sounds and particles helpers. Only vanilla sound events are used, looked up by id, so a missing event is skipped rather
 * than crashing. Layered sounds are scheduled a tick or two apart ({@link #soundIn}) so that several vanilla sounds read as one
 * big event instead of one smeared blob. All of it plays on the server at the real world position, so the game's normal
 * distance falloff applies and nothing is played twice.
 */
public final class Sfx {
	private record Delayed(int dueTick, ServerLevel level, Vec3 pos, String id, float volume, float pitch) { }

	private static final List<Delayed> QUEUE = new ArrayList<>();
	private static final int MAX_QUEUED = 600;

	private Sfx() { }

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Sfx::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> QUEUE.clear());
	}

	private static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) {
			return;
		}

		int now = server.getTickCount();
		Iterator<Delayed> iterator = QUEUE.iterator();

		while (iterator.hasNext()) {
			Delayed delayed = iterator.next();

			if (now >= delayed.dueTick()) {
				sound(delayed.level(), delayed.pos(), delayed.id(), delayed.volume(), delayed.pitch());
				iterator.remove();
			}
		}
	}

	public static void sound(ServerLevel level, Vec3 pos, String vanillaId, float volume, float pitch) {
		SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(vanillaId));

		if (event == null) {
			return;
		}

		float scaled = (float) (volume * VoidreaverConfig.get().soundVolume);

		if (scaled > 0.0f) {
			level.playSound((Player) null, pos.x, pos.y, pos.z, event, net.minecraft.sounds.SoundSource.PLAYERS, scaled, pitch);
		}
	}

	/** Plays a sound after {@code delayTicks} server ticks (0 = now). Used to stagger the layers of one event. */
	public static void soundIn(ServerLevel level, Vec3 pos, int delayTicks, String vanillaId, float volume, float pitch) {
		if (delayTicks <= 0) {
			sound(level, pos, vanillaId, volume, pitch);
			return;
		}

		if (QUEUE.size() < MAX_QUEUED) {
			QUEUE.add(new Delayed(level.getServer().getTickCount() + delayTicks, level, pos, vanillaId, volume, pitch));
		}
	}

	/** Spawns particles scaled by the configured particle intensity. */
	public static void particles(ServerLevel level, ParticleOptions type, Vec3 pos, int count, double spreadX, double spreadY, double spreadZ, double speed) {
		int scaled = (int) Math.round(count * VoidreaverConfig.get().particleIntensity);

		if (scaled > 0) {
			level.sendParticles(type, pos.x, pos.y, pos.z, scaled, spreadX, spreadY, spreadZ, speed);
		}
	}
}
