package cn.ayaka.omnibattery.registry;

import cn.ayaka.omnibattery.BatteryTier;
import cn.ayaka.omnibattery.MachineStickerItem;
import cn.ayaka.omnibattery.OmniBatteryItem;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, OmniBatteryMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OmniBatteryMod.MOD_ID);

    public static final DeferredHolder<Item, Item> LOW = ITEMS.register("low_battery", () -> new OmniBatteryItem(BatteryTier.LOW, new Item.Properties()));
    public static final DeferredHolder<Item, Item> MEDIUM = ITEMS.register("medium_battery", () -> new OmniBatteryItem(BatteryTier.MEDIUM, new Item.Properties()));
    public static final DeferredHolder<Item, Item> ADVANCED = ITEMS.register("advanced_battery", () -> new OmniBatteryItem(BatteryTier.ADVANCED, new Item.Properties()));
    public static final DeferredHolder<Item, Item> ELITE = ITEMS.register("elite_battery", () -> new OmniBatteryItem(BatteryTier.ELITE, new Item.Properties()));
    public static final DeferredHolder<Item, Item> ULTIMATE = ITEMS.register("ultimate_battery", () -> new OmniBatteryItem(BatteryTier.ULTIMATE, new Item.Properties()));
    public static final DeferredHolder<Item, Item> MACHINE_STICKER = ITEMS.register("machine_sticker", () -> new MachineStickerItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.omnibattery.main"))
            .icon(() -> new ItemStack(ULTIMATE.get()))
            .displayItems((p, o) -> {
                o.accept(LOW.get());
                o.accept(MEDIUM.get());
                o.accept(ADVANCED.get());
                o.accept(ELITE.get());
                o.accept(ULTIMATE.get());
                o.accept(MACHINE_STICKER.get());
            })
            .build());
}
