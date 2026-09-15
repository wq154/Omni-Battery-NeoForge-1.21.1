package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.network.SetCustomCapPayload;
import cn.ayaka.omnibattery.network.SetMachineCapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「自定义」容量输入框。两种用法：
 * <ul>
 *   <li>手持标签潜行右键空气（自定义模式）→ 修改**标签工具**上的默认值</li>
 *   <li>电池 GUI -> 用电配置 -> 某台机器选"自定义" → 直接修改**那台机器**的容量上限</li>
 * </ul>
 */
public class CustomCapScreen extends Screen {
    private final ItemStack stack;        // 物品模式（否则为 null）
    private final BlockPos machinePos;    // 机器模式（否则为 null）
    private final long initial;

    /** 物品模式。 */
    public CustomCapScreen(ItemStack stack) {
        super(Component.literal("自定义过载速率"));
        this.stack = stack;
        this.machinePos = null;
        this.initial = MachineStickerItem.getCustomCap(stack);
    }

    /** 机器模式（从电池 GUI 进入）。 */
    public CustomCapScreen(BlockPos machinePos, long initial) {
        super(Component.literal("自定义过载速率"));
        this.stack = null;
        this.machinePos = machinePos;
        this.initial = initial > 0L ? initial : 1_000_000L;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        EditBox box = new EditBox(this.font, cx - 90, cy - 8, 180, 20, Component.literal("速率"));
        box.setMaxLength(18);
        box.setValue(String.valueOf(this.initial));
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> {
            long v;
            try {
                v = Long.parseLong(box.getValue().trim().replace("_", "").replace(",", ""));
            } catch (Exception e) {
                v = 1_000_000L;
            }
            if (machinePos != null) {
                PacketDistributor.sendToServer(
                        new SetMachineCapPayload(machinePos.getX(), machinePos.getY(), machinePos.getZ(), v));
            } else if (stack != null) {
                PacketDistributor.sendToServer(new SetCustomCapPayload(v));
            }
            onClose();
        }).bounds(cx - 90, cy + 20, 86, 20).build());

        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(cx + 4, cy + 20, 86, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.drawCenteredString(this.font, "自定义过载速率（FE/t）", cx, cy - 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                machinePos != null
                        ? "这台机器改用自定义过载：每 tick 最多传输此数值"
                        : "自定义 = 过载模式，但速率用这个数值（每 tick 最多传输这么多）",
                cx, cy - 26, 0xA0A6B0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
