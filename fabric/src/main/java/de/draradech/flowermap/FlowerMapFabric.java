package de.draradech.flowermap;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;


public class FlowerMapFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FlowerMapMain.init();

        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyToggle);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyToggleDynamic);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyIncreaseY);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keyDecreaseY);
        KeyBindingHelper.registerKeyBinding(FlowerMapMain.keySetY);

        HudRenderCallback.EVENT.register((guiGraphics, delta) -> { FlowerMapMain.render(guiGraphics); });
    }
}
