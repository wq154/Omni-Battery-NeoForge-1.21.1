package cn.ayaka.omnibattery;

public enum StickerMode {
    // 注意：**新常量只能追加到末尾**。插在中间会让所有 ordinal 偏移，
    // 旧存档里已存的 mode 序号会被解析成别的模式（曾导致机器被误当"自定义"改坏 NBT）。
    SUPPLY("\u4f9b\u7535", "Supply"),
    ABSORB("\u5438\u7535", "Absorb"),
    OVERLOAD("\u8fc7\u8f7d", "Overload"),
    CLEAR("\u6e05\u9664", "Clear"),
    CUSTOM("\u81ea\u5b9a\u4e49", "Custom");

    private final String displayZh;
    private final String displayEn;
    StickerMode(String displayZh, String displayEn) { this.displayZh = displayZh; this.displayEn = displayEn; }
    public String displayZh() { return displayZh; }
    public String displayEn() { return displayEn; }

    /** 循环顺序（按界面显示顺序，与 ordinal 声明顺序无关）：供电→吸电→过载→自定义→清除。 */
    public StickerMode next() {
        return switch (this) {
            case SUPPLY -> ABSORB;
            case ABSORB -> OVERLOAD;
            case OVERLOAD -> CUSTOM;
            case CUSTOM -> CLEAR;
            case CLEAR -> SUPPLY;
        };
    }

    public boolean isActiveTransferMode() { return this == SUPPLY || this == ABSORB || this == OVERLOAD || this == CUSTOM; }
}