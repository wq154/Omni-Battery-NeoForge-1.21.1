package cn.ayaka.omnibattery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** 真正打开客户端屏幕的地方（只会在客户端被反射加载）。 */
public final class ClientScreens {
    private ClientScreens() {}

    public static void openCustomCap(ItemStack stack) {
        Minecraft.getInstance().setScreen(new CustomCapScreen(stack));
    }
}
