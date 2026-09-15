package cn.ayaka.omnibattery;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 机器贴纸（移植自 1.20.1 原版）。
 * 潜行右键空气 = 切换模式（供电 -> 吸电 -> 过载 -> 清除）；
 * 潜行右键机器 = 贴上当前模式标签（对机器坐标写入本维度 StickerSavedData）。
 */
public class MachineStickerItem extends Item {
    private static final String TAG_MODE = "StickerMode";
    private static final String TAG_AUTO = "AutoTag";
    private static final String TAG_BX = "StickerBindX";
    private static final String TAG_BY = "StickerBindY";
    private static final String TAG_BZ = "StickerBindZ";
    private static final String TAG_BD = "StickerBindDim";
    private static final String TAG_CAP = "StickerCustomCap";   // 自定义模式的容量上限
    private static final Direction[] CAP_SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    public MachineStickerItem(Item.Properties props) {
        super(props.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.pass(stack);
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("\u6f5c\u884c\u53f3\u952e\u7a7a\u6c14\uff1a\u5207\u6362\u6807\u7b7e\u6a21\u5f0f").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (getSelectedMode(stack) == StickerMode.CUSTOM) {
            // 已经是"自定义"模式：在客户端弹出数值输入框（服务端只负责保存）
            if (level.isClientSide) {
                net.minecraft.client.Minecraft.getInstance()
                        .setScreen(new cn.ayaka.omnibattery.client.CustomCapScreen(stack));
            }
            return InteractionResultHolder.success(stack);
        }
        StickerMode next = getSelectedMode(stack).next();
        setSelectedMode(stack, next);
        player.displayClientMessage(
                Component.literal("\u5f53\u524d\u6807\u7b7e\u6a21\u5f0f: ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(next.displayZh()).withStyle(colorOf(next))), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("\u8bf7\u6f5c\u884c\u53f3\u952e\u673a\u5668\u8d34\u6807\u7b7e\uff0c\u6f5c\u884c\u53f3\u952e\u7a7a\u6c14\u5207\u6362\u6a21\u5f0f").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.PASS;
        }
        // 如果右键的是电池，顺便把这块电池绑定为"快捷键目标"
        if (level.getBlockEntity(pos) instanceof OmniBatteryBlockEntity obe && obe.canManage(player)) {
            int bound = addBind(stack, pos, level.dimension().location().toString());
            player.displayClientMessage(Component.literal("\u5df2\u7ed1\u5b9a\uff08\u5171 " + bound + " \u4e2a\uff09\uff1a\u6309\u5feb\u6377\u952e\u6253\u5f00\uff0c\u754c\u9762\u5185\u53ef\u5207\u6362: ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                            .withStyle(ChatFormatting.WHITE)), true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            player.displayClientMessage(Component.literal("\u8fd9\u91cc\u6ca1\u6709\u673a\u5668\uff0c\u65e0\u6cd5\u8d34\u6807\u7b7e").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!hasAnyEnergyCapability(level, be)) {
            player.displayClientMessage(Component.literal("\u8fd9\u4e2a\u65b9\u5757\u4e0d\u652f\u6301 FE \u80fd\u91cf\uff0c\u65e0\u6cd5\u8d34\u6807\u7b7e").withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        StickerSavedData data = StickerSavedData.get(serverLevel);
        StickerMode mode = getSelectedMode(stack);
        if (mode == StickerMode.CLEAR) {
            data.removeSticker(pos);
            player.displayClientMessage(Component.literal("\u5df2\u6e05\u9664\u673a\u5668\u6807\u7b7e").withStyle(ChatFormatting.GRAY), true);
        } else {
            // 贴标签时把"自定义"容量上限一并记录（仅 CUSTOM 模式有意义）
            data.setMode(pos, mode, player.getUUID(), player.getGameProfile().getName(), getCustomCap(stack));
            player.displayClientMessage(
                    Component.literal("\u5df2\u8d34\u6807\u7b7e: ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(mode.displayZh()).withStyle(colorOf(mode))), true);
            // 贴纸是无限使用的工具，不消耗
        }
        return InteractionResult.CONSUME;
    }

    private boolean hasAnyEnergyCapability(Level level, BlockEntity be) {
        BlockState state = level.getBlockState(be.getBlockPos());
        for (Direction dir : CAP_SIDES) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), state, be, dir) != null) return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        StickerMode mode = getSelectedMode(stack);
        boolean auto = isAutoTagEnabled(stack);
        tooltip.add(Component.literal("\u5f53\u524d\u6a21\u5f0f\uff1a").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(mode.displayZh()).withStyle(colorOf(mode))));
        tooltip.add(Component.literal("\u81ea\u52a8\u8d34\u6807\uff1a").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(auto ? "\u5df2\u5f00\u542f" : "\u5df2\u5173\u95ed")
                        .withStyle(auto ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        tooltip.add(Component.literal("\u6f5c\u884c\u53f3\u952e\u7a7a\u6c14\uff1a\u5207\u6362\u6a21\u5f0f").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("\u5de6\u952e\u7a7a\u6c14\uff1a\u5207\u6362\u81ea\u52a8\u8d34\u6807\u5f00\u5173").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("\u6f5c\u884c\u53f3\u952e\u673a\u5668\uff1a\u8d34\u4e0a\u5f53\u524d\u6a21\u5f0f\u6807\u7b7e").withStyle(ChatFormatting.GRAY));
        if (hasBind(stack)) {
            BlockPos bp = getBindPos(stack);
            tooltip.add(Component.literal("\u5df2\u7ed1\u5b9a " + getBindCount(stack) + " \u4e2a\u7535\u6c60\uff0c\u5f53\u524d\uff1a")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(bp.getX() + ", " + bp.getY() + ", " + bp.getZ())
                            .withStyle(ChatFormatting.WHITE)));
            tooltip.add(Component.literal("\u6309\u5feb\u6377\u952e\uff08\u9ed8\u8ba4 \u53cd\u659c\u6760 \u952e\uff0c\u53ef\u5728\u8bbe\u7f6e\u91cc\u6539\uff09\u8fdc\u7a0b\u6253\u5f00").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else {
            tooltip.add(Component.literal("\u6f5c\u884c\u53f3\u952e\u7535\u6c60\uff1a\u7ed1\u5b9a\u4e3a\u5feb\u6377\u951a\u70b9").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.literal("\u8bbe\u7f6e\uff1a\u53ef\u653e\u5165\u9970\u54c1\u680f\u62a4\u8eab\u7b26/CHARM \u69fd\u4f4d").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (mode == StickerMode.CUSTOM) {
            tooltip.add(Component.literal("\u81ea\u5b9a\u4e49\u5bb9\u91cf\uff1a" + getCustomCap(stack) + " FE")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal("\u6f5c\u884c\u53f3\u952e\u7a7a\u6c14\uff1a\u4fee\u6539\u6570\u503c").withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.literal("\u6a21\u5f0f\uff1a\u4f9b\u7535 \u2192 \u5435\u7535 \u2192 \u8fc7\u8f7d \u2192 \u81ea\u5b9a\u4e49 \u2192 \u6e05\u9664").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("\u81ea\u52a8\u8d34\u6807\u5f00\u542f\u540e\uff0c\u673a\u5668\u653e\u7f6e\u65f6\u81ea\u52a8\u8d34\u4e0a\u5f53\u524d\u6a21\u5f0f").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---------------- 快捷键绑定目标（可绑定多个电池）----------------
    private static final String TAG_BINDS = "StickerBinds";   // ListTag: {x,y,z,d}
    private static final String TAG_BIDX = "StickerBindIdx";  // 当前选中的索引

    /** 绑定一个电池（已绑定过则只切换选中项）。返回绑定总数。 */
    public static int addBind(ItemStack stack, BlockPos pos, String dim) {
        final int[] total = {0};
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> {
            ListTag list = t.getList(TAG_BINDS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                if (e.getInt("x") == pos.getX() && e.getInt("y") == pos.getY()
                        && e.getInt("z") == pos.getZ() && e.getString("d").equals(dim)) {
                    t.putInt(TAG_BIDX, i);
                    total[0] = list.size();
                    return;
                }
            }
            CompoundTag e = new CompoundTag();
            e.putInt("x", pos.getX());
            e.putInt("y", pos.getY());
            e.putInt("z", pos.getZ());
            e.putString("d", dim);
            list.add(e);
            t.put(TAG_BINDS, list);
            t.putInt(TAG_BIDX, list.size() - 1);
            total[0] = list.size();
        });
        return total[0];
    }

    /** 全部绑定（每项：x,y,z,dim）。旧版单绑定会自动兼容。 */
    public static java.util.List<Object[]> getBinds(ItemStack stack) {
        java.util.List<Object[]> out = new java.util.ArrayList<>();
        var t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ListTag list = t.getList(TAG_BINDS, 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            out.add(new Object[]{e.getInt("x"), e.getInt("y"), e.getInt("z"), e.getString("d")});
        }
        if (out.isEmpty() && t.contains(TAG_BX)) {
            out.add(new Object[]{t.getInt(TAG_BX), t.getInt(TAG_BY), t.getInt(TAG_BZ), t.getString(TAG_BD)});
        }
        return out;
    }

    public static int getBindCount(ItemStack stack) { return getBinds(stack).size(); }

    /** 当前选中的绑定索引（越界自动回绕）。 */
    public static int getBindIndex(ItemStack stack) {
        int n = getBindCount(stack);
        if (n == 0) return -1;
        int i = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TAG_BIDX);
        return (i % n + n) % n;
    }

    /** 直接选中断定索引（下拉选择用）。返回是否成功。 */
    public static boolean setBindIndex(ItemStack stack, int idx) {
        int n = getBindCount(stack);
        if (n == 0) return false;
        final int i = ((idx % n) + n) % n;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt(TAG_BIDX, i));
        return true;
    }

    /** 切到下一个绑定，返回新的选中项 [x,y,z,dim]，没有绑定则返回 null。 */
    public static Object[] cycleBind(ItemStack stack) {
        int n = getBindCount(stack);
        if (n == 0) return null;
        final int[] next = {(getBindIndex(stack) + 1) % n};
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt(TAG_BIDX, next[0]));
        return getBinds(stack).get(next[0]);
    }

    public static boolean hasBind(ItemStack stack) { return getBindCount(stack) > 0; }

    /** 当前选中的绑定坐标（无绑定返回 null）。 */
    public static BlockPos getBindPos(ItemStack stack) {
        java.util.List<Object[]> all = getBinds(stack);
        if (all.isEmpty()) return null;
        Object[] e = all.get(Math.max(0, getBindIndex(stack)));
        return new BlockPos((int) e[0], (int) e[1], (int) e[2]);
    }

    public static String getBindDim(ItemStack stack) {
        java.util.List<Object[]> all = getBinds(stack);
        if (all.isEmpty()) return "";
        return (String) all.get(Math.max(0, getBindIndex(stack)))[3];
    }

    /** 自定义容量上限（FE）。默认 100 万。 */
    public static long getCustomCap(ItemStack stack) {
        long v = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(TAG_CAP);
        return v > 0L ? v : 1_000_000L;
    }

    public static void setCustomCap(ItemStack stack, long value) {
        long v = Math.max(1L, Math.min(Long.MAX_VALUE / 4, value));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putLong(TAG_CAP, v));
    }

    public static StickerMode getSelectedMode(ItemStack stack) {
        int idx = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(TAG_MODE);
        StickerMode[] values = StickerMode.values();
        return idx >= 0 && idx < values.length ? values[idx] : StickerMode.SUPPLY;
    }

    public static void setSelectedMode(ItemStack stack, StickerMode mode) {
        int m = mode.ordinal();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putInt(TAG_MODE, m));
    }

    public static boolean isAutoTagEnabled(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(TAG_AUTO);
    }

    public static void setAutoTagEnabled(ItemStack stack, boolean enabled) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, t -> t.putBoolean(TAG_AUTO, enabled));
    }

    private static ChatFormatting colorOf(StickerMode mode) {
        return switch (mode) {
            case SUPPLY -> ChatFormatting.GREEN;
            case ABSORB -> ChatFormatting.YELLOW;
            case OVERLOAD -> ChatFormatting.RED;
            case CUSTOM -> ChatFormatting.GOLD;
            case CLEAR -> ChatFormatting.GRAY;
        };
    }
}