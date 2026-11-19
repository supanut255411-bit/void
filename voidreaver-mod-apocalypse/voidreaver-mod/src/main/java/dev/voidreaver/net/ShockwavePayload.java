package dev.voidreaver.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.voidreaver.VoidreaverMod;

/** Server to nearby clients: draw an expanding shockwave (ring, dust, debris, rumble). Kind 0 = smash, 1 = Apocalypse Stomp. */
public record ShockwavePayload(int kind, double x, double y, double z, float radius, float speed, float intensity) implements CustomPacketPayload {
	public static final int SMASH = 0;
	public static final int ULTIMATE = 1;

	public static final CustomPacketPayload.Type<ShockwavePayload> TYPE = new CustomPacketPayload.Type<>(VoidreaverMod.id("shockwave"));
	public static final StreamCodec<FriendlyByteBuf, ShockwavePayload> CODEC = StreamCodec.ofMember(ShockwavePayload::write, ShockwavePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeByte(kind);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeFloat(radius);
		buf.writeFloat(speed);
		buf.writeFloat(intensity);
	}

	private static ShockwavePayload read(FriendlyByteBuf buf) {
		return new ShockwavePayload(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
