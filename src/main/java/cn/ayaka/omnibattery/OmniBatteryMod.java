package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.registry.ModBlockEntities;
import cn.ayaka.omnibattery.registry.ModBlocks;
import cn.ayaka.omnibattery.registry.ModItems;
import cn.ayaka.omnibattery.registry.ModMenuTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(OmniBatteryMod.MOD_ID)
public final class OmniBatteryMod {
    public static final String MOD_ID = "omnibattery";

    public OmniBatteryMod(IEventBus bus) {
        ModBlocks.BLOCKS.register(bus);
        ModBlockEntities.TYPES.register(bus);
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        ModMenuTypes.MENU_TYPES.register(bus);
        bus.register(new NeoForgeEvents());
    }
}