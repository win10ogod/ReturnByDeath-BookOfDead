package dev.rbd.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record MessagePayload(String json) implements CustomPacketPayload {
    public static final Type<MessagePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rbd","message"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MessagePayload> CODEC=StreamCodec.composite(ByteBufCodecs.stringUtf8(30000),MessagePayload::json,MessagePayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
