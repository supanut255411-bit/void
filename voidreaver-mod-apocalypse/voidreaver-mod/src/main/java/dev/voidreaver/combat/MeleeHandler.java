package dev.voidreaver.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.voidreaver.ModItems;
import dev.voidreaver.config.VoidreaverConfig;
import dev.voidreaver.net.FxEventPayload;
import dev.voidreaver.net.ShakePayload;
import dev.voidreaver.util.Net;
import dev.voidreaver.util.Sfx;

/**
 * Left-click: heavy melee hit, or the falling smash when the player is airborne and falling.
 * Runs only on the server, replacing vanilla damage for this item. Does not touch the chain or its cooldown.
 * All particles are drawn by the client from small events; only sounds are played here.
 */
public final class MeleeHandler {
	/** Prevents two hits from one swing within the same tick. */
	private static final Map<UUID, Integer> LAST_ATTACK_TICK = new HashMap<>();
	/** Own cooldown for the falling smash, separate from the swing cooldown and from the chain cooldown. */
	private static final Map<UUID, Integer> SMASH_READY_TICK = new HashMap<>();

	private MeleeHandler() { }

	public static void init() {
		AttackEntityCallback.EVENT.register(MeleeHandler::onAttack);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_ATTACK_TICK.clear();
			SMASH_READY_TICK.clear();
		});
	}

	private static InteractionResult onAttack(Player player, Level world, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		// On the client we let vanilla run so the packet is sent and the swing/cooldown indicator behave normally.
		if (world.isClientSide() || !(player instanceof ServerPlayer attacker) || attacker.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (!attacker.getMainHandItem().is(ModItems.VOIDREAVER) || !(entity instanceof LivingEntity target)) {
			return InteractionResult.PASS;
		}

		if (!target.isAttackable() || target.skipAttackInteraction(attacker)) {
			return InteractionResult.SUCCESS;
		}

		attack(attacker, target);
		return InteractionResult.SUCCESS; // cancels vanilla damage for this swing
	}

	private static void attack(ServerPlayer attacker, LivingEntity target) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		ServerLevel level = (ServerLevel) attacker.level();
		int now = level.getServer().getTickCount();
		UUID id = attacker.getUUID();

		Integer last = LAST_ATTACK_TICK.put(id, now);

		if (last != null && last == now) {
			return;
		}

		float strength = attacker.getAttackStrengthScale(0.5f);
		attacker.resetAttackStrengthTicker();

		if (strength < cfg.meleeMinStrength) {
			Sfx.sound(level, attacker.position(), "entity.player.attack.weak", 0.8f, 0.8f);
			return;
		}

		DamageSource source = level.damageSources().playerAttack(attacker);
		double fall = attacker.fallDistance;
		Integer smashReady = SMASH_READY_TICK.get(id);
		boolean smash = !attacker.onGround()
				&& fall >= cfg.smashMinFallDistance
				&& strength >= cfg.smashMinStrength
				&& !attacker.isFallFlying()
				&& !attacker.getAbilities().flying
				&& !attacker.isInWater()
				&& !attacker.onClimbable()
				&& attacker.getVehicle() == null
				&& (smashReady == null || now >= smashReady);

		attacker.causeFoodExhaustion(0.1f);

		if (smash) {
			smash(attacker, target, level, source, fall, now);
			return;
		}

		double scale = 0.2 + strength * strength * 0.8; // same charge curve as vanilla
		boolean damaged = target.hurtServer(level, source, (float) (cfg.meleeDamage * scale));

		if (damaged) {
			knockBack(attacker, target, cfg.meleeKnockback * scale, cfg.meleeUpwardKnockback * scale);
			Vec3 hitPos = target.getBoundingBox().getCenter();
			Vec3 look = attacker.getLookAngle();
			Net.around(level, hitPos, 64.0, new FxEventPayload(FxEventPayload.MELEE_HIT, hitPos.x, hitPos.y, hitPos.z,
					(float) look.x, (float) look.y, (float) look.z, (float) scale));
			// The main metallic impact is loudest; the others sit behind it and arrive a moment later.
			Sfx.sound(level, hitPos, "item.mace.smash_ground", 1.3f, 1.0f);
			Sfx.sound(level, hitPos, "block.anvil.hit", 0.7f, 0.7f);
			Sfx.soundIn(level, hitPos, 1, "entity.player.attack.strong", 0.7f, 0.8f);
			Sfx.soundIn(level, hitPos, 3, "block.chain.hit", 0.5f, 1.4f);
			ServerPlayNetworking.send(attacker, new ShakePayload(0.4f, 7));
		}
	}

	private static void smash(ServerPlayer attacker, LivingEntity target, ServerLevel level, DamageSource source, double fall, int now) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		double counted = Math.min(fall, cfg.smashMaxCountedFall);
		double damage = cfg.smashBaseDamage + cfg.smashDamagePerBlock * counted;

		if (!target.hurtServer(level, source, (float) damage)) {
			return;
		}

		SMASH_READY_TICK.put(attacker.getUUID(), now + cfg.smashCooldownTicks);

		knockBack(attacker, target, cfg.smashKnockback, cfg.smashUpwardKnockback);

		Vec3 impact = target.position();

		Sfx.sound(level, impact, "item.mace.smash_ground_heavy", 2.0f, 0.8f);
		Sfx.soundIn(level, impact, 1, "block.anvil.land", 1.5f, 0.6f);
		Sfx.soundIn(level, impact, 1, "entity.generic.explode", 1.2f, 0.7f);
		Sfx.soundIn(level, impact, 2, "entity.warden.attack_impact", 1.0f, 0.8f);
		Sfx.soundIn(level, impact, 3, "block.respawn_anchor.deplete", 0.8f, 1.3f);

		// Ring, dust, debris and rumble are all drawn by the client from this one event.
		Shockwaves.spawn(level, impact, attacker, target, fall);

		// No fall damage from the impact, plus a small rebound so the player is not glued to the floor.
		attacker.fallDistance = 0;
		Vec3 motion = attacker.getDeltaMovement();
		attacker.setDeltaMovement(motion.x * 0.2, cfg.smashBounce, motion.z * 0.2);
		attacker.hurtMarked = true;
	}

	/** Vanilla-style horizontal knockback away from the attacker plus extra vertical lift. */
	private static void knockBack(ServerPlayer attacker, LivingEntity target, double strength, double up) {
		double yaw = Math.toRadians(attacker.getYRot());
		target.knockback(strength, Math.sin(yaw), -Math.cos(yaw));
		target.setDeltaMovement(target.getDeltaMovement().add(0.0, up, 0.0));
		target.hurtMarked = true;
	}
}
