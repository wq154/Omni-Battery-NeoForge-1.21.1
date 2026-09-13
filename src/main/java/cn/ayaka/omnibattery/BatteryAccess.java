package cn.ayaka.omnibattery;

/**
 * 电池用电权限。
 * <ul>
 *   <li>PRIVATE 私人：仅电池主人可用</li>
 *   <li>TEAM 队伍：主人 + 与主人同一记分板队伍的玩家可用</li>
 *   <li>PUBLIC 公开：任何人可用</li>
 * </ul>
 */
public enum BatteryAccess {
    PRIVATE("私人"),
    TEAM("队伍"),
    PUBLIC("公开");

    private final String display;

    BatteryAccess(String display) {
        this.display = display;
    }

    public String display() { return display; }

    public BatteryAccess next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
