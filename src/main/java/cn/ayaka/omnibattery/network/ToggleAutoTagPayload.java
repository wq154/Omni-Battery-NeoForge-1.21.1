package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：切换手持贴纸的"自动贴标"开关。
 * 触发方式：左键点击空气（客户端拦截），只有主/副手实际持有 MachineStickerItem 时才生效。
 */
public record ToggleAutoTagPayload(boolean unused) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToggleAutoTagPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(OmniBatteryMod.MOD_ID, "toggle_auto_tag"));

    public static final StreamCodec<FriendlyByteBuf, ToggleAutoTagPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeBoolean(msg.unused),
                    buf -> new ToggleAutoTagPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleAutoTagPayload payload, IPayloadContext ctx) {
        ctx.player().level().getServer().execute(() -> {
            var player = ctx.player();
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (stack.getItem() instanceof MachineStickerItem) {
                    boolean now = !MachineStickerItem.isAutoTagEnabled(stack);
                    MachineStickerItem.setAutoTagEnabled(stack, now);
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal(
                                    now ? "\u81ea\u52a8\u8d34\u6807\u5df2\u5f00\u542f" : "\u81ea\u52a8\u8d34\u6807\u5df2\u5173\u95ed"
                            ).withStyle(now ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY),
                            true);
                    return;
                }
            }
        });
    }
}
