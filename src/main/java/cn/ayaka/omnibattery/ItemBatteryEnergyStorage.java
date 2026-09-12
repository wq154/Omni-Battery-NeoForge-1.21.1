package cn.ayaka.omnibattery;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 物品形态能量容器（供其它模组机器对电池物品充放电）。
 * 内部以 long 存储；对外 int API 在容量超过 int 上限时按比例映射，
 * 保证外部显示的充盈比例与物品内部一致（不再"外面看满、里面没满"）。
 */
public final class ItemBatteryEnergyStorage implements IEnergyStorage {
    private final ItemStack stack;
    private final BatteryTier tier;

    public ItemBatteryEnergyStorage(ItemStack stack, BatteryTier tier) {
        this.stack = stack;
        this.tier = tier;
    }

    private long displayCapacity() {
        return tier.isUltimate() ? (long) Integer.MAX_VALUE : tier.capacity();
    }

    private int toForge(long value) {
        long cap = displayCapacity();
        long clamped = Math.max(0L, Math.min(cap, value));
        if (cap <= Integer.MAX_VALUE) return (int) clamped;
        long mapped = (long) ((double) clamped * (double) Integer.MAX_VALUE / (double) cap);
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, mapped));
    }

    private long fromForge(int forgeValue) {
        long cap = displayCapacity();
        long v = Math.max(0, forgeValue);
        if (cap <= Integer.MAX_VALUE) return v;
        return (long) ((double) v * (double) cap / (double) Integer.MAX_VALUE);
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) return 0;
        int space = toForge(tier.capacity() - BatteryData.getEnergy(stack));
        int accepted = Math.min(maxReceive, Math.max(0, space));
        if (accepted <= 0) return 0;
        if (!simulate) {
            long energy = BatteryData.getEnergy(stack);
            BatteryData.setEnergy(stack, Math.min(tier.capacity(), energy + fromForge(accepted)), tier);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (maxExtract <= 0) return 0;
        int available = toForge(BatteryData.getEnergy(stack));
        int taken = Math.min(maxExtract, Math.max(0, available));
        if (taken <= 0) return 0;
        if (!simulate) {
            long energy = BatteryData.getEnergy(stack);
            BatteryData.setEnergy(stack, Math.max(0L, energy - fromForge(taken)), tier);
        }
        return taken;
    }

    @Override
    public int getEnergyStored() { return toForge(BatteryData.getEnergy(stack)); }

    @Override
    public int getMaxEnergyStored() { return BatteryData.clampToForgeInt(displayCapacity()); }

    @Override
    public boolean canExtract() { return true; }

    @Override
    public boolean canReceive() { return true; }
}