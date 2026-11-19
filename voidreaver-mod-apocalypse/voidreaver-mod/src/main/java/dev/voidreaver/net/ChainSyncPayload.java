package dev.voidreaver.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import dev.voidreaver.VoidreaverMod;

/**
 * Server to client: tells clients how to animate one player's chain.
 * Clients simulate the extension themselves (origin + direction + speed), so only state changes are sent.
 */
public record ChainSyncPayload(int kind, int ownerId, boolean mainHand,
		double ox, double oy, double oz, double dx, double dy, double dz,
		float speed, float tipDist, int targetId) implements CustomPacketPayload {
	public static final int LAUNCH = 0;
	public static final int HOOK = 1;
	public static final int RETRACT = 2;
	public static final int CANCEL = 3;

	public static final CustomPacketPayload.Type<ChainSyncPayload> TYPE = new CustomPacketPayload.Type<>(VoidreaverMod.id("chain_sync"));
	public static final StreamCodec<FriendlyByteBuf, ChainSyncPayload> CODEC = StreamCodec.ofMember(ChainSyncPayload::write, ChainSyncPayload::read);

	private void write(FriendlyByteBuf buf) {
		buf.writeByte(kind);
		buf.writeVarInt(ownerId);
		buf.writeBoolean(mainHand);
		buf.writeDouble(ox);
		buf.writeDouble(oy);
		buf.writeDouble(oz);
		buf.writeFloat((float) dx);
		buf.writeFloat((float) dy);
		buf.writeFloat((float) dz);
		buf.writeFloat(speed);
		buf.writeFloat(tipDist);
		buf.writeVarInt(targetId + 1); // -1 (none) encodes as 0
	}

	private static ChainSyncPayload read(FriendlyByteBuf buf) {
		int kind = buf.readByte();
		int ownerId = buf.readVarInt();
		boolean mainHand = buf.readBoolean();
		double ox = buf.readDouble();
		double oy = buf.readDouble();
		double oz = buf.readDouble();
		double dx = buf.readFloat();
		double dy = buf.readFloat();
		double dz = buf.readFloat();
		float speed = buf.readFloat();
		float tipDist = buf.readFloat();
		int targetId = buf.readVarInt() - 1;
		return new ChainSyncPayload(kind, ownerId, mainHand, ox, oy, oz, dx, dy, dz, speed, tipDist, targetId);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
