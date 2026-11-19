package dev.voidreaver.util;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

import dev.voidreaver.ModItems;

/**
 * "Chain mode" look of the weapon. While a chain is out, the held Voidreaver carries a custom-model-data flag, and the item
 * model definition switches to the chain-launch model, so everyone sees the mace change. The flag is always removed when the
 * chain ends, when the player disconnects (before their data is saved) and on join, so it can never get stuck.
 */
public final class WeaponState {
	private static final CustomModelData CHAIN_FLAG = new CustomModelData(List.of(), List.of(true), List.of(), List.of());

	private WeaponState() { }

	public static void setChainMode(ServerPlayer player, boolean on) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);

			if (stack.is(ModItems.VOIDREAVER)) {
				apply(stack, on);
			}
		}
	}

	/** Removes a stuck flag from every Voidreaver in the inventory, for example after a crash while a chain was out. */
	public static void cleanse(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);

			if (stack.is(ModItems.VOIDREAVER)) {
				apply(stack, false);
			}
		}
	}

	private static void apply(ItemStack stack, boolean on) {
		boolean has = stack.has(DataComponents.CUSTOM_MODEL_DATA);

		if (on && !has) {
			stack.set(DataComponents.CUSTOM_MODEL_DATA, CHAIN_FLAG);
		} else if (!on && has) {
			stack.remove(DataComponents.CUSTOM_MODEL_DATA);
		}
	}
}
