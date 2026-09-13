package dev.rbd.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImageAckPayload(String id,int part,boolean accepted) implements CustomPacketPayload {
    public static final Type<ImageAckPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("rbd","image_ack"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ImageAckPayload> CODEC=StreamCodec.composite(
        ByteBufCodecs.stringUtf8(64),ImageAckPayload::id,ByteBufCodecs.VAR_INT,ImageAckPayload::part,
        ByteBufCodecs.BOOL,ImageAckPayload::accepted,ImageAckPayload::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
