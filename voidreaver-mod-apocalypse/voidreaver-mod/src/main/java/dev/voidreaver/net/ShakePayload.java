package dev.voidreaver.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.voidreaver.VoidreaverMod;

/** Server to the attacking player only: a short local camera shake (melee hit, chain recoil). */
public record ShakePayload(float intensity, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ShakePayload> TYPE = new CustomPacketPayload.Type<>(VoidreaverMod.id("shake"));
	public static final StreamCodec<FriendlyByteBuf, ShakePayload> CODEC = StreamCodec.ofMember(ShakePayload::write, ShakePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeFloat(intensity);
		buf.writeVarInt(ticks);
	}

	private static ShakePayload read(FriendlyByteBuf buf) {
		return new ShakePayload(buf.readFloat(), buf.readVarInt());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
