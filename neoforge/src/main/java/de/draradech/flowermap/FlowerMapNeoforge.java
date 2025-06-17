package de.draradech.flowermap;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@Mod(FlowerMapMain.MODID)
public class FlowerMapNeoforge {
    public FlowerMapNeoforge() {
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (container, parent) -> {
            return AutoConfig.getConfigScreen(FlowerMapConfig.class, parent).get();
        });
    }

    @EventBusSubscriber(modid = FlowerMapMain.MODID, bus = EventBusSubscriber.Bus.MOD)
    static class ModBusSubscriber {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            FlowerMapMain.init();

            event.register(FlowerMapMain.keyToggle);
            event.register(FlowerMapMain.keyToggleDynamic);
            event.register(FlowerMapMain.keyIncreaseY);
            event.register(FlowerMapMain.keyDecreaseY);
            event.register(FlowerMapMain.keySetY);
        }

        @SubscribeEvent
        public static void registerGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAbove(
                VanillaGuiLayers.BOSS_OVERLAY,
                ResourceLocation.fromNamespaceAndPath(FlowerMapMain.MODID,"hud-layer"),
                new GuiLayer() {
                    @Override
                    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
                        if (!Minecraft.getInstance().options.hideGui) FlowerMapMain.render(guiGraphics);
                    }
                }
            );
        }
    }
}
