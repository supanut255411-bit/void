package dev.voidreaver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import dev.voidreaver.chain.ChainManager;
import dev.voidreaver.combat.MeleeHandler;
import dev.voidreaver.combat.Shockwaves;
import dev.voidreaver.config.VoidreaverConfig;
import dev.voidreaver.net.ChainSyncPayload;
import dev.voidreaver.net.CooldownPayload;
import dev.voidreaver.net.FxEventPayload;
import dev.voidreaver.net.UltPhasePayload;
import dev.voidreaver.ultimate.UltimateManager;
import dev.voidreaver.net.ShakePayload;
import dev.voidreaver.net.ShockwavePayload;

public class VoidreaverMod implements ModInitializer {
	public static final String MOD_ID = "voidreaver";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Config first: the item's attribute tooltip reads it while it is being registered.
		VoidreaverConfig.load();

		ModItems.register();

		PayloadTypeRegistry.playS2C().register(ChainSyncPayload.TYPE, ChainSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShockwavePayload.TYPE, ShockwavePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ShakePayload.TYPE, ShakePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(FxEventPayload.TYPE, FxEventPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(UltPhasePayload.TYPE, UltPhasePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(CooldownPayload.TYPE, CooldownPayload.CODEC);

		ChainManager.init();
		MeleeHandler.init();
		Shockwaves.init();
		UltimateManager.init();
		dev.voidreaver.util.Sfx.init();

		LOGGER.info("The Voidreaver is awake.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
