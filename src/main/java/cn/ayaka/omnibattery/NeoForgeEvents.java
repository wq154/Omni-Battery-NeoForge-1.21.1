package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.network.ToggleAutoTagPayload;
import cn.ayaka.omnibattery.registry.ModBlocks;
import cn.ayaka.omnibattery.registry.ModItems;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 能量能力 + 网络包 + Curios 集成 注册（MOD 总线）。
 *  - 方块 / 物品能量能力
 *  - 客户端 -> 服务端 payload（切换贴纸自动贴标）
 *  - 贴纸的 Curios ICurio 能力（当 Curios 已加载时启用）
 */
public final class NeoForgeEvents {

    @SubscribeEvent
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 方块：能量能力
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> be instanceof OmniBatteryBlockEntity battery ? battery.storage() : null,
                ModBlocks.LOW.get(), ModBlocks.MEDIUM.get(), ModBlocks.ADVANCED.get(), ModBlocks.ELITE.get(), ModBlocks.ULTIMATE.get());

        // 物品：能量能力
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> stack.getItem() instanceof OmniBatteryItem item
                        ? new ItemBatteryEnergyStorage(stack, item.getTier()) : null,
                ModItems.LOW.get(), ModItems.MEDIUM.get(), ModItems.ADVANCED.get(), ModItems.ELITE.get(), ModItems.ULTIMATE.get());

        // Curios 存在时，注册贴纸为可放入饰品栏的物品（放在独立类里避免直接依赖）
        if (ModList.get() != null && ModList.get().isLoaded("curios")) {
            cn.ayaka.omnibattery.compat.CuriosStickerCap.registerItemCap(event, ModItems.MACHINE_STICKER.get());
        }
    }

    @SubscribeEvent
    public void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(OmniBatteryMod.MOD_ID).versioned("1.0");
        registrar.playToServer(ToggleAutoTagPayload.TYPE, ToggleAutoTagPayload.STREAM_CODEC,
                ToggleAutoTagPayload::handle);
    }
}
