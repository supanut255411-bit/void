package dev.voidreaver.util;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import dev.voidreaver.chain.ChainManager;
import dev.voidreaver.config.VoidreaverConfig;
import dev.voidreaver.net.CooldownPayload;
import dev.voidreaver.ultimate.UltimateManager;

/** Tells a player's HUD how long the chain and the Apocalypse Stomp still need to recharge. The server stays the authority. */
public final class CooldownSync {
	private CooldownSync() { }

	public static void send(ServerPlayer player) {
		VoidreaverConfig cfg = VoidreaverConfig.get();
		int now = player.level().getServer().getTickCount();
		ServerPlayNetworking.send(player, new CooldownPayload(
				ChainManager.cooldownLeft(player.getUUID(), now), cfg.chainCooldownTicks,
				UltimateManager.cooldownLeft(player.getUUID(), now), cfg.ultimateCooldownTicks));
	}
}
