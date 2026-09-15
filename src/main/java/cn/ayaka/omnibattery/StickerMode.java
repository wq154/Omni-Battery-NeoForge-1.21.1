package cn.ayaka.omnibattery;

public enum StickerMode {
    SUPPLY("\u4f9b\u7535", "Supply"),
    ABSORB("\u5438\u7535", "Absorb"),
    OVERLOAD("\u8fc7\u8f7d", "Overload"),
    CUSTOM("\u81ea\u5b9a\u4e49", "Custom"),
    CLEAR("\u6e05\u9664", "Clear");

    private final String displayZh;
    private final String displayEn;
    StickerMode(String displayZh, String displayEn) { this.displayZh = displayZh; this.displayEn = displayEn; }
    public String displayZh() { return displayZh; }
    public String displayEn() { return displayEn; }

    public StickerMode next() {
        StickerMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public boolean isActiveTransferMode() { return this == SUPPLY || this == ABSORB || this == OVERLOAD || this == CUSTOM; }
}