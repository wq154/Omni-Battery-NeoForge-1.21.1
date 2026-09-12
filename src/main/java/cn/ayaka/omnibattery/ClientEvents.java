package cn.ayaka.omnibattery;

import cn.ayaka.omnibattery.client.RangeOverlay;
import cn.ayaka.omnibattery.client.OmniBatteryScreen;
import cn.ayaka.omnibattery.registry.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** 客户端事件：GUI 注册 + 范围显示渲染。 */
public final class ClientEvents {
    private ClientEvents() {}

    @EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModBus {
        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent e) {
            e.register(ModMenuTypes.OMNI_BATTERY.get(), OmniBatteryScreen::new);
        }

        @SubscribeEvent
        public static void bers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(
                    cn.ayaka.omnibattery.registry.ModBlockEntities.OMNI_BATTERY.get(),
                    cn.ayaka.omnibattery.client.OmniBatteryRenderer::new);
        }
    }

    @EventBusSubscriber(modid = OmniBatteryMod.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void render(RenderLevelStageEvent e) {
            RangeOverlay.render(e);
        }
    }
}