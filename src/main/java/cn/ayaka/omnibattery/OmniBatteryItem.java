package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.util.List;

/**
 * 万能电池物品（BlockItem，移植自 1.20.1 原版）。
 * 放置时把能量/模式/速率档/范围写入方块实体；带能量耐久条。
 * 物品自身可作为能量容器被其它模组机器充放电（capability 注册见 NeoForgeEvents）。
 */
public class OmniBatteryItem extends BlockItem {
    private final BatteryTier tier;

    public OmniBatteryItem(BatteryTier tier, Item.Properties props) {
        super(blockFor(tier), props.stacksTo(1));
        this.tier = tier;
    }

    private static Block blockFor(BatteryTier tier) {
        return switch (tier) {
            case LOW -> ModBlocks.LOW.get();
            case MEDIUM -> ModBlocks.MEDIUM.get();
            case ADVANCED -> ModBlocks.ADVANCED.get();
            case ELITE -> ModBlocks.ELITE.get();
            case ULTIMATE -> ModBlocks.ULTIMATE.get();
        };
    }

    public BatteryTier getTier() { return tier; }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        long energy = BatteryData.getEnergy(stack);
        BatteryMode mode = BatteryData.getMode(stack);
        int rateIndex = BatteryData.getRateIndex(stack);
        int range = BatteryData.getRange(stack, tier);
        InteractionResult result = super.place(context);
        if (result.consumesAction() && !context.getLevel().isClientSide) {
            Level level = context.getLevel();
            BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof OmniBatteryBlockEntity)) {
                blockEntity = level.getBlockEntity(context.getClickedPos());
            }
            if (blockEntity instanceof OmniBatteryBlockEntity be) {
                be.setEnergy(energy);
                be.setMode(mode);
                be.setRateIndex(rateIndex);
                be.setRange(range);
            }
        }
        return result;
    }

    // ---------------- 耐久条（按能量比例显示） ----------------

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BatteryData.getEnergy(stack) > 0L;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * (float) Math.min(1.0, (double) BatteryData.getEnergy(stack) / (double) tier.capacity()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 4511743;
    }

    // ---------------- Tooltip ----------------

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        long energy = BatteryData.getEnergy(stack);
        int rateIndex = BatteryData.getRateIndex(stack);
        int range = BatteryData.getRange(stack, tier);
        BatteryMode mode = BatteryData.getMode(stack);
        tooltip.add(Component.translatable("tooltip.omnibattery.tier", tier.display()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.omnibattery.energy", fmt(energy), fmt(tier.capacity())).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.omnibattery.mode", mode.display()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.omnibattery.range", formatRange(range)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("tooltip.omnibattery.rate", formatRate(rateIndex)).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.literal(" "));
        tooltip.add(Component.translatable("tooltip.omnibattery.help.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omnibattery.help.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.omnibattery.help.3").withStyle(ChatFormatting.DARK_GRAY));
        if (tier.isUltimate()) {
            tooltip.add(Component.literal("\u7ec8\u6781\uff1a\u6700\u5927\u8303\u56f4\u5168\u7ef4\u5ea6\uff0c\u6700\u5927\u901f\u5ea6\u65e0\u9650").withStyle(ChatFormatting.RED));
        }
    }

    private String formatRate(int rateIndex) {
        if (tier.isUltimate() && rateIndex >= tier.rates().length - 1) return "\u65e0\u9650";
        return fmt(tier.rate(rateIndex)) + " FE/t";
    }

    private static String fmt(long value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private static Component formatRange(int range) {
        if (range < 0) return Component.literal("\u5168\u7ef4\u5ea6");
        return Component.literal(fmt(range) + " \u683c");
    }
}