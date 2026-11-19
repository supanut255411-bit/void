package dev.voidreaver.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.voidreaver.chain.ChainGeometry;
import dev.voidreaver.chain.PullMath;
import dev.voidreaver.client.fx.Emitters;
import dev.voidreaver.net.ChainSyncPayload;

/**
 * Client-side animation state of every visible chain. The server only sends state changes (launch, hook, retract);
 * the extension itself is simulated here from origin + direction + speed, which keeps it smooth and costs no bandwidth.
 * Chain particles are spawned here from the real chain path.
 */
final class ClientChains {
	enum Phase { EXTENDING, HOOKED, RETRACTING }

	static final class Chain {
		final int ownerId;
		boolean mainHand;
		Vec3 origin;
		Vec3 dir;
		double speed;
		Phase phase = Phase.EXTENDING;
		int targetId = -1;
		double ext;
		boolean reached;
		Vec3 tip;
		Vec3 prevTip;
		int age;

		Chain(int ownerId) {
			this.ownerId = ownerId;
		}
	}

	/** Blocks per tick the chain snaps back to the mace. */
	private static final double RETRACT_SPEED = 8.0;
	private static final Map<Integer, Chain> CHAINS = new HashMap<>();

	private ClientChains() { }

	static Iterable<Chain> all() {
		return CHAINS.values();
	}

	static boolean isEmpty() {
		return CHAINS.isEmpty();
	}

	static void clear() {
		CHAINS.clear();
	}

	static void handle(ChainSyncPayload payload) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;

		if (level == null) {
			return;
		}

		switch (payload.kind()) {
			case ChainSyncPayload.LAUNCH -> {
				Chain chain = new Chain(payload.ownerId());
				apply(chain, payload);
				chain.ext = payload.speed();
				chain.tip = chain.origin.add(chain.dir.scale(chain.ext));
				chain.prevTip = chain.tip;
				CHAINS.put(payload.ownerId(), chain);

				if (level.getEntity(payload.ownerId()) instanceof Player owner) {
					Emitters.chainLaunch(ChainGeometry.headPosition(owner, chain.mainHand, 1.0f), chain.dir);
				}
			}
			case ChainSyncPayload.HOOK -> {
				Chain chain = CHAINS.get(payload.ownerId());

				if (chain == null) { // we started tracking this player mid-flight
					chain = new Chain(payload.ownerId());
					apply(chain, payload);
					chain.ext = payload.tipDist();
					chain.tip = chain.origin.add(chain.dir.scale(chain.ext));
					chain.prevTip = chain.tip;
					CHAINS.put(payload.ownerId(), chain);
				}

				chain.phase = Phase.HOOKED;
				chain.targetId = payload.targetId();
				chain.reached = false;

				Vec3 hit = chain.origin.add(chain.dir.scale(payload.tipDist()));
				Emitters.hookImpact(hit, chain.dir);

				if (client.player != null && payload.ownerId() == client.player.getId()) {
					ScreenShake.add(0.3f, 6);
				}
			}
			case ChainSyncPayload.RETRACT -> {
				Chain chain = CHAINS.get(payload.ownerId());

				if (chain == null) {
					return;
				}

				if (chain.phase == Phase.EXTENDING) {
					chain.ext = payload.tipDist();
					chain.tip = chain.origin.add(chain.dir.scale(chain.ext));
				}

				// The server says the pull is over: stop the local player instantly so no client-side momentum carries on.
				if (chain.phase == Phase.HOOKED && client.player != null && payload.ownerId() == client.player.getId()) {
					client.player.setDeltaMovement(Vec3.ZERO);
				}

				chain.phase = Phase.RETRACTING;
			}
			case ChainSyncPayload.CANCEL -> CHAINS.remove(payload.ownerId());
			default -> { }
		}
	}

	private static void apply(Chain chain, ChainSyncPayload payload) {
		chain.mainHand = payload.mainHand();
		chain.origin = new Vec3(payload.ox(), payload.oy(), payload.oz());
		chain.dir = new Vec3(payload.dx(), payload.dy(), payload.dz()).normalize();
		chain.speed = Math.max(1.0, payload.speed());
	}

	/**
	 * Runs at the very start of a client tick, after the server's velocity packet was applied and before the player moves.
	 * While being pulled, clamps this tick's movement so the player's hitbox ends exactly on the target's hitbox and never
	 * goes into it, even between server updates. Everything before the last step stays at full speed.
	 */
	static void guard(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		Chain chain = CHAINS.get(client.player.getId());

		if (chain == null || chain.phase != Phase.HOOKED) {
			return;
		}

		Entity target = client.level.getEntity(chain.targetId);

		if (target == null) {
			return;
		}

		Vec3 motion = client.player.getDeltaMovement();

		if (motion.lengthSqr() < 1.0E-6) {
			return;
		}

		AABB p = client.player.getBoundingBox();
		AABB t = target.getBoundingBox();
		double fraction = PullMath.travelFraction(
				new double[] {p.minX, p.minY, p.minZ, p.maxX, p.maxY, p.maxZ},
				new double[] {t.minX, t.minY, t.minZ, t.maxX, t.maxY, t.maxZ},
				motion.x, motion.y, motion.z, 0.01);

		if (fraction < 1.0) {
			client.player.setDeltaMovement(motion.scale(fraction));
		}
	}

	static void tick(Minecraft client) {
		ClientLevel level = client.level;

		if (level == null) {
			CHAINS.clear();
			return;
		}

		Iterator<Chain> iterator = CHAINS.values().iterator();

		while (iterator.hasNext()) {
			Chain chain = iterator.next();
			Entity owner = level.getEntity(chain.ownerId);

			if (!(owner instanceof Player player) || owner.isRemoved() || ++chain.age > 400) {
				iterator.remove();
				continue;
			}

			chain.prevTip = chain.tip;

			switch (chain.phase) {
				case EXTENDING -> {
					chain.ext += chain.speed;
					chain.tip = chain.origin.add(chain.dir.scale(chain.ext));

					if (chain.ext > 120.0) { // the server never answered; don't leave it hanging
						chain.phase = Phase.RETRACTING;
					}
				}
				case HOOKED -> {
					Entity target = level.getEntity(chain.targetId);

					if (target == null || target.isRemoved()) {
						chain.phase = Phase.RETRACTING;
						break;
					}

					Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);

					if (!chain.reached) {
						double distance = center.distanceTo(chain.origin);
						chain.ext = Math.min(chain.ext + chain.speed, distance);
						chain.reached = chain.ext >= distance - 0.01;
						chain.tip = chain.reached ? center : chain.origin.add(center.subtract(chain.origin).normalize().scale(chain.ext));
					} else {
						chain.tip = center;
					}

					// Speed lines along the real movement of the player being pulled.
					Vec3 moved = player.position().subtract(new Vec3(player.xo, player.yo, player.zo));
					Emitters.pullTrail(player.position(), moved);
				}
				case RETRACTING -> {
					Vec3 head = ChainGeometry.headPosition(player, chain.mainHand, 1.0f);
					Vec3 toHead = head.subtract(chain.tip);
					double distance = toHead.length();

					if (distance <= RETRACT_SPEED) {
						iterator.remove();
						continue;
					}

					chain.tip = chain.tip.add(toHead.scale(RETRACT_SPEED / distance));
				}
			}

			if (chain.phase != Phase.RETRACTING) {
				Emitters.chainFlight(ChainGeometry.headPosition(player, chain.mainHand, 1.0f), chain.tip, chain.phase == Phase.HOOKED);
			}
		}
	}
}
