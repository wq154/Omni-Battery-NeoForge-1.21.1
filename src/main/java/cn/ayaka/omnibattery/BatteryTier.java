package cn.ayaka.omnibattery;

/**
 * 电池等级参数。数值与 1.20.1 原版一致。
 * rates: 5 档传输速率 (FE/t)，ULTIMATE 最高档为无限（Long.MAX_VALUE）；
 * rangeSteps: 可选范围档位；ULTIMATE 的 -1 表示全维度。
 */
public enum BatteryTier {
    LOW("\u4f4e\u7ea7", 10000000000L, 16, new long[]{100000, 500000, 1000000, 2500000, 5000000}, new int[]{8, 12, 16}),
    MEDIUM("\u4e2d\u7ea7", 100000000000L, 32, new long[]{500000, 1000000, 2500000, 5000000, 10000000}, new int[]{12, 18, 24, 32}),
    ADVANCED("\u9ad8\u7ea7", 1000000000000L, 96, new long[]{1000000, 2500000, 5000000, 10000000, 25000000}, new int[]{16, 32, 48, 64, 96}),
    ELITE("\u7cbe\u82f1", 100000000000000L, 256, new long[]{2500000, 5000000, 10000000, 25000000, 50000000}, new int[]{32, 64, 128, 192, 256}),
    ULTIMATE("\u7ec8\u6781", Long.MAX_VALUE, -1, new long[]{50000000, 100000000, 250000000, 500000000, Long.MAX_VALUE}, new int[]{64, 128, 256, 512, -1});

    private final String display;
    private final long capacity;
    private final int defaultRange;
    private final long[] rates;
    private final int[] rangeSteps;

    BatteryTier(String display, long capacity, int defaultRange, long[] rates, int[] rangeSteps) {
        this.display = display;
        this.capacity = capacity;
        this.defaultRange = defaultRange;
        this.rates = rates;
        this.rangeSteps = rangeSteps;
    }

    public String display() { return display; }
    public long capacity() { return capacity; }

    /** Forge/NeoForge 能量 API 侧暴露的最大容量（int 上限）。 */
    public int forgeCapacity() { return Integer.MAX_VALUE; }

    public int defaultRange() { return defaultRange; }

    public long rate(int index) {
        return rates[Math.max(0, Math.min(index, rates.length - 1))];
    }

    public long[] rates() { return rates; }
    public int[] rangeSteps() { return rangeSteps; }
    public boolean isUltimate() { return this == ULTIMATE; }
}
