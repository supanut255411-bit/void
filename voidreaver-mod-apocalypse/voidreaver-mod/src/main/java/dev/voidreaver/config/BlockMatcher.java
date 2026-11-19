package dev.voidreaver.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Compiled form of a config list of blocks ("minecraft:glass" or "#minecraft:leaves"). Rebuilt when the list changes. */
public final class BlockMatcher {
	private final Supplier<List<String>> source;
	private List<String> compiledFrom;
	private final Set<Block> blocks = new HashSet<>();
	private final Set<TagKey<Block>> tags = new HashSet<>();

	public BlockMatcher(Supplier<List<String>> source) {
		this.source = source;
	}

	public boolean matches(BlockState state) {
		rebuildIfNeeded();

		if (blocks.contains(state.getBlock())) {
			return true;
		}

		for (TagKey<Block> tag : tags) {
			if (state.is(tag)) {
				return true;
			}
		}

		return false;
	}

	private void rebuildIfNeeded() {
		List<String> current = source.get();

		if (current == compiledFrom) {
			return;
		}

		compiledFrom = current;
		blocks.clear();
		tags.clear();

		for (String entry : current) {
			if (entry == null || entry.isBlank()) {
				continue;
			}

			boolean isTag = entry.startsWith("#");
			Identifier id = Identifier.tryParse(isTag ? entry.substring(1) : entry);

			if (id == null) {
				continue;
			}

			if (isTag) {
				tags.add(TagKey.create(Registries.BLOCK, id));
			} else if (BuiltInRegistries.BLOCK.containsKey(id)) {
				blocks.add(BuiltInRegistries.BLOCK.getValue(id));
			}
		}
	}
}
