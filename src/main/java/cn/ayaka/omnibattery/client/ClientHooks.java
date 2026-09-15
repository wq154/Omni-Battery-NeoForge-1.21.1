package cn.ayaka.omnibattery.client;

import net.minecraft.world.item.ItemStack;

/**
 * 客户端屏幕的"反射桥"。
 * <p>
 * 公共代码（服务端也会加载的类）**绝不能直接引用客户端类** —— 一旦直接引用，
 * 类加载器在注册阶段就会尝试解析 net.minecraft.client.*，专用服务器上没有这些类，
 * 于是整个注册流程崩掉（症状往往表现为"别的模组"注册失败，Suspected Mods: None）。
 * 这里通过反射在运行时（仅客户端）才去链接真正的屏幕类。
 */
public final class ClientHooks {
    private ClientHooks() {}

    /** 从电池 GUI 打开"这台机器"的自定义容量输入框（仅客户端调用）。 */
    public static void openMachineCap(int x, int y, int z, long initial) {
        try {
            Class<?> cls = Class.forName("cn.ayaka.omnibattery.client.ClientScreens");
            cls.getMethod("openMachineCap", int.class, int.class, int.class, long.class)
                    .invoke(null, x, y, z, initial);
        } catch (Throwable ignored) {
        }
    }

    /** 打开自定义容量输入框（仅客户端调用）。 */
    public static void openCustomCap(ItemStack stack) {
        try {
            Class<?> cls = Class.forName("cn.ayaka.omnibattery.client.ClientScreens");
            cls.getMethod("openCustomCap", ItemStack.class).invoke(null, stack);
        } catch (Throwable ignored) {
            // 服务端或屏幕不可用时静默忽略
        }
    }
}
