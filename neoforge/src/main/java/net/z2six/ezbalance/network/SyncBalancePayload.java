package net.z2six.ezbalance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.z2six.ezbalance.Constants;

public record SyncBalancePayload(String json, boolean openEditor) implements CustomPacketPayload {
    public static final Type<SyncBalancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "sync_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBalancePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {
                buffer.writeUtf(payload.json(), 1_048_576);
                buffer.writeBoolean(payload.openEditor());
            },
            buffer -> new SyncBalancePayload(buffer.readUtf(1_048_576), buffer.readBoolean())
    );

    @Override
    public Type<SyncBalancePayload> type() {
        return TYPE;
    }
}
