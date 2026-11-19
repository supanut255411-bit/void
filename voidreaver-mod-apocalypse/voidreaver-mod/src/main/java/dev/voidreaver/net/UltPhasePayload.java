package dev.voidreaver.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.voidreaver.VoidreaverMod;

/** Server to the owner and everyone who sees them: the Apocalypse Stomp moved to a new phase. Phase 0 means it ended. */
public record UltPhasePayload(int ownerId, int phase) implements CustomPacketPayload {
	public static final int END = 0;
	public static final int CHARGE = 1;
	public static final int ASCENT = 2;
	public static final int SUSPENSION = 3;
	public static final int DESCENT = 4;
	public static final int RECOVERY = 5;

	public static final CustomPacketPayload.Type<UltPhasePayload> TYPE = new CustomPacketPayload.Type<>(VoidreaverMod.id("ult_phase"));
	public static final StreamCodec<FriendlyByteBuf, UltPhasePayload> CODEC = StreamCodec.ofMember(UltPhasePayload::write, UltPhasePayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeVarInt(ownerId);
		buf.writeByte(phase);
	}

	private static UltPhasePayload read(FriendlyByteBuf buf) {
		return new UltPhasePayload(buf.readVarInt(), buf.readByte());
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
