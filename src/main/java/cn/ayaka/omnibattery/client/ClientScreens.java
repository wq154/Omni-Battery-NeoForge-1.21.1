package cn.ayaka.omnibattery.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** 真正打开客户端屏幕的地方（只会在客户端被反射加载）。 */
public final class ClientScreens {
    private ClientScreens() {}

    public static void openCustomCap(ItemStack stack) {
        Minecraft.getInstance().setScreen(new CustomCapScreen(stack));
    }

    /** 从电池 GUI 打开：设置某台机器的自定义容量。 */
    public static void openMachineCap(int x, int y, int z, long initial) {
        Minecraft.getInstance().setScreen(
                new CustomCapScreen(new net.minecraft.core.BlockPos(x, y, z), initial));
    }
}
