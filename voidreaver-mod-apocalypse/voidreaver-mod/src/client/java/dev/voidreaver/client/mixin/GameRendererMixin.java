package dev.voidreaver.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;

import dev.voidreaver.client.UltFx;

/**
 * Adds the Apocalypse Stomp's speed-based field-of-view boost to whatever vanilla computes (so the player's own FOV setting
 * and effects still apply). The boost is 0 whenever the stomp is not running for the local player, and the mixin config is
 * not "required", so if a Minecraft update changes this method the game still starts, only without the FOV effect.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
	private void voidreaver$stompFov(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		float boost = UltFx.fovBoost();

		if (boost > 0.01f && cir.getReturnValue() != null) {
			cir.setReturnValue(cir.getReturnValue() + boost);
		}
	}
}
