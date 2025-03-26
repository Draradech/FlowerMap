package de.draradech.flowermap;

import me.shedaniel.autoconfig.AutoConfig;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;


@Mod(FlowerMapNeoforge.MODID)
public class FlowerMapNeoforge {
    public static final String MODID = "flowermap";
    
    public FlowerMapNeoforge() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (container, parent) -> {
            return AutoConfig.getConfigScreen(FlowerMapConfig.class, parent).get();
        });
    }

    @EventBusSubscriber(modid = FlowerMapNeoforge.MODID, bus = EventBusSubscriber.Bus.MOD)
    static class ModBusSubscriber {
        @SubscribeEvent
        public static void registerBindings(RegisterKeyMappingsEvent event) {
            FlowerMapMain.init();

            event.register(FlowerMapMain.keyToggle);
            event.register(FlowerMapMain.keyToggleDynamic);
            event.register(FlowerMapMain.keyIncreaseY);
            event.register(FlowerMapMain.keyDecreaseY);
            event.register(FlowerMapMain.keySetY);
        }
    }

    @EventBusSubscriber(modid = FlowerMapNeoforge.MODID)
    static class BusSubscriber {
        @SubscribeEvent
        public static void onPostRenderGui(RenderGuiEvent.Post event) {
            FlowerMapMain.render(event.getGuiGraphics());
        }
    }
}
