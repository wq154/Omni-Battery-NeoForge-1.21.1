package cn.ayaka.omnibattery.compat;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICurio;

/**
 * 把贴纸物品注册为 Curios 可佩戴物品。只在 Curios 已加载时被调用（见 NeoForgeEvents）。
 *
 * ICurio 默认接口全部无操作即可满足我们的需求：
 *   贴纸在饰品栏里唯一的语义是"允许被 findAutoSticker 遍历到"（见 CuriosStickerCompat），
 *   不需要 tick / 属性修饰 / 掉落规则改写。
 */
public final class CuriosStickerCap {
    private CuriosStickerCap() {}

    public static void registerItemCap(RegisterCapabilitiesEvent event, Item item) {
        event.registerItem(CuriosCapability.ITEM,
                (stack, ctx) -> new ICurio() {
                    @Override public net.minecraft.world.item.ItemStack getStack() { return stack; }
                },
                item);
    }
}
