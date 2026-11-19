package dev.voidreaver;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import dev.voidreaver.chain.ChainManager;
import dev.voidreaver.ultimate.UltimateManager;

/**
 * Right-click launches the chain; Shift + right-click starts the Apocalypse Stomp instead.
 * Left-click is handled in {@link dev.voidreaver.combat.MeleeHandler}. The item has no durability and never breaks.
 */
public class VoidreaverItem extends Item {
	public VoidreaverItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.FAIL;
		}

		boolean started = serverPlayer.isShiftKeyDown()
				? UltimateManager.tryActivate(serverPlayer)
				: ChainManager.tryLaunch(serverPlayer, hand);

		return started ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}
}
