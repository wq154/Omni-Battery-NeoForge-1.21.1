package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * 电池设置菜单（移植自 1.20.1 原版）。
 * 8 个 data slot：0-1 能量(long)、2-3 容量(long)、4 等级、5 模式、6 速率档、7 范围。
 * 服务端持有 BlockEntity 引用并每次 broadcastChanges 时刷新；客户端仅由 data slot 同步显示。
 */
public class OmniBatteryMenu extends AbstractContainerMenu {
    private final ContainerData data;
    private final OmniBatteryBlockEntity blockEntity;

    /** 服务端：从方块实体创建 */
    public OmniBatteryMenu(int id, Inventory inv, OmniBatteryBlockEntity be) {
        super(ModMenuTypes.OMNI_BATTERY.get(), id);
        this.blockEntity = be;
        this.data = new SimpleContainerData(15 + OmniBatteryBlockEntity.HISTORY_SIZE * 4);
        addDataSlots(data);
    }

    /** 客户端：由 MenuType 工厂创建（不含 BE 引用，纯 data slot 显示） */
    public OmniBatteryMenu(int id, Inventory inv) {
        this(id, inv, (OmniBatteryBlockEntity) null);
    }

    @Override
    public boolean stillValid(Player player) {
        // 客户端没有 BE 引用，保持打开；服务端在方块被移除后关闭
        return blockEntity == null || (!blockEntity.isRemoved() && blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        // 先更新 data slots，再调用 super 发送给客户端
        // （顺序反了会导致客户端收到旧值，因为 super.broadcastChanges 才真正把数据发出去）
        if (blockEntity != null) {
            syncLong(0, blockEntity.getEnergy());            // slot 0-1
            syncLong(2, blockEntity.getTier().capacity());   // slot 2-3
            data.set(4, blockEntity.getTier().ordinal());
            data.set(5, blockEntity.getMode().ordinal());
            data.set(6, blockEntity.getRateIndex());
            data.set(7, blockEntity.getRange());
            // 实时速率：absorbed 占 8-9，supplied 占 10-11（不能重叠！）
            // 之前的 bug：syncLong(9, supplied) 会覆盖 syncLong(8, absorbed) 的 slot 9
            syncLong(8, blockEntity.getAbsorbedPerSecond());   // slot 8-9
            syncLong(10, blockEntity.getSuppliedPerSecond());  // slot 10-11
            data.set(12, blockEntity.isChargeInventory() ? 1 : 0);
            data.set(13, blockEntity.isChargeCurios() ? 1 : 0);
            data.set(14, blockEntity.getAccess().ordinal());
            // 趋势图历史：每点 4 个 int slot（absorb long + supply long）
            for (int i = 0; i < OmniBatteryBlockEntity.HISTORY_SIZE; i++) {
                syncLong(15 + i * 4, blockEntity.getAbsorbHistory(i));
                syncLong(15 + i * 4 + 2, blockEntity.getSupplyHistory(i));
            }
        }
        super.broadcastChanges();
    }

    private void syncLong(int index, long value) {
        data.set(index, (int) (value & 0xFFFFFFFFL));
        data.set(index + 1, (int) (value >>> 32 & 0xFFFFFFFFL));
    }

    private long readLong(int index) {
        return Integer.toUnsignedLong(data.get(index + 1)) << 32 | Integer.toUnsignedLong(data.get(index));
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (blockEntity == null) return false;
        // 注意：这里**不能**自动认领，否则任何玩家点一下按钮就会变成主人，
        // 从而改掉别人的电池配置。认领只发生在放置方块时或潜行右键时。
        if (!blockEntity.canManage(player)) return false;
        switch (button) {
            case 0 -> blockEntity.setMode(blockEntity.getMode().next());
            case 1 -> blockEntity.setRateIndex(Math.min(4, blockEntity.getRateIndex() + 1));
            case 2 -> blockEntity.setRateIndex(Math.max(0, blockEntity.getRateIndex() - 1));
            case 3 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), true));
            case 4 -> blockEntity.setRange(cycleRange(blockEntity.getRange(), blockEntity.getTier(), false));
            case 6 -> blockEntity.setChargeInventory(!blockEntity.isChargeInventory());
            case 7 -> blockEntity.setChargeCurios(!blockEntity.isChargeCurios());
            case 9 -> {
                net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "§6[万能电池·用电报告] §r电池 @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ()), false);
                for (String line : blockEntity.drainReport()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal("  " + line), false);
                }
            }
            case 8 -> {
                blockEntity.setAccess(blockEntity.getAccess().next());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                        "电池权限：" + blockEntity.getAccess().display()), true);
            }
            default -> {
                return false;
            }
        }
        broadcastChanges();
        return true;
    }

    /**
     * 范围档位切换：+ 只升档、- 只降档，到达上下限后保持（不再循环回绕）。
     * 终极电池在最大有限档之上还有"全维度(-1)"。
     */
    private int cycleRange(int current, BatteryTier tier, boolean increase) {
        int[] steps = tier.rangeSteps();
        if (current < 0) {
            // 当前已是全维度：继续 + 保持全维度，- 退回最大有限档
            return increase ? -1 : steps[steps.length - 2];
        }
        if (increase) {
            for (int s : steps) {
                if (s < 0) return -1;   // 到全维度（仅终极有）
                if (s > current) return s;
            }
            return current;             // 已是最大有限档
        }
        int best = current;
        for (int s : steps) {
            if (s >= 0 && s < current) best = s;
        }
        return best;                    // 已是最小档则保持不变
    }

    // ---------------- 供 GUI 读取（data slot 同步值） ----------------

    public long getEnergy() { return readLong(0); }
    public long getMaxEnergy() { return readLong(2); }
    public long getAbsorbedPerSecond() { return readLong(8); }   // slot 8-9
    public long getSuppliedPerSecond() { return readLong(10); }  // slot 10-11

    // ---------------- 趋势图历史（客户端从 data slot 读取） ----------------
    public int getHistorySize() { return OmniBatteryBlockEntity.HISTORY_SIZE; }
    public long getAbsorbHistory(int i) { return readLong(15 + i * 4); }
    public long getSupplyHistory(int i) { return readLong(15 + i * 4 + 2); }
    public boolean isChargeInventory() { return data.get(12) != 0; }
    public boolean isChargeCurios() { return data.get(13) != 0; }
    public BatteryAccess getAccess() {
        return BatteryAccess.values()[Math.max(0, Math.min(data.get(14), BatteryAccess.values().length - 1))];
    }
    public String getAccessDisplay() { return getAccess().display(); }
    public BatteryTier getTier() {
        return BatteryTier.values()[Math.max(0, Math.min(data.get(4), BatteryTier.values().length - 1))];
    }
    public BatteryMode getMode() {
        return BatteryMode.values()[Math.max(0, Math.min(data.get(5), BatteryMode.values().length - 1))];
    }
    public int getRateIndex() { return data.get(6); }
    public int getRange() { return data.get(7); }

    public float getEnergyRatio() {
        return getMaxEnergy() > 0L ? (float) Math.min(1.0, (double) getEnergy() / (double) getMaxEnergy()) : 0.0f;
    }

    public String getRateDisplay() {
        BatteryTier tier = getTier();
        if (tier.isUltimate() && getRateIndex() >= tier.rates().length - 1) return "\u65e0\u9650";
        return fmt(tier.rate(getRateIndex())) + " FE/t";
    }

    public static String fmt(long v) {
        // 完整数字（千分位），不加 K/M/B/T 缩写
        if (v == Long.MAX_VALUE) return "\u221e";  // 无限
        return String.format("%,d", v);
    }

    /** 简写数字：K/M/B/T 单位 + 两位小数（用于终极电池等位数极长的显示）。 */
    public static String fmtShort(long v) {
        if (v >= 1_000_000_000_000L) return String.format("%.2fT", v / 1e12);
        if (v >= 1_000_000_000L) return String.format("%.2fB", v / 1e9);
        if (v >= 1_000_000L) return String.format("%.2fM", v / 1e6);
        if (v >= 1_000L) return String.format("%.2fK", v / 1e3);
        return String.valueOf(v);
    }

    public String getRangeDisplay() {
        int r = getRange();
        return r < 0 ? "\u5168\u7ef4\u5ea6" : String.format("%,d \u683c", r);
    }
}