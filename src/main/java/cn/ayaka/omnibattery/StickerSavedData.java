package cn.ayaka.omnibattery;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 每维度一张"贴标机器表"：记录被 Machine Sticker 标记的机器坐标、模式与贴标者。
 * 贴标者用于电池权限判定（私人 / 队伍 / 公开）。
 */
public class StickerSavedData extends SavedData {
    private static final String DATA_NAME = "omnibattery_stickers";
    private final Map<BlockPos, StickerEntry> stickers = new HashMap<>();

    public static StickerSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        StickerSavedData data = new StickerSavedData();
        ListTag list = tag.getList("stickers", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            int modeIdx = entry.getInt("mode");
            if (modeIdx < 0 || modeIdx >= StickerMode.values().length) continue;
            data.stickers.put(pos.immutable(), new StickerEntry(
                    StickerMode.values()[modeIdx],
                    parseUUID(entry.getString("owner")),
                    entry.getString("ownerName")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, StickerEntry> e : stickers.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", e.getKey().getX());
            t.putInt("y", e.getKey().getY());
            t.putInt("z", e.getKey().getZ());
            t.putInt("mode", e.getValue().mode().ordinal());
            if (e.getValue().owner() != null) t.putString("owner", e.getValue().owner().toString());
            if (e.getValue().ownerName() != null) t.putString("ownerName", e.getValue().ownerName());
            list.add(t);
        }
        tag.put("stickers", list);
        return tag;
    }

    public StickerMode getMode(BlockPos pos) {
        StickerEntry e = stickers.get(pos);
        return e == null ? null : e.mode();
    }

    /** 所有已打标签的机器坐标（供用电配置界面枚举）。 */
    public java.util.Set<BlockPos> positions() {
        return new java.util.HashSet<>(stickers.keySet());
    }

    public StickerEntry getEntry(BlockPos pos) {
        return stickers.get(pos);
    }

    public void setMode(BlockPos pos, StickerMode mode) {
        setMode(pos, mode, null, "");
    }

    public void setMode(BlockPos pos, StickerMode mode, UUID owner, String ownerName) {
        if (mode == null) {
            stickers.remove(pos);
        } else {
            stickers.put(pos.immutable(), new StickerEntry(mode, owner, ownerName == null ? "" : ownerName));
        }
        setDirty();
    }

    public void removeSticker(BlockPos pos) {
        stickers.remove(pos);
        setDirty();
    }

    public static StickerSavedData get(ServerLevel level) {
        SavedData.Factory<StickerSavedData> factory =
                new SavedData.Factory<>(StickerSavedData::new, StickerSavedData::load);
        return level.getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    private static UUID parseUUID(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    /** 单条贴纸记录：模式 + 贴标者。 */
    public record StickerEntry(StickerMode mode, UUID owner, String ownerName) {}
}
