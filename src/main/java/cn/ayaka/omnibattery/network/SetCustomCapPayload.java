package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：保存"自定义"标签的容量上限数值。
 */
public record SetCustomCapPayload(long value) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetCustomCapPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(OmniBatteryMod.MOD_ID, "set_custom_cap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetCustomCapPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeLong(msg.value), buf -> new SetCustomCapPayload(buf.readLong()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetCustomCapPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack st = sp.getItemInHand(hand);
                if (st.getItem() instanceof MachineStickerItem) {
                    MachineStickerItem.setCustomCap(st, payload.value());
                    sp.displayClientMessage(Component.literal("\u81ea\u5b9a\u4e49\u5bb9\u91cf\u5df2\u8bbe\u4e3a " + payload.value() + " FE")
                            .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                    return;
                }
            }
        });
    }
}
