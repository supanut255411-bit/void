package dev.voidreaver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;

import dev.voidreaver.VoidreaverMod;
import dev.voidreaver.client.fx.Emitters;
import dev.voidreaver.client.fx.Fx;
import dev.voidreaver.net.ChainSyncPayload;
import dev.voidreaver.net.CooldownPayload;
import dev.voidreaver.net.FxEventPayload;
import dev.voidreaver.net.ShakePayload;
import dev.voidreaver.net.ShockwavePayload;
import dev.voidreaver.net.UltPhasePayload;

public class VoidreaverClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Network handlers hop onto the client thread explicitly, so they are safe whichever thread Fabric calls them on.
		ClientPlayNetworking.registerGlobalReceiver(ChainSyncPayload.TYPE,
				(payload, context) -> context.client().execute(() -> ClientChains.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ShockwavePayload.TYPE,
				(payload, context) -> context.client().execute(() -> ShockwaveFx.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(ShakePayload.TYPE,
				(payload, context) -> context.client().execute(() -> ScreenShake.add(payload.intensity(), payload.ticks())));
		ClientPlayNetworking.registerGlobalReceiver(UltPhasePayload.TYPE,
				(payload, context) -> context.client().execute(() -> UltFx.handle(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CooldownPayload.TYPE,
				(payload, context) -> context.client().execute(() -> ClientCooldowns.set(payload.chainLeft(), payload.chainTotal(), payload.ultLeft(), payload.ultTotal())));
		ClientPlayNetworking.registerGlobalReceiver(FxEventPayload.TYPE,
				(payload, context) -> context.client().execute(() -> {
					Minecraft client = Minecraft.getInstance();

					if (client.level == null) {
						return;
					}

					Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
					Vec3 direction = new Vec3(payload.dx(), payload.dy(), payload.dz());

					if (payload.kind() == FxEventPayload.MELEE_HIT) {
						Emitters.meleeHit(client.level, at, direction, payload.strength());
					} else if (payload.kind() == FxEventPayload.ULT_HIT) {
						Emitters.ultHit(client.level, at, direction, payload.strength());
					}
				}));

		// Start of the tick: the server's velocity packet is already applied, the player has not moved yet.
		ClientTickEvents.START_CLIENT_TICK.register(ClientChains::guard);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientChains.tick(client);
			ShockwaveFx.tick(client);
			UltFx.tick(client);
			Fx.tick(client.level);
			ScreenShake.tick();
			ImpactFrame.tick();
			ClientCooldowns.tick();
		});

		// Chain links, shockwave rings and particles are drawn as real 3D geometry in the world.
		WorldRenderEvents.BEFORE_TRANSLUCENT.register(VoidreaverRenderer::render);

		HudElementRegistry.addLast(VoidreaverMod.id("voidreaver_hud"), VoidreaverHud::render);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientChains.clear();
			ShockwaveFx.clear();
			UltFx.clear();
			Fx.clear();
			ScreenShake.clear();
			ImpactFrame.clear();
			ClientCooldowns.clear();
		});
	}
}
