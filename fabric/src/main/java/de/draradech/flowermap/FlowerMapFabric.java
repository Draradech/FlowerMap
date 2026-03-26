package de.draradech.flowermap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.resources.Identifier;

public class FlowerMapFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FlowerMapMain.init();

        KeyMappingHelper.registerKeyMapping(FlowerMapMain.keyToggle);
        KeyMappingHelper.registerKeyMapping(FlowerMapMain.keyToggleMode);
        KeyMappingHelper.registerKeyMapping(FlowerMapMain.keyIncreaseY);
        KeyMappingHelper.registerKeyMapping(FlowerMapMain.keyDecreaseY);
        KeyMappingHelper.registerKeyMapping(FlowerMapMain.keySetY);

        HudElementRegistry.attachElementAfter(
            VanillaHudElements.BOSS_BAR,
            Identifier.fromNamespaceAndPath(FlowerMapMain.MODID,"hud-layer"),
            (guiGraphics, delta) -> { FlowerMapMain.render(guiGraphics); }
        );
    }
}
