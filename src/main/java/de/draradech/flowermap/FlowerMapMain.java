package de.draradech.flowermap;

import org.lwjgl.glfw.GLFW;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;


public class FlowerMapMain implements ModInitializer{
    public static FlowerMapConfig config;
    public static FlowerMapRenderer renderer;
    static KeyMapping keyToggle;
    
    @Override
    public void onInitialize() {
        AutoConfig.register(FlowerMapConfig.class, GsonConfigSerializer::new);
        config = AutoConfig.getConfigHolder(FlowerMapConfig.class).getConfig();
        
        renderer = new FlowerMapRenderer();
        keyToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.flowermap.toggle", GLFW.GLFW_KEY_F8, "category.flowermap"));
        
        HudRenderCallback.EVENT.register((matrixStack, delta) -> {
            if (keyToggle.consumeClick()) renderer.toggle();
            renderer.render();
        });
    }
}
