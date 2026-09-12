package cn.ayaka.omnibattery.registry;
import cn.ayaka.omnibattery.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
public final class ModBlockEntities {
 public static final DeferredRegister<BlockEntityType<?>> TYPES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,OmniBatteryMod.MOD_ID);
 public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<OmniBatteryBlockEntity>> OMNI_BATTERY=TYPES.register("omni_battery",()->BlockEntityType.Builder.of(OmniBatteryBlockEntity::new,ModBlocks.LOW.get(),ModBlocks.MEDIUM.get(),ModBlocks.ADVANCED.get(),ModBlocks.ELITE.get(),ModBlocks.ULTIMATE.get()).build(null));
}
