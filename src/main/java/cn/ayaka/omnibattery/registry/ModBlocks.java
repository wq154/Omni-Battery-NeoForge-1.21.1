package cn.ayaka.omnibattery.registry;
import cn.ayaka.omnibattery.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.*;
public final class ModBlocks {
 public static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,OmniBatteryMod.MOD_ID);
 public static final DeferredHolder<Block,Block> LOW=BLOCKS.register("low_battery_block",()->new OmniBatteryBlock(BatteryTier.LOW));
 public static final DeferredHolder<Block,Block> MEDIUM=BLOCKS.register("medium_battery_block",()->new OmniBatteryBlock(BatteryTier.MEDIUM));
 public static final DeferredHolder<Block,Block> ADVANCED=BLOCKS.register("advanced_battery_block",()->new OmniBatteryBlock(BatteryTier.ADVANCED));
 public static final DeferredHolder<Block,Block> ELITE=BLOCKS.register("elite_battery_block",()->new OmniBatteryBlock(BatteryTier.ELITE));
 public static final DeferredHolder<Block,Block> ULTIMATE=BLOCKS.register("ultimate_battery_block",()->new OmniBatteryBlock(BatteryTier.ULTIMATE));
}
