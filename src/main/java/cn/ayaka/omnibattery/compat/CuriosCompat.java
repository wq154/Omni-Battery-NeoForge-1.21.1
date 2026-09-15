package cn.ayaka.omnibattery.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Curios 兼容入口。
 *
 * 本类不直接引用 Curios API，只在 {@link ModList#isLoaded(String) Curios 存在}时反射调用
 * {@code CuriosStickerCompat.findAutoSticker(Player)}。这样即使玩家没装 Curios，
 * 类加载也不会因为找不到 Curios 类而抛出 NoClassDefFoundError。
 */
public final class CuriosCompat {
    private static final boolean CURIOS_LOADED = ModList.get() != null && ModList.get().isLoaded("curios");
    private static java.lang.reflect.Method findMethod;

    static {
        if (CURIOS_LOADED) {
            try {
                Class<?> cls = Class.forName("cn.ayaka.omnibattery.compat.CuriosStickerCompat");
                findMethod = cls.getMethod("findAutoSticker", Player.class);
            } catch (Throwable ignored) {
                findMethod = null;
            }
        }
    }

    private CuriosCompat() {}

    /** 在饰品栏中查找已开启自动贴标的贴纸；Curios 未安装或未找到时返回 EMPTY。 */
    public static ItemStack findAutoSticker(Player player) {
        if (findMethod == null) return ItemStack.EMPTY;
        try {
            Object r = findMethod.invoke(null, player);
            return r instanceof ItemStack ? (ItemStack) r : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** 找一枚"已绑定快捷键目标"的贴纸（同为反射调用，Curios 缺失时返回空）。 */
    public static ItemStack findStickerWithBind(Player player) {
        return InvokeFindBound.invoke(player);
    }

    /** 反射桥：调用 CuriosStickerCompat.findStickerWithBind，避免硬依赖 Curios。 */
    private static final class InvokeFindBound {
        static ItemStack invoke(Player player) {
            try {
                Class<?> cls = Class.forName("cn.ayaka.omnibattery.compat.CuriosStickerCompat");
                Object r = cls.getMethod("findStickerWithBind", Player.class).invoke(null, player);
                return r instanceof ItemStack is ? is : ItemStack.EMPTY;
            } catch (Throwable t) {
                return ItemStack.EMPTY;
            }
        }
    }

    public static boolean isCuriosLoaded() {
        return CURIOS_LOADED;
    }
}
