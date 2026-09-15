package cn.ayaka.omnibattery.client;

import cn.ayaka.omnibattery.OmniBatteryMod;
import cn.ayaka.omnibattery.network.OpenBoundBatteryPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键：默认 B，打开贴纸绑定的电池界面（远程）。
 */
public final class ModKeybinds {
    private ModKeybinds() {}

    public static final String CATEGORY = "key.categories.omnibattery";
    // 默认 \ （反斜杠）：几乎不与其它模组冲突；玩家可在「选项 -> 控制 -> 按键绑定」里随时修改。
    public static final KeyMapping OPEN_BATTERY = new KeyMapping(
            "key.omnibattery.open_battery", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSLASH, CATEGORY);

    @EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT,
            bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        private ModBus() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_BATTERY);
        }
    }

    @EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            while (OPEN_BATTERY.consumeClick()) {
                PacketDistributor.sendToServer(new OpenBoundBatteryPayload(true));
            }
        }
    }
}
