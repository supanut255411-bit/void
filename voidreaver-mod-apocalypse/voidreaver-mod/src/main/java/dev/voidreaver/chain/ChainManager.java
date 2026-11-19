package dev.voidreaver.chain;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.voidreaver.ModItems;
import dev.voidreaver.config.BreakableBlocks;
import dev.voidreaver.config.VoidreaverConfig;
import dev.voidreaver.net.ChainSyncPayload;
import dev.voidreaver.net.ShakePayload;
import dev.voidreaver.ultimate.UltimateManager;
import dev.voidreaver.util.CooldownSync;
import dev.voidreaver.util.WeaponState;
import dev.voidreaver.util.Sfx;

/**
 * Server-authoritative chain logic: launch, sweep for entities and blocks, pull, retract.
 * Only players with an active chain cost any tick time; everything else is a couple of map lookups.
 */
public final class ChainManager {
	private enum Reason { MISS, WALL, ARRIVED, LOST, STUCK, TIMEOUT }

	private static final Map<UUID, ChainState> ACTIVE = new HashMap<>();
	/** Server tick at which each player's chain cooldown ends. Survives relogging, so reconnecting does not reset it. */
	private static final Map<UUID, Integer> COOLDOWN_END = new HashMap<>();

	private ChainManager() { }

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ChainManager::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ACTIVE.remove(handler.getPlayer().getUUID());
			WeaponState.setChainMode(handler.getPlayer(), false);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			WeaponState.cleanse(handler.getPlayer());
			CooldownSync.send(handler.getPlayer());
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			ACTIVE.remove(newPlayer.getUUID());
			CooldownSync.send(newPlayer);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			COOLDOWN_END.clear();
		});
	}

	// ------------------------------------------------------------------ launch

	/** Returns true if a chain was launched. */
	public static boolean tryLaunch(ServerPlayer player, InteractionHand hand) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		ServerLevel level = (ServerLevel) player.level();
		MinecraftServer server = level.getServer();
		UUID id = player.getUUID();
		int now = server.getTickCount();

		if (ACTIVE.containsKey(id) || player.isSpectator() || UltimateManager.isActive(id)) {
			return false;
		}

		Integer end = COOLDOWN_END.get(id);

		if (end != null && now < end) {
			double seconds = (end - now) / 20.0;
			player.displayClientMessage(Component.literal(String.format("Chain recharging: %.1fs", seconds)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
			return false;
		}

		Vec3 eye = player.getEyePosition();
		Vec3 dir = player.getLookAngle().normalize();
		ChainState state = new ChainState(id, level, hand == InteractionHand.MAIN_HAND, eye, dir);
		ACTIVE.put(id, state);

		// Cooldown starts now. It is tracked per player here and shown on the HUD; the item itself is never put on a vanilla
		// cooldown, because that would also block the Apocalypse Stomp, which has its own independent cooldown.
		COOLDOWN_END.put(id, now + cfg.chainCooldownTicks);
		CooldownSync.send(player);
		WeaponState.setChainMode(player, true);

		broadcast(player, new ChainSyncPayload(ChainSyncPayload.LAUNCH, player.getId(), state.mainHand,
				eye.x, eye.y, eye.z, dir.x, dir.y, dir.z, (float) cfg.chainLaunchSpeed, 0.0f, -1));
		ServerPlayNetworking.send(player, new ShakePayload(0.35f, 6));

		Vec3 head = ChainGeometry.headPosition(player, state.mainHand, 1.0f);
		// Layered launch: chain rattle first, then the throw, the chain locking, and a thin gust.
		Sfx.sound(level, head, "item.armor.equip_chain", 1.0f, 0.8f);
		Sfx.soundIn(level, head, 0, "item.trident.throw", 1.1f, 0.6f);
		Sfx.soundIn(level, head, 1, "block.chain.place", 0.9f, 0.7f);
		Sfx.soundIn(level, head, 2, "entity.wind_charge.throw", 0.6f, 1.4f);

		if (advance(player, state)) {
			ACTIVE.remove(id);
		}

		return true;
	}

	public static boolean isActive(UUID id) {
		return ACTIVE.containsKey(id);
	}

	public static int cooldownLeft(UUID id, int now) {
		Integer end = COOLDOWN_END.get(id);
		return end == null ? 0 : Math.max(0, end - now);
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();

		if (now % 1200 == 0 && !COOLDOWN_END.isEmpty()) {
			COOLDOWN_END.values().removeIf(end -> end <= now); // no memory growth from one-off players
		}

		if (ACTIVE.isEmpty()) {
			return;
		}

		Iterator<ChainState> iterator = ACTIVE.values().iterator();

		while (iterator.hasNext()) {
			ChainState state = iterator.next();
			ServerPlayer player = server.getPlayerList().getPlayer(state.owner);

			if (player == null) {
				iterator.remove();
				continue;
			}

			if (!isValidOwner(player, state)) {
				broadcast(player, simple(ChainSyncPayload.CANCEL, player, state));
				WeaponState.setChainMode(player, false);
				iterator.remove();
				continue;
			}

			boolean finished = state.phase == ChainState.Phase.EXTENDING ? advance(player, state) : pull(player, state);

			if (finished) {
				iterator.remove();
			}
		}
	}

	private static boolean isValidOwner(ServerPlayer player, ChainState state) {
		return player.isAlive()
				&& !player.isRemoved()
				&& !player.isSpectator()
				&& player.level() == state.level
				&& (player.getMainHandItem().is(ModItems.VOIDREAVER) || player.getOffhandItem().is(ModItems.VOIDREAVER));
	}

	// ------------------------------------------------------------------ extending

	/** Moves the chain tip forward one tick. Returns true if the chain is finished (retract was sent). */
	private static boolean advance(ServerPlayer player, ChainState state) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		ServerLevel level = state.level;
		double from = state.tipDist;
		double to = Math.min(cfg.chainMaxRange, from + cfg.chainLaunchSpeed);

		for (int i = 0; i <= cfg.chainMaxBlocksBroken + 1; i++) {
			Vec3 a = state.origin.add(state.dir.scale(from));
			Vec3 b = state.origin.add(state.dir.scale(to));

			BlockHitResult blockHit = level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
			double limit = hitBlock ? from + a.distanceTo(blockHit.getLocation()) : to;
			Vec3 limitPos = state.origin.add(state.dir.scale(limit));

			// The first entity the chain actually touches wins, but only if it is in front of the first solid block.
			Entity hitEntity = null;
			Vec3 hitPoint = null;
			double bestDistSqr = Double.MAX_VALUE;
			AABB search = new AABB(a, limitPos).inflate(2.0 + cfg.chainHitInflate + 2.0 * cfg.chainHitboxScale);
			List<Entity> candidates = level.getEntities(player, search, e -> isHookable(player, e));

			for (Entity candidate : candidates) {
				// The chain's hitbox: the target's box scaled around its centre, plus the margin. Only used to decide whether the
				// chain hooks it; the pull itself still stops on the target's real hitbox.
				AABB real = candidate.getBoundingBox();
				double growX = (real.maxX - real.minX) * (cfg.chainHitboxScale - 1.0) * 0.5 + cfg.chainHitInflate;
				double growY = (real.maxY - real.minY) * (cfg.chainHitboxScale - 1.0) * 0.5 + cfg.chainHitInflate;
				double growZ = (real.maxZ - real.minZ) * (cfg.chainHitboxScale - 1.0) * 0.5 + cfg.chainHitInflate;
				AABB box = real.inflate(growX, growY, growZ);
				Vec3 point;

				if (box.contains(a)) {
					point = a;
				} else {
					Optional<Vec3> clip = box.clip(a, limitPos);

					if (clip.isEmpty()) {
						continue;
					}

					point = clip.get();
				}

				double distSqr = a.distanceToSqr(point);

				if (distSqr < bestDistSqr) {
					bestDistSqr = distSqr;
					hitEntity = candidate;
					hitPoint = point;
				}
			}

			if (hitEntity != null) {
				state.tipDist = from + a.distanceTo(hitPoint);
				hook(player, state, hitEntity, hitPoint);
				return false;
			}

			if (!hitBlock) {
				state.tipDist = to;

				if (to >= cfg.chainMaxRange - 1.0E-6) {
					retract(player, state, Reason.MISS);
					return true;
				}

				return false;
			}

			BlockPos pos = blockHit.getBlockPos();
			BlockState blockState = level.getBlockState(pos);

			if (state.broken < cfg.chainMaxBlocksBroken && tryBreak(player, level, pos, blockState)) {
				state.broken++;
				from = limit + 0.05;

				if (from >= to) {
					state.tipDist = to;
					return false;
				}

				continue; // keep travelling through the hole
			}

			state.tipDist = limit;
			retract(player, state, Reason.WALL);
			return true;
		}

		state.tipDist = Math.min(to, from);
		return false;
	}

	private static boolean isHookable(ServerPlayer owner, Entity entity) {
		return entity instanceof LivingEntity living
				&& living.isAlive()
				&& !living.isSpectator()
				&& living.isPickable()
				&& living != owner.getVehicle();
	}

	/** Breaks a whitelisted block if the player is allowed to. Never touches bedrock or unbreakable blocks. */
	private static boolean tryBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		VoidreaverConfig cfg = VoidreaverConfig.get();

		if (state.isAir() || state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0.0f) {
			return false;
		}

		if (!BreakableBlocks.matches(state)) {
			return false;
		}

		BlockEntity blockEntity = level.getBlockEntity(pos);

		if (blockEntity != null && !cfg.chainCanBreakBlockEntities) {
			return false;
		}

		// Spawn protection, world border, adventure mode, and any protection mod hooked into Fabric's break event.
		if (!player.getAbilities().mayBuild || !level.mayInteract(player, pos)) {
			return false;
		}

		if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, blockEntity)) {
			return false;
		}

		boolean broken = level.destroyBlock(pos, true, player);

		if (broken) {
			PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, blockEntity);
		}

		return broken;
	}

	private static void hook(ServerPlayer player, ChainState state, Entity target, Vec3 hitPoint) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		state.phase = ChainState.Phase.PULLING;
		state.targetId = target.getId();
		state.lastTargetCenter = target.getBoundingBox().getCenter();

		broadcast(player, new ChainSyncPayload(ChainSyncPayload.HOOK, player.getId(), state.mainHand,
				state.origin.x, state.origin.y, state.origin.z, state.dir.x, state.dir.y, state.dir.z,
				(float) cfg.chainLaunchSpeed, (float) state.tipDist, target.getId()));

		// Layered hook: chain contact, impact, a metallic crack, then a short magical ring.
		Sfx.sound(state.level, hitPoint, "block.chain.hit", 1.3f, 0.9f);
		Sfx.soundIn(state.level, hitPoint, 1, "item.trident.hit", 1.0f, 0.8f);
		Sfx.soundIn(state.level, hitPoint, 2, "entity.iron_golem.damage", 0.8f, 0.9f);
		Sfx.soundIn(state.level, hitPoint, 3, "block.amethyst_block.chime", 0.6f, 1.6f);
	}

	// ------------------------------------------------------------------ pulling

	private static double[] boxOf(AABB box) {
		return new double[] {box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ};
	}

	/**
	 * Pulls the weapon user towards the hooked entity at one constant speed. There is no acceleration, no easing and no
	 * slowdown on approach. The direction is recomputed every tick from the target's current hitbox. Only the very last
	 * step is shortened, so that it ends exactly where the two hitboxes touch; then all movement stops instantly.
	 */
	private static boolean pull(ServerPlayer player, ChainState state) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		ServerLevel level = state.level;
		Entity entity = level.getEntity(state.targetId);

		if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved() || target.level() != level) {
			retract(player, state, Reason.LOST);
			return true;
		}

		state.pullTicks++;

		AABB playerBox = player.getBoundingBox();
		AABB targetBox = target.getBoundingBox();
		double[] mover = boxOf(playerBox);
		double[] goal = boxOf(targetBox);
		double gap = PullMath.boxGap(mover[0], mover[1], mover[2], mover[3], mover[4], mover[5], goal[0], goal[1], goal[2], goal[3], goal[4], goal[5]);

		if (gap > cfg.chainMaxRange * 1.6) {
			retract(player, state, Reason.LOST);
			return true;
		}

		// Hitboxes touch: stop at once. Zero velocity so no leftover momentum can carry the player into or through the target.
		boolean touching = gap <= cfg.pullContactTolerance || (state.reachedLastTick && gap <= 1.0);

		if (touching) {
			stopAtTarget(player, state);
			return true;
		}

		state.reachedLastTick = false;

		if (state.pullTicks > cfg.pullMaxTicks) {
			brake(player);
			retract(player, state, Reason.TIMEOUT);
			return true;
		}

		// Stuck detection: if terrain blocks us the gap stops shrinking. Be patient on laggy connections.
		int lagTicks = Math.max(1, Math.min(8, Math.round(player.connection.latency() / 50.0f)));

		if (gap < state.bestGap - cfg.pullStuckProgress) {
			state.bestGap = gap;
			state.stuckTicks = 0;
		} else if (++state.stuckTicks >= cfg.pullStuckTicks + 2 * lagTicks) {
			brake(player);
			retract(player, state, Reason.STUCK);
			return true;
		}

		Vec3 toTarget = targetBox.getCenter().subtract(playerBox.getCenter());

		if (toTarget.lengthSqr() < 1.0E-6) {
			stopAtTarget(player, state);
			return true;
		}

		Vec3 motion = toTarget.normalize().scale(cfg.pullSpeed);
		double fraction = PullMath.travelFraction(mover, goal, motion.x, motion.y, motion.z, 0.01);

		if (fraction <= 0.0) {
			stopAtTarget(player, state);
			return true;
		}

		if (fraction < 1.0) {
			// Last step: travel exactly to the surface of the target, no further.
			state.reachedLastTick = true;
			motion = motion.scale(fraction);
		}

		// Velocity only: the client's own physics resolves collisions, so walls and floors still block the player.
		player.setDeltaMovement(motion);
		player.hurtMarked = true;
		player.resetFallDistance();

		if (state.pullTicks % 5 == 1) {
			Sfx.sound(level, player.position().add(0.0, 0.9, 0.0), "item.trident.riptide_1", 0.6f, 1.2f);
		}

		if (state.pullTicks % 8 == 4) {
			Sfx.sound(level, player.position().add(0.0, 0.9, 0.0), "item.armor.equip_chain", 0.5f, 1.1f);
		}

		return false;
	}

	private static void stopAtTarget(ServerPlayer player, ChainState state) {
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		player.resetFallDistance();
		retract(player, state, Reason.ARRIVED);
	}

	private static void brake(ServerPlayer player) {
		player.setDeltaMovement(player.getDeltaMovement().scale(0.2));
		player.hurtMarked = true;
	}

	// ------------------------------------------------------------------ retract / network

	private static void retract(ServerPlayer player, ChainState state, Reason reason) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		WeaponState.setChainMode(player, false);

		broadcast(player, new ChainSyncPayload(ChainSyncPayload.RETRACT, player.getId(), state.mainHand,
				state.origin.x, state.origin.y, state.origin.z, state.dir.x, state.dir.y, state.dir.z,
				(float) cfg.chainLaunchSpeed, (float) state.tipDist, state.targetId));

		Vec3 tip = state.origin.add(state.dir.scale(state.tipDist));

		switch (reason) {
			case WALL -> {
				Sfx.sound(state.level, tip, "block.chain.hit", 1.0f, 0.8f);
				Sfx.soundIn(state.level, tip, 1, "block.anvil.hit", 0.5f, 1.3f);
			}
			case ARRIVED -> {
				Vec3 pos = player.position().add(0.0, 0.9, 0.0);
				Sfx.sound(state.level, pos, "item.mace.smash_air", 0.9f, 1.3f);
				Sfx.soundIn(state.level, pos, 1, "block.chain.break", 0.6f, 1.1f);
			}
			default -> {
				Vec3 head = ChainGeometry.headPosition(player, state.mainHand, 1.0f);
				Sfx.sound(state.level, head, "block.chain.break", 0.7f, 1.2f);
				Sfx.soundIn(state.level, head, 1, "item.armor.equip_chain", 0.5f, 1.2f);
			}
		}
	}

	private static ChainSyncPayload simple(int kind, ServerPlayer player, ChainState state) {
		return new ChainSyncPayload(kind, player.getId(), state.mainHand,
				state.origin.x, state.origin.y, state.origin.z, state.dir.x, state.dir.y, state.dir.z,
				0.0f, (float) state.tipDist, state.targetId);
	}

	/** Sends to the owner and everyone who can see the owner. */
	private static void broadcast(ServerPlayer owner, ChainSyncPayload payload) {
		ServerPlayNetworking.send(owner, payload);

		for (ServerPlayer watcher : PlayerLookup.tracking(owner)) {
			ServerPlayNetworking.send(watcher, payload);
		}
	}
}
