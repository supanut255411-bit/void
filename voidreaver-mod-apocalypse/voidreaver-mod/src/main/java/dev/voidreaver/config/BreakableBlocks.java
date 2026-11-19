package dev.voidreaver.config;

import net.minecraft.world.level.block.state.BlockState;

/** Blocks the chain may destroy ({@link VoidreaverConfig#chainBreakableBlocks}). */
public final class BreakableBlocks {
	private static final BlockMatcher MATCHER = new BlockMatcher(() -> VoidreaverConfig.get().chainBreakableBlocks);

	private BreakableBlocks() { }

	public static boolean matches(BlockState state) {
		return MATCHER.matches(state);
	}
}
