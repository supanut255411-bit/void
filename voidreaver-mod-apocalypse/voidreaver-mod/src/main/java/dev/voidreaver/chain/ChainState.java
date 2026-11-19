package dev.voidreaver.chain;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-side state of one player's chain. One instance per player at most; removed when the chain retracts. */
final class ChainState {
	enum Phase { EXTENDING, PULLING }

	final UUID owner;
	final ServerLevel level;
	final boolean mainHand;
	/** Aim ray: starts at the player's eyes and follows their exact look direction (yaw and pitch). */
	final Vec3 origin;
	final Vec3 dir;

	Phase phase = Phase.EXTENDING;
	double tipDist;
	int broken;

	// Pulling
	int targetId = -1;
	double bestGap = Double.MAX_VALUE;
	int stuckTicks;
	int pullTicks;
	/** The last velocity we ordered ends exactly on the target's surface; next tick the pull is over. */
	boolean reachedLastTick;

	ChainState(UUID owner, ServerLevel level, boolean mainHand, Vec3 origin, Vec3 dir) {
		this.owner = owner;
		this.level = level;
		this.mainHand = mainHand;
		this.origin = origin;
		this.dir = dir;
	}
}
