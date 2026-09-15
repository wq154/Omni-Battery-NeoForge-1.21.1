package cn.ayaka.omnibattery.network;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 -> 服务端：按快捷键打开"贴纸绑定的电池"界面（可跨维度）。
 */
public record OpenBoundBatteryPayload(boolean unused) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenBoundBatteryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(OmniBatteryMod.MOD_ID, "open_bound_battery"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBoundBatteryPayload> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> buf.writeBoolean(msg.unused),
                    buf -> new OpenBoundBatteryPayload(buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBoundBatteryPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            handleOpen(sp, false);
        });
    }

    /** 供切换按钮复用：next=true 时先切到下一个绑定。内部保证在服务器主线程执行。 */
    public static void handleOpen(ServerPlayer sp, boolean next) {
        if (sp == null || sp.hasDisconnected()) return;
        if (!sp.server.isSameThread()) {
            sp.server.execute(() -> handleOpen(sp, next));
            return;
        }
            // 不必手持：只要身上带着已绑定的标签工具就行（主副手 -> 物品栏 -> 饰品栏）
            ItemStack sticker = ItemStack.EMPTY;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack st = sp.getItemInHand(hand);
                if (st.getItem() instanceof MachineStickerItem && MachineStickerItem.hasBind(st)) {
                    sticker = st;
                    break;
                }
            }
            if (sticker.isEmpty()) {
                var inv = sp.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack st = inv.getItem(i);
                    if (st.getItem() instanceof MachineStickerItem && MachineStickerItem.hasBind(st)) {
                        sticker = st;
                        break;
                    }
                }
            }
            if (sticker.isEmpty()) {
                ItemStack cu = cn.ayaka.omnibattery.compat.CuriosCompat.findStickerWithBind(sp);
                if (!cu.isEmpty()) sticker = cu;
            }
            if (!sticker.isEmpty() && next) {
                MachineStickerItem.cycleBind(sticker);   // 切到下一块电池
            }
            if (sticker.isEmpty()) {
                sp.displayClientMessage(Component.literal("\u8eab\u4e0a\u8981\u5e26\u7740\u5df2\u7ed1\u5b9a\u7684\u6807\u7b7e\u5de5\u5177\u624d\u80fd\u5feb\u6377\u6253\u5f00\u7535\u6c60").withStyle(net.minecraft.ChatFormatting.GRAY), true);
                return;
            }
            BlockPos pos = MachineStickerItem.getBindPos(sticker);
            String dim = MachineStickerItem.getBindDim(sticker);
            ServerLevel target = null;
            for (ServerLevel sl : sp.server.getAllLevels()) {
                if (sl.dimension().location().toString().equals(dim)) {
                    target = sl;
                    break;
                }
            }
            if (target == null || pos == null || !(target.getBlockEntity(pos) instanceof OmniBatteryBlockEntity be)) {
                sp.displayClientMessage(Component.literal("\u7ed1\u5b9a\u7684\u7535\u6c60\u4e0d\u5b58\u5728\u4e86\uff08\u5df2\u62c6\u9664\u6216\u533a\u5757\u672a\u52a0\u8f7d\uff09").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
            if (!be.canManage(sp)) {
                sp.displayClientMessage(Component.literal("\u4f60\u6ca1\u6709\u6743\u9650\u64cd\u4f5c\u8fd9\u4e2a\u7535\u6c60").withStyle(net.minecraft.ChatFormatting.RED), true);
                return;
            }
            sp.openMenu(be);
    }
}
