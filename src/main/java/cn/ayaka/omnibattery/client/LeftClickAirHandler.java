package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryMod;
import cn.ayaka.omnibattery.network.ToggleAutoTagPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端：左键点击空气切换贴纸自动贴标开关。
 * LeftClickEmpty 只在客户端触发；这里检查手持是否贴纸，若是则发 payload 到服务端。
 */
@EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT)
public final class LeftClickAirHandler {
    private LeftClickAirHandler() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof MachineStickerItem) {
                PacketDistributor.sendToServer(new ToggleAutoTagPayload(true));
                return;
            }
        }
    }
}
