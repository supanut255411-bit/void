package dev.voidreaver.chain;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.voidreaver.config.VoidreaverConfig;

/** Shared by server and client so both agree on where the chain leaves the mace head. */
public final class ChainGeometry {
	private ChainGeometry() { }

	/** World position of the point where the chain is attached to the mace head. */
	public static Vec3 headPosition(Player player, boolean mainHand, float partialTick) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 forward = player.getViewVector(partialTick).normalize();
		Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));

		if (right.lengthSqr() < 1.0E-6) {
			right = new Vec3(1.0, 0.0, 0.0); // looking straight up or down
		}

		right = right.normalize();
		Vec3 up = right.cross(forward).normalize();
		boolean rightSide = mainHand == (player.getMainArm() == HumanoidArm.RIGHT);
		double side = rightSide ? 1.0 : -1.0;

		return eye
				.add(forward.scale(cfg.chainOriginForward))
				.add(right.scale(side * cfg.chainOriginSide))
				.add(up.scale(cfg.chainOriginUp));
	}
}
