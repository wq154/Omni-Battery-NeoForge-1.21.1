package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.OmniBatteryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 万能电池控制面板（220x196，全自绘无重叠布局）。
 *
 * 三段式布局，防止元素重叠：
 *   顶栏 y=6..30    标题 + 等级徽章
 *   信息 y=34..76   左侧圆形能量表 + 右侧数值/等级/迷你条
 *   控制 y=80..172  四行设置 + 手绘按钮
 *   底部 y=176..192 提示行
 * 所有按钮尺寸固定 18x18（−/+）或 60x18（模式/开关），
 * 与左侧标签的最小间距 24px，杜绝重叠。
 */
public class OmniBatteryScreen extends AbstractContainerScreen<OmniBatteryMenu> {
    private static final int W = 214;
    private static final int H = 272;

    // 顶栏
    private static final int TITLE_Y = 12;

    // 信息栏：文字在左，圆形能量表在右
    private static final int INFO_X = 18;                  // 信息文字左界（与行标签左对齐）
    private static final int INFO_Y = 34;                  // 信息文字起始 y（行距 11）
    private static final int GAUGE_R = 16;                 // 圆表半径
    private static final int GAUGE_CX = W - 12 - GAUGE_R;  // 圆表中心 x（靠右）
    private static final int GAUGE_CY = 50;                // 圆表中心 y

    // 控制栏 - 每行 20px 高
    private static final int ROW_H = 20;       // 行高
    private static final int PANEL_X = 12;     // 面板左边界（与信息文字同界）
    private static final int PANEL_W = W - PANEL_X - 8;
    private static final int ROW1 = 74;        // 模式
    private static final int ROW2 = 94;        // 速率
    private static final int ROW3 = 114;       // 范围
    private static final int ROW4 = 134;       // 范围显示
    private static final int ROW5 = 154;       // 每秒吸电（实时）
    private static final int ROW6 = 174;       // 每秒供电（实时）
    private static final int ROW7 = 194;       // 玩家供电开关（物品 / 饰品）+ 权限
    // ---------- 趋势图区域 ----------
    private static final int TREND_TITLE_Y = 216;
    private static final int TREND_Y = 224;
    private static final int TREND_H = 28;
    private static final int TREND_W = W - PANEL_X - 16;
    private static final int BTN_H = 18;

    // 按钮位置 (相对面板)
    private static final int BTN_MODE_W = 62;
    private static final int BTN_PM_W = 18;

    private int pressedId = -1;
    private String hint = null;

    public OmniBatteryScreen(OmniBatteryMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = W;
        imageHeight = H;
        inventoryLabelY = H - 94;  // 玩家背包标题位置
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 隐藏所有默认标签（我们全部自绘）
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        int dark = tierDark();
        int mid = tierMid();
        int bright = tierBright();
        hint = null;

        // ==== 外框（Minecraft 原版 GUI 灰色底 + 立体边框）====
        graphics.fill(x, y, x + W, y + H, 0xFF555555);            // 外框深灰
        graphics.fill(x + 1, y + 1, x + W - 1, y + H - 1, 0xFFC6C6C6);  // 主体原版灰
        // 立体高光（左/上亮，右/下暗）
        graphics.fill(x + 1, y + 1, x + W - 1, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 2, y + H - 1, 0xFFFFFFFF);
        graphics.fill(x + W - 2, y + 1, x + W - 1, y + H - 1, 0xFF555555);
        graphics.fill(x + 1, y + H - 2, x + W - 1, y + H - 1, 0xFF555555);

        // ==== 顶栏（vanilla 灰 + 等级色作为徽章）====
        graphics.fill(x + 4, y + 4, x + W - 4, y + 26, 0xFF373737);       // 深槽
        graphics.fill(x + 5, y + 5, x + W - 5, y + 25, 0xFF8B8B8B);       // 底色
        graphics.fill(x + W - 5, y + 5, x + W - 4, y + 26, 0xFFFFFFFF);   // 立体右白
        graphics.fill(x + 5, y + 25, x + W - 5, y + 26, 0xFFFFFFFF);      // 立体下白
        graphics.fill(x + 4, y + 26, x + W - 4, y + 28, 0xFF373737);      // 分隔线
        String title = "\u4e07\u80fd\u7535\u6c60\u63a7\u5236\u9762\u677f";
        graphics.drawString(font, title, x + 10, y + TITLE_Y, 0xFF202020, false);
        String tierName = menu.getTier().display();
        int badgeW = font.width(tierName) + 12;
        // 徽章用等级色
        graphics.fill(x + W - 8 - badgeW, y + 8, x + W - 8, y + 22, dark);
        graphics.fill(x + W - 7 - badgeW, y + 9, x + W - 9, y + 21, bright);
        graphics.drawString(font, tierName, x + W - 8 - badgeW + 6, y + TITLE_Y, 0xFF202020, false);

        // ==== 信息栏 - 左侧文字（与下方行标签左对齐）====
        int infoX = x + INFO_X;
        graphics.drawString(font, "\u5f53\u524d\u7535\u91cf", infoX, y + INFO_Y, 0xFF404040, false);
        String energyStr = OmniBatteryMenu.fmt(menu.getEnergy());
        String maxStr = maxStr();
        graphics.drawString(font, energyStr + " FE", infoX, y + INFO_Y + 11, 0xFF1E5B26, false);   // 深绿
        graphics.drawString(font, "/ " + maxStr + " FE", infoX, y + INFO_Y + 22, 0xFF606060, false);

        // ==== 信息栏 - 右侧圆形能量表 ====
        boolean isUltimate = menu.getTier() == cn.ayaka.omnibattery.BatteryTier.ULTIMATE;
        // 终极电池容量无限，百分比无意义 -> 显示电量简写（K/M/B/T + 两位小数 + FE），不画扇形
        String gaugeCenter = isUltimate
                ? OmniBatteryMenu.fmtShort(menu.getEnergy()) + " FE"
                : String.format("%.2f%%", menu.getEnergyRatio() * 100.0f);
        drawCircularGauge(graphics, x + GAUGE_CX, y + GAUGE_CY, GAUGE_R,
                (float) menu.getEnergyRatio(), dark, mid, bright, gaugeCenter, isUltimate);

        // ==== 控制栏 - 4 行 ====
        drawRow(graphics, x, y, ROW1, "\u6a21\u5f0f");
        drawRow(graphics, x, y, ROW2, "\u901f\u7387");
        drawRow(graphics, x, y, ROW3, "\u8303\u56f4");
        drawRow(graphics, x, y, ROW4, "\u8303\u56f4\u663e\u793a");

        // 速率/范围的当前值（放在标签右侧、按钮左侧的中段）
        // 数值在行凹槽（深色 #373737）内，用亮色显示以保证对比度
        graphics.drawString(font, menu.getRateDisplay(),
                x + PANEL_X + 40, y + ROW2 + 6, 0xFFFFD98A, false);
        graphics.drawString(font, menu.getRangeDisplay(),
                x + PANEL_X + 40, y + ROW3 + 6, 0xFFAFE0AF, false);

        // ==== 按钮（严格右对齐，杜绝重叠）====
        int rightEdge = x + W - 8;

        // 模式：60x18 长按钮，显示当前模式
        int modeBtnX = rightEdge - BTN_MODE_W;
        drawChip(graphics, modeBtnX, y + ROW1 + 1, BTN_MODE_W, BTN_H,
                menu.getMode().display(), mouseX, mouseY, 0, "\u5207\u6362\u6a21\u5f0f");

        // 速率 -/+
        int plusX = rightEdge - BTN_PM_W;
        int minusX = plusX - BTN_PM_W - 2;
        drawChip(graphics, minusX, y + ROW2 + 1, BTN_PM_W, BTN_H, "-",
                mouseX, mouseY, 2, "\u964d\u4f4e\u901f\u7387");
        drawChip(graphics, plusX, y + ROW2 + 1, BTN_PM_W, BTN_H, "+",
                mouseX, mouseY, 1, "\u63d0\u9ad8\u901f\u7387");

        // 范围 -/+
        drawChip(graphics, minusX, y + ROW3 + 1, BTN_PM_W, BTN_H, "-",
                mouseX, mouseY, 4, "\u7f29\u5c0f\u8303\u56f4");
        drawChip(graphics, plusX, y + ROW3 + 1, BTN_PM_W, BTN_H, "+",
                mouseX, mouseY, 3, "\u6269\u5927\u8303\u56f4");

        // 范围显示开关
        boolean on = RangeOverlay.isVisible();
        int togBtnX = rightEdge - 44;
        drawSwitch(graphics, togBtnX, y + ROW4 + 1, 44, BTN_H, on, mouseX, mouseY, 5,
                on ? "关闭范围显示" : "打开范围显示");

        // ==== 实时速率（只显示，无按钮）====
        // 吸电（机器 → 电池）
        drawRow(graphics, x, y, ROW5, "吸电");
        long absorbed = menu.getAbsorbedPerSecond();
        // /s 数值 = 每 tick 速率 × 20，直接除回 /t 与"速率"设置同单位
        long absorbedPerTick = absorbed / 20;
        String absStr = OmniBatteryMenu.fmt(absorbedPerTick) + " FE/t";
        int absColor = absorbed > 0 ? 0xFFFFA640 : 0xFF6E7076;
        graphics.drawString(font, absStr,
                x + PANEL_X + 40, y + ROW5 + 6, absColor, false);

        // 供电（电池 → 机器）
        drawRow(graphics, x, y, ROW6, "供电");
        long supplied = menu.getSuppliedPerSecond();
        long suppliedPerTick = supplied / 20;
        String supStr = OmniBatteryMenu.fmt(suppliedPerTick) + " FE/t";
        int supColor = supplied > 0 ? 0xFF6AE8E0 : 0xFF6E7076;
        graphics.drawString(font, supStr,
                x + PANEL_X + 40, y + ROW6 + 6, supColor, false);

        // ==== 玩家供电开关 + 权限（合并一行）====
        drawRow(graphics, x, y, ROW7, "玩家/权限");
        drawNamedSwitch(graphics, rightEdge - 142, y + ROW7 + 1, 40, BTN_H, "物品",
                menu.isChargeInventory(), mouseX, mouseY, 6,
                menu.isChargeInventory() ? "关闭：不给物品栏物品充电" : "开启：给物品栏物品充电");
        drawNamedSwitch(graphics, rightEdge - 98, y + ROW7 + 1, 40, BTN_H, "饰品",
                menu.isChargeCurios(), mouseX, mouseY, 7,
                menu.isChargeCurios() ? "关闭：不给饰品栏物品充电" : "开启：给饰品栏物品充电");
        drawChip(graphics, rightEdge - 54, y + ROW7 + 1, 54, BTN_H,
                menu.getAccessDisplay(), mouseX, mouseY, 8, "切换权限：私人 / 队伍 / 公开");

        // ==== 趋势图（最近吸电/供电每秒趋势）====
        drawTrend(graphics, x, y);

        // ==== 底部提示（vanilla 灰）====
        graphics.fill(x + 4, y + H - 18, x + W - 4, y + H - 4, 0xFF8B8B8B);
        graphics.fill(x + 5, y + H - 17, x + W - 5, y + H - 5, 0xFF373737);
        String footer = hint != null
                ? "▶ " + hint
                : "提示：右键方块可设置，左键破坏";
        graphics.drawString(font, footer, x + 10, y + H - 14, hint != null ? 0xFFFFE9A8 : 0xFFC8C8C8, false);
    }

    /** 圆形能量表：外圈刻度环 + 内部扇形填充 + 中央文字。
     *  ultimateMode=true 时不画扇形（终极容量无限，扇形无意义），只显示中心电量。 */
    private void drawCircularGauge(GuiGraphics graphics, int cx, int cy, int r,
                                   float ratio, int dark, int mid, int bright,
                                   String centerText, boolean ultimateMode) {
        // 底盘
        for (int dy = -r - 2; dy <= r + 2; dy++) {
            for (int dx = -r - 2; dx <= r + 2; dx++) {
                int d2 = dx * dx + dy * dy;
                int rr = r + 2;
                if (d2 > rr * rr) continue;
                if (d2 > (r + 1) * (r + 1)) {
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF0A0C12);
                } else if (d2 > r * r) {
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF3A4250);
                } else if (d2 > (r - 3) * (r - 3)) {
                    // 圆环
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, dark);
                } else if (d2 > (r - 4) * (r - 4)) {
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF14181F);
                } else {
                    // 内圆背景
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFF161B24);
                }
            }
        }
        // 扇形填充（终极电池不画，容量无限）
        if (!ultimateMode) {
            double filled = ratio * Math.PI * 2;
            for (int dy = -r + 4; dy <= r - 4; dy++) {
                for (int dx = -r + 4; dx <= r - 4; dx++) {
                    int d2 = dx * dx + dy * dy;
                    int inner = (r - 8) * (r - 8);
                    int outer = (r - 4) * (r - 4);
                    if (d2 <= inner || d2 > outer) continue;
                    double ang = Math.atan2(dx, -dy);  // 0 = 上, 顺时针
                    if (ang < 0) ang += Math.PI * 2;
                    if (ang <= filled) {
                        int c = ratio > 0.66f ? bright : (ratio > 0.33f ? mid : dark);
                        graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
                    }
                }
            }
        }
        // 中央文字
        int tw = font.width(centerText);
        graphics.drawString(font, centerText, cx - tw / 2, cy - 4, 0xFFFFFFFF, true);
    }

    private void drawRow(GuiGraphics graphics, int x, int y, int rowY, String label) {
        int ry = y + rowY;
        // 凹槽（原版 GUI 里 slot 的经典 3-色边）
        graphics.fill(x + PANEL_X, ry, x + W - 8, ry + ROW_H, 0xFF8B8B8B);
        graphics.fill(x + PANEL_X + 1, ry + 1, x + W - 9, ry + ROW_H - 1, 0xFF373737);
        graphics.fill(x + W - 9, ry + 1, x + W - 8, ry + ROW_H, 0xFFFFFFFF);
        graphics.fill(x + PANEL_X + 1, ry + ROW_H - 1, x + W - 9, ry + ROW_H, 0xFFFFFFFF);
        graphics.drawString(font, label, x + PANEL_X + 6, ry + 6, 0xFFFFFFFF, false);
    }

    private boolean drawChip(GuiGraphics graphics, int bx, int by, int bw, int bh, String label,
                             int mouseX, int mouseY, int id, String hintText) {
        boolean hover = isHover(mouseX, mouseY, bx, by, bw, bh);
        if (hover) hint = hintText;
        boolean down = pressedId == id;
        int bright = tierBright();
        int dark = tierDark();
        // 外框
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF05070A);
        // 内填
        int fill = down ? dark : (hover ? bright : 0xFF2A303C);
        graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, fill);
        // 高光
        graphics.fill(bx + 1, by + 1, bx + bw - 1, by + 2,
                down ? dark : (hover ? 0x66FFFFFF : 0xFF454D5C));
        // 阴影
        graphics.fill(bx + 1, by + bh - 2, bx + bw - 1, by + bh - 1,
                down ? 0xFF000000 : 0xFF16191F);
        // 文字
        int textColor = hover && !down ? 0xFF10141C : 0xFFE6EBF2;
        int tw = font.width(label);
        graphics.drawString(font, label, bx + (bw - tw) / 2, by + (bh - 8) / 2, textColor, false);
        return hover;
    }

    /** 带名称标签的开关（绿色=开，红色=关）。 */
    private void drawNamedSwitch(GuiGraphics graphics, int bx, int by, int bw, int bh,
                                 String name, boolean on, int mouseX, int mouseY, int id, String hintText) {
        boolean hover = isHover(mouseX, mouseY, bx, by, bw, bh);
        if (hover) hint = hintText;
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF05070A);
        graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, on ? 0xFF1E3A24 : 0xFF33202A);
        int textColor = hover ? (on ? 0xFFB6FFC4 : 0xFFFFB0B0) : (on ? 0xFF7DFF99 : 0xFFFF8A8A);
        int tw = font.width(name);
        graphics.drawString(font, name, bx + (bw - tw) / 2, by + (bh - 8) / 2, textColor, false);
    }

    private boolean drawSwitch(GuiGraphics graphics, int bx, int by, int bw, int bh, boolean on,
                               int mouseX, int mouseY, int id, String hintText) {
        boolean hover = isHover(mouseX, mouseY, bx, by, bw, bh);
        if (hover) hint = hintText;
        graphics.fill(bx, by, bx + bw, by + bh, 0xFF05070A);
        graphics.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, on ? 0xFF1E3A24 : 0xFF33202A);
        // 滑块
        int trackW = 20;
        int tx = on ? bx + bw - 3 - trackW : bx + 3;
        int trackColor = on ? 0xFF3E9256 : (hover ? 0xFF7A5060 : 0xFF5A3846);
        graphics.fill(tx, by + 3, tx + trackW, by + bh - 3, trackColor);
        String text = on ? "\u5f00" : "\u5173";
        int textX = on ? bx + 6 : bx + bw - 6 - font.width(text);
        graphics.drawString(font, text, textX, by + (bh - 8) / 2, 0xFFE6EBF2, false);
        return hover;
    }

    private static boolean isHover(int mx, int my, int bx, int by, int bw, int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    // ---------------- 输入 ----------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = leftPos, y = topPos;
        int rightEdge = x + W - 8;
        int mx = (int) mouseX, my = (int) mouseY;

        // 模式
        if (isHover(mx, my, rightEdge - BTN_MODE_W, y + ROW1 + 1, BTN_MODE_W, BTN_H)) {
            return press(0);
        }
        int plusX = rightEdge - BTN_PM_W;
        int minusX = plusX - BTN_PM_W - 2;
        // 速率
        if (isHover(mx, my, minusX, y + ROW2 + 1, BTN_PM_W, BTN_H)) return press(2);
        if (isHover(mx, my, plusX, y + ROW2 + 1, BTN_PM_W, BTN_H)) return press(1);
        // 范围
        if (isHover(mx, my, minusX, y + ROW3 + 1, BTN_PM_W, BTN_H)) return press(4);
        if (isHover(mx, my, plusX, y + ROW3 + 1, BTN_PM_W, BTN_H)) return press(3);
        // 玩家供电开关 + 权限（同一行）
        if (isHover(mx, my, rightEdge - 142, y + ROW7 + 1, 40, BTN_H)) return press(6);
        if (isHover(mx, my, rightEdge - 98, y + ROW7 + 1, 40, BTN_H)) return press(7);
        if (isHover(mx, my, rightEdge - 54, y + ROW7 + 1, 54, BTN_H)) return press(8);
        // 范围显示
        if (isHover(mx, my, rightEdge - 44, y + ROW4 + 1, 44, BTN_H)) {
            pressedId = 5;
            RangeOverlay.toggle();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressedId = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean press(int id) {
        pressedId = id;
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
        return true;
    }

    // ---------------- 工具 ----------------

    private String maxStr() {
        long cap = menu.getMaxEnergy();
        return cap == Long.MAX_VALUE ? "\u221e" : OmniBatteryMenu.fmt(cap);
    }

    // 等级配色与贴图一致（gen-battery-assets.py METALS 的 dark/base/spec + ACCENTS 的 accent）：
    //   LOW      = 铁灰   accent = 青蓝
    //   MEDIUM   = 铜     accent = 暖橙
    //   ADVANCED = 银     accent = 青绿
    //   ELITE    = 金     accent = 亮黄
    //   ULTIMATE = 钛紫   accent = 紫

    // ---------------- 趋势图 ----------------

    /** 绘制最近 60 秒的吸电/供电每秒趋势线。 */
    private void drawTrend(GuiGraphics graphics, int x, int y) {
        int ty = y + TREND_Y;
        int tw = TREND_W;
        // 标题 + 反例
        graphics.drawString(font, "每秒趋势", x + PANEL_X, y + TREND_TITLE_Y, 0xFFC0C4CC, false);
        graphics.fill(x + PANEL_X + 54, y + TREND_TITLE_Y + 1, x + PANEL_X + 58, y + TREND_TITLE_Y + 5, 0xFFFFA640);
        graphics.drawString(font, "吸电", x + PANEL_X + 60, y + TREND_TITLE_Y, 0xFFFFA640, false);
        graphics.fill(x + PANEL_X + 82, y + TREND_TITLE_Y + 1, x + PANEL_X + 86, y + TREND_TITLE_Y + 5, 0xFF6AE8E0);
        graphics.drawString(font, "供电", x + PANEL_X + 88, y + TREND_TITLE_Y, 0xFF6AE8E0, false);

        // 背景深坑
        graphics.fill(x + PANEL_X, ty, x + PANEL_X + tw, ty + TREND_H, 0xFF0A0C12);
        graphics.fill(x + PANEL_X + 1, ty + 1, x + PANEL_X + tw - 1, ty + TREND_H - 1, 0xFF161B24);

        int size = menu.getHistorySize();
        if (size <= 0) return;

        // 找最大值作为统一缩放边界
        long maxV = 0;
        for (int i = 0; i < size; i++) {
            maxV = Math.max(maxV, menu.getAbsorbHistory(i));
            maxV = Math.max(maxV, menu.getSupplyHistory(i));
        }
        if (maxV <= 0) maxV = 1;

        int plotX0 = x + PANEL_X + 3;
        int plotY0 = ty + 3;
        int plotW = tw - 6;
        int plotH = TREND_H - 6;

        // 吸电线（橙）
        for (int i = 0; i < size - 1; i++) {
            int px1 = plotX0 + (int) ((long) i * plotW / (size - 1));
            int px2 = plotX0 + (int) ((long) (i + 1) * plotW / (size - 1));
            int py1 = plotY0 + plotH - (int) (menu.getAbsorbHistory(i) * plotH / maxV);
            int py2 = plotY0 + plotH - (int) (menu.getAbsorbHistory(i + 1) * plotH / maxV);
            drawLine(graphics, px1, py1, px2, py2, 0xFFFFA640);
        }
        // 供电线（静）
        for (int i = 0; i < size - 1; i++) {
            int px1 = plotX0 + (int) ((long) i * plotW / (size - 1));
            int px2 = plotX0 + (int) ((long) (i + 1) * plotW / (size - 1));
            int py1 = plotY0 + plotH - (int) (menu.getSupplyHistory(i) * plotH / maxV);
            int py2 = plotY0 + plotH - (int) (menu.getSupplyHistory(i + 1) * plotH / maxV);
            drawLine(graphics, px1, py1, px2, py2, 0xFF6AE8E0);
        }
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int guard = 0;
        while (guard++ < 10000) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            if (x1 == x2 && y1 == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x1 += sx; }
            if (e2 < dx) { err += dx; y1 += sy; }
        }
    }

    private int tierDark() {
        return switch (menu.getTier()) {
            case LOW -> 0xFF303036;
            case MEDIUM -> 0xFF4A2814;
            case ADVANCED -> 0xFF38363E;
            case ELITE -> 0xFF543E0E;
            case ULTIMATE -> 0xFF2A2440;
        };
    }

    private int tierMid() {
        return switch (menu.getTier()) {
            case LOW -> 0xFF6C6E76;
            case MEDIUM -> 0xFFA8602C;
            case ADVANCED -> 0xFF989AA2;
            case ELITE -> 0xFFC4A230;
            case ULTIMATE -> 0xFF64588E;
        };
    }

    /** 高亮色 = 贴图的 accent（等级 LED 颜色）。 */
    private int tierBright() {
        return switch (menu.getTier()) {
            case LOW -> 0xFF60C8FF;
            case MEDIUM -> 0xFFFFA640;
            case ADVANCED -> 0xFF6AE8E0;
            case ELITE -> 0xFFFFF090;
            case ULTIMATE -> 0xFFC488FF;
        };
    }
}
