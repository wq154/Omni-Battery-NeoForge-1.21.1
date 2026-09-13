package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 涓囪兘鐢垫睜鏂瑰潡瀹炰綋锛氭棤绾夸緵鑳?鍚歌兘寮曟搸锛堥€昏緫绉绘鑷?1.20.1 鍘熺増锛夈€?
 * 姣?tick 鎸夋ā寮忎笌閫熺巼棰勭畻锛屽褰撳墠缁村害 StickerSavedData 涓?琚创鏍?鐨勬満鍣ㄨ繘琛岃兘閲忎紶杈擄紝
 * 鍚屾椂鍙粰鑼冨洿鍐呯帺瀹惰儗鍖?楗板搧鏍忕墿鍝佸厖鐢点€?
 */
public class OmniBatteryBlockEntity extends BlockEntity implements MenuProvider {
    private static final Direction[] CAP_SIDES = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] DIR_SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final int MAX_BLOCKS_PER_SCAN = 512;
    private static final int MAX_PLAYER_ITEMS_PER_SCAN = 128;
    private static final int ULTIMATE_FALLBACK_CHUNK_RADIUS = 32;
    private static final int MAX_TRANSFER_LOOPS_PER_SIDE = 32768;
    /** 单个目标每 tick 的最大重复传输次数（用于无限档突破单次 int 上限 21 亿）。 */
    private static final int MAX_LOOPS_PER_TARGET = 256;

    private final BatteryTier tier;
    private long energy;
    private BatteryMode mode = BatteryMode.BOTH;
    private int rateIndex;
    private int range;
    private int tickCount;

    // ---- 实时速率统计（每 20 tick = 1 秒归零并冻结一次，供 GUI 显示）----
    private long absorbedThisSecond;
    private long suppliedThisSecond;
    private long lastAbsorbed;
    private long lastSupplied;
    private int secondTick;

    // ---- 趋势图历史（每秒采样一次，环形缓冲 60 点 = 最近 60 秒）----
    public static final int HISTORY_SIZE = 60;
    private final long[] absorbHistory = new long[HISTORY_SIZE];
    private final long[] supplyHistory = new long[HISTORY_SIZE];
    private int historyIndex;

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (maxReceive <= 0) return 0;
            int space = toForge(tier.capacity() - energy);
            int accepted = Math.min(maxReceive, Math.max(0, space));
            if (accepted <= 0) return 0;
            if (!simulate) {
                energy = Math.min(tier.capacity(), energy + fromForge(accepted));
                setChanged();
                updateChargeState();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            if (maxExtract <= 0) return 0;
            int available = toForge(energy);
            int taken = Math.min(maxExtract, Math.max(0, available));
            if (taken <= 0) return 0;
            if (!simulate) {
                energy = Math.max(0L, energy - fromForge(taken));
                setChanged();
                updateChargeState();
            }
            return taken;
        }

        @Override
        public int getEnergyStored() { return toForge(energy); }

        @Override
        public int getMaxEnergyStored() { return BatteryData.clampToForgeInt(displayCapacity()); }

        @Override
        public boolean canExtract() { return true; }

        @Override
        public boolean canReceive() { return true; }
    };

    /** 对外（int 能量 API）容量基准：终极按 int 上限，其余为真实容量。 */
    private long displayCapacity() {
        return tier.isUltimate() ? (long) Integer.MAX_VALUE : tier.capacity();
    }

    /**
     * 内部真实能量 -> 对外 int 值。
     * 容量超过 int 上限时按同一比例映射，保证外部（Jade/TOP 等）看到的充盈比例与内部一致，
     * 不会再出现"外面看满电、里面没满"。
     */
    private int toForge(long value) {
        long cap = displayCapacity();
        long clamped = Math.max(0L, Math.min(cap, value));
        if (cap <= Integer.MAX_VALUE) return (int) clamped;
        long mapped = (long) ((double) clamped * (double) Integer.MAX_VALUE / (double) cap);
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, mapped));
    }

    /** 对外 int 值 -> 内部真实能量（与 {@link #toForge(long)} 同一比例）。 */
    private long fromForge(int forgeValue) {
        long cap = displayCapacity();
        long v = Math.max(0, forgeValue);
        if (cap <= Integer.MAX_VALUE) return v;
        return (long) ((double) v * (double) cap / (double) Integer.MAX_VALUE);
    }

    /** 按当前能量比例更新方块外观档位（0-10，即每格 10%）。 */
    private void updateChargeState() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(OmniBatteryBlock.CHARGE)) return;
        long cap = displayCapacity();
        int step = energy <= 0L ? 0
                : (int) Math.max(1L, Math.min(10L, Math.round((double) energy * 10.0 / (double) Math.max(1L, cap))));
        if (state.getValue(OmniBatteryBlock.CHARGE) != step) {
            serverLevel.setBlock(worldPosition, state.setValue(OmniBatteryBlock.CHARGE, step),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    public OmniBatteryBlockEntity(BlockPos pos, BlockState state) {
        this(pos, state, state.getBlock() instanceof OmniBatteryBlock b ? b.getTier() : BatteryTier.LOW);
    }

    public OmniBatteryBlockEntity(BlockPos pos, BlockState state, BatteryTier tier) {
        super(ModBlockEntities.OMNI_BATTERY.get(), pos, state);
        this.tier = tier;
        this.range = tier.defaultRange();
    }

    public IEnergyStorage storage() { return energyStorage; }

    // ------------------------------------------------------------------ tick

    public static void tick(Level level, BlockPos pos, BlockState state, OmniBatteryBlockEntity be) {
        if (level.isClientSide) return;
        ServerLevel serverLevel = (ServerLevel) level;
        be.tickCount++;
        be.secondTick++;
        if (be.secondTick >= 20) {
            // 每秒（20 tick）结算一次：把当前秒的累计值转为 last*（供 GUI 显示），并清零
            be.lastAbsorbed = be.absorbedThisSecond;
            be.lastSupplied = be.suppliedThisSecond;
            // 记录趋势图采样点
            be.recordHistory(be.lastAbsorbed, be.lastSupplied);
            be.absorbedThisSecond = 0;
            be.suppliedThisSecond = 0;
            be.secondTick = 0;
            be.syncToClients();   // 同步 BE 本体（NBT 含 LastAbsorbed/LastSupplied）
            // 关键：同步 Menu 的 data slots（GUI 显示源），否则客户端读不到新速率
            be.broadcastMenuChangesIfOpen(serverLevel);
        }
        if (be.mode == BatteryMode.OFF) return;
        // 速率可能是 long（ULTIMATE 无限档 = Long.MAX_VALUE，突破 int 上限 21 亿）
        long rate = be.tier.rate(be.rateIndex);
        // 吸电与供电各自独立使用完整 rate 预算，互不计算（用户要求）
        if (be.mode.canAbsorb()) {
            long absorbed = be.absorbFromLoadedDimensions(serverLevel, rate);
            be.absorbedThisSecond += absorbed;
        }
        if (be.mode.canCharge()) {
            // 机器供电与玩家物品充电共享 rate 预算（合计不超过 rate，符合速率设置）
            long remaining = rate;
            long machineSupplied = be.supplyToLoadedDimensions(serverLevel, remaining);
            remaining -= machineSupplied;
            long playerSupplied = 0;
            if (remaining > 0) {
                playerSupplied = be.chargePlayersInLoadedDimensions(serverLevel, remaining);
            }
            be.suppliedThisSecond += machineSupplied + playerSupplied;
        }
        be.updateChargeState();
    }

    // ------------------------------------------------------------ 吸收（机器 -> 电池）

    private long absorbFromLoadedDimensions(ServerLevel originLevel, long budget) {
        long space = tier.capacity() - energy;
        long remaining = Math.min(budget, space);
        if (remaining <= 0) return 0;
        long start = remaining;
        for (ServerLevel level : originLevel.getServer().getAllLevels()) {
            StickerSavedData stickerData = StickerSavedData.get(level);
            for (BlockPos targetPos : getLoadedBlockEntityPositions(level)) {
                if (remaining <= 0) break;
                if (level == originLevel && targetPos.equals(worldPosition)) continue;
                StickerMode sticker = stickerData.getMode(targetPos);
                if (sticker == null || !sticker.isActiveTransferMode()) continue;
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be == null || be.isRemoved()) { stickerData.removeSticker(targetPos); continue; }
                if (be instanceof OmniBatteryBlockEntity) { stickerData.removeSticker(targetPos); continue; }
                if (!hasAnyEnergyCapability(level, be)) { stickerData.removeSticker(targetPos); continue; }
                if (sticker != StickerMode.ABSORB && sticker != StickerMode.OVERLOAD) continue;
                if (sticker == StickerMode.ABSORB) {
                    // 无限档突破：对同一目标重复传输，直到预算用尽或对方无能量（单次受 int 上限 21 亿限制）
                    int loops = 0;
                    while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                        int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                        int moved = tryAbsorbFromFirstSide(level, be, c, sticker);
                        if (moved <= 0) break;
                        remaining -= moved;
                    }
                    continue;
                }
                for (Direction dir : DIR_SIDES) {
                    if (remaining <= 0) break;
                    int loops = 0;
                    while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                        int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                        int moved = tryAbsorbFrom(level, be, dir, c, sticker);
                        if (moved <= 0) break;
                        remaining -= moved;
                    }
                }
            }
            if (remaining <= 0) break;
        }
        if (remaining < start) setChanged();
        return start - remaining;
    }

    // ------------------------------------------------------------ 供电（电池 -> 机器）

    private long supplyToLoadedDimensions(ServerLevel originLevel, long budget) {
        long remaining = Math.min(budget, energy);
        if (remaining <= 0) return 0;
        long start = remaining;
        for (ServerLevel level : originLevel.getServer().getAllLevels()) {
            StickerSavedData stickerData = StickerSavedData.get(level);
            for (BlockPos targetPos : getLoadedBlockEntityPositions(level)) {
                if (remaining <= 0) break;
                if (level == originLevel && targetPos.equals(worldPosition)) continue;
                StickerMode sticker = stickerData.getMode(targetPos);
                if (sticker == null || !sticker.isActiveTransferMode()) continue;
                BlockEntity be = level.getBlockEntity(targetPos);
                if (be == null || be.isRemoved()) { stickerData.removeSticker(targetPos); continue; }
                if (be instanceof OmniBatteryBlockEntity) { stickerData.removeSticker(targetPos); continue; }
                if (!hasAnyEnergyCapability(level, be)) { stickerData.removeSticker(targetPos); continue; }
                if (sticker != StickerMode.SUPPLY && sticker != StickerMode.OVERLOAD) continue;
                if (sticker == StickerMode.SUPPLY) {
                    // 无限档突破：对同一目标重复传输，直到预算用尽或对方已满（单次受 int 上限 21 亿限制）
                    int loops = 0;
                    while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                        int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                        int moved = trySupplyToFirstSide(level, be, c, sticker);
                        if (moved <= 0) break;
                        remaining -= moved;
                    }
                    continue;
                }
                for (Direction dir : DIR_SIDES) {
                    if (remaining <= 0) break;
                    int loops = 0;
                    while (remaining > 0 && loops++ < MAX_LOOPS_PER_TARGET) {
                        int c = (int) Math.min(remaining, Integer.MAX_VALUE);
                        int moved = trySupplyTo(level, be, dir, c, sticker);
                        if (moved <= 0) break;
                        remaining -= moved;
                    }
                }
            }
            if (remaining <= 0) break;
        }
        if (remaining < start) setChanged();
        return start - remaining;
    }

    private boolean hasAnyEnergyCapability(Level level, BlockEntity be) {
        BlockState state = level.getBlockState(be.getBlockPos());
        for (Direction dir : CAP_SIDES) {
            if (level.getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), state, be, dir) != null) return true;
        }
        return false;
    }

    private int tryAbsorbFromFirstSide(Level level, BlockEntity be, int request, StickerMode sticker) {
        for (Direction dir : DIR_SIDES) {
            int moved = tryAbsorbFrom(level, be, dir, request, sticker);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private int trySupplyToFirstSide(Level level, BlockEntity be, int request, StickerMode sticker) {
        for (Direction dir : DIR_SIDES) {
            int moved = trySupplyTo(level, be, dir, request, sticker);
            if (moved > 0) return moved;
        }
        return 0;
    }

    private int tryAbsorbFrom(Level level, BlockEntity be, Direction dir, int request, StickerMode sticker) {
        BlockState state = level.getBlockState(be.getBlockPos());
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), state, be, dir);
        if (storage == null) return 0;
        if (isOwnOrOmniBatteryStorage(be, storage)) return 0;
        if (sticker == StickerMode.ABSORB) return transferExtractOnce(storage, request);
        if (sticker == StickerMode.OVERLOAD) {
            if (!canActuallyExtract(storage, request) && readEnergyReflective(storage) <= 0L) return 0;
            int moved = transferExtractLoop(storage, request);
            if (moved < request) moved += drainEnergyReflective(storage, request - moved);
            if (moved < request) moved += drainEnergyNbt(level, be, request - moved);
            return moved;
        }
        return 0;
    }

    private int trySupplyTo(Level level, BlockEntity be, Direction dir, int request, StickerMode sticker) {
        BlockState state = level.getBlockState(be.getBlockPos());
        IEnergyStorage storage = level.getCapability(Capabilities.EnergyStorage.BLOCK, be.getBlockPos(), state, be, dir);
        if (storage == null) return 0;
        if (isOwnOrOmniBatteryStorage(be, storage)) return 0;
        if (sticker == StickerMode.SUPPLY) return transferReceiveOnce(storage, request);
        if (sticker == StickerMode.OVERLOAD) {
            int moved = transferReceiveLoop(storage, request);
            if (moved < request) moved += fillEnergyReflective(storage, request - moved);
            if (moved < request) moved += fillEnergyNbt(level, be, request - moved);
            return moved;
        }
        return 0;
    }

    private int transferExtractOnce(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        int can = storage.extractEnergy(request, true);
        if (can <= 0) return 0;
        int accepted = energyStorage.receiveEnergy(can, true);
        if (accepted <= 0) return 0;
        int extracted = storage.extractEnergy(accepted, false);
        if (extracted <= 0) return 0;
        energyStorage.receiveEnergy(extracted, false);
        return extracted;
    }

    private int transferReceiveOnce(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        int can = storage.receiveEnergy(request, true);
        if (can <= 0) return 0;
        int extracted = energyStorage.extractEnergy(can, true);
        if (extracted <= 0) return 0;
        int accepted = storage.receiveEnergy(extracted, false);
        if (accepted <= 0) return 0;
        energyStorage.extractEnergy(accepted, false);
        return accepted;
    }

    private int transferExtractLoop(IEnergyStorage storage, int request) {
        int moved = 0;
        int loops = 0;
        while (moved < request && loops++ < MAX_TRANSFER_LOOPS_PER_SIDE) {
            int want = request - moved;
            int can = storage.extractEnergy(want, true);
            if (can <= 0) break;
            int accepted = energyStorage.receiveEnergy(can, true);
            if (accepted <= 0) break;
            int extracted = storage.extractEnergy(accepted, false);
            if (extracted <= 0) break;
            energyStorage.receiveEnergy(extracted, false);
            moved += extracted;
        }
        return moved;
    }

    private int transferReceiveLoop(IEnergyStorage storage, int request) {
        int moved = 0;
        int loops = 0;
        while (moved < request && loops++ < MAX_TRANSFER_LOOPS_PER_SIDE) {
            int want = request - moved;
            int can = storage.receiveEnergy(want, true);
            if (can <= 0) break;
            int extracted = energyStorage.extractEnergy(can, true);
            if (extracted <= 0) break;
            int accepted = storage.receiveEnergy(extracted, false);
            if (accepted <= 0) break;
            energyStorage.extractEnergy(accepted, false);
            moved += accepted;
        }
        return moved;
    }

    private boolean isOwnOrOmniBatteryStorage(BlockEntity be, IEnergyStorage storage) {
        return be == this || be instanceof OmniBatteryBlockEntity || storage == energyStorage;
    }

    // ------------------------------------------------------------ OVERLOAD锛歂BT 娉ㄥ叆

    private int drainEnergyNbt(Level level, BlockEntity be, int request) {
        if (request <= 0 || be == null || be instanceof OmniBatteryBlockEntity) return 0;
        try {
            CompoundTag tag = be.saveWithId(level.registryAccess());
            NbtEnergyEdit edit = new NbtEnergyEdit(request, false);
            edit.visitCompound(tag);
            int moved = BatteryData.clampToForgeInt(edit.moved);
            if (moved <= 0) return 0;
            int accepted = energyStorage.receiveEnergy(moved, true);
            if (accepted <= 0) return 0;
            if (accepted < moved) {
                edit = new NbtEnergyEdit(accepted, false);
                tag = be.saveWithId(level.registryAccess());
                edit.visitCompound(tag);
                moved = BatteryData.clampToForgeInt(edit.moved);
            }
            be.loadWithComponents(tag, level.registryAccess());
            be.setChanged();
            energyStorage.receiveEnergy(moved, false);
            return moved;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int fillEnergyNbt(Level level, BlockEntity be, int request) {
        if (request <= 0 || be == null || be instanceof OmniBatteryBlockEntity) return 0;
        try {
            int extracted = energyStorage.extractEnergy(request, true);
            if (extracted <= 0) return 0;
            CompoundTag tag = be.saveWithId(level.registryAccess());
            NbtEnergyEdit edit = new NbtEnergyEdit(extracted, true);
            edit.visitCompound(tag);
            int moved = BatteryData.clampToForgeInt(edit.moved);
            if (moved <= 0) return 0;
            be.loadWithComponents(tag, level.registryAccess());
            be.setChanged();
            energyStorage.extractEnergy(moved, false);
            return moved;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // ------------------------------------------------------------ OVERLOAD锛氬瓧娈靛弽灏?

    private int drainEnergyReflective(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        long stored = readEnergyReflective(storage);
        if (stored <= 0L) return 0;
        int amount = BatteryData.clampToForgeInt(Math.min(request, stored));
        int accepted = energyStorage.receiveEnergy(amount, true);
        if (accepted <= 0) return 0;
        if (writeEnergyReflective(storage, stored - accepted)) {
            energyStorage.receiveEnergy(accepted, false);
            return accepted;
        }
        return 0;
    }

    private int fillEnergyReflective(IEnergyStorage storage, int request) {
        if (request <= 0) return 0;
        long stored = readEnergyReflective(storage);
        long max = readMaxEnergyReflective(storage);
        if (max <= stored) return 0;
        int amount = BatteryData.clampToForgeInt(Math.min(request, max - stored));
        int extracted = energyStorage.extractEnergy(amount, true);
        if (extracted <= 0) return 0;
        if (writeEnergyReflective(storage, stored + extracted)) {
            energyStorage.extractEnergy(extracted, false);
            return extracted;
        }
        return 0;
    }

    private static long readEnergyReflective(Object obj) {
        EnergyFields fields = findEnergyFields(obj);
        if (fields == null || fields.energy == null) return -1L;
        try {
            return getNumberField(fields.owner, fields.energy);
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static long readMaxEnergyReflective(Object obj) {
        EnergyFields fields = findEnergyFields(obj);
        if (fields == null) return -1L;
        try {
            if (fields.capacity != null) return getNumberField(fields.owner, fields.capacity);
            if (obj instanceof IEnergyStorage storage) return storage.getMaxEnergyStored();
        } catch (Throwable ignored) {
        }
        return -1L;
    }

    private static boolean writeEnergyReflective(Object obj, long value) {
        EnergyFields fields = findEnergyFields(obj);
        if (fields == null || fields.energy == null) return false;
        try {
            setNumberField(fields.owner, fields.energy, Math.max(0L, value));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static EnergyFields findEnergyFields(Object root) {
        return findEnergyFields(root, 0, new HashSet<>());
    }

    private static EnergyFields findEnergyFields(Object obj, int depth, Set<Object> visited) {
        if (obj == null || depth > 3 || visited.contains(obj)) return null;
        visited.add(obj);
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field energy = null;
            Field capacity = null;
            for (Field f : c.getDeclaredFields()) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                Class<?> t = f.getType();
                if ((t == Integer.TYPE || t == Long.TYPE || Number.class.isAssignableFrom(t)) && isEnergyName(n)) energy = f;
                if ((t == Integer.TYPE || t == Long.TYPE || Number.class.isAssignableFrom(t)) && isCapacityName(n)) capacity = f;
            }
            if (energy != null) return new EnergyFields(obj, energy, capacity);
        }
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object child = f.get(obj);
                    if (child == null || child == obj || child instanceof String || child instanceof Number || child instanceof Enum) continue;
                    String type = child.getClass().getName().toLowerCase(Locale.ROOT);
                    if (!(child instanceof IEnergyStorage) && !type.contains("energy") && !type.contains("storage")) continue;
                    EnergyFields found = findEnergyFields(child, depth + 1, visited);
                    if (found != null) return found;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean isEnergyName(String name) {
        return name.equals("energy") || name.equals("stored") || name.equals("storedenergy")
                || name.equals("energycontainer") || name.equals("f_1550_");
    }

    private static boolean isCapacityName(String name) {
        return name.equals("capacity") || name.equals("maxenergy") || name.equals("maxenergystored")
                || name.equals("maxstored") || name.equals("max") || name.equals("f_1551_");
    }

    private static long getNumberField(Object owner, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object v = field.get(owner);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    private static void setNumberField(Object owner, Field field, long value) throws IllegalAccessException {
        field.setAccessible(true);
        if (field.getType() == Integer.TYPE || field.getType() == Integer.class) {
            field.set(owner, BatteryData.clampToForgeInt(value));
        } else if (field.getType() == Long.TYPE || field.getType() == Long.class) {
            field.set(owner, value);
        }
    }

    private static boolean canActuallyExtract(IEnergyStorage storage, int request) {
        try {
            return storage.extractEnergy(Math.max(1, Math.min(request, 1024)), true) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------ 鐜╁闅忚韩鐗╁搧鍏呯數

    private long chargePlayersInLoadedDimensions(ServerLevel originLevel, long budget) {
        long remaining = Math.min(budget, energy);
        if (remaining <= 0) return 0;
        long start = remaining;
        for (ServerPlayer player : originLevel.getServer().getPlayerList().getPlayers()) {
            if (remaining <= 0) break;
            Level level = player.level();
            if (!(level instanceof ServerLevel playerLevel)) continue;
            if (!isLevelLoaded(playerLevel, player.blockPosition())) continue;
            if (!tier.isUltimate() && !isTargetInRange(player.blockPosition())) continue;
            List<ItemStack> targets = new ArrayList<>();
            Inventory inv = player.getInventory();
            targets.addAll(inv.items);
            targets.addAll(inv.armor);
            targets.addAll(inv.offhand);
            addCuriosItemsSoft(player, targets);
            int touched = 0;
            for (ItemStack target : targets) {
                if (remaining <= 0 || touched++ > MAX_PLAYER_ITEMS_PER_SCAN) break;
                if (target.isEmpty() || target.getItem() instanceof OmniBatteryItem) continue;
                if (isBackpackLikeItem(target)) continue;
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                remaining -= moveEnergyToPlayerItem(target, chunk);
            }
        }
        if (remaining < start) setChanged();
        return start - remaining;
    }

    private static boolean isBackpackLikeItem(ItemStack stack) {
        try {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT);
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            return id.contains("backpack") || id.contains("rucksack") || id.contains("bag") || id.contains("satchel")
                    || id.contains("sophisticatedbackpacks") || id.contains("travelersbackpack")
                    || name.contains("\u80cc\u5305") || name.contains("backpack") || name.contains("rucksack") || name.contains("satchel");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int moveEnergyToPlayerItem(ItemStack target, int max) {
        if (max <= 0) return 0;
        IEnergyStorage storage = target.getCapability(Capabilities.EnergyStorage.ITEM);
        if (storage == null) return 0;
        int moved = transferReceiveLoop(storage, max);
        if (moved < max) moved += fillEnergyReflective(storage, max - moved);
        return moved;
    }

    private static void addCuriosItemsSoft(ServerPlayer player, List<ItemStack> out) {
        if (!ModList.get().isLoaded("curios")) return;
        try {
            Class<?> curiosApi = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApi.getMethod("getCuriosInventory", LivingEntity.class);
            Object optional = getCuriosInventory.invoke(null, player);
            if (!(optional instanceof Optional<?> opt) || opt.isEmpty()) return;
            Object handler = opt.get();
            Method getCurios = handler.getClass().getMethod("getCurios");
            Object curios = getCurios.invoke(handler);
            if (!(curios instanceof Map<?, ?> map)) return;
            for (Object slotHandlerObj : map.values()) {
                Method getStacks = slotHandlerObj.getClass().getMethod("getStacks");
                Object stacksObj = getStacks.invoke(slotHandlerObj);
                if (!(stacksObj instanceof net.neoforged.neoforge.items.IItemHandler itemHandler)) continue;
                for (int i = 0; i < itemHandler.getSlots(); i++) {
                    out.add(itemHandler.getStackInSlot(i));
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ 鐩爣鏀堕泦锛堣寖鍥?鍏ㄧ淮搴︼級

    private static boolean isLevelLoaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    private List<BlockPos> getLoadedBlockEntityPositions(ServerLevel level) {
        return tier.isUltimate() || range < 0
                ? getUltimateLoadedBlockEntityPositions(level)
                : getRangedLoadedBlockEntityPositions(level);
    }

    private List<BlockPos> getUltimateLoadedBlockEntityPositions(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        collectPlayerViewLoadedBlockEntities(level, result, visited);
        collectAroundBatteryLoadedBlockEntities(level, result, visited);
        if (!result.isEmpty()) return result;
        return getAllLoadedBlockEntityPositions(level);
    }

    private void collectPlayerViewLoadedBlockEntities(ServerLevel level, List<BlockPos> out, Set<Long> visited) {
        int radius = Math.max(2, level.getServer().getPlayerList().getViewDistance() + 2);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            Level lv = player.level();
            if (!(lv instanceof ServerLevel playerLevel) || playerLevel != level) continue;
            collectLoadedBlockEntitiesAround(level, player.blockPosition().getX() >> 4, player.blockPosition().getZ() >> 4, radius, out, visited);
        }
    }

    private void collectAroundBatteryLoadedBlockEntities(ServerLevel level, List<BlockPos> out, Set<Long> visited) {
        if (level != this.level) return;
        collectLoadedBlockEntitiesAround(level, worldPosition.getX() >> 4, worldPosition.getZ() >> 4, ULTIMATE_FALLBACK_CHUNK_RADIUS, out, visited);
    }

    private void collectLoadedBlockEntitiesAround(ServerLevel level, int centerChunkX, int centerChunkZ, int radius, List<BlockPos> out, Set<Long> visited) {
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                long key = ChunkPos.asLong(chunkX, chunkZ);
                if (!visited.add(key)) continue;
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) continue;
                    out.add(be.getBlockPos());
                }
            }
        }
    }

    private List<BlockPos> getRangedLoadedBlockEntityPositions(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        int chunkRadius = Math.max(1, (Math.max(1, range) + 15) / 16);
        int cx = worldPosition.getX() >> 4;
        int cz = worldPosition.getZ() >> 4;
        int count = 0;
        for (int chunkX = cx - chunkRadius; chunkX <= cx + chunkRadius; chunkX++) {
            for (int chunkZ = cz - chunkRadius; chunkZ <= cz + chunkRadius; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be == null || be.isRemoved()) continue;
                    BlockPos bePos = be.getBlockPos();
                    if (!isTargetInRange(bePos)) continue;
                    result.add(bePos);
                    if (++count >= MAX_BLOCKS_PER_SCAN) return result;
                }
            }
        }
        return result;
    }

    private List<BlockPos> getAllLoadedBlockEntityPositions(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        for (LevelChunk chunk : getLoadedChunks(level)) {
            if (chunk == null) continue;
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be == null || be.isRemoved()) continue;
                result.add(be.getBlockPos());
            }
        }
        return result;
    }

    /** 生效判定与范围框显示一致：以电池为中心的 (2r+1)^3 方块立方体（|Δx|,|Δy|,|Δz| ≤ r）。 */
    private boolean isTargetInRange(BlockPos targetPos) {
        if (tier.isUltimate() || range < 0) return true;
        long r = Math.max(1, range);
        return Math.abs(worldPosition.getX() - targetPos.getX()) <= r
                && Math.abs(worldPosition.getY() - targetPos.getY()) <= r
                && Math.abs(worldPosition.getZ() - targetPos.getZ()) <= r;
    }

    /** 鍙嶅皠鏋氫妇褰撳墠鏈嶅姟绔凡鍔犺浇鐨勫叏閮?LevelChunk锛堢粓鏋佺數姹犲叏缁村害鎵弿鐢級銆?*/
    private static List<LevelChunk> getLoadedChunks(ServerLevel level) {
        List<LevelChunk> chunks = new ArrayList<>();
        try {
            ServerChunkCache chunkSource = level.getChunkSource();
            Object chunkMap = null;
            for (String name : new String[]{"chunkMap", "f_8325_"}) {
                try {
                    Field f = chunkSource.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    chunkMap = f.get(chunkSource);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            if (chunkMap == null) return chunks;
            Object visibleChunks = null;
            for (String name : new String[]{"visibleChunkMap", "f_140130_"}) {
                try {
                    Field f = chunkMap.getClass().getDeclaredField(name);
                    f.setAccessible(true);
                    visibleChunks = f.get(chunkMap);
                    break;
                } catch (NoSuchFieldException ignored) {
                }
            }
            Iterable<?> holders = null;
            if (visibleChunks instanceof Map<?, ?> map) {
                holders = map.values();
            } else if (visibleChunks instanceof Iterable<?> it) {
                holders = it;
            } else {
                try {
                    Method values = visibleChunks.getClass().getMethod("values");
                    Object valueCollection = values.invoke(visibleChunks);
                    if (valueCollection instanceof Iterable<?> it) holders = it;
                } catch (Throwable ignored) {
                }
            }
            if (holders == null) return chunks;
            for (Object holder : holders) {
                LevelChunk chunk = extractLevelChunk(holder);
                if (chunk != null) chunks.add(chunk);
            }
        } catch (Throwable ignored) {
        }
        return chunks;
    }

    private static LevelChunk extractLevelChunk(Object holder) {
        if (holder == null) return null;
        try {
            for (String methodName : new String[]{"getTickingChunk", "getFullChunk", "getEntityTickingChunk"}) {
                try {
                    Method m = holder.getClass().getMethod(methodName);
                    Object value = m.invoke(holder);
                    if (value instanceof LevelChunk chunk) return chunk;
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (Field f : holder.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Object value = f.get(holder);
                if (value instanceof LevelChunk chunk) return chunk;
                if (value instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof LevelChunk chunk) return chunk;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------ MenuProvider / 璁块棶鍣?

    @Override
    public Component getDisplayName() {
        return Component.translatable("screen.omnibattery.title");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new OmniBatteryMenu(id, inv, this);
    }

    public BatteryTier tier() { return tier; }
    public BatteryTier getTier() { return tier; }
    public long getEnergy() { return energy; }
    public BatteryMode getMode() { return mode; }
    public int getRateIndex() { return rateIndex; }
    public int getRange() { return range; }
    public long getAbsorbedPerSecond() { return lastAbsorbed; }
    public long getSuppliedPerSecond() { return lastSupplied; }

    // ---- 趋势图历史（每秒采样一次，环形缓冲）----

    /** 记录趋势图采样点（每秒一次）。 */
    public void recordHistory(long absorbed, long supplied) {
        absorbHistory[historyIndex] = absorbed;
        supplyHistory[historyIndex] = supplied;
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
    }

    /** 按时间顺序（旧->新）读取历史吸电值。 */
    public long getAbsorbHistory(int i) {
        int idx = (historyIndex + i) % HISTORY_SIZE;
        if (idx < 0) idx += HISTORY_SIZE;
        return absorbHistory[idx];
    }

    /** 按时间顺序（旧->新）读取历史供电值。 */
    public long getSupplyHistory(int i) {
        int idx = (historyIndex + i) % HISTORY_SIZE;
        if (idx < 0) idx += HISTORY_SIZE;
        return supplyHistory[idx];
    }

    public int getHistorySize() { return HISTORY_SIZE; }

    public void setEnergy(long e) {
        energy = Math.max(0L, Math.min(tier.capacity(), e));
        setChanged();
        syncToClients();
        updateChargeState();
    }

    public void setMode(BatteryMode m) {
        mode = m;
        setChanged();
        syncToClients();
    }

    public void setRateIndex(int r) {
        rateIndex = Math.max(0, Math.min(4, r));
        setChanged();
        syncToClients();
    }

    public void setRange(int r) {
        range = tier.isUltimate() && r < 0 ? -1 : Math.max(1, r);
        setChanged();
        syncToClients();
    }


    // ------------------------------------------------------------ 客户端同步（范围显示需读取真实 range/mode）

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return saveWithoutMetadata(provider);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) {
        loadAdditional(tag, provider);
    }

    /** 配置变化后主动推送 BE 更新包给附近玩家（客户端范围显示/后续交互需要真实数据）。 */
    private void syncToClients() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet =
                net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
        long reachSq = 256L * 256L;
        for (net.minecraft.server.level.ServerPlayer sp : serverLevel.players()) {
            if (sp.blockPosition().distSqr(worldPosition) <= reachSq) {
                sp.connection.send(packet);
            }
        }
    }

    /**
     * 主动推送 Menu data slots 给正在查看该菜单的附近玩家。
     * 用途：BE 每秒结算吸电/供电速率后，让客户端 GUI 立刻看到新值（不必等下次点击按钮）。
     * 注意：打开 OMNI_BATTERY 菜单的 BE 必然是当前 BE（不会混），所以无需校验 menu 的 BE 引用。
     */
    public void broadcastMenuChangesIfOpen(net.minecraft.server.level.ServerLevel serverLevel) {
        long reachSq = 256L * 256L;
        for (net.minecraft.server.level.ServerPlayer sp : serverLevel.players()) {
            if (sp.blockPosition().distSqr(worldPosition) > reachSq) continue;
            if (sp.containerMenu instanceof OmniBatteryMenu menu) {
                menu.broadcastChanges();
            }
        }
    }
    // ------------------------------------------------------------ 瀛樻。

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putLong("Energy", energy);
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("RateIndex", rateIndex);
        tag.putInt("Range", range);
        // 速率统计（供 GUI 显示，BE 更新包同步到客户端）
        tag.putLong("LastAbsorbed", lastAbsorbed);
        tag.putLong("LastSupplied", lastSupplied);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        energy = Math.max(0L, Math.min(tier.capacity(), tag.getLong("Energy")));
        int mi = tag.getInt("Mode");
        BatteryMode[] modes = BatteryMode.values();
        mode = mi >= 0 && mi < modes.length ? modes[mi] : BatteryMode.BOTH;
        rateIndex = Math.max(0, Math.min(4, tag.getInt("RateIndex")));
        range = tag.contains("Range") ? tag.getInt("Range") : tier.defaultRange();
        lastAbsorbed = tag.getLong("LastAbsorbed");
        lastSupplied = tag.getLong("LastSupplied");
    }

    // ------------------------------------------------------------ 鍐呴儴宸ュ叿

    private static class NbtEnergyEdit {
        private long budget;
        private long moved;
        private final boolean fill;

        NbtEnergyEdit(long budget, boolean fill) {
            this.budget = Math.max(0L, budget);
            this.fill = fill;
        }

        private void visitCompound(CompoundTag tag) {
            if (budget <= 0L || tag == null) return;
            for (String key : new ArrayList<>(tag.getAllKeys())) {
                if (budget <= 0L) return;
                Tag value = tag.get(key);
                String lower = key.toLowerCase(Locale.ROOT);
                if (value instanceof CompoundTag compound) {
                    visitCompound(compound);
                    continue;
                }
                if (value instanceof ListTag list) {
                    visitList(list);
                    continue;
                }
                if (!isEnergyNbtName(lower) || !tag.contains(key, Tag.TAG_ANY_NUMERIC)) continue;
                long current = tag.getLong(key);
                long max = findNbtCapacityNear(tag);
                if (fill) {
                    if (max <= 0L) max = Long.MAX_VALUE;
                    long add = Math.min(budget, Math.max(0L, max - current));
                    if (add <= 0L) continue;
                    putNumber(tag, key, value, current + add);
                    budget -= add;
                    moved += add;
                    continue;
                }
                long remove = Math.min(budget, Math.max(0L, current));
                if (remove <= 0L) continue;
                putNumber(tag, key, value, current - remove);
                budget -= remove;
                moved += remove;
            }
        }

        private void visitList(ListTag list) {
            for (int i = 0; i < list.size() && budget > 0L; i++) {
                Tag value = list.get(i);
                if (value instanceof CompoundTag compound) {
                    visitCompound(compound);
                } else if (value instanceof ListTag nested) {
                    visitList(nested);
                }
            }
        }

        private static boolean isEnergyNbtName(String name) {
            if (name.equals("x") || name.equals("y") || name.equals("z") || name.equals("id")
                    || name.equals("burntime") || name.equals("cooktime")) return false;
            return name.equals("energy") || name.equals("stored") || name.equals("storedenergy")
                    || name.equals("energy_stored") || name.equals("energystored") || name.equals("joules")
                    || name.equals("fe") || name.equals("power") || name.contains("energy")
                    || name.contains("stored") || name.contains("joule");
        }

        private static long findNbtCapacityNear(CompoundTag tag) {
            long max = -1L;
            for (String key : tag.getAllKeys()) {
                String lower = key.toLowerCase(Locale.ROOT);
                if (!lower.contains("capacity") && !lower.contains("max") && !lower.contains("limit")) continue;
                if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) continue;
                max = Math.max(max, tag.getLong(key));
            }
            return max;
        }

        private static void putNumber(CompoundTag tag, String key, Tag oldValue, long value) {
            if (oldValue instanceof IntTag) {
                tag.putInt(key, BatteryData.clampToForgeInt(value));
            } else {
                tag.putLong(key, value);
            }
        }
    }

    private record EnergyFields(Object owner, Field energy, Field capacity) {}
}
