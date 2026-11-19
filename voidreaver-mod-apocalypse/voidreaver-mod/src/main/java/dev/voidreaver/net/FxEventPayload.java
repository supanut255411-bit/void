package dev.voidreaver.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.voidreaver.VoidreaverMod;

/** Server to nearby clients: a one-off cosmetic event at a position (for example where a melee hit landed). */
public record FxEventPayload(int kind, double x, double y, double z, float dx, float dy, float dz, float strength) implements CustomPacketPayload {
	public static final int MELEE_HIT = 0;
	/** An entity caught by the Apocalypse Stomp. dx/dz = outward direction, strength 0..1 (closer to the impact = stronger). */
	public static final int ULT_HIT = 1;

	public static final CustomPacketPayload.Type<FxEventPayload> TYPE = new CustomPacketPayload.Type<>(VoidreaverMod.id("fx_event"));
	public static final StreamCodec<FriendlyByteBuf, FxEventPayload> CODEC = StreamCodec.ofMember(FxEventPayload::write, FxEventPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeByte(kind);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeFloat(dx);
		buf.writeFloat(dy);
		buf.writeFloat(dz);
		buf.writeFloat(strength);
	}

	private static FxEventPayload read(FriendlyByteBuf buf) {
		return new FxEventPayload(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
