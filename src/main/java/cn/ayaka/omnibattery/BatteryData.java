package cn.ayaka.omnibattery;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * 物品 NBT 读写（键名与 1.20.1 原版完全一致，旧存档物品可直接沿用）。
 * 1.20.5+ 的 NBT 自定义数据放在 CUSTOM_DATA 组件中。
 */
public final class BatteryData {
    public static final String ENERGY = "OmniEnergy";
    public static final String RANGE = "OmniRange";
    public static final String RATE = "OmniRate";
    public static final String MODE = "OmniMode";
    public static final String WORK = "OmniWorking";

    private BatteryData() {}

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void save(ItemStack stack, Consumer<CompoundTag> edit) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, edit);
    }

    public static long getEnergy(ItemStack stack) {
        CompoundTag t = tag(stack);
        if (!t.contains(ENERGY)) return 0L;
        return Math.max(0L, t.getLong(ENERGY));
    }

    public static void setEnergy(ItemStack stack, long energy, BatteryTier tier) {
        long e = energy;
        save(stack, t -> t.putLong(ENERGY, Math.max(0L, Math.min(tier.capacity(), e))));
    }

    public static int getRateIndex(ItemStack stack) {
        return Math.max(0, Math.min(4, tag(stack).getInt(RATE)));
    }

    public static void setRateIndex(ItemStack stack, int index) {
        int i = index;
        save(stack, t -> t.putInt(RATE, Math.max(0, Math.min(4, i))));
    }

    public static int cycleRate(ItemStack stack) {
        int next = (getRateIndex(stack) + 1) % 5;
        setRateIndex(stack, next);
        return next;
    }

    public static BatteryMode getMode(ItemStack stack) {
        int id = tag(stack).getInt(MODE);
        BatteryMode[] v = BatteryMode.values();
        return id >= 0 && id < v.length ? v[id] : BatteryMode.BOTH;
    }

    public static void setMode(ItemStack stack, BatteryMode mode) {
        BatteryMode m = mode;
        save(stack, t -> t.putInt(MODE, m.ordinal()));
    }

    public static BatteryMode cycleMode(ItemStack stack) {
        BatteryMode next = getMode(stack).next();
        setMode(stack, next);
        return next;
    }

    public static int getRange(ItemStack stack, BatteryTier tier) {
        CompoundTag t = tag(stack);
        if (!t.contains(RANGE)) return tier.defaultRange();
        int range = t.getInt(RANGE);
        if (tier.isUltimate() && range < 0) return -1;
        return Math.max(1, range);
    }

    public static void setRange(ItemStack stack, BatteryTier tier, int range) {
        int r = range;
        if (tier.isUltimate() && r < 0) {
            save(stack, t -> t.putInt(RANGE, -1));
            return;
        }
        int max = Math.max(1, tier.rangeSteps()[tier.rangeSteps().length - 1]);
        int rr = Math.max(1, Math.min(max, r));
        save(stack, t -> t.putInt(RANGE, rr));
    }

    public static int cycleRange(ItemStack stack, BatteryTier tier, boolean reverse) {
        int[] steps = tier.rangeSteps();
        int current = getRange(stack, tier);
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == current) { idx = i; break; }
        }
        idx = reverse ? (idx - 1 + steps.length) % steps.length : (idx + 1) % steps.length;
        setRange(stack, tier, steps[idx]);
        return steps[idx];
    }

    public static boolean isWorking(ItemStack stack) {
        CompoundTag t = tag(stack);
        return !t.contains(WORK) || t.getBoolean(WORK);
    }

    public static void setWorking(ItemStack stack, boolean working) {
        boolean w = working;
        save(stack, t -> t.putBoolean(WORK, w));
    }

    public static int clampToForgeInt(long value) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, value));
    }
}