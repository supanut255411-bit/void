package dev.voidreaver.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.voidreaver.config.VoidreaverConfig;
import dev.voidreaver.net.ShockwavePayload;

/**
 * Expanding ground shockwave. The ring grows a little each tick and damages every entity it passes over exactly once.
 * The visuals (ring, dust, screen shake) are driven by one packet and rendered on the client.
 */
public final class Shockwaves {
	private static final int MAX_ACTIVE = 64;
	private static final List<Wave> WAVES = new ArrayList<>();

	private static final class Wave {
		final ServerLevel level;
		final Vec3 center;
		final UUID owner;
		final int primaryId;
		final double maxRadius;
		double radius;
		final Set<Integer> alreadyHit = new HashSet<>();

		Wave(ServerLevel level, Vec3 center, UUID owner, int primaryId, double maxRadius) {
			this.level = level;
			this.center = center;
			this.owner = owner;
			this.primaryId = primaryId;
			this.maxRadius = maxRadius;
		}
	}

	private Shockwaves() { }

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAVES.clear());
	}

	public static void spawn(ServerLevel level, Vec3 center, ServerPlayer owner, Entity primary, double fallDistance) {
		if (WAVES.size() >= MAX_ACTIVE) {
			return;
		}

		VoidreaverConfig cfg = VoidreaverConfig.get();
		double fallFactor = Math.min(1.0, fallDistance / 20.0);
		double radius = cfg.shockwaveRadius * (0.75 + 0.25 * fallFactor);

		WAVES.add(new Wave(level, center, owner.getUUID(), primary.getId(), radius));

		ShockwavePayload payload = new ShockwavePayload(ShockwavePayload.SMASH, center.x, center.y, center.z, (float) radius, (float) cfg.shockwaveSpeed, (float) (0.8 + 0.8 * fallFactor));

		for (ServerPlayer player : PlayerLookup.around(level, center, 96.0)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	private static void tick() {
		if (WAVES.isEmpty()) {
			return;
		}

		VoidreaverConfig cfg = VoidreaverConfig.get();
		Iterator<Wave> iterator = WAVES.iterator();

		while (iterator.hasNext()) {
			Wave wave = iterator.next();
			wave.radius += cfg.shockwaveSpeed;
			double reach = Math.min(wave.radius, wave.maxRadius);

			ServerPlayer owner = wave.level.getServer().getPlayerList().getPlayer(wave.owner);
			DamageSource source = owner != null ? wave.level.damageSources().playerAttack(owner) : wave.level.damageSources().generic();

			AABB area = new AABB(wave.center, wave.center).inflate(reach + 1.0, cfg.shockwaveVerticalReach + 1.0, reach + 1.0);
			List<LivingEntity> entities = wave.level.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && !e.isSpectator());

			for (LivingEntity entity : entities) {
				if (wave.alreadyHit.contains(entity.getId()) || entity.getId() == wave.primaryId) {
					continue;
				}

				if (owner != null && entity == owner && !cfg.shockwaveHurtsOwner) {
					continue;
				}

				double dx = entity.getX() - wave.center.x;
				double dz = entity.getZ() - wave.center.z;
				double dy = entity.getY() - wave.center.y;
				double distance = Math.sqrt(dx * dx + dz * dz);

				// The ring has to have reached the entity, and the entity must be near ground level (not high in the air).
				if (distance > reach + entity.getBbWidth() * 0.5 || dy < -2.5 || dy > cfg.shockwaveVerticalReach) {
					continue;
				}

				wave.alreadyHit.add(entity.getId());

				double edge = Math.min(1.0, distance / wave.maxRadius);
				double falloff = 1.0 - (1.0 - cfg.shockwaveMinDamageFraction) * edge;

				if (entity.hurtServer(wave.level, source, (float) (cfg.shockwaveDamage * falloff))) {
					double resistance = 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					double horizontal = cfg.shockwaveKnockback * falloff * Math.max(0.0, resistance);
					double vertical = cfg.shockwaveUpwardKnockback * Math.max(0.0, resistance);
					double length = Math.max(0.001, distance);
					double pushX = distance < 0.01 ? 0.0 : dx / length * horizontal;
					double pushZ = distance < 0.01 ? 0.0 : dz / length * horizontal;

					entity.setDeltaMovement(entity.getDeltaMovement().add(pushX, vertical, pushZ));
					entity.hurtMarked = true;
				}
			}

			if (wave.radius >= wave.maxRadius + cfg.shockwaveSpeed) {
				iterator.remove();
			}
		}
	}
}
