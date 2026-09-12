package cn.ayaka.omnibattery;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 合成升级电池时保留电量与设置。
 * 普通合成会生成全新物品（电量清空），这里把合成材料里旧电池的能量与配置迁移到结果上，
 * 避免"升级后电量没了"。
 */
@EventBusSubscriber(modid = OmniBatteryMod.MOD_ID)
public final class CraftingEvents {
    private CraftingEvents() {}

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (!(result.getItem() instanceof OmniBatteryItem resultItem)) return;
        if (event.getEntity() == null || event.getEntity().level().isClientSide) return;

        Container inv = event.getInventory();
        long bestEnergy = -1L;
        BatteryMode bestMode = null;
        int bestRate = 0;
        int bestRange = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || s == result) continue;
            if (!(s.getItem() instanceof OmniBatteryItem srcItem)) continue;
            long e = BatteryData.getEnergy(s);
            if (e > bestEnergy) {
                bestEnergy = e;
                bestMode = BatteryData.getMode(s);
                bestRate = BatteryData.getRateIndex(s);
                bestRange = BatteryData.getRange(s, srcItem.getTier());
            }
        }
        if (bestEnergy <= 0L) return;

        BatteryTier tier = resultItem.getTier();
        BatteryData.setEnergy(result, Math.min(tier.capacity(), bestEnergy), tier);
        if (bestMode != null) {
            BatteryData.setMode(result, bestMode);
            BatteryData.setRateIndex(result, bestRate);
            BatteryData.setRange(result, tier, bestRange);
        }
    }
}
