package net.z2six.ezbalance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.z2six.ezbalance.Constants;

public record SaveBalancePayload(String json) implements CustomPacketPayload {
    public static final Type<SaveBalancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "save_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SaveBalancePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> buffer.writeUtf(payload.json(), 1_048_576),
            buffer -> new SaveBalancePayload(buffer.readUtf(1_048_576))
    );

    @Override
    public Type<SaveBalancePayload> type() {
        return TYPE;
    }
}
