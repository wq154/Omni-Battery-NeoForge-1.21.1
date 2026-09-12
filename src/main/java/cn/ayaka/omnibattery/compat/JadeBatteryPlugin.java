package cn.ayaka.omnibattery.compat;

import cn.ayaka.omnibattery.OmniBatteryBlock;
import cn.ayaka.omnibattery.OmniBatteryBlockEntity;
import cn.ayaka.omnibattery.OmniBatteryMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;

/**
 * Jade 集成（可选依赖，Jade 不在时本类不会被加载）。
 *
 * 背景：NeoForge 的能量能力是 int 接口，容量超过 21.4 亿时任何外部模组（Jade 等）
 * 都只能读到 2.14G，于是出现"外面显示满电、里面还没满"的错觉。
 * Jade 本身支持 long 能量数据（EnergyView 用 Cur/Capacity 两个 long 键），
 * 这里给我们的方块注册一个比 Jade 通用 provider（注册在 Block 上）更具体的能量数据源，
 * 直接把内部 long 真实数值交给 Jade 显示，比例与数值都与内部一致。
 */
@WailaPlugin(OmniBatteryMod.MOD_ID)
public class JadeBatteryPlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEnergyStorage(BatteryEnergyProvider.INSTANCE, OmniBatteryBlock.class);
    }

    /** 直接把内部 long 能量塞进 Jade 的能源视图 NBT（键名取自 Jade EnergyView）。 */
    public enum BatteryEnergyProvider implements IServerExtensionProvider<CompoundTag> {
        INSTANCE;

        @Override
        public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
            if (!(accessor instanceof BlockAccessor blockAccessor)) return List.of();
            BlockEntity be = blockAccessor.getBlockEntity();
            if (!(be instanceof OmniBatteryBlockEntity battery)) return List.of();
            CompoundTag tag = new CompoundTag();
            tag.putLong("Capacity", battery.getTier().capacity());
            tag.putLong("Cur", battery.getEnergy());
            return List.of(new ViewGroup<>(List.of(tag)));
        }

        @Override
        public boolean shouldRequestData(Accessor<?> accessor) {
            return accessor instanceof BlockAccessor blockAccessor
                    && blockAccessor.getBlockEntity() instanceof OmniBatteryBlockEntity;
        }

        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath(OmniBatteryMod.MOD_ID, "battery_energy");
        }

        /** 比 Jade 通用能源 provider（1000）更高，确保我们的方块优先用这份数据。 */
        @Override
        public int getDefaultPriority() {
            return 2000;
        }
    }
}
