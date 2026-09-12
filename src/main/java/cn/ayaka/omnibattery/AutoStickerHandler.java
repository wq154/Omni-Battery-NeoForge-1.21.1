package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.compat.CuriosCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 自动贴标处理器：玩家放置方块后，若玩家身上（背包 + 副手 + 饰品栏 CHARM 槽）
 * 存在启用了"自动贴标"的 {@link MachineStickerItem}，且被放置的方块支持 FE 能量，
 * 则按贴纸当前模式自动写入 StickerSavedData。
 * <p>
 * 我们的电池自身也算"支持 FE"的方块（能量能力已注册），因此放下电池同样会自动贴标。
 */
@EventBusSubscriber(modid = OmniBatteryMod.MOD_ID)
public final class AutoStickerHandler {
    private static final Direction[] SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private AutoStickerHandler() {}

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getPlacedBlock();

        // 找玩家身上第一枚开启自动贴标的贴纸
        ItemStack sticker = findAutoSticker(player);
        if (sticker.isEmpty()) return;

        // 目标方块必须真的接受 FE，否则贴上去无意义（并可能对其它模组机器造成误解）
        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (be == null || !acceptsEnergy(serverLevel, pos, state, be)) return;

        StickerMode mode = MachineStickerItem.getSelectedMode(sticker);
        if (mode == StickerMode.CLEAR) return;   // 自动贴标模式为"清除"时不做操作

        StickerSavedData data = StickerSavedData.get(serverLevel);
        data.setMode(pos, mode);

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("\u81ea\u52a8\u8d34\u6807: ")
                        .withStyle(net.minecraft.ChatFormatting.AQUA)
                        .append(net.minecraft.network.chat.Component.literal(mode.displayZh())
                                .withStyle(colorOf(mode))), true);
    }

    private static ItemStack findAutoSticker(Player player) {
        // 主/副手优先
        for (ItemStack s : player.getHandSlots()) {
            if (s.getItem() instanceof MachineStickerItem && MachineStickerItem.isAutoTagEnabled(s)) return s;
        }
        // 背包
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof MachineStickerItem && MachineStickerItem.isAutoTagEnabled(s)) return s;
        }
        // 饰品栏（若 Curios 存在）
        return CuriosCompat.findAutoSticker(player);
    }

    private static boolean acceptsEnergy(ServerLevel level, BlockPos pos, BlockState state, BlockEntity be) {
        for (Direction dir : SIDES) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, state, be, dir) != null) return true;
        }
        return false;
    }

    private static net.minecraft.ChatFormatting colorOf(StickerMode mode) {
        return switch (mode) {
            case SUPPLY -> net.minecraft.ChatFormatting.GREEN;
            case ABSORB -> net.minecraft.ChatFormatting.YELLOW;
            case OVERLOAD -> net.minecraft.ChatFormatting.RED;
            case CLEAR -> net.minecraft.ChatFormatting.GRAY;
        };
    }
}
