package dev.voidreaver.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Camera;

import dev.voidreaver.client.ScreenShake;

/**
 * Applies the screen shake to the camera after vanilla has positioned it.
 * The mixin config is not "required", so if a future Minecraft changes Camera the game still starts, only without shake.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private float xRot;

	@Shadow
	private float yRot;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "setup", at = @At("RETURN"), require = 0)
	private void voidreaver$applyShake(CallbackInfo ci) {
		float[] offset = ScreenShake.offset();

		if (offset != null) {
			this.setRotation(this.yRot + offset[0], this.xRot + offset[1]);
		}
	}
}
