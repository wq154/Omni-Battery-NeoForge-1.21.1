package cn.ayaka.omnibattery;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;

/**
 * 游戏总线事件：注册命令 + 方块被破坏时清理对应贴标记录。
 */
@EventBusSubscriber(modid = OmniBatteryMod.MOD_ID)
public final class OmniBatteryTicker {
    private OmniBatteryTicker() {}

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        OmniBatteryCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (levelAccessor instanceof ServerLevel level) {
            StickerSavedData.get(level).removeSticker(event.getPos());
        }
    }
}