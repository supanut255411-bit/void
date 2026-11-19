package dev.voidreaver.util;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Small helpers for sending one packet to the right group of players. */
public final class Net {
	private Net() { }

	/** To the owner and everyone who can currently see the owner. */
	public static void toOwnerAndWatchers(ServerPlayer owner, CustomPacketPayload payload) {
		ServerPlayNetworking.send(owner, payload);

		for (ServerPlayer watcher : PlayerLookup.tracking(owner)) {
			ServerPlayNetworking.send(watcher, payload);
		}
	}

	/** To everyone within {@code radius} blocks of a position. */
	public static void around(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
		for (ServerPlayer player : PlayerLookup.around(level, pos, radius)) {
			ServerPlayNetworking.send(player, payload);
		}
	}
}
