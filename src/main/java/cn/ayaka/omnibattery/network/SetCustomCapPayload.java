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

            // 1) 更新身上的标签工具（下次贴标签时用这个新值）
            boolean found = false;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack st = sp.getItemInHand(hand);
                if (st.getItem() instanceof MachineStickerItem) {
                    MachineStickerItem.setCustomCap(st, payload.value());
                    found = true;
                    break;
                }
            }
            if (!found) {
                var inv = sp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack st = inv.getItem(i);
                    if (st.getItem() instanceof MachineStickerItem) {
                        MachineStickerItem.setCustomCap(st, payload.value());
                        break;
                    }
                }
            }

            // 2) **关键**：把这个值同步到该玩家名下所有"自定义"标签的机器。
            // 否则机器上留的还是打标签那一刻的旧数值，customCapFor() 读到旧值，
            // 于是一算 room = 旧值 - 机器电量 <= 0 就直接不灌电 —— 表现就是"打了自定义没反应"。
            int updated = 0;
            for (net.minecraft.server.level.ServerLevel sl : sp.server.getAllLevels()) {
                cn.ayaka.omnibattery.StickerSavedData data =
                        cn.ayaka.omnibattery.StickerSavedData.get(sl);
                for (net.minecraft.core.BlockPos pos : data.positions()) {
                    cn.ayaka.omnibattery.StickerSavedData.StickerEntry e = data.getEntry(pos);
                    if (e == null || e.mode() != cn.ayaka.omnibattery.StickerMode.CUSTOM) continue;
                    if (e.owner() != null && !e.owner().equals(sp.getUUID())) continue;
                    data.setMode(pos, cn.ayaka.omnibattery.StickerMode.CUSTOM,
                            e.owner(), e.ownerName(), payload.value());
                    updated++;
                }
            }

            sp.displayClientMessage(Component.literal(
                            "\u81ea\u5b9a\u4e49\u8fc7\u8f7d\u901f\u7387\u5df2\u8bbe\u4e3a " + payload.value() + " FE/t"
                                    + (updated > 0 ? "\uff08\u5df2\u540c\u6b65 " + updated + " \u53f0\u673a\u5668\uff09" : ""))
                    .withStyle(net.minecraft.ChatFormatting.GOLD), true);
        });
    }
}
