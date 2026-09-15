package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.network.SetCustomCapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「自定义」标签模式的数值输入框。
 * 潜行右键空气（手持已切到"自定义"的机器标签）时弹出，输入容量上限后保存到标签工具上。
 */
public class CustomCapScreen extends Screen {
    private final ItemStack stack;
    private EditBox box;

    public CustomCapScreen(ItemStack stack) {
        super(Component.literal("自定义容量"));
        this.stack = stack;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        box = new EditBox(this.font, cx - 90, cy - 8, 180, 20, Component.literal("容量"));
        box.setMaxLength(18);
        box.setValue(String.valueOf(MachineStickerItem.getCustomCap(stack)));
        addRenderableWidget(box);
        setInitialFocus(box);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> {
            long v;
            try {
                v = Long.parseLong(box.getValue().trim().replace("_", "").replace(",", ""));
            } catch (Exception e) {
                v = 1_000_000L;
            }
            PacketDistributor.sendToServer(new SetCustomCapPayload(v));
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
        graphics.drawCenteredString(this.font, "自定义容量上限（FE）", cx, cy - 40, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                "过载时会把机器容量设成这个值，只灌到这里为止，不会无限吃电",
                cx, cy - 26, 0xA0A6B0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
