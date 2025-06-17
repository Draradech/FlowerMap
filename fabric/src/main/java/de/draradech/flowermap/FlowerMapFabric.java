package de.draradech.flowermap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.resources.ResourceLocation;

public class FlowerMapFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FlowerMapMain.init();

        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyToggle);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyToggleDynamic);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyIncreaseY);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyDecreaseY);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keySetY);

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.BOSS_BAR,
            ResourceLocation.fromNamespaceAndPath(FlowerMapMain.MODID,"hud-layer"),
            (guiGraphics, delta) -> { FlowerMapMain.render(guiGraphics); }
        );
    }
}
