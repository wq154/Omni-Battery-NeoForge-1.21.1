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
    // ---------------- 用电配置页 ----------------
    /** 界面最多展示的机器数（仅用于按钮 id 分配；实际列表由 BE 的 NBT 同步，无上限时为 0）。 */
    public static final int TARGET_COUNT = 0;

    /** 主界面"切换电池"按钮 id（切到贴纸绑定的下一块电池）。 */
    public static final int SWITCH_BATTERY = 800;
    /** 电池位置的数据槽（服务端每 tick 写入，客户端据此查找客户端 BE）。 */
    private static final int POS_SLOT = 15 + OmniBatteryBlockEntity.HISTORY_SIZE * 4 + 8;


    private final ContainerData data;
    private final OmniBatteryBlockEntity blockEntity;

    /** 服务端：从方块实体创建 */
    public OmniBatteryMenu(int id, Inventory inv, OmniBatteryBlockEntity be) {
        super(ModMenuTypes.OMNI_BATTERY.get(), id);
        this.blockEntity = be;
        // menuPos 由 data slot 提供（见 POS_SLOT）
        this.data = new SimpleContainerData(15 + OmniBatteryBlockEntity.HISTORY_SIZE * 4 + 11);
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
            // 电池坐标（供客户端查找客户端 BE 上的同步列表）
            net.minecraft.core.BlockPos bp = blockEntity.getBlockPos();
            data.set(POS_SLOT, bp.getX());
            data.set(POS_SLOT + 1, bp.getY());
            data.set(POS_SLOT + 2, bp.getZ());
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
                if (button >= 900 && button < 900 + 256) {
                    // 下拉选择：切到第 (button-900) 个绑定并打开它（延迟到下一 tick，避免容器崩溃）
                    int idx = button - 900;
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp3) {
                        sp3.server.tell(new net.minecraft.server.TickTask(
                                sp3.server.getTickCount() + 1,
                                () -> cn.ayaka.omnibattery.network.OpenBoundBatteryPayload.handleOpenIndex(sp3, idx)));
                    }
                    return true;
                }
                if (button == SWITCH_BATTERY) {
                    // 切到贴纸绑定的下一块电池并打开它的界面。
                    // 关键：绝不能在这个菜单回调内部直接 openMenu —— 旧容器此刻仍在 tick 处理中，
                    // 立刻换菜单会破坏容器状态并直接导致服务端崩溃。推迟到下一 tick 执行。
                    if (player instanceof net.minecraft.server.level.ServerPlayer sp2) {
                        sp2.server.tell(new net.minecraft.server.TickTask(
                                sp2.server.getTickCount() + 1,
                                () -> cn.ayaka.omnibattery.network.OpenBoundBatteryPayload.handleOpen(sp2, true)));
                    }
                    return true;
                }
                if (button >= 400 && button < 4000) {
                    return applyTargetOption((button - 400) / 5, (button - 400) % 5, player);
                }
                if (button == 700) {     // 交换正/反序（纯客户端操作，这里只是兜底）
                    return true;
                }
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

    // ---------------- 用电配置页读取 ----------------
    public net.minecraft.core.BlockPos getMenuPos() {
        return new net.minecraft.core.BlockPos(data.get(POS_SLOT), data.get(POS_SLOT + 1), data.get(POS_SLOT + 2));
    }

    /** 把第 index 台机器设为指定模式（option: 0 吸电 / 1 供电 / 2 过载 / 3 清除标签）。 */
    public boolean applyTargetOption(int index, int option, Player player) {
        if (blockEntity == null) return false;
        return blockEntity.applyTargetByIndex(index, option, player);
    }

}