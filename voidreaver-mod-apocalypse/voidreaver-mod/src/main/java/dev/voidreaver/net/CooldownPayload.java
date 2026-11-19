package dev.voidreaver.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.voidreaver.VoidreaverMod;

/** Server to one player: ticks left on the chain and on the Apocalypse Stomp, with the totals for the HUD bars. */
public record CooldownPayload(int chainLeft, int chainTotal, int ultLeft, int ultTotal) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CooldownPayload> TYPE = new CustomPacketPayload.Type<>(VoidreaverMod.id("cooldowns"));
	public static final StreamCodec<FriendlyByteBuf, CooldownPayload> CODEC = StreamCodec.ofMember(CooldownPayload::write, CooldownPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(chainLeft);
		buf.writeVarInt(chainTotal);
		buf.writeVarInt(ultLeft);
		buf.writeVarInt(ultTotal);
	}

	private static CooldownPayload read(FriendlyByteBuf buf) {
		return new CooldownPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
