package net.z2six.ezbalance.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.z2six.ezbalance.Constants;

public record OpenBalancePayload() implements CustomPacketPayload {
    public static final Type<OpenBalancePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "open_balance"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBalancePayload> STREAM_CODEC = CustomPacketPayload.codec(
            (payload, buffer) -> {},
            buffer -> new OpenBalancePayload()
    );

    @Override
    public Type<OpenBalancePayload> type() {
        return TYPE;
    }
}
