package dev.voidreaver;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

import dev.voidreaver.config.VoidreaverConfig;

public final class ModItems {
	public static final ResourceKey<Item> VOIDREAVER_KEY = ResourceKey.create(Registries.ITEM, VoidreaverMod.id("voidreaver"));
	public static Item VOIDREAVER;

	private ModItems() { }

	public static void register() {
		VoidreaverConfig cfg = VoidreaverConfig.get();

		Item.Properties properties = new Item.Properties()
				.setId(VOIDREAVER_KEY)
				.stacksTo(1)
				.rarity(Rarity.EPIC)
				.fireResistant()
				.attributes(ItemAttributeModifiers.builder()
						.add(Attributes.ATTACK_DAMAGE,
								new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, cfg.meleeDamage - 1.0, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.add(Attributes.ATTACK_SPEED,
								new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, cfg.meleeAttackSpeedModifier, AttributeModifier.Operation.ADD_VALUE),
								EquipmentSlotGroup.MAINHAND)
						.build())
				.component(DataComponents.LORE, new ItemLore(List.of(
						Component.literal("Right-click: chain grapple (" + (cfg.chainCooldownTicks / 20) + "s cooldown)").withStyle(ChatFormatting.DARK_PURPLE),
						Component.literal("Shift + right-click: APOCALYPSE STOMP (" + (cfg.ultimateCooldownTicks / 20) + "s cooldown)").withStyle(ChatFormatting.LIGHT_PURPLE),
						Component.literal("Left-click: crushing blow. Strike while falling to shatter the ground.").withStyle(ChatFormatting.DARK_PURPLE))));

		VOIDREAVER = Registry.register(BuiltInRegistries.ITEM, VOIDREAVER_KEY, new VoidreaverItem(properties));

		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(VOIDREAVER));
	}
}
