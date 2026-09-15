package cn.ayaka.omnibattery.compat;

import cn.ayaka.omnibattery.MachineStickerItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import java.util.Optional;

/**
 * Curios 真实兼容实现。仅在 Curios 已加载时通过反射被 {@link CuriosCompat} 调用。
 * 遍历玩家饰品栏的所有槽位，返回第一枚开启自动贴标的贴纸。
 */
public final class CuriosStickerCompat {
    private CuriosStickerCompat() {}

    /** 找一枚已绑定快捷键目标的贴纸。 */
    public static ItemStack findStickerWithBind(Player player) {
        Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(player);
        if (handler.isEmpty()) return ItemStack.EMPTY;
        for (var entry : handler.get().getCurios().entrySet()) {
            var stacks = entry.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack s = stacks.getStackInSlot(i);
                if (s.getItem() instanceof MachineStickerItem && MachineStickerItem.hasBind(s)) {
                    return s;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findAutoSticker(Player player) {
        Optional<ICuriosItemHandler> handler = CuriosApi.getCuriosInventory(player);
        if (handler.isEmpty()) return ItemStack.EMPTY;
        var curios = handler.get().getCurios();
        for (var entry : curios.entrySet()) {
            var stacks = entry.getValue().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack s = stacks.getStackInSlot(i);
                if (s.getItem() instanceof MachineStickerItem && MachineStickerItem.isAutoTagEnabled(s)) {
                    return s;
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
