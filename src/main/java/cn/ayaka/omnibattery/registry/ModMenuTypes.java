package cn.ayaka.omnibattery.registry;

import cn.ayaka.omnibattery.OmniBatteryMenu;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, OmniBatteryMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<OmniBatteryMenu>> OMNI_BATTERY =
            MENU_TYPES.register("omni_battery", () -> new MenuType<>(OmniBatteryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenuTypes() {}
}