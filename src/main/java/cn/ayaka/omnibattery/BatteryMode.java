package cn.ayaka.omnibattery;

public enum BatteryMode {
    BOTH("\u5438\u53d6+\u4f9b\u80fd"),
    CHARGE_ONLY("\u4ec5\u4f9b\u80fd"),
    ABSORB_ONLY("\u4ec5\u5438\u53d6"),
    OFF("\u5173\u95ed");

    private final String display;
    BatteryMode(String display) { this.display = display; }
    public String display() { return display; }

    public BatteryMode next() {
        BatteryMode[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    /** 可以向机器供电 */
    public boolean canCharge() { return this == BOTH || this == CHARGE_ONLY; }

    /** 可以从机器吸取能量 */
    public boolean canAbsorb() { return this == BOTH || this == ABSORB_ONLY; }
}